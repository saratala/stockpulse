/**
 * Real-time Stock Price Streaming Service
 * Uses Server-Sent Events (SSE) to receive live price updates from the backend
 */

const API_BASE_URL = '/api/v1';

export interface RealTimeQuote {
  symbol: string;
  price: number;
  volume: number;
  timestamp: number;
  previousPrice: number;
  change: number;
  changePercent: number;
}

export interface StreamStatus {
  webSocketConnected: boolean;
  subscribedSymbols: string[];
  activeSSEConnections: number;
  latestQuotesCount: number;
}

type PriceUpdateCallback = (quote: RealTimeQuote) => void;
type ConnectionCallback = (status: { connected: boolean; subscribedSymbols: number }) => void;
type SnapshotCallback = (quotes: Record<string, RealTimeQuote>) => void;

class RealTimeService {
  private eventSource: EventSource | null = null;
  private priceCallbacks: PriceUpdateCallback[] = [];
  private connectionCallbacks: ConnectionCallback[] = [];
  private snapshotCallbacks: SnapshotCallback[] = [];
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private reconnectDelay = 3000;
  private isConnecting = false;

  /**
   * Connect to the SSE stream for real-time price updates
   */
  connect(): void {
    if (this.eventSource || this.isConnecting) {
      console.log('RealTimeService: Already connected or connecting');
      return;
    }

    this.isConnecting = true;

    try {
      this.eventSource = new EventSource(`${API_BASE_URL}/stream/prices`);

      this.eventSource.addEventListener('connected', (event: MessageEvent) => {
        const data = JSON.parse(event.data);
        console.log('RealTimeService: Connected to price stream', data);
        this.reconnectAttempts = 0;
        this.isConnecting = false;
        this.notifyConnectionCallbacks({ connected: true, subscribedSymbols: data.subscribedSymbols });
      });

      this.eventSource.addEventListener('price', (event: MessageEvent) => {
        const quote: RealTimeQuote = JSON.parse(event.data);
        this.notifyPriceCallbacks(quote);
      });

      this.eventSource.addEventListener('snapshot', (event: MessageEvent) => {
        const data = JSON.parse(event.data);
        console.log(`RealTimeService: Received snapshot with ${data.count} quotes`);
        this.notifySnapshotCallbacks(data.quotes);
      });

      this.eventSource.addEventListener('heartbeat', (event: MessageEvent) => {
        // Keep-alive heartbeat - can be used for connection health monitoring
        const data = JSON.parse(event.data);
        console.debug('RealTimeService: Heartbeat received', data);
      });

      this.eventSource.onerror = (error) => {
        console.error('RealTimeService: SSE connection error', error);
        this.isConnecting = false;
        this.notifyConnectionCallbacks({ connected: false, subscribedSymbols: 0 });
        this.handleReconnect();
      };

      this.eventSource.onopen = () => {
        console.log('RealTimeService: SSE connection opened');
      };

    } catch (error) {
      console.error('RealTimeService: Failed to create EventSource', error);
      this.isConnecting = false;
      this.handleReconnect();
    }
  }

