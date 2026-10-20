package com.stockpulse.agent.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Entity for storing market-wide news and economic events.
 * These are macro events that affect the entire market (Fed decisions, employment reports, etc.)
 * rather than individual stocks.
 */
@Entity
@Table(name = "market_news", indexes = {
    @Index(name = "idx_market_news_category", columnList = "category"),
    @Index(name = "idx_market_news_published", columnList = "published_at"),
    @Index(name = "idx_market_news_impact", columnList = "impact_level")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarketNews {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String title;

    @Column(nullable = false, unique = true, columnDefinition = "TEXT")
    private String url;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(nullable = false)
    private String source;

    @Column(columnDefinition = "TEXT")
    private String content;

    /**
     * Category of the market news/event
     * Examples: FED, EMPLOYMENT, CPI, GDP, EARNINGS_SEASON, GEOPOLITICAL, MARKET_OUTLOOK
     */
    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private MarketNewsCategory category;

    /**
     * Impact level of the event on the market
     */
    @Column(name = "impact_level", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ImpactLevel impactLevel;

    /**
     * Comma-separated list of affected sectors (nullable)
     * Examples: "TECH,FINANCIALS", "ENERGY,UTILITIES", null for broad market impact
     */
    @Column(name = "affected_sectors", length = 200)
    private String affectedSectors;

    /**
     * Sentiment of the news (-1.0 to 1.0, null if not analyzed)
     */
    private Double sentiment;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Categories for market-wide news and events
     */
    public enum MarketNewsCategory {
        FED,                // Federal Reserve decisions, speeches, minutes
        EMPLOYMENT,         // Jobs report, unemployment claims, NFP
        CPI,                // Consumer Price Index, inflation data
        GDP,                // Gross Domestic Product reports
        PPI,                // Producer Price Index
        RETAIL_SALES,       // Retail sales data
        HOUSING,            // Housing starts, home sales
        MANUFACTURING,      // ISM, PMI data
        CONSUMER_SENTIMENT, // Consumer confidence, sentiment surveys
        EARNINGS_SEASON,    // Broad earnings season news
        GEOPOLITICAL,       // Trade wars, sanctions, global events
        MARKET_OUTLOOK,     // General market analysis/outlook
        TREASURY,           // Treasury yields, bond market
        CRYPTO,             // Crypto market news affecting stocks
        OTHER               // Other macro news
    }

    /**
     * Impact level of market events
     */
    public enum ImpactLevel {
        HIGH,       // Major market-moving events (Fed rate decisions, NFP)
        MEDIUM,     // Significant but less impactful events
        LOW         // Minor economic data releases
    }
}
