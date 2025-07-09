package com.stockpulse.agent.repository;

import com.stockpulse.agent.model.Technical;
import com.stockpulse.agent.model.TechnicalId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TechnicalRepository extends JpaRepository<Technical, TechnicalId> {
    
    List<Technical> findByTicker(String ticker);
    
    @Query("SELECT t FROM Technical t WHERE t.ticker = :ticker ORDER BY t.date DESC")
    List<Technical> findByTickerOrderByDateDesc(@Param("ticker") String ticker);
    
    @Query("SELECT t FROM Technical t WHERE t.ticker = :ticker AND t.date >= :startDate AND t.date <= :endDate ORDER BY t.date")
    List<Technical> findByTickerAndDateBetween(@Param("ticker") String ticker, 
                                              @Param("startDate") LocalDateTime startDate, 
                                              @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT t FROM Technical t WHERE t.ticker = :ticker ORDER BY t.date DESC LIMIT 1")
    Optional<Technical> findLatestByTicker(@Param("ticker") String ticker);
    
    @Query("SELECT t FROM Technical t WHERE t.date >= :startDate ORDER BY t.date DESC")
    List<Technical> findRecentTechnicals(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT DISTINCT t.ticker FROM Technical t WHERE t.date >= :startDate")
    List<String> findDistinctTickersWithRecentData(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT t FROM Technical t WHERE t.ticker IN :tickers ORDER BY t.ticker, t.date DESC")
    List<Technical> findByTickerInOrderByTickerAndDateDesc(@Param("tickers") List<String> tickers);
}