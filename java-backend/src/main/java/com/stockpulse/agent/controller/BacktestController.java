package com.stockpulse.agent.controller;

import com.stockpulse.agent.dto.BacktestResult;
import com.stockpulse.agent.dto.StrategyComparison;
import com.stockpulse.agent.service.BacktestingService;
import com.stockpulse.agent.service.ComprehensiveBacktestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/backtest")
@RequiredArgsConstructor
@Slf4j
public class BacktestController {
    
    private final BacktestingService backtestingService;
    private final ComprehensiveBacktestService comprehensiveBacktestService;
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Backtest controller is working!");
    }
    
    @PostMapping("/sma")
    public ResponseEntity<BacktestResult> runSMABacktest(
            @RequestParam String ticker,
            @RequestParam(defaultValue = "5") int shortPeriod,
            @RequestParam(defaultValue = "20") int longPeriod,
            @RequestParam(defaultValue = "10000") BigDecimal initialCapital) {
        
        log.info("Running SMA backtest for ticker: {}, periods: {}/{}, capital: {}", 
                ticker, shortPeriod, longPeriod, initialCapital);
        
        try {
            BacktestResult result = backtestingService.runSMABacktest(
                ticker, shortPeriod, longPeriod, initialCapital);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error running SMA backtest", e);
            return ResponseEntity.ok(BacktestResult.builder()
                .ticker(ticker)
                .strategy("SMA Crossover")
                .error(e.getMessage())
                .build());
        }
    }
    
    @PostMapping("/rsi")
    public ResponseEntity<BacktestResult> runRSIBacktest(
            @RequestParam String ticker,
            @RequestParam(defaultValue = "14") int period,
            @RequestParam(defaultValue = "30") int oversoldLevel,
            @RequestParam(defaultValue = "70") int overboughtLevel,
            @RequestParam(defaultValue = "10000") BigDecimal initialCapital) {
        
        log.info("Running RSI backtest for ticker: {}, period: {}, levels: {}/{}, capital: {}", 
                ticker, period, oversoldLevel, overboughtLevel, initialCapital);
        
        try {
            BacktestResult result = backtestingService.runRSIBacktest(
                ticker, period, oversoldLevel, overboughtLevel, initialCapital);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error running RSI backtest", e);
            return ResponseEntity.ok(BacktestResult.builder()
                .ticker(ticker)
                .strategy("RSI Strategy")
                .error(e.getMessage())
                .build());
        }
    }
    
    @PostMapping("/multi")
    public ResponseEntity<BacktestResult[]> runMultiStrategyBacktest(
            @RequestParam String ticker,
            @RequestParam(defaultValue = "10000") BigDecimal initialCapital) {
        
        log.info("Running multi-strategy backtest for ticker: {}", ticker);
        
        try {
            BacktestResult[] results = new BacktestResult[4];
            
            // Test different SMA combinations
            results[0] = backtestingService.runSMABacktest(ticker, 5, 10, initialCapital);
            results[1] = backtestingService.runSMABacktest(ticker, 10, 20, initialCapital);
            
            // Test different RSI settings
            results[2] = backtestingService.runRSIBacktest(ticker, 14, 30, 70, initialCapital);
            results[3] = backtestingService.runRSIBacktest(ticker, 14, 20, 80, initialCapital);
            
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error running multi-strategy backtest", e);
            return ResponseEntity.status(500).body(new BacktestResult[0]);
        }
    }
    
    @PostMapping("/comprehensive")
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
    
    @PostMapping("/all-tickers")
    public ResponseEntity<java.util.Map<String, StrategyComparison>> runAllTickersBacktest(
            @RequestParam(defaultValue = "10000") BigDecimal initialCapital) {
        
        log.info("Running comprehensive backtest for all tickers with capital: {}", initialCapital);
        
        try {
            // Get all available tickers from database
            java.util.List<String> tickers = java.util.Arrays.asList(
                "AAPL", "MSFT", "GOOGL", "AMZN", "TSLA", "META", "NVDA", "NFLX"
            );
            
            java.util.Map<String, StrategyComparison> results = new java.util.HashMap<>();
            
            for (String ticker : tickers) {
                try {
                    StrategyComparison comparison = comprehensiveBacktestService.runComprehensiveBacktest(ticker, initialCapital);
                    results.put(ticker, comparison);
                    log.info("Completed backtest for ticker: {}", ticker);
                } catch (Exception e) {
                    log.error("Error backtesting ticker: {}", ticker, e);
                }
            }
            
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error running all tickers backtest", e);
            return ResponseEntity.status(500).body(new java.util.HashMap<>());
        }
    }
}