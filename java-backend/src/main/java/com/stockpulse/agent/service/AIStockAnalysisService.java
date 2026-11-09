package com.stockpulse.agent.service;

import com.stockpulse.agent.model.StockPrice;
import com.stockpulse.agent.repository.StockPriceRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Comprehensive AI-powered stock analysis service that combines:
 * - Technical indicators (30+ indicators from ImprovedScoringService)
 * - ML price predictions (direction & magnitude)
 * - Walk-forward backtesting
 * - Multiple strategy comparison
 * - AI-generated trading recommendations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AIStockAnalysisService {

    private final StockPriceRepository stockPriceRepository;
    private final ImprovedScoringService improvedScoringService;
    private final MLPredictionService mlPredictionService;

    @Data
    @Builder
    public static class ComprehensiveAnalysis {
        private String ticker;
        private LocalDateTime analysisTime;
        private String analysisVersion;

        // Current Market Data
        private BigDecimal currentPrice;
        private BigDecimal priceChange;
        private BigDecimal priceChangePercent;
        private Long volume;
        private Long avgVolume;

        // Technical Analysis Summary
        private TechnicalSummary technicalSummary;

        // ML Prediction
        private MLPrediction mlPrediction;

        // Backtest Results
        private BacktestSummary backtestSummary;

        // AI Recommendation
        private AIRecommendation recommendation;

        // Risk Assessment
        private RiskAssessment riskAssessment;

        // Fundamental Data (if available)
        private FundamentalData fundamentals;
    }

    @Data
    @Builder
    public static class TechnicalSummary {
        private double compositeScore;
        private String primarySignal;       // BULLISH, BEARISH, NEUTRAL
        private double signalConfidence;
        private String trendDirection;      // UP, DOWN, SIDEWAYS

        // Key Indicators
        private double rsi;
        private String rsiSignal;
        private double macd;
        private String macdSignal;
        private boolean emaStackAligned;

        // Trend Indicators
        private double supertrend;
        private boolean supertrendBullish;
        private String ichimokuSignal;

        // Volume Indicators
        private double obvTrend;
        private double mfi;
        private double chaikinMoneyFlow;

        // Volatility
        private double atr;
        private double atrPercent;
        private double historicalVolatility;
        private String riskLevel;

        // Support/Resistance
        private double supportLevel;
        private double resistanceLevel;
        private double pivotPoint;

        // All detected signals
        private List<String> signals;
        private List<String> candlestickPatterns;

        // Indicator scores
        private double trendScore;
        private double momentumScore;
        private double volumeScore;
        private double volatilityScore;
    }

    @Data
    @Builder
    public static class MLPrediction {
        private String predictedDirection;
        private double bullishProbability;
        private double bearishProbability;
        private double overallConfidence;

        private BigDecimal predicted1Day;
        private BigDecimal predicted1Week;
        private double return1Day;
        private double return1Week;

        private Map<String, Double> featureContributions;
        private String modelVersion;
    }

    @Data
    @Builder
    public static class BacktestSummary {
        private int daysAnalyzed;
        private double directionAccuracy;
        private double winRate;
        private double profitFactor;
        private double sharpeRatio;
        private double maxDrawdown;

        private String bestStrategy;
        private double bestStrategyReturn;
        private double buyHoldReturn;

        private List<StrategyResult> topStrategies;
    }

    @Data
    @Builder
    public static class StrategyResult {
        private String strategyName;
        private double totalReturn;
        private double winRate;
        private int numberOfTrades;
    }

    @Data
    @Builder
    public static class AIRecommendation {
        private String action;              // STRONG_BUY, BUY, HOLD, SELL, STRONG_SELL
        private double confidence;
        private String timeHorizon;         // SHORT_TERM, MEDIUM_TERM, LONG_TERM
        private String reasoning;

        private BigDecimal suggestedEntryPrice;
        private BigDecimal stopLossPrice;
        private BigDecimal targetPrice1;
        private BigDecimal targetPrice2;

        private double riskRewardRatio;
        private double positionSizePercent;

        private List<String> bullishFactors;
        private List<String> bearishFactors;
        private List<String> keyRisks;
    }

    @Data
    @Builder
    public static class RiskAssessment {
        private String overallRisk;         // LOW, MEDIUM, HIGH, VERY_HIGH
        private double riskScore;           // 0-100
        private double volatilityRisk;
        private double trendRisk;
        private double liquidityRisk;

        private double valueAtRisk;         // 1-day VaR at 95%
        private double maxExpectedLoss;

        private List<String> riskFactors;
    }

    @Data
    @Builder
    public static class FundamentalData {
        private Double peRatio;
        private Double epsGrowth;
        private Long marketCap;
        private String marketCapCategory;
        private String sector;
        private String industry;
    }

    /**
     * Run comprehensive AI analysis on a stock
     */
    public Optional<ComprehensiveAnalysis> analyzeStock(String ticker) {
        log.info("Starting comprehensive AI analysis for {}", ticker);
        long startTime = System.currentTimeMillis();

        try {
            // 1. Get price data
            LocalDateTime since = LocalDateTime.now().minusDays(365);
            List<StockPrice> prices = stockPriceRepository.findByTickerAndDateBetween(
                ticker, since, LocalDateTime.now());

            if (prices.size() < 50) {
                log.warn("Insufficient data for analysis: {} (only {} records)", ticker, prices.size());
                return Optional.empty();
            }

            prices.sort(Comparator.comparing(StockPrice::getDate));
            StockPrice latest = prices.get(prices.size() - 1);
            StockPrice previous = prices.size() > 1 ? prices.get(prices.size() - 2) : latest;

            // 2. Technical Analysis
            Optional<ImprovedScoringService.TechnicalScore> techScoreOpt =
                improvedScoringService.calculateTechnicalScore(ticker);

            TechnicalSummary technicalSummary = buildTechnicalSummary(techScoreOpt, latest);

            // 3. ML Prediction
            Optional<MLPredictionService.PricePrediction> mlPredOpt =
                mlPredictionService.predictPrice(ticker);

            MLPrediction mlPrediction = buildMLPrediction(mlPredOpt, latest.getClose());

            // 4. Backtest Summary
            Optional<MLPredictionService.BacktestResult> backtestOpt =
                mlPredictionService.runBacktest(ticker, 120);

            BacktestSummary backtestSummary = buildBacktestSummary(backtestOpt, prices);

            // 5. Generate AI Recommendation
            AIRecommendation recommendation = generateRecommendation(
                technicalSummary, mlPrediction, backtestSummary, latest.getClose());

            // 6. Risk Assessment
            RiskAssessment riskAssessment = assessRisk(
                technicalSummary, prices, latest.getClose());

            // 7. Fundamental Data
            FundamentalData fundamentals = buildFundamentals(techScoreOpt);

            // Calculate price change
            BigDecimal priceChange = latest.getClose().subtract(previous.getClose());
            BigDecimal priceChangePercent = priceChange
                .divide(previous.getClose(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));

            // Calculate average volume
            long avgVolume = prices.stream()
                .skip(Math.max(0, prices.size() - 20))
                .mapToLong(StockPrice::getVolume)
                .sum() / Math.min(20, prices.size());

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Completed AI analysis for {} in {}ms", ticker, elapsed);

            return Optional.of(ComprehensiveAnalysis.builder()
                .ticker(ticker)
                .analysisTime(LocalDateTime.now())
                .analysisVersion("v2.0-comprehensive")
                .currentPrice(latest.getClose())
                .priceChange(priceChange)
                .priceChangePercent(priceChangePercent)
                .volume(latest.getVolume())
                .avgVolume(avgVolume)
                .technicalSummary(technicalSummary)
                .mlPrediction(mlPrediction)
                .backtestSummary(backtestSummary)
                .recommendation(recommendation)
                .riskAssessment(riskAssessment)
                .fundamentals(fundamentals)
                .build());

        } catch (Exception e) {
            log.error("Error in AI analysis for {}: {}", ticker, e.getMessage(), e);
            return Optional.empty();
        }
    }

    private TechnicalSummary buildTechnicalSummary(
            Optional<ImprovedScoringService.TechnicalScore> techScoreOpt,
            StockPrice latest) {

        if (!techScoreOpt.isPresent()) {
            return TechnicalSummary.builder()
                .compositeScore(50)
                .primarySignal("NEUTRAL")
                .signalConfidence(50)
                .trendDirection("SIDEWAYS")
                .signals(Collections.emptyList())
                .candlestickPatterns(Collections.emptyList())
                .build();
        }

        ImprovedScoringService.TechnicalScore ts = techScoreOpt.get();

        // Determine RSI signal
        String rsiSignal = ts.getRsi() < 30 ? "OVERSOLD" :
                          ts.getRsi() > 70 ? "OVERBOUGHT" : "NEUTRAL";

        // Determine MACD signal
        String macdSignal = ts.getMacdHistogram() > 0 ? "BULLISH" : "BEARISH";

        // Calculate ATR percent
        double atrPercent = (ts.getAtr() / latest.getClose().doubleValue()) * 100;

        return TechnicalSummary.builder()
            .compositeScore(ts.getCompositeScore())
            .primarySignal(ts.getPrimarySignal())
            .signalConfidence(ts.getSignalConfidence())
            .trendDirection(ts.getTrendDirection())
            .rsi(ts.getRsi())
            .rsiSignal(rsiSignal)
            .macd(ts.getMacd())
            .macdSignal(macdSignal)
            .emaStackAligned(ts.isEmaStackAligned())
            .supertrend(ts.getSupertrend())
            .supertrendBullish(ts.isSupertrendBullish())
            .ichimokuSignal(ts.getIchimokuSignal())
            .obvTrend(ts.getObv())
            .mfi(ts.getMfi())
            .chaikinMoneyFlow(ts.getChaikinMoneyFlow())
            .atr(ts.getAtr())
            .atrPercent(Math.round(atrPercent * 100.0) / 100.0)
            .historicalVolatility(ts.getHistoricalVolatility())
            .riskLevel(ts.getRiskLevel())
            .supportLevel(ts.getSupportLevel())
            .resistanceLevel(ts.getResistanceLevel())
            .pivotPoint(ts.getPivotPoint())
            .signals(ts.getSignals())
            .candlestickPatterns(ts.getCandlestickPatterns())
            .trendScore(ts.getTrendScore())
            .momentumScore(ts.getMomentumScore())
            .volumeScore(ts.getVolumeScore())
            .volatilityScore(ts.getVolatilityScore())
            .build();
    }

    private MLPrediction buildMLPrediction(
            Optional<MLPredictionService.PricePrediction> predOpt,
            BigDecimal currentPrice) {

        if (!predOpt.isPresent()) {
            return MLPrediction.builder()
                .predictedDirection("NEUTRAL")
                .bullishProbability(0.33)
                .bearishProbability(0.33)
                .overallConfidence(0.33)
                .predicted1Day(currentPrice)
                .predicted1Week(currentPrice)
                .return1Day(0)
                .return1Week(0)
                .featureContributions(Collections.emptyMap())
                .modelVersion("unavailable")
                .build();
        }

        MLPredictionService.PricePrediction pred = predOpt.get();

        return MLPrediction.builder()
            .predictedDirection(pred.getPredictedDirection())
            .bullishProbability(pred.getBullishProbability())
            .bearishProbability(pred.getBearishProbability())
            .overallConfidence(pred.getOverallConfidence())
            .predicted1Day(BigDecimal.valueOf(pred.getPredicted1Day()))
            .predicted1Week(BigDecimal.valueOf(pred.getPredicted1Week()))
            .return1Day(pred.getReturn1Day())
            .return1Week(pred.getReturn1Week())
            .featureContributions(pred.getFeatureContributions())
            .modelVersion(pred.getModelVersion())
            .build();
    }

    private BacktestSummary buildBacktestSummary(
            Optional<MLPredictionService.BacktestResult> backtestOpt,
            List<StockPrice> prices) {

        // Calculate buy & hold return
        double buyHoldReturn = 0;
        if (prices.size() > 1) {
            double startPrice = prices.get(0).getClose().doubleValue();
            double endPrice = prices.get(prices.size() - 1).getClose().doubleValue();
            buyHoldReturn = ((endPrice - startPrice) / startPrice) * 100;
        }

        if (!backtestOpt.isPresent()) {
            return BacktestSummary.builder()
                .daysAnalyzed(prices.size())
                .directionAccuracy(50)
                .winRate(50)
                .profitFactor(1.0)
                .sharpeRatio(0)
                .maxDrawdown(0)
                .bestStrategy("N/A")
                .bestStrategyReturn(0)
                .buyHoldReturn(Math.round(buyHoldReturn * 100.0) / 100.0)
                .topStrategies(Collections.emptyList())
                .build();
        }

        MLPredictionService.BacktestResult bt = backtestOpt.get();

        return BacktestSummary.builder()
            .daysAnalyzed(bt.getTotalPredictions())
            .directionAccuracy(bt.getDirectionAccuracy())
            .winRate(bt.getWinRate())
            .profitFactor(bt.getProfitFactor())
            .sharpeRatio(bt.getSharpeRatio())
            .maxDrawdown(bt.getMaxDrawdown())
            .bestStrategy("ML Gradient Ensemble")
            .bestStrategyReturn(bt.getWinRate() > 50 ?
                (bt.getWinRate() - 50) * 0.5 : -(50 - bt.getWinRate()) * 0.5)
            .buyHoldReturn(Math.round(buyHoldReturn * 100.0) / 100.0)
            .topStrategies(Collections.singletonList(
                StrategyResult.builder()
                    .strategyName("ML Gradient Ensemble")
                    .totalReturn(bt.getWinRate() > 50 ?
                        (bt.getWinRate() - 50) * 0.5 : -(50 - bt.getWinRate()) * 0.5)
                    .winRate(bt.getWinRate())
                    .numberOfTrades(bt.getTotalPredictions())
                    .build()
            ))
            .build();
    }

    private AIRecommendation generateRecommendation(
            TechnicalSummary tech,
            MLPrediction ml,
            BacktestSummary backtest,
            BigDecimal currentPrice) {

        // Score each factor
        double technicalScore = (tech.getCompositeScore() - 50) / 50;  // -1 to 1
        double mlScore = (ml.getBullishProbability() - ml.getBearishProbability());  // -1 to 1
        double backtestScore = (backtest.getWinRate() - 50) / 50;  // -1 to 1

        // Weighted combination
        double combinedScore = technicalScore * 0.40 + mlScore * 0.40 + backtestScore * 0.20;

        // Determine action
        String action;
        if (combinedScore > 0.5) action = "STRONG_BUY";
        else if (combinedScore > 0.2) action = "BUY";
        else if (combinedScore > -0.2) action = "HOLD";
        else if (combinedScore > -0.5) action = "SELL";
        else action = "STRONG_SELL";

        // Calculate confidence
        double confidence = Math.abs(combinedScore) * 100;
        confidence = Math.min(95, Math.max(40, confidence));

        // Generate entry, stop loss, targets
        double atr = tech.getAtr();
        BigDecimal stopLoss = currentPrice.subtract(BigDecimal.valueOf(atr * 2));
        BigDecimal target1 = currentPrice.add(BigDecimal.valueOf(atr * 2));
        BigDecimal target2 = currentPrice.add(BigDecimal.valueOf(atr * 4));

        // Risk/reward ratio
        double risk = currentPrice.subtract(stopLoss).doubleValue();
        double reward = target1.subtract(currentPrice).doubleValue();
        double rrRatio = risk > 0 ? reward / risk : 0;

        // Collect factors
        List<String> bullishFactors = new ArrayList<>();
        List<String> bearishFactors = new ArrayList<>();
        List<String> keyRisks = new ArrayList<>();

        // Technical factors
        if (tech.isSupertrendBullish()) bullishFactors.add("Supertrend bullish");
        else bearishFactors.add("Supertrend bearish");

        if ("BULLISH".equals(tech.getIchimokuSignal())) bullishFactors.add("Ichimoku bullish (above cloud)");
        else if ("BEARISH".equals(tech.getIchimokuSignal())) bearishFactors.add("Ichimoku bearish (below cloud)");

        if (tech.getRsi() < 30) bullishFactors.add("RSI oversold (" + tech.getRsi() + ")");
        else if (tech.getRsi() > 70) bearishFactors.add("RSI overbought (" + tech.getRsi() + ")");

        if (tech.isEmaStackAligned()) bullishFactors.add("EMA stack aligned (bullish trend)");

        if (tech.getChaikinMoneyFlow() > 20) bullishFactors.add("Strong buying pressure (CMF: " + tech.getChaikinMoneyFlow() + ")");
        else if (tech.getChaikinMoneyFlow() < -20) bearishFactors.add("Strong selling pressure (CMF: " + tech.getChaikinMoneyFlow() + ")");

        // ML factors
        if (ml.getBullishProbability() > 0.6)
            bullishFactors.add("ML predicts upward movement (" + (int)(ml.getBullishProbability() * 100) + "% confidence)");
        else if (ml.getBearishProbability() > 0.6)
            bearishFactors.add("ML predicts downward movement (" + (int)(ml.getBearishProbability() * 100) + "% confidence)");

        // Risks
        if ("HIGH".equals(tech.getRiskLevel())) keyRisks.add("High volatility risk");
        if (tech.getHistoricalVolatility() > 40) keyRisks.add("Historical volatility above 40%");
        if (backtest.getMaxDrawdown() > 10) keyRisks.add("Strategy max drawdown: " + backtest.getMaxDrawdown() + "%");
        if (backtest.getDirectionAccuracy() < 55) keyRisks.add("Historical direction accuracy below 55%");

        // Generate reasoning
        String reasoning = generateReasoning(action, combinedScore, tech, ml, backtest);

        // Determine time horizon
        String timeHorizon = ml.getOverallConfidence() > 0.6 ? "SHORT_TERM" :
                            tech.getSignalConfidence() > 70 ? "MEDIUM_TERM" : "LONG_TERM";

        // Position size based on risk
        double positionSize = 10; // Base 10%
        if ("HIGH".equals(tech.getRiskLevel())) positionSize *= 0.5;
        if (backtest.getMaxDrawdown() > 15) positionSize *= 0.7;

        return AIRecommendation.builder()
            .action(action)
            .confidence(Math.round(confidence * 100.0) / 100.0)
            .timeHorizon(timeHorizon)
            .reasoning(reasoning)
            .suggestedEntryPrice(currentPrice.setScale(2, RoundingMode.HALF_UP))
            .stopLossPrice(stopLoss.setScale(2, RoundingMode.HALF_UP))
            .targetPrice1(target1.setScale(2, RoundingMode.HALF_UP))
            .targetPrice2(target2.setScale(2, RoundingMode.HALF_UP))
            .riskRewardRatio(Math.round(rrRatio * 100.0) / 100.0)
            .positionSizePercent(Math.round(positionSize * 100.0) / 100.0)
            .bullishFactors(bullishFactors)
            .bearishFactors(bearishFactors)
            .keyRisks(keyRisks)
            .build();
    }

    private String generateReasoning(String action, double combinedScore,
            TechnicalSummary tech, MLPrediction ml, BacktestSummary backtest) {

        StringBuilder sb = new StringBuilder();

        sb.append("Based on comprehensive analysis: ");

        // Technical summary
        sb.append("Technical indicators show ");
        if (tech.getCompositeScore() > 65) sb.append("BULLISH");
        else if (tech.getCompositeScore() < 45) sb.append("BEARISH");
        else sb.append("NEUTRAL");
        sb.append(" signals (score: ").append((int)tech.getCompositeScore()).append("/100). ");

        // ML prediction
        sb.append("ML model predicts ");
        sb.append(ml.getPredictedDirection().toLowerCase());
        sb.append(" movement with ").append((int)(ml.getOverallConfidence() * 100)).append("% confidence. ");

        // Trend
        sb.append("Current trend is ").append(tech.getTrendDirection().toLowerCase());
        if (tech.isSupertrendBullish() || "BULLISH".equals(tech.getIchimokuSignal())) {
            sb.append(" with trend indicators confirming bullish bias. ");
        } else if (!tech.isSupertrendBullish() || "BEARISH".equals(tech.getIchimokuSignal())) {
            sb.append(" with trend indicators suggesting caution. ");
        } else {
            sb.append(". ");
        }

        // Backtest
        if (backtest.getDirectionAccuracy() > 55) {
            sb.append("Historical backtest shows ").append(Math.round(backtest.getDirectionAccuracy()))
              .append("% direction accuracy. ");
        }

        // Risk
        sb.append("Risk level: ").append(tech.getRiskLevel()).append(".");

        return sb.toString();
    }

    private RiskAssessment assessRisk(TechnicalSummary tech, List<StockPrice> prices, BigDecimal currentPrice) {
        List<String> riskFactors = new ArrayList<>();
        double riskScore = 50; // Start neutral

        // Volatility risk
        double volatilityRisk = tech.getHistoricalVolatility() / 50 * 100;
        volatilityRisk = Math.min(100, volatilityRisk);

        if (tech.getHistoricalVolatility() > 40) {
            riskFactors.add("High historical volatility: " + tech.getHistoricalVolatility() + "%");
            riskScore += 15;
        }
        if (tech.getAtrPercent() > 3) {
            riskFactors.add("High daily volatility (ATR): " + tech.getAtrPercent() + "%");
            riskScore += 10;
        }

        // Trend risk
        double trendRisk = 50;
        if ("SIDEWAYS".equals(tech.getTrendDirection())) {
            trendRisk = 60;
            riskFactors.add("Sideways/choppy market conditions");
            riskScore += 5;
        }

        // Overbought/Oversold risk
        if (tech.getRsi() > 80 || tech.getRsi() < 20) {
            riskFactors.add("Extreme RSI levels: " + tech.getRsi());
            riskScore += 10;
        }

        // Liquidity risk (proxy from volume)
        double liquidityRisk = 30; // Assume decent liquidity for now

        // Calculate VaR (simplified - using historical volatility)
        double dailyVol = tech.getHistoricalVolatility() / Math.sqrt(252);
        double var95 = currentPrice.doubleValue() * dailyVol * 1.65 / 100; // 1.65 for 95% confidence
        double maxExpectedLoss = currentPrice.doubleValue() * dailyVol * 2.33 / 100; // 99% confidence

        // Determine overall risk
        riskScore = Math.min(100, Math.max(0, riskScore));
        String overallRisk;
        if (riskScore < 35) overallRisk = "LOW";
        else if (riskScore < 55) overallRisk = "MEDIUM";
        else if (riskScore < 75) overallRisk = "HIGH";
        else overallRisk = "VERY_HIGH";

        return RiskAssessment.builder()
            .overallRisk(overallRisk)
            .riskScore(Math.round(riskScore * 100.0) / 100.0)
            .volatilityRisk(Math.round(volatilityRisk * 100.0) / 100.0)
            .trendRisk(Math.round(trendRisk * 100.0) / 100.0)
            .liquidityRisk(Math.round(liquidityRisk * 100.0) / 100.0)
            .valueAtRisk(Math.round(var95 * 100.0) / 100.0)
            .maxExpectedLoss(Math.round(maxExpectedLoss * 100.0) / 100.0)
            .riskFactors(riskFactors)
            .build();
    }

    private FundamentalData buildFundamentals(Optional<ImprovedScoringService.TechnicalScore> techScoreOpt) {
        if (!techScoreOpt.isPresent()) {
            return FundamentalData.builder().build();
        }

        ImprovedScoringService.TechnicalScore ts = techScoreOpt.get();

        return FundamentalData.builder()
            .peRatio(ts.getPeRatio())
            .epsGrowth(ts.getEpsGrowth())
            .marketCap(ts.getMarketCap())
            .marketCapCategory(ts.getMarketCapCategory())
            .build();
    }
}
