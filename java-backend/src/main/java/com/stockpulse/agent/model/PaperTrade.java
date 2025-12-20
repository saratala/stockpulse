package com.stockpulse.agent.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "paper_trades")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaperTrade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String ticker;

    @Column(name = "signal_prediction_id", nullable = false)
    private Integer signalPredictionId;

    @Column(name = "trade_type", nullable = false)
    private String tradeType; // 'BUY' or 'SELL'

    @Column(name = "entry_price", nullable = false)
    private Double entryPrice;

    @Column(name = "entry_timestamp", nullable = false)
    private LocalDateTime entryTimestamp;

    @Column(name = "target_price")
    private Double targetPrice;

    @Column(name = "stop_loss_price")
    private Double stopLossPrice;

    @Column(name = "position_size")
    @Builder.Default
    private Integer positionSize = 100;

    @Column(name = "position_value", nullable = false)
    private Double positionValue;

    @Column(name = "exit_price")
    private Double exitPrice;

    @Column(name = "exit_timestamp")
    private LocalDateTime exitTimestamp;

    @Column(name = "exit_reason")
    private String exitReason; // 'TARGET_HIT', 'STOP_LOSS', 'TIME_EXPIRED', 'MANUAL'

    @Column(name = "realized_pnl")
    private Double realizedPnl;

    @Column(name = "realized_pnl_percent")
    private Double realizedPnlPercent;

    @Column(nullable = false)
    @Builder.Default
    private String status = "OPEN"; // 'OPEN', 'CLOSED', 'CANCELLED'

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
