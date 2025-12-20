package com.stockpulse.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestResult {
    private String ticker;
    private String strategy;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private BigDecimal initialCapital;
    private BigDecimal finalValue;
    private BigDecimal totalReturn; // Percentage
    private BigDecimal buyHoldReturn; // Buy and hold comparison
    private Integer numberOfTrades;
    private BigDecimal winRate; // Percentage of winning trades
    private BigDecimal profitFactor; // Gross profit / Gross loss
    private BigDecimal sharpeRatio;
    private BigDecimal maxDrawdown;
    private List<Trade> trades;
    private String error;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Trade {
        private LocalDateTime date;
        private String type; // BUY or SELL
        private BigDecimal price;
        private BigDecimal shares;
        private BigDecimal value;
        private String reason;
        private BigDecimal profit; // For SELL trades
        private BigDecimal profitPercent; // For SELL trades
    }
}