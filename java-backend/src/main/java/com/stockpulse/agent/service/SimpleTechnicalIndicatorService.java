package com.stockpulse.agent.service;

import com.stockpulse.agent.model.StockPrice;
import com.stockpulse.agent.model.Technical;
import com.stockpulse.agent.repository.StockPriceRepository;
import com.stockpulse.agent.repository.TechnicalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SimpleTechnicalIndicatorService {
    
    private final StockPriceRepository stockPriceRepository;
    private final TechnicalRepository technicalRepository;
    
    @Transactional
    public void calculateBasicIndicators(String ticker) {
        log.debug("Calculating basic technical indicators for ticker: {}", ticker);
        
        try {
            // Get historical price data (last 200 days for moving averages)
            LocalDateTime since = LocalDateTime.now().minusDays(200);
            List<StockPrice> prices = stockPriceRepository.findByTickerAndDateBetween(
                ticker, since, LocalDateTime.now());
            
            if (prices.size() < 20) {
                log.warn("Insufficient price data for technical indicators calculation for ticker: {}", ticker);
                return;
            }
            
            // Calculate simple moving averages
            BigDecimal sma20 = calculateSMA(prices, 20);
            BigDecimal sma50 = calculateSMA(prices, 50);
            BigDecimal sma200 = calculateSMA(prices, 200);
            
            // Calculate simple RSI
            BigDecimal rsi = calculateRSI(prices, 14);
            
            // Calculate simple MACD
            BigDecimal[] macd = calculateSimpleMACD(prices);
            
            // Calculate Bollinger Bands
            BigDecimal[] bb = calculateBollingerBands(prices, 20, 2.0);
            
            LocalDateTime latestDate = prices.get(prices.size() - 1).getDate();
            
            Technical technical = Technical.builder()
                .ticker(ticker)
                .date(latestDate)
                .sma20(sma20)
                .sma50(sma50)
                .sma200(sma200)
                .rsi(rsi)
                .macd(macd[0])
                .macdSignal(macd[1])
                .macdHist(macd[2])
                .bollingerUpper(bb[0])
                .bollingerMiddle(bb[1])
                .bollingerLower(bb[2])
                .build();
            
            technicalRepository.save(technical);
            
            log.debug("Basic technical indicators calculated and saved for ticker: {}", ticker);
            
        } catch (Exception e) {
            log.error("Error calculating basic technical indicators for ticker: {}", ticker, e);
        }
    }
    
    private BigDecimal calculateSMA(List<StockPrice> prices, int period) {
        if (prices.size() < period) {
            return null;
        }
        
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            sum = sum.add(prices.get(i).getClose());
        }
        
        return sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
    }
    
    private BigDecimal calculateRSI(List<StockPrice> prices, int period) {
        if (prices.size() < period + 1) {
            return BigDecimal.valueOf(50); // Default RSI
        }
        
        BigDecimal avgGain = BigDecimal.ZERO;
        BigDecimal avgLoss = BigDecimal.ZERO;
        
        // Calculate initial average gain and loss
        for (int i = prices.size() - period; i < prices.size(); i++) {
            BigDecimal change = prices.get(i).getClose().subtract(prices.get(i - 1).getClose());
            if (change.compareTo(BigDecimal.ZERO) > 0) {
                avgGain = avgGain.add(change);
            } else {
                avgLoss = avgLoss.add(change.abs());
            }
        }
        
        avgGain = avgGain.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
        avgLoss = avgLoss.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
        
        if (avgLoss.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100);
        }
        
        BigDecimal rs = avgGain.divide(avgLoss, 4, RoundingMode.HALF_UP);
        BigDecimal rsi = BigDecimal.valueOf(100).subtract(
            BigDecimal.valueOf(100).divide(BigDecimal.ONE.add(rs), 4, RoundingMode.HALF_UP)
        );
        
        return rsi;
    }
    
    private BigDecimal[] calculateSimpleMACD(List<StockPrice> prices) {
        if (prices.size() < 26) {
            return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO};
        }
        
        // Simplified MACD calculation
        BigDecimal ema12 = calculateEMA(prices, 12);
        BigDecimal ema26 = calculateEMA(prices, 26);
        BigDecimal macd = ema12.subtract(ema26);
        BigDecimal signal = macd.multiply(BigDecimal.valueOf(0.9)); // Simplified signal
        BigDecimal histogram = macd.subtract(signal);
        
        return new BigDecimal[]{macd, signal, histogram};
    }
    
    private BigDecimal calculateEMA(List<StockPrice> prices, int period) {
        if (prices.size() < period) {
            return calculateSMA(prices, prices.size());
        }
        
        BigDecimal multiplier = BigDecimal.valueOf(2.0 / (period + 1));
        BigDecimal ema = prices.get(prices.size() - period).getClose();
        
        for (int i = prices.size() - period + 1; i < prices.size(); i++) {
            ema = prices.get(i).getClose().multiply(multiplier)
                .add(ema.multiply(BigDecimal.ONE.subtract(multiplier)));
        }
        
        return ema;
    }
    
    private BigDecimal[] calculateBollingerBands(List<StockPrice> prices, int period, double standardDeviations) {
        if (prices.size() < period) {
            BigDecimal currentPrice = prices.get(prices.size() - 1).getClose();
            return new BigDecimal[]{currentPrice, currentPrice, currentPrice};
        }
        
        BigDecimal sma = calculateSMA(prices, period);
        
        // Calculate standard deviation
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = prices.size() - period; i < prices.size(); i++) {
            BigDecimal diff = prices.get(i).getClose().subtract(sma);
            sum = sum.add(diff.multiply(diff));
        }
        
        BigDecimal variance = sum.divide(BigDecimal.valueOf(period), 4, RoundingMode.HALF_UP);
        BigDecimal stdDev = BigDecimal.valueOf(Math.sqrt(variance.doubleValue()));
        
        BigDecimal upper = sma.add(stdDev.multiply(BigDecimal.valueOf(standardDeviations)));
        BigDecimal lower = sma.subtract(stdDev.multiply(BigDecimal.valueOf(standardDeviations)));
        
        return new BigDecimal[]{upper, sma, lower};
    }
}