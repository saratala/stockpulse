package com.stockpulse.agent.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreeningResponse {
    private String screeningDate;
    private Integer totalAnalyzed;
    private Integer candidatesFound;
    private ScreeningSummary screeningSummary;
    private List<ScreeningCandidate> candidates;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScreeningSummary {
        private Long strongBuy;
        private Long buy;
        private Long hold;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScreeningCandidate {
        private String ticker;
        private String name;
        private String sector;
        private Double price;
        private Double screeningScore;
        private Double changePercent;
        private Long volume;
        private Double volumeRatio;
        private Boolean emaStackAligned;
        private Double adxStrength;
        private Double stochPosition;
        private Double rsi;
        private SignalAnalysis signalAnalysis;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignalAnalysis {
        private String ticker;
        private String primarySignal;
        private Double primaryConfidence;
        private String reversalSignal;
        private Double reversalConfidence;
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
        private Double pivot;
    }
}