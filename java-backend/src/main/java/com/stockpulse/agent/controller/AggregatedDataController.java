package com.stockpulse.agent.controller;

import com.stockpulse.agent.scheduler.HistoricalDataFetcherScheduler;
import com.stockpulse.agent.service.IntradayAggregationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for aggregated intraday data (5min, 15min, hourly intervals).
 * Provides endpoints for charting and analysis with reduced data volume.
 */
@RestController
@RequestMapping("/intraday/aggregated")
@RequiredArgsConstructor
@Slf4j
public class AggregatedDataController {

    private final IntradayAggregationService aggregationService;
    private final HistoricalDataFetcherScheduler historicalFetcher;

    /**
     * Get 15-minute bars for a ticker on a date
     */
    @GetMapping("/{ticker}/15min")
    public ResponseEntity<Map<String, Object>> get15MinBars(
            @PathVariable String ticker,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate targetDate = date != null ? date : LocalDate.now();

        List<IntradayAggregationService.AggregatedBar> bars =
                aggregationService.get15MinuteBars(ticker.toUpperCase(), targetDate);

        Map<String, Object> response = new HashMap<>();
        response.put("ticker", ticker.toUpperCase());
        response.put("date", targetDate.toString());
        response.put("interval", "15min");
        response.put("count", bars.size());
        response.put("bars", bars.stream().map(IntradayAggregationService.AggregatedBar::toMap).collect(Collectors.toList()));

        return ResponseEntity.ok(response);
    }

    /**
     * Get 5-minute bars for a ticker on a date
     */
    @GetMapping("/{ticker}/5min")
    public ResponseEntity<Map<String, Object>> get5MinBars(
            @PathVariable String ticker,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate targetDate = date != null ? date : LocalDate.now();

        List<IntradayAggregationService.AggregatedBar> bars =
                aggregationService.get5MinuteBars(ticker.toUpperCase(), targetDate);

        Map<String, Object> response = new HashMap<>();
        response.put("ticker", ticker.toUpperCase());
        response.put("date", targetDate.toString());
        response.put("interval", "5min");
        response.put("count", bars.size());
        response.put("bars", bars.stream().map(IntradayAggregationService.AggregatedBar::toMap).collect(Collectors.toList()));

        return ResponseEntity.ok(response);
    }

    /**
     * Get hourly bars for a ticker on a date
     */
    @GetMapping("/{ticker}/hourly")
    public ResponseEntity<Map<String, Object>> getHourlyBars(
            @PathVariable String ticker,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        LocalDate targetDate = date != null ? date : LocalDate.now();

        List<IntradayAggregationService.AggregatedBar> bars =
                aggregationService.getHourlyBars(ticker.toUpperCase(), targetDate);

        Map<String, Object> response = new HashMap<>();
        response.put("ticker", ticker.toUpperCase());
        response.put("date", targetDate.toString());
        response.put("interval", "60min");
        response.put("count", bars.size());
        response.put("bars", bars.stream().map(IntradayAggregationService.AggregatedBar::toMap).collect(Collectors.toList()));

        return ResponseEntity.ok(response);
    }

    /**
     * Get aggregated bars for a custom interval
     */
    @GetMapping("/{ticker}/custom")
    public ResponseEntity<Map<String, Object>> getCustomIntervalBars(
            @PathVariable String ticker,
            @RequestParam int intervalMinutes,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        if (intervalMinutes < 1 || intervalMinutes > 60) {
            return ResponseEntity.badRequest().body(Map.of("error", "Interval must be between 1 and 60 minutes"));
        }

        LocalDate targetDate = date != null ? date : LocalDate.now();

        List<IntradayAggregationService.AggregatedBar> bars =
                aggregationService.aggregateToInterval(ticker.toUpperCase(), targetDate, intervalMinutes);

        Map<String, Object> response = new HashMap<>();
        response.put("ticker", ticker.toUpperCase());
        response.put("date", targetDate.toString());
        response.put("interval", intervalMinutes + "min");
        response.put("count", bars.size());
        response.put("bars", bars.stream().map(IntradayAggregationService.AggregatedBar::toMap).collect(Collectors.toList()));

        return ResponseEntity.ok(response);
    }

    /**
     * Get aggregated bars for a date range
     */
    @GetMapping("/{ticker}/range")
    public ResponseEntity<Map<String, Object>> getDateRangeBars(
            @PathVariable String ticker,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "15") int intervalMinutes) {

        if (intervalMinutes < 1 || intervalMinutes > 60) {
            return ResponseEntity.badRequest().body(Map.of("error", "Interval must be between 1 and 60 minutes"));
        }

        List<IntradayAggregationService.AggregatedBar> bars =
                aggregationService.aggregateForDateRange(ticker.toUpperCase(), startDate, endDate, intervalMinutes);

        Map<String, Object> response = new HashMap<>();
        response.put("ticker", ticker.toUpperCase());
        response.put("startDate", startDate.toString());
        response.put("endDate", endDate.toString());
        response.put("interval", intervalMinutes + "min");
        response.put("count", bars.size());
        response.put("bars", bars.stream().map(IntradayAggregationService.AggregatedBar::toMap).collect(Collectors.toList()));

        return ResponseEntity.ok(response);
    }

    /**
     * Get latest N bars for a ticker
     */
    @GetMapping("/{ticker}/latest")
    public ResponseEntity<Map<String, Object>> getLatestBars(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "15") int intervalMinutes,
            @RequestParam(defaultValue = "26") int count) {

        List<IntradayAggregationService.AggregatedBar> bars =
                aggregationService.getLatestBars(ticker.toUpperCase(), intervalMinutes, count);

        Map<String, Object> response = new HashMap<>();
        response.put("ticker", ticker.toUpperCase());
        response.put("interval", intervalMinutes + "min");
        response.put("count", bars.size());
        response.put("bars", bars.stream().map(IntradayAggregationService.AggregatedBar::toMap).collect(Collectors.toList()));

        return ResponseEntity.ok(response);
    }

    /**
     * Get data availability for a ticker
     */
    @GetMapping("/{ticker}/availability")
    public ResponseEntity<IntradayAggregationService.DataAvailability> getDataAvailability(
            @PathVariable String ticker) {

        IntradayAggregationService.DataAvailability availability =
                aggregationService.getDataAvailability(ticker.toUpperCase());

        return ResponseEntity.ok(availability);
    }

    /**
     * Get historical data fetch status
     */
    @GetMapping("/fetch/status")
    public ResponseEntity<HistoricalDataFetcherScheduler.FetchStatus> getFetchStatus() {
        return ResponseEntity.ok(historicalFetcher.getStatus());
    }

    /**
     * Trigger historical data fetch
     */
    @PostMapping("/fetch/trigger")
    public ResponseEntity<Map<String, String>> triggerFetch() {
        historicalFetcher.triggerFetch();
        return ResponseEntity.accepted().body(Map.of(
                "status", "triggered",
                "message", "Historical data fetch triggered"
        ));
    }

    /**
     * Reset historical fetch progress
     */
    @PostMapping("/fetch/reset")
    public ResponseEntity<Map<String, String>> resetFetch() {
        historicalFetcher.resetProgress();
        return ResponseEntity.ok(Map.of(
                "status", "reset",
                "message", "Historical fetch progress reset"
        ));
    }
}
