package com.stockpulse.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Finnhub REST API Service for historical stock data.
 * Uses Finnhub's /stock/candle endpoint for OHLC data.
 *
 * Free tier limits: 60 API calls/minute
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FinnhubDataService {

    private static final String FINNHUB_API_BASE = "https://finnhub.io/api/v1";

    @Value("${app.finnhub.api-key:}")
    private String apiKey;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    @Data
    @Builder
    public static class CandleData {
        private String ticker;
        private LocalDateTime date;
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private Long volume;
    }

    @Data
    @Builder
    public static class HistoricalDataResult {
        private String ticker;
        private int recordCount;
        private List<CandleData> candles;
        private String status;
        private String message;
    }

    /**
     * Check if Finnhub API is configured
     */
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }

    /**
     * Fetch historical daily candle data for a ticker
     *
     * @param ticker Stock symbol
     * @param fromDate Start date
     * @param toDate End date
     * @return HistoricalDataResult with candles or error message
     */
    public Optional<HistoricalDataResult> fetchHistoricalData(String ticker, LocalDateTime fromDate, LocalDateTime toDate) {
        if (!isConfigured()) {
            log.warn("Finnhub API key not configured");
            return Optional.empty();
        }

        try {
            // Convert LocalDateTime to Unix timestamps
            long fromTimestamp = fromDate.atZone(ZoneId.of("America/New_York")).toEpochSecond();
            long toTimestamp = toDate.atZone(ZoneId.of("America/New_York")).toEpochSecond();

            // Build URL for daily candles
            String url = String.format(
                "%s/stock/candle?symbol=%s&resolution=D&from=%d&to=%d&token=%s",
                FINNHUB_API_BASE, ticker.toUpperCase(), fromTimestamp, toTimestamp, apiKey
            );

            log.debug("Fetching Finnhub candle data for {} from {} to {}", ticker, fromDate, toDate);

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            // Check for error or no data
            String status = root.path("s").asText("ok");
            if ("no_data".equals(status)) {
                log.info("No Finnhub data available for {}", ticker);
                return Optional.of(HistoricalDataResult.builder()
                    .ticker(ticker)
                    .recordCount(0)
                    .candles(Collections.emptyList())
                    .status("no_data")
                    .message("No historical data available for this ticker")
                    .build());
            }

            // Parse candle arrays
            JsonNode closePrices = root.path("c");
            JsonNode highPrices = root.path("h");
            JsonNode lowPrices = root.path("l");
            JsonNode openPrices = root.path("o");
            JsonNode timestamps = root.path("t");
            JsonNode volumes = root.path("v");

            if (!closePrices.isArray() || closePrices.isEmpty()) {
                return Optional.of(HistoricalDataResult.builder()
                    .ticker(ticker)
                    .recordCount(0)
                    .candles(Collections.emptyList())
                    .status("no_data")
                    .message("Empty response from Finnhub")
                    .build());
            }

            List<CandleData> candles = new ArrayList<>();
            for (int i = 0; i < closePrices.size(); i++) {
                long timestamp = timestamps.get(i).asLong();
                LocalDateTime date = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(timestamp),
                    ZoneId.of("America/New_York")
                );

                CandleData candle = CandleData.builder()
                    .ticker(ticker)
                    .date(date)
                    .open(BigDecimal.valueOf(openPrices.get(i).asDouble()))
                    .high(BigDecimal.valueOf(highPrices.get(i).asDouble()))
                    .low(BigDecimal.valueOf(lowPrices.get(i).asDouble()))
                    .close(BigDecimal.valueOf(closePrices.get(i).asDouble()))
                    .volume(volumes.get(i).asLong())
                    .build();

                candles.add(candle);
            }

            log.info("Fetched {} candles for {} from Finnhub", candles.size(), ticker);

            return Optional.of(HistoricalDataResult.builder()
                .ticker(ticker)
                .recordCount(candles.size())
                .candles(candles)
                .status("success")
                .message("Data fetched successfully from Finnhub")
                .build());

        } catch (Exception e) {
            log.error("Error fetching Finnhub data for {}: {}", ticker, e.getMessage());
            return Optional.of(HistoricalDataResult.builder()
                .ticker(ticker)
                .recordCount(0)
                .candles(Collections.emptyList())
                .status("error")
                .message("Failed to fetch from Finnhub: " + e.getMessage())
                .build());
        }
    }

    /**
     * Fetch 2 years of historical data for a ticker
     */
    public Optional<HistoricalDataResult> fetchTwoYearsData(String ticker) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime twoYearsAgo = now.minusYears(2);
        return fetchHistoricalData(ticker, twoYearsAgo, now);
    }

    /**
     * Fetch one year of historical data for a specific year
     */
    public Optional<HistoricalDataResult> fetchYearData(String ticker, int year) {
        LocalDateTime startOfYear = LocalDateTime.of(year, 1, 1, 0, 0);
        LocalDateTime endOfYear = LocalDateTime.of(year, 12, 31, 23, 59);

        // Don't fetch future data
        LocalDateTime now = LocalDateTime.now();
        if (endOfYear.isAfter(now)) {
            endOfYear = now;
        }

        return fetchHistoricalData(ticker, startOfYear, endOfYear);
    }

    /**
     * Get current quote for a ticker
     */
    public Optional<Map<String, Object>> getQuote(String ticker) {
        if (!isConfigured()) {
            return Optional.empty();
        }

        try {
            String url = String.format("%s/quote?symbol=%s&token=%s",
                FINNHUB_API_BASE, ticker.toUpperCase(), apiKey);

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            if (root.path("c").asDouble() == 0) {
                return Optional.empty();
            }

            Map<String, Object> quote = new HashMap<>();
            quote.put("ticker", ticker);
            quote.put("currentPrice", root.path("c").asDouble());
            quote.put("change", root.path("d").asDouble());
            quote.put("changePercent", root.path("dp").asDouble());
            quote.put("high", root.path("h").asDouble());
            quote.put("low", root.path("l").asDouble());
            quote.put("open", root.path("o").asDouble());
            quote.put("previousClose", root.path("pc").asDouble());
            quote.put("timestamp", root.path("t").asLong());

            return Optional.of(quote);

        } catch (Exception e) {
            log.error("Error fetching Finnhub quote for {}: {}", ticker, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Get company profile
     */
    public Optional<Map<String, Object>> getCompanyProfile(String ticker) {
        if (!isConfigured()) {
            return Optional.empty();
        }

        try {
            String url = String.format("%s/stock/profile2?symbol=%s&token=%s",
                FINNHUB_API_BASE, ticker.toUpperCase(), apiKey);

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);

            if (root.path("name").asText("").isEmpty()) {
                return Optional.empty();
            }

            Map<String, Object> profile = new HashMap<>();
            profile.put("ticker", root.path("ticker").asText());
            profile.put("name", root.path("name").asText());
            profile.put("country", root.path("country").asText());
            profile.put("currency", root.path("currency").asText());
            profile.put("exchange", root.path("exchange").asText());
            profile.put("ipo", root.path("ipo").asText());
            profile.put("marketCap", root.path("marketCapitalization").asDouble());
            profile.put("shareOutstanding", root.path("shareOutstanding").asDouble());
            profile.put("industry", root.path("finnhubIndustry").asText());
            profile.put("logo", root.path("logo").asText());
            profile.put("weburl", root.path("weburl").asText());

            return Optional.of(profile);

        } catch (Exception e) {
            log.error("Error fetching Finnhub profile for {}: {}", ticker, e.getMessage());
            return Optional.empty();
        }
    }
}
