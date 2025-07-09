package com.stockpulse.agent.repository;

import com.stockpulse.agent.model.NewsArticle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NewsArticleRepository extends JpaRepository<NewsArticle, Long> {
    
    List<NewsArticle> findByTicker(String ticker);
    
    @Query("SELECT n FROM NewsArticle n WHERE n.ticker = :ticker ORDER BY n.publishedAt DESC")
    List<NewsArticle> findByTickerOrderByPublishedAtDesc(@Param("ticker") String ticker);
    
    @Query("SELECT n FROM NewsArticle n WHERE n.ticker = :ticker ORDER BY n.publishedAt DESC")
    Page<NewsArticle> findByTickerOrderByPublishedAtDesc(@Param("ticker") String ticker, Pageable pageable);
    
    @Query("SELECT n FROM NewsArticle n WHERE n.ticker = :ticker AND n.publishedAt >= :startDate AND n.publishedAt <= :endDate ORDER BY n.publishedAt DESC")
    List<NewsArticle> findByTickerAndPublishedAtBetween(@Param("ticker") String ticker, 
                                                       @Param("startDate") LocalDateTime startDate, 
                                                       @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT n FROM NewsArticle n WHERE n.publishedAt >= :startDate ORDER BY n.publishedAt DESC")
    List<NewsArticle> findRecentNews(@Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT n FROM NewsArticle n WHERE n.publishedAt >= :startDate ORDER BY n.publishedAt DESC")
    Page<NewsArticle> findRecentNews(@Param("startDate") LocalDateTime startDate, Pageable pageable);
    
    @Query("SELECT COUNT(n) FROM NewsArticle n WHERE n.ticker = :ticker AND n.publishedAt >= :startDate")
    Long countByTickerAndPublishedAtAfter(@Param("ticker") String ticker, @Param("startDate") LocalDateTime startDate);
    
    @Query("SELECT n.ticker, COUNT(n) FROM NewsArticle n WHERE n.publishedAt >= :startDate GROUP BY n.ticker ORDER BY COUNT(n) DESC")
    List<Object[]> countNewsByTickerSince(@Param("startDate") LocalDateTime startDate);
    
    Optional<NewsArticle> findByUrl(String url);
    
    boolean existsByUrl(String url);
    
    boolean existsByTitleAndUrl(String title, String url);
}