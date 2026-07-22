# PayLab — Pinned Versions

Single source of truth for every version used in this repo. Verified against official
GitHub releases / Maven Central / Docker Hub on **2026-07-06**, re-verified **2026-07-21**.
Change versions here first, then propagate; record any compatibility surprise as an ADR.

Docker base/runtime images (docker-compose.yml, Dockerfiles) and GitHub Actions
(.github/workflows/ci.yml) are pinned to an exact digest/commit SHA alongside the
human-readable tag, e.g. `image@sha256:...` / `uses: owner/action@<sha> # vX.Y.Z` — the tag
is a floating pointer, the SHA is what actually gets pulled/run. Re-resolve the digest when
bumping the tag (`docker buildx imagetools inspect <image>:<tag>` /
`gh api repos/<owner>/<action>/git/refs/tags/<tag>`).

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
| SOFABoot (`sofaboot-dependencies`) | **4.6.0** | [sofastack/sofa-boot](https://github.com/sofastack/sofa-boot/releases) | Parent BOM of this repo. Spring Boot 3.x line, JDK 17+. Still latest tag as of 2026-07-21 |
| Spring Boot (via SOFABoot BOM) | **3.5.6** (resolved, never overridden locally) | — | Verified via `dependency:tree` |
| SOFARPC starter (`rpc-sofa-boot-starter`) | **4.6.0** (BOM-managed `${sofa.boot.version}`, wraps `sofa-rpc-all` **5.13.4**) | [sofastack/sofa-boot](https://github.com/sofastack/sofa-boot/tree/v4.6.0/sofa-boot-project/sofa-boot-starters/rpc-sofa-boot-starter) | ⚠️ Trap: Central's `maven-metadata.xml` for this artifactId lists only 5.5.3/6.0.x and calls 6.0.4 `<latest>` — those are **SOFABoot 3 / Spring Boot 2 / JDK 8** artifacts (parent `sofaboot-dependencies:3.1.4`) whose transitive `infra-sofa-boot-starter:3.1.4` + `log-sofa-boot-starter:1.0.18` NoClassDefFoundError against `sofa-common-tools` 2.x. Never pin this artifact; let the SOFABoot BOM resolve it |
| SOFABolt | 1.6.10 | managed by SOFABoot BOM | RPC transport |
| SOFATracer (`tracer-core`) | 4.0.2 | managed by SOFABoot BOM | OTLP bridge wired in Phase 1+ |
| SOFARegistry **server** | **6.1.9** (`registry-all.tgz`) | [sofastack/sofa-registry](https://github.com/sofastack/sofa-registry/releases/tag/v6.1.9) | Runs on JDK 8 in-container (`eclipse-temurin:8-jre-noble`, image digest-pinned); integration/dev mode with embedded H2. Still latest tag as of 2026-07-21 |
| SOFARegistry **client** (`registry-client-all`) | **6.5.6** | Maven Central | ⚠️ SOFABoot BOM pins 6.1.8 which is **absent from Central**. Overridden via `sofa.registry.version`; bumped 6.3.0 → 6.5.6 (2026-07-21, latest non-classifier release — skips the `6.5.6.20241001` dated variant); v6 wire protocol compatible with 6.1.9 server; full `mvn verify` + compose e2e re-run green after the bump. See ADR-0001 |
| Seata | **2.6.0** (`org.apache.seata`) | [apache/incubator-seata](https://github.com/apache/incubator-seata/releases) | Server image `apache/seata-server:2.6.0.jdk17`, digest-pinned. GroupId changed from `io.seata` at 2.1.0. Wired in Phase 2. Still latest tag as of 2026-07-21 |
| OceanBase CE | **4.3.5-lts** (image `oceanbase/oceanbase-ce:4.3.5-lts`, build `4.3.5.6`, digest-pinned) | [Docker Hub](https://hub.docker.com/r/oceanbase/oceanbase-ce/tags) | LTS line; arm64 image available. 4.4.2-lts exists (newer LTS line, released 2026-03-05) but 4.3.5 is deliberately kept — it's the line supported broadly by ob-operator/ApsaraDB; bumping the major LTS line is a separate decision, not a routine dependency update. MySQL-mode tenant, port 2881 |
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
| Spotless (`spotless-maven-plugin`) | **3.8.0** | Bumped 2.44.5 → 3.8.0 (2026-07-21); requires Java 17+ (already the baseline here). Bundled formatter version moved too — one file needed a re-wrap, fixed via `mvn spotless:apply` and committed. palantir-java-format; `mvn spotless:apply` to fix |
| k6 | **v2.1.0** | Phase 2 latency proof |
| Syft / Grype / Cosign | pin in Phase 4 workflow | DevSecOps stage |
| GitHub Actions (`actions/checkout`, `actions/setup-java`) | **v7.0.1**, **v5.6.0** | Bumped from v4/v4 (2026-07-21); pinned to commit SHA in ci.yml with the tag as a trailing comment |

## Build note — resolved BOM chain

`io.paylab:paylab-parent` → `com.alipay.sofa:sofaboot-dependencies:4.6.0` → Spring Boot BOM.
Run `mvn -pl services/payment-gateway dependency:tree -Dincludes=org.springframework.boot` to
print the exact resolved Spring Boot version locally.
