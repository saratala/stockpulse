#!/usr/bin/env python3
"""
Simple Predictions API for testing the frontend
"""

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import asyncpg
import json
from datetime import datetime, timedelta
import random
import os
from typing import Optional, List
import asyncio

app = FastAPI()

# Enable CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Database connection
async def get_db_connection():
    return await asyncpg.connect(
        host=os.getenv('DB_HOST', 'localhost'),
        port=int(os.getenv('DB_PORT', '5432')),
        database=os.getenv('DB_NAME', 'stockpulse'),
        user=os.getenv('DB_USER', 'postgres'),
        password=os.getenv('DB_PASSWORD', 'postgres')
    )

@app.get("/health")
async def health_check():
    return {"status": "healthy"}

@app.get("/screener/run")
async def run_screener(
    min_score: int = 70,
    max_results: int = 50,
    include_signals: bool = True
):
    """Run screener and return sample data"""
    tickers = ['AAPL', 'GOOGL', 'MSFT', 'AMZN', 'TSLA', 'META', 'NVDA', 'NFLX', 'AMD', 'NVDA']
    sectors = ['Technology', 'Consumer Discretionary', 'Communication Services']
    signals = ['BULLISH', 'BEARISH', 'NEUTRAL']
    
    candidates = []
    for i, ticker in enumerate(tickers[:max_results]):
        signal_type = random.choice(signals)
        confidence = random.uniform(60, 95)
        score = random.uniform(min_score, 95)
        
        candidate = {
            "ticker": ticker,
            "name": f"{ticker} Corporation",
            "sector": random.choice(sectors),
            "current_price": random.uniform(50, 500),
            "screening_score": score,
            "passes_all_screens": score >= 80
        }
        
        if include_signals:
            candidate["signal_analysis"] = {
                "primary_signal": signal_type,
                "primary_confidence": confidence,
                "primary_reasons": [
                    f"Strong {signal_type.lower()} momentum detected",
                    f"Technical indicators confirm {signal_type.lower()} trend",
                    f"Volume supports {signal_type.lower()} movement"
                ]
            }
        
        candidates.append(candidate)
    
    return {
        "screening_date": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "total_analyzed": 100,
        "candidates_found": len(candidates),
        "candidates": candidates,
        "screening_summary": {
            "ema_stack": random.randint(20, 40),
            "momentum": random.randint(15, 35),
            "volume": random.randint(10, 30),
            "fundamental": random.randint(5, 15)
        }
    }

@app.get("/predictions/history")
async def get_prediction_history(
    ticker: Optional[str] = None,
    hours: Optional[int] = 24,
    limit: Optional[int] = 100
):
    """Get prediction history"""
    try:
        conn = await get_db_connection()
        
        # Build query
        where_conditions = []
        params = []
        param_count = 0
        
        if ticker:
            param_count += 1
            where_conditions.append(f"ticker = ${param_count}")
            params.append(ticker.upper())
        
        if hours:
            param_count += 1
            where_conditions.append(f"timestamp >= NOW() - INTERVAL ${param_count} * INTERVAL '1 hour'")
            params.append(hours)
        
        where_clause = "WHERE " + " AND ".join(where_conditions) if where_conditions else ""
        
        param_count += 1
        query = f"""
            SELECT 
                ticker, timestamp, current_price, signal_type, confidence,
                primary_reasons, screening_score, sector, predicted_price_1h,
                predicted_price_1d, predicted_price_1w, volume, rsi, macd,
                bollinger_position
            FROM signal_predictions
            {where_clause}
            ORDER BY timestamp DESC
            LIMIT ${param_count}
        """
        params.append(limit)
        
        rows = await conn.fetch(query, *params)
        await conn.close()
        
        predictions = []
        for row in rows:
            predictions.append({
                "ticker": row['ticker'],
                "timestamp": row['timestamp'].strftime("%Y-%m-%d %H:%M:%S"),
                "current_price": float(row['current_price']),
                "signal_type": row['signal_type'],
                "confidence": float(row['confidence']),
                "primary_reasons": row['primary_reasons'] if row['primary_reasons'] else [],
                "screening_score": float(row['screening_score']),
                "sector": row['sector'],
                "predicted_price_1h": float(row['predicted_price_1h']) if row['predicted_price_1h'] else None,
                "predicted_price_1d": float(row['predicted_price_1d']) if row['predicted_price_1d'] else None,
                "predicted_price_1w": float(row['predicted_price_1w']) if row['predicted_price_1w'] else None,
                "volume": int(row['volume']) if row['volume'] else None,
                "rsi": float(row['rsi']) if row['rsi'] else None,
                "macd": float(row['macd']) if row['macd'] else None,
                "bollinger_position": float(row['bollinger_position']) if row['bollinger_position'] else None
            })
        
        return {
            "total_predictions": len(predictions),
            "predictions": predictions
        }
        
    except Exception as e:
        print(f"Error: {e}")
        # If no data exists, create sample data
        return await create_sample_data_and_return(ticker, hours, limit)

