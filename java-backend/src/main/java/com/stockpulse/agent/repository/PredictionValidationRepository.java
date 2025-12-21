package com.stockpulse.agent.repository;

import com.stockpulse.agent.model.PredictionValidation;
import com.stockpulse.agent.model.PredictionValidationId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PredictionValidationRepository extends JpaRepository<PredictionValidation, PredictionValidationId> {

    List<PredictionValidation> findByTickerOrderByValidationTimestampDesc(String ticker);

    List<PredictionValidation> findByTimeframeOrderByValidationTimestampDesc(String timeframe);

    List<PredictionValidation> findByTickerAndTimeframeOrderByValidationTimestampDesc(String ticker, String timeframe);

    @Query("SELECT pv FROM PredictionValidation pv WHERE pv.validationTimestamp >= :since ORDER BY pv.validationTimestamp DESC")
    List<PredictionValidation> findRecentValidations(@Param("since") LocalDateTime since);

    @Query("SELECT pv FROM PredictionValidation pv WHERE pv.ticker = :ticker AND pv.validationTimestamp >= :since ORDER BY pv.validationTimestamp DESC")
    List<PredictionValidation> findRecentValidationsForTicker(@Param("ticker") String ticker, @Param("since") LocalDateTime since);

    // Accuracy statistics queries
    @Query("SELECT COUNT(pv) FROM PredictionValidation pv WHERE pv.timeframe = :timeframe AND pv.validationTimestamp >= :since")
    Long countByTimeframeSince(@Param("timeframe") String timeframe, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(pv) FROM PredictionValidation pv WHERE pv.timeframe = :timeframe AND pv.directionCorrect = true AND pv.validationTimestamp >= :since")
    Long countCorrectDirectionByTimeframeSince(@Param("timeframe") String timeframe, @Param("since") LocalDateTime since);

    @Query("SELECT AVG(pv.priceErrorPercent) FROM PredictionValidation pv WHERE pv.timeframe = :timeframe AND pv.validationTimestamp >= :since")
    Double avgErrorPercentByTimeframeSince(@Param("timeframe") String timeframe, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(pv) FROM PredictionValidation pv WHERE pv.ticker = :ticker AND pv.directionCorrect = true AND pv.validationTimestamp >= :since")
    Long countCorrectDirectionForTickerSince(@Param("ticker") String ticker, @Param("since") LocalDateTime since);

    @Query("SELECT COUNT(pv) FROM PredictionValidation pv WHERE pv.ticker = :ticker AND pv.validationTimestamp >= :since")
    Long countForTickerSince(@Param("ticker") String ticker, @Param("since") LocalDateTime since);

    // Confidence calibration - group by confidence buckets
    @Query("SELECT " +
           "CASE " +
           "  WHEN pv.confidence < 20 THEN '0-20' " +
           "  WHEN pv.confidence < 40 THEN '20-40' " +
           "  WHEN pv.confidence < 60 THEN '40-60' " +
           "  WHEN pv.confidence < 80 THEN '60-80' " +
           "  ELSE '80-100' " +
           "END as bucket, " +
           "COUNT(pv) as total, " +
           "SUM(CASE WHEN pv.directionCorrect = true THEN 1 ELSE 0 END) as correct " +
           "FROM PredictionValidation pv " +
           "WHERE pv.validationTimestamp >= :since " +
           "GROUP BY CASE " +
           "  WHEN pv.confidence < 20 THEN '0-20' " +
           "  WHEN pv.confidence < 40 THEN '20-40' " +
           "  WHEN pv.confidence < 60 THEN '40-60' " +
           "  WHEN pv.confidence < 80 THEN '60-80' " +
           "  ELSE '80-100' " +
           "END")
    List<Object[]> getConfidenceCalibrationData(@Param("since") LocalDateTime since);

    // Signal type performance
    @Query("SELECT pv.signalType, COUNT(pv), SUM(CASE WHEN pv.directionCorrect = true THEN 1 ELSE 0 END) " +
           "FROM PredictionValidation pv " +
           "WHERE pv.validationTimestamp >= :since " +
           "GROUP BY pv.signalType")
    List<Object[]> getSignalTypePerformance(@Param("since") LocalDateTime since);
}
