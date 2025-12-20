package com.stockpulse.agent.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "paper_trading_account")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaperTradingAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_name", nullable = false, unique = true)
    @Builder.Default
    private String accountName = "default";

    @Column(name = "initial_balance")
    @Builder.Default
    private Double initialBalance = 100000.0;

    @Column(name = "current_balance")
    @Builder.Default
    private Double currentBalance = 100000.0;

    @Column(name = "total_trades")
    @Builder.Default
    private Integer totalTrades = 0;

    @Column(name = "winning_trades")
    @Builder.Default
    private Integer winningTrades = 0;

    @Column(name = "losing_trades")
    @Builder.Default
    private Integer losingTrades = 0;

    @Column(name = "total_realized_pnl")
    @Builder.Default
    private Double totalRealizedPnl = 0.0;

    @Column(name = "total_unrealized_pnl")
    @Builder.Default
    private Double totalUnrealizedPnl = 0.0;

    @Column(name = "max_drawdown")
    @Builder.Default
    private Double maxDrawdown = 0.0;

    @Column(name = "max_drawdown_percent")
    @Builder.Default
    private Double maxDrawdownPercent = 0.0;

    @Column(name = "peak_balance")
    @Builder.Default
    private Double peakBalance = 100000.0;

    @Column(name = "started_at")
    @Builder.Default
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Helper methods
    public double getWinRate() {
        if (totalTrades == 0) return 0.0;
        return (double) winningTrades / totalTrades * 100;
    }

    public double getTotalReturn() {
        return ((currentBalance - initialBalance) / initialBalance) * 100;
    }

    public void recordWin(double pnl) {
        this.totalTrades++;
        this.winningTrades++;
        this.totalRealizedPnl += pnl;
        this.currentBalance += pnl;
        updatePeakAndDrawdown();
    }

    public void recordLoss(double pnl) {
        this.totalTrades++;
        this.losingTrades++;
        this.totalRealizedPnl += pnl; // pnl is negative
        this.currentBalance += pnl;
        updatePeakAndDrawdown();
    }

    private void updatePeakAndDrawdown() {
        if (currentBalance > peakBalance) {
            peakBalance = currentBalance;
        }
        double drawdown = peakBalance - currentBalance;
        if (drawdown > maxDrawdown) {
            maxDrawdown = drawdown;
            maxDrawdownPercent = (drawdown / peakBalance) * 100;
        }
    }
}
