package com.stockpulse.agent.service;

import com.stockpulse.agent.dto.SignalResponse;
import com.stockpulse.agent.model.HeikinAshiSignal;
import com.stockpulse.agent.repository.HeikinAshiSignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SignalService {
    
    private final HeikinAshiSignalRepository heikinAshiSignalRepository;
    
    @Cacheable(value = "heikin-ashi-signals", key = "#tickers + '_' + #period + '_' + #minConfidence")
    public SignalResponse getHeikinAshiSignals(String tickers, String period, int minConfidence) {
        log.info("Getting Heikin Ashi signals for tickers={}, period={}, minConfidence={}", 
                tickers, period, minConfidence);
        
        List<String> tickerList;
        if (tickers != null && !tickers.isEmpty()) {
            tickerList = Arrays.stream(tickers.split(","))
                .map(String::trim)
                .map(String::toUpperCase)
                .collect(Collectors.toList());
        } else {
            tickerList = Arrays.asList("AAPL", "MSFT", "GOOGL", "AMZN", "TSLA");
        }
        
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        
        // Get recent signals
        List<HeikinAshiSignal> signals = heikinAshiSignalRepository
            .findByConfidenceGreaterThanEqualAndAnalysisDateAfter(BigDecimal.valueOf(minConfidence), since);
        
        // Filter by tickers
        List<HeikinAshiSignal> filteredSignals = signals.stream()
            .filter(signal -> tickerList.contains(signal.getTicker()))
            .collect(Collectors.toList());
        
        // Calculate summary statistics
        Long bullish = heikinAshiSignalRepository.countBullishSignals(since);
        Long bearish = heikinAshiSignalRepository.countBearishSignals(since);
        Long neutral = heikinAshiSignalRepository.countNeutralSignals(since);
        
        SignalResponse.SignalSummary summary = SignalResponse.SignalSummary.builder()
            .bullish(bullish)
            .bearish(bearish)
            .neutral(neutral)
            .build();
        
        // Convert to DTOs
        List<SignalResponse.HeikinAshiSignal> signalDtos = filteredSignals.stream()
            .map(this::convertToHeikinAshiSignalDto)
            .collect(Collectors.toList());
        
        return SignalResponse.builder()
            .analysisDate(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .totalAnalyzed(tickerList.size())
            .signalsFound(signalDtos.size())
            .summary(summary)
            .signals(signalDtos)
            .build();
    }
    
    private SignalResponse.HeikinAshiSignal convertToHeikinAshiSignalDto(HeikinAshiSignal signal) {
        return SignalResponse.HeikinAshiSignal.builder()
            .ticker(signal.getTicker())
            .primarySignal(signal.getPrimarySignal().name())
            .primaryConfidence(signal.getPrimaryConfidence().doubleValue())
            .reversalSignal(signal.getReversalSignal() != null ? signal.getReversalSignal().name() : "NO_REVERSAL")
            .reversalConfidence(signal.getReversalConfidence() != null ? signal.getReversalConfidence().doubleValue() : 50.0)
            .currentPrice(signal.getCurrentPrice().doubleValue())
            .trendStrength(signal.getTrendStrength() != null ? signal.getTrendStrength().name() : "MODERATE")
            .riskLevel(signal.getRiskLevel() != null ? signal.getRiskLevel().name() : "MEDIUM")
            .keyLevels(SignalResponse.KeyLevels.builder()
                .support(signal.getSupportLevel() != null ? signal.getSupportLevel().doubleValue() : 0.0)
                .resistance(signal.getResistanceLevel() != null ? signal.getResistanceLevel().doubleValue() : 0.0)
                .build())
            .build();
    }
}