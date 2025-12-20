# LLM Sentiment Analysis Integration Guide

## Overview

This guide explains the integration of LLM-powered sentiment analysis into the StockPulse prediction engine. The system now combines advanced technical analysis with real-time news sentiment to generate more accurate trading signals.

## Architecture Components

### 1. MarketSentimentAnalyzer (`llm_sentiment_analyzer.py`)
- **Purpose**: Core LLM-powered sentiment analysis engine
- **Model**: OpenAI GPT-3.5-turbo with financial context prompting
- **Features**:
  - Context-aware sentiment scoring (-1.0 to 1.0)
  - Confidence assessment (0.0 to 1.0)
  - Market impact prediction (immediate, short-term, long-term, negligible)
  - Key topic extraction
  - Batch processing with rate limiting
  - Fallback to traditional sentiment analysis

### 2. NewsDataFetcher (`llm_sentiment_analyzer.py`)
- **Purpose**: Fetches real-time financial news
- **Sources**: 
  - Yahoo Finance news API (primary)
  - Alpha Vantage API (future)
  - Finnhub API (future)
- **Features**:
  - Ticker-specific news fetching
  - Market-wide news aggregation
  - Time-based filtering
  - Content preprocessing

### 3. Advanced Scheduler Integration (`advanced_scheduler.py`)
- **Purpose**: Integrates sentiment analysis into prediction pipeline
- **Enhancements**:
  - Sentiment-adjusted confidence scoring
  - Sentiment-influenced price predictions
  - Sentiment boost/penalty for screening scores
  - Real-time sentiment tracking

## Database Schema Updates

### New Fields in `signal_predictions` Table:
```sql
sentiment_score DOUBLE PRECISION DEFAULT 0,         -- -1.0 to 1.0
sentiment_confidence DOUBLE PRECISION DEFAULT 0,    -- 0.0 to 1.0
sentiment_impact TEXT DEFAULT 'negligible',         -- immediate, short-term, long-term, negligible
news_count INTEGER DEFAULT 0,                       -- Number of news items analyzed
```

### Constraints:
- `sentiment_score_range`: -1.0 ≤ sentiment_score ≤ 1.0
- `sentiment_confidence_range`: 0.0 ≤ sentiment_confidence ≤ 1.0
- `sentiment_impact_values`: IN ('immediate', 'short-term', 'long-term', 'negligible')
- `news_count_positive`: news_count ≥ 0

## Integration Flow

### 1. Data Collection (Every 5 minutes)
```
Advanced Scheduler → Stock Screener → Technical Analysis → News Fetching → Sentiment Analysis → Prediction Generation → Database Storage
```

### 2. Sentiment Analysis Process
```
Ticker → Fetch Recent News → Preprocess Content → LLM Analysis → Sentiment Signals → Weighted Aggregation → Confidence Assessment
```

### 3. Prediction Enhancement
```
Technical Signal + Sentiment Data → Confidence Adjustment → Price Prediction Modification → Screening Score Boost/Penalty
```

## API Response Format

### Enhanced Prediction History Response:
```json
{
  "ticker": "AAPL",
  "timestamp": "2024-01-15 10:30:00",
  "signal_type": "BULLISH",
  "confidence": 78.5,
  "screening_score": 82.3,
  "sentiment_score": 0.45,
  "sentiment_confidence": 0.78,
  "sentiment_impact": "short-term",
  "news_count": 5,
  "predicted_price_1h": 185.67,
  "predicted_price_1d": 188.42,
  "predicted_price_1w": 192.18
}
```

## Configuration

### Required Environment Variables:
```bash
# OpenAI API for LLM sentiment analysis
OPENAI_API_KEY=your_openai_api_key_here

# Optional: Alternative news sources
FINNHUB_API_KEY=your_finnhub_api_key_here
ALPHA_VANTAGE_API_KEY=your_alpha_vantage_api_key_here
```

### Dependencies:
```
openai>=1.0.0
aiohttp
textblob
yfinance
```

## Usage Examples

### 1. Manual Sentiment Analysis
```python
from llm_sentiment_analyzer import MarketSentimentAnalyzer

analyzer = MarketSentimentAnalyzer()
context = {
    'ticker': 'AAPL',
    'market_conditions': 'neutral',
    'sector': 'Technology'
}

texts = ["Apple reports strong earnings..."]
signals = await analyzer.analyze_batch(texts, context)
```

