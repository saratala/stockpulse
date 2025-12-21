package com.stockpulse.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class ScheduledDataService {

    private final RealTimeDataService realTimeDataService;
    private final SentimentAnalysisService sentimentAnalysisService;

    @PostConstruct
    public void initializeData() {
        log.info("=== StockPulse Data Initialization Starting ===");

        // Run initialization in a separate thread to not block startup
        new Thread(() -> {
            try {
                // Wait for database to be fully ready
                Thread.sleep(10000);

                log.info("Loading historical data for current year...");
                int currentYear = LocalDate.now().getYear();
                int recordsLoaded = realTimeDataService.loadYearlyData(currentYear);
                log.info("Loaded {} historical records for year {}", recordsLoaded, currentYear);

                // Also load previous year for better backtesting
                int previousYear = currentYear - 1;
                int prevYearRecords = realTimeDataService.loadYearlyData(previousYear);
                log.info("Loaded {} historical records for year {}", prevYearRecords, previousYear);

                // Run initial data update
                log.info("Running initial real-time data update...");
                realTimeDataService.updateRealTimeData();

                // Run initial screening
                log.info("Running initial screening...");
                realTimeDataService.updateScreeningResults();

                // Generate initial predictions
                log.info("Generating initial predictions...");
                realTimeDataService.generateSignalPredictions();

                log.info("=== StockPulse Data Initialization Complete ===");

            } catch (Exception e) {
                log.error("Error during data initialization", e);
            }
        }, "data-init-thread").start();
    }
    
    @Scheduled(cron = "${app.scheduling.price-updates.cron:0 */5 * * * *}")
    public void updatePriceData() {
        log.info("Starting scheduled price data update...");
        try {
            realTimeDataService.updateRealTimeData();
            log.info("Scheduled price data update completed successfully");
        } catch (Exception e) {
            log.error("Error during scheduled price data update", e);
        }
    }
    
    @Scheduled(cron = "${app.scheduling.screening.cron:0 */15 * * * *}")
    public void updateScreeningData() {
        log.info("Starting scheduled screening data update...");
        try {
            realTimeDataService.updateScreeningResults();
            log.info("Scheduled screening data update completed successfully");
        } catch (Exception e) {
            log.error("Error during scheduled screening data update", e);
        }
    }
    
    @Scheduled(cron = "${app.scheduling.predictions.cron:0 */30 * * * *}")
    public void generatePredictions() {
        log.info("Starting scheduled predictions generation...");
        try {
            realTimeDataService.generateSignalPredictions();
            log.info("Scheduled predictions generation completed successfully");
        } catch (Exception e) {
            log.error("Error during scheduled predictions generation", e);
        }
    }
    
    @Scheduled(cron = "${app.scheduling.sentiment.cron:0 */10 * * * *}")
    public void updateSentimentAnalysis() {
        log.info("Starting scheduled sentiment analysis update...");
        try {
            sentimentAnalysisService.updateRecentSentimentAnalysis();
            log.info("Scheduled sentiment analysis update completed successfully");
        } catch (Exception e) {
            log.error("Error during scheduled sentiment analysis update", e);
        }
    }
    
    @Scheduled(cron = "${app.scheduling.news-fetch.cron:0 0 */2 * * *}")
    public void fetchNewsForAllTickers() {
        log.info("Starting scheduled news fetch for all tickers...");
        try {
            sentimentAnalysisService.analyzeActiveTickers();
            log.info("Scheduled news fetch for all tickers completed successfully");
        } catch (Exception e) {
            log.error("Error during scheduled news fetch", e);
        }
    }
    
    @Scheduled(fixedDelay = 300000) // 5 minutes
    public void healthCheck() {
        log.debug("Scheduled service health check - OK");
    }
}