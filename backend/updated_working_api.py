from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import pandas as pd
from sqlalchemy import create_engine
import os
from dotenv import load_dotenv
import logging
from datetime import datetime
from realtime_data_fetcher import RealTimeDataFetcher

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

# Initialize real-time data fetcher
data_fetcher = RealTimeDataFetcher()

@app.get("/")
def read_root():
    return {"message": "StockPulse API is running!"}

@app.get("/health")
def health_check():
    return {"status": "healthy", "timestamp": datetime.now().isoformat()}

@app.get("/screener/run")
def run_stock_screener(min_score: int = 70, max_results: int = 50, include_signals: bool = True):
    """
    Run the stock screener with real-time data
    """
    try:
        # Get real-time screening data
        candidates = data_fetcher.get_realtime_screener_data(max_results)
        
        # Filter by minimum score
        filtered_candidates = [c for c in candidates if c['screening_score'] >= min_score]
        
        # Sort by screening score
        filtered_candidates.sort(key=lambda x: x['screening_score'], reverse=True)
        
        # Calculate summary statistics
        summary = {
            "strong_buy": len([c for c in filtered_candidates if c['screening_score'] > 90]),
            "buy": len([c for c in filtered_candidates if 80 <= c['screening_score'] <= 90]),
            "hold": len([c for c in filtered_candidates if 70 <= c['screening_score'] < 80])
        }
        
        return {
            "screening_date": datetime.now().isoformat(),
            "total_analyzed": len(candidates),
            "candidates_found": len(filtered_candidates),
            "screening_summary": summary,
            "candidates": filtered_candidates
        }
        
    except Exception as e:
        logger.error(f"Error running stock screener: {e}")
        return {"error": str(e), "candidates": []}

@app.get("/screener/signals")
def get_heikin_ashi_signals(tickers: str = None, period: str = "3mo", min_confidence: int = 40):
    """
    Get Heikin Ashi signals for specified tickers with real-time data
    """
    try:
        # Parse tickers or use defaults
        if tickers:
            ticker_list = [t.strip().upper() for t in tickers.split(',')]
        else:
            ticker_list = ['AAPL', 'MSFT', 'GOOGL', 'AMZN', 'TSLA']
        
        # Get real-time signals
        signals = []
        for ticker in ticker_list:
            try:
                stock_info = data_fetcher.get_stock_info(ticker)
                if stock_info:
                    # Generate signal based on real-time data
                    signal = {
                        "ticker": ticker,
                        "primary_signal": "BULLISH" if stock_info['change_percent'] > 0.5 else "BEARISH" if stock_info['change_percent'] < -0.5 else "NEUTRAL",
                        "primary_confidence": min(95, 60 + abs(stock_info['change_percent']) * 5),
                        "reversal_signal": "NO_REVERSAL",
                        "reversal_confidence": 50,
                        "current_price": stock_info['price'],
                        "trend_strength": "STRONG" if abs(stock_info['change_percent']) > 2 else "MODERATE" if abs(stock_info['change_percent']) > 0.5 else "WEAK",
                        "risk_level": "HIGH" if abs(stock_info['change_percent']) > 3 else "MEDIUM" if abs(stock_info['change_percent']) > 1 else "LOW",
                        "key_levels": {
                            "support": round(stock_info['price'] * 0.97, 2),
                            "resistance": round(stock_info['price'] * 1.03, 2)
                        }
                    }
                    
                    if signal["primary_confidence"] >= min_confidence:
                        signals.append(signal)
            except Exception as e:
                logger.warning(f"Error getting signal for {ticker}: {e}")
                continue
        
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

@app.get("/predictions/history")
def get_prediction_history(ticker: str = None, hours: int = 24, limit: int = 100):
    """
    Get prediction history with real-time data
    """
    try:
        # Get tickers to generate predictions for
        if ticker:
            tickers = [ticker.upper()]
        else:
            tickers = data_fetcher.tickers[:limit//4]  # Get subset for demo
        
        predictions = []
        for t in tickers:
            try:
                prediction_data = data_fetcher.get_prediction_data(t)
                if prediction_data:
                    predictions.append(prediction_data)
            except Exception as e:
                logger.warning(f"Error getting prediction for {t}: {e}")
                continue
        
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
        # Get sample predictions
        predictions = []
        for ticker in data_fetcher.tickers[:20]:
            try:
                prediction_data = data_fetcher.get_prediction_data(ticker)
                if prediction_data:
                    predictions.append(prediction_data)
            except:
                continue
        
        # Calculate summary
        bullish_count = len([p for p in predictions if p['signal_type'] == 'BULLISH'])
        bearish_count = len([p for p in predictions if p['signal_type'] == 'BEARISH'])
        neutral_count = len([p for p in predictions if p['signal_type'] == 'NEUTRAL'])
        
        summary = [
            {
                "signal_type": "BULLISH",
                "count": bullish_count,
                "avg_confidence": sum([p['confidence'] for p in predictions if p['signal_type'] == 'BULLISH']) / max(1, bullish_count),
                "avg_screening_score": sum([p['screening_score'] for p in predictions if p['signal_type'] == 'BULLISH']) / max(1, bullish_count),
                "unique_tickers": len(set([p['ticker'] for p in predictions if p['signal_type'] == 'BULLISH']))
            },
            {
                "signal_type": "BEARISH",
                "count": bearish_count,
                "avg_confidence": sum([p['confidence'] for p in predictions if p['signal_type'] == 'BEARISH']) / max(1, bearish_count),
                "avg_screening_score": sum([p['screening_score'] for p in predictions if p['signal_type'] == 'BEARISH']) / max(1, bearish_count),
                "unique_tickers": len(set([p['ticker'] for p in predictions if p['signal_type'] == 'BEARISH']))
            },
            {
                "signal_type": "NEUTRAL",
                "count": neutral_count,
                "avg_confidence": sum([p['confidence'] for p in predictions if p['signal_type'] == 'NEUTRAL']) / max(1, neutral_count),
                "avg_screening_score": sum([p['screening_score'] for p in predictions if p['signal_type'] == 'NEUTRAL']) / max(1, neutral_count),
                "unique_tickers": len(set([p['ticker'] for p in predictions if p['signal_type'] == 'NEUTRAL']))
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
        # Generate multiple predictions for the ticker
        predictions = []
        for i in range(10):
            prediction_data = data_fetcher.get_prediction_data(ticker.upper())
            if prediction_data:
                predictions.append(prediction_data)
        
        return {
            "ticker": ticker.upper(),
            "predictions": predictions,
            "total_predictions": len(predictions)
        }
        
    except Exception as e:
        logger.error(f"Error generating ticker predictions: {e}")
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)