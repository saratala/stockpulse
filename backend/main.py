from fastapi import FastAPI, Body, HTTPException
import logging
from pydantic import BaseModel
import pandas as pd
from advanced_models import train_xgboost, predict_xgboost, train_lstm, predict_lstm, train_prophet, predict_prophet
from sqlalchemy import create_engine
import os
from dotenv import load_dotenv
from fastapi.middleware.cors import CORSMiddleware
from typing import List, Optional

# Import new modules
from screener_module import StockScreener
from heikin_ashi_signals import HeikinAshiSignalDetector
from enhanced_data_fetcher import EnhancedDataFetcher
from daily_scheduler import DailyScheduler

app = FastAPI()

# Enable CORS for all origins (for development)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

logger = logging.getLogger(__name__)

class TrainRequest(BaseModel):
    model: str  # 'xgboost', 'lstm', or 'prophet'
    X: list = None  # Features for XGBoost/LSTM
    y: list = None  # Target for XGBoost/LSTM
    df: list = None  # DataFrame records for Prophet
    date_col: str = 'date'
    target_col: str = 'close'

class PredictRequest(BaseModel):
    model: str
    X: list = None
    df: list = None
    future_dates: list = None
    scaler: dict = None  # For LSTM

class ScreenerRequest(BaseModel):
    tickers: Optional[List[str]] = None
    min_score: Optional[int] = 70
    max_results: Optional[int] = 50

class SignalRequest(BaseModel):
    tickers: List[str]
    period: Optional[str] = "3mo"

# Add DB connection for predictions API
load_dotenv()
DATABASE_URL = os.getenv("DATABASE_URL")
engine = create_engine(DATABASE_URL)

# Initialize new modules
screener = StockScreener(engine)
data_fetcher = EnhancedDataFetcher(engine)
signal_detector = HeikinAshiSignalDetector(data_fetcher)
daily_scheduler = DailyScheduler()

@app.get("/")
def read_root():
    return {"message": "Backend is working!"}

@app.get("/health")
async def health_check():
    return {"status": "healthy"}

@app.post("/train_advanced_model")
def train_advanced_model(req: TrainRequest):
    if req.model == 'xgboost':
        X = pd.DataFrame(req.X)
        y = req.y
        model = train_xgboost(X, y)
        return {"status": "trained", "model": "xgboost"}
    elif req.model == 'lstm':
        X = pd.DataFrame(req.X)
        y = req.y
        model, scaler = train_lstm(X, y)
        return {"status": "trained", "model": "lstm"}
    elif req.model == 'prophet':
        df = pd.DataFrame(req.df)
        model = train_prophet(df, req.date_col, req.target_col)
        return {"status": "trained", "model": "prophet"}
    else:
        return {"error": "Unknown model"}

@app.post("/predict_advanced_model")
def predict_advanced_model(req: PredictRequest):
    if req.model == 'xgboost':
        X = pd.DataFrame(req.X)
        # Model loading logic needed in production
        return {"error": "Model loading not implemented in this demo."}
    elif req.model == 'lstm':
        X = pd.DataFrame(req.X)
        # Model/scaler loading logic needed in production
        return {"error": "Model loading not implemented in this demo."}
    elif req.model == 'prophet':
        # Model loading logic needed in production
        return {"error": "Model loading not implemented in this demo."}
    else:
        return {"error": "Unknown model"}

@app.get("/predictions/model/{model_version}")
def get_predictions_by_model(model_version: str):
    query = '''
        SELECT p.ticker, p.prediction_date, p.target_date, p.predicted_movement_percent, p.predicted_direction, p.confidence_score, p.model_version,
               sp.close AS current_price
        FROM predictions p
        LEFT JOIN LATERAL (
            SELECT close FROM stock_prices sp2 WHERE sp2.ticker = p.ticker ORDER BY date DESC LIMIT 1
        ) sp ON TRUE
        WHERE p.model_version = %(model_version)s
        ORDER BY p.prediction_date DESC, p.confidence_score DESC
        LIMIT 100
    '''
    df = pd.read_sql(query, engine, params={"model_version": model_version})
    return df.to_dict(orient="records")

