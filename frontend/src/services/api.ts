import { StockPrediction, SentimentData } from '../types';

// Use relative URL - works with proxy in development and correctly in production
const API_BASE_URL = '/api/v1';

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

// News Articles API Functions
export interface NewsArticle {
  id: number;
  ticker: string;
  title: string;
  url: string;
  publishedAt: string;
  source: string;
  content: string | null;
  createdAt: string;
}

export const fetchNewsForTicker = async (ticker: string, hours = 72, limit = 20): Promise<NewsArticle[]> => {
  const response = await fetch(`${API_BASE_URL}/sentiment/news/${ticker.toUpperCase()}?hours=${hours}&limit=${limit}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const refreshNewsForTicker = async (ticker: string) => {
  const response = await fetch(`${API_BASE_URL}/sentiment/news/${ticker.toUpperCase()}/refresh`, {
    method: 'POST',
  });
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
  const response = await fetch(`${API_BASE_URL}/health`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Screener API Functions (Java Backend)
export const fetchScreeningResults = async (params?: {
  minScore?: number;
  maxResults?: number;
  includeSignals?: boolean;
  signalFilter?: 'ALL' | 'BULLISH' | 'BEARISH' | 'NEUTRAL' | 'SIDEWAYS';
}) => {
  const searchParams = new URLSearchParams();
  if (params?.minScore !== undefined) searchParams.append('minScore', params.minScore.toString());
  if (params?.maxResults) searchParams.append('maxResults', params.maxResults.toString());
  if (params?.includeSignals !== undefined) searchParams.append('includeSignals', params.includeSignals.toString());
  if (params?.signalFilter) searchParams.append('signalFilter', params.signalFilter);

  const response = await fetch(`${API_BASE_URL}/screener/run?${searchParams}`);
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

// Prediction Validation API Functions
export const fetchValidationAccuracy = async (days = 7) => {
  const response = await fetch(`${API_BASE_URL}/validation/accuracy?days=${days}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const fetchTickerValidationAccuracy = async (ticker: string, days = 30) => {
  const response = await fetch(`${API_BASE_URL}/validation/accuracy/${ticker.toUpperCase()}?days=${days}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const fetchValidationRankings = async (timeframe = '1d', days = 30) => {
  const response = await fetch(`${API_BASE_URL}/validation/rankings?timeframe=${timeframe}&days=${days}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const fetchRecentValidations = async (limit = 50) => {
  const response = await fetch(`${API_BASE_URL}/validation/recent?limit=${limit}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const fetchValidationDashboard = async (days = 7) => {
  const response = await fetch(`${API_BASE_URL}/validation/dashboard?days=${days}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const fetchPaperTradingStats = async () => {
  const response = await fetch(`${API_BASE_URL}/validation/paper-trading`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

export const triggerValidationRun = async () => {
  const response = await fetch(`${API_BASE_URL}/validation/run`, {
    method: 'POST',
  });
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// ============ AI Analysis API Functions ============

// Comprehensive AI analysis for a single stock
export const fetchAIAnalysis = async (ticker: string) => {
  const response = await fetch(`${API_BASE_URL}/ai/analyze/${ticker.toUpperCase()}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// ML price prediction only (lighter weight)
export const fetchMLPrediction = async (ticker: string) => {
  const response = await fetch(`${API_BASE_URL}/ai/predict/${ticker.toUpperCase()}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// ML backtest for a ticker
export const fetchMLBacktest = async (ticker: string, daysBack = 120) => {
  const response = await fetch(`${API_BASE_URL}/ai/backtest/${ticker.toUpperCase()}?daysBack=${daysBack}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Quick AI recommendation
export const fetchAIRecommendation = async (ticker: string) => {
  const response = await fetch(`${API_BASE_URL}/ai/recommendation/${ticker.toUpperCase()}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Batch AI analysis for multiple stocks
export const fetchBatchAIAnalysis = async (tickers: string[]) => {
  const response = await fetch(`${API_BASE_URL}/ai/analyze-batch`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(tickers),
  });
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// AI service health check
export const fetchAIHealth = async () => {
  const response = await fetch(`${API_BASE_URL}/ai/health`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// ============ Intraday Data API Functions ============

export interface IntradayPrice {
  ticker: string;
  timestamp: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
}

export interface IntradayResponse {
  ticker: string;
  date: string;
  count: number;
  prices: IntradayPrice[];
}

export interface IntradayDatesResponse {
  ticker: string;
  dates: string[];
  totalDates: number;
  startTime: string | null;
  endTime: string | null;
  totalRecords: number;
}

export interface IntradayStats {
  ticker: string;
  startTime: string | null;
  endTime: string | null;
  totalRecords: number;
}

// Get intraday prices for a specific date
export const fetchIntradayPrices = async (ticker: string, date?: string): Promise<IntradayResponse> => {
  const params = date ? `?date=${date}` : '';
  const response = await fetch(`${API_BASE_URL}/intraday/${ticker.toUpperCase()}${params}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Get intraday prices for a date range
export const fetchIntradayPricesRange = async (
  ticker: string,
  start: string,
  end: string,
  limit = 10000
): Promise<IntradayResponse> => {
  const params = new URLSearchParams({
    start,
    end,
    limit: limit.toString(),
  });
  const response = await fetch(`${API_BASE_URL}/intraday/${ticker.toUpperCase()}/range?${params}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Get latest intraday prices
export const fetchLatestIntradayPrices = async (ticker: string, limit = 100): Promise<IntradayPrice[]> => {
  const response = await fetch(`${API_BASE_URL}/intraday/${ticker.toUpperCase()}/latest?limit=${limit}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Get available dates with intraday data
export const fetchIntradayDates = async (ticker: string): Promise<IntradayDatesResponse> => {
  const response = await fetch(`${API_BASE_URL}/intraday/${ticker.toUpperCase()}/dates`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Get hourly aggregated data
export const fetchIntradayHourly = async (ticker: string, date: string) => {
  const response = await fetch(`${API_BASE_URL}/intraday/${ticker.toUpperCase()}/hourly?date=${date}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Get list of tickers with intraday data
export const fetchTickersWithIntradayData = async (): Promise<string[]> => {
  const response = await fetch(`${API_BASE_URL}/intraday/tickers`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Get intraday data statistics for a ticker
export const fetchIntradayStats = async (ticker: string): Promise<IntradayStats> => {
  const response = await fetch(`${API_BASE_URL}/intraday/${ticker.toUpperCase()}/stats`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Import intraday CSV data (admin function)
export const importIntradayCsv = async (ticker: string, file: File, skipDuplicates = true) => {
  const formData = new FormData();
  formData.append('file', file);

  const params = new URLSearchParams({ skipDuplicates: skipDuplicates.toString() });
  const response = await fetch(`${API_BASE_URL}/intraday/import/${ticker.toUpperCase()}?${params}`, {
    method: 'POST',
    body: formData,
  });
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Clear intraday data for a ticker (admin function)
export const clearIntradayData = async (ticker: string) => {
  const response = await fetch(`${API_BASE_URL}/intraday/${ticker.toUpperCase()}`, {
    method: 'DELETE',
  });
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// ============ Aggregated Intraday Data API Functions ============

export interface AggregatedBar {
  ticker: string;
  timestamp: string;
  interval: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  vwap: number;
  dataPoints: number;
}

export interface AggregatedDataResponse {
  ticker: string;
  date: string;
  interval: string;
  count: number;
  bars: AggregatedBar[];
}

export interface DataAvailability {
  ticker: string;
  hasData: boolean;
  earliestDate: string | null;
  latestDate: string | null;
  totalMinuteRecords: number;
  estimatedTradingDays: number;
  estimated15MinBars: number;
}

export interface HistoricalFetchStatus {
  enabled: boolean;
  running: boolean;
  currentTickerIndex: number;
  currentTicker: string;
  daysBack: number;
  tickerRecordCounts: Record<string, number>;
  totalRecords: number;
}

// Get 15-minute aggregated bars for a date
export const fetch15MinBars = async (ticker: string, date?: string): Promise<AggregatedDataResponse> => {
  const params = date ? `?date=${date}` : '';
  const response = await fetch(`${API_BASE_URL}/intraday/aggregated/${ticker.toUpperCase()}/15min${params}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Get 5-minute aggregated bars for a date
export const fetch5MinBars = async (ticker: string, date?: string): Promise<AggregatedDataResponse> => {
  const params = date ? `?date=${date}` : '';
  const response = await fetch(`${API_BASE_URL}/intraday/aggregated/${ticker.toUpperCase()}/5min${params}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Get hourly aggregated bars for a date
export const fetchHourlyBars = async (ticker: string, date?: string): Promise<AggregatedDataResponse> => {
  const params = date ? `?date=${date}` : '';
  const response = await fetch(`${API_BASE_URL}/intraday/aggregated/${ticker.toUpperCase()}/hourly${params}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Get custom interval bars for a date
export const fetchCustomIntervalBars = async (
  ticker: string,
  intervalMinutes: number,
  date?: string
): Promise<AggregatedDataResponse> => {
  const params = new URLSearchParams({ intervalMinutes: intervalMinutes.toString() });
  if (date) params.append('date', date);
  const response = await fetch(`${API_BASE_URL}/intraday/aggregated/${ticker.toUpperCase()}/custom?${params}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Get aggregated bars for a date range
export const fetchAggregatedBarsRange = async (
  ticker: string,
  startDate: string,
  endDate: string,
  intervalMinutes = 15
): Promise<AggregatedDataResponse> => {
  const params = new URLSearchParams({
    startDate,
    endDate,
    intervalMinutes: intervalMinutes.toString(),
  });
  const response = await fetch(`${API_BASE_URL}/intraday/aggregated/${ticker.toUpperCase()}/range?${params}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Get latest N aggregated bars
export const fetchLatestAggregatedBars = async (
  ticker: string,
  intervalMinutes = 15,
  count = 26
): Promise<AggregatedDataResponse> => {
  const params = new URLSearchParams({
    intervalMinutes: intervalMinutes.toString(),
    count: count.toString(),
  });
  const response = await fetch(`${API_BASE_URL}/intraday/aggregated/${ticker.toUpperCase()}/latest?${params}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Get data availability for a ticker
export const fetchDataAvailability = async (ticker: string): Promise<DataAvailability> => {
  const response = await fetch(`${API_BASE_URL}/intraday/aggregated/${ticker.toUpperCase()}/availability`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Get historical data fetch status
export const fetchHistoricalFetchStatus = async (): Promise<HistoricalFetchStatus> => {
  const response = await fetch(`${API_BASE_URL}/intraday/aggregated/fetch/status`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Trigger historical data fetch
export const triggerHistoricalFetch = async () => {
  const response = await fetch(`${API_BASE_URL}/intraday/aggregated/fetch/trigger`, {
    method: 'POST',
  });
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// ============ Massive Data API Functions ============

// Fetch intraday data for a specific date
export const fetchMassiveDataForDate = async (ticker: string, date: string) => {
  const response = await fetch(`${API_BASE_URL}/massivedata/fetch/${ticker.toUpperCase()}/date/${date}`, {
    method: 'POST',
  });
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Get Massive Data API status
export const fetchMassiveDataStatus = async () => {
  const response = await fetch(`${API_BASE_URL}/massivedata/status`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// ============ Market News API Functions ============

export interface MarketNews {
  id: number;
  title: string;
  url: string;
  publishedAt: string;
  source: string;
  content: string | null;
  category: string;
  impactLevel: string;
  affectedSectors: string | null;
  sentiment: number | null;
  createdAt: string;
}

export interface MarketNewsSummary {
  hours: number;
  totalCount: number;
  highImpactCount: number;
  highImpactNews: MarketNews[];
  categoryCounts: Record<string, number>;
  timestamp: string;
}

// Get recent market news
export const fetchMarketNews = async (hours = 48, limit = 50): Promise<{ count: number; news: MarketNews[] }> => {
  const response = await fetch(`${API_BASE_URL}/market-news?hours=${hours}&limit=${limit}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Get high-impact market news
export const fetchHighImpactNews = async (hours = 72): Promise<{ count: number; news: MarketNews[] }> => {
  const response = await fetch(`${API_BASE_URL}/market-news/high-impact?hours=${hours}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Get market news by category
export const fetchMarketNewsByCategory = async (category: string, limit = 20): Promise<{ category: string; count: number; news: MarketNews[] }> => {
  const response = await fetch(`${API_BASE_URL}/market-news/category/${category}?limit=${limit}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Get market news summary
export const fetchMarketNewsSummary = async (hours = 24): Promise<MarketNewsSummary> => {
  const response = await fetch(`${API_BASE_URL}/market-news/summary?hours=${hours}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Get available market news categories
export const fetchMarketNewsCategories = async () => {
  const response = await fetch(`${API_BASE_URL}/market-news/categories`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Trigger market news fetch
export const triggerMarketNewsFetch = async () => {
  const response = await fetch(`${API_BASE_URL}/market-news/fetch`, {
    method: 'POST',
  });
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// ============ Backtesting API Functions ============

export interface BacktestPerformance {
  initialCapital: number;
  finalCapital: number;
  totalReturn: string;
  buyHoldReturn: string;
  excessReturn: string;
  maxDrawdown: string;
  sharpeRatio: number;
  totalTrades: number;
  tradesPerDay: number;
  winningTrades: number;
  losingTrades: number;
  winRate: string;
  avgWin: string;
  avgLoss: string;
  profitFactor: number;
  totalProfit: string;
  totalLoss: string;
  maxConsecutiveWins: number;
  maxConsecutiveLosses: number;
}

export interface BacktestTrade {
  date: string;
  entryTime?: string;
  exitTime?: string;
  direction: string;
  entryPrice: number;
  exitPrice: number;
  shares: number;
  pnl: number;
  returnPct: string;
  exitReason: string;
  confidence?: number;
}

export interface DayTradingBacktestResult {
  ticker: string;
  strategy: string;
  testPeriod: {
    start: string;
    end: string;
    tradingDays: number;
  };
  performance: BacktestPerformance;
  configuration: Record<string, any>;
  exitReasonBreakdown: Record<string, number>;
  dailyPerformance: Array<{
    date: string;
    trades: number;
    pnl: number;
    capital: number;
    drawdown: string;
    limitHit: boolean;
  }>;
  recentTrades: BacktestTrade[];
  recommendation: string;
  error?: string;
}

export interface StrategyComparisonResult {
  _summary: Array<{
    strategy: string;
    totalReturn: string;
    winRate: string;
    totalTrades: number;
    profitFactor: number;
    maxDrawdown: string;
    recommendation?: string;
  }>;
  _bestStrategy: string;
  [key: string]: any;
}

// Day Trading Backtest - Single strategy
export const runDayTradingBacktest = async (
  ticker: string,
  strategy = 'combined',
  initialCapital = 10000,
  params?: {
    takeProfit?: number;
    stopLoss?: number;
    momentumThreshold?: number;
    rsiOversold?: number;
    rsiOverbought?: number;
  }
): Promise<DayTradingBacktestResult> => {
  const searchParams = new URLSearchParams({
    ticker: ticker.toUpperCase(),
    strategy,
    initialCapital: initialCapital.toString(),
  });
  if (params?.takeProfit) searchParams.append('takeProfit', params.takeProfit.toString());
  if (params?.stopLoss) searchParams.append('stopLoss', params.stopLoss.toString());
  if (params?.momentumThreshold) searchParams.append('momentumThreshold', params.momentumThreshold.toString());
  if (params?.rsiOversold) searchParams.append('rsiOversold', params.rsiOversold.toString());
  if (params?.rsiOverbought) searchParams.append('rsiOverbought', params.rsiOverbought.toString());

  const response = await fetch(`${API_BASE_URL}/backtest/day-trading?${searchParams}`, {
    method: 'POST',
  });
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Day Trading Backtest - Compare all strategies
export const compareDayTradingStrategies = async (
  ticker: string,
  initialCapital = 10000
): Promise<StrategyComparisonResult> => {
  const response = await fetch(
    `${API_BASE_URL}/backtest/day-trading/compare?ticker=${ticker.toUpperCase()}&initialCapital=${initialCapital}`,
    { method: 'POST' }
  );
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Get available day trading strategies
export const getDayTradingStrategies = async (): Promise<Record<string, {
  name: string;
  description: string;
  riskLevel: string;
  tradesPerDay: string;
  bestFor: string;
  parameters: string[];
}>> => {
  const response = await fetch(`${API_BASE_URL}/backtest/day-trading/strategies`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Advanced Day Trading Backtest with sophisticated rules
export const runAdvancedDayTradingBacktest = async (
  ticker: string,
  initialCapital = 10000,
  config?: {
    minConfirmations?: number;
    riskPerTrade?: number;
    maxPositionSize?: number;
    stopLossAtr?: number;
    takeProfitAtr?: number;
    trailingStopAtr?: number;
    maxDailyLoss?: number;
    enableScalping?: boolean;
  }
): Promise<DayTradingBacktestResult> => {
  const searchParams = new URLSearchParams({
    ticker: ticker.toUpperCase(),
    initialCapital: initialCapital.toString(),
  });
  if (config?.minConfirmations) searchParams.append('minConfirmations', config.minConfirmations.toString());
  if (config?.riskPerTrade) searchParams.append('riskPerTrade', config.riskPerTrade.toString());
  if (config?.maxPositionSize) searchParams.append('maxPositionSize', config.maxPositionSize.toString());
  if (config?.stopLossAtr) searchParams.append('stopLossAtr', config.stopLossAtr.toString());
  if (config?.takeProfitAtr) searchParams.append('takeProfitAtr', config.takeProfitAtr.toString());
  if (config?.trailingStopAtr) searchParams.append('trailingStopAtr', config.trailingStopAtr.toString());
  if (config?.maxDailyLoss) searchParams.append('maxDailyLoss', config.maxDailyLoss.toString());
  if (config?.enableScalping !== undefined) searchParams.append('enableScalping', config.enableScalping.toString());

  const response = await fetch(`${API_BASE_URL}/backtest/day-trading/advanced?${searchParams}`, {
    method: 'POST',
  });
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// Compare advanced day trading configurations
export const compareAdvancedDayTradingStrategies = async (
  ticker: string,
  initialCapital = 10000
): Promise<StrategyComparisonResult> => {
  const response = await fetch(
    `${API_BASE_URL}/backtest/day-trading/advanced/compare?ticker=${ticker.toUpperCase()}&initialCapital=${initialCapital}`,
    { method: 'POST' }
  );
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// ML Prediction Backtest
export const runMLPredictionBacktest = async (
  ticker: string,
  initialCapital = 10000,
  timeframe = '1d'
) => {
  const response = await fetch(
    `${API_BASE_URL}/backtest/ml-prediction?ticker=${ticker.toUpperCase()}&initialCapital=${initialCapital}&timeframe=${timeframe}`,
    { method: 'POST' }
  );
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

// ML Prediction Backtest for all tickers
export const runMLPredictionBacktestAll = async (
  initialCapital = 10000,
  timeframe = '1d'
) => {
  const response = await fetch(
    `${API_BASE_URL}/backtest/ml-prediction/all?initialCapital=${initialCapital}&timeframe=${timeframe}`,
    { method: 'POST' }
  );
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