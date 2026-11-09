# StockPulse

A comprehensive real-time stock market analysis platform built with Java Spring Boot, combining AI-powered sentiment analysis, technical indicators, and advanced screening to help traders make informed decisions.

## 🚀 Features

### Core Functionality
- **Real-time Stock Data**: Continuous price updates every 5 minutes with historical data tracking
- **AI-Powered Sentiment Analysis**: Advanced LLM-based sentiment analysis of news articles with trend tracking
- **Advanced Signal Detection**: Technical analysis-based signal detection with confidence scoring
- **Advanced Stock Screener**: AI-powered signal detection with confidence scoring and grading system
- **Technical Analysis**: Comprehensive technical indicators including RSI, MACD, Bollinger Bands, and Heikin Ashi signals
- **Interactive Dashboard**: Real-time system monitoring with glassmorphism UI design

### Key Components
- **Java Spring Boot Backend**: Robust enterprise-grade backend with comprehensive REST APIs
- **Automated Schedulers**: 
  - Price updates (5 minutes)
  - Sentiment analysis (10 minutes)
  - News fetching (2 hours)
  - Stock screening (15 minutes)
  - Signal generation (30 minutes)
- **Data Persistence**: TimescaleDB for time-series data with Redis caching
- **Responsive Frontend**: React with TypeScript, Tailwind CSS, and Framer Motion animations

## 🛠️ Technology Stack

### Backend
- **Java Backend**: Spring Boot 3.2, Java 21, PostgreSQL, Redis
- **Database**: TimescaleDB (PostgreSQL extension) for time-series data
- **Caching**: Redis for performance optimization
- **Technical Analysis**: TA4J library for comprehensive technical indicators
- **External APIs**: Yahoo Finance, News APIs, LLM services

### Frontend
- **Framework**: React 18 with TypeScript
- **Styling**: Tailwind CSS with custom glassmorphism design
- **Charts**: Recharts for data visualization
- **Animations**: Framer Motion and React Spring
- **Icons**: Lucide React

## 📊 API Endpoints

### Java Backend (Port 8181)
- **Health**: `/api/v1/health` - System health check
- **Stocks**: `/api/v1/stocks` - Stock data and prices
- **Screener**: `/api/v1/screener` - AI-powered stock screening
- **Predictions**: `/api/v1/predictions` - Technical analysis-based predictions
- **Sentiment**: `/api/v1/sentiment-analysis` - LLM sentiment analysis
- **Data Updates**: `/api/v1/data` - Manual data refresh triggers
- **Trading**: `/api/v1/trading` - Alpaca paper-trading auto-trader (off by default, see `docs/PAPER_TRADING.md`)

## 🚀 Quick Start

### Prerequisites
- Docker and Docker Compose
- Git

### Installation

1. **Clone the repository**:
```bash
git clone https://github.com/saratala/stockpulse.git
cd stockpulse
```

2. **Environment Setup**:
```bash
cp .env.example .env
```

Configure the following in `.env`:
- `FINNHUB_API_KEY`: Get from https://finnhub.io/
- `OPENAI_API_KEY`: For LLM sentiment analysis
- Database credentials (default provided)
- Other API keys as needed

3. **Start all services**:
```bash
docker-compose up --build
```

## 📡 Service URLs

- **Frontend**: http://localhost:3010
- **Java API**: http://localhost:8181/api/v1
- **Database**: localhost:5432
- **Redis**: localhost:6379

## 📱 Frontend Pages

1. **Dashboard** (`/`): System overview with quick actions and real-time status
2. **Stock Screener** (`/screener`): AI-powered stock screening with signal detection
3. **LLM Sentiment** (`/llm-sentiment`): Advanced sentiment analysis dashboard
4. **Predictions History** (`/predictions-history`): Historical prediction tracking
5. **Stock Analysis** (`/analysis/:ticker`): Comprehensive individual stock analysis
6. **Stock Details** (`/stock/:ticker`): Simple sentiment view for individual stocks

## 🔧 Development

### Running Individual Services

```bash
# Frontend development
cd frontend
npm install
npm start

# Java backend
cd java-backend
./gradlew bootRun
```

### Architecture Notes
- **Frontend** connects to Java backend on port 8181
- **Java backend** serves all APIs and handles business logic
- **All services** run in Docker containers for consistency
- **Data flow**: External APIs → Java Backend → TimescaleDB → Redis Cache → Frontend

## 📈 Features in Detail

### Stock Screening
- AI-powered signal detection (Bullish/Bearish/Neutral)
- Confidence scoring (0-100%)
- Letter grade system (A+, A, B+, etc.)
- Technical indicator integration
- Real-time filtering and search

### Sentiment Analysis
- LLM-enhanced sentiment analysis
- 7-day trend visualization
- News article aggregation
- Sentiment impact on signal generation

### Technical Analysis
- 20+ technical indicators
- Heikin Ashi candle analysis
- Support/resistance levels
- Moving average crossovers
- Volume analysis

## 🔒 Security & Performance

- Redis caching for API responses
- Rate limiting on external API calls
- Secure credential management via environment variables
- Health checks for all services
- Automatic service recovery

## 📚 Additional Documentation

- [Advanced Screener Guide](./ADVANCED_SCREENER_GUIDE.md)
- [LLM Sentiment Integration](./LLM_SENTIMENT_INTEGRATION_GUIDE.md)
- [Frontend Implementation](./FRONTEND_IMPLEMENTATION_GUIDE.md)
- [Docker Instructions](./DOCKER_RUN_INSTRUCTIONS.md)
- [Development Context](./CLAUDE.md)

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.
