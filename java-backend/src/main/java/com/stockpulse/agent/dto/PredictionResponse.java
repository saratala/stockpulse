package com.stockpulse.agent.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionResponse {
    private Integer totalPredictions;
    private List<PredictionData> predictions;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PredictionData {
        private String ticker;
        private String timestamp;
        private Double currentPrice;
        private String signalType;
        private Double confidence;
        private Double predictedPrice1h;
        private Double predictedPrice1d;
        private Double predictedPrice1w;
        private Long volume;
        private Double rsi;
        private Double macd;
        private Double bollingerPosition;
        private Double screeningScore;
        private String sector;
        private List<String> primaryReasons;
        private Double sentimentScore;
        private Double sentimentConfidence;
        private String sentimentImpact;
        private Integer newsCount;
    }
}