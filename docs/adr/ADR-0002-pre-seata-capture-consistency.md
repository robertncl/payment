# ADR-0002: Pre-Seata consistency for capture/refund (Phase 1 only)

- **Status:** Superseded by ADR-0004 (Seata AT). This ordering remains the documented
  fallback whenever Seata is disabled (`PAYLAB_SEATA_ENABLED=false`).
- **Date:** 2026-07-07
- **Phase:** 1

## Context

The spec requires payment state change + ledger posting to be atomic, explicitly as the
Seata showcase — scheduled for Phase 2. Phase 1 still needs a correctness story for the
gateway→ledger flow so the e2e gate is meaningful and the Phase 2 diff is legible.

## Decision

Until Seata lands, capture/refund use **idempotent-callee-first ordering** instead of a
distributed transaction:

1. Validate the state transition first (cheap local reject of illegal calls).
2. Call fx (`lockQuote`) and ledger (`postCapture`/`postRefund`) **before** the local commit.
   Ledger postings are idempotent per `(payment_id, entry_type)` (unique index), so retries
   and races cannot double-post.
3. Commit the local transaction: payment status + timeline event + outbox row together.
4. Concurrent captures are resolved by re-checking status inside the local transaction plus
   JPA optimistic locking (`@Version`); the loser returns the winner's row.

Failure window: a crash between (2) and (3) leaves a ledger CAPTURE entry with the payment
still `RISK_APPROVED`. The client retry (same or new Idempotency-Key) re-runs capture; the
ledger call replays as `alreadyPosted` — but the *second* attempt locks a **new fx quote**
whose rate the ledger ignores, so payment.fx_rate could disagree with the posted legs if the
static table ever changed between attempts. With the static in-repo rate table this cannot
produce a real mismatch today; it is exactly the class of gap Seata AT closes in Phase 2.

## Consequences

- No orphaned ledger entries under retry; no double-posting under races (unique-index-backed).
- A crashed capture is *client-visible* (payment not CAPTURED) rather than silently
  half-committed — acceptable for a lab gate, not for production.
- Phase 2 wraps steps 2–3 in a Seata global transaction and adds the forced-failure rollback
  test; this ADR is then superseded.
