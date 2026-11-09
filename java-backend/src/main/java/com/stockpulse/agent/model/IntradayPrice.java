package com.stockpulse.agent.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.SQLInsert;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_prices_intraday", indexes = {
    @Index(name = "idx_intraday_ticker_time", columnList = "ticker, timestamp")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(IntradayPriceId.class)
// Hibernate applies @ColumnTransformer(write) to ordinary columns but not to @Id
// columns, so without this the Eastern wall-clock time was stored as if it were UTC
// (bars 4-5h early). Parameter order is Hibernate's: non-id attributes alphabetically,
// then the id attributes. IntradayPriceRepositoryTimezoneTest pins both the order and
// the conversion.
@SQLInsert(sql = """
    INSERT INTO stock_prices_intraday (close, high, low, open, volume, ticker, timestamp)
    VALUES (?, ?, ?, ?, ?, ?, (CAST(? AS TIMESTAMP) AT TIME ZONE 'America/New_York'))
    """)
public class IntradayPrice {

    @Id
    @Column(nullable = false, length = 10)
    private String ticker;

    /**
     * Bar start time as US/Eastern wall-clock time. The column is TIMESTAMPTZ,
     * so convert in SQL rather than relying on the JVM time zone (UTC in Docker).
     */
    @Id
    @Column(nullable = false)
    @ColumnTransformer(
        read = "(timestamp AT TIME ZONE 'America/New_York')",
        write = "(CAST(? AS TIMESTAMP) AT TIME ZONE 'America/New_York')"
    )
    private LocalDateTime timestamp;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal open;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal high;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal low;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal close;

    @Column(nullable = false)
    private Long volume;
}
