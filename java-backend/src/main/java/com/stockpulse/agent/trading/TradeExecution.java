package com.stockpulse.agent.trading;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Audit log of every decision the auto-trader makes — including the ones it
 * declined. This is the dataset you'll want when asking "why didn't it trade X?"
 * or "how often does the risk guard fire?".
 */
@Entity
@Table(name = "trade_executions", indexes = {
    @Index(name = "idx_trade_exec_ticker_time", columnList = "ticker, created_at"),
    @Index(name = "idx_trade_exec_action", columnList = "action, created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeExecution {

    public enum Action { ENTRY, EXIT, SKIPPED, ERROR }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String ticker;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Action action;

    /** Side as sent to the broker: buy / sell. Null when skipped. */
    @Column(length = 8)
    private String side;

    @Column(name = "signal_prediction_id")
    private Long signalPredictionId;

    @Column(name = "signal_confidence", precision = 5, scale = 2)
    private BigDecimal signalConfidence;

    @Column(precision = 12, scale = 2)
    private BigDecimal notional;

    @Column(name = "reference_price", precision = 12, scale = 4)
    private BigDecimal referencePrice;

    @Column(name = "take_profit_price", precision = 12, scale = 4)
    private BigDecimal takeProfitPrice;

    @Column(name = "stop_loss_price", precision = 12, scale = 4)
    private BigDecimal stopLossPrice;

    @Column(name = "broker_order_id", length = 64)
    private String brokerOrderId;

    @Column(name = "client_order_id", length = 64)
    private String clientOrderId;

    @Column(columnDefinition = "TEXT")
    private String reason;

    /** "paper" or "live", copied from config at the time of the decision. */
    @Column(nullable = false, length = 8)
    private String mode;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
