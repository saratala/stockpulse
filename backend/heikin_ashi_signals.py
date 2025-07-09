"""
Heikin Ashi Signal Detection Module
Advanced signal detection using Heikin Ashi candles combined with technical indicators
"""

import pandas as pd
from ta import trend, momentum, volatility, volume
import numpy as np
from datetime import datetime, timedelta
from typing import Dict, List, Tuple, Optional
import logging
from enhanced_data_fetcher import EnhancedDataFetcher

# Set up logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class HeikinAshiSignalDetector:
    """
    Advanced signal detection using Heikin Ashi candles
    """
    
    def __init__(self, data_fetcher: EnhancedDataFetcher = None):
        """Initialize the Heikin Ashi signal detector"""
        self.data_fetcher = data_fetcher or EnhancedDataFetcher()
    
    def calculate_heikin_ashi(self, df: pd.DataFrame) -> pd.DataFrame:
        """
        Calculate Heikin Ashi candles with enhanced analysis
        """
        ha_df = df.copy()
        
        # Calculate basic Heikin Ashi values
        ha_df['HA_Close'] = (df['Open'] + df['High'] + df['Low'] + df['Close']) / 4
        
        # Initialize first HA_Open
        ha_df['HA_Open'] = np.nan
        ha_df.loc[ha_df.index[0], 'HA_Open'] = (df['Open'].iloc[0] + df['Close'].iloc[0]) / 2
        
        # Calculate subsequent HA_Open values
        for i in range(1, len(ha_df)):
            ha_df.loc[ha_df.index[i], 'HA_Open'] = (ha_df['HA_Open'].iloc[i-1] + ha_df['HA_Close'].iloc[i-1]) / 2
        
        # Calculate HA_High and HA_Low
        ha_df['HA_High'] = ha_df[['HA_Open', 'HA_Close', 'High']].max(axis=1)
        ha_df['HA_Low'] = ha_df[['HA_Open', 'HA_Close', 'Low']].min(axis=1)
        
        # Enhanced Heikin Ashi analysis
        ha_df['HA_Bullish'] = ha_df['HA_Close'] > ha_df['HA_Open']
        ha_df['HA_Bearish'] = ha_df['HA_Close'] < ha_df['HA_Open']
        ha_df['HA_Doji'] = abs(ha_df['HA_Close'] - ha_df['HA_Open']) < (ha_df['HA_High'] - ha_df['HA_Low']) * 0.1
        
        # Heikin Ashi candle strength
        ha_df['HA_Body_Size'] = abs(ha_df['HA_Close'] - ha_df['HA_Open'])
        ha_df['HA_Upper_Shadow'] = ha_df['HA_High'] - np.maximum(ha_df['HA_Open'], ha_df['HA_Close'])
        ha_df['HA_Lower_Shadow'] = np.minimum(ha_df['HA_Open'], ha_df['HA_Close']) - ha_df['HA_Low']
        ha_df['HA_Total_Range'] = ha_df['HA_High'] - ha_df['HA_Low']
        
        # Candle pattern recognition
        ha_df['HA_Strong_Bull'] = (ha_df['HA_Bullish'] & 
                                   (ha_df['HA_Body_Size'] > ha_df['HA_Total_Range'] * 0.6) &
                                   (ha_df['HA_Upper_Shadow'] < ha_df['HA_Body_Size'] * 0.3))
        
        ha_df['HA_Strong_Bear'] = (ha_df['HA_Bearish'] & 
                                   (ha_df['HA_Body_Size'] > ha_df['HA_Total_Range'] * 0.6) &
                                   (ha_df['HA_Lower_Shadow'] < ha_df['HA_Body_Size'] * 0.3))
        
        ha_df['HA_Hammer'] = ((ha_df['HA_Lower_Shadow'] > ha_df['HA_Body_Size'] * 2) &
                              (ha_df['HA_Upper_Shadow'] < ha_df['HA_Body_Size'] * 0.5))
        
        ha_df['HA_Shooting_Star'] = ((ha_df['HA_Upper_Shadow'] > ha_df['HA_Body_Size'] * 2) &
                                     (ha_df['HA_Lower_Shadow'] < ha_df['HA_Body_Size'] * 0.5))
        
        # Consecutive candle analysis
        ha_df['HA_Bull_Consecutive'] = (ha_df['HA_Bullish'] & ha_df['HA_Bullish'].shift(1)).astype(int)
        ha_df['HA_Bear_Consecutive'] = (ha_df['HA_Bearish'] & ha_df['HA_Bearish'].shift(1)).astype(int)
        
        # Trend strength based on consecutive candles
        ha_df['HA_Trend_Strength'] = 0
        for i in range(1, len(ha_df)):
            if ha_df['HA_Bullish'].iloc[i]:
                if ha_df['HA_Bullish'].iloc[i-1]:
                    ha_df.loc[ha_df.index[i], 'HA_Trend_Strength'] = ha_df['HA_Trend_Strength'].iloc[i-1] + 1
                else:
                    ha_df.loc[ha_df.index[i], 'HA_Trend_Strength'] = 1
            elif ha_df['HA_Bearish'].iloc[i]:
                if ha_df['HA_Bearish'].iloc[i-1]:
                    ha_df.loc[ha_df.index[i], 'HA_Trend_Strength'] = ha_df['HA_Trend_Strength'].iloc[i-1] - 1
                else:
                    ha_df.loc[ha_df.index[i], 'HA_Trend_Strength'] = -1
        
        return ha_df
    
    def is_bullish_signal(self, df: pd.DataFrame) -> Dict:
        """
        Enhanced bullish signal detection using Heikin Ashi candles
        """
        if len(df) < 5:
            return {'signal': False, 'confidence': 0, 'reasons': []}
        
        ha_df = self.calculate_heikin_ashi(df)
        last = ha_df.iloc[-1]
        prev = ha_df.iloc[-2]
        
        # Calculate RSI
        rsi = ta.rsi(df['Close'], length=14).iloc[-1]
        
        # Calculate ATR for price position analysis
        atr = ta.atr(df['High'], df['Low'], df['Close'], length=14).iloc[-1]
        
        # Current price and EMA21
        current_price = df['Close'].iloc[-1]
        ema_21 = ta.ema(df['Close'], length=21).iloc[-1]
        
        # Signal components
        signal_components = []
        confidence = 0
        
        # 1. Strong bullish Heikin Ashi candle
        if last['HA_Strong_Bull']:
            signal_components.append("Strong bullish Heikin Ashi candle")
            confidence += 25
        elif last['HA_Bullish'] and last['HA_Close'] > prev['HA_Close']:
            signal_components.append("Bullish Heikin Ashi with higher close")
            confidence += 15
        
        # 2. Consecutive bullish candles
        if last['HA_Trend_Strength'] >= 2:
            signal_components.append(f"Consecutive bullish momentum ({int(last['HA_Trend_Strength'])} candles)")
            confidence += min(20, last['HA_Trend_Strength'] * 5)
        
        # 3. RSI conditions
        if 30 < rsi < 70:
            signal_components.append(f"Healthy RSI level: {rsi:.1f}")
            confidence += 15
        elif rsi > 70:
            signal_components.append(f"RSI overbought warning: {rsi:.1f}")
            confidence -= 10
        elif rsi < 30:
            signal_components.append(f"RSI oversold opportunity: {rsi:.1f}")
            confidence += 10
        
        # 4. Price within ATR of EMA21
        price_distance = abs(current_price - ema_21)
        if price_distance <= atr:
            signal_components.append("Price near EMA21 support")
            confidence += 15
        elif current_price > ema_21:
            signal_components.append("Price above EMA21")
            confidence += 10
        
        # 5. Volume confirmation
        if 'Volume_Ratio' in ha_df.columns and last['Volume_Ratio'] > 1.2:
            signal_components.append(f"Volume confirmation: {last['Volume_Ratio']:.1f}x")
            confidence += 10
        
        # 6. Hammer pattern at support
        if last['HA_Hammer'] and current_price <= ema_21 + atr:
            signal_components.append("Hammer pattern near support")
            confidence += 20
        
        # 7. Trend reversal patterns
        if prev['HA_Bearish'] and last['HA_Bullish'] and last['HA_Close'] > prev['HA_Close']:
            signal_components.append("Potential trend reversal")
            confidence += 15
        
        # Determine signal strength
        is_signal = confidence >= 40
        
        return {
            'signal': is_signal,
            'confidence': min(100, confidence),
            'signal_strength': self._get_signal_strength(confidence),
            'reasons': signal_components,
            'technical_data': {
                'current_price': round(current_price, 2),
                'ema_21': round(ema_21, 2),
                'atr': round(atr, 2),
                'rsi': round(rsi, 2),
                'ha_trend_strength': last['HA_Trend_Strength'],
                'price_distance_from_ema': round(price_distance, 2),
                'volume_ratio': round(last['Volume_Ratio'], 2) if 'Volume_Ratio' in ha_df.columns else None
            }
        }
    
    def is_bearish_signal(self, df: pd.DataFrame) -> Dict:
        """
        Enhanced bearish signal detection using Heikin Ashi candles
        """
        if len(df) < 5:
            return {'signal': False, 'confidence': 0, 'reasons': []}
        
        ha_df = self.calculate_heikin_ashi(df)
        last = ha_df.iloc[-1]
        prev = ha_df.iloc[-2]
        
        # Calculate RSI
        rsi = ta.rsi(df['Close'], length=14).iloc[-1]
        
        # Calculate ATR for price position analysis
        atr = ta.atr(df['High'], df['Low'], df['Close'], length=14).iloc[-1]
        
        # Current price and EMA21
        current_price = df['Close'].iloc[-1]
        ema_21 = ta.ema(df['Close'], length=21).iloc[-1]
        
        # Signal components
        signal_components = []
        confidence = 0
        
        # 1. Strong bearish Heikin Ashi candle
        if last['HA_Strong_Bear']:
            signal_components.append("Strong bearish Heikin Ashi candle")
            confidence += 25
        elif last['HA_Bearish'] and last['HA_Close'] < prev['HA_Close']:
            signal_components.append("Bearish Heikin Ashi with lower close")
            confidence += 15
        
        # 2. Consecutive bearish candles
        if last['HA_Trend_Strength'] <= -2:
            signal_components.append(f"Consecutive bearish momentum ({abs(int(last['HA_Trend_Strength']))} candles)")
            confidence += min(20, abs(last['HA_Trend_Strength']) * 5)
        
        # 3. RSI conditions
        if 30 < rsi < 70:
            signal_components.append(f"Neutral RSI level: {rsi:.1f}")
            confidence += 10
        elif rsi > 70:
            signal_components.append(f"RSI overbought sell signal: {rsi:.1f}")
            confidence += 20
        elif rsi < 30:
            signal_components.append(f"RSI oversold warning: {rsi:.1f}")
            confidence -= 10
        
        # 4. Price within ATR of EMA21
        price_distance = abs(current_price - ema_21)
        if price_distance <= atr:
            signal_components.append("Price near EMA21 resistance")
            confidence += 15
        elif current_price < ema_21:
            signal_components.append("Price below EMA21")
            confidence += 10
        
        # 5. Volume confirmation
        if 'Volume_Ratio' in ha_df.columns and last['Volume_Ratio'] > 1.2:
            signal_components.append(f"Volume confirmation: {last['Volume_Ratio']:.1f}x")
            confidence += 10
        
        # 6. Shooting star pattern at resistance
        if last['HA_Shooting_Star'] and current_price >= ema_21 - atr:
            signal_components.append("Shooting star pattern near resistance")
            confidence += 20
        
        # 7. Trend reversal patterns
        if prev['HA_Bullish'] and last['HA_Bearish'] and last['HA_Close'] < prev['HA_Close']:
            signal_components.append("Potential trend reversal")
            confidence += 15
        
        # Determine signal strength
        is_signal = confidence >= 40
        
        return {
            'signal': is_signal,
            'confidence': min(100, confidence),
            'signal_strength': self._get_signal_strength(confidence),
            'reasons': signal_components,
            'technical_data': {
                'current_price': round(current_price, 2),
                'ema_21': round(ema_21, 2),
                'atr': round(atr, 2),
                'rsi': round(rsi, 2),
                'ha_trend_strength': last['HA_Trend_Strength'],
                'price_distance_from_ema': round(price_distance, 2),
                'volume_ratio': round(last['Volume_Ratio'], 2) if 'Volume_Ratio' in ha_df.columns else None
            }
        }
    
    def _get_signal_strength(self, confidence: int) -> str:
        """
        Convert confidence score to signal strength
        """
        if confidence >= 80:
            return "VERY_STRONG"
        elif confidence >= 60:
            return "STRONG"
        elif confidence >= 40:
            return "MODERATE"
        elif confidence >= 20:
            return "WEAK"
        else:
            return "VERY_WEAK"
    
    def analyze_single_stock(self, ticker: str, period: str = "3mo") -> Dict:
        """
        Analyze a single stock for Heikin Ashi signals
        """
        try:
            # Fetch data
            df = self.data_fetcher.fetch_comprehensive_data(ticker, period)
            if df is None:
                return {'ticker': ticker, 'error': 'Could not fetch data'}
            
            # Generate signals
            bullish_signal = self.is_bullish_signal(df)
            bearish_signal = self.is_bearish_signal(df)
            
            # Calculate Heikin Ashi for additional metrics
            ha_df = self.calculate_heikin_ashi(df)
            latest_ha = ha_df.iloc[-1]
            
            # Determine primary signal
            if bullish_signal['signal'] and bearish_signal['signal']:
                # Both signals present - choose stronger one
                if bullish_signal['confidence'] > bearish_signal['confidence']:
                    primary_signal = 'BULLISH'
                    primary_confidence = bullish_signal['confidence']
                    primary_reasons = bullish_signal['reasons']
                else:
                    primary_signal = 'BEARISH'
                    primary_confidence = bearish_signal['confidence']
                    primary_reasons = bearish_signal['reasons']
            elif bullish_signal['signal']:
                primary_signal = 'BULLISH'
                primary_confidence = bullish_signal['confidence']
                primary_reasons = bullish_signal['reasons']
            elif bearish_signal['signal']:
                primary_signal = 'BEARISH'
                primary_confidence = bearish_signal['confidence']
                primary_reasons = bearish_signal['reasons']
            else:
                primary_signal = 'NEUTRAL'
                primary_confidence = 0
                primary_reasons = ['No clear signal detected']
            
            return {
                'ticker': ticker,
                'analysis_date': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
                'primary_signal': primary_signal,
                'primary_confidence': primary_confidence,
                'primary_reasons': primary_reasons,
                'bullish_analysis': bullish_signal,
                'bearish_analysis': bearish_signal,
                'heikin_ashi_data': {
                    'ha_close': round(latest_ha['HA_Close'], 2),
                    'ha_open': round(latest_ha['HA_Open'], 2),
                    'ha_bullish': latest_ha['HA_Bullish'],
                    'ha_trend_strength': latest_ha['HA_Trend_Strength'],
                    'ha_strong_bull': latest_ha['HA_Strong_Bull'],
                    'ha_strong_bear': latest_ha['HA_Strong_Bear'],
                    'ha_hammer': latest_ha['HA_Hammer'],
                    'ha_shooting_star': latest_ha['HA_Shooting_Star']
                }
            }
            
        except Exception as e:
            logger.error(f"Error analyzing {ticker}: {e}")
            return {'ticker': ticker, 'error': str(e)}
    
    def scan_multiple_stocks(self, tickers: List[str], period: str = "3mo") -> List[Dict]:
        """
        Scan multiple stocks for Heikin Ashi signals
        """
        results = []
        
        logger.info(f"Scanning {len(tickers)} stocks for Heikin Ashi signals...")
        
        for ticker in tickers:
            try:
                analysis = self.analyze_single_stock(ticker, period)
                results.append(analysis)
                
                # Log significant signals
                if 'primary_signal' in analysis and analysis['primary_signal'] != 'NEUTRAL':
                    logger.info(f"{ticker}: {analysis['primary_signal']} signal with {analysis['primary_confidence']}% confidence")
                    
            except Exception as e:
                logger.error(f"Error scanning {ticker}: {e}")
                continue
        
        # Sort by signal strength and confidence
        signal_priority = {'BULLISH': 2, 'BEARISH': 1, 'NEUTRAL': 0}
        results.sort(key=lambda x: (
            signal_priority.get(x.get('primary_signal', 'NEUTRAL'), 0),
            x.get('primary_confidence', 0)
        ), reverse=True)
        
        return results
    
    def generate_signal_report(self, results: List[Dict]) -> Dict:
        """
        Generate a comprehensive signal report
        """
        # Filter out error results
        valid_results = [r for r in results if 'error' not in r]
        
        # Categorize signals
        bullish_signals = [r for r in valid_results if r.get('primary_signal') == 'BULLISH']
        bearish_signals = [r for r in valid_results if r.get('primary_signal') == 'BEARISH']
        neutral_signals = [r for r in valid_results if r.get('primary_signal') == 'NEUTRAL']
        
        # Get high confidence signals
        high_confidence_bullish = [r for r in bullish_signals if r.get('primary_confidence', 0) >= 70]
        high_confidence_bearish = [r for r in bearish_signals if r.get('primary_confidence', 0) >= 70]
        
        report = {
            'report_date': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            'summary': {
                'total_analyzed': len(valid_results),
                'bullish_signals': len(bullish_signals),
                'bearish_signals': len(bearish_signals),
                'neutral_signals': len(neutral_signals),
                'high_confidence_bullish': len(high_confidence_bullish),
                'high_confidence_bearish': len(high_confidence_bearish)
            },
            'top_bullish_signals': bullish_signals[:10],
            'top_bearish_signals': bearish_signals[:10],
            'high_confidence_signals': {
                'bullish': high_confidence_bullish,
                'bearish': high_confidence_bearish
            },
            'all_results': valid_results
        }
        
        return report


