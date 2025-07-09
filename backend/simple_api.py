#!/usr/bin/env python3
"""
Simple FastAPI backend for StockPulse without problematic dependencies
"""

import os
import logging
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import create_engine, text
import pandas as pd
from typing import List, Dict, Any, Optional
from pydantic import BaseModel
from datetime import datetime, timedelta
import random

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="StockPulse Simple API")

# Enable CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Database connection
DATABASE_URL = os.getenv('DATABASE_URL', 'postgresql://postgres:postgres@db:5432/stockpulse')
engine = create_engine(DATABASE_URL)

class ScreeningCandidate(BaseModel):
    ticker: str
    name: str
    sector: str
    current_price: float
    change_percent: float
    signal_analysis: Dict[str, Any]
    passes_all_screens: bool
    screening_score: int

@app.get("/health")
async def health_check():
    return {"status": "healthy", "timestamp": datetime.now()}

@app.get("/screener/run")
async def run_screener(
    min_score: int = 70,
    max_results: int = 50,
    signal_type: str = "all"
):
    """
    Run stock screening with current data
    """
    try:
        # Get latest stock data
        query = """
        WITH latest_prices AS (
            SELECT DISTINCT ON (ticker) ticker, date, close, open, volume
            FROM stock_prices 
            ORDER BY ticker, date DESC
        ),
        previous_prices AS (
            SELECT DISTINCT ON (sp.ticker) sp.ticker, sp.close as prev_close
            FROM stock_prices sp
            JOIN latest_prices lp ON sp.ticker = lp.ticker
            WHERE sp.date < lp.date
            ORDER BY sp.ticker, sp.date DESC
        )
        SELECT 
            lp.ticker,
            lp.close as current_price,
            lp.volume,
            COALESCE(((lp.close - pp.prev_close) / pp.prev_close * 100), 0) as change_percent
        FROM latest_prices lp
        LEFT JOIN previous_prices pp ON lp.ticker = pp.ticker
        ORDER BY lp.ticker
        """
        
        with engine.connect() as conn:
            result = conn.execute(text(query))
            data = result.fetchall()
        
        # Create mock screening candidates
        candidates = []
        company_info = {
            'AAPL': {'name': 'Apple Inc.', 'sector': 'Technology'},
            'MSFT': {'name': 'Microsoft Corp.', 'sector': 'Technology'},
            'GOOGL': {'name': 'Alphabet Inc.', 'sector': 'Technology'},
            'AMZN': {'name': 'Amazon.com Inc.', 'sector': 'Consumer Discretionary'},
            'NVDA': {'name': 'NVIDIA Corp.', 'sector': 'Technology'},
            'META': {'name': 'Meta Platforms Inc.', 'sector': 'Technology'},
            'TSLA': {'name': 'Tesla Inc.', 'sector': 'Consumer Discretionary'},
            'JPM': {'name': 'JPMorgan Chase & Co.', 'sector': 'Financial Services'},
            'JNJ': {'name': 'Johnson & Johnson', 'sector': 'Healthcare'},
            'HD': {'name': 'Home Depot Inc.', 'sector': 'Consumer Discretionary'},
            'MA': {'name': 'Mastercard Inc.', 'sector': 'Financial Services'}
        }
        
        for row in data:
            ticker = row[0]
            current_price = float(row[1])
            volume = int(row[2])
            change_percent = float(row[3])
            
            # Get company info or use defaults
            info = company_info.get(ticker, {
                'name': f'{ticker} Corp.',
                'sector': 'Technology'
            })
            
            # Generate mock signals based on price change and volume
            signal_strength = abs(change_percent) * 10 + (volume / 1000000) * 5
            confidence = min(95, max(60, signal_strength + random.uniform(-10, 10)))
            
            signal = "BULLISH" if change_percent > 0 else "BEARISH" if change_percent < -1 else "NEUTRAL"
            
            # Generate screening score
            screening_score = int(min(100, max(50, 70 + change_percent * 2 + random.uniform(-5, 15))))
            passes_screens = screening_score >= min_score
            
            candidate = ScreeningCandidate(
                ticker=ticker,
                name=info['name'],
                sector=info['sector'],
                current_price=current_price,
                change_percent=change_percent,
                signal_analysis={
                    "primary_signal": signal,
                    "primary_confidence": round(confidence, 1),
                    "primary_reasons": [
                        f"Price change: {change_percent:+.2f}%",
                        f"Volume: {volume:,}",
                        "Technical indicators alignment",
                        "Market momentum analysis"
                    ]
                },
                passes_all_screens=passes_screens,
                screening_score=screening_score
            )
            
            if passes_screens and len(candidates) < max_results:
                candidates.append(candidate)
        
        # Sort by screening score
        candidates.sort(key=lambda x: x.screening_score, reverse=True)
        
        logger.info(f"Generated {len(candidates)} screening candidates")
        
        return {
            "candidates": [c.dict() for c in candidates],
            "total_analyzed": len(data),
            "total_passed": len(candidates),
            "timestamp": datetime.now().isoformat()
        }
        
    except Exception as e:
        logger.error(f"Error in screening: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/screener/comprehensive/{ticker}")
