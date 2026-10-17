# merchant-portal

The PayLab merchant UI: antd 5 + `@umijs/max`, styled with the ACME design system (warm Oat
neutrals, one Clay accent, Source Serif 4 / Inter / JetBrains Mono). It talks only to the
payment-gateway REST API.

```bash
npm ci
npm run dev        # http://localhost:8000; /api is proxied to http://localhost:8080
npm run typecheck
npm run build      # static bundle in dist/
```

Point the dev proxy elsewhere with `PAYLAB_GATEWAY_URL=http://host:port npm run dev`.

## Screens

| Route | What it does | API |
|---|---|---|
| `/payments` | List with status tabs, search, summary cards, inline Capture for approved payments | `GET /api/payments?merchantId=merchant-1`, `GET /api/trial-balance`, `POST …/capture` |
| `/payments/:id` | Lifecycle, amounts, locked FX quote, ledger legs, timeline; Capture / Refund (confirmed) | `GET /api/payments/{id}`, `GET …/events`, `POST …/capture`, `POST …/refund` |
| `/payments/new` | Create with live fee preview; shows the risk verdict, idempotent replays and key reuse (422) | `POST /api/payments` |
| `/trial-balance` | Totals per currency and per account, balanced flag | `GET /api/trial-balance` |

## Notes

- **Idempotency.** Every mutation sends an `Idempotency-Key` the page owns. The detail page
  keeps one key per intended action and only rotates it after success, so a retry after a
  dropped response is replayed rather than duplicated.
- **Ledger legs** on the detail page are derived from the posting rules in
  `docs/payment-flow.md` and the payment's own amounts: the gateway has no per-payment ledger
  read yet. The trial balance is the ledger-backed proof.
- **Decline reasons** come from the `RISK_DECLINED` event's detail, so they show on the
  detail page and after create, not in the list.
- **Merchant** is fixed to `merchant-1` (`src/services/api.ts`); there is no auth in the lab.
- **Theme** follows the OS (`prefers-color-scheme`), mirrored onto `<html data-theme>` so the
  CSS-variable tokens in `src/global.css` and the antd tokens in `src/theme.ts` agree.
