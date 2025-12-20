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

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Finnhub WebSocket Service for real-time stock price streaming.
 * Connects to Finnhub's WebSocket API and streams live trade data.
 */
@Service
public class FinnhubWebSocketService {

    private static final Logger logger = LoggerFactory.getLogger(FinnhubWebSocketService.class);

    @Value("${app.finnhub.api-key:}")
    private String apiKey;

    @Value("${app.finnhub.websocket-url:wss://ws.finnhub.io}")
    private String websocketUrl;

    @Value("${app.finnhub.enabled:true}")
    private boolean enabled;

    @Value("${app.finnhub.reconnect-delay-ms:5000}")
    private long reconnectDelayMs;

    @Value("${app.finnhub.heartbeat-interval-ms:30000}")
    private long heartbeatIntervalMs;

    private final ObjectMapper objectMapper;
    private FinnhubWebSocket webSocketClient;
    private final Set<String> subscribedSymbols = ConcurrentHashMap.newKeySet();
    private final Map<String, RealTimeQuote> latestQuotes = new ConcurrentHashMap<>();
    private final List<Consumer<RealTimeQuote>> priceListeners = new CopyOnWriteArrayList<>();
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicBoolean shouldReconnect = new AtomicBoolean(true);
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> reconnectTask;

    // S&P 500 tickers (top 100 for demo - can be expanded)
    private static final List<String> SP500_TICKERS = Arrays.asList(
            "AAPL", "MSFT", "AMZN", "NVDA", "GOOGL", "META", "TSLA", "BRK.B", "UNH", "XOM",
            "JNJ", "JPM", "V", "PG", "MA", "HD", "CVX", "MRK", "ABBV", "LLY",
            "PEP", "KO", "COST", "AVGO", "WMT", "MCD", "CSCO", "TMO", "ACN", "ABT",
            "DHR", "NEE", "VZ", "ADBE", "CRM", "CMCSA", "NKE", "TXN", "PM", "BMY",
            "LIN", "RTX", "INTC", "HON", "ORCL", "UPS", "AMD", "QCOM", "COP", "LOW",
            "AMGN", "IBM", "SPGI", "BA", "GE", "CAT", "INTU", "SBUX", "PLD", "DE",
            "MDLZ", "MS", "GS", "BLK", "AMAT", "NOW", "ISRG", "AXP", "ELV", "GILD",
            "ADI", "MMC", "TJX", "LMT", "CVS", "C", "SYK", "REGN", "BKNG", "VRTX",
            "SCHW", "MO", "ZTS", "ADP", "CI", "TMUS", "CME", "PGR", "LRCX", "CB",
            "ETN", "EOG", "FI", "BSX", "PANW", "SO", "DUK", "SLB", "MU", "SNOW"
    );

    public FinnhubWebSocketService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        if (!enabled) {
            logger.info("Finnhub WebSocket is disabled");
            return;
        }

        if (apiKey == null || apiKey.isEmpty()) {
            logger.warn("Finnhub API key not configured. Real-time streaming disabled.");
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

        logger.info("Finnhub WebSocket service shut down");
    }

    /**
     * Connect to Finnhub WebSocket
     */
    public void connect() {
        if (apiKey == null || apiKey.isEmpty()) {
            logger.error("Cannot connect: Finnhub API key not configured");
            return;
        }

        try {
            String wsUrl = websocketUrl + "?token=" + apiKey;
            URI uri = new URI(wsUrl);

            webSocketClient = new FinnhubWebSocket(uri);
            webSocketClient.connect();

            logger.info("Connecting to Finnhub WebSocket...");
        } catch (Exception e) {
            logger.error("Failed to connect to Finnhub WebSocket", e);
            scheduleReconnect();
        }
    }

    /**
     * Subscribe to real-time updates for a symbol
     */
    public void subscribe(String symbol) {
        if (!isConnected.get()) {
            subscribedSymbols.add(symbol);
            logger.debug("Queued subscription for {} (not yet connected)", symbol);
            return;
        }

        if (subscribedSymbols.add(symbol)) {
            sendSubscribe(symbol);
        }
    }

