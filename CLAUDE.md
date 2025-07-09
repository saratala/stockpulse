# StockPulse Project Context

## Important Development Notes

### Docker Environment
- **ALL applications run in Docker containers** - never use `npm start` or direct commands
- Frontend runs on port 3010 via Docker
- Backend runs on port 8181 via Docker
- Use `docker-compose up` to start all services

### Architecture
- Java Spring Boot backend with PostgreSQL + TimescaleDB + Redis
- React TypeScript frontend with Tailwind CSS
- Schedulers running: price updates (5min), sentiment analysis (10min), news fetching (2h), screening (15min), predictions (30min)

### API Integration
- Frontend API base URL: `http://localhost:8181/api/v1`
- LLM Sentiment Analysis integrated with comprehensive UI
- System health monitoring via SchedulerStatus component

### Key Components
- Dashboard: Shows system status, scheduler info, sentiment summary
- LLM Sentiment Analysis: Full sentiment analysis with trending
- All components use default imports (not named imports)

### Current Status
- Frontend: ✅ Running on port 3010, builds successfully
- Python Backend: ❌ Missing dependencies (ta module)
- Java Backend: ✅ Running on port 8181, fully operational
- Database: ✅ Running TimescaleDB on port 5432
- Redis: ✅ Connected and working properly

### Working Endpoints
- Frontend: http://localhost:3010
- Java Backend Health: http://localhost:8181/api/v1/actuator/health
- Stocks API: http://localhost:8181/api/v1/stocks
- Sentiment Analysis: http://localhost:8181/api/v1/sentiment-analysis/analyze/{ticker}

### Common Commands
- Start all services: `docker-compose up`
- Build frontend: `docker-compose build frontend`
- Check logs: `docker-compose logs [service]`
- Java backend container: `stockpulse-java-final`