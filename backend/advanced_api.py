#!/usr/bin/env python3
"""
Advanced API using sophisticated screener and Heikin Ashi signal detection
"""

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
import asyncpg
import logging
from datetime import datetime
from typing import Optional, Dict, List
import traceback

# Import your advanced modules
from screener_module import StockScreener
from heikin_ashi_signals import HeikinAshiSignalDetector
from enhanced_data_fetcher import EnhancedDataFetcher

app = FastAPI(title="StockPulse Advanced API", version="2.0.0")

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Initialize advanced modules
screener = StockScreener()
signal_detector = HeikinAshiSignalDetector()
data_fetcher = EnhancedDataFetcher()

async def get_db_connection():
    """Get database connection"""
    return await asyncpg.connect(
        host='stockpulse_db',
        user='postgres',
        password='postgres',
        database='stockpulse'
    )

@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {
        "status": "healthy",
        "system": "advanced",
        "modules": {
            "screener": "StockScreener",
            "signals": "HeikinAshiSignalDetector",
            "data": "EnhancedDataFetcher"
        }
    }

@app.get("/screener/run")
async def run_advanced_screener(
    min_score: int = 70,
    max_results: int = 50,
    include_signals: bool = True,
    min_adx: int = 20,
    max_stoch_k: int = 40
):
    """Run advanced screener with EMA stack and sophisticated filtering"""
    try:
        logger.info(f"Running advanced screener: score≥{min_score}, max_results={max_results}")
        
        # Use your sophisticated screener
        screening_results = screener.screen_stocks(
            min_score=min_score,
            max_results=max_results,
            detailed=True
        )
        
        # Convert to API format
        candidates = []
        for result in screening_results:
            candidate = {
                "ticker": result.get('ticker', ''),
                "name": result.get('name', f"{result.get('ticker', 'Unknown')} Corporation"),
                "sector": result.get('sector', 'Unknown'),
                "current_price": float(result.get('current_price', 0)),
                "screening_score": float(result.get('screening_score', 0)),
                "passes_all_screens": result.get('passes_all_screens', False),
                "ema_stack_score": result.get('ema_stack_score', 0),
                "adx": result.get('adx', 0),
                "stoch_k": result.get('stoch_k', 0),
                "volume_ratio": result.get('volume_ratio', 1.0)
            }
            
            # Add signal analysis if requested
            if include_signals and result.get('signal_analysis'):
                signal_data = result['signal_analysis']
                candidate["signal_analysis"] = {
                    "primary_signal": signal_data.get('signal', 'NEUTRAL'),
                    "primary_confidence": float(signal_data.get('confidence', 50)),
                    "primary_reasons": signal_data.get('reasons', [])
                }
            
            candidates.append(candidate)
        
        # Calculate screening summary
        total_bullish = sum(1 for c in candidates 
                          if c.get('signal_analysis', {}).get('primary_signal') == 'BULLISH')
        total_bearish = sum(1 for c in candidates 
                          if c.get('signal_analysis', {}).get('primary_signal') == 'BEARISH')
        high_confidence = sum(1 for c in candidates 
                            if c.get('signal_analysis', {}).get('primary_confidence', 0) >= 70)
        high_score = sum(1 for c in candidates if c['screening_score'] >= 80)
        
        return {
            "screening_date": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "total_analyzed": len(screener.sp500_tickers),
            "candidates_found": len(candidates),
            "candidates": candidates,
            "screening_summary": {
                "ema_stack": total_bullish,
                "momentum": high_confidence,
                "volume": high_score,
                "fundamental": sum(1 for c in candidates if c['passes_all_screens'])
            },
            "filters_applied": {
                "min_score": min_score,
                "min_adx": min_adx,
                "max_stoch_k": max_stoch_k,
                "ema_stack_required": True,
                "market_cap_min": "$100B"
            }
        }
        
    except Exception as e:
        logger.error(f"Error in advanced screener: {e}")
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=f"Screener error: {str(e)}")

@app.get("/screener/signals")
async def get_heikin_ashi_signals(
    tickers: str,
    period: str = "6mo",
    min_confidence: int = 50
):
    """Get Heikin Ashi signals for specific tickers"""
    try:
        ticker_list = [t.strip().upper() for t in tickers.split(',')]
        
        if len(ticker_list) > 20:
            raise HTTPException(status_code=400, detail="Maximum 20 tickers allowed")
        
        results = []
        
        for ticker in ticker_list:
            try:
                # Get Heikin Ashi signal analysis
                signal_result = signal_detector.detect_signals(
                    ticker=ticker,
                    period=period,
                    interval='1d'
                )
                
                if signal_result and signal_result.get('confidence', 0) >= min_confidence:
                    results.append({
                        "ticker": ticker,
                        "signal": signal_result.get('signal', 'NEUTRAL'),
                        "confidence": signal_result.get('confidence', 50),
                        "reasons": signal_result.get('reasons', []),
                        "score": signal_result.get('score', 50),
                        "sector": signal_result.get('sector', 'Unknown'),
                        "analysis_date": datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                    })
                    
            except Exception as e:
                logger.warning(f"Error analyzing {ticker}: {e}")
                continue
        
        return {
            "analysis_date": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "signals_found": len(results),
            "min_confidence": min_confidence,
            "results": results
        }
        
    except Exception as e:
        logger.error(f"Error in Heikin Ashi signals: {e}")
        raise HTTPException(status_code=500, detail=f"Signal analysis error: {str(e)}")

