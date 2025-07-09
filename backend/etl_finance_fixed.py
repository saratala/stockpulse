"""
ETL script for fetching and storing stock price data with TimescaleDB compression handling
"""

import yfinance as yf
import pandas as pd
from sqlalchemy import create_engine, text
from sqlalchemy.orm import sessionmaker
from datetime import datetime, timedelta
import os
from dotenv import load_dotenv
import logging
import time
from typing import List, Dict, Any
import numpy as np

# Set up logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Load environment variables
load_dotenv()

# Database connection
DATABASE_URL = os.getenv("DATABASE_URL")
engine = create_engine(DATABASE_URL)
Session = sessionmaker(bind=engine)

# List of stocks to fetch
STOCKS = [
    'AAPL', 'MSFT', 'GOOGL', 'AMZN', 'TSLA', 'META', 'NVDA', 'BRK-B',
    'JNJ', 'JPM', 'V', 'PG', 'UNH', 'HD', 'MA', 'DIS', 'PYPL', 'BAC',
    'XOM', 'NFLX', 'KO', 'PFE', 'CMCSA', 'PEP', 'TMO', 'ABT', 'CVX',
    'WMT', 'CSCO', 'MRK', 'VZ', 'ADBE', 'AVGO', 'NKE', 'LLY', 'CRM',
    'INTC', 'WFC', 'ORCL', 'DHR', 'MCD', 'T', 'ACN', 'TXN', 'COST',
    'PM', 'NEE', 'UPS', 'MDT', 'BMY', 'QCOM', 'HON', 'RTX', 'IBM',
    'LIN', 'LOW', 'AMGN', 'BA', 'NOW', 'SPGI', 'GS', 'SBUX', 'CVS',
    'BLK', 'GILD', 'CAT', 'MO', 'ZTS', 'PLD', 'ISRG', 'BKNG', 'AXP',
    'MDLZ', 'MMC', 'DE', 'SYK', 'GE', 'INTU', 'AMD', 'CI', 'ADI',
    'AMAT', 'VRTX', 'TGT', 'TMUS', 'REGN', 'DUK', 'MU', 'CCI', 'F',
    'SLB', 'BDX', 'CSX', 'HUM', 'COP', 'BSX', 'EMR', 'KLAC', 'FDX'
]

def check_and_decompress_chunks(session, ticker: str, start_date: datetime, end_date: datetime):
    """
    Check if the date range falls into compressed chunks and decompress if needed
    """
    try:
        # Query to find compressed chunks that overlap with our date range
        query = text("""
            SELECT chunk_name, range_start, range_end
            FROM timescaledb_information.chunks
            WHERE hypertable_name = 'stock_prices'
            AND is_compressed = true
            AND range_start <= :end_date
            AND range_end >= :start_date
        """)
        
        result = session.execute(query, {
            'start_date': start_date,
            'end_date': end_date
        })
        
        compressed_chunks = result.fetchall()
        
        # Decompress any affected chunks
        for chunk in compressed_chunks:
            chunk_name = chunk[0]
            logger.info(f"Decompressing chunk {chunk_name} for ticker {ticker}")
            
            decompress_query = text(f"SELECT decompress_chunk('_timescaledb_internal.{chunk_name}')")
            session.execute(decompress_query)
            session.commit()
            
    except Exception as e:
        logger.warning(f"Error checking/decompressing chunks: {e}")
        # Continue anyway - might not have compression enabled

def insert_stock_data(ticker: str, data: pd.DataFrame, session):
    """
    Insert stock data with proper handling of TimescaleDB compression
    """
    try:
        # First, check if we need to decompress any chunks
        if not data.empty:
            start_date = data.index.min()
            end_date = data.index.max()
            
            # Try to decompress affected chunks
            check_and_decompress_chunks(session, ticker, start_date, end_date)
        
        # Process data insertion
        for date, row in data.iterrows():
            try:
                # Check if record exists
                check_query = text("""
                    SELECT 1 FROM stock_prices 
                    WHERE ticker = :ticker AND date = :date
                    LIMIT 1
                """)
                
                exists = session.execute(check_query, {
                    'ticker': ticker,
                    'date': date
                }).fetchone()
                
                if exists:
                    # Try to update existing record
                    update_query = text("""
                        UPDATE stock_prices 
                        SET open = :open, high = :high, low = :low, 
                            close = :close, volume = :volume, 
                            updated_at = CURRENT_TIMESTAMP
                        WHERE ticker = :ticker AND date = :date
                    """)
                    
                    session.execute(update_query, {
                        'ticker': ticker,
                        'date': date,
                        'open': float(row['Open']),
                        'high': float(row['High']),
                        'low': float(row['Low']),
                        'close': float(row['Close']),
                        'volume': int(row['Volume'])
                    })
                else:
                    # Insert new record
                    insert_query = text("""
                        INSERT INTO stock_prices (ticker, date, open, high, low, close, volume)
                        VALUES (:ticker, :date, :open, :high, :low, :close, :volume)
                    """)
                    
                    session.execute(insert_query, {
                        'ticker': ticker,
                        'date': date,
                        'open': float(row['Open']),
                        'high': float(row['High']),
                        'low': float(row['Low']),
                        'close': float(row['Close']),
                        'volume': int(row['Volume'])
                    })
                
                # Commit after each successful operation
                session.commit()
                
            except Exception as e:
                session.rollback()
                
                # If it's a compression error, try insert only (skip update)
                if "compressed" in str(e).lower():
                    try:
                        # Just try to insert, skip if exists
                        insert_query = text("""
                            INSERT INTO stock_prices (ticker, date, open, high, low, close, volume)
                            VALUES (:ticker, :date, :open, :high, :low, :close, :volume)
                            ON CONFLICT (ticker, date) DO NOTHING
                        """)
                        
                        session.execute(insert_query, {
                            'ticker': ticker,
                            'date': date,
                            'open': float(row['Open']),
                            'high': float(row['High']),
                            'low': float(row['Low']),
                            'close': float(row['Close']),
                            'volume': int(row['Volume'])
                        })
                        
                        session.commit()
                        logger.info(f"Inserted new data for {ticker} on {date} (skipped update due to compression)")
                        
                    except Exception as insert_error:
                        session.rollback()
                        logger.error(f"Error inserting data for {ticker} on {date}: {insert_error}")
                else:
                    logger.error(f"Error storing price data for {ticker} on {date}: {e}")
        
        logger.info(f"Successfully processed {len(data)} records for {ticker}")
        
    except Exception as e:
        session.rollback()
        logger.error(f"Critical error processing {ticker}: {e}")
        raise

