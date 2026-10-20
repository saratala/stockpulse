import React, { useState, useEffect, useCallback } from 'react';
import { useSearchParams } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Brain,
  TrendingUp,
  TrendingDown,
  Minus,
  Activity,
  BarChart3,
  Target,
  AlertTriangle,
  Zap,
  RefreshCw,
  Search,
  ChevronDown,
  ChevronUp,
  Newspaper,
  LineChart,
  Shield,
  Clock,
  DollarSign,
  Percent,
  PieChart,
  ArrowUpRight,
  ArrowDownRight,
  TrendingUp as TrendUp,
  AlertCircle,
  CheckCircle,
  XCircle,
  BarChart2
} from 'lucide-react';
import GlassContainer from '../components/GlassContainer';
import GlassButton from '../components/GlassButton';
import LoadingSpinner from '../components/LoadingSpinner';
import {
  fetchAllTickers,
  fetchAIAnalysis,
  analyzeSentimentForTicker,
  fetchNewsForTicker,
  NewsArticle
} from '../services/api';
import '../styles/glassmorphism.css';

interface AIAnalysisData {
  ticker: string;
  analysisTime: string;
  analysisVersion: string;
  currentPrice: number;
  priceChange: number;
  priceChangePercent: number;
  volume: number;
  avgVolume: number;
  technicalSummary: {
    compositeScore: number;
    primarySignal: string;
    signalConfidence: number;
    trendDirection: string;
    rsi: number;
    rsiSignal: string;
    macd: number;
    macdSignal: string;
    emaStackAligned: boolean;
    supertrend: number;
    supertrendBullish: boolean;
    ichimokuSignal: string;
    obvTrend: number;
    mfi: number;
    chaikinMoneyFlow: number;
    atr: number;
    atrPercent: number;
    historicalVolatility: number;
    riskLevel: string;
    supportLevel: number;
    resistanceLevel: number;
    pivotPoint: number;
    signals: string[];
    candlestickPatterns: string[];
    trendScore: number;
    momentumScore: number;
    volumeScore: number;
    volatilityScore: number;
  };
  mlPrediction: {
    predictedDirection: string;
    bullishProbability: number;
    bearishProbability: number;
    overallConfidence: number;
    predicted1Day: number;
    predicted1Week: number;
    return1Day: number;
    return1Week: number;
    featureContributions: Record<string, number>;
    modelVersion: string;
  };
  backtestSummary: {
    daysAnalyzed: number;
    directionAccuracy: number;
    winRate: number;
    profitFactor: number;
    sharpeRatio: number;
    maxDrawdown: number;
    bestStrategy: string;
    bestStrategyReturn: number;
    buyHoldReturn: number;
    topStrategies: Array<{
      strategyName: string;
      totalReturn: number;
      winRate: number;
      numberOfTrades: number;
    }>;
  };
  recommendation: {
    action: string;
    confidence: number;
    timeHorizon: string;
    reasoning: string;
    suggestedEntryPrice: number;
    stopLossPrice: number;
    targetPrice1: number;
    targetPrice2: number;
    riskRewardRatio: number;
    positionSizePercent: number;
    bullishFactors: string[];
    bearishFactors: string[];
    keyRisks: string[];
  };
  riskAssessment: {
    overallRisk: string;
    riskScore: number;
    volatilityRisk: number;
    trendRisk: number;
    liquidityRisk: number;
    valueAtRisk: number;
    maxExpectedLoss: number;
    riskFactors: string[];
  };
  fundamentals: {
    peRatio: number | null;
    epsGrowth: number | null;
    marketCap: number | null;
    marketCapCategory: string;
  };
}

interface SentimentData {
  ticker: string;
  sentiment: string;
  averageSentiment: number;
  confidence: number;
  newsCount: number;
  lastUpdated: string;
}

// US Market holidays for 2025-2026 (NYSE/NASDAQ)
const US_MARKET_HOLIDAYS: string[] = [
  // 2025
  '2025-01-01', // New Year's Day
  '2025-01-20', // MLK Day
  '2025-02-17', // Presidents Day
  '2025-04-18', // Good Friday
  '2025-05-26', // Memorial Day
  '2025-06-19', // Juneteenth
  '2025-07-04', // Independence Day
  '2025-09-01', // Labor Day
  '2025-11-27', // Thanksgiving
  '2025-12-25', // Christmas
  // 2026
  '2026-01-01', // New Year's Day
  '2026-01-19', // MLK Day
  '2026-02-16', // Presidents Day
  '2026-04-03', // Good Friday
  '2026-05-25', // Memorial Day
  '2026-06-19', // Juneteenth
  '2026-07-03', // Independence Day (observed)
  '2026-09-07', // Labor Day
  '2026-11-26', // Thanksgiving
  '2026-12-25', // Christmas
];

/**
 * Check if a date is a trading day (not weekend, not holiday)
 */
const isTradingDay = (date: Date): boolean => {
  const dayOfWeek = date.getDay();
  // Weekend check (0 = Sunday, 6 = Saturday)
  if (dayOfWeek === 0 || dayOfWeek === 6) {
    return false;
  }
  // Holiday check
  const dateStr = date.toISOString().split('T')[0];
  return !US_MARKET_HOLIDAYS.includes(dateStr);
};

/**
 * Get the next trading day after a given number of trading days
 * @param tradingDays Number of trading days to add (1 = next trading day, 5 = ~1 week)
 */
const getNextTradingDay = (tradingDays: number): Date => {
  const result = new Date();
  let daysAdded = 0;

  while (daysAdded < tradingDays) {
    result.setDate(result.getDate() + 1);
    if (isTradingDay(result)) {
      daysAdded++;
    }
  }

  return result;
};

/**
 * Format a trading day for display
 */
const formatTradingDay = (date: Date): string => {
  return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
};

