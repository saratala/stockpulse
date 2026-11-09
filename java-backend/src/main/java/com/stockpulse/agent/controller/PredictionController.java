package com.stockpulse.agent.controller;

import com.stockpulse.agent.dto.PredictionResponse;
import com.stockpulse.agent.dto.PredictionSummaryResponse;
import com.stockpulse.agent.service.PredictionService;
import com.stockpulse.agent.service.MLPredictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/predictions")
@RequiredArgsConstructor
@Slf4j
public class PredictionController {

    private final PredictionService predictionService;
    private final MLPredictionService mlPredictionService;
    
    @GetMapping("/history")
    public ResponseEntity<PredictionResponse> getPredictionHistory(
            @RequestParam(required = false) String ticker,
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "100") int limit) {
        
        log.info("Getting prediction history for ticker={}, hours={}, limit={}", ticker, hours, limit);
        
        try {
            PredictionResponse response = predictionService.getPredictionHistory(ticker, hours, limit);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting prediction history", e);
            return ResponseEntity.ok(PredictionResponse.builder()
                .totalPredictions(0)
                .predictions(java.util.Collections.emptyList())
                .build());
        }
    }
    
    @GetMapping("/summary")
    public ResponseEntity<PredictionSummaryResponse> getPredictionSummary(
            @RequestParam(defaultValue = "24") int hours) {
        
        log.info("Getting prediction summary for hours={}", hours);
        
        try {
            PredictionSummaryResponse response = predictionService.getPredictionSummary(hours);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting prediction summary", e);
            return ResponseEntity.ok(PredictionSummaryResponse.builder()
                .timePeriodHours(hours)
                .summary(java.util.Collections.emptyList())
                .build());
        }
    }
    
    @GetMapping("/ticker/{ticker}")
    public ResponseEntity<PredictionResponse> getTickerPredictions(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "168") int hours) {

        log.info("Getting ticker predictions for ticker={}, hours={}", ticker, hours);

        try {
            PredictionResponse response = predictionService.getTickerPredictions(ticker, hours);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting ticker predictions", e);
            return ResponseEntity.ok(PredictionResponse.builder()
                .totalPredictions(0)
                .predictions(java.util.Collections.emptyList())
                .build());
        }
    }

    // ============ ML Prediction Endpoints ============

    /**
     * Fetch 2 years of historical daily data for all tickers from Yahoo Finance
     */
    @PostMapping("/ml/fetch-historical")
    public ResponseEntity<Map<String, Object>> fetchHistoricalData() {
        log.info("Fetching 2 years of historical data for all tickers...");

        try {
            Map<String, Integer> results = mlPredictionService.fetchHistoricalDataForAllTickers();

            int totalRecords = results.values().stream()
                .filter(v -> v > 0)
                .mapToInt(Integer::intValue)
                .sum();
            int successfulTickers = (int) results.values().stream()
                .filter(v -> v > 0)
                .count();

            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("status", "success");
            response.put("message", "Historical data fetch complete");
            response.put("totalRecords", totalRecords);
            response.put("successfulTickers", successfulTickers);
            response.put("totalTickers", results.size());
            response.put("details", results);
            response.put("timestamp", java.time.LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching historical data", e);
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to fetch historical data: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Get ML-based price prediction for a specific ticker
     */
    @GetMapping("/ml/predict/{ticker}")
    public ResponseEntity<Object> getMLPrediction(@PathVariable String ticker) {
        log.info("Getting ML prediction for ticker: {}", ticker);

        try {
            Optional<MLPredictionService.PricePrediction> predictionOpt =
                mlPredictionService.predictPrice(ticker.toUpperCase());

            if (predictionOpt.isPresent()) {
                return ResponseEntity.ok(predictionOpt.get());
            } else {
                Map<String, Object> response = new java.util.HashMap<>();
                response.put("error", "Insufficient data for ML prediction");
                response.put("ticker", ticker);
                response.put("message", "Need at least 50 days of price data. Try fetching historical data first.");
                return ResponseEntity.status(404).body(response);
            }

        } catch (Exception e) {
            log.error("Error getting ML prediction for {}", ticker, e);
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("error", "Failed to generate prediction");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Run ML backtest for a specific ticker
     */
    @GetMapping("/ml/backtest/{ticker}")
    public ResponseEntity<Object> runMLBacktest(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "90") int daysBack) {

        log.info("Running ML backtest for ticker: {}, daysBack: {}", ticker, daysBack);

        try {
            Optional<MLPredictionService.BacktestResult> resultOpt =
                mlPredictionService.runBacktest(ticker.toUpperCase(), daysBack);

            if (resultOpt.isPresent()) {
                return ResponseEntity.ok(resultOpt.get());
            } else {
                Map<String, Object> response = new java.util.HashMap<>();
                response.put("error", "Insufficient data for backtest");
                response.put("ticker", ticker);
                response.put("message", "Need at least 100 days of price data. Try fetching historical data first.");
                return ResponseEntity.status(404).body(response);
            }

        } catch (Exception e) {
            log.error("Error running ML backtest for {}", ticker, e);
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("error", "Failed to run backtest");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Get ML predictions for multiple tickers
     */
    @GetMapping("/ml/predict-batch")
    public ResponseEntity<Map<String, Object>> getBatchMLPredictions(
            @RequestParam(defaultValue = "AAPL,MSFT,GOOGL,AMZN,TSLA") String tickers) {

        log.info("Getting batch ML predictions for tickers: {}", tickers);

        try {
            String[] tickerArray = tickers.split(",");
            Map<String, Object> predictions = new java.util.LinkedHashMap<>();
            int successCount = 0;
            int failCount = 0;

            for (String ticker : tickerArray) {
                String cleanTicker = ticker.trim().toUpperCase();
                Optional<MLPredictionService.PricePrediction> predictionOpt =
                    mlPredictionService.predictPrice(cleanTicker);

                if (predictionOpt.isPresent()) {
                    predictions.put(cleanTicker, predictionOpt.get());
                    successCount++;
                } else {
                    predictions.put(cleanTicker, Map.of(
                        "error", "Insufficient data",
                        "message", "Need at least 50 days of price data"
                    ));
                    failCount++;
                }
            }

            Map<String, Object> response = new java.util.LinkedHashMap<>();
            response.put("totalRequested", tickerArray.length);
            response.put("successfulPredictions", successCount);
            response.put("failedPredictions", failCount);
            response.put("predictions", predictions);
            response.put("timestamp", java.time.LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting batch ML predictions", e);
            Map<String, Object> response = new java.util.HashMap<>();
            response.put("error", "Failed to generate batch predictions");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}