# Paper Trading with Alpaca

StockPulse can turn its own signals into broker orders. It ships **disabled**, pointed at
Alpaca's **paper** endpoint, with conservative risk limits. Read this whole page before
changing any of that.

## How it works

Every 5 minutes (`app.trading.cron`) `AutoTraderService` runs one cycle:

1. **Snapshot** account, market clock, and open positions from Alpaca.
2. **Global gate** (`RiskGuard.checkGlobal`) — bail if trading is disabled, the kill switch
   is on, the account is blocked, the market is closed, or equity is down more than
   `max-daily-loss-pct` from the previous close.
3. **Exits** — for each open position, if the latest signal in the lookback window is
   `BEARISH` with confidence ≥ `exit-confidence`, close it at market.
4. **Entries** — take the latest `BULLISH` signal per ticker, highest confidence first.
   Each one passes `RiskGuard.checkEntry` (confidence, screening score, not already held,
   position count cap, not within 30 min of close, buying power) and is submitted as a
   **bracket market order** with a take-profit and stop-loss attached.

Every decision — including skips and errors — is written to `trade_executions`, so
`GET /api/v1/trading/executions` answers "why did/didn't it trade X?".

Signals are only acted on once: the signal's id is recorded, and a later run that sees the
same signal skips it.

## Setup

1. Create an Alpaca account and generate **paper** API keys (paper keys only work against
   `paper-api.alpaca.markets`).
2. In `.env`:
   ```
   ALPACA_KEY_ID=...
   ALPACA_SECRET_KEY=...
   TRADING_ENABLED=true
   ```
3. `docker-compose up --build`, then check `GET /api/v1/trading/status` shows
   `brokerConfigured: true`, `mode: paper`, `enabled: true`.

## Endpoints (`/api/v1/trading`)

| Method | Path | What |
| --- | --- | --- |
| GET | `/status` | Enabled/kill-switch/mode, market clock, active risk + signal config |
| GET | `/account` | Alpaca account (equity, buying power, PDT count) |
| GET | `/positions` | Open positions with unrealized P&L |
| GET | `/orders?status=all&limit=50` | Recent orders |
| GET | `/executions?ticker=AAPL&limit=100` | Audit log of auto-trader decisions |
| POST | `/run` | Run one cycle now (still risk-gated) |
| POST | `/kill-switch?engage=true` | Halt new entries immediately |
| POST | `/kill-switch?engage=true&liquidate=true` | Halt **and** close everything |
| POST | `/close/{symbol}` | Close one position at market |

The backend has no auth. Do not expose port 8181 publicly with trading enabled.

## Risk knobs (`app.trading.risk`)

| Key | Default | Meaning |
| --- | --- | --- |
| `max-position-notional` | 1000 | USD cap per new position |
| `max-position-pct-of-equity` | 0.05 | …or 5% of equity, whichever is lower |
| `max-open-positions` | 5 | Hard cap on simultaneous positions |
| `max-daily-loss-pct` | 0.02 | Stop opening positions once down 2% on the day |
| `take-profit-pct` | 0.03 | Bracket take-profit (+3%) |
| `stop-loss-pct` | 0.015 | Bracket stop-loss (−1.5%) |
| `no-new-entries-minutes-before-close` | 30 | Avoid holding into the close |
| `allow-shorts` | false | Reserved; shorts are not implemented |

Signal thresholds live under `app.trading.signals` (`min-confidence` 75,
`min-screening-score` 70, `lookback-minutes` 35, `exit-confidence` 70).

## Going live

Don't, yet. The point of this integration is to collect a few months of paper results in
`trade_executions` and compare them against `prediction_validations`. If you ever do switch
`ALPACA_BASE_URL` to `https://api.alpaca.markets`:

- US pattern-day-trader rules require $25k equity in a margin account for >3 day trades
  in 5 business days; the account's `daytrade_count` is exposed on `/account`.
- Start with `max-position-notional` far below what you can afford to lose.
- The kill switch is in-memory; a restart resets it to `false`. Set `TRADING_ENABLED=false`
  for anything longer than a pause.
