package com.stockpulse.agent.controller;

import com.stockpulse.agent.service.MassiveDataWebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for Massive Data WebSocket real-time streaming.
 * Provides endpoints for managing WebSocket connections and subscriptions.
 */
@RestController
@RequestMapping("/massivedata/websocket")
@RequiredArgsConstructor
@Slf4j
public class MassiveDataWebSocketController {

    private final MassiveDataWebSocketService webSocketService;

    /**
     * Get WebSocket connection status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(webSocketService.getStatus());
    }

    /**
     * Connect to WebSocket (if not already connected)
     */
    @PostMapping("/connect")
    public ResponseEntity<Map<String, Object>> connect() {
        if (webSocketService.isConnected()) {
            return ResponseEntity.ok(Map.of(
                    "status", "already_connected",
                    "authenticated", webSocketService.isAuthenticated()
            ));
        }

        webSocketService.connect();

        Map<String, Object> response = new HashMap<>();
        response.put("status", "connecting");
        response.put("message", "WebSocket connection initiated");
        return ResponseEntity.accepted().body(response);
    }

    /**
     * Subscribe to a single symbol
     */
    @PostMapping("/subscribe/{symbol}")
    public ResponseEntity<Map<String, Object>> subscribe(@PathVariable String symbol) {
        webSocketService.subscribe(symbol.toUpperCase());

        return ResponseEntity.ok(Map.of(
                "status", "subscribed",
                "symbol", symbol.toUpperCase(),
                "connected", webSocketService.isConnected(),
                "authenticated", webSocketService.isAuthenticated()
        ));
    }

    /**
     * Subscribe to multiple symbols
     */
    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, Object>> subscribeMultiple(@RequestBody List<String> symbols) {
        List<String> upperSymbols = symbols.stream()
                .map(String::toUpperCase)
                .collect(Collectors.toList());

        webSocketService.subscribeToTickers(upperSymbols);

        return ResponseEntity.ok(Map.of(
                "status", "subscribed",
                "symbols", upperSymbols,
                "count", upperSymbols.size(),
                "connected", webSocketService.isConnected(),
                "authenticated", webSocketService.isAuthenticated()
        ));
    }

    /**
     * Subscribe to default tickers
     */
    @PostMapping("/subscribe/defaults")
    public ResponseEntity<Map<String, Object>> subscribeDefaults() {
        webSocketService.subscribeToDefaults();

        return ResponseEntity.ok(Map.of(
                "status", "subscribed",
                "message", "Subscribed to default tickers",
                "connected", webSocketService.isConnected(),
                "authenticated", webSocketService.isAuthenticated()
        ));
    }

    /**
     * Unsubscribe from a symbol
     */
    @PostMapping("/unsubscribe/{symbol}")
    public ResponseEntity<Map<String, Object>> unsubscribe(@PathVariable String symbol) {
        webSocketService.unsubscribe(symbol.toUpperCase());

        return ResponseEntity.ok(Map.of(
                "status", "unsubscribed",
                "symbol", symbol.toUpperCase()
        ));
    }

    /**
     * Get latest bar for a symbol
     */
    @GetMapping("/bar/{symbol}")
    public ResponseEntity<?> getLatestBar(@PathVariable String symbol) {
        return webSocketService.getLatestBar(symbol.toUpperCase())
                .map(bar -> ResponseEntity.ok(bar.toMap()))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all latest bars
     */
    @GetMapping("/bars")
    public ResponseEntity<Map<String, Object>> getAllLatestBars() {
        Map<String, MassiveDataWebSocketService.AggregateBar> bars = webSocketService.getAllLatestBars();

        Map<String, Object> result = new HashMap<>();
        result.put("count", bars.size());
        result.put("bars", bars.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().toMap()
                )));

        return ResponseEntity.ok(result);
    }

    /**
     * Get list of subscribed symbols
     */
    @GetMapping("/subscriptions")
    public ResponseEntity<Map<String, Object>> getSubscriptions() {
        return ResponseEntity.ok(Map.of(
                "symbols", webSocketService.getSubscribedSymbols(),
                "count", webSocketService.getSubscribedSymbols().size()
        ));
    }
}
