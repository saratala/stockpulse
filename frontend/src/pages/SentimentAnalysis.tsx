import React, { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { Brain, TrendingUp, TrendingDown, AlertCircle, Clock, Eye, EyeOff, RefreshCw, BarChart3, GitCompare } from 'lucide-react';
import { api } from '../services/api';
import GlassContainer from '../components/GlassContainer';
import GlassButton from '../components/GlassButton';
import LoadingSpinner from '../components/LoadingSpinner';
import SentimentChart from '../components/SentimentChart';
import PredictionComparison from '../components/PredictionComparison';
import SentimentDashboard from '../components/SentimentDashboard';

interface SentimentPrediction {
  ticker: string;
  timestamp: string;
  signal_type: string;
  confidence: number;
  screening_score: number;
  sentiment_score: number;
  sentiment_confidence: number;
  sentiment_impact: string;
  news_count: number;
  current_price: number;
  predicted_price_1h: number | null;
  predicted_price_1d: number | null;
  predicted_price_1w: number | null;
  primary_reasons: string[];
  sector: string;
  rsi: number;
  macd: number;
}

interface ComparisonData {
  withSentiment: SentimentPrediction[];
  withoutSentiment: SentimentPrediction[];
}

const SentimentAnalysis: React.FC = () => {
  const [predictions, setPredictions] = useState<SentimentPrediction[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [viewMode, setViewMode] = useState<'comparison' | 'sentiment-only' | 'detailed' | 'dashboard'>('comparison');
  const [selectedTimeframe, setSelectedTimeframe] = useState<'1h' | '1d' | '1w'>('1d');
  const [refreshing, setRefreshing] = useState(false);

  const fetchPredictions = async () => {
    try {
      setRefreshing(true);
      // Fetch predictions with sentiment data
      const response = await api.get('/predictions/history?hours=24&limit=50');
      setPredictions(response.data.predictions || []);
      setError(null);
    } catch (err) {
      console.error('Error fetching predictions:', err);
      setError('Failed to fetch predictions. Please try again.');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchPredictions();
  }, []);

  const getSentimentColor = (score: number) => {
    if (score > 0.2) return 'text-green-800';
    if (score < -0.2) return 'text-red-600';
    return 'text-orange-500';
  };

  const getSentimentIcon = (score: number) => {
    if (score > 0.2) return <TrendingUp className="w-4 h-4" />;
    if (score < -0.2) return <TrendingDown className="w-4 h-4" />;
    return <AlertCircle className="w-4 h-4" />;
  };

  const getImpactColor = (impact: string) => {
    switch (impact) {
      case 'immediate': return 'bg-red-500/20 text-red-300';
      case 'short-term': return 'bg-orange-500/20 text-orange-300';
      case 'long-term': return 'bg-blue-500/20 text-blue-300';
      default: return 'bg-gray-500/20 text-gray-300';
    }
  };

  const calculatePriceChange = (current: number, predicted: number | null) => {
    if (!predicted) return null;
    return ((predicted - current) / current) * 100;
  };

  const getPredictedPrice = (prediction: SentimentPrediction) => {
    switch (selectedTimeframe) {
      case '1h': return prediction.predicted_price_1h;
      case '1d': return prediction.predicted_price_1d;
      case '1w': return prediction.predicted_price_1w;
      default: return prediction.predicted_price_1d;
    }
  };

  // Simulate predictions without sentiment for comparison
  const generateWithoutSentimentComparison = (withSentiment: SentimentPrediction[]) => {
    return withSentiment.map(pred => ({
      ...pred,
      // Simulate confidence without sentiment boost
      confidence: Math.max(30, pred.confidence - (pred.sentiment_score * pred.sentiment_confidence * 20)),
      // Simulate screening score without sentiment
      screening_score: Math.max(0, pred.screening_score - (pred.sentiment_score * pred.sentiment_confidence * 15)),
      // Reset sentiment data
      sentiment_score: 0,
      sentiment_confidence: 0,
      sentiment_impact: 'negligible',
      news_count: 0
    }));
  };

  const withoutSentimentPredictions = generateWithoutSentimentComparison(predictions);

  const renderPredictionCard = (prediction: SentimentPrediction, showSentiment: boolean) => {
    const priceChange = calculatePriceChange(prediction.current_price, getPredictedPrice(prediction));
    
    return (
      <motion.div
        key={`${prediction.ticker}-${showSentiment}`}
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="p-4 bg-white/5 backdrop-blur-sm rounded-lg border border-white/10 hover:bg-white/10 transition-all duration-300"
      >
        <div className="flex items-start justify-between mb-3">
          <div>
            <h3 className="font-semibold text-lg" style={{ color: 'var(--text-primary)' }}>{prediction.ticker}</h3>
            <p className="text-sm" style={{ color: 'var(--text-secondary)' }}>{prediction.sector}</p>
          </div>
          <div className="text-right">
            <div className={`px-2 py-1 rounded-full text-xs font-medium ${
              prediction.signal_type === 'BULLISH' ? 'bg-green-800/20 text-green-800' :
              prediction.signal_type === 'BEARISH' ? 'bg-red-600/20 text-red-600' :
              'bg-orange-500/20 text-orange-500'
            }`}>
              {prediction.signal_type}
            </div>
          </div>
        </div>

        <div className="space-y-2 mb-3">
          <div className="flex justify-between items-center">
            <span className="text-sm" style={{ color: 'var(--text-tertiary)' }}>Confidence</span>
            <span className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>{prediction.confidence.toFixed(1)}%</span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-sm" style={{ color: 'var(--text-tertiary)' }}>Screening Score</span>
            <span className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>{prediction.screening_score.toFixed(1)}</span>
          </div>
          <div className="flex justify-between items-center">
            <span className="text-sm" style={{ color: 'var(--text-tertiary)' }}>Current Price</span>
            <span className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>${prediction.current_price.toFixed(2)}</span>
          </div>
          {priceChange !== null && (
            <div className="flex justify-between items-center">
              <span className="text-sm" style={{ color: 'var(--text-tertiary)' }}>
                {selectedTimeframe.toUpperCase()} Prediction
              </span>
              <span className={`text-sm font-medium ${
                priceChange > 0 ? 'text-green-800' : priceChange < 0 ? 'text-red-600' : 'text-orange-500'
              }`}>
                {priceChange > 0 ? '+' : ''}{priceChange.toFixed(2)}%
              </span>
            </div>
          )}
        </div>

        {showSentiment && (
          <div className="border-t border-white/10 pt-3 mt-3">
            <div className="flex items-center justify-between mb-2">
              <span className="text-sm font-medium flex items-center gap-1" style={{ color: 'var(--text-primary)' }}>
                <Brain className="w-4 h-4" />
                Sentiment Analysis
              </span>
              <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>{prediction.news_count} news items</span>
            </div>
            
            <div className="grid grid-cols-2 gap-2 text-xs">
              <div className="flex justify-between">
                <span style={{ color: 'var(--text-tertiary)' }}>Score</span>
                <span className={`font-medium ${getSentimentColor(prediction.sentiment_score)}`}>
                  {prediction.sentiment_score.toFixed(3)}
                </span>
              </div>
              <div className="flex justify-between">
                <span style={{ color: 'var(--text-tertiary)' }}>Confidence</span>
                <span className="font-medium" style={{ color: 'var(--text-primary)' }}>
                  {(prediction.sentiment_confidence * 100).toFixed(1)}%
                </span>
              </div>
            </div>
            
            <div className="mt-2">
              <span className={`inline-flex px-2 py-1 rounded-full text-xs font-medium ${getImpactColor(prediction.sentiment_impact)}`}>
                {prediction.sentiment_impact}
              </span>
            </div>
          </div>
        )}

        <div className="mt-3 pt-3 border-t border-white/10">
          <div className="flex items-center justify-between text-xs" style={{ color: 'var(--text-tertiary)' }}>
            <span className="flex items-center gap-1">
              <Clock className="w-3 h-3" />
              {new Date(prediction.timestamp).toLocaleTimeString()}
            </span>
            <span>RSI: {prediction.rsi?.toFixed(1) || 'N/A'}</span>
          </div>
        </div>
      </motion.div>
    );
  };

  const renderSummaryStats = (predictions: SentimentPrediction[], title: string) => {
    const bullishCount = predictions.filter(p => p.signal_type === 'BULLISH').length;
    const bearishCount = predictions.filter(p => p.signal_type === 'BEARISH').length;
    const avgConfidence = predictions.reduce((sum, p) => sum + p.confidence, 0) / predictions.length;
    const avgScreeningScore = predictions.reduce((sum, p) => sum + p.screening_score, 0) / predictions.length;

    return (
      <GlassContainer className="p-4 mb-6">
        <h3 className="font-semibold mb-3" style={{ color: 'var(--text-primary)' }}>{title}</h3>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-center">
          <div>
            <div className="text-2xl font-bold text-green-800">{bullishCount}</div>
            <div className="text-sm" style={{ color: 'var(--text-tertiary)' }}>Bullish</div>
          </div>
          <div>
            <div className="text-2xl font-bold text-red-600">{bearishCount}</div>
            <div className="text-sm" style={{ color: 'var(--text-tertiary)' }}>Bearish</div>
          </div>
          <div>
            <div className="text-2xl font-bold text-orange-500">{avgConfidence.toFixed(1)}%</div>
            <div className="text-sm" style={{ color: 'var(--text-tertiary)' }}>Avg Confidence</div>
          </div>
          <div>
            <div className="text-2xl font-bold" style={{ color: 'var(--accent-primary)' }}>{avgScreeningScore.toFixed(1)}</div>
            <div className="text-sm" style={{ color: 'var(--text-tertiary)' }}>Avg Score</div>
          </div>
        </div>
      </GlassContainer>
    );
  };

  if (loading) {
    return (
      <div className="min-h-screen relative overflow-hidden" style={{ background: 'var(--bg-primary)' }}>
        <div className="absolute inset-0 opacity-5" style={{ background: 'var(--bg-secondary)' }}></div>
        <div className="max-w-7xl mx-auto relative z-10">
          <div className="flex items-center justify-center h-96">
            <LoadingSpinner size="lg" />
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen relative overflow-hidden" style={{ background: 'var(--bg-primary)' }}>
      <div className="absolute inset-0 opacity-5" style={{ background: 'var(--bg-secondary)' }}></div>
      <div className="max-w-7xl mx-auto relative z-10 p-4">
        {/* Header */}
        <div 
          className="mb-6 backdrop-blur-sm border rounded-2xl"
          style={{ 
            background: 'var(--header-bg)',
            borderColor: 'var(--header-border)',
            boxShadow: 'var(--shadow-glass-small)'
          }}
        >
          <div className="p-6">
            <div className="flex items-center justify-between mb-4">
              <div>
                <h1 className="text-2xl md:text-3xl font-bold mb-2" style={{ color: 'var(--text-primary)' }}>
                  <Brain className="w-8 h-8 inline mr-3" style={{ color: 'var(--accent-primary)' }} />
                  LLM Sentiment Analysis
                </h1>
                <p className="text-sm md:text-base" style={{ color: 'var(--text-secondary)' }}>
                  Compare stock predictions enhanced with AI-powered sentiment analysis vs traditional technical analysis
                </p>
              </div>
            </div>
          </div>
        </div>

        {/* Controls */}
        <GlassContainer className="p-4 mb-6">
          <div className="flex flex-wrap items-center justify-between gap-4">
            <div className="flex items-center gap-2">
              <label className="text-sm text-gray-400">View Mode:</label>
              <GlassButton
                variant={viewMode === 'dashboard' ? 'primary' : 'secondary'}
                onClick={() => setViewMode('dashboard')}
                className="text-sm"
              >
                <BarChart3 className="w-4 h-4 mr-1" />
                Dashboard
              </GlassButton>
              <GlassButton
                variant={viewMode === 'comparison' ? 'primary' : 'secondary'}
                onClick={() => setViewMode('comparison')}
                className="text-sm"
              >
                <Eye className="w-4 h-4 mr-1" />
                Comparison
              </GlassButton>
              <GlassButton
                variant={viewMode === 'sentiment-only' ? 'primary' : 'secondary'}
                onClick={() => setViewMode('sentiment-only')}
                className="text-sm"
              >
                <Brain className="w-4 h-4 mr-1" />
                Sentiment Only
              </GlassButton>
              <GlassButton
                variant={viewMode === 'detailed' ? 'primary' : 'secondary'}
                onClick={() => setViewMode('detailed')}
                className="text-sm"
              >
                <GitCompare className="w-4 h-4 mr-1" />
                Detailed
              </GlassButton>
            </div>

            <div className="flex items-center gap-2">
              <label className="text-sm text-gray-400">Timeframe:</label>
              {(['1h', '1d', '1w'] as const).map((timeframe) => (
                <GlassButton
                  key={timeframe}
                  variant={selectedTimeframe === timeframe ? 'primary' : 'secondary'}
                  onClick={() => setSelectedTimeframe(timeframe)}
                  className="text-sm"
                >
                  {timeframe.toUpperCase()}
                </GlassButton>
              ))}
            </div>

            <GlassButton
              variant="secondary"
              onClick={fetchPredictions}
              disabled={refreshing}
              className="text-sm"
            >
              <RefreshCw className={`w-4 h-4 mr-1 ${refreshing ? 'animate-spin' : ''}`} />
              Refresh
            </GlassButton>
          </div>
        </GlassContainer>

        {error && (
          <GlassContainer variant="bearish" className="mb-6">
            <div className="p-6 text-center">
              <AlertCircle className="w-12 h-12 mx-auto mb-4 text-red-400" />
              <h3 className="text-lg font-semibold text-white/95 mb-2">Error Loading Data</h3>
              <p className="text-white/70 mb-4">{error}</p>
              <GlassButton onClick={fetchPredictions} variant="warning">
                Try Again
              </GlassButton>
            </div>
          </GlassContainer>
        )}

        {/* Content */}
        {viewMode === 'dashboard' ? (
          <SentimentDashboard />
        ) : viewMode === 'comparison' ? (
          <div>
            {/* Performance Metrics Comparison */}
            <div className="grid md:grid-cols-2 gap-6 mb-8">
              <GlassContainer className="p-6">
                <h3 className="text-lg font-semibold mb-4 flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
                  <Brain className="w-5 h-5" style={{ color: 'var(--accent-primary)' }} />
                  LLM-Enhanced Performance
                </h3>
                <div className="space-y-4">
                  <div className="grid grid-cols-2 gap-4">
                    <div className="text-center p-3 bg-white/5 rounded-lg">
                      <div className="text-xl font-bold text-green-800">
                        {predictions.filter(p => p.signal_type === 'BULLISH').length}
                      </div>
                      <div className="text-sm" style={{ color: 'var(--text-tertiary)' }}>Bullish Signals</div>
                    </div>
                    <div className="text-center p-3 bg-white/5 rounded-lg">
                      <div className="text-xl font-bold text-red-600">
                        {predictions.filter(p => p.signal_type === 'BEARISH').length}
                      </div>
                      <div className="text-sm" style={{ color: 'var(--text-tertiary)' }}>Bearish Signals</div>
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-4">
                    <div className="text-center p-3 bg-white/5 rounded-lg">
                      <div className="text-xl font-bold text-orange-500">
                        {(predictions.reduce((sum, p) => sum + p.confidence, 0) / predictions.length).toFixed(1)}%
                      </div>
                      <div className="text-sm" style={{ color: 'var(--text-tertiary)' }}>Avg Confidence</div>
                    </div>
                    <div className="text-center p-3 bg-white/5 rounded-lg">
                      <div className="text-xl font-bold" style={{ color: 'var(--accent-primary)' }}>
                        {(predictions.reduce((sum, p) => sum + (p.sentiment_score || 0), 0) / predictions.length).toFixed(3)}
                      </div>
                      <div className="text-sm" style={{ color: 'var(--text-tertiary)' }}>Avg Sentiment</div>
                    </div>
                  </div>
                </div>
              </GlassContainer>

              <GlassContainer className="p-6">
                <h3 className="text-lg font-semibold mb-4 flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
                  <EyeOff className="w-5 h-5" style={{ color: 'var(--text-secondary)' }} />
                  Traditional Analysis Performance
                </h3>
                <div className="space-y-4">
                  <div className="grid grid-cols-2 gap-4">
                    <div className="text-center p-3 bg-white/5 rounded-lg">
                      <div className="text-xl font-bold text-green-400">
                        {withoutSentimentPredictions.filter(p => p.signal_type === 'BULLISH').length}
                      </div>
                      <div className="text-sm text-gray-400">Bullish Signals</div>
                    </div>
                    <div className="text-center p-3 bg-white/5 rounded-lg">
                      <div className="text-xl font-bold text-red-400">
                        {withoutSentimentPredictions.filter(p => p.signal_type === 'BEARISH').length}
                      </div>
                      <div className="text-sm text-gray-400">Bearish Signals</div>
                    </div>
                  </div>
                  <div className="grid grid-cols-2 gap-4">
                    <div className="text-center p-3 bg-white/5 rounded-lg">
                      <div className="text-xl font-bold text-blue-400">
                        {(withoutSentimentPredictions.reduce((sum, p) => sum + p.confidence, 0) / withoutSentimentPredictions.length).toFixed(1)}%
                      </div>
                      <div className="text-sm text-gray-400">Avg Confidence</div>
                    </div>
                    <div className="text-center p-3 bg-white/5 rounded-lg">
                      <div className="text-xl font-bold text-gray-400">
                        N/A
                      </div>
                      <div className="text-sm text-gray-400">Sentiment</div>
                    </div>
                  </div>
                </div>
              </GlassContainer>
            </div>

            {/* Side by Side Predictions */}
            <div className="grid md:grid-cols-2 gap-6">
              {/* With Sentiment */}
              <div>
                <h2 className="text-xl font-semibold text-white mb-4 flex items-center gap-2">
                  <Brain className="w-5 h-5 text-purple-400" />
                  With LLM Sentiment Analysis
                </h2>
                <div className="space-y-4 max-h-96 overflow-y-auto">
                  {predictions.slice(0, 10).map(prediction => renderPredictionCard(prediction, true))}
                </div>
              </div>

              {/* Without Sentiment */}
              <div>
                <h2 className="text-xl font-semibold text-white mb-4 flex items-center gap-2">
                  <EyeOff className="w-5 h-5 text-gray-400" />
                  Traditional Technical Analysis
                </h2>
                <div className="space-y-4 max-h-96 overflow-y-auto">
                  {withoutSentimentPredictions.slice(0, 10).map(prediction => renderPredictionCard(prediction, false))}
                </div>
              </div>
            </div>
          </div>
        ) : viewMode === 'detailed' ? (
          <div>
            <h2 className="text-xl font-semibold text-white mb-4 flex items-center gap-2">
              <GitCompare className="w-5 h-5 text-blue-400" />
              Detailed Comparison Analysis
            </h2>
            <div className="space-y-6">
              {predictions.slice(0, 5).map((prediction, index) => (
                <div key={prediction.ticker} className="grid lg:grid-cols-3 gap-6">
                  <div className="lg:col-span-2">
                    <PredictionComparison
                      withSentiment={prediction}
                      withoutSentiment={withoutSentimentPredictions[index]}
                    />
                  </div>
                  <div>
                    <SentimentChart
                      data={{
                        score: prediction.sentiment_score,
                        confidence: prediction.sentiment_confidence,
                        impact: prediction.sentiment_impact,
                        newsCount: prediction.news_count,
                        timestamp: prediction.timestamp
                      }}
                    />
                  </div>
                </div>
              ))}
            </div>
          </div>
        ) : (
          <div>
            <h2 className="text-xl font-semibold text-white mb-4 flex items-center gap-2">
              <Brain className="w-5 h-5 text-purple-400" />
              LLM-Enhanced Predictions
            </h2>
            {renderSummaryStats(predictions, 'Sentiment-Enhanced Analysis')}
            <div className="grid md:grid-cols-2 lg:grid-cols-3 gap-4">
              {predictions.map(prediction => renderPredictionCard(prediction, true))}
            </div>
          </div>
        )}

        {predictions.length === 0 && !loading && (
          <div className="text-center py-12">
            <AlertCircle className="w-16 h-16 text-gray-400 mx-auto mb-4" />
            <h3 className="text-xl font-semibold text-gray-400 mb-2">No Predictions Found</h3>
            <p className="text-gray-500">
              No sentiment-enhanced predictions available. The system may still be generating data.
            </p>
          </div>
        )}
      </div>
    </div>
  );
};

export default SentimentAnalysis;