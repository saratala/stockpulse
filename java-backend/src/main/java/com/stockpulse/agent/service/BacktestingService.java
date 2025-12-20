package com.stockpulse.agent.service;

import com.stockpulse.agent.dto.BacktestResult;
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
public class BacktestingService {
    
    private final StockPriceRepository stockPriceRepository;
    
    /**
     * Run backtest using Simple Moving Average Crossover Strategy
     */
    public BacktestResult runSMABacktest(String ticker, int shortPeriod, int longPeriod, BigDecimal initialCapital) {
        log.info("Running SMA backtest for {} with periods {}/{}", ticker, shortPeriod, longPeriod);
        
        // Fetch historical data
        List<StockPrice> prices = stockPriceRepository.findByTickerOrderByDateAsc(ticker);
        
        if (prices.size() < longPeriod) {
            log.warn("Insufficient data for backtesting. Need at least {} data points", longPeriod);
            return BacktestResult.builder()
                .ticker(ticker)
                .strategy("SMA Crossover")
                .error("Insufficient data")
                .build();
        }
        
        // Calculate SMAs
        List<BigDecimal> shortSMA = calculateSMA(prices, shortPeriod);
        List<BigDecimal> longSMA = calculateSMA(prices, longPeriod);
        
        // Run backtest
        List<BacktestResult.Trade> trades = new ArrayList<>();
        BigDecimal capital = initialCapital;
        BigDecimal shares = BigDecimal.ZERO;
        boolean inPosition = false;
        
        for (int i = longPeriod; i < prices.size(); i++) {
            StockPrice currentPrice = prices.get(i);
            BigDecimal shortMA = shortSMA.get(i);
            BigDecimal longMA = longSMA.get(i);
            BigDecimal prevShortMA = shortSMA.get(i - 1);
            BigDecimal prevLongMA = longSMA.get(i - 1);
            
            // Check for crossover
            boolean bullishCrossover = prevShortMA.compareTo(prevLongMA) <= 0 && 
                                       shortMA.compareTo(longMA) > 0;
            boolean bearishCrossover = prevShortMA.compareTo(prevLongMA) >= 0 && 
                                       shortMA.compareTo(longMA) < 0;
            
            if (bullishCrossover && !inPosition) {
                // Buy signal
                shares = capital.divide(currentPrice.getClose(), 2, RoundingMode.DOWN);
                capital = BigDecimal.ZERO;
                inPosition = true;
                
                trades.add(BacktestResult.Trade.builder()
                    .date(currentPrice.getDate())
                    .type("BUY")
                    .price(currentPrice.getClose())
                    .shares(shares)
                    .value(shares.multiply(currentPrice.getClose()))
                    .reason("SMA Bullish Crossover")
                    .build());
                
                log.debug("BUY {} shares at {} on {}", shares, currentPrice.getClose(), currentPrice.getDate());
                
            } else if (bearishCrossover && inPosition) {
                // Sell signal
                capital = shares.multiply(currentPrice.getClose());
                BigDecimal soldShares = shares;
                shares = BigDecimal.ZERO;
                inPosition = false;
                
                trades.add(BacktestResult.Trade.builder()
                    .date(currentPrice.getDate())
                    .type("SELL")
                    .price(currentPrice.getClose())
                    .shares(soldShares)
                    .value(capital)
                    .reason("SMA Bearish Crossover")
                    .build());
                
                log.debug("SELL {} shares at {} on {}", soldShares, currentPrice.getClose(), currentPrice.getDate());
            }
        }
        
        // Close any open position at end
        if (inPosition) {
            StockPrice lastPrice = prices.get(prices.size() - 1);
            capital = shares.multiply(lastPrice.getClose());
            trades.add(BacktestResult.Trade.builder()
                .date(lastPrice.getDate())
                .type("SELL")
                .price(lastPrice.getClose())
                .shares(shares)
                .value(capital)
                .reason("End of backtest")
                .build());
        }
        
        // Calculate performance metrics
        BigDecimal finalValue = inPosition ? shares.multiply(prices.get(prices.size() - 1).getClose()) : capital;
        BigDecimal totalReturn = finalValue.subtract(initialCapital)
            .divide(initialCapital, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
        
        // Calculate buy and hold return
        BigDecimal buyHoldShares = initialCapital.divide(prices.get(longPeriod).getClose(), 2, RoundingMode.DOWN);
        BigDecimal buyHoldFinalValue = buyHoldShares.multiply(prices.get(prices.size() - 1).getClose());
        BigDecimal buyHoldReturn = buyHoldFinalValue.subtract(initialCapital)
            .divide(initialCapital, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
        
        // Calculate win rate
        long winningTrades = 0;
        long losingTrades = 0;
        BigDecimal totalProfit = BigDecimal.ZERO;
        BigDecimal totalLoss = BigDecimal.ZERO;
        
        for (int i = 1; i < trades.size(); i++) {
            if (trades.get(i).getType().equals("SELL") && i > 0) {
                BacktestResult.Trade buyTrade = trades.get(i - 1);
                BacktestResult.Trade sellTrade = trades.get(i);
                BigDecimal profit = sellTrade.getPrice().subtract(buyTrade.getPrice())
                    .multiply(buyTrade.getShares());
                
                if (profit.compareTo(BigDecimal.ZERO) > 0) {
                    winningTrades++;
                    totalProfit = totalProfit.add(profit);
                } else {
                    losingTrades++;
                    totalLoss = totalLoss.add(profit.abs());
                }
            }
        }
        
        BigDecimal winRate = winningTrades + losingTrades > 0 
            ? BigDecimal.valueOf(winningTrades * 100.0 / (winningTrades + losingTrades))
            : BigDecimal.ZERO;
        
        BigDecimal profitFactor = totalLoss.compareTo(BigDecimal.ZERO) > 0
            ? totalProfit.divide(totalLoss, 2, RoundingMode.HALF_UP)
            : totalProfit.compareTo(BigDecimal.ZERO) > 0 ? BigDecimal.valueOf(999) : BigDecimal.ZERO;
        
        return BacktestResult.builder()
            .ticker(ticker)
            .strategy("SMA Crossover (" + shortPeriod + "/" + longPeriod + ")")
            .startDate(prices.get(longPeriod).getDate())
            .endDate(prices.get(prices.size() - 1).getDate())
            .initialCapital(initialCapital)
            .finalValue(finalValue)
            .totalReturn(totalReturn)
            .buyHoldReturn(buyHoldReturn)
            .numberOfTrades(trades.size())
            .winRate(winRate)
            .profitFactor(profitFactor)
            .trades(trades)
            .build();
    }
    
    /**
     * Calculate Simple Moving Average
     */
    private List<BigDecimal> calculateSMA(List<StockPrice> prices, int period) {
        List<BigDecimal> sma = new ArrayList<>();
        
        for (int i = 0; i < prices.size(); i++) {
            if (i < period - 1) {
                sma.add(BigDecimal.ZERO);
            } else {
                BigDecimal sum = BigDecimal.ZERO;
                for (int j = i - period + 1; j <= i; j++) {
                    sum = sum.add(prices.get(j).getClose());
                }
                sma.add(sum.divide(BigDecimal.valueOf(period), 2, RoundingMode.HALF_UP));
            }
        }
        
        return sma;
    }
    
    /**
     * Run RSI-based strategy backtest
     */
    public BacktestResult runRSIBacktest(String ticker, int period, int oversoldLevel, int overboughtLevel, BigDecimal initialCapital) {
        log.info("Running RSI backtest for {} with period {}", ticker, period);
        
        List<StockPrice> prices = stockPriceRepository.findByTickerOrderByDateAsc(ticker);
        
        if (prices.size() < period + 1) {
            return BacktestResult.builder()
                .ticker(ticker)
                .strategy("RSI Strategy")
                .error("Insufficient data")
                .build();
        }
        
        // Calculate RSI
        List<BigDecimal> rsiValues = calculateRSI(prices, period);
        
        // Run backtest
        List<BacktestResult.Trade> trades = new ArrayList<>();
        BigDecimal capital = initialCapital;
        BigDecimal shares = BigDecimal.ZERO;
        boolean inPosition = false;
        
        for (int i = period; i < prices.size(); i++) {
            StockPrice currentPrice = prices.get(i);
            BigDecimal rsi = rsiValues.get(i);
            
            if (rsi.compareTo(BigDecimal.valueOf(oversoldLevel)) < 0 && !inPosition) {
                // Buy signal (oversold)
                shares = capital.divide(currentPrice.getClose(), 2, RoundingMode.DOWN);
                capital = BigDecimal.ZERO;
                inPosition = true;
                
                trades.add(BacktestResult.Trade.builder()
                    .date(currentPrice.getDate())
                    .type("BUY")
                    .price(currentPrice.getClose())
                    .shares(shares)
                    .value(shares.multiply(currentPrice.getClose()))
                    .reason("RSI Oversold (" + rsi.setScale(2, RoundingMode.HALF_UP) + ")")
                    .build());
                
            } else if (rsi.compareTo(BigDecimal.valueOf(overboughtLevel)) > 0 && inPosition) {
                // Sell signal (overbought)
                capital = shares.multiply(currentPrice.getClose());
                BigDecimal soldShares = shares;
                shares = BigDecimal.ZERO;
                inPosition = false;
                
                trades.add(BacktestResult.Trade.builder()
                    .date(currentPrice.getDate())
                    .type("SELL")
                    .price(currentPrice.getClose())
                    .shares(soldShares)
                    .value(capital)
                    .reason("RSI Overbought (" + rsi.setScale(2, RoundingMode.HALF_UP) + ")")
                    .build());
            }
        }
        
        // Close any open position
        if (inPosition) {
            StockPrice lastPrice = prices.get(prices.size() - 1);
            capital = shares.multiply(lastPrice.getClose());
            trades.add(BacktestResult.Trade.builder()
                .date(lastPrice.getDate())
                .type("SELL")
                .price(lastPrice.getClose())
                .shares(shares)
                .value(capital)
                .reason("End of backtest")
                .build());
        }
        
        // Calculate metrics
        BigDecimal finalValue = inPosition ? shares.multiply(prices.get(prices.size() - 1).getClose()) : capital;
        BigDecimal totalReturn = finalValue.subtract(initialCapital)
            .divide(initialCapital, 4, RoundingMode.HALF_UP)
            .multiply(BigDecimal.valueOf(100));
        
        return BacktestResult.builder()
            .ticker(ticker)
            .strategy("RSI Strategy (" + oversoldLevel + "/" + overboughtLevel + ")")
            .startDate(prices.get(period).getDate())
            .endDate(prices.get(prices.size() - 1).getDate())
            .initialCapital(initialCapital)
            .finalValue(finalValue)
            .totalReturn(totalReturn)
            .numberOfTrades(trades.size())
            .trades(trades)
            .build();
    }
    
    /**
     * Calculate RSI (Relative Strength Index)
     */
    private List<BigDecimal> calculateRSI(List<StockPrice> prices, int period) {
        List<BigDecimal> rsiValues = new ArrayList<>();
        
        // Calculate price changes
        List<BigDecimal> gains = new ArrayList<>();
        List<BigDecimal> losses = new ArrayList<>();
        
        for (int i = 1; i < prices.size(); i++) {
            BigDecimal change = prices.get(i).getClose().subtract(prices.get(i - 1).getClose());
            if (change.compareTo(BigDecimal.ZERO) > 0) {
                gains.add(change);
                losses.add(BigDecimal.ZERO);
            } else {
                gains.add(BigDecimal.ZERO);
                losses.add(change.abs());
            }
        }
        
        // Calculate initial average gain and loss
        BigDecimal avgGain = BigDecimal.ZERO;
        BigDecimal avgLoss = BigDecimal.ZERO;
        
        for (int i = 0; i < period && i < gains.size(); i++) {
            avgGain = avgGain.add(gains.get(i));
            avgLoss = avgLoss.add(losses.get(i));
        }
        
        avgGain = avgGain.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
        avgLoss = avgLoss.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
        
        // Add initial zeros for period
        for (int i = 0; i < period; i++) {
            rsiValues.add(BigDecimal.valueOf(50));
        }
        
        // Calculate RSI for each subsequent period
        for (int i = period; i < prices.size(); i++) {
            if (i - 1 < gains.size()) {
                avgGain = avgGain.multiply(BigDecimal.valueOf(period - 1))
                    .add(gains.get(i - 1))
                    .divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
                    
                avgLoss = avgLoss.multiply(BigDecimal.valueOf(period - 1))
                    .add(losses.get(i - 1))
                    .divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
            }
            
            BigDecimal rs = avgLoss.compareTo(BigDecimal.ZERO) > 0 
                ? avgGain.divide(avgLoss, 6, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(100);
                
            BigDecimal rsi = BigDecimal.valueOf(100).subtract(
                BigDecimal.valueOf(100).divide(
                    BigDecimal.ONE.add(rs), 2, RoundingMode.HALF_UP
                )
            );
            
            rsiValues.add(rsi);
        }
        
        return rsiValues;
    }
}