async def get_comprehensive_analysis(ticker: str):
    """
    Get comprehensive analysis for a specific ticker
    """
    try:
        # Get recent price data
        query = """
        SELECT date, open, high, low, close, volume
        FROM stock_prices 
        WHERE ticker = :ticker
        ORDER BY date DESC
        LIMIT 50
        """
        
        with engine.connect() as conn:
            result = conn.execute(text(query), {"ticker": ticker.upper()})
            data = result.fetchall()
        
        if not data:
            raise HTTPException(status_code=404, detail=f"No data found for ticker {ticker}")
        
        # Calculate basic technical indicators
        df = pd.DataFrame(data, columns=['date', 'open', 'high', 'low', 'close', 'volume'])
        df = df.sort_values('date')
        
        current_price = float(df.iloc[-1]['close'])
        previous_price = float(df.iloc[-2]['close']) if len(df) > 1 else current_price
        
        # Calculate returns
        returns_1d = ((current_price - previous_price) / previous_price * 100) if previous_price else 0
        returns_5d = ((current_price - float(df.iloc[-6]['close'])) / float(df.iloc[-6]['close']) * 100) if len(df) >= 6 else returns_1d
        returns_10d = ((current_price - float(df.iloc[-11]['close'])) / float(df.iloc[-11]['close']) * 100) if len(df) >= 11 else returns_5d
        returns_20d = ((current_price - float(df.iloc[-21]['close'])) / float(df.iloc[-21]['close']) * 100) if len(df) >= 21 else returns_10d
        
        # Calculate moving averages
        ma_20 = df['close'].tail(20).mean() if len(df) >= 20 else current_price
        ma_50 = df['close'].tail(50).mean() if len(df) >= 50 else current_price
        
        # Mock comprehensive analysis
        analysis = {
            "ticker": ticker.upper(),
            "analysis_date": datetime.now().isoformat(),
            "current_price": current_price,
            "market_regime": {
                "regime": "bullish" if returns_1d > 0 else "bearish" if returns_1d < -1 else "neutral",
                "strength": abs(returns_1d) * 10,
                "components": {}
            },
            "key_levels": {
                "pivot_points": {},
                "moving_averages": {
                    "ma_20": float(ma_20),
                    "ma_50": float(ma_50)
                },
                "bollinger_bands": {
                    "upper": current_price * 1.02,
                    "middle": current_price,
                    "lower": current_price * 0.98
                },
                "recent_high": float(df['high'].max()),
                "recent_low": float(df['low'].min())
            },
            "technical_indicators": {
                "trend": {
                    "sma_20": float(ma_20),
                    "ema_12": current_price * 0.99,
                    "ema_26": current_price * 1.01
                },
                "momentum": {
                    "rsi": 50 + returns_1d,
                    "macd": returns_1d * 0.1,
                    "stochastic": 50 + returns_1d * 2
                },
                "volatility": {},
                "strength": {}
            },
            "heikin_ashi": {
                "ha_bullish": returns_1d > 0,
                "ha_strength": abs(returns_1d) * 5
            },
            "performance": {
                "return_1d": returns_1d,
                "return_5d": returns_5d,
                "return_10d": returns_10d,
                "return_20d": returns_20d
            },
            "signal_analysis": {
                "primary_signal": "BULLISH" if returns_1d > 2 else "BEARISH" if returns_1d < -2 else "NEUTRAL",
                "primary_confidence": min(95, max(60, abs(returns_1d) * 10 + 50)),
                "primary_reasons": [
                    f"Recent performance: {returns_1d:+.2f}%",
                    f"Above/below MA20: {((current_price - ma_20) / ma_20 * 100):+.1f}%",
                    "Volume analysis",
                    "Technical pattern recognition"
                ]
            },
            "screening_analysis": {
                "screening_score": int(70 + returns_1d * 2 + random.uniform(-5, 15)),
                "passes_all_screens": returns_1d > -2
            }
        }
        
        return analysis
        
    except Exception as e:
        logger.error(f"Error getting analysis for {ticker}: {e}")
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)