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
    market_cap BIGINT,
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
    article_id BIGINT REFERENCES news_articles(id),
    news_article_id BIGINT,
    sentiment_score NUMERIC(5,3) NOT NULL,
    polarity TEXT NOT NULL,
    content TEXT,
    reasoning TEXT,
    analysis_provider TEXT,
    source TEXT NOT NULL,
    published_at TIMESTAMPTZ NOT NULL,
    confidence NUMERIC(5,3) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT sentiment_score_range CHECK (sentiment_score >= -1 AND sentiment_score <= 1),
    CONSTRAINT polarity_values CHECK (polarity IN ('positive', 'neutral', 'negative')),
    CONSTRAINT confidence_range CHECK (confidence >= 0 AND confidence <= 1)
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
    id SERIAL NOT NULL,
    ticker TEXT NOT NULL REFERENCES stocks(ticker),
    timestamp TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (id, timestamp),
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

-- -------------------------
-- Table: prediction_validations (tracks predicted vs actual outcomes)
-- -------------------------
CREATE TABLE IF NOT EXISTS prediction_validations (
    id SERIAL NOT NULL,
    ticker TEXT NOT NULL REFERENCES stocks(ticker),
    prediction_timestamp TIMESTAMPTZ NOT NULL,
    validation_timestamp TIMESTAMPTZ NOT NULL,
    timeframe TEXT NOT NULL, -- '1h', '1d', '1w'

    -- Original prediction data
    signal_type TEXT NOT NULL,
    predicted_price DOUBLE PRECISION NOT NULL,
    prediction_price DOUBLE PRECISION NOT NULL, -- price when prediction was made
    confidence DOUBLE PRECISION NOT NULL,

    -- Actual outcome
    actual_price DOUBLE PRECISION NOT NULL,

    -- Calculated metrics
    predicted_change_percent DOUBLE PRECISION NOT NULL,
    actual_change_percent DOUBLE PRECISION NOT NULL,
    price_error DOUBLE PRECISION NOT NULL, -- absolute error in dollars
    price_error_percent DOUBLE PRECISION NOT NULL, -- percentage error
    direction_correct BOOLEAN NOT NULL, -- did we get the direction right?
    within_5_percent BOOLEAN NOT NULL, -- was prediction within 5% of actual?
    within_10_percent BOOLEAN NOT NULL, -- was prediction within 10% of actual?

    PRIMARY KEY (id, validation_timestamp),
    CONSTRAINT timeframe_values CHECK (timeframe IN ('1h', '1d', '1w')),
    CONSTRAINT signal_type_values CHECK (signal_type IN ('BULLISH', 'BEARISH', 'NEUTRAL')),
    CONSTRAINT confidence_range CHECK (confidence >= 0 AND confidence <= 100),
    CONSTRAINT positive_prices CHECK (predicted_price > 0 AND prediction_price > 0 AND actual_price > 0)
);

-- Convert to hypertable
SELECT create_hypertable('prediction_validations', 'validation_timestamp',
    chunk_time_interval => INTERVAL '1 week',
    if_not_exists => TRUE
);

