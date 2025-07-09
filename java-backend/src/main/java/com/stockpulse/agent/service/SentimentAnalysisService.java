package com.stockpulse.agent.service;

import com.stockpulse.agent.model.NewsArticle;
import com.stockpulse.agent.model.SentimentScore;
import com.stockpulse.agent.repository.NewsArticleRepository;
import com.stockpulse.agent.repository.SentimentScoreRepository;
import com.stockpulse.agent.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SentimentAnalysisService {
    
    private final NewsService newsService;
    private final LLMSentimentService llmSentimentService;
    private final NewsArticleRepository newsArticleRepository;
    private final SentimentScoreRepository sentimentScoreRepository;
    private final StockRepository stockRepository;
    
    @Value("${app.news.fetch.enabled:true}")
    private boolean newsFetchEnabled;
    
    @Value("${app.news.fetch.max-age-hours:48}")
    private int maxAgeHours;
    
    // Default tickers as a fallback
    private final List<String> defaultTickers = List.of(
        "AAPL", "GOOGL", "MSFT", "AMZN", "TSLA", "META", "NVDA", "NFLX"
    );
    
    /**
     * Perform comprehensive sentiment analysis for a specific ticker
     */
    @Transactional
    public Map<String, Object> analyzeSentimentForTicker(String ticker) {
        log.info("Starting sentiment analysis for ticker: {}", ticker);
        
        try {
            // Step 1: Fetch latest news for the ticker
            List<NewsArticle> newsArticles = null;
            if (newsFetchEnabled) {
                newsArticles = newsService.fetchNewsForTicker(ticker);
                log.info("Fetched {} news articles for ticker: {}", newsArticles.size(), ticker);
            } else {
                // Use existing news from database
                LocalDateTime since = LocalDateTime.now().minusHours(maxAgeHours);
                newsArticles = newsArticleRepository.findByTickerAndPublishedAtBetween(
                    ticker.toUpperCase(), since, LocalDateTime.now());
                log.info("Using {} existing news articles for ticker: {}", newsArticles.size(), ticker);
            }
            
            // Step 2: Analyze sentiment for fetched news
            List<SentimentScore> sentimentScores = llmSentimentService.analyzeSentimentForTicker(ticker, newsArticles);
            log.info("Analyzed sentiment for {} articles for ticker: {}", sentimentScores.size(), ticker);
            
            // Step 3: Get aggregated sentiment
            Map<String, Object> aggregatedSentiment = new HashMap<>(llmSentimentService.getAggregatedSentiment(ticker, 24));
            
            // Step 4: Add additional analysis data
            aggregatedSentiment.put("newsCount", newsArticles.size());
            aggregatedSentiment.put("sentimentScoresCount", sentimentScores.size());
            aggregatedSentiment.put("lastUpdated", LocalDateTime.now());
            
            log.info("Completed sentiment analysis for ticker: {}", ticker);
            return aggregatedSentiment;
            
        } catch (Exception e) {
            log.error("Error during sentiment analysis for ticker: {}", ticker, e);
            throw new RuntimeException("Failed to analyze sentiment for ticker: " + ticker, e);
        }
    }
    
    /**
     * Perform sentiment analysis for all configured tickers
     */
    @Transactional
    public Map<String, Map<String, Object>> analyzeAllTickers() {
        log.info("Starting sentiment analysis for all default tickers: {}", defaultTickers);
        
        Map<String, Map<String, Object>> results = defaultTickers.stream()
                .collect(Collectors.toMap(
                    ticker -> ticker,
                    this::analyzeSentimentForTicker
                ));
        
        log.info("Completed sentiment analysis for all {} tickers", defaultTickers.size());
        return results;
    }
    
    /**
     * Perform sentiment analysis for active tickers (tickers with recent stock data)
     */
    @Transactional
    public Map<String, Map<String, Object>> analyzeActiveTickers() {
        log.info("Starting sentiment analysis for active tickers");
        
        // Get tickers with recent stock data
        List<String> activeTickers = stockRepository.findAllTickers();
        
        if (activeTickers.isEmpty()) {
            log.warn("No active tickers found, using default tickers");
            activeTickers = defaultTickers;
        }
        
        Map<String, Map<String, Object>> results = activeTickers.stream()
                .limit(20) // Limit to avoid overwhelming the system
                .collect(Collectors.toMap(
                    ticker -> ticker,
                    this::analyzeSentimentForTicker
                ));
        
        log.info("Completed sentiment analysis for {} active tickers", results.size());
        return results;
    }
    
    /**
     * Update sentiment analysis for recent news
     */
    @Transactional
    public void updateRecentSentimentAnalysis() {
        log.info("Updating sentiment analysis for recent news");
        
        try {
            // Get recent news articles that don't have sentiment scores yet
            LocalDateTime since = LocalDateTime.now().minusHours(maxAgeHours);
            List<NewsArticle> recentNews = newsArticleRepository.findRecentNews(since);
            
            // Filter out articles that already have sentiment scores
            List<NewsArticle> unanalyzedNews = recentNews.stream()
                    .filter(article -> !sentimentScoreRepository.existsByNewsArticleId(article.getId()))
                    .collect(Collectors.toList());
            
            log.info("Found {} unanalyzed news articles", unanalyzedNews.size());
            
            if (!unanalyzedNews.isEmpty()) {
                // Analyze sentiment for unanalyzed news
                List<SentimentScore> newSentimentScores = llmSentimentService.analyzeSentimentBatch(unanalyzedNews);
                log.info("Generated {} new sentiment scores", newSentimentScores.size());
            }
            
        } catch (Exception e) {
            log.error("Error updating recent sentiment analysis", e);
            throw new RuntimeException("Failed to update recent sentiment analysis", e);
        }
    }
    
    /**
     * Get sentiment summary for all tickers
     */
    public Map<String, Object> getSentimentSummary(int hours) {
        log.info("Getting sentiment summary for last {} hours", hours);
        
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        // Get sentiment counts by ticker
        List<Object[]> sentimentByTicker = sentimentScoreRepository.findAverageSentimentByTickerSince(since);
        
        // Get total counts
        long totalSentiments = sentimentScoreRepository.count();
        long totalNews = newsArticleRepository.count();
        
        // Get recent sentiment scores
        List<SentimentScore> recentSentiments = sentimentScoreRepository.findRecentSentiments(since);
        
        // Calculate overall sentiment
        double overallSentiment = recentSentiments.stream()
                .mapToDouble(s -> s.getSentimentScore().doubleValue())
                .average()
                .orElse(0.0);
        
        Map<String, Object> summary = Map.of(
            "timePeriodHours", hours,
            "overallSentiment", overallSentiment,
            "totalSentiments", totalSentiments,
            "totalNews", totalNews,
            "recentSentiments", recentSentiments.size(),
            "sentimentByTicker", sentimentByTicker,
            "lastUpdated", LocalDateTime.now()
        );
        
        log.info("Generated sentiment summary with {} tickers", sentimentByTicker.size());
        return summary;
    }
    
    /**
     * Refresh news and sentiment for a specific ticker
     */
    @Transactional
    public Map<String, Object> refreshTickerSentiment(String ticker) {
        log.info("Refreshing news and sentiment for ticker: {}", ticker);
        
        try {
            // Force fetch new news
            List<NewsArticle> newsArticles = newsService.fetchNewsForTicker(ticker);
            
            // Analyze sentiment for all articles (including existing ones)
            List<SentimentScore> sentimentScores = llmSentimentService.analyzeSentimentForTicker(ticker, newsArticles);
            
            // Return updated analysis
            Map<String, Object> result = llmSentimentService.getAggregatedSentiment(ticker, 24);
            result.put("refreshedAt", LocalDateTime.now());
            result.put("newArticles", newsArticles.size());
            result.put("analyzedSentiments", sentimentScores.size());
            
            log.info("Refreshed sentiment analysis for ticker: {}", ticker);
            return result;
            
        } catch (Exception e) {
            log.error("Error refreshing sentiment for ticker: {}", ticker, e);
            throw new RuntimeException("Failed to refresh sentiment for ticker: " + ticker, e);
        }
    }
    
    /**
     * Get sentiment trend for a ticker over time
     */
    public Map<String, Object> getSentimentTrend(String ticker, int days) {
        log.info("Getting sentiment trend for ticker: {} over {} days", ticker, days);
        
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        
        List<SentimentScore> sentiments = sentimentScoreRepository.findByTickerAndPublishedAtBetween(
            ticker.toUpperCase(), since, LocalDateTime.now());
        
        // Group by day and calculate average sentiment
        Map<String, Double> dailySentiment = sentiments.stream()
                .collect(Collectors.groupingBy(
                    s -> s.getPublishedAt().toLocalDate().toString(),
                    Collectors.averagingDouble(s -> s.getSentimentScore().doubleValue())
                ));
        
        Map<String, Object> trend = Map.of(
            "ticker", ticker.toUpperCase(),
            "days", days,
            "dailySentiment", dailySentiment,
            "totalDataPoints", sentiments.size(),
            "generatedAt", LocalDateTime.now()
        );
        
        log.info("Generated sentiment trend for ticker: {} with {} data points", ticker, sentiments.size());
        return trend;
    }
}