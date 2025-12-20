package com.stockpulse.agent.controller;

import com.stockpulse.agent.dto.BacktestResult;
import com.stockpulse.agent.dto.StrategyComparison;
import com.stockpulse.agent.model.Stock;
import com.stockpulse.agent.model.StockPrice;
import com.stockpulse.agent.repository.StockRepository;
import com.stockpulse.agent.repository.StockPriceRepository;
import com.stockpulse.agent.service.BacktestingService;
import com.stockpulse.agent.service.ComprehensiveBacktestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/stocks")
@RequiredArgsConstructor
@Slf4j
public class StockController {
    
    private final StockRepository stockRepository;
    private final StockPriceRepository stockPriceRepository;
    private final BacktestingService backtestingService;
    private final ComprehensiveBacktestService comprehensiveBacktestService;
    
    @GetMapping
    public ResponseEntity<List<Stock>> getAllStocks() {
        log.info("Getting all stocks");
        List<Stock> stocks = stockRepository.findAll();
        return ResponseEntity.ok(stocks);
    }
    
    @GetMapping("/{ticker}")
    public ResponseEntity<Stock> getStock(@PathVariable String ticker) {
        log.info("Getting stock for ticker={}", ticker);
        
        Optional<Stock> stock = stockRepository.findByTicker(ticker.toUpperCase());
        return stock.map(ResponseEntity::ok)
                   .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/{ticker}/prices")
    public ResponseEntity<List<StockPrice>> getStockPrices(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(defaultValue = "1000") int limit) {
        
        log.info("Getting stock prices for ticker={}, days={}, limit={}", ticker, days, limit);
        
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<StockPrice> prices = stockPriceRepository.findByTickerAndDateBetween(
            ticker.toUpperCase(), since, LocalDateTime.now());
        
        // Limit results
        if (prices.size() > limit) {
            prices = prices.subList(0, limit);
        }
        
        return ResponseEntity.ok(prices);
    }
    
    @GetMapping("/{ticker}/prices/latest")
    public ResponseEntity<StockPrice> getLatestStockPrice(@PathVariable String ticker) {
        log.info("Getting latest stock price for ticker={}", ticker);
        
        Optional<StockPrice> latestPrice = stockPriceRepository.findLatestByTicker(ticker.toUpperCase());
        return latestPrice.map(ResponseEntity::ok)
                         .orElse(ResponseEntity.notFound().build());
    }
    
    @GetMapping("/tickers")
    public ResponseEntity<List<String>> getAllTickers() {
        log.info("Getting all tickers");
        List<String> tickers = stockRepository.findAllTickers();
        return ResponseEntity.ok(tickers);
    }
    
    @GetMapping("/sector/{sector}")
    public ResponseEntity<List<Stock>> getStocksBySector(@PathVariable String sector) {
        log.info("Getting stocks by sector={}", sector);
        List<Stock> stocks = stockRepository.findBySector(sector);
        return ResponseEntity.ok(stocks);
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
    
    @PostMapping("/backtest/sma")
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
}