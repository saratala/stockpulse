import React from 'react';
import { motion } from 'framer-motion';
import { TrendingUp, TrendingDown, Minus, AlertCircle, Clock, MessageSquare } from 'lucide-react';

interface SentimentData {
  score: number;
  confidence: number;
  impact: string;
  newsCount: number;
  timestamp?: string;
}

interface SentimentChartProps {
  data: SentimentData;
  className?: string;
}

const SentimentChart: React.FC<SentimentChartProps> = ({ data, className = '' }) => {
  const getSentimentCategory = (score: number) => {
    if (score >= 0.3) return 'Very Positive';
    if (score >= 0.1) return 'Positive';
    if (score >= -0.1) return 'Neutral';
    if (score >= -0.3) return 'Negative';
    return 'Very Negative';
  };

  const getSentimentColor = (score: number) => {
    if (score >= 0.3) return { bg: 'bg-green-500', text: 'text-green-400', border: 'border-green-500' };
    if (score >= 0.1) return { bg: 'bg-green-400', text: 'text-green-300', border: 'border-green-400' };
    if (score >= -0.1) return { bg: 'bg-gray-400', text: 'text-gray-300', border: 'border-gray-400' };
    if (score >= -0.3) return { bg: 'bg-red-400', text: 'text-red-300', border: 'border-red-400' };
    return { bg: 'bg-red-500', text: 'text-red-400', border: 'border-red-500' };
  };

  const getImpactColor = (impact: string) => {
    switch (impact) {
      case 'immediate': return 'text-red-400 bg-red-500/20 border-red-500/50';
      case 'short-term': return 'text-orange-400 bg-orange-500/20 border-orange-500/50';
      case 'long-term': return 'text-blue-400 bg-blue-500/20 border-blue-500/50';
      default: return 'text-gray-400 bg-gray-500/20 border-gray-500/50';
    }
  };

  const getSentimentIcon = (score: number) => {
    if (score >= 0.1) return <TrendingUp className="w-5 h-5" />;
    if (score <= -0.1) return <TrendingDown className="w-5 h-5" />;
    return <Minus className="w-5 h-5" />;
  };

  const colors = getSentimentColor(data.score);
  const sentimentCategory = getSentimentCategory(data.score);
  
  // Calculate positions for the gauge
  const gaugeValue = ((data.score + 1) / 2) * 100; // Convert -1 to 1 range to 0 to 100
  const confidenceHeight = data.confidence * 100;

  return (
    <div className={`p-4 bg-white/5 backdrop-blur-sm rounded-lg border border-white/10 ${className}`}>
      <div className="flex items-center justify-between mb-4">
        <h3 className="font-semibold text-white flex items-center gap-2">
          {getSentimentIcon(data.score)}
          Sentiment Analysis
        </h3>
        <div className="flex items-center gap-2 text-xs text-gray-400">
          <MessageSquare className="w-4 h-4" />
          {data.newsCount} news items
        </div>
      </div>

      {/* Sentiment Gauge */}
      <div className="mb-4">
        <div className="flex justify-between items-center mb-2">
          <span className="text-sm text-gray-400">Sentiment Score</span>
          <span className={`font-semibold ${colors.text}`}>
            {data.score.toFixed(3)}
          </span>
        </div>
        
        <div className="relative h-3 bg-gray-700 rounded-full overflow-hidden">
          {/* Background gradient */}
          <div className="absolute inset-0 bg-gradient-to-r from-red-500 via-gray-500 to-green-500 opacity-50"></div>
          
          {/* Sentiment indicator */}
          <motion.div
            initial={{ width: 0 }}
            animate={{ width: `${gaugeValue}%` }}
            transition={{ duration: 0.8, ease: "easeOut" }}
            className="absolute left-0 top-0 h-full bg-white/20"
          />
          
          {/* Indicator line */}
          <motion.div
            initial={{ left: '50%' }}
            animate={{ left: `${gaugeValue}%` }}
            transition={{ duration: 0.8, ease: "easeOut" }}
            className={`absolute top-0 h-full w-0.5 ${colors.bg} transform -translate-x-1/2`}
          />
        </div>
        
        <div className="flex justify-between text-xs text-gray-400 mt-1">
          <span>Very Negative</span>
          <span>Neutral</span>
          <span>Very Positive</span>
        </div>
      </div>

      {/* Confidence Bar */}
      <div className="mb-4">
        <div className="flex justify-between items-center mb-2">
          <span className="text-sm text-gray-400">Confidence</span>
          <span className="font-semibold text-white">
            {(data.confidence * 100).toFixed(1)}%
          </span>
        </div>
        
        <div className="relative h-2 bg-gray-700 rounded-full overflow-hidden">
          <motion.div
            initial={{ width: 0 }}
            animate={{ width: `${confidenceHeight}%` }}
            transition={{ duration: 0.8, ease: "easeOut", delay: 0.2 }}
            className="absolute left-0 top-0 h-full bg-gradient-to-r from-blue-500 to-purple-500"
          />
        </div>
      </div>

      {/* Sentiment Category */}
      <div className="mb-4">
        <div className="flex justify-between items-center mb-2">
          <span className="text-sm text-gray-400">Category</span>
          <span className={`px-2 py-1 rounded-full text-xs font-medium ${colors.text} ${colors.bg}/20 border ${colors.border}/50`}>
            {sentimentCategory}
          </span>
        </div>
      </div>

      {/* Market Impact */}
      <div className="mb-4">
        <div className="flex justify-between items-center mb-2">
          <span className="text-sm text-gray-400">Market Impact</span>
          <span className={`px-2 py-1 rounded-full text-xs font-medium capitalize ${getImpactColor(data.impact)} border`}>
            {data.impact}
          </span>
        </div>
      </div>

      {/* Timestamp */}
      {data.timestamp && (
        <div className="flex items-center justify-between text-xs text-gray-400 pt-2 border-t border-white/10">
          <span className="flex items-center gap-1">
            <Clock className="w-3 h-3" />
            {new Date(data.timestamp).toLocaleString()}
          </span>
        </div>
      )}

      {/* Interpretation */}
      <div className="mt-4 p-3 bg-white/5 rounded-lg border border-white/10">
        <h4 className="text-sm font-medium text-white mb-1">Interpretation</h4>
        <p className="text-xs text-gray-400">
          {data.confidence > 0.7 ? (
            data.score > 0.2 ? "Strong positive sentiment detected. Market may react favorably." :
            data.score < -0.2 ? "Strong negative sentiment detected. Market may react unfavorably." :
            "High confidence neutral sentiment. Market reaction likely muted."
          ) : data.confidence > 0.5 ? (
            data.score > 0.1 ? "Moderate positive sentiment with decent confidence." :
            data.score < -0.1 ? "Moderate negative sentiment with decent confidence." :
            "Moderate confidence neutral sentiment."
          ) : (
            "Low confidence sentiment analysis. Technical indicators may be more reliable."
          )}
        </p>
      </div>

      {/* Trading Recommendations */}
      <div className="mt-4 p-3 bg-gradient-to-r from-purple-500/10 to-blue-500/10 rounded-lg border border-purple-500/20">
        <h4 className="text-sm font-medium text-white mb-2 flex items-center gap-2">
          <AlertCircle className="w-4 h-4 text-purple-400" />
          Trading Signal
        </h4>
        <div className="text-xs text-gray-300">
          {data.confidence > 0.7 && Math.abs(data.score) > 0.2 ? (
            <span className={`font-medium ${data.score > 0 ? 'text-green-400' : 'text-red-400'}`}>
              {data.score > 0 ? 'CONSIDER LONG' : 'CONSIDER SHORT'} - High confidence sentiment aligned with {data.impact} impact
            </span>
          ) : data.confidence > 0.5 && Math.abs(data.score) > 0.1 ? (
            <span className="font-medium text-yellow-400">
              MONITOR - Moderate sentiment signal, wait for confirmation
            </span>
          ) : (
            <span className="font-medium text-gray-400">
              NEUTRAL - Sentiment unclear, rely on technical analysis
            </span>
          )}
        </div>
      </div>
    </div>
  );
};

export default SentimentChart;