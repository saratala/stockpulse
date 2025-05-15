import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { StockPrediction } from '../types';
import { fetchLatestPredictions } from '../services/api';

const Dashboard = () => {
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
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
      {predictions.map((prediction) => (
        <Link 
          key={prediction.ticker}
          to={`/stock/${prediction.ticker}`}
          className="block"
        >
          <div className="bg-white p-4 rounded shadow hover:shadow-lg transition-shadow">
            <h2 className="text-xl font-bold">{prediction.ticker}</h2>
            <p>Predicted Movement: {prediction.predicted_movement_percent.toFixed(2)}%</p>
            <p>Confidence: {(prediction.confidence_score * 100).toFixed(1)}%</p>
          </div>
        </Link>
      ))}
    </div>
  );
};

export default Dashboard;