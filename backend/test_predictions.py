#!/usr/bin/env python3
"""
Test script to populate sample prediction data
"""

import asyncio
import asyncpg
import random
from datetime import datetime, timedelta
import os

async def create_sample_predictions():
    """Create sample prediction data for testing"""
    
    # Database connection
    db_host = os.getenv('DB_HOST', 'localhost')
    db_port = int(os.getenv('DB_PORT', '5432'))
    db_name = os.getenv('DB_NAME', 'stockpulse')
    db_user = os.getenv('DB_USER', 'postgres')
    db_password = os.getenv('DB_PASSWORD', 'postgres')
    
    conn = await asyncpg.connect(
        host=db_host,
        port=db_port,
        database=db_name,
        user=db_user,
        password=db_password
    )
    
    # Sample data
    tickers = ['AAPL', 'GOOGL', 'MSFT', 'AMZN', 'TSLA', 'META', 'NVDA', 'NFLX']
    sectors = ['Technology', 'Consumer Discretionary', 'Communication Services']
    signals = ['BULLISH', 'BEARISH', 'NEUTRAL']
    
    # Insert stocks first
    for ticker in tickers:
        await conn.execute("""
            INSERT INTO stocks (ticker, name, sector, industry) 
            VALUES ($1, $2, $3, $4)
            ON CONFLICT (ticker) DO NOTHING
        """, ticker, f"{ticker} Corp", random.choice(sectors), "Technology")
    
    # Create predictions for the last 2 hours (every 5 minutes)
    base_time = datetime.now() - timedelta(hours=2)
    
    for i in range(24):  # 24 intervals of 5 minutes = 2 hours
        timestamp = base_time + timedelta(minutes=i * 5)
        
        for ticker in tickers:
            # Generate sample data
            base_price = random.uniform(50, 300)
            signal_type = random.choice(signals)
            confidence = random.uniform(40, 95)
            screening_score = random.uniform(50, 90)
            
            # Calculate predicted prices with some logic
            if signal_type == 'BULLISH':
                price_1h = base_price * random.uniform(1.001, 1.02)
                price_1d = base_price * random.uniform(1.005, 1.05)
                price_1w = base_price * random.uniform(1.01, 1.1)
            elif signal_type == 'BEARISH':
                price_1h = base_price * random.uniform(0.98, 0.999)
                price_1d = base_price * random.uniform(0.95, 0.995)
                price_1w = base_price * random.uniform(0.9, 0.99)
            else:  # NEUTRAL
                price_1h = base_price * random.uniform(0.995, 1.005)
                price_1d = base_price * random.uniform(0.99, 1.01)
                price_1w = base_price * random.uniform(0.98, 1.02)
            
            # Generate reasons
            reasons = [
                f"RSI indicates {signal_type.lower()} momentum",
                f"MACD shows {signal_type.lower()} crossover",
                f"Volume confirms {signal_type.lower()} pressure",
                f"Moving averages support {signal_type.lower()} trend"
            ]
            
            await conn.execute("""
                INSERT INTO signal_predictions (
                    ticker, timestamp, current_price, signal_type, confidence,
                    primary_reasons, screening_score, sector, predicted_price_1h,
                    predicted_price_1d, predicted_price_1w, volume, rsi, macd,
                    bollinger_position
                ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15)
            """, 
                ticker,
                timestamp,
                base_price,
                signal_type,
                confidence,
                random.sample(reasons, 2),  # Random 2 reasons
                screening_score,
                random.choice(sectors),
                price_1h,
                price_1d,
                price_1w,
                random.randint(1000000, 50000000),  # volume
                random.uniform(20, 80),  # rsi
                random.uniform(-2, 2),   # macd
                random.uniform(0, 1)     # bollinger_position
            )
    
    await conn.close()
    print(f"Created sample predictions for {len(tickers)} tickers over 2 hours")

if __name__ == "__main__":
    asyncio.run(create_sample_predictions())