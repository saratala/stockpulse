package com.stockpulse.agent.service;

import com.stockpulse.agent.model.StockPrice;
import com.stockpulse.agent.repository.StockPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for backtesting ML prediction model performance.
 * Uses the same prediction algorithm as MLPredictionService but runs it historically
 * to measure accuracy and trading performance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MLBacktestService {

    private final StockPriceRepository stockPriceRepository;

    // Signal weights (same as MLPredictionService)
    private static final double RSI_WEIGHT = 0.15;
    private static final double MACD_WEIGHT = 0.20;
    private static final double TREND_WEIGHT = 0.25;
    private static final double MOMENTUM_WEIGHT = 0.20;
    private static final double VOLUME_WEIGHT = 0.10;
    private static final double VOLATILITY_WEIGHT = 0.10;

    /**
     * Run ML prediction backtest for a ticker
     */
    public Map<String, Object> runMLBacktest(String ticker, BigDecimal initialCapital, String timeframe) {
        log.info("Running ML backtest for {} with capital {} and timeframe {}", ticker, initialCapital, timeframe);

        List<StockPrice> allPrices = stockPriceRepository.findByTickerOrderByDateAsc(ticker);

        if (allPrices.size() < 50) {
            return createErrorResult(ticker, "Insufficient data for backtest (need at least 50 data points)");
        }

        // Aggregate to daily OHLCV data
        List<DailyPrice> dailyPrices = aggregateToDailyPrices(allPrices);

        if (dailyPrices.size() < 25) {
            return createErrorResult(ticker, "Insufficient daily data for backtest (need at least 25 trading days)");
        }

        log.info("Aggregated {} intraday records to {} daily candles", allPrices.size(), dailyPrices.size());

        // Run backtest with predictions
        return runPredictionBacktest(ticker, dailyPrices, initialCapital, timeframe);
    }

    /**
     * Aggregate intraday prices to daily OHLCV
     */
    private List<DailyPrice> aggregateToDailyPrices(List<StockPrice> prices) {
        Map<String, List<StockPrice>> byDay = prices.stream()
            .collect(Collectors.groupingBy(
                p -> p.getDate().toLocalDate().toString(),
                LinkedHashMap::new,
                Collectors.toList()
            ));

        List<DailyPrice> dailyPrices = new ArrayList<>();
        for (Map.Entry<String, List<StockPrice>> entry : byDay.entrySet()) {
            List<StockPrice> dayPrices = entry.getValue();
            if (dayPrices.isEmpty()) continue;

            DailyPrice daily = new DailyPrice();
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

    /**
     * Run the actual backtest using ML predictions
     */
    private Map<String, Object> runPredictionBacktest(String ticker, List<DailyPrice> prices,
                                                       BigDecimal initialCapital, String timeframe) {
        List<Map<String, Object>> trades = new ArrayList<>();
        List<Map<String, Object>> predictions = new ArrayList<>();

        double capital = initialCapital.doubleValue();
        double shares = 0;
        double peakCapital = capital;
        double maxDrawdown = 0;

        int correctDirections = 0;
        int totalPredictions = 0;
        double totalPriceError = 0;

        // Lookback period for indicators
        int lookback = 20;

        // For each day (starting after lookback period), generate prediction and evaluate
        for (int i = lookback; i < prices.size() - 1; i++) {
            List<DailyPrice> historicalPrices = prices.subList(0, i + 1);
            DailyPrice currentDay = prices.get(i);
            DailyPrice nextDay = prices.get(i + 1);

            // Generate prediction using ML model
            Map<String, Double> features = extractFeatures(historicalPrices);
            double[] predictedReturns = predictReturns(features, historicalPrices);
            double[] signalProbs = calculateSignalProbabilities(features);

            // Determine signal based on timeframe
            double predictedReturn = timeframe.equals("1h") ? predictedReturns[0] :
                                    timeframe.equals("1d") ? predictedReturns[1] : predictedReturns[2];

            String signal = predictedReturn > 0.5 ? "BULLISH" : predictedReturn < -0.5 ? "BEARISH" : "NEUTRAL";
            double confidence = Math.max(signalProbs[0], Math.max(signalProbs[1], signalProbs[2])) * 100;

            // Calculate actual return
            double actualReturn = ((nextDay.close - currentDay.close) / currentDay.close) * 100;

            // Validate prediction
            boolean directionCorrect = (predictedReturn > 0 && actualReturn > 0) ||
                                       (predictedReturn < 0 && actualReturn < 0) ||
                                       (Math.abs(predictedReturn) < 0.5 && Math.abs(actualReturn) < 1);

            double priceError = Math.abs(predictedReturn - actualReturn);

            if (Math.abs(predictedReturn) > 0.1) { // Only count meaningful predictions
                totalPredictions++;
                if (directionCorrect) correctDirections++;
                totalPriceError += priceError;
            }

            // Record prediction
            Map<String, Object> predRecord = new LinkedHashMap<>();
            predRecord.put("date", currentDay.date);
            predRecord.put("currentPrice", round(currentDay.close, 2));
            predRecord.put("predictedReturn", round(predictedReturn, 2));
            predRecord.put("actualReturn", round(actualReturn, 2));
            predRecord.put("signal", signal);
            predRecord.put("confidence", round(confidence, 1));
            predRecord.put("directionCorrect", directionCorrect);
            predRecord.put("priceError", round(priceError, 2));
            predictions.add(predRecord);

            // Execute trades based on signal (simple strategy: follow signal if confidence > 60%)
            double portfolioValue = capital + shares * currentDay.close;

            if (confidence > 60) {
                if (signal.equals("BULLISH") && shares == 0) {
                    // Buy with all capital
                    shares = capital / currentDay.close;
                    double cost = shares * currentDay.close;
                    capital = 0;

                    Map<String, Object> trade = new LinkedHashMap<>();
                    trade.put("date", currentDay.date);
                    trade.put("type", "BUY");
                    trade.put("price", round(currentDay.close, 2));
                    trade.put("shares", round(shares, 4));
                    trade.put("value", round(cost, 2));
                    trade.put("signal", signal);
                    trade.put("confidence", round(confidence, 1));
                    trades.add(trade);

                } else if (signal.equals("BEARISH") && shares > 0) {
                    // Sell all shares
                    double proceeds = shares * currentDay.close;

                    Map<String, Object> trade = new LinkedHashMap<>();
                    trade.put("date", currentDay.date);
                    trade.put("type", "SELL");
                    trade.put("price", round(currentDay.close, 2));
                    trade.put("shares", round(shares, 4));
                    trade.put("value", round(proceeds, 2));
                    trade.put("signal", signal);
                    trade.put("confidence", round(confidence, 1));
                    trades.add(trade);

                    capital = proceeds;
                    shares = 0;
                }
            }

            // Track drawdown
            portfolioValue = capital + shares * currentDay.close;
            if (portfolioValue > peakCapital) {
                peakCapital = portfolioValue;
            }
            double drawdown = (peakCapital - portfolioValue) / peakCapital * 100;
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown;
            }
        }

        // Calculate final portfolio value
        DailyPrice lastDay = prices.get(prices.size() - 1);
        double finalValue = capital + shares * lastDay.close;
        double totalReturn = ((finalValue - initialCapital.doubleValue()) / initialCapital.doubleValue()) * 100;

        // Calculate buy-and-hold comparison
        DailyPrice firstTradeDay = prices.get(lookback);
        double buyHoldReturn = ((lastDay.close - firstTradeDay.close) / firstTradeDay.close) * 100;

        // Build result
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ticker", ticker);
        result.put("strategy", "ML Prediction Model");
        result.put("timeframe", timeframe);
        result.put("testPeriod", Map.of(
            "start", prices.get(lookback).date,
            "end", lastDay.date,
            "tradingDays", prices.size() - lookback
        ));

        // Performance metrics
        Map<String, Object> performance = new LinkedHashMap<>();
        performance.put("initialCapital", initialCapital.doubleValue());
        performance.put("finalValue", round(finalValue, 2));
        performance.put("totalReturn", round(totalReturn, 2) + "%");
        performance.put("buyHoldReturn", round(buyHoldReturn, 2) + "%");
        performance.put("excessReturn", round(totalReturn - buyHoldReturn, 2) + "%");
        performance.put("maxDrawdown", round(maxDrawdown, 2) + "%");
        performance.put("totalTrades", trades.size());
        result.put("performance", performance);

        // Prediction accuracy metrics
        Map<String, Object> accuracy = new LinkedHashMap<>();
        accuracy.put("totalPredictions", totalPredictions);
        accuracy.put("correctDirections", correctDirections);
        accuracy.put("directionAccuracy", totalPredictions > 0 ?
            round((double) correctDirections / totalPredictions * 100, 1) + "%" : "N/A");
        accuracy.put("avgPriceError", totalPredictions > 0 ?
            round(totalPriceError / totalPredictions, 2) + "%" : "N/A");
        result.put("predictionAccuracy", accuracy);

        // Trade details
        result.put("trades", trades);

        // Last 5 predictions with details
        int startIdx = Math.max(0, predictions.size() - 10);
        result.put("recentPredictions", predictions.subList(startIdx, predictions.size()));

        // Feature importance (simulated based on weights)
        Map<String, Object> features = new LinkedHashMap<>();
        features.put("trend", TREND_WEIGHT * 100 + "%");
        features.put("macd", MACD_WEIGHT * 100 + "%");
        features.put("momentum", MOMENTUM_WEIGHT * 100 + "%");
        features.put("rsi", RSI_WEIGHT * 100 + "%");
        features.put("volume", VOLUME_WEIGHT * 100 + "%");
        features.put("volatility", VOLATILITY_WEIGHT * 100 + "%");
        result.put("modelWeights", features);

        log.info("ML Backtest complete for {}: Return={}%, Accuracy={}%",
                ticker, round(totalReturn, 2),
                totalPredictions > 0 ? round((double) correctDirections / totalPredictions * 100, 1) : 0);

        return result;
    }

    // ============ Feature Extraction (mirrors MLPredictionService) ============

    private Map<String, Double> extractFeatures(List<DailyPrice> prices) {
        Map<String, Double> features = new HashMap<>();
        int n = prices.size();

        double[] closes = prices.stream().mapToDouble(p -> p.close).toArray();
        double currentPrice = closes[n - 1];

        // Moving averages
        double sma5 = calculateSMA(closes, 5);
        double sma10 = calculateSMA(closes, 10);
        double sma20 = calculateSMA(closes, 20);

        features.put("sma5", sma5);
        features.put("sma10", sma10);
        features.put("sma20", sma20);

        // Trend signal
        double trendSignal = 0;
        if (currentPrice > sma5 && sma5 > sma10 && sma10 > sma20) trendSignal = 1.0;
        else if (currentPrice < sma5 && sma5 < sma10 && sma10 < sma20) trendSignal = -1.0;
        else if (currentPrice > sma20) trendSignal = 0.5;
        else if (currentPrice < sma20) trendSignal = -0.5;
        features.put("trend_signal", trendSignal);

        // RSI
        double rsi = calculateRSI(closes, 14);
        features.put("rsi", rsi);
        double rsiSignal = rsi < 30 ? 1.0 : rsi > 70 ? -1.0 : (50 - rsi) / 50;
        features.put("rsi_signal", rsiSignal);

        // MACD
        double ema12 = calculateEMA(closes, 12);
        double ema26 = calculateEMA(closes, 26);
        double macd = ema12 - ema26;
        double macdSignal = macd > 0 ? Math.min(1.0, macd / currentPrice * 100) :
                           Math.max(-1.0, macd / currentPrice * 100);
        features.put("macd", macd);
        features.put("macd_signal", macdSignal);

        // Momentum
        double return5d = n > 5 ? (currentPrice - closes[n - 6]) / closes[n - 6] * 100 : 0;
        double return10d = n > 10 ? (currentPrice - closes[n - 11]) / closes[n - 11] * 100 : 0;
        features.put("return_5d", return5d);
        features.put("return_10d", return10d);
        double momentumSignal = return5d > 2 ? 1.0 : return5d < -2 ? -1.0 : return5d / 2;
        features.put("momentum_signal", momentumSignal);

        // Volume
        long[] volumes = prices.stream().mapToLong(p -> p.volume).toArray();
        double avgVolume = Arrays.stream(volumes).skip(Math.max(0, n - 21)).limit(20).average().orElse(volumes[n-1]);
        double volumeRatio = volumes[n - 1] / avgVolume;
        features.put("volume_ratio", volumeRatio);
        features.put("volume_signal", volumeRatio > 1.5 ? 1.0 : volumeRatio < 0.5 ? -1.0 : 0.0);

        // Volatility
        double[] returns = new double[Math.min(20, n - 1)];
        for (int i = 0; i < returns.length; i++) {
            int idx = n - returns.length + i;
            returns[i] = (closes[idx] - closes[idx - 1]) / closes[idx - 1];
        }
        double volatility = calculateStdDev(returns) * Math.sqrt(252) * 100;
        if (volatility < 0.01) volatility = 25.0;
        features.put("volatility", volatility);
        features.put("volatility_signal", volatility < 20 ? 0.5 : volatility > 40 ? -0.5 : 0.0);

        return features;
    }

    private double[] predictReturns(Map<String, Double> features, List<DailyPrice> prices) {
        double volatility = features.getOrDefault("volatility", 20.0);
        double dailyVol = volatility / Math.sqrt(252);

        double rsiSignal = features.getOrDefault("rsi_signal", 0.0);
        double macdSignal = features.getOrDefault("macd_signal", 0.0);
        double trendSignal = features.getOrDefault("trend_signal", 0.0);
        double momentumSignal = features.getOrDefault("momentum_signal", 0.0);

        double weightSum = RSI_WEIGHT + MACD_WEIGHT + TREND_WEIGHT + MOMENTUM_WEIGHT;
        double compositeSignal = (rsiSignal * RSI_WEIGHT +
                                  macdSignal * MACD_WEIGHT +
                                  trendSignal * TREND_WEIGHT +
                                  momentumSignal * MOMENTUM_WEIGHT) / weightSum;

        double return5d = features.getOrDefault("return_5d", 0.0);
        double avgDailyReturn5d = return5d / 5.0;
        double expectedDailyMove = dailyVol * 1.0;
        double direction = compositeSignal >= 0 ? 1.0 : -1.0;
        double signalStrength = Math.abs(compositeSignal);
        double effectiveStrength = Math.max(0.15, signalStrength);
        double baseReturn = direction * expectedDailyMove * effectiveStrength;

        double momentumAdjust = 0;
        if (Math.abs(avgDailyReturn5d) > 0.01) {
            if (Math.signum(avgDailyReturn5d) == direction) {
                momentumAdjust = avgDailyReturn5d * 0.2;
            } else {
                momentumAdjust = -avgDailyReturn5d * 0.1;
            }
        }

        double return1d = baseReturn + momentumAdjust;
        double return1h = return1d * 0.12;
        double return1w = return1d * 4.0;

        if (Math.abs(return1w) > 5.0) {
            return1w = return1w * 0.7;
        }

        return1d = Math.max(-8.0, Math.min(8.0, return1d));
        return1w = Math.max(-20.0, Math.min(20.0, return1w));
        return1h = Math.max(-2.0, Math.min(2.0, return1h));

        return new double[]{return1h, return1d, return1w};
    }

    private double[] calculateSignalProbabilities(Map<String, Double> features) {
        double rsiSignal = features.getOrDefault("rsi_signal", 0.0);
        double macdSignal = features.getOrDefault("macd_signal", 0.0);
        double trendSignal = features.getOrDefault("trend_signal", 0.0);
        double momentumSignal = features.getOrDefault("momentum_signal", 0.0);
        double volumeSignal = features.getOrDefault("volume_signal", 0.0);
        double volatilitySignal = features.getOrDefault("volatility_signal", 0.0);

        double compositeSignal = rsiSignal * RSI_WEIGHT +
                                 macdSignal * MACD_WEIGHT +
                                 trendSignal * TREND_WEIGHT +
                                 momentumSignal * MOMENTUM_WEIGHT +
                                 volumeSignal * VOLUME_WEIGHT +
                                 volatilitySignal * VOLATILITY_WEIGHT;

        double expUp = Math.exp(compositeSignal * 2);
        double expDown = Math.exp(-compositeSignal * 2);
        double expNeutral = Math.exp(-Math.abs(compositeSignal));
        double total = expUp + expDown + expNeutral;

        return new double[]{expUp / total, expDown / total, expNeutral / total};
    }

    // ============ Technical Calculations ============

    private double calculateSMA(double[] prices, int period) {
        int n = prices.length;
        if (n < period) return prices[n - 1];
        double sum = 0;
        for (int i = n - period; i < n; i++) {
            sum += prices[i];
        }
        return sum / period;
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

    private Map<String, Object> createErrorResult(String ticker, String error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ticker", ticker);
        result.put("strategy", "ML Prediction Model");
        result.put("error", error);
        return result;
    }

    // Helper class for daily aggregated prices
    private static class DailyPrice {
        String date;
        double open;
        double high;
        double low;
        double close;
        long volume;
    }
}
