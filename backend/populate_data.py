#!/usr/bin/env python3
"""
Populate database with stock data for demonstration
"""

import yfinance as yf
import pandas as pd
from sqlalchemy import create_engine, text
from datetime import datetime, timedelta
import os
from dotenv import load_dotenv
import logging

# Load environment variables
load_dotenv()

# Set up logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Database connection
DATABASE_URL = os.getenv("DATABASE_URL", "postgresql://postgres:postgres@db:5432/stockpulse")
engine = create_engine(DATABASE_URL)

def get_existing_tickers():
    """Get existing tickers from the database"""
    with engine.connect() as conn:
        result = conn.execute(text("SELECT ticker FROM stocks"))
        return [row[0] for row in result]

def populate_stocks_table():
    """Populate the stocks table with ticker symbols"""
    logger.info("Populating stocks table...")
    
    stocks_data = []
    for ticker in POPULAR_TICKERS:
        try:
            stock = yf.Ticker(ticker)
            info = stock.info
            
            stocks_data.append({
                'ticker': ticker,
                'name': info.get('longName', f'{ticker} Inc.'),
                'sector': info.get('sector', 'Technology'),
                'industry': info.get('industry', 'Software')
            })
            
        except Exception as e:
            logger.warning(f"Error getting info for {ticker}: {e}")
            stocks_data.append({
                'ticker': ticker,
                'name': f'{ticker} Inc.',
                'sector': 'Technology',
                'industry': 'Software'
            })
    
    # Insert into database
    df = pd.DataFrame(stocks_data)
    df.to_sql('stocks', engine, if_exists='append', index=False)
    logger.info(f"Inserted {len(stocks_data)} stocks into database")

def populate_stock_prices():
    """Populate stock_prices table with recent data"""
    logger.info("Populating stock prices...")
    
    tickers = get_existing_tickers()
    all_prices = []
    
    for ticker in tickers:
        try:
            logger.info(f"Fetching price data for {ticker}...")
            stock = yf.Ticker(ticker)
            
            # Get last 30 days of data
            hist = stock.history(period="30d")
            
            for date, row in hist.iterrows():
                all_prices.append({
                    'ticker': ticker,
                    'date': date.date(),
                    'open': float(row['Open']),
                    'high': float(row['High']),
                    'low': float(row['Low']),
                    'close': float(row['Close']),
                    'volume': int(row['Volume'])
                })
                
        except Exception as e:
            logger.error(f"Error fetching price data for {ticker}: {e}")
            continue
    
    # Insert into database in chunks
    df = pd.DataFrame(all_prices)
    if not df.empty:
        df.to_sql('stock_prices', engine, if_exists='append', index=False)
        logger.info(f"Inserted {len(all_prices)} price records into database")

def populate_predictions():
    """Populate predictions table with sample data"""
    logger.info("Populating predictions table...")
    
    tickers = get_existing_tickers()
    predictions = []
    
    for ticker in tickers:
        try:
            # Get current price
            stock = yf.Ticker(ticker)
            current_price = stock.history(period="1d")['Close'].iloc[-1]
            
            # Generate sample prediction
            import random
            
            signal_types = ['BULLISH', 'BEARISH', 'NEUTRAL']
            signal_type = random.choice(signal_types)
            
            predictions.append({
                'ticker': ticker,
                'timestamp': datetime.now(),
                'signal_type': signal_type,
                'confidence': round(random.uniform(60, 90), 1),
                'current_price': float(current_price),
                'predicted_price_1h': float(current_price * random.uniform(0.99, 1.01)),
                'predicted_price_1d': float(current_price * random.uniform(0.95, 1.05)),
                'predicted_price_1w': float(current_price * random.uniform(0.90, 1.10)),
                'volume': random.randint(1000000, 50000000),
                'rsi': round(random.uniform(30, 70), 1),
                'macd': round(random.uniform(-2, 2), 4),
                'bollinger_position': round(random.uniform(0, 1), 2),
                'screening_score': round(random.uniform(70, 95), 1),
                'sector': random.choice(['Technology', 'Healthcare', 'Finance', 'Energy']),
                'primary_reasons': [
                    'Strong technical momentum',
                    'Positive sentiment analysis',
                    'Volume confirmation'
                ],
                'sentiment_score': round(random.uniform(-0.5, 0.5), 3),
                'sentiment_confidence': round(random.uniform(0.4, 0.9), 2),
                'sentiment_impact': random.choice(['immediate', 'short-term', 'long-term', 'negligible']),
                'news_count': random.randint(1, 25),
                'created_at': datetime.now()
            })
            
        except Exception as e:
            logger.error(f"Error creating prediction for {ticker}: {e}")
            continue
    
    # Insert into database
    df = pd.DataFrame(predictions)
    if not df.empty:
        df.to_sql('predictions', engine, if_exists='append', index=False)
        logger.info(f"Inserted {len(predictions)} predictions into database")

def main():
    """Main function to populate all data"""
    logger.info("Starting data population process...")
    
    try:
        # Skip stocks population - already exists
        logger.info("Skipping stocks population - already exists")
        
        # Populate stock prices
        populate_stock_prices()
        
        # Populate predictions
        populate_predictions()
        
        logger.info("Data population completed successfully!")
        
    except Exception as e:
        logger.error(f"Error during data population: {e}")
        raise

if __name__ == "__main__":
    main()