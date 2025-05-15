# StockPulse

A real-time stock market analysis and prediction system using machine learning and sentiment analysis.

## Features

- Real-time stock data collection
- Sentiment analysis of news articles
- Machine learning-based price movement predictions
- Interactive dashboard
- RESTful API backend
- TimescaleDB for time-series data

## Setup

1. Clone the repository:
```bash
git clone https://github.com/yourusername/stockpulse.git
cd stockpulse
```

## Environment Setup

2. Copy the example environment file:
```bash
cp .env.example .env
```

- Get your Finnhub API key from https://finnhub.io/
- Update database credentials if needed
- Adjust other settings as required

3. Start the services:
```bash
docker-compose up --build
```

## Services

- Dashboard: http://localhost:8050
- Backend API: http://localhost:8000
- Frontend: http://localhost:3000
- Database: localhost:5432

## Development

- Python 3.10
- FastAPI
- Dash
- TimescaleDB
- Docker
