-- StockPulse Data Import Script
-- This script loads pre-exported data into the database

-- Disable triggers during import for performance
SET session_replication_role = replica;

-- Clear existing data (optional, but ensures clean import)
TRUNCATE TABLE prediction_validations CASCADE;
TRUNCATE TABLE sentiment_scores CASCADE;
TRUNCATE TABLE news_articles CASCADE;
TRUNCATE TABLE signal_predictions CASCADE;
TRUNCATE TABLE screening_results CASCADE;
TRUNCATE TABLE stock_prices CASCADE;
TRUNCATE TABLE stocks CASCADE;

-- Import stocks
\COPY stocks(id, ticker, name, sector, industry, market_cap, created_at, updated_at) FROM '/app/backup/stocks.csv' WITH CSV HEADER;

-- Import stock_prices
\COPY stock_prices(ticker, date, open, high, low, close, volume, adjusted_close) FROM '/app/backup/stock_prices.csv' WITH CSV HEADER;

-- Import screening_results
\COPY screening_results(id, ticker, screening_date, overall_score, technical_score, sentiment_score, momentum_score, volume_score, rsi, macd, bollinger_position, volume_ratio, created_at) FROM '/app/backup/screening_results.csv' WITH CSV HEADER;

-- Import signal_predictions
\COPY signal_predictions(id, ticker, signal_type, confidence, current_price, predicted_price_1h, predicted_price_1d, predicted_price_1w, rsi, macd, bollinger_position, volume, news_count, sentiment_score, sentiment_impact, sentiment_confidence, primary_reasons, sector, screening_score, timestamp, created_at) FROM '/app/backup/signal_predictions.csv' WITH CSV HEADER;

-- Import news_articles
\COPY news_articles(id, ticker, title, url, source, published_at, summary, sentiment, created_at) FROM '/app/backup/news_articles.csv' WITH CSV HEADER;

-- Import sentiment_scores
\COPY sentiment_scores(id, ticker, score, magnitude, source, analysis_date, details, created_at) FROM '/app/backup/sentiment_scores.csv' WITH CSV HEADER;

-- Import prediction_validations
\COPY prediction_validations(id, prediction_id, ticker, signal_type, predicted_price, actual_price, prediction_horizon, predicted_at, validated_at, price_difference, percentage_error, is_direction_correct, created_at) FROM '/app/backup/prediction_validations.csv' WITH CSV HEADER;

-- Re-enable triggers
SET session_replication_role = DEFAULT;

-- Update sequences
SELECT setval('stocks_id_seq', (SELECT MAX(id) FROM stocks));
SELECT setval('screening_results_id_seq', (SELECT MAX(id) FROM screening_results));
SELECT setval('signal_predictions_id_seq', (SELECT MAX(id) FROM signal_predictions));
SELECT setval('news_articles_id_seq', (SELECT MAX(id) FROM news_articles));
SELECT setval('sentiment_scores_id_seq', (SELECT MAX(id) FROM sentiment_scores));
SELECT setval('prediction_validations_id_seq', (SELECT MAX(id) FROM prediction_validations));

-- Show counts
SELECT 'stocks' as table_name, COUNT(*) as count FROM stocks
UNION ALL SELECT 'stock_prices', COUNT(*) FROM stock_prices
UNION ALL SELECT 'screening_results', COUNT(*) FROM screening_results
UNION ALL SELECT 'signal_predictions', COUNT(*) FROM signal_predictions
UNION ALL SELECT 'news_articles', COUNT(*) FROM news_articles
UNION ALL SELECT 'sentiment_scores', COUNT(*) FROM sentiment_scores
UNION ALL SELECT 'prediction_validations', COUNT(*) FROM prediction_validations;
