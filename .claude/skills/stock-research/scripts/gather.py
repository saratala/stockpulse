#!/usr/bin/env python3
"""Read-only data gatherer for the stock-research skill.

Usage: python3 gather.py AAPL [MSFT ...] [--base URL] [--no-backtest]

Calls only GET endpoints of the local StockPulse backend plus the
read-only-in-effect POST /backtest/day-trading/compare, in parallel, and
prints one compact JSON document (raw responses are large). Never calls
fetch/import/update/refresh endpoints and never touches anything trading-related.
Exit codes: 0 ok, 2 backend unreachable.
"""
import json
import os
import sys
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime

BASE = "http://localhost:8181/api/v1"


def _api_key():
    """X-API-Key for the backend: $STOCKPULSE_API_KEY, else APP_API_KEY from the repo's .env."""
    key = os.environ.get("STOCKPULSE_API_KEY")
    if key:
        return key.strip()
    env_file = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "..", "..", ".env")
    try:
        with open(env_file) as f:
            for line in f:
                if line.startswith("APP_API_KEY="):
                    return line.split("=", 1)[1].strip().strip("'\"") or None
    except OSError:
        pass
    return None


API_KEY = _api_key()


def call(path, method="GET", timeout=120):
    req = urllib.request.Request(BASE + path, method=method)
    if API_KEY:
        req.add_header("X-API-Key", API_KEY)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            body = r.read().decode()
            return {"status": r.status, "data": json.loads(body) if body else None}
    except urllib.error.HTTPError as e:
        if e.code == 401:
            return {"status": 401, "error": "HTTP 401: backend needs an API key; set APP_API_KEY in the repo .env "
                                            "or STOCKPULSE_API_KEY in the environment"}
        return {"status": e.code, "error": "HTTP %s" % e.code}
    except Exception as e:  # timeout, connection refused, bad JSON
        return {"status": None, "error": "%s: %s" % (type(e).__name__, e)}


def pct(a, b):
    return round((a / b - 1) * 100, 2) if a and b else None


def price_snapshot(rows):
    """/stocks/{t}/prices mixes 09:30 daily bars with intraday realtime snapshots.
    Collapse to one close per calendar date (last row wins) before computing trend."""
    if not isinstance(rows, list) or not rows:
        return {"error": "no price rows"}
    by_day = {}
    for r in sorted(rows, key=lambda r: r["date"]):
        by_day[r["date"][:10]] = r
    days = sorted(by_day)
    closes = [by_day[d]["close"] for d in days]
    last = closes[-1]

    def ret(n):
        return pct(last, closes[-1 - n]) if len(closes) > n else None

    def sma(n):
        return round(sum(closes[-n:]) / n, 2) if len(closes) >= n else None

    yr = closes[-252:]
    s20, s50, s200 = sma(20), sma(50), sma(200)
    return {
        "lastClose": last,
        "asOf": by_day[days[-1]]["date"],
        "dailyBars": len(days),
        "firstDate": days[0],
        "return1d": ret(1), "return5d": ret(5), "return20d": ret(20),
        "return60d": ret(60), "return252d": ret(252),
        "sma20": s20, "sma50": s50, "sma200": s200,
        "aboveSma50": last > s50 if s50 else None,
        "aboveSma200": last > s200 if s200 else None,
        "high52w": max(yr), "low52w": min(yr),
        "pctFrom52wHigh": pct(last, max(yr)),
        "last5Closes": [[d, by_day[d]["close"]] for d in days[-5:]],
    }


def ai_summary(d):
    if not isinstance(d, dict):
        return None
    t, m, b, rec, risk = (d.get(k) or {} for k in
                          ("technicalSummary", "mlPrediction", "backtestSummary",
                           "recommendation", "riskAssessment"))
    keep = lambda src, keys: {k: src.get(k) for k in keys}
    return {
        "analysisTime": d.get("analysisTime"),
        "currentPrice": d.get("currentPrice"),
        "technical": keep(t, ["compositeScore", "primarySignal", "signalConfidence",
                              "trendDirection", "rsi", "rsiSignal", "macdSignal",
                              "emaStackAligned", "supertrendBullish", "ichimokuSignal",
                              "mfi", "atrPercent", "riskLevel", "supportLevel",
                              "resistanceLevel", "signals"]),
        "ml": keep(m, ["predictedDirection", "bullishProbability", "bearishProbability",
                       "overallConfidence", "return1Day", "return1Week", "modelVersion"]),
        "mlBacktest": keep(b, ["daysAnalyzed", "directionAccuracy", "winRate",
                               "profitFactor", "sharpeRatio", "maxDrawdown",
                               "bestStrategyReturn", "buyHoldReturn"]),
        "modelLabel": keep(rec, ["action", "confidence", "timeHorizon",
                                 "bullishFactors", "bearishFactors", "keyRisks"]),
        "risk": keep(risk, ["overallRisk", "riskScore", "riskFactors"]),
    }


def compare_summary(d):
    if not isinstance(d, dict):
        return None
    if "_summary" not in d or not d["_summary"]:
        errs = {k: v.get("error") for k, v in d.items() if isinstance(v, dict) and v.get("error")}
        return {"error": errs or "no results"}
    any_strat = next((v for k, v in d.items() if not k.startswith("_") and isinstance(v, dict)
                      and "performance" in v), {})
    perf = any_strat.get("performance", {})
    return {
        "testPeriod": any_strat.get("testPeriod"),
        "dataSource": any_strat.get("dataSource"),
        "costs": any_strat.get("costs"),
        "buyHoldReturn": perf.get("buyHoldReturn"),
        "strategies": d["_summary"],
        "profitableCount": sum(1 for s in d["_summary"]
                               if float(str(s.get("totalReturn", "0")).rstrip("%")) > 0),
    }


