import StatusBadge, { StatusIcon } from '@/components/StatusBadge';
import {
  api,
  ApiError,
  newIdempotencyKey,
  type Payment,
  type PaymentEvent,
  type PaymentStatus,
} from '@/services/api';
import {
  atSec,
  captureLegs,
  money2,
  money4,
  refundLegs,
  STATUS,
  timeOf,
  type Leg,
} from '@/services/format';
import { Link, useParams } from '@umijs/max';
import { Alert, App, Button, Card, Modal, Table, type TableColumnsType } from 'antd';
import { useCallback, useEffect, useState } from 'react';

const QUOTE_TTL_MS = 60_000;

interface Step {
  code: PaymentStatus;
  label: string;
  hint: string;
}

/** The happy path, with the terminal branch swapped in when the payment left it. */
function stepsFor(status: PaymentStatus): Step[] {
  const created: Step = { code: 'CREATED', label: 'Created', hint: 'Idempotency key claimed' };
  if (status === 'RISK_DECLINED') {
    return [created, { code: 'RISK_DECLINED', label: 'Declined', hint: 'Final: no money moves' }];
  }
  const approved: Step = {
    code: 'RISK_APPROVED',
    label: 'Risk approved',
    hint: 'Denylist, corridor cap, velocity passed',
  };
  const captured: Step = { code: 'CAPTURED', label: 'Captured', hint: 'Locks FX, posts the ledger' };
  if (status === 'FAILED') {
    return [created, approved, { code: 'FAILED', label: 'Failed', hint: 'Capture did not complete' }];
  }
  const last: Step =
    status === 'REFUNDED'
      ? { code: 'REFUNDED', label: 'Refunded', hint: 'Reversing entry posted' }
      : { code: 'SETTLED', label: 'Settled', hint: 'End-of-day sweep' };
  return [created, approved, captured, last];
}

const legColumns: TableColumnsType<Leg> = [
  { title: 'Leg', dataIndex: 'n', align: 'right', width: 56 },
  { title: 'Account', dataIndex: 'account', render: (v) => <span className="pl-mono">{v}</span> },
  { title: 'Ccy', dataIndex: 'currency', width: 72 },
  { title: 'Debit', dataIndex: 'debit', align: 'right', render: (v) => (v == null ? '—' : money4(v)) },
  { title: 'Credit', dataIndex: 'credit', align: 'right', render: (v) => (v == null ? '—' : money4(v)) },
];

