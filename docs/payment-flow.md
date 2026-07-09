# Payment processing flow (Phase 2)

How a cross-border payment moves through PayLab, end to end. Code references are the
authoritative source; this document explains how the pieces compose.

## Actors

| Service | Role |
|---|---|
| **payment-gateway** | Only public HTTP surface. Owns the payment record, its state machine, idempotency, and the outbox. Orchestrates the other services over SOFARPC (bolt). |
| **risk-service** | Verdict at create time: denylist, per-corridor amount cap, payer velocity. Owns the decision log (idempotent per payment). |
| **fx-service** | Quotes exchange rates from a static in-repo table and locks them into 60-second quotes. |
| **ledger-service** | Append-only double-entry journal. The financial source of truth. |
| **seata-server** | Transaction coordinator: capture/refund enroll the ledger posting and the gateway's local commit as branches of one global transaction (ADR-0004). |

## State machine

```
CREATED ──► RISK_APPROVED ──► CAPTURED ──► SETTLED
   │              │               │
   ▼              ▼               ▼
RISK_DECLINED   FAILED         REFUNDED
```

`PaymentStateMachine` is the single gatekeeper: every status change goes through
`Payment.transitionTo`, which asserts the edge exists. Illegal transitions throw
(HTTP 409), they are never silently ignored. `SETTLED` is reached in Phase 3 by the
settlement job; in Phase 1 payments rest at `CAPTURED` or `REFUNDED`.

## 1. Create — `POST /api/payments`

1. **Idempotency claim.** Every mutating endpoint requires an `Idempotency-Key` header.
   `IdempotencyService.begin` inserts the key in its own transaction *before* business
   logic runs. A duplicate request either replays the stored response
   (`X-Idempotent-Replay: true`), gets 422 if the key was reused with a different body,
   or 409 if the original call is still in flight.
2. **Validation.** Currencies must be in the supported set (MYR/SGD/USD/EUR/CNY), source
   and target must differ (cross-border only), amount must be a positive DECIMAL(20,4).
3. **Risk verdict.** The gateway asks risk-service over bolt (like all remote side effects,
   before the local transaction). Rules run in order — denylisted payer/merchant, corridor
   supported and amount within its cap, payer velocity (max 10 approvals per rolling 60s) —
   first hit declines. The decision is recorded idempotently per paymentId, so a gateway
   retry replays the original verdict instead of re-rolling the dice.
4. **Local transaction.** The payment is persisted with a fixed 1% fee (`FeePolicy`) as
   `RISK_APPROVED` or `RISK_DECLINED` (a terminal state — the API returns the payment, not
   an error), the transitions are recorded as `PaymentEvent` audit rows with the risk reason
   code, and a `payment.created` event is written to the outbox — all atomically. No money
   moves at create.

## 2. Capture — `POST /api/payments/{id}/capture`

The step where money moves. The whole method runs inside a **Seata AT global transaction**
(`@GlobalTransactional`, ADR-0004); the XID travels to the ledger inside the bolt call via
seata-sofa-rpc's SPI filters:

1. **Guard.** Reject unless `RISK_APPROVED → CAPTURED` is legal, before any side effect.
2. **FX (not a branch).** `FxFacade.lockQuote` freezes the current rate (mid derived from
   per-USD quotes, minus a 15 bps spread) into a quote with a 60s TTL. Captures always
   execute at a locked quote, never a live rate, so the rate stored on the payment and the
   rate the ledger posted at are the same number. Quotes are in-memory and self-expiring —
   there is nothing to roll back, so fx deliberately stays outside the transaction.
3. **Ledger branch.** The gateway computes `targetAmount = round4(amount × rate)` and sends
   a `CapturePostingCommand` to `LedgerFacade.postCapture`. The ledger re-validates the
   arithmetic and books one balanced journal entry (five legs, see below), recording undo
   images in its `undo_log`. Still idempotent per `(paymentId, CAPTURE)` as defense in depth.
4. **Local branch.** Transition to `CAPTURED`, attach the FX terms to the payment, append
   the audit event, enqueue `payment.captured` — one local transaction, also undo-logged.

A failure anywhere before the global commit — crash, RPC error, or the chaos hook the
forced-rollback e2e uses — makes the coordinator roll back **both** branches: the journal
rows disappear and the payment reads `RISK_APPROVED` again, with the idempotency key
released for a clean retry. When Seata is disabled (bare local runs without a TC), the same
code degrades to the ADR-0002 ordering — idempotent callee first, healed by client retry —
which is why the posting idempotency stays load-bearing.

### Capture journal entry

Every entry must net to zero **per currency** (`PostingService.assertBalanced`); the FX
spread remains in `house:fx_pnl` as house P&L. For a payment of `amount` in source
currency `S`, converted to `targetAmount` in target currency `T`:

| Leg | Account | Ccy | Direction | Amount |
|---|---|---|---|---|
| 1 | `payer:{payerId}` | S | DEBIT | amount + fee |
| 2 | `house:fee_revenue` | S | CREDIT | fee |
| 3 | `house:fx_pnl` | S | CREDIT | amount |
| 4 | `house:fx_pnl` | T | DEBIT | targetAmount |
| 5 | `house:settlement_clearing` | T | CREDIT | targetAmount |

Accounts are created lazily on first posting. The journal tables only ever see INSERT and
SELECT — corrections are compensating entries, never updates.

## 3. Refund — `POST /api/payments/{id}/refund`

Full refund of a `CAPTURED` payment, in the same global-transaction shape as capture: the
ledger reversal and the local `CAPTURED → REFUNDED` transition are two branches of one XID.
The ledger loads the original CAPTURE legs and reposts them with each direction flipped as
a REFUND entry — the capture itself is never modified. Idempotent per `(paymentId, REFUND)`.

## Cross-cutting machinery

- **Idempotency (gateway).** Insert-first key claim; successful responses are stored and
  replayed byte-identically; business failures release the key so the same key may retry.
- **Idempotency (ledger).** Natural key `(payment_id, entry_type)` with a DB unique
  constraint as the last line of defense against concurrent duplicates.
- **Idempotency (risk).** One decision per `payment_id` (unique index); retries replay the
  stored verdict.
- **Distributed transactions.** Seata 2.6 AT, file-registry client config, TC in compose
  (`seata-server:8091`). `undo_log` tables live in `paylab_gateway` and `paylab_ledger`
  (Flyway V2 each). Env-gated by `PAYLAB_SEATA_ENABLED` — off for bare local runs/tests.
- **Chaos hook.** `X-PayLab-Chaos: fail-after-capture-branches` on capture (only honored
  when `paylab.chaos.enabled=true`) throws after both branches — the forced-rollback e2e
  gate watches Seata undo them.
- **Audit trail.** Every transition writes a `PaymentEvent`; `GET /api/payments/{id}/events`
  exposes the timeline.
- **Outbox.** Domain events commit in the same transaction as the state change;
  `OutboxRelay` polls and publishes them at-least-once (a structured log line until a
  broker arrives in Phase 3). Consumers must deduplicate.
- **Money rules.** DECIMAL(20,4), `BigDecimal` only, HALF_EVEN rounding, ISO-4217 codes
  (`Money`). Rates carry scale 10.
- **Verification.** `GET /api/trial-balance` proxies the ledger's trial balance: total
  debits vs credits per account/currency, and a `balanced` flag asserting every currency
  nets to zero across all postings ever made.
