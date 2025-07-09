#!/usr/bin/env python3
"""
Real-time Signal Prediction Scheduler
Runs stock analysis every 5 minutes and saves predictions to database
"""

import asyncio
import asyncpg
import aiohttp
import json
import logging
from datetime import datetime, timedelta
from typing import List, Dict, Optional
import os
from dataclasses import dataclass
import yfinance as yf
import pandas as pd
import numpy as np
from screener_module import AdvancedScreener
from heikin_ashi_signals import HeikinAshiSignalDetector
import traceback

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

@dataclass
class PredictionData:
    ticker: str
    current_price: float
    signal_type: str
    confidence: float
    primary_reasons: List[str]
    screening_score: float
    sector: str
    predicted_price_1h: Optional[float] = None
    predicted_price_1d: Optional[float] = None
    predicted_price_1w: Optional[float] = None
    volume: Optional[int] = None
    rsi: Optional[float] = None
    macd: Optional[float] = None
    bollinger_position: Optional[float] = None

class SignalPredictionScheduler:
    def __init__(self):
        self.db_host = os.getenv('DB_HOST', 'localhost')
        self.db_port = int(os.getenv('DB_PORT', '5432'))
        self.db_name = os.getenv('DB_NAME', 'stockpulse')
        self.db_user = os.getenv('DB_USER', 'postgres')
        self.db_password = os.getenv('DB_PASSWORD', 'password')
        
        self.screener = AdvancedScreener()
        self.signal_detector = HeikinAshiSignalDetector()
        
        # Default tickers to monitor
        self.default_tickers = [
            'AAPL', 'GOOGL', 'MSFT', 'AMZN', 'TSLA', 'META', 'NVDA', 'NFLX',
            'SPY', 'QQQ', 'IWM', 'DIA', 'VTI', 'AMD', 'INTC', 'CRM', 'ORCL',
            'JPM', 'BAC', 'GS', 'C', 'WFC', 'JNJ', 'PFE', 'UNH', 'CVX', 'XOM'
        ]
        
        self.running = False
        self.db_pool = None

    async def init_db_pool(self):
        """Initialize database connection pool"""
        try:
            self.db_pool = await asyncpg.create_pool(
                host=self.db_host,
                port=self.db_port,
                database=self.db_name,
                user=self.db_user,
                password=self.db_password,
                min_size=5,
                max_size=20
            )
            logger.info("Database connection pool initialized")
        except Exception as e:
            logger.error(f"Failed to initialize database pool: {e}")
            raise

    async def close_db_pool(self):
        """Close database connection pool"""
        if self.db_pool:
            await self.db_pool.close()
            logger.info("Database connection pool closed")

    async def get_active_tickers(self) -> List[str]:
        """Get list of active tickers from database, fallback to default"""
        try:
            async with self.db_pool.acquire() as conn:
                result = await conn.fetch(
                    "SELECT DISTINCT ticker FROM stocks WHERE ticker IS NOT NULL LIMIT 50"
                )
                if result:
                    return [row['ticker'] for row in result]
        except Exception as e:
            logger.warning(f"Failed to get tickers from database: {e}")
        
        return self.default_tickers

    def calculate_price_predictions(self, data: Dict, signal_type: str, confidence: float) -> Dict[str, float]:
        """Calculate simple price predictions based on signal and technical indicators"""
        try:
            current_price = data.get('current_price', 0)
            if current_price <= 0:
                return {}
            
            # Base prediction multipliers based on signal type and confidence
            confidence_factor = confidence / 100.0
            
            if signal_type == 'BULLISH':
                base_multiplier = 1.0 + (0.02 * confidence_factor)  # Up to 2% for high confidence
            elif signal_type == 'BEARISH':
                base_multiplier = 1.0 - (0.02 * confidence_factor)  # Down to -2% for high confidence
            else:  # NEUTRAL
                base_multiplier = 1.0 + (0.005 * (confidence_factor - 0.5))  # Small movements
            
            # Time-based decay (shorter predictions are more reliable)
            predictions = {}
            
            # 1-hour prediction
            hourly_volatility = 0.005  # 0.5% typical hourly volatility
            predictions['predicted_price_1h'] = current_price * (
                base_multiplier * (1 - hourly_volatility) + 
                hourly_volatility * np.random.normal(1, 0.1)
            )
            
            # 1-day prediction
            daily_volatility = 0.02  # 2% typical daily volatility
            predictions['predicted_price_1d'] = current_price * (
                base_multiplier * (1 - daily_volatility) + 
                daily_volatility * np.random.normal(1, 0.2)
            )
            
            # 1-week prediction (less reliable)
            weekly_volatility = 0.05  # 5% typical weekly volatility
            predictions['predicted_price_1w'] = current_price * (
                base_multiplier * (1 - weekly_volatility) + 
                weekly_volatility * np.random.normal(1, 0.3)
            )
            
            # Ensure predictions are positive
            for key in predictions:
                if predictions[key] <= 0:
                    predictions[key] = current_price * 0.95  # Fallback to 5% below current
            
            return predictions
            
        except Exception as e:
            logger.error(f"Error calculating price predictions: {e}")
            return {}

    async def analyze_ticker(self, ticker: str) -> Optional[PredictionData]:
        """Analyze a single ticker and return prediction data"""
        try:
            # Get current stock data
            stock = yf.Ticker(ticker)
            hist = stock.history(period='5d', interval='1m')
            
            if hist.empty:
                logger.warning(f"No data available for {ticker}")
                return None
            
            # Get current price
            current_price = hist['Close'].iloc[-1]
            volume = int(hist['Volume'].iloc[-1])
            
            # Get stock info
            info = stock.info
            sector = info.get('sector', 'Unknown')
            
            # Run screener analysis
            screener_results = await self.run_screener_analysis(ticker)
            if not screener_results:
                return None
            
            # Get signal analysis
            signal_analysis = screener_results.get('signal_analysis', {})
            signal_type = signal_analysis.get('primary_signal', 'NEUTRAL')
            confidence = signal_analysis.get('primary_confidence', 50.0)
            reasons = signal_analysis.get('primary_reasons', [])
            screening_score = screener_results.get('screening_score', 50.0)
            
            # Calculate technical indicators
            technical_data = self.calculate_technical_indicators(hist)
            
            # Calculate price predictions
            predictions = self.calculate_price_predictions(
                {'current_price': current_price}, 
                signal_type, 
                confidence
            )
            
            return PredictionData(
                ticker=ticker,
                current_price=current_price,
                signal_type=signal_type,
                confidence=confidence,
                primary_reasons=reasons,
                screening_score=screening_score,
                sector=sector,
                predicted_price_1h=predictions.get('predicted_price_1h'),
                predicted_price_1d=predictions.get('predicted_price_1d'),
                predicted_price_1w=predictions.get('predicted_price_1w'),
                volume=volume,
                rsi=technical_data.get('rsi'),
                macd=technical_data.get('macd'),
                bollinger_position=technical_data.get('bollinger_position')
            )
            
        except Exception as e:
            logger.error(f"Error analyzing ticker {ticker}: {e}")
            return None

    async def run_screener_analysis(self, ticker: str) -> Optional[Dict]:
        """Run the screener analysis for a ticker"""
        try:
            # This would typically call your existing screener
            # For now, we'll simulate the analysis
            results = await self.screener.run_comprehensive_analysis(ticker)
            return results
        except Exception as e:
            logger.error(f"Error running screener analysis for {ticker}: {e}")
            return None

    def calculate_technical_indicators(self, hist: pd.DataFrame) -> Dict:
        """Calculate technical indicators from price history"""
        try:
            close_prices = hist['Close']
            
            # RSI
            rsi = None
            if len(close_prices) >= 14:
                delta = close_prices.diff()
                gain = (delta.where(delta > 0, 0)).rolling(window=14).mean()
                loss = (-delta.where(delta < 0, 0)).rolling(window=14).mean()
                rs = gain / loss
                rsi = 100 - (100 / (1 + rs)).iloc[-1]
            
            # MACD
            macd = None
            if len(close_prices) >= 26:
                exp1 = close_prices.ewm(span=12).mean()
                exp2 = close_prices.ewm(span=26).mean()
                macd = (exp1 - exp2).iloc[-1]
            
            # Bollinger Bands position
            bollinger_position = None
            if len(close_prices) >= 20:
                sma = close_prices.rolling(window=20).mean()
                std = close_prices.rolling(window=20).std()
                upper_band = sma + (std * 2)
                lower_band = sma - (std * 2)
                
                current_price = close_prices.iloc[-1]
                upper = upper_band.iloc[-1]
                lower = lower_band.iloc[-1]
                
                # Position between bands (0 = lower band, 1 = upper band)
                if upper != lower:
                    bollinger_position = (current_price - lower) / (upper - lower)
            
            return {
                'rsi': rsi,
                'macd': macd,
                'bollinger_position': bollinger_position
            }
            
        except Exception as e:
            logger.error(f"Error calculating technical indicators: {e}")
            return {}

    async def save_prediction(self, prediction: PredictionData):
        """Save prediction to database"""
        try:
            async with self.db_pool.acquire() as conn:
                await conn.execute("""
                    INSERT INTO signal_predictions (
                        ticker, timestamp, current_price, signal_type, confidence,
                        primary_reasons, screening_score, sector, predicted_price_1h,
                        predicted_price_1d, predicted_price_1w, volume, rsi, macd,
                        bollinger_position
                    ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15)
                """, 
                    prediction.ticker,
                    datetime.now(),
                    prediction.current_price,
                    prediction.signal_type,
                    prediction.confidence,
                    prediction.primary_reasons,
                    prediction.screening_score,
                    prediction.sector,
                    prediction.predicted_price_1h,
                    prediction.predicted_price_1d,
                    prediction.predicted_price_1w,
                    prediction.volume,
                    prediction.rsi,
                    prediction.macd,
                    prediction.bollinger_position
                )
            
            logger.info(f"Saved prediction for {prediction.ticker}: {prediction.signal_type} ({prediction.confidence}%)")
            
        except Exception as e:
            logger.error(f"Error saving prediction for {prediction.ticker}: {e}")

    async def run_prediction_cycle(self):
        """Run a single prediction cycle for all tickers"""
        try:
            tickers = await self.get_active_tickers()
            logger.info(f"Starting prediction cycle for {len(tickers)} tickers")
            
            # Process tickers in batches to avoid overwhelming the system
            batch_size = 5
            for i in range(0, len(tickers), batch_size):
                batch = tickers[i:i + batch_size]
                
                # Process batch concurrently
                tasks = [self.analyze_ticker(ticker) for ticker in batch]
                predictions = await asyncio.gather(*tasks, return_exceptions=True)
                
                # Save valid predictions
                for prediction in predictions:
                    if isinstance(prediction, PredictionData):
                        await self.save_prediction(prediction)
                    elif isinstance(prediction, Exception):
                        logger.error(f"Prediction failed: {prediction}")
                
                # Small delay between batches
                await asyncio.sleep(1)
            
            logger.info("Prediction cycle completed")
            
        except Exception as e:
            logger.error(f"Error in prediction cycle: {e}")
            traceback.print_exc()

    async def start_scheduler(self):
        """Start the 5-minute prediction scheduler"""
        self.running = True
        await self.init_db_pool()
        
        logger.info("Starting signal prediction scheduler (5-minute intervals)")
        
        while self.running:
            try:
                start_time = datetime.now()
                await self.run_prediction_cycle()
                
                # Calculate time until next 5-minute interval
                elapsed = (datetime.now() - start_time).total_seconds()
                sleep_time = max(0, 300 - elapsed)  # 300 seconds = 5 minutes
                
                logger.info(f"Cycle completed in {elapsed:.1f}s, sleeping for {sleep_time:.1f}s")
                await asyncio.sleep(sleep_time)
                
            except Exception as e:
                logger.error(f"Error in scheduler loop: {e}")
                await asyncio.sleep(60)  # Wait 1 minute before retrying

    async def stop_scheduler(self):
        """Stop the scheduler"""
        self.running = False
        await self.close_db_pool()
        logger.info("Scheduler stopped")

async def main():
    """Main entry point"""
    scheduler = SignalPredictionScheduler()
    
    try:
        await scheduler.start_scheduler()
    except KeyboardInterrupt:
        logger.info("Received interrupt signal")
    finally:
        await scheduler.stop_scheduler()

if __name__ == "__main__":
    asyncio.run(main())