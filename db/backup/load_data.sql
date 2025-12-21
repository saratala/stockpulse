-- StockPulse Data Import Script
-- This script loads pre-exported data into the database

-- Disable triggers during import for performance
SET session_replication_role = replica;

-- Clear existing data (optional, but ensures clean import)
TRUNCATE TABLE prediction_validations CASCADE;
TRUNCATE TABLE sentiment_scores CASCADE;
TRUNCATE TABLE news_articles CASCADE;
TRUNCATE TABLE signal_predictions CASCADE;
TRUNCATE TABLE stock_prices CASCADE;
TRUNCATE TABLE stocks CASCADE;

-- Import stocks (matches CSV: id,ticker,name,sector,industry,market_cap,created_at,updated_at)
\COPY stocks(id, ticker, name, sector, industry, market_cap, created_at, updated_at) FROM '/app/backup/stocks.csv' WITH CSV HEADER;

-- Import stock_prices (matches CSV: ticker,date,open,high,low,close,volume,adjusted_close)
\COPY stock_prices(ticker, date, open, high, low, close, volume, adjusted_close) FROM '/app/backup/stock_prices.csv' WITH CSV HEADER;

-- Import signal_predictions (matches CSV: id,ticker,timestamp,current_price,signal_type,confidence,primary_reasons,screening_score,sector,predicted_price_1h,predicted_price_1d,predicted_price_1w,volume,rsi,macd,bollinger_position,sentiment_score,sentiment_confidence,sentiment_impact,news_count,created_at)
\COPY signal_predictions(id, ticker, timestamp, current_price, signal_type, confidence, primary_reasons, screening_score, sector, predicted_price_1h, predicted_price_1d, predicted_price_1w, volume, rsi, macd, bollinger_position, sentiment_score, sentiment_confidence, sentiment_impact, news_count, created_at) FROM '/app/backup/signal_predictions.csv' WITH CSV HEADER;

-- Import news_articles (matches CSV: id,ticker,title,url,published_at,source,content,created_at)
\COPY news_articles(id, ticker, title, url, published_at, source, content, created_at) FROM '/app/backup/news_articles.csv' WITH CSV HEADER;

-- Import sentiment_scores (matches CSV: id,ticker,article_id,news_article_id,sentiment_score,polarity,content,reasoning,analysis_provider,source,published_at,confidence,created_at)
\COPY sentiment_scores(id, ticker, article_id, news_article_id, sentiment_score, polarity, content, reasoning, analysis_provider, source, published_at, confidence, created_at) FROM '/app/backup/sentiment_scores.csv' WITH CSV HEADER;

-- Import prediction_validations (matches CSV: id,ticker,prediction_timestamp,validation_timestamp,timeframe,signal_type,predicted_price,prediction_price,confidence,actual_price,predicted_change_percent,actual_change_percent,price_error,price_error_percent,direction_correct,within_5_percent,within_10_percent)
\COPY prediction_validations(id, ticker, prediction_timestamp, validation_timestamp, timeframe, signal_type, predicted_price, prediction_price, confidence, actual_price, predicted_change_percent, actual_change_percent, price_error, price_error_percent, direction_correct, within_5_percent, within_10_percent) FROM '/app/backup/prediction_validations.csv' WITH CSV HEADER;

-- Re-enable triggers
SET session_replication_role = DEFAULT;

-- Update sequences
SELECT setval('stocks_id_seq', (SELECT COALESCE(MAX(id), 1) FROM stocks));
SELECT setval('news_articles_id_seq', (SELECT COALESCE(MAX(id), 1) FROM news_articles));
SELECT setval('sentiment_scores_id_seq', (SELECT COALESCE(MAX(id), 1) FROM sentiment_scores));

-- Show counts
SELECT 'stocks' as table_name, COUNT(*) as count FROM stocks
UNION ALL SELECT 'stock_prices', COUNT(*) FROM stock_prices
UNION ALL SELECT 'signal_predictions', COUNT(*) FROM signal_predictions
UNION ALL SELECT 'news_articles', COUNT(*) FROM news_articles
UNION ALL SELECT 'sentiment_scores', COUNT(*) FROM sentiment_scores
UNION ALL SELECT 'prediction_validations', COUNT(*) FROM prediction_validations;
