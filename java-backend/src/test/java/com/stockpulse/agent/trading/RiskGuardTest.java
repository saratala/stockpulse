package com.stockpulse.agent.trading;

import com.stockpulse.agent.trading.alpaca.AlpacaModels.Account;
import com.stockpulse.agent.trading.alpaca.AlpacaModels.Clock;
import com.stockpulse.agent.trading.alpaca.AlpacaModels.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RiskGuardTest {

    private TradingProperties props;
    private RiskGuard guard;
    private OffsetDateTime now;

    @BeforeEach
    void setUp() {
        props = new TradingProperties();
        props.setEnabled(true);
        guard = new RiskGuard(props);
        now = OffsetDateTime.parse("2026-10-19T14:00:00Z"); // 10:00 ET, market open
    }

    private Account account(String equity, String lastEquity, String buyingPower) {
        return new Account("acct", "ACTIVE", "USD", new BigDecimal(buyingPower), new BigDecimal(equity),
            new BigDecimal(lastEquity), new BigDecimal(buyingPower), new BigDecimal(equity),
            0, false, false, false);
    }

    private Clock open() {
        return new Clock(now, true, now.plusDays(1), now.plusHours(6));
    }

    private Position pos(String symbol) {
        return new Position(symbol, BigDecimal.TEN, "long", BigDecimal.TEN, BigDecimal.TEN,
            new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private RiskGuard.Snapshot snap(Account a, Clock c, List<Position> p) {
        return new RiskGuard.Snapshot(a, c, p, now);
    }

    @Test
    void deniesWhenTradingDisabled() {
        props.setEnabled(false);
        var d = guard.checkGlobal(snap(account("10000", "10000", "10000"), open(), List.of()));
        assertFalse(d.allowed());
        assertEquals("TRADING_DISABLED", d.reason());
    }

    @Test
    void killSwitchBeatsEverything() {
        props.setKillSwitch(true);
        var d = guard.checkEntry(snap(account("10000", "10000", "10000"), open(), List.of()),
            "AAPL", new BigDecimal("99"), new BigDecimal("99"));
        assertFalse(d.allowed());
        assertEquals("KILL_SWITCH", d.reason());
    }

    @Test
    void deniesWhenMarketClosed() {
        Clock closed = new Clock(now, false, now.plusHours(10), now.minusHours(1));
        var d = guard.checkGlobal(snap(account("10000", "10000", "10000"), closed, List.of()));
        assertEquals("MARKET_CLOSED", d.reason());
    }

    @Test
    void dailyLossLimitHalts() {
        // 2% default limit; equity down 2.5% from last close
        var d = guard.checkGlobal(snap(account("9750", "10000", "9750"), open(), List.of()));
        assertFalse(d.allowed());
        assertTrue(d.reason().startsWith("DAILY_LOSS_LIMIT"));
    }

    @Test
    void dailyLossJustUnderLimitIsFine() {
        var d = guard.checkGlobal(snap(account("9850", "10000", "9850"), open(), List.of()));
        assertTrue(d.allowed());
    }

    @Test
    void lowConfidenceIsSkipped() {
        var d = guard.checkEntry(snap(account("10000", "10000", "10000"), open(), List.of()),
            "AAPL", new BigDecimal("60"), new BigDecimal("90"));
        assertTrue(d.reason().startsWith("LOW_CONFIDENCE"));
    }

    @Test
    void duplicatePositionIsSkipped() {
        var d = guard.checkEntry(snap(account("10000", "10000", "10000"), open(), List.of(pos("AAPL"))),
            "aapl", new BigDecimal("90"), new BigDecimal("90"));
        assertEquals("ALREADY_IN_POSITION", d.reason());
    }

    @Test
    void maxOpenPositionsEnforced() {
        props.getRisk().setMaxOpenPositions(2);
        var d = guard.checkEntry(snap(account("10000", "10000", "10000"), open(), List.of(pos("A"), pos("B"))),
            "C", new BigDecimal("90"), new BigDecimal("90"));
        assertEquals("MAX_OPEN_POSITIONS", d.reason());
    }

    @Test
    void noEntriesNearClose() {
        Clock nearClose = new Clock(now, true, now.plusDays(1), now.plusMinutes(10));
        var d = guard.checkEntry(snap(account("10000", "10000", "10000"), nearClose, List.of()),
            "AAPL", new BigDecimal("90"), new BigDecimal("90"));
        assertEquals("TOO_CLOSE_TO_MARKET_CLOSE", d.reason());
    }

    @Test
    void positionSizeIsMinOfCaps() {
        // max notional 1000, 5% of 10000 = 500, buying power 10000 → 500
        assertEquals(new BigDecimal("500"), guard.sizePosition(account("10000", "10000", "10000")));
        // buying power is the binding constraint
        assertEquals(new BigDecimal("120"), guard.sizePosition(account("10000", "10000", "120.99")));
        // large account: max notional binds
        assertEquals(new BigDecimal("1000"), guard.sizePosition(account("1000000", "1000000", "1000000")));
    }

    @Test
    void happyPathAllowsWithSizedNotional() {
        var d = guard.checkEntry(snap(account("10000", "10000", "10000"), open(), List.of()),
            "AAPL", new BigDecimal("85"), new BigDecimal("80"));
        assertTrue(d.allowed(), d.reason());
        assertEquals(new BigDecimal("500"), d.notional());
    }
}
