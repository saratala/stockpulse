package com.stockpulse.agent.controller;

import com.stockpulse.agent.dto.PredictionResponse;
import com.stockpulse.agent.dto.PredictionSummaryResponse;
import com.stockpulse.agent.service.PredictionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/predictions")
@RequiredArgsConstructor
@Slf4j
public class PredictionController {
    
    private final PredictionService predictionService;
    
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
}