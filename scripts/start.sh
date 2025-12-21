#!/bin/bash
set -e

echo "=== StockPulse Startup Script ==="

# Find PostgreSQL version and set paths
PG_VERSION=$(ls /usr/lib/postgresql/ | head -1)
PG_BIN="/usr/lib/postgresql/${PG_VERSION}/bin"
PG_DATA="/var/lib/postgresql/${PG_VERSION}/main"

echo "Using PostgreSQL version: ${PG_VERSION}"
echo "Binary path: ${PG_BIN}"
echo "Data path: ${PG_DATA}"

# Write the PostgreSQL paths for supervisor to use
echo "${PG_BIN}" > /tmp/pg_bin_path
echo "${PG_DATA}" > /tmp/pg_data_path

# Clean up any stale data directory
if [ -d "${PG_DATA}" ] && [ ! -f "${PG_DATA}/postgresql.conf" ]; then
    echo "Cleaning up incomplete PostgreSQL data directory..."
    rm -rf "${PG_DATA}"
fi

# Initialize PostgreSQL if needed
if [ ! -f "${PG_DATA}/postgresql.conf" ]; then
    echo "Initializing PostgreSQL..."
    mkdir -p "${PG_DATA}"
    chown -R postgres:postgres /var/lib/postgresql
    chmod 700 "${PG_DATA}"
    sudo -u postgres "${PG_BIN}/initdb" -D "${PG_DATA}" --encoding=UTF8 --locale=C

    # Configure authentication
    echo "host all all 0.0.0.0/0 md5" >> "${PG_DATA}/pg_hba.conf"
    echo "host all all ::0/0 md5" >> "${PG_DATA}/pg_hba.conf"
    sed -i "s/#listen_addresses = 'localhost'/listen_addresses = '*'/" "${PG_DATA}/postgresql.conf"

    echo "PostgreSQL initialized!"

    # Start PostgreSQL to create database
    echo "Starting PostgreSQL to create database..."
    sudo -u postgres "${PG_BIN}/pg_ctl" -D "${PG_DATA}" -l /tmp/pg.log start
    sleep 5

    # Create database and load data
    echo "Creating database..."
    sudo -u postgres createdb stockpulse
    echo "Loading schema..."
    sudo -u postgres psql stockpulse -f /app/init.sql || true

    # Load CSV data if available
    if [ -f /app/backup/stocks.csv ]; then
        echo "Loading pre-exported data from CSV files..."
        sudo -u postgres psql stockpulse -c "\COPY stocks(id, ticker, name, sector, industry, market_cap, created_at, updated_at) FROM '/app/backup/stocks.csv' WITH CSV HEADER" || true
        sudo -u postgres psql stockpulse -c "\COPY stock_prices(ticker, date, open, high, low, close, volume, adjusted_close) FROM '/app/backup/stock_prices.csv' WITH CSV HEADER" || true
        sudo -u postgres psql stockpulse -c "\COPY screening_results(id, ticker, screening_date, overall_score, technical_score, sentiment_score, momentum_score, volume_score, rsi, macd, bollinger_position, volume_ratio, created_at) FROM '/app/backup/screening_results.csv' WITH CSV HEADER" || true
        sudo -u postgres psql stockpulse -c "\COPY signal_predictions(id, ticker, signal_type, confidence, current_price, predicted_price_1h, predicted_price_1d, predicted_price_1w, rsi, macd, bollinger_position, volume, news_count, sentiment_score, sentiment_impact, sentiment_confidence, primary_reasons, sector, screening_score, timestamp, created_at) FROM '/app/backup/signal_predictions.csv' WITH CSV HEADER" || true
        sudo -u postgres psql stockpulse -c "\COPY news_articles(id, ticker, title, url, source, published_at, summary, sentiment, created_at) FROM '/app/backup/news_articles.csv' WITH CSV HEADER" || true
        sudo -u postgres psql stockpulse -c "\COPY sentiment_scores(id, ticker, score, magnitude, source, analysis_date, details, created_at) FROM '/app/backup/sentiment_scores.csv' WITH CSV HEADER" || true
        sudo -u postgres psql stockpulse -c "\COPY prediction_validations(id, prediction_id, ticker, signal_type, predicted_price, actual_price, prediction_horizon, predicted_at, validated_at, price_difference, percentage_error, is_direction_correct, created_at) FROM '/app/backup/prediction_validations.csv' WITH CSV HEADER" || true

        # Update sequences
        sudo -u postgres psql stockpulse -c "SELECT setval('stocks_id_seq', COALESCE((SELECT MAX(id) FROM stocks), 1));" || true
        sudo -u postgres psql stockpulse -c "SELECT setval('screening_results_id_seq', COALESCE((SELECT MAX(id) FROM screening_results), 1));" || true
        sudo -u postgres psql stockpulse -c "SELECT setval('signal_predictions_id_seq', COALESCE((SELECT MAX(id) FROM signal_predictions), 1));" || true
        sudo -u postgres psql stockpulse -c "SELECT setval('news_articles_id_seq', COALESCE((SELECT MAX(id) FROM news_articles), 1));" || true
        sudo -u postgres psql stockpulse -c "SELECT setval('sentiment_scores_id_seq', COALESCE((SELECT MAX(id) FROM sentiment_scores), 1));" || true
        sudo -u postgres psql stockpulse -c "SELECT setval('prediction_validations_id_seq', COALESCE((SELECT MAX(id) FROM prediction_validations), 1));" || true

        echo "CSV data loaded successfully!"
        sudo -u postgres psql stockpulse -c "SELECT 'stocks' as tbl, COUNT(*) FROM stocks UNION ALL SELECT 'stock_prices', COUNT(*) FROM stock_prices UNION ALL SELECT 'screening_results', COUNT(*) FROM screening_results;"
    else
        echo "No CSV backup files found, database will start empty."
    fi
    echo "Database created and data loaded!"

    # Stop PostgreSQL (supervisor will restart it)
    echo "Stopping PostgreSQL for supervisor handoff..."
    sudo -u postgres "${PG_BIN}/pg_ctl" -D "${PG_DATA}" stop
    sleep 2
else
    echo "PostgreSQL already initialized, skipping..."
fi

# Start all services via supervisor
echo "Starting supervisor..."
exec /usr/bin/supervisord -c /etc/supervisor/supervisord.conf