def fetch_and_store_stock_data(ticker: str, period: str = '2y'):
    """
    Fetch stock data from Yahoo Finance and store in database
    """
    session = Session()
    
    try:
        logger.info(f"Fetching data for {ticker}...")
        
        # Fetch data from Yahoo Finance
        stock = yf.Ticker(ticker)
        data = stock.history(period=period)
        
        if data.empty:
            logger.warning(f"No data found for {ticker}")
            return
        
        # Clean data
        data = data[['Open', 'High', 'Low', 'Close', 'Volume']]
        data = data.dropna()
        
        # Insert data
        insert_stock_data(ticker, data, session)
        
    except Exception as e:
        logger.error(f"Error processing {ticker}: {e}")
        session.rollback()
    finally:
        session.close()

def ensure_stocks_exist():
    """
    Ensure all stocks exist in the stocks table
    """
    session = Session()
    
    try:
        for ticker in STOCKS:
            # Check if stock exists
            check_query = text("SELECT 1 FROM stocks WHERE ticker = :ticker LIMIT 1")
            exists = session.execute(check_query, {'ticker': ticker}).fetchone()
            
            if not exists:
                # Get stock info
                try:
                    stock = yf.Ticker(ticker)
                    info = stock.info
                    
                    insert_query = text("""
                        INSERT INTO stocks (ticker, name, sector, industry)
                        VALUES (:ticker, :name, :sector, :industry)
                        ON CONFLICT (ticker) DO NOTHING
                    """)
                    
                    session.execute(insert_query, {
                        'ticker': ticker,
                        'name': info.get('longName', ticker)[:100],
                        'sector': info.get('sector', 'Unknown')[:50],
                        'industry': info.get('industry', 'Unknown')[:50]
                    })
                    
                    session.commit()
                    logger.info(f"Added {ticker} to stocks table")
                    
                except Exception as e:
                    logger.error(f"Error adding {ticker} to stocks table: {e}")
                    session.rollback()
                    
                    # Add with minimal info
                    insert_query = text("""
                        INSERT INTO stocks (ticker, name, sector, industry)
                        VALUES (:ticker, :name, :sector, :industry)
                        ON CONFLICT (ticker) DO NOTHING
                    """)
                    
                    session.execute(insert_query, {
                        'ticker': ticker,
                        'name': ticker,
                        'sector': 'Unknown',
                        'industry': 'Unknown'
                    })
                    
                    session.commit()
                    
    except Exception as e:
        logger.error(f"Error ensuring stocks exist: {e}")
        session.rollback()
    finally:
        session.close()

def run_etl_batch(batch_size: int = 5):
    """
    Run ETL in batches to avoid overwhelming the API
    """
    logger.info(f"Starting ETL for {len(STOCKS)} stocks in batches of {batch_size}")
    
    # Ensure stocks exist first
    ensure_stocks_exist()
    
    # Process stocks in batches
    for i in range(0, len(STOCKS), batch_size):
        batch = STOCKS[i:i + batch_size]
        logger.info(f"Processing batch {i // batch_size + 1}: {batch}")
        
        for ticker in batch:
            try:
                fetch_and_store_stock_data(ticker)
                time.sleep(1)  # Rate limiting
            except Exception as e:
                logger.error(f"Failed to process {ticker}: {e}")
                continue
        
        # Sleep between batches
        if i + batch_size < len(STOCKS):
            logger.info("Sleeping between batches...")
            time.sleep(5)
    
    logger.info("ETL process completed")

def main():
    """
    Main ETL function
    """
    try:
        # Run the ETL process
        run_etl_batch(batch_size=5)
        
    except Exception as e:
        logger.error(f"ETL process failed: {e}")
        raise

if __name__ == "__main__":
    main()