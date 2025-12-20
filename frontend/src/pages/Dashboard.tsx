import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { SchedulerStatus } from '../components/SchedulerStatus';
import GlassContainer from '../components/GlassContainer';
import GlassButton from '../components/GlassButton';
import LoadingSpinner from '../components/LoadingSpinner';
import { RealTimeConnectionStatus } from '../components/RealTimePriceIndicator';
import { useRealTimePrices } from '../hooks/useRealTimePrices';
import {
  fetchAllTickers,
  fetchSentimentSummary,
  fetchLatestPredictions,
  fetchHealthStatus
} from '../services/api';

const Dashboard = () => {
  const [tickers, setTickers] = useState<string[]>([]);
  const [sentimentSummary, setSentimentSummary] = useState<any>(null);
  const [predictions, setPredictions] = useState<any[]>([]);
  const [healthStatus, setHealthStatus] = useState<any>(null);
  const [isLoading, setIsLoading] = useState(true);

  // Real-time price streaming
  const { prices, isConnected, subscribedCount, subscribeAll } = useRealTimePrices({
    autoSubscribe: false,
  });

  useEffect(() => {
    const loadDashboardData = async () => {
      try {
        setIsLoading(true);
        
        // Load data in parallel
        const [tickersRes, sentimentRes, predictionsRes, healthRes] = await Promise.allSettled([
          fetchAllTickers(),
          fetchSentimentSummary(24),
          fetchLatestPredictions(),
          fetchHealthStatus()
        ]);

        if (tickersRes.status === 'fulfilled') {
          setTickers(tickersRes.value);
        }
        if (sentimentRes.status === 'fulfilled') {
          setSentimentSummary(sentimentRes.value);
        }
        if (predictionsRes.status === 'fulfilled') {
          setPredictions(predictionsRes.value);
        }
        if (healthRes.status === 'fulfilled') {
          setHealthStatus(healthRes.value);
        }
      } catch (error) {
        console.error('Error loading dashboard data:', error);
      } finally {
        setIsLoading(false);
      }
    };

    loadDashboardData();
  }, []);

  const quickActions = [
    {
      title: 'Stock Screener',
      description: 'Find stocks with technical signals',
      path: '/screener',
      icon: '🔍',
      color: 'from-blue-500 to-cyan-500'
    },
    {
      title: 'AI Analysis',
      description: 'AI-powered stock analysis',
      path: '/ai-analysis',
      icon: '🧠',
      color: 'from-purple-500 to-pink-500'
    },
    {
      title: 'Backtesting',
      description: 'Test trading strategies',
      path: '/backtesting',
      icon: '📊',
      color: 'from-green-500 to-emerald-500'
    },
    {
      title: 'History',
      description: 'View signal history',
      path: '/predictions-history',
      icon: '📈',
      color: 'from-orange-500 to-amber-500'
    }
  ];

  return (
    <div className="container mx-auto px-4 py-8 space-y-8">
      <div className="text-center mb-8">
        <h1 className="text-4xl font-bold text-white mb-4">StockPulse Dashboard</h1>
        <p className="text-gray-400 text-lg">
          Real-time stock analysis with AI-powered sentiment and technical indicators
        </p>
      </div>

      {isLoading ? (
        <div className="flex justify-center">
          <LoadingSpinner />
        </div>
      ) : (
        <>
          {/* Quick Actions */}
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
            {quickActions.map((action) => (
              <Link key={action.title} to={action.path}>
                <GlassContainer className="p-6 hover:scale-105 transition-transform cursor-pointer">
                  <div className={`w-12 h-12 rounded-full bg-gradient-to-r ${action.color} flex items-center justify-center text-2xl mb-4`}>
                    {action.icon}
                  </div>
                  <h3 className="text-xl font-semibold text-white mb-2">{action.title}</h3>
                  <p className="text-gray-400 text-sm">{action.description}</p>
                </GlassContainer>
              </Link>
            ))}
          </div>

          {/* System Status */}
          <SchedulerStatus />

          {/* Quick Stats */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            {/* Market Overview */}
            <GlassContainer className="p-6">
              <h3 className="text-xl font-semibold text-white mb-4">Market Overview</h3>
              <div className="space-y-3">
                <div className="flex justify-between items-center">
                  <span className="text-gray-400">Active Tickers</span>
                  <span className="text-white font-medium">{tickers.length}</span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-gray-400">System Status</span>
                  <span className={`font-medium ${healthStatus?.status === 'UP' ? 'text-green-400' : 'text-red-400'}`}>
                    {healthStatus?.status || 'Unknown'}
                  </span>
                </div>
                <div className="flex justify-between items-center">
                  <span className="text-gray-400">Real-Time Feed</span>
                  <RealTimeConnectionStatus isConnected={isConnected} subscribedCount={subscribedCount} />
                </div>
                {!isConnected && (
                  <button
                    onClick={subscribeAll}
                    className="w-full mt-2 px-4 py-2 bg-gradient-to-r from-green-500 to-emerald-500 text-white rounded-lg text-sm hover:brightness-110 transition-all"
                  >
                    Enable Live Streaming
                  </button>
                )}
              </div>
            </GlassContainer>

            {/* Sentiment Summary */}
            <GlassContainer className="p-6">
              <h3 className="text-xl font-semibold text-white mb-4">Sentiment Summary</h3>
              {sentimentSummary ? (
                <div className="space-y-3">
                  <div className="flex justify-between items-center">
                    <span className="text-gray-400">Overall Sentiment</span>
                    <span className={`font-medium ${
                      sentimentSummary.overallSentiment > 0 ? 'text-green-400' : 
                      sentimentSummary.overallSentiment < 0 ? 'text-red-400' : 'text-gray-400'
                    }`}>
                      {(sentimentSummary.overallSentiment * 100).toFixed(1)}%
                    </span>
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="text-gray-400">Total News</span>
                    <span className="text-white font-medium">{sentimentSummary.totalNews}</span>
                  </div>
                  <div className="flex justify-between items-center">
                    <span className="text-gray-400">Analyzed</span>
                    <span className="text-white font-medium">{sentimentSummary.totalSentiments}</span>
                  </div>
                </div>
              ) : (
                <div className="text-gray-400">No sentiment data available</div>
              )}
            </GlassContainer>

            {/* Recent Activity */}
            <GlassContainer className="p-6">
              <h3 className="text-xl font-semibold text-white mb-4">Recent Activity</h3>
              <div className="space-y-3">
                <div className="flex items-center gap-3">
                  <div className="w-2 h-2 bg-green-400 rounded-full"></div>
                  <span className="text-gray-400 text-sm">Price data updated</span>
                </div>
                <div className="flex items-center gap-3">
                  <div className="w-2 h-2 bg-blue-400 rounded-full"></div>
                  <span className="text-gray-400 text-sm">Sentiment analysis running</span>
                </div>
                <div className="flex items-center gap-3">
                  <div className="w-2 h-2 bg-purple-400 rounded-full"></div>
                  <span className="text-gray-400 text-sm">Screening completed</span>
                </div>
                <div className="flex items-center gap-3">
                  <div className="w-2 h-2 bg-yellow-400 rounded-full"></div>
                  <span className="text-gray-400 text-sm">Predictions generated</span>
                </div>
              </div>
            </GlassContainer>
          </div>

          {/* Featured Tickers with Real-Time Prices */}
          {tickers.length > 0 && (
            <GlassContainer className="p-6">
              <div className="flex justify-between items-center mb-4">
                <h3 className="text-xl font-semibold text-white">Featured Tickers</h3>
                {isConnected && (
                  <span className="flex items-center gap-2 text-xs text-green-400">
                    <span className="relative flex h-2 w-2">
                      <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
                      <span className="relative inline-flex rounded-full h-2 w-2 bg-green-500"></span>
                    </span>
                    LIVE
                  </span>
                )}
              </div>
              <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-8 gap-3">
                {tickers.slice(0, 8).map((ticker) => {
                  const livePrice = prices[ticker];
                  const isPositive = livePrice?.change >= 0;
                  return (
                    <Link
                      key={ticker}
                      to={`/analysis/${ticker}`}
                      className="bg-gray-800/50 rounded-lg p-3 text-center hover:bg-gray-700/50 transition-colors"
                    >
                      <div className="text-white font-medium">{ticker}</div>
                      {livePrice && (
                        <>
                          <div className="text-sm text-gray-300 font-mono mt-1">
                            ${livePrice.price.toFixed(2)}
                          </div>
                          <div className={`text-xs font-mono ${isPositive ? 'text-green-400' : 'text-red-400'}`}>
                            {isPositive ? '+' : ''}{livePrice.changePercent.toFixed(2)}%
                          </div>
                        </>
                      )}
                    </Link>
                  );
                })}
              </div>
            </GlassContainer>
          )}
        </>
      )}
    </div>
  );
};

export default Dashboard;