package com.stockpulse.agent.controller;

import com.stockpulse.agent.dto.StrategyComparison;
import com.stockpulse.agent.service.ComprehensiveBacktestService;
import com.stockpulse.agent.service.RealTimeDataService;
import com.stockpulse.agent.service.CsvImportService;
import com.stockpulse.agent.service.DailyPriceBackfillService;
import com.stockpulse.agent.service.SP500HistoricalDataService;
import com.stockpulse.agent.service.YahooFinanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    private final CsvImportService csvImportService;
    private final SP500HistoricalDataService sp500HistoricalDataService;
    private final DailyPriceBackfillService dailyPriceBackfillService;
    
    /**
     * Load Yahoo daily bars for the current year and the previous {@code years} years,
     * overwriting existing bars for the same day (Yahoo's bar is authoritative).
     */
    @PostMapping("/daily/backfill/{ticker}")
    public ResponseEntity<DailyPriceBackfillService.BackfillResult> backfillDaily(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "1") int years) {
        return ResponseEntity.ok(dailyPriceBackfillService.backfill(ticker, Math.max(0, Math.min(years, 20))));
    }

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

    /**
     * Import historical stock data from CSV file (Yahoo Finance format)
     * CSV format: Date,Open,High,Low,Close,Adj Close,Volume
     */
    @PostMapping(value = "/import/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importCsvData(
            @RequestParam("file") MultipartFile file,
            @RequestParam("ticker") String ticker) {

        log.info("CSV import requested for ticker: {}, file: {}", ticker, file.getOriginalFilename());

        try {
            if (file.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("status", "error");
                response.put("message", "File is empty");
                return ResponseEntity.badRequest().body(response);
            }

            CsvImportService.ImportResult result = csvImportService.importYahooFinanceCsv(
                ticker.toUpperCase(), file.getInputStream());

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("ticker", ticker.toUpperCase());
            response.put("recordsImported", result.getRecordsImported());
            response.put("recordsSkipped", result.getRecordsSkipped());
            response.put("startDate", result.getStartDate());
            response.put("endDate", result.getEndDate());
            response.put("message", "Successfully imported " + result.getRecordsImported() + " records");
            response.put("timestamp", java.time.LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error importing CSV for ticker: {}", ticker, e);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "CSV import failed: " + e.getMessage());
            response.put("ticker", ticker);
            response.put("timestamp", java.time.LocalDateTime.now());

            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Import multiple CSV files at once
     */
    @PostMapping(value = "/import/csv-batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importMultipleCsvFiles(
            @RequestParam("files") MultipartFile[] files) {

        log.info("Batch CSV import requested for {} files", files.length);

        Map<String, Object> results = new HashMap<>();
        int totalImported = 0;
        int totalSkipped = 0;
        int successCount = 0;
        int failCount = 0;

        for (MultipartFile file : files) {
            String filename = file.getOriginalFilename();
            if (filename == null || filename.isEmpty()) continue;

            // Extract ticker from filename (e.g., "AAPL.csv" -> "AAPL")
            String ticker = filename.replace(".csv", "").toUpperCase();

            try {
                CsvImportService.ImportResult result = csvImportService.importYahooFinanceCsv(
                    ticker, file.getInputStream());

                results.put(ticker, Map.of(
                    "status", "success",
                    "recordsImported", result.getRecordsImported(),
                    "recordsSkipped", result.getRecordsSkipped()
                ));
                totalImported += result.getRecordsImported();
                totalSkipped += result.getRecordsSkipped();
                successCount++;

            } catch (Exception e) {
                log.error("Error importing CSV for ticker: {}", ticker, e);
                results.put(ticker, Map.of(
                    "status", "error",
                    "message", e.getMessage()
                ));
                failCount++;
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", failCount == 0 ? "success" : "partial");
        response.put("totalFiles", files.length);
        response.put("successfulImports", successCount);
        response.put("failedImports", failCount);
        response.put("totalRecordsImported", totalImported);
        response.put("totalRecordsSkipped", totalSkipped);
        response.put("details", results);
        response.put("timestamp", java.time.LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    // ============ S&P 500 Historical Data Endpoints ============

    /**
     * Start fetching 2 years of historical data for all S&P 500 tickers.
     * This runs asynchronously - use /status endpoint to monitor progress.
     */
    @PostMapping("/sp500/fetch-historical")
    public ResponseEntity<Map<String, Object>> startSP500HistoricalFetch() {
        log.info("S&P 500 historical data fetch requested");

        SP500HistoricalDataService.FetchStatus status = sp500HistoricalDataService.getStatus();

        if (status.isRunning()) {
            Map<String, Object> response = new HashMap<>();
            response.put("status", "already_running");
            response.put("message", "Fetch already in progress");
            response.put("progress", status.getProgress());
            response.put("total", status.getTotal());
            response.put("currentTicker", status.getCurrentTicker());
            return ResponseEntity.ok(response);
        }

        // Start async fetch
        sp500HistoricalDataService.startHistoricalDataFetch();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "started");
        response.put("message", "S&P 500 historical data fetch started. Use /status endpoint to monitor.");
        response.put("totalTickers", sp500HistoricalDataService.getSP500Tickers().size());
        response.put("timestamp", java.time.LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    /**
     * Get status of the S&P 500 historical data fetch
     */
    @GetMapping("/sp500/status")
    public ResponseEntity<Map<String, Object>> getSP500FetchStatus() {
        SP500HistoricalDataService.FetchStatus status = sp500HistoricalDataService.getStatus();

        Map<String, Object> response = new HashMap<>();
        response.put("isRunning", status.isRunning());
        response.put("progress", status.getProgress());
        response.put("total", status.getTotal());
        response.put("currentTicker", status.getCurrentTicker());
        response.put("currentDataSource", status.getCurrentDataSource());
        response.put("startTime", status.getStartTime());

        // Data source statistics
        Map<String, Object> sourceStats = new HashMap<>();
        sourceStats.put("yahooSuccess", status.getYahooSuccessCount());
        sourceStats.put("yahooFail", status.getYahooFailCount());
        sourceStats.put("finnhubSuccess", status.getFinnhubSuccessCount());
        sourceStats.put("finnhubFail", status.getFinnhubFailCount());
        response.put("sourceStats", sourceStats);

        if (status.getTotal() > 0) {
            double percentComplete = (status.getProgress() * 100.0) / status.getTotal();
            response.put("percentComplete", Math.round(percentComplete * 100.0) / 100.0);
        }

        return ResponseEntity.ok(response);
    }

    /**
     * Synchronous fetch for a single ticker (for testing)
     */
    @PostMapping("/sp500/fetch-single/{ticker}")
    public ResponseEntity<Map<String, Object>> fetchSingleTicker(@PathVariable String ticker) {
        log.info("Single ticker historical fetch requested: {}", ticker);

        try {
            // Use the ML prediction service to fetch for this ticker
            int currentYear = java.time.LocalDateTime.now().getYear();
            int totalRecords = 0;

            for (int year = currentYear - 1; year <= currentYear; year++) {
                java.util.Optional<YahooFinanceService.HistoricalData> dataOpt =
                    getYahooFinanceService().fetchYearlyData(ticker.toUpperCase(), year);

                if (dataOpt.isPresent() && dataOpt.get().getPriceData() != null) {
                    totalRecords += dataOpt.get().getPriceData().size();
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("ticker", ticker.toUpperCase());
            response.put("recordsFound", totalRecords);
            response.put("timestamp", java.time.LocalDateTime.now());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching single ticker: {}", ticker, e);
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Get list of S&P 500 tickers
     */
    @GetMapping("/sp500/tickers")
    public ResponseEntity<Map<String, Object>> getSP500Tickers() {
        java.util.List<String> tickers = sp500HistoricalDataService.getSP500Tickers();

        Map<String, Object> response = new HashMap<>();
        response.put("count", tickers.size());
        response.put("tickers", tickers);

        return ResponseEntity.ok(response);
    }

    // Helper to get YahooFinanceService (injected via constructor would be cleaner but this works)
    @org.springframework.beans.factory.annotation.Autowired
    private YahooFinanceService yahooFinanceService;

    private YahooFinanceService getYahooFinanceService() {
        return yahooFinanceService;
    }
}