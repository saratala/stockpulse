package com.stockpulse.agent.service;

import com.stockpulse.agent.dto.PredictionResponse;
import com.stockpulse.agent.dto.PredictionSummaryResponse;
import com.stockpulse.agent.model.SignalPrediction;
import com.stockpulse.agent.repository.SignalPredictionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PredictionService {
    
    private final SignalPredictionRepository signalPredictionRepository;
    
    @Cacheable(value = "prediction-history", key = "#ticker + '_' + #hours + '_' + #limit")
    public PredictionResponse getPredictionHistory(String ticker, int hours, int limit) {
        log.info("Getting prediction history for ticker={}, hours={}, limit={}", ticker, hours, limit);
        
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        List<SignalPrediction> predictions;
        if (ticker != null && !ticker.isEmpty()) {
            predictions = signalPredictionRepository.findByTickerAndTimestampBetween(
                ticker.toUpperCase(), since, LocalDateTime.now());
        } else {
            Pageable pageable = PageRequest.of(0, limit);
            predictions = signalPredictionRepository.findRecentSignalPredictions(since, pageable).getContent();
        }
        
        // Convert to DTOs
        List<PredictionResponse.PredictionData> predictionDtos = predictions.stream()
            .map(this::convertToPredictionDto)
            .collect(Collectors.toList());
        
        return PredictionResponse.builder()
            .totalPredictions(predictionDtos.size())
            .predictions(predictionDtos)
            .build();
    }
    
    @Cacheable(value = "prediction-summary", key = "#hours")
    public PredictionSummaryResponse getPredictionSummary(int hours) {
        log.info("Getting prediction summary for hours={}", hours);
        
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        // Get signal type counts
        List<Object[]> signalTypeCounts = signalPredictionRepository.countBySignalTypeSince(since);
        
        List<PredictionSummaryResponse.SignalTypeSummary> summaryList = signalTypeCounts.stream()
            .map(row -> {
                SignalPrediction.SignalType signalType = (SignalPrediction.SignalType) row[0];
                Long count = (Long) row[1];
                
                // Get predictions for this signal type to calculate averages
                List<SignalPrediction> typepredictions = signalPredictionRepository
                    .findBySignalTypeAndTimestampAfterOrderByConfidenceDesc(signalType, since);
                
                Double avgConfidence = typepredictions.stream()
                    .mapToDouble(p -> p.getConfidence().doubleValue())
                    .average()
                    .orElse(0.0);
                
                Double avgScreeningScore = typepredictions.stream()
                    .mapToDouble(p -> p.getScreeningScore().doubleValue())
                    .average()
                    .orElse(0.0);
                
                Integer uniqueTickers = (int) typepredictions.stream()
                    .map(SignalPrediction::getTicker)
                    .distinct()
                    .count();
                
                return PredictionSummaryResponse.SignalTypeSummary.builder()
                    .signalType(signalType.name())
                    .count(count)
                    .avgConfidence(avgConfidence)
                    .avgScreeningScore(avgScreeningScore)
                    .uniqueTickers(uniqueTickers)
                    .build();
            })
            .collect(Collectors.toList());
        
        return PredictionSummaryResponse.builder()
            .timePeriodHours(hours)
            .summary(summaryList)
            .build();
    }
    
    @Cacheable(value = "ticker-predictions", key = "#ticker + '_' + #hours")
    public PredictionResponse getTickerPredictions(String ticker, int hours) {
        log.info("Getting ticker predictions for ticker={}, hours={}", ticker, hours);
        
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        
        List<SignalPrediction> predictions = signalPredictionRepository
            .findByTickerAndTimestampBetween(ticker.toUpperCase(), since, LocalDateTime.now());
        
        // Convert to DTOs
        List<PredictionResponse.PredictionData> predictionDtos = predictions.stream()
            .map(this::convertToPredictionDto)
            .collect(Collectors.toList());
        
        return PredictionResponse.builder()
            .totalPredictions(predictionDtos.size())
            .predictions(predictionDtos)
            .build();
    }
    
    private PredictionResponse.PredictionData convertToPredictionDto(SignalPrediction prediction) {
        return PredictionResponse.PredictionData.builder()
            .ticker(prediction.getTicker())
            .timestamp(prediction.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .currentPrice(prediction.getCurrentPrice().doubleValue())
            .signalType(prediction.getSignalType().name())
            .confidence(prediction.getConfidence().doubleValue())
            .predictedPrice1h(prediction.getPredictedPrice1h() != null ? prediction.getPredictedPrice1h().doubleValue() : null)
            .predictedPrice1d(prediction.getPredictedPrice1d() != null ? prediction.getPredictedPrice1d().doubleValue() : null)
            .predictedPrice1w(prediction.getPredictedPrice1w() != null ? prediction.getPredictedPrice1w().doubleValue() : null)
            .volume(prediction.getVolume())
            .rsi(prediction.getRsi() != null ? prediction.getRsi().doubleValue() : null)
            .macd(prediction.getMacd() != null ? prediction.getMacd().doubleValue() : null)
            .bollingerPosition(prediction.getBollingerPosition() != null ? prediction.getBollingerPosition().doubleValue() : null)
            .screeningScore(prediction.getScreeningScore().doubleValue())
            .sector(prediction.getSector())
            .primaryReasons(prediction.getPrimaryReasons() != null ? Arrays.asList(prediction.getPrimaryReasons()) : null)
            .sentimentScore(prediction.getSentimentScore() != null ? prediction.getSentimentScore().doubleValue() : null)
            .sentimentConfidence(prediction.getSentimentConfidence() != null ? prediction.getSentimentConfidence().doubleValue() : null)
            .sentimentImpact(prediction.getSentimentImpact())
            .newsCount(prediction.getNewsCount())
            .build();
    }
}