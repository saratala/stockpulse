from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import pandas as pd
from sqlalchemy import create_engine
import os
from dotenv import load_dotenv
import logging
from datetime import datetime
import random

# Load environment variables
load_dotenv()

app = FastAPI(title="StockPulse API", description="Stock Analysis and Prediction API")

# Enable CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

logger = logging.getLogger(__name__)

# Database connection
DATABASE_URL = os.getenv("DATABASE_URL", "postgresql://postgres:postgres@db:5432/stockpulse")
engine = create_engine(DATABASE_URL)

@app.get("/")
def read_root():
    return {"message": "StockPulse API is running!"}

@app.get("/health")
def health_check():
    return {"status": "healthy", "timestamp": datetime.now().isoformat()}

@app.get("/predictions/history")
def get_prediction_history(ticker: str = None, hours: int = 24, limit: int = 100):
    """
    Get simulated prediction history for testing the sentiment analysis UI
    """
    try:
        # Generate simulated data for demo purposes
        predictions = []
        tickers = [ticker] if ticker else ['AAPL', 'MSFT', 'GOOGL', 'AMZN', 'TSLA', 'META', 'NVDA', 'NFLX']
        
        for i in range(min(limit, len(tickers) * 5)):
            ticker_symbol = tickers[i % len(tickers)]
            
            # Generate realistic sentiment scores
            sentiment_score = random.uniform(-0.5, 0.5)
            sentiment_confidence = random.uniform(0.4, 0.9)
            
            prediction = {
                "ticker": ticker_symbol,
                "timestamp": (datetime.now()).isoformat(),
                "current_price": round(random.uniform(100, 500), 2),
                "signal_type": random.choice(["BULLISH", "BEARISH", "NEUTRAL"]),
                "confidence": round(random.uniform(60, 90), 1),
                "primary_reasons": [
                    "Strong technical momentum",
                    "Positive sentiment analysis",
                    "Volume confirmation"
                ],
                "screening_score": round(random.uniform(70, 95), 1),
                "sector": random.choice(["Technology", "Healthcare", "Finance", "Energy"]),
                "predicted_price_1h": round(random.uniform(100, 500), 2),
                "predicted_price_1d": round(random.uniform(100, 500), 2),
                "predicted_price_1w": round(random.uniform(100, 500), 2),
                "volume": random.randint(1000000, 50000000),
                "rsi": round(random.uniform(30, 70), 1),
                "macd": round(random.uniform(-2, 2), 4),
                "bollinger_position": round(random.uniform(0, 1), 2),
                "sentiment_score": sentiment_score,
                "sentiment_confidence": sentiment_confidence,
                "sentiment_impact": random.choice(["immediate", "short-term", "long-term", "negligible"]),
                "news_count": random.randint(1, 25)
            }
            predictions.append(prediction)
        
        return {
            "total_predictions": len(predictions),
            "predictions": predictions
        }
        
    except Exception as e:
        logger.error(f"Error generating prediction history: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/predictions/summary")
def get_prediction_summary(hours: int = 24):
    """
    Get summary statistics for predictions
    """
    try:
        summary = [
            {
                "signal_type": "BULLISH",
                "count": 15,
                "avg_confidence": 82.5,
                "avg_screening_score": 87.3,
                "unique_tickers": 8
            },
            {
                "signal_type": "BEARISH",
                "count": 8,
                "avg_confidence": 75.2,
                "avg_screening_score": 79.1,
                "unique_tickers": 6
            },
            {
                "signal_type": "NEUTRAL",
                "count": 12,
                "avg_confidence": 68.7,
                "avg_screening_score": 72.4,
                "unique_tickers": 9
            }
        ]
        
        return {
            "time_period_hours": hours,
            "summary": summary
        }
        
    except Exception as e:
        logger.error(f"Error generating prediction summary: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/predictions/ticker/{ticker}")
def get_ticker_predictions(ticker: str, hours: int = 168):
    """
    Get prediction history for a specific ticker
    """
    try:
        # Generate simulated data for the specific ticker
        predictions = []
        
        for i in range(10):  # Generate 10 predictions for the ticker
            sentiment_score = random.uniform(-0.3, 0.3)
            sentiment_confidence = random.uniform(0.5, 0.8)
            
            prediction = {
                "timestamp": (datetime.now()).isoformat(),
                "current_price": round(random.uniform(100, 500), 2),
                "signal_type": random.choice(["BULLISH", "BEARISH", "NEUTRAL"]),
                "confidence": round(random.uniform(60, 90), 1),
                "primary_reasons": [
                    "Technical analysis positive",
                    "Market sentiment favorable",
                    "Volume patterns strong"
                ],
                "screening_score": round(random.uniform(70, 95), 1),
                "predicted_price_1h": round(random.uniform(100, 500), 2),
                "predicted_price_1d": round(random.uniform(100, 500), 2),
                "predicted_price_1w": round(random.uniform(100, 500), 2),
                "volume": random.randint(1000000, 50000000),
                "rsi": round(random.uniform(30, 70), 1),
                "macd": round(random.uniform(-2, 2), 4),
                "bollinger_position": round(random.uniform(0, 1), 2),
                "sentiment_score": sentiment_score,
                "sentiment_confidence": sentiment_confidence,
                "sentiment_impact": random.choice(["immediate", "short-term", "long-term", "negligible"]),
                "news_count": random.randint(1, 15)
            }
            predictions.append(prediction)
        
        return {
            "ticker": ticker.upper(),
            "predictions": predictions,
            "total_predictions": len(predictions)
        }
        
    except Exception as e:
        logger.error(f"Error generating ticker predictions: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/screener/run")
