package com.stockpulse.agent.trading;

import com.stockpulse.agent.trading.alpaca.AlpacaModels.Account;
import com.stockpulse.agent.trading.alpaca.AlpacaModels.Clock;
import com.stockpulse.agent.trading.alpaca.AlpacaModels.Position;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Pure decision logic for whether a proposed entry is allowed, and how big it may be.
 * No I/O here on purpose — every rule is unit-testable with plain inputs.
 *
 * Evaluation order is deliberately "cheapest, most global first": kill switch and
 * account state before per-ticker checks.
 */
@Component
public class RiskGuard {

    public record Snapshot(Account account, Clock clock, List<Position> positions, OffsetDateTime now) {}

    public record Decision(boolean allowed, String reason, BigDecimal notional) {
        static Decision deny(String reason) {
            return new Decision(false, reason, BigDecimal.ZERO);
        }

        static Decision allow(BigDecimal notional) {
            return new Decision(true, "OK", notional);
        }
    }

    private final TradingProperties props;

    public RiskGuard(TradingProperties props) {
        this.props = props;
    }

    /** Checks that apply to the whole trading run, independent of any one ticker. */
    public Decision checkGlobal(Snapshot s) {
        TradingProperties.Risk risk = props.getRisk();

        if (!props.isEnabled()) {
            return Decision.deny("TRADING_DISABLED");
        }
        if (props.isKillSwitch()) {
            return Decision.deny("KILL_SWITCH");
        }
        Account a = s.account();
        if (a == null) {
            return Decision.deny("NO_ACCOUNT");
        }
        if (Boolean.TRUE.equals(a.tradingBlocked()) || Boolean.TRUE.equals(a.accountBlocked())) {
            return Decision.deny("ACCOUNT_BLOCKED");
        }
        if (risk.isRequireMarketOpen()) {
            if (s.clock() == null || !s.clock().isOpen()) {
                return Decision.deny("MARKET_CLOSED");
            }
        }
        if (a.lastEquity() != null && a.equity() != null && a.lastEquity().signum() > 0) {
            BigDecimal drawdown = a.lastEquity().subtract(a.equity())
                .divide(a.lastEquity(), 6, RoundingMode.HALF_UP);
            if (drawdown.compareTo(risk.getMaxDailyLossPct()) >= 0) {
                return Decision.deny("DAILY_LOSS_LIMIT drawdown=" + drawdown.setScale(4, RoundingMode.HALF_UP));
            }
        }
        return Decision.allow(BigDecimal.ZERO);
    }

    /** Checks for opening a new long position in {@code symbol}. */
    public Decision checkEntry(Snapshot s, String symbol, BigDecimal confidence, BigDecimal screeningScore) {
        Decision global = checkGlobal(s);
        if (!global.allowed()) {
            return global;
        }
        TradingProperties.Risk risk = props.getRisk();
        TradingProperties.Signals sig = props.getSignals();

        if (confidence == null || confidence.compareTo(sig.getMinConfidence()) < 0) {
            return Decision.deny("LOW_CONFIDENCE " + confidence);
        }
        if (screeningScore == null || screeningScore.compareTo(sig.getMinScreeningScore()) < 0) {
            return Decision.deny("LOW_SCREENING_SCORE " + screeningScore);
        }
        if (s.clock() != null && s.clock().nextClose() != null && s.now() != null) {
            Duration toClose = Duration.between(s.now(), s.clock().nextClose());
            if (toClose.toMinutes() < risk.getNoNewEntriesMinutesBeforeClose()) {
                return Decision.deny("TOO_CLOSE_TO_MARKET_CLOSE");
            }
        }
        List<Position> positions = s.positions() == null ? List.of() : s.positions();
        if (positions.stream().anyMatch(p -> p.symbol().equalsIgnoreCase(symbol))) {
            return Decision.deny("ALREADY_IN_POSITION");
        }
        if (positions.size() >= risk.getMaxOpenPositions()) {
            return Decision.deny("MAX_OPEN_POSITIONS");
        }

        BigDecimal notional = sizePosition(s.account());
        if (notional.compareTo(BigDecimal.ONE) < 0) {
            return Decision.deny("INSUFFICIENT_BUYING_POWER");
        }
        return Decision.allow(notional);
    }

    /**
     * Position size = min(max notional, pct-of-equity cap, available buying power).
     * Rounded down to whole dollars; Alpaca accepts fractional notional but we keep it simple.
     */
    public BigDecimal sizePosition(Account a) {
        TradingProperties.Risk risk = props.getRisk();
        BigDecimal cap = risk.getMaxPositionNotional();
        if (a.equity() != null) {
            cap = cap.min(a.equity().multiply(risk.getMaxPositionPctOfEquity()));
        }
        if (a.buyingPower() != null) {
            cap = cap.min(a.buyingPower());
        }
        return cap.setScale(0, RoundingMode.DOWN).max(BigDecimal.ZERO);
    }
}
