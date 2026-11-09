package com.stockpulse.agent.repository;

import com.stockpulse.agent.model.MarketNews;
import com.stockpulse.agent.model.MarketNews.ImpactLevel;
import com.stockpulse.agent.model.MarketNews.MarketNewsCategory;
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
public interface MarketNewsRepository extends JpaRepository<MarketNews, Long> {

    /**
     * Find market news by category
     */
    List<MarketNews> findByCategoryOrderByPublishedAtDesc(MarketNewsCategory category);

    /**
     * Find market news by category with pagination
     */
    Page<MarketNews> findByCategoryOrderByPublishedAtDesc(MarketNewsCategory category, Pageable pageable);

    /**
     * Find market news by impact level
     */
    List<MarketNews> findByImpactLevelOrderByPublishedAtDesc(ImpactLevel impactLevel);

    /**
     * Find high-impact market news from recent hours
     */
    @Query("SELECT m FROM MarketNews m WHERE m.impactLevel = :impactLevel AND m.publishedAt >= :since ORDER BY m.publishedAt DESC")
    List<MarketNews> findByImpactLevelSince(
            @Param("impactLevel") ImpactLevel impactLevel,
            @Param("since") LocalDateTime since);

    /**
     * Find recent market news
     */
    @Query("SELECT m FROM MarketNews m WHERE m.publishedAt >= :since ORDER BY m.publishedAt DESC")
    List<MarketNews> findRecentNews(@Param("since") LocalDateTime since);

    /**
     * Find recent market news with pagination
     */
    @Query("SELECT m FROM MarketNews m WHERE m.publishedAt >= :since ORDER BY m.publishedAt DESC")
    Page<MarketNews> findRecentNews(@Param("since") LocalDateTime since, Pageable pageable);

    /**
     * Find market news by category and date range
     */
    @Query("SELECT m FROM MarketNews m WHERE m.category = :category AND m.publishedAt BETWEEN :start AND :end ORDER BY m.publishedAt DESC")
    List<MarketNews> findByCategoryAndDateRange(
            @Param("category") MarketNewsCategory category,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /**
     * Find market news affecting specific sectors
     */
    @Query("SELECT m FROM MarketNews m WHERE m.affectedSectors LIKE %:sector% ORDER BY m.publishedAt DESC")
    List<MarketNews> findByAffectedSector(@Param("sector") String sector);

    /**
     * Check if URL already exists (for deduplication)
     */
    boolean existsByUrl(String url);

    /**
     * Find by URL
     */
    Optional<MarketNews> findByUrl(String url);

    /**
     * Count news by category since a date
     */
    @Query("SELECT m.category, COUNT(m) FROM MarketNews m WHERE m.publishedAt >= :since GROUP BY m.category")
    List<Object[]> countByCategorySince(@Param("since") LocalDateTime since);

    /**
     * Get latest news of each category
     */
    @Query("SELECT m FROM MarketNews m WHERE m.publishedAt = " +
           "(SELECT MAX(m2.publishedAt) FROM MarketNews m2 WHERE m2.category = m.category)")
    List<MarketNews> findLatestByEachCategory();

    /**
     * Find all ordered by published date
     */
    List<MarketNews> findAllByOrderByPublishedAtDesc();

    /**
     * Find all with pagination
     */
    Page<MarketNews> findAllByOrderByPublishedAtDesc(Pageable pageable);
}
