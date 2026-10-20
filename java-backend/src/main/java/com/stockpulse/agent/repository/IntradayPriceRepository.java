package com.stockpulse.agent.repository;

import com.stockpulse.agent.model.IntradayPrice;
import com.stockpulse.agent.model.IntradayPriceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IntradayPriceRepository extends JpaRepository<IntradayPrice, IntradayPriceId> {

    /**
     * Find all intraday prices for a ticker within a time range
     */
    @Query("SELECT ip FROM IntradayPrice ip WHERE ip.ticker = :ticker AND ip.timestamp >= :startTime AND ip.timestamp <= :endTime ORDER BY ip.timestamp ASC")
    List<IntradayPrice> findByTickerAndTimestampBetween(
            @Param("ticker") String ticker,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * Find intraday prices for a specific date (market hours: 9:30 AM - 4:00 PM ET)
     */
    @Query("SELECT ip FROM IntradayPrice ip WHERE ip.ticker = :ticker AND CAST(ip.timestamp AS DATE) = CAST(:date AS DATE) ORDER BY ip.timestamp ASC")
    List<IntradayPrice> findByTickerAndDate(
            @Param("ticker") String ticker,
            @Param("date") LocalDateTime date);

    /**
     * Find the most recent N intraday prices for a ticker
     */
    @Query("SELECT ip FROM IntradayPrice ip WHERE ip.ticker = :ticker ORDER BY ip.timestamp DESC LIMIT :limit")
    List<IntradayPrice> findLatestByTicker(@Param("ticker") String ticker, @Param("limit") int limit);

    /**
     * Find the latest intraday price for a ticker
     */
    @Query("SELECT ip FROM IntradayPrice ip WHERE ip.ticker = :ticker ORDER BY ip.timestamp DESC LIMIT 1")
    Optional<IntradayPrice> findMostRecentByTicker(@Param("ticker") String ticker);

    /**
     * Get distinct dates that have intraday data for a ticker
     */
    @Query("SELECT DISTINCT CAST(ip.timestamp AS DATE) FROM IntradayPrice ip WHERE ip.ticker = :ticker ORDER BY CAST(ip.timestamp AS DATE) DESC")
    List<java.sql.Date> findDistinctDatesByTicker(@Param("ticker") String ticker);

    /**
     * Count intraday records for a ticker on a specific date
     */
    @Query("SELECT COUNT(ip) FROM IntradayPrice ip WHERE ip.ticker = :ticker AND CAST(ip.timestamp AS DATE) = CAST(:date AS DATE)")
    long countByTickerAndDate(@Param("ticker") String ticker, @Param("date") LocalDateTime date);

    /**
     * Check if intraday data exists for a ticker and timestamp
     */
    @Query("SELECT CASE WHEN COUNT(ip) > 0 THEN true ELSE false END FROM IntradayPrice ip WHERE ip.ticker = :ticker AND ip.timestamp = :timestamp")
    boolean existsByTickerAndTimestamp(@Param("ticker") String ticker, @Param("timestamp") LocalDateTime timestamp);

    /**
     * Delete all intraday data for a ticker
     */
    @Modifying
    @Query("DELETE FROM IntradayPrice ip WHERE ip.ticker = :ticker")
    int deleteByTicker(@Param("ticker") String ticker);

    /**
     * Aggregate intraday data to hourly OHLCV
     * Note: This returns raw Object arrays; consider using a DTO
     */
    @Query(value = """
        SELECT
            DATE_TRUNC('hour', timestamp) as hour,
            (SELECT open FROM stock_prices_intraday WHERE ticker = :ticker AND DATE_TRUNC('hour', timestamp) = DATE_TRUNC('hour', ip.timestamp) ORDER BY timestamp ASC LIMIT 1) as open,
            MAX(high) as high,
            MIN(low) as low,
            (SELECT close FROM stock_prices_intraday WHERE ticker = :ticker AND DATE_TRUNC('hour', timestamp) = DATE_TRUNC('hour', ip.timestamp) ORDER BY timestamp DESC LIMIT 1) as close,
            SUM(volume) as volume
        FROM stock_prices_intraday ip
        WHERE ip.ticker = :ticker
        AND ip.timestamp >= :startTime
        AND ip.timestamp <= :endTime
        GROUP BY DATE_TRUNC('hour', ip.timestamp)
        ORDER BY hour ASC
        """, nativeQuery = true)
    List<Object[]> aggregateToHourly(
            @Param("ticker") String ticker,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * Get available tickers with intraday data
     */
    @Query("SELECT DISTINCT ip.ticker FROM IntradayPrice ip ORDER BY ip.ticker")
    List<String> findDistinctTickers();

    /**
     * Get date range of available intraday data for a ticker
     */
    @Query("SELECT MIN(ip.timestamp), MAX(ip.timestamp) FROM IntradayPrice ip WHERE ip.ticker = :ticker")
    List<Object[]> findDateRangeByTicker(@Param("ticker") String ticker);

    /**
     * Find intraday prices ordered by timestamp ascending (for aggregation)
     */
    @Query("SELECT ip FROM IntradayPrice ip WHERE ip.ticker = :ticker AND ip.timestamp >= :startTime AND ip.timestamp <= :endTime ORDER BY ip.timestamp ASC")
    List<IntradayPrice> findByTickerAndTimestampBetweenOrderByTimestampAsc(
            @Param("ticker") String ticker,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * Find top N records for a ticker ordered by timestamp descending
     */
    @Query("SELECT ip FROM IntradayPrice ip WHERE ip.ticker = :ticker ORDER BY ip.timestamp DESC LIMIT :limit")
    List<IntradayPrice> findTopNByTickerOrderByTimestampDesc(@Param("ticker") String ticker, @Param("limit") int limit);

    /**
     * Find earliest timestamp for a ticker
     */
    @Query("SELECT MIN(ip.timestamp) FROM IntradayPrice ip WHERE ip.ticker = :ticker")
    Optional<LocalDateTime> findEarliestTimestamp(@Param("ticker") String ticker);

    /**
     * Find latest timestamp for a ticker
     */
    @Query("SELECT MAX(ip.timestamp) FROM IntradayPrice ip WHERE ip.ticker = :ticker")
    Optional<LocalDateTime> findLatestTimestamp(@Param("ticker") String ticker);

    /**
     * Count all records for a ticker
     */
    @Query("SELECT COUNT(ip) FROM IntradayPrice ip WHERE ip.ticker = :ticker")
    long countByTicker(@Param("ticker") String ticker);
}
