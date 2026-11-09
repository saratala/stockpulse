package com.stockpulse.agent.service;

import com.stockpulse.agent.model.IntradayPrice;
import com.stockpulse.agent.repository.IntradayPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Day Trading Backtest Service
 *
 * Event-driven backtest over 1-minute intraday bars (regular session, US/Eastern).
 * Rules that keep the results honest:
 * - Signals use only bars up to and including the signal bar's close
 * - Entries fill at the NEXT bar's open, plus slippage
 * - Stops/targets are checked against later bars' high/low; if both are touched
 *   in one bar the stop is assumed to fill first; gaps fill at the bar's open
 * - Every position is closed by END_OF_DAY_EXIT; no overnight holds
 * - Entries stop for the day once the daily loss limit is hit
 *
 * Strategies:
 * 1. Scalping - EMA(9)/EMA(21) cross in the direction of VWAP, tight target/stop
 * 2. Momentum - N-minute return beyond a threshold, confirmed by VWAP side
 * 3. Mean Reversion - RSI extreme while stretched away from VWAP
 * 4. Breakout - Close beyond the 15-minute opening range on above-average volume
 * 5. Combined - Momentum signal confirmed by a breakout or EMA cross
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DayTradingBacktestService {

    private final IntradayPriceRepository intradayPriceRepository;

    private static final LocalTime SESSION_OPEN = LocalTime.of(9, 30);
    private static final LocalTime SESSION_CLOSE = LocalTime.of(16, 0);
    private static final LocalTime OPENING_RANGE_END = LocalTime.of(9, 45);
    private static final LocalTime LAST_ENTRY = LocalTime.of(15, 30);
    private static final LocalTime END_OF_DAY_EXIT = LocalTime.of(15, 55);

    private static final double SLIPPAGE_PCT = 0.05;   // % per side
    private static final double COMMISSION_PER_TRADE = 0.0;
    private static final int MIN_TRADES_FOR_VERDICT = 30;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private record Bar(LocalDateTime time, double open, double high, double low, double close, long volume) {}

    private record Signal(int direction, String type) {}

    private static final class Position {
        int direction;
        String type;
        LocalDateTime entryTime;
        double entryPrice;
        long shares;
        double stopPrice;
        double targetPrice;
    }

    /**
     * Run day trading backtest with specified strategy
     */
    public Map<String, Object> runDayTradingBacktest(String ticker, BigDecimal initialCapital,
                                                      String strategy, Map<String, Double> overrides) {
        String strat = strategy == null ? "combined" : strategy.toLowerCase();
        if (!List.of("scalping", "momentum", "mean_reversion", "breakout", "combined").contains(strat)) {
            strat = "combined";
        }
        log.info("Running intraday backtest for {} with strategy: {}", ticker, strat);

        Map<String, Double> params = withDefaults(strat, overrides);

        List<IntradayPrice> rows = intradayPriceRepository.findByTickerAndTimestampBetween(
            ticker, LocalDateTime.of(2000, 1, 1, 0, 0), LocalDateTime.now().plusDays(1));

        // Group regular-session bars by trading day
        TreeMap<LocalDate, List<Bar>> days = new TreeMap<>();
        for (IntradayPrice p : rows) {
            LocalTime t = p.getTimestamp().toLocalTime();
            if (t.isBefore(SESSION_OPEN) || !t.isBefore(SESSION_CLOSE)) continue;
            days.computeIfAbsent(p.getTimestamp().toLocalDate(), d -> new ArrayList<>())
                .add(new Bar(p.getTimestamp(), p.getOpen().doubleValue(), p.getHigh().doubleValue(),
                    p.getLow().doubleValue(), p.getClose().doubleValue(), p.getVolume()));
        }
        days.values().removeIf(bars -> bars.size() < 60);

        if (days.isEmpty()) {
            return createErrorResult(ticker, strat,
                "No intraday data. Fetch 1-minute bars first (POST /massivedata/fetch/" + ticker + "/lastdays/30)");
        }

        return simulate(ticker, strat, days, initialCapital.doubleValue(), params);
    }

    private Map<String, Double> withDefaults(String strategy, Map<String, Double> overrides) {
        Map<String, Double> p = new LinkedHashMap<>();
        switch (strategy) {
            case "scalping" -> { p.put("takeProfit", 0.3); p.put("stopLoss", 0.2); p.put("maxHoldMinutes", 30.0); }
            case "momentum" -> { p.put("takeProfit", 1.0); p.put("stopLoss", 0.5); p.put("maxHoldMinutes", 120.0); }
            case "mean_reversion" -> { p.put("takeProfit", 0.5); p.put("stopLoss", 0.4); p.put("maxHoldMinutes", 60.0); }
            case "breakout" -> { p.put("takeProfit", 1.0); p.put("stopLoss", 0.5); p.put("maxHoldMinutes", 180.0); }
            default -> { p.put("takeProfit", 0.8); p.put("stopLoss", 0.4); p.put("maxHoldMinutes", 120.0); }
        }
        p.put("momentumThreshold", 0.4);   // % move over the lookback
        p.put("momentumLookback", 15.0);   // minutes
        p.put("rsiOversold", 30.0);
        p.put("rsiOverbought", 70.0);
        p.put("vwapStretch", 0.3);         // % away from VWAP for mean reversion
        p.put("volumeMultiplier", 1.5);    // breakout bar volume vs session average
        p.put("positionSize", 50.0);       // % of capital per trade
        p.put("maxTradesPerDay", 5.0);
        p.put("maxDailyLoss", 2.0);        // % of start-of-day capital
        if (overrides != null) {
            overrides.forEach((k, v) -> { if (v != null) p.put(k, v); });
        }
        return p;
    }

    private Map<String, Object> simulate(String ticker, String strategy, TreeMap<LocalDate, List<Bar>> days,
                                         double initialCapital, Map<String, Double> params) {
        double capital = initialCapital;
        double peakEquity = initialCapital;
        double maxDrawdown = 0;
        List<Map<String, Object>> trades = new ArrayList<>();
        List<Map<String, Object>> dailyPerformance = new ArrayList<>();
        List<Double> dailyReturns = new ArrayList<>();

        int maxTradesPerDay = params.get("maxTradesPerDay").intValue();

        for (Map.Entry<LocalDate, List<Bar>> day : days.entrySet()) {
            List<Bar> bars = day.getValue();
            int n = bars.size();
            double[] closes = bars.stream().mapToDouble(Bar::close).toArray();
            double[] vwap = vwap(bars);
            double[] ema9 = ema(closes, 9);
            double[] ema21 = ema(closes, 21);
            double[] rsi = rsi(closes, 14);
            double[] avgVolume = runningAverageVolume(bars);
            double orHigh = Double.NEGATIVE_INFINITY, orLow = Double.POSITIVE_INFINITY;
            for (Bar b : bars) {
                if (!b.time().toLocalTime().isBefore(OPENING_RANGE_END)) break;
                orHigh = Math.max(orHigh, b.high());
                orLow = Math.min(orLow, b.low());
            }

            double dayStartCapital = capital;
            int dayTrades = 0;
            boolean limitHit = false;
            boolean brokeOutUp = false, brokeOutDown = false;
            Position pos = null;
            Signal pending = null;

            for (int i = 0; i < n; i++) {
                Bar bar = bars.get(i);
                LocalTime t = bar.time().toLocalTime();

                // 1. Fill an entry signalled on the previous bar's close
                if (pending != null && pos == null && t.isBefore(LAST_ENTRY.plusMinutes(1))) {
                    pos = open(pending, bar, capital, params);
                }
                pending = null;

                // 2. Manage the open position against this bar
                if (pos != null) {
                    String reason = null;
                    double exitRaw = 0;
                    boolean isLong = pos.direction > 0;
                    double stopGap = isLong ? (bar.open() <= pos.stopPrice ? 1 : 0) : (bar.open() >= pos.stopPrice ? 1 : 0);
                    double targetGap = isLong ? (bar.open() >= pos.targetPrice ? 1 : 0) : (bar.open() <= pos.targetPrice ? 1 : 0);
                    boolean stopTouched = isLong ? bar.low() <= pos.stopPrice : bar.high() >= pos.stopPrice;
                    boolean targetTouched = isLong ? bar.high() >= pos.targetPrice : bar.low() <= pos.targetPrice;

                    if (stopGap > 0) { reason = "STOP_LOSS"; exitRaw = bar.open(); }
                    else if (targetGap > 0) { reason = "TAKE_PROFIT"; exitRaw = bar.open(); }
                    else if (stopTouched) { reason = "STOP_LOSS"; exitRaw = pos.stopPrice; }
                    else if (targetTouched) { reason = "TAKE_PROFIT"; exitRaw = pos.targetPrice; }
                    else if (!t.isBefore(END_OF_DAY_EXIT) || i == n - 1) { reason = "END_OF_DAY"; exitRaw = bar.close(); }
                    else if (ChronoUnit.MINUTES.between(pos.entryTime, bar.time()) >= params.get("maxHoldMinutes")) {
                        reason = "TIME_STOP"; exitRaw = bar.close();
                    }

                    if (reason != null) {
                        double exitPrice = exitRaw * (1 - pos.direction * SLIPPAGE_PCT / 100);
                        double pnl = pos.direction * (exitPrice - pos.entryPrice) * pos.shares - 2 * COMMISSION_PER_TRADE;
                        capital += pnl;
                        trades.add(tradeRecord(pos, bar.time(), exitPrice, pnl, reason));
                        dayTrades++;
                        pos = null;
                        if (capital - dayStartCapital <= -dayStartCapital * params.get("maxDailyLoss") / 100) {
                            limitHit = true;
                        }
                    }
                }

                // 3. Mark to market for drawdown
                double equity = capital + (pos == null ? 0 : pos.direction * (bar.close() - pos.entryPrice) * pos.shares);
                peakEquity = Math.max(peakEquity, equity);
                maxDrawdown = Math.max(maxDrawdown, (peakEquity - equity) / peakEquity * 100);

                // 4. Look for a new signal on this bar's close (filled next bar)
                boolean canEnter = pos == null && !limitHit && dayTrades < maxTradesPerDay
                    && !t.isBefore(OPENING_RANGE_END) && t.isBefore(LAST_ENTRY) && i < n - 1;
                if (!canEnter) continue;

                boolean breakoutUp = !brokeOutUp && closes[i] > orHigh && bar.volume() > params.get("volumeMultiplier") * avgVolume[i];
                boolean breakoutDown = !brokeOutDown && closes[i] < orLow && bar.volume() > params.get("volumeMultiplier") * avgVolume[i];
                int momentum = momentumSignal(closes, vwap, i, params);
                int emaCross = i > 0 && ema9[i - 1] <= ema21[i - 1] && ema9[i] > ema21[i] && closes[i] > vwap[i] ? 1
                    : i > 0 && ema9[i - 1] >= ema21[i - 1] && ema9[i] < ema21[i] && closes[i] < vwap[i] ? -1 : 0;

                pending = switch (strategy) {
                    case "scalping" -> emaCross != 0 ? new Signal(emaCross, "SCALP") : null;
                    case "momentum" -> momentum != 0 ? new Signal(momentum, "MOMENTUM") : null;
                    case "mean_reversion" -> {
                        double stretch = params.get("vwapStretch") / 100;
                        if (rsi[i] <= params.get("rsiOversold") && closes[i] < vwap[i] * (1 - stretch)) yield new Signal(1, "MEAN_REVERSION");
                        if (rsi[i] >= params.get("rsiOverbought") && closes[i] > vwap[i] * (1 + stretch)) yield new Signal(-1, "MEAN_REVERSION");
                        yield null;
                    }
                    case "breakout" -> breakoutUp ? new Signal(1, "BREAKOUT") : breakoutDown ? new Signal(-1, "BREAKOUT") : null;
                    default -> {
                        int confirm = (momentum > 0 && (breakoutUp || emaCross > 0)) ? 1
                            : (momentum < 0 && (breakoutDown || emaCross < 0)) ? -1 : 0;
                        yield confirm != 0 ? new Signal(confirm, "COMBINED") : null;
                    }
                };
                if (pending != null && pending.type().equals("BREAKOUT")) {
                    if (pending.direction() > 0) brokeOutUp = true; else brokeOutDown = true;
                }
            }

            double dayPnl = capital - dayStartCapital;
            dailyReturns.add(dayPnl / dayStartCapital);
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("date", day.getKey().toString());
            d.put("trades", dayTrades);
            d.put("pnl", round(dayPnl, 2));
            d.put("capital", round(capital, 2));
            d.put("drawdown", round((peakEquity - capital) / peakEquity * 100, 2) + "%");
            d.put("limitHit", limitHit);
            dailyPerformance.add(d);
        }

        return buildResult(ticker, strategy, days, initialCapital, capital, trades, dailyPerformance,
            dailyReturns, maxDrawdown, params);
    }

    private Position open(Signal signal, Bar bar, double capital, Map<String, Double> params) {
        double entry = bar.open() * (1 + signal.direction() * SLIPPAGE_PCT / 100);
        long shares = (long) Math.floor(capital * params.get("positionSize") / 100 / entry);
        if (shares <= 0) return null;
        Position p = new Position();
        p.direction = signal.direction();
        p.type = signal.type() + (signal.direction() > 0 ? "_LONG" : "_SHORT");
        p.entryTime = bar.time();
        p.entryPrice = entry;
        p.shares = shares;
        p.stopPrice = entry * (1 - signal.direction() * params.get("stopLoss") / 100);
        p.targetPrice = entry * (1 + signal.direction() * params.get("takeProfit") / 100);
        return p;
    }

    private int momentumSignal(double[] closes, double[] vwap, int i, Map<String, Double> params) {
        int lookback = params.get("momentumLookback").intValue();
        if (i < lookback) return 0;
        double change = (closes[i] - closes[i - lookback]) / closes[i - lookback] * 100;
        double threshold = params.get("momentumThreshold");
        if (change >= threshold && closes[i] > vwap[i]) return 1;
        if (change <= -threshold && closes[i] < vwap[i]) return -1;
        return 0;
    }

    private Map<String, Object> tradeRecord(Position pos, LocalDateTime exitTime, double exitPrice,
                                            double pnl, String exitReason) {
        double returnPct = pos.direction * (exitPrice - pos.entryPrice) / pos.entryPrice * 100;
        Map<String, Object> trade = new LinkedHashMap<>();
        trade.put("date", pos.entryTime.toLocalDate().toString());
        trade.put("type", pos.type);
        trade.put("direction", pos.direction > 0 ? "LONG" : "SHORT");
        trade.put("entryTime", pos.entryTime.format(TIME_FMT));
        trade.put("exitTime", exitTime.format(TIME_FMT));
        trade.put("holdingMinutes", ChronoUnit.MINUTES.between(pos.entryTime, exitTime));
        trade.put("entryPrice", round(pos.entryPrice, 4));
        trade.put("exitPrice", round(exitPrice, 4));
        trade.put("shares", (double) pos.shares);
        trade.put("pnl", round(pnl, 2));
        trade.put("returnPct", round(returnPct, 3) + "%");
        trade.put("exitReason", exitReason);
        return trade;
    }

    private Map<String, Object> buildResult(String ticker, String strategy, TreeMap<LocalDate, List<Bar>> days,
                                            double initialCapital, double finalCapital,
                                            List<Map<String, Object>> trades,
                                            List<Map<String, Object>> dailyPerformance,
                                            List<Double> dailyReturns, double maxDrawdown,
                                            Map<String, Double> params) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ticker", ticker);
        result.put("strategy", strategy);
        result.put("strategyType", "Day Trading");
        result.put("dataSource", "1-minute bars, regular session 09:30-16:00 ET");

        int tradingDays = days.size();
        result.put("testPeriod", Map.of(
            "start", days.firstKey().toString(),
            "end", days.lastKey().toString(),
            "tradingDays", tradingDays
        ));

        List<Double> pnls = trades.stream().map(t -> ((Number) t.get("pnl")).doubleValue()).toList();
        int wins = (int) pnls.stream().filter(p -> p > 0).count();
        int losses = pnls.size() - wins;
        double grossProfit = pnls.stream().filter(p -> p > 0).mapToDouble(Double::doubleValue).sum();
        double grossLoss = -pnls.stream().filter(p -> p <= 0).mapToDouble(Double::doubleValue).sum();

        double totalReturn = (finalCapital - initialCapital) / initialCapital * 100;
        double firstOpen = days.firstEntry().getValue().get(0).open();
        List<Bar> lastDay = days.lastEntry().getValue();
        double lastClose = lastDay.get(lastDay.size() - 1).close();
        double buyHoldReturn = (lastClose - firstOpen) / firstOpen * 100;
        double winRate = trades.isEmpty() ? 0 : (double) wins / trades.size() * 100;
        double avgWin = wins > 0 ? grossProfit / wins : 0;
        double avgLoss = losses > 0 ? grossLoss / losses : 0;
        double profitFactor = grossLoss > 0 ? grossProfit / grossLoss : (grossProfit > 0 ? 99.99 : 0);

        int maxConsecWins = 0, maxConsecLosses = 0, curWins = 0, curLosses = 0;
        for (double pnl : pnls) {
            if (pnl > 0) { curWins++; curLosses = 0; maxConsecWins = Math.max(maxConsecWins, curWins); }
            else { curLosses++; curWins = 0; maxConsecLosses = Math.max(maxConsecLosses, curLosses); }
        }

        // Annualised Sharpe from daily returns (risk-free rate ignored)
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
        performance.put("tradesPerDay", round((double) trades.size() / tradingDays, 2));
        performance.put("winningTrades", wins);
        performance.put("losingTrades", losses);
        performance.put("winRate", round(winRate, 1) + "%");
        performance.put("avgWin", "$" + round(avgWin, 2));
        performance.put("avgLoss", "$" + round(avgLoss, 2));
        performance.put("profitFactor", round(profitFactor, 2));
        performance.put("sharpeRatio", round(sharpeRatio, 2));
        performance.put("maxConsecutiveWins", maxConsecWins);
        performance.put("maxConsecutiveLosses", maxConsecLosses);
        result.put("performance", performance);

        result.put("parameters", params);
        result.put("costs", Map.of("slippagePctPerSide", SLIPPAGE_PCT, "commissionPerTrade", COMMISSION_PER_TRADE));
        result.put("exitReasonBreakdown", trades.stream()
            .collect(Collectors.groupingBy(t -> (String) t.get("exitReason"), TreeMap::new, Collectors.counting())));
        result.put("dailyPerformance", dailyPerformance);

        int recentCount = Math.min(20, trades.size());
        result.put("recentTrades", trades.subList(trades.size() - recentCount, trades.size()));

        String recommendation;
        if (trades.size() < MIN_TRADES_FOR_VERDICT) {
            recommendation = "INSUFFICIENT DATA - Only " + trades.size() + " trades over " + tradingDays
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
        if (tradingDays < 60) {
            result.put("warning", "Only " + tradingDays + " trading days of intraday data; "
                + "results are not statistically meaningful yet");
        }

        return result;
    }

    // ---- Indicators (computed per session, using bars up to i only) ----

    private double[] vwap(List<Bar> bars) {
        double[] out = new double[bars.size()];
        double pv = 0, vol = 0;
        for (int i = 0; i < bars.size(); i++) {
            Bar b = bars.get(i);
            double typical = (b.high() + b.low() + b.close()) / 3;
            pv += typical * b.volume();
            vol += b.volume();
            out[i] = vol > 0 ? pv / vol : b.close();
        }
        return out;
    }

    private double[] ema(double[] closes, int period) {
        double[] out = new double[closes.length];
        double k = 2.0 / (period + 1);
        for (int i = 0; i < closes.length; i++) {
            out[i] = i == 0 ? closes[0] : closes[i] * k + out[i - 1] * (1 - k);
        }
        return out;
    }

    /** Wilder's RSI; 50 until enough bars exist. */
    private double[] rsi(double[] closes, int period) {
        double[] out = new double[closes.length];
        Arrays.fill(out, 50);
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
            out[i] = avgLoss == 0 ? 100 : 100 - 100 / (1 + avgGain / avgLoss);
        }
        return out;
    }

    /** Average volume of the session's bars before bar i. */
    private double[] runningAverageVolume(List<Bar> bars) {
        double[] out = new double[bars.size()];
        double sum = 0;
        for (int i = 0; i < bars.size(); i++) {
            out[i] = i == 0 ? Double.MAX_VALUE : sum / i;
            sum += bars.get(i).volume();
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
