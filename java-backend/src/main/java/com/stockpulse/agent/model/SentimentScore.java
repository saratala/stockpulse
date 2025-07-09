package com.stockpulse.agent.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sentiment_scores", indexes = {
    @Index(name = "idx_sentiment_ticker_date", columnList = "ticker, created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SentimentScore {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 10)
    private String ticker;
    
    @Column(name = "article_id")
    private Long articleId;
    
    @Column(name = "news_article_id")
    private Long newsArticleId;
    
    @Column(name = "sentiment_score", nullable = false, precision = 5, scale = 3)
    private BigDecimal sentimentScore;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Polarity polarity;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    @Column(columnDefinition = "TEXT")
    private String reasoning;
    
    @Column(name = "analysis_provider")
    private String analysisProvider;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String source;
    
    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;
    
    @Column(nullable = false, precision = 5, scale = 3)
    private BigDecimal confidence;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    public enum Polarity {
        positive, neutral, negative
    }
}