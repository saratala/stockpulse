package com.stockpulse.agent.service;

import com.stockpulse.agent.model.StockPrice;
import com.stockpulse.agent.repository.StockPriceRepository;
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
import java.util.List;

/**
 * Service for importing historical stock data from CSV files.
 * Supports Yahoo Finance CSV format: Date,Open,High,Low,Close,Adj Close,Volume
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CsvImportService {

    private final StockPriceRepository stockPriceRepository;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Data
    @Builder
    public static class ImportResult {
        private String ticker;
        private int recordsImported;
        private int recordsSkipped;
        private LocalDate startDate;
        private LocalDate endDate;
    }

    /**
     * Import Yahoo Finance CSV data for a ticker.
     * CSV format: Date,Open,High,Low,Close,Adj Close,Volume
     */
    @Transactional
    public ImportResult importYahooFinanceCsv(String ticker, InputStream inputStream) throws Exception {
        log.info("Starting CSV import for ticker: {}", ticker);

        int imported = 0;
        int skipped = 0;
        LocalDate startDate = null;
        LocalDate endDate = null;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            boolean isHeader = true;

            while ((line = reader.readLine()) != null) {
                // Skip header
                if (isHeader) {
                    isHeader = false;
                    continue;
                }

                // Skip empty lines
                if (line.trim().isEmpty()) continue;

                try {
                    String[] parts = line.split(",");
                    if (parts.length < 7) {
                        log.warn("Skipping malformed line: {}", line);
                        skipped++;
                        continue;
                    }

                    LocalDate date = LocalDate.parse(parts[0].trim(), DATE_FORMAT);
                    LocalDateTime dateTime = date.atStartOfDay();

                    // Track date range
                    if (startDate == null || date.isBefore(startDate)) startDate = date;
                    if (endDate == null || date.isAfter(endDate)) endDate = date;

                    // Check for "null" values (Yahoo Finance sometimes has these)
                    if (parts[1].trim().equalsIgnoreCase("null") ||
                        parts[4].trim().equalsIgnoreCase("null")) {
                        skipped++;
                        continue;
                    }

                    BigDecimal open = new BigDecimal(parts[1].trim());
                    BigDecimal high = new BigDecimal(parts[2].trim());
                    BigDecimal low = new BigDecimal(parts[3].trim());
                    BigDecimal close = new BigDecimal(parts[4].trim());
                    BigDecimal adjClose = new BigDecimal(parts[5].trim());
                    long volume = Long.parseLong(parts[6].trim());

                    // Check if record already exists (within 12 hours to handle timezone)
                    List<StockPrice> existing = stockPriceRepository.findByTickerAndDateBetween(
                        ticker, dateTime.minusHours(12), dateTime.plusHours(12));

                    if (!existing.isEmpty()) {
                        skipped++;
                        continue;
                    }

                    StockPrice stockPrice = StockPrice.builder()
                        .ticker(ticker)
                        .date(dateTime)
                        .open(open)
                        .high(high)
                        .low(low)
                        .close(close)
                        .adjustedClose(adjClose)
                        .volume(volume)
                        .build();

                    stockPriceRepository.save(stockPrice);
                    imported++;

                } catch (Exception e) {
                    log.warn("Error parsing line: {} - {}", line, e.getMessage());
                    skipped++;
                }
            }
        }

        log.info("CSV import complete for {}: imported={}, skipped={}, dateRange={} to {}",
            ticker, imported, skipped, startDate, endDate);

        return ImportResult.builder()
            .ticker(ticker)
            .recordsImported(imported)
            .recordsSkipped(skipped)
            .startDate(startDate)
            .endDate(endDate)
            .build();
    }
}
