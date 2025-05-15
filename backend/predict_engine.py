
import os
import pandas as pd
import numpy as np
from datetime import timedelta
from sqlalchemy import create_engine, text
from dotenv import load_dotenv
from sklearn.ensemble import RandomForestClassifier
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report
import joblib
from db import get_engine_with_retry

# Load environment variables
load_dotenv()
# Connect to PostgreSQL
engine = get_engine_with_retry()

def load_training_data():
    """Load training data from database with proper connection handling."""
    try:
        # Get a fresh connection
        engine = get_engine_with_retry()
        
        query = text('''
        WITH price_changes AS (
            SELECT
                p1.ticker,
                p1.date,
                p1.close AS close_today,
                p2.close AS close_next_day,
                ROUND((((p2.close - p1.close) / p1.close) * 100)::NUMERIC, 2) AS pct_change
            FROM stock_prices p1
            JOIN stock_prices p2
              ON p1.ticker = p2.ticker AND p2.date = p1.date + INTERVAL '1 day'
            WHERE p1.date >= NOW() - INTERVAL '3 years'
        ),
        sentiment_avg AS (
            SELECT
                ticker,
                DATE(created_at) AS date,
                AVG(sentiment_score) AS avg_sentiment
            FROM sentiment_scores
            GROUP BY ticker, DATE(created_at)
        )
        SELECT
            pc.ticker,
            pc.date,
            pc.pct_change,
            CASE 
                WHEN pc.pct_change >= 3 THEN 1
                WHEN pc.pct_change <= -3 THEN 1
                ELSE 0 
            END AS target_move,
            t.sma_20, 
            t.sma_50, 
            t.sma_200, 
            t.rsi,
            COALESCE(s.avg_sentiment, 0) as avg_sentiment
        FROM price_changes pc
        JOIN technicals t ON pc.ticker = t.ticker AND pc.date = t.date
        LEFT JOIN sentiment_avg s ON pc.ticker = s.ticker AND pc.date = s.date
        WHERE t.sma_20 IS NOT NULL 
          AND t.sma_50 IS NOT NULL
          AND t.sma_200 IS NOT NULL 
          AND t.rsi IS NOT NULL
        ORDER BY pc.date DESC
        ''')
        
        # Use context manager for the connection
        with engine.connect() as connection:
            df = pd.read_sql_query(query, connection)
            print(f"✅ Successfully loaded {len(df)} samples from database")
            return df
            
    except Exception as e:
        print(f"❌ Error loading training data: {str(e)}")
        return pd.DataFrame()
    finally:
        if engine:
            engine.dispose()

