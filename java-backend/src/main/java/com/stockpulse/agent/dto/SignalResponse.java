package com.stockpulse.agent.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignalResponse {
    private String analysisDate;
    private Integer totalAnalyzed;
    private Integer signalsFound;
    private SignalSummary summary;
    private List<HeikinAshiSignal> signals;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignalSummary {
        private Long bullish;
        private Long bearish;
        private Long neutral;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HeikinAshiSignal {
        private String ticker;
        private String primarySignal;
        private Double primaryConfidence;
        private String reversalSignal;
        private Double reversalConfidence;
        private Double currentPrice;
        private String trendStrength;
        private String riskLevel;
        private KeyLevels keyLevels;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KeyLevels {
        private Double support;
        private Double resistance;
    }
}