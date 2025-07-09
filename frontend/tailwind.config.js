/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/**/*.{js,jsx,ts,tsx}",
  ],
  theme: {
    extend: {
      // Custom glassmorphism utilities
      backdropBlur: {
        xs: '2px',
      },
      backgroundColor: {
        glass: 'rgba(255, 255, 255, 0.1)',
        'glass-strong': 'rgba(255, 255, 255, 0.2)',
        'glass-subtle': 'rgba(255, 255, 255, 0.05)',
      },
      borderColor: {
        glass: 'rgba(255, 255, 255, 0.2)',
        'glass-strong': 'rgba(255, 255, 255, 0.3)',
      },
      boxShadow: {
        glass: '0 8px 32px rgba(0, 0, 0, 0.37)',
        'glass-small': '0 4px 16px rgba(0, 0, 0, 0.2)',
        'glass-large': '0 16px 64px rgba(0, 0, 0, 0.5)',
      },
      animation: {
        'liquid-flow': 'liquidFlow 3s ease-in-out infinite',
        'glass-shimmer': 'glassShimmer 2s ease-in-out infinite',
        'pulse-soft': 'pulseSoft 2s ease-in-out infinite',
      },
      keyframes: {
        liquidFlow: {
          '0%': { transform: 'translateX(-100%) translateY(-100%) rotate(45deg)' },
          '50%': { transform: 'translateX(0) translateY(0) rotate(45deg)' },
          '100%': { transform: 'translateX(100%) translateY(100%) rotate(45deg)' },
        },
        glassShimmer: {
          '0%': { transform: 'translateX(-100%)' },
          '100%': { transform: 'translateX(100%)' },
        },
        pulseSoft: {
          '0%, 100%': { opacity: '0.3' },
          '50%': { opacity: '0.6' },
        },
      },
      // Mobile-first responsive breakpoints
      screens: {
        'xs': '475px',
        'safe-top': { raw: '(env(safe-area-inset-top))' },
        'safe-bottom': { raw: '(env(safe-area-inset-bottom))' },
      },
      // Touch-friendly sizing
      spacing: {
        'safe-top': 'env(safe-area-inset-top)',
        'safe-bottom': 'env(safe-area-inset-bottom)',
        'safe-left': 'env(safe-area-inset-left)',
        'safe-right': 'env(safe-area-inset-right)',
        '18': '4.5rem',
        '88': '22rem',
      },
      minHeight: {
        'touch': '44px', // iOS touch target minimum
      },
      minWidth: {
        'touch': '44px',
      },
      // Performance optimizations
      willChange: {
        'transform-opacity': 'transform, opacity',
      },
    },
  },
  plugins: [
    // Custom plugin for glassmorphism utilities
    function({ addUtilities, theme }) {
      const glassmorphismUtilities = {
        '.glass-base': {
          'backdrop-filter': 'blur(16px)',
          '-webkit-backdrop-filter': 'blur(16px)',
          'background': 'rgba(255, 255, 255, 0.1)',
          'border': '1px solid rgba(255, 255, 255, 0.2)',
          'box-shadow': '0 8px 32px rgba(0, 0, 0, 0.37)',
        },
        '.glass-interactive': {
          'transition': 'all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1)',
          'cursor': 'pointer',
          '&:hover': {
            'background': 'rgba(255, 255, 255, 0.25)',
            'border-color': 'rgba(255, 255, 255, 0.4)',
            'transform': 'translateY(-2px) scale(1.02)',
            'box-shadow': '0 12px 48px rgba(0, 0, 0, 0.4)',
          },
          '&:active': {
            'transform': 'translateY(0) scale(0.98)',
            'transition': 'all 0.1s ease',
          },
        },
        '.touch-optimized': {
          'min-height': '44px',
          'min-width': '44px',
          'padding': '12px',
          'border-radius': '12px',
        },
        // Performance optimizations
        '.gpu-accelerated': {
          'transform': 'translateZ(0)',
          'will-change': 'transform, opacity',
          'backface-visibility': 'hidden',
          'perspective': '1000px',
        },
        '.reduce-motion': {
          '@media (prefers-reduced-motion: reduce)': {
            'animation': 'none !important',
            'transition': 'none !important',
          },
        },
      };

      addUtilities(glassmorphismUtilities);
    },
    
    // Plugin for mobile-first utilities
    function({ addUtilities }) {
      const mobileUtilities = {
        '.mobile-scroll': {
          '-webkit-overflow-scrolling': 'touch',
          'scroll-behavior': 'smooth',
        },
        '.no-tap-highlight': {
          '-webkit-tap-highlight-color': 'transparent',
        },
        '.no-select': {
          'user-select': 'none',
          '-webkit-user-select': 'none',
          '-webkit-touch-callout': 'none',
        },
        '.safe-area-padding': {
          'padding-top': 'env(safe-area-inset-top)',
          'padding-bottom': 'env(safe-area-inset-bottom)',
          'padding-left': 'env(safe-area-inset-left)',
          'padding-right': 'env(safe-area-inset-right)',
        },
      };

      addUtilities(mobileUtilities);
    },
  ],
  // Dark mode support
  darkMode: 'class',
  
  // Optimize for production
  future: {
    hoverOnlyWhenSupported: true,
  },
}