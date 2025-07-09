import React, { useState, useEffect } from 'react';
import { fetchHealthStatus } from '../services/api';
import GlassContainer from './GlassContainer';
import LoadingSpinner from './LoadingSpinner';

interface HealthStatus {
  status: string;
  components: {
    db: {
      status: string;
      details: {
        database: string;
        validationQuery: string;
      };
    };
    redis: {
      status: string;
      details: {
        version: string;
      };
    };
    diskSpace: {
      status: string;
      details: {
        total: number;
        free: number;
        threshold: number;
        path: string;
        exists: boolean;
      };
    };
    ping: {
      status: string;
    };
  };
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
        return 'text-green-400';
      case 'down':
        return 'text-red-400';
      default:
        return 'text-yellow-400';
    }
  };

  const getStatusIcon = (status: string) => {
    switch (status.toLowerCase()) {
      case 'up':
        return '✅';
      case 'down':
        return '❌';
      default:
        return '⚠️';
    }
  };

  const formatBytes = (bytes: number) => {
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
    if (bytes === 0) return '0 Bytes';
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return Math.round(bytes / Math.pow(1024, i) * 100) / 100 + ' ' + sizes[i];
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
        <h3 className="text-xl font-semibold text-white">System Status & Schedulers</h3>
        <div className="text-sm text-gray-400">
          Last updated: {lastUpdated.toLocaleTimeString()}
        </div>
      </div>

      {error && (
        <div className="bg-red-500/10 border border-red-500/20 rounded-lg p-4 text-red-400 mb-6">
          {error}
        </div>
      )}

      {/* System Health */}
      {healthStatus && (
        <div className="mb-6">
          <h4 className="text-lg font-semibold text-white mb-3">System Health</h4>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="bg-gray-800/50 rounded-lg p-4">
              <div className="flex items-center justify-between">
                <span className="text-gray-400">Overall</span>
                <span className={`${getStatusColor(healthStatus.status)} flex items-center gap-1`}>
                  {getStatusIcon(healthStatus.status)} {healthStatus.status}
                </span>
              </div>
            </div>
            <div className="bg-gray-800/50 rounded-lg p-4">
              <div className="flex items-center justify-between">
                <span className="text-gray-400">Database</span>
                <span className={`${getStatusColor(healthStatus.components.db.status)} flex items-center gap-1`}>
                  {getStatusIcon(healthStatus.components.db.status)} {healthStatus.components.db.status}
                </span>
              </div>
              <div className="text-xs text-gray-500 mt-1">
                {healthStatus.components.db.details.database}
              </div>
            </div>
            <div className="bg-gray-800/50 rounded-lg p-4">
              <div className="flex items-center justify-between">
                <span className="text-gray-400">Redis</span>
                <span className={`${getStatusColor(healthStatus.components.redis.status)} flex items-center gap-1`}>
                  {getStatusIcon(healthStatus.components.redis.status)} {healthStatus.components.redis.status}
                </span>
              </div>
              <div className="text-xs text-gray-500 mt-1">
                v{healthStatus.components.redis.details.version}
              </div>
            </div>
            <div className="bg-gray-800/50 rounded-lg p-4">
              <div className="flex items-center justify-between">
                <span className="text-gray-400">Disk Space</span>
                <span className={`${getStatusColor(healthStatus.components.diskSpace.status)} flex items-center gap-1`}>
                  {getStatusIcon(healthStatus.components.diskSpace.status)} {healthStatus.components.diskSpace.status}
                </span>
              </div>
              <div className="text-xs text-gray-500 mt-1">
                {formatBytes(healthStatus.components.diskSpace.details.free)} free
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Scheduler Information */}
      <div>
        <h4 className="text-lg font-semibold text-white mb-3">Active Schedulers</h4>
        <div className="space-y-3">
          {schedulerInfo.map((scheduler) => (
            <div key={scheduler.name} className="bg-gray-800/50 rounded-lg p-4">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <span className="text-2xl">{scheduler.icon}</span>
                  <div>
                    <div className="text-white font-medium">{scheduler.name}</div>
                    <div className="text-sm text-gray-400">{scheduler.description}</div>
                  </div>
                </div>
                <div className="text-right">
                  <div className="text-sm text-blue-400 font-medium">{scheduler.interval}</div>
                  <div className="text-xs text-green-400">● Active</div>
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Additional Info */}
      <div className="mt-6 p-4 bg-blue-500/10 border border-blue-500/20 rounded-lg">
        <div className="flex items-center gap-2 mb-2">
          <span className="text-blue-400">ℹ️</span>
          <span className="text-blue-400 font-medium">Scheduler Information</span>
        </div>
        <div className="text-sm text-gray-400">
          All schedulers are running automatically in the background. The Java Spring Boot application 
          handles scheduling with cron expressions for precise timing. Health checks are performed every 
          5 minutes to ensure system stability.
        </div>
      </div>
    </GlassContainer>
  );
};