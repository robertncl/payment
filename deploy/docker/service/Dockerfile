# syntax=docker/dockerfile:1
# Shared multi-stage build for every PayLab SOFABoot service; select with --build-arg MODULE=<name>.
FROM maven:3.9-eclipse-temurin-17 AS build
ARG MODULE
WORKDIR /src
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -pl services/${MODULE} -am package -DskipTests -Dspotless.check.skip=true

FROM eclipse-temurin:17-jre-noble
ARG MODULE

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --uid 10001 paylab

COPY --from=build /src/services/${MODULE}/target/${MODULE}-*.jar /app/app.jar

# add-opens: hessian/bolt reflective serialization on JDK 17
ENV JAVA_OPTS="-Xms256m -Xmx384m \
    --add-opens java.base/java.lang=ALL-UNNAMED \
    --add-opens java.base/java.util=ALL-UNNAMED \
    --add-opens java.base/java.math=ALL-UNNAMED \
    --add-opens java.base/sun.security.action=ALL-UNNAMED"
ENV HEALTH_PORT=8080

USER paylab
HEALTHCHECK --interval=10s --timeout=5s --start-period=90s --retries=12 \
  CMD curl -sf http://localhost:${HEALTH_PORT}/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["/bin/bash", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