  /**
   * Disconnect from the SSE stream
   */
  disconnect(): void {
    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
      console.log('RealTimeService: Disconnected from price stream');
      this.notifyConnectionCallbacks({ connected: false, subscribedSymbols: 0 });
    }
    this.reconnectAttempts = 0;
  }

  /**
   * Handle reconnection with exponential backoff
   */
  private handleReconnect(): void {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.error('RealTimeService: Max reconnect attempts reached');
      return;
    }

    if (this.eventSource) {
      this.eventSource.close();
      this.eventSource = null;
    }

    const delay = this.reconnectDelay * Math.pow(2, this.reconnectAttempts);
    this.reconnectAttempts++;

    console.log(`RealTimeService: Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts})`);

    setTimeout(() => {
      this.connect();
    }, delay);
  }

  /**
   * Subscribe to price updates for a specific callback
   */
  onPriceUpdate(callback: PriceUpdateCallback): () => void {
    this.priceCallbacks.push(callback);
    return () => {
      this.priceCallbacks = this.priceCallbacks.filter(cb => cb !== callback);
    };
  }

  /**
   * Subscribe to connection status changes
   */
  onConnectionChange(callback: ConnectionCallback): () => void {
    this.connectionCallbacks.push(callback);
    return () => {
      this.connectionCallbacks = this.connectionCallbacks.filter(cb => cb !== callback);
    };
  }

  /**
   * Subscribe to snapshot updates (initial data load)
   */
  onSnapshot(callback: SnapshotCallback): () => void {
    this.snapshotCallbacks.push(callback);
    return () => {
      this.snapshotCallbacks = this.snapshotCallbacks.filter(cb => cb !== callback);
    };
  }

  private notifyPriceCallbacks(quote: RealTimeQuote): void {
    this.priceCallbacks.forEach(cb => {
      try {
        cb(quote);
      } catch (error) {
        console.error('RealTimeService: Error in price callback', error);
      }
    });
  }

  private notifyConnectionCallbacks(status: { connected: boolean; subscribedSymbols: number }): void {
    this.connectionCallbacks.forEach(cb => {
      try {
        cb(status);
      } catch (error) {
        console.error('RealTimeService: Error in connection callback', error);
      }
    });
  }

  private notifySnapshotCallbacks(quotes: Record<string, RealTimeQuote>): void {
    this.snapshotCallbacks.forEach(cb => {
      try {
        cb(quotes);
      } catch (error) {
        console.error('RealTimeService: Error in snapshot callback', error);
      }
    });
  }

  /**
   * Check if currently connected
   */
  isConnected(): boolean {
    return this.eventSource !== null && this.eventSource.readyState === EventSource.OPEN;
  }
}

// Export singleton instance
export const realTimeService = new RealTimeService();

// API functions for managing subscriptions

/**
 * Subscribe to specific tickers for real-time updates
 */
export const subscribeToTickers = async (tickers: string[]): Promise<{
  status: string;
  tickers: string[];
  totalSubscribed: number;
}> => {
  const response = await fetch(`${API_BASE_URL}/stream/subscribe`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(tickers),
  });
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

/**
 * Subscribe to all S&P 500 tickers
 */
export const subscribeToSP500 = async (): Promise<{
  status: string;
  message: string;
  tickerCount: number;
}> => {
  const response = await fetch(`${API_BASE_URL}/stream/subscribe/sp500`, {
    method: 'POST',
  });
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

/**
 * Unsubscribe from specific tickers
 */
export const unsubscribeFromTickers = async (tickers: string[]): Promise<{
  status: string;
  tickers: string[];
  remainingSubscribed: number;
}> => {
  const response = await fetch(`${API_BASE_URL}/stream/unsubscribe`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(tickers),
  });
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

/**
 * Get current stream status
 */
export const getStreamStatus = async (): Promise<StreamStatus> => {
  const response = await fetch(`${API_BASE_URL}/stream/status`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

/**
 * Get latest quote for a symbol
 */
export const getLatestQuote = async (symbol: string): Promise<RealTimeQuote> => {
  const response = await fetch(`${API_BASE_URL}/stream/quote/${symbol.toUpperCase()}`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

/**
 * Get all latest quotes
 */
export const getAllQuotes = async (): Promise<{
  count: number;
  quotes: Record<string, RealTimeQuote>;
}> => {
  const response = await fetch(`${API_BASE_URL}/stream/quotes`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};

/**
 * Get S&P 500 ticker list
 */
export const getSP500Tickers = async (): Promise<string[]> => {
  const response = await fetch(`${API_BASE_URL}/stream/sp500-tickers`);
  if (!response.ok) throw new Error(`HTTP error! status: ${response.status}`);
  return response.json();
};
