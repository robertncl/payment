# Architecture Decision Records

Any deviation from the project spec, any non-obvious tool/version choice, and any
"a reasonable staff engineer would decide alone" call gets an ADR. Numbered, never deleted;
supersede with a new one.

| ADR | Title | Status |
|---|---|---|
| [0001](ADR-0001-service-registry.md) | Service registry: SOFARegistry in dev/integration mode | Accepted |
| [0002](ADR-0002-pre-seata-capture-consistency.md) | Pre-Seata capture/refund consistency (idempotent-callee-first) | Superseded by 0004 |
| [0003](ADR-0003-e2e-via-compose.md) | E2E tests against the compose stack, not Testcontainers | Accepted |
| [0004](ADR-0004-seata-at-capture.md) | Seata AT global transactions for capture/refund | Accepted |

## Template

```markdown
# ADR-NNNN: <title>

- **Status:** Proposed | Accepted | Superseded by ADR-MMMM
- **Date:** YYYY-MM-DD
- **Phase:** N

## Context
## Decision
## Consequences
```
