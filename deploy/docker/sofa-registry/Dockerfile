# syntax=docker/dockerfile:1
# SOFARegistry has no official Docker image (ADR-0001): package the official release tarball.
# Integration mode = meta + data + session roles in one JVM; dev profile = embedded H2 meta store.
# The server targets JDK 8 (start_base.sh uses CMS flags); services stay on 17 — separate processes.
FROM eclipse-temurin:8-jre-noble@sha256:2d800a598bbc502237de30e9d89284d3a5dccc6b3004f948d4b2b22960daf25d

ARG REGISTRY_VERSION=6.1.9

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# The release has no published digest; the checksum pins the tarball as first fetched
# (2026-10-16) so a later swap of the release asset fails the build. Re-pin when bumping.
ADD --checksum=sha256:98bcb605d5d1bcba9af294c9d6ffb63b2526ffb2a61ea7280418b11d92524af5 https://github.com/sofastack/sofa-registry/releases/download/v${REGISTRY_VERSION}/registry-all.tgz /tmp/registry-all.tgz
RUN tar xzf /tmp/registry-all.tgz -C / && rm /tmp/registry-all.tgz

WORKDIR /registry-all

# start_dev.sh hardcodes a 4.9g heap sized for MEM=8192; we exec the same jar with a dev-sized
# heap and without the JDK8-CMS tuning, keeping profile/config/logging identical to the script.
ENV REGISTRY_HEAP_MB=768

# 9600 session bolt (client data connection), 9603 session http (lookup/ops), 9615 meta, 9622 data
EXPOSE 9600 9603 9615 9622

HEALTHCHECK --interval=10s --timeout=5s --start-period=120s --retries=18 \
  CMD curl -sf http://localhost:9603/health/check || exit 1

CMD ["/bin/bash", "-c", "exec java -server -Xms${REGISTRY_HEAP_MB}m -Xmx${REGISTRY_HEAP_MB}m -XX:MaxMetaspaceSize=256m \
    -Dspring.profiles.active=dev \
    -Dspring.config.additional-location=/registry-all/conf/ \
    -Dlogging.config=/registry-all/log-conf/integration/log4j2.xml \
    -Dlog4j.configurationFile=/registry-all/log-conf/integration/log4j2.xml \
    -jar registry-integration.jar"]
