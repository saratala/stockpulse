import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  ArrowLeft,
  BarChart3,
  Activity,
  AlertTriangle,
  DollarSign,
  Volume2,
  Star,
  RefreshCw,
  Info,
  Eye
} from 'lucide-react';
import { XAxis, YAxis, ResponsiveContainer, AreaChart, Area } from 'recharts';
import GlassContainer from '../components/GlassContainer';
import GlassButton from '../components/GlassButton';
import LoadingSpinner from '../components/LoadingSpinner';
import '../styles/glassmorphism.css';

interface StockInfo {
  id: number;
  ticker: string;
  name: string;
  sector: string;
  industry: string;
  marketCap: number | null;
}

interface StockPrice {
  ticker: string;
  date: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  adjustedClose: number;
}

interface SentimentData {
  ticker: string;
  averageSentiment: number;
  totalNews: number;
  totalSentiments: number;
  polarityCounts: [string, number][];
  sentiments: {
    id: number;
    ticker: string;
    sentimentScore: number;
    polarity: string;
    reasoning: string;
    analysisProvider: string;
    source: string;
    confidence: number;
  }[];
  news: {
    id: number;
    ticker: string;
    title: string;
    url: string;
    publishedAt: string;
    source: string;
    content: string;
  }[];
}

interface StockAnalysisData {
  stock: StockInfo;
  latestPrice: StockPrice | null;
  sentiment: SentimentData | null;
  priceHistory: StockPrice[];
}

