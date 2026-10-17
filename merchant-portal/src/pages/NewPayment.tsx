import {
  api,
  ApiError,
  CORRIDOR_CAP,
  CURRENCIES,
  MERCHANT_ID,
  newIdempotencyKey,
  type Payment,
} from '@/services/api';
import { FEE_RATE, money4 } from '@/services/format';
import { Link } from '@umijs/max';
import { Alert, Button, Card, Form, Input, Select } from 'antd';
import { useState } from 'react';

const CURRENCY_NAMES: Record<string, string> = {
  SGD: 'Singapore dollar',
  MYR: 'Malaysian ringgit',
  USD: 'US dollar',
  EUR: 'Euro',
  CNY: 'Chinese yuan',
};
const currencyOptions = CURRENCIES.map((c) => ({ value: c, label: `${c} · ${CURRENCY_NAMES[c]}` }));

// Mirrors the gateway's @Positive @Digits(integer = 16, fraction = 4) on amount.
const AMOUNT_RE = /^\d{1,16}(\.\d{1,4})?$/;

type Result =
  | { kind: 'approved' | 'replay'; payment: Payment }
  | { kind: 'declined'; payment: Payment; reason: string }
  | { kind: 'error'; title: string; text: string };

function parseAmount(raw: string): { ok: true; value: number; text: string } | { ok: false; error: string } {
  const text = raw.replace(/,/g, '').trim();
  if (!AMOUNT_RE.test(text)) return { ok: false, error: 'Enter an amount like 100.00, with up to 4 decimal places.' };
  const value = Number(text);
  if (!(value > 0)) return { ok: false, error: 'Enter an amount greater than 0.' };
  return { ok: true, value, text };
}