export default function PaymentDetail() {
  const { id = '' } = useParams();
  const { message } = App.useApp();
  const [payment, setPayment] = useState<Payment | null>(null);
  const [events, setEvents] = useState<PaymentEvent[]>([]);
  const [error, setError] = useState<ApiError | null>(null);
  const [busy, setBusy] = useState(false);
  const [refundOpen, setRefundOpen] = useState(false);
  const [traceId, setTraceId] = useState<string | null>(null);
  // One key per intended action: a retry after a dropped response reuses it (and is replayed),
  // a fresh action after success gets a new one.
  const [actionKey, setActionKey] = useState(newIdempotencyKey);

  const load = useCallback(async () => {
    try {
      const [p, ev] = await Promise.all([api.getPayment(id), api.events(id)]);
      setPayment(p);
      setEvents(ev);
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError(0, 'network', 'The gateway did not respond.', null));
    }
  }, [id]);

  useEffect(() => {
    load();
  }, [load]);

  const run = async (action: 'capture' | 'refund') => {
    setBusy(true);
    try {
      const res = await api[action](id, actionKey);
      setPayment(res.body);
      setTraceId(res.traceId);
      setActionKey(newIdempotencyKey());
      setRefundOpen(false);
      message.success(action === 'capture' ? 'Captured.' : 'Refunded.');
      setEvents(await api.events(id));
    } catch (e) {
      const why = e instanceof ApiError ? e.message : 'The gateway did not respond.';
      message.error(`${action === 'capture' ? 'Capture' : 'Refund'} failed: ${why} Nothing was posted.`);
    } finally {
      setBusy(false);
    }
  };

  if (error) {
    return (
      <main className="pl-main">
        <Link to="/payments" className="pl-back">
          Back to payments
        </Link>
        <Alert
          type="error"
          showIcon
          message={error.status === 404 ? 'Payment not found' : 'Couldn’t load this payment'}
          description={
            error.status === 404
              ? `No payment has the ID ${id}. Check the link, or find it in the payments list.`
              : `${error.message} Check that the gateway is running, then retry.`
          }
          action={error.status === 404 ? undefined : <Button onClick={load}>Retry</Button>}
        />
      </main>
    );
  }
  if (!payment) {
    return (
      <main className="pl-main" aria-busy="true">
        <p className="pl-lede">Loading payment…</p>
      </main>
    );
  }

  const p = payment;
  const st = STATUS[p.status];
  const eventFor = (code: string) => events.find((e) => e.toStatus === code);
  const declineEvent = eventFor('RISK_DECLINED');
  const capturedAt = eventFor('CAPTURED')?.occurredAt;
  const refundedAt = eventFor('REFUNDED')?.occurredAt;
  const captured = p.targetAmount != null && ['CAPTURED', 'SETTLED', 'REFUNDED'].includes(p.status);
  const debit = p.amount + p.feeAmount;

  const steps = stepsFor(p.status);
  const currentIdx = steps.findIndex((s) => s.code === p.status);

  const entries: { type: string; at?: string; legs: Leg[] }[] = [];
  if (captured) entries.push({ type: 'Capture', at: capturedAt, legs: captureLegs(p) });
  if (p.status === 'REFUNDED') entries.push({ type: 'Refund', at: refundedAt, legs: refundLegs(p) });

  return (
    <main className="pl-main">
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <Link to="/payments" className="pl-back">
          Back to payments
        </Link>
        <div className="pl-page-head">
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8, minWidth: 0 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
              <h1 className="pl-h1">
                {money2(p.amount)} {p.sourceCurrency} → {p.targetCurrency}
              </h1>
              <StatusBadge variant={st.badge}>{st.label}</StatusBadge>
            </div>
            <p className="pl-lede pl-mono" style={{ overflowWrap: 'anywhere' }}>
              {p.id}
            </p>
          </div>
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            {p.status === 'RISK_APPROVED' && (
              <Button type="primary" loading={busy} onClick={() => run('capture')}>
                Capture {money2(p.amount)} {p.sourceCurrency}
              </Button>
            )}
            {p.status === 'CAPTURED' && <Button onClick={() => setRefundOpen(true)}>Refund payment</Button>}
          </div>
        </div>
      </div>

      {p.status === 'RISK_APPROVED' && (
        <Alert
          type="info"
          showIcon
          message="Ready to capture"
          description="Capture locks a 60-second FX quote and posts the ledger entry. Both happen together or not at all."
        />
      )}
      {p.status === 'CAPTURED' && (
        <Alert
          type="success"
          showIcon
          icon={<StatusIcon variant="success" size={16} />}
          message="Captured"
          description={`${money2(p.targetAmount ?? 0)} ${p.targetCurrency} is in settlement clearing. The end-of-day sweep moves it to ${p.merchantId}’s payable.`}
        />
      )}
      {p.status === 'REFUNDED' && (
        <Alert
          type="info"
          showIcon
          message="Refunded"
          description={`A reversing entry returned ${money2(debit)} ${p.sourceCurrency} to ${p.payerId}. The original capture entry is unchanged.`}
        />
      )}
      {p.status === 'RISK_DECLINED' && (
        <Alert
          type="error"
          showIcon
          message="Declined by risk"
          description={
            <>
              <span className="pl-mono">{declineEvent?.detail ?? 'No reason recorded'}</span>. Declines are
              final: no money moved. Create a new payment to try again.
            </>
          }
        />
      )}

      <section aria-labelledby="lifecycle-title" style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <h2 id="lifecycle-title" className="pl-h2">
          Lifecycle
        </h2>
        <ol className="pl-steps">
          {steps.map((s, i) => {
            const reached = i <= currentIdx;
            const current = i === currentIdx;
            const ev = s.code === 'CREATED' ? undefined : eventFor(s.code);
            return (
              <li
                key={s.code}
                className={'pl-step' + (current ? ' pl-step--current' : reached ? ' pl-step--done' : '')}
                aria-current={current ? 'step' : undefined}
              >
                <span className="pl-step__label">
                  {reached && <StatusIcon variant="success" size={16} />}
                  {s.label}
                </span>
                <span className="pl-step__meta pl-mono">{s.code}</span>
                <span className="pl-step__meta">
                  {s.code === 'CREATED' ? atSec(p.createdAt) : ev ? atSec(ev.occurredAt) : s.hint}
                </span>
              </li>
            );
          })}
        </ol>
      </section>

      <div className="pl-split">
        <div className="pl-split__main">
          <Card title="Ledger entries">
            {entries.length === 0 ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <p style={{ margin: 0, fontWeight: 600 }}>No money has moved.</p>
                <p className="pl-lede" style={{ maxWidth: '60ch' }}>
                  Capture posts five legs: the payer’s debit, the 1% fee, the FX conversion through house
                  P&amp;L, and the converted amount into settlement clearing. They net to zero in each
                  currency.
                </p>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 24 }}>
                {entries.map((e) => (
                  <div key={e.type} style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                    <span
                      style={{
                        fontSize: 12,
                        fontWeight: 600,
                        letterSpacing: '0.05em',
                        textTransform: 'uppercase',
                        color: 'var(--acme-color-text-muted)',
                      }}
                    >
                      {e.type}
                      {e.at ? ` · ${atSec(e.at)}` : ''}
                    </span>
                    <Table<Leg>
                      rowKey="n"
                      size="small"
                      columns={legColumns}
                      dataSource={e.legs}
                      pagination={false}
                      scroll={{ x: 'max-content' }}
                    />
                    <p className="pl-note">
                      Nets to 0.0000 {p.sourceCurrency} and 0.0000 {p.targetCurrency}.
                    </p>
                  </div>
                ))}
                <p className="pl-note">
                  Legs are derived from the posting rules and this payment’s amounts. The{' '}
                  <Link to="/trial-balance">trial balance</Link> is the ledger’s own proof.
                </p>
              </div>
            )}
          </Card>

          <Card title="Timeline">
            {events.length === 0 ? (
              <p className="pl-lede">No transitions recorded yet.</p>
            ) : (
              <ol className="pl-timeline">
                {[...events].reverse().map((ev) => (
                  <li key={ev.occurredAt + ev.toStatus}>
                    <span className="pl-timeline__time">{timeOf(ev.occurredAt)}</span>
                    <div className="pl-timeline__body">
                      <span className="pl-mono" style={{ fontSize: 14, fontWeight: 600 }}>
                        {ev.fromStatus ?? '—'} → {ev.toStatus}
                      </span>
                      {ev.detail && (
                        <span
                          className="pl-mono"
                          style={{ fontSize: 12, color: 'var(--acme-color-text-muted)', overflowWrap: 'anywhere' }}
                        >
                          {ev.detail}
                        </span>
                      )}
                    </div>
                  </li>
                ))}
              </ol>
            )}
          </Card>
        </div>

        <aside aria-label="Payment facts" className="pl-split__aside">
          <Card title="Amounts">
            <dl className="pl-facts">
              <dt>Amount</dt>
              <dd>
                {money4(p.amount)} {p.sourceCurrency}
              </dd>
              <dt>Fee (1%)</dt>
              <dd>
                {money4(p.feeAmount)} {p.sourceCurrency}
              </dd>
              <dt>Payer debited</dt>
              <dd style={{ fontWeight: 600 }}>
                {money4(debit)} {p.sourceCurrency}
              </dd>
              <dt className="pl-facts__rule">Converted</dt>
              <dd className="pl-facts__rule" style={{ fontWeight: 600 }}>
                {p.targetAmount == null ? (
                  <span style={{ color: 'var(--acme-color-text-subtle)' }}>Set at capture</span>
                ) : (
                  `${money4(p.targetAmount)} ${p.targetCurrency}`
                )}
              </dd>
            </dl>
          </Card>

          <Card title="FX quote">
            {p.fxRate == null ? (
              <p className="pl-lede">
                No quote yet. Capture locks the current rate for 60 seconds, and the ledger posts at
                exactly that rate.
              </p>
            ) : (
              <dl className="pl-facts">
                <dt>Rate</dt>
                <dd className="pl-mono">{p.fxRate.toFixed(10)}</dd>
                <dt>Quote</dt>
                <dd className="pl-mono">{p.fxQuoteId}</dd>
                {capturedAt && (
                  <>
                    <dt>Locked</dt>
                    <dd>{atSec(capturedAt)}</dd>
                    <dt>Valid until</dt>
                    <dd>{timeOf(new Date(new Date(capturedAt).getTime() + QUOTE_TTL_MS).toISOString())}</dd>
                  </>
                )}
                <dt>Spread</dt>
                <dd>15 bps under mid</dd>
              </dl>
            )}
          </Card>

          <Card title="Details">
            <dl className="pl-facts">
              <dt>Payer</dt>
              <dd className="pl-mono">{p.payerId}</dd>
              <dt>Merchant</dt>
              <dd className="pl-mono">{p.merchantId}</dd>
              <dt>Created</dt>
              <dd>{atSec(p.createdAt)}</dd>
              <dt>Updated</dt>
              <dd>{atSec(p.updatedAt)}</dd>
              {traceId && (
                <>
                  <dt>Last trace</dt>
                  <dd className="pl-mono">{traceId}</dd>
                </>
              )}
            </dl>
          </Card>
        </aside>
      </div>

      <Modal
        open={refundOpen}
        title={`Refund ${money2(debit)} ${p.sourceCurrency} to ${p.payerId}?`}
        onCancel={() => setRefundOpen(false)}
        maskClosable={false}
        cancelText="Cancel"
        okText={`Refund ${money2(debit)} ${p.sourceCurrency}`}
        okButtonProps={{ danger: true, loading: busy }}
        onOk={() => run('refund')}
      >
        <p style={{ margin: 0 }}>
          This posts a reversing ledger entry and moves the payment to Refunded. A refund can’t be undone.
        </p>
      </Modal>
    </main>
  );
}
