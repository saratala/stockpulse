import React, { ReactNode } from 'react';
import { motion } from 'framer-motion';
import '../styles/glassmorphism.css';

interface GlassButtonProps {
  children?: ReactNode;
  onClick?: (e: React.MouseEvent<HTMLButtonElement>) => void;
  variant?: 'primary' | 'secondary' | 'accent' | 'success' | 'warning' | 'error';
  size?: 'sm' | 'md' | 'lg';
  disabled?: boolean;
  loading?: boolean;
  className?: string;
  icon?: ReactNode;
  iconPosition?: 'left' | 'right';
  type?: 'button' | 'submit' | 'reset';
}

const GlassButton: React.FC<GlassButtonProps> = ({
  children,
  onClick,
  variant = 'primary',
  size = 'md',
  disabled = false,
  loading = false,
  className = '',
  icon,
  iconPosition = 'left',
  type = 'button'
}) => {
  const getVariantStyles = () => {
    switch (variant) {
      case 'secondary':
        return 'glass-button bg-opacity-10 border-white/20 hover:bg-white/20';
      case 'accent':
        return 'glass-button bg-blue-500/20 border-blue-400/30 hover:bg-blue-400/30';
      case 'success':
        return 'glass-button bg-green-500/20 border-green-400/30 hover:bg-green-400/30';
      case 'warning':
        return 'glass-button bg-orange-500/20 border-orange-400/30 hover:bg-orange-400/30';
      case 'error':
        return 'glass-button bg-red-500/20 border-red-400/30 hover:bg-red-400/30';
      default:
        return 'glass-button';
    }
  };

  const getSizeStyles = () => {
    switch (size) {
      case 'sm':
        return 'px-4 py-2 text-sm rounded-lg';
      case 'lg':
        return 'px-8 py-4 text-lg rounded-2xl';
      default:
        return 'px-6 py-3 text-base rounded-xl';
    }
  };

  const buttonClasses = [
    getVariantStyles(),
    getSizeStyles(),
    disabled || loading ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer',
    'relative overflow-hidden transition-all duration-300',
    'font-semibold',
    'backdrop-blur-md border',
    'focus:outline-none focus:ring-2 focus:ring-white/20',
    'active:scale-95',
    className
  ].filter(Boolean).join(' ');

  const handleClick = (e: React.MouseEvent<HTMLButtonElement>) => {
    if (!disabled && !loading && onClick) {
      onClick(e);
    }
  };

  return (
    <motion.button
      type={type}
      className={buttonClasses}
      style={{ color: 'var(--text-primary)' }}
      onClick={handleClick}
      disabled={disabled || loading}
      initial={{ opacity: 0, scale: 0.9 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.2 }}
      whileHover={!disabled && !loading ? { 
        scale: 1.05,
        y: -1,
        transition: { duration: 0.2 }
      } : undefined}
      whileTap={!disabled && !loading ? { 
        scale: 0.95,
        transition: { duration: 0.1 }
      } : undefined}
    >
      {/* Shimmer effect */}
      <div className="absolute inset-0 -translate-x-full bg-gradient-to-r from-transparent via-white/10 to-transparent skew-x-12 transition-transform duration-700 group-hover:translate-x-full" />
      
      {/* Button content */}
      <div className="relative flex items-center justify-center gap-2">
        {loading ? (
          <>
            <div className="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
            <span>Loading...</span>
          </>
        ) : (
          <>
            {icon && iconPosition === 'left' && (
              <span className="flex-shrink-0">{icon}</span>
            )}
            {children && <span>{children}</span>}
            {icon && iconPosition === 'right' && (
              <span className="flex-shrink-0">{icon}</span>
            )}
          </>
        )}
      </div>
    </motion.button>
  );
};

export default GlassButton;