-- Create indexes for validation queries
CREATE INDEX IF NOT EXISTS idx_pred_val_ticker_time ON prediction_validations (ticker, validation_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_pred_val_timeframe ON prediction_validations (timeframe, validation_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_pred_val_direction ON prediction_validations (direction_correct, timeframe);
CREATE INDEX IF NOT EXISTS idx_pred_val_confidence ON prediction_validations (confidence DESC, direction_correct);

-- -------------------------
-- Table: prediction_accuracy_daily (aggregated daily accuracy metrics)
-- -------------------------
CREATE TABLE IF NOT EXISTS prediction_accuracy_daily (
    id SERIAL NOT NULL,
    date DATE NOT NULL,
    ticker TEXT, -- NULL for overall metrics
    timeframe TEXT NOT NULL,

    -- Count metrics
    total_predictions INTEGER NOT NULL DEFAULT 0,
    correct_direction INTEGER NOT NULL DEFAULT 0,
    within_5_percent INTEGER NOT NULL DEFAULT 0,
    within_10_percent INTEGER NOT NULL DEFAULT 0,

    -- Accuracy percentages
    direction_accuracy DOUBLE PRECISION NOT NULL DEFAULT 0,
    accuracy_5_percent DOUBLE PRECISION NOT NULL DEFAULT 0,
    accuracy_10_percent DOUBLE PRECISION NOT NULL DEFAULT 0,

    -- Error metrics
    avg_error_percent DOUBLE PRECISION NOT NULL DEFAULT 0,
    max_error_percent DOUBLE PRECISION NOT NULL DEFAULT 0,
    min_error_percent DOUBLE PRECISION NOT NULL DEFAULT 0,

    -- Confidence calibration
    avg_confidence DOUBLE PRECISION NOT NULL DEFAULT 0,
    confidence_calibration DOUBLE PRECISION, -- how well confidence matches actual accuracy

    -- By signal type
    bullish_count INTEGER NOT NULL DEFAULT 0,
    bullish_correct INTEGER NOT NULL DEFAULT 0,
    bearish_count INTEGER NOT NULL DEFAULT 0,
    bearish_correct INTEGER NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ DEFAULT NOW(),

    PRIMARY KEY (id),
    UNIQUE (date, ticker, timeframe),
    CONSTRAINT timeframe_values CHECK (timeframe IN ('1h', '1d', '1w', 'all'))
);

CREATE INDEX IF NOT EXISTS idx_accuracy_date ON prediction_accuracy_daily (date DESC);
CREATE INDEX IF NOT EXISTS idx_accuracy_ticker ON prediction_accuracy_daily (ticker, date DESC);

-- -------------------------
-- Table: paper_trades (simulated trading for validation)
-- -------------------------
CREATE TABLE IF NOT EXISTS paper_trades (
    id SERIAL PRIMARY KEY,
    ticker TEXT NOT NULL REFERENCES stocks(ticker),
    signal_prediction_id INTEGER NOT NULL,

    -- Trade details
    trade_type TEXT NOT NULL, -- 'BUY' or 'SELL'
    entry_price DOUBLE PRECISION NOT NULL,
    entry_timestamp TIMESTAMPTZ NOT NULL,
    target_price DOUBLE PRECISION,
    stop_loss_price DOUBLE PRECISION,

    -- Position sizing
    position_size INTEGER NOT NULL DEFAULT 100, -- shares
    position_value DOUBLE PRECISION NOT NULL,

    -- Exit details (NULL until closed)
    exit_price DOUBLE PRECISION,
    exit_timestamp TIMESTAMPTZ,
    exit_reason TEXT, -- 'TARGET_HIT', 'STOP_LOSS', 'TIME_EXPIRED', 'MANUAL'

    -- P&L
    realized_pnl DOUBLE PRECISION,
    realized_pnl_percent DOUBLE PRECISION,

    -- Status
    status TEXT NOT NULL DEFAULT 'OPEN', -- 'OPEN', 'CLOSED', 'CANCELLED'

    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),

    CONSTRAINT trade_type_values CHECK (trade_type IN ('BUY', 'SELL')),
    CONSTRAINT status_values CHECK (status IN ('OPEN', 'CLOSED', 'CANCELLED')),
    CONSTRAINT positive_prices CHECK (entry_price > 0 AND position_size > 0)
);

CREATE INDEX IF NOT EXISTS idx_paper_trades_ticker ON paper_trades (ticker, entry_timestamp DESC);
CREATE INDEX IF NOT EXISTS idx_paper_trades_status ON paper_trades (status, entry_timestamp DESC);

-- -------------------------
-- Table: paper_trading_account (track overall paper trading performance)
-- -------------------------
CREATE TABLE IF NOT EXISTS paper_trading_account (
    id SERIAL PRIMARY KEY,
    account_name TEXT NOT NULL DEFAULT 'default',

    -- Account balance
    initial_balance DOUBLE PRECISION NOT NULL DEFAULT 100000,
    current_balance DOUBLE PRECISION NOT NULL DEFAULT 100000,

    -- Performance metrics
    total_trades INTEGER NOT NULL DEFAULT 0,
    winning_trades INTEGER NOT NULL DEFAULT 0,
    losing_trades INTEGER NOT NULL DEFAULT 0,

    -- P&L
    total_realized_pnl DOUBLE PRECISION NOT NULL DEFAULT 0,
    total_unrealized_pnl DOUBLE PRECISION NOT NULL DEFAULT 0,

    -- Risk metrics
    max_drawdown DOUBLE PRECISION NOT NULL DEFAULT 0,
    max_drawdown_percent DOUBLE PRECISION NOT NULL DEFAULT 0,
    peak_balance DOUBLE PRECISION NOT NULL DEFAULT 100000,

    -- Dates
    started_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),

    UNIQUE (account_name)
);

