package com.stockpulse.agent.service;

import com.stockpulse.agent.dto.BacktestResult;
import com.stockpulse.agent.dto.StrategyComparison;
import com.stockpulse.agent.model.StockPrice;
import com.stockpulse.agent.repository.StockPriceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComprehensiveBacktestService {
    
    private final StockPriceRepository stockPriceRepository;
    private final AdvancedTradingService advancedTradingService;
    private final BacktestingService backtestingService;
    
    /**
     * Run all strategies and compare performance
     */
    public StrategyComparison runComprehensiveBacktest(String ticker, BigDecimal initialCapital) {
        log.info("Running comprehensive backtest for ticker: {} with capital: {}", ticker, initialCapital);
        
        List<BacktestResult> results = new ArrayList<>();
        
        // 1. Traditional Technical Analysis Strategies
        results.add(backtestingService.runSMABacktest(ticker, 5, 20, initialCapital));
        results.add(backtestingService.runSMABacktest(ticker, 10, 30, initialCapital));
        results.add(backtestingService.runRSIBacktest(ticker, 14, 30, 70, initialCapital));
        results.add(backtestingService.runRSIBacktest(ticker, 14, 20, 80, initialCapital));
        
        // 2. Advanced Price Action Strategies
        results.add(runPriceActionStrategy(ticker, initialCapital));
        results.add(runSupportResistanceStrategy(ticker, initialCapital));
        
        // 3. Volume-Based Strategies
        results.add(runVolumeProfileStrategy(ticker, initialCapital));
        results.add(runOrderFlowStrategy(ticker, initialCapital));
        
        // 4. Volatility-Based Strategies
        results.add(runVolatilityBreakoutStrategy(ticker, initialCapital));
        results.add(runBollingerBandStrategy(ticker, initialCapital));
        
        // 5. Combined Multi-Factor Strategy
        results.add(runMultiFactorStrategy(ticker, initialCapital));
        
        // 6. Mean Reversion Strategies
        results.add(runMeanReversionStrategy(ticker, initialCapital));
        
        // Remove any null results
        results = results.stream()
            .filter(Objects::nonNull)
            .filter(r -> r.getError() == null)
            .collect(Collectors.toList());
        
        // Rank strategies by performance
        results.sort((a, b) -> {
            BigDecimal returnA = a.getTotalReturn() != null ? a.getTotalReturn() : BigDecimal.ZERO;
            BigDecimal returnB = b.getTotalReturn() != null ? b.getTotalReturn() : BigDecimal.ZERO;
            return returnB.compareTo(returnA); // Descending order
        });
        
        // Calculate additional metrics
        for (BacktestResult result : results) {
            enhanceResultWithMetrics(result);
        }
        
        return StrategyComparison.builder()
            .ticker(ticker)
            .testPeriod(getTestPeriod(ticker))
            .initialCapital(initialCapital)
            .strategies(results)
            .bestStrategy(results.isEmpty() ? null : results.get(0))
            .averageReturn(calculateAverageReturn(results))
            .build();
    }
    
    /**
     * Price Action Strategy - Pin Bars and Engulfing Patterns
     */
    private BacktestResult runPriceActionStrategy(String ticker, BigDecimal initialCapital) {
        List<StockPrice> prices = stockPriceRepository.findByTickerOrderByDateAsc(ticker);
        
        if (prices.size() < 20) {
            return createErrorResult(ticker, "Price Action Strategy", "Insufficient data");
        }
        
        List<BacktestResult.Trade> trades = new ArrayList<>();
        BigDecimal capital = initialCapital;
        BigDecimal shares = BigDecimal.ZERO;
        boolean inPosition = false;
        
        for (int i = 3; i < prices.size(); i++) {
            StockPrice current = prices.get(i);
            StockPrice prev = prices.get(i - 1);
            StockPrice prev2 = prices.get(i - 2);
            
            // Pin Bar Bullish Signal
            if (isPinBar(current) && getWickPosition(current).compareTo(BigDecimal.valueOf(0.7)) > 0 && !inPosition) {
                shares = capital.divide(current.getClose(), 2, RoundingMode.DOWN);
                capital = BigDecimal.ZERO;
                inPosition = true;
                
                trades.add(createTrade(current.getDate(), "BUY", current.getClose(), shares, "Bullish Pin Bar"));
            }
            // Bullish Engulfing Pattern
            else if (isBullishEngulfing(prev, current) && !inPosition) {
                shares = capital.divide(current.getClose(), 2, RoundingMode.DOWN);
                capital = BigDecimal.ZERO;
                inPosition = true;
                
                trades.add(createTrade(current.getDate(), "BUY", current.getClose(), shares, "Bullish Engulfing"));
            }
            // Exit on Bearish signals or 5% profit/loss
            else if (inPosition && (
                (isPinBar(current) && getWickPosition(current).compareTo(BigDecimal.valueOf(0.3)) < 0) ||
                isBearishEngulfing(prev, current) ||
                isProfitOrLossTarget(current.getClose(), trades.get(trades.size() - 1).getPrice(), 0.05)
            )) {
                capital = shares.multiply(current.getClose());
                BigDecimal soldShares = shares;
                shares = BigDecimal.ZERO;
                inPosition = false;
                
                trades.add(createTrade(current.getDate(), "SELL", current.getClose(), soldShares, "Exit Signal"));
            }
        }
        
        // Close position at end
        if (inPosition) {
            StockPrice lastPrice = prices.get(prices.size() - 1);
            capital = shares.multiply(lastPrice.getClose());
            trades.add(createTrade(lastPrice.getDate(), "SELL", lastPrice.getClose(), shares, "End of test"));
        }
        
        return buildBacktestResult(ticker, "Price Action Strategy", initialCapital, capital, trades, prices);
    }
    
    /**
     * Support/Resistance Breakout Strategy
     */
    private BacktestResult runSupportResistanceStrategy(String ticker, BigDecimal initialCapital) {
        List<StockPrice> prices = stockPriceRepository.findByTickerOrderByDateAsc(ticker);
        
        if (prices.size() < 50) {
            return createErrorResult(ticker, "S/R Breakout Strategy", "Insufficient data");
        }
        
        List<BacktestResult.Trade> trades = new ArrayList<>();
        BigDecimal capital = initialCapital;
        BigDecimal shares = BigDecimal.ZERO;
        boolean inPosition = false;
        
        // Calculate support and resistance levels using first 20 periods
        List<BigDecimal> resistanceLevels = findResistanceLevels(prices.subList(0, 20));
        List<BigDecimal> supportLevels = findSupportLevels(prices.subList(0, 20));
        
        for (int i = 20; i < prices.size(); i++) {
            StockPrice current = prices.get(i);
            StockPrice prev = prices.get(i - 1);
            
            // Breakout above resistance with volume confirmation
            BigDecimal nearestResistance = findNearestLevel(current.getClose(), resistanceLevels);
            if (nearestResistance != null && 
                current.getClose().compareTo(nearestResistance) > 0 &&
                prev.getClose().compareTo(nearestResistance) <= 0 &&
                current.getVolume() > calculateAverageVolume(prices.subList(i - 10, i)) * 1.5 &&
                !inPosition) {
                
                shares = capital.divide(current.getClose(), 2, RoundingMode.DOWN);
                capital = BigDecimal.ZERO;
                inPosition = true;
                
                trades.add(createTrade(current.getDate(), "BUY", current.getClose(), shares, "Resistance Breakout"));
            }
            // Exit on breakdown below support or 8% stop loss
            else if (inPosition) {
                BigDecimal nearestSupport = findNearestLevel(current.getClose(), supportLevels);
                boolean stopLoss = false;
                if (!trades.isEmpty()) {
                    BacktestResult.Trade lastBuy = findLastBuyTrade(trades);
                    if (lastBuy != null) {
                        BigDecimal loss = lastBuy.getPrice().subtract(current.getClose())
                            .divide(lastBuy.getPrice(), 4, RoundingMode.HALF_UP);
                        stopLoss = loss.compareTo(BigDecimal.valueOf(0.08)) > 0;
                    }
                }
                
                if (stopLoss || 
                    (nearestSupport != null && current.getClose().compareTo(nearestSupport) < 0)) {
                    
                    capital = shares.multiply(current.getClose());
                    BigDecimal soldShares = shares;
                    shares = BigDecimal.ZERO;
                    inPosition = false;
                    
                    trades.add(createTrade(current.getDate(), "SELL", current.getClose(), soldShares, 
                        stopLoss ? "Stop Loss" : "Support Breakdown"));
                }
            }
        }
        
        if (inPosition) {
            StockPrice lastPrice = prices.get(prices.size() - 1);
            capital = shares.multiply(lastPrice.getClose());
            trades.add(createTrade(lastPrice.getDate(), "SELL", lastPrice.getClose(), shares, "End of test"));
        }
        
        return buildBacktestResult(ticker, "S/R Breakout Strategy", initialCapital, capital, trades, prices);
    }
    
    /**
     * Volume Profile Strategy - Trade towards POC
     */
    private BacktestResult runVolumeProfileStrategy(String ticker, BigDecimal initialCapital) {
        List<StockPrice> prices = stockPriceRepository.findByTickerOrderByDateAsc(ticker);
        
        if (prices.size() < 30) {
            return createErrorResult(ticker, "Volume Profile Strategy", "Insufficient data");
        }
        
        List<BacktestResult.Trade> trades = new ArrayList<>();
        BigDecimal capital = initialCapital;
        BigDecimal shares = BigDecimal.ZERO;
        boolean inPosition = false;
        
        for (int i = 20; i < prices.size(); i++) {
            // Calculate volume profile for last 20 periods
            AdvancedTradingService.VolumeProfile vp = 
                advancedTradingService.analyzeVolumeProfile(ticker, 20);
            
            if (vp == null) continue;
            
            StockPrice current = prices.get(i);
            BigDecimal currentPrice = current.getClose();
            
            // Buy when price is below POC and moving towards it
            if (currentPrice.compareTo(vp.pointOfControl) < 0 && !inPosition) {
                BigDecimal distanceToPOC = vp.pointOfControl.subtract(currentPrice)
                    .divide(currentPrice, 4, RoundingMode.HALF_UP);
                
                // Only enter if within 5% of POC
                if (distanceToPOC.compareTo(BigDecimal.valueOf(0.05)) < 0) {
                    shares = capital.divide(currentPrice, 2, RoundingMode.DOWN);
                    capital = BigDecimal.ZERO;
                    inPosition = true;
                    
                    trades.add(createTrade(current.getDate(), "BUY", currentPrice, shares, "Below POC"));
                }
            }
            // Sell when reaching POC or value area high
            else if (inPosition && 
                    (currentPrice.compareTo(vp.pointOfControl) >= 0 || 
                     currentPrice.compareTo(vp.valueAreaHigh) >= 0)) {
                
                capital = shares.multiply(currentPrice);
                BigDecimal soldShares = shares;
                shares = BigDecimal.ZERO;
                inPosition = false;
                
                trades.add(createTrade(current.getDate(), "SELL", currentPrice, soldShares, "Reached POC/VAH"));
            }
        }
        
        if (inPosition) {
            StockPrice lastPrice = prices.get(prices.size() - 1);
            capital = shares.multiply(lastPrice.getClose());
            trades.add(createTrade(lastPrice.getDate(), "SELL", lastPrice.getClose(), shares, "End of test"));
        }
        
        return buildBacktestResult(ticker, "Volume Profile Strategy", initialCapital, capital, trades, prices);
    }
    
    /**
     * Order Flow Strategy - Trade with imbalance
     */
    private BacktestResult runOrderFlowStrategy(String ticker, BigDecimal initialCapital) {
        List<StockPrice> prices = stockPriceRepository.findByTickerOrderByDateAsc(ticker);
        
        if (prices.size() < 15) {
            return createErrorResult(ticker, "Order Flow Strategy", "Insufficient data");
        }
        
        List<BacktestResult.Trade> trades = new ArrayList<>();
        BigDecimal capital = initialCapital;
        BigDecimal shares = BigDecimal.ZERO;
        boolean inPosition = false;
        
        for (int i = 10; i < prices.size(); i++) {
            AdvancedTradingService.OrderFlowAnalysis ofa = 
                advancedTradingService.analyzeOrderFlow(ticker);
                
            if (ofa == null) continue;
            
            StockPrice current = prices.get(i);
            
            // Strong buy imbalance (> 60%)
            if (ofa.imbalance.compareTo(BigDecimal.valueOf(0.6)) > 0 && !inPosition) {
                shares = capital.divide(current.getClose(), 2, RoundingMode.DOWN);
                capital = BigDecimal.ZERO;
                inPosition = true;
                
                trades.add(createTrade(current.getDate(), "BUY", current.getClose(), shares, 
                    "Buy Imbalance: " + ofa.imbalance.multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP) + "%"));
            }
            // Imbalance shifts negative or weak
            else if (inPosition && ofa.imbalance.compareTo(BigDecimal.valueOf(0.2)) < 0) {
                capital = shares.multiply(current.getClose());
                BigDecimal soldShares = shares;
                shares = BigDecimal.ZERO;
                inPosition = false;
                
                trades.add(createTrade(current.getDate(), "SELL", current.getClose(), soldShares, 
                    "Imbalance Weakened"));
            }
        }
        
        if (inPosition) {
            StockPrice lastPrice = prices.get(prices.size() - 1);
            capital = shares.multiply(lastPrice.getClose());
            trades.add(createTrade(lastPrice.getDate(), "SELL", lastPrice.getClose(), shares, "End of test"));
        }
        
        return buildBacktestResult(ticker, "Order Flow Strategy", initialCapital, capital, trades, prices);
    }
    
    /**
     * Volatility Breakout Strategy
     */
    private BacktestResult runVolatilityBreakoutStrategy(String ticker, BigDecimal initialCapital) {
        List<StockPrice> prices = stockPriceRepository.findByTickerOrderByDateAsc(ticker);
        
        if (prices.size() < 25) {
            return createErrorResult(ticker, "Volatility Breakout Strategy", "Insufficient data");
        }
        
        List<BacktestResult.Trade> trades = new ArrayList<>();
        BigDecimal capital = initialCapital;
        BigDecimal shares = BigDecimal.ZERO;
        boolean inPosition = false;
        
        for (int i = 20; i < prices.size(); i++) {
            AdvancedTradingService.VolatilitySignal volSignal = 
                advancedTradingService.analyzeVolatility(ticker);
                
            StockPrice current = prices.get(i);
            
            // Enter on volatility squeeze + breakout
            if (volSignal == AdvancedTradingService.VolatilitySignal.SQUEEZE && !inPosition) {
                // Wait for breakout (price moving > 2% from previous)
                if (i > 0) {
                    StockPrice prev = prices.get(i - 1);
                    BigDecimal priceChange = current.getClose().subtract(prev.getClose())
                        .divide(prev.getClose(), 4, RoundingMode.HALF_UP).abs();
                    
                    if (priceChange.compareTo(BigDecimal.valueOf(0.02)) > 0) {
                        shares = capital.divide(current.getClose(), 2, RoundingMode.DOWN);
                        capital = BigDecimal.ZERO;
                        inPosition = true;
                        
                        trades.add(createTrade(current.getDate(), "BUY", current.getClose(), shares, "Volatility Breakout"));
                    }
                }
            }
            // Exit on expansion phase end
            else if (inPosition && volSignal == AdvancedTradingService.VolatilitySignal.NORMAL) {
                capital = shares.multiply(current.getClose());
                BigDecimal soldShares = shares;
                shares = BigDecimal.ZERO;
                inPosition = false;
                
                trades.add(createTrade(current.getDate(), "SELL", current.getClose(), soldShares, "Volatility Normalized"));
            }
        }
        
        if (inPosition) {
            StockPrice lastPrice = prices.get(prices.size() - 1);
            capital = shares.multiply(lastPrice.getClose());
            trades.add(createTrade(lastPrice.getDate(), "SELL", lastPrice.getClose(), shares, "End of test"));
        }
        
        return buildBacktestResult(ticker, "Volatility Breakout Strategy", initialCapital, capital, trades, prices);
    }
    
    /**
     * Bollinger Band Mean Reversion Strategy
     */
    private BacktestResult runBollingerBandStrategy(String ticker, BigDecimal initialCapital) {
        List<StockPrice> prices = stockPriceRepository.findByTickerOrderByDateAsc(ticker);
        
        if (prices.size() < 25) {
            return createErrorResult(ticker, "Bollinger Band Strategy", "Insufficient data");
        }
        
        List<BacktestResult.Trade> trades = new ArrayList<>();
        BigDecimal capital = initialCapital;
        BigDecimal shares = BigDecimal.ZERO;
        boolean inPosition = false;
        
        for (int i = 20; i < prices.size(); i++) {
            AdvancedTradingService.VolatilitySignal volSignal = 
                advancedTradingService.analyzeVolatility(ticker);
                
            StockPrice current = prices.get(i);
            
            // Buy at lower band (oversold)
            if (volSignal == AdvancedTradingService.VolatilitySignal.OVERSOLD && !inPosition) {
                shares = capital.divide(current.getClose(), 2, RoundingMode.DOWN);
                capital = BigDecimal.ZERO;
                inPosition = true;
                
                trades.add(createTrade(current.getDate(), "BUY", current.getClose(), shares, "Oversold - Lower Band"));
            }
            // Sell at upper band (overbought) or middle band
            else if (inPosition && 
                    (volSignal == AdvancedTradingService.VolatilitySignal.OVERBOUGHT ||
                     volSignal == AdvancedTradingService.VolatilitySignal.NORMAL)) {
                
                capital = shares.multiply(current.getClose());
                BigDecimal soldShares = shares;
                shares = BigDecimal.ZERO;
                inPosition = false;
                
                trades.add(createTrade(current.getDate(), "SELL", current.getClose(), soldShares, 
                    volSignal == AdvancedTradingService.VolatilitySignal.OVERBOUGHT ? "Overbought - Upper Band" : "Mean Reversion"));
            }
        }
        
        if (inPosition) {
            StockPrice lastPrice = prices.get(prices.size() - 1);
            capital = shares.multiply(lastPrice.getClose());
            trades.add(createTrade(lastPrice.getDate(), "SELL", lastPrice.getClose(), shares, "End of test"));
        }
        
        return buildBacktestResult(ticker, "Bollinger Band Strategy", initialCapital, capital, trades, prices);
    }
    
    /**
     * Multi-Factor Strategy - Combines multiple signals
     */
    private BacktestResult runMultiFactorStrategy(String ticker, BigDecimal initialCapital) {
        List<StockPrice> prices = stockPriceRepository.findByTickerOrderByDateAsc(ticker);
        
        if (prices.size() < 30) {
            return createErrorResult(ticker, "Multi-Factor Strategy", "Insufficient data");
        }
        
        List<BacktestResult.Trade> trades = new ArrayList<>();
        BigDecimal capital = initialCapital;
        BigDecimal shares = BigDecimal.ZERO;
        boolean inPosition = false;
        
        for (int i = 25; i < prices.size(); i++) {
            StockPrice current = prices.get(i);
            
            // Get multiple signals
            AdvancedTradingService.PriceActionSignal paSignal = 
                advancedTradingService.analyzePriceAction(ticker);
            AdvancedTradingService.VolatilitySignal volSignal = 
                advancedTradingService.analyzeVolatility(ticker);
            AdvancedTradingService.OrderFlowAnalysis ofa = 
                advancedTradingService.analyzeOrderFlow(ticker);
            
            // Calculate signal strength (0-5)
            int bullishSignals = 0;
            int bearishSignals = 0;
            
            // Price Action Signals
            if (paSignal == AdvancedTradingService.PriceActionSignal.BULLISH_REVERSAL ||
                paSignal == AdvancedTradingService.PriceActionSignal.BULLISH_CONTINUATION) {
                bullishSignals++;
            } else if (paSignal == AdvancedTradingService.PriceActionSignal.BEARISH_REVERSAL ||
                       paSignal == AdvancedTradingService.PriceActionSignal.BEARISH_CONTINUATION) {
                bearishSignals++;
            }
            
            // Volume Signals
            if (current.getVolume() > calculateAverageVolume(prices.subList(Math.max(0, i - 10), i)) * 1.5) {
                bullishSignals++;
            }
            
            // Volatility Signals
            if (volSignal == AdvancedTradingService.VolatilitySignal.OVERSOLD) {
                bullishSignals++;
            } else if (volSignal == AdvancedTradingService.VolatilitySignal.OVERBOUGHT) {
                bearishSignals++;
            }
            
            // Order Flow Signals
            if (ofa != null) {
                if (ofa.imbalance.compareTo(BigDecimal.valueOf(0.3)) > 0) {
                    bullishSignals++;
                } else if (ofa.imbalance.compareTo(BigDecimal.valueOf(-0.3)) < 0) {
                    bearishSignals++;
                }
            }
            
            // Entry: Need at least 3 bullish signals and no bearish signals
            if (bullishSignals >= 3 && bearishSignals == 0 && !inPosition) {
                shares = capital.divide(current.getClose(), 2, RoundingMode.DOWN);
                capital = BigDecimal.ZERO;
                inPosition = true;
                
                trades.add(createTrade(current.getDate(), "BUY", current.getClose(), shares, 
                    "Multi-Factor Buy (" + bullishSignals + " signals)"));
            }
            // Exit: 2+ bearish signals or profit target
            else if (inPosition && 
                    (bearishSignals >= 2 || 
                     (!trades.isEmpty() && isProfitOrLossTarget(current.getClose(), 
                         findLastBuyTrade(trades).getPrice(), 0.06)))) {
                
                capital = shares.multiply(current.getClose());
                BigDecimal soldShares = shares;
                shares = BigDecimal.ZERO;
                inPosition = false;
                
                trades.add(createTrade(current.getDate(), "SELL", current.getClose(), soldShares, 
                    bearishSignals >= 2 ? "Multi-Factor Exit" : "Profit Target"));
            }
        }
        
        if (inPosition) {
            StockPrice lastPrice = prices.get(prices.size() - 1);
            capital = shares.multiply(lastPrice.getClose());
            trades.add(createTrade(lastPrice.getDate(), "SELL", lastPrice.getClose(), shares, "End of test"));
        }
        
        return buildBacktestResult(ticker, "Multi-Factor Strategy", initialCapital, capital, trades, prices);
    }
    
    /**
     * Mean Reversion Strategy
     */
    private BacktestResult runMeanReversionStrategy(String ticker, BigDecimal initialCapital) {
        List<StockPrice> prices = stockPriceRepository.findByTickerOrderByDateAsc(ticker);
        
        if (prices.size() < 25) {
            return createErrorResult(ticker, "Mean Reversion Strategy", "Insufficient data");
        }
        
        List<BacktestResult.Trade> trades = new ArrayList<>();
        BigDecimal capital = initialCapital;
        BigDecimal shares = BigDecimal.ZERO;
        boolean inPosition = false;
        
        for (int i = 20; i < prices.size(); i++) {
            StockPrice current = prices.get(i);
            
            // Calculate 20-period moving average
            BigDecimal sma = calculateSMA(prices.subList(i - 20, i));
            
            // Calculate standard deviation
            BigDecimal stdDev = calculateStdDev(prices.subList(i - 20, i), sma);
            
            BigDecimal upperBound = sma.add(stdDev.multiply(BigDecimal.valueOf(2)));
            BigDecimal lowerBound = sma.subtract(stdDev.multiply(BigDecimal.valueOf(2)));
            
            // Buy when price is 2 std dev below mean
            if (current.getClose().compareTo(lowerBound) < 0 && !inPosition) {
                shares = capital.divide(current.getClose(), 2, RoundingMode.DOWN);
                capital = BigDecimal.ZERO;
                inPosition = true;
                
                trades.add(createTrade(current.getDate(), "BUY", current.getClose(), shares, "Below -2 Std Dev"));
            }
            // Sell when price returns to mean or goes above +1 std dev
            else if (inPosition && 
                    (current.getClose().compareTo(sma) >= 0 || 
                     current.getClose().compareTo(sma.add(stdDev)) > 0)) {
                
                capital = shares.multiply(current.getClose());
                BigDecimal soldShares = shares;
                shares = BigDecimal.ZERO;
                inPosition = false;
                
                trades.add(createTrade(current.getDate(), "SELL", current.getClose(), soldShares, "Mean Reversion"));
            }
        }
        
        if (inPosition) {
            StockPrice lastPrice = prices.get(prices.size() - 1);
            capital = shares.multiply(lastPrice.getClose());
            trades.add(createTrade(lastPrice.getDate(), "SELL", lastPrice.getClose(), shares, "End of test"));
        }
        
        return buildBacktestResult(ticker, "Mean Reversion Strategy", initialCapital, capital, trades, prices);
    }
    
    // Helper methods
    
    private BacktestResult createErrorResult(String ticker, String strategy, String error) {
        return BacktestResult.builder()
            .ticker(ticker)
            .strategy(strategy)
            .error(error)
            .build();
    }
    
    private BacktestResult.Trade createTrade(LocalDateTime date, String type, BigDecimal price, 
                                           BigDecimal shares, String reason) {
        return BacktestResult.Trade.builder()
            .date(date)
            .type(type)
            .price(price)
            .shares(shares)
            .value(price.multiply(shares))
            .reason(reason)
            .build();
    }
    
    private BacktestResult buildBacktestResult(String ticker, String strategy, BigDecimal initialCapital,
                                             BigDecimal finalCapital, List<BacktestResult.Trade> trades,
                                             List<StockPrice> prices) {
        
        BigDecimal finalValue = finalCapital;
        BigDecimal totalReturn = finalValue.subtract(initialCapital)
            .divide(initialCapital, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
        
        // Calculate buy and hold return
        BigDecimal buyHoldReturn = BigDecimal.ZERO;
        if (prices.size() > 1) {
            BigDecimal startPrice = prices.get(0).getClose();
            BigDecimal endPrice = prices.get(prices.size() - 1).getClose();
            buyHoldReturn = endPrice.subtract(startPrice)
                .divide(startPrice, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
        }
        
        return BacktestResult.builder()
            .ticker(ticker)
            .strategy(strategy)
            .startDate(prices.isEmpty() ? null : prices.get(0).getDate())
            .endDate(prices.isEmpty() ? null : prices.get(prices.size() - 1).getDate())
            .initialCapital(initialCapital)
            .finalValue(finalValue)
            .totalReturn(totalReturn)
            .buyHoldReturn(buyHoldReturn)
            .numberOfTrades(trades.size())
            .trades(trades)
            .build();
    }
    
    private void enhanceResultWithMetrics(BacktestResult result) {
        if (result.getTrades() == null || result.getTrades().isEmpty()) {
            return;
        }
        
        // Calculate win rate
        int winningTrades = 0;
        int totalRoundTrips = 0;
        BigDecimal totalProfit = BigDecimal.ZERO;
        BigDecimal totalLoss = BigDecimal.ZERO;
        
        BacktestResult.Trade lastBuy = null;
        for (BacktestResult.Trade trade : result.getTrades()) {
            if ("BUY".equals(trade.getType())) {
                lastBuy = trade;
            } else if ("SELL".equals(trade.getType()) && lastBuy != null) {
                BigDecimal profit = trade.getPrice().subtract(lastBuy.getPrice())
                    .multiply(trade.getShares());
                
                totalRoundTrips++;
                if (profit.compareTo(BigDecimal.ZERO) > 0) {
                    winningTrades++;
                    totalProfit = totalProfit.add(profit);
                } else {
                    totalLoss = totalLoss.add(profit.abs());
                }
                lastBuy = null;
            }
        }
        
        if (totalRoundTrips > 0) {
            BigDecimal winRate = BigDecimal.valueOf(winningTrades * 100.0 / totalRoundTrips);
            result.setWinRate(winRate);
            
            if (totalLoss.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal profitFactor = totalProfit.divide(totalLoss, 2, RoundingMode.HALF_UP);
                result.setProfitFactor(profitFactor);
            }
        }
    }
    
    private String getTestPeriod(String ticker) {
        List<StockPrice> prices = stockPriceRepository.findByTickerOrderByDateAsc(ticker);
        if (prices.isEmpty()) return "No data";
        
        return prices.get(0).getDate().toLocalDate() + " to " + 
               prices.get(prices.size() - 1).getDate().toLocalDate();
    }
    
    private BigDecimal calculateAverageReturn(List<BacktestResult> results) {
        if (results.isEmpty()) return BigDecimal.ZERO;
        
        BigDecimal sum = results.stream()
            .map(r -> r.getTotalReturn() != null ? r.getTotalReturn() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
            
        return sum.divide(BigDecimal.valueOf(results.size()), 2, RoundingMode.HALF_UP);
    }
    
    // Advanced Trading Service Helper Methods
    
    private boolean isPinBar(StockPrice candle) {
        BigDecimal body = candle.getClose().subtract(candle.getOpen()).abs();
        BigDecimal totalRange = candle.getHigh().subtract(candle.getLow());
        
        if (totalRange.compareTo(BigDecimal.ZERO) == 0) return false;
        
        BigDecimal bodyRatio = body.divide(totalRange, 4, RoundingMode.HALF_UP);
        return bodyRatio.compareTo(BigDecimal.valueOf(0.3)) < 0;
    }
    
    private BigDecimal getWickPosition(StockPrice candle) {
        BigDecimal upperWick = candle.getHigh().subtract(candle.getClose().max(candle.getOpen()));
        BigDecimal lowerWick = candle.getClose().min(candle.getOpen()).subtract(candle.getLow());
        BigDecimal totalWick = upperWick.add(lowerWick);
        
        if (totalWick.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.valueOf(0.5);
        
        return lowerWick.divide(totalWick, 4, RoundingMode.HALF_UP);
    }
    
    private boolean isBullishEngulfing(StockPrice prev, StockPrice current) {
        boolean prevBearish = prev.getClose().compareTo(prev.getOpen()) < 0;
        boolean currentBullish = current.getClose().compareTo(current.getOpen()) > 0;
        boolean engulfs = current.getOpen().compareTo(prev.getClose()) < 0 && 
                         current.getClose().compareTo(prev.getOpen()) > 0;
        
        return prevBearish && currentBullish && engulfs;
    }
    
    private boolean isBearishEngulfing(StockPrice prev, StockPrice current) {
        boolean prevBullish = prev.getClose().compareTo(prev.getOpen()) > 0;
        boolean currentBearish = current.getClose().compareTo(current.getOpen()) < 0;
        boolean engulfs = current.getOpen().compareTo(prev.getClose()) > 0 && 
                         current.getClose().compareTo(prev.getOpen()) < 0;
        
        return prevBullish && currentBearish && engulfs;
    }
    
    private List<BigDecimal> findResistanceLevels(List<StockPrice> prices) {
        List<BigDecimal> levels = new ArrayList<>();
        for (int i = 2; i < prices.size() - 2; i++) {
            StockPrice current = prices.get(i);
            if (current.getHigh().compareTo(prices.get(i-1).getHigh()) > 0 &&
                current.getHigh().compareTo(prices.get(i-2).getHigh()) > 0 &&
                current.getHigh().compareTo(prices.get(i+1).getHigh()) > 0 &&
                current.getHigh().compareTo(prices.get(i+2).getHigh()) > 0) {
                levels.add(current.getHigh());
            }
        }
        return levels;
    }
    
    private List<BigDecimal> findSupportLevels(List<StockPrice> prices) {
        List<BigDecimal> levels = new ArrayList<>();
        for (int i = 2; i < prices.size() - 2; i++) {
            StockPrice current = prices.get(i);
            if (current.getLow().compareTo(prices.get(i-1).getLow()) < 0 &&
                current.getLow().compareTo(prices.get(i-2).getLow()) < 0 &&
                current.getLow().compareTo(prices.get(i+1).getLow()) < 0 &&
                current.getLow().compareTo(prices.get(i+2).getLow()) < 0) {
                levels.add(current.getLow());
            }
        }
        return levels;
    }
    
    private BigDecimal findNearestLevel(BigDecimal price, List<BigDecimal> levels) {
        return levels.stream()
            .min((a, b) -> a.subtract(price).abs().compareTo(b.subtract(price).abs()))
            .orElse(null);
    }
    
    private long calculateAverageVolume(List<StockPrice> prices) {
        return prices.stream()
            .mapToLong(StockPrice::getVolume)
            .sum() / prices.size();
    }
    
    private BacktestResult.Trade findLastBuyTrade(List<BacktestResult.Trade> trades) {
        for (int i = trades.size() - 1; i >= 0; i--) {
            if ("BUY".equals(trades.get(i).getType())) {
                return trades.get(i);
            }
        }
        return null;
    }
    
    private boolean isProfitOrLossTarget(BigDecimal currentPrice, BigDecimal entryPrice, double targetPercent) {
        BigDecimal change = currentPrice.subtract(entryPrice).divide(entryPrice, 4, RoundingMode.HALF_UP).abs();
        return change.compareTo(BigDecimal.valueOf(targetPercent)) >= 0;
    }
    
    private BigDecimal calculateSMA(List<StockPrice> prices) {
        BigDecimal sum = prices.stream()
            .map(StockPrice::getClose)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(prices.size()), 4, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateStdDev(List<StockPrice> prices, BigDecimal mean) {
        BigDecimal variance = prices.stream()
            .map(p -> p.getClose().subtract(mean).pow(2))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(prices.size()), 6, RoundingMode.HALF_UP);
            
        return BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
    }
}