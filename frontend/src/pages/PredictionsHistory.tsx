import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  TrendingUp,
  TrendingDown,
  Minus,
  Filter,
  Search,
  RefreshCw,
  BarChart3,
  Target,
  Eye,
  Activity,
  Clock
} from 'lucide-react';
import GlassContainer from '../components/GlassContainer';
import GlassButton from '../components/GlassButton';
import LoadingSpinner from '../components/LoadingSpinner';
import { fetchScreeningResults } from '../services/api';
import '../styles/glassmorphism.css';

interface SignalData {
  ticker: string;
  name: string;
  sector: string;
  price: number;
  screeningScore: number;
  rsi?: number;
  volumeRatio?: number;
  changePercent?: number;
  signalAnalysis?: {
    primarySignal: 'BULLISH' | 'BEARISH' | 'NEUTRAL';
    primaryConfidence: number;
    primaryReasons?: string[];
  };
}

const PredictionsHistory: React.FC = () => {
  const [signals, setSignals] = useState<SignalData[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [signalFilter, setSignalFilter] = useState<string>('ALL');
  const [searchTerm, setSearchTerm] = useState<string>('');
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  const fetchSignals = async () => {
    try {
      setLoading(true);
      setError(null);
      const response = await fetchScreeningResults({
        minScore: 0,
        maxResults: 200,
        includeSignals: true
      });

      if (response && response.candidates) {
        setSignals(response.candidates);
        setLastUpdated(new Date());
      }
    } catch (err) {
      console.error('Error fetching signals:', err);
      setError('Failed to fetch signal data');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSignals();
  }, []);

  const getSignalIcon = (signal?: string) => {
    switch (signal) {
      case 'BULLISH':
        return <TrendingUp className="w-5 h-5 text-green-600" />;
      case 'BEARISH':
        return <TrendingDown className="w-5 h-5 text-red-600" />;
      default:
        return <Minus className="w-5 h-5 text-orange-500" />;
    }
  };

  const getSignalColor = (signal?: string) => {
    switch (signal) {
      case 'BULLISH':
        return 'text-green-600';
      case 'BEARISH':
        return 'text-red-600';
      default:
        return 'text-orange-500';
    }
  };

  const getSignalBg = (signal?: string) => {
    switch (signal) {
      case 'BULLISH':
        return 'bg-green-600/10';
      case 'BEARISH':
        return 'bg-red-600/10';
      default:
        return 'bg-orange-500/10';
    }
  };

  const formatPrice = (price: number) => {
    return price?.toFixed(2) || '0.00';
  };

  const filteredSignals = signals.filter(signal => {
    const signalType = signal.signalAnalysis?.primarySignal || 'NEUTRAL';
    const matchesSignal = signalFilter === 'ALL' || signalType === signalFilter;
    const matchesSearch = searchTerm === '' ||
      signal.ticker.toLowerCase().includes(searchTerm.toLowerCase()) ||
      signal.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      signal.sector.toLowerCase().includes(searchTerm.toLowerCase());
    return matchesSignal && matchesSearch;
  });

  // Calculate summary stats
  const summary = {
    bullish: signals.filter(s => s.signalAnalysis?.primarySignal === 'BULLISH').length,
    bearish: signals.filter(s => s.signalAnalysis?.primarySignal === 'BEARISH').length,
    neutral: signals.filter(s => !s.signalAnalysis || s.signalAnalysis.primarySignal === 'NEUTRAL').length,
    total: signals.length
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
          <h1 className="text-4xl font-bold mb-2" style={{ color: 'var(--text-primary)' }}>
            Signal History
          </h1>
          <div className="flex items-center gap-4">
            <p className="text-lg" style={{ color: 'var(--text-secondary)' }}>
              Latest screening results with signal analysis
            </p>
            {lastUpdated && (
              <div className="flex items-center gap-1 text-sm" style={{ color: 'var(--text-tertiary)' }}>
                <Clock className="w-4 h-4" />
                Last updated: {lastUpdated.toLocaleTimeString()}
              </div>
            )}
          </div>
        </motion.div>

        {/* Summary Cards */}
        <div className="grid grid-cols-1 md:grid-cols-4 gap-6 mb-8">
          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.1 }}
          >
            <GlassContainer className="p-6">
              <div className="flex items-center gap-3">
                <div className="p-3 rounded-lg bg-blue-600/10">
                  <BarChart3 className="w-6 h-6 text-blue-600" />
                </div>
                <div>
                  <div className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
                    {summary.total}
                  </div>
                  <div className="text-sm" style={{ color: 'var(--text-secondary)' }}>
                    Total Analyzed
                  </div>
                </div>
              </div>
            </GlassContainer>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.2 }}
          >
            <GlassContainer className="p-6">
              <div className="flex items-center gap-3">
                <div className="p-3 rounded-lg bg-green-600/10">
                  <TrendingUp className="w-6 h-6 text-green-600" />
                </div>
                <div>
                  <div className="text-2xl font-bold text-green-600">
                    {summary.bullish}
                  </div>
                  <div className="text-sm" style={{ color: 'var(--text-secondary)' }}>
                    Bullish Signals
                  </div>
                </div>
              </div>
            </GlassContainer>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.3 }}
          >
            <GlassContainer className="p-6">
              <div className="flex items-center gap-3">
                <div className="p-3 rounded-lg bg-red-600/10">
                  <TrendingDown className="w-6 h-6 text-red-600" />
                </div>
                <div>
                  <div className="text-2xl font-bold text-red-600">
                    {summary.bearish}
                  </div>
                  <div className="text-sm" style={{ color: 'var(--text-secondary)' }}>
                    Bearish Signals
                  </div>
                </div>
              </div>
            </GlassContainer>
          </motion.div>

          <motion.div
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.4 }}
          >
            <GlassContainer className="p-6">
              <div className="flex items-center gap-3">
                <div className="p-3 rounded-lg bg-orange-500/10">
                  <Minus className="w-6 h-6 text-orange-500" />
                </div>
                <div>
                  <div className="text-2xl font-bold text-orange-500">
                    {summary.neutral}
                  </div>
                  <div className="text-sm" style={{ color: 'var(--text-secondary)' }}>
                    Neutral Signals
                  </div>
                </div>
              </div>
            </GlassContainer>
          </motion.div>
        </div>

        {/* Filters */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.5 }}
          className="mb-6"
        >
          <GlassContainer className="p-6">
            <div className="flex flex-wrap gap-4 items-center">
              {/* Search */}
              <div className="relative flex-1 min-w-64">
                <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4"
                       style={{ color: 'var(--text-secondary)' }} />
                <input
                  type="text"
                  placeholder="Search by ticker, name, or sector..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="w-full pl-10 pr-4 py-2 rounded-lg bg-white/5 border border-white/10 focus:border-white/20 focus:outline-none"
                  style={{ color: 'var(--text-primary)' }}
                />
              </div>

              {/* Signal Filter */}
              <div className="flex items-center gap-2">
                <Filter className="w-4 h-4" style={{ color: 'var(--text-secondary)' }} />
                <select
                  value={signalFilter}
                  onChange={(e) => setSignalFilter(e.target.value)}
                  className="px-3 py-2 rounded-lg bg-white/5 border border-white/10 focus:border-white/20 focus:outline-none"
                  style={{ color: 'var(--text-primary)' }}
                >
                  <option value="ALL">All Signals</option>
                  <option value="BULLISH">Bullish</option>
                  <option value="BEARISH">Bearish</option>
                  <option value="NEUTRAL">Neutral</option>
                </select>
              </div>

              {/* Refresh */}
              <GlassButton
                onClick={fetchSignals}
                size="sm"
                variant="secondary"
                icon={<RefreshCw className={`w-4 h-4 ${loading ? 'animate-spin' : ''}`} />}
                disabled={loading}
              >
                Refresh
              </GlassButton>
            </div>
          </GlassContainer>
        </motion.div>

        {/* Signals List */}
        <AnimatePresence>
          {loading ? (
            <div className="flex justify-center py-12">
              <LoadingSpinner />
            </div>
          ) : error ? (
            <GlassContainer className="p-6 text-center">
              <p className="text-red-500 mb-4">{error}</p>
              <GlassButton onClick={fetchSignals} variant="secondary">
                Try Again
              </GlassButton>
            </GlassContainer>
          ) : (
            <div className="space-y-4">
              {filteredSignals.map((signal, index) => (
                <motion.div
                  key={`${signal.ticker}-${index}`}
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -20 }}
                  transition={{ delay: Math.min(index * 0.02, 0.5) }}
                >
                  <GlassContainer className="p-6">
                    <div className="flex items-center justify-between mb-4">
                      <div className="flex items-center gap-4">
                        <div className={`p-3 rounded-lg ${getSignalBg(signal.signalAnalysis?.primarySignal)}`}>
                          {getSignalIcon(signal.signalAnalysis?.primarySignal)}
                        </div>
                        <div>
                          <div className="flex items-center gap-2">
                            <h3 className="text-xl font-bold" style={{ color: 'var(--text-primary)' }}>
                              {signal.ticker}
                            </h3>
                            <span className={`px-2 py-1 rounded text-xs font-semibold ${
                              signal.signalAnalysis?.primarySignal === 'BULLISH' ? 'bg-green-600/20 text-green-600' :
                              signal.signalAnalysis?.primarySignal === 'BEARISH' ? 'bg-red-600/20 text-red-600' :
                              'bg-orange-500/20 text-orange-500'
                            }`}>
                              {signal.signalAnalysis?.primarySignal || 'NEUTRAL'}
                            </span>
                          </div>
                          <div className="flex items-center gap-4 text-sm" style={{ color: 'var(--text-secondary)' }}>
                            <span>{signal.name}</span>
                            <span>|</span>
                            <span>{signal.sector}</span>
                          </div>
                        </div>
                      </div>
                      <div className="text-right">
                        <div className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
                          ${formatPrice(signal.price)}
                        </div>
                        {signal.changePercent !== undefined && (
                          <div className={`text-sm ${signal.changePercent >= 0 ? 'text-green-600' : 'text-red-600'}`}>
                            {signal.changePercent >= 0 ? '+' : ''}{signal.changePercent.toFixed(2)}%
                          </div>
                        )}
                      </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-4">
                      {/* Confidence */}
                      {signal.signalAnalysis?.primaryConfidence && (
                        <div className="flex items-center gap-2">
                          <Target className="w-4 h-4" style={{ color: 'var(--text-secondary)' }} />
                          <div>
                            <div className="text-sm" style={{ color: 'var(--text-secondary)' }}>
                              Confidence
                            </div>
                            <div className={`font-semibold ${getSignalColor(signal.signalAnalysis?.primarySignal)}`}>
                              {signal.signalAnalysis.primaryConfidence.toFixed(1)}%
                            </div>
                          </div>
                        </div>
                      )}

                      {/* Screening Score */}
                      <div className="flex items-center gap-2">
                        <BarChart3 className="w-4 h-4" style={{ color: 'var(--text-secondary)' }} />
                        <div>
                          <div className="text-sm" style={{ color: 'var(--text-secondary)' }}>
                            Score
                          </div>
                          <div className="font-semibold" style={{ color: 'var(--text-primary)' }}>
                            {signal.screeningScore?.toFixed(1) || 'N/A'}
                          </div>
                        </div>
                      </div>

                      {/* RSI */}
                      {signal.rsi && (
                        <div className="flex items-center gap-2">
                          <Activity className="w-4 h-4" style={{ color: 'var(--text-secondary)' }} />
                          <div>
                            <div className="text-sm" style={{ color: 'var(--text-secondary)' }}>
                              RSI
                            </div>
                            <div className={`font-semibold ${
                              signal.rsi > 70 ? 'text-red-600' :
                              signal.rsi < 30 ? 'text-green-600' : ''
                            }`} style={{ color: signal.rsi >= 30 && signal.rsi <= 70 ? 'var(--text-primary)' : undefined }}>
                              {signal.rsi.toFixed(1)}
                            </div>
                          </div>
                        </div>
                      )}

                      {/* Volume Ratio */}
                      {signal.volumeRatio && (
                        <div className="flex items-center gap-2">
                          <BarChart3 className="w-4 h-4" style={{ color: 'var(--text-secondary)' }} />
                          <div>
                            <div className="text-sm" style={{ color: 'var(--text-secondary)' }}>
                              Volume Ratio
                            </div>
                            <div className="font-semibold" style={{ color: 'var(--text-primary)' }}>
                              {signal.volumeRatio.toFixed(2)}x
                            </div>
                          </div>
                        </div>
                      )}
                    </div>

                    {/* Reasons */}
                    {signal.signalAnalysis?.primaryReasons && signal.signalAnalysis.primaryReasons.length > 0 && (
                      <div className="bg-white/5 rounded-lg p-3">
                        <h4 className="text-sm font-semibold mb-2" style={{ color: 'var(--text-secondary)' }}>
                          Key Signals
                        </h4>
                        <div className="flex flex-wrap gap-2">
                          {signal.signalAnalysis.primaryReasons.slice(0, 4).map((reason, idx) => (
                            <span
                              key={idx}
                              className={`px-2 py-1 rounded text-xs ${
                                signal.signalAnalysis?.primarySignal === 'BULLISH' ? 'bg-green-600/10 text-green-600' :
                                signal.signalAnalysis?.primarySignal === 'BEARISH' ? 'bg-red-600/10 text-red-600' :
                                'bg-orange-500/10 text-orange-500'
                              }`}
                            >
                              {reason}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}
                  </GlassContainer>
                </motion.div>
              ))}

              {filteredSignals.length === 0 && (
                <div className="text-center py-12">
                  <Eye className="w-16 h-16 mx-auto mb-4 opacity-30" style={{ color: 'var(--text-secondary)' }} />
                  <h3 className="text-xl font-semibold mb-2" style={{ color: 'var(--text-primary)' }}>
                    No signals found
                  </h3>
                  <p style={{ color: 'var(--text-secondary)' }}>
                    Try adjusting your filters or search criteria
                  </p>
                </div>
              )}
            </div>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
};

export default PredictionsHistory;
