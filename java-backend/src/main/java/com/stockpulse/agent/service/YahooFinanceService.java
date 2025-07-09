package com.stockpulse.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class YahooFinanceService {
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    @Value("${app.stock-data.api.yahoo-finance.base-url:https://query1.finance.yahoo.com}")
    private String yahooFinanceBaseUrl;
    
    @Value("${app.stock-data.api.yahoo-finance.timeout:10000}")
    private int timeout;
    
    @Cacheable(value = "stock-quotes", key = "#ticker")
    public Optional<StockQuote> getCurrentQuote(String ticker) {
        log.debug("Fetching current quote for ticker: {}", ticker);
        
        try {
            String url = yahooFinanceBaseUrl + "/v8/finance/chart/" + ticker;
            
            Mono<String> response = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(timeout))
                .doOnError(error -> log.error("Error fetching quote for {}: {}", ticker, error.getMessage()));
            
            String jsonResponse = response.block();
            if (jsonResponse == null) {
                log.warn("No response received for ticker: {}", ticker);
                return Optional.empty();
            }
            
            return parseQuoteResponse(ticker, jsonResponse);
            
        } catch (Exception e) {
            log.error("Error fetching current quote for ticker: {}", ticker, e);
            return Optional.empty();
        }
    }
    
    @Cacheable(value = "stock-info", key = "#ticker")
    public Optional<StockInfo> getStockInfo(String ticker) {
        log.debug("Fetching stock info for ticker: {}", ticker);
        
        try {
            String url = yahooFinanceBaseUrl + "/v10/finance/quoteSummary/" + ticker + 
                        "?modules=summaryDetail,defaultKeyStatistics,assetProfile";
            
            Mono<String> response = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(timeout))
                .doOnError(error -> log.error("Error fetching stock info for {}: {}", ticker, error.getMessage()));
            
            String jsonResponse = response.block();
            if (jsonResponse == null) {
                log.warn("No stock info response received for ticker: {}", ticker);
                return Optional.empty();
            }
            
            return parseStockInfoResponse(ticker, jsonResponse);
            
        } catch (Exception e) {
            log.error("Error fetching stock info for ticker: {}", ticker, e);
            return Optional.empty();
        }
    }
    
    public Optional<HistoricalData> getHistoricalData(String ticker, String period, String interval) {
        log.debug("Fetching historical data for ticker: {}, period: {}, interval: {}", ticker, period, interval);
        
        try {
            String url = yahooFinanceBaseUrl + "/v8/finance/chart/" + ticker + 
                        "?period1=0&period2=" + Instant.now().getEpochSecond() + 
                        "&interval=" + interval + "&range=" + period;
            
            Mono<String> response = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(timeout * 2)) // Longer timeout for historical data
                .doOnError(error -> log.error("Error fetching historical data for {}: {}", ticker, error.getMessage()));
            
            String jsonResponse = response.block();
            if (jsonResponse == null) {
                log.warn("No historical data response received for ticker: {}", ticker);
                return Optional.empty();
            }
            
            return parseHistoricalDataResponse(ticker, jsonResponse);
            
        } catch (Exception e) {
            log.error("Error fetching historical data for ticker: {}", ticker, e);
            return Optional.empty();
        }
    }
    
    private Optional<StockQuote> parseQuoteResponse(String ticker, String jsonResponse) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode chartNode = rootNode.path("chart");
            
            if (!chartNode.path("error").isNull()) {
                log.warn("Yahoo Finance API error for ticker {}: {}", ticker, chartNode.path("error"));
                return Optional.empty();
            }
            
            JsonNode resultNode = chartNode.path("result").get(0);
            JsonNode metaNode = resultNode.path("meta");
            
            // Extract current price
            double currentPrice = metaNode.path("regularMarketPrice").asDouble();
            if (currentPrice == 0.0) {
                currentPrice = metaNode.path("previousClose").asDouble();
            }
            
            // Extract volume
            long volume = metaNode.path("regularMarketVolume").asLong();
            
            // Calculate change
            double previousClose = metaNode.path("previousClose").asDouble();
            double change = currentPrice - previousClose;
            double changePercent = (change / previousClose) * 100;
            
            StockQuote quote = StockQuote.builder()
                .ticker(ticker)
                .price(BigDecimal.valueOf(currentPrice))
                .change(BigDecimal.valueOf(change))
                .changePercent(BigDecimal.valueOf(changePercent))
                .volume(volume)
                .previousClose(BigDecimal.valueOf(previousClose))
                .timestamp(LocalDateTime.now())
                .build();
            
            return Optional.of(quote);
            
        } catch (Exception e) {
            log.error("Error parsing quote response for ticker: {}", ticker, e);
            return Optional.empty();
        }
    }
    
    private Optional<StockInfo> parseStockInfoResponse(String ticker, String jsonResponse) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode quoteSummaryNode = rootNode.path("quoteSummary");
            
            if (!quoteSummaryNode.path("error").isNull()) {
                log.warn("Yahoo Finance API error for stock info {}: {}", ticker, quoteSummaryNode.path("error"));
                return Optional.empty();
            }
            
            JsonNode resultNode = quoteSummaryNode.path("result").get(0);
            JsonNode summaryDetailNode = resultNode.path("summaryDetail");
            JsonNode assetProfileNode = resultNode.path("assetProfile");
            JsonNode keyStatisticsNode = resultNode.path("defaultKeyStatistics");
            
            // Extract basic info
            String longName = assetProfileNode.path("longName").asText();
            String sector = assetProfileNode.path("sector").asText();
            String industry = assetProfileNode.path("industry").asText();
            
            // Extract financial metrics
            long marketCap = keyStatisticsNode.path("marketCap").path("raw").asLong();
            double peRatio = keyStatisticsNode.path("trailingPE").path("raw").asDouble();
            double beta = keyStatisticsNode.path("beta").path("raw").asDouble();
            
            // Extract price ranges
            double fiftyTwoWeekHigh = summaryDetailNode.path("fiftyTwoWeekHigh").path("raw").asDouble();
            double fiftyTwoWeekLow = summaryDetailNode.path("fiftyTwoWeekLow").path("raw").asDouble();
            
            StockInfo stockInfo = StockInfo.builder()
                .ticker(ticker)
                .longName(longName.isEmpty() ? ticker + " Inc." : longName)
                .sector(sector.isEmpty() ? "Technology" : sector)
                .industry(industry.isEmpty() ? "Software" : industry)
                .marketCap(marketCap)
                .peRatio(peRatio > 0 ? BigDecimal.valueOf(peRatio) : null)
                .beta(beta > 0 ? BigDecimal.valueOf(beta) : BigDecimal.valueOf(1.0))
                .fiftyTwoWeekHigh(BigDecimal.valueOf(fiftyTwoWeekHigh))
                .fiftyTwoWeekLow(BigDecimal.valueOf(fiftyTwoWeekLow))
                .lastUpdated(LocalDateTime.now())
                .build();
            
            return Optional.of(stockInfo);
            
        } catch (Exception e) {
            log.error("Error parsing stock info response for ticker: {}", ticker, e);
            return Optional.empty();
        }
    }
    
    private Optional<HistoricalData> parseHistoricalDataResponse(String ticker, String jsonResponse) {
        try {
            JsonNode rootNode = objectMapper.readTree(jsonResponse);
            JsonNode chartNode = rootNode.path("chart");
            
            if (!chartNode.path("error").isNull()) {
                log.warn("Yahoo Finance API error for historical data {}: {}", ticker, chartNode.path("error"));
                return Optional.empty();
            }
            
            JsonNode resultNode = chartNode.path("result").get(0);
            JsonNode timestampsNode = resultNode.path("timestamp");
            JsonNode indicatorsNode = resultNode.path("indicators").path("quote").get(0);
            
            Map<LocalDateTime, HistoricalData.PriceData> priceData = new HashMap<>();
            
            for (int i = 0; i < timestampsNode.size(); i++) {
                long timestamp = timestampsNode.get(i).asLong();
                LocalDateTime dateTime = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());
                
                JsonNode openNode = indicatorsNode.path("open").get(i);
                JsonNode highNode = indicatorsNode.path("high").get(i);
                JsonNode lowNode = indicatorsNode.path("low").get(i);
                JsonNode closeNode = indicatorsNode.path("close").get(i);
                JsonNode volumeNode = indicatorsNode.path("volume").get(i);
                
                if (!openNode.isNull() && !highNode.isNull() && !lowNode.isNull() && !closeNode.isNull()) {
                    HistoricalData.PriceData price = HistoricalData.PriceData.builder()
                        .open(BigDecimal.valueOf(openNode.asDouble()))
                        .high(BigDecimal.valueOf(highNode.asDouble()))
                        .low(BigDecimal.valueOf(lowNode.asDouble()))
                        .close(BigDecimal.valueOf(closeNode.asDouble()))
                        .volume(volumeNode.asLong())
                        .build();
                    
                    priceData.put(dateTime, price);
                }
            }
            
            HistoricalData historicalData = HistoricalData.builder()
                .ticker(ticker)
                .priceData(priceData)
                .build();
            
            return Optional.of(historicalData);
            
        } catch (Exception e) {
            log.error("Error parsing historical data response for ticker: {}", ticker, e);
            return Optional.empty();
        }
    }
    
    // Data transfer classes
    public static class StockQuote implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        private String ticker;
        private BigDecimal price;
        private BigDecimal change;
        private BigDecimal changePercent;
        private Long volume;
        private BigDecimal previousClose;
        private LocalDateTime timestamp;
        
        public static StockQuoteBuilder builder() {
            return new StockQuoteBuilder();
        }
        
        // Getters and setters
        public String getTicker() { return ticker; }
        public void setTicker(String ticker) { this.ticker = ticker; }
        
        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }
        
        public BigDecimal getChange() { return change; }
        public void setChange(BigDecimal change) { this.change = change; }
        
        public BigDecimal getChangePercent() { return changePercent; }
        public void setChangePercent(BigDecimal changePercent) { this.changePercent = changePercent; }
        
        public Long getVolume() { return volume; }
        public void setVolume(Long volume) { this.volume = volume; }
        
        public BigDecimal getPreviousClose() { return previousClose; }
        public void setPreviousClose(BigDecimal previousClose) { this.previousClose = previousClose; }
        
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
        
        public static class StockQuoteBuilder {
            private String ticker;
            private BigDecimal price;
            private BigDecimal change;
            private BigDecimal changePercent;
            private Long volume;
            private BigDecimal previousClose;
            private LocalDateTime timestamp;
            
            public StockQuoteBuilder ticker(String ticker) { this.ticker = ticker; return this; }
            public StockQuoteBuilder price(BigDecimal price) { this.price = price; return this; }
            public StockQuoteBuilder change(BigDecimal change) { this.change = change; return this; }
            public StockQuoteBuilder changePercent(BigDecimal changePercent) { this.changePercent = changePercent; return this; }
            public StockQuoteBuilder volume(Long volume) { this.volume = volume; return this; }
            public StockQuoteBuilder previousClose(BigDecimal previousClose) { this.previousClose = previousClose; return this; }
            public StockQuoteBuilder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }
            
            public StockQuote build() {
                StockQuote quote = new StockQuote();
                quote.ticker = this.ticker;
                quote.price = this.price;
                quote.change = this.change;
                quote.changePercent = this.changePercent;
                quote.volume = this.volume;
                quote.previousClose = this.previousClose;
                quote.timestamp = this.timestamp;
                return quote;
            }
        }
    }
    
    public static class StockInfo implements java.io.Serializable {
        private static final long serialVersionUID = 1L;
        private String ticker;
        private String longName;
        private String sector;
        private String industry;
        private Long marketCap;
        private BigDecimal peRatio;
        private BigDecimal beta;
        private BigDecimal fiftyTwoWeekHigh;
        private BigDecimal fiftyTwoWeekLow;
        private LocalDateTime lastUpdated;
        
        public static StockInfoBuilder builder() {
            return new StockInfoBuilder();
        }
        
        // Getters and setters
        public String getTicker() { return ticker; }
        public void setTicker(String ticker) { this.ticker = ticker; }
        
        public String getLongName() { return longName; }
        public void setLongName(String longName) { this.longName = longName; }
        
        public String getSector() { return sector; }
        public void setSector(String sector) { this.sector = sector; }
        
        public String getIndustry() { return industry; }
        public void setIndustry(String industry) { this.industry = industry; }
        
        public Long getMarketCap() { return marketCap; }
        public void setMarketCap(Long marketCap) { this.marketCap = marketCap; }
        
        public BigDecimal getPeRatio() { return peRatio; }
        public void setPeRatio(BigDecimal peRatio) { this.peRatio = peRatio; }
        
        public BigDecimal getBeta() { return beta; }
        public void setBeta(BigDecimal beta) { this.beta = beta; }
        
        public BigDecimal getFiftyTwoWeekHigh() { return fiftyTwoWeekHigh; }
        public void setFiftyTwoWeekHigh(BigDecimal fiftyTwoWeekHigh) { this.fiftyTwoWeekHigh = fiftyTwoWeekHigh; }
        
        public BigDecimal getFiftyTwoWeekLow() { return fiftyTwoWeekLow; }
        public void setFiftyTwoWeekLow(BigDecimal fiftyTwoWeekLow) { this.fiftyTwoWeekLow = fiftyTwoWeekLow; }
        
        public LocalDateTime getLastUpdated() { return lastUpdated; }
        public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
        
        public static class StockInfoBuilder {
            private String ticker;
            private String longName;
            private String sector;
            private String industry;
            private Long marketCap;
            private BigDecimal peRatio;
            private BigDecimal beta;
            private BigDecimal fiftyTwoWeekHigh;
            private BigDecimal fiftyTwoWeekLow;
            private LocalDateTime lastUpdated;
            
            public StockInfoBuilder ticker(String ticker) { this.ticker = ticker; return this; }
            public StockInfoBuilder longName(String longName) { this.longName = longName; return this; }
            public StockInfoBuilder sector(String sector) { this.sector = sector; return this; }
            public StockInfoBuilder industry(String industry) { this.industry = industry; return this; }
            public StockInfoBuilder marketCap(Long marketCap) { this.marketCap = marketCap; return this; }
            public StockInfoBuilder peRatio(BigDecimal peRatio) { this.peRatio = peRatio; return this; }
            public StockInfoBuilder beta(BigDecimal beta) { this.beta = beta; return this; }
            public StockInfoBuilder fiftyTwoWeekHigh(BigDecimal fiftyTwoWeekHigh) { this.fiftyTwoWeekHigh = fiftyTwoWeekHigh; return this; }
            public StockInfoBuilder fiftyTwoWeekLow(BigDecimal fiftyTwoWeekLow) { this.fiftyTwoWeekLow = fiftyTwoWeekLow; return this; }
            public StockInfoBuilder lastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; return this; }
            
            public StockInfo build() {
                StockInfo info = new StockInfo();
                info.ticker = this.ticker;
                info.longName = this.longName;
                info.sector = this.sector;
                info.industry = this.industry;
                info.marketCap = this.marketCap;
                info.peRatio = this.peRatio;
                info.beta = this.beta;
                info.fiftyTwoWeekHigh = this.fiftyTwoWeekHigh;
                info.fiftyTwoWeekLow = this.fiftyTwoWeekLow;
                info.lastUpdated = this.lastUpdated;
                return info;
            }
        }
    }
    
    public static class HistoricalData {
        private String ticker;
        private Map<LocalDateTime, PriceData> priceData;
        
        public static HistoricalDataBuilder builder() {
            return new HistoricalDataBuilder();
        }
        
        // Getters and setters
        public String getTicker() { return ticker; }
        public void setTicker(String ticker) { this.ticker = ticker; }
        
        public Map<LocalDateTime, PriceData> getPriceData() { return priceData; }
        public void setPriceData(Map<LocalDateTime, PriceData> priceData) { this.priceData = priceData; }
        
        public static class PriceData {
            private BigDecimal open;
            private BigDecimal high;
            private BigDecimal low;
            private BigDecimal close;
            private Long volume;
            
            public static PriceDataBuilder builder() {
                return new PriceDataBuilder();
            }
            
            // Getters and setters
            public BigDecimal getOpen() { return open; }
            public void setOpen(BigDecimal open) { this.open = open; }
            
            public BigDecimal getHigh() { return high; }
            public void setHigh(BigDecimal high) { this.high = high; }
            
            public BigDecimal getLow() { return low; }
            public void setLow(BigDecimal low) { this.low = low; }
            
            public BigDecimal getClose() { return close; }
            public void setClose(BigDecimal close) { this.close = close; }
            
            public Long getVolume() { return volume; }
            public void setVolume(Long volume) { this.volume = volume; }
            
            public static class PriceDataBuilder {
                private BigDecimal open;
                private BigDecimal high;
                private BigDecimal low;
                private BigDecimal close;
                private Long volume;
                
                public PriceDataBuilder open(BigDecimal open) { this.open = open; return this; }
                public PriceDataBuilder high(BigDecimal high) { this.high = high; return this; }
                public PriceDataBuilder low(BigDecimal low) { this.low = low; return this; }
                public PriceDataBuilder close(BigDecimal close) { this.close = close; return this; }
                public PriceDataBuilder volume(Long volume) { this.volume = volume; return this; }
                
                public PriceData build() {
                    PriceData data = new PriceData();
                    data.open = this.open;
                    data.high = this.high;
                    data.low = this.low;
                    data.close = this.close;
                    data.volume = this.volume;
                    return data;
                }
            }
        }
        
        public static class HistoricalDataBuilder {
            private String ticker;
            private Map<LocalDateTime, PriceData> priceData;
            
            public HistoricalDataBuilder ticker(String ticker) { this.ticker = ticker; return this; }
            public HistoricalDataBuilder priceData(Map<LocalDateTime, PriceData> priceData) { this.priceData = priceData; return this; }
            
            public HistoricalData build() {
                HistoricalData data = new HistoricalData();
                data.ticker = this.ticker;
                data.priceData = this.priceData;
                return data;
            }
        }
    }
}