@app.get("/predictions/history")
async def get_prediction_history(
    ticker: Optional[str] = None,
    hours: Optional[int] = 24,
    limit: Optional[int] = 100
):
    """Get prediction history from advanced system"""
    try:
        conn = await get_db_connection()
        
        # Build query
        where_parts = []
        if ticker:
            where_parts.append(f"ticker = '{ticker.upper()}'")
        if hours:
            where_parts.append(f"timestamp >= NOW() - INTERVAL '{hours} hours'")
        
        where_clause = "WHERE " + " AND ".join(where_parts) if where_parts else ""
        
        query = f"""
            SELECT ticker, timestamp, current_price, signal_type, confidence,
                   primary_reasons, screening_score, sector, predicted_price_1h,
                   predicted_price_1d, predicted_price_1w, volume, rsi, macd,
                   bollinger_position, sentiment_score, sentiment_confidence,
                   sentiment_impact, news_count
            FROM signal_predictions
            {where_clause}
            ORDER BY timestamp DESC
            LIMIT {limit}
        """
        
        rows = await conn.fetch(query)
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
                "bollinger_position": float(row['bollinger_position']) if row['bollinger_position'] else None,
                "sentiment_score": float(row['sentiment_score']) if row['sentiment_score'] else 0,
                "sentiment_confidence": float(row['sentiment_confidence']) if row['sentiment_confidence'] else 0,
                "sentiment_impact": row['sentiment_impact'] if row['sentiment_impact'] else 'negligible',
                "news_count": int(row['news_count']) if row['news_count'] else 0,
                "source": "advanced_system"
            })
        
        return {
            "total_predictions": len(predictions),
            "predictions": predictions,
            "system": "advanced",
            "filters": {
                "ticker": ticker,
                "hours": hours,
                "limit": limit
            }
        }
        
    except Exception as e:
        logger.error(f"Error fetching prediction history: {e}")
        return {"error": str(e), "total_predictions": 0, "predictions": []}

@app.get("/predictions/summary")
async def get_prediction_summary(hours: Optional[int] = 24):
    """Get prediction summary from advanced system"""
    try:
        conn = await get_db_connection()
        
        query = f"""
            SELECT 
                signal_type,
                COUNT(*) as count,
                AVG(confidence) as avg_confidence,
                AVG(screening_score) as avg_screening_score,
                COUNT(DISTINCT ticker) as unique_tickers,
                COUNT(CASE WHEN confidence >= 70 THEN 1 END) as high_confidence_count,
                COUNT(CASE WHEN screening_score >= 80 THEN 1 END) as high_score_count
            FROM signal_predictions
            WHERE timestamp >= NOW() - INTERVAL '{hours} hours'
            GROUP BY signal_type
            ORDER BY count DESC
        """
        
        rows = await conn.fetch(query)
        await conn.close()
        
        summary = []
        for row in rows:
            summary.append({
                "signal_type": row['signal_type'],
                "count": int(row['count']),
                "avg_confidence": float(row['avg_confidence']),
                "avg_screening_score": float(row['avg_screening_score']),
                "unique_tickers": int(row['unique_tickers']),
                "high_confidence_count": int(row['high_confidence_count']),
                "high_score_count": int(row['high_score_count'])
            })
        
        return {
            "time_period_hours": hours,
            "summary": summary,
            "system": "advanced",
            "analysis_date": datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        }
        
    except Exception as e:
        logger.error(f"Error fetching prediction summary: {e}")
        return {
            "time_period_hours": hours,
            "summary": [],
            "error": str(e)
        }

@app.get("/screener/comprehensive/{ticker}")
async def get_comprehensive_analysis(ticker: str):
    """Get comprehensive analysis for a specific ticker"""
    try:
        ticker = ticker.upper()
        
        # Get comprehensive data
        data = data_fetcher.get_comprehensive_data(ticker, period='6mo')
        
        if data.empty:
            raise HTTPException(status_code=404, detail=f"No data found for {ticker}")
        
        # Get Heikin Ashi analysis
        signal_result = signal_detector.detect_signals(ticker, period='6mo')
        
        # Get latest technical indicators
        latest = data.iloc[-1]
        
        return {
            "ticker": ticker,
            "analysis_date": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "current_price": float(latest['Close']),
            "signal_analysis": signal_result,
            "technical_indicators": {
                "rsi_14": float(latest.get('RSI_14', 0)),
                "adx_14": float(latest.get('ADX_14', 0)),
                "stoch_k": float(latest.get('STOCHk_14_3_3', 0)),
                "macd": float(latest.get('MACD_12_26_9', 0)),
                "atr_14": float(latest.get('ATR_14', 0)),
                "volume_ratio": float(data['Volume'].iloc[-5:].mean() / data['Volume'].iloc[-20:].mean())
            },
            "ema_stack": {
                "ema_8": float(latest.get('EMA_8', 0)),
                "ema_13": float(latest.get('EMA_13', 0)),
                "ema_21": float(latest.get('EMA_21', 0)),
                "ema_34": float(latest.get('EMA_34', 0)),
                "ema_55": float(latest.get('EMA_55', 0)),
                "ema_89": float(latest.get('EMA_89', 0)),
                "alignment_bullish": bool(latest.get('EMA_8', 0) > latest.get('EMA_13', 0) > latest.get('EMA_21', 0)),
                "alignment_bearish": bool(latest.get('EMA_8', 0) < latest.get('EMA_13', 0) < latest.get('EMA_21', 0))
            },
            "system": "advanced_comprehensive"
        }
        
    except Exception as e:
        logger.error(f"Error in comprehensive analysis for {ticker}: {e}")
        raise HTTPException(status_code=500, detail=f"Analysis error: {str(e)}")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)