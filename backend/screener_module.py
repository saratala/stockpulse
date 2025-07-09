"""
Advanced Stock Screener Module
Filters stocks based on technical and fundamental criteria for options trading
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
import requests
from concurrent.futures import ThreadPoolExecutor, as_completed
import time

# Load environment variables
load_dotenv()

# Set up logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class StockScreener:
    """
    Advanced stock screener for options trading setups
    """
    
    def __init__(self, db_engine=None):
        """Initialize the stock screener"""
        self.engine = db_engine
        if db_engine is None and os.getenv("DATABASE_URL"):
            self.engine = create_engine(os.getenv("DATABASE_URL"))
        
        # S&P 500 tickers for screening (top liquid stocks)
        self.sp500_tickers = [
            'AAPL', 'MSFT', 'GOOGL', 'AMZN', 'NVDA', 'TSLA', 'META', 'BRK-B',
            'UNH', 'XOM', 'JNJ', 'JPM', 'V', 'PG', 'MA', 'HD', 'CVX', 'ABBV',
            'LLY', 'BAC', 'KO', 'AVGO', 'PEP', 'TMO', 'COST', 'MRK', 'WMT',
            'ACN', 'ABT', 'CSCO', 'DHR', 'VZ', 'ADBE', 'TXN', 'LIN', 'NKE',
            'CRM', 'QCOM', 'NEE', 'RTX', 'CMCSA', 'AMD', 'UPS', 'HON', 'ORCL',
            'PM', 'IBM', 'INTU', 'LOW', 'AMGN', 'SPGI', 'CAT', 'GS', 'BKNG',
            'AXP', 'BA', 'DE', 'MDT', 'BLK', 'GILD', 'NOW', 'NFLX', 'ISRG',
            'ELV', 'MMC', 'SYK', 'CVS', 'MO', 'ZTS', 'MDLZ', 'ADI', 'ANET',
            'C', 'LRCX', 'PLD', 'SCHW', 'CB', 'PYPL', 'SO', 'FI', 'REGN',
            'MU', 'DUK', 'BMY', 'EQIX', 'BSX', 'AON', 'KLAC', 'ICE', 'SHW',
            'PNC', 'CME', 'USB', 'SNPS', 'HCA', 'CDNS', 'ITW', 'WM', 'GD',
            'CL', 'APD', 'EMR', 'TJX', 'FCX', 'NSC', 'ORLY', 'MMM', 'MAR',
            'INTC', 'COF', 'MCK', 'CSX', 'ETN', 'ECL', 'ROP', 'AJG', 'CARR',
            'PCAR', 'NXPI', 'ADSK', 'MCHP', 'CPRT', 'ROST', 'PAYX', 'FAST',
            'ODFL', 'KMB', 'BDX', 'MNST', 'AEP', 'GWW', 'VRSK', 'CTSH'
        ]
        
        # Large cap tech stocks with high options volume
        self.popular_options_tickers = [
            'AAPL', 'MSFT', 'GOOGL', 'AMZN', 'TSLA', 'META', 'NVDA', 'NFLX',
            'AMD', 'INTC', 'CRM', 'UBER', 'SHOP', 'SQ', 'ROKU', 'ZM',
            'PLTR', 'NET', 'SNOW', 'CRWD', 'OKTA', 'DDOG', 'TWLO', 'ZS',
            'ABNB', 'COIN', 'HOOD', 'RBLX', 'RIVN', 'LCID', 'SOFI', 'UPST',
            'ARKK', 'QQQ', 'SPY', 'IWM', 'TLT', 'GLD', 'SQQQ', 'TQQQ'
        ]
    
    def fetch_stock_data(self, ticker: str, period: str = "6mo", interval: str = "1d") -> Optional[pd.DataFrame]:
        """
        Fetch stock data with technical indicators
        """
        try:
            # Fetch stock data
            stock = yf.Ticker(ticker)
            df = stock.history(period=period, interval=interval)
            
            if df.empty:
                logger.warning(f"No data available for {ticker}")
                return None
            
            # Calculate EMAs
            df['EMA_8'] = trend.EMAIndicator(df['Close'], window=8).ema_indicator()
            df['EMA_13'] = trend.EMAIndicator(df['Close'], window=13).ema_indicator()
            df['EMA_21'] = trend.EMAIndicator(df['Close'], window=21).ema_indicator()
            df['EMA_34'] = trend.EMAIndicator(df['Close'], window=34).ema_indicator()
            df['EMA_55'] = trend.EMAIndicator(df['Close'], window=55).ema_indicator()
            df['EMA_89'] = trend.EMAIndicator(df['Close'], window=89).ema_indicator()
            
            # Calculate ADX
            df['ADX'] = trend.ADXIndicator(df['High'], df['Low'], df['Close'], window=13).adx()
            
            # Calculate Stochastic
            stoch = momentum.StochasticOscillator(df['High'], df['Low'], df['Close'], window=8, smooth_window=3)
            df['Stoch_K'] = stoch.stoch()
            df['Stoch_D'] = stoch.stoch_signal()
            
            # Calculate RSI
            df['RSI'] = momentum.RSIIndicator(df['Close'], window=14).rsi()
            
            # Calculate ATR
            df['ATR'] = volatility.AverageTrueRange(df['High'], df['Low'], df['Close'], window=14).average_true_range()
            
            # Calculate volume moving average
            df['Volume_MA'] = df['Volume'].rolling(window=20).mean()
            df['Volume_Ratio'] = df['Volume'] / df['Volume_MA']
            
            # Drop NaN values
            df.dropna(inplace=True)
            
            return df
            
        except Exception as e:
            logger.error(f"Error fetching data for {ticker}: {e}")
            return None
    
    def get_stock_info(self, ticker: str) -> Dict:
        """
        Get stock fundamental information
        """
        try:
            stock = yf.Ticker(ticker)
            info = stock.info
            
            return {
                'name': info.get('longName', ticker),
                'sector': info.get('sector', 'Unknown'),
                'industry': info.get('industry', 'Unknown'),
                'market_cap': info.get('marketCap', 0),
                'float_shares': info.get('floatShares', 0),
                'avg_volume': info.get('averageVolume', 0),
                'beta': info.get('beta', 1.0),
                'pe_ratio': info.get('trailingPE', 0),
                'price_to_book': info.get('priceToBook', 0)
            }
        except Exception as e:
            logger.error(f"Error getting stock info for {ticker}: {e}")
            return {
                'name': ticker,
                'sector': 'Unknown',
                'industry': 'Unknown',
                'market_cap': 0,
                'float_shares': 0,
                'avg_volume': 0,
                'beta': 1.0,
                'pe_ratio': 0,
                'price_to_book': 0
            }
    
    def passes_ema_stack_screen(self, df: pd.DataFrame) -> bool:
        """
        Check if stock passes EMA stack bullish alignment: 8 > 13 > 21 > 34 > 55 > 89
        """
        if df.empty:
            return False
            
        latest = df.iloc[-1]
        
        # Check EMA stack alignment
        ema_stack = (
            latest['EMA_8'] > latest['EMA_13'] > latest['EMA_21'] > 
            latest['EMA_34'] > latest['EMA_55'] > latest['EMA_89']
        )
        
        return ema_stack
    
    def passes_momentum_screen(self, df: pd.DataFrame) -> bool:
        """
        Check if stock passes momentum criteria
        """
        if df.empty:
            return False
            
        latest = df.iloc[-1]
        
        # ADX should be > 20 for trending market
        adx_ok = pd.notna(latest['ADX']) and latest['ADX'] > 20
        
        # Stochastic %K should be < 40 for potential oversold bounce
        stoch_ok = pd.notna(latest['Stoch_K']) and latest['Stoch_K'] < 40
        
        # RSI should be between 30-70 for healthy momentum
        rsi_ok = pd.notna(latest['RSI']) and 30 <= latest['RSI'] <= 70
        
        return adx_ok and stoch_ok and rsi_ok
    
    def passes_volume_screen(self, df: pd.DataFrame) -> bool:
        """
        Check if stock passes volume criteria
        """
        if df.empty:
            return False
            
        latest = df.iloc[-1]
        
        # Volume should be above average for confirmation
        volume_ok = pd.notna(latest['Volume_Ratio']) and latest['Volume_Ratio'] > 1.0
        
        return volume_ok
    
    def passes_fundamental_screen(self, stock_info: Dict) -> bool:
        """
        Check if stock passes fundamental criteria
        """
        # Market cap should be > $100B for large cap stocks
        market_cap_ok = stock_info['market_cap'] > 100_000_000_000
        
        # Should have reasonable trading volume
        volume_ok = stock_info['avg_volume'] > 1_000_000
        
        return market_cap_ok and volume_ok
    
    def calculate_screening_score(self, df: pd.DataFrame, stock_info: Dict) -> Dict:
        """
        Calculate a comprehensive screening score
        """
        if df.empty:
            return {'score': 0, 'components': {}}
            
        latest = df.iloc[-1]
        components = {}
        
        # EMA Stack Score (0-30)
        if self.passes_ema_stack_screen(df):
            components['ema_stack'] = 30
        else:
            components['ema_stack'] = 0
            
        # Momentum Score (0-25)
        momentum_score = 0
        if pd.notna(latest['ADX']) and latest['ADX'] > 20:
            momentum_score += 10
        if pd.notna(latest['Stoch_K']) and latest['Stoch_K'] < 40:
            momentum_score += 8
        if pd.notna(latest['RSI']) and 30 <= latest['RSI'] <= 70:
            momentum_score += 7
        components['momentum'] = momentum_score
        
        # Volume Score (0-20)
        volume_score = 0
        if pd.notna(latest['Volume_Ratio']) and latest['Volume_Ratio'] > 1.5:
            volume_score += 15
        elif pd.notna(latest['Volume_Ratio']) and latest['Volume_Ratio'] > 1.0:
            volume_score += 10
        components['volume'] = volume_score
        
        # Fundamental Score (0-15)
        fundamental_score = 0
        if stock_info['market_cap'] > 100_000_000_000:
            fundamental_score += 10
        if stock_info['avg_volume'] > 1_000_000:
            fundamental_score += 5
        components['fundamental'] = fundamental_score
        
        # Technical Strength Score (0-10)
        tech_score = 0
        if pd.notna(latest['RSI']) and 40 <= latest['RSI'] <= 60:
            tech_score += 5
        if pd.notna(latest['ADX']) and latest['ADX'] > 30:
            tech_score += 5
        components['technical'] = tech_score
        
        total_score = sum(components.values())
        
        return {
            'score': total_score,
            'components': components,
            'max_score': 100
        }
    
    def screen_single_stock(self, ticker: str) -> Optional[Dict]:
        """
        Screen a single stock against all criteria
        """
        try:
            logger.info(f"Screening {ticker}...")
            
            # Fetch stock data
            df = self.fetch_stock_data(ticker)
            if df is None:
                return None
            
            # Get stock info
            stock_info = self.get_stock_info(ticker)
            
            # Calculate screening score
            score_data = self.calculate_screening_score(df, stock_info)
            
            # Check if passes basic screens
            ema_pass = self.passes_ema_stack_screen(df)
            momentum_pass = self.passes_momentum_screen(df)
            volume_pass = self.passes_volume_screen(df)
            fundamental_pass = self.passes_fundamental_screen(stock_info)
            
            # Get latest values
            latest = df.iloc[-1]
            
            result = {
                'ticker': ticker,
                'name': stock_info['name'],
                'sector': stock_info['sector'],
                'industry': stock_info['industry'],
                'current_price': round(latest['Close'], 2),
                'market_cap': stock_info['market_cap'],
                'screening_score': score_data['score'],
                'score_components': score_data['components'],
                'passes_ema_stack': ema_pass,
                'passes_momentum': momentum_pass,
                'passes_volume': volume_pass,
                'passes_fundamental': fundamental_pass,
                'overall_pass': all([ema_pass, momentum_pass, volume_pass, fundamental_pass]),
                'ema_8': round(latest['EMA_8'], 2),
                'ema_13': round(latest['EMA_13'], 2),
                'ema_21': round(latest['EMA_21'], 2),
                'ema_34': round(latest['EMA_34'], 2),
                'ema_55': round(latest['EMA_55'], 2),
                'ema_89': round(latest['EMA_89'], 2),
                'adx': round(latest['ADX'], 2) if pd.notna(latest['ADX']) else None,
                'stoch_k': round(latest['Stoch_K'], 2) if pd.notna(latest['Stoch_K']) else None,
                'stoch_d': round(latest['Stoch_D'], 2) if pd.notna(latest['Stoch_D']) else None,
                'rsi': round(latest['RSI'], 2) if pd.notna(latest['RSI']) else None,
                'atr': round(latest['ATR'], 2) if pd.notna(latest['ATR']) else None,
                'volume_ratio': round(latest['Volume_Ratio'], 2) if pd.notna(latest['Volume_Ratio']) else None,
                'avg_volume': stock_info['avg_volume'],
                'beta': stock_info['beta'],
                'pe_ratio': stock_info['pe_ratio'],
                'screened_at': datetime.now().strftime('%Y-%m-%d %H:%M:%S')
            }
            
            return result
            
        except Exception as e:
            logger.error(f"Error screening {ticker}: {e}")
            return None
    
    def run_screening(self, tickers: List[str] = None, max_workers: int = 10) -> List[Dict]:
        """
        Run screening on multiple stocks in parallel
        """
        if tickers is None:
            # Use combination of S&P 500 and popular options tickers
            tickers = list(set(self.sp500_tickers + self.popular_options_tickers))
        
        results = []
        
        logger.info(f"Starting screening of {len(tickers)} stocks...")
        
        # Use ThreadPoolExecutor for parallel processing
        with ThreadPoolExecutor(max_workers=max_workers) as executor:
            # Submit all tasks
            future_to_ticker = {
                executor.submit(self.screen_single_stock, ticker): ticker 
                for ticker in tickers
            }
            
            # Process completed tasks
            for future in as_completed(future_to_ticker):
                ticker = future_to_ticker[future]
                try:
                    result = future.result()
                    if result:
                        results.append(result)
                except Exception as e:
                    logger.error(f"Error processing {ticker}: {e}")
                    continue
        
        # Sort results by screening score
        results.sort(key=lambda x: x['screening_score'], reverse=True)
        
        logger.info(f"Screening completed. {len(results)} stocks analyzed.")
        return results
    
    def get_top_candidates(self, results: List[Dict], min_score: int = 70) -> List[Dict]:
        """
        Get top screening candidates above minimum score
        """
        return [r for r in results if r['screening_score'] >= min_score]
    
    def get_passed_screens(self, results: List[Dict]) -> List[Dict]:
        """
        Get stocks that passed all screening criteria
        """
        return [r for r in results if r['overall_pass']]
    
    def generate_screening_report(self, results: List[Dict]) -> Dict:
        """
        Generate a comprehensive screening report
        """
        total_analyzed = len(results)
        passed_all = len([r for r in results if r['overall_pass']])
        top_candidates = len([r for r in results if r['screening_score'] >= 70])
        
        # Category breakdown
        ema_pass = len([r for r in results if r['passes_ema_stack']])
        momentum_pass = len([r for r in results if r['passes_momentum']])
        volume_pass = len([r for r in results if r['passes_volume']])
        fundamental_pass = len([r for r in results if r['passes_fundamental']])
        
        # Top performers
        top_10 = results[:10]
        
        # Sector analysis
        sector_counts = {}
        for result in results:
            sector = result['sector']
            if sector not in sector_counts:
                sector_counts[sector] = 0
            sector_counts[sector] += 1
        
        report = {
            'report_date': datetime.now().strftime('%Y-%m-%d %H:%M:%S'),
            'summary': {
                'total_analyzed': total_analyzed,
                'passed_all_screens': passed_all,
                'top_candidates': top_candidates,
                'screen_breakdown': {
                    'ema_stack': ema_pass,
                    'momentum': momentum_pass,
                    'volume': volume_pass,
                    'fundamental': fundamental_pass
                }
            },
            'top_10_candidates': top_10,
            'sector_distribution': dict(sorted(sector_counts.items(), key=lambda x: x[1], reverse=True)),
            'all_results': results
        }
        
        return report


def main():
    """
    Main function to run the stock screener
    """
    screener = StockScreener()
    
    # Run screening
    results = screener.run_screening()
    
    # Generate report
    report = screener.generate_screening_report(results)
    
    # Print summary
    print("\n" + "="*80)
    print("ADVANCED STOCK SCREENER REPORT")
    print("="*80)
    
    print(f"\nReport Date: {report['report_date']}")
    print(f"Total Stocks Analyzed: {report['summary']['total_analyzed']}")
    print(f"Passed All Screens: {report['summary']['passed_all_screens']}")
    print(f"Top Candidates (Score ≥ 70): {report['summary']['top_candidates']}")
    
    print("\n📊 SCREENING BREAKDOWN:")
    print(f"EMA Stack: {report['summary']['screen_breakdown']['ema_stack']}")
    print(f"Momentum: {report['summary']['screen_breakdown']['momentum']}")
    print(f"Volume: {report['summary']['screen_breakdown']['volume']}")
    print(f"Fundamental: {report['summary']['screen_breakdown']['fundamental']}")
    
    if report['top_10_candidates']:
        print("\n🎯 TOP 10 CANDIDATES:")
        for i, stock in enumerate(report['top_10_candidates'], 1):
            print(f"{i}. {stock['ticker']} - {stock['name'][:40]}...")
            print(f"   Score: {stock['screening_score']}/100 | Price: ${stock['current_price']}")
            print(f"   Sector: {stock['sector']} | ADX: {stock['adx']} | RSI: {stock['rsi']}")
            print(f"   Passes All: {'✅' if stock['overall_pass'] else '❌'}")
    
    print("\n" + "="*80)


if __name__ == "__main__":
    main()