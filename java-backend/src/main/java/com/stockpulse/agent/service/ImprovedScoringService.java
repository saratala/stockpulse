package com.stockpulse.agent.service;

import com.stockpulse.agent.model.StockPrice;
import com.stockpulse.agent.repository.StockPriceRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Improved scoring service that uses actual technical indicators
 * instead of random values for stock analysis.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImprovedScoringService {

    private final StockPriceRepository stockPriceRepository;
    private final YahooFinanceService yahooFinanceService;

    @Data
    @Builder
    public static class TechnicalScore {
        private String ticker;
        private double compositeScore;      // 0-100 overall score
        private double trendScore;          // Trend strength
        private double momentumScore;       // Momentum indicators
        private double volatilityScore;     // Volatility assessment
        private double volumeScore;         // Volume analysis

        private double rsi;
        private double macd;
        private double macdSignal;
        private double macdHistogram;
        private double sma20;
        private double sma50;
        private double sma200;
        private double bollingerUpper;
        private double bollingerLower;
        private double bollingerPosition;   // Where price is in Bollinger range (0-1)
        private double atr;                 // Average True Range
        private double adx;                 // ADX for trend strength
        private double stochK;              // Stochastic %K
        private double stochD;              // Stochastic %D

        // New indicators (Phase 1)
        private double obv;                 // On-Balance Volume
        private double williamsR;           // Williams %R
        private double parabolicSar;        // Parabolic SAR
        private boolean sarBullish;         // True if price above SAR
        private double vwap;                // Volume Weighted Average Price
        private List<String> candlestickPatterns; // Detected candlestick patterns

        // New indicators (Phase 2)
        private double roc;                 // Rate of Change (momentum)
        private double cci;                 // Commodity Channel Index
        private double mfi;                 // Money Flow Index
        private double accumulationDistribution; // A/D Line
        private double chaikinMoneyFlow;    // Chaikin Money Flow
        private double keltnerUpper;        // Keltner Channel Upper
        private double keltnerLower;        // Keltner Channel Lower
        private double historicalVolatility; // Historical Volatility %
        private double relativeStrength;    // Relative Strength vs market

        // New indicators (Phase 3) - Supertrend & Ichimoku
        private double supertrend;          // Supertrend value
        private boolean supertrendBullish;  // True if price above Supertrend
        private double tenkanSen;           // Ichimoku Conversion Line (9-period)
        private double kijunSen;            // Ichimoku Base Line (26-period)
        private double senkouSpanA;         // Ichimoku Leading Span A
        private double senkouSpanB;         // Ichimoku Leading Span B
        private double chikouSpan;          // Ichimoku Lagging Span
        private String ichimokuSignal;      // BULLISH, BEARISH, or NEUTRAL based on cloud

        // Fundamental indicators (Phase 3)
        private Double peRatio;             // Price-to-Earnings Ratio
        private Double epsGrowth;           // EPS Growth rate (%)
        private Long marketCap;             // Market Capitalization
        private String marketCapCategory;   // MEGA, LARGE, MID, SMALL, MICRO

        private boolean emaStackAligned;    // EMA 20 > 50 > 200 (bullish) or vice versa
        private String primarySignal;       // BULLISH, BEARISH, NEUTRAL
        private double signalConfidence;
        private String trendDirection;      // UP, DOWN, SIDEWAYS
        private String riskLevel;           // LOW, MEDIUM, HIGH

        private double supportLevel;
        private double resistanceLevel;
        private double pivotPoint;
        private List<String> signals;       // List of detected patterns/signals
    }

    /**
     * Calculate comprehensive technical score for a ticker
     */
    public Optional<TechnicalScore> calculateTechnicalScore(String ticker) {
        try {
            // Get historical price data (200 days for full analysis)
            LocalDateTime since = LocalDateTime.now().minusDays(200);
            List<StockPrice> prices = stockPriceRepository.findByTickerAndDateBetween(
                ticker, since, LocalDateTime.now());

            if (prices.size() < 20) {
                log.warn("Insufficient price data for scoring: {} (only {} records)", ticker, prices.size());
                return Optional.empty();
            }

            // Sort by date ascending
            prices.sort(Comparator.comparing(StockPrice::getDate));

            BigDecimal currentPrice = prices.get(prices.size() - 1).getClose();

            // Calculate all indicators
            double rsi = calculateRSI(prices, 14);
            double[] macdValues = calculateMACD(prices);
            double sma20 = calculateSMA(prices, 20);
            double sma50 = calculateSMA(prices, Math.min(50, prices.size()));
            double sma200 = calculateSMA(prices, Math.min(200, prices.size()));
            double[] bollingerBands = calculateBollingerBands(prices, 20, 2.0);
            double atr = calculateATR(prices, 14);
            double adx = calculateADX(prices, 14);
            double[] stochastic = calculateStochastic(prices, 14, 3);
            double volumeRatio = calculateVolumeRatio(prices, 20);

            // Calculate support/resistance from price pivots
            double[] supportResistance = calculateSupportResistance(prices);

            // ===== NEW INDICATORS (Phase 1) =====
            double obvTrend = calculateOBVTrend(prices, 20);
            double williamsR = calculateWilliamsR(prices, 14);
            double[] parabolicSAR = calculateParabolicSAR(prices);
            double vwap = calculateVWAP(prices, 20);
            List<String> candlestickPatterns = detectCandlestickPatterns(prices);

            // ===== NEW INDICATORS (Phase 2) =====
            double roc = calculateROC(prices, 12);
            double cci = calculateCCI(prices, 20);
            double mfi = calculateMFI(prices, 14);
            double accDist = calculateAccumulationDistribution(prices, 20);
            double cmf = calculateChaikinMoneyFlow(prices, 20);
            double[] keltner = calculateKeltnerChannels(prices, 20, 2.0);
            double historicalVol = calculateHistoricalVolatility(prices, 20);
            double relStrength = calculateRelativeStrength(prices, 20);

            // ===== NEW INDICATORS (Phase 3) =====
            double[] supertrendResult = calculateSupertrend(prices, 10, 3.0);
            double supertrendValue = supertrendResult[0];
            boolean supertrendBullish = supertrendResult[1] > 0;

            double[] ichimoku = calculateIchimokuCloud(prices);
            double tenkanSen = ichimoku[0];
            double kijunSen = ichimoku[1];
            double senkouSpanA = ichimoku[2];
            double senkouSpanB = ichimoku[3];
            double chikouSpan = ichimoku[4];
            String ichimokuSignal = determineIchimokuSignal(currentPrice.doubleValue(),
                tenkanSen, kijunSen, senkouSpanA, senkouSpanB);

            // ===== FUNDAMENTAL DATA (Phase 3) =====
            Double peRatio = null;
            Double epsGrowth = null;
            Long marketCap = null;
            String marketCapCategory = "UNKNOWN";

            try {
                Optional<YahooFinanceService.StockInfo> stockInfoOpt = yahooFinanceService.getStockInfo(ticker);
                if (stockInfoOpt.isPresent()) {
                    YahooFinanceService.StockInfo stockInfo = stockInfoOpt.get();
                    if (stockInfo.getPeRatio() != null) {
                        peRatio = stockInfo.getPeRatio().doubleValue();
                    }
                    if (stockInfo.getMarketCap() != null && stockInfo.getMarketCap() > 0) {
                        marketCap = stockInfo.getMarketCap();
                        marketCapCategory = categorizeMarketCap(marketCap);
                    }
                    // EPS Growth would need TTM vs previous year - estimate from P/E and price change
                    if (peRatio != null && peRatio > 0) {
                        double priceChange = calculateROC(prices, Math.min(252, prices.size() - 1)); // ~1 year
                        // Rough EPS growth estimate: if P/E stable, EPS growth ≈ price growth
                        epsGrowth = priceChange;
                    }
                }
            } catch (Exception e) {
                log.debug("Could not fetch fundamental data for {}: {}", ticker, e.getMessage());
            }

            // Determine EMA alignment
            boolean emaStackAligned = sma20 > sma50 && sma50 > sma200;
            boolean bearishEmaStack = sma20 < sma50 && sma50 < sma200;

            // Calculate Bollinger position (0 = at lower band, 1 = at upper band)
            double bollingerPosition = (currentPrice.doubleValue() - bollingerBands[2]) /
                (bollingerBands[0] - bollingerBands[2]);
            bollingerPosition = Math.max(0, Math.min(1, bollingerPosition));

            // Calculate component scores
            double trendScore = calculateTrendScore(currentPrice.doubleValue(), sma20, sma50, sma200, emaStackAligned);
            double momentumScore = calculateMomentumScore(rsi, macdValues[0], macdValues[2], stochastic[0]);
            double volatilityScore = calculateVolatilityScore(atr, currentPrice.doubleValue(), adx);
            double volumeScore = calculateVolumeScore(volumeRatio);

            // Calculate composite score (weighted average)
            double compositeScore =
                trendScore * 0.30 +      // 30% trend
                momentumScore * 0.35 +   // 35% momentum
                volatilityScore * 0.15 + // 15% volatility
                volumeScore * 0.20;      // 20% volume

            // Determine primary signal and confidence
            String primarySignal = determinePrimarySignal(compositeScore, rsi, macdValues[2], emaStackAligned, bearishEmaStack);
            double signalConfidence = calculateSignalConfidence(compositeScore, adx, volumeRatio);

            // Determine trend direction
            String trendDirection = determineTrendDirection(currentPrice.doubleValue(), sma20, sma50);

            // Determine risk level
            String riskLevel = determineRiskLevel(atr, currentPrice.doubleValue(), rsi, bollingerPosition);

            // Collect all signals (including new indicators)
            List<String> signals = collectSignals(rsi, macdValues, stochastic, bollingerPosition,
                emaStackAligned, bearishEmaStack, adx, volumeRatio,
                williamsR, obvTrend, parabolicSAR[1] > 0, currentPrice.doubleValue(), vwap, candlestickPatterns,
                roc, cci, mfi, cmf, relStrength, supertrendBullish, ichimokuSignal);

            return Optional.of(TechnicalScore.builder()
                .ticker(ticker)
                .compositeScore(Math.round(compositeScore * 100.0) / 100.0)
                .trendScore(Math.round(trendScore * 100.0) / 100.0)
                .momentumScore(Math.round(momentumScore * 100.0) / 100.0)
                .volatilityScore(Math.round(volatilityScore * 100.0) / 100.0)
                .volumeScore(Math.round(volumeScore * 100.0) / 100.0)
                .rsi(Math.round(rsi * 100.0) / 100.0)
                .macd(Math.round(macdValues[0] * 10000.0) / 10000.0)
                .macdSignal(Math.round(macdValues[1] * 10000.0) / 10000.0)
                .macdHistogram(Math.round(macdValues[2] * 10000.0) / 10000.0)
                .sma20(Math.round(sma20 * 100.0) / 100.0)
                .sma50(Math.round(sma50 * 100.0) / 100.0)
                .sma200(Math.round(sma200 * 100.0) / 100.0)
                .bollingerUpper(Math.round(bollingerBands[0] * 100.0) / 100.0)
                .bollingerLower(Math.round(bollingerBands[2] * 100.0) / 100.0)
                .bollingerPosition(Math.round(bollingerPosition * 100.0) / 100.0)
                .atr(Math.round(atr * 100.0) / 100.0)
                .adx(Math.round(adx * 100.0) / 100.0)
                .stochK(Math.round(stochastic[0] * 100.0) / 100.0)
                .stochD(Math.round(stochastic[1] * 100.0) / 100.0)
                // New indicators (Phase 1)
                .obv(Math.round(obvTrend * 100.0) / 100.0)
                .williamsR(Math.round(williamsR * 100.0) / 100.0)
                .parabolicSar(Math.round(parabolicSAR[0] * 100.0) / 100.0)
                .sarBullish(parabolicSAR[1] > 0)
                .vwap(Math.round(vwap * 100.0) / 100.0)
                .candlestickPatterns(candlestickPatterns)
                // New indicators (Phase 2)
                .roc(Math.round(roc * 100.0) / 100.0)
                .cci(Math.round(cci * 100.0) / 100.0)
                .mfi(Math.round(mfi * 100.0) / 100.0)
                .accumulationDistribution(Math.round(accDist * 100.0) / 100.0)
                .chaikinMoneyFlow(Math.round(cmf * 100.0) / 100.0)
                .keltnerUpper(Math.round(keltner[0] * 100.0) / 100.0)
                .keltnerLower(Math.round(keltner[2] * 100.0) / 100.0)
                .historicalVolatility(Math.round(historicalVol * 100.0) / 100.0)
                .relativeStrength(Math.round(relStrength * 100.0) / 100.0)
                // New indicators (Phase 3) - Supertrend & Ichimoku
                .supertrend(Math.round(supertrendValue * 100.0) / 100.0)
                .supertrendBullish(supertrendBullish)
                .tenkanSen(Math.round(tenkanSen * 100.0) / 100.0)
                .kijunSen(Math.round(kijunSen * 100.0) / 100.0)
                .senkouSpanA(Math.round(senkouSpanA * 100.0) / 100.0)
                .senkouSpanB(Math.round(senkouSpanB * 100.0) / 100.0)
                .chikouSpan(Math.round(chikouSpan * 100.0) / 100.0)
                .ichimokuSignal(ichimokuSignal)
                // Fundamental indicators (Phase 3)
                .peRatio(peRatio != null ? Math.round(peRatio * 100.0) / 100.0 : null)
                .epsGrowth(epsGrowth != null ? Math.round(epsGrowth * 100.0) / 100.0 : null)
                .marketCap(marketCap)
                .marketCapCategory(marketCapCategory)
                .emaStackAligned(emaStackAligned)
                .primarySignal(primarySignal)
                .signalConfidence(Math.round(signalConfidence * 100.0) / 100.0)
                .trendDirection(trendDirection)
                .riskLevel(riskLevel)
                .supportLevel(Math.round(supportResistance[0] * 100.0) / 100.0)
                .resistanceLevel(Math.round(supportResistance[1] * 100.0) / 100.0)
                .pivotPoint(Math.round(supportResistance[2] * 100.0) / 100.0)
                .signals(signals)
                .build());

        } catch (Exception e) {
            log.error("Error calculating technical score for {}: {}", ticker, e.getMessage());
            return Optional.empty();
        }
    }

    // ============ Technical Indicator Calculations ============

    private double calculateRSI(List<StockPrice> prices, int period) {
        if (prices.size() < period + 1) return 50.0;

        double avgGain = 0, avgLoss = 0;

        for (int i = prices.size() - period; i < prices.size(); i++) {
            double change = prices.get(i).getClose().doubleValue() -
                           prices.get(i - 1).getClose().doubleValue();
            if (change > 0) avgGain += change;
            else avgLoss += Math.abs(change);
        }

        avgGain /= period;
        avgLoss /= period;

        if (avgLoss == 0) return 100.0;

        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    private double[] calculateMACD(List<StockPrice> prices) {
        if (prices.size() < 26) return new double[]{0, 0, 0};

        double ema12 = calculateEMA(prices, 12);
        double ema26 = calculateEMA(prices, 26);
        double macd = ema12 - ema26;

        // Simplified signal line (should be 9-day EMA of MACD)
        double signal = macd * 0.85;
        double histogram = macd - signal;

        return new double[]{macd, signal, histogram};
    }

    private double calculateSMA(List<StockPrice> prices, int period) {
        if (prices.size() < period) {
            period = prices.size();
        }

        double sum = 0;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            sum += prices.get(i).getClose().doubleValue();
        }
        return sum / period;
    }

    private double calculateEMA(List<StockPrice> prices, int period) {
        if (prices.size() < period) return calculateSMA(prices, prices.size());

        double multiplier = 2.0 / (period + 1);
        double ema = prices.get(prices.size() - period).getClose().doubleValue();

        for (int i = prices.size() - period + 1; i < prices.size(); i++) {
            ema = prices.get(i).getClose().doubleValue() * multiplier + ema * (1 - multiplier);
        }
        return ema;
    }

    private double[] calculateBollingerBands(List<StockPrice> prices, int period, double stdDevMultiplier) {
        double sma = calculateSMA(prices, period);

        double sumSquaredDiff = 0;
        int start = Math.max(0, prices.size() - period);
        for (int i = start; i < prices.size(); i++) {
            double diff = prices.get(i).getClose().doubleValue() - sma;
            sumSquaredDiff += diff * diff;
        }

        double stdDev = Math.sqrt(sumSquaredDiff / period);

        return new double[]{
            sma + stdDev * stdDevMultiplier,  // Upper
            sma,                               // Middle
            sma - stdDev * stdDevMultiplier   // Lower
        };
    }

    private double calculateATR(List<StockPrice> prices, int period) {
        if (prices.size() < period + 1) return 0;

        double atrSum = 0;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            double high = prices.get(i).getHigh().doubleValue();
            double low = prices.get(i).getLow().doubleValue();
            double prevClose = prices.get(i - 1).getClose().doubleValue();

            double tr = Math.max(high - low,
                        Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
            atrSum += tr;
        }
        return atrSum / period;
    }

    private double calculateADX(List<StockPrice> prices, int period) {
        if (prices.size() < period + 1) return 25.0; // Default neutral ADX

        double plusDMSum = 0, minusDMSum = 0, trSum = 0;

        for (int i = prices.size() - period; i < prices.size(); i++) {
            double high = prices.get(i).getHigh().doubleValue();
            double low = prices.get(i).getLow().doubleValue();
            double prevHigh = prices.get(i - 1).getHigh().doubleValue();
            double prevLow = prices.get(i - 1).getLow().doubleValue();
            double prevClose = prices.get(i - 1).getClose().doubleValue();

            double plusDM = high - prevHigh;
            double minusDM = prevLow - low;

            if (plusDM > minusDM && plusDM > 0) plusDMSum += plusDM;
            else plusDM = 0;

            if (minusDM > plusDM && minusDM > 0) minusDMSum += minusDM;
            else minusDM = 0;

            double tr = Math.max(high - low,
                        Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
            trSum += tr;
        }

        if (trSum == 0) return 25.0;

        double plusDI = 100 * plusDMSum / trSum;
        double minusDI = 100 * minusDMSum / trSum;

        double dx = Math.abs(plusDI - minusDI) / (plusDI + minusDI + 0.0001) * 100;
        return dx; // Simplified - real ADX uses smoothed DX
    }

    private double[] calculateStochastic(List<StockPrice> prices, int kPeriod, int dPeriod) {
        if (prices.size() < kPeriod) return new double[]{50, 50};

        // Find highest high and lowest low in period
        double highestHigh = Double.MIN_VALUE;
        double lowestLow = Double.MAX_VALUE;

        for (int i = prices.size() - kPeriod; i < prices.size(); i++) {
            highestHigh = Math.max(highestHigh, prices.get(i).getHigh().doubleValue());
            lowestLow = Math.min(lowestLow, prices.get(i).getLow().doubleValue());
        }

        double currentClose = prices.get(prices.size() - 1).getClose().doubleValue();
        double range = highestHigh - lowestLow;

        double stochK = range > 0 ? ((currentClose - lowestLow) / range) * 100 : 50;
        double stochD = stochK * 0.9; // Simplified D line

        return new double[]{stochK, stochD};
    }

    private double calculateVolumeRatio(List<StockPrice> prices, int period) {
        if (prices.size() < period + 1) return 1.0;

        long currentVolume = prices.get(prices.size() - 1).getVolume();
        long avgVolume = 0;

        for (int i = prices.size() - period - 1; i < prices.size() - 1; i++) {
            avgVolume += prices.get(i).getVolume();
        }
        avgVolume /= period;

        return avgVolume > 0 ? (double) currentVolume / avgVolume : 1.0;
    }

    private double[] calculateSupportResistance(List<StockPrice> prices) {
        if (prices.size() < 20) {
            double currentPrice = prices.get(prices.size() - 1).getClose().doubleValue();
            return new double[]{currentPrice * 0.95, currentPrice * 1.05, currentPrice};
        }

        // Find swing highs and lows
        List<Double> swingHighs = new ArrayList<>();
        List<Double> swingLows = new ArrayList<>();

        for (int i = 5; i < prices.size() - 5; i++) {
            double high = prices.get(i).getHigh().doubleValue();
            double low = prices.get(i).getLow().doubleValue();

            boolean isSwingHigh = true;
            boolean isSwingLow = true;

            for (int j = i - 5; j <= i + 5; j++) {
                if (j != i) {
                    if (prices.get(j).getHigh().doubleValue() >= high) isSwingHigh = false;
                    if (prices.get(j).getLow().doubleValue() <= low) isSwingLow = false;
                }
            }

            if (isSwingHigh) swingHighs.add(high);
            if (isSwingLow) swingLows.add(low);
        }

        double currentPrice = prices.get(prices.size() - 1).getClose().doubleValue();

        // Find nearest support (highest swing low below current price)
        double support = swingLows.stream()
            .filter(l -> l < currentPrice)
            .max(Comparator.naturalOrder())
            .orElse(currentPrice * 0.95);

        // Find nearest resistance (lowest swing high above current price)
        double resistance = swingHighs.stream()
            .filter(h -> h > currentPrice)
            .min(Comparator.naturalOrder())
            .orElse(currentPrice * 1.05);

        double pivot = (support + resistance + currentPrice) / 3;

        return new double[]{support, resistance, pivot};
    }

    // ============ New Technical Indicators ============

    /**
     * Calculate On-Balance Volume (OBV)
     * OBV adds volume on up days and subtracts on down days to confirm price trends
     */
    private long calculateOBV(List<StockPrice> prices) {
        if (prices.size() < 2) return 0;

        long obv = 0;
        for (int i = 1; i < prices.size(); i++) {
            double currentClose = prices.get(i).getClose().doubleValue();
            double prevClose = prices.get(i - 1).getClose().doubleValue();
            long volume = prices.get(i).getVolume();

            if (currentClose > prevClose) {
                obv += volume;
            } else if (currentClose < prevClose) {
                obv -= volume;
            }
            // If equal, OBV stays the same
        }
        return obv;
    }

    /**
     * Calculate OBV trend direction (normalized)
     * Returns value between -100 and 100
     */
    private double calculateOBVTrend(List<StockPrice> prices, int period) {
        if (prices.size() < period + 1) return 0;

        // Calculate current OBV and OBV from 'period' days ago
        long currentOBV = calculateOBV(prices);

        List<StockPrice> olderPrices = prices.subList(0, prices.size() - period);
        long olderOBV = calculateOBV(olderPrices);

        // Calculate average daily volume for normalization
        long avgVolume = 0;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            avgVolume += prices.get(i).getVolume();
        }
        avgVolume /= period;

        if (avgVolume == 0) return 0;

        // Normalize the OBV change as a percentage of expected volume movement
        double obvChange = (currentOBV - olderOBV) / (double) (avgVolume * period);

        // Scale to -100 to 100 range
        return Math.max(-100, Math.min(100, obvChange * 100));
    }

    /**
     * Calculate Williams %R
     * Momentum indicator showing overbought/oversold levels (-100 to 0)
     * -80 to -100 = oversold, -20 to 0 = overbought
     */
    private double calculateWilliamsR(List<StockPrice> prices, int period) {
        if (prices.size() < period) return -50; // Neutral

        double highestHigh = Double.MIN_VALUE;
        double lowestLow = Double.MAX_VALUE;

        for (int i = prices.size() - period; i < prices.size(); i++) {
            highestHigh = Math.max(highestHigh, prices.get(i).getHigh().doubleValue());
            lowestLow = Math.min(lowestLow, prices.get(i).getLow().doubleValue());
        }

        double currentClose = prices.get(prices.size() - 1).getClose().doubleValue();
        double range = highestHigh - lowestLow;

        if (range == 0) return -50;

        // Williams %R formula: ((Highest High - Close) / (Highest High - Lowest Low)) * -100
        return ((highestHigh - currentClose) / range) * -100;
    }

    /**
     * Calculate Parabolic SAR (Stop and Reverse)
     * Returns [SAR value, trend direction (1 = bullish, -1 = bearish)]
     */
    private double[] calculateParabolicSAR(List<StockPrice> prices) {
        if (prices.size() < 5) {
            double currentPrice = prices.get(prices.size() - 1).getClose().doubleValue();
            return new double[]{currentPrice, 1}; // Default bullish
        }

        double af = 0.02;       // Acceleration Factor start
        double afIncrement = 0.02;
        double afMax = 0.20;

        // Initialize with first few bars
        double sar;
        double ep;  // Extreme Point
        int trend;  // 1 = uptrend, -1 = downtrend

        // Determine initial trend from first 5 bars
        double firstClose = prices.get(0).getClose().doubleValue();
        double fifthClose = prices.get(4).getClose().doubleValue();

        if (fifthClose > firstClose) {
            trend = 1;
            sar = prices.get(0).getLow().doubleValue();
            ep = prices.get(0).getHigh().doubleValue();
        } else {
            trend = -1;
            sar = prices.get(0).getHigh().doubleValue();
            ep = prices.get(0).getLow().doubleValue();
        }

        // Calculate SAR for each bar
        for (int i = 1; i < prices.size(); i++) {
            double high = prices.get(i).getHigh().doubleValue();
            double low = prices.get(i).getLow().doubleValue();

            // Update SAR
            sar = sar + af * (ep - sar);

            if (trend == 1) {
                // Uptrend
                sar = Math.min(sar, prices.get(i - 1).getLow().doubleValue());
                if (i > 1) {
                    sar = Math.min(sar, prices.get(i - 2).getLow().doubleValue());
                }

                if (low < sar) {
                    // Trend reversal to downtrend
                    trend = -1;
                    sar = ep;
                    ep = low;
                    af = afIncrement;
                } else {
                    if (high > ep) {
                        ep = high;
                        af = Math.min(af + afIncrement, afMax);
                    }
                }
            } else {
                // Downtrend
                sar = Math.max(sar, prices.get(i - 1).getHigh().doubleValue());
                if (i > 1) {
                    sar = Math.max(sar, prices.get(i - 2).getHigh().doubleValue());
                }

                if (high > sar) {
                    // Trend reversal to uptrend
                    trend = 1;
                    sar = ep;
                    ep = high;
                    af = afIncrement;
                } else {
                    if (low < ep) {
                        ep = low;
                        af = Math.min(af + afIncrement, afMax);
                    }
                }
            }
        }

        return new double[]{sar, trend};
    }

    /**
     * Calculate Volume Weighted Average Price (VWAP)
     * Uses typical price (H+L+C)/3 weighted by volume
     */
    private double calculateVWAP(List<StockPrice> prices, int period) {
        if (prices.isEmpty()) return 0;

        int start = Math.max(0, prices.size() - period);
        double totalTPV = 0;  // Total (Typical Price * Volume)
        long totalVolume = 0;

        for (int i = start; i < prices.size(); i++) {
            StockPrice price = prices.get(i);
            double typicalPrice = (price.getHigh().doubleValue() +
                                  price.getLow().doubleValue() +
                                  price.getClose().doubleValue()) / 3.0;
            long volume = price.getVolume();

            totalTPV += typicalPrice * volume;
            totalVolume += volume;
        }

        return totalVolume > 0 ? totalTPV / totalVolume : 0;
    }

    /**
     * Detect candlestick patterns
     * Returns list of detected patterns with significance
     */
    private List<String> detectCandlestickPatterns(List<StockPrice> prices) {
        List<String> patterns = new ArrayList<>();

        if (prices.size() < 3) return patterns;

        // Get last 3 candles for pattern detection
        StockPrice c1 = prices.get(prices.size() - 3); // Oldest of 3
        StockPrice c2 = prices.get(prices.size() - 2); // Middle
        StockPrice c3 = prices.get(prices.size() - 1); // Most recent

        // Calculate candle properties
        CandleInfo candle1 = analyzeCandleInfo(c1);
        CandleInfo candle2 = analyzeCandleInfo(c2);
        CandleInfo candle3 = analyzeCandleInfo(c3);

        // ===== Single Candle Patterns =====

        // Doji - open and close nearly equal
        if (candle3.isDoji) {
            patterns.add("DOJI (Indecision)");
        }

        // Hammer - small body at top, long lower shadow (bullish reversal)
        if (candle3.isHammer) {
            patterns.add("HAMMER (Bullish Reversal)");
        }

        // Inverted Hammer / Shooting Star
        if (candle3.isInvertedHammer) {
            if (candle3.isBullish) {
                patterns.add("INVERTED_HAMMER (Potential Bullish)");
            } else {
                patterns.add("SHOOTING_STAR (Bearish Reversal)");
            }
        }

        // Marubozu - full body, no shadows (strong momentum)
        if (candle3.isMarubozu) {
            if (candle3.isBullish) {
                patterns.add("BULLISH_MARUBOZU (Strong Buying)");
            } else {
                patterns.add("BEARISH_MARUBOZU (Strong Selling)");
            }
        }

        // ===== Two Candle Patterns =====

        // Bullish Engulfing
        if (!candle2.isBullish && candle3.isBullish &&
            candle3.open < candle2.close && candle3.close > candle2.open &&
            candle3.bodySize > candle2.bodySize) {
            patterns.add("BULLISH_ENGULFING (Strong Buy Signal)");
        }

        // Bearish Engulfing
        if (candle2.isBullish && !candle3.isBullish &&
            candle3.open > candle2.close && candle3.close < candle2.open &&
            candle3.bodySize > candle2.bodySize) {
            patterns.add("BEARISH_ENGULFING (Strong Sell Signal)");
        }

        // Piercing Line (bullish)
        if (!candle2.isBullish && candle3.isBullish &&
            candle3.open < candle2.low &&
            candle3.close > (candle2.open + candle2.close) / 2 &&
            candle3.close < candle2.open) {
            patterns.add("PIERCING_LINE (Bullish Reversal)");
        }

        // Dark Cloud Cover (bearish)
        if (candle2.isBullish && !candle3.isBullish &&
            candle3.open > candle2.high &&
            candle3.close < (candle2.open + candle2.close) / 2 &&
            candle3.close > candle2.open) {
            patterns.add("DARK_CLOUD_COVER (Bearish Reversal)");
        }

        // ===== Three Candle Patterns =====

        // Morning Star (bullish reversal)
        if (!candle1.isBullish && candle1.bodySize > candle2.bodySize * 2 &&
            candle3.isBullish && candle3.close > (candle1.open + candle1.close) / 2 &&
            (candle2.isDoji || candle2.bodySize < candle1.bodySize * 0.3)) {
            patterns.add("MORNING_STAR (Strong Bullish Reversal)");
        }

        // Evening Star (bearish reversal)
        if (candle1.isBullish && candle1.bodySize > candle2.bodySize * 2 &&
            !candle3.isBullish && candle3.close < (candle1.open + candle1.close) / 2 &&
            (candle2.isDoji || candle2.bodySize < candle1.bodySize * 0.3)) {
            patterns.add("EVENING_STAR (Strong Bearish Reversal)");
        }

        // Three White Soldiers (bullish)
        if (candle1.isBullish && candle2.isBullish && candle3.isBullish &&
            candle2.close > candle1.close && candle3.close > candle2.close &&
            candle2.open > candle1.open && candle3.open > candle2.open) {
            patterns.add("THREE_WHITE_SOLDIERS (Strong Bullish)");
        }

        // Three Black Crows (bearish)
        if (!candle1.isBullish && !candle2.isBullish && !candle3.isBullish &&
            candle2.close < candle1.close && candle3.close < candle2.close &&
            candle2.open < candle1.open && candle3.open < candle2.open) {
            patterns.add("THREE_BLACK_CROWS (Strong Bearish)");
        }

        return patterns;
    }

    /**
     * Helper class for candlestick analysis
     */
    private static class CandleInfo {
        double open, high, low, close;
        double bodySize;
        double upperShadow;
        double lowerShadow;
        double totalRange;
        boolean isBullish;
        boolean isDoji;
        boolean isHammer;
        boolean isInvertedHammer;
        boolean isMarubozu;
    }

    private CandleInfo analyzeCandleInfo(StockPrice price) {
        CandleInfo info = new CandleInfo();

        info.open = price.getOpen().doubleValue();
        info.high = price.getHigh().doubleValue();
        info.low = price.getLow().doubleValue();
        info.close = price.getClose().doubleValue();

        info.isBullish = info.close >= info.open;

        double bodyTop = Math.max(info.open, info.close);
        double bodyBottom = Math.min(info.open, info.close);

        info.bodySize = bodyTop - bodyBottom;
        info.upperShadow = info.high - bodyTop;
        info.lowerShadow = bodyBottom - info.low;
        info.totalRange = info.high - info.low;

        // Doji: body is less than 10% of total range
        info.isDoji = info.totalRange > 0 && (info.bodySize / info.totalRange) < 0.1;

        // Hammer: small body at top, long lower shadow (at least 2x body)
        info.isHammer = info.bodySize > 0 &&
                       info.lowerShadow >= info.bodySize * 2 &&
                       info.upperShadow < info.bodySize * 0.5;

        // Inverted Hammer: small body at bottom, long upper shadow
        info.isInvertedHammer = info.bodySize > 0 &&
                               info.upperShadow >= info.bodySize * 2 &&
                               info.lowerShadow < info.bodySize * 0.5;

        // Marubozu: little to no shadows (body is 95%+ of range)
        info.isMarubozu = info.totalRange > 0 && (info.bodySize / info.totalRange) > 0.95;

        return info;
    }

    // ============ Phase 2 Technical Indicators ============

    /**
     * Calculate Rate of Change (ROC)
     * ROC = ((Current Price - Price n periods ago) / Price n periods ago) * 100
     * Measures momentum as percentage change
     */
    private double calculateROC(List<StockPrice> prices, int period) {
        if (prices.size() < period + 1) return 0;

        double currentPrice = prices.get(prices.size() - 1).getClose().doubleValue();
        double pastPrice = prices.get(prices.size() - 1 - period).getClose().doubleValue();

        if (pastPrice == 0) return 0;

        return ((currentPrice - pastPrice) / pastPrice) * 100;
    }

    /**
     * Calculate Commodity Channel Index (CCI)
     * CCI = (Typical Price - SMA of TP) / (0.015 * Mean Deviation)
     * Values > 100 = overbought, < -100 = oversold
     */
    private double calculateCCI(List<StockPrice> prices, int period) {
        if (prices.size() < period) return 0;

        // Calculate typical prices for the period
        double[] typicalPrices = new double[period];
        int start = prices.size() - period;

        for (int i = 0; i < period; i++) {
            StockPrice p = prices.get(start + i);
            typicalPrices[i] = (p.getHigh().doubleValue() +
                               p.getLow().doubleValue() +
                               p.getClose().doubleValue()) / 3.0;
        }

        // Calculate SMA of typical prices
        double smaTP = 0;
        for (double tp : typicalPrices) {
            smaTP += tp;
        }
        smaTP /= period;

        // Calculate Mean Deviation
        double meanDeviation = 0;
        for (double tp : typicalPrices) {
            meanDeviation += Math.abs(tp - smaTP);
        }
        meanDeviation /= period;

        if (meanDeviation == 0) return 0;

        // Current typical price
        double currentTP = typicalPrices[period - 1];

        return (currentTP - smaTP) / (0.015 * meanDeviation);
    }

    /**
     * Calculate Money Flow Index (MFI)
     * Volume-weighted RSI. Range 0-100.
     * > 80 = overbought, < 20 = oversold
     */
    private double calculateMFI(List<StockPrice> prices, int period) {
        if (prices.size() < period + 1) return 50;

        double positiveMoneyFlow = 0;
        double negativeMoneyFlow = 0;

        for (int i = prices.size() - period; i < prices.size(); i++) {
            StockPrice current = prices.get(i);
            StockPrice previous = prices.get(i - 1);

            double currentTP = (current.getHigh().doubleValue() +
                               current.getLow().doubleValue() +
                               current.getClose().doubleValue()) / 3.0;
            double previousTP = (previous.getHigh().doubleValue() +
                                previous.getLow().doubleValue() +
                                previous.getClose().doubleValue()) / 3.0;

            double rawMoneyFlow = currentTP * current.getVolume();

            if (currentTP > previousTP) {
                positiveMoneyFlow += rawMoneyFlow;
            } else if (currentTP < previousTP) {
                negativeMoneyFlow += rawMoneyFlow;
            }
        }

        if (negativeMoneyFlow == 0) return 100;
        if (positiveMoneyFlow == 0) return 0;

        double moneyFlowRatio = positiveMoneyFlow / negativeMoneyFlow;
        return 100 - (100 / (1 + moneyFlowRatio));
    }

    /**
     * Calculate Accumulation/Distribution Line
     * Measures money flow into/out of stock based on close position in range
     * Returns normalized value (-100 to 100)
     */
    private double calculateAccumulationDistribution(List<StockPrice> prices, int period) {
        if (prices.size() < period) return 0;

        double adLine = 0;
        int start = prices.size() - period;

        for (int i = start; i < prices.size(); i++) {
            StockPrice p = prices.get(i);
            double high = p.getHigh().doubleValue();
            double low = p.getLow().doubleValue();
            double close = p.getClose().doubleValue();
            long volume = p.getVolume();

            double range = high - low;
            if (range > 0) {
                // Money Flow Multiplier: ((Close - Low) - (High - Close)) / (High - Low)
                double mfMultiplier = ((close - low) - (high - close)) / range;
                adLine += mfMultiplier * volume;
            }
        }

        // Normalize to -100 to 100 based on average volume
        long avgVolume = 0;
        for (int i = start; i < prices.size(); i++) {
            avgVolume += prices.get(i).getVolume();
        }
        avgVolume /= period;

        if (avgVolume == 0) return 0;

        double normalized = (adLine / (avgVolume * period)) * 100;
        return Math.max(-100, Math.min(100, normalized));
    }

    /**
     * Calculate Chaikin Money Flow (CMF)
     * Sum of Money Flow Volume / Sum of Volume over period
     * Range: -1 to 1 (returned as -100 to 100)
     * Positive = buying pressure, Negative = selling pressure
     */
    private double calculateChaikinMoneyFlow(List<StockPrice> prices, int period) {
        if (prices.size() < period) return 0;

        double sumMFVolume = 0;
        long sumVolume = 0;
        int start = prices.size() - period;

        for (int i = start; i < prices.size(); i++) {
            StockPrice p = prices.get(i);
            double high = p.getHigh().doubleValue();
            double low = p.getLow().doubleValue();
            double close = p.getClose().doubleValue();
            long volume = p.getVolume();

            double range = high - low;
            if (range > 0) {
                double mfMultiplier = ((close - low) - (high - close)) / range;
                sumMFVolume += mfMultiplier * volume;
            }
            sumVolume += volume;
        }

        if (sumVolume == 0) return 0;

        return (sumMFVolume / sumVolume) * 100; // Scale to -100 to 100
    }

    /**
     * Calculate Keltner Channels
     * Middle = EMA(20), Upper/Lower = Middle ± (ATR * multiplier)
     * Returns [upper, middle, lower]
     */
    private double[] calculateKeltnerChannels(List<StockPrice> prices, int period, double multiplier) {
        if (prices.size() < period) {
            double price = prices.get(prices.size() - 1).getClose().doubleValue();
            return new double[]{price * 1.02, price, price * 0.98};
        }

        double ema = calculateEMA(prices, period);
        double atr = calculateATR(prices, Math.min(period, prices.size() - 1));

        return new double[]{
            ema + (atr * multiplier),  // Upper
            ema,                        // Middle
            ema - (atr * multiplier)   // Lower
        };
    }

    /**
     * Calculate Historical Volatility (HV)
     * Standard deviation of log returns, annualized
     * Returns percentage value
     */
    private double calculateHistoricalVolatility(List<StockPrice> prices, int period) {
        if (prices.size() < period + 1) return 0;

        // Calculate log returns
        double[] logReturns = new double[period];
        int start = prices.size() - period;

        for (int i = 0; i < period; i++) {
            double currentClose = prices.get(start + i).getClose().doubleValue();
            double prevClose = prices.get(start + i - 1).getClose().doubleValue();

            if (prevClose > 0) {
                logReturns[i] = Math.log(currentClose / prevClose);
            }
        }

        // Calculate mean of log returns
        double mean = 0;
        for (double r : logReturns) {
            mean += r;
        }
        mean /= period;

        // Calculate variance
        double variance = 0;
        for (double r : logReturns) {
            variance += Math.pow(r - mean, 2);
        }
        variance /= (period - 1);

        // Standard deviation, annualized (252 trading days)
        double dailyVolatility = Math.sqrt(variance);
        double annualizedVolatility = dailyVolatility * Math.sqrt(252);

        return annualizedVolatility * 100; // Return as percentage
    }

    /**
     * Calculate Relative Strength vs Market (S&P 500 proxy)
     * Compares stock performance to market performance
     * > 100 = outperforming, < 100 = underperforming
     * Note: Without S&P 500 data, we estimate using stock's own trend
     */
    private double calculateRelativeStrength(List<StockPrice> prices, int period) {
        if (prices.size() < period + 1) return 100;

        // Calculate stock's performance over period
        double currentPrice = prices.get(prices.size() - 1).getClose().doubleValue();
        double pastPrice = prices.get(prices.size() - 1 - period).getClose().doubleValue();

        if (pastPrice == 0) return 100;

        double stockReturn = ((currentPrice - pastPrice) / pastPrice) * 100;

        // Without market data, we use a baseline expected return
        // Assume market averages ~10% annually = ~0.04% daily = ~0.8% for 20 days
        double expectedMarketReturn = period * 0.04;

        // Relative strength: stock return vs expected market return
        // Normalized to center around 100
        double relativeStrength = 100 + (stockReturn - expectedMarketReturn);

        return Math.max(0, Math.min(200, relativeStrength));
    }

    // ============ Phase 3 Technical Indicators ============

    /**
     * Calculate Supertrend indicator
     * Supertrend = ATR-based trend-following indicator
     * Returns [supertrend value, trend direction (1 = bullish, -1 = bearish)]
     */
    private double[] calculateSupertrend(List<StockPrice> prices, int period, double multiplier) {
        if (prices.size() < period + 1) {
            double price = prices.get(prices.size() - 1).getClose().doubleValue();
            return new double[]{price, 1}; // Default bullish
        }

        double atr = calculateATR(prices, period);
        double currentClose = prices.get(prices.size() - 1).getClose().doubleValue();
        double currentHigh = prices.get(prices.size() - 1).getHigh().doubleValue();
        double currentLow = prices.get(prices.size() - 1).getLow().doubleValue();

        // Calculate HL2 (typical price midpoint)
        double hl2 = (currentHigh + currentLow) / 2.0;

        // Calculate basic upper and lower bands
        double basicUpperBand = hl2 + (multiplier * atr);
        double basicLowerBand = hl2 - (multiplier * atr);

        // Initialize with basic bands
        double upperBand = basicUpperBand;
        double lowerBand = basicLowerBand;

        // Get previous values for band adjustment
        if (prices.size() >= period + 2) {
            double prevClose = prices.get(prices.size() - 2).getClose().doubleValue();
            double prevHigh = prices.get(prices.size() - 2).getHigh().doubleValue();
            double prevLow = prices.get(prices.size() - 2).getLow().doubleValue();
            double prevHl2 = (prevHigh + prevLow) / 2.0;

            // Previous bands (simplified calculation)
            double prevUpperBand = prevHl2 + (multiplier * atr);
            double prevLowerBand = prevHl2 - (multiplier * atr);

            // Adjust upper band: keep previous if price closed above it
            if (prevClose > prevUpperBand || basicUpperBand < prevUpperBand) {
                upperBand = basicUpperBand;
            } else {
                upperBand = prevUpperBand;
            }

            // Adjust lower band: keep previous if price closed below it
            if (prevClose < prevLowerBand || basicLowerBand > prevLowerBand) {
                lowerBand = basicLowerBand;
            } else {
                lowerBand = prevLowerBand;
            }
        }

        // Determine trend direction
        // If close > upper band -> bullish, if close < lower band -> bearish
        double supertrend;
        int trend;

        if (currentClose > upperBand) {
            supertrend = lowerBand;
            trend = 1; // Bullish
        } else if (currentClose < lowerBand) {
            supertrend = upperBand;
            trend = -1; // Bearish
        } else {
            // Check previous trend (simplified: based on close position relative to midpoint)
            double midBand = (upperBand + lowerBand) / 2.0;
            if (currentClose > midBand) {
                supertrend = lowerBand;
                trend = 1;
            } else {
                supertrend = upperBand;
                trend = -1;
            }
        }

        return new double[]{supertrend, trend};
    }

    /**
     * Calculate Ichimoku Cloud components
     * Returns array: [tenkanSen, kijunSen, senkouSpanA, senkouSpanB, chikouSpan]
     *
     * Tenkan-sen (Conversion Line): (9-period high + 9-period low) / 2
     * Kijun-sen (Base Line): (26-period high + 26-period low) / 2
     * Senkou Span A: (Tenkan + Kijun) / 2, plotted 26 periods ahead
     * Senkou Span B: (52-period high + 52-period low) / 2, plotted 26 periods ahead
     * Chikou Span: Current close plotted 26 periods back
     */
    private double[] calculateIchimokuCloud(List<StockPrice> prices) {
        if (prices.size() < 52) {
            double price = prices.get(prices.size() - 1).getClose().doubleValue();
            return new double[]{price, price, price, price, price};
        }

        // Tenkan-sen (9-period)
        double tenkanSen = calculateIchimokuLine(prices, 9);

        // Kijun-sen (26-period)
        double kijunSen = calculateIchimokuLine(prices, 26);

        // Senkou Span A: (Tenkan + Kijun) / 2
        double senkouSpanA = (tenkanSen + kijunSen) / 2.0;

        // Senkou Span B: 52-period
        double senkouSpanB = calculateIchimokuLine(prices, 52);

        // Chikou Span: current close (would be plotted 26 periods back)
        double chikouSpan = prices.get(prices.size() - 1).getClose().doubleValue();

        return new double[]{tenkanSen, kijunSen, senkouSpanA, senkouSpanB, chikouSpan};
    }

    /**
     * Calculate Ichimoku line (highest high + lowest low) / 2 for given period
     */
    private double calculateIchimokuLine(List<StockPrice> prices, int period) {
        int start = Math.max(0, prices.size() - period);
        double highestHigh = Double.MIN_VALUE;
        double lowestLow = Double.MAX_VALUE;

        for (int i = start; i < prices.size(); i++) {
            highestHigh = Math.max(highestHigh, prices.get(i).getHigh().doubleValue());
            lowestLow = Math.min(lowestLow, prices.get(i).getLow().doubleValue());
        }

        return (highestHigh + lowestLow) / 2.0;
    }

    /**
     * Determine Ichimoku signal based on cloud and price position
     * BULLISH: Price above cloud, Tenkan > Kijun
     * BEARISH: Price below cloud, Tenkan < Kijun
     * NEUTRAL: Price inside cloud or mixed signals
     */
    private String determineIchimokuSignal(double price, double tenkanSen, double kijunSen,
                                           double senkouSpanA, double senkouSpanB) {
        double cloudTop = Math.max(senkouSpanA, senkouSpanB);
        double cloudBottom = Math.min(senkouSpanA, senkouSpanB);

        boolean priceAboveCloud = price > cloudTop;
        boolean priceBelowCloud = price < cloudBottom;
        boolean tenkanAboveKijun = tenkanSen > kijunSen;

        if (priceAboveCloud && tenkanAboveKijun) {
            return "BULLISH";
        } else if (priceBelowCloud && !tenkanAboveKijun) {
            return "BEARISH";
        } else {
            return "NEUTRAL";
        }
    }

    /**
     * Categorize market cap into standard tiers
     */
    private String categorizeMarketCap(Long marketCap) {
        if (marketCap == null || marketCap <= 0) return "UNKNOWN";

        if (marketCap >= 200_000_000_000L) return "MEGA";      // >= $200B
        if (marketCap >= 10_000_000_000L) return "LARGE";      // >= $10B
        if (marketCap >= 2_000_000_000L) return "MID";         // >= $2B
        if (marketCap >= 300_000_000L) return "SMALL";         // >= $300M
        return "MICRO";                                         // < $300M
    }

    // ============ Score Calculations ============

    private double calculateTrendScore(double price, double sma20, double sma50, double sma200, boolean aligned) {
        double score = 50; // Base score

        // Price relative to SMAs
        if (price > sma20) score += 10;
        if (price > sma50) score += 10;
        if (price > sma200) score += 10;

        // EMA alignment bonus
        if (aligned) score += 15;

        // Golden cross (SMA20 > SMA50 when it wasn't before)
        if (sma20 > sma50) score += 5;

        return Math.max(0, Math.min(100, score));
    }

    private double calculateMomentumScore(double rsi, double macd, double macdHist, double stochK) {
        double score = 50;

        // RSI scoring
        if (rsi >= 30 && rsi <= 70) {
            score += 10; // Healthy range
        } else if (rsi < 30) {
            score += 15; // Oversold - potential buy
        } else {
            score -= 10; // Overbought - caution
        }

        // MACD scoring
        if (macd > 0) score += 10;
        if (macdHist > 0) score += 10; // Bullish momentum

        // Stochastic scoring
        if (stochK >= 20 && stochK <= 80) {
            score += 10;
        } else if (stochK < 20) {
            score += 15; // Oversold
        }

        return Math.max(0, Math.min(100, score));
    }

    private double calculateVolatilityScore(double atr, double price, double adx) {
        double score = 50;

        // ATR as percentage of price
        double atrPercent = (atr / price) * 100;

        // Lower volatility is generally safer
        if (atrPercent < 2) score += 20;
        else if (atrPercent < 3) score += 10;
        else if (atrPercent > 5) score -= 15;

        // ADX scoring - strong trend is good
        if (adx > 25) score += 15;
        else if (adx < 20) score -= 10;

        return Math.max(0, Math.min(100, score));
    }

    private double calculateVolumeScore(double volumeRatio) {
        double score = 50;

        if (volumeRatio > 2.0) score += 25; // Very high volume
        else if (volumeRatio > 1.5) score += 15;
        else if (volumeRatio > 1.0) score += 5;
        else if (volumeRatio < 0.5) score -= 15; // Low volume

        return Math.max(0, Math.min(100, score));
    }

    private String determinePrimarySignal(double score, double rsi, double macdHist,
                                         boolean bullishEma, boolean bearishEma) {
        int bullishSignals = 0;
        int bearishSignals = 0;

        if (score >= 65) bullishSignals++;
        if (score < 45) bearishSignals++;

        if (rsi < 35) bullishSignals++; // Oversold
        if (rsi > 65) bearishSignals++; // Overbought

        if (macdHist > 0) bullishSignals++;
        if (macdHist < 0) bearishSignals++;

        if (bullishEma) bullishSignals++;
        if (bearishEma) bearishSignals++;

        if (bullishSignals >= 3) return "BULLISH";
        if (bearishSignals >= 3) return "BEARISH";
        return "NEUTRAL";
    }

    private double calculateSignalConfidence(double score, double adx, double volumeRatio) {
        double confidence = 50;

        // Score extremes increase confidence
        if (score > 75 || score < 35) confidence += 15;

        // Strong trend (high ADX) increases confidence
        if (adx > 30) confidence += 15;
        else if (adx > 25) confidence += 10;

        // High volume confirms signal
        if (volumeRatio > 1.5) confidence += 10;

        return Math.max(40, Math.min(95, confidence));
    }

    private String determineTrendDirection(double price, double sma20, double sma50) {
        if (price > sma20 && sma20 > sma50) return "UP";
        if (price < sma20 && sma20 < sma50) return "DOWN";
        return "SIDEWAYS";
    }

    private String determineRiskLevel(double atr, double price, double rsi, double bbPosition) {
        int riskFactors = 0;

        double atrPercent = (atr / price) * 100;
        if (atrPercent > 4) riskFactors++;
        if (atrPercent > 6) riskFactors++;

        if (rsi < 25 || rsi > 75) riskFactors++;

        if (bbPosition < 0.1 || bbPosition > 0.9) riskFactors++;

        if (riskFactors >= 3) return "HIGH";
        if (riskFactors >= 1) return "MEDIUM";
        return "LOW";
    }

    private List<String> collectSignals(double rsi, double[] macd, double[] stochastic,
                                       double bbPosition, boolean bullishEma, boolean bearishEma,
                                       double adx, double volumeRatio,
                                       double williamsR, double obvTrend, boolean sarBullish,
                                       double currentPrice, double vwap, List<String> candlestickPatterns,
                                       double roc, double cci, double mfi, double cmf, double relStrength,
                                       boolean supertrendBullish, String ichimokuSignal) {
        List<String> signals = new ArrayList<>();

        // RSI signals
        if (rsi < 30) signals.add("RSI Oversold");
        else if (rsi > 70) signals.add("RSI Overbought");

        // MACD signals
        if (macd[2] > 0 && macd[0] > 0) signals.add("MACD Bullish");
        else if (macd[2] < 0 && macd[0] < 0) signals.add("MACD Bearish");

        // Stochastic signals
        if (stochastic[0] < 20) signals.add("Stochastic Oversold");
        else if (stochastic[0] > 80) signals.add("Stochastic Overbought");

        // Bollinger Band signals
        if (bbPosition < 0.1) signals.add("Near Lower BB");
        else if (bbPosition > 0.9) signals.add("Near Upper BB");

        // EMA signals
        if (bullishEma) signals.add("Bullish EMA Stack");
        else if (bearishEma) signals.add("Bearish EMA Stack");

        // ADX signals
        if (adx > 25) signals.add("Strong Trend (ADX)");
        else if (adx < 20) signals.add("Weak Trend");

        // Volume signals
        if (volumeRatio > 2.0) signals.add("High Volume Breakout");
        else if (volumeRatio < 0.5) signals.add("Low Volume");

        // ===== PHASE 1 INDICATOR SIGNALS =====

        // Williams %R signals (-80 to -100 = oversold, -20 to 0 = overbought)
        if (williamsR < -80) signals.add("Williams %R Oversold");
        else if (williamsR > -20) signals.add("Williams %R Overbought");

        // OBV trend signals
        if (obvTrend > 30) signals.add("OBV Bullish Divergence");
        else if (obvTrend < -30) signals.add("OBV Bearish Divergence");

        // Parabolic SAR signals
        if (sarBullish) signals.add("SAR Bullish (Price Above SAR)");
        else signals.add("SAR Bearish (Price Below SAR)");

        // VWAP signals
        if (vwap > 0) {
            double vwapDiff = ((currentPrice - vwap) / vwap) * 100;
            if (vwapDiff > 2) signals.add("Price Above VWAP (+2%)");
            else if (vwapDiff < -2) signals.add("Price Below VWAP (-2%)");
        }

        // Add candlestick pattern signals (limit to top 3 most significant)
        int patternCount = 0;
        for (String pattern : candlestickPatterns) {
            if (patternCount >= 3) break;
            signals.add("Candle: " + pattern);
            patternCount++;
        }

        // ===== PHASE 2 INDICATOR SIGNALS =====

        // ROC (Rate of Change) signals
        if (roc > 5) signals.add("ROC Strong Momentum (+5%)");
        else if (roc < -5) signals.add("ROC Weak Momentum (-5%)");

        // CCI (Commodity Channel Index) signals
        if (cci > 100) signals.add("CCI Overbought (>100)");
        else if (cci < -100) signals.add("CCI Oversold (<-100)");

        // MFI (Money Flow Index) signals
        if (mfi > 80) signals.add("MFI Overbought (>80)");
        else if (mfi < 20) signals.add("MFI Oversold (<20)");

        // Chaikin Money Flow signals
        if (cmf > 20) signals.add("CMF Strong Buying Pressure");
        else if (cmf < -20) signals.add("CMF Strong Selling Pressure");

        // Relative Strength signals
        if (relStrength > 110) signals.add("Outperforming Market (+10%)");
        else if (relStrength < 90) signals.add("Underperforming Market (-10%)");

        // ===== PHASE 3 INDICATOR SIGNALS =====

        // Supertrend signals
        if (supertrendBullish) signals.add("Supertrend Bullish (Price Above)");
        else signals.add("Supertrend Bearish (Price Below)");

        // Ichimoku Cloud signals
        if ("BULLISH".equals(ichimokuSignal)) signals.add("Ichimoku Bullish (Above Cloud)");
        else if ("BEARISH".equals(ichimokuSignal)) signals.add("Ichimoku Bearish (Below Cloud)");
        else signals.add("Ichimoku Neutral (In Cloud)");

        return signals;
    }
}
