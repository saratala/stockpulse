import React, { useState, useEffect, useCallback } from 'react';
import {
  analyzeSentimentForTicker,
  fetchSentimentSummary,
  fetchSentimentTrend,
  refreshSentimentForTicker,
  analyzeAllTickers,
  updateRecentSentiment,
  fetchLatestStockPrice,
  fetchAllTickers
} from '../services/api';
import LoadingSpinner from './LoadingSpinner';
import GlassContainer from './GlassContainer';
import GlassButton from './GlassButton';

interface TickerPrice {
  ticker: string;
  price: number;
  change: number;
  changePercent: number;
  lastUpdate: Date;
}

interface SentimentAnalysisData {
  ticker: string;
  sentiment: string;
  averageSentiment: number;
  confidence: number;
  totalAnalyzed: number;
  newsCount: number;
  sentimentScoresCount: number;
  lastUpdated: string;
}

interface SentimentSummary {
  timePeriodHours: number;
  overallSentiment: number;
  totalSentiments: number;
  totalNews: number;
  recentSentiments: number;
  sentimentByTicker: [string, number][];
  lastUpdated: string;
}

interface SentimentTrend {
  ticker: string;
  days: number;
  dailySentiment: Record<string, number>;
  totalDataPoints: number;
  generatedAt: string;
}

interface ProgressStep {
  id: string;
  label: string;
  status: 'pending' | 'running' | 'completed' | 'error';
}

