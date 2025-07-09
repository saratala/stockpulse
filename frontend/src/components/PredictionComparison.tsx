import React from 'react';
import { motion } from 'framer-motion';
import { TrendingUp, TrendingDown, Brain, Activity, Target, Zap, AlertTriangle } from 'lucide-react';

interface PredictionData {
  ticker: string;
  signal_type: string;
  confidence: number;
  screening_score: number;
  predicted_price_1d: number | null;
  current_price: number;
  sentiment_score?: number;
  sentiment_confidence?: number;
  sentiment_impact?: string;
  news_count?: number;
}

interface ComparisonProps {
  withSentiment: PredictionData;
  withoutSentiment: PredictionData;
  className?: string;
}

const PredictionComparison: React.FC<ComparisonProps> = ({ 
  withSentiment, 
  withoutSentiment, 
  className = '' 
}) => {
  const calculatePriceChange = (current: number, predicted: number | null) => {
    if (!predicted) return null;
    return ((predicted - current) / current) * 100;
  };

  const getSignalColor = (signal: string) => {
    switch (signal) {
      case 'BULLISH': return 'text-green-400 bg-green-500/20 border-green-500/50';
      case 'BEARISH': return 'text-red-400 bg-red-500/20 border-red-500/50';
      default: return 'text-gray-400 bg-gray-500/20 border-gray-500/50';
    }
  };

  const getSignalIcon = (signal: string) => {
    switch (signal) {
      case 'BULLISH': return <TrendingUp className="w-4 h-4" />;
      case 'BEARISH': return <TrendingDown className="w-4 h-4" />;
      default: return <Activity className="w-4 h-4" />;
    }
  };

  const getImprovementColor = (improvement: number) => {
    if (improvement > 5) return 'text-green-400';
    if (improvement > 0) return 'text-green-300';
    if (improvement < -5) return 'text-red-400';
    if (improvement < 0) return 'text-red-300';
    return 'text-gray-400';
  };

  const withSentimentChange = calculatePriceChange(withSentiment.current_price, withSentiment.predicted_price_1d);
  const withoutSentimentChange = calculatePriceChange(withoutSentiment.current_price, withoutSentiment.predicted_price_1d);

  const confidenceImprovement = withSentiment.confidence - withoutSentiment.confidence;
  const scoreImprovement = withSentiment.screening_score - withoutSentiment.screening_score;

  return (
    <div className={`p-6 bg-white/5 backdrop-blur-sm rounded-lg border border-white/10 ${className}`}>
      <div className="flex items-center justify-between mb-6">
        <h3 className="text-lg font-semibold text-white flex items-center gap-2">
          <Target className="w-5 h-5 text-blue-400" />
          Prediction Comparison: {withSentiment.ticker}
        </h3>
        <div className="text-sm text-gray-400">
          Current: ${withSentiment.current_price.toFixed(2)}
        </div>
      </div>

      <div className="grid md:grid-cols-2 gap-6">
        {/* Traditional Analysis */}
        <div className="space-y-4">
          <div className="flex items-center gap-2 mb-3">
            <Activity className="w-5 h-5 text-gray-400" />
            <h4 className="font-medium text-white">Traditional Analysis</h4>
          </div>
          
          <div className="space-y-3">
            <div className="flex justify-between items-center">
              <span className="text-sm text-gray-400">Signal</span>
              <span className={`px-2 py-1 rounded-full text-xs font-medium border ${getSignalColor(withoutSentiment.signal_type)}`}>
                {getSignalIcon(withoutSentiment.signal_type)}
                <span className="ml-1">{withoutSentiment.signal_type}</span>
              </span>
            </div>
            
            <div className="flex justify-between items-center">
              <span className="text-sm text-gray-400">Confidence</span>
              <span className="text-sm font-medium text-white">
                {withoutSentiment.confidence.toFixed(1)}%
              </span>
            </div>
            
            <div className="flex justify-between items-center">
              <span className="text-sm text-gray-400">Screening Score</span>
              <span className="text-sm font-medium text-white">
                {withoutSentiment.screening_score.toFixed(1)}
              </span>
            </div>
            
            {withoutSentimentChange !== null && (
              <div className="flex justify-between items-center">
                <span className="text-sm text-gray-400">24h Prediction</span>
                <span className={`text-sm font-medium ${
                  withoutSentimentChange > 0 ? 'text-green-400' : 
                  withoutSentimentChange < 0 ? 'text-red-400' : 'text-gray-400'
                }`}>
                  {withoutSentimentChange > 0 ? '+' : ''}{withoutSentimentChange.toFixed(2)}%
                </span>
              </div>
            )}
          </div>
        </div>

        {/* LLM-Enhanced Analysis */}
        <div className="space-y-4">
          <div className="flex items-center gap-2 mb-3">
            <Brain className="w-5 h-5 text-purple-400" />
            <h4 className="font-medium text-white">LLM-Enhanced Analysis</h4>
          </div>
          
          <div className="space-y-3">
            <div className="flex justify-between items-center">
              <span className="text-sm text-gray-400">Signal</span>
              <span className={`px-2 py-1 rounded-full text-xs font-medium border ${getSignalColor(withSentiment.signal_type)}`}>
                {getSignalIcon(withSentiment.signal_type)}
                <span className="ml-1">{withSentiment.signal_type}</span>
              </span>
            </div>
            
            <div className="flex justify-between items-center">
              <span className="text-sm text-gray-400">Confidence</span>
              <div className="text-right">
                <span className="text-sm font-medium text-white">
                  {withSentiment.confidence.toFixed(1)}%
                </span>
                <span className={`ml-2 text-xs ${getImprovementColor(confidenceImprovement)}`}>
                  {confidenceImprovement > 0 ? '+' : ''}{confidenceImprovement.toFixed(1)}%
                </span>
              </div>
            </div>
            
            <div className="flex justify-between items-center">
              <span className="text-sm text-gray-400">Screening Score</span>
              <div className="text-right">
                <span className="text-sm font-medium text-white">
                  {withSentiment.screening_score.toFixed(1)}
                </span>
                <span className={`ml-2 text-xs ${getImprovementColor(scoreImprovement)}`}>
                  {scoreImprovement > 0 ? '+' : ''}{scoreImprovement.toFixed(1)}
                </span>
              </div>
            </div>
            
            {withSentimentChange !== null && (
              <div className="flex justify-between items-center">
                <span className="text-sm text-gray-400">24h Prediction</span>
                <span className={`text-sm font-medium ${
                  withSentimentChange > 0 ? 'text-green-400' : 
                  withSentimentChange < 0 ? 'text-red-400' : 'text-gray-400'
                }`}>
                  {withSentimentChange > 0 ? '+' : ''}{withSentimentChange.toFixed(2)}%
                </span>
              </div>
            )}
          </div>
        </div>
      </div>

      {/* Sentiment Enhancement Details */}
      {withSentiment.sentiment_score !== undefined && (
        <div className="mt-6 pt-4 border-t border-white/10">
          <div className="flex items-center gap-2 mb-3">
            <Zap className="w-4 h-4 text-yellow-400" />
            <h5 className="font-medium text-white">Sentiment Enhancement</h5>
          </div>
          
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
            <div className="text-center p-3 bg-white/5 rounded-lg">
              <div className="text-lg font-semibold text-purple-400">
                {withSentiment.sentiment_score?.toFixed(3) || '0.000'}
              </div>
              <div className="text-gray-400">Sentiment Score</div>
            </div>
            
            <div className="text-center p-3 bg-white/5 rounded-lg">
              <div className="text-lg font-semibold text-blue-400">
                {((withSentiment.sentiment_confidence || 0) * 100).toFixed(1)}%
              </div>
              <div className="text-gray-400">Confidence</div>
            </div>
            
            <div className="text-center p-3 bg-white/5 rounded-lg">
              <div className="text-lg font-semibold text-green-400">
                {withSentiment.news_count || 0}
              </div>
              <div className="text-gray-400">News Items</div>
            </div>
            
            <div className="text-center p-3 bg-white/5 rounded-lg">
              <div className="text-lg font-semibold text-orange-400 capitalize">
                {withSentiment.sentiment_impact || 'None'}
              </div>
              <div className="text-gray-400">Impact</div>
            </div>
          </div>
        </div>
      )}

      {/* Improvement Summary */}
      <div className="mt-6 pt-4 border-t border-white/10">
        <div className="flex items-center gap-2 mb-3">
          <AlertTriangle className="w-4 h-4 text-yellow-400" />
          <h5 className="font-medium text-white">Enhancement Summary</h5>
        </div>
        
        <div className="p-3 bg-white/5 rounded-lg">
          <p className="text-sm text-gray-300">
            {confidenceImprovement > 0 && scoreImprovement > 0 && (
              <span className="text-green-400">
                ✓ Sentiment analysis improved both confidence (+{confidenceImprovement.toFixed(1)}%) and screening score (+{scoreImprovement.toFixed(1)}).
              </span>
            )}
            {confidenceImprovement > 0 && scoreImprovement <= 0 && (
              <span className="text-yellow-400">
                ⚠ Sentiment analysis improved confidence (+{confidenceImprovement.toFixed(1)}%) but reduced screening score ({scoreImprovement.toFixed(1)}).
              </span>
            )}
            {confidenceImprovement <= 0 && scoreImprovement > 0 && (
              <span className="text-yellow-400">
                ⚠ Sentiment analysis improved screening score (+{scoreImprovement.toFixed(1)}) but reduced confidence ({confidenceImprovement.toFixed(1)}%).
              </span>
            )}
            {confidenceImprovement <= 0 && scoreImprovement <= 0 && (
              <span className="text-red-400">
                ✗ Sentiment analysis reduced both confidence ({confidenceImprovement.toFixed(1)}%) and screening score ({scoreImprovement.toFixed(1)}).
              </span>
            )}
          </p>
        </div>
      </div>
    </div>
  );
};

export default PredictionComparison;