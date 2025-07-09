import React, { useState, useEffect, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  Bell,
  X,
  TrendingUp,
  TrendingDown,
  AlertTriangle,
  Info,
  CheckCircle,
  Zap,
  Volume2,
  VolumeX
} from 'lucide-react';
import GlassContainer from './GlassContainer';
import GlassButton from './GlassButton';
import '../styles/glassmorphism.css';

interface Notification {
  id: string;
  type: 'success' | 'warning' | 'error' | 'info' | 'signal';
  title: string;
  message: string;
  ticker?: string;
  confidence?: number;
  timestamp: Date;
  autoClose?: boolean;
  duration?: number;
  action?: {
    label: string;
    onClick: () => void;
  };
}

interface NotificationSystemProps {
  maxNotifications?: number;
  defaultDuration?: number;
  position?: 'top-right' | 'top-left' | 'bottom-right' | 'bottom-left' | 'top-center';
  soundEnabled?: boolean;
}

const NotificationSystem: React.FC<NotificationSystemProps> = ({
  maxNotifications = 5,
  defaultDuration = 5000,
  position = 'top-right',
  soundEnabled = true
}) => {
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [soundEnabledState, setSoundEnabledState] = useState(soundEnabled);

  // Audio context for notification sounds
  const playNotificationSound = useCallback((type: Notification['type']) => {
    if (!soundEnabledState) return;

    // Create audio context for different notification types
    const audioContext = new (window.AudioContext || (window as any).webkitAudioContext)();
    const oscillator = audioContext.createOscillator();
    const gainNode = audioContext.createGain();

    oscillator.connect(gainNode);
    gainNode.connect(audioContext.destination);

    // Different frequencies for different notification types
    const frequencies = {
      success: [523.25, 659.25], // C5, E5
      warning: [440, 554.37], // A4, C#5
      error: [349.23, 293.66], // F4, D4
      info: [440], // A4
      signal: [523.25, 698.46, 830.61] // C5, F5, G#5
    };

    const freq = frequencies[type] || frequencies.info;
    
    freq.forEach((frequency, index) => {
      setTimeout(() => {
        oscillator.frequency.setValueAtTime(frequency, audioContext.currentTime);
        gainNode.gain.setValueAtTime(0.1, audioContext.currentTime);
        gainNode.gain.exponentialRampToValueAtTime(0.01, audioContext.currentTime + 0.1);
        
        if (index === freq.length - 1) {
          oscillator.stop(audioContext.currentTime + 0.1);
        }
      }, index * 100);
    });

    oscillator.start();
  }, [soundEnabledState]);

  const addNotification = useCallback((notification: Omit<Notification, 'id' | 'timestamp'>) => {
    const id = Math.random().toString(36).substr(2, 9);
    const newNotification: Notification = {
      ...notification,
      id,
      timestamp: new Date(),
      autoClose: notification.autoClose !== false,
      duration: notification.duration || defaultDuration
    };

    setNotifications(prev => {
      const updated = [newNotification, ...prev].slice(0, maxNotifications);
      return updated;
    });

    // Play sound
    playNotificationSound(notification.type);

    // Auto-remove if specified
    if (newNotification.autoClose) {
      setTimeout(() => {
        removeNotification(id);
      }, newNotification.duration);
    }

    return id;
  }, [defaultDuration, maxNotifications, playNotificationSound]);

  const removeNotification = useCallback((id: string) => {
    setNotifications(prev => prev.filter(n => n.id !== id));
  }, []);

  const clearAllNotifications = useCallback(() => {
    setNotifications([]);
  }, []);

  // Expose functions globally for use in other components
  useEffect(() => {
    (window as any).addNotification = addNotification;
    (window as any).clearNotifications = clearAllNotifications;
    
    return () => {
      delete (window as any).addNotification;
      delete (window as any).clearNotifications;
    };
  }, [addNotification, clearAllNotifications]);

  const getPositionClasses = () => {
    const baseClasses = 'fixed z-50 pointer-events-none';
    
    switch (position) {
      case 'top-left':
        return `${baseClasses} top-4 left-4`;
      case 'top-center':
        return `${baseClasses} top-4 left-1/2 transform -translate-x-1/2`;
      case 'bottom-left':
        return `${baseClasses} bottom-4 left-4`;
      case 'bottom-right':
        return `${baseClasses} bottom-4 right-4`;
      default:
        return `${baseClasses} top-4 right-4`;
    }
  };

  const getIcon = (type: Notification['type']) => {
    switch (type) {
      case 'success':
        return <CheckCircle className="w-5 h-5 text-green-400" />;
      case 'warning':
        return <AlertTriangle className="w-5 h-5 text-orange-400" />;
      case 'error':
        return <AlertTriangle className="w-5 h-5 text-red-400" />;
      case 'signal':
        return <Zap className="w-5 h-5 text-blue-400" />;
      default:
        return <Info className="w-5 h-5 text-blue-400" />;
    }
  };

  const getVariant = (type: Notification['type']) => {
    switch (type) {
      case 'success':
        return 'bullish';
      case 'warning':
        return 'neutral';
      case 'error':
        return 'bearish';
      case 'signal':
        return 'accent';
      default:
        return 'default';
    }
  };

  return (
    <>
      {/* Notification Container */}
      <div className={getPositionClasses()}>
        <div className="w-80 max-w-sm space-y-2">
          <AnimatePresence mode="popLayout">
            {notifications.map((notification) => (
              <motion.div
                key={notification.id}
                initial={{ opacity: 0, x: position.includes('right') ? 100 : -100, scale: 0.95 }}
                animate={{ opacity: 1, x: 0, scale: 1 }}
                exit={{ 
                  opacity: 0, 
                  x: position.includes('right') ? 100 : -100, 
                  scale: 0.95,
                  height: 0,
                  marginBottom: 0
                }}
                transition={{
                  duration: 0.3,
                  ease: [0.25, 0.8, 0.25, 1]
                }}
                className="pointer-events-auto"
              >
                <GlassContainer 
                  variant={getVariant(notification.type)}
                  className="relative overflow-hidden"
                >
                  {/* Progress bar for auto-close */}
                  {notification.autoClose && (
                    <motion.div
                      className="absolute top-0 left-0 h-1 bg-white/30"
                      initial={{ width: '100%' }}
                      animate={{ width: '0%' }}
                      transition={{ 
                        duration: (notification.duration || defaultDuration) / 1000,
                        ease: 'linear'
                      }}
                    />
                  )}

                  <div className="p-4">
                    <div className="flex items-start gap-3">
                      {/* Icon */}
                      <div className="flex-shrink-0 mt-0.5">
                        {getIcon(notification.type)}
                      </div>

                      {/* Content */}
                      <div className="flex-1 min-w-0">
                        <div className="flex items-start justify-between">
                          <div className="flex-1">
                            <h4 className="text-sm font-semibold text-white/95 mb-1">
                              {notification.title}
                              {notification.ticker && (
                                <span className="ml-2 px-2 py-0.5 text-xs rounded bg-white/20">
                                  {notification.ticker}
                                </span>
                              )}
                            </h4>
                            <p className="text-sm text-white/80 leading-relaxed">
                              {notification.message}
                            </p>
                            
                            {/* Confidence indicator for signals */}
                            {notification.type === 'signal' && notification.confidence && (
                              <div className="mt-2 flex items-center gap-2">
                                <span className="text-xs text-white/60">Confidence:</span>
                                <div className="flex-1 h-1 bg-white/20 rounded-full overflow-hidden">
                                  <motion.div
                                    className="h-full bg-blue-400"
                                    initial={{ width: 0 }}
                                    animate={{ width: `${notification.confidence}%` }}
                                    transition={{ duration: 0.5, delay: 0.2 }}
                                  />
                                </div>
                                <span className="text-xs text-white/80">
                                  {notification.confidence}%
                                </span>
                              </div>
                            )}

                            {/* Action button */}
                            {notification.action && (
                              <div className="mt-3">
                                <GlassButton
                                  size="sm"
                                  variant="accent"
                                  onClick={notification.action.onClick}
                                >
                                  {notification.action.label}
                                </GlassButton>
                              </div>
                            )}

                            {/* Timestamp */}
                            <div className="mt-2 text-xs text-white/50">
                              {notification.timestamp.toLocaleTimeString()}
                            </div>
                          </div>

                          {/* Close button */}
                          <button
                            onClick={() => removeNotification(notification.id)}
                            className="flex-shrink-0 ml-2 p-1 rounded-lg hover:bg-white/10 transition-colors"
                          >
                            <X className="w-4 h-4 text-white/60" />
                          </button>
                        </div>
                      </div>
                    </div>
                  </div>
                </GlassContainer>
              </motion.div>
            ))}
          </AnimatePresence>
        </div>
      </div>

      {/* Notification Controls */}
      {notifications.length > 0 && (
        <div className="fixed bottom-4 left-4 z-50 flex gap-2">
          <GlassButton
            size="sm"
            variant="secondary"
            onClick={() => setSoundEnabledState(!soundEnabledState)}
            icon={soundEnabledState ? <Volume2 className="w-4 h-4" /> : <VolumeX className="w-4 h-4" />}
          />
          
          {notifications.length > 1 && (
            <GlassButton
              size="sm"
              variant="secondary"
              onClick={clearAllNotifications}
            >
              Clear All ({notifications.length})
            </GlassButton>
          )}
        </div>
      )}
    </>
  );
};

