# ACME Exchange UI

A static, no-build front end for the ACME Exchange demo: seven screens ported from the
**ACME Exchange UI Rebuild** design canvas, styled with the **Modernist** design system
(Swiss style: Archivo, a flush-left grid, black rules instead of shadows, square corners,
blue for action and "you are here", red for danger only).

| Page | Screen |
| --- | --- |
| `index.html` | Markets |
| `trade.html` | Spot trade |
| `futures.html` | Perpetual futures |
| `earn.html` | Earn |
| `wallet.html` | Wallet |
| `sign-in.html` | Sign in |
| `create-account.html` | Create account |

## Run it

Serve this folder with any static file server, then open http://localhost:8000:

```bash
python3 -m http.server 8000 --directory web/acme-exchange
```

The pages load React 18.3.1 and htm 3.1.1 from cdnjs / jsDelivr and the Archivo and
JetBrains Mono fonts from Google Fonts, so the browser needs internet access. All data is
simulated in the page; nothing talks to the PayLab services yet.

## Layout

- `styles/modernist-tokens.css` — the Modernist tokens (colour, type, space, strokes, grid)
  compiled from the system's `tokens.json`, plus its text-style classes.
- `styles/acme-modernist.css` — the bridge: maps every `--acme-*` token the screens use onto
  a Modernist value, and restyles each `.acme-*` component class to Modernist rules
  (44px square controls with 2px ink edges, uppercase labels, hairline table rows,
  4px-ruled alerts, an 8px black bar over the top bar, no shadows or radii).
- `ds/components/bundle.js` — the ACME React components, kept for their behaviour
  (tab arrow keys, modal focus trap, field labelling). Their look comes entirely from
  `acme-modernist.css`; the ACME stylesheet and fonts are no longer used.
- `js/runtime.js` — the small runtime every page uses: `html` (htm bound to React, accepting
  HTML-style attributes), `DS` (the component namespace), `DCLogic` (base class for a
  screen's state and `renderVals()`) and `mount()`.
- `*.html` — one page per screen: page styles, the screen's logic class and its `html`
  template.

## Colour rules

Strict Modernist: a screen is black, white and one blue.

- Blue marks action and orientation only: the one primary button per view, links, the
  focus ring, the current nav link and tab, the picked Buy/Sell or Long/Short toggle,
  the chosen leverage, the selected Earn product, and the chart's last-price line.
- Buy/Sell and Long/Short are told apart by their words and arrows, not by colour.
  Price changes keep their up/down arrows. Candles are hollow (up) or solid (down) black.
- Red appears only for danger: field errors and a failed transaction's status.
- Statuses are labels in a thin black frame; the word carries the meaning.
