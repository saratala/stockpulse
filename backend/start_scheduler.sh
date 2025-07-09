#!/bin/bash

# Start the signal prediction scheduler
echo "Starting StockPulse Signal Prediction Scheduler..."

# Set environment variables
export DB_HOST=${DB_HOST:-localhost}
export DB_PORT=${DB_PORT:-5432}
export DB_NAME=${DB_NAME:-stockpulse}
export DB_USER=${DB_USER:-postgres}
export DB_PASSWORD=${DB_PASSWORD:-password}

# Navigate to the backend directory
cd "$(dirname "$0")"

# Install dependencies if needed
if [ ! -d "venv" ]; then
    echo "Creating virtual environment..."
    python3 -m venv venv
fi

# Activate virtual environment
source venv/bin/activate

# Install/upgrade dependencies
pip install -r requirements.txt

# Run the scheduler
echo "Starting scheduler at $(date)"
python3 signal_prediction_scheduler.py