"""
ETL script to fetch historical data for all tickers, compute advanced technical indicators using TA-Lib, and store in the technicals table.
"""
import yfinance as yf
import pandas as pd
from sqlalchemy import create_engine, text
from dotenv import load_dotenv
import os

# Load env and DB
load_dotenv()
DATABASE_URL = os.getenv("DATABASE_URL")
engine = create_engine(DATABASE_URL)

# Fetch all tickers from DB
def get_all_tickers():
    with engine.begin() as conn:
        result = conn.execute(text("SELECT ticker FROM stocks"))
        return [row[0] for row in result]

# Compute all technicals using yfinance (pandas-ta or custom)
def compute_technicals(df):
    # Basic price columns
    close = df['Close']
    high = df['High']
    low = df['Low']
    open_ = df['Open']
    volume = df['Volume']

    # Compute a comprehensive set of technical indicators using pandas-ta
    try:
        from ta import trend, momentum, volatility, volume
        import ta
        # Add all default pandas-ta indicators
        df = ta.strategy("All")
        df = ta.add_all_ta_features(df, open="Open", high="High", low="Low", close="Close", volume="Volume", fillna=True)
    except ImportError:
        # Fallback: Only basic indicators if pandas-ta is not available
        df['sma_20'] = df['Close'].rolling(window=20).mean()
        df['sma_50'] = df['Close'].rolling(window=50).mean()
        df['sma_200'] = df['Close'].rolling(window=200).mean()
        delta = df['Close'].diff()
        gain = (delta.where(delta > 0, 0)).rolling(window=14).mean()
        loss = (-delta.where(delta < 0, 0)).rolling(window=14).mean()
        rs = gain / loss
        df['rsi'] = 100 - (100 / (1 + rs))
        exp1 = df['Close'].ewm(span=12, adjust=False).mean()
        exp2 = df['Close'].ewm(span=26, adjust=False).mean()
        df['macd'] = exp1 - exp2
        df['macd_signal'] = df['macd'].ewm(span=9, adjust=False).mean()
        df['macd_hist'] = df['macd'] - df['macd_signal']
    return df

# Main ETL for all tickers
def fetch_and_store_all():
    tickers = get_all_tickers()
    for ticker in tickers:
        print(f"Fetching {ticker} historical data...")
        df = yf.download(ticker, period="5y")
        if df.empty:
            print(f"No data for {ticker}, skipping.")
            continue
        df = df.reset_index()
        df['ticker'] = ticker
        df['date'] = pd.to_datetime(df['Date'])
        df = compute_technicals(df)
        cols = [
            'ticker', 'date',
            'sma_20', 'sma_50', 'sma_200', 'rsi', 'macd', 'macd_signal', 'macd_hist'
        ]
        df_out = df[cols].dropna(subset=['date'])
        print(f"Writing {len(df_out)} rows to technicals for {ticker}...")
        df_out.to_sql('technicals', engine, if_exists='append', index=False, method='multi')
        print(f"Done with {ticker}.")

if __name__ == "__main__":
    fetch_and_store_all()
