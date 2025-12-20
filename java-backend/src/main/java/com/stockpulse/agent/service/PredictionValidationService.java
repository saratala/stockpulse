package com.stockpulse.agent.service;

import com.stockpulse.agent.model.*;
import com.stockpulse.agent.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PredictionValidationService {

    private final SignalPredictionRepository signalPredictionRepository;
    private final StockPriceRepository stockPriceRepository;
    private final PredictionValidationRepository validationRepository;
    private final PredictionAccuracyDailyRepository accuracyRepository;
    private final PaperTradeRepository paperTradeRepository;
    private final PaperTradingAccountRepository accountRepository;

    /**
     * Validate all expired predictions that haven't been validated yet.
     * Runs every 15 minutes to check for predictions whose timeframe has elapsed.
     */
    @Scheduled(fixedRate = 900000) // 15 minutes
    @Transactional
    public void validateExpiredPredictions() {
        log.info("Starting prediction validation job...");

        LocalDateTime now = LocalDateTime.now();
        int validated1h = 0, validated1d = 0, validated1w = 0;

        // Get predictions from the last week that need validation
        LocalDateTime oneWeekAgo = now.minusDays(7);
        List<SignalPrediction> recentPredictions = signalPredictionRepository
                .findByTimestampBetweenOrderByTimestampDesc(oneWeekAgo, now);

        for (SignalPrediction prediction : recentPredictions) {
            try {
                // Validate 1-hour predictions (if more than 1 hour old)
                if (prediction.getPredictedPrice1h() != null &&
                    prediction.getTimestamp().plusHours(1).isBefore(now)) {
                    if (validatePrediction(prediction, "1h", prediction.getPredictedPrice1h())) {
                        validated1h++;
                    }
                }

                // Validate 1-day predictions (if more than 1 day old)
                if (prediction.getPredictedPrice1d() != null &&
                    prediction.getTimestamp().plusDays(1).isBefore(now)) {
                    if (validatePrediction(prediction, "1d", prediction.getPredictedPrice1d())) {
                        validated1d++;
                    }
                }

                // Validate 1-week predictions (if more than 1 week old)
                if (prediction.getPredictedPrice1w() != null &&
                    prediction.getTimestamp().plusWeeks(1).isBefore(now)) {
                    if (validatePrediction(prediction, "1w", prediction.getPredictedPrice1w())) {
                        validated1w++;
                    }
                }
            } catch (Exception e) {
                log.error("Error validating prediction for {}: {}", prediction.getTicker(), e.getMessage());
            }
        }

        log.info("Prediction validation complete. Validated: 1h={}, 1d={}, 1w={}",
                validated1h, validated1d, validated1w);

        // Update daily accuracy metrics
        if (validated1h + validated1d + validated1w > 0) {
            updateDailyAccuracyMetrics();
        }
    }

    /**
     * Validate a single prediction against actual price data.
     */
    private boolean validatePrediction(SignalPrediction prediction, String timeframe, BigDecimal predictedPriceBD) {
        // Calculate target timestamp based on timeframe
        LocalDateTime targetTime = switch (timeframe) {
            case "1h" -> prediction.getTimestamp().plusHours(1);
            case "1d" -> prediction.getTimestamp().plusDays(1);
            case "1w" -> prediction.getTimestamp().plusWeeks(1);
            default -> throw new IllegalArgumentException("Invalid timeframe: " + timeframe);
        };

        // Check if already validated
        boolean alreadyValidated = validationRepository.findByTickerAndTimeframeOrderByValidationTimestampDesc(
                prediction.getTicker(), timeframe)
                .stream()
                .anyMatch(v -> v.getPredictionTimestamp().equals(prediction.getTimestamp()));

        if (alreadyValidated) {
            return false;
        }

        // Get actual price at target time
        Optional<StockPrice> actualPriceOpt = stockPriceRepository
                .findClosestPriceToTimestamp(prediction.getTicker(), targetTime);

        if (actualPriceOpt.isEmpty()) {
            log.debug("No price data available for {} at {}", prediction.getTicker(), targetTime);
            return false;
        }

        StockPrice actualPriceData = actualPriceOpt.get();
        double actualPrice = actualPriceData.getClose().doubleValue();
        double predictionPrice = prediction.getCurrentPrice().doubleValue();
        double predictedPrice = predictedPriceBD.doubleValue();
        double confidence = prediction.getConfidence().doubleValue();

        // Calculate metrics
        double predictedChangePercent = ((predictedPrice - predictionPrice) / predictionPrice) * 100;
        double actualChangePercent = ((actualPrice - predictionPrice) / predictionPrice) * 100;
        double priceError = Math.abs(predictedPrice - actualPrice);
        double priceErrorPercent = (priceError / actualPrice) * 100;

        // Determine if direction was correct
        boolean directionCorrect = isDirectionCorrect(prediction.getSignalType(), actualChangePercent);

        // Create validation record
        PredictionValidation validation = PredictionValidation.builder()
                .ticker(prediction.getTicker())
                .predictionTimestamp(prediction.getTimestamp())
                .validationTimestamp(LocalDateTime.now())
                .timeframe(timeframe)
                .signalType(prediction.getSignalType().name())
                .predictedPrice(predictedPrice)
                .predictionPrice(predictionPrice)
                .confidence(confidence)
                .actualPrice(actualPrice)
                .predictedChangePercent(predictedChangePercent)
                .actualChangePercent(actualChangePercent)
                .priceError(priceError)
                .priceErrorPercent(priceErrorPercent)
                .directionCorrect(directionCorrect)
                .within5Percent(priceErrorPercent <= 5)
                .within10Percent(priceErrorPercent <= 10)
                .build();

        validationRepository.save(validation);

        log.debug("Validated {}/{}: predicted={}, actual={}, error={}%, direction={}",
                prediction.getTicker(), timeframe, predictedPrice, actualPrice,
                String.format("%.2f", priceErrorPercent), directionCorrect ? "CORRECT" : "WRONG");

        return true;
    }

    /**
     * Determine if the predicted direction matches the actual movement.
     */
    private boolean isDirectionCorrect(SignalPrediction.SignalType signalType, double actualChangePercent) {
        return switch (signalType) {
            case BULLISH -> actualChangePercent > 0;
            case BEARISH -> actualChangePercent < 0;
            case NEUTRAL -> Math.abs(actualChangePercent) < 1; // Within 1% is considered correct for neutral
        };
    }

    /**
     * Update daily accuracy metrics based on recent validations.
     */
    @Scheduled(cron = "0 0 1 * * *") // Run at 1 AM daily
    @Transactional
    public void updateDailyAccuracyMetrics() {
        log.info("Updating daily accuracy metrics...");

        LocalDate yesterday = LocalDate.now().minusDays(1);
        LocalDateTime startOfYesterday = yesterday.atStartOfDay();
        LocalDateTime endOfYesterday = yesterday.plusDays(1).atStartOfDay();

        for (String timeframe : Arrays.asList("1h", "1d", "1w")) {
            // Get validations from yesterday for this timeframe
            List<PredictionValidation> validations = validationRepository
                    .findByTimeframeOrderByValidationTimestampDesc(timeframe)
                    .stream()
                    .filter(v -> v.getValidationTimestamp().isAfter(startOfYesterday) &&
                                v.getValidationTimestamp().isBefore(endOfYesterday))
                    .collect(Collectors.toList());

            if (validations.isEmpty()) {
                continue;
            }

            // Calculate overall metrics
            updateAccuracyForTickerAndTimeframe(yesterday, null, timeframe, validations);

            // Calculate per-ticker metrics
            Map<String, List<PredictionValidation>> byTicker = validations.stream()
                    .collect(Collectors.groupingBy(PredictionValidation::getTicker));

            for (Map.Entry<String, List<PredictionValidation>> entry : byTicker.entrySet()) {
                updateAccuracyForTickerAndTimeframe(yesterday, entry.getKey(), timeframe, entry.getValue());
            }
        }

        log.info("Daily accuracy metrics updated");
    }

    private void updateAccuracyForTickerAndTimeframe(LocalDate date, String ticker, String timeframe,
                                                      List<PredictionValidation> validations) {
        if (validations.isEmpty()) return;

        int total = validations.size();
        int correctDirection = (int) validations.stream().filter(PredictionValidation::getDirectionCorrect).count();
        int within5 = (int) validations.stream().filter(PredictionValidation::getWithin5Percent).count();
        int within10 = (int) validations.stream().filter(PredictionValidation::getWithin10Percent).count();

        double avgError = validations.stream()
                .mapToDouble(PredictionValidation::getPriceErrorPercent)
                .average().orElse(0);
        double maxError = validations.stream()
                .mapToDouble(PredictionValidation::getPriceErrorPercent)
                .max().orElse(0);
        double minError = validations.stream()
                .mapToDouble(PredictionValidation::getPriceErrorPercent)
                .min().orElse(0);
        double avgConfidence = validations.stream()
                .mapToDouble(PredictionValidation::getConfidence)
                .average().orElse(0);

        int bullishCount = (int) validations.stream()
                .filter(v -> "BULLISH".equals(v.getSignalType())).count();
        int bullishCorrect = (int) validations.stream()
                .filter(v -> "BULLISH".equals(v.getSignalType()) && v.getDirectionCorrect()).count();
        int bearishCount = (int) validations.stream()
                .filter(v -> "BEARISH".equals(v.getSignalType())).count();
        int bearishCorrect = (int) validations.stream()
                .filter(v -> "BEARISH".equals(v.getSignalType()) && v.getDirectionCorrect()).count();

        // Calculate confidence calibration (how well confidence predicts accuracy)
        Double confidenceCalibration = calculateConfidenceCalibration(validations);

        PredictionAccuracyDaily accuracy = accuracyRepository
                .findByDateAndTickerAndTimeframe(date, ticker, timeframe)
                .orElse(PredictionAccuracyDaily.builder()
                        .date(date)
                        .ticker(ticker)
                        .timeframe(timeframe)
                        .build());

        accuracy.setTotalPredictions(total);
        accuracy.setCorrectDirection(correctDirection);
        accuracy.setWithin5Percent(within5);
        accuracy.setWithin10Percent(within10);
        accuracy.setDirectionAccuracy((double) correctDirection / total * 100);
        accuracy.setAccuracy5Percent((double) within5 / total * 100);
        accuracy.setAccuracy10Percent((double) within10 / total * 100);
        accuracy.setAvgErrorPercent(avgError);
        accuracy.setMaxErrorPercent(maxError);
        accuracy.setMinErrorPercent(minError);
        accuracy.setAvgConfidence(avgConfidence);
        accuracy.setConfidenceCalibration(confidenceCalibration);
        accuracy.setBullishCount(bullishCount);
        accuracy.setBullishCorrect(bullishCorrect);
        accuracy.setBearishCount(bearishCount);
        accuracy.setBearishCorrect(bearishCorrect);

        accuracyRepository.save(accuracy);
    }

    /**
     * Calculate confidence calibration - measures how well confidence scores predict actual accuracy.
     * A well-calibrated system should have predictions with 80% confidence actually being correct 80% of the time.
     */
    private Double calculateConfidenceCalibration(List<PredictionValidation> validations) {
        if (validations.size() < 10) return null; // Not enough data

        // Group by confidence buckets
        Map<Integer, List<PredictionValidation>> byConfidenceBucket = validations.stream()
                .collect(Collectors.groupingBy(v -> ((int) (v.getConfidence() / 20)) * 20));

        double totalError = 0;
        int bucketCount = 0;

        for (Map.Entry<Integer, List<PredictionValidation>> entry : byConfidenceBucket.entrySet()) {
            if (entry.getValue().size() < 3) continue; // Skip small buckets

            double expectedAccuracy = entry.getKey() + 10; // Midpoint of bucket
            double actualAccuracy = entry.getValue().stream()
                    .filter(PredictionValidation::getDirectionCorrect)
                    .count() * 100.0 / entry.getValue().size();

            totalError += Math.abs(expectedAccuracy - actualAccuracy);
            bucketCount++;
        }

        if (bucketCount == 0) return null;

        // Return inverse of error (higher is better, 100 = perfectly calibrated)
        return Math.max(0, 100 - (totalError / bucketCount));
    }

    /**
     * Get accuracy summary for display.
     */
    public Map<String, Object> getAccuracySummary(int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        Map<String, Object> summary = new HashMap<>();

        for (String timeframe : Arrays.asList("1h", "1d", "1w")) {
            Map<String, Object> tfSummary = new HashMap<>();

            Long total = validationRepository.countByTimeframeSince(timeframe, since);
            Long correct = validationRepository.countCorrectDirectionByTimeframeSince(timeframe, since);
            Double avgError = validationRepository.avgErrorPercentByTimeframeSince(timeframe, since);

            tfSummary.put("totalPredictions", total);
            tfSummary.put("correctDirection", correct);
            tfSummary.put("directionAccuracy", total > 0 ? (correct * 100.0 / total) : 0);
            tfSummary.put("avgErrorPercent", avgError != null ? avgError : 0);

            summary.put(timeframe, tfSummary);
        }

        // Add confidence calibration data
        List<Object[]> calibrationData = validationRepository.getConfidenceCalibrationData(since);
        List<Map<String, Object>> calibration = new ArrayList<>();
        for (Object[] row : calibrationData) {
            Map<String, Object> bucket = new HashMap<>();
            bucket.put("bucket", row[0]);
            bucket.put("total", row[1]);
            bucket.put("correct", row[2]);
            bucket.put("accuracy", ((Long) row[1]) > 0 ?
                    ((Long) row[2]) * 100.0 / ((Long) row[1]) : 0);
            calibration.add(bucket);
        }
        summary.put("confidenceCalibration", calibration);

        // Add signal type performance
        List<Object[]> signalPerformance = validationRepository.getSignalTypePerformance(since);
        Map<String, Object> signalStats = new HashMap<>();
        for (Object[] row : signalPerformance) {
            String signalType = (String) row[0];
            Long signalTotal = (Long) row[1];
            Long signalCorrect = (Long) row[2];
            signalStats.put(signalType, Map.of(
                    "total", signalTotal,
                    "correct", signalCorrect,
                    "accuracy", signalTotal > 0 ? (signalCorrect * 100.0 / signalTotal) : 0
            ));
        }
        summary.put("signalTypePerformance", signalStats);

        return summary;
    }

    /**
     * Get accuracy for a specific ticker.
     */
    public Map<String, Object> getTickerAccuracy(String ticker, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        Map<String, Object> summary = new HashMap<>();

        Long total = validationRepository.countForTickerSince(ticker, since);
        Long correct = validationRepository.countCorrectDirectionForTickerSince(ticker, since);

        summary.put("ticker", ticker);
        summary.put("totalPredictions", total);
        summary.put("correctDirection", correct);
        summary.put("directionAccuracy", total > 0 ? (correct * 100.0 / total) : 0);

        // Get recent validations
        List<PredictionValidation> recentValidations = validationRepository
                .findRecentValidationsForTicker(ticker, since);
        summary.put("recentValidations", recentValidations.stream().limit(20).collect(Collectors.toList()));

        return summary;
    }

    /**
     * Get top and worst performing tickers.
     */
    public Map<String, Object> getTickerRankings(String timeframe, int days) {
        LocalDate since = LocalDate.now().minusDays(days);
        Map<String, Object> rankings = new HashMap<>();

        List<Object[]> topTickers = accuracyRepository.getTopPerformingTickers(timeframe, since);
        List<Map<String, Object>> top = new ArrayList<>();
        for (Object[] row : topTickers.stream().limit(10).collect(Collectors.toList())) {
            top.add(Map.of("ticker", row[0], "accuracy", row[1]));
        }
        rankings.put("topPerformers", top);

        List<Object[]> worstTickers = accuracyRepository.getWorstPerformingTickers(timeframe, since);
        List<Map<String, Object>> worst = new ArrayList<>();
        for (Object[] row : worstTickers.stream().limit(10).collect(Collectors.toList())) {
            worst.add(Map.of("ticker", row[0], "accuracy", row[1]));
        }
        rankings.put("worstPerformers", worst);

        return rankings;
    }

    /**
     * Manually trigger validation for testing.
     */
    public Map<String, Object> runValidationNow() {
        validateExpiredPredictions();
        return getAccuracySummary(7);
    }

    /**
     * Get paper trading account summary.
     */
    public Map<String, Object> getPaperTradingStats() {
        Map<String, Object> stats = new HashMap<>();

        PaperTradingAccount account = accountRepository.findByAccountName("default")
                .orElse(PaperTradingAccount.builder().build());

        stats.put("initialBalance", account.getInitialBalance());
        stats.put("currentBalance", account.getCurrentBalance());
        stats.put("totalReturn", account.getTotalReturn());
        stats.put("totalTrades", account.getTotalTrades());
        stats.put("winningTrades", account.getWinningTrades());
        stats.put("losingTrades", account.getLosingTrades());
        stats.put("winRate", account.getWinRate());
        stats.put("totalRealizedPnl", account.getTotalRealizedPnl());
        stats.put("maxDrawdown", account.getMaxDrawdown());
        stats.put("maxDrawdownPercent", account.getMaxDrawdownPercent());

        // Get open trades
        List<PaperTrade> openTrades = paperTradeRepository.findOpenTrades();
        stats.put("openTrades", openTrades.size());
        stats.put("openTradesList", openTrades);

        return stats;
    }

    /**
     * Get recent validations for display.
     */
    public List<PredictionValidation> getRecentValidations(int limit) {
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        return validationRepository.findRecentValidations(since)
                .stream()
                .limit(limit)
                .collect(Collectors.toList());
    }
}
