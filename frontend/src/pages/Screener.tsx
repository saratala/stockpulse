import React, { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { 
  Search, 
  Filter, 
  TrendingUp, 
  TrendingDown, 
  BarChart3,
  RefreshCw,
  Settings,
  ChevronDown,
  Star,
  AlertCircle,
  Activity,
  Zap,
  X
} from 'lucide-react';
import GlassContainer from '../components/GlassContainer';
import GlassButton from '../components/GlassButton';
import SignalCard from '../components/SignalCard';
import LoadingSpinner from '../components/LoadingSpinner';
import { useSwipeable } from 'react-swipeable';
import '../styles/glassmorphism.css';

interface ScreeningResult {
  ticker: string;
  name: string;
  sector: string;
  price: number;
  screening_score: number;
  passes_all_screens?: boolean;
  ema_stack_aligned?: boolean;
  adx_strength?: number;
  stoch_position?: number;
  rsi?: number;
  volume_ratio?: number;
  change_percent?: number;
  signal_analysis?: {
    ticker: string;
    primary_signal: 'BULLISH' | 'BEARISH' | 'NEUTRAL';
    primary_confidence: number;
    reversal_signal?: string;
    reversal_confidence?: number;
    risk_level?: string;
    key_levels?: {
      support: number;
      resistance: number;
      pivot: number;
    };
    primary_reasons?: string[];
  };
}

interface ScreeningData {
  screening_date: string;
  total_analyzed: number;
  candidates_found: number;
  candidates: ScreeningResult[];
  screening_summary: {
    ema_stack: number;
    momentum: number;
    volume: number;
    fundamental: number;
  };
}

const Screener: React.FC = () => {
  const [screeningData, setScreeningData] = useState<ScreeningData | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [filters, setFilters] = useState({
    minScore: 70,
    includeSignals: true,
    maxResults: 50,
    signalType: 'all' as 'all' | 'bullish' | 'bearish'
  });
  const [activeFilter, setActiveFilter] = useState<'all' | 'bullish' | 'bearish'>('all');
  const [searchQuery, setSearchQuery] = useState('');
  const [showFilters, setShowFilters] = useState(false);
  const [selectedTab, setSelectedTab] = useState<'all' | 'signals' | 'screened'>('all');
  const [refreshing, setRefreshing] = useState(false);

  const API_BASE_URL = 'http://localhost:8000';

  const fetchScreeningData = useCallback(async (showRefreshSpinner = false) => {
    try {
      if (showRefreshSpinner) {
        setRefreshing(true);
      } else {
        setLoading(true);
      }
      setError(null);

      const params = new URLSearchParams({
        min_score: filters.minScore.toString(),
        max_results: filters.maxResults.toString(),
        include_signals: filters.includeSignals.toString()
      });

      const response = await fetch(`${API_BASE_URL}/screener/run?${params}`);
      
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      const data = await response.json();
      setScreeningData(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to fetch screening data');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [filters]);

  useEffect(() => {
    fetchScreeningData();
  }, [fetchScreeningData]);

  const swipeHandlers = useSwipeable({
    onSwipedDown: () => {
      fetchScreeningData(true);
    },
    trackMouse: false,
    preventScrollOnSwipe: true,
    delta: 100
  });

  const filteredCandidates = screeningData?.candidates?.filter(candidate => {
    // Search filter
    if (searchQuery && !candidate.ticker.toLowerCase().includes(searchQuery.toLowerCase()) &&
        !candidate.name.toLowerCase().includes(searchQuery.toLowerCase())) {
      return false;
    }

    // Signal type filter
    if (activeFilter !== 'all' && candidate.signal_analysis) {
      if (activeFilter === 'bullish' && candidate.signal_analysis.primary_signal !== 'BULLISH') {
        return false;
      }
      if (activeFilter === 'bearish' && candidate.signal_analysis.primary_signal !== 'BEARISH') {
        return false;
      }
    }

    // Tab filter
    if (selectedTab === 'signals' && (!candidate.signal_analysis || candidate.signal_analysis.primary_signal === 'NEUTRAL')) {
      return false;
    }
    if (selectedTab === 'screened' && !candidate.passes_all_screens && !candidate.ema_stack_aligned) {
      return false;
    }

    return true;
  }) || [];

  const getSignalCounts = () => {
    if (!screeningData?.candidates) return { bullish: 0, bearish: 0, neutral: 0 };
    
    const counts = { bullish: 0, bearish: 0, neutral: 0 };
    screeningData.candidates.forEach(candidate => {
      if (candidate.signal_analysis) {
        const signal = candidate.signal_analysis.primary_signal.toLowerCase();
        if (signal === 'bullish') counts.bullish++;
        else if (signal === 'bearish') counts.bearish++;
        else counts.neutral++;
      }
    });
    
    return counts;
  };

  const signalCounts = getSignalCounts();

  // Filter handlers
  const handleFilterClick = (filter: 'all' | 'bullish' | 'bearish') => {
    setActiveFilter(filter);
  };

  if (loading && !screeningData) {
    return (
      <div className="min-h-screen flex items-center justify-center" style={{ background: 'var(--bg-primary)' }}>
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  return (
    <div 
      className="min-h-screen relative overflow-hidden"
      style={{ background: 'var(--bg-primary)' }}
      {...swipeHandlers}
    >
      {/* Background Elements */}
      <div className="absolute inset-0 opacity-5" style={{ background: 'var(--bg-secondary)' }}></div>
      
      {/* Header */}
      <div className="relative z-10 p-4 pt-8">
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
                  Stock Screener
                </h1>
                <p className="text-sm md:text-base" style={{ color: 'var(--text-secondary)' }}>
                  AI-powered signal detection & screening
                </p>
              </div>
              <div className="flex gap-2">
                <GlassButton
                  size="sm"
                  onClick={() => fetchScreeningData(true)}
                  disabled={refreshing}
                  icon={<RefreshCw className={`w-4 h-4 ${refreshing ? 'animate-spin' : ''}`} />}
                />
                <GlassButton
                  size="sm"
                  variant="secondary"
                  onClick={() => setShowFilters(!showFilters)}
                  icon={<Settings className="w-4 h-4" />}
                />
              </div>
            </div>
            
            {/* Stats Row - Clickable Filters */}
            {screeningData && (
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                <button
                  onClick={() => handleFilterClick('all')}
                  className={`text-center p-3 rounded-xl transition-all duration-300 ${
                    activeFilter === 'all' 
                      ? 'bg-white/20 backdrop-blur-md border border-white/30' 
                      : 'hover:bg-white/10'
                  }`}
                >
                  <div className="text-xl md:text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
                    {screeningData.total_analyzed}
                  </div>
                  <div className="text-xs md:text-sm" style={{ color: 'var(--text-tertiary)' }}>Analyzed</div>
                </button>
                <button
                  onClick={() => handleFilterClick('bullish')}
                  className={`text-center p-3 rounded-xl transition-all duration-300 ${
                    activeFilter === 'bullish' 
                      ? 'bg-green-500/20 backdrop-blur-md border border-green-400/50' 
                      : 'hover:bg-green-500/10'
                  }`}
                >
                  <div className="text-xl md:text-2xl font-bold" style={{ color: 'var(--signal-bullish)' }}>
                    {signalCounts.bullish}
                  </div>
                  <div className="text-xs md:text-sm" style={{ color: 'var(--text-tertiary)' }}>Bullish</div>
                </button>
                <button
                  onClick={() => handleFilterClick('bearish')}
                  className={`text-center p-3 rounded-xl transition-all duration-300 ${
                    activeFilter === 'bearish' 
                      ? 'bg-red-500/20 backdrop-blur-md border border-red-400/50' 
                      : 'hover:bg-red-500/10'
                  }`}
                >
                  <div className="text-xl md:text-2xl font-bold" style={{ color: 'var(--signal-bearish)' }}>
                    {signalCounts.bearish}
                  </div>
                  <div className="text-xs md:text-sm" style={{ color: 'var(--text-tertiary)' }}>Bearish</div>
                </button>
                <div className="text-center p-3">
                  <div className="text-xl md:text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
                    {filteredCandidates.length}
                  </div>
                  <div className="text-xs md:text-sm" style={{ color: 'var(--text-tertiary)' }}>Showing</div>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Search and Filters */}
        <GlassContainer className="mb-6">
          <div className="p-4">
            {/* Search Bar and Clear Filters */}
            <div className="flex gap-3 mb-4">
              <div className="relative flex-1">
                <Search className="absolute left-4 top-1/2 transform -translate-y-1/2 w-5 h-5" style={{ color: 'var(--text-tertiary)' }} />
                <input
                  type="text"
                  placeholder="Search by ticker or company name..."
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  className="glass-search-input w-full"
                />
              </div>
              {(activeFilter !== 'all' || searchQuery) && (
                <GlassButton
                  size="sm"
                  variant="secondary"
                  onClick={() => {
                    setActiveFilter('all');
                    setSearchQuery('');
                  }}
                  icon={<X className="w-4 h-4" />}
                >
                  Clear
                </GlassButton>
              )}
            </div>
            
            {/* Active Filter Indicator */}
            {activeFilter !== 'all' && (
              <div className="mb-4">
                <div className={`inline-flex items-center gap-2 px-3 py-2 rounded-lg text-sm ${
                  activeFilter === 'bullish' 
                    ? 'bg-green-500/20 border border-green-400/30' 
                    : 'bg-red-500/20 border border-red-400/30'
                }`}>
                  {activeFilter === 'bullish' ? (
                    <TrendingUp className="w-4 h-4" style={{ color: 'var(--signal-bullish)' }} />
                  ) : (
                    <TrendingDown className="w-4 h-4" style={{ color: 'var(--signal-bearish)' }} />
                  )}
                  <span style={{ color: 'var(--text-primary)' }}>
                    Showing {activeFilter} signals ({filteredCandidates.length} stocks)
                  </span>
                </div>
              </div>
            )}

            {/* Filter Toggle */}
            <AnimatePresence>
              {showFilters && (
                <motion.div
                  initial={{ height: 0, opacity: 0 }}
                  animate={{ height: 'auto', opacity: 1 }}
                  exit={{ height: 0, opacity: 0 }}
                  transition={{ duration: 0.3 }}
                  className="overflow-hidden"
                >
                  <div className="pt-4 border-t border-white/10">
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                      <div>
                        <label className="block text-sm text-white/70 mb-2">Min Score</label>
                        <input
                          type="range"
                          min="0"
                          max="100"
                          value={filters.minScore}
                          onChange={(e) => setFilters(prev => ({ ...prev, minScore: parseInt(e.target.value) }))}
                          className="w-full"
                        />
                        <div className="text-xs text-white/50 mt-1">{filters.minScore}%</div>
                      </div>
                      <div>
                        <label className="block text-sm text-white/70 mb-2">Signal Type</label>
                        <select
                          value={filters.signalType}
                          onChange={(e) => setFilters(prev => ({ ...prev, signalType: e.target.value as any }))}
                          className="glass-input h-10"
                        >
                          <option value="all">All Signals</option>
                          <option value="bullish">Bullish Only</option>
                          <option value="bearish">Bearish Only</option>
                        </select>
                      </div>
                      <div>
                        <label className="block text-sm text-white/70 mb-2">Max Results</label>
                        <input
                          type="number"
                          min="10"
                          max="100"
                          value={filters.maxResults}
                          onChange={(e) => setFilters(prev => ({ ...prev, maxResults: parseInt(e.target.value) }))}
                          className="glass-input h-10"
                        />
                      </div>
                    </div>
                  </div>
                </motion.div>
              )}
            </AnimatePresence>
          </div>
        </GlassContainer>

        {/* Tabs */}
        <GlassContainer className="mb-6">
          <div className="p-4">
            <div className="flex space-x-1">
              {[
                { key: 'all', label: 'All', icon: BarChart3 },
                { key: 'signals', label: 'Signals', icon: Zap },
                { key: 'screened', label: 'Screened', icon: Filter }
              ].map(({ key, label, icon: Icon }) => (
                <button
                  key={key}
                  onClick={() => setSelectedTab(key as any)}
                  className={`flex-1 flex items-center justify-center gap-2 px-4 py-3 rounded-xl transition-all duration-300 ${
                    selectedTab === key
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
      </div>

      {/* Content */}
      <div className="relative z-10 px-4 pb-8">
        {error ? (
          <GlassContainer variant="bearish" className="mb-6">
            <div className="p-6 text-center">
              <AlertCircle className="w-12 h-12 mx-auto mb-4 text-red-400" />
              <h3 className="text-lg font-semibold text-white/95 mb-2">Error Loading Data</h3>
              <p className="text-white/70 mb-4">{error}</p>
              <GlassButton onClick={() => fetchScreeningData()} variant="warning">
                Try Again
              </GlassButton>
            </div>
          </GlassContainer>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5 gap-4">
            <AnimatePresence mode="popLayout">
              {filteredCandidates.map((candidate, index) => (
                <motion.div
                  key={`${candidate.ticker}-${index}`}
                  initial={{ opacity: 0, y: 20, scale: 0.95 }}
                  animate={{ opacity: 1, y: 0, scale: 1 }}
                  exit={{ opacity: 0, y: -20, scale: 0.95 }}
                  transition={{
                    duration: 0.4,
                    delay: index * 0.03,
                    ease: [0.25, 0.8, 0.25, 1]
                  }}
                >
                  <SignalCard candidate={candidate} index={index} />
                </motion.div>
              ))}
            </AnimatePresence>
            
            {filteredCandidates.length === 0 && !loading && (
              <GlassContainer className="text-center py-12">
                <Activity className="w-16 h-16 mx-auto mb-4 text-white/40" />
                <h3 className="text-xl font-semibold text-white/70 mb-2">
                  No candidates found
                </h3>
                <p className="text-white/50">
                  Try adjusting your filters or search criteria
                </p>
              </GlassContainer>
            )}
          </div>
        )}
      </div>

      {/* Pull to refresh indicator */}
      {refreshing && (
        <div className="fixed top-20 left-1/2 transform -translate-x-1/2 z-50">
          <GlassContainer className="px-4 py-2">
            <div className="flex items-center gap-2 text-white/90">
              <RefreshCw className="w-4 h-4 animate-spin" />
              <span className="text-sm">Refreshing...</span>
            </div>
          </GlassContainer>
        </div>
      )}
    </div>
  );
};

export default Screener;