---
name: stock-research
description: Research-only stock briefs from the local StockPulse backend (localhost:8181) plus fresh web news. Use when the user runs /stock-research TICKER [TICKER...], or asks to research, look into, compare, or "what's going on with" one or more stocks, build or rank a watchlist, or wants a pre-market / earnings-week read on tickers — even if they don't say "research". Produces price/trend, technicals, sentiment and news with sources, catalysts, risks, honest backtest evidence and a ranked watchlist. Never places trades.
argument-hint: TICKER [TICKER...]
allowed-tools: Bash(python3 -I .claude/skills/stock-research/scripts/gather.py:*), WebSearch, WebFetch, Read
---

# Stock research brief

Produce a research brief for each requested ticker using the user's local StockPulse backend
(`http://localhost:8181/api/v1`) for prices/signals/backtests and web search for news from the last
~7 days and upcoming catalysts.

## Ground rules (why they matter)

- **Research only.** This skill reads data and writes a brief. Do not place, stage, or simulate
  orders, and do not call any broker, trading, paper-trading, or order endpoint — even if asked
  mid-conversation; say that is outside this skill. The user wants a thinking aid, not an agent
  touching money.
- **Not investment advice.** Open and close the brief with a one-line disclaimer. The backend emits
  fields like `action: "SELL"` and stop/target prices — present those as "model label", never as a
  recommendation, and don't invent your own buy/sell calls.
- **Honesty over confidence.** Every number gets a source and timestamp. If something failed or was
  skipped, say so in the brief rather than quietly omitting it.

## Step 1 — Gather backend data (one command)

Run from the repo root (it fans out all calls in parallel and prints compact JSON, ~9 KB/ticker):

```bash
python3 -I .claude/skills/stock-research/scripts/gather.py AAPL MSFT
```

Flags: `--no-backtest` (skip the ~2 s/ticker backtest), `--base URL` (other host).

- **Exit code 2 / `"backend": "down"`** → stop and tell the user to start the stack from the repo
  root with `docker compose up -d` (everything runs in Docker; backend container listens on 8181),
  then re-run. Offer a web-only brief clearly marked "no backend data" if they want it now.
- **`"unknown": true`** for a ticker → the backend doesn't track it (`/stocks/{t}` 404). Tell the
  user; check via web search whether the symbol exists (typo? delisted?). If real, give a short
  web-only section labelled "not in StockPulse universe" and exclude it from the ranking.
- Anything in a ticker's `errors` map, or a string like `"skipped: ..."` / `"not in screener
  results"`, goes into the brief's **Data gaps** line.

If the script can't run, the same data comes from these GETs (run them in parallel with `curl -s`):
see the endpoint table below.

## Step 2 — Web research (parallel)

For each ticker, run these WebSearch queries in one batch (include the current month/year so results
are fresh):
1. `<TICKER> <company> stock news <month year>` — last ~7 days of material news.
2. `<company> next earnings date <year>` — prefer the company-announced date (IR page, or outlets
   quoting it). Aggregators (nextearningsdate, Wall Street Horizon, etc.) often list *projected* dates
   that differ by days; if sources conflict, give the confirmed one and note the conflict.
3. Optionally one targeted query for anything the backend headlines hint at (downgrade, product,
   regulatory action).

Keep only items dated within ~7 days (older context only if it explains a catalyst). Record each
item's **date, outlet, and link**. Backend `backendHeadlines` URLs are Finnhub redirect links — fine
to cite, but prefer the original outlet when web search finds it.

## Step 3 — Interpret carefully

Known quirks of this backend (verified 2026-10):
- `priceTrend` is computed by the script from `/stocks/{t}/prices?days=400`, which mixes 09:30 daily
  bars with 5-minute realtime snapshots; the script keeps one close per day. Use it for the trend
  snapshot (returns, SMA20/50/200, 52-week range).
- `screener` (daily-scale RSI, ADX, support/resistance, rank out of the universe) is the better source
  for key levels. `aiAnalysis.technical` support/resistance/ATR are computed on very short-term data
  (levels often within ~0.2% of price) — label them "intraday-scale" or skip them; its RSI can
  disagree with the screener's. When two sources disagree, show both and say so.
- `aiAnalysis.mlBacktest` and `dayTradingBacktest` are the honesty check: compare strategy returns
  with `buyHoldReturn`.
- Backend timestamps are UTC without a zone suffix (e.g. `2026-10-20T20:40:00` ≈ 4:40 pm ET).
  Convert or label as UTC. A `latestQuote` with open = high = low = close is a realtime snapshot, not
  a full bar.
- `profile` (`/stocks/{t}`) is often a placeholder (e.g. "AAPL Inc.", industry "Software", null
  market cap). Use the real company name from web search, and use it in the search queries.
- If web search can't confirm a backend headline (e.g. which firm downgraded), cite the backend link
  and say it wasn't independently confirmed.
