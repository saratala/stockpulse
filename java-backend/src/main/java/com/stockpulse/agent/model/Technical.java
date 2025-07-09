package com.stockpulse.agent.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "technicals", indexes = {
    @Index(name = "idx_technicals_ticker_date", columnList = "ticker, date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(TechnicalId.class)
public class Technical {
    
    @Id
    @Column(nullable = false, length = 10)
    private String ticker;
    
    @Id
    @Column(nullable = false)
    private LocalDateTime date;
    
    @Column(name = "sma_20", precision = 12, scale = 4)
    private BigDecimal sma20;
    
    @Column(name = "sma_50", precision = 12, scale = 4)
    private BigDecimal sma50;
    
    @Column(name = "sma_200", precision = 12, scale = 4)
    private BigDecimal sma200;
    
    @Column(name = "ema_20", precision = 12, scale = 4)
    private BigDecimal ema20;
    
    @Column(name = "ema_50", precision = 12, scale = 4)
    private BigDecimal ema50;
    
    @Column(precision = 8, scale = 4)
    private BigDecimal rsi;
    
    @Column(precision = 12, scale = 6)
    private BigDecimal macd;
    
    @Column(name = "macd_signal", precision = 12, scale = 6)
    private BigDecimal macdSignal;
    
    @Column(name = "macd_hist", precision = 12, scale = 6)
    private BigDecimal macdHist;
    
    @Column(name = "stoch_k", precision = 8, scale = 4)
    private BigDecimal stochK;
    
    @Column(name = "stoch_d", precision = 8, scale = 4)
    private BigDecimal stochD;
    
    @Column(name = "bollinger_upper", precision = 12, scale = 4)
    private BigDecimal bollingerUpper;
    
    @Column(name = "bollinger_middle", precision = 12, scale = 4)
    private BigDecimal bollingerMiddle;
    
    @Column(name = "bollinger_lower", precision = 12, scale = 4)
    private BigDecimal bollingerLower;
    
    @Column(precision = 8, scale = 4)
    private BigDecimal adx;
    
    @Column(precision = 8, scale = 4)
    private BigDecimal cci;
    
    @Column(precision = 8, scale = 4)
    private BigDecimal willr;
    
    @Column(precision = 15, scale = 2)
    private BigDecimal obv;
    
    @Column(precision = 12, scale = 4)
    private BigDecimal atr;
}