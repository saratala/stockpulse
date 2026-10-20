package com.stockpulse.agent.service;

import com.stockpulse.agent.model.IntradayPrice;
import com.stockpulse.agent.repository.IntradayPriceRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for importing minute-level intraday stock data from CSV files.
 * Supports multiple CSV formats commonly used for intraday data.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntradayCsvImportService {

    private final IntradayPriceRepository intradayPriceRepository;

    private static final int BATCH_SIZE = 1000;

    // Common datetime formats for intraday data
    private static final DateTimeFormatter[] DATETIME_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm"),
            DateTimeFormatter.ISO_LOCAL_DATE_TIME
    };

    @Data
    @Builder
    public static class ImportResult {
        private String ticker;
        private int recordsImported;
        private int recordsSkipped;
        private int duplicatesSkipped;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private long processingTimeMs;
        private List<String> errors;
    }

    @Data
    @Builder
    public static class CsvFormat {
        private int timestampCol;
        private int openCol;
        private int highCol;
        private int lowCol;
        private int closeCol;
        private int volumeCol;
        private boolean hasHeader;
        private String delimiter;
    }

    /**
     * Import intraday CSV data for a ticker with auto-format detection.
     * Expected formats:
     * - timestamp,open,high,low,close,volume
     * - date,time,open,high,low,close,volume
     * - datetime,open,high,low,close,adj_close,volume
     */
    @Transactional
    public ImportResult importIntradayCsv(String ticker, InputStream inputStream) throws Exception {
        return importIntradayCsv(ticker, inputStream, true);
    }

    @Transactional
    public ImportResult importIntradayCsv(String ticker, InputStream inputStream, boolean skipDuplicates) throws Exception {
        long startTimeMs = System.currentTimeMillis();
        log.info("Starting intraday CSV import for ticker: {}", ticker);

        int imported = 0;
        int skipped = 0;
        int duplicatesSkipped = 0;
        LocalDateTime minTime = null;
        LocalDateTime maxTime = null;
        List<String> errors = new ArrayList<>();
        List<IntradayPrice> batch = new ArrayList<>();

        CsvFormat format = null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // Skip empty lines
                if (line.trim().isEmpty()) continue;

                // Detect format from header or first data line
                if (format == null) {
                    format = detectFormat(line);
                    if (format.isHasHeader()) {
                        continue; // Skip header line
                    }
                }

                try {
                    IntradayPrice price = parseLine(line, ticker, format, lineNumber);
                    if (price == null) {
                        skipped++;
                        continue;
                    }

                    // Check for duplicates if enabled
                    if (skipDuplicates && intradayPriceRepository.existsByTickerAndTimestamp(ticker, price.getTimestamp())) {
                        duplicatesSkipped++;
                        continue;
                    }

                    batch.add(price);

                    // Track time range
                    if (minTime == null || price.getTimestamp().isBefore(minTime)) {
                        minTime = price.getTimestamp();
                    }
                    if (maxTime == null || price.getTimestamp().isAfter(maxTime)) {
                        maxTime = price.getTimestamp();
                    }

                    // Batch save
                    if (batch.size() >= BATCH_SIZE) {
                        intradayPriceRepository.saveAll(batch);
                        imported += batch.size();
                        log.debug("Saved batch of {} records, total imported: {}", batch.size(), imported);
                        batch.clear();
                    }

                } catch (Exception e) {
                    String error = String.format("Line %d: %s", lineNumber, e.getMessage());
                    if (errors.size() < 10) { // Limit error messages stored
                        errors.add(error);
                    }
                    skipped++;
                }

                // Progress logging every 10,000 lines
                if (lineNumber % 10000 == 0) {
                    log.info("Processed {} lines, imported: {}, skipped: {}, duplicates: {}",
                            lineNumber, imported + batch.size(), skipped, duplicatesSkipped);
                }
            }

            // Save remaining batch
            if (!batch.isEmpty()) {
                intradayPriceRepository.saveAll(batch);
                imported += batch.size();
            }
        }

        long processingTimeMs = System.currentTimeMillis() - startTimeMs;

        log.info("Intraday CSV import complete for {}: imported={}, skipped={}, duplicates={}, timeRange={} to {}, processingTime={}ms",
                ticker, imported, skipped, duplicatesSkipped, minTime, maxTime, processingTimeMs);

        return ImportResult.builder()
                .ticker(ticker)
                .recordsImported(imported)
                .recordsSkipped(skipped)
                .duplicatesSkipped(duplicatesSkipped)
                .startTime(minTime)
                .endTime(maxTime)
                .processingTimeMs(processingTimeMs)
                .errors(errors)
                .build();
    }

    /**
     * Detect CSV format from header or first line
     */
    private CsvFormat detectFormat(String line) {
        String lowerLine = line.toLowerCase();
        String delimiter = line.contains("\t") ? "\t" : ",";
        String[] parts = line.split(delimiter);

        // Check if it's a header line
        boolean hasHeader = lowerLine.contains("time") || lowerLine.contains("date") ||
                lowerLine.contains("open") || lowerLine.contains("close");

        // Default format: timestamp,open,high,low,close,volume
        int timestampCol = 0;
        int openCol = 1;
        int highCol = 2;
        int lowCol = 3;
        int closeCol = 4;
        int volumeCol = 5;

        if (hasHeader) {
            // Parse header to find column positions
            for (int i = 0; i < parts.length; i++) {
                String col = parts[i].toLowerCase().trim();
                if (col.contains("time") || col.contains("date")) {
                    timestampCol = i;
                } else if (col.equals("open") || col.equals("o")) {
                    openCol = i;
                } else if (col.equals("high") || col.equals("h")) {
                    highCol = i;
                } else if (col.equals("low") || col.equals("l")) {
                    lowCol = i;
                } else if (col.equals("close") || col.equals("c")) {
                    closeCol = i;
                } else if (col.equals("volume") || col.equals("vol") || col.equals("v")) {
                    volumeCol = i;
                }
            }
        }

        return CsvFormat.builder()
                .timestampCol(timestampCol)
                .openCol(openCol)
                .highCol(highCol)
                .lowCol(lowCol)
                .closeCol(closeCol)
                .volumeCol(volumeCol)
                .hasHeader(hasHeader)
                .delimiter(delimiter)
                .build();
    }

    /**
     * Parse a single CSV line into an IntradayPrice
     */
    private IntradayPrice parseLine(String line, String ticker, CsvFormat format, int lineNumber) {
        String[] parts = line.split(format.getDelimiter());

        int maxCol = Math.max(format.getTimestampCol(),
                Math.max(format.getOpenCol(),
                        Math.max(format.getHighCol(),
                                Math.max(format.getLowCol(),
                                        Math.max(format.getCloseCol(), format.getVolumeCol())))));

        if (parts.length <= maxCol) {
            log.warn("Line {} has insufficient columns: expected at least {}, got {}", lineNumber, maxCol + 1, parts.length);
            return null;
        }

        // Parse timestamp
        String timestampStr = parts[format.getTimestampCol()].trim();
        LocalDateTime timestamp = parseTimestamp(timestampStr);
        if (timestamp == null) {
            log.warn("Line {} has invalid timestamp: {}", lineNumber, timestampStr);
            return null;
        }

        // Parse numeric values
        try {
            String openStr = parts[format.getOpenCol()].trim();
            String highStr = parts[format.getHighCol()].trim();
            String lowStr = parts[format.getLowCol()].trim();
            String closeStr = parts[format.getCloseCol()].trim();
            String volumeStr = parts[format.getVolumeCol()].trim();

            // Skip if any price is "null" or empty
            if (isInvalidNumber(openStr) || isInvalidNumber(closeStr)) {
                return null;
            }

            BigDecimal open = new BigDecimal(openStr);
            BigDecimal high = new BigDecimal(highStr);
            BigDecimal low = new BigDecimal(lowStr);
            BigDecimal close = new BigDecimal(closeStr);
            long volume = Long.parseLong(volumeStr.replace(",", ""));

            return IntradayPrice.builder()
                    .ticker(ticker)
                    .timestamp(timestamp)
                    .open(open)
                    .high(high)
                    .low(low)
                    .close(close)
                    .volume(volume)
                    .build();

        } catch (NumberFormatException e) {
            log.warn("Line {} has invalid numeric value: {}", lineNumber, e.getMessage());
            return null;
        }
    }

    /**
     * Try to parse a timestamp string using multiple formats
     */
    private LocalDateTime parseTimestamp(String timestampStr) {
        for (DateTimeFormatter formatter : DATETIME_FORMATS) {
            try {
                return LocalDateTime.parse(timestampStr, formatter);
            } catch (DateTimeParseException ignored) {
                // Try next format
            }
        }

        // Try parsing as date only and add start of day
        try {
            LocalDate date = LocalDate.parse(timestampStr, DateTimeFormatter.ISO_LOCAL_DATE);
            return date.atStartOfDay();
        } catch (DateTimeParseException ignored) {
            // Not a date format
        }

        return null;
    }

    private boolean isInvalidNumber(String value) {
        return value == null || value.isEmpty() ||
                value.equalsIgnoreCase("null") ||
                value.equalsIgnoreCase("nan") ||
                value.equals("-");
    }

    /**
     * Clear all intraday data for a ticker
     */
    @Transactional
    public int clearIntradayData(String ticker) {
        log.info("Clearing all intraday data for ticker: {}", ticker);
        return intradayPriceRepository.deleteByTicker(ticker);
    }

    /**
     * Get statistics about existing intraday data for a ticker
     */
    public IntradayDataStats getIntradayStats(String ticker) {
        List<Object[]> dateRange = intradayPriceRepository.findDateRangeByTicker(ticker);

        LocalDateTime minTime = null;
        LocalDateTime maxTime = null;
        long totalRecords = 0;

        if (!dateRange.isEmpty() && dateRange.get(0)[0] != null) {
            minTime = (LocalDateTime) dateRange.get(0)[0];
            maxTime = (LocalDateTime) dateRange.get(0)[1];
            // Count records if we have a date range
            if (minTime != null && maxTime != null) {
                totalRecords = intradayPriceRepository.findByTickerAndTimestampBetween(ticker, minTime, maxTime).size();
            }
        }

        return IntradayDataStats.builder()
                .ticker(ticker)
                .startTime(minTime)
                .endTime(maxTime)
                .totalRecords(totalRecords)
                .build();
    }

    @Data
    @Builder
    public static class IntradayDataStats {
        private String ticker;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private long totalRecords;
    }
}
