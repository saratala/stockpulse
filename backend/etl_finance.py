import yfinance as yf
from sqlalchemy import Column, String, Float, DateTime, Integer
from sqlalchemy.orm import declarative_base, sessionmaker
from sqlalchemy.exc import SQLAlchemyError
from datetime import datetime, timedelta
import logging
import os
from dotenv import load_dotenv
import pandas as pd
from db import get_engine_with_retry
import requests
import numpy as np

# -------------------------------
# Configuration
# -------------------------------
load_dotenv()
FINNHUB_API_KEY = os.getenv("FINNHUB_API_KEY")
FINNHUB_NEWS_URL = os.getenv("FINNHUB_NEWS_URL")
tickers_raw = os.getenv("TICKERS", "")
TICKERS = [ticker.strip() for ticker in tickers_raw.split(",") if ticker.strip()]
DAYS_BACK = int(os.getenv("DAYS_BACK", 30))

# -------------------------------
# Logging Setup
# -------------------------------
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# -------------------------------
# DB Setup
# -------------------------------
Base = declarative_base()

class Price(Base):
    __tablename__ = "stock_prices"  # Changed from "prices" to match new schema
    ticker = Column(String, primary_key=True)
    date = Column(DateTime, primary_key=True)
    open = Column(Float)
    high = Column(Float)
    low = Column(Float)
    close = Column(Float)
    volume = Column(Float)

class Technical(Base):
    __tablename__ = "technicals"
    ticker = Column(String, primary_key=True)
    date = Column(DateTime, primary_key=True)
    sma_20 = Column(Float)
    sma_50 = Column(Float)
    sma_200 = Column(Float)
    rsi = Column(Float)
    macd = Column(Float)
    macd_signal = Column(Float)
    macd_hist = Column(Float)

class Stock(Base):
    __tablename__ = "stocks"
    id = Column(Integer, primary_key=True)
    ticker = Column(String, unique=True, nullable=False)
    name = Column(String, nullable=False)
    sector = Column(String)
    industry = Column(String)
    created_at = Column(DateTime, server_default='NOW()')
    updated_at = Column(DateTime, server_default='NOW()')

class NewsArticle(Base):
    __tablename__ = "news_articles"
    id = Column(Integer, primary_key=True, autoincrement=True)
    ticker = Column(String, nullable=False)
    title = Column(String, nullable=False)
    url = Column(String, unique=True, nullable=False)
    published_at = Column(DateTime, nullable=False)
    source = Column(String, nullable=False)
    content = Column(String)
    created_at = Column(DateTime, server_default='NOW()')

engine = get_engine_with_retry()
Session = sessionmaker(bind=engine)
session = Session()
Base.metadata.create_all(engine)

# -------------------------------
# Helpers
# -------------------------------

def ensure_stock_exists(ticker: str):
    """Ensure stock exists in the stocks table."""
    try:
        # Check if stock exists
        stock = session.query(Stock).filter_by(ticker=ticker).first()
        if not stock:
            # Get stock info from yfinance
            stock_info = yf.Ticker(ticker).info
            stock = Stock(
                ticker=ticker,
                name=stock_info.get('longName', ticker),
                sector=stock_info.get('sector'),
                industry=stock_info.get('industry')
            )
            session.add(stock)
            session.commit()
            logger.info(f"Added new stock: {ticker}")
    except Exception as e:
        session.rollback()
        logger.error(f"Error ensuring stock exists for {ticker}: {str(e)}")
        raise

def calculate_indicators(df: pd.DataFrame) -> pd.DataFrame:
    """Calculate technical indicators for the given DataFrame."""
    try:
        # Convert column names to lowercase and handle multi-level columns
        if isinstance(df.columns, pd.MultiIndex):
            # Select the price level columns and drop the ticker level
            df = df.droplevel(1, axis=1)
        
        # Convert all column names to lowercase
        df.columns = df.columns.str.lower()
         # Debug log after column transformation
        logger.info(f"Transformed columns: {df.columns.tolist()}")
        # Verify required columns exist
        required_columns = ['close']
        missing_columns = [col for col in required_columns if col not in df.columns]
        if missing_columns:
            raise ValueError(f"Missing required columns: {missing_columns}")
            
        # Calculate SMAs
        df['sma_20'] = df['close'].rolling(window=20, min_periods=1).mean()
        df['sma_50'] = df['close'].rolling(window=50, min_periods=1).mean()
        df['sma_200'] = df['close'].rolling(window=200, min_periods=1).mean()

        # Calculate RSI
        delta = df['close'].diff()
        gain = delta.clip(lower=0)
        loss = -delta.clip(upper=0)
        
        avg_gain = gain.rolling(window=14, min_periods=1).mean()
        avg_loss = loss.rolling(window=14, min_periods=1).mean()
        
        # Handle division by zero
        rs = avg_gain / avg_loss
        rs = rs.replace([np.inf, -np.inf], np.nan)
        
        df['rsi'] = 100 - (100 / (1 + rs))
        
        # Round values for consistency
        df = df.round(4)
        
        # Debug log the columns
        logger.info(f"DataFrame columns after calculations: {df.columns.tolist()}")
        
        return df
    except Exception as e:
        logger.error(f"Error calculating indicators: {str(e)}")
        raise

