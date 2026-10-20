package com.stockpulse.agent.controller;

import com.stockpulse.agent.model.IntradayPrice;
import com.stockpulse.agent.repository.IntradayPriceRepository;
import com.stockpulse.agent.service.IntradayCsvImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/intraday")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class IntradayController {

    private final IntradayPriceRepository intradayPriceRepository;
    private final IntradayCsvImportService intradayCsvImportService;

    /**
     * Get intraday prices for a specific date
     * GET /api/v1/intraday/{ticker}?date=2024-01-02
     */
    @GetMapping("/{ticker}")
    public ResponseEntity<Map<String, Object>> getIntradayPrices(
            @PathVariable String ticker,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        ticker = ticker.toUpperCase();
        log.info("Getting intraday prices for ticker={}, date={}", ticker, date);

        LocalDateTime startTime;
        LocalDateTime endTime;

        if (date != null) {
            // Get data for specific date (market hours: 9:30 AM - 4:00 PM)
            startTime = date.atTime(LocalTime.of(9, 30));
            endTime = date.atTime(LocalTime.of(16, 0));
        } else {
            // Default to today
            LocalDate today = LocalDate.now();
            startTime = today.atTime(LocalTime.of(9, 30));
            endTime = today.atTime(LocalTime.of(16, 0));
        }

        List<IntradayPrice> prices = intradayPriceRepository.findByTickerAndTimestampBetween(
                ticker, startTime, endTime);

        Map<String, Object> response = new HashMap<>();
        response.put("ticker", ticker);
        response.put("date", date != null ? date : LocalDate.now());
        response.put("count", prices.size());
        response.put("prices", prices);

        return ResponseEntity.ok(response);
    }

    /**
     * Get intraday prices for a date range
     * GET /api/v1/intraday/{ticker}/range?start=2024-01-01T09:30:00&end=2024-01-02T16:00:00
     */
    @GetMapping("/{ticker}/range")
    public ResponseEntity<Map<String, Object>> getIntradayPricesRange(
            @PathVariable String ticker,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(defaultValue = "10000") int limit) {

        ticker = ticker.toUpperCase();
        log.info("Getting intraday prices range for ticker={}, start={}, end={}", ticker, start, end);

        List<IntradayPrice> prices = intradayPriceRepository.findByTickerAndTimestampBetween(
                ticker, start, end);

        // Apply limit
        if (prices.size() > limit) {
            prices = prices.subList(0, limit);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("ticker", ticker);
        response.put("startTime", start);
        response.put("endTime", end);
        response.put("count", prices.size());
        response.put("prices", prices);

        return ResponseEntity.ok(response);
    }

    /**
     * Get the most recent intraday prices
     * GET /api/v1/intraday/{ticker}/latest?limit=100
     */
    @GetMapping("/{ticker}/latest")
    public ResponseEntity<List<IntradayPrice>> getLatestIntradayPrices(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "100") int limit) {

        ticker = ticker.toUpperCase();
        log.info("Getting latest {} intraday prices for ticker={}", limit, ticker);

        List<IntradayPrice> prices = intradayPriceRepository.findLatestByTicker(ticker, limit);
        return ResponseEntity.ok(prices);
    }

    /**
     * Get available dates with intraday data
     * GET /api/v1/intraday/{ticker}/dates
     */
    @GetMapping("/{ticker}/dates")
    public ResponseEntity<Map<String, Object>> getAvailableDates(@PathVariable String ticker) {
        ticker = ticker.toUpperCase();
        log.info("Getting available intraday dates for ticker={}", ticker);

        List<java.sql.Date> dates = intradayPriceRepository.findDistinctDatesByTicker(ticker);
        IntradayCsvImportService.IntradayDataStats stats = intradayCsvImportService.getIntradayStats(ticker);

        Map<String, Object> response = new HashMap<>();
        response.put("ticker", ticker);
        response.put("dates", dates);
        response.put("totalDates", dates.size());
        response.put("startTime", stats.getStartTime());
        response.put("endTime", stats.getEndTime());
        response.put("totalRecords", stats.getTotalRecords());

        return ResponseEntity.ok(response);
    }

    /**
     * Get hourly aggregated data
     * GET /api/v1/intraday/{ticker}/hourly?date=2024-01-02
     */
    @GetMapping("/{ticker}/hourly")
    public ResponseEntity<Map<String, Object>> getHourlyData(
            @PathVariable String ticker,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        ticker = ticker.toUpperCase();
        log.info("Getting hourly aggregated data for ticker={}, date={}", ticker, date);

        LocalDateTime startTime = date.atTime(LocalTime.of(9, 30));
        LocalDateTime endTime = date.atTime(LocalTime.of(16, 0));

        List<Object[]> hourlyData = intradayPriceRepository.aggregateToHourly(ticker, startTime, endTime);

        Map<String, Object> response = new HashMap<>();
        response.put("ticker", ticker);
        response.put("date", date);
        response.put("count", hourlyData.size());
        response.put("hourlyData", hourlyData);

        return ResponseEntity.ok(response);
    }

    /**
     * Import intraday CSV data for a ticker
     * POST /api/v1/intraday/import/{ticker}
     */
    @PostMapping("/import/{ticker}")
    public ResponseEntity<Map<String, Object>> importIntradayCsv(
            @PathVariable String ticker,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean skipDuplicates) {

        ticker = ticker.toUpperCase();
        log.info("Importing intraday CSV for ticker={}, file={}, size={} bytes",
                ticker, file.getOriginalFilename(), file.getSize());

        try {
            IntradayCsvImportService.ImportResult result = intradayCsvImportService.importIntradayCsv(
                    ticker, file.getInputStream(), skipDuplicates);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("ticker", result.getTicker());
            response.put("recordsImported", result.getRecordsImported());
            response.put("recordsSkipped", result.getRecordsSkipped());
            response.put("duplicatesSkipped", result.getDuplicatesSkipped());
            response.put("startTime", result.getStartTime());
            response.put("endTime", result.getEndTime());
            response.put("processingTimeMs", result.getProcessingTimeMs());
            response.put("errors", result.getErrors());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error importing intraday CSV for ticker={}", ticker, e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("ticker", ticker);
            response.put("error", e.getMessage());

            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Clear all intraday data for a ticker
     * DELETE /api/v1/intraday/{ticker}
     */
    @DeleteMapping("/{ticker}")
    public ResponseEntity<Map<String, Object>> clearIntradayData(@PathVariable String ticker) {
        ticker = ticker.toUpperCase();
        log.info("Clearing all intraday data for ticker={}", ticker);

        int deleted = intradayCsvImportService.clearIntradayData(ticker);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("ticker", ticker);
        response.put("recordsDeleted", deleted);

        return ResponseEntity.ok(response);
    }

    /**
     * Get list of tickers with intraday data
     * GET /api/v1/intraday/tickers
     */
    @GetMapping("/tickers")
    public ResponseEntity<List<String>> getTickersWithIntradayData() {
        log.info("Getting list of tickers with intraday data");
        List<String> tickers = intradayPriceRepository.findDistinctTickers();
        return ResponseEntity.ok(tickers);
    }

    /**
     * Get intraday data statistics for a ticker
     * GET /api/v1/intraday/{ticker}/stats
     */
    @GetMapping("/{ticker}/stats")
    public ResponseEntity<IntradayCsvImportService.IntradayDataStats> getIntradayStats(
            @PathVariable String ticker) {
        ticker = ticker.toUpperCase();
        log.info("Getting intraday stats for ticker={}", ticker);

        IntradayCsvImportService.IntradayDataStats stats = intradayCsvImportService.getIntradayStats(ticker);
        return ResponseEntity.ok(stats);
    }
}
