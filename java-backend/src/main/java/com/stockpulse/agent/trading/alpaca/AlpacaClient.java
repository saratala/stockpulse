package com.stockpulse.agent.trading.alpaca;

import com.stockpulse.agent.trading.TradingProperties;
import com.stockpulse.agent.trading.alpaca.AlpacaModels.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

/**
 * Thin, blocking wrapper over the Alpaca Trading API v2.
 * Paper vs live is purely a matter of {@code app.trading.alpaca.base-url}.
 *
 * All methods throw {@link AlpacaException} on non-2xx responses so callers
 * can decide whether an error is fatal for the current trading run.
 */
@Component
@Slf4j
public class AlpacaClient {

    private final WebClient client;
    private final Duration timeout;
    private final boolean configured;

    public AlpacaClient(TradingProperties props) {
        TradingProperties.Alpaca cfg = props.getAlpaca();
        this.configured = cfg.isConfigured();
        this.timeout = Duration.ofMillis(cfg.getTimeoutMs());
        this.client = WebClient.builder()
            .baseUrl(cfg.getBaseUrl())
            .defaultHeader("APCA-API-KEY-ID", cfg.getKeyId())
            .defaultHeader("APCA-API-SECRET-KEY", cfg.getSecretKey())
            .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
            .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();
        if (configured) {
            log.info("Alpaca client configured against {}", cfg.getBaseUrl());
        } else {
            log.warn("Alpaca client NOT configured (ALPACA_KEY_ID / ALPACA_SECRET_KEY missing) — trading disabled");
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    public Account getAccount() {
        return get("/v2/account", Account.class);
    }

    public Clock getClock() {
        return get("/v2/clock", Clock.class);
    }

    public List<Position> getPositions() {
        return getList("/v2/positions", new ParameterizedTypeReference<List<Position>>() {});
    }

    public List<Order> getOrders(String status, int limit) {
        return getList("/v2/orders?status=" + status + "&limit=" + limit + "&direction=desc",
            new ParameterizedTypeReference<List<Order>>() {});
    }

    public Order submitOrder(OrderRequest request) {
        log.info("Submitting Alpaca order: {} {} {} notional={} qty={} class={}",
            request.side(), request.symbol(), request.type(), request.notional(), request.qty(), request.orderClass());
        return exchange(client.post().uri("/v2/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(request), Order.class);
    }

    /** Closes the whole position with a market order. Returns the closing order. */
    public Order closePosition(String symbol) {
        log.info("Closing Alpaca position: {}", symbol);
        return exchange(client.delete().uri("/v2/positions/{symbol}", symbol), Order.class);
    }

    /** Liquidates everything and cancels open orders. Used by the kill switch. */
    public void closeAllPositions() {
        log.warn("Closing ALL Alpaca positions and cancelling open orders");
        exchange(client.delete().uri("/v2/positions?cancel_orders=true"), String.class);
    }

    // ---- helpers -----------------------------------------------------------

    private <T> T get(String uri, Class<T> type) {
        return exchange(client.get().uri(uri), type);
    }

    private <T> List<T> getList(String uri, ParameterizedTypeReference<List<T>> type) {
        ensureConfigured();
        try {
            return client.get().uri(uri).retrieve()
                .onStatus(HttpStatusCode::isError, this::toError)
                .bodyToMono(type)
                .block(timeout);
        } catch (WebClientResponseException e) {
            throw new AlpacaException(e.getStatusCode().value(), e.getResponseBodyAsString(), e);
        }
    }

    private <T> T exchange(WebClient.RequestHeadersSpec<?> spec, Class<T> type) {
        ensureConfigured();
        try {
            return spec.retrieve()
                .onStatus(HttpStatusCode::isError, this::toError)
                .bodyToMono(type)
                .block(timeout);
        } catch (WebClientResponseException e) {
            throw new AlpacaException(e.getStatusCode().value(), e.getResponseBodyAsString(), e);
        }
    }

    private Mono<? extends Throwable> toError(org.springframework.web.reactive.function.client.ClientResponse resp) {
        return resp.bodyToMono(String.class).defaultIfEmpty("")
            .map(body -> new AlpacaException(resp.statusCode().value(), body, null));
    }

    private void ensureConfigured() {
        if (!configured) {
            throw new AlpacaException(0, "Alpaca credentials not configured", null);
        }
    }

    public static class AlpacaException extends RuntimeException {
        private final int status;

        public AlpacaException(int status, String body, Throwable cause) {
            super("Alpaca API error " + status + ": " + body, cause);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }
}
