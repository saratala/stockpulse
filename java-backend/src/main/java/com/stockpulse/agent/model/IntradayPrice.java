package com.stockpulse.agent.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.ColumnTransformer;

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
