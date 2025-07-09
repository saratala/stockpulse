"""
Automated Trading Signals for Weekly Trending Stocks
Based on technical analysis, momentum, and sentiment analysis
"""

import pandas as pd
import numpy as np
from datetime import datetime, timedelta
import yfinance as yf
from typing import Dict, List, Tuple
import logging
from sqlalchemy import create_engine
import os
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

# Set up logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class TrendingStockSignals:
    """
    Generate automated trading signals for trending stocks
    """
    
    def __init__(self):
        """Initialize the trending stock signals generator"""
        self.engine = None
        if os.getenv("DATABASE_URL"):
            self.engine = create_engine(os.getenv("DATABASE_URL"))
    
    def calculate_rsi(self, prices: pd.Series, window: int = 14) -> pd.Series:
        """Calculate Relative Strength Index"""
        delta = prices.diff()
        gain = (delta.where(delta > 0, 0)).rolling(window=window).mean()
        loss = (-delta.where(delta < 0, 0)).rolling(window=window).mean()
        rs = gain / loss
        rsi = 100 - (100 / (1 + rs))
        return rsi
    
    def calculate_macd(self, prices: pd.Series, fast: int = 12, slow: int = 26, signal: int = 9) -> Dict:
        """Calculate MACD indicator"""
        ema_fast = prices.ewm(span=fast).mean()
        ema_slow = prices.ewm(span=slow).mean()
        macd_line = ema_fast - ema_slow
        signal_line = macd_line.ewm(span=signal).mean()
        histogram = macd_line - signal_line
        
        return {
            'macd': macd_line,
            'signal': signal_line,
            'histogram': histogram
        }
    
    def calculate_bollinger_bands(self, prices: pd.Series, window: int = 20, std: int = 2) -> Dict:
        """Calculate Bollinger Bands"""
        sma = prices.rolling(window=window).mean()
        std_dev = prices.rolling(window=window).std()
        
        return {
            'upper': sma + (std_dev * std),
            'middle': sma,
            'lower': sma - (std_dev * std)
        }
    
    def calculate_volume_profile(self, volume: pd.Series, window: int = 20) -> Dict:
        """Calculate volume indicators"""
        avg_volume = volume.rolling(window=window).mean()
        volume_ratio = volume / avg_volume
        
        return {
            'avg_volume': avg_volume,
            'volume_ratio': volume_ratio,
            'volume_spike': volume_ratio > 2.0
        }
    
    def generate_momentum_signals(self, data: pd.DataFrame) -> Dict:
        """Generate momentum-based trading signals"""
        signals = {}
        
        # Price momentum
        data['return_1d'] = data['Close'].pct_change() * 100
        data['return_5d'] = (data['Close'] / data['Close'].shift(5) - 1) * 100
        data['return_10d'] = (data['Close'] / data['Close'].shift(10) - 1) * 100
        
        # Moving averages
        data['sma_10'] = data['Close'].rolling(window=10).mean()
        data['sma_20'] = data['Close'].rolling(window=20).mean()
        data['sma_50'] = data['Close'].rolling(window=50).mean()
        
        # Technical indicators
        data['rsi'] = self.calculate_rsi(data['Close'])
        macd_data = self.calculate_macd(data['Close'])
        data['macd'] = macd_data['macd']
        data['macd_signal'] = macd_data['signal']
        data['macd_histogram'] = macd_data['histogram']
        
        bb_data = self.calculate_bollinger_bands(data['Close'])
        data['bb_upper'] = bb_data['upper']
        data['bb_middle'] = bb_data['middle']
        data['bb_lower'] = bb_data['lower']
        
        volume_data = self.calculate_volume_profile(data['Volume'])
        data['volume_ratio'] = volume_data['volume_ratio']
        
        # Generate signals
        latest = data.iloc[-1]
        
        # Bullish signals
        bullish_score = 0
        bullish_reasons = []
        
        if latest['return_5d'] > 5:
            bullish_score += 25
            bullish_reasons.append(f"Strong 5-day momentum: +{latest['return_5d']:.1f}%")
        
        if latest['Close'] > latest['sma_10'] > latest['sma_20']:
            bullish_score += 20
            bullish_reasons.append("Price above rising moving averages")
        
        if 30 <= latest['rsi'] <= 70:
            bullish_score += 15
            bullish_reasons.append(f"Healthy RSI level: {latest['rsi']:.1f}")
        
        if latest['macd'] > latest['macd_signal'] and latest['macd_histogram'] > 0:
            bullish_score += 20
            bullish_reasons.append("MACD bullish crossover")
        
        if latest['volume_ratio'] > 1.5:
            bullish_score += 20
            bullish_reasons.append(f"High volume confirmation: {latest['volume_ratio']:.1f}x average")
        
        # Bearish signals
        bearish_score = 0
        bearish_reasons = []
        
        if latest['return_5d'] < -5:
            bearish_score += 25
            bearish_reasons.append(f"Weak 5-day momentum: {latest['return_5d']:.1f}%")
        
        if latest['Close'] < latest['sma_10'] < latest['sma_20']:
            bearish_score += 20
            bearish_reasons.append("Price below declining moving averages")
        
        if latest['rsi'] > 80 or latest['rsi'] < 20:
            bearish_score += 15
            bearish_reasons.append(f"Extreme RSI level: {latest['rsi']:.1f}")
        
        if latest['macd'] < latest['macd_signal'] and latest['macd_histogram'] < 0:
            bearish_score += 20
            bearish_reasons.append("MACD bearish crossover")
        
        # Determine overall signal
        net_score = bullish_score - bearish_score
        
        if net_score >= 40:
            signal = "STRONG_BUY"
        elif net_score >= 20:
            signal = "BUY"
        elif net_score >= -20:
            signal = "HOLD"
        elif net_score >= -40:
            signal = "SELL"
        else:
            signal = "STRONG_SELL"
        
        signals = {
            'signal': signal,
            'bullish_score': bullish_score,
            'bearish_score': bearish_score,
            'net_score': net_score,
            'bullish_reasons': bullish_reasons,
            'bearish_reasons': bearish_reasons,
            'confidence': min(100, abs(net_score)),
            'current_price': latest['Close'],
            'rsi': latest['rsi'],
            'return_5d': latest['return_5d'],
            'volume_ratio': latest['volume_ratio']
        }
        
        return signals
    
    def get_trending_stocks_with_signals(self, tickers: List[str] = None) -> List[Dict]:
        """
        Get trending stocks with automated trading signals
        """
        if tickers is None:
            # Default popular stocks for analysis
            tickers = [
                'AAPL', 'MSFT', 'GOOGL', 'AMZN', 'TSLA', 'META', 'NVDA', 'NFLX',
                'AMD', 'INTC', 'CRM', 'UBER', 'SHOP', 'SQ', 'ROKU', 'ZM',
                'PLTR', 'NET', 'SNOW', 'CRWD', 'OKTA', 'DDOG', 'TWLO', 'ZS'
            ]
        
        results = []
        
        for ticker in tickers:
            try:
                logger.info(f"Analyzing {ticker}...")
                
                # Fetch stock data
                stock = yf.Ticker(ticker)
                data = stock.history(period="3mo", interval="1d")
                
                if data.empty:
                    logger.warning(f"No data available for {ticker}")
                    continue
                
                # Get stock info
                info = stock.info
                
                # Generate signals
                signals = self.generate_momentum_signals(data)
                
                # Calculate additional metrics
                current_price = data['Close'].iloc[-1]
                week_high = data['High'].iloc[-5:].max()
                week_low = data['Low'].iloc[-5:].min()
                
                result = {
                    'ticker': ticker,
                    'name': info.get('longName', ticker),
                    'sector': info.get('sector', 'Unknown'),
                    'current_price': round(current_price, 2),
                    'week_high': round(week_high, 2),
                    'week_low': round(week_low, 2),
                    'market_cap': info.get('marketCap', 0),
                    'signal': signals['signal'],
                    'confidence': signals['confidence'],
                    'bullish_score': signals['bullish_score'],
                    'bearish_score': signals['bearish_score'],
                    'net_score': signals['net_score'],
                    'bullish_reasons': signals['bullish_reasons'],
                    'bearish_reasons': signals['bearish_reasons'],
                    'rsi': round(signals['rsi'], 2) if not pd.isna(signals['rsi']) else None,
                    'return_5d': round(signals['return_5d'], 2) if not pd.isna(signals['return_5d']) else None,
                    'volume_ratio': round(signals['volume_ratio'], 2) if not pd.isna(signals['volume_ratio']) else None,
                    'analysis_date': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
                }
                
                results.append(result)
                
            except Exception as e:
                logger.error(f"Error analyzing {ticker}: {e}")
                continue
        
        # Sort by signal strength and confidence
        signal_priority = {
            'STRONG_BUY': 5,
            'BUY': 4,
            'HOLD': 3,
            'SELL': 2,
            'STRONG_SELL': 1
        }
        
        results.sort(key=lambda x: (signal_priority.get(x['signal'], 0), x['confidence']), reverse=True)
        
        return results
    
    def generate_weekly_report(self, tickers: List[str] = None) -> Dict:
        """
        Generate a comprehensive weekly trending stocks report
        """
        logger.info("Generating weekly trending stocks report...")
        
        trending_stocks = self.get_trending_stocks_with_signals(tickers)
        
        # Categorize signals
        strong_buys = [s for s in trending_stocks if s['signal'] == 'STRONG_BUY']
        buys = [s for s in trending_stocks if s['signal'] == 'BUY']
        holds = [s for s in trending_stocks if s['signal'] == 'HOLD']
        sells = [s for s in trending_stocks if s['signal'] == 'SELL']
        strong_sells = [s for s in trending_stocks if s['signal'] == 'STRONG_SELL']
        
        # Top performers
        top_momentum = sorted(
            [s for s in trending_stocks if s['return_5d'] is not None],
            key=lambda x: x['return_5d'],
            reverse=True
        )[:10]
        
        # High volume stocks
        high_volume = sorted(
            [s for s in trending_stocks if s['volume_ratio'] is not None],
            key=lambda x: x['volume_ratio'],
            reverse=True
        )[:10]
        
        report = {
            'report_date': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            'summary': {
                'total_analyzed': len(trending_stocks),
                'strong_buys': len(strong_buys),
                'buys': len(buys),
                'holds': len(holds),
                'sells': len(sells),
                'strong_sells': len(strong_sells)
            },
            'signals': {
                'strong_buys': strong_buys[:10],
                'buys': buys[:10],
                'holds': holds[:10],
                'sells': sells[:10],
                'strong_sells': strong_sells[:10]
            },
            'top_performers': {
                'momentum': top_momentum,
                'volume': high_volume
            },
            'all_stocks': trending_stocks
        }
        
        logger.info(f"Report generated with {len(trending_stocks)} stocks analyzed")
        return report