-- Insert default paper trading account
INSERT INTO paper_trading_account (account_name, initial_balance, current_balance)
VALUES ('default', 100000, 100000)
ON CONFLICT (account_name) DO NOTHING;

-- -------------------------
-- Table: screening_results (stores stock screening/scoring data)
-- -------------------------
CREATE TABLE IF NOT EXISTS screening_results (
    id SERIAL PRIMARY KEY,
    ticker TEXT NOT NULL REFERENCES stocks(ticker),
    name TEXT NOT NULL,
    sector TEXT,
    screening_date TIMESTAMPTZ NOT NULL,
    current_price NUMERIC(10,2) NOT NULL,
    screening_score NUMERIC(5,2) NOT NULL,
    change_percent NUMERIC(5,2),
    volume BIGINT NOT NULL,
    volume_ratio NUMERIC(5,2),
    ema_stack_aligned BOOLEAN,
    adx_strength NUMERIC(5,2),
    stoch_position NUMERIC(5,2),
    rsi NUMERIC(5,2),
    market_cap BIGINT,
    pe_ratio NUMERIC(8,2),
    beta NUMERIC(5,2),
    fifty_two_week_high NUMERIC(10,2),
    fifty_two_week_low NUMERIC(10,2),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_screening_ticker_date ON screening_results (ticker, screening_date);
CREATE INDEX IF NOT EXISTS idx_screening_date ON screening_results (screening_date);
CREATE INDEX IF NOT EXISTS idx_screening_score ON screening_results (screening_score);

-- -------------------------
-- Table: heikin_ashi_signals (Heikin-Ashi candlestick analysis signals)
-- -------------------------
CREATE TABLE IF NOT EXISTS heikin_ashi_signals (
    id SERIAL PRIMARY KEY,
    ticker TEXT NOT NULL REFERENCES stocks(ticker),
    analysis_date TIMESTAMPTZ NOT NULL,
    primary_signal TEXT NOT NULL,
    primary_confidence NUMERIC(5,2) NOT NULL,
    reversal_signal TEXT,
    reversal_confidence NUMERIC(5,2),
    current_price NUMERIC(10,2) NOT NULL,
    trend_strength TEXT,
    risk_level TEXT,
    support_level NUMERIC(10,2),
    resistance_level NUMERIC(10,2),
    pivot_level NUMERIC(10,2),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    CONSTRAINT primary_signal_values CHECK (primary_signal IN ('BULLISH', 'BEARISH', 'NEUTRAL')),
    CONSTRAINT reversal_signal_values CHECK (reversal_signal IS NULL OR reversal_signal IN ('BULLISH_REVERSAL', 'BEARISH_REVERSAL', 'NO_REVERSAL')),
    CONSTRAINT trend_strength_values CHECK (trend_strength IS NULL OR trend_strength IN ('STRONG', 'MODERATE', 'WEAK')),
    CONSTRAINT risk_level_values CHECK (risk_level IS NULL OR risk_level IN ('LOW', 'MEDIUM', 'HIGH'))
);

CREATE INDEX IF NOT EXISTS idx_heikin_ashi_ticker_date ON heikin_ashi_signals (ticker, analysis_date);
CREATE INDEX IF NOT EXISTS idx_heikin_ashi_date ON heikin_ashi_signals (analysis_date);
-- -------------------------
-- Table: trade_executions (auto-trader audit log: every entry, exit, skip, and error)
-- -------------------------
CREATE TABLE IF NOT EXISTS trade_executions (
    id SERIAL PRIMARY KEY,
    ticker TEXT NOT NULL,
    action TEXT NOT NULL,
    side TEXT,
    signal_prediction_id BIGINT,
    signal_confidence NUMERIC(5,2),
    notional NUMERIC(12,2),
    reference_price NUMERIC(12,4),
    take_profit_price NUMERIC(12,4),
    stop_loss_price NUMERIC(12,4),
    broker_order_id TEXT,
    client_order_id TEXT,
    reason TEXT,
    mode TEXT NOT NULL DEFAULT 'paper',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT trade_exec_action_values CHECK (action IN ('ENTRY', 'EXIT', 'SKIPPED', 'ERROR')),
    CONSTRAINT trade_exec_mode_values CHECK (mode IN ('paper', 'live'))
);

CREATE INDEX IF NOT EXISTS idx_trade_exec_ticker_time ON trade_executions (ticker, created_at);
CREATE INDEX IF NOT EXISTS idx_trade_exec_action ON trade_executions (action, created_at);
