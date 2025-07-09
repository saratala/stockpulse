import React, { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  ArrowLeft,
  TrendingUp,
  TrendingDown,
  BarChart3,
  Activity,
  Target,
  Shield,
  AlertTriangle,
  DollarSign,
  Volume2,
  Clock,
  Star,
  RefreshCw,
  Info,
  Zap,
  Eye,
  Calculator
} from 'lucide-react';
import { LineChart, Line, XAxis, YAxis, ResponsiveContainer, AreaChart, Area } from 'recharts';
import GlassContainer from '../components/GlassContainer';
import GlassButton from '../components/GlassButton';
import LoadingSpinner from '../components/LoadingSpinner';
import '../styles/glassmorphism.css';

interface ComprehensiveAnalysis {
  ticker: string;
  analysis_date: string;
  current_price: number;
  market_regime: {
    regime: 'bullish' | 'bearish' | 'neutral';
    strength: number;
    components: any;
  };
  key_levels: {
    pivot_points: any;
    moving_averages: any;
    bollinger_bands: any;
    recent_high: number;
    recent_low: number;
  };
  technical_indicators: {
    trend: any;
    momentum: any;
    volatility: any;
    strength: any;
  };
  heikin_ashi: {
    ha_bullish: boolean;
    ha_strength: number;
  };
  performance: {
    return_1d: number;
    return_5d: number;
    return_10d: number;
    return_20d: number;
  };
  signal_analysis?: {
    primary_signal: 'BULLISH' | 'BEARISH' | 'NEUTRAL';
    primary_confidence: number;
    primary_reasons: string[];
  };
  screening_analysis?: {
    screening_score: number;
    passes_all_screens: boolean;
  };
}

