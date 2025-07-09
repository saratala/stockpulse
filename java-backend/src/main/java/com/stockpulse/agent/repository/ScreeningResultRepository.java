package com.stockpulse.agent.repository;

import com.stockpulse.agent.model.ScreeningResult;
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
public interface ScreeningResultRepository extends JpaRepository<ScreeningResult, Long> {
    
    @Query("SELECT sr FROM ScreeningResult sr WHERE sr.screeningDate >= :since ORDER BY sr.screeningScore DESC")
    List<ScreeningResult> findRecentScreeningResults(@Param("since") LocalDateTime since);
    
    @Query("SELECT sr FROM ScreeningResult sr WHERE sr.screeningDate >= :since ORDER BY sr.screeningScore DESC")
    Page<ScreeningResult> findRecentScreeningResults(@Param("since") LocalDateTime since, Pageable pageable);
    
    @Query("SELECT sr FROM ScreeningResult sr WHERE sr.screeningScore >= :minScore AND sr.screeningDate >= :since ORDER BY sr.screeningScore DESC")
    List<ScreeningResult> findByScoreGreaterThanEqualAndScreeningDateAfter(@Param("minScore") BigDecimal minScore, @Param("since") LocalDateTime since);
    
    @Query("SELECT sr FROM ScreeningResult sr WHERE sr.ticker = :ticker ORDER BY sr.screeningDate DESC")
    List<ScreeningResult> findByTickerOrderByScreeningDateDesc(@Param("ticker") String ticker);
    
    @Query("SELECT sr FROM ScreeningResult sr WHERE sr.sector = :sector AND sr.screeningDate >= :since ORDER BY sr.screeningScore DESC")
    List<ScreeningResult> findBySectorAndScreeningDateAfter(@Param("sector") String sector, @Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(sr) FROM ScreeningResult sr WHERE sr.screeningScore >= 90 AND sr.screeningDate >= :since")
    Long countStrongBuySignals(@Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(sr) FROM ScreeningResult sr WHERE sr.screeningScore >= 80 AND sr.screeningScore < 90 AND sr.screeningDate >= :since")
    Long countBuySignals(@Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(sr) FROM ScreeningResult sr WHERE sr.screeningScore >= 70 AND sr.screeningScore < 80 AND sr.screeningDate >= :since")
    Long countHoldSignals(@Param("since") LocalDateTime since);
}