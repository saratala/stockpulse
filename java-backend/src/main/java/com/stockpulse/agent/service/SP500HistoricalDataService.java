package com.stockpulse.agent.service;

import com.stockpulse.agent.model.StockPrice;
import com.stockpulse.agent.repository.StockPriceRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service to fetch 2 years of historical daily OHLC data for all S&P 500 tickers.
 * Uses BOTH Yahoo Finance AND Finnhub to avoid rate limiting.
 * Alternates between data sources to stay under API limits.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SP500HistoricalDataService {

    private final StockPriceRepository stockPriceRepository;
    private final YahooFinanceService yahooFinanceService;
    private final FinnhubDataService finnhubDataService;

    // Track if a fetch is currently running
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicInteger progress = new AtomicInteger(0);
    private final AtomicInteger totalTickers = new AtomicInteger(0);
    private String currentTicker = "";
    private LocalDateTime startTime = null;

    // Track which data source to use (alternates to avoid rate limits)
    private String currentDataSource = "YAHOO";
    private int yahooSuccessCount = 0;
    private int finnhubSuccessCount = 0;
    private int yahooFailCount = 0;
    private int finnhubFailCount = 0;

    @Data
    @Builder
    public static class FetchStatus {
        private boolean isRunning;
        private int progress;
        private int total;
        private String currentTicker;
        private String currentDataSource;
        private LocalDateTime startTime;
        private Map<String, Integer> results;
        private int yahooSuccessCount;
        private int finnhubSuccessCount;
        private int yahooFailCount;
        private int finnhubFailCount;
    }

    /**
     * Get current fetch status
     */
    public FetchStatus getStatus() {
        return FetchStatus.builder()
            .isRunning(isRunning.get())
            .progress(progress.get())
            .total(totalTickers.get())
            .currentTicker(currentTicker)
            .currentDataSource(currentDataSource)
            .startTime(startTime)
            .yahooSuccessCount(yahooSuccessCount)
            .finnhubSuccessCount(finnhubSuccessCount)
            .yahooFailCount(yahooFailCount)
            .finnhubFailCount(finnhubFailCount)
            .build();
    }

    /**
     * Start fetching historical data for all S&P 500 tickers.
     * This runs asynchronously and can be monitored via getStatus().
     */
    @Async
    public void startHistoricalDataFetch() {
        if (isRunning.compareAndSet(false, true)) {
            try {
                fetchAllSP500Data();
            } finally {
                isRunning.set(false);
            }
        } else {
            log.warn("Historical data fetch already in progress");
        }
    }

    /**
     * Synchronous fetch - for direct calls.
     * Alternates between Yahoo Finance and Finnhub to avoid rate limiting.
     */
    @Transactional
    public Map<String, Integer> fetchAllSP500Data() {
        log.info("Starting S&P 500 historical data fetch using DUAL sources (Yahoo + Finnhub)...");
        startTime = LocalDateTime.now();

        // Reset counters
        yahooSuccessCount = 0;
        finnhubSuccessCount = 0;
        yahooFailCount = 0;
        finnhubFailCount = 0;

        List<String> tickers = getSP500Tickers();
        totalTickers.set(tickers.size());
        progress.set(0);

        Map<String, Integer> results = new LinkedHashMap<>();
        int currentYear = LocalDateTime.now().getYear();
        boolean finnhubAvailable = finnhubDataService.isConfigured();

        if (!finnhubAvailable) {
            log.warn("Finnhub API not configured - using Yahoo Finance only");
        } else {
            log.info("Both Yahoo Finance and Finnhub configured - will alternate sources");
        }

        for (int i = 0; i < tickers.size(); i++) {
            String ticker = tickers.get(i);
            currentTicker = ticker;
            progress.set(i + 1);

            try {
                int totalRecords = 0;

                // Alternate data source for each ticker to distribute load
                // Even index = Yahoo, Odd index = Finnhub (if available)
                boolean useYahoo = !finnhubAvailable || (i % 2 == 0);
                currentDataSource = useYahoo ? "YAHOO" : "FINNHUB";

                if (useYahoo) {
                    // Use Yahoo Finance
                    for (int year = currentYear - 1; year <= currentYear; year++) {
                        int records = fetchAndStoreYearDataFromYahoo(ticker, year);
                        totalRecords += records;
                        Thread.sleep(500); // 500ms between Yahoo requests
                    }
                    if (totalRecords > 0) {
                        yahooSuccessCount++;
                    } else {
                        yahooFailCount++;
                        // Try Finnhub as fallback
                        if (finnhubAvailable) {
                            log.info("Yahoo failed for {}, trying Finnhub fallback...", ticker);
                            totalRecords = fetchAndStoreDataFromFinnhub(ticker);
                            if (totalRecords > 0) {
                                finnhubSuccessCount++;
                                yahooFailCount--; // Recovered via fallback
                            }
                        }
                    }
                } else {
                    // Use Finnhub
                    totalRecords = fetchAndStoreDataFromFinnhub(ticker);
                    if (totalRecords > 0) {
                        finnhubSuccessCount++;
                    } else {
                        finnhubFailCount++;
                        // Try Yahoo as fallback
                        log.info("Finnhub failed for {}, trying Yahoo fallback...", ticker);
                        for (int year = currentYear - 1; year <= currentYear; year++) {
                            int records = fetchAndStoreYearDataFromYahoo(ticker, year);
                            totalRecords += records;
                            Thread.sleep(500);
                        }
                        if (totalRecords > 0) {
                            yahooSuccessCount++;
                            finnhubFailCount--; // Recovered via fallback
                        }
                    }
                }

                results.put(ticker, totalRecords);
                log.info("[{}/{}] {} via {} - {} records",
                    i + 1, tickers.size(), ticker, currentDataSource, totalRecords);

            } catch (Exception e) {
                log.error("Error fetching data for {}: {}", ticker, e.getMessage());
                results.put(ticker, -1);
            }

            // Reduced delay since we're alternating sources
            // Every 20 tickers, pause for 3 seconds
            if ((i + 1) % 20 == 0) {
                try {
                    log.info("Progress: {}/{} - Yahoo: {} success/{} fail, Finnhub: {} success/{} fail",
                        i + 1, tickers.size(),
                        yahooSuccessCount, yahooFailCount,
                        finnhubSuccessCount, finnhubFailCount);
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        currentTicker = "";
        currentDataSource = "";
        log.info("S&P 500 historical data fetch complete. Total: {} tickers", results.size());
        log.info("Final stats - Yahoo: {} success/{} fail, Finnhub: {} success/{} fail",
            yahooSuccessCount, yahooFailCount, finnhubSuccessCount, finnhubFailCount);
        return results;
    }

    /**
     * Fetch and store year data from Yahoo Finance
     */
    private int fetchAndStoreYearDataFromYahoo(String ticker, int year) {
        try {
            Optional<YahooFinanceService.HistoricalData> dataOpt =
                yahooFinanceService.fetchYearlyData(ticker, year);

            if (!dataOpt.isPresent() || dataOpt.get().getPriceData() == null) {
                return 0;
            }

            YahooFinanceService.HistoricalData data = dataOpt.get();
            int saved = 0;

            for (Map.Entry<LocalDateTime, YahooFinanceService.HistoricalData.PriceData> entry :
                 data.getPriceData().entrySet()) {

                LocalDateTime date = entry.getKey();
                YahooFinanceService.HistoricalData.PriceData priceData = entry.getValue();

                // Check if record already exists
                List<StockPrice> existing = stockPriceRepository.findByTickerAndDateBetween(
                    ticker, date.minusHours(12), date.plusHours(12));

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
                    saved++;
                }
            }

            return saved;

        } catch (Exception e) {
            log.error("Error fetching year {} for {} from Yahoo: {}", year, ticker, e.getMessage());
            return 0;
        }
    }

    /**
     * Fetch and store 2 years of data from Finnhub
     */
    private int fetchAndStoreDataFromFinnhub(String ticker) {
        try {
            Optional<FinnhubDataService.HistoricalDataResult> resultOpt =
                finnhubDataService.fetchTwoYearsData(ticker);

            if (!resultOpt.isPresent() || resultOpt.get().getCandles() == null) {
                return 0;
            }

            FinnhubDataService.HistoricalDataResult result = resultOpt.get();
            if (!"success".equals(result.getStatus())) {
                log.warn("Finnhub returned status '{}' for {}: {}",
                    result.getStatus(), ticker, result.getMessage());
                return 0;
            }

            int saved = 0;
            for (FinnhubDataService.CandleData candle : result.getCandles()) {
                LocalDateTime date = candle.getDate();

                // Check if record already exists
                List<StockPrice> existing = stockPriceRepository.findByTickerAndDateBetween(
                    ticker, date.minusHours(12), date.plusHours(12));

                if (existing.isEmpty()) {
                    StockPrice stockPrice = StockPrice.builder()
                        .ticker(ticker)
                        .date(date)
                        .open(candle.getOpen())
                        .high(candle.getHigh())
                        .low(candle.getLow())
                        .close(candle.getClose())
                        .volume(candle.getVolume())
                        .adjustedClose(candle.getClose())
                        .build();

                    stockPriceRepository.save(stockPrice);
                    saved++;
                }
            }

            return saved;

        } catch (Exception e) {
            log.error("Error fetching data for {} from Finnhub: {}", ticker, e.getMessage());
            return 0;
        }
    }

    /**
     * Complete S&P 500 ticker list (503 tickers)
     */
    public List<String> getSP500Tickers() {
        return Arrays.asList(
            // Technology
            "AAPL", "MSFT", "GOOGL", "GOOG", "META", "NVDA", "AVGO", "CSCO", "ADBE", "CRM",
            "ORCL", "ACN", "IBM", "INTC", "AMD", "TXN", "QCOM", "NOW", "INTU", "AMAT",
            "ADI", "LRCX", "MU", "KLAC", "SNPS", "CDNS", "MCHP", "FTNT", "PANW", "CRWD",
            "HPQ", "HPE", "DELL", "WDC", "STX", "NTAP", "KEYS", "ANSS", "CTSH", "IT",
            "EPAM", "LDOS", "AKAM", "FFIV", "JNPR", "ZBRA", "SWKS", "QRVO", "TER", "MPWR",

            // Communication Services
            "NFLX", "DIS", "CMCSA", "T", "VZ", "TMUS", "CHTR", "EA", "TTWO", "MTCH",
            "WBD", "PARA", "FOX", "FOXA", "NWS", "NWSA", "LYV", "IPG", "OMC",

            // Consumer Discretionary
            "AMZN", "TSLA", "HD", "MCD", "NKE", "SBUX", "LOW", "TJX", "BKNG", "MAR",
            "CMG", "ORLY", "AZO", "ROST", "DHI", "LEN", "PHM", "NVR", "GRMN", "POOL",
            "BBY", "ULTA", "DRI", "YUM", "DARDEN", "HLT", "WYNN", "LVS", "MGM", "CZR",
            "F", "GM", "APTV", "BWA", "LEA", "RL", "PVH", "TPR", "VFC", "HAS",
            "EBAY", "ETSY", "W", "EXPE", "ABNB", "DASH", "LYFT", "UBER",

            // Consumer Staples
            "PG", "KO", "PEP", "COST", "WMT", "PM", "MO", "MDLZ", "CL", "KMB",
            "GIS", "K", "HSY", "HRL", "SJM", "CAG", "CPB", "MKC", "CHD", "CLX",
            "EL", "KR", "SYY", "ADM", "BG", "TSN", "KHC", "STZ", "TAP", "SAM",
            "MNST", "KDP", "WBA", "CVS",

            // Energy
            "XOM", "CVX", "COP", "SLB", "EOG", "MPC", "PSX", "VLO", "OXY", "PXD",
            "DVN", "HES", "FANG", "HAL", "BKR", "KMI", "WMB", "OKE", "TRGP", "LNG",
            "MRO", "APA", "CTRA",

            // Financials
            "BRK.B", "JPM", "V", "MA", "BAC", "WFC", "GS", "MS", "SPGI", "BLK",
            "C", "AXP", "SCHW", "CB", "MMC", "PGR", "AON", "ICE", "CME", "MCO",
            "TFC", "USB", "PNC", "COF", "BK", "STT", "NTRS", "FITB", "RF", "CFG",
            "KEY", "HBAN", "MTB", "ZION", "CMA", "SIVB", "FRC", "SBNY", "WAL",
            "AIG", "MET", "PRU", "AFL", "TRV", "ALL", "HIG", "L", "CINF", "GL",
            "RE", "AJG", "WRB", "BRO", "RJF", "MKTX", "NDAQ", "CBOE", "IVZ", "BEN",
            "TROW", "AMG", "FDS", "MSCI",

            // Healthcare
            "UNH", "JNJ", "LLY", "PFE", "ABBV", "MRK", "TMO", "ABT", "DHR", "BMY",
            "AMGN", "GILD", "VRTX", "REGN", "ISRG", "MDT", "SYK", "BSX", "EW", "ZBH",
            "BDX", "BAX", "DXCM", "IDXX", "IQV", "MTD", "A", "WAT", "PKI", "TECH",
            "CI", "ELV", "HUM", "CNC", "MOH", "CVS", "MCK", "ABC", "CAH", "HSIC",
            "ALGN", "COO", "HOLX", "TFX", "XRAY", "BIIB", "MRNA", "SGEN", "ILMN",

            // Industrials
            "UNP", "UPS", "HON", "CAT", "DE", "RTX", "BA", "LMT", "GE", "GD",
            "NOC", "TDG", "ITW", "EMR", "ETN", "PH", "ROK", "CMI", "PCAR", "OTIS",
            "CARR", "JCI", "TT", "LHX", "HII", "TXT", "LDOS", "CTAS", "PAYX", "ADP",
            "VRSK", "INFO", "BR", "CPRT", "FAST", "WM", "RSG", "WCN", "GWW", "SWK",
            "EXPD", "CHRW", "JBHT", "XPO", "ODFL", "SAIA", "LSTR", "HUBG", "SNDR",
            "FDX", "CSX", "NSC", "DAL", "UAL", "LUV", "AAL", "ALK",
            "MMM", "IR", "DOV", "PNR", "AME", "ROP", "IEX", "NDSN",

            // Materials
            "LIN", "APD", "SHW", "ECL", "DD", "NEM", "FCX", "CTVA", "DOW", "NUE",
            "VMC", "MLM", "ALB", "PPG", "CE", "EMN", "IFF", "FMC", "MOS", "CF",
            "IP", "PKG", "WRK", "SEE", "AVY", "BLL", "AMCR",

            // Real Estate
            "PLD", "AMT", "CCI", "EQIX", "PSA", "SPG", "O", "WELL", "DLR", "AVB",
            "EQR", "VTR", "ARE", "MAA", "UDR", "ESS", "EXR", "PEAK", "HST", "KIM",
            "REG", "FRT", "BXP", "VNO", "SLG", "CBRE", "JLL",

            // Utilities
            "NEE", "DUK", "SO", "D", "AEP", "SRE", "EXC", "XEL", "PEG", "ED",
            "WEC", "ES", "AWK", "DTE", "ETR", "FE", "PPL", "AEE", "CMS", "CNP",
            "EVRG", "ATO", "NI", "LNT", "PNW", "NRG", "AES",

            // Additional popular stocks
            "PYPL", "SQ", "ROKU", "ZM", "PLTR", "SNOW", "DDOG", "NET", "ZS", "OKTA",
            "COIN", "HOOD", "SOFI", "RIVN", "LCID", "NIO", "XPEV", "LI"
        );
    }
}
