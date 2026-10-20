package com.stockpulse.agent.service;

import com.stockpulse.agent.model.StockPrice;
import com.stockpulse.agent.repository.StockPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Swing Trading Backtest Service
 *
 * Event-driven, long-only backtest over daily bars (multi-day holds).
 * Rules that keep the results honest:
 * - Exactly one bar per calendar date: the stock_prices row stored at 09:30:00 UTC; any other
 *   rows on that date (intraday snapshots) are ignored
 * - Signals use only bars up to and including the signal day's close
 * - Entries AND exits fill at the NEXT day's open, with slippage against us on each side
 * - Stops are checked against later days' lows; a gap below the stop fills at the open.
 *   On the entry day the stop may trigger on that day's low (the entry at the open precedes it)
 * - No trades during indicator warmup; the test period starts after it
 * - A position still open on the last day is marked to market at the last close (no exit
 *   cost charged) and reported as openPosition, not as a closed trade
 *
 * Strategies:
 * 1. trend         - close > SMA(50) and SMA(50) > SMA(200); exit on close < SMA(50)
 * 2. donchian      - close > highest high of prior 20 days; exit on close < lowest low of
 *                    prior 10 days; stop at entry - 2 x ATR(14) (Wilder)
 * 3. rsi2_pullback - close > SMA(200) and RSI(2) < 10; exit on close > SMA(5) or after 10 days
 * 4. buy_hold      - buy at the first tested day's open and never sell (benchmark)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SwingBacktestService {

    private final StockPriceRepository stockPriceRepository;

    public static final List<String> STRATEGIES = List.of("trend", "donchian", "rsi2_pullback", "buy_hold");

    /** Daily bars are stored at this UTC time of day; every other row is an intraday snapshot. */
    static final LocalTime DAILY_BAR_TIME_UTC = LocalTime.of(9, 30);

    private static final double SLIPPAGE_PCT = 0.05;   // % per side
    private static final double COMMISSION_PER_TRADE = 0.0;
    private static final int MIN_TRADES_FOR_VERDICT = 30;
    private static final int MIN_TEST_DAYS = 20;
    private static final int DEFAULT_WARMUP_DAYS = 200;

    record Bar(LocalDate date, double open, double high, double low, double close, long volume) {}

    private static final class Position {
        int entryIndex;
        LocalDate entryDate;
        double entryPrice;
        long shares;
        double stopPrice;   // NaN when no stop
    }

    /**
     * Run a swing backtest with the given strategy.
     */
    public Map<String, Object> runSwingBacktest(String ticker, BigDecimal initialCapital,
                                                String strategy, Map<String, Double> overrides) {
        String strat = strategy == null ? "trend" : strategy.toLowerCase();
        if (!STRATEGIES.contains(strat)) {
            return createErrorResult(ticker, strat, "Unknown strategy '" + strategy + "'. Available: " + STRATEGIES);
        }
        log.info("Running swing backtest for {} with strategy: {}", ticker, strat);

        Map<String, Double> params = withDefaults(strat, overrides);
        List<Bar> bars = dailyBars(stockPriceRepository.findDailyBarsByTicker(ticker));

        int warmup = Math.max(params.get("warmupDays").intValue(), requiredHistory(strat, params));
        params.put("warmupDays", (double) warmup);
        if (bars.size() < warmup + MIN_TEST_DAYS) {
            return createErrorResult(ticker, strat, "Not enough daily bars: have " + bars.size()
                + ", need at least " + (warmup + MIN_TEST_DAYS) + " (" + warmup + " warmup + "
                + MIN_TEST_DAYS + " test days)");
        }

        return simulate(ticker, strat, bars, warmup, initialCapital.doubleValue(), params);
    }

    /**
     * Keep exactly one bar per calendar date: the row whose UTC time of day is 09:30:00.
     * Entity timestamps are LocalDateTime in the JVM zone, so convert to UTC before checking.
     */
    static List<Bar> dailyBars(List<StockPrice> rows) {
        TreeMap<LocalDate, Bar> byDate = new TreeMap<>();
        ZoneId jvmZone = ZoneId.systemDefault();
        for (StockPrice p : rows) {
            if (p.getDate() == null || p.getOpen() == null || p.getHigh() == null
                || p.getLow() == null || p.getClose() == null) continue;
            LocalDateTime utc = p.getDate().atZone(jvmZone).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
            if (!utc.toLocalTime().equals(DAILY_BAR_TIME_UTC)) continue;
            byDate.putIfAbsent(utc.toLocalDate(), new Bar(utc.toLocalDate(), p.getOpen().doubleValue(),
                p.getHigh().doubleValue(), p.getLow().doubleValue(), p.getClose().doubleValue(),
                p.getVolume() == null ? 0 : p.getVolume()));
        }
        return new ArrayList<>(byDate.values());
    }

    private Map<String, Double> withDefaults(String strategy, Map<String, Double> overrides) {
        Map<String, Double> p = new LinkedHashMap<>();
        switch (strategy) {
            case "trend" -> { p.put("smaFast", 50.0); p.put("smaSlow", 200.0); }
            case "donchian" -> {
                p.put("entryLookback", 20.0); p.put("exitLookback", 10.0); p.put("stopAtrMultiple", 2.0);
            }
            case "rsi2_pullback" -> {
                p.put("trendSma", 200.0); p.put("rsiPeriod", 2.0); p.put("rsiEntry", 10.0);
                p.put("exitSma", 5.0); p.put("maxHoldDays", 10.0);
            }
            default -> { }
        }
        p.putIfAbsent("stopAtrMultiple", 0.0);   // stop at entry - k x ATR; 0 = off
        p.put("atrPeriod", 14.0);
        p.put("stopLossPct", 0.0);         // optional fixed % stop below entry; 0 = off
        p.putIfAbsent("maxHoldDays", 0.0); // time stop in trading days; 0 = off
        p.put("positionSize", 100.0);      // % of capital per trade
        p.put("warmupDays", (double) DEFAULT_WARMUP_DAYS);   // same test window for every strategy by default
        if (overrides != null) {
            overrides.forEach((k, v) -> { if (v != null) p.put(k, v); });
        }
        return p;
    }

    /** Bars needed before every indicator the strategy uses is defined. */
    private int requiredHistory(String strategy, Map<String, Double> p) {
        int atrNeed = p.get("stopAtrMultiple") > 0 ? p.get("atrPeriod").intValue() + 1 : 0;
        return Math.max(atrNeed, switch (strategy) {
            case "trend" -> Math.max(p.get("smaFast").intValue(), p.get("smaSlow").intValue());
            case "donchian" -> Math.max(p.get("entryLookback").intValue(), p.get("exitLookback").intValue());
            case "rsi2_pullback" -> Math.max(Math.max(p.get("trendSma").intValue(), p.get("exitSma").intValue()),
                p.get("rsiPeriod").intValue() + 1);
            default -> 0;
        });
    }

    private Map<String, Object> simulate(String ticker, String strategy, List<Bar> bars, int warmup,
                                         double initialCapital, Map<String, Double> params) {
        int n = bars.size();
        double[] closes = bars.stream().mapToDouble(Bar::close).toArray();

        // Indicators: value at i uses bars 0..i only (NaN until defined)
        double[] smaFast = null, smaSlow = null, trendSma = null, exitSma = null, rsi = null;
        double[] priorHigh = null, priorLow = null, atr = null;
        switch (strategy) {
            case "trend" -> {
                smaFast = sma(closes, params.get("smaFast").intValue());
                smaSlow = sma(closes, params.get("smaSlow").intValue());
            }
            case "donchian" -> {
                priorHigh = priorHighest(bars, params.get("entryLookback").intValue());
                priorLow = priorLowest(bars, params.get("exitLookback").intValue());
            }
            case "rsi2_pullback" -> {
                trendSma = sma(closes, params.get("trendSma").intValue());
                exitSma = sma(closes, params.get("exitSma").intValue());
                rsi = rsi(closes, params.get("rsiPeriod").intValue());
            }
            default -> { }
        }

        double stopAtrMultiple = strategy.equals("buy_hold") ? 0 : params.get("stopAtrMultiple");
        double stopLossPct = strategy.equals("buy_hold") ? 0 : params.get("stopLossPct");
        int maxHoldDays = strategy.equals("buy_hold") ? 0 : params.get("maxHoldDays").intValue();
        if (stopAtrMultiple > 0) atr = atr(bars, params.get("atrPeriod").intValue());

        double cash = initialCapital;
        double prevEquity = initialCapital;
        double peakEquity = initialCapital;
        double maxDrawdown = 0;
        int daysInMarket = 0;
        List<Map<String, Object>> trades = new ArrayList<>();
        List<Double> dailyReturns = new ArrayList<>();

        Position pos = null;
        boolean pendingEntry = strategy.equals("buy_hold");   // buy_hold buys the first tested open
        double pendingStopDistance = Double.NaN;                // ATR-based stop distance known at signal time
        String pendingExit = null;

        for (int i = warmup; i < n; i++) {
            Bar bar = bars.get(i);

            // 1. Exit at the open: a gap through the stop, or an exit signalled on the previous close
            if (pos != null) {
                if (!Double.isNaN(pos.stopPrice) && bar.open() <= pos.stopPrice) {
                    cash += close(pos, bars, i, bar.open(), "STOP_LOSS", trades);
                    pos = null;
                } else if (pendingExit != null) {
                    cash += close(pos, bars, i, bar.open(), pendingExit, trades);
                    pos = null;
                }
            }
            pendingExit = null;

            // 2. Entry at the open, signalled on the previous close
            if (pendingEntry && pos == null) {
                pos = open(bar, i, cash, params, pendingStopDistance, stopLossPct);
                if (pos != null) cash -= pos.shares * pos.entryPrice;
            }
            pendingEntry = false;
            pendingStopDistance = Double.NaN;

            // 3. Intraday stop against this day's low (includes the entry day: entry at open precedes the range)
            if (pos != null && !Double.isNaN(pos.stopPrice) && bar.low() <= pos.stopPrice) {
                double fill = Math.min(pos.stopPrice, bar.open());
                cash += close(pos, bars, i, fill, "STOP_LOSS", trades);
                pos = null;
            }

            // 4. Mark to market on the close
            double equity = cash + (pos == null ? 0 : pos.shares * bar.close());
            if (pos != null) daysInMarket++;
            peakEquity = Math.max(peakEquity, equity);
            maxDrawdown = Math.max(maxDrawdown, (peakEquity - equity) / peakEquity * 100);
            dailyReturns.add(prevEquity > 0 ? equity / prevEquity - 1 : 0);
            prevEquity = equity;

            // 5. Signals on this close, filled at the next open
            if (i == n - 1) break;
            if (pos != null) {
                pendingExit = exitSignal(strategy, i, closes, smaFast, priorLow, exitSma, pos, maxHoldDays);
            } else {
                boolean enter = switch (strategy) {
                    case "trend" -> closes[i] > smaFast[i] && smaFast[i] > smaSlow[i];   // above SMA50 so an SMA50 exit doesn't re-enter next day
                    case "donchian" -> closes[i] > priorHigh[i];
                    case "rsi2_pullback" -> closes[i] > trendSma[i] && rsi[i] < params.get("rsiEntry");
                    default -> false;   // buy_hold enters once, before the loop
                };
                if (enter) {
                    pendingEntry = true;
                    if (stopAtrMultiple > 0) pendingStopDistance = stopAtrMultiple * atr[i];
                }
            }
        }

        Bar last = bars.get(n - 1);
        double finalEquity = cash + (pos == null ? 0 : pos.shares * last.close());
        Map<String, Object> openPosition = null;
        if (pos != null) {
            openPosition = new LinkedHashMap<>();
            openPosition.put("entryDate", pos.entryDate.toString());
            openPosition.put("entryPrice", round(pos.entryPrice, 4));
            openPosition.put("shares", pos.shares);
            openPosition.put("lastClose", round(last.close(), 4));
            openPosition.put("unrealizedPnl", round((last.close() - pos.entryPrice) * pos.shares, 2));
            openPosition.put("holdingDays", n - 1 - pos.entryIndex);
            if (!Double.isNaN(pos.stopPrice)) openPosition.put("stopPrice", round(pos.stopPrice, 4));
        }

        return buildResult(ticker, strategy, bars, warmup, initialCapital, finalEquity, trades, openPosition,
            dailyReturns, maxDrawdown, daysInMarket, params);
    }

    private String exitSignal(String strategy, int i, double[] closes, double[] smaFast, double[] priorLow,
                              double[] exitSma, Position pos, int maxHoldDays) {
        boolean signal = switch (strategy) {
            case "trend" -> closes[i] < smaFast[i];
            case "donchian" -> closes[i] < priorLow[i];
            case "rsi2_pullback" -> closes[i] > exitSma[i];
            default -> false;   // buy_hold never exits
        };
        if (signal) return "SIGNAL_EXIT";
        // The exit fills at the next open, so holding days at exit = i + 1 - entryIndex
        if (maxHoldDays > 0 && i + 1 - pos.entryIndex >= maxHoldDays) return "TIME_STOP";
        return null;
    }

    private Position open(Bar bar, int index, double cash, Map<String, Double> params,
                          double stopDistance, double stopLossPct) {
        double entry = bar.open() * (1 + SLIPPAGE_PCT / 100);
        long shares = (long) Math.floor(cash * params.get("positionSize") / 100 / entry);
        if (shares <= 0) return null;
        Position p = new Position();
        p.entryIndex = index;
        p.entryDate = bar.date();
        p.entryPrice = entry;
        p.shares = shares;
        double stop = Double.NaN;
        if (!Double.isNaN(stopDistance) && stopDistance > 0) stop = entry - stopDistance;
        if (stopLossPct > 0) {
            double pctStop = entry * (1 - stopLossPct / 100);
            stop = Double.isNaN(stop) ? pctStop : Math.max(stop, pctStop);   // the tighter one wins
        }
        p.stopPrice = stop;
        return p;
    }

    /** Close the position at {@code rawPrice} less slippage; returns the cash proceeds. */
    private double close(Position pos, List<Bar> bars, int index, double rawPrice, String reason,
                         List<Map<String, Object>> trades) {
        double exitPrice = rawPrice * (1 - SLIPPAGE_PCT / 100);
        double pnl = (exitPrice - pos.entryPrice) * pos.shares - 2 * COMMISSION_PER_TRADE;
        double returnPct = (exitPrice - pos.entryPrice) / pos.entryPrice * 100;
        Map<String, Object> trade = new LinkedHashMap<>();
        trade.put("entryDate", pos.entryDate.toString());
        trade.put("exitDate", bars.get(index).date().toString());
        trade.put("direction", "LONG");
        trade.put("entryPrice", round(pos.entryPrice, 4));
        trade.put("exitPrice", round(exitPrice, 4));
        trade.put("shares", pos.shares);
        trade.put("pnl", round(pnl, 2));
        trade.put("returnPct", round(returnPct, 3) + "%");
        trade.put("exitReason", reason);
        trade.put("holdingDays", index - pos.entryIndex);
        trades.add(trade);
        return exitPrice * pos.shares - 2 * COMMISSION_PER_TRADE;
    }

    private Map<String, Object> buildResult(String ticker, String strategy, List<Bar> bars, int warmup,
                                            double initialCapital, double finalCapital,
                                            List<Map<String, Object>> trades, Map<String, Object> openPosition,
                                            List<Double> dailyReturns, double maxDrawdown, int daysInMarket,
                                            Map<String, Double> params) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ticker", ticker);
        result.put("strategy", strategy);
        result.put("strategyType", "Swing Trading");
        result.put("dataSource", "Daily bars from stock_prices (one 09:30 UTC row per date; other rows ignored)");

        Bar first = bars.get(warmup);
        Bar last = bars.get(bars.size() - 1);
        int tradingDays = bars.size() - warmup;
        Map<String, Object> testPeriod = new LinkedHashMap<>();
        testPeriod.put("start", first.date().toString());
        testPeriod.put("end", last.date().toString());
        testPeriod.put("tradingDays", tradingDays);
        testPeriod.put("warmupDays", warmup);
        testPeriod.put("dataStart", bars.get(0).date().toString());
        result.put("testPeriod", testPeriod);

        List<Double> pnls = trades.stream().map(t -> ((Number) t.get("pnl")).doubleValue()).toList();
        int wins = (int) pnls.stream().filter(p -> p > 0).count();
        int losses = pnls.size() - wins;
        double grossProfit = pnls.stream().filter(p -> p > 0).mapToDouble(Double::doubleValue).sum();
        double grossLoss = -pnls.stream().filter(p -> p <= 0).mapToDouble(Double::doubleValue).sum();

        double totalReturn = (finalCapital - initialCapital) / initialCapital * 100;
        double buyHoldReturn = (last.close() - first.open()) / first.open() * 100;
        double winRate = trades.isEmpty() ? 0 : (double) wins / trades.size() * 100;
        double avgWin = wins > 0 ? grossProfit / wins : 0;
        double avgLoss = losses > 0 ? grossLoss / losses : 0;
        double profitFactor = grossLoss > 0 ? grossProfit / grossLoss : (grossProfit > 0 ? 99.99 : 0);
        double avgHoldingDays = trades.stream().mapToInt(t -> ((Number) t.get("holdingDays")).intValue())
            .average().orElse(0);

        // Annualised Sharpe from daily mark-to-market equity returns (risk-free rate ignored)
        double mean = dailyReturns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double std = calculateStdDev(dailyReturns.stream().mapToDouble(Double::doubleValue).toArray());
        double sharpeRatio = std > 0 ? mean / std * Math.sqrt(252) : 0;

        Map<String, Object> performance = new LinkedHashMap<>();
        performance.put("initialCapital", initialCapital);
        performance.put("finalCapital", round(finalCapital, 2));
        performance.put("totalReturn", round(totalReturn, 2) + "%");
        performance.put("buyHoldReturn", round(buyHoldReturn, 2) + "%");
        performance.put("excessReturn", round(totalReturn - buyHoldReturn, 2) + "%");
        performance.put("maxDrawdown", round(maxDrawdown, 2) + "%");
        performance.put("totalTrades", trades.size());
        performance.put("winningTrades", wins);
        performance.put("losingTrades", losses);
        performance.put("winRate", round(winRate, 1) + "%");
        performance.put("avgWin", "$" + round(avgWin, 2));
        performance.put("avgLoss", "$" + round(avgLoss, 2));
        performance.put("profitFactor", round(profitFactor, 2));
        performance.put("sharpeRatio", round(sharpeRatio, 2));
        performance.put("exposurePct", round((double) daysInMarket / tradingDays * 100, 1) + "%");
        performance.put("avgHoldingDays", round(avgHoldingDays, 1));
        result.put("performance", performance);

        result.put("parameters", params);
        result.put("costs", Map.of("slippagePctPerSide", SLIPPAGE_PCT, "commissionPerTrade", COMMISSION_PER_TRADE));
        result.put("exitReasonBreakdown", trades.stream()
            .collect(Collectors.groupingBy(t -> (String) t.get("exitReason"), TreeMap::new, Collectors.counting())));
        int recentCount = Math.min(20, trades.size());
        result.put("recentTrades", trades.subList(trades.size() - recentCount, trades.size()));
        result.put("trades", trades);   // swing trades are few, so the full list is cheap
        if (openPosition != null) result.put("openPosition", openPosition);

        String recommendation;
        if (strategy.equals("buy_hold")) {
            recommendation = "BENCHMARK - Buy and hold through the same engine and costs";
        } else if (trades.size() < MIN_TRADES_FOR_VERDICT) {
            recommendation = "INSUFFICIENT DATA - Only " + trades.size() + " closed trades over " + tradingDays
                + " days; need at least " + MIN_TRADES_FOR_VERDICT + " before judging";
        } else if (winRate > 55 && profitFactor > 1.5) {
            recommendation = "STRONG - Good win rate and profit factor";
        } else if (winRate > 50 && profitFactor > 1.2) {
            recommendation = "MODERATE - Profitable but needs optimization";
        } else if (profitFactor > 1.0) {
            recommendation = "WEAK - Marginally profitable, high risk";
        } else {
            recommendation = "NOT RECOMMENDED - Strategy is unprofitable";
        }
        result.put("recommendation", recommendation);
        if (!strategy.equals("buy_hold") && trades.size() < MIN_TRADES_FOR_VERDICT) {
            result.put("warning", "Only " + trades.size() + " closed trades over " + tradingDays
                + " trading days; results are not statistically meaningful yet");
        }

        return result;
    }

    // ---- Indicators (value at i uses bars 0..i only; NaN until defined) ----

    static double[] sma(double[] values, int period) {
        double[] out = new double[values.length];
        Arrays.fill(out, Double.NaN);
        double sum = 0;
        for (int i = 0; i < values.length; i++) {
            sum += values[i];
            if (i >= period) sum -= values[i - period];
            if (i >= period - 1) out[i] = sum / period;
        }
        return out;
    }

    /** Highest high of the {@code period} days BEFORE day i (day i excluded). */
    static double[] priorHighest(List<Bar> bars, int period) {
        double[] out = new double[bars.size()];
        Arrays.fill(out, Double.NaN);
        for (int i = period; i < bars.size(); i++) {
            double max = Double.NEGATIVE_INFINITY;
            for (int k = i - period; k < i; k++) max = Math.max(max, bars.get(k).high());
            out[i] = max;
        }
        return out;
    }

    /** Lowest low of the {@code period} days BEFORE day i (day i excluded). */
    static double[] priorLowest(List<Bar> bars, int period) {
        double[] out = new double[bars.size()];
        Arrays.fill(out, Double.NaN);
        for (int i = period; i < bars.size(); i++) {
            double min = Double.POSITIVE_INFINITY;
            for (int k = i - period; k < i; k++) min = Math.min(min, bars.get(k).low());
            out[i] = min;
        }
        return out;
    }

    /** Wilder's ATR: seeded with the simple mean of the first {@code period} true ranges. */
    static double[] atr(List<Bar> bars, int period) {
        double[] out = new double[bars.size()];
        Arrays.fill(out, Double.NaN);
        double avg = 0;
        for (int i = 1; i < bars.size(); i++) {
            Bar b = bars.get(i);
            double prevClose = bars.get(i - 1).close();
            double tr = Math.max(b.high() - b.low(), Math.max(Math.abs(b.high() - prevClose), Math.abs(b.low() - prevClose)));
            if (i <= period) {
                avg += tr / period;
                if (i < period) continue;
            } else {
                avg = (avg * (period - 1) + tr) / period;
            }
            out[i] = avg;
        }
        return out;
    }

    /** Wilder's RSI; NaN until {@code period} changes exist. */
    static double[] rsi(double[] closes, int period) {
        double[] out = new double[closes.length];
        Arrays.fill(out, Double.NaN);
        double avgGain = 0, avgLoss = 0;
        for (int i = 1; i < closes.length; i++) {
            double change = closes[i] - closes[i - 1];
            double gain = Math.max(change, 0), loss = Math.max(-change, 0);
            if (i <= period) {
                avgGain += gain / period;
                avgLoss += loss / period;
                if (i < period) continue;
            } else {
                avgGain = (avgGain * (period - 1) + gain) / period;
                avgLoss = (avgLoss * (period - 1) + loss) / period;
            }
            out[i] = avgLoss == 0 ? (avgGain == 0 ? 50 : 100) : 100 - 100 / (1 + avgGain / avgLoss);
        }
        return out;
    }

    private double calculateStdDev(double[] values) {
        if (values.length < 2) return 0;
        double mean = Arrays.stream(values).average().orElse(0);
        double variance = Arrays.stream(values).map(v -> (v - mean) * (v - mean)).sum() / (values.length - 1);
        return Math.sqrt(variance);
    }

    private double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }

    private Map<String, Object> createErrorResult(String ticker, String strategy, String error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ticker", ticker);
        result.put("strategy", strategy);
        result.put("error", error);
        return result;
    }
}
