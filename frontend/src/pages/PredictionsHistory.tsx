import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { 
  Clock, 
  TrendingUp, 
  TrendingDown,
  Minus,
  Filter,
  Search,
  Calendar,
  RefreshCw,
  BarChart3,
  Target,
  Eye,
  ArrowUp,
  ArrowDown,
  Activity,
  DollarSign
} from 'lucide-react';
import GlassContainer from '../components/GlassContainer';
import GlassButton from '../components/GlassButton';
import LoadingSpinner from '../components/LoadingSpinner';
import { fetchPredictionHistory, fetchPredictionSummary } from '../services/api';
import '../styles/glassmorphism.css';

interface PredictionData {
  ticker: string;
  timestamp: string;
  current_price: number;
  signal_type: 'BULLISH' | 'BEARISH' | 'NEUTRAL';
  confidence: number;
  primary_reasons: string[];
  screening_score: number;
  sector: string;
  predicted_price_1h?: number;
  predicted_price_1d?: number;
  predicted_price_1w?: number;
  volume?: number;
  rsi?: number;
  macd?: number;
  bollinger_position?: number;
}

interface PredictionSummary {
  signal_type: string;
  count: number;
  avg_confidence: number;
  avg_screening_score: number;
  unique_tickers: number;
}

const PredictionsHistory: React.FC = () => {
  const [predictions, setPredictions] = useState<PredictionData[]>([]);
  const [summary, setSummary] = useState<PredictionSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedTicker, setSelectedTicker] = useState<string>('');
  const [timeRange, setTimeRange] = useState<number>(24);
  const [signalFilter, setSignalFilter] = useState<string>('ALL');
  const [searchTerm, setSearchTerm] = useState<string>('');

  useEffect(() => {
    fetchPredictions();
    fetchSummary();
  }, [timeRange, signalFilter, selectedTicker]);

  const fetchPredictions = async () => {
    try {
      setLoading(true);
      const params: any = {
        hours: timeRange,
        limit: 200
      };
      
      if (selectedTicker) {
        params.ticker = selectedTicker;
      }

      const response = await fetchPredictionHistory(params);
      setPredictions(response.predictions || []);
    } catch (error) {
      console.error('Error fetching predictions:', error);
    } finally {
      setLoading(false);
    }
  };

  const fetchSummary = async () => {
    try {
      const response = await fetchPredictionSummary(timeRange);
      setSummary(response.summary || []);
    } catch (error) {
      console.error('Error fetching summary:', error);
    }
  };

  const getSignalIcon = (signal: string) => {
    switch (signal) {
      case 'BULLISH':
        return <TrendingUp className="w-5 h-5 text-green-600" />;
      case 'BEARISH':
        return <TrendingDown className="w-5 h-5 text-red-600" />;
      default:
        return <Minus className="w-5 h-5 text-orange-500" />;
    }
  };

  const getSignalColor = (signal: string) => {
    switch (signal) {
      case 'BULLISH':
        return 'text-green-600';
      case 'BEARISH':
        return 'text-red-600';
      default:
        return 'text-orange-500';
    }
  };

  const getSignalBg = (signal: string) => {
    switch (signal) {
      case 'BULLISH':
        return 'bg-green-600/10';
      case 'BEARISH':
        return 'bg-red-600/10';
      default:
        return 'bg-orange-500/10';
    }
  };

  const formatTimestamp = (timestamp: string) => {
    return new Date(timestamp).toLocaleString();
  };

  const formatPrice = (price: number) => {
    return price.toFixed(2);
  };

  const getPriceChange = (current: number, predicted?: number) => {
    if (!predicted) return null;
    const change = ((predicted - current) / current) * 100;
    return change;
  };

  const filteredPredictions = predictions.filter(prediction => {
    const matchesSignal = signalFilter === 'ALL' || prediction.signal_type === signalFilter;
    const matchesSearch = searchTerm === '' || 
      prediction.ticker.toLowerCase().includes(searchTerm.toLowerCase()) ||
      prediction.sector.toLowerCase().includes(searchTerm.toLowerCase());
    return matchesSignal && matchesSearch;
  });

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
            Predictions History
          </h1>
          <p className="text-lg" style={{ color: 'var(--text-secondary)' }}>
            Real-time algorithm predictions updated every 5 minutes
          </p>
        </motion.div>

        {/* Summary Cards */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-8">
          {summary.map((item, index) => (
            <motion.div
              key={item.signal_type}
              initial={{ opacity: 0, y: 20 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ delay: index * 0.1 }}
            >
              <GlassContainer className="p-6">
                <div className="flex items-center justify-between mb-4">
                  <div className="flex items-center gap-3">
                    {getSignalIcon(item.signal_type)}
                    <div>
                      <h3 className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>
                        {item.signal_type}
                      </h3>
                      <p className="text-sm" style={{ color: 'var(--text-secondary)' }}>
                        {item.count} predictions
                      </p>
                    </div>
                  </div>
                  <div className="text-right">
                    <div className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
                      {item.unique_tickers}
                    </div>
                    <div className="text-sm" style={{ color: 'var(--text-secondary)' }}>
                      stocks
                    </div>
                  </div>
                </div>
                <div className="flex justify-between text-sm">
                  <div>
                    <span style={{ color: 'var(--text-secondary)' }}>Avg Confidence: </span>
                    <span className={getSignalColor(item.signal_type)}>
                      {item.avg_confidence.toFixed(1)}%
                    </span>
                  </div>
                  <div>
                    <span style={{ color: 'var(--text-secondary)' }}>Avg Score: </span>
                    <span style={{ color: 'var(--text-primary)' }}>
                      {item.avg_screening_score.toFixed(1)}
                    </span>
                  </div>
                </div>
              </GlassContainer>
            </motion.div>
          ))}
        </div>

        {/* Filters */}
        <motion.div
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.3 }}
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
                  placeholder="Search by ticker or sector..."
                  value={searchTerm}
                  onChange={(e) => setSearchTerm(e.target.value)}
                  className="w-full pl-10 pr-4 py-2 rounded-lg bg-white/5 border border-white/10 focus:border-white/20 focus:outline-none"
                  style={{ color: 'var(--text-primary)' }}
                />
              </div>

              {/* Time Range */}
              <div className="flex items-center gap-2">
                <Calendar className="w-4 h-4" style={{ color: 'var(--text-secondary)' }} />
                <select
                  value={timeRange}
                  onChange={(e) => setTimeRange(Number(e.target.value))}
                  className="px-3 py-2 rounded-lg bg-white/5 border border-white/10 focus:border-white/20 focus:outline-none"
                  style={{ color: 'var(--text-primary)' }}
                >
                  <option value={1}>1 Hour</option>
                  <option value={6}>6 Hours</option>
                  <option value={24}>24 Hours</option>
                  <option value={168}>1 Week</option>
                  <option value={720}>1 Month</option>
                </select>
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
                onClick={fetchPredictions}
                size="sm"
                variant="secondary"
                icon={<RefreshCw className="w-4 h-4" />}
              >
                Refresh
              </GlassButton>
            </div>
          </GlassContainer>
        </motion.div>

        {/* Predictions List */}
        <AnimatePresence>
          {loading ? (
            <div className="flex justify-center py-12">
              <LoadingSpinner />
            </div>
          ) : (
            <div className="space-y-4">
              {filteredPredictions.map((prediction, index) => (
                <motion.div
                  key={`${prediction.ticker}-${prediction.timestamp}`}
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -20 }}
                  transition={{ delay: index * 0.05 }}
                >
                  <GlassContainer className="p-6">
                    <div className="flex items-center justify-between mb-4">
                      <div className="flex items-center gap-4">
                        <div className={`p-3 rounded-lg ${getSignalBg(prediction.signal_type)}`}>
                          {getSignalIcon(prediction.signal_type)}
                        </div>
                        <div>
                          <div className="flex items-center gap-2">
                            <h3 className="text-xl font-bold" style={{ color: 'var(--text-primary)' }}>
                              {prediction.ticker}
                            </h3>
                            <span className={`px-2 py-1 rounded text-xs font-semibold ${
                              prediction.signal_type === 'BULLISH' ? 'bg-green-600/20 text-green-600' :
                              prediction.signal_type === 'BEARISH' ? 'bg-red-600/20 text-red-600' :
                              'bg-orange-500/20 text-orange-500'
                            }`}>
                              {prediction.signal_type}
                            </span>
                          </div>
                          <div className="flex items-center gap-4 text-sm" style={{ color: 'var(--text-secondary)' }}>
                            <span>{prediction.sector}</span>
                            <span>•</span>
                            <span>{formatTimestamp(prediction.timestamp)}</span>
                          </div>
                        </div>
                      </div>
                      <div className="text-right">
                        <div className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
                          ${formatPrice(prediction.current_price)}
                        </div>
                        <div className="text-sm" style={{ color: 'var(--text-secondary)' }}>
                          Current Price
                        </div>
                      </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-4">
                      {/* Confidence */}
                      <div className="flex items-center gap-2">
                        <Target className="w-4 h-4" style={{ color: 'var(--text-secondary)' }} />
                        <div>
                          <div className="text-sm" style={{ color: 'var(--text-secondary)' }}>
                            Confidence
                          </div>
                          <div className={`font-semibold ${getSignalColor(prediction.signal_type)}`}>
                            {prediction.confidence.toFixed(1)}%
                          </div>
                        </div>
                      </div>

                      {/* Screening Score */}
                      <div className="flex items-center gap-2">
                        <BarChart3 className="w-4 h-4" style={{ color: 'var(--text-secondary)' }} />
                        <div>
                          <div className="text-sm" style={{ color: 'var(--text-secondary)' }}>
                            Score
                          </div>
                          <div className="font-semibold" style={{ color: 'var(--text-primary)' }}>
                            {prediction.screening_score.toFixed(1)}
                          </div>
                        </div>
                      </div>

                      {/* RSI */}
                      {prediction.rsi && (
                        <div className="flex items-center gap-2">
                          <Activity className="w-4 h-4" style={{ color: 'var(--text-secondary)' }} />
                          <div>
                            <div className="text-sm" style={{ color: 'var(--text-secondary)' }}>
                              RSI
                            </div>
                            <div className="font-semibold" style={{ color: 'var(--text-primary)' }}>
                              {prediction.rsi.toFixed(1)}
                            </div>
                          </div>
                        </div>
                      )}

                      {/* Volume */}
                      {prediction.volume && (
                        <div className="flex items-center gap-2">
                          <BarChart3 className="w-4 h-4" style={{ color: 'var(--text-secondary)' }} />
                          <div>
                            <div className="text-sm" style={{ color: 'var(--text-secondary)' }}>
                              Volume
                            </div>
                            <div className="font-semibold" style={{ color: 'var(--text-primary)' }}>
                              {(prediction.volume / 1000000).toFixed(1)}M
                            </div>
                          </div>
                        </div>
                      )}
                    </div>

                    {/* Price Predictions */}
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-4">
                      {prediction.predicted_price_1h && (
                        <div className="bg-white/5 rounded-lg p-3">
                          <div className="flex items-center justify-between">
                            <div>
                              <div className="text-sm" style={{ color: 'var(--text-secondary)' }}>
                                1H Prediction
                              </div>
                              <div className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>
                                ${formatPrice(prediction.predicted_price_1h)}
                              </div>
                            </div>
                            <div className="text-right">
                              {(() => {
                                const change = getPriceChange(prediction.current_price, prediction.predicted_price_1h);
                                return change && (
                                  <div className={`flex items-center gap-1 ${change > 0 ? 'text-green-600' : 'text-red-600'}`}>
                                    {change > 0 ? <ArrowUp className="w-3 h-3" /> : <ArrowDown className="w-3 h-3" />}
                                    <span className="text-sm">{Math.abs(change).toFixed(2)}%</span>
                                  </div>
                                );
                              })()}
                            </div>
                          </div>
                        </div>
                      )}

                      {prediction.predicted_price_1d && (
                        <div className="bg-white/5 rounded-lg p-3">
                          <div className="flex items-center justify-between">
                            <div>
                              <div className="text-sm" style={{ color: 'var(--text-secondary)' }}>
                                1D Prediction
                              </div>
                              <div className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>
                                ${formatPrice(prediction.predicted_price_1d)}
                              </div>
                            </div>
                            <div className="text-right">
                              {(() => {
                                const change = getPriceChange(prediction.current_price, prediction.predicted_price_1d);
                                return change && (
                                  <div className={`flex items-center gap-1 ${change > 0 ? 'text-green-600' : 'text-red-600'}`}>
                                    {change > 0 ? <ArrowUp className="w-3 h-3" /> : <ArrowDown className="w-3 h-3" />}
                                    <span className="text-sm">{Math.abs(change).toFixed(2)}%</span>
                                  </div>
                                );
                              })()}
                            </div>
                          </div>
                        </div>
                      )}

                      {prediction.predicted_price_1w && (
                        <div className="bg-white/5 rounded-lg p-3">
                          <div className="flex items-center justify-between">
                            <div>
                              <div className="text-sm" style={{ color: 'var(--text-secondary)' }}>
                                1W Prediction
                              </div>
                              <div className="text-lg font-semibold" style={{ color: 'var(--text-primary)' }}>
                                ${formatPrice(prediction.predicted_price_1w)}
                              </div>
                            </div>
                            <div className="text-right">
                              {(() => {
                                const change = getPriceChange(prediction.current_price, prediction.predicted_price_1w);
                                return change && (
                                  <div className={`flex items-center gap-1 ${change > 0 ? 'text-green-600' : 'text-red-600'}`}>
                                    {change > 0 ? <ArrowUp className="w-3 h-3" /> : <ArrowDown className="w-3 h-3" />}
                                    <span className="text-sm">{Math.abs(change).toFixed(2)}%</span>
                                  </div>
                                );
                              })()}
                            </div>
                          </div>
                        </div>
                      )}
                    </div>

                    {/* Reasons */}
                    {prediction.primary_reasons && prediction.primary_reasons.length > 0 && (
                      <div className="bg-white/5 rounded-lg p-3">
                        <h4 className="text-sm font-semibold mb-2" style={{ color: 'var(--text-secondary)' }}>
                          Key Signals
                        </h4>
                        <div className="space-y-1">
                          {prediction.primary_reasons.slice(0, 3).map((reason, idx) => (
                            <div key={idx} className="flex items-start gap-2">
                              <div className="w-1 h-1 rounded-full bg-current mt-2 flex-shrink-0" />
                              <span className="text-sm" style={{ color: 'var(--text-secondary)' }}>
                                {reason}
                              </span>
                            </div>
                          ))}
                        </div>
                      </div>
                    )}
                  </GlassContainer>
                </motion.div>
              ))}

              {filteredPredictions.length === 0 && (
                <div className="text-center py-12">
                  <Eye className="w-16 h-16 mx-auto mb-4 opacity-30" style={{ color: 'var(--text-secondary)' }} />
                  <h3 className="text-xl font-semibold mb-2" style={{ color: 'var(--text-primary)' }}>
                    No predictions found
                  </h3>
                  <p style={{ color: 'var(--text-secondary)' }}>
                    Try adjusting your filters or time range
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