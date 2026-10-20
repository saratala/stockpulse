# Railway Deployment Guide

This is a monorepo with multiple services. Deploy each service separately in Railway.

## Services to Deploy

### 1. PostgreSQL Database
- In Railway, click "New" → "Database" → "PostgreSQL"
- Note the connection URL provided

### 2. Redis Cache
- In Railway, click "New" → "Database" → "Redis"
- Note the connection URL provided

### 3. Java Backend
- Click "New" → "GitHub Repo" → Select your repo
- Set **Root Directory**: `java-backend`
- Add these environment variables:
  ```
  SPRING_PROFILES_ACTIVE=docker
  SPRING_DATASOURCE_URL=<PostgreSQL URL from step 1>
  SPRING_DATASOURCE_USERNAME=postgres
  SPRING_DATASOURCE_PASSWORD=<from PostgreSQL>
  SPRING_DATA_REDIS_HOST=<Redis host from step 2>
  SPRING_DATA_REDIS_PORT=6379
  FINNHUB_API_KEY=your-finnhub-key
  OPENAI_API_KEY=your-openai-key
  PORT=8181
  ```

### 4. Frontend
- Click "New" → "GitHub Repo" → Select your repo
- Set **Root Directory**: `frontend`
- Add these environment variables:
  ```
  REACT_APP_API_URL=<Java Backend URL from step 3>
  PORT=3010
  ```

## Important Notes

1. **Deploy Order**: Database → Redis → Backend → Frontend
2. **Wait for each service** to be healthy before deploying the next
3. **Update Frontend API URL** after backend is deployed
4. The frontend needs the backend URL to proxy API requests

## Environment Variables Summary

| Service | Variable | Description |
|---------|----------|-------------|
| Backend | `SPRING_DATASOURCE_URL` | PostgreSQL connection string |
| Backend | `FINNHUB_API_KEY` | For real-time stock data |
| Backend | `OPENAI_API_KEY` | For AI sentiment analysis |
| Frontend | `REACT_APP_API_URL` | Backend service URL |
