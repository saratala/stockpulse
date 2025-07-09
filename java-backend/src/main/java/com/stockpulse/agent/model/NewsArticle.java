package com.stockpulse.agent.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "news_articles", indexes = {
    @Index(name = "idx_news_ticker_date", columnList = "ticker, published_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsArticle {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 10)
    private String ticker;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;
    
    @Column(nullable = false, unique = true, columnDefinition = "TEXT")
    private String url;
    
    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String source;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}