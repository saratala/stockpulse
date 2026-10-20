-- IntradayPrice reads/filters timestamps as (timestamp AT TIME ZONE 'America/New_York')
-- via @ColumnTransformer; index that expression so those lookups don't scan the table.
CREATE INDEX IF NOT EXISTS idx_intraday_ticker_et_time
    ON stock_prices_intraday (ticker, (timestamp AT TIME ZONE 'America/New_York'));
