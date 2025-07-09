package com.stockpulse.agent.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "signal_predictions", indexes = {
    @Index(name = "idx_signal_predictions_ticker_time", columnList = "ticker, timestamp"),
    @Index(name = "idx_signal_predictions_signal_type", columnList = "signal_type, confidence"),
    @Index(name = "idx_signal_predictions_sentiment", columnList = "sentiment_score, sentiment_confidence")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SignalPrediction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 10)
    private String ticker;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "current_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal currentPrice;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false)
    private SignalType signalType;
    
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal confidence;
    
    @Column(name = "primary_reasons", columnDefinition = "TEXT[]")
    private String[] primaryReasons;
    
    @Column(name = "screening_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal screeningScore;
    
    @Column(columnDefinition = "TEXT")
    private String sector;
    
    @Column(name = "predicted_price_1h", precision = 12, scale = 4)
    private BigDecimal predictedPrice1h;
    
    @Column(name = "predicted_price_1d", precision = 12, scale = 4)
    private BigDecimal predictedPrice1d;
    
    @Column(name = "predicted_price_1w", precision = 12, scale = 4)
    private BigDecimal predictedPrice1w;
    
    @Column
    private Long volume;
    
    @Column(precision = 8, scale = 4)
    private BigDecimal rsi;
    
    @Column(precision = 12, scale = 6)
    private BigDecimal macd;
    
    @Column(name = "bollinger_position", precision = 5, scale = 2)
    private BigDecimal bollingerPosition;
    
    @Column(name = "sentiment_score", precision = 5, scale = 3)
    private BigDecimal sentimentScore;
    
    @Column(name = "sentiment_confidence", precision = 5, scale = 3)
    private BigDecimal sentimentConfidence;
    
    @Column(name = "sentiment_impact", length = 50)
    private String sentimentImpact;
    
    @Column(name = "news_count")
    private Integer newsCount;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    public enum SignalType {
        BULLISH, BEARISH, NEUTRAL
    }
}