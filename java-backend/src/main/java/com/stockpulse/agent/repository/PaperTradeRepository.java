package com.stockpulse.agent.repository;

import com.stockpulse.agent.model.PaperTrade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PaperTradeRepository extends JpaRepository<PaperTrade, Long> {

    List<PaperTrade> findByStatusOrderByEntryTimestampDesc(String status);

    List<PaperTrade> findByTickerOrderByEntryTimestampDesc(String ticker);

    List<PaperTrade> findByTickerAndStatusOrderByEntryTimestampDesc(String ticker, String status);

    @Query("SELECT pt FROM PaperTrade pt WHERE pt.status = 'OPEN' ORDER BY pt.entryTimestamp DESC")
    List<PaperTrade> findOpenTrades();

    @Query("SELECT pt FROM PaperTrade pt WHERE pt.status = 'CLOSED' AND pt.exitTimestamp >= :since ORDER BY pt.exitTimestamp DESC")
    List<PaperTrade> findClosedTradesSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(pt) FROM PaperTrade pt WHERE pt.status = 'CLOSED' AND pt.realizedPnl > 0")
    Long countWinningTrades();

    @Query("SELECT COUNT(pt) FROM PaperTrade pt WHERE pt.status = 'CLOSED' AND pt.realizedPnl <= 0")
    Long countLosingTrades();

    @Query("SELECT SUM(pt.realizedPnl) FROM PaperTrade pt WHERE pt.status = 'CLOSED'")
    Double getTotalRealizedPnl();

    @Query("SELECT AVG(pt.realizedPnlPercent) FROM PaperTrade pt WHERE pt.status = 'CLOSED' AND pt.realizedPnl > 0")
    Double getAverageWinPercent();

    @Query("SELECT AVG(pt.realizedPnlPercent) FROM PaperTrade pt WHERE pt.status = 'CLOSED' AND pt.realizedPnl <= 0")
    Double getAverageLossPercent();

    @Query("SELECT pt.ticker, COUNT(pt), SUM(pt.realizedPnl) " +
           "FROM PaperTrade pt " +
           "WHERE pt.status = 'CLOSED' " +
           "GROUP BY pt.ticker " +
           "ORDER BY SUM(pt.realizedPnl) DESC")
    List<Object[]> getPnlByTicker();
}
