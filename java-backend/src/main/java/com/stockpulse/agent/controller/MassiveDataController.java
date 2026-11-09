package com.stockpulse.agent.controller;

import com.stockpulse.agent.service.MassiveDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for Massive Data API integration.
 * Provides endpoints for fetching intraday data and checking API status.
 */
@RestController
@RequestMapping("/massivedata")
@RequiredArgsConstructor
@Slf4j
public class MassiveDataController {

    private final MassiveDataService massiveDataService;

    /**
     * Get API status and availability
     */
    @GetMapping("/status")
    public ResponseEntity<MassiveDataService.ApiStatus> getStatus() {
        return ResponseEntity.ok(massiveDataService.getApiStatus());
    }

    /**
     * Fetch intraday data for a single ticker for today
     */
    @PostMapping("/fetch/{ticker}")
    public ResponseEntity<MassiveDataService.FetchResult> fetchTicker(@PathVariable String ticker) {
        log.info("Manual fetch request for ticker: {}", ticker);

        MassiveDataService.FetchResult result = massiveDataService.fetchIntradayDataToday(ticker.toUpperCase());

        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Fetch intraday data for a single ticker for a specific date
     */
    @PostMapping("/fetch/{ticker}/date/{date}")
    public ResponseEntity<MassiveDataService.FetchResult> fetchTickerForDate(
            @PathVariable String ticker,
            @PathVariable String date) {
        log.info("Fetch request for ticker: {} on date: {}", ticker, date);

        LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
        MassiveDataService.FetchResult result = massiveDataService.fetchIntradayData(ticker.toUpperCase(), localDate);

        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Fetch historical intraday data for a date range
     */
    @PostMapping("/fetch/{ticker}/range")
    public ResponseEntity<MassiveDataService.FetchResult> fetchHistorical(
            @PathVariable String ticker,
            @RequestParam String startDate,
            @RequestParam String endDate) {

        log.info("Historical fetch request for {} from {} to {}", ticker, startDate, endDate);

        LocalDate start = LocalDate.parse(startDate, DateTimeFormatter.ISO_LOCAL_DATE);
        LocalDate end = LocalDate.parse(endDate, DateTimeFormatter.ISO_LOCAL_DATE);

        MassiveDataService.FetchResult result = massiveDataService.fetchHistoricalIntraday(
            ticker.toUpperCase(), start, end
        );

        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Fetch last N days of data for a ticker
     */
    @PostMapping("/fetch/{ticker}/lastdays/{days}")
    public ResponseEntity<MassiveDataService.FetchResult> fetchLastDays(
            @PathVariable String ticker,
            @PathVariable int days) {

        log.info("Fetching last {} days of intraday data for {}", days, ticker);

        MassiveDataService.FetchResult result = massiveDataService.fetchLastNDays(ticker.toUpperCase(), days);

        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Fetch intraday data for multiple tickers (async)
     */
    @PostMapping("/fetch/batch")
    public ResponseEntity<Map<String, Object>> fetchBatch(
            @RequestBody List<String> tickers,
            @RequestParam(defaultValue = "today") String date) {

        log.info("Manual batch fetch request for {} tickers on {}", tickers.size(), date);

        LocalDate localDate = date.equals("today")
            ? LocalDate.now()
            : LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);

        // Start async fetch
        massiveDataService.fetchIntradayDataBatch(
            tickers.stream().map(String::toUpperCase).toList(),
            localDate
        );

        Map<String, Object> response = new HashMap<>();
        response.put("status", "started");
        response.put("tickers", tickers);
        response.put("date", localDate.toString());
        response.put("message", "Batch fetch started for " + tickers.size() + " tickers.");

        return ResponseEntity.accepted().body(response);
    }

    /**
     * Fetch today's intraday data for default tickers
     */
    @PostMapping("/fetch/defaults")
    public ResponseEntity<Map<String, Object>> fetchDefaults() {
        log.info("Fetching intraday data for default tickers");

        List<String> defaultTickers = List.of(
            "AAPL", "MSFT", "GOOGL", "AMZN", "META", "NVDA", "TSLA", "NFLX",
            "AMD", "INTC", "CRM", "ADBE", "PYPL", "QCOM", "AVGO", "ORCL"
        );

        massiveDataService.fetchIntradayDataBatch(defaultTickers, LocalDate.now());

        Map<String, Object> response = new HashMap<>();
        response.put("status", "started");
        response.put("tickers", defaultTickers);
        response.put("date", LocalDate.now().toString());
        response.put("message", "Fetching intraday data for " + defaultTickers.size() + " default tickers.");

        return ResponseEntity.accepted().body(response);
    }

    /**
     * Fetch last 5 days of historical data for default tickers
     * This is useful for initial data population
     */
    @PostMapping("/fetch/defaults/historical")
    public ResponseEntity<Map<String, Object>> fetchDefaultsHistorical(
            @RequestParam(defaultValue = "5") int days) {

        log.info("Fetching last {} days of historical data for default tickers", days);

        List<String> defaultTickers = List.of(
            "AAPL", "MSFT", "GOOGL", "AMZN", "META", "NVDA", "TSLA", "NFLX"
        );

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);

        // Fetch historical data for each ticker asynchronously
        for (String ticker : defaultTickers) {
            massiveDataService.fetchHistoricalIntraday(ticker, startDate, endDate);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("status", "started");
        response.put("tickers", defaultTickers);
        response.put("startDate", startDate.toString());
        response.put("endDate", endDate.toString());
        response.put("message", "Fetching " + days + " days of historical intraday data for " +
                    defaultTickers.size() + " tickers.");

        return ResponseEntity.accepted().body(response);
    }
}
