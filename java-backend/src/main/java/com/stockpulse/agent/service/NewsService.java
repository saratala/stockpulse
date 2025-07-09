package com.stockpulse.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpulse.agent.model.NewsArticle;
import com.stockpulse.agent.repository.NewsArticleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsService {
    
    private final NewsArticleRepository newsArticleRepository;
    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    
    @Value("${app.news.api.alpha-vantage.key:}")
    private String alphaVantageApiKey;
    
    @Value("${app.news.api.newsapi.key:}")
    private String newsApiKey;
    
    @Value("${app.news.api.finnhub.key:}")
    private String finnhubApiKey;
    
    private final WebClient webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
            .build();
    
    /**
     * Fetch news for a specific ticker from multiple sources
     */
    public List<NewsArticle> fetchNewsForTicker(String ticker) {
        log.info("Fetching news for ticker: {}", ticker);
        
        List<NewsArticle> allNews = new ArrayList<>();
        
        // Try multiple news sources
        try {
            // Alpha Vantage News
            if (!alphaVantageApiKey.isEmpty()) {
                List<NewsArticle> alphaVantageNews = fetchFromAlphaVantage(ticker);
                allNews.addAll(alphaVantageNews);
            }
            
            // Finnhub News
            if (!finnhubApiKey.isEmpty()) {
                List<NewsArticle> finnhubNews = fetchFromFinnhub(ticker);
                allNews.addAll(finnhubNews);
            }
            
            // NewsAPI as fallback
            if (!newsApiKey.isEmpty()) {
                List<NewsArticle> newsApiNews = fetchFromNewsAPI(ticker);
                allNews.addAll(newsApiNews);
            }
            
            // If no API keys configured, use free sources
            if (allNews.isEmpty()) {
                List<NewsArticle> freeNews = fetchFromFreeSources(ticker);
                allNews.addAll(freeNews);
            }
            
        } catch (Exception e) {
            log.error("Error fetching news for ticker: {}", ticker, e);
        }
        
        // Remove duplicates and sort by date
        allNews = removeDuplicates(allNews);
        allNews.sort((a, b) -> b.getPublishedAt().compareTo(a.getPublishedAt()));
        
        // Save to database
        saveNewsArticles(allNews);
        
        log.info("Fetched {} news articles for ticker: {}", allNews.size(), ticker);
        return allNews;
    }
    
    private List<NewsArticle> fetchFromAlphaVantage(String ticker) {
        try {
            String url = String.format(
                "https://www.alphavantage.co/query?function=NEWS_SENTIMENT&tickers=%s&apikey=%s&limit=50",
                ticker, alphaVantageApiKey
            );
            
            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(30))
                    .block();
            
            return parseAlphaVantageResponse(response, ticker);
            
        } catch (Exception e) {
            log.error("Error fetching from Alpha Vantage for ticker: {}", ticker, e);
            return new ArrayList<>();
        }
    }
    
    private List<NewsArticle> parseAlphaVantageResponse(String response, String ticker) {
        List<NewsArticle> articles = new ArrayList<>();
        
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode feed = root.get("feed");
            
            if (feed != null && feed.isArray()) {
                for (JsonNode item : feed) {
                    NewsArticle article = NewsArticle.builder()
                            .ticker(ticker.toUpperCase())
                            .title(item.get("title").asText())
                            .content(item.get("summary").asText())
                            .url(item.get("url").asText())
                            .source("Alpha Vantage")
                            .publishedAt(parseDateTime(item.get("time_published").asText()))
                            .build();
                    
                    articles.add(article);
                }
            }
            
        } catch (Exception e) {
            log.error("Error parsing Alpha Vantage response for ticker: {}", ticker, e);
        }
        
        return articles;
    }
    
    private List<NewsArticle> fetchFromFinnhub(String ticker) {
        try {
            LocalDateTime from = LocalDateTime.now().minusDays(7);
            LocalDateTime to = LocalDateTime.now();
            
            String url = String.format(
                "https://finnhub.io/api/v1/company-news?symbol=%s&from=%s&to=%s&token=%s",
                ticker,
                from.format(DateTimeFormatter.ISO_LOCAL_DATE),
                to.format(DateTimeFormatter.ISO_LOCAL_DATE),
                finnhubApiKey
            );
            
            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(30))
                    .block();
            
            return parseFinnhubResponse(response, ticker);
            
        } catch (Exception e) {
            log.error("Error fetching from Finnhub for ticker: {}", ticker, e);
            return new ArrayList<>();
        }
    }
    
    private List<NewsArticle> parseFinnhubResponse(String response, String ticker) {
        List<NewsArticle> articles = new ArrayList<>();
        
        try {
            JsonNode root = objectMapper.readTree(response);
            
            if (root.isArray()) {
                for (JsonNode item : root) {
                    NewsArticle article = NewsArticle.builder()
                            .ticker(ticker.toUpperCase())
                            .title(item.get("headline").asText())
                            .content(item.get("summary").asText())
                            .url(item.get("url").asText())
                            .source("Finnhub")
                            .publishedAt(LocalDateTime.ofEpochSecond(
                                item.get("datetime").asLong(), 
                                0, 
                                ZoneOffset.UTC
                            ))
                            .build();
                    
                    articles.add(article);
                }
            }
            
        } catch (Exception e) {
            log.error("Error parsing Finnhub response for ticker: {}", ticker, e);
        }
        
        return articles;
    }
    
    private List<NewsArticle> fetchFromNewsAPI(String ticker) {
        try {
            String query = String.format("%s stock OR %s earnings OR %s financial", ticker, ticker, ticker);
            String url = String.format(
                "https://newsapi.org/v2/everything?q=%s&apiKey=%s&sortBy=publishedAt&pageSize=50",
                query, newsApiKey
            );
            
            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(30))
                    .block();
            
            return parseNewsAPIResponse(response, ticker);
            
        } catch (Exception e) {
            log.error("Error fetching from NewsAPI for ticker: {}", ticker, e);
            return new ArrayList<>();
        }
    }
    
    private List<NewsArticle> parseNewsAPIResponse(String response, String ticker) {
        List<NewsArticle> articles = new ArrayList<>();
        
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode articlesNode = root.get("articles");
            
            if (articlesNode != null && articlesNode.isArray()) {
                for (JsonNode item : articlesNode) {
                    NewsArticle article = NewsArticle.builder()
                            .ticker(ticker.toUpperCase())
                            .title(item.get("title").asText())
                            .content(item.get("description").asText())
                            .url(item.get("url").asText())
                            .source("NewsAPI")
                            .publishedAt(LocalDateTime.parse(
                                item.get("publishedAt").asText(),
                                DateTimeFormatter.ISO_DATE_TIME
                            ))
                            .build();
                    
                    articles.add(article);
                }
            }
            
        } catch (Exception e) {
            log.error("Error parsing NewsAPI response for ticker: {}", ticker, e);
        }
        
        return articles;
    }
    
    private List<NewsArticle> fetchFromFreeSources(String ticker) {
        List<NewsArticle> articles = new ArrayList<>();
        
        try {
            // Yahoo Finance RSS (free)
            String url = String.format("https://feeds.finance.yahoo.com/rss/2.0/headline?s=%s&region=US&lang=en-US", ticker);
            
            // Note: This is a simplified implementation
            // In a real application, you would parse RSS/XML properly
            log.info("Fetching from free sources for ticker: {} (RSS parsing not implemented)", ticker);
            
            // Create sample data for demonstration
            NewsArticle sampleArticle = NewsArticle.builder()
                    .ticker(ticker.toUpperCase())
                    .title("Sample news for " + ticker)
                    .content("This is sample news content for " + ticker + " from free sources")
                    .url("https://example.com/news/" + ticker)
                    .source("Free Sources")
                    .publishedAt(LocalDateTime.now())
                    .build();
            
            articles.add(sampleArticle);
            
        } catch (Exception e) {
            log.error("Error fetching from free sources for ticker: {}", ticker, e);
        }
        
        return articles;
    }
    
    private LocalDateTime parseDateTime(String dateStr) {
        try {
            // Handle different date formats
            if (dateStr.length() == 8) {
                // YYYYMMDD format
                return LocalDateTime.parse(dateStr + "T00:00:00");
            } else if (dateStr.contains("T")) {
                // ISO format
                return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME);
            } else {
                // Default to current time
                return LocalDateTime.now();
            }
        } catch (Exception e) {
            log.warn("Could not parse date: {}, using current time", dateStr);
            return LocalDateTime.now();
        }
    }
    
    private List<NewsArticle> removeDuplicates(List<NewsArticle> articles) {
        Map<String, NewsArticle> uniqueArticles = new HashMap<>();
        
        for (NewsArticle article : articles) {
            String key = article.getTitle() + "|" + article.getUrl();
            uniqueArticles.put(key, article);
        }
        
        return new ArrayList<>(uniqueArticles.values());
    }
    
    private void saveNewsArticles(List<NewsArticle> articles) {
        try {
            for (NewsArticle article : articles) {
                // Check if article already exists
                boolean exists = newsArticleRepository.existsByTitleAndUrl(
                    article.getTitle(), 
                    article.getUrl()
                );
                
                if (!exists) {
                    newsArticleRepository.save(article);
                }
            }
        } catch (Exception e) {
            log.error("Error saving news articles", e);
        }
    }
    
    /**
     * Fetch news for multiple tickers
     */
    public void fetchNewsForTickers(List<String> tickers) {
        log.info("Fetching news for {} tickers", tickers.size());
        
        List<CompletableFuture<Void>> futures = tickers.stream()
                .map(ticker -> CompletableFuture.runAsync(() -> fetchNewsForTicker(ticker)))
                .toList();
        
        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .orTimeout(5, TimeUnit.MINUTES)
                .join();
        
        log.info("Completed fetching news for all tickers");
    }
}