def fetch_and_store_news(ticker: str):
    logger.info(f"Fetching news for {ticker} from Finnhub")
    try:
        today = datetime.utcnow().date()
        from_date = (today - timedelta(days=3)).isoformat()
        to_date = today.isoformat()

        params = {
            "symbol": ticker,
            "from": from_date,
            "to": to_date,
            "token": FINNHUB_API_KEY
        }

        response = requests.get(FINNHUB_NEWS_URL, params=params)
        response.raise_for_status()
        articles = response.json()

        processed_count = 0
        for article in articles:
            title = article.get("headline","")
            url = article.get("url","")
            summary = article.get("summary","")
            published_at = datetime.utcfromtimestamp(article["datetime"])
            source = article.get("source")
            category = article.get("category","")

            if not title or not url:
                logger.warning(f"Skipping article with missing title or URL for {ticker}")
                continue
             # Check if article already exists
            # Create full content by combining available fields
            content = {
                "summary": summary,
                "category": category,
                "raw_data": article  # Store complete raw data
            }

            existing_article = session.query(NewsArticle).filter_by(url=url).first()
            
            if existing_article:
                logger.debug(f"Skipping existing article: {title}")
                continue

            news = NewsArticle(
                ticker=ticker,
                title=title,
                url=url,
                published_at=published_at,
                source=source,
                content=str(content)  # Store as string
            )
            session.add(news)
            session.flush()
            processed_count +=1
            
            if processed_count % 5 == 0:
                session.commit()  # Flush to get the ID for logging
                logger.debug(f"Committed batch of {processed_count} articles for {ticker}")

    except SQLAlchemyError as e:
        session.rollback()
        logger.error(f"DB Error while fetching news for {ticker}: {str(e)}")
    except Exception as e:
        session.rollback()
        logger.error(f"News ingestion failed for {ticker}: {e}")
    # Final commit for remaining articles
    session.commit()
    logger.info(f"✅ Saved {processed_count} news articles for {ticker}")
# -------------------------------
def fetch_and_store_prices(ticker: str):
    """Fetch and store price and technical data for a given ticker."""
    logger.info(f"Processing {ticker}...")

    try:
        # Verify stock exists before proceeding
        stock = session.query(Stock).filter_by(ticker=ticker).first()
        if not stock:
            raise ValueError(f"Stock {ticker} not found in database")
        
        end = datetime.today()
        start = end - timedelta(days=DAYS_BACK)
        df = yf.download(ticker, start=start, end=end, auto_adjust=False)

        if df.empty:
            logger.warning(f"No data for {ticker}")
            return

        # Debug log the DataFrame columns
        logger.info(f"Original DataFrame columns: {df.columns.tolist()}")
        
        # Calculate indicators
        df = calculate_indicators(df)
        
        # Debug log after calculations
        logger.info(f"Processed DataFrame head:\n{df.head()}")

        for index, row in df.iterrows():
            date_val = index.to_pydatetime()
            
            # Store price data
            try:
                price = Price(
                    ticker=ticker,
                    date=date_val,
                    open=float(row['open']) if pd.notnull(row['open']) else None,
                    high=float(row['high']) if pd.notnull(row['high']) else None,
                    low=float(row['low']) if pd.notnull(row['low']) else None,
                    close=float(row['close']) if pd.notnull(row['close']) else None,
                    volume=float(row['volume']) if pd.notnull(row['volume']) else None
                )
                session.merge(price)
            except Exception as e:
                logger.error(f"Error storing price data for {ticker} on {date_val}: {str(e)}")
                continue

            # Store technical data only if all indicators are available
            try:
                if all(pd.notnull(row[col]) for col in ['sma_20', 'sma_50', 'sma_200', 'rsi']):
                    technical = Technical(
                        ticker=ticker,
                        date=date_val,
                        sma_20=float(row['sma_20']),
                        sma_50=float(row['sma_50']),
                        sma_200=float(row['sma_200']),
                        rsi=float(row['rsi'])
                    )
                    session.merge(technical)
                    
                    # Debug output
                    # logger.info(f"✅ Technical data for {ticker} on {date_val}:")
                    # logger.info(f"  SMA20: {row['sma_20']:.2f}")
                    # logger.info(f"  SMA50: {row['sma_50']:.2f}")
                    # logger.info(f"  SMA200: {row['sma_200']:.2f}")
                    # logger.info(f"  RSI: {row['rsi']:.2f}")
            except Exception as e:
                logger.error(f"Error storing technical data for {ticker} on {date_val}: {str(e)}")
                continue

        session.commit()
        logger.info(f"✅ Saved data for {ticker}")

    except SQLAlchemyError as e:
        session.rollback()
        logger.error(f"DB Error for {ticker}: {str(e)}")
    except Exception as e:
        session.rollback()
        logger.error(f"Failed for {ticker}: {str(e)}")

# -------------------------------
# Main
# -------------------------------
if __name__ == "__main__":
    for ticker in TICKERS:
        try:
            ensure_stock_exists(ticker)
            fetch_and_store_prices(ticker)
            fetch_and_store_news(ticker)
        except Exception as e:
            logger.error(f"Failed to process {ticker}: {str(e)}")
            continue
