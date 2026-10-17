import type { Payment, PaymentStatus } from './api';

export type BadgeVariant = 'neutral' | 'success' | 'warning' | 'danger' | 'info';

/** Display label + badge per lifecycle state. Labels carry the meaning; colour only backs it. */
export const STATUS: Record<PaymentStatus, { label: string; badge: BadgeVariant }> = {
  CREATED: { label: 'Created', badge: 'neutral' },
  RISK_APPROVED: { label: 'Risk approved', badge: 'info' },
  RISK_DECLINED: { label: 'Declined', badge: 'danger' },
  CAPTURED: { label: 'Captured', badge: 'success' },
  SETTLED: { label: 'Settled', badge: 'success' },
  FAILED: { label: 'Failed', badge: 'danger' },
  REFUNDED: { label: 'Refunded', badge: 'neutral' },
};

const fmt = (dp: number) =>
  new Intl.NumberFormat('en-US', { minimumFractionDigits: dp, maximumFractionDigits: dp });
const fmt2 = fmt(2);
const fmt4 = fmt(4);

/** Ledger precision (DECIMAL(20,4)); negatives use a true minus sign. */
export const money4 = (n: number) => (n < 0 ? '−' : '') + fmt4.format(Math.abs(n));
/** List precision; the ledger keeps 4 places. */
export const money2 = (n: number) => fmt2.format(n);

export const shortId = (id: string) => id.slice(0, 13);

const dateTime = new Intl.DateTimeFormat('en-US', {
  month: 'short',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});
const dateTimeSec = new Intl.DateTimeFormat('en-US', {
  month: 'short',
  day: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});
const timeSec = new Intl.DateTimeFormat('en-US', {
  hour: '2-digit',
  minute: '2-digit',
  second: '2-digit',
  hourCycle: 'h23',
});

export const at = (iso: string) => dateTime.format(new Date(iso));
export const atSec = (iso: string) => dateTimeSec.format(new Date(iso));
export const timeOf = (iso: string) => timeSec.format(new Date(iso));

export const isToday = (iso: string) => new Date(iso).toDateString() === new Date().toDateString();

export interface Leg {
  n: number;
  account: string;
  currency: string;
  debit: number | null;
  credit: number | null;
}

/**
 * The capture journal entry as docs/payment-flow.md defines it: five legs that net to zero per
 * currency. The gateway exposes no per-payment ledger read, so the portal derives the legs from
 * the payment's own amounts; the trial balance is the ledger-backed proof.
 */
export function captureLegs(p: Payment): Leg[] {
  const target = p.targetAmount ?? 0;
  return [
    { n: 1, account: `payer:${p.payerId}`, currency: p.sourceCurrency, debit: p.amount + p.feeAmount, credit: null },
    { n: 2, account: 'house:fee_revenue', currency: p.sourceCurrency, debit: null, credit: p.feeAmount },
    { n: 3, account: 'house:fx_pnl', currency: p.sourceCurrency, debit: null, credit: p.amount },
    { n: 4, account: 'house:fx_pnl', currency: p.targetCurrency, debit: target, credit: null },
    { n: 5, account: 'house:settlement_clearing', currency: p.targetCurrency, debit: null, credit: target },
  ];
}

/** The refund entry reposts the capture legs with every direction flipped. */
export const refundLegs = (p: Payment): Leg[] =>
  captureLegs(p).map((l) => ({ ...l, debit: l.credit, credit: l.debit }));

export const FEE_RATE = 0.01;
