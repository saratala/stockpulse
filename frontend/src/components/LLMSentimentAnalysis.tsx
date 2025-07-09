import React, { useState, useEffect } from 'react';
import { 
  analyzeSentimentForTicker, 
  fetchSentimentSummary, 
  fetchSentimentTrend, 
  refreshSentimentForTicker,
  analyzeAllTickers,
  updateRecentSentiment 
} from '../services/api';
import LoadingSpinner from './LoadingSpinner';
import GlassContainer from './GlassContainer';
import GlassButton from './GlassButton';

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

export const LLMSentimentAnalysis: React.FC = () => {
  const [selectedTicker, setSelectedTicker] = useState('AAPL');
  const [sentimentData, setSentimentData] = useState<SentimentAnalysisData | null>(null);
  const [sentimentSummary, setSentimentSummary] = useState<SentimentSummary | null>(null);
  const [sentimentTrend, setSentimentTrend] = useState<SentimentTrend | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const tickers = ['AAPL', 'GOOGL', 'MSFT', 'AMZN', 'TSLA', 'META', 'NVDA', 'NFLX'];

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

  const analyzeTicker = async () => {
    try {
      setIsAnalyzing(true);
      setError(null);
      const result = await analyzeSentimentForTicker(selectedTicker);
      setSentimentData(result);
      await loadSentimentSummary();
      await loadSentimentTrend();
    } catch (err) {
      setError('Failed to analyze sentiment');
      console.error('Error analyzing sentiment:', err);
    } finally {
      setIsAnalyzing(false);
    }
  };

  const refreshSentiment = async () => {
    try {
      setIsRefreshing(true);
      setError(null);
      const result = await refreshSentimentForTicker(selectedTicker);
      setSentimentData(result);
      await loadSentimentSummary();
      await loadSentimentTrend();
    } catch (err) {
      setError('Failed to refresh sentiment');
      console.error('Error refreshing sentiment:', err);
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
    return (sentiment * 100).toFixed(1) + '%';
  };

  return (
    <div className="space-y-6">
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

      {error && (
        <div className="bg-red-500/10 border border-red-500/20 rounded-lg p-4 text-red-400">
          {error}
        </div>
      )}

      {/* Ticker Selection and Analysis */}
      <GlassContainer className="p-6">
        <div className="space-y-4">
          <div className="flex flex-wrap gap-2">
            {tickers.map((ticker) => (
              <button
                key={ticker}
                onClick={() => setSelectedTicker(ticker)}
                className={`px-4 py-2 rounded-lg transition-all ${
                  selectedTicker === ticker
                    ? 'glass-ticker-selected'
                    : 'glass-ticker-button sentiment-text-tertiary'
                }`}
              >
                <span className={selectedTicker === ticker ? 'ticker-text' : ''}>
                  {ticker}
                </span>
              </button>
            ))}
          </div>

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
          <h3 className="text-xl font-semibold sentiment-text-primary mb-4">
            {sentimentData.ticker} Sentiment Analysis
          </h3>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="glass-sentiment-card p-4">
              <div className="sentiment-text-tertiary text-sm">Overall Sentiment</div>
              <div className={`text-2xl font-bold ${getSentimentColor(sentimentData.averageSentiment)}`}>
                {getSentimentIcon(sentimentData.averageSentiment)} {formatSentiment(sentimentData.averageSentiment)}
              </div>
              <div className="text-sm sentiment-text-tertiary capitalize">{sentimentData.sentiment}</div>
            </div>
            <div className="glass-sentiment-card p-4">
              <div className="sentiment-text-tertiary text-sm">Confidence</div>
              <div className="text-2xl font-bold sentiment-text-primary">
                {(sentimentData.confidence * 100).toFixed(0)}%
              </div>
            </div>
            <div className="glass-sentiment-card p-4">
              <div className="sentiment-text-tertiary text-sm">News Articles</div>
              <div className="text-2xl font-bold sentiment-text-primary">{sentimentData.newsCount}</div>
            </div>
            <div className="glass-sentiment-card p-4">
              <div className="sentiment-text-tertiary text-sm">Analyzed</div>
              <div className="text-2xl font-bold sentiment-text-primary">{sentimentData.totalAnalyzed}</div>
            </div>
          </div>
          <div className="mt-4 text-sm sentiment-text-tertiary">
            Last updated: {new Date(sentimentData.lastUpdated).toLocaleString()}
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
                {getSentimentIcon(sentimentSummary.overallSentiment)} {formatSentiment(sentimentSummary.overallSentiment)}
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

          {/* Sentiment by Ticker */}
          {sentimentSummary.sentimentByTicker.length > 0 && (
            <div>
              <h4 className="text-lg font-semibold sentiment-text-primary mb-3">Sentiment by Ticker</h4>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
                {sentimentSummary.sentimentByTicker.map(([ticker, sentiment]) => (
                  <div key={ticker} className="glass-sentiment-card p-3">
                    <div className="flex justify-between items-center">
                      <span className="sentiment-text-primary font-medium">{ticker}</span>
                      <div className={`text-sm ${getSentimentColor(sentiment)}`}>
                        {getSentimentIcon(sentiment)} {formatSentiment(sentiment)}
                      </div>
                    </div>
                  </div>
                ))}
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