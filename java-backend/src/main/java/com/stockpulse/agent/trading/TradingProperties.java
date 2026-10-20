package com.stockpulse.agent.trading;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Configuration for the auto-trader. Everything defaults to the safest value:
 * trading disabled, paper endpoint, small position sizes, tight loss limits.
 *
 * Bound from {@code app.trading.*} in application.yml.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.trading")
public class TradingProperties {

    /** Master switch. When false, the scheduler never submits orders. */
    private boolean enabled = false;

    /** Runtime kill switch. Can be flipped via POST /trading/kill-switch. */
    private volatile boolean killSwitch = false;

    /** Cron for the auto-trader loop. */
    private String cron = "0 */5 * * * *";

    private Alpaca alpaca = new Alpaca();
    private Risk risk = new Risk();
    private Signals signals = new Signals();

    @Data
    public static class Alpaca {
        /** Paper by default. Live is https://api.alpaca.markets — change deliberately. */
        private String baseUrl = "https://paper-api.alpaca.markets";
        private String dataUrl = "https://data.alpaca.markets";
        private String keyId = "";
        private String secretKey = "";
        private int timeoutMs = 10000;

        public boolean isConfigured() {
            return keyId != null && !keyId.isBlank() && secretKey != null && !secretKey.isBlank();
        }
    }

    @Data
    public static class Risk {
        /** Max notional (USD) per new position. */
        private BigDecimal maxPositionNotional = new BigDecimal("1000");
        /** Max fraction of equity in a single position (0.05 = 5%). */
        private BigDecimal maxPositionPctOfEquity = new BigDecimal("0.05");
        /** Max number of simultaneously open positions. */
        private int maxOpenPositions = 5;
        /** Stop trading for the day once equity drops this fraction below last close (0.02 = 2%). */
        private BigDecimal maxDailyLossPct = new BigDecimal("0.02");
        /** Bracket order: take-profit distance from entry (0.03 = +3%). */
        private BigDecimal takeProfitPct = new BigDecimal("0.03");
        /** Bracket order: stop-loss distance from entry (0.015 = -1.5%). */
        private BigDecimal stopLossPct = new BigDecimal("0.015");
        /** Only trade when Alpaca reports the market open. */
        private boolean requireMarketOpen = true;
        /** Don't open new positions within this many minutes of the close (avoid overnight risk). */
        private int noNewEntriesMinutesBeforeClose = 30;
        /** Only allow long entries. Shorts are off until the signal quality justifies them. */
        private boolean allowShorts = false;
    }

    @Data
    public static class Signals {
        /** Minimum signal confidence (0-100) to act on. */
        private BigDecimal minConfidence = new BigDecimal("75");
        /** Minimum screening score (0-100) to act on. */
        private BigDecimal minScreeningScore = new BigDecimal("70");
        /** Only consider signals generated within this window. */
        private int lookbackMinutes = 35;
        /** Close an open long when a BEARISH signal at/above this confidence appears. */
        private BigDecimal exitConfidence = new BigDecimal("70");
    }
}
