# PayLab — Pinned Versions

Single source of truth for every version used in this repo. Verified against official
GitHub releases / Maven Central / Docker Hub on **2026-07-06**, re-verified **2026-07-21**,
re-verified **2026-08-08**, security review + full refresh **2026-10-16** (every bump
below cleared a 24h publish cooloff).
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
| JDK | **Temurin 17.0.20.1+1** (containers) | SOFABoot 4.x requires JDK 17+. Containers: `eclipse-temurin:17-jre-noble` / `maven:3.9-eclipse-temurin-17`, digests re-resolved 2026-10-16. Local dev `~/tools/jdk17` is still 17.0.19+10 (outside the repo; upgrade separately) |
| Maven | **3.9.16** | Latest 3.9.x. `./mvnw` verifies the distribution via `distributionSha256Sum` (cross-checked against Apache's published SHA-512) |
| Docker / Compose | 29.x / v5.x | Docker Desktop (WSL2, **aarch64** host — all images below verified multi-arch arm64+amd64) |
| Node.js | 24.x | For merchant-portal (Phase 3) |

## Backend (SOFAStack / Alibaba OSS)

| Component | Version | Source | Notes |
|---|---|---|---|
| SOFABoot (`sofaboot-dependencies`) | **4.6.0** | [sofastack/sofa-boot](https://github.com/sofastack/sofa-boot/releases) | **Imported** BOM (was the parent until 2026-10-16). Spring Boot 3.x line, JDK 17+. Still latest release as of 2026-10-16 |
| Spring Boot (`spring-boot-starter-parent`) | **3.5.16** | Maven Central | **Parent of this repo** since 2026-10-16, so Boot can be patched past SOFABoot 4.6.0's 3.5.6 pin (3.5.6 carried open Tomcat/Spring/Netty/Jackson advisories). Latest 3.5.x; Boot 4 is not supported by SOFABoot 4.x. See *Build note* for why BOM order matters |
| SOFARPC starter (`rpc-sofa-boot-starter`) | **4.6.0** (BOM-managed `${sofa.boot.version}`, wraps `sofa-rpc-all` **5.13.4** — deliberately not 5.14.x, see Security overrides) | [sofastack/sofa-boot](https://github.com/sofastack/sofa-boot/tree/v4.6.0/sofa-boot-project/sofa-boot-starters/rpc-sofa-boot-starter) | ⚠️ Trap: Central's `maven-metadata.xml` for this artifactId lists only 5.5.3/6.0.x and calls 6.0.4 `<latest>` — those are **SOFABoot 3 / Spring Boot 2 / JDK 8** artifacts (parent `sofaboot-dependencies:3.1.4`) whose transitive `infra-sofa-boot-starter:3.1.4` + `log-sofa-boot-starter:1.0.18` NoClassDefFoundError against `sofa-common-tools` 2.x. Never pin this artifact; let the SOFABoot BOM resolve it |
| SOFABolt | **1.6.12** | overridden (BOM: 1.6.10) | RPC transport |
| SOFA Hessian | **3.5.6** | overridden (BOM: 3.5.5) | Bolt payload serialization (blacklist-based deserialization filter) |
| SOFATracer (`tracer-core`) | 4.0.2 | managed by SOFABoot BOM | OTLP bridge wired in Phase 1+ |
| SOFARegistry **server** | **6.1.9** (`registry-all.tgz`) | [sofastack/sofa-registry](https://github.com/sofastack/sofa-registry/releases/tag/v6.1.9) | Runs on JDK 8 in-container (`eclipse-temurin:8-jre-noble`, image digest-pinned); integration/dev mode with embedded H2. Still latest release as of 2026-10-16. Base image re-pinned (8u504); tarball pinned with `ADD --checksum` |
| SOFARegistry **client** (`registry-client-all`) | **6.5.6** | Maven Central | ⚠️ SOFABoot BOM pins 6.1.8 which is **absent from Central**. Overridden via `sofa.registry.version`; bumped 6.3.0 → 6.5.6 (2026-07-21, latest non-classifier release — skips the `6.5.6.20241001` dated variant); v6 wire protocol compatible with 6.1.9 server; full `mvn verify` + compose e2e re-run green after the bump. See ADR-0001 |
| Seata | **2.6.0** (`org.apache.seata`) | [apache/incubator-seata](https://github.com/apache/incubator-seata/releases) | Server image `apache/seata-server:2.6.0.jdk17`, digest-pinned. GroupId changed from `io.seata` at 2.1.0. Wired in Phase 2. Still latest release as of 2026-10-16 (image digest unchanged) |
| OceanBase CE | **4.3.5-lts** (image `oceanbase/oceanbase-ce:4.3.5-lts`, build `4.3.5.6`, digest-pinned) | [Docker Hub](https://hub.docker.com/r/oceanbase/oceanbase-ce/tags) | LTS line; arm64 image available. 4.4.2-lts exists (newer LTS line, released 2026-03-05) but 4.3.5 is deliberately kept — it's the line supported broadly by ob-operator/ApsaraDB; bumping the major LTS line is a separate decision, not a routine dependency update. MySQL-mode tenant, port 2881 |
| ob-operator (Phase 4, GKE) | **2.3.4** | [oceanbase/ob-operator](https://github.com/oceanbase/ob-operator/releases) | |
| MySQL driver (`mysql-connector-j`) | managed by Spring Boot BOM | — | OceanBase MySQL mode is compatible with 8.x/9.x connectors; override only if OB rejects it |
| MOSN (Phase 5 stretch) | pin when Phase 5 starts | [mosn/mosn](https://github.com/mosn/mosn) | Deliberately unpinned until then |

## Frontend (Phase 3)

| Component | Version | Notes |
|---|---|---|
| Ant Design | **5.29.3** | Spec mandates antd 5 (antd 6 exists — not used) |
| Umi | **@umijs/max 4.6.72** | Includes dva-style model plugin (not enabled in the portal) |
| React / React DOM | **18.3.1** | Umi 4 targets React 18 |
| TypeScript | **5.9.3** (dev) | `npm run typecheck`; `@types/react` 18.3.31, `@types/react-dom` 18.3.7 |
| Fontsource (`inter`, `source-serif-4`, `jetbrains-mono`) | **5.3.0** | Self-hosted OFL stand-ins for the ACME design system's faces |

Every package is pinned exactly in `merchant-portal/package.json`, and `package-lock.json` was
resolved with `npm install --before=2026-10-03`, so no transitive version is newer than a
two-week cooloff. `npm audit` (2026-10-17) reports 91 advisories, all in Umi 4.6.72's build
toolchain (webpack/vite/esbuild bundlers, eslint, jest, and the dva/pro-layout plugins the
portal does not enable). The browser bundle is react, react-dom, antd and Umi's router
runtime. `npm audit fix` only offers a bogus downgrade to
`@umijs/max` 4.0.7. Re-check when bumping Umi (4.7.x exists).

## Quality / Ops

| Component | Version | Notes |
|---|---|---|
| JUnit | 5.x (Spring Boot BOM) | |
| Spotless (`spotless-maven-plugin`) | **3.10.4** | 3.10.3 → 3.10.4 (2026-10-16, published 2026-10-08). Bumped 2.44.5 → 3.8.0 (2026-07-21), 3.8.0 → 3.9.0 (2026-08-08, published 2026-07-27, clears 24h cooloff); requires Java 17+ (already the baseline here). Bundled formatter version moved too — one file needed a re-wrap, fixed via `mvn spotless:apply` and committed. palantir-java-format; `mvn spotless:apply` to fix. `mvn verify` green, no reformatting needed for the 3.9.0 bump. 3.10.2 → 3.10.3 (2026-09-27, published 2026-09-25, clears 24h cooloff): bundled formatter re-wrapped one test (`RiskCheckServiceTest`), fixed via `mvn spotless:apply`; `mvn verify` green |
| k6 | **v2.3.0** (`grafana/k6`, digest-pinned in `scripts/run-k6.sh`) | Phase 2 latency proof |
| Syft / Grype / Cosign | pin in Phase 4 workflow | DevSecOps stage |
| GitHub Actions (`actions/checkout`, `actions/setup-java`) | **v7.0.1**, **v6.1.0** | 2026-10-16: setup-java v6.0.1 → v6.1.0 (published 2026-10-14); workflow now runs with `permissions: contents: read` and `persist-credentials: false`. Earlier: Bumped from v4/v4 (2026-07-21); pinned to commit SHA in ci.yml with the tag as a trailing comment. 2026-08-03: corrected checkout's SHA (previously mislabeled — the committed hash actually resolved to v7.0.0, not v7.0.1) and bumped setup-java v5.6.0→v5.7.0 (its previous SHA also resolved to the wrong tag, v5.2.0). Re-verified 2026-08-08: still the newest cooloff-eligible release of each, SHAs unchanged |

## Security overrides (2026-10-16)

Pinned in the root `pom.xml` because the BOM-managed version had open advisories (OSV scan of the
full resolved tree). Re-check each on the next refresh and drop it once the BOM catches up.

| Artifact | Override | BOM default | Why |
|---|---|---|---|
| Netty (`netty.version` + `netty-bom` import) | 4.1.139.Final | 4.1.135 (Boot) | HTTP/2, codec, TLS SNI advisories fixed in 4.1.136/137 |
| Tomcat (`tomcat.version`) | 10.1.61 | 10.1.55 (Boot) | Critical authz/digest-auth advisories fixed in 10.1.58 |
| Jackson (`jackson-bom.version` + import) | 2.21.7 | 2.21.4 (Boot) | databind/core DoS + PTV bypasses fixed in 2.21.5–2.21.7 |
| Log4j API (`log4j2.version` + import) | 2.26.1 | 2.24.3 (Boot) | GHSA-qv9r-c865-cp47 |
| OpenTelemetry (`opentelemetry.version` + import) | 1.67.0 | 1.49.0 (Boot) | Baggage propagation memory DoS, fixed 1.62.0 |
| commons-lang3 | 3.21.0 | 3.17.0 (Boot) | Uncontrolled recursion, fixed 3.18.0 |
| fastjson (`com.alibaba:fastjson`) | **2.0.65** (fastjson2 1.x-API compat artifact) | 1.2.83 (seata-all) | CVE-2026-16723: default-config RCE in 1.2.68–1.2.83, no 1.x fix. Seata's AT undo executor calls the `com.alibaba.fastjson` API, so it is replaced, not excluded |
| gRPC (`grpc-bom`) | 1.85.0 | 1.51.1 (SOFABoot) | HTTP/2 MadeYouReset + reachable assertion (sofa-rpc-all's Triple stack; unused at runtime here) |
| protobuf (`protobuf-bom`) | 3.25.9 | 3.22.2 (SOFABoot) | DoS fixed 3.25.5; stays on 3.x — Seata's gencode is 3.x |
| Guava | 33.7.2-jre | 31.1-jre (SOFABoot) | temp-dir advisories |
| commons-io | 2.22.0 | 2.4 (SOFABoot) | XmlStreamReader DoS / path traversal |
| RESTEasy 3.x (client, jaxrs, jackson2-provider, netty4) | 3.15.6.Final | 3.6.3.Final (SOFABoot) | sofa-rpc-all's REST stack; unused at runtime here |

**Not taken:** `sofa-rpc-all` 5.14.3 (latest). 5.14.x only adds Triple fixes and Apache Fory
serialization, and pulls `fory-core` 1.0.0 — CVE-2026-50076, critical deserialization bypass.
A bolt server selects the deserializer from the request's codec byte, so any network peer could
reach it. Stay on the BOM's 5.13.4 until a 5.14.x ships a fixed fory-core (≥1.1.0).

**Accepted residuals:** two spring-webmvc advisories (GHSA-j9f9-w8pj-32f8 SSE fragment
rendering, GHSA-pc63-qcmh-9cmg XsltView) are fixed only in Spring Framework 7.0.9, unreachable
on Boot 3.5; PayLab uses neither feature. OSV also flags `rpc-sofa-boot-starter:4.6.0` with
GHSA-7q8p-9953-pxvr (CVE-2024-23636) — a false positive from the starter/core version-number
overlap; the fix shipped in sofa-rpc 5.12.0 and the resolved core is 5.13.4.

## Build note — resolved BOM chain

`io.paylab:paylab-parent` → `org.springframework.boot:spring-boot-starter-parent:3.5.16`, with
`sofaboot-dependencies:4.6.0` **imported**. ⚠️ Import order matters: `sofaboot-dependencies`
carries Spring Boot 3.5.6's *nested* BOM imports (spring-framework, netty, jackson, micrometer,
spring-data, …), and Maven lets an import declared in this POM beat the imports inherited from
the parent — so without the explicit `netty-bom`/`jackson-bom`/`log4j-bom`/`opentelemetry-bom`/
`spring-boot-dependencies:3.5.16` imports placed *before* it, those families silently resolve to
3.5.6-era versions (only directly-declared entries such as Tomcat follow the parent). For the same
reason, SOFABoot BOM properties (`sofa.registry.version`, `guava.version`, …) cannot be overridden
by property any more — override them with explicit `dependencyManagement` entries.
Run `mvn -pl services/payment-gateway dependency:tree -Dincludes=org.springframework.boot` to
print the exact resolved Spring Boot version locally.