const StockAnalysis: React.FC = () => {
  const { ticker } = useParams<{ ticker: string }>();
  const [analysisData, setAnalysisData] = useState<StockAnalysisData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'overview' | 'sentiment' | 'news'>('overview');
  const [refreshing, setRefreshing] = useState(false);

  const API_BASE_URL = '/api/v1';

  const fetchAnalysis = async (showRefreshSpinner = false) => {
    if (!ticker) return;

    try {
      if (showRefreshSpinner) {
        setRefreshing(true);
      } else {
        setLoading(true);
      }
      setError(null);

      const upperTicker = ticker.toUpperCase();

      // Fetch all data in parallel
      const [stockRes, priceRes, sentimentRes, historyRes] = await Promise.all([
        fetch(`${API_BASE_URL}/stocks/${upperTicker}`),
        fetch(`${API_BASE_URL}/stocks/${upperTicker}/prices/latest`),
        fetch(`${API_BASE_URL}/sentiment/${upperTicker}`),
        fetch(`${API_BASE_URL}/stocks/${upperTicker}/prices?limit=30`)
      ]);

      if (!stockRes.ok) {
        throw new Error(`Stock not found: ${upperTicker}`);
      }

      const stock = await stockRes.json();
      const latestPrice = priceRes.ok ? await priceRes.json() : null;
      const sentiment = sentimentRes.ok ? await sentimentRes.json() : null;
      const priceHistory = historyRes.ok ? await historyRes.json() : [];

      setAnalysisData({ stock, latestPrice, sentiment, priceHistory });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch analysis');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchAnalysis();
  }, [ticker]);

  const getSentimentColor = (sentiment: number) => {
    if (sentiment > 0.2) return 'text-emerald-400';
    if (sentiment < -0.2) return 'text-red-400';
    return 'text-amber-400';
  };

  const getSentimentLabel = (sentiment: number) => {
    if (sentiment > 0.2) return 'Bullish';
    if (sentiment < -0.2) return 'Bearish';
    return 'Neutral';
  };

  const tabs = [
    { key: 'overview', label: 'Overview', icon: BarChart3 },
    { key: 'sentiment', label: 'Sentiment', icon: Activity },
    { key: 'news', label: 'News', icon: Eye }
  ];

  if (loading && !analysisData) {
    return (
      <div className="min-h-screen flex items-center justify-center" style={{ background: 'var(--bg-primary)' }}>
        <LoadingSpinner size="lg" text="Loading stock data..." />
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex items-center justify-center p-4" style={{ background: 'var(--bg-primary)' }}>
        <GlassContainer variant="bearish" className="max-w-md w-full">
          <div className="p-8 text-center">
            <AlertTriangle className="w-16 h-16 mx-auto mb-4 text-red-400" />
            <h3 className="text-xl font-bold text-white/95 mb-2">Analysis Failed</h3>
            <p className="text-white/70 mb-6">{error}</p>
            <GlassButton onClick={() => fetchAnalysis()} variant="warning">
              Try Again
            </GlassButton>
          </div>
        </GlassContainer>
      </div>
    );
  }

  if (!analysisData) return null;

  const { stock, latestPrice, sentiment, priceHistory } = analysisData;

  return (
    <div className="min-h-screen relative overflow-hidden" style={{ background: 'var(--bg-primary)' }}>
      {/* Background Elements */}
      <div className="absolute inset-0 opacity-5" style={{ background: 'var(--bg-secondary)' }}></div>

      <div className="relative z-10 p-4 pt-8">
        {/* Header */}
        <div
          className="mb-6 backdrop-blur-sm border rounded-2xl"
          style={{
            background: 'var(--glass-primary)',
            borderColor: 'var(--border-glass)',
            boxShadow: '0 4px 16px rgba(0, 0, 0, 0.3)'
          }}
        >
          <div className="p-6">
            <div className="flex items-center justify-between mb-6">
              <div className="flex items-center gap-4">
                <GlassButton
                  size="sm"
                  variant="secondary"
                  onClick={() => window.history.back()}
                  icon={<ArrowLeft className="w-4 h-4" />}
                />
                <div>
                  <h1 className="text-2xl md:text-3xl font-bold text-slate-50">
                    {stock.ticker}
                  </h1>
                  <p className="text-sm text-slate-400">
                    {stock.name} • {stock.sector}
                  </p>
                </div>
              </div>
              <div className="flex gap-2">
                <GlassButton
                  size="sm"
                  onClick={() => fetchAnalysis(true)}
                  disabled={refreshing}
                  icon={<RefreshCw className={`w-4 h-4 ${refreshing ? 'animate-spin' : ''}`} />}
                />
                <GlassButton
                  size="sm"
                  variant="accent"
                  icon={<Star className="w-4 h-4" />}
                >
                  Watch
                </GlassButton>
              </div>
            </div>

            {/* Price and Sentiment Summary */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <GlassContainer variant="accent" className="p-4">
                <div className="flex items-center gap-3">
                  <DollarSign className="w-8 h-8 text-indigo-400" />
                  <div>
                    <div className="text-2xl font-bold text-slate-50">
                      ${latestPrice?.close?.toFixed(2) || 'N/A'}
                    </div>
                    <div className="text-sm text-slate-400">Current Price</div>
                  </div>
                </div>
              </GlassContainer>

              <GlassContainer className="p-4">
                <div className="flex items-center gap-3">
                  <Volume2 className="w-8 h-8 text-cyan-400" />
                  <div>
                    <div className="text-lg font-bold text-slate-50">
                      {latestPrice?.volume ? (latestPrice.volume / 1000000).toFixed(2) + 'M' : 'N/A'}
                    </div>
                    <div className="text-sm text-slate-400">Volume</div>
                  </div>
                </div>
              </GlassContainer>

              {sentiment && (
                <GlassContainer className="p-4">
                  <div className="flex items-center gap-3">
                    <Activity className={`w-8 h-8 ${getSentimentColor(sentiment.averageSentiment)}`} />
                    <div>
                      <div className={`text-lg font-bold ${getSentimentColor(sentiment.averageSentiment)}`}>
                        {getSentimentLabel(sentiment.averageSentiment)}
                      </div>
                      <div className="text-sm text-slate-400">
                        {sentiment.totalNews} news articles
                      </div>
                    </div>
                  </div>
                </GlassContainer>
              )}
            </div>
          </div>
        </div>

        {/* Tabs */}
        <GlassContainer className="mb-6">
          <div className="p-4">
            <div className="grid grid-cols-3 gap-2">
              {tabs.map(({ key, label, icon: Icon }) => (
                <button
                  key={key}
                  onClick={() => setActiveTab(key as any)}
                  className={`flex items-center justify-center gap-2 px-4 py-3 rounded-xl transition-all duration-300 ${
                    activeTab === key
                      ? 'bg-indigo-500/20 backdrop-blur-md border border-indigo-500/30 text-indigo-400'
                      : 'bg-slate-800/50 hover:bg-slate-700/50 text-slate-300'
                  }`}
                >
                  <Icon className="w-4 h-4" />
                  <span className="text-sm font-medium">{label}</span>
                </button>
              ))}
            </div>
          </div>
        </GlassContainer>

        {/* Tab Content */}
        <AnimatePresence mode="wait">
          <motion.div
            key={activeTab}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -20 }}
            transition={{ duration: 0.3 }}
          >
            {/* Overview Tab */}
            {activeTab === 'overview' && (
              <div className="space-y-6">
                {/* Stock Info */}
                <GlassContainer>
                  <div className="p-6">
                    <h3 className="text-lg font-bold text-slate-50 mb-4 flex items-center gap-2">
                      <Info className="w-5 h-5 text-indigo-400" />
                      Stock Information
                    </h3>
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                      <div className="p-3 rounded-xl bg-slate-800/50">
                        <div className="text-sm text-slate-400">Sector</div>
                        <div className="text-lg font-medium text-slate-100">{stock.sector}</div>
                      </div>
                      <div className="p-3 rounded-xl bg-slate-800/50">
                        <div className="text-sm text-slate-400">Industry</div>
                        <div className="text-lg font-medium text-slate-100">{stock.industry}</div>
                      </div>
                      {latestPrice && (
                        <>
                          <div className="p-3 rounded-xl bg-slate-800/50">
                            <div className="text-sm text-slate-400">Day High</div>
                            <div className="text-lg font-medium text-emerald-400">${latestPrice.high.toFixed(2)}</div>
                          </div>
                          <div className="p-3 rounded-xl bg-slate-800/50">
                            <div className="text-sm text-slate-400">Day Low</div>
                            <div className="text-lg font-medium text-red-400">${latestPrice.low.toFixed(2)}</div>
                          </div>
                        </>
                      )}
                    </div>
                  </div>
                </GlassContainer>

                {/* Price History Chart */}
                {priceHistory.length > 0 && (
                  <GlassContainer>
                    <div className="p-6">
                      <h3 className="text-lg font-bold text-slate-50 mb-4 flex items-center gap-2">
                        <BarChart3 className="w-5 h-5 text-indigo-400" />
                        Price History
                      </h3>
                      <div className="h-64">
                        <ResponsiveContainer width="100%" height="100%">
                          <AreaChart data={priceHistory.slice().reverse()}>
                            <defs>
                              <linearGradient id="priceGradient" x1="0" y1="0" x2="0" y2="1">
                                <stop offset="5%" stopColor="#6366F1" stopOpacity={0.3}/>
                                <stop offset="95%" stopColor="#6366F1" stopOpacity={0}/>
                              </linearGradient>
                            </defs>
                            <XAxis
                              dataKey="date"
                              tickFormatter={(val) => new Date(val).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}
                              stroke="#94A3B8"
                              tick={{ fill: '#94A3B8', fontSize: 12 }}
                            />
                            <YAxis
                              stroke="#94A3B8"
                              tick={{ fill: '#94A3B8', fontSize: 12 }}
                              domain={['auto', 'auto']}
                              tickFormatter={(val) => `$${val}`}
                            />
                            <Area
                              type="monotone"
                              dataKey="close"
                              stroke="#6366F1"
                              fill="url(#priceGradient)"
                              strokeWidth={2}
                            />
                          </AreaChart>
                        </ResponsiveContainer>
                      </div>
                    </div>
                  </GlassContainer>
                )}
              </div>
            )}

            {/* Sentiment Tab */}
            {activeTab === 'sentiment' && (
              <div className="space-y-6">
                {sentiment ? (
                  <>
                    <GlassContainer>
                      <div className="p-6">
                        <h3 className="text-lg font-bold text-slate-50 mb-4 flex items-center gap-2">
                          <Activity className="w-5 h-5 text-indigo-400" />
                          Sentiment Analysis
                        </h3>
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                          <div className="p-4 rounded-xl bg-slate-800/50">
                            <div className="text-sm text-slate-400 mb-1">Average Sentiment</div>
                            <div className={`text-2xl font-bold ${getSentimentColor(sentiment.averageSentiment)}`}>
                              {sentiment.averageSentiment.toFixed(2)}
                            </div>
                          </div>
                          <div className="p-4 rounded-xl bg-slate-800/50">
                            <div className="text-sm text-slate-400 mb-1">News Articles</div>
                            <div className="text-2xl font-bold text-slate-100">{sentiment.totalNews}</div>
                          </div>
                          <div className="p-4 rounded-xl bg-slate-800/50">
                            <div className="text-sm text-slate-400 mb-1">Sentiment Label</div>
                            <div className={`text-2xl font-bold ${getSentimentColor(sentiment.averageSentiment)}`}>
                              {getSentimentLabel(sentiment.averageSentiment)}
                            </div>
                          </div>
                        </div>
                      </div>
                    </GlassContainer>

                    {/* Polarity Distribution */}
                    {sentiment.polarityCounts.length > 0 && (
                      <GlassContainer>
                        <div className="p-6">
                          <h3 className="text-lg font-bold text-slate-50 mb-4">Polarity Distribution</h3>
                          <div className="flex gap-4 flex-wrap">
                            {sentiment.polarityCounts.map(([polarity, count]) => (
                              <div key={polarity} className="flex items-center gap-2 px-4 py-2 rounded-lg bg-slate-800/50">
                                <div className={`w-3 h-3 rounded-full ${
                                  polarity === 'positive' ? 'bg-emerald-400' :
                                  polarity === 'negative' ? 'bg-red-400' : 'bg-amber-400'
                                }`} />
                                <span className="text-slate-300 capitalize">{polarity}</span>
                                <span className="text-slate-400">({count})</span>
                              </div>
                            ))}
                          </div>
                        </div>
                      </GlassContainer>
                    )}

                    {/* Individual Sentiments */}
                    {sentiment.sentiments.length > 0 && (
                      <GlassContainer>
                        <div className="p-6">
                          <h3 className="text-lg font-bold text-slate-50 mb-4">Sentiment Details</h3>
                          <div className="space-y-3">
                            {sentiment.sentiments.slice(0, 5).map((s) => (
                              <div key={s.id} className="p-4 rounded-xl bg-slate-800/50 border border-slate-700/50">
                                <div className="flex justify-between items-start mb-2">
                                  <span className={`px-2 py-1 rounded text-xs font-medium ${
                                    s.polarity === 'positive' ? 'bg-emerald-500/20 text-emerald-400' :
                                    s.polarity === 'negative' ? 'bg-red-500/20 text-red-400' : 'bg-amber-500/20 text-amber-400'
                                  }`}>
                                    {s.polarity}
                                  </span>
                                  <span className="text-xs text-slate-400">{s.source}</span>
                                </div>
                                <p className="text-sm text-slate-300">{s.reasoning}</p>
                                <div className="mt-2 text-xs text-slate-500">
                                  Confidence: {(s.confidence * 100).toFixed(0)}% • Provider: {s.analysisProvider}
                                </div>
                              </div>
                            ))}
                          </div>
                        </div>
                      </GlassContainer>
                    )}
                  </>
                ) : (
                  <GlassContainer>
                    <div className="p-8 text-center">
                      <Activity className="w-12 h-12 mx-auto mb-4 text-slate-500" />
                      <p className="text-slate-400">No sentiment data available for this stock.</p>
                    </div>
                  </GlassContainer>
                )}
              </div>
            )}

            {/* News Tab */}
            {activeTab === 'news' && (
              <div className="space-y-6">
                {sentiment && sentiment.news.length > 0 ? (
                  <GlassContainer>
                    <div className="p-6">
                      <h3 className="text-lg font-bold text-slate-50 mb-4 flex items-center gap-2">
                        <Eye className="w-5 h-5 text-indigo-400" />
                        Recent News
                      </h3>
                      <div className="space-y-4">
                        {sentiment.news.map((article) => (
                          <motion.a
                            key={article.id}
                            href={article.url}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="block p-4 rounded-xl bg-slate-800/50 border border-slate-700/50 hover:border-indigo-500/30 transition-colors"
                            initial={{ opacity: 0, x: -20 }}
                            animate={{ opacity: 1, x: 0 }}
                          >
                            <h4 className="text-slate-100 font-medium mb-2 hover:text-indigo-400 transition-colors">
                              {article.title}
                            </h4>
                            <p className="text-sm text-slate-400 mb-2 line-clamp-2">{article.content}</p>
                            <div className="flex items-center gap-4 text-xs text-slate-500">
                              <span>{article.source}</span>
                              <span>{new Date(article.publishedAt).toLocaleDateString()}</span>
                            </div>
                          </motion.a>
                        ))}
                      </div>
                    </div>
                  </GlassContainer>
                ) : (
                  <GlassContainer>
                    <div className="p-8 text-center">
                      <Eye className="w-12 h-12 mx-auto mb-4 text-slate-500" />
                      <p className="text-slate-400">No news articles available for this stock.</p>
                    </div>
                  </GlassContainer>
                )}
              </div>
            )}
          </motion.div>
        </AnimatePresence>
      </div>
    </div>
  );
};

export default StockAnalysis;