def run_stock_screener(min_score: int = 70, max_results: int = 50, include_signals: bool = True):
    """
    Run the stock screener with simulated data
    """
    try:
        # Generate simulated screening results
        candidates = []
        tickers = ['AAPL', 'MSFT', 'GOOGL', 'AMZN', 'TSLA', 'META', 'NVDA', 'NFLX', 'AMD', 'ORCL', 
                  'CRM', 'ADBE', 'INTC', 'CSCO', 'AVGO', 'TXN', 'QCOM', 'MU', 'PYPL', 'SQ']
        
        for ticker in tickers[:max_results]:
            score = round(random.uniform(min_score, 100), 1)
            if score >= min_score:
                candidate = {
                    "ticker": ticker,
                    "name": f"{ticker} Inc.",
                    "sector": random.choice(["Technology", "Healthcare", "Finance", "Energy", "Consumer"]),
                    "screening_score": score,
                    "ema_stack_aligned": random.choice([True, False]),
                    "adx_strength": round(random.uniform(20, 60), 1),
                    "stoch_position": round(random.uniform(20, 80), 1),
                    "rsi": round(random.uniform(30, 70), 1),
                    "volume_ratio": round(random.uniform(0.8, 2.5), 2),
                    "price": round(random.uniform(50, 500), 2),
                    "change_percent": round(random.uniform(-5, 5), 2),
                    "signal_analysis": {
                        "ticker": ticker,
                        "primary_signal": random.choice(["BULLISH", "BEARISH", "NEUTRAL"]),
                        "primary_confidence": round(random.uniform(60, 95), 1),
                        "reversal_signal": random.choice(["BULLISH_REVERSAL", "BEARISH_REVERSAL", "NO_REVERSAL"]),
                        "reversal_confidence": round(random.uniform(40, 80), 1),
                        "risk_level": random.choice(["LOW", "MEDIUM", "HIGH"]),
                        "key_levels": {
                            "support": round(random.uniform(40, 480), 2),
                            "resistance": round(random.uniform(60, 520), 2),
                            "pivot": round(random.uniform(50, 500), 2)
                        }
                    } if include_signals else None
                }
                candidates.append(candidate)
        
        return {
            "screening_date": datetime.now().isoformat(),
            "total_analyzed": len(tickers),
            "candidates_found": len(candidates),
            "screening_summary": {
                "strong_buy": len([c for c in candidates if c['screening_score'] > 90]),
                "buy": len([c for c in candidates if 80 <= c['screening_score'] <= 90]),
                "hold": len([c for c in candidates if 70 <= c['screening_score'] < 80])
            },
            "candidates": candidates
        }
        
    except Exception as e:
        logger.error(f"Error running stock screener: {e}")
        return {"error": str(e), "candidates": []}

@app.get("/screener/signals")
def get_heikin_ashi_signals(tickers: str = None, period: str = "3mo", min_confidence: int = 40):
    """
    Get Heikin Ashi signals for specified tickers
    """
    try:
        # Parse tickers or use defaults
        if tickers:
            ticker_list = [t.strip().upper() for t in tickers.split(',')]
        else:
            ticker_list = ['AAPL', 'MSFT', 'GOOGL', 'AMZN', 'TSLA']
        
        # Generate simulated signals
        signals = []
        for ticker in ticker_list:
            confidence = round(random.uniform(min_confidence, 95), 1)
            if confidence >= min_confidence:
                signal = {
                    "ticker": ticker,
                    "primary_signal": random.choice(["BULLISH", "BEARISH", "NEUTRAL"]),
                    "primary_confidence": confidence,
                    "reversal_signal": random.choice(["BULLISH_REVERSAL", "BEARISH_REVERSAL", "NO_REVERSAL"]),
                    "reversal_confidence": round(random.uniform(40, 80), 1),
                    "current_price": round(random.uniform(50, 500), 2),
                    "trend_strength": random.choice(["STRONG", "MODERATE", "WEAK"]),
                    "risk_level": random.choice(["LOW", "MEDIUM", "HIGH"]),
                    "key_levels": {
                        "support": round(random.uniform(40, 480), 2),
                        "resistance": round(random.uniform(60, 520), 2)
                    }
                }
                signals.append(signal)
        
        return {
            "analysis_date": datetime.now().isoformat(),
            "total_analyzed": len(ticker_list),
            "signals_found": len(signals),
            "summary": {
                "bullish": len([s for s in signals if s['primary_signal'] == 'BULLISH']),
                "bearish": len([s for s in signals if s['primary_signal'] == 'BEARISH']),
                "neutral": len([s for s in signals if s['primary_signal'] == 'NEUTRAL'])
            },
            "signals": signals
        }
        
    except Exception as e:
        logger.error(f"Error getting Heikin Ashi signals: {e}")
        return {"error": str(e), "signals": []}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)