@app.get("/predictions/latest")
def get_latest_predictions():
    query = '''
        SELECT p.ticker, p.prediction_date, p.target_date, p.predicted_movement_percent, p.predicted_direction, p.confidence_score, p.model_version,
               sp.close AS current_price
        FROM predictions p
        LEFT JOIN LATERAL (
            SELECT close FROM stock_prices sp2 WHERE sp2.ticker = p.ticker ORDER BY date DESC LIMIT 1
        ) sp ON TRUE
        WHERE p.prediction_date = (
            SELECT MAX(prediction_date) FROM predictions
        )
        ORDER BY p.confidence_score DESC
        LIMIT 100
    '''
    df = pd.read_sql(query, engine)
    return df.to_dict(orient="records")

@app.get("/trending/weekly")
def get_weekly_trending_stocks():
    """
    Get trending stocks for this week based on multiple factors:
    - Price momentum (5-day and 1-week performance)
    - Volume spikes (compared to average)
    - Technical indicator strength
    - Recent sentiment scores
    """
    query = '''
        WITH weekly_performance AS (
            SELECT 
                sp.ticker,
                s.name,
                s.sector,
                s.industry,
                sp.close as current_price,
                LAG(sp.close, 5) OVER (PARTITION BY sp.ticker ORDER BY sp.date) as price_5d_ago,
                LAG(sp.close, 7) OVER (PARTITION BY sp.ticker ORDER BY sp.date) as price_7d_ago,
                sp.volume,
                AVG(sp.volume) OVER (
                    PARTITION BY sp.ticker 
                    ORDER BY sp.date 
                    ROWS BETWEEN 20 PRECEDING AND 1 PRECEDING
                ) as avg_volume_20d,
                ROW_NUMBER() OVER (PARTITION BY sp.ticker ORDER BY sp.date DESC) as rn
            FROM stock_prices sp
            JOIN stocks s ON sp.ticker = s.ticker
            WHERE sp.date >= NOW() - INTERVAL '30 days'
        ),
        price_momentum AS (
            SELECT 
                ticker,
                name,
                sector,
                industry,
                current_price,
                CASE 
                    WHEN price_5d_ago IS NOT NULL AND price_5d_ago > 0 
                    THEN ((current_price - price_5d_ago) / price_5d_ago) * 100
                    ELSE 0
                END as return_5d,
                CASE 
                    WHEN price_7d_ago IS NOT NULL AND price_7d_ago > 0 
                    THEN ((current_price - price_7d_ago) / price_7d_ago) * 100
                    ELSE 0
                END as return_7d,
                CASE 
                    WHEN avg_volume_20d > 0 
                    THEN volume / avg_volume_20d
                    ELSE 1
                END as volume_ratio
            FROM weekly_performance
            WHERE rn = 1
        ),
        latest_technicals AS (
            SELECT 
                ticker,
                rsi,
                macd,
                macd_signal,
                macd_hist,
                adx,
                ROW_NUMBER() OVER (PARTITION BY ticker ORDER BY date DESC) as rn
            FROM technicals
            WHERE date >= NOW() - INTERVAL '7 days'
        ),
        recent_sentiment AS (
            SELECT 
                ticker,
                AVG(sentiment_score) as avg_sentiment,
                COUNT(*) as sentiment_count
            FROM sentiment_scores
            WHERE created_at >= NOW() - INTERVAL '7 days'
            GROUP BY ticker
        ),
        trending_scores AS (
            SELECT 
                pm.ticker,
                pm.name,
                pm.sector,
                pm.industry,
                pm.current_price,
                pm.return_5d,
                pm.return_7d,
                pm.volume_ratio,
                COALESCE(t.rsi, 50) as rsi,
                COALESCE(t.macd, 0) as macd,
                COALESCE(t.macd_signal, 0) as macd_signal,
                COALESCE(t.macd_hist, 0) as macd_hist,
                COALESCE(t.adx, 25) as adx,
                COALESCE(rs.avg_sentiment, 0) as avg_sentiment,
                COALESCE(rs.sentiment_count, 0) as sentiment_count,
                -- Calculate composite trending score
                (
                    -- Price momentum (40% weight)
                    GREATEST(0, LEAST(40, pm.return_5d * 2)) +
                    GREATEST(0, LEAST(30, pm.return_7d * 1.5)) +
                    
                    -- Volume spike (20% weight)
                    GREATEST(0, LEAST(20, (pm.volume_ratio - 1) * 10)) +
                    
                    -- Technical strength (25% weight)
                    CASE 
                        WHEN t.rsi BETWEEN 40 AND 70 AND t.macd > t.macd_signal THEN 15
                        WHEN t.rsi BETWEEN 30 AND 80 THEN 10
                        ELSE 5
                    END +
                    CASE WHEN t.adx > 25 THEN 10 ELSE 5 END +
                    
                    -- Sentiment boost (15% weight)
                    CASE 
                        WHEN rs.avg_sentiment > 0.3 THEN 15
                        WHEN rs.avg_sentiment > 0.1 THEN 10
                        WHEN rs.avg_sentiment > 0 THEN 5
                        ELSE 0
                    END
                ) as trending_score
            FROM price_momentum pm
            LEFT JOIN latest_technicals t ON pm.ticker = t.ticker AND t.rn = 1
            LEFT JOIN recent_sentiment rs ON pm.ticker = rs.ticker
            WHERE pm.current_price > 1  -- Filter out penny stocks
        )
        SELECT 
            ticker,
            name,
            sector,
            industry,
            current_price,
            ROUND(return_5d::numeric, 2) as return_5d_percent,
            ROUND(return_7d::numeric, 2) as return_7d_percent,
            ROUND(volume_ratio::numeric, 2) as volume_ratio,
            ROUND(rsi::numeric, 2) as rsi,
            ROUND(macd::numeric, 4) as macd,
            ROUND(macd_signal::numeric, 4) as macd_signal,
            ROUND(adx::numeric, 2) as adx,
            ROUND(avg_sentiment::numeric, 3) as avg_sentiment,
            sentiment_count,
            ROUND(trending_score::numeric, 1) as trending_score,
            CASE 
                WHEN trending_score >= 80 THEN 'Very Hot'
                WHEN trending_score >= 60 THEN 'Hot'
                WHEN trending_score >= 40 THEN 'Trending'
                WHEN trending_score >= 20 THEN 'Moderate'
                ELSE 'Weak'
            END as trend_strength
        FROM trending_scores
        WHERE trending_score > 0
        ORDER BY trending_score DESC, return_7d DESC
        LIMIT 50
    '''
    
    try:
        df = pd.read_sql(query, engine)
        
        # Add additional insights
        result = {
            "report_date": pd.Timestamp.now().strftime("%Y-%m-%d %H:%M:%S"),
            "total_stocks_analyzed": len(df),
            "trending_stocks": df.to_dict(orient="records"),
            "summary": {
                "very_hot": len(df[df['trend_strength'] == 'Very Hot']),
                "hot": len(df[df['trend_strength'] == 'Hot']),
                "trending": len(df[df['trend_strength'] == 'Trending']),
                "top_sectors": df.groupby('sector')['trending_score'].mean().sort_values(ascending=False).head(5).to_dict() if not df.empty else {}
            }
        }
        
        return result
        
    except Exception as e:
        logger.error(f"Error generating weekly trending report: {e}")
        return {"error": str(e), "trending_stocks": []}

