"""
Background script to run all advanced models (XGBoost, LSTM, Prophet) on latest stock features and store predictions in the predictions table.
"""
import os
import pandas as pd
import joblib
from datetime import datetime, timedelta
from sqlalchemy import create_engine
from dotenv import load_dotenv
from advanced_models import train_xgboost, predict_xgboost, train_lstm, predict_lstm, train_prophet, predict_prophet

# Load environment variables
load_dotenv()
DATABASE_URL = os.getenv("DATABASE_URL")
engine = create_engine(DATABASE_URL)

# Model versions
MODEL_VERSIONS = {
    'xgboost': 'xgboost_v1',
    'lstm': 'lstm_v1',
    'prophet': 'prophet_v1',
}

FEATURES = ['sma_20', 'sma_50', 'sma_200', 'rsi']

# Fetch latest features for all tickers
def fetch_latest_features():
    query = '''
    WITH latest_technical AS (
        SELECT DISTINCT ON (ticker) *
        FROM technicals
        ORDER BY ticker, date DESC
    )
    SELECT
        ticker,
        sma_20,
        sma_50,
        sma_200,
        rsi,
        date
    FROM latest_technical
    WHERE sma_20 IS NOT NULL AND rsi IS NOT NULL
    '''
    return pd.read_sql(query, engine)

def store_predictions(df, model_name, prediction_date, target_date, proba, pred):
    predictions_df = pd.DataFrame({
        'ticker': df['ticker'],
        'prediction_date': prediction_date,
        'target_date': target_date,
        'predicted_movement_percent': proba,
        'predicted_direction': pred,
        'confidence_score': proba,
        'model_version': MODEL_VERSIONS[model_name]
    })
    predictions_df.to_sql('predictions', engine, if_exists='append', index=False)
    print(f"✅ {model_name} predictions saved.")

def run_xgboost():
    df = fetch_latest_features()
    if df.empty:
        print("⚠️ No data for XGBoost.")
        return
    # For demo, use last 1000 rows for training
    train_df = df.tail(1000)
    X = train_df[FEATURES]
    y = (train_df['sma_20'] > train_df['sma_50']).astype(int)  # Dummy target for demo
    model = train_xgboost(X, y)
    joblib.dump(model, 'xgboost_model.pkl')
    pred, proba = predict_xgboost(model, df[FEATURES])
    prediction_date = pd.Timestamp.now(tz='UTC')
    target_date = prediction_date + pd.Timedelta(days=1)
    store_predictions(df, 'xgboost', prediction_date, target_date, proba, pred)

def run_lstm():
    df = fetch_latest_features()
    if df.empty:
        print("⚠️ No data for LSTM.")
        return
    train_df = df.tail(1000)
    X = train_df[FEATURES]
    y = (train_df['sma_20'] > train_df['sma_50']).astype(int)  # Dummy target for demo
    model, scaler = train_lstm(X, y)
    joblib.dump((model, scaler), 'lstm_model.pkl')
    pred, proba = predict_lstm(model, scaler, df[FEATURES])
    prediction_date = pd.Timestamp.now(tz='UTC')
    target_date = prediction_date + pd.Timedelta(days=1)
    store_predictions(df, 'lstm', prediction_date, target_date, proba, pred)

def run_prophet():
    df = fetch_latest_features()
    if df.empty:
        print("⚠️ No data for Prophet.")
        return
    # Prophet expects a time series per ticker; here, just demo on one ticker
    for ticker in df['ticker'].unique():
        ticker_df = df[df['ticker'] == ticker].copy()
        if len(ticker_df) < 10:
            continue
        ticker_df = ticker_df.sort_values('date')
        prophet_df = ticker_df[['date', 'sma_20']].rename(columns={'date': 'ds', 'sma_20': 'y'})
        model = train_prophet(prophet_df, date_col='ds', target_col='y')
        future = pd.DataFrame({'ds': [prophet_df['ds'].max() + timedelta(days=1)]})
        forecast = predict_prophet(model, future)
        prediction_date = pd.Timestamp.now(tz='UTC')
        target_date = forecast['ds'].iloc[0]
        proba = forecast['yhat'].iloc[0]
        pred = int(proba > prophet_df['y'].mean())
        store_predictions(pd.DataFrame({'ticker': [ticker]}), 'prophet', prediction_date, target_date, [proba], [pred])

def main():
    run_xgboost()
    run_lstm()
    run_prophet()

if __name__ == "__main__":
    main()
