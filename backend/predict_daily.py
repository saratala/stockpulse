
import os
import pandas as pd
import joblib
from datetime import datetime
from sqlalchemy import create_engine
from dotenv import load_dotenv

# Load environment variables
load_dotenv()
DATABASE_URL = os.getenv("DATABASE_URL")

# Load model
model_path = "movement_predictor.pkl"
if not os.path.exists(model_path):
    raise FileNotFoundError(f"Model file '{model_path}' not found. Train the model first.")

clf = joblib.load(model_path)
engine = create_engine(DATABASE_URL)

def fetch_latest_features():
    query = '''
    WITH latest_technical AS (
        SELECT DISTINCT ON (ticker) *
        FROM technicals
        ORDER BY ticker, date DESC
    ),
    latest_sentiment AS (
        SELECT
            ticker,
            MAX(date(created_at)) AS latest_date
        FROM sentiment_scores
        GROUP BY ticker
    ),
    sentiment_avg AS (
        SELECT
            s.ticker,
            AVG(s.sentiment_score) AS avg_sentiment
        FROM sentiment_scores s
        JOIN latest_sentiment ls ON s.ticker = ls.ticker AND DATE(s.created_at) = ls.latest_date
        GROUP BY s.ticker
    )
    SELECT
        t.ticker,
        t.sma_20,
        t.sma_50,
        t.sma_200,
        t.rsi,
        s.avg_sentiment
    FROM latest_technical t
    LEFT JOIN sentiment_avg s ON t.ticker = s.ticker
    WHERE t.sma_20 IS NOT NULL AND t.rsi IS NOT NULL
    '''
    return pd.read_sql(query, engine)

def predict_today():
    df = fetch_latest_features()
    if df.empty:
        print("⚠️ No data to predict.")
        return

    features = ['sma_20', 'sma_50', 'sma_200', 'rsi', 'avg_sentiment']
    df = df.dropna(subset=features)
    X = df[features]

    # Make predictions
    df['predicted_move'] = clf.predict(X)
    df['probability'] = clf.predict_proba(X)[:, 1]
    
    # Set prediction and target dates with timezone awareness
    prediction_date = pd.Timestamp.now(tz='UTC')
    target_date = prediction_date + pd.Timedelta(days=1)
    
    df['prediction_date'] = prediction_date
    df['target_date'] = target_date
    df['model_version'] = 'rf_v1'
    df['confidence_score'] = df['probability']

    print("📈 Top predicted stocks likely to move ±10%:")
    print(df[['ticker', 'probability', 'predicted_move']].head(10))

    # Prepare predictions DataFrame with all required columns
    predictions_df = df[['ticker', 'prediction_date', 'target_date', 'probability', 
                        'predicted_move', 'confidence_score', 'model_version']]
    
    # Rename columns to match database schema
    predictions_df = predictions_df.rename(columns={
        'probability': 'predicted_movement_percent',
        'predicted_move': 'predicted_direction'
    })

    # Verify no null values
    if predictions_df.isnull().any().any():
        raise ValueError("Found null values in predictions DataFrame")

    # Save to database
    predictions_df.to_sql('predictions', engine, if_exists='append', index=False)
    print("✅ Predictions saved to 'predictions' table.")

if __name__ == "__main__":
    predict_today()
