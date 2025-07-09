import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { SchedulerStatus } from '../components/SchedulerStatus';
import GlassContainer from '../components/GlassContainer';
import GlassButton from '../components/GlassButton';
import LoadingSpinner from '../components/LoadingSpinner';
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
      title: 'LLM Sentiment',
      description: 'AI-powered sentiment analysis',
      path: '/llm-sentiment',
      icon: '🧠',
      color: 'from-purple-500 to-pink-500'
    },
    {
      title: 'Predictions',
      description: 'Latest ML predictions',
      path: '/predictions',
      icon: '🎯',
      color: 'from-green-500 to-emerald-500'
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
                  <span className="text-gray-400">Data Sources</span>
                  <span className="text-white font-medium">Java Backend</span>
                </div>
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

          {/* Featured Tickers */}
          {tickers.length > 0 && (
            <GlassContainer className="p-6">
              <h3 className="text-xl font-semibold text-white mb-4">Featured Tickers</h3>
              <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-8 gap-3">
                {tickers.slice(0, 8).map((ticker) => (
                  <Link 
                    key={ticker} 
                    to={`/analysis/${ticker}`}
                    className="bg-gray-800/50 rounded-lg p-3 text-center hover:bg-gray-700/50 transition-colors"
                  >
                    <div className="text-white font-medium">{ticker}</div>
                  </Link>
                ))}
              </div>
            </GlassContainer>
          )}
        </>
      )}
    </div>
  );
};

export default Dashboard;