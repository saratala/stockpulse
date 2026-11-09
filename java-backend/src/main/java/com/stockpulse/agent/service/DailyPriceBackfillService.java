package com.stockpulse.agent.service;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads Yahoo daily bars into stock_prices, overwriting any bar with the same
 * (ticker, date) key. Yahoo's bar is authoritative, so it replaces the provisional
 * bar RealTimeDataService builds from intraday quotes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DailyPriceBackfillService {

    private final YahooFinanceService yahooFinanceService;
    private final JdbcTemplate jdbcTemplate;

    public BackfillResult backfill(String ticker, int years) {
        String symbol = ticker.toUpperCase();
        int currentYear = LocalDate.now().getYear();
        int written = 0;
        List<String> errors = new ArrayList<>();

        for (int year = currentYear - years; year <= currentYear; year++) {
            try {
                Optional<YahooFinanceService.HistoricalData> data = yahooFinanceService.fetchYearlyData(symbol, year);
                if (data.isEmpty() || data.get().getPriceData() == null) {
                    errors.add(year + ": no data");
                    continue;
                }
                written += upsert(symbol, data.get().getPriceData());
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                errors.add("interrupted");
                break;
            } catch (Exception e) {
                log.warn("Daily backfill for {} {} failed: {}", symbol, year, e.getMessage());
                errors.add(year + ": " + e.getMessage());
            }
        }

        log.info("Daily backfill for {}: {} bars written, {} years failed", symbol, written, errors.size());
        return BackfillResult.builder()
            .ticker(symbol)
            .barsWritten(written)
            .errors(errors)
            .build();
    }

    private int upsert(String ticker, Map<LocalDateTime, YahooFinanceService.HistoricalData.PriceData> bars) {
        List<Object[]> rows = new ArrayList<>();
        for (Map.Entry<LocalDateTime, YahooFinanceService.HistoricalData.PriceData> e : bars.entrySet()) {
            YahooFinanceService.HistoricalData.PriceData p = e.getValue();
            if (p.getOpen() == null || p.getHigh() == null || p.getLow() == null || p.getClose() == null) continue;
            // Skip bars the table's CHECK constraint would reject (Yahoo occasionally rounds outside the range)
            if (p.getHigh().compareTo(p.getLow()) < 0 || p.getHigh().compareTo(p.getOpen()) < 0
                || p.getHigh().compareTo(p.getClose()) < 0 || p.getLow().compareTo(p.getOpen()) > 0
                || p.getLow().compareTo(p.getClose()) > 0) continue;
            rows.add(new Object[]{ticker, Timestamp.valueOf(e.getKey()), p.getOpen(), p.getHigh(), p.getLow(),
                p.getClose(), p.getVolume() == null ? 0L : p.getVolume(), p.getClose()});
        }

        int[] counts = jdbcTemplate.batchUpdate("""
            INSERT INTO stock_prices (ticker, date, open, high, low, close, volume, adjusted_close)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (ticker, date) DO UPDATE SET
                open = EXCLUDED.open, high = EXCLUDED.high, low = EXCLUDED.low, close = EXCLUDED.close,
                volume = EXCLUDED.volume, adjusted_close = EXCLUDED.adjusted_close
            """, rows);

        int written = 0;
        for (int c : counts) {
            if (c > 0) written += c;
        }
        return written;
    }

    @Data
    @Builder
    public static class BackfillResult {
        private String ticker;
        private int barsWritten;
        private List<String> errors;
    }
}
