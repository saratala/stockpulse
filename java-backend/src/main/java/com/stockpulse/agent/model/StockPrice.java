package com.stockpulse.agent.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_prices", indexes = {
    @Index(name = "idx_stock_prices_ticker_date", columnList = "ticker, date")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(StockPriceId.class)
public class StockPrice {
    
    @Id
    @Column(nullable = false, length = 10)
    private String ticker;
    
    @Id
    @Column(nullable = false)
    private LocalDateTime date;
    
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
    
    @Column(name = "adjusted_close", precision = 12, scale = 4)
    private BigDecimal adjustedClose;
}