import { useCallback, useRef, useEffect } from 'react';

interface TouchPosition {
  x: number;
  y: number;
}

interface SwipeData {
  direction: 'up' | 'down' | 'left' | 'right';
  distance: number;
  velocity: number;
  duration: number;
}

interface PinchData {
  scale: number;
  center: TouchPosition;
}

interface UseTouchOptions {
  onSwipe?: (data: SwipeData) => void;
  onPinch?: (data: PinchData) => void;
  onTap?: (position: TouchPosition) => void;
  onDoubleTap?: (position: TouchPosition) => void;
  onLongPress?: (position: TouchPosition) => void;
  onPressStart?: (position: TouchPosition) => void;
  onPressEnd?: (position: TouchPosition) => void;
  swipeThreshold?: number;
  pinchThreshold?: number;
  longPressDelay?: number;
  doubleTapDelay?: number;
  preventDefault?: boolean;
  enabled?: boolean;
}

export const useTouch = (options: UseTouchOptions = {}) => {
  const {
    onSwipe,
    onPinch,
    onTap,
    onDoubleTap,
    onLongPress,
    onPressStart,
    onPressEnd,
    swipeThreshold = 50,
    pinchThreshold = 0.1,
    longPressDelay = 500,
    doubleTapDelay = 300,
    preventDefault = true,
    enabled = true
  } = options;

  const touchStartRef = useRef<TouchList | null>(null);
  const touchStartTimeRef = useRef<number>(0);
  const lastTapTimeRef = useRef<number>(0);
  const longPressTimerRef = useRef<number | null>(null);
  const initialPinchDistanceRef = useRef<number>(0);
  const isPinchingRef = useRef<boolean>(false);

  const getTouchDistance = useCallback((touch1: Touch, touch2: Touch): number => {
    const dx = touch1.clientX - touch2.clientX;
    const dy = touch1.clientY - touch2.clientY;
    return Math.sqrt(dx * dx + dy * dy);
  }, []);

  const getTouchCenter = useCallback((touch1: Touch, touch2: Touch): TouchPosition => {
    return {
      x: (touch1.clientX + touch2.clientX) / 2,
      y: (touch1.clientY + touch2.clientY) / 2
    };
  }, []);

  const handleTouchStart = useCallback((event: TouchEvent) => {
    if (!enabled) return;

    if (preventDefault) {
      event.preventDefault();
    }

    touchStartRef.current = event.touches;
    touchStartTimeRef.current = Date.now();

    const touch = event.touches[0];
    const position = { x: touch.clientX, y: touch.clientY };

    onPressStart?.(position);

    // Handle multi-touch (pinch)
    if (event.touches.length === 2) {
      isPinchingRef.current = true;
      initialPinchDistanceRef.current = getTouchDistance(event.touches[0], event.touches[1]);
    } else {
      isPinchingRef.current = false;
      
      // Start long press timer
      if (onLongPress) {
        longPressTimerRef.current = setTimeout(() => {
          onLongPress(position);
        }, longPressDelay);
      }
    }
  }, [enabled, preventDefault, onPressStart, onLongPress, longPressDelay, getTouchDistance]);

  const handleTouchMove = useCallback((event: TouchEvent) => {
    if (!enabled || !touchStartRef.current) return;

    if (preventDefault) {
      event.preventDefault();
    }

    // Clear long press timer on move
    if (longPressTimerRef.current) {
      clearTimeout(longPressTimerRef.current);
      longPressTimerRef.current = null;
    }

    // Handle pinch gesture
    if (event.touches.length === 2 && isPinchingRef.current && onPinch) {
      const currentDistance = getTouchDistance(event.touches[0], event.touches[1]);
      const scale = currentDistance / initialPinchDistanceRef.current;
      const center = getTouchCenter(event.touches[0], event.touches[1]);

      if (Math.abs(scale - 1) > pinchThreshold) {
        onPinch({ scale, center });
      }
    }
  }, [enabled, preventDefault, onPinch, pinchThreshold, getTouchDistance, getTouchCenter]);

  const handleTouchEnd = useCallback((event: TouchEvent) => {
    if (!enabled || !touchStartRef.current) return;

    if (preventDefault) {
      event.preventDefault();
    }

    // Clear long press timer
    if (longPressTimerRef.current) {
      clearTimeout(longPressTimerRef.current);
      longPressTimerRef.current = null;
    }

    const touchEnd = event.changedTouches[0];
    const touchStart = touchStartRef.current[0];
    const endTime = Date.now();
    const duration = endTime - touchStartTimeRef.current;

    const endPosition = { x: touchEnd.clientX, y: touchEnd.clientY };
    onPressEnd?.(endPosition);

    // Reset pinch state
    if (isPinchingRef.current) {
      isPinchingRef.current = false;
      touchStartRef.current = null;
      return;
    }

    // Calculate swipe data
    const deltaX = touchEnd.clientX - touchStart.clientX;
    const deltaY = touchEnd.clientY - touchStart.clientY;
    const distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
    const velocity = distance / duration;

    // Handle swipe
    if (distance >= swipeThreshold && onSwipe) {
      let direction: SwipeData['direction'];
      
      if (Math.abs(deltaX) > Math.abs(deltaY)) {
        direction = deltaX > 0 ? 'right' : 'left';
      } else {
        direction = deltaY > 0 ? 'down' : 'up';
      }

      onSwipe({
        direction,
        distance,
        velocity,
        duration
      });
    }
    // Handle tap/double tap
    else if (distance < swipeThreshold) {
      const currentTime = Date.now();
      const timeSinceLastTap = currentTime - lastTapTimeRef.current;

      if (timeSinceLastTap < doubleTapDelay && onDoubleTap) {
        onDoubleTap(endPosition);
        lastTapTimeRef.current = 0; // Reset to prevent triple tap
      } else {
        lastTapTimeRef.current = currentTime;
        
        // Delay single tap to allow for double tap detection
        setTimeout(() => {
          if (Date.now() - lastTapTimeRef.current >= doubleTapDelay - 50) {
            onTap?.(endPosition);
          }
        }, doubleTapDelay);
      }
    }

    touchStartRef.current = null;
  }, [
    enabled,
    preventDefault,
    onPressEnd,
    onSwipe,
    onTap,
    onDoubleTap,
    swipeThreshold,
    doubleTapDelay
  ]);

  const bind = useCallback(() => ({
    onTouchStart: handleTouchStart,
    onTouchMove: handleTouchMove,
    onTouchEnd: handleTouchEnd,
    style: {
      touchAction: preventDefault ? 'none' : 'auto',
      userSelect: 'none' as const,
      WebkitUserSelect: 'none' as const,
      WebkitTouchCallout: 'none' as const
    }
  }), [handleTouchStart, handleTouchMove, handleTouchEnd, preventDefault]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      if (longPressTimerRef.current) {
        clearTimeout(longPressTimerRef.current);
      }
    };
  }, []);

  return { bind };
};

