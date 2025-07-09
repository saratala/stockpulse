package com.stockpulse.agent.repository;

import com.stockpulse.agent.model.HeikinAshiSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface HeikinAshiSignalRepository extends JpaRepository<HeikinAshiSignal, Long> {
    
    List<HeikinAshiSignal> findByTicker(String ticker);
    
    @Query("SELECT h FROM HeikinAshiSignal h WHERE h.ticker = :ticker ORDER BY h.analysisDate DESC")
    List<HeikinAshiSignal> findByTickerOrderByAnalysisDateDesc(@Param("ticker") String ticker);
    
    @Query("SELECT h FROM HeikinAshiSignal h WHERE h.analysisDate >= :since ORDER BY h.analysisDate DESC")
    List<HeikinAshiSignal> findRecentSignals(@Param("since") LocalDateTime since);
    
    @Query("SELECT h FROM HeikinAshiSignal h WHERE h.primaryConfidence >= :minConfidence AND h.analysisDate >= :since ORDER BY h.primaryConfidence DESC")
    List<HeikinAshiSignal> findByConfidenceGreaterThanEqualAndAnalysisDateAfter(@Param("minConfidence") BigDecimal minConfidence, @Param("since") LocalDateTime since);
    
    @Query("SELECT h.primarySignal, COUNT(h) FROM HeikinAshiSignal h WHERE h.analysisDate >= :since GROUP BY h.primarySignal")
    List<Object[]> countByPrimarySignalSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(h) FROM HeikinAshiSignal h WHERE h.primarySignal = 'BULLISH' AND h.analysisDate >= :since")
    Long countBullishSignals(@Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(h) FROM HeikinAshiSignal h WHERE h.primarySignal = 'BEARISH' AND h.analysisDate >= :since")
    Long countBearishSignals(@Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(h) FROM HeikinAshiSignal h WHERE h.primarySignal = 'NEUTRAL' AND h.analysisDate >= :since")
    Long countNeutralSignals(@Param("since") LocalDateTime since);
}