# PayLab SLOs (Phase 2 baseline)

Targets for the compose stack on the reference lab machine (aarch64 WSL2, 12 cores / 15 GB,
OceanBase mini mode). They are deliberately loose absolute numbers — the point of the gate
is to catch regressions (e.g. an accidental N+1, a Seata misconfiguration adding retries),
not to benchmark hardware. Revisit for the cloud profiles in Phase 4.

| SLI | Target | Why |
|---|---|---|
| Create p95 / p99 | < 300 ms / < 600 ms | 1 RPC hop (risk assess) + 1 local tx |
| Capture p95 / p99 | < 800 ms / < 1500 ms | 2 RPC hops (fx, ledger) + Seata AT (TC round-trips + undo logs on 2 branches) |
| Error rate (non-2xx on the happy path) | < 1% | idempotency + retries should absorb transient noise |
| Availability (Phase 4+, per env) | 99.9% monthly | portal-facing API |

## Running the gate

```bash
docker compose up -d --build          # stack with Seata + chaos hook
./scripts/run-k6.sh                   # 10 rps for 2m (defaults)
./scripts/run-k6.sh 25 5m             # heavier soak
```

k6 v2.1.0 (pinned in versions.md) runs containerized on the compose network and targets
`payment-gateway:8080`; thresholds above are encoded in `perf/k6/payment-lifecycle.js` and
fail the process (non-zero exit) when breached. Summaries are written to `perf/results/`
(kept out of git except as needed for phase-gate evidence).

Method notes:

- Load payers are unique per iteration; the risk velocity rule (>10 approvals/min/payer)
  would otherwise decline load-generated payments and skew latencies.
- `create` measures risk-service in the loop; `capture` measures fx + ledger + the Seata
  global commit. A regression in exactly one of the two localizes the suspect immediately.
