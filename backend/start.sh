#!/bin/bash
set -e

echo "Starting ETL and ML pipeline..."

# Run ETL and ML scripts
python etl_finance.py
python sentiment_vader.py
python predict_engine.py
python predict_daily.py &

echo "Starting FastAPI server..."
# Start the FastAPI server
uvicorn main:app --host 0.0.0.0 --port 8000

echo "Starting Dashboard..."
# Start Dashboard in foreground (last process)
python dashboard.py