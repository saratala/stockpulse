package com.stockpulse.agent.service;

import com.stockpulse.agent.model.IntradayPrice;
import com.stockpulse.agent.repository.IntradayPriceRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for aggregating minute-level intraday data into larger intervals (5min, 15min, 1hr).
 * This helps reduce data volume while maintaining useful granularity for analysis.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IntradayAggregationService {

    private final IntradayPriceRepository intradayPriceRepository;

    /**
     * Aggregate minute data into specified interval bars for a ticker on a date
     */
    public List<AggregatedBar> aggregateToInterval(String ticker, LocalDate date, int intervalMinutes) {
        LocalDateTime startOfDay = date.atTime(9, 30); // Market open
        LocalDateTime endOfDay = date.atTime(16, 0);   // Market close

        List<IntradayPrice> minuteData = intradayPriceRepository
                .findByTickerAndTimestampBetweenOrderByTimestampAsc(
                        ticker.toUpperCase(),
                        startOfDay,
                        endOfDay
                );

        if (minuteData.isEmpty()) {
            log.debug("No minute data found for {} on {}", ticker, date);
            return List.of();
        }

        return aggregateMinuteData(minuteData, intervalMinutes);
    }

    /**
     * Get 15-minute bars for a ticker on a date
     */
    public List<AggregatedBar> get15MinuteBars(String ticker, LocalDate date) {
        return aggregateToInterval(ticker, date, 15);
    }

    /**
     * Get 5-minute bars for a ticker on a date
     */
    public List<AggregatedBar> get5MinuteBars(String ticker, LocalDate date) {
        return aggregateToInterval(ticker, date, 5);
    }

    /**
     * Get hourly bars for a ticker on a date
     */
    public List<AggregatedBar> getHourlyBars(String ticker, LocalDate date) {
        return aggregateToInterval(ticker, date, 60);
    }

    /**
     * Get aggregated bars for a date range
     */
    public List<AggregatedBar> aggregateForDateRange(String ticker, LocalDate startDate, LocalDate endDate, int intervalMinutes) {
        List<AggregatedBar> allBars = new ArrayList<>();
        LocalDate currentDate = startDate;

        while (!currentDate.isAfter(endDate)) {
            List<AggregatedBar> dayBars = aggregateToInterval(ticker, currentDate, intervalMinutes);
            allBars.addAll(dayBars);
            currentDate = currentDate.plusDays(1);
        }

        return allBars;
    }

    /**
     * Get the latest N aggregated bars
     */
    public List<AggregatedBar> getLatestBars(String ticker, int intervalMinutes, int count) {
        // Get recent minute data
        List<IntradayPrice> recentData = intradayPriceRepository
                .findTopNByTickerOrderByTimestampDesc(ticker.toUpperCase(), count * intervalMinutes * 2);

        if (recentData.isEmpty()) {
            return List.of();
        }

        // Reverse to chronological order
        Collections.reverse(recentData);

        List<AggregatedBar> bars = aggregateMinuteData(recentData, intervalMinutes);

        // Return last N bars
        if (bars.size() > count) {
            return bars.subList(bars.size() - count, bars.size());
        }
        return bars;
    }

    /**
     * Core aggregation logic - convert minute data to interval bars
     */
    private List<AggregatedBar> aggregateMinuteData(List<IntradayPrice> minuteData, int intervalMinutes) {
        if (minuteData.isEmpty()) {
            return List.of();
        }

        Map<LocalDateTime, List<IntradayPrice>> grouped = minuteData.stream()
                .collect(Collectors.groupingBy(
                        price -> roundToInterval(price.getTimestamp(), intervalMinutes),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<AggregatedBar> bars = new ArrayList<>();

        for (Map.Entry<LocalDateTime, List<IntradayPrice>> entry : grouped.entrySet()) {
            LocalDateTime intervalStart = entry.getKey();
            List<IntradayPrice> prices = entry.getValue();

            if (prices.isEmpty()) continue;

            // Sort by timestamp to ensure correct OHLC
            prices.sort(Comparator.comparing(IntradayPrice::getTimestamp));

            BigDecimal open = prices.get(0).getOpen();
            BigDecimal close = prices.get(prices.size() - 1).getClose();
            BigDecimal high = prices.stream()
                    .map(IntradayPrice::getHigh)
                    .max(BigDecimal::compareTo)
                    .orElse(open);
            BigDecimal low = prices.stream()
                    .map(IntradayPrice::getLow)
                    .min(BigDecimal::compareTo)
                    .orElse(open);
            long volume = prices.stream()
                    .mapToLong(IntradayPrice::getVolume)
                    .sum();

            // Calculate VWAP
            BigDecimal vwap = calculateVWAP(prices);

            AggregatedBar bar = AggregatedBar.builder()
                    .ticker(prices.get(0).getTicker())
                    .timestamp(intervalStart)
                    .intervalMinutes(intervalMinutes)
                    .open(open)
                    .high(high)
                    .low(low)
                    .close(close)
                    .volume(volume)
                    .vwap(vwap)
                    .dataPointsCount(prices.size())
                    .build();

            bars.add(bar);
        }

        return bars;
    }

    /**
     * Round timestamp down to interval boundary
     */
    private LocalDateTime roundToInterval(LocalDateTime timestamp, int intervalMinutes) {
        int minute = timestamp.getMinute();
        int roundedMinute = (minute / intervalMinutes) * intervalMinutes;
        return timestamp.withMinute(roundedMinute).withSecond(0).withNano(0);
    }

    /**
     * Calculate Volume Weighted Average Price
     */
    private BigDecimal calculateVWAP(List<IntradayPrice> prices) {
        BigDecimal totalValue = BigDecimal.ZERO;
        long totalVolume = 0;

        for (IntradayPrice price : prices) {
            // Use typical price = (high + low + close) / 3
            BigDecimal typicalPrice = price.getHigh()
                    .add(price.getLow())
                    .add(price.getClose())
                    .divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP);

            totalValue = totalValue.add(typicalPrice.multiply(BigDecimal.valueOf(price.getVolume())));
            totalVolume += price.getVolume();
        }

        if (totalVolume == 0) {
            return prices.get(0).getClose();
        }

        return totalValue.divide(BigDecimal.valueOf(totalVolume), 4, RoundingMode.HALF_UP);
    }

    /**
     * Get data availability summary for a ticker
     */
    public DataAvailability getDataAvailability(String ticker) {
        // Get date range of available data
        Optional<LocalDateTime> earliest = intradayPriceRepository.findEarliestTimestamp(ticker.toUpperCase());
        Optional<LocalDateTime> latest = intradayPriceRepository.findLatestTimestamp(ticker.toUpperCase());
        long totalRecords = intradayPriceRepository.countByTicker(ticker.toUpperCase());

        if (earliest.isEmpty() || latest.isEmpty()) {
            return DataAvailability.builder()
                    .ticker(ticker.toUpperCase())
                    .hasData(false)
                    .totalMinuteRecords(0)
                    .build();
        }

        // Estimate trading days
        long daysBetween = ChronoUnit.DAYS.between(earliest.get().toLocalDate(), latest.get().toLocalDate()) + 1;
        long estimatedTradingDays = (daysBetween * 5) / 7; // Rough estimate

        return DataAvailability.builder()
                .ticker(ticker.toUpperCase())
                .hasData(true)
                .earliestDate(earliest.get().toLocalDate())
                .latestDate(latest.get().toLocalDate())
                .totalMinuteRecords(totalRecords)
                .estimatedTradingDays(estimatedTradingDays)
                .estimated15MinBars(totalRecords / 15)
                .build();
    }

    // ============ DTOs ============

    @Data
    @lombok.Builder
    public static class AggregatedBar {
        private String ticker;
        private LocalDateTime timestamp;
        private int intervalMinutes;
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private long volume;
        private BigDecimal vwap;
        private int dataPointsCount;

        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("ticker", ticker);
            map.put("timestamp", timestamp.toString());
            map.put("interval", intervalMinutes + "min");
            map.put("open", open);
            map.put("high", high);
            map.put("low", low);
            map.put("close", close);
            map.put("volume", volume);
            map.put("vwap", vwap);
            map.put("dataPoints", dataPointsCount);
            return map;
        }
    }

    @Data
    @lombok.Builder
    public static class DataAvailability {
        private String ticker;
        private boolean hasData;
        private LocalDate earliestDate;
        private LocalDate latestDate;
        private long totalMinuteRecords;
        private long estimatedTradingDays;
        private long estimated15MinBars;
    }
}
