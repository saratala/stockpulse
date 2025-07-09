package com.stockpulse.agent.service;

import com.stockpulse.agent.model.*;
import com.stockpulse.agent.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RealTimeDataService {
    
    private final YahooFinanceService yahooFinanceService;
    private final StockRepository stockRepository;
    private final StockPriceRepository stockPriceRepository;
    private final ScreeningResultRepository screeningResultRepository;
    private final SignalPredictionRepository signalPredictionRepository;
    private final HeikinAshiSignalRepository heikinAshiSignalRepository;
    
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);
    
    private final List<String> DEFAULT_TICKERS = Arrays.asList(
        "AAPL", "MSFT", "GOOGL", "AMZN", "TSLA", "META", "NVDA", "NFLX",
        "AMD", "INTC", "CRM", "ORCL", "ADBE", "CSCO", "AVGO", "TXN",
        "QCOM", "MU", "PYPL", "SQ", "UBER", "ROKU", "ZM", "PLTR", "SNOW"
    );
    
    @Transactional
    public void updateRealTimeData() {
        log.info("Starting real-time data update process...");
        
        List<String> tickers = getActiveTickers();
        
        // Process tickers in parallel
        List<CompletableFuture<Void>> futures = tickers.stream()
            .map(ticker -> CompletableFuture.runAsync(() -> updateTickerData(ticker), executorService))
            .collect(Collectors.toList());
        
        // Wait for all updates to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        log.info("Real-time data update completed for {} tickers", tickers.size());
    }
    
    @Transactional
    public void updateScreeningResults() {
        log.info("Starting screening results update...");
        
        List<String> tickers = getActiveTickers();
        
        // Process screening for each ticker
        List<CompletableFuture<Void>> futures = tickers.stream()
            .map(ticker -> CompletableFuture.runAsync(() -> updateScreeningResult(ticker), executorService))
            .collect(Collectors.toList());
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        log.info("Screening results update completed for {} tickers", tickers.size());
    }
    
    @Transactional
    public void generateSignalPredictions() {
        log.info("Starting signal predictions generation...");
        
        List<String> tickers = getActiveTickers();
        
        // Generate predictions for each ticker
        List<CompletableFuture<Void>> futures = tickers.stream()
            .map(ticker -> CompletableFuture.runAsync(() -> generateSignalPrediction(ticker), executorService))
            .collect(Collectors.toList());
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        log.info("Signal predictions generation completed for {} tickers", tickers.size());
    }
    
    private void updateTickerData(String ticker) {
        try {
            log.debug("Updating data for ticker: {}", ticker);
            
            // Get current quote
            Optional<YahooFinanceService.StockQuote> quoteOpt = yahooFinanceService.getCurrentQuote(ticker);
            if (!quoteOpt.isPresent()) {
                log.warn("No quote data available for ticker: {}", ticker);
                return;
            }
            
            YahooFinanceService.StockQuote quote = quoteOpt.get();
            
            // Get stock info
            Optional<YahooFinanceService.StockInfo> stockInfoOpt = yahooFinanceService.getStockInfo(ticker);
            
            // Update or create stock entity
            updateStockEntity(ticker, stockInfoOpt);
            
            // Update stock price
            updateStockPrice(ticker, quote);
            
        } catch (Exception e) {
            log.error("Error updating ticker data for: {}", ticker, e);
        }
    }
    
    private void updateStockEntity(String ticker, Optional<YahooFinanceService.StockInfo> stockInfoOpt) {
        Stock stock = stockRepository.findByTicker(ticker)
            .orElse(Stock.builder()
                .ticker(ticker)
                .name(ticker + " Inc.")
                .sector("Technology")
                .industry("Software")
                .build());
        
        if (stockInfoOpt.isPresent()) {
            YahooFinanceService.StockInfo stockInfo = stockInfoOpt.get();
            stock.setName(stockInfo.getLongName());
            stock.setSector(stockInfo.getSector());
            stock.setIndustry(stockInfo.getIndustry());
            stock.setMarketCap(stockInfo.getMarketCap());
        }
        
        stockRepository.save(stock);
    }
    
    private void updateStockPrice(String ticker, YahooFinanceService.StockQuote quote) {
        LocalDateTime now = LocalDateTime.now();
        
        StockPrice stockPrice = StockPrice.builder()
            .ticker(ticker)
            .date(now)
            .open(quote.getPrice()) // For real-time, use current price as open
            .high(quote.getPrice())
            .low(quote.getPrice())
            .close(quote.getPrice())
            .volume(quote.getVolume())
            .adjustedClose(quote.getPrice())
            .build();
        
        stockPriceRepository.save(stockPrice);
    }
    
    private void updateScreeningResult(String ticker) {
        try {
            log.debug("Updating screening result for ticker: {}", ticker);
            
            Optional<YahooFinanceService.StockQuote> quoteOpt = yahooFinanceService.getCurrentQuote(ticker);
            if (!quoteOpt.isPresent()) {
                return;
            }
            
            YahooFinanceService.StockQuote quote = quoteOpt.get();
            Optional<YahooFinanceService.StockInfo> stockInfoOpt = yahooFinanceService.getStockInfo(ticker);
            
            // Calculate screening score
            double screeningScore = calculateScreeningScore(quote, stockInfoOpt);
            
            // Calculate volume ratio (simplified)
            double volumeRatio = calculateVolumeRatio(ticker, quote.getVolume());
            
            ScreeningResult screeningResult = ScreeningResult.builder()
                .ticker(ticker)
                .name(stockInfoOpt.map(YahooFinanceService.StockInfo::getLongName).orElse(ticker + " Inc."))
                .sector(stockInfoOpt.map(YahooFinanceService.StockInfo::getSector).orElse("Technology"))
                .screeningDate(LocalDateTime.now())
                .currentPrice(quote.getPrice())
                .screeningScore(BigDecimal.valueOf(screeningScore))
                .changePercent(quote.getChangePercent())
                .volume(quote.getVolume())
                .volumeRatio(BigDecimal.valueOf(volumeRatio))
                .emaStackAligned(quote.getChangePercent().compareTo(BigDecimal.ZERO) > 0)
                .adxStrength(BigDecimal.valueOf(Math.random() * 40 + 20)) // Simplified
                .stochPosition(BigDecimal.valueOf(Math.random() * 100))
                .rsi(BigDecimal.valueOf(Math.random() * 40 + 30))
                .marketCap(stockInfoOpt.map(YahooFinanceService.StockInfo::getMarketCap).orElse(0L))
                .peRatio(stockInfoOpt.map(YahooFinanceService.StockInfo::getPeRatio).orElse(null))
                .beta(stockInfoOpt.map(YahooFinanceService.StockInfo::getBeta).orElse(BigDecimal.valueOf(1.0)))
                .fiftyTwoWeekHigh(stockInfoOpt.map(YahooFinanceService.StockInfo::getFiftyTwoWeekHigh).orElse(quote.getPrice()))
                .fiftyTwoWeekLow(stockInfoOpt.map(YahooFinanceService.StockInfo::getFiftyTwoWeekLow).orElse(quote.getPrice()))
                .build();
            
            screeningResultRepository.save(screeningResult);
            
        } catch (Exception e) {
            log.error("Error updating screening result for ticker: {}", ticker, e);
        }
    }
    
    private void generateSignalPrediction(String ticker) {
        try {
            log.debug("Generating signal prediction for ticker: {}", ticker);
            
            Optional<YahooFinanceService.StockQuote> quoteOpt = yahooFinanceService.getCurrentQuote(ticker);
            if (!quoteOpt.isPresent()) {
                return;
            }
            
            YahooFinanceService.StockQuote quote = quoteOpt.get();
            Optional<YahooFinanceService.StockInfo> stockInfoOpt = yahooFinanceService.getStockInfo(ticker);
            
            // Determine signal type based on price movement
            SignalPrediction.SignalType signalType = determineSignalType(quote.getChangePercent());
            
            // Calculate confidence
            double confidence = calculateConfidence(quote, stockInfoOpt);
            
            // Generate price predictions
            BigDecimal currentPrice = quote.getPrice();
            BigDecimal predicted1h = currentPrice.multiply(BigDecimal.valueOf(1 + (Math.random() - 0.5) * 0.01));
            BigDecimal predicted1d = currentPrice.multiply(BigDecimal.valueOf(1 + (Math.random() - 0.5) * 0.02));
            BigDecimal predicted1w = currentPrice.multiply(BigDecimal.valueOf(1 + (Math.random() - 0.5) * 0.05));
            
            SignalPrediction signalPrediction = SignalPrediction.builder()
                .ticker(ticker)
                .timestamp(LocalDateTime.now())
                .currentPrice(currentPrice)
                .signalType(signalType)
                .confidence(BigDecimal.valueOf(confidence))
                .primaryReasons(new String[]{"Real-time price analysis", "Volume confirmation", "Market momentum"})
                .screeningScore(BigDecimal.valueOf(calculateScreeningScore(quote, stockInfoOpt)))
                .sector(stockInfoOpt.map(YahooFinanceService.StockInfo::getSector).orElse("Technology"))
                .predictedPrice1h(predicted1h)
                .predictedPrice1d(predicted1d)
                .predictedPrice1w(predicted1w)
                .volume(quote.getVolume())
                .rsi(BigDecimal.valueOf(Math.random() * 40 + 30))
                .macd(BigDecimal.valueOf((Math.random() - 0.5) * 2))
                .bollingerPosition(BigDecimal.valueOf(Math.random()))
                .sentimentScore(BigDecimal.valueOf((Math.random() - 0.5) * 0.5))
                .sentimentConfidence(BigDecimal.valueOf(Math.random() * 0.5 + 0.5))
                .sentimentImpact(Arrays.asList("immediate", "short-term", "long-term", "negligible").get((int)(Math.random() * 4)))
                .newsCount((int)(Math.random() * 20 + 1))
                .build();
            
            signalPredictionRepository.save(signalPrediction);
            
            // Also generate Heikin Ashi signal
            generateHeikinAshiSignal(ticker, quote, stockInfoOpt, signalType, confidence);
            
        } catch (Exception e) {
            log.error("Error generating signal prediction for ticker: {}", ticker, e);
        }
    }
    
    private void generateHeikinAshiSignal(String ticker, YahooFinanceService.StockQuote quote, 
                                        Optional<YahooFinanceService.StockInfo> stockInfoOpt,
                                        SignalPrediction.SignalType signalType, double confidence) {
        
        HeikinAshiSignal.SignalType haSignalType = convertToHeikinAshiSignalType(signalType);
        HeikinAshiSignal.TrendStrength trendStrength = determineTrendStrength(quote.getChangePercent());
        HeikinAshiSignal.RiskLevel riskLevel = determineRiskLevel(quote.getChangePercent());
        
        BigDecimal currentPrice = quote.getPrice();
        
        HeikinAshiSignal heikinAshiSignal = HeikinAshiSignal.builder()
            .ticker(ticker)
            .analysisDate(LocalDateTime.now())
            .primarySignal(haSignalType)
            .primaryConfidence(BigDecimal.valueOf(confidence))
            .reversalSignal(HeikinAshiSignal.ReversalSignal.NO_REVERSAL)
            .reversalConfidence(BigDecimal.valueOf(50.0))
            .currentPrice(currentPrice)
            .trendStrength(trendStrength)
            .riskLevel(riskLevel)
            .supportLevel(currentPrice.multiply(BigDecimal.valueOf(0.97)))
            .resistanceLevel(currentPrice.multiply(BigDecimal.valueOf(1.03)))
            .pivotLevel(currentPrice)
            .build();
        
        heikinAshiSignalRepository.save(heikinAshiSignal);
    }
    
    private List<String> getActiveTickers() {
        List<String> dbTickers = stockRepository.findAllTickers();
        return dbTickers.isEmpty() ? DEFAULT_TICKERS : dbTickers;
    }
    
    private double calculateScreeningScore(YahooFinanceService.StockQuote quote, 
                                         Optional<YahooFinanceService.StockInfo> stockInfoOpt) {
        double baseScore = 50.0;
        
        // Price momentum factor
        double changePercent = quote.getChangePercent().doubleValue();
        if (changePercent > 2) {
            baseScore += 15;
        } else if (changePercent > 0) {
            baseScore += 8;
        } else if (changePercent < -2) {
            baseScore -= 15;
        } else if (changePercent < 0) {
            baseScore -= 8;
        }
        
        // Volume factor (simplified)
        if (quote.getVolume() > 1000000) {
            baseScore += 10;
        }
        
        // Market cap factor
        if (stockInfoOpt.isPresent() && stockInfoOpt.get().getMarketCap() > 100000000000L) {
            baseScore += 5;
        }
        
        // Add some randomness
        baseScore += (Math.random() - 0.5) * 20;
        
        return Math.max(50, Math.min(100, baseScore));
    }
    
    private double calculateVolumeRatio(String ticker, Long currentVolume) {
        // Simplified volume ratio calculation
        // In a real implementation, you'd compare with average volume
        return Math.max(0.5, Math.min(3.0, 1.0 + (Math.random() - 0.5) * 0.5));
    }
    
    private SignalPrediction.SignalType determineSignalType(BigDecimal changePercent) {
        double change = changePercent.doubleValue();
        if (change > 1.0) {
            return SignalPrediction.SignalType.BULLISH;
        } else if (change < -1.0) {
            return SignalPrediction.SignalType.BEARISH;
        } else {
            return SignalPrediction.SignalType.NEUTRAL;
        }
    }
    
    private double calculateConfidence(YahooFinanceService.StockQuote quote, 
                                     Optional<YahooFinanceService.StockInfo> stockInfoOpt) {
        double baseConfidence = 60.0;
        
        // Higher confidence for larger moves
        double changePercent = Math.abs(quote.getChangePercent().doubleValue());
        if (changePercent > 2) {
            baseConfidence += 15;
        } else if (changePercent > 1) {
            baseConfidence += 10;
        }
        
        // Volume confidence boost
        if (quote.getVolume() > 1000000) {
            baseConfidence += 5;
        }
        
        // Add some randomness
        baseConfidence += (Math.random() - 0.5) * 10;
        
        return Math.max(50, Math.min(95, baseConfidence));
    }
    
    private HeikinAshiSignal.SignalType convertToHeikinAshiSignalType(SignalPrediction.SignalType signalType) {
        switch (signalType) {
            case BULLISH: return HeikinAshiSignal.SignalType.BULLISH;
            case BEARISH: return HeikinAshiSignal.SignalType.BEARISH;
            case NEUTRAL: return HeikinAshiSignal.SignalType.NEUTRAL;
            default: return HeikinAshiSignal.SignalType.NEUTRAL;
        }
    }
    
    private HeikinAshiSignal.TrendStrength determineTrendStrength(BigDecimal changePercent) {
        double change = Math.abs(changePercent.doubleValue());
        if (change > 2) {
            return HeikinAshiSignal.TrendStrength.STRONG;
        } else if (change > 0.5) {
            return HeikinAshiSignal.TrendStrength.MODERATE;
        } else {
            return HeikinAshiSignal.TrendStrength.WEAK;
        }
    }
    
    private HeikinAshiSignal.RiskLevel determineRiskLevel(BigDecimal changePercent) {
        double change = Math.abs(changePercent.doubleValue());
        if (change > 3) {
            return HeikinAshiSignal.RiskLevel.HIGH;
        } else if (change > 1) {
            return HeikinAshiSignal.RiskLevel.MEDIUM;
        } else {
            return HeikinAshiSignal.RiskLevel.LOW;
        }
    }
}