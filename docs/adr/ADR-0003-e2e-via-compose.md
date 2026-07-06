# ADR-0003: E2E tests run against the compose stack, not Testcontainers

- **Status:** Accepted
- **Date:** 2026-07-07
- **Phase:** 1

## Context

The spec allows "Testcontainers or docker-compose" for the e2e suite. A Testcontainers
setup would need to orchestrate OceanBase (mini mode: ~6 GB RAM, 3–5 min bootstrap),
SOFARegistry, and five services per test run — slow, memory-hungry, and it duplicates the
compose topology we already maintain as the dev environment.

## Decision

`e2e-tests` is a plain JUnit module that talks to the gateway's public REST API on
`localhost:8080` (`PAYLAB_GATEWAY_URL` to override). It is **skipped by default** and enabled
with `-Pe2e`. The stack is brought up once per session/CI job:

```
docker compose up -d --build
./scripts/wait-healthy.sh 7 600
mvn -Pe2e -pl e2e-tests -am test
```

Tests are black-box and REST-only (the ledger is asserted through the gateway's
`/api/trial-balance` proxy), use fresh UUID-based idempotency keys/payers per run, and assert
global invariants (trial balance nets to zero) rather than absolute totals, so runs are
repeatable against a dirty database.

## Consequences

- One stack definition (docker-compose.yml) serves dev, gate verification, and CI.
- CI runs the suite in a dedicated job on ubuntu-latest (16 GB public runners fit OceanBase
  mini); the stack is torn down with `down -v` afterwards.
- Test data accumulates in a long-lived local stack; acceptable because assertions are
  invariant-based. `docker compose down -v` resets.
