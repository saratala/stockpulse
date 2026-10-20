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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Service for fetching intraday stock data from Twelve Data API.
 * Free tier: 800 API calls/day, 8 calls/minute
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TwelveDataService {

    private final IntradayPriceRepository intradayPriceRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.twelvedata.api-key:}")
    private String apiKey;

    @Value("${app.twelvedata.base-url:https://api.twelvedata.com}")
    private String baseUrl;

    @Value("${app.twelvedata.enabled:false}")
    private boolean enabled;

    @Value("${app.twelvedata.rate-limit.delay-ms:8000}")
    private long rateLimitDelayMs;

    @Value("${app.twelvedata.intraday.interval:1min}")
    private String interval;

    @Value("${app.twelvedata.intraday.outputsize:390}")
    private int outputSize;

    @Value("${app.twelvedata.intraday.timezone:America/New_York}")
    private String timezone;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final DateTimeFormatter DATETIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Fetch intraday data for a single ticker
     */
    @Transactional
    public FetchResult fetchIntradayData(String ticker) {
        if (!enabled || apiKey == null || apiKey.isEmpty()) {
            log.warn("Twelve Data API is not enabled or API key is missing");
            return FetchResult.builder()
                    .ticker(ticker)
                    .success(false)
                    .error("API not configured")
                    .build();
        }

        try {
            String url = String.format("%s/time_series?symbol=%s&interval=%s&outputsize=%d&timezone=%s&apikey=%s",
                    baseUrl, ticker, interval, outputSize, timezone, apiKey);

            log.info("Fetching intraday data for {} from Twelve Data", ticker);

            String response = restTemplate.getForObject(url, String.class);
            TwelveDataResponse data = objectMapper.readValue(response, TwelveDataResponse.class);

            if (data.getStatus() != null && data.getStatus().equals("error")) {
                log.error("Twelve Data API error for {}: {}", ticker, data.getMessage());
                return FetchResult.builder()
                        .ticker(ticker)
                        .success(false)
                        .error(data.getMessage())
                        .build();
            }

            if (data.getValues() == null || data.getValues().isEmpty()) {
                log.warn("No intraday data returned for {}", ticker);
                return FetchResult.builder()
                        .ticker(ticker)
                        .success(false)
                        .error("No data returned")
                        .build();
            }

            // Convert and save data
            List<IntradayPrice> prices = new ArrayList<>();
            int duplicates = 0;

            for (TwelveDataValue value : data.getValues()) {
                try {
                    LocalDateTime timestamp = LocalDateTime.parse(value.getDatetime(), DATETIME_FORMAT);

                    // Check for duplicates
                    if (intradayPriceRepository.existsByTickerAndTimestamp(ticker, timestamp)) {
                        duplicates++;
                        continue;
                    }

                    IntradayPrice price = IntradayPrice.builder()
                            .ticker(ticker)
                            .timestamp(timestamp)
                            .open(new BigDecimal(value.getOpen()))
                            .high(new BigDecimal(value.getHigh()))
                            .low(new BigDecimal(value.getLow()))
                            .close(new BigDecimal(value.getClose()))
                            .volume(Long.parseLong(value.getVolume()))
                            .build();

                    prices.add(price);
                } catch (Exception e) {
                    log.warn("Error parsing data point for {}: {}", ticker, e.getMessage());
                }
            }

            if (!prices.isEmpty()) {
                intradayPriceRepository.saveAll(prices);
                log.info("Saved {} intraday records for {} ({} duplicates skipped)",
                        prices.size(), ticker, duplicates);
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
     * Fetch intraday data for multiple tickers with rate limiting
     */
    @Async
    public CompletableFuture<List<FetchResult>> fetchIntradayDataBatch(List<String> tickers) {
        List<FetchResult> results = new ArrayList<>();

        for (int i = 0; i < tickers.size(); i++) {
            String ticker = tickers.get(i);

            FetchResult result = fetchIntradayData(ticker);
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
     * Fetch historical intraday data for a specific date range
     * Note: Free tier only provides last 30 days of intraday data
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
            String url = String.format(
                    "%s/time_series?symbol=%s&interval=%s&start_date=%s&end_date=%s&timezone=%s&apikey=%s",
                    baseUrl, ticker, interval,
                    startDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    endDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    timezone, apiKey);

            log.info("Fetching historical intraday data for {} from {} to {}", ticker, startDate, endDate);

            String response = restTemplate.getForObject(url, String.class);
            TwelveDataResponse data = objectMapper.readValue(response, TwelveDataResponse.class);

            if (data.getStatus() != null && data.getStatus().equals("error")) {
                return FetchResult.builder()
                        .ticker(ticker)
                        .success(false)
                        .error(data.getMessage())
                        .build();
            }

            if (data.getValues() == null || data.getValues().isEmpty()) {
                return FetchResult.builder()
                        .ticker(ticker)
                        .success(false)
                        .error("No data returned for date range")
                        .build();
            }

            List<IntradayPrice> prices = new ArrayList<>();
            int duplicates = 0;

            for (TwelveDataValue value : data.getValues()) {
                try {
                    LocalDateTime timestamp = LocalDateTime.parse(value.getDatetime(), DATETIME_FORMAT);

                    if (intradayPriceRepository.existsByTickerAndTimestamp(ticker, timestamp)) {
                        duplicates++;
                        continue;
                    }

                    IntradayPrice price = IntradayPrice.builder()
                            .ticker(ticker)
                            .timestamp(timestamp)
                            .open(new BigDecimal(value.getOpen()))
                            .high(new BigDecimal(value.getHigh()))
                            .low(new BigDecimal(value.getLow()))
                            .close(new BigDecimal(value.getClose()))
                            .volume(Long.parseLong(value.getVolume()))
                            .build();

                    prices.add(price);
                } catch (Exception e) {
                    log.warn("Error parsing historical data point: {}", e.getMessage());
                }
            }

            if (!prices.isEmpty()) {
                intradayPriceRepository.saveAll(prices);
            }

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
     * Get API usage/quota information
     */
    public ApiUsageInfo getApiUsage() {
        if (!enabled || apiKey == null || apiKey.isEmpty()) {
            return ApiUsageInfo.builder()
                    .enabled(false)
                    .error("API not configured")
                    .build();
        }

        try {
            String url = String.format("%s/api_usage?apikey=%s", baseUrl, apiKey);
            String response = restTemplate.getForObject(url, String.class);

            // Parse response for usage info
            log.debug("API usage response: {}", response);

            return ApiUsageInfo.builder()
                    .enabled(true)
                    .rawResponse(response)
                    .build();

        } catch (Exception e) {
            return ApiUsageInfo.builder()
                    .enabled(true)
                    .error(e.getMessage())
                    .build();
        }
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
            String url = String.format("%s/quote?symbol=AAPL&apikey=%s", baseUrl, apiKey);
            String response = restTemplate.getForObject(url, String.class);
            return response != null && !response.contains("\"code\":4");
        } catch (Exception e) {
            log.warn("API availability check failed: {}", e.getMessage());
            return false;
        }
    }

    // ============ Response DTOs ============

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TwelveDataResponse {
        private Meta meta;
        private List<TwelveDataValue> values;
        private String status;
        private String message;
        private Integer code;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Meta {
        private String symbol;
        private String interval;
        private String currency;
        @JsonProperty("exchange_timezone")
        private String exchangeTimezone;
        private String exchange;
        private String type;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TwelveDataValue {
        private String datetime;
        private String open;
        private String high;
        private String low;
        private String close;
        private String volume;
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
    public static class ApiUsageInfo {
        private boolean enabled;
        private String rawResponse;
        private String error;
    }
}
