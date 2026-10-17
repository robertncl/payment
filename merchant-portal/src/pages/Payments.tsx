import StatusBadge from '@/components/StatusBadge';
import { api, ApiError, newIdempotencyKey, type Payment, type PaymentStatus } from '@/services/api';
import { at, isToday, money2, shortId, STATUS } from '@/services/format';
import { Link, useNavigate } from '@umijs/max';
import { Alert, App, Button, Card, Input, Table, Tabs, type TableColumnsType } from 'antd';
import { useCallback, useEffect, useState } from 'react';

const TABS: { id: string; label: string; statuses?: PaymentStatus[] }[] = [
  { id: 'all', label: 'All' },
  { id: 'approved', label: 'Approved', statuses: ['RISK_APPROVED'] },
  { id: 'captured', label: 'Captured', statuses: ['CAPTURED'] },
  { id: 'settled', label: 'Settled', statuses: ['SETTLED'] },
  { id: 'refunded', label: 'Refunded', statuses: ['REFUNDED'] },
  { id: 'declined', label: 'Declined', statuses: ['RISK_DECLINED', 'FAILED'] },
];

export default function Payments() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const [payments, setPayments] = useState<Payment[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [balanced, setBalanced] = useState<boolean | null>(null);
  const [tab, setTab] = useState('all');
  const [query, setQuery] = useState('');
  const [capturing, setCapturing] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      const [list, tb] = await Promise.all([api.listPayments(), api.trialBalance()]);
      setPayments(list);
      setBalanced(tb.balanced);
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'The gateway did not respond.');
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const capture = async (p: Payment) => {
    setCapturing(p.id);
    try {
      const { body } = await api.capture(p.id, newIdempotencyKey());
      setPayments((prev) => prev?.map((x) => (x.id === body.id ? body : x)) ?? null);
      message.success(`Captured ${shortId(p.id)}.`);
    } catch (e) {
      message.error(
        e instanceof ApiError ? `Capture failed: ${e.message}` : 'Capture failed. Try again.',
      );
    } finally {
      setCapturing(null);
    }
  };

  const all = payments ?? [];
  const inTab = (p: Payment, id: string) => {
    const t = TABS.find((x) => x.id === id);
    return !t?.statuses || t.statuses.includes(p.status);
  };
  const q = query.trim().toLowerCase();
  const rows = all
    .filter((p) => inTab(p, tab))
    .filter((p) => !q || p.id.toLowerCase().includes(q) || p.payerId.toLowerCase().includes(q));

  const columns: TableColumnsType<Payment> = [
    {
      title: 'Payment',
      key: 'id',
      render: (_, p) => (
        <Link to={`/payments/${p.id}`} className="pl-mono">
          {shortId(p.id)}
        </Link>
      ),
    },
    { title: 'Payer', dataIndex: 'payerId', render: (v) => <span className="pl-mono">{v}</span> },
    {
      title: 'Corridor',
      key: 'corridor',
      render: (_, p) => `${p.sourceCurrency} → ${p.targetCurrency}`,
    },
    {
      title: 'Amount',
      key: 'amount',
      align: 'right',
      render: (_, p) => `${money2(p.amount)} ${p.sourceCurrency}`,
    },
    {
      title: 'Converted',
      key: 'converted',
      align: 'right',
      render: (_, p) =>
        p.targetAmount == null ? (
          <span style={{ color: 'var(--acme-color-text-subtle)' }}>—</span>
        ) : (
          `${money2(p.targetAmount)} ${p.targetCurrency}`
        ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      render: (s: PaymentStatus) => <StatusBadge variant={STATUS[s].badge}>{STATUS[s].label}</StatusBadge>,
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      render: (v: string) => <span style={{ color: 'var(--acme-color-text-muted)' }}>{at(v)}</span>,
    },
    {
      title: <span className="pl-sr-only">Actions</span>,
      key: 'actions',
      align: 'right',
      render: (_, p) =>
        p.status === 'RISK_APPROVED' ? (
          <Button
            type="text"
            size="small"
            loading={capturing === p.id}
            aria-label={`Capture ${shortId(p.id)}`}
            onClick={() => capture(p)}
          >
            Capture
          </Button>
        ) : null,
    },
  ];

  const count = (id: string) => all.filter((p) => inTab(p, id)).length;

  return (
    <main className="pl-main">
      <div className="pl-page-head">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <h1 className="pl-h1">Payments</h1>
          <p className="pl-lede">Cross-border payments for this merchant. Synthetic data, no real money.</p>
        </div>
        <Button type="primary" onClick={() => navigate('/payments/new')}>
          New payment
        </Button>
      </div>

      {error && (
        <Alert
          type="error"
          showIcon
          message="Couldn’t load payments"
          description={`${error} Check that the gateway is running on port 8080, then reload.`}
          action={<Button onClick={load}>Retry</Button>}
        />
      )}

      <div className="pl-grid">
        <Card title="Awaiting capture">
          <p className="pl-metric">{payments ? count('approved') : '—'}</p>
          <p className="pl-note">Risk approved, no money moved yet</p>
        </Card>
        <Card title="Captured today">
          <p className="pl-metric">
            {payments ? all.filter((p) => p.status === 'CAPTURED' && isToday(p.updatedAt)).length : '—'}
          </p>
          <p className="pl-note">Posted to the ledger at a locked FX quote</p>
        </Card>
        <Card title="Declined by risk">
          <p className="pl-metric">{payments ? count('declined') : '—'}</p>
          <p className="pl-note">Denylist, corridor cap or velocity</p>
        </Card>
        <Card title="Ledger">
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, minHeight: 48 }}>
            {balanced === null ? (
              <StatusBadge>Checking</StatusBadge>
            ) : balanced ? (
              <StatusBadge variant="success">Balanced</StatusBadge>
            ) : (
              <StatusBadge variant="danger">Out of balance</StatusBadge>
            )}
          </div>
          <Link to="/trial-balance" style={{ fontWeight: 600 }}>
            View trial balance
          </Link>
        </Card>
      </div>

      <section aria-labelledby="list-title" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <h2 id="list-title" className="pl-sr-only">
          All payments
        </h2>
        <div className="pl-page-head">
          <Tabs
            activeKey={tab}
            onChange={setTab}
            aria-label="Payment status"
            style={{ marginBottom: -16 }}
            items={TABS.map((t) => ({ key: t.id, label: `${t.label} ${payments ? count(t.id) : ''}`.trim() }))}
          />
          <div style={{ flex: '0 1 300px', minWidth: 220 }}>
            <label htmlFor="payment-search" className="pl-sr-only">
              Search payments
            </label>
            <Input
              id="payment-search"
              type="search"
              allowClear
              placeholder="Search by payment or payer ID"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
          </div>
        </div>

        <Table<Payment>
          rowKey="id"
          columns={columns}
          dataSource={rows}
          loading={payments === null && !error}
          pagination={false}
          scroll={{ x: 'max-content' }}
          style={{ border: '1px solid var(--acme-color-border)', borderRadius: 12, overflow: 'hidden' }}
          locale={{
            emptyText: q ? (
              <div className="pl-empty">
                <p style={{ fontWeight: 600 }}>No payments match “{query}”.</p>
                <p className="pl-lede">Search by payment ID or payer ID, or clear the search.</p>
                <Button size="small" onClick={() => setQuery('')}>
                  Clear search
                </Button>
              </div>
            ) : (
              <div className="pl-empty">
                <p style={{ fontWeight: 600 }}>No payments here yet.</p>
                <p className="pl-lede">Create one to see risk, capture and the ledger at work.</p>
              </div>
            ),
          }}
        />
        <p className="pl-note">
          Showing {rows.length} of {all.length}, newest first. Amounts are rounded for display; the
          ledger keeps 4 decimal places. Converted amounts are set at capture.
        </p>
      </section>
    </main>
  );
}
