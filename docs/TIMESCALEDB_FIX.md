# 🔧 TimescaleDB Compression Error Fix

## 🚨 The Problem

You're encountering this error:
```
cannot update/delete rows from chunk "_hyper_1_17_chunk" as it is compressed
```

This happens because TimescaleDB automatically compresses old data chunks to save space, and compressed chunks cannot be updated directly.

## 🛠️ Quick Fixes

### Option 1: Use the Fixed ETL Script (Recommended)

I've created a fixed version of the ETL script that handles compression:

```bash
# Stop the current ETL service
docker-compose stop etl

# Copy the fixed script
docker exec -it stockpulse-backend-1 bash -c "cp etl_finance_fixed.py etl_finance.py"

# Restart ETL
docker-compose up etl
```

### Option 2: Disable Compression Temporarily

Connect to your database and disable compression:

```bash
# Connect to database
docker exec -it stockpulse_db psql -U postgres -d stockpulse

# In psql, run:
-- View compression settings
SELECT * FROM timescaledb_information.compression_settings WHERE hypertable_name = 'stock_prices';

-- Disable compression policy
SELECT remove_compression_policy('stock_prices');

-- Decompress all chunks
SELECT decompress_chunk(c.chunk_schema || '.' || c.chunk_name) 
FROM timescaledb_information.chunks c 
WHERE c.hypertable_name = 'stock_prices' AND c.is_compressed = true;

-- Exit psql
\q
```

### Option 3: Skip Historical Updates

Modify the ETL to only insert new data, not update existing:

```bash
# Edit docker-compose.yml
# Change the ETL command to use ON CONFLICT DO NOTHING
```

## 🎯 Permanent Solution

### 1. Update ETL Logic

The fixed ETL script (`etl_finance_fixed.py`) includes:
- Automatic chunk decompression when needed
- Fallback to INSERT only when UPDATE fails
- Better error handling for compressed chunks

### 2. Configure Compression Policy

Set up a smarter compression policy:

```sql
-- Only compress data older than 30 days
SELECT add_compression_policy('stock_prices', INTERVAL '30 days');

-- Or modify existing policy
SELECT alter_compression_policy('stock_prices', INTERVAL '30 days');
```

### 3. Use INSERT ON CONFLICT

Instead of checking and updating:

```python
# Old approach (fails on compressed chunks)
if exists:
    UPDATE stock_prices SET ...

# New approach (works with compressed chunks)
INSERT INTO stock_prices (...) 
VALUES (...) 
ON CONFLICT (ticker, date) DO NOTHING;
```

## 🚀 Quick Commands

### Check Compression Status
```sql
-- See compressed chunks
SELECT chunk_name, table_bytes, compression_status 
FROM timescaledb_information.chunks 
WHERE hypertable_name = 'stock_prices' 
ORDER BY range_start DESC;
```

### Decompress Specific Date Range
```sql
-- Decompress chunks for specific dates
SELECT decompress_chunk(c.chunk_schema || '.' || c.chunk_name)
FROM timescaledb_information.chunks c
WHERE c.hypertable_name = 'stock_prices' 
  AND c.is_compressed = true
  AND c.range_start <= '2022-08-01'::timestamp
  AND c.range_end >= '2022-07-01'::timestamp;
```

### Monitor Compression
```sql
-- View compression statistics
SELECT
  hypertable_name,
  chunk_name,
  compression_status,
  pg_size_pretty(before_compression_total_bytes) as before,
  pg_size_pretty(after_compression_total_bytes) as after
FROM timescaledb_information.chunks
WHERE hypertable_name = 'stock_prices' AND is_compressed = true;
```

## 💡 Best Practices

1. **Don't update old data**: Design your ETL to be append-only for historical data
2. **Set appropriate compression age**: Only compress data you won't update (e.g., > 30 days old)
3. **Use ON CONFLICT DO NOTHING**: For historical data that might already exist
4. **Monitor compression**: Regularly check what's being compressed

## 🔄 To Resume Normal Operation

After fixing:

```bash
# Restart all services
docker-compose restart

# Or do a clean restart
docker-compose down
docker-compose up -d
```

The application will continue working normally, and the ETL will handle compressed chunks properly!