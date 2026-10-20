-- Fix stock_prices_intraday timestamps written before the IntradayPrice
-- @ColumnTransformer change. Ingestion produced US/Eastern wall-clock times
-- that the UTC JVM stored as if they were UTC, so every bar was 4-5h early.
-- Reinterpret each stored UTC wall-clock value as America/New_York.
--
-- Run ONCE, with the backend stopped:
--   docker exec -i stockpulse_db psql -U postgres -d stockpulse -v ON_ERROR_STOP=1 < db/migrations/2026-10-20_fix_intraday_timezone.sql

BEGIN;

CREATE TEMP TABLE intraday_fixed ON COMMIT DROP AS
SELECT ticker,
       (timestamp AT TIME ZONE 'UTC') AT TIME ZONE 'America/New_York' AS timestamp,
       open, high, low, close, volume
FROM stock_prices_intraday;

DELETE FROM stock_prices_intraday;

INSERT INTO stock_prices_intraday (ticker, timestamp, open, high, low, close, volume)
SELECT ticker, timestamp, open, high, low, close, volume FROM intraday_fixed;

-- Date index should bucket by the trading day, not the UTC day
DROP INDEX IF EXISTS idx_intraday_date;
CREATE INDEX IF NOT EXISTS idx_intraday_date
    ON stock_prices_intraday (((timestamp AT TIME ZONE 'America/New_York')::date), ticker);

COMMIT;
