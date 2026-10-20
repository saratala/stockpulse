package com.stockpulse.agent.scheduler;

import com.stockpulse.agent.service.TwelveDataService;
import com.stockpulse.agent.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduler for automatically fetching intraday stock data from Twelve Data API.
 * Runs during market hours to keep minute-level data up to date.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IntradayDataScheduler {

    private final TwelveDataService twelveDataService;
    private final StockRepository stockRepository;

    @Value("${app.twelvedata.enabled:false}")
    private boolean enabled;

    @Value("${app.twelvedata.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    @Value("${app.twelvedata.scheduler.max-tickers-per-run:8}")
    private int maxTickersPerRun;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    /**
     * Fetch intraday data during market hours (9:30 AM - 4:00 PM ET, Mon-Fri).
     * Runs every 30 minutes to stay within free tier API limits (800/day).
     */
    @Scheduled(cron = "${app.twelvedata.scheduler.cron:0 0,30 9-16 * * MON-FRI}", zone = "America/New_York")
    public void fetchIntradayData() {
        if (!enabled || !schedulerEnabled) {
            log.debug("Intraday data scheduler is disabled");
            return;
        }

        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Intraday data fetch already in progress, skipping this run");
            return;
        }

        try {
            log.info("Starting scheduled intraday data fetch");

            // Check API availability first
            if (!twelveDataService.isApiAvailable()) {
                log.warn("Twelve Data API is not available, skipping this run");
                return;
            }

            // Get list of tickers to fetch (prioritize most active/important)
            List<String> tickers = getTickersToFetch();

            if (tickers.isEmpty()) {
                log.info("No tickers configured for intraday data fetch");
                return;
            }

            log.info("Fetching intraday data for {} tickers: {}", tickers.size(), tickers);

            // Fetch data for each ticker (with rate limiting built into the service)
            int successCount = 0;
            int failCount = 0;

            for (String ticker : tickers) {
                TwelveDataService.FetchResult result = twelveDataService.fetchIntradayData(ticker);

                if (result.isSuccess()) {
                    successCount++;
                    log.info("Fetched {} records for {} ({} duplicates skipped)",
                            result.getRecordsSaved(), ticker, result.getDuplicatesSkipped());
                } else {
                    failCount++;
                    log.warn("Failed to fetch data for {}: {}", ticker, result.getError());
                }

                // Rate limiting delay between requests
                try {
                    Thread.sleep(8000); // 8 seconds between requests (8/minute limit)
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            log.info("Intraday data fetch complete: {} successful, {} failed", successCount, failCount);

        } catch (Exception e) {
            log.error("Error during scheduled intraday data fetch", e);
        } finally {
            isRunning.set(false);
        }
    }

    /**
     * Get the list of tickers to fetch intraday data for.
     * Limited by maxTickersPerRun to stay within API limits.
     */
    private List<String> getTickersToFetch() {
        // Default high-priority tickers
        List<String> defaultTickers = List.of(
                "AAPL", "MSFT", "GOOGL", "AMZN", "META", "NVDA", "TSLA", "NFLX"
        );

        // Get tickers from database that we're tracking
        List<String> dbTickers = stockRepository.findAllTickers();

        // Prioritize default tickers, then add from database
        List<String> result = new java.util.ArrayList<>(defaultTickers);

        for (String ticker : dbTickers) {
            if (!result.contains(ticker) && result.size() < maxTickersPerRun) {
                result.add(ticker);
            }
        }

        // Limit to max tickers per run
        if (result.size() > maxTickersPerRun) {
            result = result.subList(0, maxTickersPerRun);
        }

        return result;
    }

    /**
     * Get the current running status
     */
    public boolean isRunning() {
        return isRunning.get();
    }

    /**
     * Get scheduler status information
     */
    public SchedulerStatus getStatus() {
        return SchedulerStatus.builder()
                .enabled(enabled && schedulerEnabled)
                .running(isRunning.get())
                .maxTickersPerRun(maxTickersPerRun)
                .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class SchedulerStatus {
        private boolean enabled;
        private boolean running;
        private int maxTickersPerRun;
    }
}