def main():
    """Main function to run the trending stocks analysis"""
    analyzer = TrendingStockSignals()
    
    # Generate weekly report
    report = analyzer.generate_weekly_report()
    
    print("\n" + "="*80)
    print("WEEKLY TRENDING STOCKS REPORT")
    print("="*80)
    
    print(f"\nReport Date: {report['report_date']}")
    print(f"Total Stocks Analyzed: {report['summary']['total_analyzed']}")
    
    print("\n📊 SIGNAL SUMMARY:")
    print(f"🟢 Strong Buys: {report['summary']['strong_buys']}")
    print(f"🟢 Buys: {report['summary']['buys']}")
    print(f"🟡 Holds: {report['summary']['holds']}")
    print(f"🔴 Sells: {report['summary']['sells']}")
    print(f"🔴 Strong Sells: {report['summary']['strong_sells']}")
    
    if report['signals']['strong_buys']:
        print("\n🚀 TOP STRONG BUY SIGNALS:")
        for i, stock in enumerate(report['signals']['strong_buys'][:5], 1):
            print(f"{i}. {stock['ticker']} ({stock['name'][:30]}...)")
            print(f"   Price: ${stock['current_price']} | 5d Return: {stock['return_5d']}%")
            print(f"   Confidence: {stock['confidence']}% | Reasons: {len(stock['bullish_reasons'])}")
            if stock['bullish_reasons']:
                print(f"   🔹 {stock['bullish_reasons'][0]}")
    
    if report['top_performers']['momentum']:
        print("\n📈 TOP MOMENTUM STOCKS:")
        for i, stock in enumerate(report['top_performers']['momentum'][:5], 1):
            print(f"{i}. {stock['ticker']} - {stock['return_5d']:.1f}% (5d)")
    
    print("\n" + "="*80)


if __name__ == "__main__":
    main()