@app.get("/predictions/summary")
async def get_prediction_summary(hours: Optional[int] = 24):
    """Get prediction summary"""
    try:
        conn = await get_db_connection()
        
        query = """
            SELECT 
                signal_type,
                COUNT(*) as count,
                AVG(confidence) as avg_confidence,
                AVG(screening_score) as avg_screening_score,
                COUNT(DISTINCT ticker) as unique_tickers
            FROM signal_predictions
            WHERE timestamp >= NOW() - INTERVAL '%s hours'
            GROUP BY signal_type
            ORDER BY count DESC
        """
        
        rows = await conn.fetch(query.replace('%s', str(hours)))
        await conn.close()
        
        summary = []
        for row in rows:
            summary.append({
                "signal_type": row['signal_type'],
                "count": int(row['count']),
                "avg_confidence": float(row['avg_confidence']),
                "avg_screening_score": float(row['avg_screening_score']),
                "unique_tickers": int(row['unique_tickers'])
            })
        
        return {
            "time_period_hours": hours,
            "summary": summary
        }
        
    except Exception as e:
        print(f"Summary error: {e}")
        # Return sample summary if no data
        return {
            "time_period_hours": hours,
            "summary": [
                {"signal_type": "BULLISH", "count": 15, "avg_confidence": 75.2, "avg_screening_score": 82.1, "unique_tickers": 8},
                {"signal_type": "NEUTRAL", "count": 12, "avg_confidence": 65.8, "avg_screening_score": 71.4, "unique_tickers": 6},
                {"signal_type": "BEARISH", "count": 8, "avg_confidence": 68.9, "avg_screening_score": 69.2, "unique_tickers": 5}
            ]
        }

async def create_sample_data_and_return(ticker: Optional[str], hours: int, limit: int):
    """Create sample data if none exists"""
    try:
        conn = await get_db_connection()
        
        # Sample tickers
        tickers = ['AAPL', 'GOOGL', 'MSFT', 'AMZN', 'TSLA', 'META', 'NVDA', 'NFLX'] if not ticker else [ticker.upper()]
        sectors = ['Technology', 'Consumer Discretionary', 'Communication Services']
        signals = ['BULLISH', 'BEARISH', 'NEUTRAL']
        
        # Insert stocks first
        for t in tickers:
            await conn.execute("""
                INSERT INTO stocks (ticker, name, sector, industry) 
                VALUES ($1, $2, $3, $4)
                ON CONFLICT (ticker) DO NOTHING
            """, t, f"{t} Corp", random.choice(sectors), "Technology")
        
        # Create predictions for the last period
        predictions = []
        base_time = datetime.now() - timedelta(hours=hours)
        
        for i in range(min(limit // len(tickers), 24)):  # Max 24 time intervals
            timestamp = base_time + timedelta(minutes=i * 15)  # Every 15 minutes
            
            for t in tickers:
                base_price = random.uniform(50, 300)
                signal_type = random.choice(signals)
                confidence = random.uniform(40, 95)
                screening_score = random.uniform(50, 90)
                
                # Calculate predicted prices
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
                
                reasons = [
                    f"RSI indicates {signal_type.lower()} momentum",
                    f"MACD shows {signal_type.lower()} crossover",
                    f"Volume confirms {signal_type.lower()} pressure"
                ]
                
                await conn.execute("""
                    INSERT INTO signal_predictions (
                        ticker, timestamp, current_price, signal_type, confidence,
                        primary_reasons, screening_score, sector, predicted_price_1h,
                        predicted_price_1d, predicted_price_1w, volume, rsi, macd,
                        bollinger_position
                    ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15)
                    ON CONFLICT DO NOTHING
                """, 
                    t, timestamp, base_price, signal_type, confidence,
                    reasons[:2], screening_score, random.choice(sectors),
                    price_1h, price_1d, price_1w,
                    random.randint(1000000, 50000000),
                    random.uniform(20, 80), random.uniform(-2, 2), random.uniform(0, 1)
                )
                
                predictions.append({
                    "ticker": t,
                    "timestamp": timestamp.strftime("%Y-%m-%d %H:%M:%S"),
                    "current_price": base_price,
                    "signal_type": signal_type,
                    "confidence": confidence,
                    "primary_reasons": reasons[:2],
                    "screening_score": screening_score,
                    "sector": random.choice(sectors),
                    "predicted_price_1h": price_1h,
                    "predicted_price_1d": price_1d,
                    "predicted_price_1w": price_1w,
                    "volume": random.randint(1000000, 50000000),
                    "rsi": random.uniform(20, 80),
                    "macd": random.uniform(-2, 2),
                    "bollinger_position": random.uniform(0, 1)
                })
        
        await conn.close()
        
        return {
            "total_predictions": len(predictions),
            "predictions": predictions[:limit]
        }
        
    except Exception as e:
        print(f"Sample data error: {e}")
        # Fallback to static sample data
        return {
            "total_predictions": 3,
            "predictions": [
                {
                    "ticker": "AAPL",
                    "timestamp": "2025-01-08 19:30:00",
                    "current_price": 185.25,
                    "signal_type": "BULLISH",
                    "confidence": 78.5,
                    "primary_reasons": ["RSI indicates bullish momentum", "MACD shows bullish crossover"],
                    "screening_score": 85.2,
                    "sector": "Technology",
                    "predicted_price_1h": 186.1,
                    "predicted_price_1d": 189.3,
                    "predicted_price_1w": 195.8,
                    "volume": 25000000,
                    "rsi": 65.2,
                    "macd": 1.2,
                    "bollinger_position": 0.7
                }
            ]
        }

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)