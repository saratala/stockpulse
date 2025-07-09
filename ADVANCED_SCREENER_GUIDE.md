# StockPulse Advanced Screener & Signal Detection System

## Overview

The StockPulse application has been enhanced with a sophisticated signal detection pipeline that implements professional-grade stock screening and automated trading signal generation. This system provides modular, maintainable components that can be used standalone or integrated with your existing infrastructure.

## 🏗️ Architecture

The advanced system follows a modular microservices-style architecture:

### Core Components

1. **Screener Module** (`screener_module.py`)
   - Filters stocks based on technical and fundamental criteria
   - EMA stack analysis (8 > 13 > 21 > 34 > 55 > 89)
   - ADX > 20 for trending markets
   - Stochastic %K < 40 for oversold opportunities
   - Market cap > $100B for liquid, optionable stocks

2. **Enhanced Data Fetcher** (`enhanced_data_fetcher.py`)
   - Comprehensive technical indicator calculation
   - Market regime detection
   - Key level identification
   - Volume profile analysis

3. **Heikin Ashi Signal Detection** (`heikin_ashi_signals.py`)
   - Advanced signal detection using Heikin Ashi candles
   - RSI confluence analysis
   - Price position relative to EMA(21)
   - Confidence scoring and signal strength classification

4. **Daily Scheduler** (`daily_scheduler.py`)
   - Automated daily screening before market open
   - Cloud-ready scheduling (GCP, AWS Lambda compatible)
   - Multi-channel notifications
   - Result storage and historical tracking

5. **Notification & Logging System** (`notification_logger.py`)
   - Multi-channel notifications (Email, Slack, Discord, Telegram)
   - Comprehensive logging with rotation
   - Performance monitoring
   - Error tracking and alerting

## 🚀 Quick Start

### 1. Environment Setup

Create a `.env` file with your configuration:

```bash
# Database
DATABASE_URL=postgresql://user:password@localhost/stockpulse

# Email Notifications
SMTP_SERVER=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your_email@gmail.com
SMTP_PASSWORD=your_app_password
NOTIFICATION_EMAIL=alerts@yourdomain.com

# Slack Integration
SLACK_WEBHOOK=https://hooks.slack.com/services/YOUR/SLACK/WEBHOOK
SLACK_CHANNEL=#stock-alerts

# Discord Integration
DISCORD_WEBHOOK=https://discord.com/api/webhooks/YOUR/DISCORD/WEBHOOK

# Telegram Integration
TELEGRAM_BOT_TOKEN=your_bot_token
TELEGRAM_CHAT_ID=your_chat_id

# Screening Configuration
MIN_SCREENING_SCORE=70
MAX_STOCKS_TO_ANALYZE=100
```

### 2. Install Dependencies

```bash
pip install -r requirements.txt
```

### 3. Start the Enhanced StockPulse

```bash
# Start the FastAPI server
uvicorn main:app --reload --port 8000

# Or use Docker
docker-compose up
```

## 📊 API Endpoints

### New Screening Endpoints

#### 1. Run Stock Screener
```
GET /screener/run?min_score=70&max_results=50&include_signals=true
```

**Response:**
```json
{
  "screening_date": "2025-01-08 10:30:00",
  "total_analyzed": 120,
  "candidates_found": 15,
  "screening_summary": {
    "ema_stack": 45,
    "momentum": 38,
    "volume": 52,
    "fundamental": 89
  },
  "candidates": [
    {
      "ticker": "AAPL",
      "name": "Apple Inc.",
      "screening_score": 85,
      "current_price": 185.50,
      "passes_ema_stack": true,
      "signal_analysis": {
        "primary_signal": "BULLISH",
        "primary_confidence": 78
      }
    }
  ]
}
```

#### 2. Heikin Ashi Signals
```
GET /screener/signals?tickers=AAPL,MSFT,GOOGL&min_confidence=60
```