export const LLMSentimentAnalysis: React.FC = () => {
  const [selectedTicker, setSelectedTicker] = useState('');
  const [sentimentData, setSentimentData] = useState<SentimentAnalysisData | null>(null);
  const [sentimentSummary, setSentimentSummary] = useState<SentimentSummary | null>(null);
  const [sentimentTrend, setSentimentTrend] = useState<SentimentTrend | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [progressSteps, setProgressSteps] = useState<ProgressStep[]>([]);
  const [showProgress, setShowProgress] = useState(false);
  const [tickerPrices, setTickerPrices] = useState<Record<string, TickerPrice>>({});
  const [pricesLoading, setPricesLoading] = useState(true);
  const [tickers, setTickers] = useState<string[]>([]);
  const [tickersLoading, setTickersLoading] = useState(true);

  // Fetch available tickers from API on component mount
  useEffect(() => {
    const loadTickers = async () => {
      try {
        const tickerList = await fetchAllTickers();
        if (tickerList && tickerList.length > 0) {
          setTickers(tickerList);
          setSelectedTicker(tickerList[0]);
        }
      } catch (error) {
        console.error('Error fetching tickers:', error);
      } finally {
        setTickersLoading(false);
      }
    };
    loadTickers();
  }, []);

  // Fetch live prices for all tickers
  const fetchAllPricesForTickers = useCallback(async () => {
    if (tickers.length === 0) return;
    try {
      const pricePromises = tickers.map(async (ticker) => {
        try {
          const data = await fetchLatestStockPrice(ticker);
          return {
            ticker,
            price: data.close || data.adjustedClose || 0,
            change: data.close - data.open,
            changePercent: ((data.close - data.open) / data.open) * 100,
            lastUpdate: new Date()
          };
        } catch {
          return null;
        }
      });

      const results = await Promise.all(pricePromises);
      const newPrices: Record<string, TickerPrice> = {};
      results.forEach((result) => {
        if (result) {
          newPrices[result.ticker] = result;
        }
      });
      setTickerPrices(newPrices);
      setPricesLoading(false);
    } catch (err) {
      console.error('Error fetching prices:', err);
      setPricesLoading(false);
    }
  }, [tickers]);

  // Initial load and auto-refresh prices every 10 seconds
  useEffect(() => {
    if (tickers.length > 0) {
      fetchAllPricesForTickers();
      const interval = setInterval(fetchAllPricesForTickers, 10000); // Refresh every 10 seconds
      return () => clearInterval(interval);
    }
  }, [tickers, fetchAllPricesForTickers]);

  useEffect(() => {
    loadSentimentSummary();
  }, []);

  useEffect(() => {
    if (selectedTicker) {
      loadSentimentTrend();
    }
  }, [selectedTicker]);

  const loadSentimentSummary = async () => {
    try {
      setIsLoading(true);
      const summary = await fetchSentimentSummary(24);
      setSentimentSummary(summary);
    } catch (err) {
      setError('Failed to load sentiment summary');
      console.error('Error loading sentiment summary:', err);
    } finally {
      setIsLoading(false);
    }
  };

  const loadSentimentTrend = async () => {
    try {
      const trend = await fetchSentimentTrend(selectedTicker, 7);
      setSentimentTrend(trend);
    } catch (err) {
      console.error('Error loading sentiment trend:', err);
    }
  };

  const updateProgress = (stepId: string, status: ProgressStep['status']) => {
    setProgressSteps(prev => prev.map(step =>
      step.id === stepId ? { ...step, status } : step
    ));
  };

  const analyzeTicker = async () => {
    try {
      setIsAnalyzing(true);
      setError(null);
      setShowProgress(true);
      setProgressSteps([
        { id: 'connect', label: 'Connecting to analysis service', status: 'running' },
        { id: 'fetch', label: `Fetching news for ${selectedTicker}`, status: 'pending' },
        { id: 'analyze', label: 'Running LLM sentiment analysis', status: 'pending' },
        { id: 'save', label: 'Saving results', status: 'pending' },
        { id: 'summary', label: 'Updating market summary', status: 'pending' },
      ]);

      // Simulate step progress
      await new Promise(resolve => setTimeout(resolve, 300));
      updateProgress('connect', 'completed');
      updateProgress('fetch', 'running');

      const result = await analyzeSentimentForTicker(selectedTicker);
      updateProgress('fetch', 'completed');
      updateProgress('analyze', 'running');

      await new Promise(resolve => setTimeout(resolve, 200));
      updateProgress('analyze', 'completed');
      updateProgress('save', 'running');

      setSentimentData(result);
      updateProgress('save', 'completed');
      updateProgress('summary', 'running');

      await loadSentimentSummary();
      await loadSentimentTrend();
      updateProgress('summary', 'completed');

      // Hide progress after success
      setTimeout(() => setShowProgress(false), 1500);
    } catch (err) {
      setProgressSteps(prev => prev.map(step =>
        step.status === 'running' ? { ...step, status: 'error' } : step
      ));
      setError('Failed to analyze sentiment. The LLM service may be unavailable.');
      console.error('Error analyzing sentiment:', err);
      setTimeout(() => setShowProgress(false), 3000);
    } finally {
      setIsAnalyzing(false);
    }
  };

  const refreshSentiment = async () => {
    try {
      setIsRefreshing(true);
      setError(null);
      setShowProgress(true);
      setProgressSteps([
        { id: 'connect', label: 'Connecting to news service', status: 'running' },
        { id: 'fetch', label: `Fetching latest news for ${selectedTicker}`, status: 'pending' },
        { id: 'process', label: 'Processing news articles', status: 'pending' },
        { id: 'update', label: 'Updating sentiment data', status: 'pending' },
      ]);

      await new Promise(resolve => setTimeout(resolve, 300));
      updateProgress('connect', 'completed');
      updateProgress('fetch', 'running');

      const result = await refreshSentimentForTicker(selectedTicker);
      updateProgress('fetch', 'completed');
      updateProgress('process', 'running');

      await new Promise(resolve => setTimeout(resolve, 200));
      updateProgress('process', 'completed');
      updateProgress('update', 'running');

      setSentimentData(result);
      await loadSentimentSummary();
      await loadSentimentTrend();
      updateProgress('update', 'completed');

      setTimeout(() => setShowProgress(false), 1500);
    } catch (err) {
      setProgressSteps(prev => prev.map(step =>
        step.status === 'running' ? { ...step, status: 'error' } : step
      ));
      setError('Failed to refresh news. News API may be rate-limited or unavailable. Try again later.');
      console.error('Error refreshing sentiment:', err);
      setTimeout(() => setShowProgress(false), 3000);
    } finally {
      setIsRefreshing(false);
    }
  };

  const analyzeAll = async () => {
    try {
      setIsAnalyzing(true);
      setError(null);
      await analyzeAllTickers();
      await loadSentimentSummary();
      await loadSentimentTrend();
    } catch (err) {
      setError('Failed to analyze all tickers');
      console.error('Error analyzing all tickers:', err);
    } finally {
      setIsAnalyzing(false);
    }
  };

  const updateRecent = async () => {
    try {
      setIsRefreshing(true);
      setError(null);
      await updateRecentSentiment();
      await loadSentimentSummary();
      await loadSentimentTrend();
    } catch (err) {
      setError('Failed to update recent sentiment');
      console.error('Error updating recent sentiment:', err);
    } finally {
      setIsRefreshing(false);
    }
  };

  const getSentimentColor = (sentiment: number) => {
    if (sentiment > 0.1) return 'text-green-400';
    if (sentiment < -0.1) return 'text-red-400';
    return 'sentiment-text-tertiary';
  };

  const getSentimentIcon = (sentiment: number) => {
    if (sentiment > 0.1) return '📈';
    if (sentiment < -0.1) return '📉';
    return '➡️';
  };

  const formatSentiment = (sentiment: number) => {
    // Show "Neutral" for values very close to 0, otherwise show the percentage
    if (Math.abs(sentiment) < 0.01) {
      return 'Neutral';
    }
    const sign = sentiment > 0 ? '+' : '';
    return sign + (sentiment * 100).toFixed(1) + '%';
  };

  const getSentimentDescription = (sentiment: number) => {
    if (sentiment > 0.3) return 'Very Bullish';
    if (sentiment > 0.1) return 'Bullish';
    if (sentiment > 0.01) return 'Slightly Bullish';
    if (sentiment < -0.3) return 'Very Bearish';
    if (sentiment < -0.1) return 'Bearish';
    if (sentiment < -0.01) return 'Slightly Bearish';
    return 'Neutral';
  };

  // Progress indicator component
  const ProgressOverlay = () => {
    if (!showProgress) return null;

    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/80 backdrop-blur-sm">
        <GlassContainer className="p-6 max-w-md w-full mx-4">
          <h3 className="text-lg font-semibold text-slate-50 mb-4 flex items-center gap-2">
            <span className="animate-pulse">⚡</span>
            {isAnalyzing ? 'Analyzing Sentiment' : 'Refreshing News'}
          </h3>
          <div className="space-y-3">
            {progressSteps.map((step) => (
              <div key={step.id} className="flex items-center gap-3">
                <div className="w-5 h-5 flex items-center justify-center">
                  {step.status === 'pending' && (
                    <div className="w-2 h-2 rounded-full bg-slate-500" />
                  )}
                  {step.status === 'running' && (
                    <div className="w-4 h-4 border-2 border-indigo-400 border-t-transparent rounded-full animate-spin" />
                  )}
                  {step.status === 'completed' && (
                    <span className="text-emerald-400">✓</span>
                  )}
                  {step.status === 'error' && (
                    <span className="text-red-400">✗</span>
                  )}
                </div>
                <span className={`text-sm ${
                  step.status === 'completed' ? 'text-emerald-400' :
                  step.status === 'error' ? 'text-red-400' :
                  step.status === 'running' ? 'text-indigo-400' :
                  'text-slate-400'
                }`}>
                  {step.label}
                </span>
              </div>
            ))}
          </div>
          {error && (
            <div className="mt-4 p-3 bg-red-500/10 border border-red-500/30 rounded-lg">
              <p className="text-sm text-red-400">{error}</p>
            </div>
          )}
        </GlassContainer>
      </div>
    );
  };

  return (
    <div className="space-y-6">
      {/* Progress Overlay */}
      <ProgressOverlay />

      <div className="flex flex-col sm:flex-row gap-4 items-center justify-between">
        <h2 className="text-2xl font-bold sentiment-text-primary">LLM Sentiment Analysis</h2>
        <div className="flex gap-2">
          <GlassButton
            onClick={updateRecent}
            disabled={isRefreshing}
            className="px-4 py-2"
          >
            {isRefreshing ? <LoadingSpinner size="sm" /> : 'Update Recent'}
          </GlassButton>
          <GlassButton
            onClick={analyzeAll}
            disabled={isAnalyzing}
            className="px-4 py-2"
          >
            {isAnalyzing ? <LoadingSpinner size="sm" /> : 'Analyze All'}
          </GlassButton>
        </div>
      </div>

      {error && !showProgress && (
        <div className="bg-red-500/10 border border-red-500/20 rounded-lg p-4 text-red-400">
          {error}
        </div>
      )}

      {/* Ticker Selection with Live Prices */}
      <GlassContainer className="p-6">
        <div className="space-y-4">
          <div className="flex items-center justify-between mb-2">
            <h3 className="text-lg font-semibold text-slate-50">Select Ticker</h3>
            <div className="flex items-center gap-2 text-xs text-slate-400">
              <span className={`w-2 h-2 rounded-full ${tickersLoading || pricesLoading ? 'bg-amber-400 animate-pulse' : 'bg-emerald-400'}`} />
              {tickersLoading ? 'Loading tickers...' : pricesLoading ? 'Updating...' : 'Live prices (10s refresh)'}
            </div>
          </div>
          {tickersLoading ? (
            <div className="flex items-center justify-center py-8">
              <LoadingSpinner />
              <span className="ml-2 text-slate-400">Loading available tickers...</span>
            </div>
          ) : tickers.length === 0 ? (
            <div className="text-center py-8 text-slate-400">
              No tickers available. Please check your connection.
            </div>
          ) : (
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
            {tickers.map((ticker) => {
              const priceData = tickerPrices[ticker];
              const isPositive = priceData && priceData.change >= 0;
              return (
                <button
                  key={ticker}
                  onClick={() => setSelectedTicker(ticker)}
                  className={`p-3 rounded-xl transition-all ${
                    selectedTicker === ticker
                      ? 'glass-ticker-selected'
                      : 'glass-ticker-button hover:scale-105'
                  }`}
                >
                  <div className="flex flex-col items-start">
                    <span className={`font-bold text-sm ${selectedTicker === ticker ? 'ticker-text' : 'text-slate-200'}`}>
                      {ticker}
                    </span>
                    {priceData ? (
                      <div className="flex flex-col w-full mt-1">
                        <span className="text-lg font-semibold text-slate-50">
                          ${priceData.price.toFixed(2)}
                        </span>
                        <span className={`text-xs font-medium ${isPositive ? 'text-emerald-400' : 'text-red-400'}`}>
                          {isPositive ? '▲' : '▼'} {Math.abs(priceData.changePercent).toFixed(2)}%
                        </span>
                      </div>
                    ) : (
                      <div className="mt-1">
                        <span className="text-sm text-slate-500">Loading...</span>
                      </div>
                    )}
                  </div>
                </button>
              );
            })}
          </div>
          )}
        </div>
      </GlassContainer>

      {/* Action Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        <GlassContainer 
          className={`p-6 glass-interactive ${isAnalyzing ? 'opacity-75 cursor-not-allowed' : 'cursor-pointer'}`} 
          interactive={!isAnalyzing} 
          onClick={isAnalyzing ? undefined : analyzeTicker}
        >
          <div className="flex items-center justify-center space-x-3">
            <div className="text-3xl">🎯</div>
            <div className="flex-1">
              <h3 className="text-lg font-semibold sentiment-text-primary">
                {isAnalyzing ? 'Analyzing...' : `Analyze ${selectedTicker}`}
              </h3>
              <p className="text-sm sentiment-text-tertiary">
                Get AI-powered sentiment analysis
              </p>
            </div>
            {isAnalyzing && <LoadingSpinner size="sm" />}
          </div>
        </GlassContainer>

        <GlassContainer 
          className={`p-6 glass-interactive ${isRefreshing ? 'opacity-75 cursor-not-allowed' : 'cursor-pointer'}`} 
          interactive={!isRefreshing} 
          onClick={isRefreshing ? undefined : refreshSentiment}
        >
          <div className="flex items-center justify-center space-x-3">
            <div className="text-3xl">🔄</div>
            <div className="flex-1">
              <h3 className="text-lg font-semibold sentiment-text-primary">
                {isRefreshing ? 'Refreshing...' : 'Refresh News'}
              </h3>
              <p className="text-sm sentiment-text-tertiary">
                Update latest news for {selectedTicker}
              </p>
            </div>
            {isRefreshing && <LoadingSpinner size="sm" />}
          </div>
        </GlassContainer>
      </div>

      {/* Individual Ticker Analysis Result */}
      {sentimentData && (
        <GlassContainer className="p-6">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-xl font-semibold sentiment-text-primary">
              {sentimentData.ticker} Sentiment Analysis
            </h3>
            {sentimentData.confidence <= 0.5 && (
              <span className="px-3 py-1 text-xs bg-amber-500/20 text-amber-400 rounded-full">
                Keyword Analysis
              </span>
            )}
          </div>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="glass-sentiment-card p-4">
              <div className="sentiment-text-tertiary text-sm">Overall Sentiment</div>
              <div className={`text-2xl font-bold ${getSentimentColor(sentimentData.averageSentiment)}`}>
                {getSentimentIcon(sentimentData.averageSentiment)} {getSentimentDescription(sentimentData.averageSentiment)}
              </div>
              <div className="text-sm sentiment-text-tertiary">
                Score: {(sentimentData.averageSentiment * 100).toFixed(1)}%
              </div>
            </div>
            <div className="glass-sentiment-card p-4">
              <div className="sentiment-text-tertiary text-sm">Analysis Quality</div>
              <div className={`text-2xl font-bold ${
                sentimentData.confidence > 0.7 ? 'text-emerald-400' :
                sentimentData.confidence > 0.5 ? 'text-amber-400' : 'text-slate-400'
              }`}>
                {sentimentData.confidence > 0.7 ? 'High' :
                 sentimentData.confidence > 0.5 ? 'Medium' : 'Basic'}
              </div>
              <div className="text-xs sentiment-text-tertiary">
                {sentimentData.confidence > 0.5 ? 'LLM Analysis' : 'Keyword-based'}
              </div>
            </div>
            <div className="glass-sentiment-card p-4">
              <div className="sentiment-text-tertiary text-sm">News Articles</div>
              <div className="text-2xl font-bold sentiment-text-primary">{sentimentData.newsCount}</div>
              <div className="text-xs sentiment-text-tertiary">sources analyzed</div>
            </div>
            <div className="glass-sentiment-card p-4">
              <div className="sentiment-text-tertiary text-sm">Data Points</div>
              <div className="text-2xl font-bold sentiment-text-primary">{sentimentData.totalAnalyzed}</div>
              <div className="text-xs sentiment-text-tertiary">sentiment scores</div>
            </div>
          </div>
          <div className="mt-4 flex items-center justify-between">
            <div className="text-sm sentiment-text-tertiary">
              Last updated: {new Date(sentimentData.lastUpdated).toLocaleString()}
            </div>
            {sentimentData.confidence <= 0.5 && (
              <div className="text-xs text-amber-400/70">
                ℹ️ Configure LLM API key for advanced analysis
              </div>
            )}
          </div>
        </GlassContainer>
      )}

      {/* Market Sentiment Summary */}
      {sentimentSummary && (
        <GlassContainer className="p-6">
          <h3 className="text-xl font-semibold sentiment-text-primary mb-4">Market Sentiment Summary</h3>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
            <div className="glass-sentiment-card p-4">
              <div className="sentiment-text-tertiary text-sm">Overall Sentiment</div>
              <div className={`text-2xl font-bold ${getSentimentColor(sentimentSummary.overallSentiment)}`}>
                {getSentimentIcon(sentimentSummary.overallSentiment)} {getSentimentDescription(sentimentSummary.overallSentiment)}
              </div>
              <div className="text-xs sentiment-text-tertiary">
                Score: {(sentimentSummary.overallSentiment * 100).toFixed(1)}%
              </div>
            </div>
            <div className="glass-sentiment-card p-4">
              <div className="sentiment-text-tertiary text-sm">Total Sentiments</div>
              <div className="text-2xl font-bold sentiment-text-primary">{sentimentSummary.totalSentiments}</div>
            </div>
            <div className="glass-sentiment-card p-4">
              <div className="sentiment-text-tertiary text-sm">Total News</div>
              <div className="text-2xl font-bold sentiment-text-primary">{sentimentSummary.totalNews}</div>
            </div>
            <div className="glass-sentiment-card p-4">
              <div className="sentiment-text-tertiary text-sm">Recent Analysis</div>
              <div className="text-2xl font-bold sentiment-text-primary">{sentimentSummary.recentSentiments}</div>
            </div>
          </div>

          {/* Sentiment by Ticker with Live Prices */}
          {sentimentSummary.sentimentByTicker.length > 0 && (
            <div>
              <h4 className="text-lg font-semibold sentiment-text-primary mb-3">Sentiment by Ticker</h4>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3">
                {sentimentSummary.sentimentByTicker.map(([ticker, sentiment]) => {
                  const priceData = tickerPrices[ticker];
                  const isPositive = priceData && priceData.change >= 0;
                  return (
                    <div key={ticker} className="glass-sentiment-card p-3">
                      <div className="flex justify-between items-start">
                        <div>
                          <span className="sentiment-text-primary font-bold">{ticker}</span>
                          {priceData && (
                            <div className="flex items-center gap-2 mt-1">
                              <span className="text-sm text-slate-200">${priceData.price.toFixed(2)}</span>
                              <span className={`text-xs ${isPositive ? 'text-emerald-400' : 'text-red-400'}`}>
                                {isPositive ? '▲' : '▼'}{Math.abs(priceData.changePercent).toFixed(1)}%
                              </span>
                            </div>
                          )}
                        </div>
                        <div className={`text-right ${getSentimentColor(sentiment)}`}>
                          <div className="text-lg">{getSentimentIcon(sentiment)}</div>
                          <div className="text-xs">{getSentimentDescription(sentiment)}</div>
                        </div>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          <div className="mt-4 text-sm sentiment-text-tertiary">
            Last updated: {new Date(sentimentSummary.lastUpdated).toLocaleString()}
          </div>
        </GlassContainer>
      )}

      {/* Sentiment Trend */}
      {sentimentTrend && (
        <GlassContainer className="p-6">
          <h3 className="text-xl font-semibold sentiment-text-primary mb-4">
            {sentimentTrend.ticker} Sentiment Trend (Last 7 Days)
          </h3>
          <div className="space-y-2">
            {Object.entries(sentimentTrend.dailySentiment).map(([date, sentiment]) => (
              <div key={date} className="flex justify-between items-center p-2 glass-sentiment-card">
                <span className="sentiment-text-tertiary">{new Date(date).toLocaleDateString()}</span>
                <div className={`${getSentimentColor(sentiment)}`}>
                  {getSentimentIcon(sentiment)} {formatSentiment(sentiment)}
                </div>
              </div>
            ))}
          </div>
          <div className="mt-4 text-sm sentiment-text-tertiary">
            Total data points: {sentimentTrend.totalDataPoints}
          </div>
        </GlassContainer>
      )}

      {isLoading && (
        <div className="flex justify-center">
          <LoadingSpinner />
        </div>
      )}
    </div>
  );
};