@app.get("/trending/movers")
def get_market_movers():
    """
    Get top market movers (gainers and losers) for the current week
    """
    query = '''
        WITH weekly_movers AS (
            SELECT 
                sp.ticker,
                s.name,
                s.sector,
                sp.close as current_price,
                LAG(sp.close, 7) OVER (PARTITION BY sp.ticker ORDER BY sp.date) as price_7d_ago,
                sp.volume,
                AVG(sp.volume) OVER (
                    PARTITION BY sp.ticker 
                    ORDER BY sp.date 
                    ROWS BETWEEN 20 PRECEDING AND 1 PRECEDING
                ) as avg_volume_20d,
                ROW_NUMBER() OVER (PARTITION BY sp.ticker ORDER BY sp.date DESC) as rn
            FROM stock_prices sp
            JOIN stocks s ON sp.ticker = s.ticker
            WHERE sp.date >= NOW() - INTERVAL '30 days'
        ),
        performance_calc AS (
            SELECT 
                ticker,
                name,
                sector,
                current_price,
                CASE 
                    WHEN price_7d_ago IS NOT NULL AND price_7d_ago > 0 
                    THEN ((current_price - price_7d_ago) / price_7d_ago) * 100
                    ELSE 0
                END as weekly_return,
                CASE 
                    WHEN avg_volume_20d > 0 
                    THEN volume / avg_volume_20d
                    ELSE 1
                END as volume_ratio
            FROM weekly_movers
            WHERE rn = 1 AND price_7d_ago IS NOT NULL
        )
        SELECT 
            'gainers' as category,
            ticker,
            name,
            sector,
            current_price,
            ROUND(weekly_return::numeric, 2) as weekly_return_percent,
            ROUND(volume_ratio::numeric, 2) as volume_ratio
        FROM performance_calc
        WHERE weekly_return > 0
        ORDER BY weekly_return DESC
        LIMIT 10
        
        UNION ALL
        
        SELECT 
            'losers' as category,
            ticker,
            name,
            sector,
            current_price,
            ROUND(weekly_return::numeric, 2) as weekly_return_percent,
            ROUND(volume_ratio::numeric, 2) as volume_ratio
        FROM performance_calc
        WHERE weekly_return < 0
        ORDER BY weekly_return ASC
        LIMIT 10
    '''
    
    try:
        df = pd.read_sql(query, engine)
        
        gainers = df[df['category'] == 'gainers'].drop('category', axis=1).to_dict(orient="records")
        losers = df[df['category'] == 'losers'].drop('category', axis=1).to_dict(orient="records")
        
        return {
            "report_date": pd.Timestamp.now().strftime("%Y-%m-%d %H:%M:%S"),
            "gainers": gainers,
            "losers": losers
        }
        
    except Exception as e:
        logger.error(f"Error generating market movers report: {e}")
        return {"error": str(e), "gainers": [], "losers": []}