**Response:**
```json
{
  "analysis_date": "2025-01-08 10:30:00",
  "total_analyzed": 3,
  "signals_found": 2,
  "summary": {
    "bullish_signals": 1,
    "bearish_signals": 1,
    "high_confidence_signals": 2
  },
  "signals": [
    {
      "ticker": "AAPL",
      "primary_signal": "BULLISH",
      "primary_confidence": 78,
      "primary_reasons": [
        "Strong bullish Heikin Ashi candle",
        "Price above EMA21",
        "Volume confirmation: 1.8x"
      ]
    }
  ]
}
```

#### 3. Comprehensive Analysis
```
GET /screener/comprehensive/AAPL
```

Returns detailed technical analysis including:
- All technical indicators
- Market regime analysis
- Key support/resistance levels
- Volume profile
- Heikin Ashi analysis
- Screening scores
- Signal detection results

#### 4. Market Regime Analysis
```
GET /screener/market-regime/AAPL
```

#### 5. Daily Screening Results
```
GET /screener/daily-results
```

#### 6. Manual Daily Run
```
POST /screener/run-daily
```

## 🔄 Automated Daily Scheduling

### Local Scheduling (Cron)

```bash
# Run daily at 7:00 AM EST
python daily_scheduler.py --schedule

# Run once manually
python daily_scheduler.py --run-once
```

### Cloud Deployment

#### Google Cloud Functions
```yaml
# cloudbuild.yaml
steps:
- name: 'gcr.io/cloud-builders/gcloud'
  args: ['functions', 'deploy', 'stockpulse-screener',
         '--runtime', 'python39',
         '--trigger-topic', 'stockpulse-trigger',
         '--entry-point', 'cloud_function_handler']
```

#### AWS Lambda
```python
# Deploy using AWS SAM or Serverless Framework
# The cloud_function_handler in daily_scheduler.py is AWS Lambda compatible
```

#### Google Cloud Scheduler
```bash
gcloud scheduler jobs create pubsub stockpulse-daily \
    --schedule="0 11 * * 1-5" \
    --time-zone="America/New_York" \
    --topic="stockpulse-trigger" \
    --message-body="{}"
```

## 🎯 Signal Logic Deep Dive

### Screening Criteria

**EMA Stack Filter:**
- Requires: EMA(8) > EMA(13) > EMA(21) > EMA(34) > EMA(55) > EMA(89)
- Indicates strong bullish trend alignment

**Momentum Filter:**
- ADX > 20: Strong trending market
- Stochastic %K < 40: Oversold opportunity
- RSI 30-70: Healthy momentum range

**Volume Filter:**
- Volume > 1.0x average: Institutional interest
- Average volume > 1M: Sufficient liquidity

**Fundamental Filter:**
- Market cap > $100B: Large cap stability
- Optionable stocks only: Professional trading focus

### Heikin Ashi Signal Detection

**Bullish Signal Requirements:**
- Strong bullish Heikin Ashi candle
- RSI > 30 (not oversold)
- Price within ±1 ATR of EMA(21)
- Volume confirmation
- Consecutive bullish momentum

**Confidence Scoring:**
- 80-100%: VERY_STRONG
- 60-79%: STRONG  
- 40-59%: MODERATE
- 20-39%: WEAK
- 0-19%: VERY_WEAK

## 📈 Usage Examples

### 1. Daily Pre-Market Screening

```python
from daily_scheduler import DailyScheduler

scheduler = DailyScheduler()

# Run complete daily pipeline
results = scheduler.run_once()

print(f"Found {len(results['high_quality_signals'])} high-quality signals")
```

### 2. Custom Stock Analysis

```python
from screener_module import StockScreener
from heikin_ashi_signals import HeikinAshiSignalDetector

screener = StockScreener()
detector = HeikinAshiSignalDetector()

# Screen custom list
custom_tickers = ['AAPL', 'MSFT', 'GOOGL']
results = screener.run_screening(custom_tickers)

# Analyze signals
signals = detector.scan_multiple_stocks(custom_tickers)
```

### 3. Real-time Monitoring

```python
from notification_logger import NotificationLogger

logger = NotificationLogger()

# Send alerts for high-confidence signals
for signal in high_confidence_signals:
    if signal['primary_confidence'] >= 80:
        logger.send_alert_notification(
            signal['primary_signal'].lower(),
            signal['ticker'],
            signal
        )
```

## 📊 Performance Monitoring