### 2. Testing Integration
```bash
cd backend
python test_sentiment_integration.py
```

## Sentiment Impact on Predictions

### Confidence Adjustment Logic:
- **Bullish Technical + Positive Sentiment**: Confidence boost up to +15%
- **Bearish Technical + Negative Sentiment**: Confidence boost up to +15%
- **Bullish Technical + Negative Sentiment**: Confidence reduction up to -10%
- **Bearish Technical + Positive Sentiment**: Confidence reduction up to -10%

### Price Prediction Modifications:
- **High Confidence Sentiment (>0.7)**: 30% impact on price movements
- **Medium Confidence Sentiment (0.5-0.7)**: 20% impact on price movements
- **Low Confidence Sentiment (<0.5)**: 10% impact on price movements

### Screening Score Adjustments:
- **Positive Sentiment**: +5 to +15 points based on confidence
- **Negative Sentiment**: -5 to -15 points based on confidence
- **Neutral/Low Confidence**: No adjustment

## Performance Monitoring

### Key Metrics:
- **Sentiment Analysis Coverage**: % of tickers with sentiment data
- **News Fetch Success Rate**: % of successful news retrievals
- **LLM Response Time**: Average time for sentiment analysis
- **Prediction Accuracy**: Backtesting with vs. without sentiment

### Logging:
- Sentiment analysis results logged for each ticker
- Error handling for API failures
- Performance metrics tracked

## Error Handling

### Fallback Strategies:
1. **LLM API Failure**: Falls back to TextBlob sentiment analysis
2. **News Fetch Failure**: Continues with technical analysis only
3. **Rate Limiting**: Implements exponential backoff
4. **Cache Misses**: Graceful degradation with warning logs

## Future Enhancements

### Planned Features:
1. **Multi-Model Ensemble**: Combine multiple LLM providers
2. **Sector-Specific Analysis**: Tailored prompts for different sectors
3. **Real-Time News Streaming**: WebSocket-based news feeds
4. **Sentiment Backtesting**: Historical performance analysis
5. **Custom Model Fine-Tuning**: Domain-specific model training

### Additional Data Sources:
- Social media sentiment (Twitter, Reddit)
- Analyst reports and upgrades/downgrades
- Options flow and unusual activity
- Insider trading notifications

## Troubleshooting

### Common Issues:

1. **No Sentiment Data**:
   - Check OPENAI_API_KEY environment variable
   - Verify internet connection
   - Check OpenAI API quota

2. **Low News Count**:
   - Verify yfinance library is working
   - Check if ticker has recent news
   - Adjust hours_back parameter

3. **Sentiment Analysis Errors**:
   - Monitor OpenAI API rate limits
   - Check prompt formatting
   - Verify model availability

4. **Performance Issues**:
   - Adjust batch size for LLM calls
   - Implement caching for repeated analysis
   - Optimize database queries

### Debugging Commands:
```bash
# Test sentiment analysis
python test_sentiment_integration.py

# Check API connectivity
python -c "from llm_sentiment_analyzer import MarketSentimentAnalyzer; print('API loaded')"

# Validate database schema
psql -d stockpulse -c "\\d signal_predictions"
```

## Best Practices

### 1. API Usage:
- Implement proper rate limiting
- Cache results to minimize API calls
- Handle API failures gracefully
- Monitor usage quotas

### 2. Data Quality:
- Validate news content before analysis
- Filter out duplicate news items
- Clean and preprocess text data
- Verify timestamp accuracy

### 3. Performance:
- Use batch processing when possible
- Implement async operations
- Monitor memory usage
- Log performance metrics

### 4. Security:
- Secure API keys in environment variables
- Implement proper error handling
- Validate user inputs
- Monitor for suspicious activity

## Conclusion

The LLM sentiment analysis integration significantly enhances the StockPulse prediction engine by incorporating real-time market sentiment into technical analysis. This multi-dimensional approach provides more nuanced and accurate trading signals while maintaining the robustness of the existing technical analysis framework.

The system is designed to be resilient, scalable, and maintainable, with comprehensive error handling and fallback mechanisms to ensure continuous operation even when external services are unavailable.