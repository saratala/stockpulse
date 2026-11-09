package com.stockpulse.agent.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpulse.agent.model.IntradayPrice;
import com.stockpulse.agent.repository.IntradayPriceRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for fetching intraday stock data from Massive Data API.
 * API: https://api.massive.com/v2/aggs/ticker/{ticker}/range/{multiplier}/{timespan}/{from}/{to}
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MassiveDataService {

    private final IntradayPriceRepository intradayPriceRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.massivedata.api-key:}")
    private String apiKey;

    @Value("${app.massivedata.base-url:https://api.massive.com}")
    private String baseUrl;

    @Value("${app.massivedata.enabled:false}")
    private boolean enabled;

    @Value("${app.massivedata.rate-limit.delay-ms:250}")
    private long rateLimitDelayMs;

    @Value("${app.massivedata.timezone:America/New_York}")
    private String timezone;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Fetch intraday data for a single ticker for a specific date
     */
    @Transactional
    public FetchResult fetchIntradayData(String ticker, LocalDate date) {
        if (!enabled || apiKey == null || apiKey.isEmpty()) {
            log.warn("Massive Data API is not enabled or API key is missing");
            return FetchResult.builder()
                    .ticker(ticker)
                    .success(false)
                    .error("API not configured")
                    .build();
        }

        try {
            String dateStr = date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            String url = String.format("%s/v2/aggs/ticker/%s/range/1/minute/%s/%s?adjusted=true&sort=asc&limit=50000&apiKey=%s",
                    baseUrl, ticker.toUpperCase(), dateStr, dateStr, apiKey);

            log.info("Fetching intraday data for {} on {} from Massive Data", ticker, dateStr);

            String response = restTemplate.getForObject(url, String.class);
            MassiveDataResponse data = objectMapper.readValue(response, MassiveDataResponse.class);

            if (data.getStatus() != null && data.getStatus().equals("ERROR")) {
                log.error("Massive Data API error for {}: {}", ticker, data.getError());
                return FetchResult.builder()
                        .ticker(ticker)
                        .success(false)
                        .error(data.getError())
                        .build();
            }

            if (data.getResults() == null || data.getResults().isEmpty()) {
                log.warn("No intraday data returned for {} on {}", ticker, dateStr);
                return FetchResult.builder()
                        .ticker(ticker)
                        .success(false)
                        .error("No data returned")
                        .build();
            }

            // Convert and save data
            List<IntradayPrice> prices = new ArrayList<>();
            int duplicates = 0;
            ZoneId zoneId = ZoneId.of(timezone);

            for (MassiveDataBar bar : data.getResults()) {
                try {
                    // Convert Unix milliseconds to LocalDateTime
                    LocalDateTime timestamp = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(bar.getT()), zoneId);

                    // Check for duplicates
                    if (intradayPriceRepository.existsByTickerAndTimestamp(ticker.toUpperCase(), timestamp)) {
                        duplicates++;
                        continue;
                    }

                    IntradayPrice price = IntradayPrice.builder()
                            .ticker(ticker.toUpperCase())
                            .timestamp(timestamp)
                            .open(BigDecimal.valueOf(bar.getO()))
                            .high(BigDecimal.valueOf(bar.getH()))
                            .low(BigDecimal.valueOf(bar.getL()))
                            .close(BigDecimal.valueOf(bar.getC()))
                            .volume(bar.getV())
                            .build();

                    prices.add(price);
                } catch (Exception e) {
                    log.warn("Error parsing data point for {}: {}", ticker, e.getMessage());
                }
            }

            if (!prices.isEmpty()) {
                intradayPriceRepository.saveAll(prices);
                log.info("Saved {} intraday records for {} on {} ({} duplicates skipped)",
                        prices.size(), ticker, dateStr, duplicates);
            }

            return FetchResult.builder()
                    .ticker(ticker)
                    .success(true)
                    .recordsSaved(prices.size())
                    .duplicatesSkipped(duplicates)
                    .build();

        } catch (Exception e) {
            log.error("Error fetching intraday data for {}: {}", ticker, e.getMessage());
            return FetchResult.builder()
                    .ticker(ticker)
                    .success(false)
                    .error(e.getMessage())
                    .build();
        }
    }

    /**
     * Fetch intraday data for today
     */
    @Transactional
    public FetchResult fetchIntradayDataToday(String ticker) {
        return fetchIntradayData(ticker, LocalDate.now(ZoneId.of(timezone)));
    }

    /**
     * Fetch historical intraday data for a date range. Not transactional: each
     * window commits on its own so a late failure keeps earlier windows.
     */
    public FetchResult fetchHistoricalIntraday(String ticker, LocalDate startDate, LocalDate endDate) {
        if (!enabled || apiKey == null || apiKey.isEmpty()) {
            return FetchResult.builder()
                    .ticker(ticker)
                    .success(false)
                    .error("API not configured")
                    .build();
        }

        // One request returns at most 50,000 bars (~50 trading days incl. extended hours),
        // so walk the range in 30-day windows to avoid silently truncating it.
        String symbol = ticker.toUpperCase();
        int saved = 0;
        int duplicates = 0;
        List<String> errors = new ArrayList<>();

        for (LocalDate from = startDate; !from.isAfter(endDate); from = from.plusDays(30)) {
            LocalDate to = from.plusDays(29).isAfter(endDate) ? endDate : from.plusDays(29);
            if (from.isAfter(startDate)) {
                try {
                    Thread.sleep(rateLimitDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    errors.add("interrupted");
                    break;
                }
            }

            try {
                String url = String.format("%s/v2/aggs/ticker/%s/range/1/minute/%s/%s?adjusted=true&sort=asc&limit=50000&apiKey=%s",
                        baseUrl, symbol, from, to, apiKey);
                log.info("Fetching historical intraday data for {} from {} to {}", symbol, from, to);

                MassiveDataResponse data = objectMapper.readValue(
                        restTemplate.getForObject(url, String.class), MassiveDataResponse.class);

                if ("ERROR".equals(data.getStatus())) {
                    errors.add(from + ".." + to + ": " + data.getError());
                    continue;
                }
                if (data.getResults() == null || data.getResults().isEmpty()) {
                    continue;
                }

                int[] written = insertBars(symbol, data.getResults());
                saved += written[0];
                duplicates += written[1];
            } catch (Exception e) {
                // A plan's history limit comes back as HTTP 403; keep going with later windows
                log.warn("Historical fetch for {} {}..{} failed: {}", symbol, from, to, e.getMessage());
                errors.add(from + ".." + to + ": " + e.getMessage());
            }
        }

        log.info("Saved {} historical intraday records for {} ({} duplicates skipped, {} windows failed)",
                saved, symbol, duplicates, errors.size());

        return FetchResult.builder()
                .ticker(symbol)
                .success(saved + duplicates > 0)
                .recordsSaved(saved)
                .duplicatesSkipped(duplicates)
                .error(errors.isEmpty() ? null : String.join("; ", errors))
                .build();
    }

    /**
     * Batch insert bars, letting the primary key reject duplicates. Timestamps are
     * epoch millis converted in SQL, so no JVM/Eastern time zone handling is involved.
     *
     * @return {saved, duplicatesOrInvalid}
     */
    private int[] insertBars(String ticker, List<MassiveDataBar> bars) {
        List<MassiveDataBar> valid = bars.stream()
                .filter(b -> b.getH() >= b.getL() && b.getH() >= b.getO() && b.getH() >= b.getC()
                        && b.getL() <= b.getO() && b.getL() <= b.getC() && b.getV() >= 0)
                .toList();

        int[][] counts = jdbcTemplate.batchUpdate("""
                INSERT INTO stock_prices_intraday (ticker, timestamp, open, high, low, close, volume)
                VALUES (?, to_timestamp(? / 1000.0), ?, ?, ?, ?, ?)
                ON CONFLICT (ticker, timestamp) DO NOTHING
                """, valid, 5000, (ps, b) -> {
                    ps.setString(1, ticker);
                    ps.setLong(2, b.getT());
                    ps.setDouble(3, b.getO());
                    ps.setDouble(4, b.getH());
                    ps.setDouble(5, b.getL());
                    ps.setDouble(6, b.getC());
                    ps.setLong(7, b.getV());
                });

        int saved = 0;
        for (int[] batch : counts) {
            for (int c : batch) {
                if (c > 0) saved += c;
            }
        }
        return new int[]{saved, bars.size() - saved};
    }

    /**
     * Fetch intraday data for multiple tickers with rate limiting
     */
    @Async
    public CompletableFuture<List<FetchResult>> fetchIntradayDataBatch(List<String> tickers, LocalDate date) {
        List<FetchResult> results = new ArrayList<>();

        for (int i = 0; i < tickers.size(); i++) {
            String ticker = tickers.get(i);

            FetchResult result = fetchIntradayData(ticker, date);
            results.add(result);

            // Rate limiting - wait between requests
            if (i < tickers.size() - 1) {
                try {
                    log.debug("Rate limiting: waiting {}ms before next request", rateLimitDelayMs);
                    Thread.sleep(rateLimitDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return CompletableFuture.completedFuture(results);
    }

    /**
     * Fetch last N days of historical data for a ticker
     */
    @Transactional
    public FetchResult fetchLastNDays(String ticker, int days) {
        LocalDate endDate = LocalDate.now(ZoneId.of(timezone));
        LocalDate startDate = endDate.minusDays(days);
        return fetchHistoricalIntraday(ticker, startDate, endDate);
    }

    /**
     * Check if API is configured and working
     */
    public boolean isApiAvailable() {
        if (!enabled || apiKey == null || apiKey.isEmpty()) {
            return false;
        }

        try {
            // Quick test with a simple quote request
            String url = String.format("%s/v2/aggs/ticker/AAPL/range/1/minute/2024-12-20/2024-12-20?limit=1&apiKey=%s",
                    baseUrl, apiKey);
            String response = restTemplate.getForObject(url, String.class);
            return response != null && !response.contains("\"status\":\"ERROR\"");
        } catch (Exception e) {
            log.warn("API availability check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get API status information
     */
    public ApiStatus getApiStatus() {
        return ApiStatus.builder()
                .enabled(enabled)
                .apiKeyConfigured(apiKey != null && !apiKey.isEmpty())
                .available(isApiAvailable())
                .build();
    }

    // ============ Response DTOs ============

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MassiveDataResponse {
        private String ticker;
        private String status;
        private String error;
        private Boolean adjusted;
        private Integer queryCount;
        private Integer resultsCount;
        @JsonProperty("request_id")
        private String requestId;
        private List<MassiveDataBar> results;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MassiveDataBar {
        private long t;   // Unix timestamp in milliseconds
        private double o; // Open
        private double h; // High
        private double l; // Low
        private double c; // Close
        private long v;   // Volume
        private double vw; // Volume-weighted average price
        private int n;    // Number of transactions
    }

    @Data
    @lombok.Builder
    public static class FetchResult {
        private String ticker;
        private boolean success;
        private int recordsSaved;
        private int duplicatesSkipped;
        private String error;
    }

    @Data
    @lombok.Builder
    public static class ApiStatus {
        private boolean enabled;
        private boolean apiKeyConfigured;
        private boolean available;
    }
}
