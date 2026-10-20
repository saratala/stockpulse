package com.stockpulse.agent.controller;

import com.stockpulse.agent.scheduler.IntradayDataScheduler;
import com.stockpulse.agent.service.TwelveDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for Twelve Data API integration.
 * Provides endpoints for fetching intraday data and checking API status.
 */
@RestController
@RequestMapping("/twelvedata")
@RequiredArgsConstructor
@Slf4j
public class TwelveDataController {

    private final TwelveDataService twelveDataService;
    private final IntradayDataScheduler intradayDataScheduler;

    /**
     * Get API status and availability
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();

        boolean apiAvailable = twelveDataService.isApiAvailable();
        TwelveDataService.ApiUsageInfo usage = twelveDataService.getApiUsage();
        IntradayDataScheduler.SchedulerStatus schedulerStatus = intradayDataScheduler.getStatus();

        status.put("apiAvailable", apiAvailable);
        status.put("apiUsage", usage);
        status.put("scheduler", schedulerStatus);

        return ResponseEntity.ok(status);
    }

    /**
     * Fetch intraday data for a single ticker
     */
    @PostMapping("/fetch/{ticker}")
    public ResponseEntity<TwelveDataService.FetchResult> fetchTicker(@PathVariable String ticker) {
        log.info("Manual fetch request for ticker: {}", ticker);

        TwelveDataService.FetchResult result = twelveDataService.fetchIntradayData(ticker.toUpperCase());

        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Fetch intraday data for multiple tickers (async with rate limiting)
     */
    @PostMapping("/fetch/batch")
    public ResponseEntity<Map<String, Object>> fetchBatch(@RequestBody List<String> tickers) {
        log.info("Manual batch fetch request for {} tickers: {}", tickers.size(), tickers);

        // Start async fetch (fire and forget)
        twelveDataService.fetchIntradayDataBatch(
            tickers.stream().map(String::toUpperCase).toList()
        );

        Map<String, Object> response = new HashMap<>();
        response.put("status", "started");
        response.put("tickers", tickers);
        response.put("message", "Batch fetch started. Due to rate limiting, this will take approximately " +
                    (tickers.size() * 8) + " seconds.");

        return ResponseEntity.accepted().body(response);
    }

    /**
     * Fetch historical intraday data for a date range
     * Note: Free tier limited to last 30 days
     */
    @PostMapping("/fetch/{ticker}/historical")
    public ResponseEntity<TwelveDataService.FetchResult> fetchHistorical(
            @PathVariable String ticker,
            @RequestParam String startDate,
            @RequestParam String endDate) {

        log.info("Historical fetch request for {} from {} to {}", ticker, startDate, endDate);

        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        TwelveDataService.FetchResult result = twelveDataService.fetchHistoricalIntraday(
            ticker.toUpperCase(), start, end
        );

        if (result.isSuccess()) {
            return ResponseEntity.ok(result);
        } else {
            return ResponseEntity.badRequest().body(result);
        }
    }

    /**
     * Trigger the scheduler to run immediately
     */
    @PostMapping("/scheduler/run")
    public ResponseEntity<Map<String, Object>> triggerScheduler() {
        log.info("Manual scheduler trigger requested");

        Map<String, Object> response = new HashMap<>();

        if (intradayDataScheduler.isRunning()) {
            response.put("status", "already_running");
            response.put("message", "Scheduler is already running");
            return ResponseEntity.ok(response);
        }

        // Run scheduler in background
        CompletableFuture.runAsync(() -> intradayDataScheduler.fetchIntradayData());

        response.put("status", "started");
        response.put("message", "Scheduler run triggered");

        return ResponseEntity.accepted().body(response);
    }

    /**
     * Get scheduler status
     */
    @GetMapping("/scheduler/status")
    public ResponseEntity<IntradayDataScheduler.SchedulerStatus> getSchedulerStatus() {
        return ResponseEntity.ok(intradayDataScheduler.getStatus());
    }

    /**
     * Fetch today's intraday data for default tickers
     * This is useful for quickly populating data for the main stocks
     */
    @PostMapping("/fetch/defaults")
    public ResponseEntity<Map<String, Object>> fetchDefaults() {
        log.info("Fetching intraday data for default tickers");

        List<String> defaultTickers = List.of(
            "AAPL", "MSFT", "GOOGL", "AMZN", "META", "NVDA", "TSLA", "NFLX"
        );

        // Start async fetch (fire and forget)
        twelveDataService.fetchIntradayDataBatch(defaultTickers);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "started");
        response.put("tickers", defaultTickers);
        response.put("message", "Fetching intraday data for " + defaultTickers.size() +
                    " default tickers. This will take approximately " +
                    (defaultTickers.size() * 8) + " seconds due to rate limiting.");

        return ResponseEntity.accepted().body(response);
    }
}
