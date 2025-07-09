package com.stockpulse.agent.service;

import com.stockpulse.agent.dto.ScreeningResponse;
import com.stockpulse.agent.model.ScreeningResult;
import com.stockpulse.agent.repository.ScreeningResultRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScreeningService {
    
    private final ScreeningResultRepository screeningResultRepository;
    
    @Cacheable(value = "screening-results", key = "#minScore + '_' + #maxResults")
    public ScreeningResponse runStockScreener(int minScore, int maxResults, boolean includeSignals) {
        log.info("Running stock screener with minScore={}, maxResults={}, includeSignals={}", 
                minScore, maxResults, includeSignals);
        
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        
        // Get recent screening results
        List<ScreeningResult> results = screeningResultRepository
            .findByScoreGreaterThanEqualAndScreeningDateAfter(BigDecimal.valueOf(minScore), since);
        
        // Limit results
        List<ScreeningResult> limitedResults = results.stream()
            .limit(maxResults)
            .collect(Collectors.toList());
        
        // Calculate summary statistics
        Long strongBuy = screeningResultRepository.countStrongBuySignals(since);
        Long buy = screeningResultRepository.countBuySignals(since);
        Long hold = screeningResultRepository.countHoldSignals(since);
        
        ScreeningResponse.ScreeningSummary summary = ScreeningResponse.ScreeningSummary.builder()
            .strongBuy(strongBuy)
            .buy(buy)
            .hold(hold)
            .build();
        
        // Convert to DTOs
        List<ScreeningResponse.ScreeningCandidate> candidates = limitedResults.stream()
            .map(this::convertToScreeningCandidate)
            .collect(Collectors.toList());
        
        return ScreeningResponse.builder()
            .screeningDate(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .totalAnalyzed(results.size())
            .candidatesFound(candidates.size())
            .screeningSummary(summary)
            .candidates(candidates)
            .build();
    }
    
    private ScreeningResponse.ScreeningCandidate convertToScreeningCandidate(ScreeningResult result) {
        // Create signal analysis
        ScreeningResponse.SignalAnalysis signalAnalysis = ScreeningResponse.SignalAnalysis.builder()
            .ticker(result.getTicker())
            .primarySignal(determinePrimarySignal(result))
            .primaryConfidence(calculateConfidence(result))
            .reversalSignal("NO_REVERSAL")
            .reversalConfidence(50.0)
            .riskLevel(determineRiskLevel(result))
            .keyLevels(ScreeningResponse.KeyLevels.builder()
                .support(result.getCurrentPrice().doubleValue() * 0.97)
                .resistance(result.getCurrentPrice().doubleValue() * 1.03)
                .pivot(result.getCurrentPrice().doubleValue())
                .build())
            .build();
        
        return ScreeningResponse.ScreeningCandidate.builder()
            .ticker(result.getTicker())
            .name(result.getName())
            .sector(result.getSector())
            .price(result.getCurrentPrice().doubleValue())
            .screeningScore(result.getScreeningScore().doubleValue())
            .changePercent(result.getChangePercent() != null ? result.getChangePercent().doubleValue() : 0.0)
            .volume(result.getVolume())
            .volumeRatio(result.getVolumeRatio() != null ? result.getVolumeRatio().doubleValue() : 1.0)
            .emaStackAligned(result.getEmaStackAligned() != null ? result.getEmaStackAligned() : false)
            .adxStrength(result.getAdxStrength() != null ? result.getAdxStrength().doubleValue() : 0.0)
            .stochPosition(result.getStochPosition() != null ? result.getStochPosition().doubleValue() : 50.0)
            .rsi(result.getRsi() != null ? result.getRsi().doubleValue() : 50.0)
            .signalAnalysis(signalAnalysis)
            .build();
    }
    
    private String determinePrimarySignal(ScreeningResult result) {
        if (result.getScreeningScore().compareTo(BigDecimal.valueOf(80)) >= 0) {
            return "BULLISH";
        } else if (result.getScreeningScore().compareTo(BigDecimal.valueOf(60)) <= 0) {
            return "BEARISH";
        } else {
            return "NEUTRAL";
        }
    }
    
    private Double calculateConfidence(ScreeningResult result) {
        return Math.min(95.0, result.getScreeningScore().doubleValue() + 10.0);
    }
    
    private String determineRiskLevel(ScreeningResult result) {
        if (result.getChangePercent() != null && result.getChangePercent().abs().compareTo(BigDecimal.valueOf(3)) > 0) {
            return "HIGH";
        } else if (result.getChangePercent() != null && result.getChangePercent().abs().compareTo(BigDecimal.valueOf(1)) > 0) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
}