package com.stockpulse.agent.repository;

import com.stockpulse.agent.model.StockPrice;
import com.stockpulse.agent.model.StockPriceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockPriceRepository extends JpaRepository<StockPrice, StockPriceId> {
    
    List<StockPrice> findByTicker(String ticker);
    
    @Query("SELECT sp FROM StockPrice sp WHERE sp.ticker = :ticker ORDER BY sp.date DESC")
    List<StockPrice> findByTickerOrderByDateDesc(@Param("ticker") String ticker);
    
    @Query("SELECT sp FROM StockPrice sp WHERE sp.ticker = :ticker AND sp.date >= :startDate AND sp.date <= :endDate ORDER BY sp.date")
    List<StockPrice> findByTickerAndDateBetween(@Param("ticker") String ticker, 
                                               @Param("startDate") LocalDateTime startDate, 
                                               @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT sp FROM StockPrice sp WHERE sp.ticker = :ticker ORDER BY sp.date DESC LIMIT 1")
    Optional<StockPrice> findLatestByTicker(@Param("ticker") String ticker);
    
    @Query("SELECT sp FROM StockPrice sp WHERE sp.date >= :startDate ORDER BY sp.date DESC")
    List<StockPrice> findRecentPrices(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT DISTINCT sp.ticker FROM StockPrice sp WHERE sp.date >= :startDate")
    List<String> findDistinctTickersWithRecentData(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT sp FROM StockPrice sp WHERE sp.ticker IN :tickers ORDER BY sp.ticker, sp.date DESC")
    List<StockPrice> findByTickerInOrderByTickerAndDateDesc(@Param("tickers") List<String> tickers);
}