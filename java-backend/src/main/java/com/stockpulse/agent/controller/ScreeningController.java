package com.stockpulse.agent.controller;

import com.stockpulse.agent.dto.ScreeningResponse;
import com.stockpulse.agent.dto.SignalResponse;
import com.stockpulse.agent.service.ScreeningService;
import com.stockpulse.agent.service.SignalService;
import com.stockpulse.agent.service.ImprovedScoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/screener")
@RequiredArgsConstructor
@Slf4j
public class ScreeningController {

    private final ScreeningService screeningService;
    private final SignalService signalService;
    private final ImprovedScoringService improvedScoringService;
    private final com.stockpulse.agent.service.RealTimeDataService realTimeDataService;
    
    @GetMapping("/run")
    public ResponseEntity<ScreeningResponse> runStockScreener(
            @RequestParam(defaultValue = "0") int minScore,
            @RequestParam(defaultValue = "100") int maxResults,
            @RequestParam(defaultValue = "true") boolean includeSignals,
            @RequestParam(defaultValue = "ALL") String signalFilter) {

        log.info("Running stock screener with minScore={}, maxResults={}, includeSignals={}, signalFilter={}",
                minScore, maxResults, includeSignals, signalFilter);

        try {
            ScreeningResponse response = screeningService.runStockScreener(minScore, maxResults, includeSignals, signalFilter);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error running stock screener", e);
            return ResponseEntity.ok(ScreeningResponse.builder()
                .screeningDate(java.time.LocalDateTime.now().toString())
                .totalAnalyzed(0)
                .candidatesFound(0)
                .candidates(java.util.Collections.emptyList())
                .build());
        }
    }
    
    @GetMapping("/signals")
    public ResponseEntity<SignalResponse> getHeikinAshiSignals(
            @RequestParam(required = false) String tickers,
            @RequestParam(defaultValue = "3mo") String period,
            @RequestParam(defaultValue = "40") int minConfidence) {
        
        log.info("Getting Heikin Ashi signals for tickers={}, period={}, minConfidence={}", 
                tickers, period, minConfidence);
        
        try {
            SignalResponse response = signalService.getHeikinAshiSignals(tickers, period, minConfidence);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting Heikin Ashi signals", e);
            return ResponseEntity.ok(SignalResponse.builder()
                .analysisDate(java.time.LocalDateTime.now().toString())
                .totalAnalyzed(0)
                .signalsFound(0)
                .signals(java.util.Collections.emptyList())
                .build());
        }
    }
    
    @PostMapping("/update")
    public ResponseEntity<String> triggerScreeningUpdate() {
        log.info("Manually triggering screening data update...");
        try {
            realTimeDataService.updateScreeningResults();
            return ResponseEntity.ok("Screening update triggered successfully");
        } catch (Exception e) {
            log.error("Error triggering screening update", e);
            return ResponseEntity.status(500).body("Failed to trigger screening update: " + e.getMessage());
        }
    }
    
    @PostMapping("/fetch-historical")
    public ResponseEntity<String> fetchHistoricalData(
            @RequestParam(defaultValue = "1y") String period,
            @RequestParam(defaultValue = "1d") String interval) {
        log.info("Fetching historical data for period: {}, interval: {}", period, interval);
        try {
            // This will fetch historical data for all tickers
            int recordsAdded = realTimeDataService.fetchHistoricalData(period, interval);
            return ResponseEntity.ok("Successfully fetched historical data. Records added: " + recordsAdded);
        } catch (Exception e) {
            log.error("Error fetching historical data", e);
            return ResponseEntity.status(500).body("Failed to fetch historical data: " + e.getMessage());
        }
    }
    
