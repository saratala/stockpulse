import React, { useState, useEffect } from 'react';
import GlassContainer from '../components/GlassContainer';
import LoadingSpinner from '../components/LoadingSpinner';
import { fetchValidationDashboard, triggerValidationRun } from '../services/api';

interface AccuracyData {
  totalPredictions: number;
  correctDirection: number;
  directionAccuracy: number;
  avgErrorPercent: number;
}

interface CalibrationBucket {
  bucket: string;
  total: number;
  correct: number;
  accuracy: number;
}

interface SignalPerformance {
  total: number;
  correct: number;
  accuracy: number;
}

interface TickerRanking {
  ticker: string;
  accuracy: number;
}

interface ValidationRecord {
  ticker: string;
  timeframe: string;
  signalType: string;
  predictedPrice: number;
  actualPrice: number;
  priceErrorPercent: number;
  directionCorrect: boolean;
  confidence: number;
  validationTimestamp: string;
}

interface PaperTradingStats {
  initialBalance: number;
  currentBalance: number;
  totalReturn: number;
  totalTrades: number;
  winningTrades: number;
  losingTrades: number;
  winRate: number;
  totalRealizedPnl: number;
  maxDrawdown: number;
  maxDrawdownPercent: number;
  openTrades: number;
}

const PredictionAccuracy: React.FC = () => {
  const [isLoading, setIsLoading] = useState(true);
  const [days, setDays] = useState(7);
  const [selectedTimeframe, setSelectedTimeframe] = useState('1d');
  const [dashboardData, setDashboardData] = useState<any>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);

  useEffect(() => {
    loadDashboardData();
  }, [days]);

  const loadDashboardData = async () => {
    try {
      setIsLoading(true);
      const data = await fetchValidationDashboard(days);
      setDashboardData(data);
    } catch (error) {
      console.error('Error loading validation dashboard:', error);
    } finally {
      setIsLoading(false);
    }
  };

  const handleRunValidation = async () => {
    try {
      setIsRefreshing(true);
      await triggerValidationRun();
      await loadDashboardData();
    } catch (error) {
      console.error('Error running validation:', error);
    } finally {
      setIsRefreshing(false);
    }
  };

  const getAccuracyColor = (accuracy: number) => {
    if (accuracy >= 60) return 'text-green-400';
    if (accuracy >= 50) return 'text-yellow-400';
    return 'text-red-400';
  };

  const getAccuracyBgColor = (accuracy: number) => {
    if (accuracy >= 60) return 'bg-green-500/20';
    if (accuracy >= 50) return 'bg-yellow-500/20';
    return 'bg-red-500/20';
  };

  if (isLoading) {
    return (
      <div className="container mx-auto px-4 py-8 flex justify-center">
        <LoadingSpinner />
      </div>
    );
  }

  const accuracy = dashboardData?.accuracy || {};
  const rankings = dashboardData?.rankings?.[selectedTimeframe] || {};
  const recentValidations = dashboardData?.recentValidations || [];
  const paperTrading = dashboardData?.paperTrading || {};
  const calibration = accuracy?.confidenceCalibration || [];
  const signalPerformance = accuracy?.signalTypePerformance || {};

  return (
    <div className="container mx-auto px-4 py-8 space-y-6">
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h1 className="text-3xl font-bold text-white">Prediction Accuracy</h1>
          <p className="text-gray-400 mt-1">Track how well our predictions match actual outcomes</p>
        </div>
        <div className="flex items-center gap-4">
          <select
            value={days}
            onChange={(e) => setDays(Number(e.target.value))}
            className="bg-gray-800 text-white rounded-lg px-4 py-2 border border-gray-700"
          >
            <option value={7}>Last 7 days</option>
            <option value={14}>Last 14 days</option>
            <option value={30}>Last 30 days</option>
            <option value={90}>Last 90 days</option>
          </select>
          <button
            onClick={handleRunValidation}
            disabled={isRefreshing}
            className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
          >
            {isRefreshing ? 'Running...' : 'Run Validation'}
          </button>
        </div>
      </div>

      {/* Accuracy Overview Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {['1h', '1d', '1w'].map((tf) => {
          const tfData = accuracy[tf] || {};
          const acc = tfData.directionAccuracy || 0;
          return (
            <GlassContainer key={tf} className="p-6">
              <div className="flex justify-between items-start mb-4">
                <div>
                  <h3 className="text-lg font-semibold text-white">
                    {tf === '1h' ? '1 Hour' : tf === '1d' ? '1 Day' : '1 Week'} Predictions
                  </h3>
                  <p className="text-gray-400 text-sm">{tfData.totalPredictions || 0} predictions validated</p>
                </div>
                <div className={`text-3xl font-bold ${getAccuracyColor(acc)}`}>
                  {acc.toFixed(1)}%
                </div>
              </div>
              <div className="space-y-2">
                <div className="flex justify-between text-sm">
                  <span className="text-gray-400">Correct Direction</span>
                  <span className="text-white">{tfData.correctDirection || 0}</span>
                </div>
                <div className="flex justify-between text-sm">
                  <span className="text-gray-400">Avg Error</span>
                  <span className="text-white">{(tfData.avgErrorPercent || 0).toFixed(2)}%</span>
                </div>
              </div>
              {/* Progress bar */}
              <div className="mt-4 h-2 bg-gray-700 rounded-full overflow-hidden">
                <div
                  className={`h-full transition-all ${acc >= 60 ? 'bg-green-500' : acc >= 50 ? 'bg-yellow-500' : 'bg-red-500'}`}
                  style={{ width: `${Math.min(acc, 100)}%` }}
                />
              </div>
            </GlassContainer>
          );
        })}
      </div>

      {/* Signal Type Performance */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <GlassContainer className="p-6">
          <h3 className="text-xl font-semibold text-white mb-4">Signal Type Performance</h3>
          <div className="space-y-4">
            {Object.entries(signalPerformance).map(([signal, data]: [string, any]) => (
              <div key={signal} className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <span className={`px-3 py-1 rounded-full text-sm font-medium ${
                    signal === 'BULLISH' ? 'bg-green-500/20 text-green-400' :
                    signal === 'BEARISH' ? 'bg-red-500/20 text-red-400' :
                    'bg-gray-500/20 text-gray-400'
                  }`}>
                    {signal}
                  </span>
                  <span className="text-gray-400 text-sm">{data.total} predictions</span>
                </div>
                <div className={`text-lg font-semibold ${getAccuracyColor(data.accuracy)}`}>
                  {data.accuracy.toFixed(1)}% ({data.correct}/{data.total})
                </div>
              </div>
            ))}
          </div>
        </GlassContainer>

        {/* Confidence Calibration */}
        <GlassContainer className="p-6">
          <h3 className="text-xl font-semibold text-white mb-4">Confidence Calibration</h3>
          <p className="text-gray-400 text-sm mb-4">
            Do our confidence scores match actual accuracy?
          </p>
          <div className="space-y-3">
            {calibration.map((bucket: CalibrationBucket) => (
              <div key={bucket.bucket} className="flex items-center gap-4">
                <span className="text-gray-400 w-16 text-sm">{bucket.bucket}%</span>
                <div className="flex-1 h-6 bg-gray-700 rounded-full overflow-hidden relative">
                  <div
                    className={`h-full ${getAccuracyBgColor(bucket.accuracy)}`}
                    style={{ width: `${Math.min(bucket.accuracy, 100)}%` }}
                  />
                  <span className="absolute inset-0 flex items-center justify-center text-xs text-white">
                    {bucket.accuracy.toFixed(1)}% ({bucket.correct}/{bucket.total})
                  </span>
                </div>
              </div>
            ))}
          </div>
        </GlassContainer>
      </div>

      {/* Ticker Rankings */}
      <GlassContainer className="p-6">
        <div className="flex justify-between items-center mb-4">
          <h3 className="text-xl font-semibold text-white">Ticker Rankings</h3>
          <div className="flex gap-2">
            {['1h', '1d', '1w'].map((tf) => (
              <button
                key={tf}
                onClick={() => setSelectedTimeframe(tf)}
                className={`px-3 py-1 rounded-lg text-sm ${
                  selectedTimeframe === tf
                    ? 'bg-blue-600 text-white'
                    : 'bg-gray-700 text-gray-300 hover:bg-gray-600'
                }`}
              >
                {tf === '1h' ? '1 Hour' : tf === '1d' ? '1 Day' : '1 Week'}
              </button>
            ))}
          </div>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {/* Top Performers */}
          <div>
            <h4 className="text-green-400 font-medium mb-3">Top Performers</h4>
            <div className="space-y-2">
              {(rankings.topPerformers || []).slice(0, 5).map((item: TickerRanking, idx: number) => (
                <div key={item.ticker} className="flex items-center justify-between bg-gray-800/50 rounded-lg px-4 py-2">
                  <div className="flex items-center gap-3">
                    <span className="text-gray-500 text-sm">#{idx + 1}</span>
                    <span className="text-white font-medium">{item.ticker}</span>
                  </div>
                  <span className="text-green-400 font-semibold">{item.accuracy.toFixed(1)}%</span>
                </div>
              ))}
              {(!rankings.topPerformers || rankings.topPerformers.length === 0) && (
                <p className="text-gray-500 text-sm">No data available yet</p>
              )}
            </div>
          </div>
          {/* Worst Performers */}
          <div>
            <h4 className="text-red-400 font-medium mb-3">Needs Improvement</h4>
            <div className="space-y-2">
              {(rankings.worstPerformers || []).slice(0, 5).map((item: TickerRanking, idx: number) => (
                <div key={item.ticker} className="flex items-center justify-between bg-gray-800/50 rounded-lg px-4 py-2">
                  <div className="flex items-center gap-3">
                    <span className="text-gray-500 text-sm">#{idx + 1}</span>
                    <span className="text-white font-medium">{item.ticker}</span>
                  </div>
                  <span className="text-red-400 font-semibold">{item.accuracy.toFixed(1)}%</span>
                </div>
              ))}
              {(!rankings.worstPerformers || rankings.worstPerformers.length === 0) && (
                <p className="text-gray-500 text-sm">No data available yet</p>
              )}
            </div>
          </div>
        </div>
      </GlassContainer>

      {/* Paper Trading Stats */}
      <GlassContainer className="p-6">
        <h3 className="text-xl font-semibold text-white mb-4">Paper Trading Performance</h3>
        <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
          <div className="bg-gray-800/50 rounded-lg p-4 text-center">
            <p className="text-gray-400 text-sm">Initial Balance</p>
            <p className="text-white text-xl font-semibold">${(paperTrading.initialBalance || 100000).toLocaleString()}</p>
          </div>
          <div className="bg-gray-800/50 rounded-lg p-4 text-center">
            <p className="text-gray-400 text-sm">Current Balance</p>
            <p className="text-white text-xl font-semibold">${(paperTrading.currentBalance || 100000).toLocaleString()}</p>
          </div>
          <div className="bg-gray-800/50 rounded-lg p-4 text-center">
            <p className="text-gray-400 text-sm">Total Return</p>
            <p className={`text-xl font-semibold ${(paperTrading.totalReturn || 0) >= 0 ? 'text-green-400' : 'text-red-400'}`}>
              {(paperTrading.totalReturn || 0) >= 0 ? '+' : ''}{(paperTrading.totalReturn || 0).toFixed(2)}%
            </p>
          </div>
          <div className="bg-gray-800/50 rounded-lg p-4 text-center">
            <p className="text-gray-400 text-sm">Win Rate</p>
            <p className={`text-xl font-semibold ${getAccuracyColor(paperTrading.winRate || 0)}`}>
              {(paperTrading.winRate || 0).toFixed(1)}%
            </p>
          </div>
          <div className="bg-gray-800/50 rounded-lg p-4 text-center">
            <p className="text-gray-400 text-sm">Total Trades</p>
            <p className="text-white text-xl font-semibold">
              {paperTrading.totalTrades || 0}
              <span className="text-sm text-gray-400 ml-1">
                ({paperTrading.winningTrades || 0}W / {paperTrading.losingTrades || 0}L)
              </span>
            </p>
          </div>
          <div className="bg-gray-800/50 rounded-lg p-4 text-center">
            <p className="text-gray-400 text-sm">Max Drawdown</p>
            <p className="text-red-400 text-xl font-semibold">
              {(paperTrading.maxDrawdownPercent || 0).toFixed(2)}%
            </p>
          </div>
        </div>
      </GlassContainer>

      {/* Recent Validations */}
      <GlassContainer className="p-6">
        <h3 className="text-xl font-semibold text-white mb-4">Recent Validations</h3>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="text-left text-gray-400 text-sm border-b border-gray-700">
                <th className="pb-3 px-2">Ticker</th>
                <th className="pb-3 px-2">Timeframe</th>
                <th className="pb-3 px-2">Signal</th>
                <th className="pb-3 px-2">Confidence</th>
                <th className="pb-3 px-2">Predicted</th>
                <th className="pb-3 px-2">Actual</th>
                <th className="pb-3 px-2">Error</th>
                <th className="pb-3 px-2">Result</th>
              </tr>
            </thead>
            <tbody>
              {recentValidations.slice(0, 15).map((v: ValidationRecord, idx: number) => (
                <tr key={idx} className="border-b border-gray-700/50 hover:bg-gray-800/30">
                  <td className="py-3 px-2 font-medium text-white">{v.ticker}</td>
                  <td className="py-3 px-2 text-gray-300">{v.timeframe}</td>
                  <td className="py-3 px-2">
                    <span className={`px-2 py-1 rounded text-xs ${
                      v.signalType === 'BULLISH' ? 'bg-green-500/20 text-green-400' :
                      v.signalType === 'BEARISH' ? 'bg-red-500/20 text-red-400' :
                      'bg-gray-500/20 text-gray-400'
                    }`}>
                      {v.signalType}
                    </span>
                  </td>
                  <td className="py-3 px-2 text-gray-300">{v.confidence.toFixed(0)}%</td>
                  <td className="py-3 px-2 font-mono text-gray-300">${v.predictedPrice.toFixed(2)}</td>
                  <td className="py-3 px-2 font-mono text-gray-300">${v.actualPrice.toFixed(2)}</td>
                  <td className="py-3 px-2 font-mono text-gray-300">{v.priceErrorPercent.toFixed(2)}%</td>
                  <td className="py-3 px-2">
                    {v.directionCorrect ? (
                      <span className="text-green-400 font-medium">CORRECT</span>
                    ) : (
                      <span className="text-red-400 font-medium">WRONG</span>
                    )}
                  </td>
                </tr>
              ))}
              {recentValidations.length === 0 && (
                <tr>
                  <td colSpan={8} className="py-8 text-center text-gray-500">
                    No validations yet. Predictions will be validated as they expire.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </GlassContainer>

      {/* Info Box */}
      <GlassContainer className="p-6 border-l-4 border-blue-500">
        <h4 className="text-white font-semibold mb-2">How Validation Works</h4>
        <ul className="text-gray-400 text-sm space-y-1">
          <li>- Predictions are automatically validated when their timeframe expires (1h, 1d, 1w)</li>
          <li>- Direction accuracy measures if we correctly predicted up/down movement</li>
          <li>- Confidence calibration shows if our confidence scores match actual success rates</li>
          <li>- Paper trading simulates real trades to measure potential P&L</li>
          <li>- A well-calibrated system should have 80% confidence predictions succeed ~80% of the time</li>
        </ul>
      </GlassContainer>
    </div>
  );
};

export default PredictionAccuracy;
