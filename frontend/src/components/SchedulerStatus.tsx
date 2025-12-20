import React, { useState, useEffect } from 'react';
import { fetchHealthStatus } from '../services/api';
import GlassContainer from './GlassContainer';
import LoadingSpinner from './LoadingSpinner';

interface HealthStatus {
  service: string;
  status: string;
  timestamp: string;
}

export const SchedulerStatus: React.FC = () => {
  const [healthStatus, setHealthStatus] = useState<HealthStatus | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastUpdated, setLastUpdated] = useState<Date>(new Date());

  useEffect(() => {
    const loadHealthStatus = async () => {
      try {
        setIsLoading(true);
        const status = await fetchHealthStatus();
        setHealthStatus(status);
        setLastUpdated(new Date());
        setError(null);
      } catch (err) {
        setError('Failed to load health status');
        console.error('Error loading health status:', err);
      } finally {
        setIsLoading(false);
      }
    };

    loadHealthStatus();
    
    // Auto-refresh every 30 seconds
    const interval = setInterval(loadHealthStatus, 30000);
    
    return () => clearInterval(interval);
  }, []);

  const getStatusColor = (status: string) => {
    switch (status.toLowerCase()) {
      case 'up':
      case 'healthy':
        return 'text-emerald-400';
      case 'down':
      case 'unhealthy':
        return 'text-red-400';
      default:
        return 'text-amber-400';
    }
  };

  const getStatusIcon = (status: string) => {
    switch (status.toLowerCase()) {
      case 'up':
      case 'healthy':
        return '●';
      case 'down':
      case 'unhealthy':
        return '●';
      default:
        return '●';
    }
  };

  const schedulerInfo = [
    {
      name: 'Price Updates',
      interval: 'Every 5 minutes',
      description: 'Real-time stock price data from Yahoo Finance',
      icon: '📈'
    },
    {
      name: 'Sentiment Analysis',
      interval: 'Every 10 minutes',
      description: 'LLM-powered sentiment analysis of recent news',
      icon: '🧠'
    },
    {
      name: 'News Fetching',
      interval: 'Every 2 hours',
      description: 'News articles from multiple sources',
      icon: '📰'
    },
    {
      name: 'Screening',
      interval: 'Every 15 minutes',
      description: 'Stock screening and signal generation',
      icon: '🔍'
    },
    {
      name: 'Predictions',
      interval: 'Every 30 minutes',
      description: 'Machine learning predictions and signals',
      icon: '🎯'
    }
  ];

  if (isLoading) {
    return (
      <GlassContainer className="p-6">
        <div className="flex items-center justify-center">
          <LoadingSpinner />
        </div>
      </GlassContainer>
    );
  }

  return (
    <GlassContainer className="p-6">
      <div className="flex items-center justify-between mb-6">
        <h3 className="text-xl font-semibold text-slate-50">System Status & Schedulers</h3>
        <div className="text-sm text-slate-400">
          Last updated: {lastUpdated.toLocaleTimeString()}
        </div>
      </div>

      {error && (
        <div className="bg-red-500/10 border border-red-500/30 rounded-lg p-4 text-red-400 mb-6">
          {error}
        </div>
      )}

      {/* System Health */}
      {healthStatus && (
        <div className="mb-6">
          <h4 className="text-lg font-semibold text-slate-50 mb-3">System Health</h4>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="bg-slate-800/50 rounded-lg p-4 border border-slate-700/50">
              <div className="flex items-center justify-between">
                <span className="text-slate-400">Service</span>
                <span className="text-slate-200 font-medium">{healthStatus.service}</span>
              </div>
            </div>
            <div className="bg-slate-800/50 rounded-lg p-4 border border-slate-700/50">
              <div className="flex items-center justify-between">
                <span className="text-slate-400">Status</span>
                <span className={`${getStatusColor(healthStatus.status)} flex items-center gap-2 font-medium`}>
                  {getStatusIcon(healthStatus.status)} {healthStatus.status.toUpperCase()}
                </span>
              </div>
            </div>
            <div className="bg-slate-800/50 rounded-lg p-4 border border-slate-700/50">
              <div className="flex items-center justify-between">
                <span className="text-slate-400">Last Check</span>
                <span className="text-slate-200 text-sm">
                  {new Date(healthStatus.timestamp).toLocaleTimeString()}
                </span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Scheduler Information */}
      <div>
        <h4 className="text-lg font-semibold text-slate-50 mb-3">Active Schedulers</h4>
        <div className="space-y-3">
          {schedulerInfo.map((scheduler) => (
            <div key={scheduler.name} className="bg-slate-800/50 rounded-lg p-4 border border-slate-700/50 hover:border-indigo-500/30 transition-colors">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <span className="text-2xl">{scheduler.icon}</span>
                  <div>
                    <div className="text-slate-100 font-medium">{scheduler.name}</div>
                    <div className="text-sm text-slate-400">{scheduler.description}</div>
                  </div>
                </div>
                <div className="text-right">
                  <div className="text-sm text-indigo-400 font-medium">{scheduler.interval}</div>
                  <div className="text-xs text-emerald-400">● Active</div>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Additional Info */}
      <div className="mt-6 p-4 bg-indigo-500/10 border border-indigo-500/20 rounded-lg">
        <div className="flex items-center gap-2 mb-2">
          <span className="text-indigo-400">ℹ️</span>
          <span className="text-indigo-400 font-medium">Scheduler Information</span>
        </div>
        <div className="text-sm text-slate-400">
          All schedulers are running automatically in the background. The Java Spring Boot application
          handles scheduling with cron expressions for precise timing. Health checks are performed every
          30 seconds to ensure system stability.
        </div>
      </div>
    </GlassContainer>
  );
};