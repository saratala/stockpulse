package com.stockpulse.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyComparison {
    private String ticker;
    private String testPeriod;
    private BigDecimal initialCapital;
    private List<BacktestResult> strategies;
    private BacktestResult bestStrategy;
    private BigDecimal averageReturn;
    private PerformanceMetrics overallMetrics;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceMetrics {
        private BigDecimal totalReturn;
        private BigDecimal annualizedReturn;
        private BigDecimal volatility;
        private BigDecimal sharpeRatio;
        private BigDecimal maxDrawdown;
        private BigDecimal winRate;
        private BigDecimal profitFactor;
        private Integer totalTrades;
        private String riskLevel;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StrategyRanking {
        private String strategyName;
        private BigDecimal totalReturn;
        private BigDecimal sharpeRatio;
        private BigDecimal maxDrawdown;
        private BigDecimal winRate;
        private Integer totalTrades;
        private String riskAssessment;
        private Integer rank;
    }
}