const AIAnalysis: React.FC = () => {
  const [searchParams] = useSearchParams();
  const [selectedTicker, setSelectedTicker] = useState<string>('');
  const [tickers, setTickers] = useState<string[]>([]);
  const [tickersLoading, setTickersLoading] = useState(true);
  const [aiAnalysis, setAiAnalysis] = useState<AIAnalysisData | null>(null);
  const [sentimentData, setSentimentData] = useState<SentimentData | null>(null);
  const [newsArticles, setNewsArticles] = useState<NewsArticle[]>([]);
  const [newsLoading, setNewsLoading] = useState(false);
  const [loading, setLoading] = useState(false);
  const [sentimentLoading, setSentimentLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [expandedSection, setExpandedSection] = useState<string | null>('recommendation');
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  // Load tickers on mount and check for URL ticker parameter
  useEffect(() => {
    const loadTickers = async () => {
      try {
        const tickerList = await fetchAllTickers();
        if (tickerList && tickerList.length > 0) {
          setTickers(tickerList);
          // Check if ticker is passed via URL query param
          const urlTicker = searchParams.get('ticker');
          if (urlTicker && tickerList.map((t: string) => t.toUpperCase()).includes(urlTicker.toUpperCase())) {
            setSelectedTicker(urlTicker.toUpperCase());
          } else {
            setSelectedTicker(tickerList[0]);
          }
        }
      } catch (err) {
        console.error('Error fetching tickers:', err);
      } finally {
        setTickersLoading(false);
      }
    };
    loadTickers();
  }, [searchParams]);

  // Load AI analysis when ticker changes
  const loadAnalysis = useCallback(async (ticker?: string) => {
    const tickerToAnalyze = ticker || selectedTicker;
    if (!tickerToAnalyze) return;

    try {
      setLoading(true);
      setError(null);
      const data = await fetchAIAnalysis(tickerToAnalyze);
      setAiAnalysis(data);
      setLastUpdated(new Date());
    } catch (err) {
      setError('Failed to load AI analysis');
      console.error('Error:', err);
    } finally {
      setLoading(false);
    }
  }, [selectedTicker]);

  // Load sentiment for selected ticker
  const loadSentiment = useCallback(async (ticker?: string) => {
    const tickerToAnalyze = ticker || selectedTicker;
    if (!tickerToAnalyze) return;
    try {
      setSentimentLoading(true);
      const result = await analyzeSentimentForTicker(tickerToAnalyze);
      setSentimentData(result);
    } catch (err) {
      console.error('Error loading sentiment:', err);
    } finally {
      setSentimentLoading(false);
    }
  }, [selectedTicker]);

  // Load news articles for selected ticker
  const loadNews = useCallback(async (ticker?: string) => {
    const tickerToAnalyze = ticker || selectedTicker;
    if (!tickerToAnalyze) return;
    try {
      setNewsLoading(true);
      const articles = await fetchNewsForTicker(tickerToAnalyze, 72, 10);
      setNewsArticles(articles);
    } catch (err) {
      console.error('Error loading news:', err);
      setNewsArticles([]);
    } finally {
      setNewsLoading(false);
    }
  }, [selectedTicker]);

  // Auto-load analysis when ticker is selected
  useEffect(() => {
    if (selectedTicker) {
      setAiAnalysis(null);
      setSentimentData(null);
      setNewsArticles([]);
      loadAnalysis(selectedTicker);
    }
  }, [selectedTicker]);

  const getSignalColor = (signal?: string) => {
    switch (signal?.toUpperCase()) {
      case 'BULLISH':
      case 'UP':
      case 'STRONG_BUY':
      case 'BUY': return 'text-green-500';
      case 'BEARISH':
      case 'DOWN':
      case 'STRONG_SELL':
      case 'SELL': return 'text-red-500';
      default: return 'text-orange-500';
    }
  };

  const getSignalBg = (signal?: string) => {
    switch (signal?.toUpperCase()) {
      case 'BULLISH':
      case 'UP':
      case 'STRONG_BUY':
      case 'BUY': return 'bg-green-500/10 border-green-500/30';
      case 'BEARISH':
      case 'DOWN':
      case 'STRONG_SELL':
      case 'SELL': return 'bg-red-500/10 border-red-500/30';
      default: return 'bg-orange-500/10 border-orange-500/30';
    }
  };

  const getSignalIcon = (signal?: string) => {
    switch (signal?.toUpperCase()) {
      case 'BULLISH':
      case 'UP':
      case 'STRONG_BUY':
      case 'BUY': return <TrendingUp className="w-5 h-5" />;
      case 'BEARISH':
      case 'DOWN':
      case 'STRONG_SELL':
      case 'SELL': return <TrendingDown className="w-5 h-5" />;
      default: return <Minus className="w-5 h-5" />;
    }
  };

  const getRiskColor = (risk?: string) => {
    switch (risk?.toUpperCase()) {
      case 'LOW': return 'text-green-500';
      case 'MEDIUM': return 'text-yellow-500';
      case 'HIGH': return 'text-orange-500';
      case 'VERY_HIGH': return 'text-red-500';
      default: return 'text-gray-500';
    }
  };

  const getActionColor = (action?: string) => {
    switch (action?.toUpperCase()) {
      case 'STRONG_BUY': return 'bg-green-600 text-white';
      case 'BUY': return 'bg-green-500/20 text-green-400 border border-green-500/50';
      case 'HOLD': return 'bg-yellow-500/20 text-yellow-400 border border-yellow-500/50';
      case 'SELL': return 'bg-red-500/20 text-red-400 border border-red-500/50';
      case 'STRONG_SELL': return 'bg-red-600 text-white';
      default: return 'bg-gray-500/20 text-gray-400';
    }
  };

  const filteredTickers = tickers.filter(t =>
    t.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const toggleSection = (section: string) => {
    setExpandedSection(expandedSection === section ? null : section);
  };

  const formatNumber = (num: number | null | undefined) => {
    if (num === null || num === undefined) return 'N/A';
    return num.toFixed(2);
  };

  const formatPercent = (num: number | null | undefined) => {
    if (num === null || num === undefined) return 'N/A';
    const sign = num >= 0 ? '+' : '';
    return `${sign}${num.toFixed(2)}%`;
  };

  const formatMarketCap = (cap: number | null | undefined) => {
    if (!cap) return 'N/A';
    if (cap >= 1e12) return `$${(cap / 1e12).toFixed(2)}T`;
    if (cap >= 1e9) return `$${(cap / 1e9).toFixed(2)}B`;
    if (cap >= 1e6) return `$${(cap / 1e6).toFixed(2)}M`;
    return `$${cap.toLocaleString()}`;
  };

  return (
    <div className="min-h-screen p-6" style={{ background: 'var(--bg-primary)' }}>
      {/* Full-screen Loading Overlay */}
      <AnimatePresence>
        {loading && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 z-50 flex items-center justify-center"
            style={{ background: 'rgba(15, 23, 42, 0.85)', backdropFilter: 'blur(8px)' }}
          >
            <motion.div
              initial={{ scale: 0.9, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.9, opacity: 0 }}
              className="text-center"
            >
              <div className="relative mb-6">
                <div className="w-20 h-20 border-4 border-purple-500/30 rounded-full" />
                <div className="absolute inset-0 w-20 h-20 border-4 border-transparent border-t-purple-500 rounded-full animate-spin" />
                <Brain className="absolute inset-0 m-auto w-8 h-8 text-purple-400" />
              </div>
              <h3 className="text-xl font-bold text-white mb-2">Analyzing {selectedTicker}</h3>
              <p className="text-slate-400">Running comprehensive AI analysis...</p>
              <div className="mt-4 flex items-center justify-center gap-2 text-sm text-slate-500">
                <RefreshCw className="w-4 h-4 animate-spin" />
                <span>Technical indicators, ML predictions, backtesting</span>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>

      <div className="max-w-7xl mx-auto">
        {/* Header */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          className="mb-8"
        >
          <div className="flex items-center gap-3 mb-2">
            <Brain className="w-8 h-8 text-purple-500" />
            <h1 className="text-4xl font-bold" style={{ color: 'var(--text-primary)' }}>
              AI Analysis
            </h1>
          </div>
          <div className="flex items-center justify-between">
            <p className="text-lg" style={{ color: 'var(--text-secondary)' }}>
              Comprehensive AI-powered stock analysis with ML predictions & backtesting
            </p>
            {lastUpdated && (
              <div className="flex items-center gap-1 text-sm" style={{ color: 'var(--text-tertiary)' }}>
                <Clock className="w-4 h-4" />
                Updated: {lastUpdated.toLocaleTimeString()}
              </div>
            )}
          </div>
        </motion.div>

        {/* Ticker Selection */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.1 }}
          className="mb-6"
        >
          <GlassContainer className="p-6">
            <div className="flex flex-col md:flex-row gap-4 items-start md:items-center justify-between mb-4">
              <h3 className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>
                Select Stock for Analysis
              </h3>
              <div className="flex items-center gap-2">
                <div className="relative">
                  <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4" style={{ color: 'var(--text-tertiary)' }} />
                  <input
                    type="text"
                    placeholder="Search ticker..."
                    value={searchTerm}
                    onChange={(e) => setSearchTerm(e.target.value)}
                    className="pl-9 pr-4 py-2 rounded-lg bg-white/5 border border-white/10 focus:border-white/20 focus:outline-none w-40"
                    style={{ color: 'var(--text-primary)' }}
                  />
                </div>
                <GlassButton
                  onClick={() => loadAnalysis()}
                  size="sm"
                  icon={<RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />}
                  disabled={loading}
                >
                  Refresh
                </GlassButton>
              </div>
            </div>

            {tickersLoading ? (
              <div className="flex justify-center py-8">
                <LoadingSpinner />
              </div>
            ) : (
              <div className="grid grid-cols-4 sm:grid-cols-6 md:grid-cols-8 lg:grid-cols-10 gap-2">
                {filteredTickers.slice(0, 20).map((ticker) => (
                  <button
                    key={ticker}
                    onClick={() => setSelectedTicker(ticker)}
                    disabled={loading && selectedTicker === ticker}
                    className={`p-2 rounded-lg transition-all text-center ${
                      selectedTicker === ticker
                        ? 'bg-purple-500/20 border border-purple-500/50'
                        : 'bg-white/5 border border-white/10 hover:bg-white/10'
                    } ${loading && selectedTicker === ticker ? 'opacity-75' : ''}`}
                  >
                    <div className={`font-bold text-sm flex items-center justify-center gap-1 ${
                      selectedTicker === ticker ? 'text-purple-400' : ''
                    }`} style={{ color: selectedTicker !== ticker ? 'var(--text-primary)' : undefined }}>
                      {ticker}
                      {loading && selectedTicker === ticker && (
                        <RefreshCw className="w-3 h-3 animate-spin" />
                      )}
                    </div>
                  </button>
                ))}
              </div>
            )}
          </GlassContainer>
        </motion.div>

        {/* Main Analysis Dashboard */}
        {aiAnalysis && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* Left Column - Stock Overview & AI Recommendation */}
            <motion.div
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 0.2 }}
              className="lg:col-span-1 space-y-6"
            >
              {/* Stock Overview Card */}
              <GlassContainer className="p-6">
                <div className="flex items-center justify-between mb-4">
                  <div>
                    <h2 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
                      {aiAnalysis.ticker}
                    </h2>
                    <p className="text-sm" style={{ color: 'var(--text-tertiary)' }}>
                      {aiAnalysis.fundamentals.marketCapCategory && aiAnalysis.fundamentals.marketCapCategory !== 'UNKNOWN'
                        ? aiAnalysis.fundamentals.marketCapCategory
                        : 'Stock Analysis'}
                    </p>
                  </div>
                  <div className={`p-3 rounded-lg ${getSignalBg(aiAnalysis.recommendation.action)}`}>
                    {getSignalIcon(aiAnalysis.recommendation.action)}
                  </div>
                </div>

                <div className="space-y-4">
                  {/* Price */}
                  <div className="bg-white/5 rounded-lg p-4">
                    <div className="text-sm" style={{ color: 'var(--text-secondary)' }}>Current Price</div>
                    <div className="flex items-baseline gap-2">
                      <span className="text-3xl font-bold" style={{ color: 'var(--text-primary)' }}>
                        ${formatNumber(aiAnalysis.currentPrice)}
                      </span>
                      <span className={`text-sm ${aiAnalysis.priceChangePercent >= 0 ? 'text-green-500' : 'text-red-500'}`}>
                        {formatPercent(aiAnalysis.priceChangePercent)}
                      </span>
                    </div>
                  </div>

                  {/* AI Score */}
                  <div className="bg-white/5 rounded-lg p-4">
                    <div className="flex items-center justify-between">
                      <div>
                        <div className="text-sm" style={{ color: 'var(--text-secondary)' }}>AI Score</div>
                        <div className={`text-2xl font-bold ${
                          aiAnalysis.technicalSummary.compositeScore >= 65 ? 'text-green-500' :
                          aiAnalysis.technicalSummary.compositeScore >= 45 ? 'text-orange-500' : 'text-red-500'
                        }`}>
                          {formatNumber(aiAnalysis.technicalSummary.compositeScore)}
                        </div>
                      </div>
                      <div className="w-16 h-16">
                        <svg className="transform -rotate-90" viewBox="0 0 36 36">
                          <path
                            className="stroke-current text-white/10"
                            fill="none"
                            strokeWidth="3"
                            d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                          />
                          <path
                            className={`stroke-current ${
                              aiAnalysis.technicalSummary.compositeScore >= 65 ? 'text-green-500' :
                              aiAnalysis.technicalSummary.compositeScore >= 45 ? 'text-orange-500' : 'text-red-500'
                            }`}
                            fill="none"
                            strokeWidth="3"
                            strokeLinecap="round"
                            strokeDasharray={`${aiAnalysis.technicalSummary.compositeScore}, 100`}
                            d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                          />
                        </svg>
                      </div>
                    </div>
                  </div>

                  {/* Risk Level */}
                  <div className="bg-white/5 rounded-lg p-4">
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <AlertTriangle className={`w-5 h-5 ${getRiskColor(aiAnalysis.riskAssessment.overallRisk)}`} />
                        <span className="text-sm" style={{ color: 'var(--text-secondary)' }}>Risk Level</span>
                      </div>
                      <span className={`font-bold ${getRiskColor(aiAnalysis.riskAssessment.overallRisk)}`}>
                        {aiAnalysis.riskAssessment.overallRisk}
                      </span>
                    </div>
                  </div>
                </div>
              </GlassContainer>

              {/* AI Recommendation Card */}
              <GlassContainer className="p-6">
                <h3 className="text-lg font-semibold mb-4 flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
                  <Target className="w-5 h-5 text-purple-500" />
                  AI Recommendation
                </h3>

                {/* Action Badge */}
                <div className={`inline-flex items-center gap-2 px-4 py-2 rounded-lg font-bold text-lg mb-4 ${getActionColor(aiAnalysis.recommendation.action)}`}>
                  {getSignalIcon(aiAnalysis.recommendation.action)}
                  {aiAnalysis.recommendation.action.replace('_', ' ')}
                </div>

                <div className="space-y-3">
                  <div className="flex items-center justify-between text-sm">
                    <span style={{ color: 'var(--text-secondary)' }}>Confidence</span>
                    <span className="font-bold" style={{ color: 'var(--text-primary)' }}>
                      {formatNumber(aiAnalysis.recommendation.confidence)}%
                    </span>
                  </div>
                  <div className="flex items-center justify-between text-sm">
                    <span style={{ color: 'var(--text-secondary)' }}>Time Horizon</span>
                    <span className="font-bold" style={{ color: 'var(--text-primary)' }}>
                      {aiAnalysis.recommendation.timeHorizon.replace('_', ' ')}
                    </span>
                  </div>
                  <div className="flex items-center justify-between text-sm">
                    <span style={{ color: 'var(--text-secondary)' }}>R/R Ratio</span>
                    <span className="font-bold" style={{ color: 'var(--text-primary)' }}>
                      {formatNumber(aiAnalysis.recommendation.riskRewardRatio)}
                    </span>
                  </div>
                </div>

                {/* Price Targets */}
                <div className="mt-4 pt-4 border-t border-white/10 grid grid-cols-3 gap-2">
                  <div className="text-center p-2 rounded bg-red-500/10">
                    <div className="text-xs text-red-400">Stop Loss</div>
                    <div className="font-bold text-red-500">${formatNumber(aiAnalysis.recommendation.stopLossPrice)}</div>
                  </div>
                  <div className="text-center p-2 rounded bg-blue-500/10">
                    <div className="text-xs text-blue-400">Target 1</div>
                    <div className="font-bold text-blue-500">${formatNumber(aiAnalysis.recommendation.targetPrice1)}</div>
                  </div>
                  <div className="text-center p-2 rounded bg-green-500/10">
                    <div className="text-xs text-green-400">Target 2</div>
                    <div className="font-bold text-green-500">${formatNumber(aiAnalysis.recommendation.targetPrice2)}</div>
                  </div>
                </div>
              </GlassContainer>
            </motion.div>

            {/* Right Column - Detailed Analysis */}
            <motion.div
              initial={{ opacity: 0, x: 20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 0.3 }}
              className="lg:col-span-2 space-y-4"
            >
              {/* ML Prediction Section */}
              <GlassContainer className="overflow-hidden">
                <button
                  onClick={() => toggleSection('ml')}
                  className="w-full p-4 flex items-center justify-between hover:bg-white/5 transition-colors"
                >
                  <div className="flex items-center gap-3">
                    <Brain className="w-5 h-5 text-purple-500" />
                    <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>ML Prediction</span>
                    <span className={`px-2 py-0.5 rounded text-xs font-medium ${getSignalBg(aiAnalysis.mlPrediction.predictedDirection)} ${getSignalColor(aiAnalysis.mlPrediction.predictedDirection)}`}>
                      {aiAnalysis.mlPrediction.predictedDirection}
                    </span>
                  </div>
                  {expandedSection === 'ml' ? <ChevronUp className="w-5 h-5" /> : <ChevronDown className="w-5 h-5" />}
                </button>
                <AnimatePresence>
                  {expandedSection === 'ml' && (
                    <motion.div
                      initial={{ height: 0, opacity: 0 }}
                      animate={{ height: 'auto', opacity: 1 }}
                      exit={{ height: 0, opacity: 0 }}
                      className="border-t border-white/10"
                    >
                      <div className="p-4 space-y-4">
                        {/* Direction Probabilities */}
                        <div className="grid grid-cols-3 gap-4">
                          <div className="p-3 rounded-lg bg-green-500/10 border border-green-500/30">
                            <div className="flex items-center gap-1 text-xs text-green-500">
                              <ArrowUpRight className="w-3 h-3" /> Bullish
                            </div>
                            <div className="text-2xl font-bold text-green-500">
                              {(aiAnalysis.mlPrediction.bullishProbability * 100).toFixed(0)}%
                            </div>
                          </div>
                          <div className="p-3 rounded-lg bg-red-500/10 border border-red-500/30">
                            <div className="flex items-center gap-1 text-xs text-red-500">
                              <ArrowDownRight className="w-3 h-3" /> Bearish
                            </div>
                            <div className="text-2xl font-bold text-red-500">
                              {(aiAnalysis.mlPrediction.bearishProbability * 100).toFixed(0)}%
                            </div>
                          </div>
                          <div className="p-3 rounded-lg bg-white/5">
                            <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Confidence</div>
                            <div className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
                              {(aiAnalysis.mlPrediction.overallConfidence * 100).toFixed(0)}%
                            </div>
                          </div>
                        </div>

                        {/* Price Predictions */}
                        <div className="grid grid-cols-2 gap-4">
                          <div className="p-3 rounded-lg bg-white/5">
                            <div className="flex items-center justify-between">
                              <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>1-Day Prediction</div>
                              <div className="text-xs px-1.5 py-0.5 rounded bg-blue-500/20 text-blue-400">
                                {formatTradingDay(getNextTradingDay(1))}
                              </div>
                            </div>
                            <div className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
                              ${formatNumber(aiAnalysis.mlPrediction.predicted1Day)}
                            </div>
                            <div className={`text-sm ${aiAnalysis.mlPrediction.return1Day >= 0 ? 'text-green-500' : 'text-red-500'}`}>
                              {formatPercent(aiAnalysis.mlPrediction.return1Day)}
                            </div>
                          </div>
                          <div className="p-3 rounded-lg bg-white/5">
                            <div className="flex items-center justify-between">
                              <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>1-Week Prediction</div>
                              <div className="text-xs px-1.5 py-0.5 rounded bg-purple-500/20 text-purple-400">
                                {formatTradingDay(getNextTradingDay(5))}
                              </div>
                            </div>
                            <div className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
                              ${formatNumber(aiAnalysis.mlPrediction.predicted1Week)}
                            </div>
                            <div className={`text-sm ${aiAnalysis.mlPrediction.return1Week >= 0 ? 'text-green-500' : 'text-red-500'}`}>
                              {formatPercent(aiAnalysis.mlPrediction.return1Week)}
                            </div>
                          </div>
                        </div>

                        {/* Feature Contributions */}
                        <div className="p-3 rounded-lg bg-white/5">
                          <div className="text-xs mb-2" style={{ color: 'var(--text-secondary)' }}>Feature Contributions</div>
                          <div className="grid grid-cols-3 gap-2">
                            {Object.entries(aiAnalysis.mlPrediction.featureContributions).map(([key, value]) => (
                              <div key={key} className="flex items-center justify-between text-xs">
                                <span style={{ color: 'var(--text-tertiary)' }}>{key}</span>
                                <span className={value >= 0 ? 'text-green-500' : 'text-red-500'}>
                                  {value >= 0 ? '+' : ''}{value.toFixed(2)}
                                </span>
                              </div>
                            ))}
                          </div>
                        </div>
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>
              </GlassContainer>

              {/* Backtest Results Section */}
              <GlassContainer className="overflow-hidden">
                <button
                  onClick={() => toggleSection('backtest')}
                  className="w-full p-4 flex items-center justify-between hover:bg-white/5 transition-colors"
                >
                  <div className="flex items-center gap-3">
                    <BarChart2 className="w-5 h-5 text-blue-500" />
                    <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>Backtest Results</span>
                    <span className="px-2 py-0.5 rounded text-xs font-medium bg-blue-500/20 text-blue-400">
                      {aiAnalysis.backtestSummary.daysAnalyzed} days
                    </span>
                  </div>
                  {expandedSection === 'backtest' ? <ChevronUp className="w-5 h-5" /> : <ChevronDown className="w-5 h-5" />}
                </button>
                <AnimatePresence>
                  {expandedSection === 'backtest' && (
                    <motion.div
                      initial={{ height: 0, opacity: 0 }}
                      animate={{ height: 'auto', opacity: 1 }}
                      exit={{ height: 0, opacity: 0 }}
                      className="border-t border-white/10"
                    >
                      <div className="p-4 space-y-4">
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                          <div className="p-3 rounded-lg bg-white/5">
                            <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Direction Accuracy</div>
                            <div className={`text-xl font-bold ${aiAnalysis.backtestSummary.directionAccuracy >= 55 ? 'text-green-500' : 'text-red-500'}`}>
                              {formatNumber(aiAnalysis.backtestSummary.directionAccuracy)}%
                            </div>
                          </div>
                          <div className="p-3 rounded-lg bg-white/5">
                            <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Win Rate</div>
                            <div className={`text-xl font-bold ${aiAnalysis.backtestSummary.winRate >= 50 ? 'text-green-500' : 'text-red-500'}`}>
                              {formatNumber(aiAnalysis.backtestSummary.winRate)}%
                            </div>
                          </div>
                          <div className="p-3 rounded-lg bg-white/5">
                            <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Profit Factor</div>
                            <div className={`text-xl font-bold ${aiAnalysis.backtestSummary.profitFactor >= 1 ? 'text-green-500' : 'text-red-500'}`}>
                              {formatNumber(aiAnalysis.backtestSummary.profitFactor)}
                            </div>
                          </div>
                          <div className="p-3 rounded-lg bg-white/5">
                            <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Sharpe Ratio</div>
                            <div className={`text-xl font-bold ${aiAnalysis.backtestSummary.sharpeRatio >= 0 ? 'text-green-500' : 'text-red-500'}`}>
                              {formatNumber(aiAnalysis.backtestSummary.sharpeRatio)}
                            </div>
                          </div>
                        </div>

                        <div className="grid grid-cols-2 gap-4">
                          <div className="p-3 rounded-lg bg-red-500/10 border border-red-500/30">
                            <div className="flex items-center gap-1 text-xs text-red-500">
                              <AlertTriangle className="w-3 h-3" /> Max Drawdown
                            </div>
                            <div className="text-lg font-bold text-red-500">
                              -{formatNumber(aiAnalysis.backtestSummary.maxDrawdown)}%
                            </div>
                          </div>
                          <div className="p-3 rounded-lg bg-blue-500/10 border border-blue-500/30">
                            <div className="flex items-center gap-1 text-xs text-blue-500">
                              <TrendUp className="w-3 h-3" /> Buy & Hold Return
                            </div>
                            <div className={`text-lg font-bold ${aiAnalysis.backtestSummary.buyHoldReturn >= 0 ? 'text-green-500' : 'text-red-500'}`}>
                              {formatPercent(aiAnalysis.backtestSummary.buyHoldReturn)}
                            </div>
                          </div>
                        </div>
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>
              </GlassContainer>

              {/* Technical Indicators Section */}
              <GlassContainer className="overflow-hidden">
                <button
                  onClick={() => toggleSection('technicals')}
                  className="w-full p-4 flex items-center justify-between hover:bg-white/5 transition-colors"
                >
                  <div className="flex items-center gap-3">
                    <LineChart className="w-5 h-5 text-cyan-500" />
                    <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>Technical Indicators</span>
                  </div>
                  {expandedSection === 'technicals' ? <ChevronUp className="w-5 h-5" /> : <ChevronDown className="w-5 h-5" />}
                </button>
                <AnimatePresence>
                  {expandedSection === 'technicals' && (
                    <motion.div
                      initial={{ height: 0, opacity: 0 }}
                      animate={{ height: 'auto', opacity: 1 }}
                      exit={{ height: 0, opacity: 0 }}
                      className="border-t border-white/10"
                    >
                      <div className="p-4 space-y-4">
                        {/* Score Breakdown */}
                        <div className="grid grid-cols-4 gap-4">
                          <div className="p-3 rounded-lg bg-white/5">
                            <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Trend</div>
                            <div className="text-xl font-bold" style={{ color: 'var(--text-primary)' }}>
                              {formatNumber(aiAnalysis.technicalSummary.trendScore)}
                            </div>
                          </div>
                          <div className="p-3 rounded-lg bg-white/5">
                            <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Momentum</div>
                            <div className="text-xl font-bold" style={{ color: 'var(--text-primary)' }}>
                              {formatNumber(aiAnalysis.technicalSummary.momentumScore)}
                            </div>
                          </div>
                          <div className="p-3 rounded-lg bg-white/5">
                            <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Volume</div>
                            <div className="text-xl font-bold" style={{ color: 'var(--text-primary)' }}>
                              {formatNumber(aiAnalysis.technicalSummary.volumeScore)}
                            </div>
                          </div>
                          <div className="p-3 rounded-lg bg-white/5">
                            <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Volatility</div>
                            <div className="text-xl font-bold" style={{ color: 'var(--text-primary)' }}>
                              {formatNumber(aiAnalysis.technicalSummary.volatilityScore)}
                            </div>
                          </div>
                        </div>

                        {/* Key Indicators */}
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                          <div className="p-3 rounded-lg bg-white/5">
                            <div className="flex items-center gap-1 text-xs" style={{ color: 'var(--text-secondary)' }}>
                              <Activity className="w-3 h-3" /> RSI
                            </div>
                            <div className={`text-xl font-bold ${
                              aiAnalysis.technicalSummary.rsi > 70 ? 'text-red-500' :
                              aiAnalysis.technicalSummary.rsi < 30 ? 'text-green-500' : ''
                            }`} style={{ color: aiAnalysis.technicalSummary.rsi >= 30 && aiAnalysis.technicalSummary.rsi <= 70 ? 'var(--text-primary)' : undefined }}>
                              {formatNumber(aiAnalysis.technicalSummary.rsi)}
                            </div>
                            <div className="text-xs" style={{ color: 'var(--text-tertiary)' }}>
                              {aiAnalysis.technicalSummary.rsiSignal}
                            </div>
                          </div>
                          <div className="p-3 rounded-lg bg-white/5">
                            <div className="flex items-center gap-1 text-xs" style={{ color: 'var(--text-secondary)' }}>
                              <TrendUp className="w-3 h-3" /> Supertrend
                            </div>
                            <div className={`text-xl font-bold ${aiAnalysis.technicalSummary.supertrendBullish ? 'text-green-500' : 'text-red-500'}`}>
                              {aiAnalysis.technicalSummary.supertrendBullish ? 'Bullish' : 'Bearish'}
                            </div>
                          </div>
                          <div className="p-3 rounded-lg bg-white/5">
                            <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Ichimoku</div>
                            <div className={`text-xl font-bold ${getSignalColor(aiAnalysis.technicalSummary.ichimokuSignal)}`}>
                              {aiAnalysis.technicalSummary.ichimokuSignal}
                            </div>
                          </div>
                          <div className="p-3 rounded-lg bg-white/5">
                            <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>ATR %</div>
                            <div className="text-xl font-bold" style={{ color: 'var(--text-primary)' }}>
                              {formatNumber(aiAnalysis.technicalSummary.atrPercent)}%
                            </div>
                          </div>
                        </div>

                        {/* Key Levels */}
                        <div className="grid grid-cols-3 gap-4">
                          <div className="p-3 rounded-lg bg-green-500/10 border border-green-500/30">
                            <div className="flex items-center gap-1 text-xs text-green-500">
                              <Shield className="w-3 h-3" /> Support
                            </div>
                            <div className="text-lg font-bold text-green-500">
                              ${formatNumber(aiAnalysis.technicalSummary.supportLevel)}
                            </div>
                          </div>
                          <div className="p-3 rounded-lg bg-blue-500/10 border border-blue-500/30">
                            <div className="flex items-center gap-1 text-xs text-blue-500">
                              <Target className="w-3 h-3" /> Pivot
                            </div>
                            <div className="text-lg font-bold text-blue-500">
                              ${formatNumber(aiAnalysis.technicalSummary.pivotPoint)}
                            </div>
                          </div>
                          <div className="p-3 rounded-lg bg-red-500/10 border border-red-500/30">
                            <div className="flex items-center gap-1 text-xs text-red-500">
                              <TrendingUp className="w-3 h-3" /> Resistance
                            </div>
                            <div className="text-lg font-bold text-red-500">
                              ${formatNumber(aiAnalysis.technicalSummary.resistanceLevel)}
                            </div>
                          </div>
                        </div>

                        {/* Signals List */}
                        {aiAnalysis.technicalSummary.signals.length > 0 && (
                          <div className="p-3 rounded-lg bg-white/5">
                            <div className="text-xs mb-2" style={{ color: 'var(--text-secondary)' }}>Active Signals</div>
                            <div className="flex flex-wrap gap-2">
                              {aiAnalysis.technicalSummary.signals.slice(0, 8).map((signal, idx) => (
                                <span key={idx} className="px-2 py-1 rounded text-xs bg-white/10" style={{ color: 'var(--text-primary)' }}>
                                  {signal}
                                </span>
                              ))}
                            </div>
                          </div>
                        )}
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>
              </GlassContainer>

              {/* AI Reasoning Section */}
              <GlassContainer className="overflow-hidden">
                <button
                  onClick={() => toggleSection('recommendation')}
                  className="w-full p-4 flex items-center justify-between hover:bg-white/5 transition-colors"
                >
                  <div className="flex items-center gap-3">
                    <Zap className="w-5 h-5 text-yellow-500" />
                    <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>AI Reasoning & Factors</span>
                  </div>
                  {expandedSection === 'recommendation' ? <ChevronUp className="w-5 h-5" /> : <ChevronDown className="w-5 h-5" />}
                </button>
                <AnimatePresence>
                  {expandedSection === 'recommendation' && (
                    <motion.div
                      initial={{ height: 0, opacity: 0 }}
                      animate={{ height: 'auto', opacity: 1 }}
                      exit={{ height: 0, opacity: 0 }}
                      className="border-t border-white/10"
                    >
                      <div className="p-4 space-y-4">
                        {/* Reasoning */}
                        <div className="p-3 rounded-lg bg-white/5">
                          <div className="text-sm" style={{ color: 'var(--text-secondary)' }}>
                            {aiAnalysis.recommendation.reasoning}
                          </div>
                        </div>

                        {/* Bullish Factors */}
                        {aiAnalysis.recommendation.bullishFactors.length > 0 && (
                          <div className="p-3 rounded-lg bg-green-500/10 border border-green-500/30">
                            <div className="flex items-center gap-2 text-sm text-green-500 mb-2">
                              <CheckCircle className="w-4 h-4" />
                              Bullish Factors
                            </div>
                            <ul className="space-y-1">
                              {aiAnalysis.recommendation.bullishFactors.map((factor, idx) => (
                                <li key={idx} className="text-sm text-green-400 flex items-center gap-2">
                                  <span className="w-1 h-1 bg-green-500 rounded-full" />
                                  {factor}
                                </li>
                              ))}
                            </ul>
                          </div>
                        )}

                        {/* Bearish Factors */}
                        {aiAnalysis.recommendation.bearishFactors.length > 0 && (
                          <div className="p-3 rounded-lg bg-red-500/10 border border-red-500/30">
                            <div className="flex items-center gap-2 text-sm text-red-500 mb-2">
                              <XCircle className="w-4 h-4" />
                              Bearish Factors
                            </div>
                            <ul className="space-y-1">
                              {aiAnalysis.recommendation.bearishFactors.map((factor, idx) => (
                                <li key={idx} className="text-sm text-red-400 flex items-center gap-2">
                                  <span className="w-1 h-1 bg-red-500 rounded-full" />
                                  {factor}
                                </li>
                              ))}
                            </ul>
                          </div>
                        )}

                        {/* Key Risks */}
                        {aiAnalysis.recommendation.keyRisks.length > 0 && (
                          <div className="p-3 rounded-lg bg-orange-500/10 border border-orange-500/30">
                            <div className="flex items-center gap-2 text-sm text-orange-500 mb-2">
                              <AlertCircle className="w-4 h-4" />
                              Key Risks
                            </div>
                            <ul className="space-y-1">
                              {aiAnalysis.recommendation.keyRisks.map((risk, idx) => (
                                <li key={idx} className="text-sm text-orange-400 flex items-center gap-2">
                                  <span className="w-1 h-1 bg-orange-500 rounded-full" />
                                  {risk}
                                </li>
                              ))}
                            </ul>
                          </div>
                        )}
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>
              </GlassContainer>

              {/* Sentiment Analysis Section */}
              <GlassContainer className="overflow-hidden">
                <button
                  onClick={() => toggleSection('sentiment')}
                  className="w-full p-4 flex items-center justify-between hover:bg-white/5 transition-colors"
                >
                  <div className="flex items-center gap-3">
                    <Newspaper className="w-5 h-5 text-purple-500" />
                    <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>News Sentiment</span>
                    {newsArticles.length > 0 && (
                      <span className="px-2 py-0.5 rounded text-xs font-medium bg-purple-500/20 text-purple-400">
                        {newsArticles.length} articles
                      </span>
                    )}
                  </div>
                  {expandedSection === 'sentiment' ? <ChevronUp className="w-5 h-5" /> : <ChevronDown className="w-5 h-5" />}
                </button>
                <AnimatePresence>
                  {expandedSection === 'sentiment' && (
                    <motion.div
                      initial={{ height: 0, opacity: 0 }}
                      animate={{ height: 'auto', opacity: 1 }}
                      exit={{ height: 0, opacity: 0 }}
                      className="border-t border-white/10"
                    >
                      <div className="p-4 space-y-4">
                        {sentimentData ? (
                          <>
                            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                              <div className={`p-3 rounded-lg ${
                                sentimentData.averageSentiment > 0.1 ? 'bg-green-500/10 border border-green-500/30' :
                                sentimentData.averageSentiment < -0.1 ? 'bg-red-500/10 border border-red-500/30' :
                                'bg-orange-500/10 border border-orange-500/30'
                              }`}>
                                <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Sentiment</div>
                                <div className={`text-lg font-bold ${
                                  sentimentData.averageSentiment > 0.1 ? 'text-green-500' :
                                  sentimentData.averageSentiment < -0.1 ? 'text-red-500' : 'text-orange-500'
                                }`}>
                                  {sentimentData.averageSentiment > 0.3 ? 'Very Bullish' :
                                   sentimentData.averageSentiment > 0.1 ? 'Bullish' :
                                   sentimentData.averageSentiment < -0.3 ? 'Very Bearish' :
                                   sentimentData.averageSentiment < -0.1 ? 'Bearish' : 'Neutral'}
                                </div>
                              </div>
                              <div className="p-3 rounded-lg bg-white/5">
                                <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Score</div>
                                <div className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
                                  {(sentimentData.averageSentiment * 100).toFixed(1)}%
                                </div>
                              </div>
                              <div className="p-3 rounded-lg bg-white/5">
                                <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>News Articles</div>
                                <div className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
                                  {sentimentData.newsCount}
                                </div>
                              </div>
                              <div className="p-3 rounded-lg bg-white/5">
                                <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Confidence</div>
                                <div className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
                                  {(sentimentData.confidence * 100).toFixed(0)}%
                                </div>
                              </div>
                            </div>

                            {/* News Articles Section */}
                            <div className="pt-4 border-t border-white/10">
                              <div className="flex items-center justify-between mb-3">
                                <h4 className="text-sm font-semibold" style={{ color: 'var(--text-primary)' }}>
                                  Recent News Articles
                                </h4>
                                <GlassButton
                                  onClick={() => loadNews()}
                                  size="sm"
                                  disabled={newsLoading}
                                  icon={newsLoading ? <RefreshCw className="w-3 h-3 animate-spin" /> : <Newspaper className="w-3 h-3" />}
                                >
                                  {newsLoading ? 'Loading...' : newsArticles.length > 0 ? 'Refresh' : 'Load News'}
                                </GlassButton>
                              </div>

                              {newsLoading ? (
                                <div className="flex justify-center py-4">
                                  <LoadingSpinner />
                                </div>
                              ) : newsArticles.length > 0 ? (
                                <div className="space-y-2 max-h-80 overflow-y-auto">
                                  {newsArticles.map((article) => (
                                    <a
                                      key={article.id}
                                      href={article.url}
                                      target="_blank"
                                      rel="noopener noreferrer"
                                      className="block p-3 rounded-lg bg-white/5 hover:bg-white/10 transition-colors"
                                    >
                                      <div className="flex items-start justify-between gap-2">
                                        <div className="flex-1 min-w-0">
                                          <h5 className="text-sm font-medium line-clamp-2" style={{ color: 'var(--text-primary)' }}>
                                            {article.title}
                                          </h5>
                                          <div className="flex items-center gap-2 mt-1">
                                            <span className="text-xs px-2 py-0.5 rounded bg-white/10" style={{ color: 'var(--text-tertiary)' }}>
                                              {article.source}
                                            </span>
                                            <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>
                                              {new Date(article.publishedAt).toLocaleDateString('en-US', {
                                                month: 'short',
                                                day: 'numeric',
                                                hour: '2-digit',
                                                minute: '2-digit'
                                              })}
                                            </span>
                                          </div>
                                        </div>
                                        <ArrowUpRight className="w-4 h-4 flex-shrink-0 text-purple-400" />
                                      </div>
                                    </a>
                                  ))}
                                </div>
                              ) : (
                                <p className="text-sm text-center py-4" style={{ color: 'var(--text-tertiary)' }}>
                                  Click "Load News" to view recent articles
                                </p>
                              )}
                            </div>
                          </>
                        ) : (
                          <div className="text-center py-4">
                            <GlassButton
                              onClick={() => loadSentiment()}
                              disabled={sentimentLoading}
                              icon={sentimentLoading ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Brain className="w-4 h-4" />}
                            >
                              {sentimentLoading ? 'Analyzing...' : 'Analyze Sentiment'}
                            </GlassButton>
                            <p className="text-sm mt-2" style={{ color: 'var(--text-tertiary)' }}>
                              Click to run LLM-powered news sentiment analysis
                            </p>
                          </div>
                        )}
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>
              </GlassContainer>
            </motion.div>
          </div>
        )}

        {/* Loading State */}
        {!loading && !aiAnalysis && selectedTicker && (
          <GlassContainer className="p-6 text-center">
            <p style={{ color: 'var(--text-secondary)' }}>Select a ticker to view AI analysis</p>
          </GlassContainer>
        )}

        {/* Error State */}
        {error && (
          <GlassContainer className="p-6 text-center">
            <p className="text-red-500 mb-4">{error}</p>
            <GlassButton onClick={() => loadAnalysis()} variant="secondary">
              Try Again
            </GlassButton>
          </GlassContainer>
        )}
      </div>
    </div>
  );
};

export default AIAnalysis;
