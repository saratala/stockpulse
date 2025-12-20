import React, { useState, useEffect } from 'react';
import { useRealTimePrice } from '../hooks/useRealTimePrices';
import { RealTimeQuote } from '../services/realTimeService';

interface RealTimePriceIndicatorProps {
  ticker: string;
  showChange?: boolean;
  showVolume?: boolean;
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

const RealTimePriceIndicator: React.FC<RealTimePriceIndicatorProps> = ({
  ticker,
  showChange = true,
  showVolume = false,
  size = 'md',
  className = '',
}) => {
  const realTimeQuote = useRealTimePrice(ticker);
  const [flash, setFlash] = useState<'up' | 'down' | null>(null);
  const [prevPrice, setPrevPrice] = useState<number | null>(null);

  // Flash effect when price changes
  useEffect(() => {
    if (realTimeQuote && prevPrice !== null && realTimeQuote.price !== prevPrice) {
      setFlash(realTimeQuote.price > prevPrice ? 'up' : 'down');
      const timer = setTimeout(() => setFlash(null), 500);
      return () => clearTimeout(timer);
    }
    if (realTimeQuote) {
      setPrevPrice(realTimeQuote.price);
    }
  }, [realTimeQuote?.price, prevPrice]);

  const sizeClasses = {
    sm: 'text-sm',
    md: 'text-base',
    lg: 'text-lg font-semibold',
  };

  const flashClasses = {
    up: 'bg-green-500/20',
    down: 'bg-red-500/20',
  };

  if (!realTimeQuote) {
    return (
      <div className={`inline-flex items-center gap-2 ${className}`}>
        <span className={`${sizeClasses[size]} text-gray-400`}>--</span>
      </div>
    );
  }

  const formatPrice = (price: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    }).format(price);
  };

  const formatVolume = (volume: number) => {
    if (volume >= 1000000) {
      return (volume / 1000000).toFixed(2) + 'M';
    } else if (volume >= 1000) {
      return (volume / 1000).toFixed(2) + 'K';
    }
    return volume.toString();
  };

  const formatChange = (change: number, changePercent: number) => {
    const sign = change >= 0 ? '+' : '';
    return `${sign}${change.toFixed(2)} (${sign}${changePercent.toFixed(2)}%)`;
  };

  const isPositive = realTimeQuote.change >= 0;
  const changeColor = isPositive ? 'text-green-400' : 'text-red-400';

  return (
    <div
      className={`inline-flex items-center gap-2 px-2 py-1 rounded transition-colors duration-300 ${
        flash ? flashClasses[flash] : ''
      } ${className}`}
    >
      {/* Live indicator */}
      <span className="relative flex h-2 w-2">
        <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75"></span>
        <span className="relative inline-flex rounded-full h-2 w-2 bg-green-500"></span>
      </span>

      {/* Price */}
      <span className={`${sizeClasses[size]} font-mono`} style={{ color: 'var(--text-primary)' }}>
        {formatPrice(realTimeQuote.price)}
      </span>

      {/* Change */}
      {showChange && realTimeQuote.change !== 0 && (
        <span className={`${sizeClasses[size]} font-mono ${changeColor}`}>
          {formatChange(realTimeQuote.change, realTimeQuote.changePercent)}
        </span>
      )}

      {/* Volume */}
      {showVolume && (
        <span className="text-xs text-gray-400">
          Vol: {formatVolume(realTimeQuote.volume)}
        </span>
      )}
    </div>
  );
};

export default RealTimePriceIndicator;

/**
 * Connection status indicator component
 */
interface ConnectionStatusProps {
  isConnected: boolean;
  subscribedCount: number;
}

export const RealTimeConnectionStatus: React.FC<ConnectionStatusProps> = ({
  isConnected,
  subscribedCount,
}) => {
  return (
    <div className="flex items-center gap-2 text-sm">
      <span
        className={`h-2 w-2 rounded-full ${
          isConnected ? 'bg-green-500' : 'bg-red-500'
        }`}
      />
      <span style={{ color: 'var(--text-secondary)' }}>
        {isConnected ? `Live (${subscribedCount} symbols)` : 'Disconnected'}
      </span>
    </div>
  );
};

/**
 * Real-time price table row component
 */
interface RealTimePriceRowProps {
  quote: RealTimeQuote;
  onClick?: () => void;
}

export const RealTimePriceRow: React.FC<RealTimePriceRowProps> = ({ quote, onClick }) => {
  const [flash, setFlash] = useState<'up' | 'down' | null>(null);
  const [prevPrice, setPrevPrice] = useState<number>(quote.price);

  useEffect(() => {
    if (quote.price !== prevPrice) {
      setFlash(quote.price > prevPrice ? 'up' : 'down');
      setPrevPrice(quote.price);
      const timer = setTimeout(() => setFlash(null), 500);
      return () => clearTimeout(timer);
    }
  }, [quote.price, prevPrice]);

  const isPositive = quote.change >= 0;
  const changeColor = isPositive ? 'text-green-400' : 'text-red-400';
  const flashBg = flash === 'up' ? 'bg-green-500/10' : flash === 'down' ? 'bg-red-500/10' : '';

  return (
    <tr
      className={`border-b border-gray-700/50 hover:bg-gray-700/30 cursor-pointer transition-colors ${flashBg}`}
      onClick={onClick}
    >
      <td className="px-4 py-3 font-medium" style={{ color: 'var(--text-primary)' }}>
        {quote.symbol}
      </td>
      <td className="px-4 py-3 text-right font-mono" style={{ color: 'var(--text-primary)' }}>
        ${quote.price.toFixed(2)}
      </td>
      <td className={`px-4 py-3 text-right font-mono ${changeColor}`}>
        {isPositive ? '+' : ''}{quote.change.toFixed(2)}
      </td>
      <td className={`px-4 py-3 text-right font-mono ${changeColor}`}>
        {isPositive ? '+' : ''}{quote.changePercent.toFixed(2)}%
      </td>
      <td className="px-4 py-3 text-right text-gray-400">
        {(quote.volume / 1000).toFixed(1)}K
      </td>
    </tr>
  );
};
