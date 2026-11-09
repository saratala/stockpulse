package com.stockpulse.agent.service;

import com.stockpulse.agent.config.SP500Tickers;
import com.stockpulse.agent.model.*;
import com.stockpulse.agent.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
    
    // Use the full S&P 500 ticker list from configuration
    private final List<String> DEFAULT_TICKERS = SP500Tickers.getTickers();
    
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
    
    static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");
    static final LocalTime MARKET_OPEN = LocalTime.of(9, 30);

    private void updateStockPrice(String ticker, YahooFinanceService.StockQuote quote) {
        updateStockPrice(ticker, quote, ZonedDateTime.now(MARKET_ZONE));
    }

    /**
     * Upserts the current trading day's single daily bar from a real-time quote.
     * stock_prices holds one row per ticker per trading day, keyed (ticker, date) with the date
     * at 09:30 New York local time as a LocalDateTime - the same key the historical loader writes
     * (YahooFinanceService converts Yahoo's daily timestamps with America/New_York; the JVM runs
     * in UTC, so these rows are stored as 09:30+00). This used to insert a new row keyed by
     * LocalDateTime.now() on every 5-minute run, around the clock.
     */
    void updateStockPrice(String ticker, YahooFinanceService.StockQuote quote, ZonedDateTime now) {
        BigDecimal price = quote.getPrice();
        if (price == null || price.signum() <= 0) {
            return;
        }

        ZonedDateTime marketNow = now.withZoneSameInstant(MARKET_ZONE);
        LocalDate tradingDay = marketNow.toLocalDate();
        DayOfWeek dayOfWeek = tradingDay.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
                || marketNow.toLocalTime().isBefore(MARKET_OPEN)) {
            // No session today (yet): the quote is still the previous session's price.
            log.debug("Market not open for {} on {}, skipping daily bar update", ticker, tradingDay);
            return;
        }

        LocalDateTime barDate = tradingDay.atTime(MARKET_OPEN);
        Long volume = quote.getVolume();
        Optional<StockPrice> existing = stockPriceRepository.findById(new StockPriceId(ticker, barDate));

        StockPrice bar;
        if (existing.isPresent()) {
            bar = existing.get();
            BigDecimal high = bar.getHigh().max(price);
            BigDecimal low = bar.getLow().min(price);
            if (price.compareTo(bar.getClose()) == 0 && high.compareTo(bar.getHigh()) == 0
                    && low.compareTo(bar.getLow()) == 0 && Objects.equals(volume, bar.getVolume())) {
                return; // nothing changed (e.g. after the close)
            }
            bar.setHigh(high);
            bar.setLow(low);
            bar.setClose(price);
            bar.setAdjustedClose(price);
            bar.setVolume(volume);
        } else {
            // Weekday holiday: Yahoo keeps returning the last session's price and volume.
            Optional<StockPrice> latest = stockPriceRepository.findLatestByTicker(ticker);
            if (latest.isPresent() && price.compareTo(latest.get().getClose()) == 0
                    && Objects.equals(volume, latest.get().getVolume())) {
                log.debug("Quote for {} unchanged since last bar, skipping (market closed?)", ticker);
                return;
            }
            bar = StockPrice.builder()
                .ticker(ticker)
                .date(barDate)
                .open(price) // first quote seen in today's session
                .high(price)
                .low(price)
                .close(price)
                .volume(volume)
                .adjustedClose(price)
                .build();
        }

        stockPriceRepository.save(bar);
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

            // Ensure the Stock entity exists before creating screening result (foreign key constraint)
            updateStockEntity(ticker, stockInfoOpt);

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
        // Always use the full S&P 500 ticker list to ensure comprehensive coverage
        // The DEFAULT_TICKERS list contains all S&P 500 stocks
        return DEFAULT_TICKERS;
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
    
    @Transactional
    public int fetchHistoricalData(String period, String interval) {
        log.info("Fetching historical data for period: {}, interval: {}", period, interval);
        
        // For now, just return 0 as we're using existing data
        // This would be implemented when we add proper historical data fetching
        log.info("Using existing data for backtesting");
        return 0;
    }
    
    public int loadYearlyData(int year) {
        log.info("Loading yearly historical data for year: {} using parallel processing", year);

        List<String> tickers = getActiveTickers();
        java.util.concurrent.atomic.AtomicInteger totalRecords = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger processedCount = new java.util.concurrent.atomic.AtomicInteger(0);

        // Use a thread pool with 20 parallel threads for faster loading
        ExecutorService parallelExecutor = Executors.newFixedThreadPool(20);

        // Create futures for all tickers
        List<CompletableFuture<Integer>> futures = tickers.stream()
            .map(ticker -> CompletableFuture.supplyAsync(() -> {
                try {
                    int records = loadTickerYearlyData(ticker, year);
                    int count = processedCount.incrementAndGet();
                    if (count % 50 == 0) {
                        log.info("Progress: {}/{} tickers processed for year {}", count, tickers.size(), year);
                    }
                    return records;
                } catch (Exception e) {
                    log.error("Error loading yearly data for ticker: {}", ticker, e);
                    return 0;
                }
            }, parallelExecutor))
            .collect(Collectors.toList());

        // Wait for all to complete and sum up records
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        for (CompletableFuture<Integer> future : futures) {
            try {
                totalRecords.addAndGet(future.get());
            } catch (Exception e) {
                log.error("Error getting future result", e);
            }
        }

        parallelExecutor.shutdown();

        log.info("Successfully loaded {} total historical records for year {} ({} tickers)",
                totalRecords.get(), year, tickers.size());
        return totalRecords.get();
    }

    /**
     * Load yearly data for a single ticker - used by parallel processing
     */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public int loadTickerYearlyData(String ticker, int year) {
        int recordsLoaded = 0;
        try {
            Optional<YahooFinanceService.HistoricalData> historicalDataOpt =
                yahooFinanceService.fetchYearlyData(ticker, year);

            if (historicalDataOpt.isPresent()) {
                YahooFinanceService.HistoricalData historicalData = historicalDataOpt.get();

                // Ensure stock entity exists first
                updateStockEntity(ticker, yahooFinanceService.getStockInfo(ticker));

                // Convert and save each day's data
                for (Map.Entry<LocalDateTime, YahooFinanceService.HistoricalData.PriceData> entry :
                     historicalData.getPriceData().entrySet()) {

                    LocalDateTime date = entry.getKey();
                    YahooFinanceService.HistoricalData.PriceData priceData = entry.getValue();

                    // Check if record already exists to avoid duplicates
                    Optional<StockPrice> existing = stockPriceRepository
                        .findByTickerAndDateBetween(ticker,
                            date.minusMinutes(30), date.plusMinutes(30))
                        .stream().findFirst();

                    if (existing.isEmpty()) {
                        StockPrice stockPrice = StockPrice.builder()
                            .ticker(ticker)
                            .date(date)
                            .open(priceData.getOpen())
                            .high(priceData.getHigh())
                            .low(priceData.getLow())
                            .close(priceData.getClose())
                            .volume(priceData.getVolume())
                            .adjustedClose(priceData.getClose())
                            .build();

                        stockPriceRepository.save(stockPrice);
                        recordsLoaded++;
                    }
                }

                log.debug("Loaded {} records for ticker: {} year: {}", recordsLoaded, ticker, year);
            }

            // Small delay to be respectful to Yahoo API (50ms instead of 1000ms)
            Thread.sleep(50);

        } catch (Exception e) {
            log.error("Error loading yearly data for ticker: {} year: {}", ticker, year, e);
        }
        return recordsLoaded;
    }
}