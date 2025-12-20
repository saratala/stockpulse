import React from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Navbar from './components/Navbar';
import Dashboard from './pages/Dashboard';
import StockDetails from './pages/StockDetails';
import PredictionsHistory from './pages/PredictionsHistory';
import Screener from './pages/Screener';
import StockAnalysis from './pages/StockAnalysis';
import BacktestingDashboard from './pages/BacktestingDashboard';
import AIAnalysis from './pages/AIAnalysis';
import PredictionAccuracy from './pages/PredictionAccuracy';
import NotificationSystem from './components/NotificationSystem';
import './styles/App.css';
import './styles/glassmorphism.css';

function App() {
  return (
    <Router>
      <div className="App min-h-screen" style={{ background: 'var(--bg-primary)' }}>
        <Navbar />
        <main>
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/stock/:ticker" element={<StockDetails />} />
            <Route path="/predictions" element={<Navigate to="/screener" replace />} />
            <Route path="/predictions-history" element={<PredictionsHistory />} />
            <Route path="/screener" element={<Screener />} />
            <Route path="/analysis/:ticker" element={<StockAnalysis />} />
            <Route path="/backtesting" element={<BacktestingDashboard />} />
            <Route path="/ai-analysis" element={<AIAnalysis />} />
            <Route path="/accuracy" element={<PredictionAccuracy />} />
            <Route path="/llm-sentiment" element={<Navigate to="/ai-analysis" replace />} />
          </Routes>
        </main>
        
        {/* Global Notification System */}
        <NotificationSystem 
          position="top-right"
          maxNotifications={5}
          soundEnabled={true}
        />
      </div>
    </Router>
  );
}

export default App;