### Logging Capabilities

- **Screening Results**: Structured logging of all screening outcomes
- **Signal Detection**: Detailed signal analysis logs
- **Performance Metrics**: Operation timing and efficiency
- **Error Tracking**: Comprehensive error logging with context
- **Notification History**: Track all sent notifications

### Log Files (in `/logs/` directory)

- `stockpulse.log`: Main application log
- `screening_results.log`: Screening outcome history
- `signals.log`: Signal detection results
- `errors.log`: Error tracking and debugging
- `performance.log`: Performance metrics

## 🔔 Notification Channels

### Email Notifications
- Daily summary reports
- High-confidence signal alerts
- Error notifications

### Slack Integration
- Real-time signal alerts
- Daily summaries
- System status updates

### Discord Notifications
- Trading signal alerts
- Performance reports

### Telegram Alerts
- Instant high-confidence signals
- Daily summaries

## 🛠️ Customization

### Screening Criteria

Modify screening thresholds in `screener_module.py`:

```python
def passes_momentum_screen(self, df: pd.DataFrame) -> bool:
    latest = df.iloc[-1]
    
    # Customize these thresholds
    adx_threshold = 25  # Default: 20
    stoch_threshold = 35  # Default: 40
    rsi_min, rsi_max = 25, 75  # Default: 30, 70
```

### Signal Confidence

Adjust confidence scoring in `heikin_ashi_signals.py`:

```python
def is_bullish_signal(self, df: pd.DataFrame) -> Dict:
    # Modify confidence weights
    if last['HA_Strong_Bull']:
        confidence += 30  # Default: 25
    
    if rsi_healthy:
        confidence += 20  # Default: 15
```

### Notification Thresholds

Configure alert sensitivity:

```python
# In daily_scheduler.py
def send_alert_notification(self, alert_type: str, ticker: str, signal_data: Dict):
    if signal_data.get('primary_confidence', 0) < 75:  # Default: 80
        return  # Lower threshold for more alerts
```

## 📋 Testing

### Unit Tests

```bash
# Test individual modules
python screener_module.py
python heikin_ashi_signals.py
python enhanced_data_fetcher.py
```

### Integration Tests

```bash
# Test complete pipeline
python daily_scheduler.py --run-once
```

### API Testing

```bash
# Test new endpoints
curl "http://localhost:8000/screener/run?min_score=70"
curl "http://localhost:8000/screener/signals?tickers=AAPL,MSFT"
curl "http://localhost:8000/screener/comprehensive/AAPL"
```

## 🚨 Troubleshooting

### Common Issues

1. **No data returned**: Check yfinance connectivity and ticker validity
2. **Email not sending**: Verify SMTP credentials and app passwords
3. **Slack notifications failing**: Confirm webhook URL and permissions
4. **Database errors**: Check DATABASE_URL and connection
5. **Import errors**: Ensure all dependencies are installed

### Debug Mode

Enable detailed logging:

```bash
export LOG_LEVEL=DEBUG
python daily_scheduler.py --run-once
```

### Log Analysis

Check specific log files for issues:

```bash
tail -f logs/errors.log
tail -f logs/performance.log
tail -f logs/signals.log
```

## 🔗 Integration with Existing StockPulse

The new system seamlessly integrates with existing StockPulse functionality:

- **Database**: Uses same PostgreSQL schema
- **API**: Extends existing FastAPI endpoints
- **Frontend**: Can be integrated with React dashboard
- **Docker**: Compatible with existing Docker setup

## 📈 Next Steps

Potential enhancements:

1. **Real-time WebSocket feeds** for live signal updates
2. **Machine learning models** for signal validation
3. **Backtesting framework** for strategy validation
4. **Risk management** integration
5. **Broker API integration** for automated execution
6. **Mobile app notifications**
7. **Advanced chart patterns** detection
8. **Sentiment analysis** integration

## 📞 Support

For issues or questions:

1. Check the logs in `/logs/` directory
2. Review error messages in `errors.log`
3. Test individual modules in isolation
4. Verify environment configuration
5. Check API endpoint responses

The system is designed to be production-ready with comprehensive error handling, logging, and monitoring capabilities.