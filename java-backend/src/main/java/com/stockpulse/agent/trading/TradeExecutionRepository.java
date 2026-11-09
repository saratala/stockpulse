package com.stockpulse.agent.trading;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TradeExecutionRepository extends JpaRepository<TradeExecution, Long> {

    @Query("SELECT t FROM TradeExecution t ORDER BY t.createdAt DESC")
    List<TradeExecution> findRecent(Pageable pageable);

    @Query("SELECT t FROM TradeExecution t WHERE t.ticker = :ticker ORDER BY t.createdAt DESC")
    List<TradeExecution> findByTicker(@Param("ticker") String ticker, Pageable pageable);

    boolean existsBySignalPredictionId(Long signalPredictionId);

    @Query("SELECT COUNT(t) FROM TradeExecution t WHERE t.action = :action AND t.createdAt >= :since")
    long countByActionSince(@Param("action") TradeExecution.Action action, @Param("since") LocalDateTime since);
}
