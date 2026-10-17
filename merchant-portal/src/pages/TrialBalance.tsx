import StatusBadge from '@/components/StatusBadge';
import { api, ApiError, type TrialBalanceLine, type TrialBalanceReport } from '@/services/api';
import { atSec, money4 } from '@/services/format';
import { Alert, Button, Card, Table, Tabs, type TableColumnsType } from 'antd';
import { useCallback, useEffect, useState } from 'react';

const columns: TableColumnsType<TrialBalanceLine> = [
  { title: 'Account', dataIndex: 'accountId', render: (v) => <span className="pl-mono">{v}</span> },
  { title: 'Type', dataIndex: 'accountType', render: (v) => <span style={{ color: 'var(--acme-color-text-muted)' }}>{v}</span> },
  { title: 'Ccy', dataIndex: 'currency', width: 72 },
  { title: 'Debits', dataIndex: 'debitTotal', align: 'right', render: money4 },
  { title: 'Credits', dataIndex: 'creditTotal', align: 'right', render: money4 },
  {
    title: 'Net',
    dataIndex: 'net',
    align: 'right',
    render: (v: number) => (
      <span style={{ color: v === 0 ? 'var(--acme-color-text-subtle)' : undefined }}>{money4(v)}</span>
    ),
  },
];

export default function TrialBalance() {
  const [report, setReport] = useState<TrialBalanceReport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [tab, setTab] = useState('all');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setReport(await api.trialBalance());
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'The gateway did not respond.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const lines = report?.lines ?? [];
  const currencies = [...new Set(lines.map((l) => l.currency))].sort();
  const totals = (ccy: string) =>
    lines
      .filter((l) => l.currency === ccy)
      .reduce((t, l) => ({ d: t.d + l.debitTotal, c: t.c + l.creditTotal }), { d: 0, c: 0 });
  const offCurrencies = currencies.filter((c) => (report?.netByCurrency[c] ?? 0) !== 0);
  const rows = lines.filter((l) => tab === 'all' || l.currency === tab);

  return (
    <main className="pl-main">
      <div className="pl-page-head">
        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
            <h1 className="pl-h1">Trial balance</h1>
            {report &&
              (report.balanced ? (
                <StatusBadge variant="success">Balanced</StatusBadge>
              ) : (
                <StatusBadge variant="danger">Out of balance</StatusBadge>
              ))}
          </div>
          <p className="pl-lede">
            Every posting ever made, totalled per account and currency.
            {report && ` Computed ${atSec(report.computedAt)}.`}
          </p>
        </div>
        <Button onClick={load} loading={loading}>
          Refresh
        </Button>
      </div>

      {error && (
        <Alert
          type="error"
          showIcon
          message="Couldn’t load the trial balance"
          description={`${error} The gateway proxies it from ledger-service; check both are running, then refresh.`}
        />
      )}
      {report && !report.balanced && (
        <Alert
          type="error"
          showIcon
          message={`Out of balance in ${offCurrencies.join(', ') || 'one or more currencies'}`}
          description="Debits and credits must match in every currency. Pause captures and check the most recent entries in those currencies before posting more."
        />
      )}

      <section aria-labelledby="ccy-title" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <h2 id="ccy-title" className="pl-h2">
          By currency
        </h2>
        {report && currencies.length === 0 ? (
          <p className="pl-lede">No postings yet. Capture a payment to see the ledger move.</p>
        ) : (
          <div className="pl-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))' }}>
            {currencies.map((c) => {
              const t = totals(c);
              const net = report?.netByCurrency[c] ?? 0;
              return (
                <Card key={c} title={c}>
                  <dl className="pl-facts" style={{ gap: '8px 12px' }}>
                    <dt>Debits</dt>
                    <dd>{money4(t.d)}</dd>
                    <dt>Credits</dt>
                    <dd>{money4(t.c)}</dd>
                    <dt className="pl-facts__rule">Net</dt>
                    <dd
                      className="pl-facts__rule"
                      style={{ fontWeight: 600, color: net === 0 ? undefined : 'var(--acme-color-danger)' }}
                    >
                      {money4(net)}
                    </dd>
                  </dl>
                </Card>
              );
            })}
          </div>
        )}
      </section>

      <section aria-labelledby="acct-title" style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <div className="pl-page-head">
          <h2 id="acct-title" className="pl-h2">
            Accounts
          </h2>
          <Tabs
            activeKey={tab}
            onChange={setTab}
            aria-label="Currency"
            style={{ marginBottom: -16 }}
            items={[{ key: 'all', label: `All ${lines.length}` }].concat(
              currencies.map((c) => ({ key: c, label: `${c} ${lines.filter((l) => l.currency === c).length}` })),
            )}
          />
        </div>
        <Table<TrialBalanceLine>
          rowKey={(l) => l.accountId + '|' + l.currency}
          columns={columns}
          dataSource={rows}
          loading={!report && !error}
          pagination={false}
          scroll={{ x: 'max-content' }}
          style={{ border: '1px solid var(--acme-color-border)', borderRadius: 12, overflow: 'hidden' }}
          summary={() => {
            if (tab === 'all') return null;
            const t = totals(tab);
            return (
              <Table.Summary.Row>
                <Table.Summary.Cell index={0} colSpan={3}>
                  <strong>Total {tab}</strong>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={3} align="right">
                  <strong>{money4(t.d)}</strong>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={4} align="right">
                  <strong>{money4(t.c)}</strong>
                </Table.Summary.Cell>
                <Table.Summary.Cell index={5} align="right">
                  <strong>{money4(report?.netByCurrency[tab] ?? 0)}</strong>
                </Table.Summary.Cell>
              </Table.Summary.Row>
            );
          }}
        />
        <p className="pl-note">
          Net is debits minus credits. Payers carry debit balances, and house and merchant accounts carry
          credit balances. Each currency nets to zero because every entry does. Corrections are new
          entries; nothing is ever edited.
        </p>
      </section>
    </main>
  );
}
