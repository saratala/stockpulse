#!/usr/bin/env python3
"""Independent re-implementation of the intraday day-trading backtest (cross-check).

Usage: python3 scripts/verify_backtest.py TICKER STRATEGY
Python 3.9 stdlib only. Reads data via `docker exec stockpulse_db psql`.
"""
import json
import math
import subprocess
import sys
from collections import OrderedDict
from datetime import datetime, time

SLIP = 0.0005
INITIAL_CAPITAL = 10000.0

STRATEGIES = {
    "scalping": (0.3, 0.2, 30),
    "momentum": (1.0, 0.5, 120),
    "mean_reversion": (0.5, 0.4, 60),
    "breakout": (1.0, 0.5, 180),
    "combined": (0.8, 0.4, 120),
}
MOM_THRESHOLD = 0.4
MOM_LOOKBACK = 15
RSI_OVERSOLD = 30
RSI_OVERBOUGHT = 70
VWAP_STRETCH = 0.3
VOLUME_MULT = 1.5
POSITION_SIZE = 50.0
MAX_TRADES_PER_DAY = 5
MAX_DAILY_LOSS = 2.0

T_OPEN = time(9, 30)
T_CLOSE = time(16, 0)
T_OR_END = time(9, 45)
T_LAST_ENTRY = time(15, 30)
T_EOD = time(15, 55)


def load_bars(ticker):
    t = ticker.replace("'", "''")
    sql = (
        "SELECT ticker, to_char(timestamp AT TIME ZONE 'America/New_York', "
        "'YYYY-MM-DD HH24:MI:SS'), open, high, low, close, volume "
        "FROM stock_prices_intraday WHERE ticker = '%s' ORDER BY timestamp" % t
    )
    out = subprocess.run(
        ["docker", "exec", "stockpulse_db", "psql", "-U", "postgres", "-d",
         "stockpulse", "-At", "-F,", "-c", sql],
        check=True, capture_output=True, text=True,
    ).stdout
    bars = []
    for line in out.splitlines():
        if not line.strip():
            continue
        _, ts, o, h, l, c, v = line.split(",")
        bars.append({
            "dt": datetime.strptime(ts, "%Y-%m-%d %H:%M:%S"),
            "open": float(o), "high": float(h), "low": float(l),
            "close": float(c), "volume": float(v),
        })
    return bars


def group_days(bars):
    days = OrderedDict()
    for b in bars:
        tt = b["dt"].time()
        if tt >= T_OPEN and tt < T_CLOSE:
            days.setdefault(b["dt"].date(), []).append(b)
    return [(d, days[d]) for d in sorted(days) if len(days[d]) >= 60]


def vwap_series(bars):
    out, pv, vv = [], 0.0, 0.0
    for b in bars:
        typ = (b["high"] + b["low"] + b["close"]) / 3.0
        pv += typ * b["volume"]
        vv += b["volume"]
        out.append(pv / vv if vv != 0 else b["close"])
    return out


def ema_series(closes, p):
    k = 2.0 / (p + 1)
    out = [closes[0]]
    for i in range(1, len(closes)):
        out.append(closes[i] * k + out[-1] * (1 - k))
    return out


def rsi_series(closes, period=14):
    n = len(closes)
    out = [50.0] * n
    ag = al = 0.0
    for i in range(1, n):
        d = closes[i] - closes[i - 1]
        gain = d if d > 0 else 0.0
        loss = -d if d < 0 else 0.0
        if i <= period:
            ag += gain / period
            al += loss / period
            if i < period:
                continue
        else:
            ag = (ag * (period - 1) + gain) / period
            al = (al * (period - 1) + loss) / period
        out[i] = 100.0 if al == 0 else 100.0 - 100.0 / (1.0 + ag / al)
    return out


def avg_vol_series(bars):
    out, s = [], 0.0
    for i, b in enumerate(bars):
        out.append(math.inf if i == 0 else s / i)
        s += b["volume"]
    return out


