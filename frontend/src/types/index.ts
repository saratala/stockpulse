export interface StockPrediction {
  ticker: string;
  prediction_date: string;
  target_date: string;
  predicted_movement_percent: number;
  predicted_direction: number;
  confidence_score: number;
  model_version: string;
}

export interface SentimentData {
  ticker: string;
  sentiment_score: number;
  polarity: string;
  created_at: string;
}