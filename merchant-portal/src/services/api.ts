// Thin client for the payment-gateway REST API (the portal's only backend). Mutations always
// send an Idempotency-Key; callers own the key so a retry can reuse it.

export type PaymentStatus =
  | 'CREATED'
  | 'RISK_APPROVED'
  | 'RISK_DECLINED'
  | 'CAPTURED'
  | 'SETTLED'
  | 'FAILED'
  | 'REFUNDED';

/**
 * Money fields arrive as JSON numbers (Jackson BigDecimal, DECIMAL(20,4)). They are only ever
 * displayed here, never summed into new amounts, so number precision is enough for the lab.
 */
export interface Payment {
  id: string;
  status: PaymentStatus;
  payerId: string;
  merchantId: string;
  sourceCurrency: string;
  targetCurrency: string;
  amount: number;
  feeAmount: number;
  targetAmount: number | null;
  fxRate: number | null;
  fxQuoteId: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PaymentEvent {
  fromStatus: string | null;
  toStatus: string;
  detail: string | null;
  occurredAt: string;
}

export interface TrialBalanceLine {
  accountId: string;
  accountType: string;
  currency: string;
  debitTotal: number;
  creditTotal: number;
  net: number;
}

export interface TrialBalanceReport {
  computedAt: string;
  lines: TrialBalanceLine[];
  netByCurrency: Record<string, number>;
  balanced: boolean;
}

export interface CreatePaymentRequest {
  payerId: string;
  merchantId: string;
  sourceCurrency: string;
  targetCurrency: string;
  /** Sent as the typed decimal string so no float rounding happens before the gateway. */
  amount: string;
}

export interface Mutation<T> {
  body: T;
  /** X-Idempotent-Replay: the gateway returned the stored response of an earlier call. */
  replayed: boolean;
  traceId: string | null;
}

/** The gateway's ApiError body plus the HTTP status, thrown for every non-2xx response. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly traceId: string | null,
  ) {
    super(message);
  }
}

export const MERCHANT_ID = 'merchant-1';
export const CURRENCIES = ['SGD', 'MYR', 'USD', 'EUR', 'CNY'] as const;
export const CORRIDOR_CAP = 10000;

export function newIdempotencyKey(): string {
  return 'portal-' + crypto.randomUUID();
}

async function call<T>(path: string, init?: RequestInit): Promise<Mutation<T>> {
  const res = await fetch('/api' + path, {
    ...init,
    headers: { Accept: 'application/json', ...init?.headers },
  });
  const traceId = res.headers.get('X-PayLab-Trace-Id');
  const text = await res.text();
  const json = text ? JSON.parse(text) : null;
  if (!res.ok) {
    throw new ApiError(
      res.status,
      json?.error ?? 'http_' + res.status,
      json?.message ?? res.statusText,
      json?.traceId ?? traceId,
    );
  }
  return { body: json as T, replayed: res.headers.get('X-Idempotent-Replay') === 'true', traceId };
}

const get = async <T>(path: string) => (await call<T>(path)).body;

const mutate = <T>(path: string, key: string, body?: unknown) =>
  call<T>(path, {
    method: 'POST',
    headers: {
      'Idempotency-Key': key,
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

export const api = {
  listPayments: (limit = 50) =>
    get<Payment[]>(`/payments?merchantId=${encodeURIComponent(MERCHANT_ID)}&limit=${limit}`),
  getPayment: (id: string) => get<Payment>(`/payments/${encodeURIComponent(id)}`),
  events: (id: string) => get<PaymentEvent[]>(`/payments/${encodeURIComponent(id)}/events`),
  trialBalance: () => get<TrialBalanceReport>('/trial-balance'),
  create: (req: CreatePaymentRequest, key: string) => mutate<Payment>('/payments', key, req),
  capture: (id: string, key: string) =>
    mutate<Payment>(`/payments/${encodeURIComponent(id)}/capture`, key),
  refund: (id: string, key: string) =>
    mutate<Payment>(`/payments/${encodeURIComponent(id)}/refund`, key),
};