def gather_ticker(t, screener, news_hi, backtest):
    with ThreadPoolExecutor(6) as ex:
        f = {
            "stock": ex.submit(call, "/stocks/%s" % t),
            "prices": ex.submit(call, "/stocks/%s/prices?days=400&limit=5000" % t),
            "latest": ex.submit(call, "/stocks/%s/prices/latest" % t),
            "ai": ex.submit(call, "/ai/analyze/%s" % t),
            "trend": ex.submit(call, "/sentiment-analysis/trend/%s?days=7" % t),
        }
        if backtest:
            f["compare"] = ex.submit(call, "/backtest/day-trading/compare?ticker=%s" % t, "POST", 300)
        r = {k: v.result() for k, v in f.items()}

    out = {"ticker": t, "errors": {}}
    if r["stock"]["status"] == 404:
        out["unknown"] = True
        out["note"] = "Ticker not tracked by the backend (GET /stocks/%s -> 404)." % t
        return out
    for k, v in r.items():
        if v.get("error"):
            out["errors"][k] = v["error"]
    s = r["stock"].get("data") or {}
    out["profile"] = {k: s.get(k) for k in ("name", "sector", "industry", "marketCap")}
    out["latestQuote"] = r["latest"].get("data")
    out["priceTrend"] = price_snapshot(r["prices"].get("data"))
    out["aiAnalysis"] = ai_summary(r["ai"].get("data"))
    out["sentiment7d"] = r["trend"].get("data")

    # Headlines: only if the DB already has sentiment data for this ticker, because
    # /sentiment/news/{t} falls back to an external fetch when the DB is empty.
    if (out["sentiment7d"] or {}).get("totalDataPoints"):
        n = call("/sentiment/news/%s?limit=10" % t)
        out["backendHeadlines"] = [{k: a.get(k) for k in ("publishedAt", "source", "title", "url")}
                                   for a in (n.get("data") or [])] if not n.get("error") else n["error"]
    else:
        out["backendHeadlines"] = "skipped: no sentiment data in DB (avoids external fetch)"

    cand = next((c for c in (screener.get("candidates") or []) if c.get("ticker") == t), None)
    if cand:
        sig = cand.get("signalAnalysis") or {}
        out["screener"] = {k: cand.get(k) for k in ("screeningScore", "changePercent", "volumeRatio",
                                                     "rsi", "adxStrength", "emaStackAligned")}
        out["screener"].update({"primarySignal": sig.get("primarySignal"),
                                "primaryConfidence": sig.get("primaryConfidence"),
                                "keyLevels": sig.get("keyLevels")})
        ranked = [c["ticker"] for c in screener["candidates"]]
        out["screener"]["rank"] = "%d of %d" % (ranked.index(t) + 1, len(ranked))
    else:
        out["screener"] = "not in screener results"

    words = {t.lower(), (s.get("name") or "").split(" ")[0].lower()} - {""}
    out["highImpactMarketNews"] = [
        {k: n.get(k) for k in ("publishedAt", "source", "title", "url", "impactLevel")}
        for n in (news_hi.get("news") or [])
        if any(w in (n.get("title") or "").lower() for w in words)][:5]
    if backtest:
        out["dayTradingBacktest"] = compare_summary(r["compare"].get("data"))
    return out


def main(argv):
    global BASE
    backtest = "--no-backtest" not in argv
    args = [a for a in argv if a != "--no-backtest"]
    if "--base" in args:
        i = args.index("--base")
        BASE = args[i + 1].rstrip("/")
        del args[i:i + 2]
    tickers = list(dict.fromkeys(a.upper().strip(",") for a in args if a.strip(",")))
    if not tickers:
        print("usage: gather.py TICKER [TICKER ...] [--base URL] [--no-backtest]", file=sys.stderr)
        return 1

    health = call("/health", timeout=5)
    if health.get("error"):
        print(json.dumps({"backend": "down", "base": BASE, "error": health["error"],
                          "hint": "Start the stack from the repo root with `docker compose up -d` "
                                  "(everything runs in Docker; backend on port 8181)."}, indent=1))
        return 2

    with ThreadPoolExecutor(3) as ex:
        scr_f = ex.submit(call, "/screener/run?maxResults=1000")
        hi_f = ex.submit(call, "/market-news/high-impact?hours=168")
        sent_f = ex.submit(call, "/sentiment-analysis/summary")
        scr, hi, sent = scr_f.result(), hi_f.result(), sent_f.result()

    with ThreadPoolExecutor(min(4, len(tickers))) as ex:
        results = list(ex.map(lambda t: gather_ticker(t, scr.get("data") or {}, hi.get("data") or {},
                                                      backtest), tickers))
    sd = sent.get("data") or {}
    print(json.dumps({
        "generatedAt": datetime.now().isoformat(timespec="seconds"),
        "base": BASE,
        "backendHealth": health.get("data"),
        "screenerRunAt": (scr.get("data") or {}).get("screeningDate"),
        "screenerError": scr.get("error"),
        "marketSentiment24h": {"overall": sd.get("overallSentiment"), "lastUpdated": sd.get("lastUpdated")},
        "tickers": results,
    }, indent=1, default=str))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
