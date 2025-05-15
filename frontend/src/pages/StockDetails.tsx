import React, { FC, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { StockPrediction, SentimentData } from '../types';
import { fetchSentimentData } from '../services/api';

type StockParams = {
  ticker: string;
};

const StockDetails: FC = () => {
  // Specify the generic type parameter for useParams
  const { ticker } = useParams<StockParams>();
  const [loading, setLoading] = useState<boolean>(true);
  const [sentiment, setSentiment] = useState<SentimentData[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const loadData = async () => {
      try {
        if (ticker) {
          const sentimentData = await fetchSentimentData(ticker);
          setSentiment(sentimentData);
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : 'An error occurred');
      } finally {
        setLoading(false);
      }
    };

    loadData();
  }, [ticker]);

  if (loading) return <div className="p-4">Loading...</div>;
  if (error) return <div className="p-4 text-red-600">Error: {error}</div>;

  return (
    <div className="p-4">
      <h1 className="text-2xl font-bold mb-6">Stock Details: {ticker}</h1>
      
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {/* Sentiment Analysis Section */}
        <div className="bg-white rounded-lg shadow p-6">
          <h2 className="text-xl font-semibold mb-4">Sentiment Analysis</h2>
          {sentiment.length > 0 ? (
            <div className="space-y-4">
              {sentiment.map((data, index) => (
                <div key={index} className="border-b pb-4">
                  <p className="font-medium">Score: {data.sentiment_score.toFixed(2)}</p>
                  <p className={`${
                    data.polarity === 'positive' ? 'text-green-600' :
                    data.polarity === 'negative' ? 'text-red-600' :
                    'text-gray-600'
                  }`}>
                    Polarity: {data.polarity}
                  </p>
                  <p className="text-sm text-gray-500">
                    {new Date(data.created_at).toLocaleDateString()}
                  </p>
                </div>
              ))}
            </div>
          ) : (
            <p className="text-gray-500">No sentiment data available</p>
          )}
        </div>
      </div>
    </div>
  );
};

export default StockDetails;