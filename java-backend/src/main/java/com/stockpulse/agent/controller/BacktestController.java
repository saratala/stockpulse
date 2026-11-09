package com.stockpulse.agent.controller;

import com.stockpulse.agent.dto.BacktestResult;
import com.stockpulse.agent.dto.StrategyComparison;
import com.stockpulse.agent.service.AdvancedDayTradingService;
import com.stockpulse.agent.service.BacktestingService;
import com.stockpulse.agent.service.ComprehensiveBacktestService;
import com.stockpulse.agent.service.DayTradingBacktestService;
import com.stockpulse.agent.service.MLBacktestService;
import com.stockpulse.agent.service.SwingBacktestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/backtest")
@RequiredArgsConstructor
@Slf4j
public class BacktestController {

    private final BacktestingService backtestingService;
    private final ComprehensiveBacktestService comprehensiveBacktestService;
    private final MLBacktestService mlBacktestService;
    private final DayTradingBacktestService dayTradingBacktestService;
    private final AdvancedDayTradingService advancedDayTradingService;
    private final SwingBacktestService swingBacktestService;
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Backtest controller is working!");
    }
    
    @PostMapping("/sma")
    public ResponseEntity<BacktestResult> runSMABacktest(
            @RequestParam String ticker,
            @RequestParam(defaultValue = "5") int shortPeriod,
            @RequestParam(defaultValue = "20") int longPeriod,
            @RequestParam(defaultValue = "10000") BigDecimal initialCapital) {
        
        log.info("Running SMA backtest for ticker: {}, periods: {}/{}, capital: {}", 
                ticker, shortPeriod, longPeriod, initialCapital);
        
        try {
            BacktestResult result = backtestingService.runSMABacktest(
                ticker, shortPeriod, longPeriod, initialCapital);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error running SMA backtest", e);
            return ResponseEntity.ok(BacktestResult.builder()
                .ticker(ticker)
                .strategy("SMA Crossover")
                .error(e.getMessage())
                .build());
        }
    }
    
    @PostMapping("/rsi")
    public ResponseEntity<BacktestResult> runRSIBacktest(
            @RequestParam String ticker,
            @RequestParam(defaultValue = "14") int period,
            @RequestParam(defaultValue = "30") int oversoldLevel,
            @RequestParam(defaultValue = "70") int overboughtLevel,
            @RequestParam(defaultValue = "10000") BigDecimal initialCapital) {
        
        log.info("Running RSI backtest for ticker: {}, period: {}, levels: {}/{}, capital: {}", 
                ticker, period, oversoldLevel, overboughtLevel, initialCapital);
        
        try {
            BacktestResult result = backtestingService.runRSIBacktest(
                ticker, period, oversoldLevel, overboughtLevel, initialCapital);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error running RSI backtest", e);
            return ResponseEntity.ok(BacktestResult.builder()
                .ticker(ticker)
                .strategy("RSI Strategy")
                .error(e.getMessage())
                .build());
        }
    }
    
    @PostMapping("/multi")
    public ResponseEntity<BacktestResult[]> runMultiStrategyBacktest(
            @RequestParam String ticker,
            @RequestParam(defaultValue = "10000") BigDecimal initialCapital) {
        
        log.info("Running multi-strategy backtest for ticker: {}", ticker);
        
        try {
            BacktestResult[] results = new BacktestResult[4];
            
            // Test different SMA combinations
            results[0] = backtestingService.runSMABacktest(ticker, 5, 10, initialCapital);
            results[1] = backtestingService.runSMABacktest(ticker, 10, 20, initialCapital);
            
            // Test different RSI settings
            results[2] = backtestingService.runRSIBacktest(ticker, 14, 30, 70, initialCapital);
            results[3] = backtestingService.runRSIBacktest(ticker, 14, 20, 80, initialCapital);
            
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error running multi-strategy backtest", e);
            return ResponseEntity.status(500).body(new BacktestResult[0]);
        }
    }
    
    @PostMapping("/comprehensive")
    public ResponseEntity<StrategyComparison> runComprehensiveBacktest(
            @RequestParam String ticker,
            @RequestParam(defaultValue = "10000") BigDecimal initialCapital) {
        
        log.info("Running comprehensive strategy backtest for ticker: {} with capital: {}", ticker, initialCapital);
        
        try {
            StrategyComparison comparison = comprehensiveBacktestService.runComprehensiveBacktest(ticker, initialCapital);
            return ResponseEntity.ok(comparison);
        } catch (Exception e) {
            log.error("Error running comprehensive backtest", e);
            return ResponseEntity.status(500).body(StrategyComparison.builder()
                .ticker(ticker)
                .strategies(new java.util.ArrayList<>())
                .build());
        }
    }
    
    @PostMapping("/all-tickers")
    public ResponseEntity<java.util.Map<String, StrategyComparison>> runAllTickersBacktest(
            @RequestParam(defaultValue = "10000") BigDecimal initialCapital) {
        
        log.info("Running comprehensive backtest for all tickers with capital: {}", initialCapital);
        
        try {
            // Get all available tickers from database
            java.util.List<String> tickers = java.util.Arrays.asList(
                "AAPL", "MSFT", "GOOGL", "AMZN", "TSLA", "META", "NVDA", "NFLX"
            );
            
            java.util.Map<String, StrategyComparison> results = new java.util.HashMap<>();
            
            for (String ticker : tickers) {
                try {
                    StrategyComparison comparison = comprehensiveBacktestService.runComprehensiveBacktest(ticker, initialCapital);
                    results.put(ticker, comparison);
                    log.info("Completed backtest for ticker: {}", ticker);
                } catch (Exception e) {
                    log.error("Error backtesting ticker: {}", ticker, e);
                }
            }
            
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Error running all tickers backtest", e);
            return ResponseEntity.status(500).body(new java.util.HashMap<>());
        }
    }

    /**
     * Run ML Prediction Model backtest.
     * Uses the same prediction algorithm as MLPredictionService but runs it historically.
     */
    @PostMapping("/ml-prediction")
    public ResponseEntity<Map<String, Object>> runMLPredictionBacktest(
            @RequestParam String ticker,
            @RequestParam(defaultValue = "10000") BigDecimal initialCapital,
            @RequestParam(defaultValue = "1d") String timeframe) {

        log.info("Running ML Prediction backtest for ticker: {}, capital: {}, timeframe: {}",
                ticker, initialCapital, timeframe);

        try {
            Map<String, Object> result = mlBacktestService.runMLBacktest(
                ticker.toUpperCase(), initialCapital, timeframe);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error running ML Prediction backtest", e);
            Map<String, Object> error = new java.util.HashMap<>();
            error.put("ticker", ticker);
            error.put("strategy", "ML Prediction Model");
            error.put("error", e.getMessage());
            return ResponseEntity.ok(error);
        }
    }

    /**
     * Run ML Prediction backtest for multiple tickers.
     */
    @PostMapping("/ml-prediction/all")
    public ResponseEntity<Map<String, Object>> runMLPredictionBacktestAll(
            @RequestParam(defaultValue = "10000") BigDecimal initialCapital,
            @RequestParam(defaultValue = "1d") String timeframe) {

        log.info("Running ML Prediction backtest for all tickers with capital: {}", initialCapital);

        java.util.List<String> tickers = java.util.Arrays.asList(
            "AAPL", "MSFT", "GOOGL", "AMZN", "TSLA", "META", "NVDA", "NFLX"
        );

        Map<String, Object> results = new java.util.LinkedHashMap<>();
        java.util.List<Map<String, Object>> summary = new java.util.ArrayList<>();

        for (String ticker : tickers) {
            try {
                Map<String, Object> result = mlBacktestService.runMLBacktest(ticker, initialCapital, timeframe);
                results.put(ticker, result);

                // Extract key metrics for summary
                Map<String, Object> tickerSummary = new java.util.LinkedHashMap<>();
                tickerSummary.put("ticker", ticker);
                if (result.containsKey("performance")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> perf = (Map<String, Object>) result.get("performance");
                    tickerSummary.put("totalReturn", perf.get("totalReturn"));
                    tickerSummary.put("buyHoldReturn", perf.get("buyHoldReturn"));
                }
                if (result.containsKey("predictionAccuracy")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> acc = (Map<String, Object>) result.get("predictionAccuracy");
                    tickerSummary.put("directionAccuracy", acc.get("directionAccuracy"));
                }
                summary.add(tickerSummary);

                log.info("Completed ML backtest for {}", ticker);
            } catch (Exception e) {
                log.error("Error backtesting {}: {}", ticker, e.getMessage());
            }
        }

        results.put("_summary", summary);
        return ResponseEntity.ok(results);
    }

    // ============ Day Trading Strategies ============

    /**
     * Run day trading backtest with a specific strategy.
     * Available strategies: scalping, momentum, mean_reversion, breakout, combined
     */
    @PostMapping("/day-trading")
    public ResponseEntity<Map<String, Object>> runDayTradingBacktest(
            @RequestParam String ticker,
            @RequestParam(defaultValue = "10000") BigDecimal initialCapital,
            @RequestParam(defaultValue = "combined") String strategy,
            @RequestParam(required = false) Double takeProfit,
            @RequestParam(required = false) Double stopLoss,
            @RequestParam(required = false) Double momentumThreshold,
            @RequestParam(required = false) Double rsiOversold,
            @RequestParam(required = false) Double rsiOverbought) {

        log.info("Running day trading backtest for ticker: {}, strategy: {}", ticker, strategy);

        try {
            // Build parameters map
            Map<String, Double> params = new java.util.HashMap<>();
            if (takeProfit != null) params.put("takeProfit", takeProfit);
            if (stopLoss != null) params.put("stopLoss", stopLoss);
            if (momentumThreshold != null) params.put("momentumThreshold", momentumThreshold);
            if (rsiOversold != null) params.put("rsiOversold", rsiOversold);
            if (rsiOverbought != null) params.put("rsiOverbought", rsiOverbought);

            Map<String, Object> result = dayTradingBacktestService.runDayTradingBacktest(
                ticker.toUpperCase(), initialCapital, strategy, params);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error running day trading backtest", e);
            Map<String, Object> error = new java.util.HashMap<>();
            error.put("ticker", ticker);
            error.put("strategy", strategy);
            error.put("error", e.getMessage());
            return ResponseEntity.ok(error);
        }
    }

    /**
     * Compare all day trading strategies for a ticker.
     */
    @PostMapping("/day-trading/compare")
    public ResponseEntity<Map<String, Object>> compareDayTradingStrategies(
            @RequestParam String ticker,
            @RequestParam(defaultValue = "10000") BigDecimal initialCapital) {

        log.info("Comparing all day trading strategies for ticker: {}", ticker);

        Map<String, Object> results = new java.util.LinkedHashMap<>();
        java.util.List<String> strategies = java.util.Arrays.asList(
            "scalping", "momentum", "mean_reversion", "breakout", "combined"
        );

        java.util.List<Map<String, Object>> summary = new java.util.ArrayList<>();

        for (String strategy : strategies) {
            try {
                Map<String, Object> result = dayTradingBacktestService.runDayTradingBacktest(
                    ticker.toUpperCase(), initialCapital, strategy, new java.util.HashMap<>());
                results.put(strategy, result);

                // Extract summary
                Map<String, Object> stratSummary = new java.util.LinkedHashMap<>();
                stratSummary.put("strategy", strategy);
                if (result.containsKey("performance")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> perf = (Map<String, Object>) result.get("performance");
                    stratSummary.put("totalReturn", perf.get("totalReturn"));
                    stratSummary.put("winRate", perf.get("winRate"));
                    stratSummary.put("totalTrades", perf.get("totalTrades"));
                    stratSummary.put("profitFactor", perf.get("profitFactor"));
                    stratSummary.put("maxDrawdown", perf.get("maxDrawdown"));
                }
                if (result.containsKey("recommendation")) {
                    stratSummary.put("recommendation", result.get("recommendation"));
                }
                summary.add(stratSummary);

            } catch (Exception e) {
                log.error("Error running {} strategy: {}", strategy, e.getMessage());
            }
        }

        // Sort by return
        summary.sort((a, b) -> {
            String retA = (String) a.getOrDefault("totalReturn", "0%");
            String retB = (String) b.getOrDefault("totalReturn", "0%");
            double valA = Double.parseDouble(retA.replace("%", ""));
            double valB = Double.parseDouble(retB.replace("%", ""));
            return Double.compare(valB, valA);
        });

        results.put("_summary", summary);
        results.put("_bestStrategy", summary.isEmpty() ? null : summary.get(0).get("strategy"));

        return ResponseEntity.ok(results);
    }

    /**
     * Get available day trading strategies and their descriptions.
     */
    @GetMapping("/day-trading/strategies")
    public ResponseEntity<Map<String, Object>> getDayTradingStrategies() {
        Map<String, Object> strategies = new java.util.LinkedHashMap<>();

        strategies.put("scalping", Map.of(
            "name", "Scalping",
            "description", "EMA(9)/EMA(21) cross on 1-min bars in the direction of VWAP; 0.3% target, 0.2% stop, 30-min time stop",
            "riskLevel", "HIGH",
            "tradesPerDay", "3-5",
            "bestFor", "High volatility, range-bound markets",
            "parameters", java.util.Arrays.asList("takeProfit", "stopLoss", "maxHoldMinutes")
        ));

        strategies.put("momentum", Map.of(
            "name", "Momentum",
            "description", "15-min return beyond momentumThreshold %, confirmed by price vs VWAP",
            "riskLevel", "MEDIUM-HIGH",
            "tradesPerDay", "1-2",
            "bestFor", "Trending markets, strong directional moves",
            "parameters", java.util.Arrays.asList("momentumThreshold", "momentumLookback", "takeProfit", "stopLoss")
        ));

        strategies.put("mean_reversion", Map.of(
            "name", "Mean Reversion",
            "description", "RSI(14) extreme while price is stretched away from VWAP",
            "riskLevel", "MEDIUM",
            "tradesPerDay", "1-3",
            "bestFor", "Range-bound markets, extreme RSI readings",
            "parameters", java.util.Arrays.asList("rsiOversold", "rsiOverbought", "vwapStretch")
        ));

        strategies.put("breakout", Map.of(
            "name", "Breakout",
            "description", "Close beyond the 9:30-9:45 opening range on above-average volume, once per side per day",
            "riskLevel", "MEDIUM-HIGH",
            "tradesPerDay", "0-2",
            "bestFor", "High volume days, earnings/news catalysts",
            "parameters", java.util.Arrays.asList("volumeMultiplier", "takeProfit", "stopLoss")
        ));

        strategies.put("combined", Map.of(
            "name", "Combined/Adaptive",
            "description", "Momentum signal confirmed by an opening-range breakout or EMA cross",
            "riskLevel", "MEDIUM",
            "tradesPerDay", "2-4",
            "bestFor", "Filtering out weak momentum signals",
            "parameters", java.util.Arrays.asList("momentumThreshold", "takeProfit", "stopLoss")
        ));

        return ResponseEntity.ok(strategies);
    }

    // ============ Advanced Day Trading with Sophisticated Rules ============

    /**
     * Run advanced day trading backtest with sophisticated entry/exit rules.
     * Features: multiple confirmations, ATR-based stops, trailing stops, daily loss limits.
     */
    @PostMapping("/day-trading/advanced")
    public ResponseEntity<Map<String, Object>> runAdvancedDayTradingBacktest(
            @RequestParam String ticker,
            @RequestParam(defaultValue = "10000") BigDecimal initialCapital,
            @RequestParam(defaultValue = "3") int minConfirmations,
            @RequestParam(defaultValue = "1.0") double riskPerTrade,
            @RequestParam(defaultValue = "25.0") double maxPositionSize,
            @RequestParam(defaultValue = "1.5") double stopLossAtr,
            @RequestParam(defaultValue = "2.5") double takeProfitAtr,
            @RequestParam(defaultValue = "1.0") double trailingStopAtr,
            @RequestParam(defaultValue = "3.0") double maxDailyLoss,
            @RequestParam(defaultValue = "true") boolean enableScalping) {

        log.info("Running advanced day trading backtest for ticker: {} with {} confirmations",
                ticker, minConfirmations);

        try {
            Map<String, Object> config = new java.util.HashMap<>();
            config.put("minConfirmations", minConfirmations);
            config.put("riskPerTrade", riskPerTrade);
            config.put("maxPositionSize", maxPositionSize);
            config.put("stopLossAtr", stopLossAtr);
            config.put("takeProfitAtr", takeProfitAtr);
            config.put("trailingStopAtr", trailingStopAtr);
            config.put("maxDailyLoss", maxDailyLoss);
            config.put("enableScalping", enableScalping);

            Map<String, Object> result = advancedDayTradingService.runAdvancedBacktest(
                ticker.toUpperCase(), initialCapital, config);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error running advanced day trading backtest", e);
            Map<String, Object> error = new java.util.HashMap<>();
            error.put("ticker", ticker);
            error.put("strategy", "Advanced Day Trading");
            error.put("error", e.getMessage());
            return ResponseEntity.ok(error);
        }
    }

    /**
     * Compare advanced vs basic day trading strategies.
     */
    @PostMapping("/day-trading/advanced/compare")
    public ResponseEntity<Map<String, Object>> compareAdvancedStrategies(
            @RequestParam String ticker,
            @RequestParam(defaultValue = "10000") BigDecimal initialCapital) {

        log.info("Comparing advanced day trading configurations for ticker: {}", ticker);

        Map<String, Object> results = new java.util.LinkedHashMap<>();

        // Configuration presets
        java.util.List<Map<String, Object>> configs = java.util.Arrays.asList(
            createConfig("Conservative", 4, 0.5, 15.0, 2.0, 3.0, 1.5, 2.0, false),
            createConfig("Balanced", 3, 1.0, 25.0, 1.5, 2.5, 1.0, 3.0, true),
            createConfig("Aggressive", 2, 2.0, 40.0, 1.0, 2.0, 0.8, 5.0, true),
            createConfig("Scalping Focus", 2, 1.5, 30.0, 1.0, 1.5, 0.5, 4.0, true)
        );

        java.util.List<Map<String, Object>> summary = new java.util.ArrayList<>();

        for (Map<String, Object> config : configs) {
            String presetName = (String) config.get("presetName");
            try {
                Map<String, Object> result = advancedDayTradingService.runAdvancedBacktest(
                    ticker.toUpperCase(), initialCapital, config);
                results.put(presetName, result);

                // Extract summary
                Map<String, Object> configSummary = new java.util.LinkedHashMap<>();
                configSummary.put("preset", presetName);
                if (result.containsKey("performance")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> perf = (Map<String, Object>) result.get("performance");
                    configSummary.put("totalReturn", perf.get("totalReturn"));
                    configSummary.put("winRate", perf.get("winRate"));
                    configSummary.put("totalTrades", perf.get("totalTrades"));
                    configSummary.put("profitFactor", perf.get("profitFactor"));
                    configSummary.put("maxDrawdown", perf.get("maxDrawdown"));
                    configSummary.put("sharpeRatio", perf.get("sharpeRatio"));
                }
                if (result.containsKey("recommendation")) {
                    configSummary.put("recommendation", result.get("recommendation"));
                }
                summary.add(configSummary);

                log.info("Completed {} backtest for {}", presetName, ticker);
            } catch (Exception e) {
                log.error("Error running {} backtest: {}", presetName, e.getMessage());
            }
        }

        // Sort by Sharpe ratio (risk-adjusted return)
        summary.sort((a, b) -> {
            Object sharpeA = a.get("sharpeRatio");
            Object sharpeB = b.get("sharpeRatio");
            double valA = sharpeA instanceof Number ? ((Number) sharpeA).doubleValue() : 0;
            double valB = sharpeB instanceof Number ? ((Number) sharpeB).doubleValue() : 0;
            return Double.compare(valB, valA);
        });

        results.put("_summary", summary);
        results.put("_bestPreset", summary.isEmpty() ? null : summary.get(0).get("preset"));

        return ResponseEntity.ok(results);
    }

    private Map<String, Object> createConfig(String name, int minConf, double risk, double maxPos,
                                              double stopAtr, double tpAtr, double trailAtr,
                                              double maxLoss, boolean scalping) {
        Map<String, Object> config = new java.util.HashMap<>();
        config.put("presetName", name);
        config.put("minConfirmations", minConf);
        config.put("riskPerTrade", risk);
        config.put("maxPositionSize", maxPos);
        config.put("stopLossAtr", stopAtr);
        config.put("takeProfitAtr", tpAtr);
        config.put("trailingStopAtr", trailAtr);
        config.put("maxDailyLoss", maxLoss);
        config.put("enableScalping", scalping);
        return config;
    }

    // ============ Swing Trading (multi-day holds on daily bars) ============

    /**
     * Run a swing backtest on daily bars.
     * Available strategies: trend, donchian, rsi2_pullback, buy_hold
     */
    @PostMapping("/swing")
    public ResponseEntity<Map<String, Object>> runSwingBacktest(
            @RequestParam String ticker,
            @RequestParam(defaultValue = "10000") BigDecimal initialCapital,
            @RequestParam(defaultValue = "trend") String strategy,
            @RequestParam(required = false) Double stopLossPct,
            @RequestParam(required = false) Double stopAtrMultiple,
            @RequestParam(required = false) Double maxHoldDays,
            @RequestParam(required = false) Double positionSize,
            @RequestParam(required = false) Double warmupDays,
            @RequestParam(required = false) Double rsiEntry) {

        log.info("Running swing backtest for ticker: {}, strategy: {}", ticker, strategy);

        try {
            Map<String, Double> params = new java.util.HashMap<>();
            if (stopLossPct != null) params.put("stopLossPct", stopLossPct);
            if (stopAtrMultiple != null) params.put("stopAtrMultiple", stopAtrMultiple);
            if (maxHoldDays != null) params.put("maxHoldDays", maxHoldDays);
            if (positionSize != null) params.put("positionSize", positionSize);
            if (warmupDays != null) params.put("warmupDays", warmupDays);
            if (rsiEntry != null) params.put("rsiEntry", rsiEntry);

            return ResponseEntity.ok(swingBacktestService.runSwingBacktest(
                ticker.toUpperCase(), initialCapital, strategy, params));
        } catch (Exception e) {
            log.error("Error running swing backtest", e);
            Map<String, Object> error = new java.util.HashMap<>();
            error.put("ticker", ticker);
            error.put("strategy", strategy);
            error.put("error", e.getMessage());
            return ResponseEntity.ok(error);
        }
    }

    /**
     * Compare all swing strategies (including the buy_hold benchmark) over the same test window.
     */
    @PostMapping("/swing/compare")
    public ResponseEntity<Map<String, Object>> compareSwingStrategies(
            @RequestParam String ticker,
            @RequestParam(defaultValue = "10000") BigDecimal initialCapital) {

        log.info("Comparing swing strategies for ticker: {}", ticker);

        Map<String, Object> results = new java.util.LinkedHashMap<>();
        java.util.List<Map<String, Object>> summary = new java.util.ArrayList<>();

        for (String strategy : SwingBacktestService.STRATEGIES) {
            try {
                Map<String, Object> result = swingBacktestService.runSwingBacktest(
                    ticker.toUpperCase(), initialCapital, strategy, new java.util.HashMap<>());
                results.put(strategy, result);

                Map<String, Object> stratSummary = new java.util.LinkedHashMap<>();
                stratSummary.put("strategy", strategy);
                if (result.containsKey("performance")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> perf = (Map<String, Object>) result.get("performance");
                    for (String key : java.util.List.of("totalReturn", "buyHoldReturn", "excessReturn",
                            "maxDrawdown", "sharpeRatio", "totalTrades", "winRate", "profitFactor",
                            "exposurePct", "avgHoldingDays")) {
                        stratSummary.put(key, perf.get(key));
                    }
                }
                if (result.containsKey("recommendation")) {
                    stratSummary.put("recommendation", result.get("recommendation"));
                }
                if (result.containsKey("error")) {
                    stratSummary.put("error", result.get("error"));
                }
                summary.add(stratSummary);
            } catch (Exception e) {
                log.error("Error running swing {} strategy: {}", strategy, e.getMessage());
            }
        }

        summary.sort((a, b) -> Double.compare(percent(b.get("totalReturn")), percent(a.get("totalReturn"))));

        results.put("_summary", summary);
        results.put("_bestStrategy", summary.isEmpty() || !summary.get(0).containsKey("totalReturn")
            ? null : summary.get(0).get("strategy"));

        return ResponseEntity.ok(results);
    }

    private static double percent(Object value) {
        if (value instanceof String s) {
            try {
                return Double.parseDouble(s.replace("%", ""));
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return Double.NEGATIVE_INFINITY;
    }
}
