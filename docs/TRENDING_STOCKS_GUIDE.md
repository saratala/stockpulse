# StockPulse Weekly Trending Stocks Guide

This document explains the new automated trending stocks analysis functionality added to StockPulse.

## 🚀 Features Added

### 1. Weekly Trending Analysis API
- **Endpoint**: `GET /trending/weekly`
- **Description**: Analyzes stocks for weekly trending patterns using multiple factors
- **Factors Analyzed**:
  - Price momentum (5-day and 7-day returns)
  - Volume spikes compared to 20-day average
  - Technical indicator strength (RSI, MACD, ADX)
  - Recent sentiment scores from news analysis

### 2. Market Movers API
- **Endpoint**: `GET /trending/movers`
- **Description**: Get top weekly gainers and losers
- **Returns**: Top 10 gainers and top 10 losers for the week

### 3. Automated Trading Signals
- **File**: `trending_signals.py`
- **Description**: Standalone script for generating automated trading signals
- **Signals Generated**: STRONG_BUY, BUY, HOLD, SELL, STRONG_SELL

## 📊 Trending Score Algorithm

The trending score is calculated using a weighted approach:

### Price Momentum (70% weight)
- **5-day returns** (40% weight): `GREATEST(0, LEAST(40, return_5d * 2))`
- **7-day returns** (30% weight): `GREATEST(0, LEAST(30, return_7d * 1.5))`

### Volume Analysis (20% weight)
- **Volume spike**: `GREATEST(0, LEAST(20, (volume_ratio - 1) * 10))`
- Compares current volume to 20-day average

### Technical Strength (25% weight)
- **RSI and MACD** (15% weight): Healthy RSI (40-70) + bullish MACD crossover
- **ADX** (10% weight): Strong trend when ADX > 25

### Sentiment Boost (15% weight)
- **Positive sentiment** (15% weight): Based on recent news sentiment analysis
- Ranges from 0-15 points based on sentiment score

### Trend Strength Categories
- **Very Hot**: Score ≥ 80
- **Hot**: Score ≥ 60
- **Trending**: Score ≥ 40
- **Moderate**: Score ≥ 20
- **Weak**: Score < 20

## 🔧 Technical Indicators Used

### Momentum Indicators
- **RSI (Relative Strength Index)**: Measures overbought/oversold conditions
- **MACD**: Moving Average Convergence Divergence for trend changes
- **Price Returns**: 1-day, 5-day, and 10-day percentage changes

### Trend Indicators
- **Moving Averages**: SMA 10, 20, 50 for trend direction
- **ADX**: Average Directional Index for trend strength
- **Bollinger Bands**: Price volatility and potential reversal points

### Volume Indicators
- **Volume Ratio**: Current volume vs 20-day average
- **Volume Spikes**: Unusual volume activity detection

## 🚀 Usage Examples

### 1. Get Weekly Trending Stocks
```bash
curl http://localhost:8000/trending/weekly
```

**Response Example**:
```json
{
  "report_date": "2025-07-08 12:00:00",
  "total_stocks_analyzed": 45,
  "trending_stocks": [
    {
      "ticker": "NVDA",
      "name": "NVIDIA Corporation",
      "sector": "Technology",
      "current_price": 892.15,
      "return_5d_percent": 8.45,
      "return_7d_percent": 12.30,
      "volume_ratio": 2.1,
      "rsi": 65.2,
      "trending_score": 87.5,
      "trend_strength": "Very Hot"
    }
  ],
  "summary": {
    "very_hot": 3,
    "hot": 8,
    "trending": 15,
    "top_sectors": {
      "Technology": 72.5,
      "Healthcare": 58.3
    }
  }
}
```

### 2. Get Market Movers
```bash
curl http://localhost:8000/trending/movers
```

### 3. Run Automated Signals Analysis
```bash
cd /Users/saratala/Projects/stockpulse/backend
python trending_signals.py
```

### 4. Test the Functionality
```bash
cd /Users/saratala/Projects/stockpulse/backend
python test_trending.py
```

## 📈 Signal Generation Logic

### Bullish Signals (Points System)
- **Strong 5-day momentum** (+5%): +25 points
- **Price above rising MAs**: +20 points
- **Healthy RSI** (30-70): +15 points
- **MACD bullish crossover**: +20 points
- **High volume confirmation** (>1.5x): +20 points

### Bearish Signals (Points System)
- **Weak 5-day momentum** (-5%): +25 points
- **Price below declining MAs**: +20 points
- **Extreme RSI** (<20 or >80): +15 points
- **MACD bearish crossover**: +20 points

### Signal Categories
- **STRONG_BUY**: Net score ≥ 40
- **BUY**: Net score ≥ 20
- **HOLD**: Net score -20 to 20
- **SELL**: Net score ≤ -20
- **STRONG_SELL**: Net score ≤ -40

## 🛠 Installation & Setup

### 1. Start StockPulse
```bash
cd /Users/saratala/Projects/stockpulse
docker-compose up -d
```

### 2. Test the New Endpoints
```bash
# Check if server is running
curl http://localhost:8000/health

# Get weekly trending stocks
curl http://localhost:8000/trending/weekly

# Get market movers
curl http://localhost:8000/trending/movers
```

### 3. Run Standalone Analysis
```bash
cd /Users/saratala/Projects/stockpulse/backend
python trending_signals.py
```

## 📊 Database Requirements

The trending analysis requires the following tables:
- `stocks`: Basic stock information
- `stock_prices`: Historical OHLCV data
- `technicals`: Technical indicators
- `sentiment_scores`: News sentiment analysis

Ensure your database has recent data (within 30 days) for accurate trending analysis.

## 🔍 Monitoring & Performance

### Key Metrics to Monitor
- API response times for trending endpoints
- Database query performance
- Data freshness (last update timestamps)
- Signal accuracy over time

### Performance Optimizations
- Use database indexes on ticker and date columns
- Cache trending results for 15-30 minutes
- Limit analysis to liquid stocks (price > $1, volume > threshold)
- Use connection pooling for database access

## 🐛 Troubleshooting

### Common Issues

1. **No trending stocks returned**
   - Check if stock_prices table has recent data
   - Verify database connection
   - Ensure stocks meet minimum criteria (price > $1)

2. **API timeout errors**
   - Reduce LIMIT in SQL queries
   - Add database indexes
   - Optimize query performance

3. **Missing technical indicators**
   - Run ETL process to populate technicals table
   - Check if technical calculation scripts are working

### Debug Commands
```bash
# Test database connection
python -c "from trending_signals import TrendingStockSignals; t = TrendingStockSignals(); print('DB connected' if t.engine else 'No DB')"

# Test with specific stock
python -c "from trending_signals import TrendingStockSignals; print(TrendingStockSignals().get_trending_stocks_with_signals(['AAPL']))"
```

## 📝 Next Steps

1. **Enhanced Filtering**: Add sector/industry filters
2. **Real-time Updates**: Implement WebSocket for live updates
3. **Backtesting**: Add historical signal performance analysis
4. **Alerts**: Email/SMS notifications for strong signals
5. **Portfolio Integration**: Connect with paper trading functionality

## 🤝 Contributing

To extend the trending analysis:

1. Add new technical indicators in `trending_signals.py`
2. Modify scoring algorithm in `get_weekly_trending_stocks()` 
3. Add new API endpoints in `main.py`
4. Update tests in `test_trending.py`

---

For questions or issues, check the StockPulse main README or create an issue in the project repository.