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
}