@app.get("/screener/run")
def run_stock_screener(
    min_score: int = 70,
    max_results: int = 50,
    include_signals: bool = True
):
    """
    Run the advanced stock screener with EMA stack, ADX, and Stochastic filters
    """
    try:
        logger.info(f"Running stock screener with min_score={min_score}")
        
        # Run screening
        results = screener.run_screening()
        
        # Generate report
        report = screener.generate_screening_report(results)
        
        # Filter by minimum score
        top_candidates = screener.get_top_candidates(results, min_score=min_score)
        
        # Limit results
        limited_results = top_candidates[:max_results]
        
        # Include signal analysis if requested
        if include_signals and limited_results:
            candidate_tickers = [stock['ticker'] for stock in limited_results]
            signal_results = signal_detector.scan_multiple_stocks(candidate_tickers)
            
            # Combine screening and signal data
            combined_results = []
            for stock in limited_results:
                ticker = stock['ticker']
                signal_data = next((s for s in signal_results if s['ticker'] == ticker), None)
                
                combined_stock = {
                    **stock,
                    'signal_analysis': signal_data if signal_data else None
                }
                combined_results.append(combined_stock)
            
            return {
                "screening_date": report['report_date'],
                "total_analyzed": report['summary']['total_analyzed'],
                "candidates_found": len(limited_results),
                "screening_summary": report['summary']['screen_breakdown'],
                "candidates": combined_results
            }
        else:
            return {
                "screening_date": report['report_date'],
                "total_analyzed": report['summary']['total_analyzed'],
                "candidates_found": len(limited_results),
                "screening_summary": report['summary']['screen_breakdown'],
                "candidates": limited_results
            }
            
    except Exception as e:
        logger.error(f"Error running stock screener: {e}")
        return {"error": str(e), "candidates": []}

