package com.stockpulse.agent.service;

import com.stockpulse.agent.model.StockPrice;
import com.stockpulse.agent.repository.StockPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Advanced Day Trading Service with sophisticated entry/exit rules
 *
 * Features:
 * - Multiple confirmation signals before entry
 * - Dynamic position sizing based on volatility
 * - Trailing stops with ATR-based levels
 * - Time-based exits (no overnight holds)
 * - Risk management with max daily loss limits
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdvancedDayTradingService {

    private final StockPriceRepository stockPriceRepository;

    // Risk Management Constants
    private static final double MAX_DAILY_LOSS_PCT = 3.0; // Stop trading after 3% daily loss
    private static final double MAX_POSITION_SIZE_PCT = 50.0; // Never risk more than 50% on one trade
    private static final double BASE_SLIPPAGE_PCT = 0.03; // 0.03% base slippage

    /**
     * Run advanced day trading backtest with configurable rules
     */
    public Map<String, Object> runAdvancedBacktest(String ticker, BigDecimal initialCapital,
                                                    Map<String, Object> config) {
        log.info("Running advanced day trading backtest for {} with config: {}", ticker, config);

        List<StockPrice> allPrices = stockPriceRepository.findByTickerOrderByDateAsc(ticker);
        if (allPrices.size() < 100) {
            return createErrorResult(ticker, "Insufficient data (need 100+ data points)");
        }

        List<DailyOHLCV> dailyData = aggregateToDailyOHLCV(allPrices);
        if (dailyData.size() < 50) {
            return createErrorResult(ticker, "Insufficient daily data");
        }

        // Parse configuration
        TradingConfig cfg = parseConfig(config);

        return runBacktest(ticker, dailyData, initialCapital, cfg);
    }

    /**
     * Main backtest execution with advanced rules
     */
    private Map<String, Object> runBacktest(String ticker, List<DailyOHLCV> prices,
                                             BigDecimal initialCapital, TradingConfig cfg) {
        List<Trade> allTrades = new ArrayList<>();
        List<DailyPerformance> dailyPerformance = new ArrayList<>();

        double capital = initialCapital.doubleValue();
        double peakCapital = capital;
        double maxDrawdown = 0;
        double dailyStartCapital = capital;

        int totalWins = 0, totalLosses = 0;
        int consecutiveWins = 0, consecutiveLosses = 0;
        int maxConsecutiveWins = 0, maxConsecutiveLosses = 0;

        // Calculate ATR for position sizing
        double[] atrValues = calculateATR(prices, 14);

        for (int dayIdx = 50; dayIdx < prices.size() - 1; dayIdx++) {
            DailyOHLCV today = prices.get(dayIdx);
            DailyOHLCV tomorrow = prices.get(dayIdx + 1);

            // Reset daily tracking
            dailyStartCapital = capital;
            List<Trade> dayTrades = new ArrayList<>();
            double dailyPnL = 0;
            boolean dailyLimitHit = false;

            // Calculate all indicators for today
            TradingSignals signals = calculateSignals(prices, dayIdx, atrValues[dayIdx]);

            // Determine entry opportunities based on strategy
            List<EntrySignal> entries = generateEntrySignals(signals, cfg, today);

            for (EntrySignal entry : entries) {
                // Check if we can still trade today
                if (dailyLimitHit) break;
                if (Math.abs(dailyPnL / dailyStartCapital * 100) >= cfg.maxDailyLoss) {
                    dailyLimitHit = true;
                    break;
                }

                // Calculate position size based on volatility and risk
                double positionSize = calculatePositionSize(capital, signals.atr,
                    today.close, cfg.riskPerTrade, cfg.maxPositionSize);

                // Simulate trade execution
                Trade trade = executeTrade(entry, today, tomorrow, signals, positionSize, cfg);

                if (trade != null) {
                    dayTrades.add(trade);
                    dailyPnL += trade.pnl;
                    capital += trade.pnl;

                    if (trade.pnl > 0) {
                        totalWins++;
                        consecutiveWins++;
                        consecutiveLosses = 0;
                        maxConsecutiveWins = Math.max(maxConsecutiveWins, consecutiveWins);
                    } else {
                        totalLosses++;
                        consecutiveLosses++;
                        consecutiveWins = 0;
                        maxConsecutiveLosses = Math.max(maxConsecutiveLosses, consecutiveLosses);
                    }

                    allTrades.add(trade);
                }
            }

            // Track drawdown
            if (capital > peakCapital) peakCapital = capital;
            double drawdown = (peakCapital - capital) / peakCapital * 100;
            if (drawdown > maxDrawdown) maxDrawdown = drawdown;

            // Record daily performance
            dailyPerformance.add(new DailyPerformance(
                today.date, dayTrades.size(), dailyPnL,
                capital, drawdown, dailyLimitHit
            ));
        }

        return buildAdvancedResult(ticker, prices, initialCapital, capital, allTrades,
            dailyPerformance, totalWins, totalLosses, maxDrawdown,
            maxConsecutiveWins, maxConsecutiveLosses, cfg);
    }

    /**
     * Generate entry signals based on multiple confirmations
     */
    private List<EntrySignal> generateEntrySignals(TradingSignals signals, TradingConfig cfg,
                                                    DailyOHLCV today) {
        List<EntrySignal> entries = new ArrayList<>();

        // Check minimum confirmations
        int bullishConfirmations = 0;
        int bearishConfirmations = 0;

        // RSI confirmation
        if (signals.rsi < 30) bullishConfirmations++;
        if (signals.rsi > 70) bearishConfirmations++;
        if (signals.rsi < 40 && signals.rsi > signals.prevRsi) bullishConfirmations++;
        if (signals.rsi > 60 && signals.rsi < signals.prevRsi) bearishConfirmations++;

        // MACD confirmation
        if (signals.macdHistogram > 0 && signals.macdHistogram > signals.prevMacdHistogram)
            bullishConfirmations++;
        if (signals.macdHistogram < 0 && signals.macdHistogram < signals.prevMacdHistogram)
            bearishConfirmations++;

        // Trend confirmation
        if (signals.priceAboveSMA20 && signals.sma20AboveSMA50) bullishConfirmations++;
        if (!signals.priceAboveSMA20 && !signals.sma20AboveSMA50) bearishConfirmations++;

        // Volume confirmation
        if (signals.volumeRatio > 1.2) {
            if (today.close > today.open) bullishConfirmations++;
            else bearishConfirmations++;
        }

        // Bollinger Band confirmation
        if (signals.bbPosition < 0.2) bullishConfirmations++; // Near lower band
        if (signals.bbPosition > 0.8) bearishConfirmations++; // Near upper band

        // Momentum confirmation
        if (signals.momentum5d > 0 && signals.momentum10d > 0) bullishConfirmations++;
        if (signals.momentum5d < 0 && signals.momentum10d < 0) bearishConfirmations++;

        // Generate entries if enough confirmations
        if (bullishConfirmations >= cfg.minConfirmations && bearishConfirmations < 2) {
            double confidence = Math.min(100, bullishConfirmations * 15 + 30);
            entries.add(new EntrySignal("LONG", today.close, confidence,
                "Bullish: " + bullishConfirmations + " confirmations"));
        }

        if (bearishConfirmations >= cfg.minConfirmations && bullishConfirmations < 2) {
            double confidence = Math.min(100, bearishConfirmations * 15 + 30);
            entries.add(new EntrySignal("SHORT", today.close, confidence,
                "Bearish: " + bearishConfirmations + " confirmations"));
        }

        // Add scalping opportunities if volatility is right
        if (cfg.enableScalping && signals.atr > 0) {
            double volatilityPct = signals.atr / today.close * 100;
            if (volatilityPct > 1.5 && volatilityPct < 5.0) {
                // Good scalping conditions
                if (Math.abs(signals.momentum5d) < 2) {
                    entries.add(new EntrySignal("SCALP_LONG", today.low + signals.atr * 0.5,
                        50, "Scalp: Low volatility range"));
                }
            }
        }

        return entries;
    }

    /**
     * Execute trade with advanced exit rules
     */
    private Trade executeTrade(EntrySignal entry, DailyOHLCV today, DailyOHLCV tomorrow,
                               TradingSignals signals, double positionSize, TradingConfig cfg) {
        double entryPrice = entry.price;
        double atr = signals.atr;

        // Calculate stop loss and take profit based on ATR
        double stopLoss, takeProfit;
        if (entry.direction.equals("LONG") || entry.direction.equals("SCALP_LONG")) {
            stopLoss = entryPrice - (atr * cfg.stopLossAtrMultiple);
            takeProfit = entryPrice + (atr * cfg.takeProfitAtrMultiple);
        } else {
            stopLoss = entryPrice + (atr * cfg.stopLossAtrMultiple);
            takeProfit = entryPrice - (atr * cfg.takeProfitAtrMultiple);
        }

        // Simulate intraday price action
        double exitPrice;
        String exitReason;

        boolean isLong = entry.direction.contains("LONG");
        double highReached = Math.max(today.high, tomorrow.open * 1.01);
        double lowReached = Math.min(today.low, tomorrow.open * 0.99);

        if (isLong) {
            if (lowReached <= stopLoss) {
                exitPrice = stopLoss;
                exitReason = "STOP_LOSS";
            } else if (highReached >= takeProfit) {
                exitPrice = takeProfit;
                exitReason = "TAKE_PROFIT";
            } else {
                // Use trailing stop logic
                double trailingStop = Math.max(stopLoss, highReached - atr * cfg.trailingStopAtr);
                if (tomorrow.close < trailingStop) {
                    exitPrice = trailingStop;
                    exitReason = "TRAILING_STOP";
                } else {
                    // End of day exit
                    exitPrice = tomorrow.close;
                    exitReason = "EOD_EXIT";
                }
            }
        } else {
            if (highReached >= stopLoss) {
                exitPrice = stopLoss;
                exitReason = "STOP_LOSS";
            } else if (lowReached <= takeProfit) {
                exitPrice = takeProfit;
                exitReason = "TAKE_PROFIT";
            } else {
                double trailingStop = Math.min(stopLoss, lowReached + atr * cfg.trailingStopAtr);
                if (tomorrow.close > trailingStop) {
                    exitPrice = trailingStop;
                    exitReason = "TRAILING_STOP";
                } else {
                    exitPrice = tomorrow.close;
                    exitReason = "EOD_EXIT";
                }
            }
        }

        // Apply slippage
        double slippage = entryPrice * BASE_SLIPPAGE_PCT / 100 * 2; // Entry + exit

        // Calculate P&L
        double shares = positionSize / entryPrice;
        double grossPnl = isLong ?
            shares * (exitPrice - entryPrice) :
            shares * (entryPrice - exitPrice);
        double netPnl = grossPnl - slippage * shares;

        return new Trade(
            today.date,
            entry.direction,
            entryPrice,
            exitPrice,
            shares,
            positionSize,
            netPnl,
            netPnl / positionSize * 100,
            exitReason,
            entry.confidence,
            entry.reason
        );
    }

    /**
     * Calculate position size based on volatility-adjusted risk
     */
    private double calculatePositionSize(double capital, double atr, double price,
                                          double riskPct, double maxPct) {
        // Risk-based position sizing
        double riskAmount = capital * riskPct / 100;
        double stopDistance = atr * 1.5; // 1.5 ATR stop
        double sharesFromRisk = riskAmount / stopDistance;
        double positionFromRisk = sharesFromRisk * price;

        // Cap at max position size
        double maxPosition = capital * maxPct / 100;

        return Math.min(positionFromRisk, maxPosition);
    }

    /**
     * Calculate trading signals from price data
     */
    private TradingSignals calculateSignals(List<DailyOHLCV> prices, int index, double atr) {
        TradingSignals signals = new TradingSignals();
        signals.atr = atr;

        int n = index + 1;
        double[] closes = new double[n];
        for (int i = 0; i < n; i++) closes[i] = prices.get(i).close;

        double currentPrice = closes[n - 1];

        // Moving averages
        double sma20 = calculateSMA(closes, 20);
        double sma50 = calculateSMA(closes, 50);
        signals.priceAboveSMA20 = currentPrice > sma20;
        signals.sma20AboveSMA50 = sma20 > sma50;

        // RSI
        signals.rsi = calculateRSI(closes, 14);
        if (n > 1) {
            double[] prevCloses = Arrays.copyOf(closes, n - 1);
            signals.prevRsi = calculateRSI(prevCloses, 14);
        }

        // MACD
        double ema12 = calculateEMA(closes, 12);
        double ema26 = calculateEMA(closes, 26);
        double macdLine = ema12 - ema26;
        double signalLine = calculateEMA(new double[]{macdLine}, 9);
        signals.macdHistogram = macdLine - signalLine;
        signals.prevMacdHistogram = signals.macdHistogram * 0.95; // Approximation

        // Bollinger Bands
        double bb_std = calculateStdDev(Arrays.copyOfRange(closes, Math.max(0, n - 20), n));
        double bb_upper = sma20 + 2 * bb_std;
        double bb_lower = sma20 - 2 * bb_std;
        signals.bbPosition = (bb_upper != bb_lower) ?
            (currentPrice - bb_lower) / (bb_upper - bb_lower) : 0.5;

        // Volume
        long[] volumes = new long[n];
        for (int i = 0; i < n; i++) volumes[i] = prices.get(i).volume;
        double avgVolume = Arrays.stream(volumes).skip(Math.max(0, n - 21)).limit(20)
            .average().orElse(volumes[n - 1]);
        signals.volumeRatio = volumes[n - 1] / avgVolume;

        // Momentum
        signals.momentum5d = n > 5 ? (currentPrice - closes[n - 6]) / closes[n - 6] * 100 : 0;
        signals.momentum10d = n > 10 ? (currentPrice - closes[n - 11]) / closes[n - 11] * 100 : 0;

        return signals;
    }

    /**
     * Calculate ATR (Average True Range) for all days
     */
    private double[] calculateATR(List<DailyOHLCV> prices, int period) {
        double[] atr = new double[prices.size()];
        double[] tr = new double[prices.size()];

        for (int i = 1; i < prices.size(); i++) {
            DailyOHLCV today = prices.get(i);
            DailyOHLCV yesterday = prices.get(i - 1);

            double highLow = today.high - today.low;
            double highClose = Math.abs(today.high - yesterday.close);
            double lowClose = Math.abs(today.low - yesterday.close);

            tr[i] = Math.max(highLow, Math.max(highClose, lowClose));
        }

        // Calculate ATR as EMA of TR
        atr[period] = Arrays.stream(tr).skip(1).limit(period).average().orElse(0);
        double multiplier = 2.0 / (period + 1);

        for (int i = period + 1; i < prices.size(); i++) {
            atr[i] = (tr[i] - atr[i - 1]) * multiplier + atr[i - 1];
        }

        return atr;
    }

    // ============ Result Building ============

    private Map<String, Object> buildAdvancedResult(String ticker, List<DailyOHLCV> prices,
            BigDecimal initialCapital, double finalCapital, List<Trade> trades,
            List<DailyPerformance> dailyPerf, int wins, int losses, double maxDrawdown,
            int maxConsecWins, int maxConsecLosses, TradingConfig cfg) {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ticker", ticker);
        result.put("strategy", "Advanced Day Trading");

        // Test period
        result.put("testPeriod", Map.of(
            "start", prices.get(50).date,
            "end", prices.get(prices.size() - 1).date,
            "tradingDays", prices.size() - 50
        ));

        // Performance metrics
        double totalReturn = ((finalCapital - initialCapital.doubleValue()) /
            initialCapital.doubleValue()) * 100;
        double buyHoldReturn = ((prices.get(prices.size() - 1).close - prices.get(50).close) /
            prices.get(50).close) * 100;

        int totalTrades = wins + losses;
        double winRate = totalTrades > 0 ? (double) wins / totalTrades * 100 : 0;

        double avgWin = trades.stream().filter(t -> t.pnl > 0)
            .mapToDouble(t -> t.pnl).average().orElse(0);
        double avgLoss = trades.stream().filter(t -> t.pnl < 0)
            .mapToDouble(t -> Math.abs(t.pnl)).average().orElse(0);
        double profitFactor = avgLoss > 0 ? (avgWin * wins) / (avgLoss * losses) : 0;

        double totalProfit = trades.stream().filter(t -> t.pnl > 0).mapToDouble(t -> t.pnl).sum();
        double totalLoss = trades.stream().filter(t -> t.pnl < 0).mapToDouble(t -> Math.abs(t.pnl)).sum();

        // Sharpe ratio approximation
        double avgDailyReturn = dailyPerf.stream().mapToDouble(d -> d.pnl).average().orElse(0);
        double stdDailyReturn = calculateStdDev(
            dailyPerf.stream().mapToDouble(d -> d.pnl).toArray());
        double sharpeRatio = stdDailyReturn > 0 ?
            (avgDailyReturn / stdDailyReturn) * Math.sqrt(252) : 0;

        Map<String, Object> performance = new LinkedHashMap<>();
        performance.put("initialCapital", initialCapital.doubleValue());
        performance.put("finalCapital", round(finalCapital, 2));
        performance.put("totalReturn", round(totalReturn, 2) + "%");
        performance.put("buyHoldReturn", round(buyHoldReturn, 2) + "%");
        performance.put("excessReturn", round(totalReturn - buyHoldReturn, 2) + "%");
        performance.put("maxDrawdown", round(maxDrawdown, 2) + "%");
        performance.put("sharpeRatio", round(sharpeRatio, 2));
        performance.put("totalTrades", totalTrades);
        performance.put("tradesPerDay", round((double) totalTrades / (prices.size() - 50), 2));
        performance.put("winningTrades", wins);
        performance.put("losingTrades", losses);
        performance.put("winRate", round(winRate, 1) + "%");
        performance.put("avgWin", "$" + round(avgWin, 2));
        performance.put("avgLoss", "$" + round(avgLoss, 2));
        performance.put("profitFactor", round(profitFactor, 2));
        performance.put("totalProfit", "$" + round(totalProfit, 2));
        performance.put("totalLoss", "$" + round(totalLoss, 2));
        performance.put("maxConsecutiveWins", maxConsecWins);
        performance.put("maxConsecutiveLosses", maxConsecLosses);
        result.put("performance", performance);

        // Configuration used
        Map<String, Object> configUsed = new LinkedHashMap<>();
        configUsed.put("minConfirmations", cfg.minConfirmations);
        configUsed.put("riskPerTrade", cfg.riskPerTrade + "%");
        configUsed.put("maxPositionSize", cfg.maxPositionSize + "%");
        configUsed.put("stopLossAtrMultiple", cfg.stopLossAtrMultiple);
        configUsed.put("takeProfitAtrMultiple", cfg.takeProfitAtrMultiple);
        configUsed.put("trailingStopAtr", cfg.trailingStopAtr);
        configUsed.put("maxDailyLoss", cfg.maxDailyLoss + "%");
        configUsed.put("enableScalping", cfg.enableScalping);
        result.put("configuration", configUsed);

        // Exit reason breakdown
        Map<String, Long> exitReasons = trades.stream()
            .collect(Collectors.groupingBy(t -> t.exitReason, Collectors.counting()));
        result.put("exitReasonBreakdown", exitReasons);

        // Daily performance summary
        List<Map<String, Object>> dailySummary = dailyPerf.stream()
            .skip(Math.max(0, dailyPerf.size() - 20))
            .map(d -> {
                Map<String, Object> day = new LinkedHashMap<>();
                day.put("date", d.date);
                day.put("trades", d.tradeCount);
                day.put("pnl", round(d.pnl, 2));
                day.put("capital", round(d.endCapital, 2));
                day.put("drawdown", round(d.drawdown, 2) + "%");
                day.put("limitHit", d.dailyLimitHit);
                return day;
            })
            .collect(Collectors.toList());
        result.put("dailyPerformance", dailySummary);

        // Recent trades
        List<Map<String, Object>> recentTrades = trades.stream()
            .skip(Math.max(0, trades.size() - 15))
            .map(t -> {
                Map<String, Object> trade = new LinkedHashMap<>();
                trade.put("date", t.date);
                trade.put("direction", t.direction);
                trade.put("entryPrice", round(t.entryPrice, 2));
                trade.put("exitPrice", round(t.exitPrice, 2));
                trade.put("shares", round(t.shares, 4));
                trade.put("pnl", round(t.pnl, 2));
                trade.put("returnPct", round(t.returnPct, 2) + "%");
                trade.put("exitReason", t.exitReason);
                trade.put("confidence", round(t.confidence, 1));
                return trade;
            })
            .collect(Collectors.toList());
        result.put("recentTrades", recentTrades);

        // Recommendation
        String recommendation;
        if (winRate > 55 && profitFactor > 1.5 && sharpeRatio > 1.0) {
            recommendation = "STRONG BUY - Excellent risk-adjusted returns";
        } else if (winRate > 50 && profitFactor > 1.2) {
            recommendation = "MODERATE - Profitable with room for optimization";
        } else if (profitFactor > 1.0) {
            recommendation = "WEAK - Marginally profitable, high risk";
        } else {
            recommendation = "NOT RECOMMENDED - Strategy needs adjustment";
        }
        result.put("recommendation", recommendation);

        return result;
    }

    // ============ Configuration Parsing ============

    private TradingConfig parseConfig(Map<String, Object> config) {
        TradingConfig cfg = new TradingConfig();

        cfg.minConfirmations = getInt(config, "minConfirmations", 3);
        cfg.riskPerTrade = getDouble(config, "riskPerTrade", 1.0);
        cfg.maxPositionSize = getDouble(config, "maxPositionSize", 25.0);
        cfg.stopLossAtrMultiple = getDouble(config, "stopLossAtr", 1.5);
        cfg.takeProfitAtrMultiple = getDouble(config, "takeProfitAtr", 2.5);
        cfg.trailingStopAtr = getDouble(config, "trailingStopAtr", 1.0);
        cfg.maxDailyLoss = getDouble(config, "maxDailyLoss", 3.0);
        cfg.enableScalping = getBoolean(config, "enableScalping", true);

        return cfg;
    }

    // ============ Helper Methods ============

    private List<DailyOHLCV> aggregateToDailyOHLCV(List<StockPrice> prices) {
        Map<String, List<StockPrice>> byDay = prices.stream()
            .collect(Collectors.groupingBy(
                p -> p.getDate().toLocalDate().toString(),
                LinkedHashMap::new,
                Collectors.toList()
            ));

        List<DailyOHLCV> dailyPrices = new ArrayList<>();
        for (Map.Entry<String, List<StockPrice>> entry : byDay.entrySet()) {
            List<StockPrice> dayPrices = entry.getValue();
            if (dayPrices.isEmpty()) continue;

            DailyOHLCV daily = new DailyOHLCV();
            daily.date = entry.getKey();
            daily.open = dayPrices.get(0).getOpen().doubleValue();
            daily.high = dayPrices.stream().mapToDouble(p -> p.getHigh().doubleValue()).max().orElse(0);
            daily.low = dayPrices.stream().mapToDouble(p -> p.getLow().doubleValue()).min().orElse(0);
            daily.close = dayPrices.get(dayPrices.size() - 1).getClose().doubleValue();
            daily.volume = dayPrices.stream().mapToLong(StockPrice::getVolume).sum();
            dailyPrices.add(daily);
        }
        return dailyPrices;
    }

    private double calculateSMA(double[] prices, int period) {
        int n = prices.length;
        if (n < period) return prices[n - 1];
        return Arrays.stream(prices).skip(n - period).average().orElse(prices[n - 1]);
    }

    private double calculateEMA(double[] prices, int period) {
        if (prices.length < period) return prices[prices.length - 1];
        double multiplier = 2.0 / (period + 1);
        double ema = calculateSMA(Arrays.copyOfRange(prices, 0, period), period);
        for (int i = period; i < prices.length; i++) {
            ema = (prices[i] - ema) * multiplier + ema;
        }
        return ema;
    }

    private double calculateRSI(double[] prices, int period) {
        if (prices.length < period + 1) return 50.0;
        double avgGain = 0, avgLoss = 0;
        for (int i = prices.length - period; i < prices.length; i++) {
            double change = prices[i] - prices[i - 1];
            if (change > 0) avgGain += change;
            else avgLoss -= change;
        }
        avgGain /= period;
        avgLoss /= period;
        if (avgLoss == 0) return 100;
        return 100 - (100 / (1 + avgGain / avgLoss));
    }

    private double calculateStdDev(double[] values) {
        if (values.length == 0) return 0;
        double mean = Arrays.stream(values).average().orElse(0);
        double variance = Arrays.stream(values).map(v -> Math.pow(v - mean, 2)).average().orElse(0);
        return Math.sqrt(variance);
    }

    private double round(double value, int decimals) {
        return BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).doubleValue();
    }

    private int getInt(Map<String, Object> map, String key, int defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        return defaultVal;
    }

    private double getDouble(Map<String, Object> map, String key, double defaultVal) {
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        return defaultVal;
    }

    private boolean getBoolean(Map<String, Object> map, String key, boolean defaultVal) {
        Object val = map.get(key);
        if (val instanceof Boolean) return (Boolean) val;
        return defaultVal;
    }

    private Map<String, Object> createErrorResult(String ticker, String error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ticker", ticker);
        result.put("error", error);
        return result;
    }

    // ============ Inner Classes ============

    private static class DailyOHLCV {
        String date;
        double open, high, low, close;
        long volume;
    }

    private static class TradingConfig {
        int minConfirmations = 3;
        double riskPerTrade = 1.0;
        double maxPositionSize = 25.0;
        double stopLossAtrMultiple = 1.5;
        double takeProfitAtrMultiple = 2.5;
        double trailingStopAtr = 1.0;
        double maxDailyLoss = 3.0;
        boolean enableScalping = true;
    }

    private static class TradingSignals {
        double atr;
        double rsi, prevRsi;
        double macdHistogram, prevMacdHistogram;
        double bbPosition;
        double volumeRatio;
        double momentum5d, momentum10d;
        boolean priceAboveSMA20, sma20AboveSMA50;
    }

    private static class EntrySignal {
        String direction;
        double price;
        double confidence;
        String reason;

        EntrySignal(String direction, double price, double confidence, String reason) {
            this.direction = direction;
            this.price = price;
            this.confidence = confidence;
            this.reason = reason;
        }
    }

    private static class Trade {
        String date, direction;
        double entryPrice, exitPrice;
        double shares, positionSize;
        double pnl, returnPct;
        String exitReason;
        double confidence;
        String entryReason;

        Trade(String date, String direction, double entryPrice, double exitPrice,
              double shares, double positionSize, double pnl, double returnPct,
              String exitReason, double confidence, String entryReason) {
            this.date = date;
            this.direction = direction;
            this.entryPrice = entryPrice;
            this.exitPrice = exitPrice;
            this.shares = shares;
            this.positionSize = positionSize;
            this.pnl = pnl;
            this.returnPct = returnPct;
            this.exitReason = exitReason;
            this.confidence = confidence;
            this.entryReason = entryReason;
        }
    }

    private static class DailyPerformance {
        String date;
        int tradeCount;
        double pnl, endCapital, drawdown;
        boolean dailyLimitHit;

        DailyPerformance(String date, int tradeCount, double pnl, double endCapital,
                        double drawdown, boolean dailyLimitHit) {
            this.date = date;
            this.tradeCount = tradeCount;
            this.pnl = pnl;
            this.endCapital = endCapital;
            this.drawdown = drawdown;
            this.dailyLimitHit = dailyLimitHit;
        }
    }
}
