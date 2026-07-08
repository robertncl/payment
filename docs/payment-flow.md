# Payment processing flow (Phase 1)

How a cross-border payment moves through PayLab, end to end. Code references are the
authoritative source; this document explains how the pieces compose.

## Actors

| Service | Role |
|---|---|
| **payment-gateway** | Only public HTTP surface. Owns the payment record, its state machine, idempotency, and the outbox. Orchestrates the other services over SOFARPC (bolt). |
| **fx-service** | Quotes exchange rates from a static in-repo table and locks them into 60-second quotes. |
| **ledger-service** | Append-only double-entry journal. The financial source of truth. |
| **risk-service** | Phase 2 — currently stubbed as auto-approve inside the gateway. |

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
3. **Local transaction.** The payment is persisted with a fixed 1% fee (`FeePolicy`),
   auto-approved by the risk stub (`CREATED → RISK_APPROVED`), both transitions are
   recorded as `PaymentEvent` audit rows, and a `payment.created` event is written to the
   outbox — all atomically. No money moves at create.

## 2. Capture — `POST /api/payments/{id}/capture`

The step where money moves, in three deliberate phases (see ADR-0002):

1. **Guard.** Reject unless `RISK_APPROVED → CAPTURED` is legal, before any side effect.
2. **Remote side effects, before local commit.**
   - `FxFacade.lockQuote` freezes the current rate (mid derived from per-USD quotes,
     minus a 15 bps spread) into a quote with a 60s TTL. Captures always execute at a
     locked quote, never a live rate, so the rate stored on the payment and the rate the
     ledger posted at are the same number.
   - The gateway computes `targetAmount = round4(amount × rate)` and sends a
     `CapturePostingCommand` to `LedgerFacade.postCapture`. The ledger re-validates the
     arithmetic and books one balanced journal entry (five legs, see below). The posting
     is idempotent per `(paymentId, CAPTURE)`.
3. **Local commit.** Transition to `CAPTURED`, attach the FX terms to the payment, append
   the audit event, enqueue `payment.captured`.

Why side effects run first: pre-Seata there is no distributed transaction. If the process
crashes after the ledger posted but before the local commit, the payment still reads
`RISK_APPROVED` and the client simply retries — the ledger call is absorbed as
already-posted and the local state catches up. The reverse order (commit locally, then
post) could strand a `CAPTURED` payment with no ledger entry, which is a books integrity
violation with no self-healing retry. Phase 2 replaces this ordering with a Seata AT scope.

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

Full refund of a `CAPTURED` payment, same shape as capture: ledger first, local commit
second. The ledger loads the original CAPTURE legs and reposts them with each direction
flipped as a REFUND entry — the capture itself is never modified. Idempotent per
`(paymentId, REFUND)`.

## Cross-cutting machinery

- **Idempotency (gateway).** Insert-first key claim; successful responses are stored and
  replayed byte-identically; business failures release the key so the same key may retry.
- **Idempotency (ledger).** Natural key `(payment_id, entry_type)` with a DB unique
  constraint as the last line of defense against concurrent duplicates.
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
