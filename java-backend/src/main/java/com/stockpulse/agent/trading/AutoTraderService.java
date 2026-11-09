package com.stockpulse.agent.trading;

import com.stockpulse.agent.model.SignalPrediction;
import com.stockpulse.agent.repository.SignalPredictionRepository;
import com.stockpulse.agent.trading.alpaca.AlpacaClient;
import com.stockpulse.agent.trading.alpaca.AlpacaModels.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Turns StockPulse signals into broker orders, gated by {@link RiskGuard}.
 *
 * One run:
 *   1. Snapshot account / clock / positions from Alpaca.
 *   2. Global gate (disabled? kill switch? market closed? daily loss hit?) — bail early.
 *   3. EXITS first: any open long whose latest signal is BEARISH at/above exit confidence is closed.
 *   4. ENTRIES: best BULLISH signal per ticker in the lookback window, highest confidence first,
 *      each passed through the per-entry risk gate and submitted as a bracket market order.
 *
 * Every decision — taken or skipped — is written to trade_executions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AutoTraderService {

    private final TradingProperties props;
    private final AlpacaClient alpaca;
    private final RiskGuard riskGuard;
    private final SignalPredictionRepository signalRepo;
    private final TradeExecutionRepository executionRepo;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile LocalDateTime lastRunAt;
    private volatile String lastRunSummary = "never run";

    @Scheduled(cron = "${app.trading.cron:0 */5 * * * *}")
    public void scheduledRun() {
        if (!props.isEnabled()) {
            return; // stay silent when disabled; this is the default state
        }
        runOnce();
    }

    /** Runs one full trading cycle. Safe to call manually; overlapping calls are rejected. */
    public RunResult runOnce() {
        if (!running.compareAndSet(false, true)) {
            return new RunResult(false, "A trading run is already in progress", 0, 0, 0);
        }
        try {
            RunResult r = doRun();
            lastRunAt = LocalDateTime.now();
            lastRunSummary = r.message();
            return r;
        } finally {
            running.set(false);
        }
    }

    private RunResult doRun() {
        if (!alpaca.isConfigured()) {
            return new RunResult(false, "Alpaca not configured", 0, 0, 0);
        }

        RiskGuard.Snapshot snap;
        try {
            snap = snapshot();
        } catch (Exception e) {
            log.error("Could not snapshot broker state", e);
            record(TradeExecution.Action.ERROR, "*", null, null, null, null, "SNAPSHOT_FAILED: " + e.getMessage());
            return new RunResult(false, "Broker snapshot failed: " + e.getMessage(), 0, 0, 0);
        }

        RiskGuard.Decision global = riskGuard.checkGlobal(snap);
        if (!global.allowed()) {
            log.info("Auto-trader run skipped: {}", global.reason());
            return new RunResult(true, "Skipped: " + global.reason(), 0, 0, 0);
        }

        LocalDateTime since = LocalDateTime.now().minusMinutes(props.getSignals().getLookbackMinutes());
        List<SignalPrediction> recent = signalRepo.findRecentSignalPredictions(since);

        // Latest signal per ticker wins.
        Map<String, SignalPrediction> latestByTicker = new LinkedHashMap<>();
        for (SignalPrediction sp : recent) { // already ordered timestamp DESC
            latestByTicker.putIfAbsent(sp.getTicker().toUpperCase(), sp);
        }

        int exits = handleExits(snap, latestByTicker);

        // Re-snapshot positions after exits so entry gating sees the freed slots.
        if (exits > 0) {
            try {
                snap = snapshot();
            } catch (Exception e) {
                log.warn("Re-snapshot after exits failed; skipping entries this run", e);
                return new RunResult(true, "Exits=" + exits + ", entries skipped (re-snapshot failed)", exits, 0, 0);
            }
        }

        int[] entrySkip = handleEntries(snap, latestByTicker);
        String msg = String.format("exits=%d entries=%d skipped=%d", exits, entrySkip[0], entrySkip[1]);
        log.info("Auto-trader run complete: {}", msg);
        return new RunResult(true, msg, exits, entrySkip[0], entrySkip[1]);
    }

    private int handleExits(RiskGuard.Snapshot snap, Map<String, SignalPrediction> latest) {
        int exits = 0;
        BigDecimal exitConf = props.getSignals().getExitConfidence();
        for (Position p : snap.positions()) {
            SignalPrediction sp = latest.get(p.symbol().toUpperCase());
            if (sp == null || sp.getSignalType() != SignalPrediction.SignalType.BEARISH) continue;
            if (sp.getConfidence() == null || sp.getConfidence().compareTo(exitConf) < 0) continue;
            if (executionRepo.existsBySignalPredictionId(sp.getId())) continue; // already acted on

            try {
                Order o = alpaca.closePosition(p.symbol());
                record(TradeExecution.Action.EXIT, p.symbol(), "sell", sp, p.marketValue(), p.currentPrice(),
                    "BEARISH signal conf=" + sp.getConfidence(), o);
                exits++;
            } catch (Exception e) {
                log.error("Exit failed for {}", p.symbol(), e);
                record(TradeExecution.Action.ERROR, p.symbol(), "sell", sp, null, null, "EXIT_FAILED: " + e.getMessage());
            }
        }
        return exits;
    }

    /** @return {entries, skipped} */
    private int[] handleEntries(RiskGuard.Snapshot snap, Map<String, SignalPrediction> latest) {
        int entries = 0, skipped = 0;

        List<SignalPrediction> candidates = latest.values().stream()
            .filter(sp -> sp.getSignalType() == SignalPrediction.SignalType.BULLISH)
            .filter(sp -> !executionRepo.existsBySignalPredictionId(sp.getId()))
            .sorted(Comparator.comparing(SignalPrediction::getConfidence,
                Comparator.nullsLast(Comparator.<BigDecimal>reverseOrder())))
            .collect(Collectors.toList());

        List<Position> livePositions = new ArrayList<>(snap.positions());
        for (SignalPrediction sp : candidates) {
            RiskGuard.Snapshot view = new RiskGuard.Snapshot(snap.account(), snap.clock(), livePositions, snap.now());
            RiskGuard.Decision d = riskGuard.checkEntry(view, sp.getTicker(), sp.getConfidence(), sp.getScreeningScore());
            if (!d.allowed()) {
                // Global denials (e.g. MAX_OPEN_POSITIONS) end the run; per-ticker ones just skip.
                record(TradeExecution.Action.SKIPPED, sp.getTicker(), null, sp, null, sp.getCurrentPrice(), d.reason());
                skipped++;
                if (d.reason().startsWith("MAX_OPEN_POSITIONS") || d.reason().startsWith("INSUFFICIENT")
                    || d.reason().startsWith("TOO_CLOSE")) {
                    break;
                }
                continue;
            }

            try {
                Order o = submitBracketBuy(sp, d.notional());
                record(TradeExecution.Action.ENTRY, sp.getTicker(), "buy", sp, d.notional(), sp.getCurrentPrice(),
                    "BULLISH conf=" + sp.getConfidence() + " score=" + sp.getScreeningScore(), o);
                // Pretend the position exists so subsequent gating in this run counts it.
                livePositions.add(new Position(sp.getTicker(), BigDecimal.ZERO, "long", sp.getCurrentPrice(),
                    sp.getCurrentPrice(), d.notional(), BigDecimal.ZERO, BigDecimal.ZERO));
                entries++;
            } catch (Exception e) {
                log.error("Entry failed for {}", sp.getTicker(), e);
                record(TradeExecution.Action.ERROR, sp.getTicker(), "buy", sp, d.notional(), sp.getCurrentPrice(),
                    "ENTRY_FAILED: " + e.getMessage());
            }
        }
        return new int[]{entries, skipped};
    }

    private Order submitBracketBuy(SignalPrediction sp, BigDecimal notional) {
        TradingProperties.Risk risk = props.getRisk();
        BigDecimal ref = sp.getCurrentPrice();
        // Bracket orders need a qty, not notional, so derive whole shares from the reference price.
        BigDecimal qty = notional.divide(ref, 0, RoundingMode.DOWN);
        if (qty.signum() <= 0) {
            // Price too high for the notional cap: fall back to a plain notional market buy (no bracket).
            return alpaca.submitOrder(new OrderRequest(sp.getTicker(), null, notional.toPlainString(),
                "buy", "market", "day", null, clientId(sp), null, null));
        }
        BigDecimal tp = ref.multiply(BigDecimal.ONE.add(risk.getTakeProfitPct())).setScale(2, RoundingMode.HALF_UP);
        BigDecimal sl = ref.multiply(BigDecimal.ONE.subtract(risk.getStopLossPct())).setScale(2, RoundingMode.HALF_UP);
        return alpaca.submitOrder(new OrderRequest(sp.getTicker(), qty.toPlainString(), null,
            "buy", "market", "day", "bracket", clientId(sp),
            new OrderRequest.TakeProfit(tp.toPlainString()),
            new OrderRequest.StopLoss(sl.toPlainString())));
    }

    private String clientId(SignalPrediction sp) {
        return "sp-" + sp.getId() + "-" + Long.toHexString(System.currentTimeMillis());
    }

    private RiskGuard.Snapshot snapshot() {
        Account a = alpaca.getAccount();
        Clock c = alpaca.getClock();
        List<Position> p = alpaca.getPositions();
        return new RiskGuard.Snapshot(a, c, p, OffsetDateTime.now());
    }

    // ---- audit --------------------------------------------------------------

    private void record(TradeExecution.Action action, String ticker, String side, SignalPrediction sp,
                        BigDecimal notional, BigDecimal refPrice, String reason) {
        record(action, ticker, side, sp, notional, refPrice, reason, null);
    }

    private void record(TradeExecution.Action action, String ticker, String side, SignalPrediction sp,
                        BigDecimal notional, BigDecimal refPrice, String reason, Order order) {
        TradingProperties.Risk risk = props.getRisk();
        BigDecimal tp = null, sl = null;
        if (action == TradeExecution.Action.ENTRY && refPrice != null) {
            tp = refPrice.multiply(BigDecimal.ONE.add(risk.getTakeProfitPct())).setScale(4, RoundingMode.HALF_UP);
            sl = refPrice.multiply(BigDecimal.ONE.subtract(risk.getStopLossPct())).setScale(4, RoundingMode.HALF_UP);
        }
        try {
            executionRepo.save(TradeExecution.builder()
                .ticker(ticker)
                .action(action)
                .side(side)
                .signalPredictionId(sp == null ? null : sp.getId())
                .signalConfidence(sp == null ? null : sp.getConfidence())
                .notional(notional)
                .referencePrice(refPrice)
                .takeProfitPrice(tp)
                .stopLossPrice(sl)
                .brokerOrderId(order == null ? null : order.id())
                .clientOrderId(order == null ? null : order.clientOrderId())
                .reason(reason)
                .mode(mode())
                .build());
        } catch (Exception e) {
            log.error("Failed to record trade execution for {}: {}", ticker, reason, e);
        }
    }

    public String mode() {
        return props.getAlpaca().getBaseUrl().contains("paper") ? "paper" : "live";
    }

    public LocalDateTime getLastRunAt() {
        return lastRunAt;
    }

    public String getLastRunSummary() {
        return lastRunSummary;
    }

    public boolean isRunning() {
        return running.get();
    }

    public record RunResult(boolean ok, String message, int exits, int entries, int skipped) {}
}
