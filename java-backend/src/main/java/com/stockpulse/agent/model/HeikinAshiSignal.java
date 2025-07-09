package com.stockpulse.agent.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "heikin_ashi_signals", indexes = {
    @Index(name = "idx_heikin_ashi_ticker_date", columnList = "ticker, analysis_date"),
    @Index(name = "idx_heikin_ashi_date", columnList = "analysis_date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HeikinAshiSignal {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 10)
    private String ticker;
    
    @Column(name = "analysis_date", nullable = false)
    private LocalDateTime analysisDate;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "primary_signal", nullable = false)
    private SignalType primarySignal;
    
    @Column(name = "primary_confidence", nullable = false, precision = 5, scale = 2)
    private BigDecimal primaryConfidence;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "reversal_signal")
    private ReversalSignal reversalSignal;
    
    @Column(name = "reversal_confidence", precision = 5, scale = 2)
    private BigDecimal reversalConfidence;
    
    @Column(name = "current_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal currentPrice;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "trend_strength")
    private TrendStrength trendStrength;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level")
    private RiskLevel riskLevel;
    
    @Column(name = "support_level", precision = 10, scale = 2)
    private BigDecimal supportLevel;
    
    @Column(name = "resistance_level", precision = 10, scale = 2)
    private BigDecimal resistanceLevel;
    
    @Column(name = "pivot_level", precision = 10, scale = 2)
    private BigDecimal pivotLevel;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    public enum SignalType {
        BULLISH, BEARISH, NEUTRAL
    }
    
    public enum ReversalSignal {
        BULLISH_REVERSAL, BEARISH_REVERSAL, NO_REVERSAL
    }
    
    public enum TrendStrength {
        STRONG, MODERATE, WEAK
    }
    
    public enum RiskLevel {
        LOW, MEDIUM, HIGH
    }
}