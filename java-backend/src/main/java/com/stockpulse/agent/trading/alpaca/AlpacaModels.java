package com.stockpulse.agent.trading.alpaca;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Minimal Alpaca Trading API v2 payloads. Only the fields we actually use are mapped;
 * everything else is ignored so API additions don't break deserialization.
 */
public final class AlpacaModels {

    private AlpacaModels() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Account(
        String id,
        String status,
        String currency,
        BigDecimal cash,
        BigDecimal equity,
        @JsonProperty("last_equity") BigDecimal lastEquity,
        @JsonProperty("buying_power") BigDecimal buyingPower,
        @JsonProperty("portfolio_value") BigDecimal portfolioValue,
        @JsonProperty("daytrade_count") Integer daytradeCount,
        @JsonProperty("pattern_day_trader") Boolean patternDayTrader,
        @JsonProperty("trading_blocked") Boolean tradingBlocked,
        @JsonProperty("account_blocked") Boolean accountBlocked
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Position(
        String symbol,
        BigDecimal qty,
        String side,
        @JsonProperty("avg_entry_price") BigDecimal avgEntryPrice,
        @JsonProperty("current_price") BigDecimal currentPrice,
        @JsonProperty("market_value") BigDecimal marketValue,
        @JsonProperty("unrealized_pl") BigDecimal unrealizedPl,
        @JsonProperty("unrealized_plpc") BigDecimal unrealizedPlPct
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Order(
        String id,
        @JsonProperty("client_order_id") String clientOrderId,
        String symbol,
        String side,
        String type,
        String status,
        BigDecimal qty,
        BigDecimal notional,
        @JsonProperty("filled_qty") BigDecimal filledQty,
        @JsonProperty("filled_avg_price") BigDecimal filledAvgPrice,
        @JsonProperty("submitted_at") OffsetDateTime submittedAt,
        @JsonProperty("filled_at") OffsetDateTime filledAt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Clock(
        OffsetDateTime timestamp,
        @JsonProperty("is_open") boolean isOpen,
        @JsonProperty("next_open") OffsetDateTime nextOpen,
        @JsonProperty("next_close") OffsetDateTime nextClose
    ) {}

    /** Request body for POST /v2/orders. Null fields are omitted. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OrderRequest(
        String symbol,
        String qty,
        String notional,
        String side,
        String type,
        @JsonProperty("time_in_force") String timeInForce,
        @JsonProperty("order_class") String orderClass,
        @JsonProperty("client_order_id") String clientOrderId,
        @JsonProperty("take_profit") TakeProfit takeProfit,
        @JsonProperty("stop_loss") StopLoss stopLoss
    ) {
        public record TakeProfit(@JsonProperty("limit_price") String limitPrice) {}
        public record StopLoss(@JsonProperty("stop_price") String stopPrice) {}
    }
}
