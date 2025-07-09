package com.stockpulse.agent.repository;

import com.stockpulse.agent.model.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {
    
    Optional<Stock> findByTicker(String ticker);
    
    List<Stock> findBySector(String sector);
    
    @Query("SELECT s.ticker FROM Stock s")
    List<String> findAllTickers();
    
    @Query("SELECT s FROM Stock s WHERE s.ticker IN :tickers")
    List<Stock> findByTickerIn(List<String> tickers);
    
    boolean existsByTicker(String ticker);
}