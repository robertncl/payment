# PayLab — Pinned Versions

Single source of truth for every version used in this repo. Verified against official
GitHub releases / Maven Central / Docker Hub on **2026-07-06**. Change versions here first,
then propagate; record any compatibility surprise as an ADR.

## Toolchain

| Tool | Version | Notes |
|---|---|---|
| JDK | **Temurin 17.0.19+10** | SOFABoot 4.x requires JDK 17+. Local dev: `~/tools/jdk17` (linux/aarch64); containers: `eclipse-temurin:17-jre-noble` |
| Maven | **3.9.16** | Latest 3.9.x from dlcdn.apache.org |
| Docker / Compose | 29.x / v5.x | Docker Desktop (WSL2, **aarch64** host — all images below verified multi-arch arm64+amd64) |
| Node.js | 24.x | For merchant-portal (Phase 3) |

## Backend (SOFAStack / Alibaba OSS)

| Component | Version | Source | Notes |
|---|---|---|---|
| SOFABoot (`sofaboot-dependencies`) | **4.6.0** | [sofastack/sofa-boot](https://github.com/sofastack/sofa-boot/releases) | Parent BOM of this repo. Spring Boot 3.x line, JDK 17+ |
| Spring Boot (via SOFABoot BOM) | _resolved by BOM — see build note below_ | — | Never overridden locally |
| SOFARPC starter (`rpc-sofa-boot-starter`) | **6.0.4** | [sofastack/rpc-sofa-boot](https://github.com/sofastack/rpc-sofa-boot) | The Boot-3 RPC starter line. NOT `sofa-rpc` 5.14.x (that line is Spring Boot 2 / JDK 8) |
| SOFABolt | 1.6.10 | managed by SOFABoot BOM | RPC transport |
| SOFATracer (`tracer-core`) | 4.0.2 | managed by SOFABoot BOM | OTLP bridge wired in Phase 1+ |
| SOFARegistry **server** | **6.1.9** (`registry-all.tgz`) | [sofastack/sofa-registry](https://github.com/sofastack/sofa-registry/releases/tag/v6.1.9) | Runs on JDK 8 in-container (`eclipse-temurin:8-jre-noble`); integration/dev mode with embedded H2 |
| SOFARegistry **client** (`registry-client-all`) | **6.3.0** | Maven Central | ⚠️ SOFABoot BOM pins 6.1.8 which is **absent from Central** (gap 5.4.3 → 6.3.0). Overridden via `sofa.registry.version`; v6 wire protocol compatible with 6.1.9 server. See ADR-0001 |
| Seata | **2.6.0** (`org.apache.seata`) | [apache/incubator-seata](https://github.com/apache/incubator-seata/releases) | Server image `apache/seata-server:2.6.0.jdk17`. GroupId changed from `io.seata` at 2.1.0. Wired in Phase 2 |
| OceanBase CE | **4.3.5-lts** (image `oceanbase/oceanbase-ce:4.3.5-lts`, build `4.3.5.6`) | [Docker Hub](https://hub.docker.com/r/oceanbase/oceanbase-ce/tags) | LTS line; arm64 image available. 4.4.2-lts exists but 4.3.5 is the line supported broadly by ob-operator/ApsaraDB. MySQL-mode tenant, port 2881 |
| ob-operator (Phase 4, GKE) | **2.3.4** | [oceanbase/ob-operator](https://github.com/oceanbase/ob-operator/releases) | |
| MySQL driver (`mysql-connector-j`) | managed by Spring Boot BOM | — | OceanBase MySQL mode is compatible with 8.x/9.x connectors; override only if OB rejects it |
| MOSN (Phase 5 stretch) | pin when Phase 5 starts | [mosn/mosn](https://github.com/mosn/mosn) | Deliberately unpinned until then |

## Frontend (Phase 3)

| Component | Version | Notes |
|---|---|---|
| Ant Design | **5.29.3** | Spec mandates antd 5 (antd 6 exists — not used) |
| Umi | **@umijs/max 4.6.72** | Includes dva-style model plugin |

## Quality / Ops

| Component | Version | Notes |
|---|---|---|
| JUnit | 5.x (Spring Boot BOM) | |
| Spotless (`spotless-maven-plugin`) | 2.44.5 | palantir-java-format; `mvn spotless:apply` to fix |
| k6 | **v2.1.0** | Phase 2 latency proof |
| Syft / Grype / Cosign | pin in Phase 4 workflow | DevSecOps stage |

## Build note — resolved BOM chain

`io.paylab:paylab-parent` → `com.alipay.sofa:sofaboot-dependencies:4.6.0` → Spring Boot BOM.
Run `mvn -pl services/payment-gateway dependency:tree -Dincludes=org.springframework.boot` to
print the exact resolved Spring Boot version locally.
