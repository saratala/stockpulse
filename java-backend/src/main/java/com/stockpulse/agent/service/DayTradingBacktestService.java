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
 * Day Trading Backtest Service
 *
 * Implements multiple day trading strategies optimized for frequent trading:
 * 1. Scalping - Quick trades on small price movements
 * 2. Momentum - Ride intraday trends
 * 3. Mean Reversion - Trade bounces from extremes
 * 4. Breakout - Trade price breakouts with volume confirmation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DayTradingBacktestService {

    private final StockPriceRepository stockPriceRepository;

    // Trading parameters
    private static final double COMMISSION_PER_TRADE = 0.0; // Assume commission-free
    private static final double SLIPPAGE_PCT = 0.05; // 0.05% slippage per trade

    /**
     * Run day trading backtest with specified strategy
     */
    public Map<String, Object> runDayTradingBacktest(String ticker, BigDecimal initialCapital,
                                                      String strategy, Map<String, Double> params) {
        log.info("Running day trading backtest for {} with strategy: {}", ticker, strategy);

        List<StockPrice> allPrices = stockPriceRepository.findByTickerOrderByDateAsc(ticker);

        if (allPrices.size() < 50) {
            return createErrorResult(ticker, strategy, "Insufficient data");
        }

        // Aggregate to daily OHLCV
        List<DailyOHLCV> dailyData = aggregateToDailyOHLCV(allPrices);

        if (dailyData.size() < 30) {
            return createErrorResult(ticker, strategy, "Insufficient daily data");
        }

        // Run the selected strategy
        return switch (strategy.toLowerCase()) {
            case "scalping" -> runScalpingStrategy(ticker, dailyData, initialCapital, params);
            case "momentum" -> runMomentumStrategy(ticker, dailyData, initialCapital, params);
            case "mean_reversion" -> runMeanReversionStrategy(ticker, dailyData, initialCapital, params);
            case "breakout" -> runBreakoutStrategy(ticker, dailyData, initialCapital, params);
            case "combined" -> runCombinedStrategy(ticker, dailyData, initialCapital, params);
            default -> runCombinedStrategy(ticker, dailyData, initialCapital, params);
        };
    }

    /**
     * SCALPING STRATEGY
     * - Trade on small intraday price movements
     * - Quick entries and exits
     * - Uses tight stop losses and take profits
     */
    private Map<String, Object> runScalpingStrategy(String ticker, List<DailyOHLCV> prices,
                                                     BigDecimal initialCapital, Map<String, Double> params) {
        double takeProfitPct = params.getOrDefault("takeProfit", 0.5); // 0.5% profit target
        double stopLossPct = params.getOrDefault("stopLoss", 0.3); // 0.3% stop loss
        double entryThreshold = params.getOrDefault("entryThreshold", 0.2); // 0.2% move triggers entry

        List<Map<String, Object>> trades = new ArrayList<>();
        double capital = initialCapital.doubleValue();
        double peakCapital = capital;
        double maxDrawdown = 0;
        int winningTrades = 0;
        int losingTrades = 0;

        // Simulate multiple trades per day based on daily range
        for (int i = 20; i < prices.size(); i++) {
            DailyOHLCV today = prices.get(i);
            double dailyRange = (today.high - today.low) / today.open * 100;

            // Number of potential scalp trades based on daily volatility
            int potentialTrades = Math.min(5, (int)(dailyRange / (takeProfitPct + stopLossPct)));

            if (potentialTrades < 1) continue;

            // Calculate success probability based on trend and momentum
            double[] indicators = calculateIndicators(prices, i);
            double trendStrength = indicators[0];
            double momentum = indicators[1];
            double volatility = indicators[2];

            // Scalping works better in ranging markets
            double winProbability = 0.5 + (0.1 * (1 - Math.abs(trendStrength))) + (momentum * 0.1);
            winProbability = Math.max(0.35, Math.min(0.65, winProbability));

            for (int t = 0; t < potentialTrades; t++) {
                boolean isWin = Math.random() < winProbability;
                double tradeResult;

                if (isWin) {
                    tradeResult = takeProfitPct / 100;
                    winningTrades++;
                } else {
                    tradeResult = -stopLossPct / 100;
                    losingTrades++;
                }

                // Apply slippage
                tradeResult -= SLIPPAGE_PCT / 100;

                double positionSize = capital * 0.25; // Use 25% of capital per trade
                double pnl = positionSize * tradeResult;
                capital += pnl;

                trades.add(createTradeRecord(today.date, isWin ? "SCALP_WIN" : "SCALP_LOSS",
                    today.close, positionSize, pnl, winProbability * 100));

                // Track drawdown
                if (capital > peakCapital) peakCapital = capital;
                double drawdown = (peakCapital - capital) / peakCapital * 100;
                if (drawdown > maxDrawdown) maxDrawdown = drawdown;
            }
        }

        return buildResult(ticker, "Scalping", prices, initialCapital, capital, trades,
                          winningTrades, losingTrades, maxDrawdown, params);
    }

    /**
     * MOMENTUM STRATEGY
     * - Follow intraday trends
     * - Enter on breakouts, exit on reversals
     * - Larger position sizes, fewer trades
     */
    private Map<String, Object> runMomentumStrategy(String ticker, List<DailyOHLCV> prices,
                                                     BigDecimal initialCapital, Map<String, Double> params) {
        double momentumThreshold = params.getOrDefault("momentumThreshold", 1.5); // 1.5% move
        double trailingStopPct = params.getOrDefault("trailingStop", 1.0); // 1% trailing stop

        List<Map<String, Object>> trades = new ArrayList<>();
        double capital = initialCapital.doubleValue();
        double peakCapital = capital;
        double maxDrawdown = 0;
        int winningTrades = 0;
        int losingTrades = 0;

        for (int i = 20; i < prices.size(); i++) {
            DailyOHLCV today = prices.get(i);
            DailyOHLCV yesterday = prices.get(i - 1);

            double[] indicators = calculateIndicators(prices, i);
            double trendStrength = indicators[0];
            double momentum = indicators[1];
            double rsi = indicators[3];

            // Check for momentum entry
            double gapPct = (today.open - yesterday.close) / yesterday.close * 100;
            double intradayMove = (today.close - today.open) / today.open * 100;

            boolean bullishMomentum = gapPct > 0.3 && trendStrength > 0.3 && momentum > 0;
            boolean bearishMomentum = gapPct < -0.3 && trendStrength < -0.3 && momentum < 0;

            if (bullishMomentum || bearishMomentum) {
                // Simulate riding the momentum
                double expectedMove = bullishMomentum ?
                    Math.min(momentumThreshold * 2, (today.high - today.open) / today.open * 100) :
                    Math.max(-momentumThreshold * 2, (today.low - today.open) / today.open * 100);

                // Apply trailing stop logic
                double actualMove = expectedMove * (0.5 + Math.random() * 0.5); // Capture 50-100% of move
                actualMove -= SLIPPAGE_PCT / 100 * 2; // Entry and exit slippage

                double positionSize = capital * 0.4; // 40% position for momentum trades
                double pnl = positionSize * (actualMove / 100);
                capital += pnl;

                boolean isWin = pnl > 0;
                if (isWin) winningTrades++; else losingTrades++;

                trades.add(createTradeRecord(today.date,
                    bullishMomentum ? "MOMENTUM_LONG" : "MOMENTUM_SHORT",
                    today.open, positionSize, pnl, Math.abs(trendStrength) * 100));

                // Track drawdown
                if (capital > peakCapital) peakCapital = capital;
                double drawdown = (peakCapital - capital) / peakCapital * 100;
                if (drawdown > maxDrawdown) maxDrawdown = drawdown;
            }
        }

        return buildResult(ticker, "Momentum", prices, initialCapital, capital, trades,
                          winningTrades, losingTrades, maxDrawdown, params);
    }

    /**
     * MEAN REVERSION STRATEGY
     * - Trade bounces from oversold/overbought levels
     * - Best for range-bound markets
     * - Uses RSI and Bollinger Bands
     */
    private Map<String, Object> runMeanReversionStrategy(String ticker, List<DailyOHLCV> prices,
                                                          BigDecimal initialCapital, Map<String, Double> params) {
        double rsiOversold = params.getOrDefault("rsiOversold", 30.0);
        double rsiOverbought = params.getOrDefault("rsiOverbought", 70.0);
        double targetReversion = params.getOrDefault("targetReversion", 1.5); // 1.5% reversion target

        List<Map<String, Object>> trades = new ArrayList<>();
        double capital = initialCapital.doubleValue();
        double peakCapital = capital;
        double maxDrawdown = 0;
        int winningTrades = 0;
        int losingTrades = 0;

        for (int i = 20; i < prices.size() - 1; i++) {
            DailyOHLCV today = prices.get(i);
            DailyOHLCV tomorrow = prices.get(i + 1);

            double[] indicators = calculateIndicators(prices, i);
            double trendStrength = indicators[0];
            double rsi = indicators[3];
            double bbPosition = indicators[4]; // 0 = lower band, 1 = upper band

            boolean oversold = rsi < rsiOversold || bbPosition < 0.1;
            boolean overbought = rsi > rsiOverbought || bbPosition > 0.9;

            if (oversold || overbought) {
                // Calculate expected reversion
                double actualMove = (tomorrow.close - today.close) / today.close * 100;

                // Check if reversion happened in right direction
                boolean correctDirection = (oversold && actualMove > 0) || (overbought && actualMove < 0);

                double capturedMove = correctDirection ?
                    Math.min(Math.abs(actualMove), targetReversion) : -Math.abs(actualMove) * 0.5;

                capturedMove -= SLIPPAGE_PCT / 100 * 2;

                double positionSize = capital * 0.3; // 30% position
                double pnl = positionSize * (capturedMove / 100);
                capital += pnl;

                boolean isWin = pnl > 0;
                if (isWin) winningTrades++; else losingTrades++;

                trades.add(createTradeRecord(today.date,
                    oversold ? "REVERSION_LONG" : "REVERSION_SHORT",
                    today.close, positionSize, pnl, Math.abs(50 - rsi)));

                if (capital > peakCapital) peakCapital = capital;
                double drawdown = (peakCapital - capital) / peakCapital * 100;
                if (drawdown > maxDrawdown) maxDrawdown = drawdown;
            }
        }

        return buildResult(ticker, "Mean Reversion", prices, initialCapital, capital, trades,
                          winningTrades, losingTrades, maxDrawdown, params);
    }

    /**
     * BREAKOUT STRATEGY
     * - Trade price breakouts with volume confirmation
     * - Uses support/resistance levels
     */
    private Map<String, Object> runBreakoutStrategy(String ticker, List<DailyOHLCV> prices,
                                                     BigDecimal initialCapital, Map<String, Double> params) {
        double breakoutThreshold = params.getOrDefault("breakoutThreshold", 1.0); // 1% breakout
        double volumeMultiplier = params.getOrDefault("volumeMultiplier", 1.5); // 1.5x average volume

        List<Map<String, Object>> trades = new ArrayList<>();
        double capital = initialCapital.doubleValue();
        double peakCapital = capital;
        double maxDrawdown = 0;
        int winningTrades = 0;
        int losingTrades = 0;

        for (int i = 20; i < prices.size() - 1; i++) {
            DailyOHLCV today = prices.get(i);
            DailyOHLCV tomorrow = prices.get(i + 1);

            // Calculate resistance (20-day high) and support (20-day low)
            double resistance = 0, support = Double.MAX_VALUE;
            double avgVolume = 0;
            for (int j = i - 20; j < i; j++) {
                resistance = Math.max(resistance, prices.get(j).high);
                support = Math.min(support, prices.get(j).low);
                avgVolume += prices.get(j).volume;
            }
            avgVolume /= 20;

            // Check for breakout
            boolean breakoutUp = today.close > resistance && today.volume > avgVolume * volumeMultiplier;
            boolean breakoutDown = today.close < support && today.volume > avgVolume * volumeMultiplier;

            if (breakoutUp || breakoutDown) {
                // Follow-through probability based on volume and momentum
                double[] indicators = calculateIndicators(prices, i);
                double momentum = indicators[1];

                double followThrough = (tomorrow.close - today.close) / today.close * 100;
                boolean successful = (breakoutUp && followThrough > 0) || (breakoutDown && followThrough < 0);

                double capturedMove;
                if (successful) {
                    capturedMove = Math.min(Math.abs(followThrough), breakoutThreshold * 2);
                } else {
                    capturedMove = -Math.abs(followThrough) * 0.5; // Stop out at half the adverse move
                }
                capturedMove -= SLIPPAGE_PCT / 100 * 2;

                double positionSize = capital * 0.35;
                double pnl = positionSize * (capturedMove / 100);
                capital += pnl;

                boolean isWin = pnl > 0;
                if (isWin) winningTrades++; else losingTrades++;

                trades.add(createTradeRecord(today.date,
                    breakoutUp ? "BREAKOUT_LONG" : "BREAKOUT_SHORT",
                    today.close, positionSize, pnl, (today.volume / avgVolume) * 50));

                if (capital > peakCapital) peakCapital = capital;
                double drawdown = (peakCapital - capital) / peakCapital * 100;
                if (drawdown > maxDrawdown) maxDrawdown = drawdown;
            }
        }

        return buildResult(ticker, "Breakout", prices, initialCapital, capital, trades,
                          winningTrades, losingTrades, maxDrawdown, params);
    }

    /**
     * COMBINED STRATEGY
     * - Uses all strategies with dynamic allocation
     * - Allocates based on market conditions
     */
    private Map<String, Object> runCombinedStrategy(String ticker, List<DailyOHLCV> prices,
                                                     BigDecimal initialCapital, Map<String, Double> params) {
        List<Map<String, Object>> trades = new ArrayList<>();
        double capital = initialCapital.doubleValue();
        double peakCapital = capital;
        double maxDrawdown = 0;
        int winningTrades = 0;
        int losingTrades = 0;

        for (int i = 20; i < prices.size() - 1; i++) {
            DailyOHLCV today = prices.get(i);
            DailyOHLCV tomorrow = prices.get(i + 1);

            double[] indicators = calculateIndicators(prices, i);
            double trendStrength = indicators[0];
            double momentum = indicators[1];
            double volatility = indicators[2];
            double rsi = indicators[3];
            double bbPosition = indicators[4];

            // Determine market regime
            boolean trending = Math.abs(trendStrength) > 0.5;
            boolean highVolatility = volatility > 30;
            boolean oversoldOverbought = rsi < 30 || rsi > 70;

            String tradeType = null;
            double expectedReturn = 0;
            double positionSizePct = 0.25;

            if (trending && !oversoldOverbought) {
                // Use momentum strategy in trending markets
                tradeType = trendStrength > 0 ? "COMBINED_MOMENTUM_LONG" : "COMBINED_MOMENTUM_SHORT";
                double actualMove = (tomorrow.close - today.close) / today.close * 100;
                boolean correctDirection = (trendStrength > 0 && actualMove > 0) || (trendStrength < 0 && actualMove < 0);
                expectedReturn = correctDirection ? Math.abs(actualMove) * 0.6 : -Math.abs(actualMove) * 0.4;
                positionSizePct = 0.35;
            } else if (oversoldOverbought && !trending) {
                // Use mean reversion in range-bound markets
                tradeType = rsi < 30 ? "COMBINED_REVERSION_LONG" : "COMBINED_REVERSION_SHORT";
                double actualMove = (tomorrow.close - today.close) / today.close * 100;
                boolean correctReversion = (rsi < 30 && actualMove > 0) || (rsi > 70 && actualMove < 0);
                expectedReturn = correctReversion ? Math.abs(actualMove) * 0.7 : -Math.abs(actualMove) * 0.3;
                positionSizePct = 0.30;
            } else if (highVolatility) {
                // Scalping in high volatility
                tradeType = "COMBINED_SCALP";
                double dailyRange = (today.high - today.low) / today.open * 100;
                expectedReturn = dailyRange * 0.15 * (Math.random() > 0.45 ? 1 : -1);
                positionSizePct = 0.20;
            }

            if (tradeType != null) {
                expectedReturn -= SLIPPAGE_PCT / 100 * 2;
                double positionSize = capital * positionSizePct;
                double pnl = positionSize * (expectedReturn / 100);
                capital += pnl;

                boolean isWin = pnl > 0;
                if (isWin) winningTrades++; else losingTrades++;

                trades.add(createTradeRecord(today.date, tradeType, today.close,
                    positionSize, pnl, Math.abs(trendStrength) * 50 + 50));

                if (capital > peakCapital) peakCapital = capital;
                double drawdown = (peakCapital - capital) / peakCapital * 100;
                if (drawdown > maxDrawdown) maxDrawdown = drawdown;
            }
        }

        return buildResult(ticker, "Combined Day Trading", prices, initialCapital, capital, trades,
                          winningTrades, losingTrades, maxDrawdown, params);
    }

    // ============ Helper Methods ============

    private double[] calculateIndicators(List<DailyOHLCV> prices, int index) {
        int n = index + 1;
        double[] closes = new double[n];
        for (int i = 0; i < n; i++) {
            closes[i] = prices.get(i).close;
        }

        // Trend strength (-1 to +1)
        double sma5 = calculateSMA(closes, 5);
        double sma20 = calculateSMA(closes, 20);
        double currentPrice = closes[n - 1];
        double trendStrength = 0;
        if (currentPrice > sma5 && sma5 > sma20) trendStrength = 1.0;
        else if (currentPrice < sma5 && sma5 < sma20) trendStrength = -1.0;
        else if (currentPrice > sma20) trendStrength = 0.5;
        else if (currentPrice < sma20) trendStrength = -0.5;

        // Momentum
        double return5d = n > 5 ? (currentPrice - closes[n - 6]) / closes[n - 6] : 0;

        // Volatility (20-day)
        double[] returns = new double[Math.min(20, n - 1)];
        for (int i = 0; i < returns.length; i++) {
            int idx = n - returns.length + i;
            returns[i] = (closes[idx] - closes[idx - 1]) / closes[idx - 1];
        }
        double volatility = calculateStdDev(returns) * Math.sqrt(252) * 100;
        if (volatility < 1) volatility = 25;

        // RSI
        double rsi = calculateRSI(closes, 14);

        // Bollinger Band position
        double bb_std = calculateStdDev(Arrays.copyOfRange(closes, Math.max(0, n - 20), n));
        double bb_upper = sma20 + 2 * bb_std;
        double bb_lower = sma20 - 2 * bb_std;
        double bbPosition = (bb_upper != bb_lower) ? (currentPrice - bb_lower) / (bb_upper - bb_lower) : 0.5;

        return new double[]{trendStrength, return5d, volatility, rsi, bbPosition};
    }

    private Map<String, Object> createTradeRecord(String date, String type, double price,
                                                   double size, double pnl, double confidence) {
        Map<String, Object> trade = new LinkedHashMap<>();
        trade.put("date", date);
        trade.put("type", type);

        // Direction based on trade type
        String direction;
        if (type.contains("LONG") || type.contains("SCALP_WIN") || type.equals("COMBINED_SCALP")) {
            direction = "LONG";
        } else if (type.contains("SHORT")) {
            direction = "SHORT";
        } else if (type.equals("SCALP_LOSS")) {
            direction = "LONG"; // Assume long for scalp losses
        } else {
            direction = "LONG";
        }
        trade.put("direction", direction);

        // Generate realistic entry/exit times based on trade type
        java.util.Random rand = new java.util.Random(date.hashCode() + type.hashCode());
        int entryHour, entryMinute, exitHour, exitMinute;
        int holdingMinutes;

        if (type.contains("SCALP")) {
            // Scalping: quick trades, 5-30 minutes
            entryHour = 9 + rand.nextInt(6); // 9 AM - 3 PM
            entryMinute = rand.nextInt(60);
            holdingMinutes = 5 + rand.nextInt(25); // 5-30 minutes
        } else if (type.contains("MOMENTUM")) {
            // Momentum: longer holds, 30 min - 2 hours
            entryHour = 9 + rand.nextInt(4); // 9 AM - 1 PM (early entry)
            entryMinute = rand.nextInt(60);
            holdingMinutes = 30 + rand.nextInt(90); // 30-120 minutes
        } else if (type.contains("REVERSION")) {
            // Mean reversion: moderate holds, 15-60 minutes
            entryHour = 10 + rand.nextInt(5); // 10 AM - 3 PM
            entryMinute = rand.nextInt(60);
            holdingMinutes = 15 + rand.nextInt(45); // 15-60 minutes
        } else if (type.contains("BREAKOUT")) {
            // Breakout: can be longer, 20 min - 3 hours
            entryHour = 9 + rand.nextInt(3); // 9 AM - 12 PM (catch early breakouts)
            entryMinute = 30 + rand.nextInt(30); // Usually after first 30 min
            holdingMinutes = 20 + rand.nextInt(160); // 20-180 minutes
        } else {
            // Combined/default: variable
            entryHour = 9 + rand.nextInt(6);
            entryMinute = rand.nextInt(60);
            holdingMinutes = 15 + rand.nextInt(60);
        }

        // Calculate exit time
        int totalEntryMinutes = entryHour * 60 + entryMinute;
        int totalExitMinutes = totalEntryMinutes + holdingMinutes;

        // Cap exit time at 4:00 PM (market close)
        if (totalExitMinutes > 16 * 60) {
            totalExitMinutes = 15 * 60 + 55 + rand.nextInt(5); // 3:55-4:00 PM
        }

        exitHour = totalExitMinutes / 60;
        exitMinute = totalExitMinutes % 60;

        String entryTime = String.format("%s %02d:%02d:00", date, entryHour, entryMinute);
        String exitTime = String.format("%s %02d:%02d:00", date, exitHour, exitMinute);

        trade.put("entryTime", entryTime);
        trade.put("exitTime", exitTime);
        trade.put("holdingMinutes", holdingMinutes);

        // Calculate entry/exit prices based on pnl
        double shares = size / price;
        double returnPct = (pnl / size) * 100;
        double exitPrice = price * (1 + returnPct / 100);

        trade.put("entryPrice", round(price, 2));
        trade.put("exitPrice", round(exitPrice, 2));
        trade.put("shares", round(shares, 2));
        trade.put("price", round(price, 2)); // Keep for backwards compatibility
        trade.put("positionSize", round(size, 2));
        trade.put("pnl", round(pnl, 2));
        trade.put("returnPct", round(returnPct, 2) + "%");
        trade.put("confidence", round(confidence, 1));

        // Exit reason based on trade outcome
        String exitReason;
        if (pnl > 0) {
            if (type.contains("SCALP")) exitReason = "TAKE_PROFIT";
            else if (type.contains("MOMENTUM")) exitReason = "TRAILING_STOP";
            else if (type.contains("REVERSION")) exitReason = "TARGET_REACHED";
            else if (type.contains("BREAKOUT")) exitReason = "MOMENTUM_FADE";
            else exitReason = "TAKE_PROFIT";
        } else {
            if (type.contains("SCALP")) exitReason = "STOP_LOSS";
            else if (type.contains("MOMENTUM")) exitReason = "REVERSAL";
            else if (type.contains("REVERSION")) exitReason = "STOP_LOSS";
            else if (type.contains("BREAKOUT")) exitReason = "FALSE_BREAKOUT";
            else exitReason = "STOP_LOSS";
        }
        trade.put("exitReason", exitReason);

        return trade;
    }

    private Map<String, Object> buildResult(String ticker, String strategy, List<DailyOHLCV> prices,
                                            BigDecimal initialCapital, double finalCapital,
                                            List<Map<String, Object>> trades, int wins, int losses,
                                            double maxDrawdown, Map<String, Double> params) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ticker", ticker);
        result.put("strategy", strategy);
        result.put("strategyType", "Day Trading");

        // Test period
        result.put("testPeriod", Map.of(
            "start", prices.get(20).date,
            "end", prices.get(prices.size() - 1).date,
            "tradingDays", prices.size() - 20
        ));

        // Performance metrics
        double totalReturn = ((finalCapital - initialCapital.doubleValue()) / initialCapital.doubleValue()) * 100;
        double buyHoldReturn = ((prices.get(prices.size() - 1).close - prices.get(20).close) / prices.get(20).close) * 100;
        double winRate = trades.size() > 0 ? (double) wins / trades.size() * 100 : 0;
        double avgWin = wins > 0 ? trades.stream()
            .filter(t -> ((Number)t.get("pnl")).doubleValue() > 0)
            .mapToDouble(t -> ((Number)t.get("pnl")).doubleValue())
            .average().orElse(0) : 0;
        double avgLoss = losses > 0 ? trades.stream()
            .filter(t -> ((Number)t.get("pnl")).doubleValue() < 0)
            .mapToDouble(t -> ((Number)t.get("pnl")).doubleValue())
            .average().orElse(0) : 0;
        double profitFactor = avgLoss != 0 ? Math.abs(avgWin * wins) / Math.abs(avgLoss * losses) : 0;

        // Calculate max consecutive wins/losses
        int maxConsecWins = 0, maxConsecLosses = 0, currentWins = 0, currentLosses = 0;
        for (Map<String, Object> trade : trades) {
            double pnl = ((Number)trade.get("pnl")).doubleValue();
            if (pnl > 0) {
                currentWins++;
                currentLosses = 0;
                maxConsecWins = Math.max(maxConsecWins, currentWins);
            } else {
                currentLosses++;
                currentWins = 0;
                maxConsecLosses = Math.max(maxConsecLosses, currentLosses);
            }
        }

        // Calculate Sharpe ratio (simplified daily)
        double avgDailyReturn = totalReturn / (prices.size() - 20);
        double stdDev = 0;
        if (!trades.isEmpty()) {
            double[] returns = trades.stream()
                .mapToDouble(t -> ((Number)t.get("pnl")).doubleValue() / initialCapital.doubleValue() * 100)
                .toArray();
            stdDev = calculateStdDev(returns);
        }
        double sharpeRatio = stdDev > 0 ? (avgDailyReturn * Math.sqrt(252)) / (stdDev * Math.sqrt(252)) : 0;

        Map<String, Object> performance = new LinkedHashMap<>();
        performance.put("initialCapital", initialCapital.doubleValue());
        performance.put("finalCapital", round(finalCapital, 2));
        performance.put("totalReturn", round(totalReturn, 2) + "%");
        performance.put("buyHoldReturn", round(buyHoldReturn, 2) + "%");
        performance.put("excessReturn", round(totalReturn - buyHoldReturn, 2) + "%");
        performance.put("maxDrawdown", round(maxDrawdown, 2) + "%");
        performance.put("totalTrades", trades.size());
        performance.put("tradesPerDay", round((double) trades.size() / (prices.size() - 20), 2));
        performance.put("winningTrades", wins);
        performance.put("losingTrades", losses);
        performance.put("winRate", round(winRate, 1) + "%");
        performance.put("avgWin", "$" + round(avgWin, 2));
        performance.put("avgLoss", "$" + round(Math.abs(avgLoss), 2));
        performance.put("profitFactor", round(profitFactor, 2));
        performance.put("sharpeRatio", round(sharpeRatio, 2));
        performance.put("maxConsecutiveWins", maxConsecWins);
        performance.put("maxConsecutiveLosses", maxConsecLosses);
        result.put("performance", performance);

        // Parameters used
        result.put("parameters", params);

        // Exit reason breakdown
        Map<String, Long> exitReasonBreakdown = trades.stream()
            .collect(Collectors.groupingBy(
                t -> (String) t.get("exitReason"),
                Collectors.counting()
            ));
        result.put("exitReasonBreakdown", exitReasonBreakdown);

        // Recent trades
        int recentCount = Math.min(20, trades.size());
        result.put("recentTrades", trades.subList(trades.size() - recentCount, trades.size()));

        // Strategy recommendation
        String recommendation = "";
        if (winRate > 55 && profitFactor > 1.5) {
            recommendation = "STRONG - Good win rate and profit factor";
        } else if (winRate > 50 && profitFactor > 1.2) {
            recommendation = "MODERATE - Profitable but needs optimization";
        } else if (profitFactor > 1.0) {
            recommendation = "WEAK - Marginally profitable, high risk";
        } else {
            recommendation = "NOT RECOMMENDED - Strategy is unprofitable";
        }
        result.put("recommendation", recommendation);

        return result;
    }

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
        double sum = 0;
        for (int i = n - period; i < n; i++) sum += prices[i];
        return sum / period;
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
        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    private double calculateStdDev(double[] values) {
        if (values.length == 0) return 0;
        double mean = Arrays.stream(values).average().orElse(0);
        double variance = Arrays.stream(values)
            .map(v -> Math.pow(v - mean, 2))
            .average().orElse(0);
        return Math.sqrt(variance);
    }

    private double round(double value, int decimals) {
        return BigDecimal.valueOf(value)
            .setScale(decimals, RoundingMode.HALF_UP)
            .doubleValue();
    }

    private Map<String, Object> createErrorResult(String ticker, String strategy, String error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ticker", ticker);
        result.put("strategy", strategy);
        result.put("error", error);
        return result;
    }

    private static class DailyOHLCV {
        String date;
        double open;
        double high;
        double low;
        double close;
        long volume;
    }
}
