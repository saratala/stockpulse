import React from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import Navbar from './components/Navbar';
import Dashboard from './pages/Dashboard';
import StockDetails from './pages/StockDetails';
import Predictions from './pages/Predictions';
import PredictionsHistory from './pages/PredictionsHistory';
import Screener from './pages/Screener';
import StockAnalysis from './pages/StockAnalysis';
import { LLMSentimentAnalysis } from './components/LLMSentimentAnalysis';
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
            <Route path="/predictions" element={<Predictions />} />
            <Route path="/predictions-history" element={<PredictionsHistory />} />
            <Route path="/screener" element={<Screener />} />
            <Route path="/analysis/:ticker" element={<StockAnalysis />} />
            <Route path="/llm-sentiment" element={<LLMSentimentAnalysis />} />
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