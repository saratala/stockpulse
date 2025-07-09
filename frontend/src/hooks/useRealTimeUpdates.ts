import { useState, useEffect, useCallback, useRef } from 'react';

interface UseRealTimeUpdatesOptions {
  interval?: number; // milliseconds
  enabled?: boolean;
  onUpdate?: (data: any) => void;
  onError?: (error: Error) => void;
}

interface RealTimeState<T> {
  data: T | null;
  loading: boolean;
  error: string | null;
  lastUpdated: Date | null;
  isConnected: boolean;
}

export const useRealTimeUpdates = <T>(
  fetchFunction: () => Promise<T>,
  options: UseRealTimeUpdatesOptions = {}
) => {
  const {
    interval = 30000, // 30 seconds default
    enabled = true,
    onUpdate,
    onError
  } = options;

  const [state, setState] = useState<RealTimeState<T>>({
    data: null,
    loading: false,
    error: null,
    lastUpdated: null,
    isConnected: false
  });

  const intervalRef = useRef<number | null>(null);
  const mountedRef = useRef(true);
  const retryCountRef = useRef(0);
  const maxRetries = 3;

  const updateData = useCallback(async (showLoading = false) => {
    if (!mountedRef.current) return;

    try {
      if (showLoading) {
        setState(prev => ({ ...prev, loading: true, error: null }));
      }

      const data = await fetchFunction();
      
      if (!mountedRef.current) return;

      setState(prev => ({
        ...prev,
        data,
        loading: false,
        error: null,
        lastUpdated: new Date(),
        isConnected: true
      }));

      retryCountRef.current = 0;
      onUpdate?.(data);
    } catch (error) {
      if (!mountedRef.current) return;

      const errorMessage = error instanceof Error ? error.message : 'Unknown error occurred';
      
      setState(prev => ({
        ...prev,
        loading: false,
        error: errorMessage,
        isConnected: false
      }));

      retryCountRef.current++;
      onError?.(error instanceof Error ? error : new Error(errorMessage));

      // Exponential backoff for retries
      if (retryCountRef.current < maxRetries) {
        const retryDelay = Math.min(1000 * Math.pow(2, retryCountRef.current), 10000);
        setTimeout(() => {
          if (mountedRef.current && enabled) {
            updateData();
          }
        }, retryDelay);
      }
    }
  }, [fetchFunction, onUpdate, onError, enabled]);

  const startPolling = useCallback(() => {
    if (!enabled || intervalRef.current) return;

    // Initial fetch
    updateData(true);

    // Set up polling
    intervalRef.current = setInterval(() => {
      updateData();
    }, interval);

    setState(prev => ({ ...prev, isConnected: true }));
  }, [enabled, interval, updateData]);

  const stopPolling = useCallback(() => {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
    setState(prev => ({ ...prev, isConnected: false }));
  }, []);

  const refresh = useCallback(() => {
    updateData(true);
  }, [updateData]);

  // Handle visibility change
  useEffect(() => {
    const handleVisibilityChange = () => {
      if (document.hidden) {
        stopPolling();
      } else if (enabled) {
        startPolling();
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    
    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [enabled, startPolling, stopPolling]);

  // Handle online/offline
  useEffect(() => {
    const handleOnline = () => {
      if (enabled) {
        startPolling();
      }
    };

    const handleOffline = () => {
      stopPolling();
    };

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
    };
  }, [enabled, startPolling, stopPolling]);

  // Main effect for starting/stopping polling
  useEffect(() => {
    if (enabled) {
      startPolling();
    } else {
      stopPolling();
    }

    return () => {
      stopPolling();
    };
  }, [enabled, startPolling, stopPolling]);

  // Cleanup on unmount
  useEffect(() => {
    mountedRef.current = true;
    
    return () => {
      mountedRef.current = false;
      stopPolling();
    };
  }, [stopPolling]);

  return {
    ...state,
    refresh,
    startPolling,
    stopPolling
  };
};

// Specialized hook for screening data
export const useScreeningUpdates = (filters?: {
  minScore?: number;
  maxResults?: number;
  includeSignals?: boolean;
}) => {
  const fetchScreeningData = useCallback(async () => {
    const response = await fetch('/api/screener/run?' + new URLSearchParams({
      min_score: filters?.minScore?.toString() || '70',
      max_results: filters?.maxResults?.toString() || '50',
      include_signals: filters?.includeSignals?.toString() || 'true'
    }));
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    
    return response.json();
  }, [filters]);

  return useRealTimeUpdates(fetchScreeningData, {
    interval: 60000, // 1 minute for screening data
    enabled: true
  });
};

// Hook for signal updates
export const useSignalUpdates = (tickers: string[]) => {
  const fetchSignals = useCallback(async () => {
    if (tickers.length === 0) return null;
    
    const response = await fetch('/api/screener/signals?' + new URLSearchParams({
      tickers: tickers.join(','),
      min_confidence: '40'
    }));
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    
    return response.json();
  }, [tickers]);

  return useRealTimeUpdates(fetchSignals, {
    interval: 30000, // 30 seconds for signals
    enabled: tickers.length > 0
  });
};

// Hook for market data updates
export const useMarketUpdates = () => {
  const fetchMarketData = useCallback(async () => {
    const response = await fetch('/api/screener/daily-results');
    
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    
    return response.json();
  }, []);

  return useRealTimeUpdates(fetchMarketData, {
    interval: 120000, // 2 minutes for market overview
    enabled: true
  });
};