- `sentiment7d.dailySentiment` is a per-day mean score (roughly −1…+1); report the 7-day average and
  direction plus `totalDataPoints`.

## Step 4 — Write the brief

Use this structure. Keep each ticker to roughly one screen; tables beat prose for numbers.

```markdown
# Stock research brief — <TICKERS> — <date>
> Research only, not investment advice. No orders were placed. Data: StockPulse backend (as of <backendHealth.timestamp> UTC) + web search (<date>).

## <TICKER> — <company>
**Price & trend** — last $X (as of …, UTC); 1d / 5d / 20d / 1y returns; vs SMA50/SMA200; % from 52w high.
**Technical signals** — screener score & rank, primary signal + confidence, RSI, ADX, EMA stack, support/resistance; AI-analysis composite & notable signals (flag disagreements); ML direction/probabilities as "model output".
**Sentiment & news (last 7 days)** — backend 7-day sentiment avg/trend; 3–6 dated bullets `YYYY-MM-DD — headline — [Outlet](url)`.
**Upcoming catalysts** — next earnings date (source), events, ex-dividend, product/regulatory dates.
**Risks** — 3–5 concrete, ticker-specific risks (valuation, concentration, legal, macro, technical).
**Backtest evidence** — table of the 5 day-trading strategies (return, win rate, PF, max DD, trades) vs buy-and-hold over the test period and costs used; plus the ML backtest line. State plainly: none were profitable after costs (or which were) and that signals here are informational, not a demonstrated trading edge.
**Data gaps** — anything failed/skipped/stale, or "none".

## Watchlist ranking   (only when 2+ tickers)
| Rank | Ticker | Price | 20d | 1y | Screener score | Signal | Sentiment 7d | Next catalyst | Note |
Rank by a transparent rule you state (e.g. screener score, tie-break on trend vs SMA200 and sentiment), and say the ranking is an attention order for further research, not a buy list.

> Not investment advice. Backtests show these signals have not been profitable after costs.
```

If the day-trading backtest returns an error (e.g. "No intraday data"), say the backend has no 1-minute
history for that ticker — do not call the fetch endpoint it suggests.

## Endpoints

Verified working (GET unless noted; base `http://localhost:8181/api/v1`):

| Endpoint | Use | Key fields |
|---|---|---|
| `/health` | liveness | `status`, `timestamp` |
| `/stocks/{t}` | exists? (404 = unknown) | `name`, `sector` |
| `/stocks/{t}/prices?days=400&limit=5000` | trend | `date`, `close`, `volume` |
| `/stocks/{t}/prices/latest` | latest quote | `close`, `date` |
| `/ai/analyze/{t}` | technicals + ML + label | `technicalSummary`, `mlPrediction`, `backtestSummary`, `recommendation`, `riskAssessment` |
| `/screener/run?maxResults=1000` | daily-scale score/levels, rank | `candidates[].screeningScore`, `rsi`, `adxStrength`, `signalAnalysis.keyLevels` |
| `/sentiment-analysis/trend/{t}?days=7` | sentiment trend | `dailySentiment`, `totalDataPoints` |
| `/sentiment-analysis/summary` | market sentiment | `overallSentiment`, `sentimentByTicker` |
| `/sentiment/news/{t}?limit=10` | backend headlines (only when trend has data) | `publishedAt`, `source`, `title`, `url` |
| `/market-news/high-impact?hours=168` | macro / high-impact news | `news[].title`, `url`, `publishedAt` |
| `POST /backtest/day-trading/compare?ticker={t}` | strategy evidence (~2 s, computes only) | `_summary[]`, `<strategy>.performance.buyHoldReturn`, `costs`, `testPeriod` |
| `/intraday/{t}/stats` | 1-min data coverage (optional) | `startTime`, `endTime`, `totalRecords` |

**Do not call** — these write to the DB, trigger external data pulls, or burn API quota:
`/massivedata/**` (fetch, s3 import), `/twelvedata/fetch/**`, `/twelvedata/scheduler/run`,
`/data/**` (update/load), `/screener/update`, `/screener/fetch-historical`, `/screener/load-yearly`,
`/intraday/import/{t}`, `DELETE /intraday/{t}`, `/intraday/aggregated/fetch/*`, `/market-news/fetch`,
`/sentiment/news/{t}/refresh`, `POST /sentiment-analysis/analyze/*`, `/sentiment-analysis/refresh/*`,
`/sentiment-analysis/update-recent`, `/validation/run`, `/stream/subscribe*`, `/stream/unsubscribe`,
`/backtest/all-tickers`, `/backtest/ml-prediction/all`. Also avoid `/sentiment/{t}` and
`/sentiment/news/{t}` for tickers with no sentiment data — they fall back to an external news fetch.
Not useful: `/predictions/ticker/{t}` (usually empty), `/screener/signals` (often empty).
