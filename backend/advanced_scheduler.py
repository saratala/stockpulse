#!/usr/bin/env python3
"""
Advanced Stock Prediction Scheduler
Uses the sophisticated screener and Heikin Ashi signal detection system
Runs every 5 minutes to collect high-quality trading signals
"""

import asyncio
import asyncpg
import pandas as pd
import numpy as np
from datetime import datetime, timedelta
import os
import logging
import traceback
from typing import Dict, List, Optional

# Import your advanced modules
from screener_module import StockScreener
from heikin_ashi_signals import HeikinAshiSignalDetector
from enhanced_data_fetcher import EnhancedDataFetcher

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

class AdvancedPredictionScheduler:
    """
    Advanced scheduler using sophisticated technical analysis
    """
    
    def __init__(self):
        self.db_config = {
            'host': os.getenv('DB_HOST', 'localhost'),
            'port': int(os.getenv('DB_PORT', '5432')),
            'database': os.getenv('DB_NAME', 'stockpulse'),
            'user': os.getenv('DB_USER', 'postgres'),
            'password': os.getenv('DB_PASSWORD', 'postgres')
        }
        
        # Initialize the advanced modules
        self.screener = StockScreener()
        self.signal_detector = HeikinAshiSignalDetector()
        self.data_fetcher = EnhancedDataFetcher()
        
        # Screening parameters
        self.screening_params = {
            'min_score': 60,  # Lower threshold for more candidates
            'max_results': 50,
            'include_signals': True,
            'min_market_cap': 1e11,  # $100B market cap
            'min_adx': 20,
            'max_stoch_k': 40,
            'min_volume_ratio': 1.0
        }
        
        self.running = False

    async def get_db_connection(self):
        """Get database connection"""
        return await asyncpg.connect(**self.db_config)

    async def run_advanced_screening(self) -> List[Dict]:
        """Run advanced screening using your sophisticated system"""
        try:
            logger.info("Running advanced stock screening...")
            
            # Get screening results using your advanced screener
            screening_results = self.screener.screen_stocks(
                min_score=self.screening_params['min_score'],
                max_results=self.screening_params['max_results'],
                detailed=True
            )
            
            logger.info(f"Screening completed: {len(screening_results)} candidates found")
            return screening_results
            
        except Exception as e:
            logger.error(f"Error in advanced screening: {e}")
            traceback.print_exc()
            return []

    async def analyze_with_heikin_ashi(self, ticker: str) -> Optional[Dict]:
        """Analyze ticker using Heikin Ashi signal detection"""
        try:
            # Get comprehensive signal analysis
            signal_result = self.signal_detector.detect_signals(
                ticker=ticker,
                period='6mo',
                interval='1d'
            )
            
            if not signal_result:
                logger.warning(f"No signal result for {ticker}")
                return None
            
            # Get enhanced data for additional metrics
            enhanced_data = self.data_fetcher.get_comprehensive_data(
                ticker=ticker,
                period='6mo'
            )
            
            if enhanced_data.empty:
                logger.warning(f"No enhanced data for {ticker}")
                return None
            
            # Get current price and calculate predictions
            current_price = enhanced_data['Close'].iloc[-1]
            
            # Extract signal information
            signal_type = signal_result.get('signal', 'NEUTRAL')
            confidence = signal_result.get('confidence', 50.0)
            reasons = signal_result.get('reasons', [])
            
            # Calculate screening score (combination of screener score and signal strength)
            base_score = signal_result.get('score', 50.0)
            signal_boost = (confidence - 50) * 0.5  # Boost/penalty based on confidence
            screening_score = min(100, max(0, base_score + signal_boost))
            
            # Calculate advanced price predictions using multiple timeframes
            predictions = self.calculate_advanced_predictions(
                enhanced_data, signal_type, confidence
            )
            
            # Get additional technical indicators
            latest_data = enhanced_data.iloc[-1]
            
            return {
                'ticker': ticker,
                'current_price': float(current_price),
                'signal_type': signal_type,
                'confidence': float(confidence),
                'screening_score': float(screening_score),
                'primary_reasons': reasons,
                'sector': signal_result.get('sector', 'Unknown'),
                'predicted_price_1h': predictions.get('price_1h'),
                'predicted_price_1d': predictions.get('price_1d'),
                'predicted_price_1w': predictions.get('price_1w'),
                'volume': int(latest_data.get('Volume', 0)),
                'rsi': float(latest_data.get('RSI_14', 50)),
                'macd': float(latest_data.get('MACD_12_26_9', 0)),
                'adx': float(latest_data.get('ADX_14', 0)),
                'stoch_k': float(latest_data.get('STOCHk_14_3_3', 50)),
                'bollinger_position': self.calculate_bollinger_position(enhanced_data),
                'ema_stack_score': self.calculate_ema_stack_score(enhanced_data),
                'atr': float(latest_data.get('ATR_14', 0))
            }
            
        except Exception as e:
            logger.error(f"Error analyzing {ticker} with Heikin Ashi: {e}")
            return None

    def calculate_advanced_predictions(self, df: pd.DataFrame, signal_type: str, confidence: float) -> Dict:
        """Calculate sophisticated price predictions using multiple factors"""
        try:
            current_price = df['Close'].iloc[-1]
            
            # Base prediction factors
            confidence_factor = confidence / 100.0
            
            # ATR-based volatility adjustment
            atr = df.get('ATR_14', pd.Series([current_price * 0.02])).iloc[-1]
            volatility_factor = atr / current_price
            
            # Trend strength from EMA stack
            ema_8 = df.get('EMA_8', df['Close']).iloc[-1]
            ema_21 = df.get('EMA_21', df['Close']).iloc[-1]
            ema_55 = df.get('EMA_55', df['Close']).iloc[-1]
            
            trend_strength = 0.5  # neutral default
            if ema_8 > ema_21 > ema_55:
                trend_strength = 0.8  # strong uptrend
            elif ema_8 < ema_21 < ema_55:
                trend_strength = 0.2  # strong downtrend
            
            # Volume momentum
            volume_ratio = df['Volume'].iloc[-5:].mean() / df['Volume'].iloc[-20:].mean()
            volume_factor = min(1.5, max(0.5, volume_ratio))
            
            # Calculate predictions with sophisticated logic
            if signal_type == 'BULLISH':
                # Bullish predictions with trend and volume confirmation
                base_move_1h = 0.003 * confidence_factor * trend_strength * volume_factor
                base_move_1d = 0.015 * confidence_factor * trend_strength * volume_factor
                base_move_1w = 0.04 * confidence_factor * trend_strength * volume_factor
                
                # Add volatility-based uncertainty
                price_1h = current_price * (1 + base_move_1h + np.random.normal(0, volatility_factor * 0.1))
                price_1d = current_price * (1 + base_move_1d + np.random.normal(0, volatility_factor * 0.2))
                price_1w = current_price * (1 + base_move_1w + np.random.normal(0, volatility_factor * 0.3))
                
            elif signal_type == 'BEARISH':
                # Bearish predictions
                base_move_1h = -0.003 * confidence_factor * (1 - trend_strength) * volume_factor
                base_move_1d = -0.015 * confidence_factor * (1 - trend_strength) * volume_factor
                base_move_1w = -0.04 * confidence_factor * (1 - trend_strength) * volume_factor
                
                price_1h = current_price * (1 + base_move_1h + np.random.normal(0, volatility_factor * 0.1))
                price_1d = current_price * (1 + base_move_1d + np.random.normal(0, volatility_factor * 0.2))
                price_1w = current_price * (1 + base_move_1w + np.random.normal(0, volatility_factor * 0.3))
                
            else:  # NEUTRAL
                # Neutral predictions with smaller, random movements
                price_1h = current_price * (1 + np.random.normal(0, volatility_factor * 0.05))
                price_1d = current_price * (1 + np.random.normal(0, volatility_factor * 0.1))
                price_1w = current_price * (1 + np.random.normal(0, volatility_factor * 0.15))
            
            # Ensure predictions are reasonable (within 3 ATR)
            max_move = atr * 3
            price_1h = max(current_price - max_move, min(current_price + max_move, price_1h))
            price_1d = max(current_price - max_move, min(current_price + max_move, price_1d))
            price_1w = max(current_price - max_move, min(current_price + max_move, price_1w))
            
            return {
                'price_1h': round(price_1h, 2),
                'price_1d': round(price_1d, 2),
                'price_1w': round(price_1w, 2)
            }
            
        except Exception as e:
            logger.error(f"Error calculating predictions: {e}")
            return {
                'price_1h': None,
                'price_1d': None,
                'price_1w': None
            }

    def calculate_bollinger_position(self, df: pd.DataFrame) -> float:
        """Calculate position within Bollinger Bands"""
        try:
            if 'BBU_20_2.0' in df.columns and 'BBL_20_2.0' in df.columns:
                current_price = df['Close'].iloc[-1]
                upper_band = df['BBU_20_2.0'].iloc[-1]
                lower_band = df['BBL_20_2.0'].iloc[-1]
                
                if upper_band != lower_band:
                    return max(0, min(1, (current_price - lower_band) / (upper_band - lower_band)))
            return 0.5  # Default to middle
        except:
            return 0.5

    def calculate_ema_stack_score(self, df: pd.DataFrame) -> float:
        """Calculate EMA stack alignment score"""
        try:
            emas = ['EMA_8', 'EMA_13', 'EMA_21', 'EMA_34', 'EMA_55', 'EMA_89']
            current_values = []
            
            for ema in emas:
                if ema in df.columns:
                    current_values.append(df[ema].iloc[-1])
            
            if len(current_values) < 4:
                return 0.5
            
            # Check bullish alignment (ascending order)
            bullish_score = sum(1 for i in range(len(current_values)-1) 
                              if current_values[i] > current_values[i+1])
            
            # Check bearish alignment (descending order)
            bearish_score = sum(1 for i in range(len(current_values)-1) 
                               if current_values[i] < current_values[i+1])
            
            max_score = len(current_values) - 1
            
            if bullish_score > bearish_score:
                return 0.5 + (bullish_score / max_score) * 0.5
            else:
                return 0.5 - (bearish_score / max_score) * 0.5
                
        except:
            return 0.5

    async def analyze_sentiment_for_ticker(self, ticker: str) -> Dict:
        """Analyze sentiment for a specific ticker using news data"""
        try:
            # Fetch recent news for the ticker
            news_items = await self.news_fetcher.fetch_ticker_news(ticker, hours_back=24)
            
            if not news_items:
                logger.debug(f"No recent news found for {ticker}")
                return {
                    'sentiment_score': 0.0,
                    'sentiment_confidence': 0.0,
                    'sentiment_impact': 'negligible',
                    'sentiment_boost': 0.0,
                    'news_count': 0
                }
            
            # Prepare news content for analysis
            news_texts = [item['content'] for item in news_items]
            
            # Create context for sentiment analysis
            context = {
                'ticker': ticker,
                'market_conditions': 'neutral',  # Could be enhanced with market regime detection
                'sector': 'Unknown',  # Could be enhanced with sector information
                'volatility_regime': 'normal',
                'timestamp': datetime.now()
            }
            
            # Analyze sentiment using LLM
            sentiment_signals = await self.sentiment_analyzer.analyze_batch(news_texts, context)
            
            if not sentiment_signals:
                logger.debug(f"No sentiment signals generated for {ticker}")
                return {
                    'sentiment_score': 0.0,
                    'sentiment_confidence': 0.0,
                    'sentiment_impact': 'negligible',
                    'sentiment_boost': 0.0,
                    'news_count': len(news_items)
                }
            
            # Calculate weighted sentiment score
            total_weight = 0
            weighted_sentiment = 0
            weighted_confidence = 0
            
            for signal in sentiment_signals:
                weight = signal.confidence
                total_weight += weight
                weighted_sentiment += signal.sentiment_score * weight
                weighted_confidence += signal.confidence * weight
            
            if total_weight > 0:
                avg_sentiment = weighted_sentiment / total_weight
                avg_confidence = weighted_confidence / total_weight
            else:
                avg_sentiment = 0.0
                avg_confidence = 0.0
            
            # Determine sentiment impact
            if avg_confidence > 0.7 and abs(avg_sentiment) > 0.3:
                sentiment_impact = 'immediate'
            elif avg_confidence > 0.5 and abs(avg_sentiment) > 0.2:
                sentiment_impact = 'short-term'
            elif avg_confidence > 0.3 and abs(avg_sentiment) > 0.1:
                sentiment_impact = 'long-term'
            else:
                sentiment_impact = 'negligible'
            
            # Calculate sentiment boost for screening score
            sentiment_boost = 0
            if sentiment_impact != 'negligible':
                if avg_sentiment > 0:
                    sentiment_boost = min(15, avg_sentiment * avg_confidence * 20)
                else:
                    sentiment_boost = max(-15, avg_sentiment * avg_confidence * 20)
            
            logger.info(f"Sentiment analysis for {ticker}: score={avg_sentiment:.2f}, confidence={avg_confidence:.2f}, impact={sentiment_impact}")
            
            return {
                'sentiment_score': round(avg_sentiment, 3),
                'sentiment_confidence': round(avg_confidence, 3),
                'sentiment_impact': sentiment_impact,
                'sentiment_boost': round(sentiment_boost, 1),
                'news_count': len(news_items)
            }
            
        except Exception as e:
            logger.error(f"Error analyzing sentiment for {ticker}: {e}")
            return {
                'sentiment_score': 0.0,
                'sentiment_confidence': 0.0,
                'sentiment_impact': 'negligible',
                'sentiment_boost': 0.0,
                'news_count': 0
            }
    
    def adjust_confidence_with_sentiment(self, technical_confidence: float, signal_type: str, sentiment_data: Dict) -> float:
        """Adjust technical confidence based on sentiment analysis"""
        try:
            sentiment_score = sentiment_data.get('sentiment_score', 0)
            sentiment_confidence = sentiment_data.get('sentiment_confidence', 0)
            
            # Only adjust if sentiment is confident enough
            if sentiment_confidence < 0.5:
                return technical_confidence
            
            # Calculate sentiment alignment with technical signal
            if signal_type == 'BULLISH' and sentiment_score > 0:
                # Bullish technical + positive sentiment = boost confidence
                adjustment = min(15, sentiment_score * sentiment_confidence * 20)
            elif signal_type == 'BEARISH' and sentiment_score < 0:
                # Bearish technical + negative sentiment = boost confidence
                adjustment = min(15, abs(sentiment_score) * sentiment_confidence * 20)
            elif signal_type == 'BULLISH' and sentiment_score < -0.2:
                # Bullish technical + negative sentiment = reduce confidence
                adjustment = max(-10, sentiment_score * sentiment_confidence * 15)
            elif signal_type == 'BEARISH' and sentiment_score > 0.2:
                # Bearish technical + positive sentiment = reduce confidence
                adjustment = max(-10, -sentiment_score * sentiment_confidence * 15)
            else:
                # Neutral or weak sentiment = minimal adjustment
                adjustment = 0
            
            adjusted_confidence = min(100, max(0, technical_confidence + adjustment))
            
            return adjusted_confidence
            
        except Exception as e:
            logger.error(f"Error adjusting confidence with sentiment: {e}")
            return technical_confidence

    async def save_prediction(self, prediction: Dict):
        """Save prediction to database"""
        try:
            conn = await self.get_db_connection()
            
            await conn.execute("""
                INSERT INTO signal_predictions (
                    ticker, timestamp, current_price, signal_type, confidence,
                    primary_reasons, screening_score, sector, predicted_price_1h,
                    predicted_price_1d, predicted_price_1w, volume, rsi, macd,
                    bollinger_position
                ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15)
            """, 
                prediction['ticker'],
                datetime.now(),
                prediction['current_price'],
                prediction['signal_type'],
                prediction['confidence'],
                prediction['primary_reasons'],
                prediction['screening_score'],
                prediction['sector'],
                prediction['predicted_price_1h'],
                prediction['predicted_price_1d'],
                prediction['predicted_price_1w'],
                prediction['volume'],
                prediction['rsi'],
                prediction['macd'],
                prediction['bollinger_position']
            )
            
            await conn.close()
            
            logger.info(f"✓ {prediction['ticker']}: {prediction['signal_type']} "
                       f"({prediction['confidence']:.1f}%) - Score: {prediction['screening_score']:.1f}")
            
        except Exception as e:
            logger.error(f"Error saving prediction for {prediction['ticker']}: {e}")

    async def run_cycle(self):
        """Run one advanced prediction cycle"""
        try:
            logger.info(f"Starting advanced prediction cycle at {datetime.now()}")
            
            # Step 1: Run advanced screening
            screening_results = await self.run_advanced_screening()
            
            if not screening_results:
                logger.warning("No screening results, skipping cycle")
                return
            
            # Step 2: Analyze each candidate with Heikin Ashi
            predictions = []
            for result in screening_results:
                ticker = result.get('ticker')
                if not ticker:
                    continue
                    
                prediction = await self.analyze_with_heikin_ashi(ticker)
                if prediction:
                    predictions.append(prediction)
            
            # Step 3: Save predictions to database
            for prediction in predictions:
                await self.save_prediction(prediction)
            
            logger.info(f"Advanced cycle completed: {len(predictions)} predictions saved")
            
        except Exception as e:
            logger.error(f"Error in advanced cycle: {e}")
            traceback.print_exc()

    async def start(self):
        """Start the advanced scheduler"""
        self.running = True
        logger.info("🚀 Starting Advanced Stock Prediction Scheduler")
        logger.info("📊 Using sophisticated screener + Heikin Ashi signal detection")
        logger.info("⏰ Running every 5 minutes with professional-grade analysis")
        
        while self.running:
            try:
                start_time = datetime.now()
                await self.run_cycle()
                
                # Calculate time until next 5-minute mark
                elapsed = (datetime.now() - start_time).total_seconds()
                sleep_time = max(0, 300 - elapsed)  # 300 seconds = 5 minutes
                
                logger.info(f"⏳ Next advanced cycle in {sleep_time:.0f} seconds...")
                await asyncio.sleep(sleep_time)
                
            except KeyboardInterrupt:
                logger.info("📴 Advanced scheduler stopped by user")
                break
            except Exception as e:
                logger.error(f"❌ Advanced scheduler error: {e}")
                await asyncio.sleep(60)  # Wait 1 minute before retry
        
        self.running = False

    async def stop(self):
        """Stop the scheduler"""
        self.running = False
        logger.info("🛑 Advanced scheduler stopped")

async def main():
    """Main entry point"""
    scheduler = AdvancedPredictionScheduler()
    try:
        await scheduler.start()
    except KeyboardInterrupt:
        logger.info("Received interrupt signal")
    finally:
        await scheduler.stop()

if __name__ == "__main__":
    asyncio.run(main())