def main():
    """
    Main function to demonstrate Heikin Ashi signal detection
    """
    # Initialize detector
    detector = HeikinAshiSignalDetector()
    
    # Test with popular tickers
    test_tickers = [
        'AAPL', 'TSLA', 'GOOGL', 'MSFT', 'NVDA', 'META', 'AMZN', 'NFLX',
        'AMD', 'INTC', 'CRM', 'UBER', 'ROKU', 'ZM', 'PLTR', 'SNOW'
    ]
    
    # Scan for signals
    results = detector.scan_multiple_stocks(test_tickers)
    
    # Generate report
    report = detector.generate_signal_report(results)
    
    # Print results
    print("\n" + "="*80)
    print("HEIKIN ASHI SIGNAL DETECTION REPORT")
    print("="*80)
    
    print(f"\nReport Date: {report['report_date']}")
    print(f"Total Analyzed: {report['summary']['total_analyzed']}")
    print(f"Bullish Signals: {report['summary']['bullish_signals']}")
    print(f"Bearish Signals: {report['summary']['bearish_signals']}")
    print(f"High Confidence Bullish: {report['summary']['high_confidence_bullish']}")
    print(f"High Confidence Bearish: {report['summary']['high_confidence_bearish']}")
    
    if report['top_bullish_signals']:
        print("\n🟢 TOP BULLISH SIGNALS:")
        for signal in report['top_bullish_signals'][:5]:
            print(f"  {signal['ticker']}: {signal['primary_confidence']}% confidence")
            print(f"    Reasons: {', '.join(signal['primary_reasons'][:2])}")
    
    if report['top_bearish_signals']:
        print("\n🔴 TOP BEARISH SIGNALS:")
        for signal in report['top_bearish_signals'][:5]:
            print(f"  {signal['ticker']}: {signal['primary_confidence']}% confidence")
            print(f"    Reasons: {', '.join(signal['primary_reasons'][:2])}")
    
    print("\n" + "="*80)


if __name__ == "__main__":
    main()