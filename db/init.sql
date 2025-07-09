-- Enable TimescaleDB extension
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- -------------------------
-- Table: stocks (base table for stock information)
-- -------------------------
CREATE TABLE IF NOT EXISTS stocks (
    id SERIAL PRIMARY KEY,
    ticker TEXT UNIQUE NOT NULL,
    name TEXT NOT NULL,
    sector TEXT,
    industry TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- -------------------------
-- Table: stock_prices (time-series hypertable)
-- -------------------------
CREATE TABLE IF NOT EXISTS stock_prices (
    ticker TEXT NOT NULL REFERENCES stocks(ticker),
    date TIMESTAMPTZ NOT NULL,
    open DOUBLE PRECISION NOT NULL,
    high DOUBLE PRECISION NOT NULL,
    low DOUBLE PRECISION NOT NULL,
    close DOUBLE PRECISION NOT NULL,
    volume BIGINT NOT NULL,
    adjusted_close DOUBLE PRECISION,
    PRIMARY KEY (ticker, date),
    CONSTRAINT price_validation CHECK (
        high >= low AND 
        high >= open AND 
        high >= close AND 
        low <= open AND 
        low <= close AND 
        volume >= 0
    )
);

-- Convert to hypertable with partitioning
SELECT create_hypertable('stock_prices', 'date', 
    chunk_time_interval => INTERVAL '1 week',
    if_not_exists => TRUE
);

-- -------------------------
-- Table: technicals (time-series hypertable)
-- -------------------------
CREATE TABLE IF NOT EXISTS technicals (
    ticker TEXT NOT NULL REFERENCES stocks(ticker),
    date TIMESTAMPTZ NOT NULL,
    sma_20 DOUBLE PRECISION,
    sma_50 DOUBLE PRECISION,
    sma_200 DOUBLE PRECISION,
    ema_20 DOUBLE PRECISION,
    ema_50 DOUBLE PRECISION,
    rsi DOUBLE PRECISION,
    macd DOUBLE PRECISION,
    macd_signal DOUBLE PRECISION,
    macd_hist DOUBLE PRECISION,
    stoch_k DOUBLE PRECISION,
    stoch_d DOUBLE PRECISION,
    bollinger_upper DOUBLE PRECISION,
    bollinger_middle DOUBLE PRECISION,
    bollinger_lower DOUBLE PRECISION,
    adx DOUBLE PRECISION,
    cci DOUBLE PRECISION,
    willr DOUBLE PRECISION,
    obv DOUBLE PRECISION,
    atr DOUBLE PRECISION,
    PRIMARY KEY (ticker, date),
    CONSTRAINT rsi_range CHECK (rsi >= 0 AND rsi <= 100)
);

-- Convert technicals to hypertable
SELECT create_hypertable('technicals', 'date', 
    chunk_time_interval => INTERVAL '1 week',
    if_not_exists => TRUE
);

-- -------------------------
-- Table: news_articles
-- -------------------------
CREATE TABLE IF NOT EXISTS news_articles (
    id SERIAL PRIMARY KEY,
    ticker TEXT NOT NULL REFERENCES stocks(ticker),
    title TEXT NOT NULL,
    url TEXT UNIQUE NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    source TEXT NOT NULL,
    content TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- -------------------------
-- Table: sentiment_scores
-- -------------------------
CREATE TABLE IF NOT EXISTS sentiment_scores (
    id SERIAL PRIMARY KEY,
    ticker TEXT NOT NULL REFERENCES stocks(ticker),
    article_id INTEGER REFERENCES news_articles(id),
    sentiment_score DOUBLE PRECISION NOT NULL,
    polarity TEXT NOT NULL,
    content TEXT,
    source TEXT NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT sentiment_score_range CHECK (sentiment_score >= -1 AND sentiment_score <= 1),
    CONSTRAINT polarity_values CHECK (polarity IN ('positive', 'neutral', 'negative')),
    CONSTRAINT confidence_range CHECK (confidence >= 0 AND confidence <= 1),
    CONSTRAINT sentiment_article_unique UNIQUE (article_id)
);

-- -------------------------
-- Table: predictions
-- -------------------------
CREATE TABLE IF NOT EXISTS predictions (
    id SERIAL PRIMARY KEY,
    ticker TEXT NOT NULL REFERENCES stocks(ticker),
    prediction_date TIMESTAMPTZ NOT NULL,
    target_date TIMESTAMPTZ NOT NULL,
    predicted_movement_percent DOUBLE PRECISION NOT NULL,
    predicted_direction INTEGER NOT NULL,
    confidence_score DOUBLE PRECISION NOT NULL,
    model_version TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT direction_values CHECK (predicted_direction IN (-1, 0, 1)),
    CONSTRAINT confidence_range CHECK (confidence_score >= 0 AND confidence_score <= 1),
    CONSTRAINT future_target CHECK (target_date > prediction_date)
);

-- -------------------------
-- Table: signal_predictions (time-series hypertable for 5-minute predictions)
-- -------------------------
CREATE TABLE IF NOT EXISTS signal_predictions (
    id SERIAL PRIMARY KEY,
    ticker TEXT NOT NULL REFERENCES stocks(ticker),
    timestamp TIMESTAMPTZ NOT NULL,
    current_price DOUBLE PRECISION NOT NULL,
    signal_type TEXT NOT NULL,
    confidence DOUBLE PRECISION NOT NULL,
    primary_reasons TEXT[],
    screening_score DOUBLE PRECISION NOT NULL,
    sector TEXT,
    predicted_price_1h DOUBLE PRECISION,
    predicted_price_1d DOUBLE PRECISION,
    predicted_price_1w DOUBLE PRECISION,
    volume BIGINT,
    rsi DOUBLE PRECISION,
    macd DOUBLE PRECISION,
    bollinger_position DOUBLE PRECISION,
    sentiment_score DOUBLE PRECISION DEFAULT 0,
    sentiment_confidence DOUBLE PRECISION DEFAULT 0,
    sentiment_impact TEXT DEFAULT 'negligible',
    news_count INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT signal_type_values CHECK (signal_type IN ('BULLISH', 'BEARISH', 'NEUTRAL')),
    CONSTRAINT confidence_range CHECK (confidence >= 0 AND confidence <= 100),
    CONSTRAINT screening_score_range CHECK (screening_score >= 0 AND screening_score <= 100),
    CONSTRAINT positive_prices CHECK (current_price > 0 AND (predicted_price_1h IS NULL OR predicted_price_1h > 0) AND (predicted_price_1d IS NULL OR predicted_price_1d > 0) AND (predicted_price_1w IS NULL OR predicted_price_1w > 0)),
    CONSTRAINT sentiment_score_range CHECK (sentiment_score >= -1 AND sentiment_score <= 1),
    CONSTRAINT sentiment_confidence_range CHECK (sentiment_confidence >= 0 AND sentiment_confidence <= 1),
    CONSTRAINT sentiment_impact_values CHECK (sentiment_impact IN ('immediate', 'short-term', 'long-term', 'negligible')),
    CONSTRAINT news_count_positive CHECK (news_count >= 0)
);

-- Convert signal_predictions to hypertable
SELECT create_hypertable('signal_predictions', 'timestamp', 
    chunk_time_interval => INTERVAL '1 day',
    if_not_exists => TRUE
);

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_stock_prices_ticker_date ON stock_prices (ticker, date DESC);
CREATE INDEX IF NOT EXISTS idx_technicals_ticker_date ON technicals (ticker, date DESC);
CREATE INDEX IF NOT EXISTS idx_news_ticker_date ON news_articles (ticker, published_at DESC);
CREATE INDEX IF NOT EXISTS idx_sentiment_ticker_date ON sentiment_scores (ticker, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_predictions_ticker_dates ON predictions (ticker, prediction_date DESC, target_date);
CREATE INDEX IF NOT EXISTS idx_signal_predictions_ticker_time ON signal_predictions (ticker, timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_signal_predictions_signal_type ON signal_predictions (signal_type, confidence DESC);
CREATE INDEX IF NOT EXISTS idx_signal_predictions_sentiment ON signal_predictions (sentiment_score, sentiment_confidence DESC);

-- Enable compression on hypertables
ALTER TABLE stock_prices SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'ticker',
    timescaledb.compress_orderby = 'date DESC'
);

ALTER TABLE technicals SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'ticker',
    timescaledb.compress_orderby = 'date DESC'
);

ALTER TABLE signal_predictions SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'ticker',
    timescaledb.compress_orderby = 'timestamp DESC'
);

-- Add compression policies for hypertables (helps with storage)
SELECT add_compression_policy('stock_prices', INTERVAL '1 week');
SELECT add_compression_policy('technicals', INTERVAL '1 week');
SELECT add_compression_policy('signal_predictions', INTERVAL '1 day');

-- Add retention policies (optional - adjust intervals as needed)
SELECT add_retention_policy('stock_prices', INTERVAL '5 years');
SELECT add_retention_policy('technicals', INTERVAL '5 years');
SELECT add_retention_policy('signal_predictions', INTERVAL '1 year');