// Specialized hooks for common gestures

export const useSwipe = (
  onSwipe: (data: SwipeData) => void,
  threshold = 50
) => {
  return useTouch({
    onSwipe,
    swipeThreshold: threshold
  });
};

export const usePinch = (
  onPinch: (data: PinchData) => void,
  threshold = 0.1
) => {
  return useTouch({
    onPinch,
    pinchThreshold: threshold
  });
};

export const useTap = (
  onTap: (position: TouchPosition) => void,
  onDoubleTap?: (position: TouchPosition) => void
) => {
  return useTouch({
    onTap,
    onDoubleTap
  });
};

export const useLongPress = (
  onLongPress: (position: TouchPosition) => void,
  delay = 500
) => {
  return useTouch({
    onLongPress,
    longPressDelay: delay
  });
};

// Hook for pull-to-refresh functionality
export const usePullToRefresh = (
  onRefresh: () => void,
  threshold = 100
) => {
  const isRefreshingRef = useRef(false);
  const startYRef = useRef(0);

  return useTouch({
    onPressStart: (position) => {
      startYRef.current = position.y;
    },
    onSwipe: (data) => {
      if (
        data.direction === 'down' &&
        data.distance >= threshold &&
        !isRefreshingRef.current &&
        window.scrollY === 0
      ) {
        isRefreshingRef.current = true;
        onRefresh();
        
        // Reset after refresh
        setTimeout(() => {
          isRefreshingRef.current = false;
        }, 1000);
      }
    },
    swipeThreshold: threshold
  });
};

// Hook for card swipe actions (like Tinder)
export const useCardSwipe = (
  onSwipeLeft?: () => void,
  onSwipeRight?: () => void,
  threshold = 100
) => {
  return useTouch({
    onSwipe: (data) => {
      if (data.distance >= threshold) {
        if (data.direction === 'left' && onSwipeLeft) {
          onSwipeLeft();
        } else if (data.direction === 'right' && onSwipeRight) {
          onSwipeRight();
        }
      }
    },
    swipeThreshold: threshold
  });
};