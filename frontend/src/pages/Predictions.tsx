import React, { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

const Predictions = () => {
  const navigate = useNavigate();

  useEffect(() => {
    // Redirect to screener since it contains the signal analysis and predictions
    navigate('/screener');
  }, [navigate]);

  return null; // Component will redirect immediately
};

export default Predictions;