package com.stockpulse.agent.repository;

import com.stockpulse.agent.model.Prediction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PredictionRepository extends JpaRepository<Prediction, Long> {
    
    List<Prediction> findByTicker(String ticker);
    
    Page<Prediction> findByTicker(String ticker, Pageable pageable);
    
    List<Prediction> findByTickerAndPredictionDateBetween(String ticker, LocalDateTime start, LocalDateTime end);
    
    @Query("SELECT p FROM Prediction p WHERE p.predictionDate >= :since ORDER BY p.predictionDate DESC")
    List<Prediction> findRecentPredictions(@Param("since") LocalDateTime since);
    
    @Query("SELECT p FROM Prediction p WHERE p.predictionDate >= :since ORDER BY p.predictionDate DESC")
    Page<Prediction> findRecentPredictions(@Param("since") LocalDateTime since, Pageable pageable);
    
    @Query("SELECT p FROM Prediction p WHERE p.ticker = :ticker ORDER BY p.predictionDate DESC")
    List<Prediction> findByTickerOrderByPredictionDateDesc(@Param("ticker") String ticker);
    
    @Query("SELECT p.predictedDirection, COUNT(p) FROM Prediction p WHERE p.predictionDate >= :since GROUP BY p.predictedDirection")
    List<Object[]> countByPredictedDirectionSince(@Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(p) FROM Prediction p WHERE p.predictionDate >= :since")
    Long countTotalPredictions(@Param("since") LocalDateTime since);
    
    @Query("SELECT p FROM Prediction p WHERE p.targetDate <= :currentDate ORDER BY p.predictionDate DESC")
    List<Prediction> findMaturedPredictions(@Param("currentDate") LocalDateTime currentDate);
    
    @Query("SELECT p FROM Prediction p WHERE p.ticker = :ticker AND p.targetDate <= :currentDate ORDER BY p.predictionDate DESC")
    List<Prediction> findMaturedPredictionsByTicker(@Param("ticker") String ticker, @Param("currentDate") LocalDateTime currentDate);
    
    @Query("SELECT p FROM Prediction p WHERE p.modelVersion = :modelVersion AND p.predictionDate >= :since ORDER BY p.predictionDate DESC")
    List<Prediction> findByModelVersionAndPredictionDateAfter(@Param("modelVersion") String modelVersion, @Param("since") LocalDateTime since);
}