package com.stockpulse.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.stockpulse.agent.config.SP500Tickers;
import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Massive Data WebSocket Service for real-time stock price streaming.
 * Connects to Massive Data's WebSocket API and streams live aggregate (minute) data.
 *
 * WebSocket URL: wss://socket.massive.com/stocks (real-time) or wss://delayed.massive.com/stocks (15-min delayed)
 * Authentication: {"action": "auth", "params": "YOUR_API_KEY"}
 * Subscription: {"action": "subscribe", "params": "AM.AAPL,AM.MSFT"} for minute aggregates
 */
@Service
public class MassiveDataWebSocketService {

    private static final Logger logger = LoggerFactory.getLogger(MassiveDataWebSocketService.class);

    @Value("${app.massivedata.api-key:}")
    private String apiKey;

    @Value("${app.massivedata.websocket.url:wss://delayed.massive.com/stocks}")
    private String websocketUrl;

    @Value("${app.massivedata.websocket.enabled:true}")
    private boolean enabled;

    @Value("${app.massivedata.websocket.reconnect-delay-ms:5000}")
    private long reconnectDelayMs;

    @Value("${app.massivedata.websocket.heartbeat-interval-ms:30000}")
    private long heartbeatIntervalMs;

    private final ObjectMapper objectMapper;
    private MassiveWebSocket webSocketClient;
    private final Set<String> subscribedSymbols = ConcurrentHashMap.newKeySet();
    private final Map<String, AggregateBar> latestBars = new ConcurrentHashMap<>();
    private final List<Consumer<AggregateBar>> barListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicBoolean isAuthenticated = new AtomicBoolean(false);
    private final AtomicBoolean shouldReconnect = new AtomicBoolean(true);
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> reconnectTask;

    // Use the full S&P 500 ticker list from configuration
    private static final List<String> DEFAULT_TICKERS = SP500Tickers.getTickers();

    public MassiveDataWebSocketService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        if (!enabled) {
            logger.info("Massive Data WebSocket is disabled");
            return;
        }

        if (apiKey == null || apiKey.isEmpty()) {
            logger.warn("Massive Data API key not configured. Real-time streaming disabled.");
            return;
        }

