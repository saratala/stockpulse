package com.stockpulse.agent.repository;

import com.stockpulse.agent.model.PredictionAccuracyDaily;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PredictionAccuracyDailyRepository extends JpaRepository<PredictionAccuracyDaily, Long> {

    Optional<PredictionAccuracyDaily> findByDateAndTickerAndTimeframe(LocalDate date, String ticker, String timeframe);

    List<PredictionAccuracyDaily> findByTickerOrderByDateDesc(String ticker);

    List<PredictionAccuracyDaily> findByTickerIsNullOrderByDateDesc();

    List<PredictionAccuracyDaily> findByTimeframeOrderByDateDesc(String timeframe);

    @Query("SELECT pad FROM PredictionAccuracyDaily pad WHERE pad.date >= :since AND pad.ticker IS NULL ORDER BY pad.date DESC")
    List<PredictionAccuracyDaily> findOverallAccuracySince(@Param("since") LocalDate since);

    @Query("SELECT pad FROM PredictionAccuracyDaily pad WHERE pad.date >= :since AND pad.ticker = :ticker ORDER BY pad.date DESC")
    List<PredictionAccuracyDaily> findTickerAccuracySince(@Param("ticker") String ticker, @Param("since") LocalDate since);

    @Query("SELECT AVG(pad.directionAccuracy) FROM PredictionAccuracyDaily pad WHERE pad.date >= :since AND pad.ticker IS NULL AND pad.timeframe = :timeframe")
    Double getAverageDirectionAccuracy(@Param("timeframe") String timeframe, @Param("since") LocalDate since);

    @Query("SELECT pad.ticker, AVG(pad.directionAccuracy) as avgAccuracy " +
           "FROM PredictionAccuracyDaily pad " +
           "WHERE pad.date >= :since AND pad.ticker IS NOT NULL AND pad.timeframe = :timeframe " +
           "GROUP BY pad.ticker " +
           "ORDER BY avgAccuracy DESC")
    List<Object[]> getTopPerformingTickers(@Param("timeframe") String timeframe, @Param("since") LocalDate since);

    @Query("SELECT pad.ticker, AVG(pad.directionAccuracy) as avgAccuracy " +
           "FROM PredictionAccuracyDaily pad " +
           "WHERE pad.date >= :since AND pad.ticker IS NOT NULL AND pad.timeframe = :timeframe " +
           "GROUP BY pad.ticker " +
           "ORDER BY avgAccuracy ASC")
    List<Object[]> getWorstPerformingTickers(@Param("timeframe") String timeframe, @Param("since") LocalDate since);
}
