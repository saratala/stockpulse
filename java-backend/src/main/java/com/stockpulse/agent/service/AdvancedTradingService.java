package com.stockpulse.agent.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.stockpulse.agent.model.StockPrice;
import com.stockpulse.agent.repository.StockPriceRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdvancedTradingService {
    
    private final StockPriceRepository stockPriceRepository;
    
    /**
     * Price Action Analysis - Identifies key patterns
     */
    public PriceActionSignal analyzePriceAction(String ticker) {
        List<StockPrice> prices = stockPriceRepository.findByTickerOrderByDateDesc(ticker);
        
        if (prices.size() < 20) {
            return PriceActionSignal.NEUTRAL;
        }
        
        StockPrice current = prices.get(0);
        StockPrice previous = prices.get(1);
        StockPrice twoDaysAgo = prices.get(2);
        
        // Pin Bar Detection (Reversal Signal)
        if (isPinBar(current)) {
            BigDecimal wickPosition = getWickPosition(current);
            if (wickPosition.compareTo(BigDecimal.valueOf(0.7)) > 0) {
                log.info("Bullish Pin Bar detected for {}", ticker);
                return PriceActionSignal.BULLISH_REVERSAL;
            } else if (wickPosition.compareTo(BigDecimal.valueOf(0.3)) < 0) {
                log.info("Bearish Pin Bar detected for {}", ticker);
                return PriceActionSignal.BEARISH_REVERSAL;
            }
        }
        
        // Engulfing Pattern
        if (isBullishEngulfing(previous, current)) {
            log.info("Bullish Engulfing pattern for {}", ticker);
            return PriceActionSignal.BULLISH_CONTINUATION;
        }
        if (isBearishEngulfing(previous, current)) {
            log.info("Bearish Engulfing pattern for {}", ticker);
            return PriceActionSignal.BEARISH_CONTINUATION;
        }
        
        // Inside Bar (Consolidation)
        if (isInsideBar(previous, current)) {
            log.info("Inside Bar consolidation for {}", ticker);
            return PriceActionSignal.CONSOLIDATION;
        }
        
        // Break of Structure (BOS)
        if (detectBreakOfStructure(prices)) {
            return PriceActionSignal.TREND_CHANGE;
        }
        
        return PriceActionSignal.NEUTRAL;
    }
    
    /**
     * Support and Resistance Detection
     */
    public SupportResistanceLevels findSupportResistance(String ticker, int lookback) {
        List<StockPrice> prices = stockPriceRepository.findByTickerOrderByDateDesc(ticker);
        
        if (prices.size() < lookback) {
            return null;
        }
        
        List<BigDecimal> pivotHighs = new ArrayList<>();
        List<BigDecimal> pivotLows = new ArrayList<>();
        
        // Find pivot points
        for (int i = 2; i < lookback - 2 && i < prices.size() - 2; i++) {
            StockPrice current = prices.get(i);
            StockPrice prev1 = prices.get(i + 1);
            StockPrice prev2 = prices.get(i + 2);
            StockPrice next1 = prices.get(i - 1);
            StockPrice next2 = prices.get(i - 2);
            
            // Pivot High
            if (current.getHigh().compareTo(prev1.getHigh()) > 0 &&
                current.getHigh().compareTo(prev2.getHigh()) > 0 &&
                current.getHigh().compareTo(next1.getHigh()) > 0 &&
                current.getHigh().compareTo(next2.getHigh()) > 0) {
                pivotHighs.add(current.getHigh());
            }
            
            // Pivot Low
            if (current.getLow().compareTo(prev1.getLow()) < 0 &&
                current.getLow().compareTo(prev2.getLow()) < 0 &&
                current.getLow().compareTo(next1.getLow()) < 0 &&
                current.getLow().compareTo(next2.getLow()) < 0) {
                pivotLows.add(current.getLow());
            }
        }
        
        // Cluster similar levels
        List<BigDecimal> resistanceLevels = clusterLevels(pivotHighs, BigDecimal.valueOf(0.02));
        List<BigDecimal> supportLevels = clusterLevels(pivotLows, BigDecimal.valueOf(0.02));
        
        return new SupportResistanceLevels(supportLevels, resistanceLevels);
    }
    
    /**
     * Volume Profile Analysis
     */
    public VolumeProfile analyzeVolumeProfile(String ticker, int periods) {
        List<StockPrice> prices = stockPriceRepository.findByTickerOrderByDateDesc(ticker);
        
        if (prices.size() < periods) {
            return null;
        }
        
        // Create price buckets
        BigDecimal minPrice = prices.stream()
            .limit(periods)
            .map(StockPrice::getLow)
            .min(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
            
        BigDecimal maxPrice = prices.stream()
            .limit(periods)
            .map(StockPrice::getHigh)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
            
        BigDecimal range = maxPrice.subtract(minPrice);
        int numBuckets = 20;
        BigDecimal bucketSize = range.divide(BigDecimal.valueOf(numBuckets), 2, RoundingMode.HALF_UP);
        
        Map<BigDecimal, Long> volumeByPrice = new TreeMap<>();
        
        // Accumulate volume in price buckets
        for (int i = 0; i < periods && i < prices.size(); i++) {
            StockPrice price = prices.get(i);
            BigDecimal avgPrice = price.getHigh().add(price.getLow()).divide(BigDecimal.TWO, 2, RoundingMode.HALF_UP);
            BigDecimal bucket = avgPrice.subtract(minPrice).divide(bucketSize, 0, RoundingMode.DOWN).multiply(bucketSize).add(minPrice);
            
            volumeByPrice.merge(bucket, price.getVolume(), Long::sum);
        }
        
        // Find Point of Control (POC) - price with highest volume
        Map.Entry<BigDecimal, Long> poc = volumeByPrice.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .orElse(null);
            
        // Value Area (70% of volume)
        long totalVolume = volumeByPrice.values().stream().mapToLong(Long::longValue).sum();
        long targetVolume = (long)(totalVolume * 0.7);
        
        BigDecimal valueAreaHigh = BigDecimal.ZERO;
        BigDecimal valueAreaLow = BigDecimal.ZERO;
        long accumulatedVolume = 0;
        
        List<Map.Entry<BigDecimal, Long>> sortedEntries = new ArrayList<>(volumeByPrice.entrySet());
        sortedEntries.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        
        for (Map.Entry<BigDecimal, Long> entry : sortedEntries) {
            accumulatedVolume += entry.getValue();
            if (valueAreaLow.equals(BigDecimal.ZERO) || entry.getKey().compareTo(valueAreaLow) < 0) {
                valueAreaLow = entry.getKey();
            }
            if (valueAreaHigh.equals(BigDecimal.ZERO) || entry.getKey().compareTo(valueAreaHigh) > 0) {
                valueAreaHigh = entry.getKey();
            }
            
            if (accumulatedVolume >= targetVolume) {
                break;
            }
        }
        
        return new VolumeProfile(poc.getKey(), valueAreaHigh, valueAreaLow, volumeByPrice);
    }
    
    /**
     * Market Microstructure Analysis (Order Flow Imbalance)
     */
    public OrderFlowAnalysis analyzeOrderFlow(String ticker) {
        List<StockPrice> prices = stockPriceRepository.findByTickerOrderByDateDesc(ticker);
        
        if (prices.size() < 10) {
            return null;
        }
        
        BigDecimal buyPressure = BigDecimal.ZERO;
        BigDecimal sellPressure = BigDecimal.ZERO;
        
        for (int i = 0; i < 10 && i < prices.size(); i++) {
            StockPrice price = prices.get(i);
            
            // Estimate buy/sell pressure from price action
            BigDecimal range = price.getHigh().subtract(price.getLow());
            BigDecimal closePosition = BigDecimal.ZERO;
            
            if (range.compareTo(BigDecimal.ZERO) > 0) {
                closePosition = price.getClose().subtract(price.getLow()).divide(range, 4, RoundingMode.HALF_UP);
            }
            
            // Higher close in range = more buying pressure
            if (closePosition.compareTo(BigDecimal.valueOf(0.5)) > 0) {
                buyPressure = buyPressure.add(BigDecimal.valueOf(price.getVolume()).multiply(closePosition));
            } else {
                sellPressure = sellPressure.add(BigDecimal.valueOf(price.getVolume()).multiply(BigDecimal.ONE.subtract(closePosition)));
            }
        }
        
        BigDecimal imbalance = buyPressure.subtract(sellPressure)
            .divide(buyPressure.add(sellPressure), 4, RoundingMode.HALF_UP);
            
        return new OrderFlowAnalysis(buyPressure, sellPressure, imbalance);
    }
    
    /**
     * Options Greeks Calculator (Simplified Black-Scholes)
     */
    public OptionsGreeks calculateGreeks(BigDecimal spotPrice, BigDecimal strikePrice, 
                                         double timeToExpiry, double volatility, double riskFreeRate) {
        
        double S = spotPrice.doubleValue();
        double K = strikePrice.doubleValue();
        double T = timeToExpiry;
        double σ = volatility;
        double r = riskFreeRate;
        
        // Calculate d1 and d2
        double d1 = (Math.log(S / K) + (r + σ * σ / 2) * T) / (σ * Math.sqrt(T));
        double d2 = d1 - σ * Math.sqrt(T);
        
        // Delta: Rate of change of option price with respect to underlying
        double delta = normCDF(d1);
        
        // Gamma: Rate of change of delta
        double gamma = normPDF(d1) / (S * σ * Math.sqrt(T));
        
        // Theta: Time decay (negative for long positions)
        double theta = -(S * normPDF(d1) * σ) / (2 * Math.sqrt(T)) 
                      - r * K * Math.exp(-r * T) * normCDF(d2);
        theta = theta / 365; // Convert to daily theta
        
        // Vega: Sensitivity to volatility
        double vega = S * normPDF(d1) * Math.sqrt(T) / 100; // Per 1% change in volatility
        
        // Rho: Sensitivity to interest rate
        double rho = K * T * Math.exp(-r * T) * normCDF(d2) / 100; // Per 1% change in rate
        
        return new OptionsGreeks(delta, gamma, theta, vega, rho);
    }
    
    /**
     * Volatility-based Signals (Using ATR and Bollinger Bands)
     */
    public VolatilitySignal analyzeVolatility(String ticker) {
        List<StockPrice> prices = stockPriceRepository.findByTickerOrderByDateDesc(ticker);
        
        if (prices.size() < 20) {
            return VolatilitySignal.NORMAL;
        }
        
        // Calculate ATR (Average True Range)
        BigDecimal atr = calculateATR(prices, 14);
        
        // Calculate historical volatility
        BigDecimal historicalVol = calculateHistoricalVolatility(prices, 20);
        
        // Bollinger Bands
        BollingerBands bb = calculateBollingerBands(prices, 20, 2);
        
        StockPrice current = prices.get(0);
        BigDecimal currentPrice = current.getClose();
        
        // Check for volatility squeeze (Bollinger Band squeeze)
        BigDecimal bandWidth = bb.upper.subtract(bb.lower).divide(bb.middle, 4, RoundingMode.HALF_UP);
        
        if (bandWidth.compareTo(BigDecimal.valueOf(0.05)) < 0) {
            log.info("Volatility squeeze detected for {}", ticker);
            return VolatilitySignal.SQUEEZE;
        }
        
        // Check for volatility expansion
        if (atr.compareTo(historicalVol.multiply(BigDecimal.valueOf(1.5))) > 0) {
            log.info("Volatility expansion for {}", ticker);
            return VolatilitySignal.EXPANSION;
        }
        
        // Check band position
        if (currentPrice.compareTo(bb.upper) > 0) {
            return VolatilitySignal.OVERBOUGHT;
        } else if (currentPrice.compareTo(bb.lower) < 0) {
            return VolatilitySignal.OVERSOLD;
        }
        
        return VolatilitySignal.NORMAL;
    }
    
    // Helper methods
    
    private boolean isPinBar(StockPrice candle) {
        BigDecimal body = candle.getClose().subtract(candle.getOpen()).abs();
        BigDecimal totalRange = candle.getHigh().subtract(candle.getLow());
        
        if (totalRange.compareTo(BigDecimal.ZERO) == 0) return false;
        
        BigDecimal bodyRatio = body.divide(totalRange, 4, RoundingMode.HALF_UP);
        return bodyRatio.compareTo(BigDecimal.valueOf(0.3)) < 0; // Body is less than 30% of total range
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
    
    private boolean isInsideBar(StockPrice prev, StockPrice current) {
        return current.getHigh().compareTo(prev.getHigh()) < 0 && 
               current.getLow().compareTo(prev.getLow()) > 0;
    }
    
    private boolean detectBreakOfStructure(List<StockPrice> prices) {
        if (prices.size() < 10) return false;
        
        // Find recent swing high and low
        BigDecimal swingHigh = prices.subList(2, 8).stream()
            .map(StockPrice::getHigh)
            .max(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
            
        BigDecimal swingLow = prices.subList(2, 8).stream()
            .map(StockPrice::getLow)
            .min(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);
            
        StockPrice current = prices.get(0);
        
        // Break above swing high or below swing low
        return current.getClose().compareTo(swingHigh) > 0 || 
               current.getClose().compareTo(swingLow) < 0;
    }
    
    private List<BigDecimal> clusterLevels(List<BigDecimal> levels, BigDecimal threshold) {
        List<BigDecimal> clustered = new ArrayList<>();
        Collections.sort(levels);
        
        for (BigDecimal level : levels) {
            boolean merged = false;
            for (int i = 0; i < clustered.size(); i++) {
                BigDecimal existing = clustered.get(i);
                BigDecimal diff = level.subtract(existing).abs();
                BigDecimal pctDiff = diff.divide(existing, 4, RoundingMode.HALF_UP);
                
                if (pctDiff.compareTo(threshold) < 0) {
                    // Merge with existing level (average)
                    clustered.set(i, existing.add(level).divide(BigDecimal.TWO, 2, RoundingMode.HALF_UP));
                    merged = true;
                    break;
                }
            }
            
            if (!merged) {
                clustered.add(level);
            }
        }
        
        return clustered;
    }
    
    private BigDecimal calculateATR(List<StockPrice> prices, int period) {
        BigDecimal atr = BigDecimal.ZERO;
        
        for (int i = 1; i < period && i < prices.size(); i++) {
            StockPrice current = prices.get(i - 1);
            StockPrice previous = prices.get(i);
            
            BigDecimal tr1 = current.getHigh().subtract(current.getLow());
            BigDecimal tr2 = current.getHigh().subtract(previous.getClose()).abs();
            BigDecimal tr3 = current.getLow().subtract(previous.getClose()).abs();
            
            BigDecimal tr = tr1.max(tr2).max(tr3);
            atr = atr.add(tr);
        }
        
        return atr.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateHistoricalVolatility(List<StockPrice> prices, int period) {
        List<BigDecimal> returns = new ArrayList<>();
        
        for (int i = 1; i < period && i < prices.size(); i++) {
            BigDecimal currentPrice = prices.get(i - 1).getClose();
            BigDecimal previousPrice = prices.get(i).getClose();
            BigDecimal dailyReturn = currentPrice.subtract(previousPrice)
                .divide(previousPrice, 6, RoundingMode.HALF_UP);
            returns.add(dailyReturn);
        }
        
        // Calculate standard deviation
        BigDecimal mean = returns.stream()
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(returns.size()), 6, RoundingMode.HALF_UP);
            
        BigDecimal variance = returns.stream()
            .map(r -> r.subtract(mean).pow(2))
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .divide(BigDecimal.valueOf(returns.size()), 6, RoundingMode.HALF_UP);
            
        return BigDecimal.valueOf(Math.sqrt(variance.doubleValue()) * Math.sqrt(252)); // Annualized
    }
    
    private BollingerBands calculateBollingerBands(List<StockPrice> prices, int period, double stdDevMultiplier) {
        BigDecimal sma = BigDecimal.ZERO;
        
        for (int i = 0; i < period && i < prices.size(); i++) {
            sma = sma.add(prices.get(i).getClose());
        }
        sma = sma.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
        
        // Calculate standard deviation
        BigDecimal variance = BigDecimal.ZERO;
        for (int i = 0; i < period && i < prices.size(); i++) {
            BigDecimal diff = prices.get(i).getClose().subtract(sma);
            variance = variance.add(diff.multiply(diff));
        }
        variance = variance.divide(BigDecimal.valueOf(period), 6, RoundingMode.HALF_UP);
        
        BigDecimal stdDev = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
        BigDecimal upper = sma.add(stdDev.multiply(BigDecimal.valueOf(stdDevMultiplier)));
        BigDecimal lower = sma.subtract(stdDev.multiply(BigDecimal.valueOf(stdDevMultiplier)));
        
        return new BollingerBands(upper, sma, lower);
    }
    
    // Normal distribution functions for Black-Scholes
    private double normCDF(double x) {
        return 0.5 * (1 + erf(x / Math.sqrt(2)));
    }
    
    private double normPDF(double x) {
        return Math.exp(-x * x / 2) / Math.sqrt(2 * Math.PI);
    }
    
    private double erf(double x) {
        // Approximation of error function
        double a1 =  0.254829592;
        double a2 = -0.284496736;
        double a3 =  1.421413741;
        double a4 = -1.453152027;
        double a5 =  1.061405429;
        double p  =  0.3275911;
        
        int sign = x < 0 ? -1 : 1;
        x = Math.abs(x);
        
        double t = 1.0 / (1.0 + p * x);
        double y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);
        
        return sign * y;
    }
    
    // Data classes
    
    public enum PriceActionSignal {
        BULLISH_REVERSAL, BEARISH_REVERSAL,
        BULLISH_CONTINUATION, BEARISH_CONTINUATION,
        CONSOLIDATION, TREND_CHANGE, NEUTRAL
    }
    
    public enum VolatilitySignal {
        SQUEEZE, EXPANSION, OVERBOUGHT, OVERSOLD, NORMAL
    }
    
    public static class SupportResistanceLevels {
        public final List<BigDecimal> support;
        public final List<BigDecimal> resistance;
        
        public SupportResistanceLevels(List<BigDecimal> support, List<BigDecimal> resistance) {
            this.support = support;
            this.resistance = resistance;
        }
    }
    
    public static class VolumeProfile {
        public final BigDecimal pointOfControl;
        public final BigDecimal valueAreaHigh;
        public final BigDecimal valueAreaLow;
        public final Map<BigDecimal, Long> volumeByPrice;
        
        public VolumeProfile(BigDecimal poc, BigDecimal vah, BigDecimal val, Map<BigDecimal, Long> vbp) {
            this.pointOfControl = poc;
            this.valueAreaHigh = vah;
            this.valueAreaLow = val;
            this.volumeByPrice = vbp;
        }
    }
    
    public static class OrderFlowAnalysis {
        public final BigDecimal buyPressure;
        public final BigDecimal sellPressure;
        public final BigDecimal imbalance; // Positive = buying, Negative = selling
        
        public OrderFlowAnalysis(BigDecimal buy, BigDecimal sell, BigDecimal imbalance) {
            this.buyPressure = buy;
            this.sellPressure = sell;
            this.imbalance = imbalance;
        }
    }
    
    public static class OptionsGreeks {
        public final double delta;
        public final double gamma;
        public final double theta;
        public final double vega;
        public final double rho;
        
        public OptionsGreeks(double delta, double gamma, double theta, double vega, double rho) {
            this.delta = delta;
            this.gamma = gamma;
            this.theta = theta;
            this.vega = vega;
            this.rho = rho;
        }
    }
    
    private static class BollingerBands {
        public final BigDecimal upper;
        public final BigDecimal middle;
        public final BigDecimal lower;
        
        public BollingerBands(BigDecimal upper, BigDecimal middle, BigDecimal lower) {
            this.upper = upper;
            this.middle = middle;
            this.lower = lower;
        }
    }
}