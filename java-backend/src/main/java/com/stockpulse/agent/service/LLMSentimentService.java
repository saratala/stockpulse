package com.stockpulse.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stockpulse.agent.model.NewsArticle;
import com.stockpulse.agent.model.SentimentScore;
import com.stockpulse.agent.repository.SentimentScoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class LLMSentimentService {
    
    private final SentimentScoreRepository sentimentScoreRepository;
    private final ObjectMapper objectMapper;
    
    @Value("${app.llm.openai.api-key:}")
    private String openaiApiKey;
    
    @Value("${app.llm.anthropic.api-key:}")
    private String anthropicApiKey;
    
    @Value("${app.llm.enabled:true}")
    private boolean llmEnabled;
    
    @Value("${app.llm.provider:openai}")
    private String llmProvider;
    
    private final WebClient webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
            .build();
    
    /**
     * Analyze sentiment for a single news article
     */
    public SentimentScore analyzeSentiment(NewsArticle article) {
        log.debug("Analyzing sentiment for article: {}", article.getTitle());
        
        if (!llmEnabled) {
            log.debug("LLM is disabled, using fallback sentiment analysis");
            return analyzeSentimentFallback(article);
        }
        
        try {
            switch (llmProvider.toLowerCase()) {
                case "openai":
                    return analyzeWithOpenAI(article);
                case "anthropic":
                    return analyzeWithAnthropic(article);
                default:
                    log.warn("Unknown LLM provider: {}, using fallback", llmProvider);
                    return analyzeSentimentFallback(article);
            }
        } catch (Exception e) {
            log.error("Error analyzing sentiment with LLM, using fallback", e);
            return analyzeSentimentFallback(article);
        }
    }
    
    private SentimentScore analyzeWithOpenAI(NewsArticle article) {
        try {
            String prompt = buildSentimentPrompt(article);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-3.5-turbo");
            requestBody.put("messages", List.of(
                Map.of("role", "system", "content", "You are a financial sentiment analyzer. Analyze the sentiment of financial news articles and return a JSON response with sentiment score (-1 to 1) and confidence (0 to 1)."),
                Map.of("role", "user", "content", prompt)
            ));
            requestBody.put("max_tokens", 150);
            requestBody.put("temperature", 0.1);
            
            String response = webClient.post()
                    .uri("https://api.openai.com/v1/chat/completions")
                    .header("Authorization", "Bearer " + openaiApiKey)
                    .header("Content-Type", "application/json")
                    .body(Mono.just(requestBody), Map.class)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(30))
                    .block();
            
            return parseOpenAIResponse(response, article);
            
        } catch (Exception e) {
            log.error("Error calling OpenAI API for article: {}", article.getTitle(), e);
            return analyzeSentimentFallback(article);
        }
    }
    
    private SentimentScore analyzeWithAnthropic(NewsArticle article) {
        try {
            String prompt = buildSentimentPrompt(article);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "claude-3-haiku-20240307");
            requestBody.put("max_tokens", 150);
            requestBody.put("messages", List.of(
                Map.of("role", "user", "content", prompt)
            ));
            
            String response = webClient.post()
                    .uri("https://api.anthropic.com/v1/messages")
                    .header("x-api-key", anthropicApiKey)
                    .header("Content-Type", "application/json")
                    .header("anthropic-version", "2023-06-01")
                    .body(Mono.just(requestBody), Map.class)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(java.time.Duration.ofSeconds(30))
                    .block();
            
            return parseAnthropicResponse(response, article);
            
        } catch (Exception e) {
            log.error("Error calling Anthropic API for article: {}", article.getTitle(), e);
            return analyzeSentimentFallback(article);
        }
    }
    
    private String buildSentimentPrompt(NewsArticle article) {
        return String.format("""
            Analyze the sentiment of this financial news article for stock ticker %s:
            
            Title: %s
            Content: %s
            
            Please provide your analysis in the following JSON format:
            {
                "sentiment_score": <number between -1 and 1, where -1 is very negative, 0 is neutral, 1 is very positive>,
                "confidence": <number between 0 and 1, where 0 is not confident, 1 is very confident>,
                "reasoning": "<brief explanation of the sentiment analysis>"
            }
            
            Focus on how this news might affect the stock price and investor sentiment.
            """, 
            article.getTicker(), 
            article.getTitle(), 
            article.getContent()
        );
    }
    
    private SentimentScore parseOpenAIResponse(String response, NewsArticle article) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.get("choices");
            
            if (choices != null && choices.isArray() && choices.size() > 0) {
                String content = choices.get(0).get("message").get("content").asText();
                return parseSentimentFromContent(content, article);
            }
            
        } catch (Exception e) {
            log.error("Error parsing OpenAI response for article: {}", article.getTitle(), e);
        }
        
        return analyzeSentimentFallback(article);
    }
    
    private SentimentScore parseAnthropicResponse(String response, NewsArticle article) {
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode content = root.get("content");
            
            if (content != null && content.isArray() && content.size() > 0) {
                String text = content.get(0).get("text").asText();
                return parseSentimentFromContent(text, article);
            }
            
        } catch (Exception e) {
            log.error("Error parsing Anthropic response for article: {}", article.getTitle(), e);
        }
        
        return analyzeSentimentFallback(article);
    }
    
    private SentimentScore parseSentimentFromContent(String content, NewsArticle article) {
        try {
            // Try to extract JSON from the content
            int jsonStart = content.indexOf('{');
            int jsonEnd = content.lastIndexOf('}');
            
            if (jsonStart != -1 && jsonEnd != -1) {
                String jsonStr = content.substring(jsonStart, jsonEnd + 1);
                JsonNode sentimentJson = objectMapper.readTree(jsonStr);
                
                double sentimentScore = sentimentJson.get("sentiment_score").asDouble();
                double confidence = sentimentJson.get("confidence").asDouble();
                String reasoning = sentimentJson.has("reasoning") ? 
                    sentimentJson.get("reasoning").asText() : "LLM analysis";
                
                return SentimentScore.builder()
                        .ticker(article.getTicker())
                        .newsArticleId(article.getId())
                        .sentimentScore(BigDecimal.valueOf(sentimentScore))
                        .confidence(BigDecimal.valueOf(confidence))
                        .reasoning(reasoning)
                        .analysisProvider("LLM-" + llmProvider)
                        .publishedAt(article.getPublishedAt())
                        .polarity(calculatePolarity(sentimentScore))
                        .source("LLM Analysis")
                        .build();
            }
            
        } catch (Exception e) {
            log.error("Error parsing sentiment JSON from content: {}", content, e);
        }
        
        return analyzeSentimentFallback(article);
    }
    
    /**
     * Fallback sentiment analysis using keyword-based approach
     */
    private SentimentScore analyzeSentimentFallback(NewsArticle article) {
        log.debug("Using fallback sentiment analysis for article: {}", article.getTitle());
        
        String text = (article.getTitle() + " " + article.getContent()).toLowerCase();
        
        // Define sentiment keywords
        String[] positiveWords = {
            "profit", "gain", "up", "rise", "boost", "growth", "increase", "positive", 
            "bull", "rally", "surge", "outperform", "beat", "strong", "good", "excellent",
            "upgrade", "buy", "recommend", "bullish", "optimistic", "recovery", "expansion"
        };
        
        String[] negativeWords = {
            "loss", "down", "fall", "drop", "decline", "negative", "bear", "crash", 
            "plunge", "underperform", "miss", "weak", "bad", "poor", "downgrade", 
            "sell", "bearish", "pessimistic", "recession", "bankruptcy", "layoff"
        };
        
        int positiveCount = 0;
        int negativeCount = 0;
        
        for (String word : positiveWords) {
            if (text.contains(word)) {
                positiveCount++;
            }
        }
        
        for (String word : negativeWords) {
            if (text.contains(word)) {
                negativeCount++;
            }
        }
        
        // Calculate sentiment score
        double sentimentScore = 0.0;
        double confidence = 0.5;
        
        if (positiveCount > negativeCount) {
            sentimentScore = Math.min(1.0, (double) positiveCount / (positiveCount + negativeCount));
            confidence = 0.6;
        } else if (negativeCount > positiveCount) {
            sentimentScore = Math.max(-1.0, -((double) negativeCount / (positiveCount + negativeCount)));
            confidence = 0.6;
        }
        
        return SentimentScore.builder()
                .ticker(article.getTicker())
                .newsArticleId(article.getId())
                .sentimentScore(BigDecimal.valueOf(sentimentScore))
                .confidence(BigDecimal.valueOf(confidence))
                .reasoning("Keyword-based sentiment analysis")
                .analysisProvider("fallback")
                .publishedAt(article.getPublishedAt())
                .polarity(calculatePolarity(sentimentScore))
                .source("Keyword Analysis")
                .build();
    }
    
    /**
     * Analyze sentiment for multiple articles
     */
    public List<SentimentScore> analyzeSentimentBatch(List<NewsArticle> articles) {
        log.info("Analyzing sentiment for {} articles", articles.size());
        
        List<CompletableFuture<SentimentScore>> futures = articles.stream()
                .map(article -> CompletableFuture.supplyAsync(() -> analyzeSentiment(article)))
                .toList();
        
        List<SentimentScore> results = new ArrayList<>();
        
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .orTimeout(5, TimeUnit.MINUTES)
                    .join();
            
            for (CompletableFuture<SentimentScore> future : futures) {
                try {
                    SentimentScore score = future.get();
                    if (score != null) {
                        results.add(score);
                    }
                } catch (Exception e) {
                    log.error("Error getting sentiment analysis result", e);
                }
            }
            
        } catch (Exception e) {
            log.error("Error in batch sentiment analysis", e);
        }
        
        // Save results to database
        saveSentimentScores(results);
        
        log.info("Completed sentiment analysis for {} articles", results.size());
        return results;
    }
    
    /**
     * Analyze sentiment for a specific ticker
     */
    public List<SentimentScore> analyzeSentimentForTicker(String ticker, List<NewsArticle> articles) {
        log.info("Analyzing sentiment for ticker: {} with {} articles", ticker, articles.size());
        
        List<NewsArticle> tickerArticles = articles.stream()
                .filter(article -> article.getTicker().equalsIgnoreCase(ticker))
                .toList();
        
        return analyzeSentimentBatch(tickerArticles);
    }
    
    private void saveSentimentScores(List<SentimentScore> scores) {
        try {
            for (SentimentScore score : scores) {
                // Check if sentiment already exists for this article
                boolean exists = sentimentScoreRepository.existsByNewsArticleId(score.getNewsArticleId());
                
                if (!exists) {
                    sentimentScoreRepository.save(score);
                    log.debug("Saved sentiment score for article ID: {}", score.getNewsArticleId());
                } else {
                    log.debug("Sentiment score already exists for article ID: {}", score.getNewsArticleId());
                }
            }
        } catch (Exception e) {
            log.error("Error saving sentiment scores", e);
        }
    }
    
    /**
     * Get aggregate sentiment for a ticker
     */
    public Map<String, Object> getAggregatedSentiment(String ticker, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        List<SentimentScore> scores = sentimentScoreRepository
                .findByTickerAndPublishedAtBetween(ticker.toUpperCase(), since, LocalDateTime.now());
        
        if (scores.isEmpty()) {
            return Map.of(
                "ticker", ticker.toUpperCase(),
                "averageSentiment", 0.0,
                "totalAnalyzed", 0,
                "confidence", 0.0,
                "sentiment", "neutral"
            );
        }
        
        double avgSentiment = scores.stream()
                .mapToDouble(s -> s.getSentimentScore().doubleValue())
                .average()
                .orElse(0.0);
        
        double avgConfidence = scores.stream()
                .mapToDouble(s -> s.getConfidence().doubleValue())
                .average()
                .orElse(0.0);
        
        String sentiment = "neutral";
        if (avgSentiment > 0.1) {
            sentiment = "positive";
        } else if (avgSentiment < -0.1) {
            sentiment = "negative";
        }
        
        return Map.of(
            "ticker", ticker.toUpperCase(),
            "averageSentiment", avgSentiment,
            "totalAnalyzed", scores.size(),
            "confidence", avgConfidence,
            "sentiment", sentiment
        );
    }
    
    /**
     * Calculate polarity from sentiment score
     */
    private SentimentScore.Polarity calculatePolarity(double sentimentScore) {
        if (sentimentScore > 0.1) {
            return SentimentScore.Polarity.positive;
        } else if (sentimentScore < -0.1) {
            return SentimentScore.Polarity.negative;
        } else {
            return SentimentScore.Polarity.neutral;
        }
    }
}