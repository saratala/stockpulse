
import os
from datetime import datetime
import pandas as pd
import nltk
from nltk.sentiment.vader import SentimentIntensityAnalyzer
from sqlalchemy import Column, String, Float, DateTime, Integer, ForeignKey, text
from sqlalchemy.orm import declarative_base
from db import get_engine_with_retry

# Download VADER lexicon
nltk.download("vader_lexicon")

# Connect to DB with retry logic
engine = get_engine_with_retry()
Base = declarative_base()   

class SentimentScore(Base):
    __tablename__ = "sentiment_scores"
    id = Column(Integer, primary_key=True)
    ticker = Column(String, nullable=False)
    article_id = Column(Integer, ForeignKey('news_articles.id'), unique=True)
    sentiment_score = Column(Float, nullable=False)
    polarity = Column(String, nullable=False)
    content = Column(String)
    source = Column(String)
    created_at = Column(DateTime, server_default='NOW()')
    published_at = Column(DateTime, nullable=False)  # Add published_at column
    confidence = Column(Float, nullable=False)  # Add confidence column

def ensure_ticker_in_stocks(engine, ticker):
    query = """
        INSERT INTO stocks (ticker, name)
        VALUES (:ticker, :name)
        ON CONFLICT (ticker) DO NOTHING
    """
    stmt = text(query)
    with engine.begin() as conn:
        conn.execute(stmt, {"ticker": ticker, "name": ticker})
        conn.commit()

def score_sentiment_vader():
    print("🚀 Running VADER Sentiment Analysis...")
    sid = SentimentIntensityAnalyzer()

    # Load news articles from the past 3 days
    df = pd.read_sql(
        """
        SELECT id as article_id, ticker, title, published_at 
        FROM news_articles 
        WHERE published_at >= CURRENT_DATE - INTERVAL '3 days'
        """,
        engine
    )

    if df.empty:
        print("⚠️ No recent news articles found.")
        return

    # Calculate sentiment scores and confidence
    sentiment_results = df['title'].apply(lambda text: sid.polarity_scores(text))
    df['sentiment_score'] = sentiment_results.apply(lambda x: x['compound'])
    # Use the average of pos/neg/neu scores as confidence
    df['confidence'] = sentiment_results.apply(
        lambda x: max(x['pos'], x['neg'], x['neu'])
    )
    df['polarity'] = df['sentiment_score'].apply(
        lambda x: 'positive' if x > 0.1 else 'negative' if x < -0.1 else 'neutral'
    )
    df['source'] = 'vader'
    df['content'] = df['title']
    df['created_at'] = datetime.utcnow()

    # Verify all required columns are present
    print("Available columns:", df.columns.tolist())
    
    # Insert into database
    columns_to_insert = [
        'ticker', 'article_id', 'sentiment_score', 'polarity',
        'content', 'source', 'published_at', 'confidence', 'created_at'
    ]
    
    # Verify all columns exist before inserting
    missing_cols = [col for col in columns_to_insert if col not in df.columns]
    if missing_cols:
        raise ValueError(f"Missing columns: {missing_cols}")
    
    try:
        df[columns_to_insert].to_sql(
            'sentiment_scores', 
            engine, 
            if_exists='append', 
            index=False
        )

        print(f"✅ Sentiment scores inserted: {len(df)} rows")
    except Exception as e:
        print(f"❌ Error inserting sentiment scores: {str(e)}")
        print("DataFrame columns:", df.columns.tolist())

if __name__ == "__main__":
    score_sentiment_vader()
