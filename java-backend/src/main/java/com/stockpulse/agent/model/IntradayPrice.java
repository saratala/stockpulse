package com.stockpulse.agent.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

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

    @Id
    @Column(nullable = false)
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
