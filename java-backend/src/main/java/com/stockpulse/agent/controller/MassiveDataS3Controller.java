package com.stockpulse.agent.controller;

import com.stockpulse.agent.service.MassiveDataS3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for Massive Data S3 integration.
 * Provides endpoints for importing bulk historical data from S3 storage.
 *
 * Strategy:
 * - Use S3 for historical data (past years) - no rate limits, bulk download
 * - Use API for recent data (last few days) - rate limited but up-to-date
 */
@RestController
@RequestMapping("/massivedata/s3")
@RequiredArgsConstructor
@Slf4j
public class MassiveDataS3Controller {

    private final MassiveDataS3Service s3Service;

    /**
     * Get S3 status and configuration
     */
    @GetMapping("/status")
    public ResponseEntity<MassiveDataS3Service.S3Status> getStatus() {
        return ResponseEntity.ok(s3Service.getStatus());
    }

    /**
     * List available files in S3 with a given prefix
     */
    @GetMapping("/files")
    public ResponseEntity<List<MassiveDataS3Service.S3FileInfo>> listFiles(
            @RequestParam(defaultValue = "us_stocks_sip/minute_aggs_v1/") String prefix) {
        List<MassiveDataS3Service.S3FileInfo> files = s3Service.listAvailableFiles(prefix);
        return ResponseEntity.ok(files);
    }

    /**
     * Import data for a specific ticker from a specific S3 file
     */
    @PostMapping("/import/{ticker}")
    public ResponseEntity<MassiveDataS3Service.ImportResult> importFromFile(
            @PathVariable String ticker,
            @RequestParam String s3Key) {

        log.info("Import request for {} from S3 key: {}", ticker, s3Key);
        MassiveDataS3Service.ImportResult result = s3Service.importFromS3(ticker.toUpperCase(), s3Key);

        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Import historical data for a ticker for a specific year/month
     */
    @PostMapping("/import/{ticker}/month")
    public ResponseEntity<Map<String, Object>> importMonth(
            @PathVariable String ticker,
            @RequestParam int year,
            @RequestParam int month) {

        log.info("Import historical data for {} - {}/{}", ticker, year, month);

        // Start async import
        s3Service.importHistoricalData(ticker.toUpperCase(), year, month);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "started");
        response.put("ticker", ticker.toUpperCase());
        response.put("year", year);
        response.put("month", month);
        response.put("message", "Historical import started. This may take several minutes.");

        return ResponseEntity.accepted().body(response);
    }

    /**
     * Bulk import historical data for multiple tickers over a date range
     * This is the main endpoint for loading years of historical data
     */
    @PostMapping("/import/bulk")
    public ResponseEntity<Map<String, Object>> bulkImport(
            @RequestBody BulkImportRequest request) {

        log.info("Bulk import request for {} tickers from {}/{} to {}/{}",
                request.getTickers().size(),
                request.getStartYear(), request.getStartMonth(),
                request.getEndYear(), request.getEndMonth());

        // Start async bulk import
        s3Service.bulkImportHistoricalData(
                request.getTickers(),
                request.getStartYear(),
                request.getStartMonth(),
                request.getEndYear(),
                request.getEndMonth()
        );

        Map<String, Object> response = new HashMap<>();
        response.put("status", "started");
        response.put("tickers", request.getTickers());
        response.put("dateRange", String.format("%d/%02d - %d/%02d",
                request.getStartYear(), request.getStartMonth(),
                request.getEndYear(), request.getEndMonth()));
        response.put("message", "Bulk historical import started. This may take a long time for multiple years of data.");

        return ResponseEntity.accepted().body(response);
    }

    /**
     * Import last N years of data for default tickers
     * Convenience endpoint for initial data population
     */
    @PostMapping("/import/defaults/years/{years}")
    public ResponseEntity<Map<String, Object>> importDefaultsHistorical(
            @PathVariable int years) {

        List<String> defaultTickers = List.of(
                "AAPL", "MSFT", "GOOGL", "AMZN", "META", "NVDA", "TSLA", "NFLX"
        );

        // Calculate date range
        java.time.LocalDate now = java.time.LocalDate.now();
        int endYear = now.getYear();
        int endMonth = now.getMonthValue();
        int startYear = endYear - years;
        int startMonth = endMonth;

        log.info("Starting {} year historical import for {} default tickers", years, defaultTickers.size());

        // Start async bulk import
        s3Service.bulkImportHistoricalData(defaultTickers, startYear, startMonth, endYear, endMonth);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "started");
        response.put("tickers", defaultTickers);
        response.put("years", years);
        response.put("dateRange", String.format("%d/%02d - %d/%02d", startYear, startMonth, endYear, endMonth));
        response.put("estimatedRecords", String.format("~%dM records (490K/ticker × %d tickers × %d years)",
                (490 * defaultTickers.size() * years) / 1000, defaultTickers.size(), years));
        response.put("message", "Bulk historical import started for " + years + " years of data. " +
                "This will take a long time. Monitor logs for progress.");

        return ResponseEntity.accepted().body(response);
    }

    /**
     * Request DTO for bulk import
     */
    @lombok.Data
    public static class BulkImportRequest {
        private List<String> tickers;
        private int startYear;
        private int startMonth;
        private int endYear;
        private int endMonth;
    }
}
