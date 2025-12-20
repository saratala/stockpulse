import React, { useState, useEffect, useCallback } from 'react';
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
  Clock
} from 'lucide-react';
import GlassContainer from '../components/GlassContainer';
import GlassButton from '../components/GlassButton';
import LoadingSpinner from '../components/LoadingSpinner';
import {
  fetchScreeningResults,
  fetchAllTickers,
  fetchLatestStockPrice,
  analyzeSentimentForTicker,
  fetchSentimentSummary,
  fetchSentimentTrend
} from '../services/api';
import '../styles/glassmorphism.css';

interface StockAnalysis {
  ticker: string;
  name: string;
  sector: string;
  price: number;
  screeningScore: number;
  changePercent?: number;
  rsi?: number;
  adxStrength?: number;
  volumeRatio?: number;
  emaStackAligned?: boolean;
  stochPosition?: number;
  signalAnalysis?: {
    primarySignal: 'BULLISH' | 'BEARISH' | 'NEUTRAL';
    primaryConfidence: number;
    reversalSignal?: string;
    reversalConfidence?: number;
    riskLevel?: string;
    keyLevels?: {
      support: number;
      resistance: number;
      pivot: number;
    };
    primaryReasons?: string[];
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

const AIAnalysis: React.FC = () => {
  const [selectedTicker, setSelectedTicker] = useState<string>('');
  const [tickers, setTickers] = useState<string[]>([]);
  const [tickersLoading, setTickersLoading] = useState(true);
  const [stockData, setStockData] = useState<StockAnalysis | null>(null);
  const [sentimentData, setSentimentData] = useState<SentimentData | null>(null);
  const [allStocks, setAllStocks] = useState<StockAnalysis[]>([]);
  const [loading, setLoading] = useState(false);
  const [sentimentLoading, setSentimentLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [searchTerm, setSearchTerm] = useState('');
  const [expandedSection, setExpandedSection] = useState<string | null>('signals');
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  // Load tickers on mount
  useEffect(() => {
    const loadTickers = async () => {
      try {
        const tickerList = await fetchAllTickers();
        if (tickerList && tickerList.length > 0) {
          setTickers(tickerList);
          setSelectedTicker(tickerList[0]);
        }
      } catch (err) {
        console.error('Error fetching tickers:', err);
      } finally {
        setTickersLoading(false);
      }
    };
    loadTickers();
  }, []);

  // Load all stock analysis data
  const loadAllAnalysis = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await fetchScreeningResults({
        minScore: 0,
        maxResults: 100,
        includeSignals: true
      });
      if (response?.candidates) {
        setAllStocks(response.candidates);
        setLastUpdated(new Date());
      }
    } catch (err) {
      setError('Failed to load analysis data');
      console.error('Error:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadAllAnalysis();
  }, [loadAllAnalysis]);

  // Load specific stock data when ticker changes
  useEffect(() => {
    if (selectedTicker && allStocks.length > 0) {
      const stock = allStocks.find(s => s.ticker === selectedTicker);
      setStockData(stock || null);
    }
  }, [selectedTicker, allStocks]);

  // Load sentiment for selected ticker
  const loadSentiment = async () => {
    if (!selectedTicker) return;
    try {
      setSentimentLoading(true);
      const result = await analyzeSentimentForTicker(selectedTicker);
      setSentimentData(result);
    } catch (err) {
      console.error('Error loading sentiment:', err);
    } finally {
      setSentimentLoading(false);
    }
  };

  const getSignalColor = (signal?: string) => {
    switch (signal) {
      case 'BULLISH': return 'text-green-500';
      case 'BEARISH': return 'text-red-500';
      default: return 'text-orange-500';
    }
  };

  const getSignalBg = (signal?: string) => {
    switch (signal) {
      case 'BULLISH': return 'bg-green-500/10 border-green-500/30';
      case 'BEARISH': return 'bg-red-500/10 border-red-500/30';
      default: return 'bg-orange-500/10 border-orange-500/30';
    }
  };

  const getSignalIcon = (signal?: string) => {
    switch (signal) {
      case 'BULLISH': return <TrendingUp className="w-5 h-5" />;
      case 'BEARISH': return <TrendingDown className="w-5 h-5" />;
      default: return <Minus className="w-5 h-5" />;
    }
  };

  const getRiskColor = (risk?: string) => {
    switch (risk?.toUpperCase()) {
      case 'LOW': return 'text-green-500';
      case 'MEDIUM': return 'text-yellow-500';
      case 'HIGH': return 'text-red-500';
      default: return 'text-gray-500';
    }
  };

  const getSentimentColor = (sentiment: number) => {
    if (sentiment > 0.1) return 'text-green-500';
    if (sentiment < -0.1) return 'text-red-500';
    return 'text-orange-500';
  };

  const getSentimentLabel = (sentiment: number) => {
    if (sentiment > 0.3) return 'Very Bullish';
    if (sentiment > 0.1) return 'Bullish';
    if (sentiment > 0.01) return 'Slightly Bullish';
    if (sentiment < -0.3) return 'Very Bearish';
    if (sentiment < -0.1) return 'Bearish';
    if (sentiment < -0.01) return 'Slightly Bearish';
    return 'Neutral';
  };

  const filteredTickers = tickers.filter(t =>
    t.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const toggleSection = (section: string) => {
    setExpandedSection(expandedSection === section ? null : section);
  };

  return (
    <div className="min-h-screen p-6" style={{ background: 'var(--bg-primary)' }}>
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
              Comprehensive AI-powered stock analysis combining signals, technicals & sentiment
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
                  onClick={loadAllAnalysis}
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
                {filteredTickers.slice(0, 20).map((ticker) => {
                  const stock = allStocks.find(s => s.ticker === ticker);
                  const signal = stock?.signalAnalysis?.primarySignal;
                  return (
                    <button
                      key={ticker}
                      onClick={() => setSelectedTicker(ticker)}
                      className={`p-2 rounded-lg transition-all text-center ${
                        selectedTicker === ticker
                          ? 'bg-purple-500/20 border border-purple-500/50'
                          : 'bg-white/5 border border-white/10 hover:bg-white/10'
                      }`}
                    >
                      <div className={`font-bold text-sm ${
                        selectedTicker === ticker ? 'text-purple-400' : ''
                      }`} style={{ color: selectedTicker !== ticker ? 'var(--text-primary)' : undefined }}>
                        {ticker}
                      </div>
                      {stock && (
                        <div className={`text-xs ${getSignalColor(signal)}`}>
                          {signal?.slice(0, 4) || 'N/A'}
                        </div>
                      )}
                    </button>
                  );
                })}
              </div>
            )}
          </GlassContainer>
        </motion.div>

        {/* Main Analysis Dashboard */}
        {stockData && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* Left Column - Stock Overview */}
            <motion.div
              initial={{ opacity: 0, x: -20 }}
              animate={{ opacity: 1, x: 0 }}
              transition={{ delay: 0.2 }}
              className="lg:col-span-1"
            >
              <GlassContainer className="p-6 h-full">
                <div className="flex items-center justify-between mb-4">
                  <div>
                    <h2 className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
                      {stockData.ticker}
                    </h2>
                    <p className="text-sm" style={{ color: 'var(--text-secondary)' }}>
                      {stockData.name}
                    </p>
                    <p className="text-xs" style={{ color: 'var(--text-tertiary)' }}>
                      {stockData.sector}
                    </p>
                  </div>
                  <div className={`p-3 rounded-lg ${getSignalBg(stockData.signalAnalysis?.primarySignal)}`}>
                    {getSignalIcon(stockData.signalAnalysis?.primarySignal)}
                  </div>
                </div>

                <div className="space-y-4">
                  {/* Price */}
                  <div className="bg-white/5 rounded-lg p-4">
                    <div className="text-sm" style={{ color: 'var(--text-secondary)' }}>Current Price</div>
                    <div className="flex items-baseline gap-2">
                      <span className="text-3xl font-bold" style={{ color: 'var(--text-primary)' }}>
                        ${stockData.price?.toFixed(2)}
                      </span>
                      {stockData.changePercent !== undefined && (
                        <span className={`text-sm ${stockData.changePercent >= 0 ? 'text-green-500' : 'text-red-500'}`}>
                          {stockData.changePercent >= 0 ? '+' : ''}{stockData.changePercent.toFixed(2)}%
                        </span>
                      )}
                    </div>
                  </div>

                  {/* Score */}
                  <div className="bg-white/5 rounded-lg p-4">
                    <div className="flex items-center justify-between">
                      <div>
                        <div className="text-sm" style={{ color: 'var(--text-secondary)' }}>AI Score</div>
                        <div className={`text-2xl font-bold ${
                          stockData.screeningScore >= 80 ? 'text-green-500' :
                          stockData.screeningScore >= 60 ? 'text-orange-500' : 'text-red-500'
                        }`}>
                          {stockData.screeningScore?.toFixed(1)}
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
                              stockData.screeningScore >= 80 ? 'text-green-500' :
                              stockData.screeningScore >= 60 ? 'text-orange-500' : 'text-red-500'
                            }`}
                            fill="none"
                            strokeWidth="3"
                            strokeLinecap="round"
                            strokeDasharray={`${stockData.screeningScore}, 100`}
                            d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831"
                          />
                        </svg>
                      </div>
                    </div>
                  </div>

                  {/* Risk Level */}
                  {stockData.signalAnalysis?.riskLevel && (
                    <div className="bg-white/5 rounded-lg p-4">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <AlertTriangle className={`w-5 h-5 ${getRiskColor(stockData.signalAnalysis.riskLevel)}`} />
                          <span className="text-sm" style={{ color: 'var(--text-secondary)' }}>Risk Level</span>
                        </div>
                        <span className={`font-bold ${getRiskColor(stockData.signalAnalysis.riskLevel)}`}>
                          {stockData.signalAnalysis.riskLevel}
                        </span>
                      </div>
                    </div>
                  )}
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
              {/* Signal Analysis Section */}
              <GlassContainer className="overflow-hidden">
                <button
                  onClick={() => toggleSection('signals')}
                  className="w-full p-4 flex items-center justify-between hover:bg-white/5 transition-colors"
                >
                  <div className="flex items-center gap-3">
                    <Zap className="w-5 h-5 text-yellow-500" />
                    <span className="font-semibold" style={{ color: 'var(--text-primary)' }}>Signal Analysis</span>
                  </div>
                  {expandedSection === 'signals' ? <ChevronUp className="w-5 h-5" /> : <ChevronDown className="w-5 h-5" />}
                </button>
                <AnimatePresence>
                  {expandedSection === 'signals' && stockData.signalAnalysis && (
                    <motion.div
                      initial={{ height: 0, opacity: 0 }}
                      animate={{ height: 'auto', opacity: 1 }}
                      exit={{ height: 0, opacity: 0 }}
                      className="border-t border-white/10"
                    >
                      <div className="p-4 space-y-4">
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                          <div className={`p-3 rounded-lg ${getSignalBg(stockData.signalAnalysis.primarySignal)}`}>
                            <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Primary Signal</div>
                            <div className={`text-lg font-bold ${getSignalColor(stockData.signalAnalysis.primarySignal)}`}>
                              {stockData.signalAnalysis.primarySignal}
                            </div>
                          </div>
                          <div className="p-3 rounded-lg bg-white/5">
                            <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Confidence</div>
                            <div className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
                              {stockData.signalAnalysis.primaryConfidence?.toFixed(1)}%
                            </div>
                          </div>
                          <div className="p-3 rounded-lg bg-white/5">
                            <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Reversal Signal</div>
                            <div className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
                              {stockData.signalAnalysis.reversalSignal === 'NO_REVERSAL' ? 'None' : stockData.signalAnalysis.reversalSignal}
                            </div>
                          </div>
                          <div className="p-3 rounded-lg bg-white/5">
                            <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Reversal Conf.</div>
                            <div className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
                              {stockData.signalAnalysis.reversalConfidence?.toFixed(1)}%
                            </div>
                          </div>
                        </div>

                        {/* Key Levels */}
                        {stockData.signalAnalysis.keyLevels && (
                          <div className="grid grid-cols-3 gap-4">
                            <div className="p-3 rounded-lg bg-green-500/10 border border-green-500/30">
                              <div className="flex items-center gap-1 text-xs text-green-500">
                                <Shield className="w-3 h-3" /> Support
                              </div>
                              <div className="text-lg font-bold text-green-500">
                                ${stockData.signalAnalysis.keyLevels.support?.toFixed(2)}
                              </div>
                            </div>
                            <div className="p-3 rounded-lg bg-blue-500/10 border border-blue-500/30">
                              <div className="flex items-center gap-1 text-xs text-blue-500">
                                <Target className="w-3 h-3" /> Pivot
                              </div>
                              <div className="text-lg font-bold text-blue-500">
                                ${stockData.signalAnalysis.keyLevels.pivot?.toFixed(2)}
                              </div>
                            </div>
                            <div className="p-3 rounded-lg bg-red-500/10 border border-red-500/30">
                              <div className="flex items-center gap-1 text-xs text-red-500">
                                <TrendingUp className="w-3 h-3" /> Resistance
                              </div>
                              <div className="text-lg font-bold text-red-500">
                                ${stockData.signalAnalysis.keyLevels.resistance?.toFixed(2)}
                              </div>
                            </div>
                          </div>
                        )}
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
                    <LineChart className="w-5 h-5 text-blue-500" />
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
                      <div className="p-4">
                        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                          <div className="p-3 rounded-lg bg-white/5">
                            <div className="flex items-center gap-1 text-xs" style={{ color: 'var(--text-secondary)' }}>
                              <Activity className="w-3 h-3" /> RSI
                            </div>
                            <div className={`text-xl font-bold ${
                              (stockData.rsi ?? 0) > 70 ? 'text-red-500' :
                              (stockData.rsi ?? 0) < 30 ? 'text-green-500' : ''
                            }`} style={{ color: (stockData.rsi ?? 50) >= 30 && (stockData.rsi ?? 50) <= 70 ? 'var(--text-primary)' : undefined }}>
                              {stockData.rsi?.toFixed(1) || 'N/A'}
                            </div>
                            <div className="text-xs" style={{ color: 'var(--text-tertiary)' }}>
                              {(stockData.rsi ?? 50) > 70 ? 'Overbought' : (stockData.rsi ?? 50) < 30 ? 'Oversold' : 'Neutral'}
                            </div>
                          </div>
                          <div className="p-3 rounded-lg bg-white/5">
                            <div className="flex items-center gap-1 text-xs" style={{ color: 'var(--text-secondary)' }}>
                              <BarChart3 className="w-3 h-3" /> ADX Strength
                            </div>
                            <div className="text-xl font-bold" style={{ color: 'var(--text-primary)' }}>
                              {stockData.adxStrength?.toFixed(1) || 'N/A'}
                            </div>
                            <div className="text-xs" style={{ color: 'var(--text-tertiary)' }}>
                              {(stockData.adxStrength ?? 0) > 25 ? 'Strong Trend' : 'Weak Trend'}
                            </div>
                          </div>
                          <div className="p-3 rounded-lg bg-white/5">
                            <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Stochastic</div>
                            <div className="text-xl font-bold" style={{ color: 'var(--text-primary)' }}>
                              {stockData.stochPosition?.toFixed(1) || 'N/A'}
                            </div>
                            <div className="text-xs" style={{ color: 'var(--text-tertiary)' }}>
                              {(stockData.stochPosition ?? 50) > 80 ? 'Overbought' : (stockData.stochPosition ?? 50) < 20 ? 'Oversold' : 'Neutral'}
                            </div>
                          </div>
                          <div className="p-3 rounded-lg bg-white/5">
                            <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Volume Ratio</div>
                            <div className="text-xl font-bold" style={{ color: 'var(--text-primary)' }}>
                              {stockData.volumeRatio?.toFixed(2) || 'N/A'}x
                            </div>
                            <div className="text-xs" style={{ color: 'var(--text-tertiary)' }}>
                              {(stockData.volumeRatio ?? 1) > 1.5 ? 'High Volume' : (stockData.volumeRatio ?? 1) < 0.5 ? 'Low Volume' : 'Normal'}
                            </div>
                          </div>
                        </div>

                        {/* EMA Stack */}
                        <div className="mt-4 p-3 rounded-lg bg-white/5">
                          <div className="flex items-center justify-between">
                            <div className="flex items-center gap-2">
                              <TrendingUp className="w-4 h-4" style={{ color: 'var(--text-secondary)' }} />
                              <span className="text-sm" style={{ color: 'var(--text-secondary)' }}>EMA Stack Aligned</span>
                            </div>
                            <span className={`font-bold ${stockData.emaStackAligned ? 'text-green-500' : 'text-red-500'}`}>
                              {stockData.emaStackAligned ? 'Yes' : 'No'}
                            </span>
                          </div>
                        </div>
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
                      <div className="p-4">
                        {sentimentData ? (
                          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                            <div className={`p-3 rounded-lg ${
                              sentimentData.averageSentiment > 0.1 ? 'bg-green-500/10 border border-green-500/30' :
                              sentimentData.averageSentiment < -0.1 ? 'bg-red-500/10 border border-red-500/30' :
                              'bg-orange-500/10 border border-orange-500/30'
                            }`}>
                              <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Sentiment</div>
                              <div className={`text-lg font-bold ${getSentimentColor(sentimentData.averageSentiment)}`}>
                                {getSentimentLabel(sentimentData.averageSentiment)}
                              </div>
                            </div>
                            <div className="p-3 rounded-lg bg-white/5">
                              <div className="text-xs" style={{ color: 'var(--text-secondary)' }}>Score</div>
                              <div className={`text-lg font-bold ${getSentimentColor(sentimentData.averageSentiment)}`}>
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
                        ) : (
                          <div className="text-center py-4">
                            <GlassButton
                              onClick={loadSentiment}
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
        {loading && !stockData && (
          <div className="flex justify-center py-12">
            <LoadingSpinner />
          </div>
        )}

        {/* Error State */}
        {error && (
          <GlassContainer className="p-6 text-center">
            <p className="text-red-500 mb-4">{error}</p>
            <GlassButton onClick={loadAllAnalysis} variant="secondary">
              Try Again
            </GlassButton>
          </GlassContainer>
        )}
      </div>
    </div>
  );
};

export default AIAnalysis;
