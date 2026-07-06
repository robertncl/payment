# ADR-0001: Service registry — SOFARegistry, run in dev/integration mode

- **Status:** Accepted
- **Date:** 2026-07-06
- **Phase:** 0

## Context

The stack mandate is SOFARegistry, with Nacos as the sanctioned fallback if SOFARegistry
"proves impractical". Concerns evaluated:

1. **No official Docker image.** SOFARegistry ships only a `registry-all.tgz` release
   artifact; the docs describe tarball deployment (`bin/start_dev.sh` for dev with embedded
   H2, `bin/integration/start.sh` + MySQL for production).
2. **Server runs on JDK 8**, while our services are JDK 17. These are separate processes, so
   this only constrains the registry container's base image, not the services.
3. **Client/BOM version gap.** SOFABoot 4.6.0's BOM pins `registry-client-all:6.1.8`, which
   was never published to Maven Central (published: ≤5.4.3, then 6.3.0, 6.5.6). The v6 wire
   protocol is compatible across the line.
4. Host is **aarch64**; the registry is pure Java + scripts, so a Temurin arm64 base works.

## Decision

Use **SOFARegistry 6.1.9** as the service registry, packaged in a first-party Docker image
(`deploy/docker/sofa-registry/Dockerfile`):

- Base `eclipse-temurin:8-jre-noble`, `registry-all.tgz` fetched from the official GitHub
  release at build time.
- **Integration (all-roles-in-one) dev mode** with the embedded H2 meta store — one
  container, no external MySQL. Session port 9603, meta 9615, data 9622; health via
  `GET /health/check` on each.
- Services depend on `registry-client-all` **6.3.0** (override of the BOM's phantom 6.1.8)
  and register via SOFARPC's `sofa://<host>:9603` registry protocol.

## Fallback triggers (would flip us to Nacos, in a superseding ADR)

- Registry client 6.3.0 cannot register/subscribe against server 6.1.9 (wire incompatibility).
- Registry server unstable on arm64 (e.g. bundled rocksdb JNI lacks aarch64 support).
- Dev-mode (H2) registry loses registrations in a way that breaks the local dev loop.

Phase 0 gate verification exercises exactly these risks; none fired.

## Consequences

- Prod-grade SOFARegistry (3-role cluster + MySQL meta store) is intentionally out of MVP
  scope; the k8s deployment (Phase 4) reuses the same single-container dev-mode image. Noted
  as accepted debt.
- We own the registry image build (arm64+amd64) in CI from Phase 4 on.
