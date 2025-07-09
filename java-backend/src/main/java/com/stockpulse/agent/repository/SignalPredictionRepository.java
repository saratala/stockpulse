package com.stockpulse.agent.repository;

import com.stockpulse.agent.model.SignalPrediction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SignalPredictionRepository extends JpaRepository<SignalPrediction, Long> {
    
    List<SignalPrediction> findByTicker(String ticker);
    
    @Query("SELECT sp FROM SignalPrediction sp WHERE sp.ticker = :ticker ORDER BY sp.timestamp DESC")
    List<SignalPrediction> findByTickerOrderByTimestampDesc(@Param("ticker") String ticker);
    
    @Query("SELECT sp FROM SignalPrediction sp WHERE sp.ticker = :ticker ORDER BY sp.timestamp DESC")
    Page<SignalPrediction> findByTickerOrderByTimestampDesc(@Param("ticker") String ticker, Pageable pageable);
    
    @Query("SELECT sp FROM SignalPrediction sp WHERE sp.ticker = :ticker AND sp.timestamp >= :startDate AND sp.timestamp <= :endDate ORDER BY sp.timestamp DESC")
    List<SignalPrediction> findByTickerAndTimestampBetween(@Param("ticker") String ticker, 
                                                          @Param("startDate") LocalDateTime startDate, 
                                                          @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT sp FROM SignalPrediction sp WHERE sp.timestamp >= :startDate ORDER BY sp.timestamp DESC")
    List<SignalPrediction> findRecentSignalPredictions(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT sp FROM SignalPrediction sp WHERE sp.timestamp >= :startDate ORDER BY sp.timestamp DESC")
    Page<SignalPrediction> findRecentSignalPredictions(@Param("startDate") LocalDateTime startDate, Pageable pageable);
    
    @Query("SELECT sp FROM SignalPrediction sp WHERE sp.screeningScore >= :minScore AND sp.timestamp >= :startDate ORDER BY sp.screeningScore DESC")
    List<SignalPrediction> findByScreeningScoreGreaterThanEqualAndTimestampAfter(@Param("minScore") BigDecimal minScore, 
                                                                                @Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT sp.signalType, COUNT(sp) FROM SignalPrediction sp WHERE sp.timestamp >= :startDate GROUP BY sp.signalType")
    List<Object[]> countBySignalTypeSince(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT COUNT(sp) FROM SignalPrediction sp WHERE sp.sentimentScore IS NOT NULL AND sp.sentimentScore <> 0 AND sp.timestamp >= :startDate")
    Long countSentimentEnhancedPredictions(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT COUNT(sp) FROM SignalPrediction sp WHERE sp.timestamp >= :startDate")
    Long countTotalSignalPredictions(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT sp FROM SignalPrediction sp WHERE sp.signalType = :signalType AND sp.timestamp >= :startDate ORDER BY sp.confidence DESC")
    List<SignalPrediction> findBySignalTypeAndTimestampAfterOrderByConfidenceDesc(@Param("signalType") SignalPrediction.SignalType signalType, 
                                                                                 @Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT sp FROM SignalPrediction sp WHERE sp.sector = :sector AND sp.timestamp >= :startDate ORDER BY sp.screeningScore DESC")
    List<SignalPrediction> findBySectorAndTimestampAfterOrderByScreeningScoreDesc(@Param("sector") String sector, 
                                                                                 @Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT AVG(sp.sentimentScore) FROM SignalPrediction sp WHERE sp.ticker = :ticker AND sp.sentimentScore IS NOT NULL AND sp.timestamp >= :startDate")
    BigDecimal findAverageSentimentByTickerSince(@Param("ticker") String ticker, @Param("startDate") LocalDateTime startDate);
}