package com.stockpulse.agent.controller;

import com.stockpulse.agent.model.PredictionValidation;
import com.stockpulse.agent.service.PredictionValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for prediction validation and accuracy metrics.
 * Provides endpoints to view prediction accuracy, validation results, and paper trading stats.
 */
@RestController
@RequestMapping("/validation")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class PredictionValidationController {

    private final PredictionValidationService validationService;

    /**
     * Get overall accuracy summary for the specified number of days.
     * Returns accuracy metrics for 1h, 1d, and 1w predictions.
     */
    @GetMapping("/accuracy")
    public ResponseEntity<Map<String, Object>> getAccuracySummary(
            @RequestParam(defaultValue = "7") int days) {
        log.info("Getting accuracy summary for {} days", days);
        return ResponseEntity.ok(validationService.getAccuracySummary(days));
    }

    /**
     * Get accuracy for a specific ticker.
     */
    @GetMapping("/accuracy/{ticker}")
    public ResponseEntity<Map<String, Object>> getTickerAccuracy(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "30") int days) {
        log.info("Getting accuracy for ticker {} over {} days", ticker, days);
        return ResponseEntity.ok(validationService.getTickerAccuracy(ticker.toUpperCase(), days));
    }

    /**
     * Get top and worst performing tickers.
     */
    @GetMapping("/rankings")
    public ResponseEntity<Map<String, Object>> getTickerRankings(
            @RequestParam(defaultValue = "1d") String timeframe,
            @RequestParam(defaultValue = "30") int days) {
        log.info("Getting ticker rankings for {} timeframe over {} days", timeframe, days);
        return ResponseEntity.ok(validationService.getTickerRankings(timeframe, days));
    }

    /**
     * Get recent validations.
     */
    @GetMapping("/recent")
    public ResponseEntity<List<PredictionValidation>> getRecentValidations(
            @RequestParam(defaultValue = "50") int limit) {
        log.info("Getting {} recent validations", limit);
        return ResponseEntity.ok(validationService.getRecentValidations(limit));
    }

    /**
     * Manually trigger validation job (for testing).
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runValidationNow() {
        log.info("Manually triggering validation job");
        return ResponseEntity.ok(validationService.runValidationNow());
    }

    /**
     * Get paper trading account summary.
     */
    @GetMapping("/paper-trading")
    public ResponseEntity<Map<String, Object>> getPaperTradingStats() {
        log.info("Getting paper trading stats");
        return ResponseEntity.ok(validationService.getPaperTradingStats());
    }

    /**
     * Get comprehensive validation dashboard data.
     * Combines accuracy summary, rankings, and recent validations.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboardData(
            @RequestParam(defaultValue = "7") int days) {
        log.info("Getting validation dashboard data for {} days", days);

        Map<String, Object> dashboard = new java.util.HashMap<>();

        // Add accuracy summary
        dashboard.put("accuracy", validationService.getAccuracySummary(days));

        // Add rankings for each timeframe
        Map<String, Object> rankings = new java.util.HashMap<>();
        rankings.put("1h", validationService.getTickerRankings("1h", days));
        rankings.put("1d", validationService.getTickerRankings("1d", days));
        rankings.put("1w", validationService.getTickerRankings("1w", days));
        dashboard.put("rankings", rankings);

        // Add recent validations
        dashboard.put("recentValidations", validationService.getRecentValidations(20));

        // Add paper trading stats
        dashboard.put("paperTrading", validationService.getPaperTradingStats());

        return ResponseEntity.ok(dashboard);
    }
}
