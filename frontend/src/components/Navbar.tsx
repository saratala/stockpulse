import React from 'react';
import { Link } from 'react-router-dom';

const Navbar = () => {
  return (
    <nav 
      className="p-4 backdrop-blur-sm border-b"
      style={{ 
        background: 'var(--header-bg)',
        borderColor: 'var(--header-border)',
        color: 'var(--text-primary)'
      }}
    >
      <div className="container mx-auto flex justify-between items-center">
        <Link 
          to="/" 
          className="text-xl font-bold transition-colors duration-200 hover:brightness-110"
          style={{ color: 'var(--text-primary)' }}
        >
          StockPulse
        </Link>
        <div className="space-x-6">
          <Link
            to="/"
            className="transition-colors duration-200 hover:brightness-75"
            style={{ color: 'var(--text-secondary)' }}
          >
            Dashboard
          </Link>
          <Link
            to="/screener"
            className="transition-colors duration-200 hover:brightness-75"
            style={{ color: 'var(--text-secondary)' }}
          >
            Screener
          </Link>
          <Link
            to="/predictions-history"
            className="transition-colors duration-200 hover:brightness-75"
            style={{ color: 'var(--text-secondary)' }}
          >
            History
          </Link>
          <Link
            to="/backtesting"
            className="transition-colors duration-200 hover:brightness-75"
            style={{ color: 'var(--text-secondary)' }}
          >
            Backtesting
          </Link>
          <Link
            to="/ai-analysis"
            className="transition-colors duration-200 hover:brightness-75"
            style={{ color: 'var(--text-secondary)' }}
          >
            AI Analysis
          </Link>
          <Link
            to="/accuracy"
            className="transition-colors duration-200 hover:brightness-75"
            style={{ color: 'var(--text-secondary)' }}
          >
            Accuracy
          </Link>
        </div>
      </div>
    </nav>
  );
}

export default Navbar;