@app.get("/screener/signals")
def get_heikin_ashi_signals(
    tickers: str = None,
    period: str = "3mo",
    min_confidence: int = 40
):
    """
    Get Heikin Ashi signals for specified tickers or screened candidates
    """
    try:
        # Parse tickers from comma-separated string
        if tickers:
            ticker_list = [t.strip().upper() for t in tickers.split(',')]
        else:
            # Use top screened candidates
            screening_results = screener.run_screening()
            top_candidates = screener.get_top_candidates(screening_results, min_score=70)
            ticker_list = [stock['ticker'] for stock in top_candidates[:20]]
        
        logger.info(f"Analyzing signals for {len(ticker_list)} tickers")
        
        # Run signal detection
        signal_results = signal_detector.scan_multiple_stocks(ticker_list, period)
        
        # Filter by minimum confidence
        filtered_signals = [
            signal for signal in signal_results 
            if signal.get('primary_confidence', 0) >= min_confidence and 
               signal.get('primary_signal') != 'NEUTRAL'
        ]
        
        # Generate signal report
        signal_report = signal_detector.generate_signal_report(signal_results)
        
        return {
            "analysis_date": signal_report['report_date'],
            "total_analyzed": signal_report['summary']['total_analyzed'],
            "signals_found": len(filtered_signals),
            "summary": signal_report['summary'],
            "signals": filtered_signals
        }
        
    except Exception as e:
        logger.error(f"Error getting Heikin Ashi signals: {e}")
        return {"error": str(e), "signals": []}

@app.get("/screener/comprehensive/{ticker}")
def get_comprehensive_analysis(ticker: str):
    """
    Get comprehensive technical analysis for a single ticker
    """
    try:
        ticker = ticker.upper()
        logger.info(f"Running comprehensive analysis for {ticker}")
        
        # Get comprehensive data analysis
        analysis = data_fetcher.get_comprehensive_analysis(ticker)
        
        if 'error' in analysis:
            raise HTTPException(status_code=404, detail=analysis['error'])
        
        # Get screening analysis
        screening_result = screener.screen_single_stock(ticker)
        
        # Get signal analysis
        signal_result = signal_detector.analyze_single_stock(ticker)
        
        return {
            "ticker": ticker,
            "analysis_date": analysis['analysis_date'],
            "comprehensive_analysis": analysis,
            "screening_analysis": screening_result,
            "signal_analysis": signal_result
        }
        
    except Exception as e:
        logger.error(f"Error getting comprehensive analysis for {ticker}: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/screener/daily-results")
def get_daily_screening_results():
    """
    Get the latest daily screening results
    """
    try:
        latest_results = daily_scheduler.get_latest_results()
        
        if not latest_results:
            return {"message": "No daily screening results available", "results": None}
        
        return {
            "latest_results": latest_results,
            "has_results": True
        }
        
    except Exception as e:
        logger.error(f"Error getting daily screening results: {e}")
        return {"error": str(e), "results": None}

@app.post("/screener/run-daily")
def trigger_daily_screening():
    """
    Manually trigger daily screening pipeline
    """
    try:
        logger.info("Manually triggering daily screening pipeline")
        
        results = daily_scheduler.run_once()
        
        return {
            "message": "Daily screening completed successfully",
            "results": results,
            "signals_found": len(results.get('high_quality_signals', [])),
            "timestamp": results.get('run_date')
        }
        
    except Exception as e:
        logger.error(f"Error running daily screening: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/screener/market-regime/{ticker}")
def get_market_regime(ticker: str):
    """
    Get market regime analysis for a ticker
    """
    try:
        ticker = ticker.upper()
        
        # Fetch comprehensive data
        df = data_fetcher.fetch_comprehensive_data(ticker)
        if df is None:
            raise HTTPException(status_code=404, detail=f"Could not fetch data for {ticker}")
        
        # Get market regime
        regime = data_fetcher.get_market_regime(df)
        
        # Get key levels
        key_levels = data_fetcher.get_key_levels(df)
        
        # Get volume analysis
        volume_analysis = data_fetcher.analyze_volume_profile(df)
        
        return {
            "ticker": ticker,
            "analysis_date": pd.Timestamp.now().strftime("%Y-%m-%d %H:%M:%S"),
            "market_regime": regime,
            "key_levels": key_levels,
            "volume_analysis": volume_analysis
        }
        
    except Exception as e:
        logger.error(f"Error getting market regime for {ticker}: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/screener/batch-analysis")