def train_model():
    # Load and validate training data
    df = load_training_data()
    if df.empty:
        print("⚠️ No training data found.")
        return

    features = ['sma_20', 'sma_50', 'sma_200', 'rsi', 'avg_sentiment']
    target = 'target_move'  # Changed from target_move_10pct to match SQL query
    
    # Drop rows with any missing values in features or target
    df = df.dropna(subset=features + [target])

    # Check if we have enough data after cleaning
    if len(df) < 50:
        print(f"⚠️ Insufficient data points ({len(df)}) for training. Need at least 50 samples.")
        return

    print(f"ℹ️ Training with {len(df)} samples")

    X = df[features]
    y = df[target]
    
    # Debug information
    print("\nFeature columns present:", X.columns.tolist())
    print("Target column present:", target)
    print("Number of samples:", len(df))

    # Ensure we have both classes represented
    if len(y.unique()) < 2:
        print("⚠️ Training data only contains one class. Need both 0 and 1 classes.")
        return

    # Add this before train_test_split
    print("\nData Overview:")
    print(f"Total samples: {len(df)}")
    print("\nClass distribution:")
    print(y.value_counts(normalize=True))
    print("\nFeature statistics:")
    print(X.describe())

    try:
        # Split with stratification to maintain class distribution
        X_train, X_test, y_train, y_test = train_test_split(
            X, y, 
            test_size=0.2, 
            random_state=42,
            stratify=y
        )

        # Train model
        clf = RandomForestClassifier(
            n_estimators=100, 
            random_state=42,
            min_samples_split=5,
            min_samples_leaf=2
        )
        clf.fit(X_train, y_train)

        # Get probabilities instead of just predictions
        y_pred_proba = clf.predict_proba(X_test)
        y_pred = (y_pred_proba[:, 1] >= 0.5).astype(int)  # Use 0.5 threshold

        # Convert predictions to allowed values (-1, 0, 1)
        y_pred_direction = np.where(
            y_pred == 1,
            np.where(y_pred_proba[:, 1] >= 0.7, 1, 0),  # High confidence for upward movement
            np.where(y_pred_proba[:, 1] <= 0.3, -1, 0)  # High confidence for downward movement
        )

        # Evaluate and save
        y_pred = clf.predict(X_test)
        print("\n✅ Model Evaluation:")
        print(classification_report(y_test, y_pred))

        # Save predictions to database
        prediction_date = pd.Timestamp.now(tz='UTC')
        target_date = prediction_date + pd.Timedelta(days=1)

         # Create predictions DataFrame ensuring all columns are present
        predictions_df = pd.DataFrame({
            'ticker': df['ticker'].unique(),
            'prediction_date': [prediction_date] * len(df['ticker'].unique()),
            'target_date': [target_date] * len(df['ticker'].unique()),
            'predicted_movement_percent': y_pred_proba[:len(df['ticker'].unique()), 1],
            'predicted_direction': y_pred_direction[:len(df['ticker'].unique())],
            'confidence_score': np.max(y_pred_proba[:len(df['ticker'].unique())], axis=1),
            'model_version': ['rf_v1'] * len(df['ticker'].unique())
        })

        # Verify all required columns are present and not null
        required_columns = [
            'ticker', 'prediction_date', 'target_date', 
            'predicted_movement_percent', 'predicted_direction',
            'confidence_score', 'model_version'
        ]

        missing_cols = [col for col in required_columns if col not in predictions_df.columns]
        
        if missing_cols:
            raise ValueError(f"Missing columns in predictions_df: {missing_cols}")
    
        print("\n✅ Model Evaluation:")
        print(classification_report(y_test, y_pred))

        # Check for null values
        null_counts = predictions_df[required_columns].isnull().sum()
        if null_counts.any():
            raise ValueError(f"Found null values in columns: {null_counts[null_counts > 0]}")

        # Save to database
        predictions_df.to_sql(
            'predictions',
            engine,
            if_exists='append',
            index=False
        )

        # Save model
        model_metadata = {
            'model': clf,
            'features': features,
            'version': 'rf_v1',
            'trained_at': prediction_date.isoformat()
        }

        # Save model
        model_path = "movement_predictor.pkl"
        joblib.dump(clf, model_path)
        print(f"✅ Model saved to {model_path}")

    except Exception as e:
        print(f"❌ Error during model training: {str(e)}")

def predict_movement(features_df, model_metadata):
    """
    Predict market movement direction using the trained model.
    Returns -1 (down), 0 (neutral), or 1 (up)
    """
    try:
        clf = model_metadata['model']
        required_features = model_metadata['features']
        
        # Ensure all required features are present
        if not all(feature in features_df.columns for feature in required_features):
            missing = [f for f in required_features if f not in features_df.columns]
            raise ValueError(f"Missing features: {missing}")
            
        # Get prediction probabilities
        proba = clf.predict_proba(features_df[required_features])[0]
        
        # Convert to direction using thresholds
        if proba[1] >= model_metadata['threshold_up']:
            return 1  # Strong upward signal
        elif proba[1] <= model_metadata['threshold_down']:
            return -1  # Strong downward signal
        else:
            return 0  # Neutral
            
    except Exception as e:
        print(f"❌ Prediction error: {str(e)}")
        return 0  # Return neutral on error

if __name__ == "__main__":
    train_model()
