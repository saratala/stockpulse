package com.stockpulse.agent.service;

import com.stockpulse.agent.model.IntradayPrice;
import com.stockpulse.agent.repository.IntradayPriceRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

/**
 * Service for fetching bulk historical stock data from Massive Data S3 storage.
 * Uses S3-compatible API for efficient download of large historical datasets.
 *
 * Strategy: Use S3 for historical data (past years), API for recent data (last few days)
 */
@Service
@Slf4j
public class MassiveDataS3Service {

    private final IntradayPriceRepository intradayPriceRepository;

    @Value("${app.massivedata.s3.access-key-id:}")
    private String accessKeyId;

    @Value("${app.massivedata.s3.secret-access-key:}")
    private String secretAccessKey;

    @Value("${app.massivedata.s3.endpoint:https://files.massive.com}")
    private String s3Endpoint;

    @Value("${app.massivedata.s3.bucket:flatfiles}")
    private String bucketName;

    @Value("${app.massivedata.s3.enabled:true}")
    private boolean enabled;

    @Value("${app.massivedata.timezone:America/New_York}")
    private String timezone;

    private S3Client s3Client;

    public MassiveDataS3Service(IntradayPriceRepository intradayPriceRepository) {
        this.intradayPriceRepository = intradayPriceRepository;
    }