    /**
     * Subscribe to all S&P 500 tickers
     */
    public void subscribeToSP500() {
        logger.info("Subscribing to S&P 500 tickers ({} symbols)", SP500_TICKERS.size());
        for (String ticker : SP500_TICKERS) {
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
        if (subscribedSymbols.remove(symbol) && isConnected.get()) {
            sendUnsubscribe(symbol);
        }
    }

    /**
     * Get the latest quote for a symbol
     */
    public Optional<RealTimeQuote> getLatestQuote(String symbol) {
        return Optional.ofNullable(latestQuotes.get(symbol));
    }

    /**
     * Get all latest quotes
     */
    public Map<String, RealTimeQuote> getAllLatestQuotes() {
        return new HashMap<>(latestQuotes);
    }

    /**
     * Add a listener for real-time price updates
     */
    public void addPriceListener(Consumer<RealTimeQuote> listener) {
        priceListeners.add(listener);
    }

    /**
     * Remove a price listener
     */
    public void removePriceListener(Consumer<RealTimeQuote> listener) {
        priceListeners.remove(listener);
    }

    /**
     * Check if connected to WebSocket
     */
    public boolean isConnected() {
        return isConnected.get();
    }

    /**
     * Get list of subscribed symbols
     */
    public Set<String> getSubscribedSymbols() {
        return new HashSet<>(subscribedSymbols);
    }

    /**
     * Get S&P 500 ticker list
     */
    public List<String> getSP500Tickers() {
        return new ArrayList<>(SP500_TICKERS);
    }

    private void sendSubscribe(String symbol) {
        try {
            String message = objectMapper.writeValueAsString(Map.of(
                    "type", "subscribe",
                    "symbol", symbol
            ));
            webSocketClient.send(message);
            logger.debug("Subscribed to {}", symbol);
        } catch (Exception e) {
            logger.error("Failed to subscribe to {}", symbol, e);
        }
    }

    private void sendUnsubscribe(String symbol) {
        try {
            String message = objectMapper.writeValueAsString(Map.of(
                    "type", "unsubscribe",
                    "symbol", symbol
            ));
            webSocketClient.send(message);
            logger.debug("Unsubscribed from {}", symbol);
        } catch (Exception e) {
            logger.error("Failed to unsubscribe from {}", symbol, e);
        }
    }

    private void handleMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String type = root.path("type").asText();

            if ("trade".equals(type)) {
                JsonNode data = root.path("data");
                if (data.isArray()) {
                    for (JsonNode trade : data) {
                        processTradeData(trade);
                    }
                }
            } else if ("ping".equals(type)) {
                logger.trace("Received ping from Finnhub");
            } else if ("error".equals(type)) {
                logger.error("Finnhub error: {}", root.path("msg").asText());
            }
        } catch (Exception e) {
            logger.error("Failed to parse WebSocket message: {}", message, e);
        }
    }

    private void processTradeData(JsonNode trade) {
        try {
            String symbol = trade.path("s").asText();
            double price = trade.path("p").asDouble();
            long volume = trade.path("v").asLong();
            long timestamp = trade.path("t").asLong();

            RealTimeQuote quote = new RealTimeQuote(
                    symbol,
                    price,
                    volume,
                    Instant.ofEpochMilli(timestamp)
            );

            // Update latest quote
            RealTimeQuote previous = latestQuotes.put(symbol, quote);

            // Calculate change if we have previous data
            if (previous != null) {
                quote.setPreviousPrice(previous.getPrice());
                quote.setChange(price - previous.getPrice());
                quote.setChangePercent(((price - previous.getPrice()) / previous.getPrice()) * 100);
            }

            // Notify listeners
            for (Consumer<RealTimeQuote> listener : priceListeners) {
                try {
                    listener.accept(quote);
                } catch (Exception e) {
                    logger.error("Error in price listener", e);
                }
            }

            logger.trace("Trade: {} @ ${} vol={}", symbol, price, volume);
        } catch (Exception e) {
            logger.error("Failed to process trade data", e);
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
            logger.info("Attempting to reconnect to Finnhub WebSocket...");
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
        logger.info("Resubscribing to {} symbols", subscribedSymbols.size());
        for (String symbol : subscribedSymbols) {
            sendSubscribe(symbol);
            // Small delay to avoid rate limiting
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Inner WebSocket client class
     */
    private class FinnhubWebSocket extends WebSocketClient {

        public FinnhubWebSocket(URI serverUri) {
            super(serverUri);
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            logger.info("Connected to Finnhub WebSocket");
            isConnected.set(true);
            startHeartbeat();

            // Resubscribe to all symbols
            if (!subscribedSymbols.isEmpty()) {
                scheduler.schedule(() -> resubscribeAll(), 1000, TimeUnit.MILLISECONDS);
            }
        }

        @Override
        public void onMessage(String message) {
            handleMessage(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            logger.warn("Finnhub WebSocket closed: code={}, reason={}, remote={}", code, reason, remote);
            isConnected.set(false);

            if (shouldReconnect.get()) {
                scheduleReconnect();
            }
        }

        @Override
        public void onError(Exception ex) {
            logger.error("Finnhub WebSocket error", ex);
            isConnected.set(false);
        }
    }

    /**
     * Real-time quote data class
     */
    public static class RealTimeQuote {
        private final String symbol;
        private final double price;
        private final long volume;
        private final Instant timestamp;
        private double previousPrice;
        private double change;
        private double changePercent;

        public RealTimeQuote(String symbol, double price, long volume, Instant timestamp) {
            this.symbol = symbol;
            this.price = price;
            this.volume = volume;
            this.timestamp = timestamp;
        }

        public String getSymbol() { return symbol; }
        public double getPrice() { return price; }
        public long getVolume() { return volume; }
        public Instant getTimestamp() { return timestamp; }
        public double getPreviousPrice() { return previousPrice; }
        public void setPreviousPrice(double previousPrice) { this.previousPrice = previousPrice; }
        public double getChange() { return change; }
        public void setChange(double change) { this.change = change; }
        public double getChangePercent() { return changePercent; }
        public void setChangePercent(double changePercent) { this.changePercent = changePercent; }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("symbol", symbol);
            map.put("price", price);
            map.put("volume", volume);
            map.put("timestamp", timestamp.toEpochMilli());
            map.put("previousPrice", previousPrice);
            map.put("change", change);
            map.put("changePercent", changePercent);
            return map;
        }
    }
}