        scheduler = Executors.newScheduledThreadPool(2);
        connect();
    }

    @PreDestroy
    public void shutdown() {
        shouldReconnect.set(false);

        if (heartbeatTask != null) {
            heartbeatTask.cancel(true);
        }
        if (reconnectTask != null) {
            reconnectTask.cancel(true);
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (webSocketClient != null) {
            webSocketClient.close();
        }

        logger.info("Massive Data WebSocket service shut down");
    }

    /**
     * Connect to Massive Data WebSocket
     */
    public void connect() {
        if (apiKey == null || apiKey.isEmpty()) {
            logger.error("Cannot connect: Massive Data API key not configured");
            return;
        }

        try {
            URI uri = new URI(websocketUrl);
            webSocketClient = new MassiveWebSocket(uri);
            webSocketClient.connect();

            logger.info("Connecting to Massive Data WebSocket: {}", websocketUrl);
        } catch (Exception e) {
            logger.error("Failed to connect to Massive Data WebSocket", e);
            scheduleReconnect();
        }
    }

    /**
     * Authenticate with the API
     */
    private void authenticate() {
        try {
            String authMessage = objectMapper.writeValueAsString(Map.of(
                    "action", "auth",
                    "params", apiKey
            ));
            webSocketClient.send(authMessage);
            logger.info("Sent authentication request to Massive Data");
        } catch (Exception e) {
            logger.error("Failed to send authentication", e);
        }
    }

    /**
     * Subscribe to minute aggregate data for a symbol
     */
    public void subscribe(String symbol) {
        if (!isAuthenticated.get()) {
            subscribedSymbols.add(symbol);
            logger.debug("Queued subscription for {} (not yet authenticated)", symbol);
            return;
        }

        if (subscribedSymbols.add(symbol)) {
            sendSubscribe(symbol);
        }
    }

    /**
     * Subscribe to default tickers
     */
    public void subscribeToDefaults() {
        logger.info("Subscribing to default tickers ({} symbols)", DEFAULT_TICKERS.size());
        for (String ticker : DEFAULT_TICKERS) {
            subscribe(ticker);
        }
    }

    /**
     * Subscribe to a list of tickers
     */
    public void subscribeToTickers(List<String> tickers) {
        logger.info("Subscribing to {} tickers", tickers.size());
        for (String ticker : tickers) {
            subscribe(ticker);
        }
    }

    /**
     * Unsubscribe from a symbol
     */
    public void unsubscribe(String symbol) {
        if (subscribedSymbols.remove(symbol) && isAuthenticated.get()) {
            sendUnsubscribe(symbol);
        }
    }

    /**
     * Get the latest bar for a symbol
     */
    public Optional<AggregateBar> getLatestBar(String symbol) {
        return Optional.ofNullable(latestBars.get(symbol));
    }

    /**
     * Get all latest bars
     */
    public Map<String, AggregateBar> getAllLatestBars() {
        return new HashMap<>(latestBars);
    }

    /**
     * Add a listener for real-time bar updates
     */
    public void addBarListener(Consumer<AggregateBar> listener) {
        barListeners.add(listener);
    }

    /**
     * Remove a bar listener
     */
    public void removeBarListener(Consumer<AggregateBar> listener) {
        barListeners.remove(listener);
    }

    /**
     * Check if connected to WebSocket
     */
    public boolean isConnected() {
        return isConnected.get();
    }

    /**
     * Check if authenticated
     */
    public boolean isAuthenticated() {
        return isAuthenticated.get();
    }

    /**
     * Get list of subscribed symbols
     */
    public Set<String> getSubscribedSymbols() {
        return new HashSet<>(subscribedSymbols);
    }

    /**
     * Get connection status
     */
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", enabled);
        status.put("connected", isConnected.get());
        status.put("authenticated", isAuthenticated.get());
        status.put("subscribedCount", subscribedSymbols.size());
        status.put("subscribedSymbols", new ArrayList<>(subscribedSymbols));
        status.put("latestBarsCount", latestBars.size());
        status.put("websocketUrl", websocketUrl);
        return status;
    }

    private void sendSubscribe(String symbol) {
        try {
            // Massive Data uses "AM." prefix for aggregate minute data
            String subscribeParam = "AM." + symbol.toUpperCase();
            String message = objectMapper.writeValueAsString(Map.of(
                    "action", "subscribe",
                    "params", subscribeParam
            ));
            webSocketClient.send(message);
            logger.debug("Subscribed to {}", subscribeParam);
        } catch (Exception e) {
            logger.error("Failed to subscribe to {}", symbol, e);
        }
    }

    private void sendUnsubscribe(String symbol) {
        try {
            String unsubscribeParam = "AM." + symbol.toUpperCase();
            String message = objectMapper.writeValueAsString(Map.of(
                    "action", "unsubscribe",
                    "params", unsubscribeParam
            ));
            webSocketClient.send(message);
            logger.debug("Unsubscribed from {}", unsubscribeParam);
        } catch (Exception e) {
            logger.error("Failed to unsubscribe from {}", symbol, e);
        }
    }

    private void handleMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);

            // Check for status messages
            if (root.has("status")) {
                String status = root.path("status").asText();
                if ("auth_success".equals(status)) {
                    logger.info("Massive Data WebSocket authentication successful");
                    isAuthenticated.set(true);
                    // Subscribe to queued symbols
                    if (!subscribedSymbols.isEmpty()) {
                        scheduler.schedule(this::resubscribeAll, 500, TimeUnit.MILLISECONDS);
                    }
                } else if ("auth_failed".equals(status)) {
                    logger.error("Massive Data WebSocket authentication failed: {}", root.path("message").asText());
                    isAuthenticated.set(false);
                } else if ("connected".equals(status)) {
                    logger.info("Massive Data WebSocket connected, authenticating...");
                    authenticate();
                }
                return;
            }

            // Handle array of events
            if (root.isArray()) {
                for (JsonNode event : root) {
                    processEvent(event);
                }
            } else {
                processEvent(root);
            }

        } catch (Exception e) {
            logger.error("Failed to parse WebSocket message: {}", message, e);
        }
    }

    private void processEvent(JsonNode event) {
        try {
            String eventType = event.path("ev").asText();

            if ("AM".equals(eventType)) {
                // Aggregate minute bar
                processAggregateBar(event);
            } else if ("status".equals(eventType)) {
                String status = event.path("status").asText();
                String message = event.path("message").asText();
                logger.info("Status event: {} - {}", status, message);
            }
        } catch (Exception e) {
            logger.error("Failed to process event", e);
        }
    }

    private void processAggregateBar(JsonNode bar) {
        try {
            String symbol = bar.path("sym").asText();
            double open = bar.path("o").asDouble();
            double high = bar.path("h").asDouble();
            double low = bar.path("l").asDouble();
            double close = bar.path("c").asDouble();
            long volume = bar.path("v").asLong();
            double vwap = bar.path("a").asDouble(); // VWAP
            long startTime = bar.path("s").asLong();
            long endTime = bar.path("e").asLong();

            AggregateBar aggregateBar = new AggregateBar(
                    symbol,
                    open, high, low, close,
                    volume, vwap,
                    Instant.ofEpochMilli(startTime),
                    Instant.ofEpochMilli(endTime)
            );

            // Calculate change from previous bar
            AggregateBar previous = latestBars.put(symbol, aggregateBar);
            if (previous != null) {
                aggregateBar.setPreviousClose(previous.getClose());
                aggregateBar.setChange(close - previous.getClose());
                aggregateBar.setChangePercent(((close - previous.getClose()) / previous.getClose()) * 100);
            }

            // Notify listeners
            for (Consumer<AggregateBar> listener : barListeners) {
                try {
                    listener.accept(aggregateBar);
                } catch (Exception e) {
                    logger.error("Error in bar listener", e);
                }
            }

            logger.trace("Aggregate bar: {} O={} H={} L={} C={} V={}",
                    symbol, open, high, low, close, volume);
        } catch (Exception e) {
            logger.error("Failed to process aggregate bar", e);
        }
    }

    private void scheduleReconnect() {
        if (!shouldReconnect.get()) {
            return;
        }

        if (reconnectTask != null && !reconnectTask.isDone()) {
            return;
        }

        reconnectTask = scheduler.schedule(() -> {
            logger.info("Attempting to reconnect to Massive Data WebSocket...");
            isAuthenticated.set(false);
            connect();
        }, reconnectDelayMs, TimeUnit.MILLISECONDS);
    }

    private void startHeartbeat() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(false);
        }

        heartbeatTask = scheduler.scheduleAtFixedRate(() -> {
            if (isConnected.get() && webSocketClient != null) {
                try {
                    webSocketClient.sendPing();
                } catch (Exception e) {
                    logger.warn("Failed to send heartbeat", e);
                }
            }
        }, heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS);
    }

    private void resubscribeAll() {
        logger.info("Subscribing to {} queued symbols", subscribedSymbols.size());

        // Build batch subscription (Massive Data supports comma-separated)
        List<String> symbols = new ArrayList<>(subscribedSymbols);
        int batchSize = 10;

        for (int i = 0; i < symbols.size(); i += batchSize) {
            List<String> batch = symbols.subList(i, Math.min(i + batchSize, symbols.size()));
            String params = batch.stream()
                    .map(s -> "AM." + s.toUpperCase())
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");

            try {
                String message = objectMapper.writeValueAsString(Map.of(
                        "action", "subscribe",
                        "params", params
                ));
                webSocketClient.send(message);
                logger.debug("Batch subscribed to: {}", params);

                // Small delay between batches
                Thread.sleep(100);
            } catch (Exception e) {
                logger.error("Failed to batch subscribe", e);
            }
        }
    }

    /**
     * Inner WebSocket client class
     */
    private class MassiveWebSocket extends WebSocketClient {

        public MassiveWebSocket(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            logger.info("Connected to Massive Data WebSocket");
            isConnected.set(true);
            startHeartbeat();

            // Authenticate immediately after connection
            authenticate();
        }

        @Override
        public void onMessage(String message) {
            handleMessage(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            logger.warn("Massive Data WebSocket closed: code={}, reason={}, remote={}", code, reason, remote);
            isConnected.set(false);
            isAuthenticated.set(false);

            if (shouldReconnect.get()) {
                scheduleReconnect();
            }
        }

        @Override
        public void onError(Exception ex) {
            logger.error("Massive Data WebSocket error", ex);
            isConnected.set(false);
        }
    }

    /**
     * Aggregate bar data class (minute OHLCV data)
     */
    public static class AggregateBar {
        private final String symbol;
        private final double open;
        private final double high;
        private final double low;
        private final double close;
        private final long volume;
        private final double vwap;
        private final Instant startTime;
        private final Instant endTime;
        private double previousClose;
        private double change;
        private double changePercent;

        public AggregateBar(String symbol, double open, double high, double low, double close,
                           long volume, double vwap, Instant startTime, Instant endTime) {
            this.symbol = symbol;
            this.open = open;
            this.high = high;
            this.low = low;
            this.close = close;
            this.volume = volume;
            this.vwap = vwap;
            this.startTime = startTime;
            this.endTime = endTime;
        }

        public String getSymbol() { return symbol; }
        public double getOpen() { return open; }
        public double getHigh() { return high; }
        public double getLow() { return low; }
        public double getClose() { return close; }
        public long getVolume() { return volume; }
        public double getVwap() { return vwap; }
        public Instant getStartTime() { return startTime; }
        public Instant getEndTime() { return endTime; }
        public double getPreviousClose() { return previousClose; }
        public void setPreviousClose(double previousClose) { this.previousClose = previousClose; }
        public double getChange() { return change; }
        public void setChange(double change) { this.change = change; }
        public double getChangePercent() { return changePercent; }
        public void setChangePercent(double changePercent) { this.changePercent = changePercent; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("symbol", symbol);
            map.put("open", open);
            map.put("high", high);
            map.put("low", low);
            map.put("close", close);
            map.put("volume", volume);
            map.put("vwap", vwap);
            map.put("startTime", startTime.toEpochMilli());
            map.put("endTime", endTime.toEpochMilli());
            map.put("previousClose", previousClose);
            map.put("change", change);
            map.put("changePercent", changePercent);
            return map;
        }
    }
}