def run(ticker, strategy):
    tp, sl, max_hold = STRATEGIES[strategy]
    capital = INITIAL_CAPITAL
    trades = []

    for _day, bars in group_days(load_bars(ticker)):
        n = len(bars)
        closes = [b["close"] for b in bars]
        vwap = vwap_series(bars)
        ema9 = ema_series(closes, 9)
        ema21 = ema_series(closes, 21)
        rsi = rsi_series(closes)
        avgv = avg_vol_series(bars)
        orb = [b for b in bars if b["dt"].time() < T_OR_END]
        or_high = max(b["high"] for b in orb) if orb else math.inf
        or_low = min(b["low"] for b in orb) if orb else -math.inf

        day_start = capital
        day_trades = 0
        limit_hit = False
        broke_up = broke_down = False
        pos = None
        pending = None

        for i, b in enumerate(bars):
            t = b["dt"].time()
            # 1) fill pending
            if pending is not None and pos is None and t <= T_LAST_ENTRY:
                d = pending
                entry = b["open"] * (1 + d * SLIP)
                shares = int(math.floor(capital * POSITION_SIZE / 100.0 / entry))
                if shares > 0:
                    pos = {"dir": d, "entry": entry, "shares": shares,
                           "stop": entry * (1 - d * sl / 100.0),
                           "target": entry * (1 + d * tp / 100.0),
                           "time": b["dt"]}
            if pending is not None:
                pending = None
            # 2) exits
            if pos is not None:
                d, stop, target = pos["dir"], pos["stop"], pos["target"]
                o, h, l, c = b["open"], b["high"], b["low"], b["close"]
                raw = reason = None
                if (d == 1 and o <= stop) or (d == -1 and o >= stop):
                    raw, reason = o, "STOP_LOSS"
                elif (d == 1 and o >= target) or (d == -1 and o <= target):
                    raw, reason = o, "TAKE_PROFIT"
                elif (d == 1 and l <= stop) or (d == -1 and h >= stop):
                    raw, reason = stop, "STOP_LOSS"
                elif (d == 1 and h >= target) or (d == -1 and l <= target):
                    raw, reason = target, "TAKE_PROFIT"
                elif t >= T_EOD or i == n - 1:
                    raw, reason = c, "END_OF_DAY"
                elif (b["dt"] - pos["time"]).total_seconds() / 60.0 >= max_hold:
                    raw, reason = c, "TIME_STOP"
                if reason is not None:
                    exit_px = raw * (1 - d * SLIP)
                    pnl = d * (exit_px - pos["entry"]) * pos["shares"]
                    capital += pnl
                    day_trades += 1
                    trades.append({
                        "entryTime": pos["time"].strftime("%Y-%m-%d %H:%M"),
                        "exitTime": b["dt"].strftime("%Y-%m-%d %H:%M"),
                        "direction": "LONG" if d == 1 else "SHORT",
                        "entryPrice": round(pos["entry"], 4),
                        "exitPrice": round(exit_px, 4),
                        "shares": pos["shares"],
                        "pnl": round(pnl, 2),
                        "exitReason": reason,
                    })
                    pos = None
                    if capital - day_start <= -day_start * MAX_DAILY_LOSS / 100.0:
                        limit_hit = True
            # 4) signals
            if (pos is not None or limit_hit or day_trades >= MAX_TRADES_PER_DAY
                    or t < T_OR_END or t >= T_LAST_ENTRY or i >= n - 1):
                continue
            c = b["close"]
            vol_ok = b["volume"] > VOLUME_MULT * avgv[i]
            bo_up = (not broke_up) and c > or_high and vol_ok
            bo_down = (not broke_down) and c < or_low and vol_ok
            mom = 0
            if i >= MOM_LOOKBACK:
                prev = closes[i - MOM_LOOKBACK]
                chg = (c - prev) / prev * 100.0
                if chg >= MOM_THRESHOLD and c > vwap[i]:
                    mom = 1
                elif chg <= -MOM_THRESHOLD and c < vwap[i]:
                    mom = -1
            cross = 0
            if i > 0 and ema9[i - 1] <= ema21[i - 1] and ema9[i] > ema21[i] and c > vwap[i]:
                cross = 1
            elif i > 0 and ema9[i - 1] >= ema21[i - 1] and ema9[i] < ema21[i] and c < vwap[i]:
                cross = -1

            sig = 0
            if strategy == "scalping":
                sig = cross
            elif strategy == "momentum":
                sig = mom
            elif strategy == "mean_reversion":
                if rsi[i] <= RSI_OVERSOLD and c < vwap[i] * (1 - VWAP_STRETCH / 100.0):
                    sig = 1
                elif rsi[i] >= RSI_OVERBOUGHT and c > vwap[i] * (1 + VWAP_STRETCH / 100.0):
                    sig = -1
            elif strategy == "breakout":
                if bo_up:
                    sig = 1
                    broke_up = True
                elif bo_down:
                    sig = -1
                    broke_down = True
            elif strategy == "combined":
                if mom > 0 and (bo_up or cross > 0):
                    sig = 1
                elif mom < 0 and (bo_down or cross < 0):
                    sig = -1
            if sig != 0:
                pending = sig

    breakdown = {}
    for tr in trades:
        breakdown[tr["exitReason"]] = breakdown.get(tr["exitReason"], 0) + 1
    return {
        "totalTrades": len(trades),
        "finalCapital": round(capital, 2),
        "exitReasonBreakdown": breakdown,
        "trades": trades,
    }


def main():
    if len(sys.argv) != 3 or sys.argv[2] not in STRATEGIES:
        sys.stderr.write("usage: verify_backtest.py TICKER {%s}\n" % "|".join(STRATEGIES))
        sys.exit(2)
    print(json.dumps(run(sys.argv[1].upper(), sys.argv[2]), indent=2))


if __name__ == "__main__":
    main()
