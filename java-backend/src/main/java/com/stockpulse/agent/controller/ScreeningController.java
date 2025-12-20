package com.stockpulse.agent.controller;

import com.stockpulse.agent.dto.ScreeningResponse;
import com.stockpulse.agent.dto.SignalResponse;
import com.stockpulse.agent.service.ScreeningService;
import com.stockpulse.agent.service.SignalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/screener")
@RequiredArgsConstructor
@Slf4j
public class ScreeningController {
    
    private final ScreeningService screeningService;
    private final SignalService signalService;
    private final com.stockpulse.agent.service.RealTimeDataService realTimeDataService;
    
    @GetMapping("/run")
    public ResponseEntity<ScreeningResponse> runStockScreener(
            @RequestParam(defaultValue = "70") int minScore,
            @RequestParam(defaultValue = "50") int maxResults,
            @RequestParam(defaultValue = "true") boolean includeSignals) {
        
        log.info("Running stock screener with minScore={}, maxResults={}, includeSignals={}", 
                minScore, maxResults, includeSignals);
        
        try {
            ScreeningResponse response = screeningService.runStockScreener(minScore, maxResults, includeSignals);
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
}