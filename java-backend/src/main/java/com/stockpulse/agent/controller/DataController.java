package com.stockpulse.agent.controller;

import com.stockpulse.agent.dto.StrategyComparison;
import com.stockpulse.agent.service.ComprehensiveBacktestService;
import com.stockpulse.agent.service.RealTimeDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/data")
@RequiredArgsConstructor
@Slf4j
public class DataController {
    
    private final RealTimeDataService realTimeDataService;
    private final ComprehensiveBacktestService comprehensiveBacktestService;
    
    @PostMapping("/update/realtime")
    public ResponseEntity<Map<String, Object>> updateRealTimeData() {
        log.info("Manual real-time data update requested");
        
        try {
            realTimeDataService.updateRealTimeData();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Real-time data update completed successfully");
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error during manual real-time data update", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Real-time data update failed: " + e.getMessage());
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PostMapping("/update/screening")
    public ResponseEntity<Map<String, Object>> updateScreeningData() {
        log.info("Manual screening data update requested");
        
        try {
            realTimeDataService.updateScreeningResults();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Screening data update completed successfully");
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error during manual screening data update", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Screening data update failed: " + e.getMessage());
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PostMapping("/update/predictions")
    public ResponseEntity<Map<String, Object>> generatePredictions() {
        log.info("Manual predictions generation requested");
        
        try {
            realTimeDataService.generateSignalPredictions();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Predictions generation completed successfully");
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error during manual predictions generation", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Predictions generation failed: " + e.getMessage());
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PostMapping("/update/all")
    public ResponseEntity<Map<String, Object>> updateAllData() {
        log.info("Manual full data update requested");
        
        try {
            // Update in sequence
            realTimeDataService.updateRealTimeData();
            realTimeDataService.updateScreeningResults();
            realTimeDataService.generateSignalPredictions();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Full data update completed successfully");
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error during manual full data update", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Full data update failed: " + e.getMessage());
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    @PostMapping("/backtest/comprehensive")
    public ResponseEntity<StrategyComparison> runComprehensiveBacktest(
            @RequestParam String ticker,
            @RequestParam(defaultValue = "10000") BigDecimal initialCapital) {
        
        log.info("Running comprehensive strategy backtest for ticker: {} with capital: {}", ticker, initialCapital);
        
        try {
            StrategyComparison comparison = comprehensiveBacktestService.runComprehensiveBacktest(ticker, initialCapital);
            return ResponseEntity.ok(comparison);
        } catch (Exception e) {
            log.error("Error running comprehensive backtest", e);
            return ResponseEntity.status(500).body(StrategyComparison.builder()
                .ticker(ticker)
                .strategies(new java.util.ArrayList<>())
                .build());
        }
    }
    
    @PostMapping("/load/yearly")
    public ResponseEntity<Map<String, Object>> loadYearlyData(
            @RequestParam(defaultValue = "2024") int year) {
        
        log.info("Loading yearly historical data for year: {}", year);
        
        try {
            int recordsLoaded = realTimeDataService.loadYearlyData(year);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Successfully loaded " + recordsLoaded + " records for year " + year);
            response.put("year", year);
            response.put("recordsLoaded", recordsLoaded);
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error loading yearly data for year: {}", year, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to load yearly data: " + e.getMessage());
            response.put("year", year);
            response.put("timestamp", java.time.LocalDateTime.now());
            
            return ResponseEntity.status(500).body(response);
        }
    }
}