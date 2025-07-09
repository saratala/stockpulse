"""
Enhanced Data Fetcher Module
Comprehensive technical indicators and data fetching for signal detection
"""

import yfinance as yf
import pandas as pd
from ta import trend, momentum, volatility, volume
import numpy as np
from datetime import datetime, timedelta
from typing import Dict, List, Tuple, Optional
import logging
from sqlalchemy import create_engine
import os
from dotenv import load_dotenv
from concurrent.futures import ThreadPoolExecutor, as_completed
import time

# Load environment variables
load_dotenv()

# Set up logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class EnhancedDataFetcher:
    """
    Enhanced data fetcher with comprehensive technical indicators
    """
    
    def __init__(self, db_engine=None):
        """Initialize the enhanced data fetcher"""
        self.engine = db_engine
        if db_engine is None and os.getenv("DATABASE_URL"):
            self.engine = create_engine(os.getenv("DATABASE_URL"))
    
    def fetch_comprehensive_data(self, ticker: str, period: str = "6mo", interval: str = "1d") -> Optional[pd.DataFrame]:
        """
        Fetch comprehensive stock data with all technical indicators
        """
        try:
            # Fetch stock data
            stock = yf.Ticker(ticker)
            df = stock.history(period=period, interval=interval)
            
            if df.empty:
                logger.warning(f"No data available for {ticker}")
                return None
            
            # Ensure we have required columns
            if not all(col in df.columns for col in ['Open', 'High', 'Low', 'Close', 'Volume']):
                logger.error(f"Missing required columns for {ticker}")
                return None
            
            # Add comprehensive technical indicators
            df = self._add_all_indicators(df)
            
            # Drop rows with NaN values
            df.dropna(inplace=True)
            
            return df
            
        except Exception as e:
            logger.error(f"Error fetching comprehensive data for {ticker}: {e}")
            return None
    
    def _add_all_indicators(self, df: pd.DataFrame) -> pd.DataFrame:
        """
        Add all technical indicators to the dataframe
        """
        # Moving Averages
        df['SMA_5'] = ta.sma(df['Close'], length=5)
        df['SMA_10'] = ta.sma(df['Close'], length=10)
        df['SMA_20'] = ta.sma(df['Close'], length=20)
        df['SMA_50'] = ta.sma(df['Close'], length=50)
        df['SMA_200'] = ta.sma(df['Close'], length=200)
        
        # Exponential Moving Averages
        df['EMA_8'] = ta.ema(df['Close'], length=8)
        df['EMA_13'] = ta.ema(df['Close'], length=13)
        df['EMA_21'] = ta.ema(df['Close'], length=21)
        df['EMA_34'] = ta.ema(df['Close'], length=34)
        df['EMA_55'] = ta.ema(df['Close'], length=55)
        df['EMA_89'] = ta.ema(df['Close'], length=89)
        
        # Momentum Indicators
        df['RSI_14'] = ta.rsi(df['Close'], length=14)
        df['RSI_21'] = ta.rsi(df['Close'], length=21)
        
        # Stochastic Oscillators
        stoch_14 = ta.stoch(df['High'], df['Low'], df['Close'], k=14, d=3)
        df['Stoch_K_14'] = stoch_14['STOCHk_14_3_3']
        df['Stoch_D_14'] = stoch_14['STOCHd_14_3_3']
        
        stoch_8 = ta.stoch(df['High'], df['Low'], df['Close'], k=8, d=3)
        df['Stoch_K_8'] = stoch_8['STOCHk_8_3_3']
        df['Stoch_D_8'] = stoch_8['STOCHd_8_3_3']
        
        # MACD
        macd = ta.macd(df['Close'], fast=12, slow=26, signal=9)
        df['MACD'] = macd['MACD_12_26_9']
        df['MACD_Signal'] = macd['MACDs_12_26_9']
        df['MACD_Histogram'] = macd['MACDh_12_26_9']
        
        # Bollinger Bands
        bb = ta.bbands(df['Close'], length=20, std=2)
        df['BB_Upper'] = bb['BBU_20_2.0']
        df['BB_Middle'] = bb['BBM_20_2.0']
        df['BB_Lower'] = bb['BBL_20_2.0']
        df['BB_Width'] = (df['BB_Upper'] - df['BB_Lower']) / df['BB_Middle']
        df['BB_Position'] = (df['Close'] - df['BB_Lower']) / (df['BB_Upper'] - df['BB_Lower'])
        
        # Average Directional Index (ADX)
        adx = ta.adx(df['High'], df['Low'], df['Close'], length=13)
        df['ADX_13'] = adx['ADX_13']
        df['DI_Plus'] = adx['DMP_13']
        df['DI_Minus'] = adx['DMN_13']
        
        adx_21 = ta.adx(df['High'], df['Low'], df['Close'], length=21)
        df['ADX_21'] = adx_21['ADX_21']
        
        # Average True Range (ATR)
        df['ATR_14'] = ta.atr(df['High'], df['Low'], df['Close'], length=14)
        df['ATR_21'] = ta.atr(df['High'], df['Low'], df['Close'], length=21)
        
        # Volume Indicators
        df['Volume_SMA_20'] = ta.sma(df['Volume'], length=20)
        df['Volume_Ratio'] = df['Volume'] / df['Volume_SMA_20']
        df['Volume_SMA_50'] = ta.sma(df['Volume'], length=50)
        
        # On-Balance Volume
        df['OBV'] = ta.obv(df['Close'], df['Volume'])
        
        # Volume Weighted Average Price (VWAP)
        df['VWAP'] = ta.vwap(df['High'], df['Low'], df['Close'], df['Volume'])
        
        # Commodity Channel Index (CCI)
        df['CCI_20'] = ta.cci(df['High'], df['Low'], df['Close'], length=20)
        
        # Williams %R
        df['WillR_14'] = ta.willr(df['High'], df['Low'], df['Close'], length=14)
        
        # Money Flow Index (MFI)
        df['MFI_14'] = ta.mfi(df['High'], df['Low'], df['Close'], df['Volume'], length=14)
        
        # Parabolic SAR
        df['PSAR'] = ta.psar(df['High'], df['Low'], df['Close'])['PSARl_0.02_0.2']
        
        # Keltner Channels
        kc = ta.kc(df['High'], df['Low'], df['Close'], length=20)
        df['KC_Upper'] = kc['KCUe_20_2']
        df['KC_Middle'] = kc['KCBe_20_2']
        df['KC_Lower'] = kc['KCLe_20_2']
        
        # Donchian Channels
        dc = ta.donchian(df['High'], df['Low'], lower_length=20, upper_length=20)
        df['DC_Upper'] = dc['DCU_20_20']
        df['DC_Middle'] = dc['DCM_20_20']
        df['DC_Lower'] = dc['DCL_20_20']
        
        # Price Returns
        df['Return_1D'] = df['Close'].pct_change() * 100
        df['Return_3D'] = df['Close'].pct_change(periods=3) * 100
        df['Return_5D'] = df['Close'].pct_change(periods=5) * 100
        df['Return_10D'] = df['Close'].pct_change(periods=10) * 100
        df['Return_20D'] = df['Close'].pct_change(periods=20) * 100
        
        # Volatility
        df['Volatility_10D'] = df['Return_1D'].rolling(window=10).std()
        df['Volatility_20D'] = df['Return_1D'].rolling(window=20).std()
        
        # Support and Resistance Levels
        df['Support_20D'] = df['Low'].rolling(window=20).min()
        df['Resistance_20D'] = df['High'].rolling(window=20).max()
        
        # Gap Analysis
        df['Gap_Up'] = (df['Open'] > df['Close'].shift(1)) & (df['Low'] > df['High'].shift(1))
        df['Gap_Down'] = (df['Open'] < df['Close'].shift(1)) & (df['High'] < df['Low'].shift(1))
        df['Gap_Size'] = np.where(df['Gap_Up'] | df['Gap_Down'], 
                                  abs(df['Open'] - df['Close'].shift(1)) / df['Close'].shift(1) * 100, 0)
        
        # Candlestick Patterns (simplified)
        df['Doji'] = abs(df['Close'] - df['Open']) <= (df['High'] - df['Low']) * 0.1
        df['Hammer'] = (df['High'] - df['Low']) > 3 * abs(df['Close'] - df['Open'])
        df['Shooting_Star'] = (df['High'] - np.maximum(df['Open'], df['Close'])) > 2 * abs(df['Close'] - df['Open'])
        
        # Trend Strength
        df['Trend_Strength'] = np.where(df['Close'] > df['SMA_20'], 
                                       (df['Close'] - df['SMA_20']) / df['SMA_20'] * 100,
                                       (df['Close'] - df['SMA_20']) / df['SMA_20'] * 100)
        
        return df
    
    def calculate_heikin_ashi(self, df: pd.DataFrame) -> pd.DataFrame:
        """
        Calculate Heikin Ashi candles
        """
        ha_df = df.copy()
        
        # Calculate Heikin Ashi values
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
        
        # Add Heikin Ashi trend indicators
        ha_df['HA_Bullish'] = ha_df['HA_Close'] > ha_df['HA_Open']
        ha_df['HA_Bearish'] = ha_df['HA_Close'] < ha_df['HA_Open']
        ha_df['HA_Strength'] = abs(ha_df['HA_Close'] - ha_df['HA_Open']) / ha_df['HA_Open'] * 100
        
        # Consecutive bullish/bearish candles
        ha_df['HA_Bullish_Count'] = (ha_df['HA_Bullish'] != ha_df['HA_Bullish'].shift(1)).cumsum()
        ha_df['HA_Bearish_Count'] = (ha_df['HA_Bearish'] != ha_df['HA_Bearish'].shift(1)).cumsum()
        
        return ha_df
    
    def get_market_regime(self, df: pd.DataFrame) -> Dict:
        """
        Determine current market regime based on technical indicators
        """
        if df.empty:
            return {'regime': 'unknown', 'strength': 0}
        
        latest = df.iloc[-1]
        
        # Trend indicators
        ema_trend = 1 if latest['Close'] > latest['EMA_21'] else -1
        sma_trend = 1 if latest['Close'] > latest['SMA_50'] else -1
        
        # Momentum indicators
        rsi_momentum = 1 if latest['RSI_14'] > 50 else -1
        macd_momentum = 1 if latest['MACD'] > latest['MACD_Signal'] else -1
        
        # Volume confirmation
        volume_confirm = 1 if latest['Volume_Ratio'] > 1.0 else 0
        
        # ADX trend strength
        adx_strong = 1 if latest['ADX_13'] > 25 else 0
        
        # Calculate regime score
        regime_score = ema_trend + sma_trend + rsi_momentum + macd_momentum
        
        if regime_score >= 2:
            regime = 'bullish'
        elif regime_score <= -2:
            regime = 'bearish'
        else:
            regime = 'neutral'
        
        # Calculate strength
        strength = abs(regime_score) * 25 + adx_strong * 25 + volume_confirm * 25
        
        return {
            'regime': regime,
            'strength': min(100, strength),
            'components': {
                'ema_trend': ema_trend,
                'sma_trend': sma_trend,
                'rsi_momentum': rsi_momentum,
                'macd_momentum': macd_momentum,
                'volume_confirm': volume_confirm,
                'adx_strong': adx_strong
            }
        }
    
    def get_key_levels(self, df: pd.DataFrame) -> Dict:
        """
        Calculate key support and resistance levels
        """
        if df.empty:
            return {}
        
        # Get recent data for level calculation
        recent_data = df.tail(50)
        
        # Pivot points calculation
        high = recent_data['High'].max()
        low = recent_data['Low'].min()
        close = recent_data['Close'].iloc[-1]
        
        pivot = (high + low + close) / 3
        r1 = 2 * pivot - low
        s1 = 2 * pivot - high
        r2 = pivot + (high - low)
        s2 = pivot - (high - low)
        
        # Moving average levels
        sma_levels = {
            'SMA_20': recent_data['SMA_20'].iloc[-1],
            'SMA_50': recent_data['SMA_50'].iloc[-1],
            'SMA_200': recent_data['SMA_200'].iloc[-1] if not pd.isna(recent_data['SMA_200'].iloc[-1]) else None
        }
        
        # Bollinger Band levels
        bb_levels = {
            'BB_Upper': recent_data['BB_Upper'].iloc[-1],
            'BB_Middle': recent_data['BB_Middle'].iloc[-1],
            'BB_Lower': recent_data['BB_Lower'].iloc[-1]
        }
        
        return {
            'pivot_points': {
                'pivot': round(pivot, 2),
                'r1': round(r1, 2),
                'r2': round(r2, 2),
                's1': round(s1, 2),
                's2': round(s2, 2)
            },
            'moving_averages': {k: round(v, 2) if v is not None else None for k, v in sma_levels.items()},
            'bollinger_bands': {k: round(v, 2) for k, v in bb_levels.items()},
            'recent_high': round(high, 2),
            'recent_low': round(low, 2),
            'current_price': round(close, 2)
        }
    
    def analyze_volume_profile(self, df: pd.DataFrame) -> Dict:
        """
        Analyze volume profile and patterns
        """
        if df.empty:
            return {}
        
        recent_data = df.tail(20)
        
        # Volume statistics
        avg_volume = recent_data['Volume'].mean()
        max_volume = recent_data['Volume'].max()
        min_volume = recent_data['Volume'].min()
        current_volume = recent_data['Volume'].iloc[-1]
        
        # Volume trend
        volume_trend = 'increasing' if recent_data['Volume'].iloc[-5:].mean() > recent_data['Volume'].iloc[-10:-5].mean() else 'decreasing'
        
        # Price-volume relationship
        price_up_volume = recent_data[recent_data['Return_1D'] > 0]['Volume'].mean()
        price_down_volume = recent_data[recent_data['Return_1D'] < 0]['Volume'].mean()
        
        # On-balance volume trend
        obv_trend = 'increasing' if recent_data['OBV'].iloc[-1] > recent_data['OBV'].iloc[-10] else 'decreasing'
        
        return {
            'volume_stats': {
                'avg_volume': int(avg_volume),
                'max_volume': int(max_volume),
                'min_volume': int(min_volume),
                'current_volume': int(current_volume),
                'volume_ratio': round(current_volume / avg_volume, 2)
            },
            'volume_trend': volume_trend,
            'price_volume_analysis': {
                'price_up_volume': int(price_up_volume) if not pd.isna(price_up_volume) else 0,
                'price_down_volume': int(price_down_volume) if not pd.isna(price_down_volume) else 0,
                'accumulation_distribution': 'accumulation' if price_up_volume > price_down_volume else 'distribution'
            },
            'obv_trend': obv_trend
        }
    
    def get_comprehensive_analysis(self, ticker: str, period: str = "6mo") -> Dict:
        """
        Get comprehensive analysis for a single ticker
        """
        # Fetch comprehensive data
        df = self.fetch_comprehensive_data(ticker, period)
        if df is None:
            return {'error': f'Could not fetch data for {ticker}'}
        
        # Calculate Heikin Ashi
        ha_df = self.calculate_heikin_ashi(df)
        
        # Get latest values
        latest = df.iloc[-1]
        ha_latest = ha_df.iloc[-1]
        
        # Get market regime
        regime = self.get_market_regime(df)
        
        # Get key levels
        key_levels = self.get_key_levels(df)
        
        # Get volume analysis
        volume_analysis = self.analyze_volume_profile(df)
        
        # Compile comprehensive analysis
        analysis = {
            'ticker': ticker,
            'analysis_date': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            'current_price': round(latest['Close'], 2),
            'market_regime': regime,
            'key_levels': key_levels,
            'volume_analysis': volume_analysis,
            'technical_indicators': {
                'trend': {
                    'ema_8': round(latest['EMA_8'], 2),
                    'ema_21': round(latest['EMA_21'], 2),
                    'sma_20': round(latest['SMA_20'], 2),
                    'sma_50': round(latest['SMA_50'], 2),
                    'sma_200': round(latest['SMA_200'], 2) if not pd.isna(latest['SMA_200']) else None
                },
                'momentum': {
                    'rsi_14': round(latest['RSI_14'], 2),
                    'stoch_k': round(latest['Stoch_K_14'], 2),
                    'stoch_d': round(latest['Stoch_D_14'], 2),
                    'macd': round(latest['MACD'], 4),
                    'macd_signal': round(latest['MACD_Signal'], 4),
                    'macd_histogram': round(latest['MACD_Histogram'], 4)
                },
                'volatility': {
                    'atr_14': round(latest['ATR_14'], 2),
                    'bb_width': round(latest['BB_Width'], 4),
                    'bb_position': round(latest['BB_Position'], 4),
                    'volatility_20d': round(latest['Volatility_20D'], 2)
                },
                'strength': {
                    'adx_13': round(latest['ADX_13'], 2),
                    'adx_21': round(latest['ADX_21'], 2),
                    'di_plus': round(latest['DI_Plus'], 2),
                    'di_minus': round(latest['DI_Minus'], 2)
                }
            },
            'heikin_ashi': {
                'ha_close': round(ha_latest['HA_Close'], 2),
                'ha_open': round(ha_latest['HA_Open'], 2),
                'ha_high': round(ha_latest['HA_High'], 2),
                'ha_low': round(ha_latest['HA_Low'], 2),
                'ha_bullish': ha_latest['HA_Bullish'],
                'ha_strength': round(ha_latest['HA_Strength'], 2)
            },
            'performance': {
                'return_1d': round(latest['Return_1D'], 2),
                'return_5d': round(latest['Return_5D'], 2),
                'return_10d': round(latest['Return_10D'], 2),
                'return_20d': round(latest['Return_20D'], 2)
            }
        }
        
        return analysis
    
    def batch_comprehensive_analysis(self, tickers: List[str], max_workers: int = 10) -> Dict:
        """
        Run comprehensive analysis on multiple tickers in parallel
        """
        results = {}
        
        logger.info(f"Starting comprehensive analysis of {len(tickers)} tickers...")
        
        # Use ThreadPoolExecutor for parallel processing
        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            # Submit all tasks
            future_to_ticker = {
                executor.submit(self.get_comprehensive_analysis, ticker): ticker 
                for ticker in tickers
            }
            
            # Process completed tasks
            for future in as_completed(future_to_ticker):
                ticker = future_to_ticker[future]
                try:
                    result = future.result()
                    results[ticker] = result
                except Exception as e:
                    logger.error(f"Error analyzing {ticker}: {e}")
                    results[ticker] = {'error': str(e)}
        
        logger.info(f"Comprehensive analysis completed for {len(results)} tickers.")
        return results


def main():
    """
    Main function to demonstrate enhanced data fetcher
    """
    fetcher = EnhancedDataFetcher()
    
    # Test with a few popular tickers
    test_tickers = ['AAPL', 'TSLA', 'GOOGL', 'MSFT', 'NVDA']
    
    print("Running comprehensive analysis...")
    results = fetcher.batch_comprehensive_analysis(test_tickers)
    
    for ticker, analysis in results.items():
        if 'error' in analysis:
            print(f"\n❌ {ticker}: {analysis['error']}")
            continue
            
        print(f"\n📊 {ticker} - Comprehensive Analysis")
        print(f"Current Price: ${analysis['current_price']}")
        print(f"Market Regime: {analysis['market_regime']['regime'].upper()} (Strength: {analysis['market_regime']['strength']}%)")
        print(f"RSI: {analysis['technical_indicators']['momentum']['rsi_14']}")
        print(f"ADX: {analysis['technical_indicators']['strength']['adx_13']}")
        print(f"Volume Ratio: {analysis['volume_analysis']['volume_stats']['volume_ratio']}")
        print(f"Heikin Ashi: {'🟢 Bullish' if analysis['heikin_ashi']['ha_bullish'] else '🔴 Bearish'}")


if __name__ == "__main__":
    main()