// Helper functions for easy notification creation
export const notificationHelpers = {
  success: (title: string, message: string, options?: Partial<Notification>) => {
    if ((window as any).addNotification) {
      (window as any).addNotification({
        type: 'success',
        title,
        message,
        ...options
      });
    }
  },

  error: (title: string, message: string, options?: Partial<Notification>) => {
    if ((window as any).addNotification) {
      (window as any).addNotification({
        type: 'error',
        title,
        message,
        autoClose: false, // Errors should be manually dismissed
        ...options
      });
    }
  },

  warning: (title: string, message: string, options?: Partial<Notification>) => {
    if ((window as any).addNotification) {
      (window as any).addNotification({
        type: 'warning',
        title,
        message,
        ...options
      });
    }
  },

  info: (title: string, message: string, options?: Partial<Notification>) => {
    if ((window as any).addNotification) {
      (window as any).addNotification({
        type: 'info',
        title,
        message,
        ...options
      });
    }
  },

  signal: (
    ticker: string, 
    signal: 'BULLISH' | 'BEARISH', 
    confidence: number, 
    reason?: string,
    options?: Partial<Notification>
  ) => {
    if ((window as any).addNotification) {
      (window as any).addNotification({
        type: 'signal',
        title: `${signal} Signal Detected`,
        message: reason || `High confidence ${signal.toLowerCase()} signal for ${ticker}`,
        ticker,
        confidence,
        autoClose: false, // Signals should be manually reviewed
        action: {
          label: 'View Analysis',
          onClick: () => {
            window.location.href = `/analysis/${ticker}`;
          }
        },
        ...options
      });
    }
  }
};

export default NotificationSystem;