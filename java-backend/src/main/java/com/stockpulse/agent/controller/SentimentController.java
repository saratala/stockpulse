package com.stockpulse.agent.controller;

import com.stockpulse.agent.model.NewsArticle;
import com.stockpulse.agent.model.SentimentScore;
import com.stockpulse.agent.repository.NewsArticleRepository;
import com.stockpulse.agent.repository.SentimentScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sentiment")
@RequiredArgsConstructor
@Slf4j
public class SentimentController {
    
    private final SentimentScoreRepository sentimentScoreRepository;
    private final NewsArticleRepository newsArticleRepository;
    
    @GetMapping("/{ticker}")
    public ResponseEntity<Map<String, Object>> getSentimentByTicker(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "24") int hours) {
        
        log.info("Getting sentiment for ticker={}, hours={}", ticker, hours);
        
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        // Get sentiment scores
        List<SentimentScore> sentiments = sentimentScoreRepository
            .findByTickerAndPublishedAtBetween(ticker.toUpperCase(), since, LocalDateTime.now());
        
        // Get news articles
        List<NewsArticle> news = newsArticleRepository
            .findByTickerAndPublishedAtBetween(ticker.toUpperCase(), since, LocalDateTime.now());
        
        // Calculate average sentiment
        BigDecimal avgSentiment = sentimentScoreRepository
            .findAverageSentimentByTickerSince(ticker.toUpperCase(), since);
        
        // Get polarity counts
        List<Object[]> polarityCounts = sentimentScoreRepository
            .countSentimentPolarityByTickerSince(ticker.toUpperCase(), since);
        
        Map<String, Object> response = new HashMap<>();
        response.put("ticker", ticker.toUpperCase());
        response.put("timePeriodHours", hours);
        response.put("averageSentiment", avgSentiment != null ? avgSentiment.doubleValue() : 0.0);
        response.put("totalSentiments", sentiments.size());
        response.put("totalNews", news.size());
        response.put("sentiments", sentiments);
        response.put("news", news);
        response.put("polarityCounts", polarityCounts);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSentimentSummary(
            @RequestParam(defaultValue = "24") int hours) {
        
        log.info("Getting sentiment summary for hours={}", hours);
        
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        // Get average sentiment by ticker
        List<Object[]> avgSentimentByTicker = sentimentScoreRepository
            .findAverageSentimentByTickerSince(since);
        
        // Get recent sentiments
        Pageable pageable = PageRequest.of(0, 100);
        List<SentimentScore> recentSentiments = sentimentScoreRepository
            .findRecentSentiments(since, pageable).getContent();
        
        // Get recent news
        List<NewsArticle> recentNews = newsArticleRepository
            .findRecentNews(since, pageable).getContent();
        
        Map<String, Object> response = new HashMap<>();
        response.put("timePeriodHours", hours);
        response.put("averageSentimentByTicker", avgSentimentByTicker);
        response.put("recentSentiments", recentSentiments);
        response.put("recentNews", recentNews);
        response.put("totalSentiments", recentSentiments.size());
        response.put("totalNews", recentNews.size());
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/news/{ticker}")
    public ResponseEntity<List<NewsArticle>> getNewsByTicker(
            @PathVariable String ticker,
            @RequestParam(defaultValue = "24") int hours,
            @RequestParam(defaultValue = "50") int limit) {
        
        log.info("Getting news for ticker={}, hours={}, limit={}", ticker, hours, limit);
        
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        Pageable pageable = PageRequest.of(0, limit);
        List<NewsArticle> news = newsArticleRepository
            .findByTickerOrderByPublishedAtDesc(ticker.toUpperCase(), pageable).getContent();
        
        return ResponseEntity.ok(news);
    }
}