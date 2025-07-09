import React from 'react';
import { motion } from 'framer-motion';
import '../styles/glassmorphism.css';

interface LoadingSpinnerProps {
  size?: 'sm' | 'md' | 'lg';
  variant?: 'default' | 'glass' | 'minimal';
  text?: string;
}

const LoadingSpinner: React.FC<LoadingSpinnerProps> = ({
  size = 'md',
  variant = 'glass',
  text
}) => {
  const getSizeClasses = () => {
    switch (size) {
      case 'sm':
        return 'w-6 h-6';
      case 'lg':
        return 'w-12 h-12';
      default:
        return 'w-8 h-8';
    }
  };

  const getContainerSize = () => {
    switch (size) {
      case 'sm':
        return 'w-16 h-16';
      case 'lg':
        return 'w-32 h-32';
      default:
        return 'w-24 h-24';
    }
  };

  if (variant === 'minimal') {
    return (
      <div className="flex items-center justify-center">
        <motion.div
          className={`${getSizeClasses()} border-2 border-white/20 border-t-white rounded-full`}
          animate={{ rotate: 360 }}
          transition={{ duration: 1, repeat: Infinity, ease: 'linear' }}
        />
        {text && (
          <span className="ml-3 text-white/70 text-sm">{text}</span>
        )}
      </div>
    );
  }

  return (
    <div className="flex flex-col items-center justify-center">
      <div className={`glass-frosted ${getContainerSize()} rounded-3xl p-6 relative overflow-hidden`}>
        {/* Background pulse */}
        <motion.div
          className="absolute inset-0 bg-gradient-to-r from-blue-500/10 via-purple-500/10 to-pink-500/10 rounded-3xl"
          animate={{
            scale: [1, 1.05, 1],
            opacity: [0.3, 0.6, 0.3]
          }}
          transition={{
            duration: 2,
            repeat: Infinity,
            ease: 'easeInOut'
          }}
        />

        {/* Main spinner */}
        <div className="relative flex items-center justify-center h-full">
          <motion.div
            className={`${getSizeClasses()} relative`}
            animate={{ rotate: 360 }}
            transition={{ duration: 2, repeat: Infinity, ease: 'linear' }}
          >
            {/* Outer ring */}
            <div className="absolute inset-0 border-2 border-white/20 rounded-full" />
            
            {/* Animated segments */}
            <motion.div
              className="absolute inset-0 border-2 border-transparent border-t-blue-400 border-r-purple-400 rounded-full"
              animate={{ rotate: 360 }}
              transition={{ duration: 1.5, repeat: Infinity, ease: 'linear' }}
            />
            
            {/* Inner dot */}
            <motion.div
              className="absolute inset-1/2 w-1 h-1 -translate-x-1/2 -translate-y-1/2 bg-white rounded-full"
              animate={{
                scale: [1, 1.5, 1],
                opacity: [0.5, 1, 0.5]
              }}
              transition={{
                duration: 1,
                repeat: Infinity,
                ease: 'easeInOut'
              }}
            />
          </motion.div>
        </div>

        {/* Liquid effect */}
        <motion.div
          className="absolute inset-0 bg-gradient-to-r from-transparent via-white/5 to-transparent skew-x-12"
          animate={{ x: ['-100%', '200%'] }}
          transition={{
            duration: 2,
            repeat: Infinity,
            ease: 'easeInOut',
            repeatDelay: 1
          }}
        />
      </div>

      {/* Loading text */}
      {text && (
        <motion.div
          className="mt-4 text-center"
          initial={{ opacity: 0, y: 10 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2 }}
        >
          <div className="text-white/90 font-medium mb-1">{text}</div>
          <div className="flex justify-center space-x-1">
            {[0, 1, 2].map((index) => (
              <motion.div
                key={index}
                className="w-1 h-1 bg-white/50 rounded-full"
                animate={{
                  scale: [1, 1.5, 1],
                  opacity: [0.5, 1, 0.5]
                }}
                transition={{
                  duration: 1,
                  repeat: Infinity,
                  delay: index * 0.2,
                  ease: 'easeInOut'
                }}
              />
            ))}
          </div>
        </motion.div>
      )}
    </div>
  );
};

export default LoadingSpinner;