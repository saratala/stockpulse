import React, { FC, useEffect, useState, useCallback, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { motion, AnimatePresence } from 'framer-motion';
import {
  TrendingUp,
  TrendingDown,
  Activity,
  DollarSign,
  BarChart3,
  Clock,
  RefreshCw,
  ArrowLeft,
  Brain,
  Newspaper,
  AlertCircle,
  Volume2,
  Calendar,
  ChevronDown,
  ChevronUp,
  X
} from 'lucide-react';
import GlassContainer from '../components/GlassContainer';
import GlassButton from '../components/GlassButton';
import LoadingSpinner from '../components/LoadingSpinner';
import {
  fetchStockByTicker,
  fetchLatestStockPrice,
  fetchStockPrices,
  fetchComprehensiveAnalysis,
  fetchIntradayPrices,
  fetchIntradayDates,
  IntradayPrice as ApiIntradayPrice
} from '../services/api';
import '../styles/glassmorphism.css';

interface StockInfo {
  id: number;
  ticker: string;
  name: string;
  sector: string;
  industry: string;
  marketCap: number | null;
}

interface PriceData {
  ticker: string;
  date: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  adjustedClose: number;
}

interface TechnicalAnalysis {
  ticker: string;
  analysisDate: string;
  compositeScore: number;
  primarySignal: string;
  signalConfidence: number;
  trendDirection: string;
  rsi: number;
  macd: number;
  supportLevel: number;
  resistanceLevel: number;
  signals: string[];
}

type TimePeriod = '1W' | '1M' | '3M' | 'YTD' | '1Y' | 'ALL';

const periodDays: Record<TimePeriod, number> = {
  '1W': 7,
  '1M': 30,
  '3M': 90,
  'YTD': -1, // Special case - calculate from start of year
  '1Y': 365,
  'ALL': 730 // 2 years
};

const StockDetails: FC = () => {
  const { ticker } = useParams<{ ticker: string }>();
  const navigate = useNavigate();
  const [stockInfo, setStockInfo] = useState<StockInfo | null>(null);
  const [latestPrice, setLatestPrice] = useState<PriceData | null>(null);
  const [allPriceHistory, setAllPriceHistory] = useState<PriceData[]>([]);
  const [technicalAnalysis, setTechnicalAnalysis] = useState<TechnicalAnalysis | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Period selectors
  const [statsPeriod, setStatsPeriod] = useState<TimePeriod>('1M');
  const [historyPeriod, setHistoryPeriod] = useState<TimePeriod>('1M');
  const [selectedDate, setSelectedDate] = useState<string | null>(null);
  const [selectedTimeSlot, setSelectedTimeSlot] = useState<number | null>(null);

  // Real intraday data
  const [realIntradayData, setRealIntradayData] = useState<ApiIntradayPrice[]>([]);
  const [intradayAvailableDates, setIntradayAvailableDates] = useState<string[]>([]);
  const [loadingIntraday, setLoadingIntraday] = useState(false);
  const [hasRealIntradayData, setHasRealIntradayData] = useState(false);

  const loadData = useCallback(async (showRefresh = false) => {
    if (!ticker) return;

    try {
      if (showRefresh) {
        setRefreshing(true);
      } else {
        setLoading(true);
      }
      setError(null);

      // Fetch all data in parallel - get 2 years of history for all period calculations
      const [stockData, priceData, historyData, analysisData] = await Promise.allSettled([
        fetchStockByTicker(ticker),
        fetchLatestStockPrice(ticker),
        fetchStockPrices(ticker, 730), // 2 years of data
        fetchComprehensiveAnalysis(ticker)
      ]);

      if (stockData.status === 'fulfilled') {
        setStockInfo(stockData.value);
      }
      if (priceData.status === 'fulfilled') {
        setLatestPrice(priceData.value);
      }
      if (historyData.status === 'fulfilled') {
        setAllPriceHistory(historyData.value);
      }
      if (analysisData.status === 'fulfilled') {
        setTechnicalAnalysis(analysisData.value);
      }

      if (stockData.status === 'rejected' && priceData.status === 'rejected') {
        setError('Unable to load stock data. Please try again.');
      }

      // Check for available intraday data
      try {
        const intradayDatesResponse = await fetchIntradayDates(ticker);
        if (intradayDatesResponse.dates && intradayDatesResponse.dates.length > 0) {
          setIntradayAvailableDates(intradayDatesResponse.dates);
          setHasRealIntradayData(true);
        }
      } catch {
        // No intraday data available - will use synthetic
        setHasRealIntradayData(false);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'An error occurred');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [ticker]);

  // Load real intraday data when a date is selected
  const loadIntradayData = useCallback(async (date: string) => {
    if (!ticker || !hasRealIntradayData) return;

    try {
      setLoadingIntraday(true);
      const dateStr = new Date(date).toISOString().split('T')[0];
      const response = await fetchIntradayPrices(ticker, dateStr);

      if (response.prices && response.prices.length > 0) {
        setRealIntradayData(response.prices);
      } else {
        setRealIntradayData([]);
      }
    } catch {
      setRealIntradayData([]);
    } finally {
      setLoadingIntraday(false);
    }
  }, [ticker, hasRealIntradayData]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // Load real intraday data when date is selected
  useEffect(() => {
    if (selectedDate && hasRealIntradayData) {
      loadIntradayData(selectedDate);
    } else {
      setRealIntradayData([]);
    }
  }, [selectedDate, hasRealIntradayData, loadIntradayData]);

  // Aggregate price history by date (combine intraday entries into daily OHLCV)
  const sortedPriceHistory = useMemo(() => {
    if (!allPriceHistory.length) return [];

    // Group entries by date (ignoring time portion)
    const dateMap = new Map<string, PriceData[]>();

    allPriceHistory.forEach(entry => {
      const dateKey = new Date(entry.date).toISOString().split('T')[0]; // YYYY-MM-DD
      if (!dateMap.has(dateKey)) {
        dateMap.set(dateKey, []);
      }
      dateMap.get(dateKey)!.push(entry);
    });

    // Aggregate each day's data into a single OHLCV entry
    const aggregatedData: PriceData[] = [];

    dateMap.forEach((entries, dateKey) => {
      // Sort entries by time for this day
      entries.sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime());

      // Find the entry with the most realistic OHLC data (highest high-low range)
      // This helps filter out the repeated snapshot entries
      let bestEntry = entries[0];
      let maxRange = 0;

      entries.forEach(entry => {
        const range = entry.high - entry.low;
        if (range > maxRange) {
          maxRange = range;
          bestEntry = entry;
        }
      });

      // If we have a good OHLC entry (with proper high/low range), use it
      // Otherwise, aggregate from all entries
      if (maxRange > 0.01) {
        aggregatedData.push({
          ...bestEntry,
          date: dateKey, // Normalize date to just the date portion
          volume: Math.max(...entries.map(e => e.volume)) // Use max volume seen
        });
      } else {
        // All entries have same price (flat day or stale data), use first entry
        aggregatedData.push({
          ...entries[0],
          date: dateKey,
          open: entries[0].open || entries[0].close,
          high: Math.max(...entries.map(e => e.high || e.close)),
          low: Math.min(...entries.map(e => e.low || e.close)),
          close: entries[entries.length - 1].close,
          volume: Math.max(...entries.map(e => e.volume))
        });
      }
    });

    // Sort by date ascending
    return aggregatedData.sort((a, b) =>
      new Date(a.date).getTime() - new Date(b.date).getTime()
    );
  }, [allPriceHistory]);

  // Filter price history based on selected period
  const getFilteredHistory = useCallback((period: TimePeriod): PriceData[] => {
    if (!sortedPriceHistory.length) return [];

    const now = new Date();
    let cutoffDate: Date;

    if (period === 'YTD') {
      cutoffDate = new Date(now.getFullYear(), 0, 1); // Jan 1st of current year
    } else {
      const days = periodDays[period];
      cutoffDate = new Date(now.getTime() - days * 24 * 60 * 60 * 1000);
    }

    const filtered = sortedPriceHistory.filter(p => new Date(p.date) >= cutoffDate);

    // If no data for selected period, return all available data
    if (filtered.length === 0) {
      return sortedPriceHistory;
    }

    return filtered;
  }, [sortedPriceHistory]);

  // Calculate High/Low for selected stats period
  const periodStats = useMemo(() => {
    const filtered = getFilteredHistory(statsPeriod);
    if (!filtered.length) return { high: 0, low: 0, avgVolume: 0, change: 0, changePercent: 0 };

    const high = Math.max(...filtered.map(p => p.high));
    const low = Math.min(...filtered.map(p => p.low));
    const avgVolume = filtered.reduce((sum, p) => sum + p.volume, 0) / filtered.length;

    const firstClose = filtered[0]?.close || 0;
    const lastClose = filtered[filtered.length - 1]?.close || 0;
    const change = lastClose - firstClose;
    const changePercent = firstClose ? (change / firstClose) * 100 : 0;

    return { high, low, avgVolume, change, changePercent };
  }, [getFilteredHistory, statsPeriod]);

  // Get price history for display
  const displayHistory = useMemo(() => {
    return getFilteredHistory(historyPeriod);
  }, [getFilteredHistory, historyPeriod]);

  // Get intraday data for selected date - use real data if available, otherwise simulate
  const intradayData = useMemo(() => {
    if (!selectedDate) return null;
    const dayData = sortedPriceHistory.find(p =>
      new Date(p.date).toLocaleDateString() === new Date(selectedDate).toLocaleDateString()
    );
    if (!dayData) return null;

    // If we have real intraday data, use it
    if (realIntradayData.length > 0) {
      const points = realIntradayData.map((point, idx) => {
        const timestamp = new Date(point.timestamp);
        const time = timestamp.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit', hour12: false });

        // Determine label based on price position
        let label = '';
        const isFirst = idx === 0;
        const isLast = idx === realIntradayData.length - 1;
        const isHigh = point.high === dayData.high || point.close === Math.max(...realIntradayData.map(p => p.close));
        const isLow = point.low === dayData.low || point.close === Math.min(...realIntradayData.map(p => p.close));

        if (isFirst) label = 'Open';
        else if (isLast) label = 'Close';
        else if (isHigh) label = 'High';
        else if (isLow) label = 'Low';

        const prevPrice = idx > 0 ? realIntradayData[idx - 1].close : point.open;
        const change = point.close - prevPrice;
        const changePercent = prevPrice ? (change / prevPrice) * 100 : 0;

        return {
          time,
          price: point.close,
          label,
          change,
          changePercent,
          volume: point.volume,
          open: point.open,
          high: point.high,
          low: point.low,
          close: point.close
        };
      });

      return { date: dayData.date, ohlcv: dayData, points, isRealData: true };
    }

    // Fall back to synthetic data generation
    const range = dayData.high - dayData.low;

    // Create a seed based on date for consistent random-like behavior
    const dateSeed = new Date(dayData.date).getTime();
    const pseudoRandom = (index: number) => {
      const x = Math.sin(dateSeed + index * 1000) * 10000;
      return x - Math.floor(x);
    };

    // Generate 14 time slots from 9:30 AM to 4:00 PM
    const timeSlots = [
      '09:30', '10:00', '10:30', '11:00', '11:30', '12:00', '12:30',
      '13:00', '13:30', '14:00', '14:30', '15:00', '15:30', '16:00'
    ];

    // Create price path that hits OHLC points
    const highIndex = Math.floor(pseudoRandom(1) * 6) + 2; // High between 10:30-12:30
    const lowIndex = Math.floor(pseudoRandom(2) * 4) + 7;  // Low between 1:00-2:30

    // First pass: calculate prices
    const pricePoints: { time: string; price: number; label: string }[] = [];

    timeSlots.forEach((time, idx) => {
      let price: number;
      let label = '';

      if (idx === 0) {
        price = dayData.open;
        label = 'Open';
      } else if (idx === timeSlots.length - 1) {
        price = dayData.close;
        label = 'Close';
      } else if (idx === highIndex) {
        price = dayData.high;
        label = 'High';
      } else if (idx === lowIndex) {
        price = dayData.low;
        label = 'Low';
      } else {
        // Interpolate with some randomness
        const progress = idx / (timeSlots.length - 1);
        const basePrice = dayData.open + (dayData.close - dayData.open) * progress;
        const variation = (pseudoRandom(idx) - 0.5) * range * 0.6;
        price = Math.max(dayData.low, Math.min(dayData.high, basePrice + variation));
      }

      pricePoints.push({ time, price, label });
    });

    // Second pass: calculate changes based on previous prices
    const finalPoints = pricePoints.map((p, idx) => {
      if (idx === 0) {
        return { ...p, change: 0, changePercent: 0 };
      }
      const prevPrice = pricePoints[idx - 1].price;
      const change = p.price - prevPrice;
      const changePercent = prevPrice ? (change / prevPrice) * 100 : 0;
      return { ...p, change, changePercent };
    });

    return { date: dayData.date, ohlcv: dayData, points: finalPoints, isRealData: false };
  }, [selectedDate, sortedPriceHistory, realIntradayData]);

  // Calculate daily price change
  const priceChange = latestPrice && sortedPriceHistory.length > 1
    ? latestPrice.close - sortedPriceHistory[sortedPriceHistory.length - 2]?.close
    : 0;
  const priceChangePercent = sortedPriceHistory.length > 1 && sortedPriceHistory[sortedPriceHistory.length - 2]?.close
    ? (priceChange / sortedPriceHistory[sortedPriceHistory.length - 2].close) * 100
    : 0;

  const PeriodSelector: FC<{
    value: TimePeriod;
    onChange: (period: TimePeriod) => void;
    label?: string;
  }> = ({ value, onChange, label }) => (
    <div className="flex items-center gap-2">
      {label && <span className="text-sm" style={{ color: 'var(--text-tertiary)' }}>{label}</span>}
      <div className="flex gap-1 p-1 rounded-lg bg-white/5">
        {(['1W', '1M', '3M', 'YTD', '1Y', 'ALL'] as TimePeriod[]).map((period) => (
          <button
            key={period}
            onClick={() => onChange(period)}
            className={`px-2 py-1 text-xs font-medium rounded transition-all ${
              value === period
                ? 'bg-blue-500/30 text-blue-400'
                : 'text-white/60 hover:text-white/80 hover:bg-white/10'
            }`}
          >
            {period}
          </button>
        ))}
      </div>
    </div>
  );

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center" style={{ background: 'var(--bg-primary)' }}>
        <LoadingSpinner size="lg" />
      </div>
    );
  }

  return (
    <div className="min-h-screen relative overflow-hidden" style={{ background: 'var(--bg-primary)' }}>
      {/* Background */}
      <div className="absolute inset-0 opacity-5" style={{ background: 'var(--bg-secondary)' }}></div>

      {/* Content */}
      <div className="relative z-10 p-4 pt-8 max-w-7xl mx-auto">
        {/* Header */}
        <div className="mb-6">
          <div className="flex items-center gap-4 mb-4">
            <GlassButton
              size="sm"
              variant="secondary"
              onClick={() => navigate(-1)}
              icon={<ArrowLeft className="w-4 h-4" />}
            >
              Back
            </GlassButton>
            <GlassButton
              size="sm"
              variant="accent"
              onClick={() => navigate(`/ai-analysis?ticker=${ticker}`)}
              icon={<Brain className="w-4 h-4" />}
            >
              AI Analysis
            </GlassButton>
            <GlassButton
              size="sm"
              onClick={() => loadData(true)}
              disabled={refreshing}
              icon={<RefreshCw className={`w-4 h-4 ${refreshing ? 'animate-spin' : ''}`} />}
            >
              Refresh
            </GlassButton>
          </div>

          <GlassContainer className="p-6">
            <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4">
              <div>
                <div className="flex items-center gap-3 mb-2">
                  <h1 className="text-3xl font-bold" style={{ color: 'var(--text-primary)' }}>
                    {ticker}
                  </h1>
                  {technicalAnalysis && (
                    <span className={`px-3 py-1 rounded-full text-sm font-semibold ${
                      technicalAnalysis.primarySignal === 'BULLISH'
                        ? 'bg-green-500/20 text-green-400'
                        : technicalAnalysis.primarySignal === 'BEARISH'
                        ? 'bg-red-500/20 text-red-400'
                        : 'bg-orange-500/20 text-orange-400'
                    }`}>
                      {technicalAnalysis.primarySignal}
                    </span>
                  )}
                </div>
                <p className="text-lg" style={{ color: 'var(--text-secondary)' }}>
                  {stockInfo?.name || ticker}
                </p>
                <p className="text-sm" style={{ color: 'var(--text-tertiary)' }}>
                  {stockInfo?.sector} {stockInfo?.industry && `• ${stockInfo.industry}`}
                </p>
              </div>

              {latestPrice && (
                <div className="text-right">
                  <div className="text-4xl font-bold" style={{ color: 'var(--text-primary)' }}>
                    ${latestPrice.close.toFixed(2)}
                  </div>
                  <div className={`flex items-center justify-end gap-2 text-lg ${
                    priceChange >= 0 ? 'text-green-400' : 'text-red-400'
                  }`}>
                    {priceChange >= 0 ? (
                      <TrendingUp className="w-5 h-5" />
                    ) : (
                      <TrendingDown className="w-5 h-5" />
                    )}
                    <span>
                      {priceChange >= 0 ? '+' : ''}{priceChange.toFixed(2)} ({priceChangePercent.toFixed(2)}%)
                    </span>
                  </div>
                  <div className="text-xs mt-1" style={{ color: 'var(--text-tertiary)' }}>
                    Daily Change
                  </div>
                </div>
              )}
            </div>
          </GlassContainer>
        </div>

        {error && (
          <GlassContainer variant="bearish" className="mb-6 p-4">
            <div className="flex items-center gap-3">
              <AlertCircle className="w-5 h-5 text-red-400" />
              <p style={{ color: 'var(--text-primary)' }}>{error}</p>
            </div>
          </GlassContainer>
        )}

        {/* Stats Grid with Period Selector */}
        <GlassContainer className="p-4 mb-6">
          <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 mb-4">
            <h2 className="text-lg font-bold flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
              <Calendar className="w-5 h-5" />
              Price Statistics
            </h2>
            <PeriodSelector value={statsPeriod} onChange={setStatsPeriod} />
          </div>

          <div className="grid grid-cols-2 md:grid-cols-5 gap-4">
            {latestPrice && (
              <>
                <div className="p-3 rounded-lg bg-white/5">
                  <div className="flex items-center gap-2 mb-1">
                    <DollarSign className="w-4 h-4" style={{ color: 'var(--text-tertiary)' }} />
                    <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>Current</span>
                  </div>
                  <div className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
                    ${latestPrice.close.toFixed(2)}
                  </div>
                </div>

                <div className="p-3 rounded-lg bg-white/5">
                  <div className="flex items-center gap-2 mb-1">
                    <TrendingUp className="w-4 h-4 text-green-400" />
                    <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>{statsPeriod} High</span>
                  </div>
                  <div className="text-lg font-bold text-green-400">
                    ${periodStats.high.toFixed(2)}
                  </div>
                </div>

                <div className="p-3 rounded-lg bg-white/5">
                  <div className="flex items-center gap-2 mb-1">
                    <TrendingDown className="w-4 h-4 text-red-400" />
                    <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>{statsPeriod} Low</span>
                  </div>
                  <div className="text-lg font-bold text-red-400">
                    ${periodStats.low.toFixed(2)}
                  </div>
                </div>

                <div className="p-3 rounded-lg bg-white/5">
                  <div className="flex items-center gap-2 mb-1">
                    <Activity className="w-4 h-4" style={{ color: 'var(--text-tertiary)' }} />
                    <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>{statsPeriod} Change</span>
                  </div>
                  <div className={`text-lg font-bold ${periodStats.change >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                    {periodStats.change >= 0 ? '+' : ''}{periodStats.changePercent.toFixed(2)}%
                  </div>
                </div>

                <div className="p-3 rounded-lg bg-white/5">
                  <div className="flex items-center gap-2 mb-1">
                    <Volume2 className="w-4 h-4" style={{ color: 'var(--text-tertiary)' }} />
                    <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>Avg Volume</span>
                  </div>
                  <div className="text-lg font-bold" style={{ color: 'var(--text-primary)' }}>
                    {(periodStats.avgVolume / 1000000).toFixed(2)}M
                  </div>
                </div>
              </>
            )}
          </div>
        </GlassContainer>

        {/* Technical Analysis */}
        {technicalAnalysis && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-6">
            <GlassContainer className="p-6">
              <h2 className="text-xl font-bold mb-4 flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
                <BarChart3 className="w-5 h-5" />
                Technical Analysis
              </h2>

              <div className="space-y-4">
                <div className="flex justify-between items-center">
                  <span style={{ color: 'var(--text-secondary)' }}>Composite Score</span>
                  <span className={`font-bold ${
                    technicalAnalysis.compositeScore >= 70 ? 'text-green-400' :
                    technicalAnalysis.compositeScore >= 40 ? 'text-orange-400' : 'text-red-400'
                  }`}>
                    {technicalAnalysis.compositeScore.toFixed(1)}/100
                  </span>
                </div>

                <div className="flex justify-between items-center">
                  <span style={{ color: 'var(--text-secondary)' }}>Signal Confidence</span>
                  <span className="font-bold" style={{ color: 'var(--text-primary)' }}>
                    {technicalAnalysis.signalConfidence.toFixed(1)}%
                  </span>
                </div>

                <div className="flex justify-between items-center">
                  <span style={{ color: 'var(--text-secondary)' }}>RSI</span>
                  <span className={`font-bold ${
                    technicalAnalysis.rsi > 70 ? 'text-red-400' :
                    technicalAnalysis.rsi < 30 ? 'text-green-400' : ''
                  }`} style={{ color: technicalAnalysis.rsi <= 70 && technicalAnalysis.rsi >= 30 ? 'var(--text-primary)' : undefined }}>
                    {technicalAnalysis.rsi.toFixed(1)}
                  </span>
                </div>

                <div className="flex justify-between items-center">
                  <span style={{ color: 'var(--text-secondary)' }}>Trend</span>
                  <span className={`font-bold ${
                    technicalAnalysis.trendDirection === 'UP' ? 'text-green-400' :
                    technicalAnalysis.trendDirection === 'DOWN' ? 'text-red-400' : 'text-orange-400'
                  }`}>
                    {technicalAnalysis.trendDirection}
                  </span>
                </div>
              </div>
            </GlassContainer>

            <GlassContainer className="p-6">
              <h2 className="text-xl font-bold mb-4 flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
                <Activity className="w-5 h-5" />
                Key Levels
              </h2>

              <div className="space-y-4">
                <div className="flex justify-between items-center">
                  <span style={{ color: 'var(--text-secondary)' }}>Support Level</span>
                  <span className="font-bold text-green-400">
                    ${technicalAnalysis.supportLevel.toFixed(2)}
                  </span>
                </div>

                <div className="flex justify-between items-center">
                  <span style={{ color: 'var(--text-secondary)' }}>Resistance Level</span>
                  <span className="font-bold text-red-400">
                    ${technicalAnalysis.resistanceLevel.toFixed(2)}
                  </span>
                </div>

                <div className="flex justify-between items-center">
                  <span style={{ color: 'var(--text-secondary)' }}>MACD</span>
                  <span className={`font-bold ${technicalAnalysis.macd >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                    {technicalAnalysis.macd.toFixed(4)}
                  </span>
                </div>
              </div>
            </GlassContainer>
          </div>
        )}

        {/* Signals */}
        {technicalAnalysis?.signals && technicalAnalysis.signals.length > 0 && (
          <GlassContainer className="p-6 mb-6">
            <h2 className="text-xl font-bold mb-4 flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
              <Newspaper className="w-5 h-5" />
              Active Signals
            </h2>
            <div className="flex flex-wrap gap-2">
              {technicalAnalysis.signals.map((signal, index) => (
                <motion.span
                  key={index}
                  initial={{ opacity: 0, scale: 0.9 }}
                  animate={{ opacity: 1, scale: 1 }}
                  transition={{ delay: index * 0.05 }}
                  className={`px-3 py-1 rounded-full text-sm ${
                    signal.toLowerCase().includes('bullish') || signal.toLowerCase().includes('oversold')
                      ? 'bg-green-500/20 text-green-400'
                      : signal.toLowerCase().includes('bearish') || signal.toLowerCase().includes('overbought')
                      ? 'bg-red-500/20 text-red-400'
                      : 'bg-blue-500/20 text-blue-400'
                  }`}
                >
                  {signal}
                </motion.span>
              ))}
            </div>
          </GlassContainer>
        )}

        {/* Price History with Period Selector - Grouped by Date */}
        {displayHistory.length > 0 && (
          <GlassContainer className="p-6">
            <div className="flex flex-col md:flex-row md:items-center md:justify-between gap-4 mb-6">
              <h2 className="text-xl font-bold flex items-center gap-2" style={{ color: 'var(--text-primary)' }}>
                <Clock className="w-5 h-5" />
                Price History
              </h2>
              <PeriodSelector value={historyPeriod} onChange={(p) => { setHistoryPeriod(p); setSelectedDate(null); setSelectedTimeSlot(null); }} />
            </div>

            {/* Date Cards Grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {displayHistory.slice().reverse().slice(0, 30).map((price, index, arr) => {
                const prevClose = arr[index + 1]?.close || price.open;
                const dayChange = price.close - prevClose;
                const dayChangePercent = prevClose ? (dayChange / prevClose) * 100 : 0;
                const isSelected = selectedDate === price.date;

                // Generate intraday data for this specific date
                const getIntradayForDate = () => {
                  const range = price.high - price.low;
                  const dateSeed = new Date(price.date).getTime();
                  const pseudoRandom = (idx: number) => {
                    const x = Math.sin(dateSeed + idx * 1000) * 10000;
                    return x - Math.floor(x);
                  };

                  const timeSlots = [
                    '09:30', '10:00', '10:30', '11:00', '11:30', '12:00', '12:30',
                    '13:00', '13:30', '14:00', '14:30', '15:00', '15:30', '16:00'
                  ];

                  const highIndex = Math.floor(pseudoRandom(1) * 6) + 2;
                  const lowIndex = Math.floor(pseudoRandom(2) * 4) + 7;

                  return timeSlots.map((time, idx) => {
                    let priceVal: number;
                    let label = '';

                    if (idx === 0) {
                      priceVal = price.open;
                      label = 'Open';
                    } else if (idx === timeSlots.length - 1) {
                      priceVal = price.close;
                      label = 'Close';
                    } else if (idx === highIndex) {
                      priceVal = price.high;
                      label = 'High';
                    } else if (idx === lowIndex) {
                      priceVal = price.low;
                      label = 'Low';
                    } else {
                      const progress = idx / (timeSlots.length - 1);
                      const basePrice = price.open + (price.close - price.open) * progress;
                      const variation = (pseudoRandom(idx) - 0.5) * range * 0.6;
                      priceVal = Math.max(price.low, Math.min(price.high, basePrice + variation));
                    }

                    return { time, price: priceVal, label };
                  });
                };

                const intradayPoints = isSelected ? getIntradayForDate() : [];

                return (
                  <motion.div
                    key={price.date}
                    initial={{ opacity: 0, y: 10 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ delay: index * 0.02 }}
                    className={`rounded-xl border transition-all cursor-pointer ${
                      isSelected
                        ? 'bg-blue-500/15 border-blue-500/40 ring-2 ring-blue-500/20'
                        : 'bg-white/5 border-white/10 hover:bg-white/10 hover:border-white/20'
                    }`}
                    onClick={() => {
                      setSelectedDate(isSelected ? null : price.date);
                      setSelectedTimeSlot(null);
                    }}
                  >
                    {/* Date Card Header */}
                    <div className="p-4">
                      <div className="flex items-center justify-between mb-3">
                        <div className="flex items-center gap-2">
                          <Calendar className={`w-4 h-4 ${isSelected ? 'text-blue-400' : ''}`} style={{ color: isSelected ? undefined : 'var(--text-tertiary)' }} />
                          <span className={`font-semibold ${isSelected ? 'text-blue-400' : ''}`} style={{ color: isSelected ? undefined : 'var(--text-primary)' }}>
                            {new Date(price.date).toLocaleDateString('en-US', {
                              weekday: 'short',
                              month: 'short',
                              day: 'numeric'
                            })}
                          </span>
                        </div>
                        <div className={`flex items-center gap-1 px-2 py-1 rounded-full text-sm font-semibold ${
                          dayChange >= 0 ? 'bg-green-500/20 text-green-400' : 'bg-red-500/20 text-red-400'
                        }`}>
                          {dayChange >= 0 ? <TrendingUp className="w-3 h-3" /> : <TrendingDown className="w-3 h-3" />}
                          {dayChange >= 0 ? '+' : ''}{dayChangePercent.toFixed(2)}%
                        </div>
                      </div>

                      {/* OHLCV Mini Grid */}
                      <div className="grid grid-cols-4 gap-2 text-center">
                        <div>
                          <div className="text-[10px] uppercase tracking-wide" style={{ color: 'var(--text-tertiary)' }}>Open</div>
                          <div className="text-sm font-medium" style={{ color: 'var(--text-primary)' }}>${price.open.toFixed(2)}</div>
                        </div>
                        <div>
                          <div className="text-[10px] uppercase tracking-wide text-green-400">High</div>
                          <div className="text-sm font-medium text-green-400">${price.high.toFixed(2)}</div>
                        </div>
                        <div>
                          <div className="text-[10px] uppercase tracking-wide text-red-400">Low</div>
                          <div className="text-sm font-medium text-red-400">${price.low.toFixed(2)}</div>
                        </div>
                        <div>
                          <div className="text-[10px] uppercase tracking-wide" style={{ color: 'var(--text-tertiary)' }}>Close</div>
                          <div className="text-sm font-bold" style={{ color: 'var(--text-primary)' }}>${price.close.toFixed(2)}</div>
                        </div>
                      </div>

                      {/* Volume */}
                      <div className="mt-2 flex items-center justify-between text-xs" style={{ color: 'var(--text-tertiary)' }}>
                        <span>Volume: {(price.volume / 1000000).toFixed(2)}M</span>
                        <span className="flex items-center gap-1">
                          {isSelected ? <ChevronUp className="w-3 h-3" /> : <ChevronDown className="w-3 h-3" />}
                          {isSelected ? 'Hide intraday' : 'View intraday'}
                        </span>
                      </div>
                    </div>

                    {/* Expanded Intraday Section */}
                    <AnimatePresence>
                      {isSelected && (
                        <motion.div
                          initial={{ height: 0, opacity: 0 }}
                          animate={{ height: 'auto', opacity: 1 }}
                          exit={{ height: 0, opacity: 0 }}
                          transition={{ duration: 0.2 }}
                          className="overflow-hidden"
                        >
                          <div className="px-4 pb-4 pt-2 border-t border-white/10">
                            {/* Data Source Badge */}
                            {loadingIntraday ? (
                              <div className="mb-4 flex items-center justify-center py-4">
                                <LoadingSpinner size="sm" />
                                <span className="ml-2 text-sm" style={{ color: 'var(--text-tertiary)' }}>Loading intraday data...</span>
                              </div>
                            ) : (
                              <>
                                <div className="mb-2 flex items-center gap-2">
                                  <span className={`text-xs px-2 py-0.5 rounded ${
                                    intradayData?.isRealData
                                      ? 'bg-green-500/20 text-green-400 border border-green-500/30'
                                      : 'bg-orange-500/20 text-orange-400 border border-orange-500/30'
                                  }`}>
                                    {intradayData?.isRealData ? 'Real Data' : 'Simulated'}
                                  </span>
                                  {intradayData?.isRealData && (
                                    <span className="text-xs" style={{ color: 'var(--text-tertiary)' }}>
                                      {intradayPoints.length} data points
                                    </span>
                                  )}
                                </div>

                            {/* Current Time Display */}
                            <div className="mb-4 p-3 rounded-lg bg-gradient-to-r from-blue-500/20 to-purple-500/20 border border-blue-500/30">
                              <div className="flex items-center justify-between mb-2">
                                <div className="flex items-center gap-2">
                                  <Clock className="w-4 h-4 text-blue-400" />
                                  <span className="text-lg font-bold text-blue-400">
                                    {intradayPoints[selectedTimeSlot ?? 0]?.time || '09:30'}
                                  </span>
                                  {intradayPoints[selectedTimeSlot ?? 0]?.label && (
                                    <span className={`text-xs px-2 py-0.5 rounded ${
                                      intradayPoints[selectedTimeSlot ?? 0].label === 'High' ? 'bg-green-500/30 text-green-400' :
                                      intradayPoints[selectedTimeSlot ?? 0].label === 'Low' ? 'bg-red-500/30 text-red-400' :
                                      'bg-blue-500/30 text-blue-400'
                                    }`}>
                                      {intradayPoints[selectedTimeSlot ?? 0].label}
                                    </span>
                                  )}
                                </div>
                                <div className="text-right">
                                  <div className="text-2xl font-bold" style={{ color: 'var(--text-primary)' }}>
                                    ${intradayPoints[selectedTimeSlot ?? 0]?.price.toFixed(2) || price.open.toFixed(2)}
                                  </div>
                                </div>
                              </div>

                              {/* Price change from open */}
                              {(() => {
                                const currentPoint = intradayPoints[selectedTimeSlot ?? 0];
                                const changeFromOpen = currentPoint ? currentPoint.price - price.open : 0;
                                const changePercent = price.open ? (changeFromOpen / price.open) * 100 : 0;
                                return (
                                  <div className={`text-sm ${changeFromOpen >= 0 ? 'text-green-400' : 'text-red-400'}`}>
                                    {changeFromOpen >= 0 ? '+' : ''}{changeFromOpen.toFixed(2)} ({changePercent.toFixed(2)}%) from open
                                  </div>
                                );
                              })()}
                            </div>

                            {/* Mini price chart line */}
                            <div className="mb-3 h-16 relative">
                              <svg className="w-full h-full" viewBox="0 0 100 40" preserveAspectRatio="none">
                                {/* Grid lines */}
                                <line x1="0" y1="10" x2="100" y2="10" stroke="rgba(255,255,255,0.1)" strokeWidth="0.5" />
                                <line x1="0" y1="20" x2="100" y2="20" stroke="rgba(255,255,255,0.1)" strokeWidth="0.5" />
                                <line x1="0" y1="30" x2="100" y2="30" stroke="rgba(255,255,255,0.1)" strokeWidth="0.5" />

                                {/* Price line */}
                                <polyline
                                  fill="none"
                                  stroke="url(#priceGradient)"
                                  strokeWidth="1.5"
                                  strokeLinecap="round"
                                  strokeLinejoin="round"
                                  points={intradayPoints.map((point, idx) => {
                                    const x = (idx / (intradayPoints.length - 1)) * 100;
                                    const range = price.high - price.low;
                                    const y = range > 0 ? 40 - ((point.price - price.low) / range) * 35 : 20;
                                    return `${x},${y}`;
                                  }).join(' ')}
                                />

                                {/* Gradient definition */}
                                <defs>
                                  <linearGradient id="priceGradient" x1="0%" y1="0%" x2="100%" y2="0%">
                                    <stop offset="0%" stopColor="#3b82f6" />
                                    <stop offset="50%" stopColor="#8b5cf6" />
                                    <stop offset="100%" stopColor="#3b82f6" />
                                  </linearGradient>
                                </defs>

                                {/* Current position indicator */}
                                {(() => {
                                  const idx = selectedTimeSlot ?? 0;
                                  const x = (idx / (intradayPoints.length - 1)) * 100;
                                  const range = price.high - price.low;
                                  const y = range > 0 ? 40 - ((intradayPoints[idx]?.price - price.low) / range) * 35 : 20;
                                  return (
                                    <circle cx={x} cy={y} r="3" fill="#3b82f6" stroke="white" strokeWidth="1.5" />
                                  );
                                })()}

                                {/* High/Low markers */}
                                {intradayPoints.map((point, idx) => {
                                  if (!point.label || point.label === 'Open' || point.label === 'Close') return null;
                                  const x = (idx / (intradayPoints.length - 1)) * 100;
                                  const range = price.high - price.low;
                                  const y = range > 0 ? 40 - ((point.price - price.low) / range) * 35 : 20;
                                  return (
                                    <circle
                                      key={idx}
                                      cx={x}
                                      cy={y}
                                      r="2"
                                      fill={point.label === 'High' ? '#22c55e' : '#ef4444'}
                                    />
                                  );
                                })}
                              </svg>
                            </div>

                            {/* Draggable Timeline Slider */}
                            <div className="relative" onClick={(e) => e.stopPropagation()}>
                              <input
                                type="range"
                                min="0"
                                max={intradayPoints.length - 1}
                                value={selectedTimeSlot ?? 0}
                                onChange={(e) => setSelectedTimeSlot(parseInt(e.target.value))}
                                className="w-full h-3 rounded-full appearance-none cursor-pointer"
                                style={{
                                  background: `linear-gradient(to right,
                                    #3b82f6 0%,
                                    #3b82f6 ${((selectedTimeSlot ?? 0) / (intradayPoints.length - 1)) * 100}%,
                                    rgba(255,255,255,0.2) ${((selectedTimeSlot ?? 0) / (intradayPoints.length - 1)) * 100}%,
                                    rgba(255,255,255,0.2) 100%)`
                                }}
                              />

                              {/* Time markers on track */}
                              <div className="flex justify-between mt-2">
                                {['9:30', '11:00', '12:30', '14:00', '16:00'].map((time, idx) => (
                                  <span key={time} className="text-[10px]" style={{ color: 'var(--text-tertiary)' }}>
                                    {time}
                                  </span>
                                ))}
                              </div>
                            </div>

                            {/* OHLC Key Levels */}
                            <div className="mt-4 grid grid-cols-4 gap-2 text-center text-xs">
                              <div
                                className={`p-2 rounded cursor-pointer transition-all ${
                                  intradayPoints[selectedTimeSlot ?? 0]?.label === 'Open'
                                    ? 'bg-blue-500/30 ring-1 ring-blue-400'
                                    : 'bg-white/5 hover:bg-white/10'
                                }`}
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setSelectedTimeSlot(0);
                                }}
                              >
                                <div style={{ color: 'var(--text-tertiary)' }}>Open</div>
                                <div className="font-semibold" style={{ color: 'var(--text-primary)' }}>${price.open.toFixed(2)}</div>
                              </div>
                              <div
                                className={`p-2 rounded cursor-pointer transition-all ${
                                  intradayPoints[selectedTimeSlot ?? 0]?.label === 'High'
                                    ? 'bg-green-500/30 ring-1 ring-green-400'
                                    : 'bg-white/5 hover:bg-white/10'
                                }`}
                                onClick={(e) => {
                                  e.stopPropagation();
                                  const highIdx = intradayPoints.findIndex(p => p.label === 'High');
                                  if (highIdx >= 0) setSelectedTimeSlot(highIdx);
                                }}
                              >
                                <div className="text-green-400">High</div>
                                <div className="font-semibold text-green-400">${price.high.toFixed(2)}</div>
                              </div>
                              <div
                                className={`p-2 rounded cursor-pointer transition-all ${
                                  intradayPoints[selectedTimeSlot ?? 0]?.label === 'Low'
                                    ? 'bg-red-500/30 ring-1 ring-red-400'
                                    : 'bg-white/5 hover:bg-white/10'
                                }`}
                                onClick={(e) => {
                                  e.stopPropagation();
                                  const lowIdx = intradayPoints.findIndex(p => p.label === 'Low');
                                  if (lowIdx >= 0) setSelectedTimeSlot(lowIdx);
                                }}
                              >
                                <div className="text-red-400">Low</div>
                                <div className="font-semibold text-red-400">${price.low.toFixed(2)}</div>
                              </div>
                              <div
                                className={`p-2 rounded cursor-pointer transition-all ${
                                  intradayPoints[selectedTimeSlot ?? 0]?.label === 'Close'
                                    ? 'bg-blue-500/30 ring-1 ring-blue-400'
                                    : 'bg-white/5 hover:bg-white/10'
                                }`}
                                onClick={(e) => {
                                  e.stopPropagation();
                                  setSelectedTimeSlot(intradayPoints.length - 1);
                                }}
                              >
                                <div style={{ color: 'var(--text-tertiary)' }}>Close</div>
                                <div className="font-semibold" style={{ color: 'var(--text-primary)' }}>${price.close.toFixed(2)}</div>
                              </div>
                            </div>
                              </>
                            )}
                          </div>
                        </motion.div>
                      )}
                    </AnimatePresence>
                  </motion.div>
                );
              })}
            </div>

            {displayHistory.length > 30 && (
              <div className="text-center mt-6 text-sm" style={{ color: 'var(--text-tertiary)' }}>
                Showing 30 of {displayHistory.length} days
              </div>
            )}
          </GlassContainer>
        )}
      </div>
    </div>
  );
};

export default StockDetails;
