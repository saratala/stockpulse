import React, { FC, useEffect, useState } from 'react';
import { StockPrediction } from '../types';
import { fetchLatestPredictions } from '../services/api';

const Predictions: FC = () => {
  const [predictions, setPredictions] = useState<StockPrediction[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const loadPredictions = async () => {
      try {
        const data = await fetchLatestPredictions();
        setPredictions(data);
      } catch (error) {
        console.error('Error loading predictions:', error);
      } finally {
        setLoading(false);
      }
    };

    loadPredictions();
  }, []);

  if (loading) return <div>Loading...</div>;

  return (
    <div className="p-4">
      <h1 className="text-2xl font-bold mb-4">Stock Predictions</h1>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {predictions.map((prediction) => (
          <div 
            key={`${prediction.ticker}-${prediction.prediction_date}`}
            className="bg-white p-4 rounded shadow"
          >
            <h2 className="text-xl font-semibold">{prediction.ticker}</h2>
            <p>Prediction Date: {new Date(prediction.prediction_date).toLocaleDateString()}</p>
            <p>Target Date: {new Date(prediction.target_date).toLocaleDateString()}</p>
            <p>Movement: {prediction.predicted_movement_percent.toFixed(2)}%</p>
            <p>Direction: {prediction.predicted_direction > 0 ? 'Up' : 'Down'}</p>
            <p>Confidence: {(prediction.confidence_score * 100).toFixed(1)}%</p>
          </div>
        ))}
      </div>
    </div>
  );
};

export default Predictions;