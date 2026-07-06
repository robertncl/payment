#!/usr/bin/env bash
# Waits until all compose services report healthy. Usage: wait-healthy.sh [expected_count] [timeout_s]
set -uo pipefail
EXPECTED=${1:-7}
TIMEOUT=${2:-600}
START=$(date +%s)

while true; do
  STATUS=$(docker compose ps --format '{{.Service}} {{.Health}}' 2>/dev/null)
  HEALTHY=$(echo "$STATUS" | grep -c healthy || true)
  if [ "$HEALTHY" -ge "$EXPECTED" ]; then
    echo "all $HEALTHY services healthy"
    exit 0
  fi
  if [ $(($(date +%s) - START)) -gt "$TIMEOUT" ]; then
    echo "TIMEOUT waiting for health ($HEALTHY/$EXPECTED):"
    docker compose ps
    exit 1
  fi
  sleep 5
done
