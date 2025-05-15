import { StockPrediction, SentimentData } from '../types';

const API_BASE_URL = 'http://localhost:8000';

export const fetchLatestPredictions = async (): Promise<StockPrediction[]> => {
  const response = await fetch(`${API_BASE_URL}/predictions/latest`);
  return response.json();
};

export const fetchSentimentData = async (ticker: string): Promise<SentimentData[]> => {
  const response = await fetch(`${API_BASE_URL}/sentiment/${ticker}`);
  return response.json();
};