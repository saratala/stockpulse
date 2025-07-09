package com.stockpulse.agent.controller;

import com.stockpulse.agent.service.SentimentAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/sentiment-analysis")
@RequiredArgsConstructor
@Slf4j
public class SentimentAnalysisController {
    
    private final SentimentAnalysisService sentimentAnalysisService;
    
    @PostMapping("/analyze/{ticker}")
    public ResponseEntity<Map<String, Object>> analyzeTicker(@PathVariable String ticker) {
        log.info("Analyzing sentiment for ticker: {}", ticker);
        
        try {
            Map<String, Object> result = sentimentAnalysisService.analyzeSentimentForTicker(ticker);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error analyzing sentiment for ticker: {}", ticker, e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to analyze sentiment for ticker: " + ticker)
            );
        }
    }
    
    @PostMapping("/analyze/all")
    public ResponseEntity<Map<String, Map<String, Object>>> analyzeAllTickers() {
        log.info("Analyzing sentiment for all tickers");
        
        try {
            Map<String, Map<String, Object>> result = sentimentAnalysisService.analyzeAllTickers();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error analyzing sentiment for all tickers", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", Map.of("message", "Failed to analyze sentiment for all tickers"))
            );
        }
    }
    
    @PostMapping("/analyze/active")
    public ResponseEntity<Map<String, Map<String, Object>>> analyzeActiveTickers() {
        log.info("Analyzing sentiment for active tickers");
        
        try {
            Map<String, Map<String, Object>> result = sentimentAnalysisService.analyzeActiveTickers();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error analyzing sentiment for active tickers", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", Map.of("message", "Failed to analyze sentiment for active tickers"))
            );
        }
    }
    
    @PostMapping("/refresh/{ticker}")
    public ResponseEntity<Map<String, Object>> refreshTicker(@PathVariable String ticker) {
        log.info("Refreshing sentiment for ticker: {}", ticker);
        
        try {
            Map<String, Object> result = sentimentAnalysisService.refreshTickerSentiment(ticker);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error refreshing sentiment for ticker: {}", ticker, e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to refresh sentiment for ticker: " + ticker)
            );
        }
    }
    
    @PostMapping("/update-recent")
    public ResponseEntity<Map<String, Object>> updateRecentSentiment() {
        log.info("Updating recent sentiment analysis");
        
        try {
            sentimentAnalysisService.updateRecentSentimentAnalysis();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Recent sentiment analysis updated successfully"
            ));
        } catch (Exception e) {
            log.error("Error updating recent sentiment analysis", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to update recent sentiment analysis")
            );
        }
    }
    
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSentimentSummary(
            @RequestParam(defaultValue = "24") int hours) {
        
        log.info("Getting sentiment summary for {} hours", hours);
        
        try {
            Map<String, Object> summary = sentimentAnalysisService.getSentimentSummary(hours);
            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            log.error("Error getting sentiment summary", e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to get sentiment summary")
            );
        }
    }
    
    @GetMapping("/trend/{ticker}")
    public ResponseEntity<Map<String, Object>> getSentimentTrend(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "7") int days) {
        
        log.info("Getting sentiment trend for ticker: {} over {} days", ticker, days);
        
        try {
            Map<String, Object> trend = sentimentAnalysisService.getSentimentTrend(ticker, days);
            return ResponseEntity.ok(trend);
        } catch (Exception e) {
            log.error("Error getting sentiment trend for ticker: {}", ticker, e);
            return ResponseEntity.internalServerError().body(
                Map.of("error", "Failed to get sentiment trend for ticker: " + ticker)
            );
        }
    }
}