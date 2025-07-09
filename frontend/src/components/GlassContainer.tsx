import React, { ReactNode } from 'react';
import { motion } from 'framer-motion';
import '../styles/glassmorphism.css';

interface GlassContainerProps {
  children: ReactNode;
  className?: string;
  variant?: 'default' | 'strong' | 'subtle' | 'frosted' | 'accent' | 'bullish' | 'bearish' | 'neutral';
  interactive?: boolean;
  liquid?: boolean;
  onClick?: () => void;
  style?: React.CSSProperties;
}

const GlassContainer: React.FC<GlassContainerProps> = ({
  children,
  className = '',
  variant = 'default',
  interactive = false,
  liquid = false,
  onClick,
  style
}) => {
  const getVariantClass = () => {
    switch (variant) {
      case 'strong':
        return 'glass-strong';
      case 'subtle':
        return 'glass-subtle';
      case 'frosted':
        return 'glass-frosted';
      case 'accent':
        return 'glass-accent';
      case 'bullish':
        return 'glass-bullish';
      case 'bearish':
        return 'glass-bearish';
      case 'neutral':
        return 'glass-neutral';
      default:
        return 'glass';
    }
  };

  const containerClasses = [
    getVariantClass(),
    interactive ? 'glass-interactive' : '',
    liquid ? 'liquid-glass' : '',
    className
  ].filter(Boolean).join(' ');

  const motionProps = {
    initial: { opacity: 0, scale: 0.95, y: 20 },
    animate: { opacity: 1, scale: 1, y: 0 },
    transition: {
      duration: 0.4,
      ease: [0.25, 0.8, 0.25, 1]
    },
    whileHover: interactive ? {
      scale: 1.02,
      y: -2,
      transition: { duration: 0.2 }
    } : undefined,
    whileTap: interactive ? {
      scale: 0.98,
      transition: { duration: 0.1 }
    } : undefined
  };

  return (
    <motion.div
      className={containerClasses}
      onClick={onClick}
      style={style}
      {...motionProps}
    >
      {children}
    </motion.div>
  );
};

export default GlassContainer;