    @PostConstruct
    public void init() {
        if (enabled && accessKeyId != null && !accessKeyId.isEmpty()) {
            try {
                AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey);

                // Configure S3 with path-style access and chunked encoding disabled
                S3Configuration s3Config = S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build();

                this.s3Client = S3Client.builder()
                        .region(Region.US_EAST_1) // Required but not used for S3-compatible endpoints
                        .endpointOverride(URI.create(s3Endpoint))
                        .credentialsProvider(StaticCredentialsProvider.create(credentials))
                        .serviceConfiguration(s3Config)
                        .build();

                log.info("Massive Data S3 client initialized. Endpoint: {}, Bucket: {}", s3Endpoint, bucketName);
            } catch (Exception e) {
                log.error("Failed to initialize S3 client: {}", e.getMessage());
                this.s3Client = null;
            }
        } else {
            log.info("Massive Data S3 service is disabled or not configured");
        }
    }

    /**
     * List available data files in the S3 bucket
     */
    public List<S3FileInfo> listAvailableFiles(String prefix) {
        if (s3Client == null) {
            log.warn("S3 client not initialized");
            return List.of();
        }

        try {
            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .maxKeys(1000)
                    .build();

            ListObjectsV2Response response = s3Client.listObjectsV2(request);

            return response.contents().stream()
                    .map(obj -> S3FileInfo.builder()
                            .key(obj.key())
                            .size(obj.size())
                            .lastModified(obj.lastModified().toString())
                            .build())
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error listing S3 files: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Download and import intraday data from S3 for a specific ticker
     * Files are expected to be in format: us_stocks_sip/minute_aggs_v1/2024/01/2024-01-02.csv.gz
     */
    @Transactional
    public ImportResult importFromS3(String ticker, String s3Key) {
        if (s3Client == null) {
            return ImportResult.builder()
                    .ticker(ticker)
                    .success(false)
                    .error("S3 client not initialized")
                    .build();
        }

        try {
            log.info("Importing {} data from S3: {}", ticker, s3Key);

            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .build();

            InputStream inputStream = s3Client.getObject(request);

            // Handle gzipped files
            if (s3Key.endsWith(".gz")) {
                inputStream = new GZIPInputStream(inputStream);
            }

            return parseAndSaveData(ticker, inputStream);

        } catch (NoSuchKeyException e) {
            log.warn("S3 file not found: {}", s3Key);
            return ImportResult.builder()
                    .ticker(ticker)
                    .success(false)
                    .error("File not found: " + s3Key)
                    .build();
        } catch (Exception e) {
            log.error("Error importing from S3: {}", e.getMessage());
            return ImportResult.builder()
                    .ticker(ticker)
                    .success(false)
                    .error(e.getMessage())
                    .build();
        }
    }

    /**
     * Import historical data for a ticker from multiple date files
     */
    @Async
    public CompletableFuture<ImportResult> importHistoricalData(String ticker, int year, int month) {
        if (s3Client == null) {
            return CompletableFuture.completedFuture(ImportResult.builder()
                    .ticker(ticker)
                    .success(false)
                    .error("S3 client not initialized")
                    .build());
        }

        try {
            // List files for the month
            String prefix = String.format("us_stocks_sip/minute_aggs_v1/%d/%02d/", year, month);
            List<S3FileInfo> files = listAvailableFiles(prefix);

            if (files.isEmpty()) {
                return CompletableFuture.completedFuture(ImportResult.builder()
                        .ticker(ticker)
                        .success(false)
                        .error("No files found for " + prefix)
                        .build());
            }

            log.info("Found {} files for {}/{}", files.size(), year, month);

            int totalSaved = 0;
            int totalDuplicates = 0;
            int filesProcessed = 0;

            for (S3FileInfo file : files) {
                ImportResult result = importFromS3(ticker, file.getKey());
                if (result.isSuccess()) {
                    totalSaved += result.getRecordsSaved();
                    totalDuplicates += result.getDuplicatesSkipped();
                    filesProcessed++;
                }

                // Small delay to avoid overwhelming the system
                Thread.sleep(100);
            }

            return CompletableFuture.completedFuture(ImportResult.builder()
                    .ticker(ticker)
                    .success(true)
                    .recordsSaved(totalSaved)
                    .duplicatesSkipped(totalDuplicates)
                    .filesProcessed(filesProcessed)
                    .build());

        } catch (Exception e) {
            log.error("Error importing historical data: {}", e.getMessage());
            return CompletableFuture.completedFuture(ImportResult.builder()
                    .ticker(ticker)
                    .success(false)
                    .error(e.getMessage())
                    .build());
        }
    }

    /**
     * Bulk import for multiple tickers for a date range
     */
    @Async
    public CompletableFuture<BulkImportResult> bulkImportHistoricalData(
            List<String> tickers, int startYear, int startMonth, int endYear, int endMonth) {

        BulkImportResult bulkResult = new BulkImportResult();
        bulkResult.setTickers(tickers);
        bulkResult.setStarted(true);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        AtomicInteger totalRecords = new AtomicInteger(0);

        // Process each year/month combination
        int currentYear = startYear;
        int currentMonth = startMonth;

        while (currentYear < endYear || (currentYear == endYear && currentMonth <= endMonth)) {
            for (String ticker : tickers) {
                try {
                    CompletableFuture<ImportResult> future = importHistoricalData(ticker, currentYear, currentMonth);
                    ImportResult result = future.get(); // Wait for completion

                    if (result.isSuccess()) {
                        successCount.incrementAndGet();
                        totalRecords.addAndGet(result.getRecordsSaved());
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("Error importing {} for {}/{}: {}", ticker, currentYear, currentMonth, e.getMessage());
                    failCount.incrementAndGet();
                }
            }

            // Move to next month
            currentMonth++;
            if (currentMonth > 12) {
                currentMonth = 1;
                currentYear++;
            }
        }

        bulkResult.setSuccessCount(successCount.get());
        bulkResult.setFailCount(failCount.get());
        bulkResult.setTotalRecordsSaved(totalRecords.get());
        bulkResult.setCompleted(true);

        log.info("Bulk import complete: {} successful, {} failed, {} total records",
                successCount.get(), failCount.get(), totalRecords.get());

        return CompletableFuture.completedFuture(bulkResult);
    }

    /**
     * Parse CSV data and save to database
     * Expected format: ticker,timestamp,open,high,low,close,volume (or similar)
     */
    private ImportResult parseAndSaveData(String ticker, InputStream inputStream) {
        List<IntradayPrice> prices = new ArrayList<>();
        int duplicates = 0;
        int lineCount = 0;
        ZoneId zoneId = ZoneId.of(timezone);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            boolean isFirstLine = true;
            String[] headers = null;

            while ((line = reader.readLine()) != null) {
                lineCount++;

                // Skip header row
                if (isFirstLine) {
                    headers = line.split(",");
                    isFirstLine = false;
                    continue;
                }

                try {
                    String[] values = line.split(",");

                    // Handle different CSV formats
                    IntradayPrice price = parseRow(ticker, values, headers, zoneId);

                    if (price != null) {
                        // Check for duplicates
                        if (intradayPriceRepository.existsByTickerAndTimestamp(
                                price.getTicker(), price.getTimestamp())) {
                            duplicates++;
                            continue;
                        }
                        prices.add(price);
                    }

                    // Batch save every 1000 records
                    if (prices.size() >= 1000) {
                        intradayPriceRepository.saveAll(prices);
                        log.debug("Saved batch of {} records for {}", prices.size(), ticker);
                        prices.clear();
                    }

                } catch (Exception e) {
                    log.trace("Error parsing line {}: {}", lineCount, e.getMessage());
                }
            }

            // Save remaining records
            if (!prices.isEmpty()) {
                intradayPriceRepository.saveAll(prices);
            }

            int totalSaved = lineCount - 1 - duplicates; // Subtract header and duplicates
            log.info("Imported {} records for {} ({} duplicates skipped)", totalSaved, ticker, duplicates);

            return ImportResult.builder()
                    .ticker(ticker)
                    .success(true)
                    .recordsSaved(totalSaved)
                    .duplicatesSkipped(duplicates)
                    .build();

        } catch (Exception e) {
            log.error("Error parsing S3 data: {}", e.getMessage());
            return ImportResult.builder()
                    .ticker(ticker)
                    .success(false)
                    .error(e.getMessage())
                    .build();
        }
    }

    /**
     * Parse a single row based on detected format
     */
    private IntradayPrice parseRow(String targetTicker, String[] values, String[] headers, ZoneId zoneId) {
        // Try to detect format from headers
        int tickerIdx = findColumnIndex(headers, "ticker", "symbol");
        int timestampIdx = findColumnIndex(headers, "timestamp", "t", "window_start", "datetime");
        int openIdx = findColumnIndex(headers, "open", "o");
        int highIdx = findColumnIndex(headers, "high", "h");
        int lowIdx = findColumnIndex(headers, "low", "l");
        int closeIdx = findColumnIndex(headers, "close", "c");
        int volumeIdx = findColumnIndex(headers, "volume", "v");

        // If this is a multi-ticker file, check if this row is for our target ticker
        if (tickerIdx >= 0 && tickerIdx < values.length) {
            String rowTicker = values[tickerIdx].trim().toUpperCase();
            if (!rowTicker.equals(targetTicker.toUpperCase())) {
                return null; // Skip rows for other tickers
            }
        }

        // Parse timestamp
        LocalDateTime timestamp = null;
        if (timestampIdx >= 0 && timestampIdx < values.length) {
            String timestampStr = values[timestampIdx].trim();
            timestamp = parseTimestamp(timestampStr, zoneId);
        }

        if (timestamp == null) {
            return null;
        }

        // Parse OHLCV values
        BigDecimal open = parseDecimal(values, openIdx);
        BigDecimal high = parseDecimal(values, highIdx);
        BigDecimal low = parseDecimal(values, lowIdx);
        BigDecimal close = parseDecimal(values, closeIdx);
        long volume = parseLong(values, volumeIdx);

        if (open == null || close == null) {
            return null;
        }

        return IntradayPrice.builder()
                .ticker(targetTicker.toUpperCase())
                .timestamp(timestamp)
                .open(open)
                .high(high != null ? high : open)
                .low(low != null ? low : open)
                .close(close)
                .volume(volume)
                .build();
    }

    private int findColumnIndex(String[] headers, String... possibleNames) {
        if (headers == null) return -1;

        for (int i = 0; i < headers.length; i++) {
            String header = headers[i].trim().toLowerCase();
            for (String name : possibleNames) {
                if (header.equals(name.toLowerCase())) {
                    return i;
                }
            }
        }
        return -1;
    }

    private LocalDateTime parseTimestamp(String value, ZoneId zoneId) {
        try {
            // Try Unix timestamp (milliseconds)
            if (value.matches("\\d{13}")) {
                return LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(value)), zoneId);
            }
            // Try Unix timestamp (seconds)
            if (value.matches("\\d{10}")) {
                return LocalDateTime.ofInstant(Instant.ofEpochSecond(Long.parseLong(value)), zoneId);
            }
            // Try ISO format
            if (value.contains("T")) {
                return LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
            }
            // Try standard datetime format
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseDecimal(String[] values, int index) {
        if (index < 0 || index >= values.length) return null;
        try {
            return new BigDecimal(values[index].trim());
        } catch (Exception e) {
            return null;
        }
    }

    private long parseLong(String[] values, int index) {
        if (index < 0 || index >= values.length) return 0;
        try {
            return Long.parseLong(values[index].trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Check if S3 is available
     */
    public boolean isAvailable() {
        return enabled && s3Client != null;
    }

    /**
     * Get S3 status
     */
    public S3Status getStatus() {
        return S3Status.builder()
                .enabled(enabled)
                .configured(accessKeyId != null && !accessKeyId.isEmpty())
                .clientInitialized(s3Client != null)
                .endpoint(s3Endpoint)
                .bucket(bucketName)
                .build();
    }

    // ============ DTOs ============

    @Data
    @lombok.Builder
    public static class S3FileInfo {
        private String key;
        private long size;
        private String lastModified;
    }

    @Data
    @lombok.Builder
    public static class ImportResult {
        private String ticker;
        private boolean success;
        private int recordsSaved;
        private int duplicatesSkipped;
        private int filesProcessed;
        private String error;
    }

    @Data
    public static class BulkImportResult {
        private List<String> tickers;
        private boolean started;
        private boolean completed;
        private int successCount;
        private int failCount;
        private int totalRecordsSaved;
    }

    @Data
    @lombok.Builder
    public static class S3Status {
        private boolean enabled;
        private boolean configured;
        private boolean clientInitialized;
        private String endpoint;
        private String bucket;
    }
}
