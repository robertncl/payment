#!/usr/bin/env bash
# Phase 0 gate verification: OceanBase up + all five SOFABoot services registered in SOFARegistry.
set -uo pipefail

PASS=0
FAIL=0
ok()   { echo "  PASS  $1"; PASS=$((PASS + 1)); }
bad()  { echo "  FAIL  $1"; FAIL=$((FAIL + 1)); }

echo "== 1. SOFARegistry integration node health =="
for role in "session 9603" "meta 9615" "data 9622"; do
  set -- $role
  if curl -sf "http://localhost:$2/health/check" | grep -q '"success":true'; then
    ok "registry $1 ($2) healthy"
  else
    bad "registry $1 ($2) health check"
  fi
done

echo "== 2. OceanBase tenant reachable + canary row =="
if docker compose exec -T oceanbase obclient -h127.0.0.1 -P2881 "-uroot@paylab" \
    "-p${OB_TENANT_PASSWORD:-paylab-dev}" -Dpaylab -e "SELECT note FROM phase0_canary WHERE id=1;" 2>/dev/null \
    | grep -q "oceanbase bootstrapped"; then
  ok "oceanbase paylab tenant + init.d canary row"
else
  bad "oceanbase paylab tenant / canary row"
fi

echo "== 3. Service health endpoints =="
for svc in "payment-gateway 8080" "risk-service 8081" "fx-service 8082" "ledger-service 8083" "settlement-recon-job 8084"; do
  set -- $svc
  if curl -sf "http://localhost:$2/actuator/health" | grep -q '"status":"UP"'; then
    ok "$1 UP ($2)"
  else
    bad "$1 health ($2)"
  fi
done

echo "== 4. PingFacade publishers visible in SOFARegistry (the actual registration proof) =="
DIGEST=$(curl -sf "http://localhost:9603/digest/getDataInfoIdList" 2>/dev/null)
COUNT=$(echo "$DIGEST" | grep -o "io.paylab.api.PingFacade" | wc -l)
if [ "${COUNT:-0}" -ge 1 ]; then
  ok "PingFacade dataInfoId present in session digest"
  DATA_INFO_ID=$(echo "$DIGEST" | tr ',"[]' '\n\n\n\n' | grep "io.paylab.api.PingFacade" | head -1)
  PUBS=$(curl -sf "http://localhost:9603/digest/pub/data/query?dataInfoId=${DATA_INFO_ID//#/%23}")
  NPUB=$(echo "$PUBS" | grep -o '"sourceAddress"' | wc -l)
  echo "  info: dataInfoId=${DATA_INFO_ID} publishers=${NPUB}"
  if [ "${NPUB:-0}" -ge 5 ]; then
    ok "all 5 services publish PingFacade (publishers=$NPUB)"
  else
    bad "expected 5 publishers, got ${NPUB:-0}"
  fi
else
  bad "PingFacade not found in session digest"
fi

echo
echo "Phase 0 gate: PASS=$PASS FAIL=$FAIL"
[ "$FAIL" -eq 0 ]
