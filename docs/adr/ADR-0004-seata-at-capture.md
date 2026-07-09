# ADR-0004: Seata AT global transactions for capture/refund

- **Status:** Accepted (supersedes ADR-0002)
- **Date:** 2026-07-09
- **Phase:** 2

## Context

ADR-0002 bridged Phase 1 with idempotent-callee-first ordering: ledger posting before local
commit, healed by client retry. Its documented gap — a crash between the ledger branch and
the local commit leaves work half-done until a client retries, and a retry locks a fresh fx
quote whose rate the ledger ignores. The spec schedules Seata as the Phase 2 showcase to
close exactly this.

## Decision

Capture and refund each run inside a **Seata 2.6.0 AT global transaction**
(`@GlobalTransactional` on `PaymentService.capture/refund`):

- **Branches.** (1) ledger posting (`postCapture`/`postRefund` over bolt) against
  `paylab_ledger`; (2) the gateway's local commit (payment status + timeline + outbox)
  against `paylab_gateway`. Both DBs carry Seata's `undo_log` (Flyway V2). The **fx quote is
  deliberately not a branch** — quotes are in-memory, expire in 60s, and cost nothing to
  re-lock; there is nothing to compensate.
- **XID propagation.** `org.apache.seata:seata-sofa-rpc` ships SPI-registered SOFARPC
  filters (`TransactionContextProviderFilter`/`ConsumerFilter`) — presence on the classpath
  wires XID transport over bolt; no code.
- **Client config.** `seata-spring-boot-starter` (Boot-3 ready, verified
  `AutoConfiguration.imports`), file registry, `paylab-tx-group → default →
  $PAYLAB_SEATA_ENDPOINT`. `seata.enabled` is env-gated and **defaults false**: bare local
  runs and unit tests need no TC and degrade to the ADR-0002 ordering, which stays correct
  (side effects before local commit + idempotent callee). ⚠️ Seata's autoconfiguration is
  `matchIfMissing=true` — test properties shadow main's, so every test properties file must
  set `seata.enabled=false` explicitly.
- **Server.** `apache/seata-server:2.6.0.jdk17` (multi-arch, arm64 ✓) in compose, file
  store, TC on 8091, console 7091.
- **Forced-rollback gate.** A chaos hook (`X-PayLab-Chaos: fail-after-capture-branches`
  header, honored only when `paylab.chaos.enabled=true`) throws after BOTH branches
  completed; the e2e asserts the payment is still RISK_APPROVED and the payer account never
  appears in the trial balance — i.e. both branches rolled back — then retries the same
  idempotency key (released on failure) to a clean success.

## Consequences

- Capture/refund state change + ledger posting are atomic; the ADR-0002 retry-heals window
  is closed. Idempotent postings stay as defense in depth (and remain load-bearing whenever
  Seata is disabled).
- The ledger's no-UPDATE/no-DELETE rule is unchanged for business flows; Seata's rollback
  deleting rows a rolled-back branch inserted is transaction recovery, not a correction.
- AT adds a TC round-trip + undo-log write per branch — measured by the Phase 2 k6 gate
  (slo/slo.md) rather than assumed.
- Journal inserts under an XID acquire Seata global locks keyed by primary key; entry ids
  are UUIDs, so contention is nil.
