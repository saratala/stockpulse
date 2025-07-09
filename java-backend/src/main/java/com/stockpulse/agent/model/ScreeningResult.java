package com.stockpulse.agent.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "screening_results", indexes = {
    @Index(name = "idx_screening_ticker_date", columnList = "ticker, screening_date"),
    @Index(name = "idx_screening_date", columnList = "screening_date"),
    @Index(name = "idx_screening_score", columnList = "screening_score")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScreeningResult {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 10)
    private String ticker;
    
    @Column(nullable = false, length = 255)
    private String name;
    
    @Column(length = 100)
    private String sector;
    
    @Column(name = "screening_date", nullable = false)
    private LocalDateTime screeningDate;
    
    @Column(name = "current_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal currentPrice;
    
    @Column(name = "screening_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal screeningScore;
    
    @Column(name = "change_percent", precision = 5, scale = 2)
    private BigDecimal changePercent;
    
    @Column(nullable = false)
    private Long volume;
    
    @Column(name = "volume_ratio", precision = 5, scale = 2)
    private BigDecimal volumeRatio;
    
    @Column(name = "ema_stack_aligned")
    private Boolean emaStackAligned;
    
    @Column(name = "adx_strength", precision = 5, scale = 2)
    private BigDecimal adxStrength;
    
    @Column(name = "stoch_position", precision = 5, scale = 2)
    private BigDecimal stochPosition;
    
    @Column(precision = 5, scale = 2)
    private BigDecimal rsi;
    
    @Column(name = "market_cap")
    private Long marketCap;
    
    @Column(name = "pe_ratio", precision = 8, scale = 2)
    private BigDecimal peRatio;
    
    @Column(precision = 5, scale = 2)
    private BigDecimal beta;
    
    @Column(name = "fifty_two_week_high", precision = 10, scale = 2)
    private BigDecimal fiftyTwoWeekHigh;
    
    @Column(name = "fifty_two_week_low", precision = 10, scale = 2)
    private BigDecimal fiftyTwoWeekLow;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}