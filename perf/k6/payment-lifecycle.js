// Phase 2 latency gate: create -> capture lifecycle under steady load, thresholds from
// slo/slo.md. Run via scripts/run-k6.sh (joins the compose network, targets the gateway).
//
// Payers are unique per iteration ON PURPOSE: the risk velocity rule declines a payer with
// >10 approvals/minute, which would poison latency numbers with 201-but-declined creates.
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  scenarios: {
    // JIT/plan-cache warm-up: same flow, separate tags so it never touches the thresholds
    warmup: {
      executor: 'constant-arrival-rate',
      rate: 5,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 10,
      maxVUs: 20,
      env: { PHASE: 'warmup' },
    },
    lifecycle: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 10), // payments/second
      timeUnit: '1s',
      duration: __ENV.DURATION || '2m',
      startTime: '30s',
      preAllocatedVUs: 30,
      maxVUs: 100,
    },
  },
  thresholds: {
    // SLO targets (slo/slo.md): create = 1 RPC hop (risk), capture = 2 hops + Seata AT
    'http_req_duration{name:create}': ['p(95)<300', 'p(99)<600'],
    'http_req_duration{name:capture}': ['p(95)<800', 'p(99)<1500'],
    checks: ['rate>0.99'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const phase = __ENV.PHASE === 'warmup' ? 'warmup-' : '';
  const uid = `${__VU}-${__ITER}-${Date.now()}`;
  const params = (name, key) => ({
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': `k6-${phase}${name}-${uid}-${key}`,
    },
    tags: { name: `${phase}${name}` },
  });

  const created = http.post(
    `${BASE}/api/payments`,
    JSON.stringify({
      payerId: `payer-k6-${uid}`,
      merchantId: `merchant-k6-${__VU % 10}`,
      sourceCurrency: 'SGD',
      targetCurrency: 'MYR',
      amount: 100.0,
    }),
    params('create', 'c'),
  );

  const createOk = check(created, {
    'create 201': (r) => r.status === 201,
    'create approved': (r) => r.json('status') === 'RISK_APPROVED',
  });
  if (!createOk) return;

  const captured = http.post(
    `${BASE}/api/payments/${created.json('id')}/capture`,
    null,
    params('capture', 'x'),
  );

  check(captured, {
    'capture 200': (r) => r.status === 200,
    'capture status CAPTURED': (r) => r.json('status') === 'CAPTURED',
  });
}