    @PostMapping("/load-yearly")
    public ResponseEntity<java.util.Map<String, Object>> loadYearlyData(
            @RequestParam(defaultValue = "2024") int year) {
        
        log.info("Loading yearly historical data for year: {}", year);
        
        try {
            int recordsLoaded = realTimeDataService.loadYearlyData(year);
            
            java.util.Map<String, Object> response = new java.util.HashMap<>();
            response.put("status", "success");
            response.put("message", "Successfully loaded " + recordsLoaded + " records for year " + year);
            response.put("year", year);
            response.put("recordsLoaded", recordsLoaded);
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error loading yearly data for year: {}", year, e);
            
            java.util.Map<String, Object> response = new java.util.HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to load yearly data: " + e.getMessage());
            response.put("year", year);
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Get detailed technical analysis for a specific ticker
     * Uses the improved scoring algorithm with actual indicator calculations
     */
    @GetMapping("/technical-analysis/{ticker}")
    public ResponseEntity<java.util.Map<String, Object>> getTechnicalAnalysis(
            @PathVariable String ticker) {

        log.info("Getting technical analysis for ticker: {}", ticker);

        try {
            Optional<ImprovedScoringService.TechnicalScore> scoreOpt =
                improvedScoringService.calculateTechnicalScore(ticker.toUpperCase());

            if (scoreOpt.isPresent()) {
                ImprovedScoringService.TechnicalScore score = scoreOpt.get();

                java.util.Map<String, Object> response = new java.util.LinkedHashMap<>();
                response.put("ticker", score.getTicker());
                response.put("timestamp", java.time.LocalDateTime.now());

                // Scores
                java.util.Map<String, Object> scores = new java.util.LinkedHashMap<>();
                scores.put("composite", score.getCompositeScore());
                scores.put("trend", score.getTrendScore());
                scores.put("momentum", score.getMomentumScore());
                scores.put("volatility", score.getVolatilityScore());
                scores.put("volume", score.getVolumeScore());
                response.put("scores", scores);

                // Signal
                java.util.Map<String, Object> signal = new java.util.LinkedHashMap<>();
                signal.put("primary", score.getPrimarySignal());
                signal.put("confidence", score.getSignalConfidence());
                signal.put("trendDirection", score.getTrendDirection());
                signal.put("riskLevel", score.getRiskLevel());
                signal.put("detectedSignals", score.getSignals());
                response.put("signal", signal);

                // Technical Indicators
                java.util.Map<String, Object> indicators = new java.util.LinkedHashMap<>();
                indicators.put("rsi", score.getRsi());
                indicators.put("macd", score.getMacd());
                indicators.put("macdSignal", score.getMacdSignal());
                indicators.put("macdHistogram", score.getMacdHistogram());
                indicators.put("stochK", score.getStochK());
                indicators.put("stochD", score.getStochD());
                indicators.put("adx", score.getAdx());
                indicators.put("atr", score.getAtr());
                response.put("indicators", indicators);

                // Moving Averages
                java.util.Map<String, Object> movingAverages = new java.util.LinkedHashMap<>();
                movingAverages.put("sma20", score.getSma20());
                movingAverages.put("sma50", score.getSma50());
                movingAverages.put("sma200", score.getSma200());
                movingAverages.put("emaStackAligned", score.isEmaStackAligned());
                response.put("movingAverages", movingAverages);

                // Bollinger Bands
                java.util.Map<String, Object> bollinger = new java.util.LinkedHashMap<>();
                bollinger.put("upper", score.getBollingerUpper());
                bollinger.put("lower", score.getBollingerLower());
                bollinger.put("position", score.getBollingerPosition());
                response.put("bollingerBands", bollinger);

                // Key Levels
                java.util.Map<String, Object> levels = new java.util.LinkedHashMap<>();
                levels.put("support", score.getSupportLevel());
                levels.put("resistance", score.getResistanceLevel());
                levels.put("pivot", score.getPivotPoint());
                response.put("keyLevels", levels);

                return ResponseEntity.ok(response);

            } else {
                java.util.Map<String, Object> response = new java.util.HashMap<>();
                response.put("error", "Insufficient data for technical analysis");
                response.put("ticker", ticker);
                response.put("message", "Need at least 20 days of price data");
                return ResponseEntity.status(404).body(response);
            }

        } catch (Exception e) {
            log.error("Error getting technical analysis for {}: {}", ticker, e.getMessage());
            java.util.Map<String, Object> response = new java.util.HashMap<>();
            response.put("error", "Failed to calculate technical analysis");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}