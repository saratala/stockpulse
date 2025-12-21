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

        # Load stocks (matches CSV: id,ticker,name,sector,industry,market_cap,created_at,updated_at)
        sudo -u postgres psql stockpulse -c "\COPY stocks(id, ticker, name, sector, industry, market_cap, created_at, updated_at) FROM '/app/backup/stocks.csv' WITH CSV HEADER" || true

        # Load stock_prices (matches CSV: ticker,date,open,high,low,close,volume,adjusted_close)
        sudo -u postgres psql stockpulse -c "\COPY stock_prices(ticker, date, open, high, low, close, volume, adjusted_close) FROM '/app/backup/stock_prices.csv' WITH CSV HEADER" || true

        # Load signal_predictions (matches CSV: id,ticker,timestamp,current_price,signal_type,confidence,primary_reasons,screening_score,sector,predicted_price_1h,predicted_price_1d,predicted_price_1w,volume,rsi,macd,bollinger_position,sentiment_score,sentiment_confidence,sentiment_impact,news_count,created_at)
        sudo -u postgres psql stockpulse -c "\COPY signal_predictions(id, ticker, timestamp, current_price, signal_type, confidence, primary_reasons, screening_score, sector, predicted_price_1h, predicted_price_1d, predicted_price_1w, volume, rsi, macd, bollinger_position, sentiment_score, sentiment_confidence, sentiment_impact, news_count, created_at) FROM '/app/backup/signal_predictions.csv' WITH CSV HEADER" || true

        # Load news_articles (matches CSV: id,ticker,title,url,published_at,source,content,created_at)
        sudo -u postgres psql stockpulse -c "\COPY news_articles(id, ticker, title, url, published_at, source, content, created_at) FROM '/app/backup/news_articles.csv' WITH CSV HEADER" || true

        # Load sentiment_scores (matches CSV: id,ticker,article_id,news_article_id,sentiment_score,polarity,content,reasoning,analysis_provider,source,published_at,confidence,created_at)
        sudo -u postgres psql stockpulse -c "\COPY sentiment_scores(id, ticker, article_id, news_article_id, sentiment_score, polarity, content, reasoning, analysis_provider, source, published_at, confidence, created_at) FROM '/app/backup/sentiment_scores.csv' WITH CSV HEADER" || true

        # Load prediction_validations (matches CSV: id,ticker,prediction_timestamp,validation_timestamp,timeframe,signal_type,predicted_price,prediction_price,confidence,actual_price,predicted_change_percent,actual_change_percent,price_error,price_error_percent,direction_correct,within_5_percent,within_10_percent)
        sudo -u postgres psql stockpulse -c "\COPY prediction_validations(id, ticker, prediction_timestamp, validation_timestamp, timeframe, signal_type, predicted_price, prediction_price, confidence, actual_price, predicted_change_percent, actual_change_percent, price_error, price_error_percent, direction_correct, within_5_percent, within_10_percent) FROM '/app/backup/prediction_validations.csv' WITH CSV HEADER" || true

        # Update sequences
        sudo -u postgres psql stockpulse -c "SELECT setval('stocks_id_seq', COALESCE((SELECT MAX(id) FROM stocks), 1));" || true
        sudo -u postgres psql stockpulse -c "SELECT setval('news_articles_id_seq', COALESCE((SELECT MAX(id) FROM news_articles), 1));" || true
        sudo -u postgres psql stockpulse -c "SELECT setval('sentiment_scores_id_seq', COALESCE((SELECT MAX(id) FROM sentiment_scores), 1));" || true

        echo "CSV data loaded successfully!"
        sudo -u postgres psql stockpulse -c "SELECT 'stocks' as tbl, COUNT(*) FROM stocks UNION ALL SELECT 'stock_prices', COUNT(*) FROM stock_prices UNION ALL SELECT 'signal_predictions', COUNT(*) FROM signal_predictions;"
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
