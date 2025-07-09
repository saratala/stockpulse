#!/usr/bin/env python3
"""
Simple Signal Prediction Scheduler - Runs every 5 minutes
Fetches real stock data and saves predictions to database
"""

import asyncio
import asyncpg
import yfinance as yf
import pandas as pd
import numpy as np
from datetime import datetime, timedelta
import os
import logging
import json

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

class SimpleScheduler:
    def __init__(self):
        self.db_config = {
            'host': os.getenv('DB_HOST', 'localhost'),
            'port': int(os.getenv('DB_PORT', '5432')),
            'database': os.getenv('DB_NAME', 'stockpulse'),
            'user': os.getenv('DB_USER', 'postgres'),
            'password': os.getenv('DB_PASSWORD', 'postgres')
        }
        
        # Popular stocks to track
        self.tickers = [
            'AAPL', 'GOOGL', 'MSFT', 'AMZN', 'TSLA', 'META', 'NVDA', 'NFLX',
            'AMD', 'INTC', 'CRM', 'ORCL', 'ADBE', 'PYPL', 'SQ', 'UBER',
            'JPM', 'BAC', 'GS', 'C', 'WFC', 'V', 'MA', 'AXP',
            'JNJ', 'PFE', 'UNH', 'CVS', 'ABBV', 'MRK', 'TMO', 'DHR'
        ]

    async def get_db_connection(self):
        return await asyncpg.connect(**self.db_config)

    def calculate_technical_indicators(self, df):
        """Calculate RSI, MACD, and Bollinger Bands position"""
        try:
            # RSI
            delta = df['Close'].diff()
            gain = (delta.where(delta > 0, 0)).rolling(window=14).mean()
            loss = (-delta.where(delta < 0, 0)).rolling(window=14).mean()
            rs = gain / loss
            rsi = 100 - (100 / (1 + rs)).iloc[-1]
            
            # MACD
            exp1 = df['Close'].ewm(span=12, adjust=False).mean()
            exp2 = df['Close'].ewm(span=26, adjust=False).mean()
            macd = (exp1 - exp2).iloc[-1]
            
            # Bollinger Bands position (0 = at lower band, 1 = at upper band)
            sma = df['Close'].rolling(window=20).mean()
            std = df['Close'].rolling(window=20).std()
            upper_band = (sma + (std * 2)).iloc[-1]
            lower_band = (sma - (std * 2)).iloc[-1]
            current_price = df['Close'].iloc[-1]
            
            bb_position = 0.5  # default middle
            if upper_band != lower_band:
                bb_position = (current_price - lower_band) / (upper_band - lower_band)
                bb_position = max(0, min(1, bb_position))  # Clamp between 0 and 1
            
            return {
                'rsi': float(rsi) if not pd.isna(rsi) else 50.0,
                'macd': float(macd) if not pd.isna(macd) else 0.0,
                'bollinger_position': float(bb_position)
            }
        except Exception as e:
            logger.error(f"Error calculating indicators: {e}")
            return {'rsi': 50.0, 'macd': 0.0, 'bollinger_position': 0.5}

    def determine_signal(self, indicators, price_change_pct):
        """Determine signal type based on technical indicators"""
        rsi = indicators['rsi']
        macd = indicators['macd']
        bb_pos = indicators['bollinger_position']
        
        bullish_score = 0
        bearish_score = 0
        reasons = []
        
        # RSI signals
        if rsi > 70:
            bearish_score += 30
            reasons.append("RSI indicates overbought conditions")
        elif rsi < 30:
            bullish_score += 30
            reasons.append("RSI indicates oversold conditions")
        elif 50 <= rsi <= 70:
            bullish_score += 15
            reasons.append("RSI shows bullish momentum")
        elif 30 <= rsi < 50:
            bearish_score += 15
            reasons.append("RSI shows bearish momentum")
        
        # MACD signals
        if macd > 0:
            bullish_score += 25
            reasons.append("MACD positive crossover")
        else:
            bearish_score += 25
            reasons.append("MACD negative crossover")
        
        # Bollinger Bands signals
        if bb_pos > 0.8:
            bearish_score += 20
            reasons.append("Price near upper Bollinger Band")
        elif bb_pos < 0.2:
            bullish_score += 20
            reasons.append("Price near lower Bollinger Band")
        
        # Price momentum
        if price_change_pct > 2:
            bullish_score += 15
            reasons.append("Strong upward price momentum")
        elif price_change_pct < -2:
            bearish_score += 15
            reasons.append("Strong downward price momentum")
        
        # Determine final signal
        if bullish_score > bearish_score and bullish_score >= 40:
            signal = 'BULLISH'
            confidence = min(95, bullish_score)
        elif bearish_score > bullish_score and bearish_score >= 40:
            signal = 'BEARISH'
            confidence = min(95, bearish_score)
        else:
            signal = 'NEUTRAL'
            confidence = 50 + abs(bullish_score - bearish_score) / 2
        
        return signal, confidence, reasons[:3]  # Top 3 reasons

    def calculate_price_predictions(self, current_price, signal, confidence):
        """Calculate predicted prices based on signal and confidence"""
        confidence_factor = confidence / 100.0
        
        if signal == 'BULLISH':
            # Bullish predictions
            price_1h = current_price * (1 + 0.002 * confidence_factor)  # 0.1-0.2% for 1 hour
            price_1d = current_price * (1 + 0.01 * confidence_factor)   # 0.5-1% for 1 day
            price_1w = current_price * (1 + 0.03 * confidence_factor)   # 1.5-3% for 1 week
        elif signal == 'BEARISH':
            # Bearish predictions
            price_1h = current_price * (1 - 0.002 * confidence_factor)
            price_1d = current_price * (1 - 0.01 * confidence_factor)
            price_1w = current_price * (1 - 0.03 * confidence_factor)
        else:
            # Neutral predictions - small random movements
            price_1h = current_price * (1 + np.random.uniform(-0.001, 0.001))
            price_1d = current_price * (1 + np.random.uniform(-0.005, 0.005))
            price_1w = current_price * (1 + np.random.uniform(-0.01, 0.01))
        
        return {
            'predicted_price_1h': round(price_1h, 2),
            'predicted_price_1d': round(price_1d, 2),
            'predicted_price_1w': round(price_1w, 2)
        }

    async def analyze_and_save_ticker(self, ticker):
        """Analyze a single ticker and save to database"""
        try:
            logger.info(f"Analyzing {ticker}...")
            
            # Fetch stock data
            stock = yf.Ticker(ticker)
            
            # Get recent price data (5 days of 1-minute data)
            hist = stock.history(period='5d', interval='15m')
            if hist.empty:
                logger.warning(f"No data for {ticker}")
                return
            
            # Get stock info
            info = stock.info
            sector = info.get('sector', 'Unknown')
            
            # Current data
            current_price = float(hist['Close'].iloc[-1])
            volume = int(hist['Volume'].iloc[-1])
            
            # Calculate price change
            price_24h_ago = hist['Close'].iloc[-96] if len(hist) > 96 else hist['Close'].iloc[0]
            price_change_pct = ((current_price - price_24h_ago) / price_24h_ago) * 100
            
            # Calculate technical indicators
            indicators = self.calculate_technical_indicators(hist)
            
            # Determine signal
            signal, confidence, reasons = self.determine_signal(indicators, price_change_pct)
            
            # Calculate screening score (simplified)
            screening_score = 50 + (confidence - 50) * 0.5 + np.random.uniform(-10, 10)
            screening_score = max(0, min(100, screening_score))
            
            # Calculate price predictions
            predictions = self.calculate_price_predictions(current_price, signal, confidence)
            
            # Save to database
            conn = await self.get_db_connection()
            
            # Insert stock if not exists
            await conn.execute("""
                INSERT INTO stocks (ticker, name, sector, industry)
                VALUES ($1, $2, $3, $4)
                ON CONFLICT (ticker) DO UPDATE SET sector = $3
            """, ticker, info.get('longName', ticker), sector, info.get('industry', 'Unknown'))
            
            # Insert prediction
            await conn.execute("""
                INSERT INTO signal_predictions (
                    ticker, timestamp, current_price, signal_type, confidence,
                    primary_reasons, screening_score, sector, predicted_price_1h,
                    predicted_price_1d, predicted_price_1w, volume, rsi, macd,
                    bollinger_position
                ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14, $15)
            """, 
                ticker,
                datetime.now(),
                current_price,
                signal,
                confidence,
                reasons,
                screening_score,
                sector,
                predictions['predicted_price_1h'],
                predictions['predicted_price_1d'],
                predictions['predicted_price_1w'],
                volume,
                indicators['rsi'],
                indicators['macd'],
                indicators['bollinger_position']
            )
            
            await conn.close()
            
            logger.info(f"✓ {ticker}: {signal} ({confidence:.1f}%) - Price: ${current_price:.2f}")
            
        except Exception as e:
            logger.error(f"Error analyzing {ticker}: {e}")

    async def run_cycle(self):
        """Run one prediction cycle for all tickers"""
        logger.info(f"Starting prediction cycle at {datetime.now()}")
        
        # Process tickers in batches
        batch_size = 5
        for i in range(0, len(self.tickers), batch_size):
            batch = self.tickers[i:i + batch_size]
            tasks = [self.analyze_and_save_ticker(ticker) for ticker in batch]
            await asyncio.gather(*tasks, return_exceptions=True)
            await asyncio.sleep(2)  # Small delay between batches
        
        logger.info(f"Cycle completed at {datetime.now()}")

    async def start(self):
        """Start the scheduler - runs every 5 minutes"""
        logger.info("Starting Simple Stock Prediction Scheduler")
        logger.info(f"Tracking {len(self.tickers)} stocks")
        logger.info("Running predictions every 5 minutes...")
        
        while True:
            try:
                start_time = datetime.now()
                await self.run_cycle()
                
                # Calculate time until next 5-minute mark
                elapsed = (datetime.now() - start_time).total_seconds()
                sleep_time = max(0, 300 - elapsed)  # 300 seconds = 5 minutes
                
                logger.info(f"Next cycle in {sleep_time:.0f} seconds...")
                await asyncio.sleep(sleep_time)
                
            except KeyboardInterrupt:
                logger.info("Scheduler stopped by user")
                break
            except Exception as e:
                logger.error(f"Scheduler error: {e}")
                await asyncio.sleep(60)  # Wait 1 minute before retry

async def main():
    scheduler = SimpleScheduler()
    await scheduler.start()

if __name__ == "__main__":
    asyncio.run(main())