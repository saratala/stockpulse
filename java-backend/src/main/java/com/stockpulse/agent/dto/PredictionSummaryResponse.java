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
public class PredictionSummaryResponse {
    private Integer timePeriodHours;
    private List<SignalTypeSummary> summary;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SignalTypeSummary {
        private String signalType;
        private Long count;
        private Double avgConfidence;
        private Double avgScreeningScore;
        private Integer uniqueTickers;
    }
}