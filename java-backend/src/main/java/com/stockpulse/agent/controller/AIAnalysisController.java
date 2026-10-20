package com.stockpulse.agent.controller;

import com.stockpulse.agent.service.AIStockAnalysisService;
import com.stockpulse.agent.service.MLPredictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Controller for AI-powered stock analysis endpoints
 */
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Slf4j
public class AIAnalysisController {

    private final AIStockAnalysisService aiAnalysisService;
    private final MLPredictionService mlPredictionService;

    /**
     * Run comprehensive AI analysis on a single stock
     * Combines technical indicators, ML predictions, and backtesting
     */
    @GetMapping("/analyze/{ticker}")
    public ResponseEntity<AIStockAnalysisService.ComprehensiveAnalysis> analyzeStock(
            @PathVariable String ticker) {

        log.info("AI analysis requested for ticker: {}", ticker);

        Optional<AIStockAnalysisService.ComprehensiveAnalysis> analysisOpt =
            aiAnalysisService.analyzeStock(ticker.toUpperCase());

        if (analysisOpt.isPresent()) {
            return ResponseEntity.ok(analysisOpt.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get ML price prediction only (lighter weight)
     */
    @GetMapping("/predict/{ticker}")
    public ResponseEntity<MLPredictionService.PricePrediction> getPrediction(
            @PathVariable String ticker) {

        log.info("ML prediction requested for ticker: {}", ticker);

        Optional<MLPredictionService.PricePrediction> predictionOpt =
            mlPredictionService.predictPrice(ticker.toUpperCase());

        if (predictionOpt.isPresent()) {
            return ResponseEntity.ok(predictionOpt.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Run ML backtest for a ticker
     */
    @GetMapping("/backtest/{ticker}")
    public ResponseEntity<MLPredictionService.BacktestResult> runBacktest(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "120") int daysBack) {

        log.info("ML backtest requested for ticker: {} over {} days", ticker, daysBack);

        Optional<MLPredictionService.BacktestResult> backtestOpt =
            mlPredictionService.runBacktest(ticker.toUpperCase(), daysBack);

        if (backtestOpt.isPresent()) {
            return ResponseEntity.ok(backtestOpt.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Analyze multiple stocks at once
     */
    @PostMapping("/analyze-batch")
    public ResponseEntity<Map<String, AIStockAnalysisService.ComprehensiveAnalysis>> analyzeBatch(
            @RequestBody List<String> tickers) {

        log.info("Batch AI analysis requested for {} tickers", tickers.size());

        Map<String, AIStockAnalysisService.ComprehensiveAnalysis> results = new LinkedHashMap<>();

        for (String ticker : tickers) {
            try {
                Optional<AIStockAnalysisService.ComprehensiveAnalysis> analysisOpt =
                    aiAnalysisService.analyzeStock(ticker.toUpperCase());

                if (analysisOpt.isPresent()) {
                    results.put(ticker.toUpperCase(), analysisOpt.get());
                }
            } catch (Exception e) {
                log.error("Error analyzing ticker {}: {}", ticker, e.getMessage());
            }
        }

        return ResponseEntity.ok(results);
    }

    /**
     * Get quick recommendation for a stock
     */
    @GetMapping("/recommendation/{ticker}")
    public ResponseEntity<Map<String, Object>> getRecommendation(@PathVariable String ticker) {

        log.info("Quick recommendation requested for ticker: {}", ticker);

        Optional<AIStockAnalysisService.ComprehensiveAnalysis> analysisOpt =
            aiAnalysisService.analyzeStock(ticker.toUpperCase());

        if (!analysisOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        AIStockAnalysisService.ComprehensiveAnalysis analysis = analysisOpt.get();
        AIStockAnalysisService.AIRecommendation rec = analysis.getRecommendation();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ticker", ticker.toUpperCase());
        response.put("currentPrice", analysis.getCurrentPrice());
        response.put("action", rec.getAction());
        response.put("confidence", rec.getConfidence());
        response.put("timeHorizon", rec.getTimeHorizon());
        response.put("reasoning", rec.getReasoning());
        response.put("stopLoss", rec.getStopLossPrice());
        response.put("target1", rec.getTargetPrice1());
        response.put("target2", rec.getTargetPrice2());
        response.put("riskRewardRatio", rec.getRiskRewardRatio());
        response.put("bullishFactors", rec.getBullishFactors());
        response.put("bearishFactors", rec.getBearishFactors());
        response.put("keyRisks", rec.getKeyRisks());

        return ResponseEntity.ok(response);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("service", "AI Analysis Service");
        response.put("version", "v2.0-comprehensive");
        response.put("timestamp", java.time.LocalDateTime.now());
        return ResponseEntity.ok(response);
    }
}