export default function NewPayment() {
  const [payerId, setPayerId] = useState('payer-1');
  const [src, setSrc] = useState('SGD');
  const [tgt, setTgt] = useState('MYR');
  const [amount, setAmount] = useState('100.00');
  const [key, setKey] = useState(newIdempotencyKey);
  const [touched, setTouched] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<Result | null>(null);

  const parsed = parseAmount(amount);
  const sameCurrency = src === tgt;
  const payerMissing = payerId.trim() === '';
  const fee = parsed.ok ? Math.round(parsed.value * FEE_RATE * 10000) / 10000 : null;

  const submit = async () => {
    setTouched(true);
    if (!parsed.ok || sameCurrency || payerMissing) return;
    setSubmitting(true);
    try {
      const { body, replayed } = await api.create(
        { payerId: payerId.trim(), merchantId: MERCHANT_ID, sourceCurrency: src, targetCurrency: tgt, amount: parsed.text },
        key,
      );
      if (replayed) {
        setResult({ kind: 'replay', payment: body });
      } else if (body.status === 'RISK_DECLINED') {
        const events = await api.events(body.id).catch(() => []);
        const reason = events.find((e) => e.toStatus === 'RISK_DECLINED')?.detail ?? 'No reason recorded';
        setResult({ kind: 'declined', payment: body, reason });
      } else {
        setResult({ kind: 'approved', payment: body });
      }
    } catch (e) {
      if (e instanceof ApiError && e.code === 'idempotency_key_reuse') {
        setResult({
          kind: 'error',
          title: 'Key already used',
          text: 'This idempotency key was used for a different request, so the gateway refused it (422). Generate a new key and try again.',
        });
      } else if (e instanceof ApiError && e.code === 'request_in_flight') {
        setResult({
          kind: 'error',
          title: 'Still processing',
          text: 'The first request with this key hasn’t finished. Wait a moment, then submit again with the same key.',
        });
      } else {
        setResult({
          kind: 'error',
          title: 'Payment not created',
          text: `${e instanceof ApiError ? e.message : 'The gateway did not respond.'} Submit again with the same key: if the first try got through, you’ll get its response instead of a duplicate.`,
        });
      }
    } finally {
      setSubmitting(false);
    }
  };

  const show = (cond: boolean) => touched && cond;

  return (
    <main className="pl-main" style={{ gap: 24 }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
        <Link to="/payments" className="pl-back">
          Back to payments
        </Link>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
          <h1 className="pl-h1">New payment</h1>
          <p className="pl-lede">Risk checks it the moment you create it. No money moves until you capture.</p>
        </div>
      </div>

      {result?.kind === 'approved' && (
        <Alert
          type="success"
          showIcon
          message="Payment created"
          description={
            <>
              <span className="pl-mono">{result.payment.id}</span> is risk approved.{' '}
              <Link to={`/payments/${result.payment.id}`} style={{ fontWeight: 600 }}>
                Open the payment
              </Link>{' '}
              to capture it.
            </>
          }
        />
      )}
      {result?.kind === 'declined' && (
        <Alert
          type="error"
          showIcon
          message="Declined by risk"
          description={
            <>
              <span className="pl-mono">{result.reason}</span>. Declines are final: change the payment and
              create a new one with a new key.
            </>
          }
        />
      )}
      {result?.kind === 'replay' && (
        <Alert
          type="info"
          showIcon
          message="Same request, same key"
          description={
            <>
              Nothing new was created. This is the original response for{' '}
              <Link to={`/payments/${result.payment.id}`} className="pl-mono">
                {result.payment.id}
              </Link>
              , replayed by the gateway.
            </>
          }
        />
      )}
      {result?.kind === 'error' && <Alert type="error" showIcon message={result.title} description={result.text} />}

      <div className="pl-split">
        <Card className="pl-split__main" styles={{ body: { display: 'flex', flexDirection: 'column', gap: 8 } }}>
          <Form layout="vertical" requiredMark={false} onFinish={submit} aria-label="Payment details">
            <fieldset style={{ margin: 0, padding: 0, border: 0 }}>
              <legend className="pl-h2" style={{ marginBottom: 16 }}>
                Who pays
              </legend>
              <Form.Item
                label="Payer ID"
                htmlFor="np-payer"
                validateStatus={show(payerMissing) ? 'error' : undefined}
                help={show(payerMissing) ? 'Enter a payer ID, like payer-1.' : 'Synthetic IDs only. payer-denylisted always declines.'}
              >
                <Input
                  id="np-payer"
                  value={payerId}
                  onChange={(e) => setPayerId(e.target.value)}
                  autoComplete="off"
                  spellCheck={false}
                  maxLength={55}
                />
              </Form.Item>
            </fieldset>

            <fieldset style={{ margin: 0, padding: 0, border: 0 }}>
              <legend className="pl-h2" style={{ margin: '16px 0' }}>
                How much
              </legend>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', columnGap: 20 }}>
                <Form.Item label="From" htmlFor="np-src">
                  <Select id="np-src" value={src} onChange={setSrc} options={currencyOptions} />
                </Form.Item>
                <Form.Item
                  label="To"
                  htmlFor="np-tgt"
                  validateStatus={sameCurrency ? 'error' : undefined}
                  help={sameCurrency ? 'Pick a different currency. PayLab only moves money across borders.' : undefined}
                >
                  <Select id="np-tgt" value={tgt} onChange={setTgt} options={currencyOptions} />
                </Form.Item>
              </div>
              <Form.Item
                label="Amount"
                htmlFor="np-amount"
                validateStatus={show(!parsed.ok) ? 'error' : undefined}
                help={show(!parsed.ok) && !parsed.ok ? parsed.error : `In ${src}. Up to 4 decimal places.`}
              >
                <Input
                  id="np-amount"
                  inputMode="decimal"
                  value={amount}
                  onChange={(e) => setAmount(e.target.value)}
                  onBlur={() => setTouched(true)}
                  autoComplete="off"
                  className="pl-num"
                />
              </Form.Item>
            </fieldset>

            <fieldset style={{ margin: 0, padding: 0, border: 0 }}>
              <legend className="pl-h2" style={{ margin: '16px 0' }}>
                Retry safety
              </legend>
              <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start', flexWrap: 'wrap' }}>
                <Form.Item
                  label="Idempotency key"
                  htmlFor="np-key"
                  help="Sent as Idempotency-Key. Resubmitting with the same key replays the first response."
                  style={{ flex: '1 1 280px', minWidth: 0 }}
                >
                  <Input id="np-key" value={key} readOnly className="pl-mono" />
                </Form.Item>
                <Button
                  style={{ marginTop: 30 }}
                  onClick={() => {
                    setKey(newIdempotencyKey());
                    setResult(null);
                  }}
                >
                  Generate new key
                </Button>
              </div>
            </fieldset>

            <div
              style={{
                display: 'flex',
                gap: 12,
                flexWrap: 'wrap',
                marginTop: 24,
                paddingTop: 20,
                borderTop: '1px solid var(--acme-color-border)',
              }}
            >
              <Button type="primary" htmlType="submit" loading={submitting} disabled={sameCurrency}>
                Create payment
              </Button>
              <Link to="/payments" className="ant-btn ant-btn-text" style={{ display: 'inline-flex', alignItems: 'center', padding: '0 15px', fontWeight: 600 }}>
                Cancel
              </Link>
            </div>
          </Form>
        </Card>

        <aside aria-label="Preview" className="pl-split__aside">
          <Card title="Preview">
            <dl className="pl-facts" style={{ gridTemplateColumns: '1fr auto' }}>
              <dt>Merchant</dt>
              <dd className="pl-mono">{MERCHANT_ID}</dd>
              <dt>Corridor</dt>
              <dd>
                {src} → {tgt}
              </dd>
              <dt>Amount</dt>
              <dd>{parsed.ok ? `${money4(parsed.value)} ${src}` : '—'}</dd>
              <dt>Fee (1%)</dt>
              <dd>{fee != null ? `${money4(fee)} ${src}` : '—'}</dd>
              <dt className="pl-facts__rule">Payer debited at capture</dt>
              <dd className="pl-facts__rule" style={{ fontWeight: 600 }}>
                {parsed.ok && fee != null ? `${money4(parsed.value + fee)} ${src}` : '—'}
              </dd>
              <dt>Converted</dt>
              <dd style={{ color: 'var(--acme-color-text-subtle)' }}>Set at capture</dd>
            </dl>
          </Card>
          <Card title="Risk checks">
            <ul
              style={{
                margin: 0,
                paddingLeft: 20,
                display: 'flex',
                flexDirection: 'column',
                gap: 8,
                fontSize: 14,
                color: 'var(--acme-color-text-muted)',
              }}
            >
              <li>Payer and merchant are not on the denylist.</li>
              <li>The corridor is supported and the amount is within its cap of {money4(CORRIDOR_CAP)} in the source currency.</li>
              <li>The payer has fewer than 10 approvals in the last 60 seconds.</li>
            </ul>
            <p className="pl-note" style={{ marginTop: 12 }}>
              The first check that fails declines the payment. Retries replay the original verdict.
            </p>
          </Card>
        </aside>
      </div>
    </main>
  );
}