const StockAnalysis: React.FC = () => {
  const { ticker } = useParams<{ ticker: string }>();
  const [analysis, setAnalysis] = useState<ComprehensiveAnalysis | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'overview' | 'technical' | 'signals' | 'levels'>('overview');
  const [refreshing, setRefreshing] = useState(false);

  const API_BASE_URL = 'http://localhost:8000';

  const fetchAnalysis = async (showRefreshSpinner = false) => {
    if (!ticker) return;
    
    try {
      if (showRefreshSpinner) {
        setRefreshing(true);
      } else {
        setLoading(true);
      }
      setError(null);

      const response = await fetch(`${API_BASE_URL}/screener/comprehensive/${ticker.toUpperCase()}`);
      
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const data = await response.json();
      setAnalysis(data);
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

  const getRegimeColor = (regime: string) => {
    switch (regime) {
      case 'bullish':
        return 'text-green-400';
      case 'bearish':
        return 'text-red-400';
      default:
        return 'text-orange-400';
    }
  };

  const getRegimeIcon = (regime: string) => {
    switch (regime) {
      case 'bullish':
        return <TrendingUp className="w-5 h-5" />;
      case 'bearish':
        return <TrendingDown className="w-5 h-5" />;
      default:
        return <Activity className="w-5 h-5" />;
    }
  };

  const getPerformanceColor = (value: number) => {
    if (value > 0) return 'text-green-400';
    if (value < 0) return 'text-red-400';
    return 'text-white/70';
  };

  const tabs = [
    { key: 'overview', label: 'Overview', icon: BarChart3 },
    { key: 'technical', label: 'Technical', icon: Activity },
    { key: 'signals', label: 'Signals', icon: Zap },
    { key: 'levels', label: 'Levels', icon: Target }
  ];

  if (loading && !analysis) {
    return (
      <div className="min-h-screen flex items-center justify-center" style={{ background: 'var(--bg-primary)' }}>
        <LoadingSpinner size="lg" text="Analyzing stock..." />
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

  if (!analysis) return null;

  return (
    <div className="min-h-screen relative overflow-hidden" style={{ background: 'var(--bg-primary)' }}>
      {/* Background Elements */}
      <div className="absolute inset-0 opacity-5" style={{ background: 'var(--bg-secondary)' }}></div>

      <div className="relative z-10 p-4 pt-8">
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
            <div className="flex items-center justify-between mb-6">
              <div className="flex items-center gap-4">
                <GlassButton
                  size="sm"
                  variant="secondary"
                  onClick={() => window.history.back()}
                  icon={<ArrowLeft className="w-4 h-4" />}
                />
                <div>
                  <h1 className="text-2xl md:text-3xl font-bold" style={{ color: 'var(--text-primary)' }}>
                    {analysis.ticker}
                  </h1>
                  <p className="text-sm" style={{ color: 'var(--text-secondary)' }}>
                    Comprehensive Analysis
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

            {/* Price and Market Regime */}
            <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
              <GlassContainer variant="accent" className="p-4">
                <div className="flex items-center gap-3">
                  <DollarSign className="w-8 h-8 text-blue-400" />
                  <div>
                    <div className="text-2xl font-bold text-white/95">
                      ${analysis.current_price.toFixed(2)}
                    </div>
                    <div className="text-sm text-white/60">Current Price</div>
                  </div>
                </div>
              </GlassContainer>

              <GlassContainer variant={analysis.market_regime.regime} className="p-4">
                <div className="flex items-center gap-3">
                  <div className={getRegimeColor(analysis.market_regime.regime)}>
                    {getRegimeIcon(analysis.market_regime.regime)}
                  </div>
                  <div>
                    <div className="text-lg font-bold text-white/95 capitalize">
                      {analysis.market_regime.regime}
                    </div>
                    <div className="text-sm text-white/60">
                      {analysis.market_regime.strength}% Strength
                    </div>
                  </div>
                </div>
              </GlassContainer>

              {analysis.signal_analysis && (
                <GlassContainer 
                  variant={analysis.signal_analysis.primary_signal === 'BULLISH' ? 'bullish' : 
                           analysis.signal_analysis.primary_signal === 'BEARISH' ? 'bearish' : 'neutral'}
                  className="p-4"
                >
                  <div className="flex items-center gap-3">
                    <Zap className={
                      analysis.signal_analysis.primary_signal === 'BULLISH' ? 'text-green-400' :
                      analysis.signal_analysis.primary_signal === 'BEARISH' ? 'text-red-400' : 'text-orange-400'
                    } />
                    <div>
                      <div className="text-lg font-bold text-white/95">
                        {analysis.signal_analysis.primary_signal}
                      </div>
                      <div className="text-sm text-white/60">
                        {analysis.signal_analysis.primary_confidence}% Confidence
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
            <div className="grid grid-cols-2 md:grid-cols-4 gap-2">
              {tabs.map(({ key, label, icon: Icon }) => (
                <button
                  key={key}
                  onClick={() => setActiveTab(key as any)}
                  className={`flex items-center justify-center gap-2 px-4 py-3 rounded-xl transition-all duration-300 ${
                    activeTab === key
                      ? 'bg-white/20 backdrop-blur-md border border-white/30'
                      : 'bg-white/5 hover:bg-white/10'
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
                {/* Performance */}
                <GlassContainer>
                  <div className="p-6">
                    <h3 className="text-lg font-bold text-white/95 mb-4 flex items-center gap-2">
                      <BarChart3 className="w-5 h-5" />
                      Performance
                    </h3>
                    <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                      {[
                        { label: '1D', value: analysis.performance.return_1d },
                        { label: '5D', value: analysis.performance.return_5d },
                        { label: '10D', value: analysis.performance.return_10d },
                        { label: '20D', value: analysis.performance.return_20d }
                      ].map(({ label, value }) => (
                        <div key={label} className="text-center p-3 rounded-xl bg-white/5">
                          <div className={`text-xl font-bold ${getPerformanceColor(value)}`}>
                            {value > 0 ? '+' : ''}{value.toFixed(2)}%
                          </div>
                          <div className="text-sm text-white/60">{label} Return</div>
                        </div>
                      ))}
                    </div>
                  </div>
                </GlassContainer>

                {/* Quick Stats */}
                {analysis.screening_analysis && (
                  <GlassContainer>
                    <div className="p-6">
                      <h3 className="text-lg font-bold text-white/95 mb-4 flex items-center gap-2">
                        <Shield className="w-5 h-5" />
                        Screening Results
                      </h3>
                      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div className="p-4 rounded-xl bg-white/5">
                          <div className="text-2xl font-bold text-white/95 mb-1">
                            {analysis.screening_analysis.screening_score}%
                          </div>
                          <div className="text-sm text-white/60">Screening Score</div>
                        </div>
                        <div className="p-4 rounded-xl bg-white/5">
                          <div className={`text-2xl font-bold mb-1 ${
                            analysis.screening_analysis.passes_all_screens ? 'text-green-400' : 'text-red-400'
                          }`}>
                            {analysis.screening_analysis.passes_all_screens ? 'PASS' : 'FAIL'}
                          </div>
                          <div className="text-sm text-white/60">All Screens</div>
                        </div>
                      </div>
                    </div>
                  </GlassContainer>
                )}
              </div>
            )}

            {/* Technical Tab */}
            {activeTab === 'technical' && (
              <div className="space-y-6">
                <GlassContainer>
                  <div className="p-6">
                    <h3 className="text-lg font-bold text-white/95 mb-4">Technical Indicators</h3>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                      {/* Trend Indicators */}
                      <div>
                        <h4 className="text-white/90 font-semibold mb-3">Trend</h4>
                        <div className="space-y-2">
                          {Object.entries(analysis.technical_indicators.trend).map(([key, value]) => (
                            <div key={key} className="flex justify-between items-center p-2 rounded-lg bg-white/5">
                              <span className="text-sm text-white/70 capitalize">{key.replace('_', ' ')}</span>
                              <span className="text-sm font-medium text-white/90">
                                {typeof value === 'number' ? value.toFixed(2) : 'N/A'}
                              </span>
                            </div>
                          ))}
                        </div>
                      </div>

                      {/* Momentum Indicators */}
                      <div>
                        <h4 className="text-white/90 font-semibold mb-3">Momentum</h4>
                        <div className="space-y-2">
                          {Object.entries(analysis.technical_indicators.momentum).map(([key, value]) => (
                            <div key={key} className="flex justify-between items-center p-2 rounded-lg bg-white/5">
                              <span className="text-sm text-white/70 capitalize">{key.replace('_', ' ')}</span>
                              <span className="text-sm font-medium text-white/90">
                                {typeof value === 'number' ? value.toFixed(2) : 'N/A'}
                              </span>
                            </div>
                          ))}
                        </div>
                      </div>
                    </div>
                  </div>
                </GlassContainer>

                {/* Heikin Ashi */}
                <GlassContainer variant={analysis.heikin_ashi.ha_bullish ? 'bullish' : 'bearish'}>
                  <div className="p-6">
                    <h3 className="text-lg font-bold text-white/95 mb-4 flex items-center gap-2">
                      <Activity className="w-5 h-5" />
                      Heikin Ashi Analysis
                    </h3>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                      <div className="p-4 rounded-xl bg-white/10">
                        <div className="text-xl font-bold text-white/95 mb-1">
                          {analysis.heikin_ashi.ha_bullish ? 'BULLISH' : 'BEARISH'}
                        </div>
                        <div className="text-sm text-white/60">Current Trend</div>
                      </div>
                      <div className="p-4 rounded-xl bg-white/10">
                        <div className="text-xl font-bold text-white/95 mb-1">
                          {analysis.heikin_ashi.ha_strength.toFixed(2)}%
                        </div>
                        <div className="text-sm text-white/60">Trend Strength</div>
                      </div>
                    </div>
                  </div>
                </GlassContainer>
              </div>
            )}

            {/* Signals Tab */}
            {activeTab === 'signals' && analysis.signal_analysis && (
              <GlassContainer>
                <div className="p-6">
                  <h3 className="text-lg font-bold text-white/95 mb-4 flex items-center gap-2">
                    <Zap className="w-5 h-5" />
                    Signal Analysis
                  </h3>
                  
                  <div className="mb-6">
                    <div className="flex items-center gap-4 mb-4">
                      <div className={`p-4 rounded-xl ${
                        analysis.signal_analysis.primary_signal === 'BULLISH' ? 'bg-green-500/20' :
                        analysis.signal_analysis.primary_signal === 'BEARISH' ? 'bg-red-500/20' : 'bg-orange-500/20'
                      }`}>
                        {analysis.signal_analysis.primary_signal === 'BULLISH' ? 
                          <TrendingUp className="w-8 h-8 text-green-400" /> :
                          analysis.signal_analysis.primary_signal === 'BEARISH' ?
                          <TrendingDown className="w-8 h-8 text-red-400" /> :
                          <Activity className="w-8 h-8 text-orange-400" />
                        }
                      </div>
                      <div>
                        <div className="text-2xl font-bold text-white/95">
                          {analysis.signal_analysis.primary_signal}
                        </div>
                        <div className="text-white/70">
                          {analysis.signal_analysis.primary_confidence}% Confidence
                        </div>
                      </div>
                    </div>

                    <div className="w-full h-2 bg-white/20 rounded-full overflow-hidden mb-6">
                      <motion.div
                        className={`h-full ${
                          analysis.signal_analysis.primary_signal === 'BULLISH' ? 'bg-green-400' :
                          analysis.signal_analysis.primary_signal === 'BEARISH' ? 'bg-red-400' : 'bg-orange-400'
                        }`}
                        initial={{ width: 0 }}
                        animate={{ width: `${analysis.signal_analysis.primary_confidence}%` }}
                        transition={{ duration: 1, delay: 0.5 }}
                      />
                    </div>
                  </div>

                  <div>
                    <h4 className="text-white/90 font-semibold mb-3">Signal Reasons</h4>
                    <div className="space-y-3">
                      {analysis.signal_analysis.primary_reasons.map((reason, index) => (
                        <motion.div
                          key={index}
                          initial={{ opacity: 0, x: -20 }}
                          animate={{ opacity: 1, x: 0 }}
                          transition={{ delay: index * 0.1 }}
                          className="flex items-start gap-3 p-3 rounded-xl bg-white/5"
                        >
                          <div className="w-2 h-2 rounded-full bg-current mt-2 flex-shrink-0" />
                          <span className="text-white/80">{reason}</span>
                        </motion.div>
                      ))}
                    </div>
                  </div>
                </div>
              </GlassContainer>
            )}

            {/* Levels Tab */}
            {activeTab === 'levels' && (
              <div className="space-y-6">
                {/* Support/Resistance */}
                <GlassContainer>
                  <div className="p-6">
                    <h3 className="text-lg font-bold text-white/95 mb-4 flex items-center gap-2">
                      <Target className="w-5 h-5" />
                      Key Levels
                    </h3>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                      <div>
                        <h4 className="text-white/90 font-semibold mb-3">Recent Range</h4>
                        <div className="space-y-2">
                          <div className="flex justify-between items-center p-2 rounded-lg bg-green-500/10">
                            <span className="text-sm text-white/70">Recent High</span>
                            <span className="text-sm font-medium text-green-400">
                              ${analysis.key_levels.recent_high.toFixed(2)}
                            </span>
                          </div>
                          <div className="flex justify-between items-center p-2 rounded-lg bg-red-500/10">
                            <span className="text-sm text-white/70">Recent Low</span>
                            <span className="text-sm font-medium text-red-400">
                              ${analysis.key_levels.recent_low.toFixed(2)}
                            </span>
                          </div>
                        </div>
                      </div>

                      <div>
                        <h4 className="text-white/90 font-semibold mb-3">Moving Averages</h4>
                        <div className="space-y-2">
                          {Object.entries(analysis.key_levels.moving_averages).map(([key, value]) => 
                            value ? (
                              <div key={key} className="flex justify-between items-center p-2 rounded-lg bg-white/5">
                                <span className="text-sm text-white/70">{key.replace('_', ' ').toUpperCase()}</span>
                                <span className="text-sm font-medium text-white/90">
                                  ${typeof value === 'number' ? value.toFixed(2) : 'N/A'}
                                </span>
                              </div>
                            ) : null
                          )}
                        </div>
                      </div>
                    </div>
                  </div>
                </GlassContainer>

                {/* Bollinger Bands */}
                <GlassContainer>
                  <div className="p-6">
                    <h3 className="text-lg font-bold text-white/95 mb-4">Bollinger Bands</h3>
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                      {Object.entries(analysis.key_levels.bollinger_bands).map(([key, value]) => (
                        <div key={key} className="p-4 rounded-xl bg-white/5 text-center">
                          <div className="text-lg font-bold text-white/95 mb-1">
                            ${typeof value === 'number' ? value.toFixed(2) : 'N/A'}
                          </div>
                          <div className="text-sm text-white/60 capitalize">{key.replace('_', ' ')}</div>
                        </div>
                      ))}
                    </div>
                  </div>
                </GlassContainer>
              </div>
            )}
          </motion.div>
        </AnimatePresence>
      </div>
    </div>
  );
};

export default StockAnalysis;