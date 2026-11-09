-- Remove the flat snapshot rows RealTimeDataService.updateStockPrice inserted into
-- stock_prices every 5 minutes (keyed by LocalDateTime.now()) before it was fixed to
-- upsert one bar per trading day. Real daily bars are keyed at 09:30 UTC; the junk
-- rows sit at other times and have open = high = low = close. A few real bars at
-- other times (with a real range) are kept.
--
-- Nothing is synthesized: days left without a bar are reloaded from Yahoo with
--   POST /api/v1/data/daily/backfill/{ticker}
--
-- Run once, after deploying the fix:
--   docker exec -i stockpulse_db psql -U postgres -d stockpulse -v ON_ERROR_STOP=1 < db/migrations/2026-10-20_remove_junk_daily_prices.sql

BEGIN;

-- Some junk lands in compressed chunks; allow this transaction to decompress them
SET LOCAL timescaledb.max_tuples_decompressed_per_dml_transaction = 0;

SELECT count(*) AS junk_rows_to_delete
FROM stock_prices
WHERE (date AT TIME ZONE 'UTC')::time <> '09:30:00'
  AND open = high AND high = low AND low = close;

DELETE FROM stock_prices
WHERE (date AT TIME ZONE 'UTC')::time <> '09:30:00'
  AND open = high AND high = low AND low = close;

SELECT count(*) AS remaining_off_0930_rows
FROM stock_prices
WHERE (date AT TIME ZONE 'UTC')::time <> '09:30:00';

COMMIT;
