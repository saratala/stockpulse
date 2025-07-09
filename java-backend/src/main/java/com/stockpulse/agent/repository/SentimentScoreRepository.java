package com.stockpulse.agent.repository;

import com.stockpulse.agent.model.SentimentScore;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SentimentScoreRepository extends JpaRepository<SentimentScore, Long> {
    
    List<SentimentScore> findByTicker(String ticker);
    
    @Query("SELECT s FROM SentimentScore s WHERE s.ticker = :ticker ORDER BY s.publishedAt DESC")
    List<SentimentScore> findByTickerOrderByPublishedAtDesc(@Param("ticker") String ticker);
    
    @Query("SELECT s FROM SentimentScore s WHERE s.ticker = :ticker ORDER BY s.publishedAt DESC")
    Page<SentimentScore> findByTickerOrderByPublishedAtDesc(@Param("ticker") String ticker, Pageable pageable);
    
    @Query("SELECT s FROM SentimentScore s WHERE s.ticker = :ticker AND s.publishedAt >= :startDate AND s.publishedAt <= :endDate ORDER BY s.publishedAt DESC")
    List<SentimentScore> findByTickerAndPublishedAtBetween(@Param("ticker") String ticker, 
                                                          @Param("startDate") LocalDateTime startDate, 
                                                          @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT s FROM SentimentScore s WHERE s.publishedAt >= :startDate ORDER BY s.publishedAt DESC")
    List<SentimentScore> findRecentSentiments(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT s FROM SentimentScore s WHERE s.publishedAt >= :startDate ORDER BY s.publishedAt DESC")
    Page<SentimentScore> findRecentSentiments(@Param("startDate") LocalDateTime startDate, Pageable pageable);
    
    @Query("SELECT AVG(s.sentimentScore) FROM SentimentScore s WHERE s.ticker = :ticker AND s.publishedAt >= :startDate")
    BigDecimal findAverageSentimentByTickerSince(@Param("ticker") String ticker, @Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT s.polarity, COUNT(s) FROM SentimentScore s WHERE s.ticker = :ticker AND s.publishedAt >= :startDate GROUP BY s.polarity")
    List<Object[]> countSentimentPolarityByTickerSince(@Param("ticker") String ticker, @Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT s.ticker, AVG(s.sentimentScore) FROM SentimentScore s WHERE s.publishedAt >= :startDate GROUP BY s.ticker ORDER BY AVG(s.sentimentScore) DESC")
    List<Object[]> findAverageSentimentByTickerSince(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT COUNT(s) FROM SentimentScore s WHERE s.ticker = :ticker AND s.publishedAt >= :startDate")
    Long countByTickerAndPublishedAtAfter(@Param("ticker") String ticker, @Param("startDate") LocalDateTime startDate);
    
    Optional<SentimentScore> findByArticleId(Long articleId);
    
    boolean existsByArticleId(Long articleId);
    
    boolean existsByNewsArticleId(Long newsArticleId);
}