def batch_technical_analysis(
    tickers: str,
    period: str = "6mo"
):
    """
    Run batch technical analysis on multiple tickers
    """
    try:
        # Parse tickers from comma-separated string
        ticker_list = [t.strip().upper() for t in tickers.split(',')]
        
        if len(ticker_list) > 20:
            raise HTTPException(status_code=400, detail="Maximum 20 tickers allowed")
        
        logger.info(f"Running batch analysis for {len(ticker_list)} tickers")
        
        # Run batch analysis
        results = data_fetcher.batch_comprehensive_analysis(ticker_list)
        
        return {
            "analysis_date": pd.Timestamp.now().strftime("%Y-%m-%d %H:%M:%S"),
            "total_analyzed": len(results),
            "results": results
        }
        
    except Exception as e:
        logger.error(f"Error running batch analysis: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/predictions/history")
def get_prediction_history(
    ticker: Optional[str] = None,
    hours: Optional[int] = 24,
    limit: Optional[int] = 100
):
    """
    Get prediction history from signal_predictions table
    """
    try:
        # Build query based on parameters
        where_conditions = []
        params = []
        
        if ticker:
            where_conditions.append("ticker = %s")
            params.append(ticker.upper())
        
        if hours:
            where_conditions.append("timestamp >= NOW() - INTERVAL '%s hours'")
            params.append(hours)
        
        where_clause = "WHERE " + " AND ".join(where_conditions) if where_conditions else ""
        
        query = f"""
            SELECT 
                ticker,
                timestamp,
                current_price,
                signal_type,
                confidence,
                primary_reasons,
                screening_score,
                sector,
                predicted_price_1h,
                predicted_price_1d,
                predicted_price_1w,
                volume,
                rsi,
                macd,
                bollinger_position,
                sentiment_score,
                sentiment_confidence,
                sentiment_impact,
                news_count
            FROM signal_predictions
            {where_clause}
            ORDER BY timestamp DESC
            LIMIT %s
        """
        
        params.append(limit)
        
        # Execute query
        df = pd.read_sql_query(query, engine, params=params)
        
        if df.empty:
            return {
                "total_predictions": 0,
                "predictions": []
            }
        
        # Convert to records
        predictions = []
        for _, row in df.iterrows():
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
                "news_count": int(row['news_count']) if row['news_count'] else 0
            })
        
        return {
            "total_predictions": len(predictions),
            "predictions": predictions
        }
        
    except Exception as e:
        logger.error(f"Error fetching prediction history: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/predictions/summary")
def get_prediction_summary(hours: Optional[int] = 24):
    """
    Get summary statistics for predictions
    """
    try:
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
        
        df = pd.read_sql_query(query, engine, params=[hours])
        
        summary = []
        for _, row in df.iterrows():
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
        logger.error(f"Error fetching prediction summary: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/predictions/ticker/{ticker}")
def get_ticker_predictions(
    ticker: str,
    hours: Optional[int] = 168  # Default to 1 week
):
    """
    Get prediction history for a specific ticker
    """
    try:
        query = """
            SELECT 
                timestamp,
                current_price,
                signal_type,
                confidence,
                primary_reasons,
                screening_score,
                predicted_price_1h,
                predicted_price_1d,
                predicted_price_1w,
                volume,
                rsi,
                macd,
                bollinger_position,
                sentiment_score,
                sentiment_confidence,
                sentiment_impact,
                news_count
            FROM signal_predictions
            WHERE ticker = %s AND timestamp >= NOW() - INTERVAL '%s hours'
            ORDER BY timestamp DESC
        """
        
        df = pd.read_sql_query(query, engine, params=[ticker.upper(), hours])
        
        if df.empty:
            return {
                "ticker": ticker.upper(),
                "predictions": [],
                "total_predictions": 0
            }
        
        predictions = []
        for _, row in df.iterrows():
            predictions.append({
                "timestamp": row['timestamp'].strftime("%Y-%m-%d %H:%M:%S"),
                "current_price": float(row['current_price']),
                "signal_type": row['signal_type'],
                "confidence": float(row['confidence']),
                "primary_reasons": row['primary_reasons'] if row['primary_reasons'] else [],
                "screening_score": float(row['screening_score']),
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
                "news_count": int(row['news_count']) if row['news_count'] else 0
            })
        
        return {
            "ticker": ticker.upper(),
            "predictions": predictions,
            "total_predictions": len(predictions)
        }
        
    except Exception as e:
        logger.error(f"Error fetching predictions for ticker {ticker}: {e}")
        raise HTTPException(status_code=500, detail=str(e))