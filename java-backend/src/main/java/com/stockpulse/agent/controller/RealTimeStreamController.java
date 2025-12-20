package com.stockpulse.agent.controller;

import com.stockpulse.agent.service.FinnhubWebSocketService;
import com.stockpulse.agent.service.FinnhubWebSocketService.RealTimeQuote;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * REST Controller for real-time stock price streaming via Server-Sent Events (SSE).
 * Connects to the Finnhub WebSocket service and broadcasts price updates to all connected clients.
 */
@RestController
@RequestMapping("/stream")
@CrossOrigin(origins = "*")
public class RealTimeStreamController {

    private static final Logger logger = LoggerFactory.getLogger(RealTimeStreamController.class);
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L; // 30 minutes

    private final FinnhubWebSocketService finnhubService;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();

    public RealTimeStreamController(FinnhubWebSocketService finnhubService) {
        this.finnhubService = finnhubService;

        // Register as a price listener
        finnhubService.addPriceListener(this::broadcastQuote);

        // Start heartbeat to keep connections alive
        startHeartbeat();

        logger.info("RealTimeStreamController initialized");
    }

    /**
     * SSE endpoint for streaming real-time stock prices.
     * Clients connect to this endpoint to receive live price updates.
     */
    @GetMapping(value = "/prices", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPrices() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        emitter.onCompletion(() -> {
            emitters.remove(emitter);
            logger.debug("SSE client disconnected. Active connections: {}", emitters.size());
        });

        emitter.onTimeout(() -> {
            emitters.remove(emitter);
            logger.debug("SSE client timed out. Active connections: {}", emitters.size());
        });

        emitter.onError(e -> {
            emitters.remove(emitter);
            logger.debug("SSE client error: {}. Active connections: {}", e.getMessage(), emitters.size());
        });

        emitters.add(emitter);
        logger.info("New SSE client connected. Active connections: {}", emitters.size());

        // Send initial connection message
        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of(
                            "status", "connected",
                            "subscribedSymbols", finnhubService.getSubscribedSymbols().size(),
                            "isWebSocketConnected", finnhubService.isConnected()
                    )));
        } catch (IOException e) {
            logger.error("Failed to send initial connection message", e);
        }

        // Send current quotes snapshot
        sendCurrentQuotesSnapshot(emitter);

        return emitter;
    }

    /**
     * Subscribe to specific tickers for real-time updates.
     */
    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, Object>> subscribe(@RequestBody List<String> tickers) {
        finnhubService.subscribeToTickers(tickers);

        return ResponseEntity.ok(Map.of(
                "status", "subscribed",
                "tickers", tickers,
                "totalSubscribed", finnhubService.getSubscribedSymbols().size()
        ));
    }

    /**
     * Subscribe to all S&P 500 tickers.
     */
    @PostMapping("/subscribe/sp500")
    public ResponseEntity<Map<String, Object>> subscribeToSP500() {
        finnhubService.subscribeToSP500();

        return ResponseEntity.ok(Map.of(
                "status", "subscribed",
                "message", "Subscribed to S&P 500 tickers",
                "tickerCount", finnhubService.getSP500Tickers().size()
        ));
    }

    /**
     * Unsubscribe from specific tickers.
     */
    @PostMapping("/unsubscribe")
    public ResponseEntity<Map<String, Object>> unsubscribe(@RequestBody List<String> tickers) {
        for (String ticker : tickers) {
            finnhubService.unsubscribe(ticker);
        }

        return ResponseEntity.ok(Map.of(
                "status", "unsubscribed",
                "tickers", tickers,
                "remainingSubscribed", finnhubService.getSubscribedSymbols().size()
        ));
    }

    /**
     * Get current WebSocket connection status.
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "webSocketConnected", finnhubService.isConnected(),
                "subscribedSymbols", finnhubService.getSubscribedSymbols(),
                "activeSSEConnections", emitters.size(),
                "latestQuotesCount", finnhubService.getAllLatestQuotes().size()
        ));
    }

    /**
     * Get the latest quote for a specific symbol.
     */
    @GetMapping("/quote/{symbol}")
    public ResponseEntity<?> getLatestQuote(@PathVariable String symbol) {
        return finnhubService.getLatestQuote(symbol.toUpperCase())
                .map(quote -> ResponseEntity.ok(quote.toMap()))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all latest quotes.
     */
    @GetMapping("/quotes")
    public ResponseEntity<Map<String, Object>> getAllQuotes() {
        Map<String, RealTimeQuote> quotes = finnhubService.getAllLatestQuotes();

        return ResponseEntity.ok(Map.of(
                "count", quotes.size(),
                "quotes", quotes.entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                e -> e.getValue().toMap()
                        ))
        ));
    }

    /**
     * Get the list of available S&P 500 tickers.
     */
    @GetMapping("/sp500-tickers")
    public ResponseEntity<List<String>> getSP500Tickers() {
        return ResponseEntity.ok(finnhubService.getSP500Tickers());
    }

    /**
     * Broadcast a quote to all connected SSE clients.
     */
    private void broadcastQuote(RealTimeQuote quote) {
        if (emitters.isEmpty()) {
            return;
        }

        List<SseEmitter> deadEmitters = new java.util.ArrayList<>();

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name("price")
                        .data(quote.toMap()));
            } catch (IOException e) {
                deadEmitters.add(emitter);
            }
        }

        emitters.removeAll(deadEmitters);
    }

    /**
     * Send a snapshot of all current quotes to a newly connected client.
     */
    private void sendCurrentQuotesSnapshot(SseEmitter emitter) {
        Map<String, RealTimeQuote> quotes = finnhubService.getAllLatestQuotes();

        if (quotes.isEmpty()) {
            return;
        }

        try {
            emitter.send(SseEmitter.event()
                    .name("snapshot")
                    .data(Map.of(
                            "type", "snapshot",
                            "count", quotes.size(),
                            "quotes", quotes.entrySet().stream()
                                    .collect(java.util.stream.Collectors.toMap(
                                            Map.Entry::getKey,
                                            e -> e.getValue().toMap()
                                    ))
                    )));
        } catch (IOException e) {
            logger.error("Failed to send quotes snapshot", e);
        }
    }

    /**
     * Start periodic heartbeat to keep SSE connections alive.
     */
    private void startHeartbeat() {
        heartbeatExecutor.scheduleAtFixedRate(() -> {
            if (emitters.isEmpty()) {
                return;
            }

            List<SseEmitter> deadEmitters = new java.util.ArrayList<>();

            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("heartbeat")
                            .data(Map.of(
                                    "timestamp", System.currentTimeMillis(),
                                    "connectedClients", emitters.size()
                            )));
                } catch (IOException e) {
                    deadEmitters.add(emitter);
                }
            }

            emitters.removeAll(deadEmitters);

            if (!deadEmitters.isEmpty()) {
                logger.debug("Removed {} dead SSE connections. Active: {}", deadEmitters.size(), emitters.size());
            }
        }, 15, 15, TimeUnit.SECONDS);
    }
}
