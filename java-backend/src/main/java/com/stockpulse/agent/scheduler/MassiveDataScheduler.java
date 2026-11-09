package com.stockpulse.agent.scheduler;

import com.stockpulse.agent.service.MassiveDataService;
import com.stockpulse.agent.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Scheduler for automatically fetching intraday stock data from Massive Data API.
 * Runs during market hours to keep minute-level data up to date.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MassiveDataScheduler {

    private final MassiveDataService massiveDataService;
    private final StockRepository stockRepository;

    @Value("${app.massivedata.enabled:false}")
    private boolean enabled;

    @Value("${app.massivedata.scheduler.enabled:true}")
    private boolean schedulerEnabled;

    @Value("${app.massivedata.scheduler.max-tickers-per-run:20}")
    private int maxTickersPerRun;

    @Value("${app.massivedata.rate-limit.delay-ms:12000}")
    private long requestDelayMs;

    @Value("${app.massivedata.timezone:America/New_York}")
    private String timezone;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    /**
     * Fetch intraday data during market hours (9:30 AM - 4:00 PM ET, Mon-Fri).
     * Runs every 15 minutes.
     */
    @Scheduled(cron = "${app.massivedata.scheduler.cron:0 */15 9-16 * * MON-FRI}", zone = "America/New_York")
    public void fetchIntradayData() {
        if (!enabled || !schedulerEnabled) {
            log.debug("Massive Data scheduler is disabled");
            return;
        }

        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Massive Data fetch already in progress, skipping this run");
            return;
        }

        try {
            log.info("Starting scheduled Massive Data intraday fetch");

            // Check API availability first
            if (!massiveDataService.isApiAvailable()) {
                log.warn("Massive Data API is not available, skipping this run");
                return;
            }

            // Get list of tickers to fetch
            List<String> tickers = getTickersToFetch();

            if (tickers.isEmpty()) {
                log.info("No tickers configured for intraday data fetch");
                return;
            }

            log.info("Fetching intraday data for {} tickers: {}", tickers.size(), tickers);

            LocalDate today = LocalDate.now(ZoneId.of(timezone));
            int successCount = 0;
            int failCount = 0;

            for (String ticker : tickers) {
                MassiveDataService.FetchResult result = massiveDataService.fetchIntradayData(ticker, today);

                if (result.isSuccess()) {
                    successCount++;
                    log.info("Fetched {} records for {} ({} duplicates skipped)",
                            result.getRecordsSaved(), ticker, result.getDuplicatesSkipped());
                } else {
                    failCount++;
                    log.warn("Failed to fetch data for {}: {}", ticker, result.getError());
                }

                // Stay under the API's per-minute request limit
                try {
                    Thread.sleep(requestDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            log.info("Massive Data fetch complete: {} successful, {} failed", successCount, failCount);

        } catch (Exception e) {
            log.error("Error during scheduled Massive Data fetch", e);
        } finally {
            isRunning.set(false);
        }
    }

    /**
     * Get the list of tickers to fetch intraday data for.
     */
    private List<String> getTickersToFetch() {
        // Default high-priority tickers
        List<String> defaultTickers = List.of(
                "AAPL", "MSFT", "GOOGL", "AMZN", "META", "NVDA", "TSLA", "NFLX",
                "AMD", "INTC", "CRM", "ADBE", "PYPL", "QCOM", "AVGO", "ORCL"
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
     * Manual trigger to fetch data
     */
    public void triggerFetch() {
        if (!isRunning.get()) {
            fetchIntradayData();
        } else {
            log.warn("Cannot trigger fetch: already running");
        }
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
