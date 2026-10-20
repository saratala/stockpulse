package com.stockpulse.agent.controller;

import com.stockpulse.agent.trading.AutoTraderService;
import com.stockpulse.agent.trading.TradeExecution;
import com.stockpulse.agent.trading.TradeExecutionRepository;
import com.stockpulse.agent.trading.TradingProperties;
import com.stockpulse.agent.trading.alpaca.AlpacaClient;
import com.stockpulse.agent.trading.alpaca.AlpacaModels.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Operator surface for the auto-trader. Everything that changes state is a POST,
 * and the destructive ones (kill switch, close-all) are deliberately separate endpoints.
 *
 * NOTE: this API has no auth of its own. Keep the backend behind the frontend/nginx and
 * never expose 8181 publicly while trading is enabled.
 */
@RestController
@RequestMapping("/trading")
@RequiredArgsConstructor
@Slf4j
public class TradingController {

    private final TradingProperties props;
    private final AlpacaClient alpaca;
    private final AutoTraderService trader;
    private final TradeExecutionRepository executionRepo;

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", props.isEnabled());
        m.put("killSwitch", props.isKillSwitch());
        m.put("mode", trader.mode());
        m.put("brokerConfigured", alpaca.isConfigured());
        m.put("running", trader.isRunning());
        m.put("lastRunAt", trader.getLastRunAt());
        m.put("lastRunSummary", trader.getLastRunSummary());
        m.put("cron", props.getCron());
        m.put("risk", props.getRisk());
        m.put("signals", props.getSignals());
        if (alpaca.isConfigured()) {
            try {
                Clock c = alpaca.getClock();
                m.put("marketOpen", c.isOpen());
                m.put("nextOpen", c.nextOpen());
                m.put("nextClose", c.nextClose());
            } catch (Exception e) {
                m.put("marketOpen", null);
                m.put("brokerError", e.getMessage());
            }
        }
        return m;
    }

    @GetMapping("/account")
    public ResponseEntity<?> account() {
        return broker(alpaca::getAccount);
    }

    @GetMapping("/positions")
    public ResponseEntity<?> positions() {
        return broker(alpaca::getPositions);
    }

    @GetMapping("/orders")
    public ResponseEntity<?> orders(@RequestParam(defaultValue = "all") String status,
                                    @RequestParam(defaultValue = "50") int limit) {
        return broker(() -> alpaca.getOrders(status, Math.min(limit, 500)));
    }

    @GetMapping("/executions")
    public List<TradeExecution> executions(@RequestParam(required = false) String ticker,
                                           @RequestParam(defaultValue = "100") int limit) {
        PageRequest page = PageRequest.of(0, Math.min(limit, 500));
        return ticker == null ? executionRepo.findRecent(page) : executionRepo.findByTicker(ticker.toUpperCase(), page);
    }

    /** Runs one trading cycle now, regardless of the cron. Still subject to every risk gate. */
    @PostMapping("/run")
    public ResponseEntity<AutoTraderService.RunResult> run() {
        AutoTraderService.RunResult r = trader.runOnce();
        return r.ok() ? ResponseEntity.ok(r) : ResponseEntity.status(HttpStatus.CONFLICT).body(r);
    }

    /**
     * Flips the runtime kill switch. {@code engage=true} halts new entries immediately;
     * add {@code liquidate=true} to also close every open position and cancel open orders.
     */
    @PostMapping("/kill-switch")
    public Map<String, Object> killSwitch(@RequestParam boolean engage,
                                          @RequestParam(defaultValue = "false") boolean liquidate) {
        props.setKillSwitch(engage);
        log.warn("Kill switch set to {} (liquidate={})", engage, liquidate);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("killSwitch", engage);
        if (engage && liquidate) {
            try {
                alpaca.closeAllPositions();
                m.put("liquidated", true);
            } catch (Exception e) {
                log.error("Liquidation failed", e);
                m.put("liquidated", false);
                m.put("error", e.getMessage());
            }
        }
        return m;
    }

    @PostMapping("/close/{symbol}")
    public ResponseEntity<?> close(@PathVariable String symbol) {
        return broker(() -> alpaca.closePosition(symbol.toUpperCase()));
    }

    private ResponseEntity<?> broker(java.util.function.Supplier<?> call) {
        if (!alpaca.isConfigured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Alpaca credentials not configured"));
        }
        try {
            return ResponseEntity.ok(call.get());
        } catch (AlpacaClient.AlpacaException e) {
            int code = e.getStatus() >= 400 && e.getStatus() < 600 ? e.getStatus() : 502;
            return ResponseEntity.status(code).body(Map.of("error", e.getMessage()));
        }
    }
}
