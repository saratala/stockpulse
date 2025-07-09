import { StockPrediction, SentimentData } from '../types';

const API_BASE_URL = 'http://localhost:8181/api/v1';

export const fetchLatestPredictions = async (): Promise<StockPrediction[]> => {
  const response = await fetch(`${API_BASE_URL}/predictions`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const fetchSentimentData = async (ticker: string): Promise<SentimentData[]> => {
  const response = await fetch(`${API_BASE_URL}/sentiment/${ticker}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// New LLM Sentiment Analysis API Functions
export const analyzeSentimentForTicker = async (ticker: string) => {
  const response = await fetch(`${API_BASE_URL}/sentiment-analysis/analyze/${ticker.toUpperCase()}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
  });
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const refreshSentimentForTicker = async (ticker: string) => {
  const response = await fetch(`${API_BASE_URL}/sentiment-analysis/refresh/${ticker.toUpperCase()}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
  });
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const fetchSentimentSummary = async (hours = 24) => {
  const response = await fetch(`${API_BASE_URL}/sentiment-analysis/summary?hours=${hours}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const fetchSentimentTrend = async (ticker: string, days = 7) => {
  const response = await fetch(`${API_BASE_URL}/sentiment-analysis/trend/${ticker.toUpperCase()}?days=${days}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const analyzeAllTickers = async () => {
  const response = await fetch(`${API_BASE_URL}/sentiment-analysis/analyze/all`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
  });
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const updateRecentSentiment = async () => {
  const response = await fetch(`${API_BASE_URL}/sentiment-analysis/update-recent`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
  });
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const fetchPredictionsByModel = async (model_version: string): Promise<StockPrediction[]> => {
  const response = await fetch(`${API_BASE_URL}/predictions?model_version=${model_version}`);
  return response.json();
};

// Java Backend Stock and Technical Analysis API Functions
export const fetchAllStocks = async () => {
  const response = await fetch(`${API_BASE_URL}/stocks`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const fetchStockByTicker = async (ticker: string) => {
  const response = await fetch(`${API_BASE_URL}/stocks/${ticker.toUpperCase()}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const fetchStockPrices = async (ticker: string, days = 30) => {
  const response = await fetch(`${API_BASE_URL}/stocks/${ticker.toUpperCase()}/prices?days=${days}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const fetchLatestStockPrice = async (ticker: string) => {
  const response = await fetch(`${API_BASE_URL}/stocks/${ticker.toUpperCase()}/prices/latest`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const fetchAllTickers = async () => {
  const response = await fetch(`${API_BASE_URL}/stocks/tickers`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const fetchHealthStatus = async () => {
  const response = await fetch(`${API_BASE_URL}/actuator/health`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Screener API Functions (Java Backend)
export const fetchScreeningResults = async (params?: {
  minScore?: number;
  maxResults?: number;
  includeSignals?: boolean;
}) => {
  const searchParams = new URLSearchParams();
  if (params?.minScore) searchParams.append('minScore', params.minScore.toString());
  if (params?.maxResults) searchParams.append('maxResults', params.maxResults.toString());
  if (params?.includeSignals !== undefined) searchParams.append('includeSignals', params.includeSignals.toString());
  
  const response = await fetch(`${API_BASE_URL}/screening/run?${searchParams}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const fetchSignalAnalysis = async (tickers: string[], params?: {
  period?: string;
  minConfidence?: number;
}) => {
  const searchParams = new URLSearchParams();
  searchParams.append('tickers', tickers.join(','));
  if (params?.period) searchParams.append('period', params.period);
  if (params?.minConfidence) searchParams.append('min_confidence', params.minConfidence.toString());
  
  const response = await fetch(`${API_BASE_URL}/screener/signals?${searchParams}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const fetchComprehensiveAnalysis = async (ticker: string) => {
  const response = await fetch(`${API_BASE_URL}/screener/comprehensive/${ticker.toUpperCase()}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const fetchMarketRegime = async (ticker: string) => {
  const response = await fetch(`${API_BASE_URL}/screener/market-regime/${ticker.toUpperCase()}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const fetchDailyResults = async () => {
  const response = await fetch(`${API_BASE_URL}/screener/daily-results`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const triggerDailyScreening = async () => {
  const response = await fetch(`${API_BASE_URL}/screener/run-daily`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
  });
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const fetchBatchAnalysis = async (tickers: string[], period = '6mo') => {
  const searchParams = new URLSearchParams();
  searchParams.append('tickers', tickers.join(','));
  searchParams.append('period', period);
  
  const response = await fetch(`${API_BASE_URL}/screener/batch-analysis?${searchParams}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Prediction History API Functions
export const fetchPredictionHistory = async (params?: {
  ticker?: string;
  hours?: number;
  limit?: number;
}) => {
  const searchParams = new URLSearchParams();
  if (params?.ticker) searchParams.append('ticker', params.ticker);
  if (params?.hours) searchParams.append('hours', params.hours.toString());
  if (params?.limit) searchParams.append('limit', params.limit.toString());
  
  const response = await fetch(`${API_BASE_URL}/predictions/history?${searchParams}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const fetchPredictionSummary = async (hours = 24) => {
  const response = await fetch(`${API_BASE_URL}/predictions/summary?hours=${hours}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const fetchTickerPredictions = async (ticker: string, hours = 168) => {
  const response = await fetch(`${API_BASE_URL}/predictions/ticker/${ticker}?hours=${hours}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// API object for easier imports
export const api = {
  get: async (endpoint: string) => {
    const response = await fetch(`${API_BASE_URL}${endpoint}`);
    if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
    return response.json();
  },
  post: async (endpoint: string, data?: any) => {
    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: data ? JSON.stringify(data) : undefined,
    });
    if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
    return response.json();
  },
  put: async (endpoint: string, data?: any) => {
    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
      method: 'PUT',
      headers: {
        'Content-Type': 'application/json',
      },
      body: data ? JSON.stringify(data) : undefined,
    });
    if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
    return response.json();
  },
  delete: async (endpoint: string) => {
    const response = await fetch(`${API_BASE_URL}${endpoint}`, {
      method: 'DELETE',
    });
    if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
    return response.json();
  },
};