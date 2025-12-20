import { useState, useEffect, useCallback, useRef } from 'react';
import {
  realTimeService,
  RealTimeQuote,
  subscribeToTickers,
  subscribeToSP500,
} from '../services/realTimeService';

interface UseRealTimePricesOptions {
  tickers?: string[];
  autoSubscribe?: boolean;
  subscribeToAllSP500?: boolean;
}

interface UseRealTimePricesReturn {
  prices: Record<string, RealTimeQuote>;
  isConnected: boolean;
  subscribedCount: number;
  connect: () => void;
  disconnect: () => void;
  subscribe: (tickers: string[]) => Promise<void>;
  subscribeAll: () => Promise<void>;
  getPrice: (ticker: string) => RealTimeQuote | undefined;
}

/**
 * React hook for consuming real-time stock price updates
 */
export function useRealTimePrices(options: UseRealTimePricesOptions = {}): UseRealTimePricesReturn {
  const { tickers = [], autoSubscribe = true, subscribeToAllSP500 = false } = options;

  const [prices, setPrices] = useState<Record<string, RealTimeQuote>>({});
  const [isConnected, setIsConnected] = useState(false);
  const [subscribedCount, setSubscribedCount] = useState(0);
  const hasSubscribed = useRef(false);

  // Handle price updates
  useEffect(() => {
    const unsubscribePrice = realTimeService.onPriceUpdate((quote) => {
      setPrices(prev => ({
        ...prev,
        [quote.symbol]: quote,
      }));
    });

    const unsubscribeConnection = realTimeService.onConnectionChange((status) => {
      setIsConnected(status.connected);
      setSubscribedCount(status.subscribedSymbols);
    });

    const unsubscribeSnapshot = realTimeService.onSnapshot((quotes) => {
      setPrices(prev => ({
        ...prev,
        ...quotes,
      }));
    });

    // Connect to the stream
    realTimeService.connect();

    return () => {
      unsubscribePrice();
      unsubscribeConnection();
      unsubscribeSnapshot();
    };
  }, []);

  // Auto-subscribe to tickers when connected
  useEffect(() => {
    if (!isConnected || hasSubscribed.current || !autoSubscribe) {
      return;
    }

    const doSubscribe = async () => {
      try {
        if (subscribeToAllSP500) {
          const result = await subscribeToSP500();
          setSubscribedCount(result.tickerCount);
          console.log('Subscribed to S&P 500:', result);
        } else if (tickers.length > 0) {
          const result = await subscribeToTickers(tickers);
          setSubscribedCount(result.totalSubscribed);
          console.log('Subscribed to tickers:', result);
        }
        hasSubscribed.current = true;
      } catch (error) {
        console.error('Failed to subscribe to tickers:', error);
      }
    };

    doSubscribe();
  }, [isConnected, tickers, autoSubscribe, subscribeToAllSP500]);

  const connect = useCallback(() => {
    realTimeService.connect();
  }, []);

  const disconnect = useCallback(() => {
    realTimeService.disconnect();
    hasSubscribed.current = false;
  }, []);

  const subscribe = useCallback(async (tickersToSubscribe: string[]) => {
    try {
      const result = await subscribeToTickers(tickersToSubscribe);
      setSubscribedCount(result.totalSubscribed);
    } catch (error) {
      console.error('Failed to subscribe:', error);
      throw error;
    }
  }, []);

  const subscribeAll = useCallback(async () => {
    try {
      const result = await subscribeToSP500();
      setSubscribedCount(result.tickerCount);
    } catch (error) {
      console.error('Failed to subscribe to S&P 500:', error);
      throw error;
    }
  }, []);

  const getPrice = useCallback((ticker: string): RealTimeQuote | undefined => {
    return prices[ticker.toUpperCase()];
  }, [prices]);

  return {
    prices,
    isConnected,
    subscribedCount,
    connect,
    disconnect,
    subscribe,
    subscribeAll,
    getPrice,
  };
}

/**
 * Hook for getting a single ticker's real-time price
 */
export function useRealTimePrice(ticker: string): RealTimeQuote | undefined {
  const [price, setPrice] = useState<RealTimeQuote | undefined>();

  useEffect(() => {
    const unsubscribe = realTimeService.onPriceUpdate((quote) => {
      if (quote.symbol === ticker.toUpperCase()) {
        setPrice(quote);
      }
    });

    const unsubscribeSnapshot = realTimeService.onSnapshot((quotes) => {
      const quote = quotes[ticker.toUpperCase()];
      if (quote) {
        setPrice(quote);
      }
    });

    // Connect if not already connected
    realTimeService.connect();

    return () => {
      unsubscribe();
      unsubscribeSnapshot();
    };
  }, [ticker]);

  return price;
}
