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
     * Fetch historical intraday data for a date range
     */
    @Transactional
    public FetchResult fetchHistoricalIntraday(String ticker, LocalDate startDate, LocalDate endDate) {
        if (!enabled || apiKey == null || apiKey.isEmpty()) {
            return FetchResult.builder()
                    .ticker(ticker)
                    .success(false)
                    .error("API not configured")
                    .build();
        }

        try {
            String startStr = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
            String endStr = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE);

            String url = String.format("%s/v2/aggs/ticker/%s/range/1/minute/%s/%s?adjusted=true&sort=asc&limit=50000&apiKey=%s",
                    baseUrl, ticker.toUpperCase(), startStr, endStr, apiKey);

            log.info("Fetching historical intraday data for {} from {} to {}", ticker, startStr, endStr);

            String response = restTemplate.getForObject(url, String.class);
            MassiveDataResponse data = objectMapper.readValue(response, MassiveDataResponse.class);

            if (data.getStatus() != null && data.getStatus().equals("ERROR")) {
                return FetchResult.builder()
                        .ticker(ticker)
                        .success(false)
                        .error(data.getError())
                        .build();
            }

            if (data.getResults() == null || data.getResults().isEmpty()) {
                return FetchResult.builder()
                        .ticker(ticker)
                        .success(false)
                        .error("No data returned for date range")
                        .build();
            }

            List<IntradayPrice> prices = new ArrayList<>();
            int duplicates = 0;
            ZoneId zoneId = ZoneId.of(timezone);

            for (MassiveDataBar bar : data.getResults()) {
                try {
                    LocalDateTime timestamp = LocalDateTime.ofInstant(
                            Instant.ofEpochMilli(bar.getT()), zoneId);

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
                    log.warn("Error parsing historical data point: {}", e.getMessage());
                }
            }

            if (!prices.isEmpty()) {
                intradayPriceRepository.saveAll(prices);
            }

            log.info("Saved {} historical intraday records for {} ({} duplicates skipped)",
                    prices.size(), ticker, duplicates);

            return FetchResult.builder()
                    .ticker(ticker)
                    .success(true)
                    .recordsSaved(prices.size())
                    .duplicatesSkipped(duplicates)
                    .build();

        } catch (Exception e) {
            log.error("Error fetching historical intraday data for {}: {}", ticker, e.getMessage());
            return FetchResult.builder()
                    .ticker(ticker)
                    .success(false)
                    .error(e.getMessage())
                    .build();
        }
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
