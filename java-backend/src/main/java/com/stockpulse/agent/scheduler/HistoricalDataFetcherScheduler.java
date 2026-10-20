package com.stockpulse.agent.scheduler;

import com.stockpulse.agent.service.MassiveDataService;
import com.stockpulse.agent.repository.IntradayPriceRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Smart scheduler for gradually fetching historical minute-level data.
 * Designed to work within free tier rate limits (5 requests/minute).
 *
 * Strategy:
 * - Runs every 15 minutes during off-market hours
 * - Fetches data for one ticker per run (rate limit friendly)
 * - Works backwards from recent dates to build historical data
 * - Tracks progress to avoid duplicate fetches
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HistoricalDataFetcherScheduler {

    private final MassiveDataService massiveDataService;
    private final IntradayPriceRepository intradayPriceRepository;

    @Value("${app.massivedata.enabled:true}")
    private boolean enabled;

    @Value("${app.massivedata.historical-fetch.enabled:true}")
    private boolean historicalFetchEnabled;

    @Value("${app.massivedata.historical-fetch.days-back:30}")
    private int daysBack;

    @Value("${app.massivedata.timezone:America/New_York}")
    private String timezone;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicInteger tickerIndex = new AtomicInteger(0);
    private final Map<String, LocalDate> lastFetchedDate = new HashMap<>();

    // Priority tickers to fetch first
    private static final List<String> PRIORITY_TICKERS = Arrays.asList(
            "AAPL", "MSFT", "GOOGL", "AMZN", "META", "NVDA", "TSLA", "NFLX",
            "AMD", "INTC", "CRM", "ADBE", "PYPL", "QCOM", "AVGO", "ORCL"
    );

    /**
     * Run during off-market hours to fetch historical data
     * Cron: Every 15 minutes between 5 PM and 9 AM ET (off-market hours)
     */
    @Scheduled(cron = "${app.massivedata.historical-fetch.cron:0 */15 17-23,0-8 * * *}")
    public void fetchHistoricalData() {
        if (!enabled || !historicalFetchEnabled) {
            log.debug("Historical data fetch is disabled");
            return;
        }

        if (!isRunning.compareAndSet(false, true)) {
            log.debug("Historical fetch already in progress, skipping");
            return;
        }

        try {
            log.info("Starting historical data fetch run");

            // Check API availability
            if (!massiveDataService.isApiAvailable()) {
                log.warn("Massive Data API not available, skipping historical fetch");
                return;
            }

            // Get next ticker to fetch
            String ticker = getNextTicker();
            LocalDate dateToFetch = getNextDateToFetch(ticker);

            if (dateToFetch == null) {
                log.info("All historical data for {} has been fetched", ticker);
                // Move to next ticker
                tickerIndex.incrementAndGet();
                return;
            }

            log.info("Fetching historical data for {} on {}", ticker, dateToFetch);

            MassiveDataService.FetchResult result = massiveDataService.fetchIntradayData(ticker, dateToFetch);

            if (result.isSuccess()) {
                log.info("Successfully fetched {} records for {} on {} ({} duplicates)",
                        result.getRecordsSaved(), ticker, dateToFetch, result.getDuplicatesSkipped());
                lastFetchedDate.put(ticker, dateToFetch);
            } else {
                log.warn("Failed to fetch data for {} on {}: {}", ticker, dateToFetch, result.getError());
            }

            // Log progress
            logProgress();

        } catch (Exception e) {
            log.error("Error during historical data fetch", e);
        } finally {
            isRunning.set(false);
        }
    }

    /**
     * Get the next ticker to fetch data for
     */
    private String getNextTicker() {
        int index = tickerIndex.get() % PRIORITY_TICKERS.size();
        return PRIORITY_TICKERS.get(index);
    }

    /**
     * Get the next date to fetch for a ticker (working backwards)
     */
    private LocalDate getNextDateToFetch(String ticker) {
        ZoneId zone = ZoneId.of(timezone);
        LocalDate today = LocalDate.now(zone);
        LocalDate earliestDate = today.minusDays(daysBack);

        // Get the last date we fetched for this ticker
        LocalDate lastFetched = lastFetchedDate.get(ticker);

        LocalDate dateToCheck;
        if (lastFetched != null) {
            dateToCheck = lastFetched.minusDays(1);
        } else {
            // Start from yesterday (today's data may be incomplete)
            dateToCheck = today.minusDays(1);
        }

        // Find the next trading day to fetch
        while (!dateToCheck.isBefore(earliestDate)) {
            // Skip weekends
            if (dateToCheck.getDayOfWeek() != DayOfWeek.SATURDAY &&
                dateToCheck.getDayOfWeek() != DayOfWeek.SUNDAY) {

                // Check if we already have data for this date
                long count = intradayPriceRepository.countByTickerAndDate(
                        ticker, dateToCheck.atStartOfDay());

                if (count < 100) { // Less than 100 records = incomplete day
                    return dateToCheck;
                }
            }
            dateToCheck = dateToCheck.minusDays(1);
        }

        return null; // All dates have been fetched
    }

    /**
     * Log progress summary
     */
    private void logProgress() {
        Map<String, Long> tickerCounts = new HashMap<>();
        for (String ticker : PRIORITY_TICKERS) {
            long count = intradayPriceRepository.countByTicker(ticker);
            tickerCounts.put(ticker, count);
        }

        long totalRecords = tickerCounts.values().stream().mapToLong(Long::longValue).sum();
        log.info("Historical data progress: {} total records across {} tickers",
                totalRecords, tickerCounts.size());

        // Log per-ticker stats
        tickerCounts.forEach((ticker, count) -> {
            if (count > 0) {
                log.debug("  {} : {} records (~{} trading days)",
                        ticker, count, count / 390); // ~390 minutes per trading day
            }
        });
    }

    /**
     * Manually trigger a fetch run
     */
    public void triggerFetch() {
        if (!isRunning.get()) {
            fetchHistoricalData();
        }
    }

    /**
     * Get current status
     */
    public FetchStatus getStatus() {
        Map<String, Long> tickerCounts = new HashMap<>();
        for (String ticker : PRIORITY_TICKERS) {
            long count = intradayPriceRepository.countByTicker(ticker);
            tickerCounts.put(ticker, count);
        }

        return FetchStatus.builder()
                .enabled(enabled && historicalFetchEnabled)
                .running(isRunning.get())
                .currentTickerIndex(tickerIndex.get())
                .currentTicker(PRIORITY_TICKERS.get(tickerIndex.get() % PRIORITY_TICKERS.size()))
                .daysBack(daysBack)
                .tickerRecordCounts(tickerCounts)
                .totalRecords(tickerCounts.values().stream().mapToLong(Long::longValue).sum())
                .build();
    }

    /**
     * Reset fetch progress (start from beginning)
     */
    public void resetProgress() {
        tickerIndex.set(0);
        lastFetchedDate.clear();
        log.info("Historical fetch progress reset");
    }

    @Data
    @lombok.Builder
    public static class FetchStatus {
        private boolean enabled;
        private boolean running;
        private int currentTickerIndex;
        private String currentTicker;
        private int daysBack;
        private Map<String, Long> tickerRecordCounts;
        private long totalRecords;
    }
}
