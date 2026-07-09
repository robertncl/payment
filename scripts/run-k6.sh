#!/usr/bin/env bash
# Phase 2 latency gate: run the k6 lifecycle scenario against the compose stack.
# Usage: ./scripts/run-k6.sh [rate] [duration]   (defaults: 10 rps, 2m)
# Results land in perf/results/ as JSON summaries; thresholds fail the exit code.
set -euo pipefail

cd "$(dirname "$0")/.."

RATE="${1:-10}"
DURATION="${2:-2m}"
STAMP="$(date +%Y%m%d-%H%M%S)"
NETWORK="${PAYLAB_COMPOSE_NETWORK:-paylab_default}"
K6_IMAGE="grafana/k6:2.1.0"

mkdir -p perf/results

# k6 joins the compose network and talks to the gateway by service name — no host networking
# assumptions (Docker Desktop / WSL2 friendly).
docker run --rm \
  --network "${NETWORK}" \
  -v "$(pwd)/perf/k6:/scripts:ro" \
  -v "$(pwd)/perf/results:/results" \
  -e BASE_URL=http://payment-gateway:8080 \
  -e RATE="${RATE}" \
  -e DURATION="${DURATION}" \
  "${K6_IMAGE}" run \
  --summary-export "/results/k6-${STAMP}.json" \
  /scripts/payment-lifecycle.js

echo "summary: perf/results/k6-${STAMP}.json"
