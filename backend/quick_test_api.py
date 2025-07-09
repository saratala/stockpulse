#!/usr/bin/env python3
"""
Quick Test API to verify real predictions
"""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
import asyncpg
import os

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.get("/test-predictions")
async def test_predictions():
    try:
        conn = await asyncpg.connect(
            host='stockpulse_db',
            user='postgres',
            password='postgres',
            database='stockpulse'
        )
        
        # Get recent predictions
        rows = await conn.fetch("""
            SELECT ticker, signal_type, confidence, current_price, 
                   predicted_price_1h, predicted_price_1d, timestamp
            FROM signal_predictions 
            WHERE timestamp >= NOW() - INTERVAL '1 hour'
            ORDER BY timestamp DESC 
            LIMIT 5
        """)
        
        result = []
        for row in rows:
            result.append({
                "ticker": row['ticker'],
                "signal_type": row['signal_type'],
                "confidence": row['confidence'],
                "current_price": row['current_price'],
                "predicted_price_1h": row['predicted_price_1h'],
                "predicted_price_1d": row['predicted_price_1d'],
                "timestamp": str(row['timestamp'])
            })
        
        await conn.close()
        return {"count": len(result), "predictions": result}
        
    except Exception as e:
        return {"error": str(e)}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001)