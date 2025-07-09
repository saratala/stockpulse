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
            to="/predictions" 
            className="transition-colors duration-200 hover:brightness-75"
            style={{ color: 'var(--text-secondary)' }}
          >
            Predictions
          </Link>
          <Link 
            to="/predictions-history" 
            className="transition-colors duration-200 hover:brightness-75"
            style={{ color: 'var(--text-secondary)' }}
          >
            History
          </Link>
          <Link 
            to="/screener" 
            className="transition-colors duration-200 hover:brightness-75"
            style={{ color: 'var(--text-secondary)' }}
          >
            Screener
          </Link>
          <Link 
            to="/llm-sentiment" 
            className="transition-colors duration-200 hover:brightness-75"
            style={{ color: 'var(--text-secondary)' }}
          >
            LLM Sentiment
          </Link>
        </div>
      </div>
    </nav>
  );
}

export default Navbar;