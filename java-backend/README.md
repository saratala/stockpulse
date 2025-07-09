# StockPulse Agent - Java Spring Boot Backend

A high-performance Java Spring Boot backend for real-time stock analysis, technical indicators, and trading signals.

## Features

- **Real-time Stock Data**: Fetches live stock prices from Yahoo Finance API
- **Technical Indicators**: Comprehensive technical analysis using TA4J library
- **Signal Generation**: Automated trading signals and predictions
- **Stock Screening**: Advanced stock screening with multiple criteria
- **Sentiment Analysis**: Integration with news sentiment data
- **Caching**: Redis-based caching for improved performance
- **Time-series Data**: TimescaleDB for efficient time-series storage
- **Containerized**: Full Docker support with docker-compose

## Tech Stack

- **Framework**: Spring Boot 3.2.0
- **Language**: Java 21
- **Database**: PostgreSQL with TimescaleDB extension
- **Caching**: Redis
- **Technical Analysis**: TA4J library
- **Build Tool**: Gradle 8.5
- **Containerization**: Docker & Docker Compose
- **Reverse Proxy**: Nginx

## Quick Start

### Prerequisites
- Docker and Docker Compose
- Java 21 (for local development)

### Running with Docker (Recommended)

1. **Build and start all services:**
   ```bash
   docker-compose up --build
   ```

2. **Access the API:**
   - API Base URL: `http://localhost:8181/api/v1/`
   - Health Check: `http://localhost:8181/api/v1/health`
   - Nginx Proxy: `http://localhost:80/`

3. **Stop services:**
   ```bash
   docker-compose down
   ```

### Local Development

1. **Start database services:**
   ```bash
   docker-compose up postgres redis
   ```

2. **Run the application:**
   ```bash
   ./gradlew bootRun
   ```

3. **For debugging:**
   ```bash
   ./gradlew bootRun --debug-jvm
   ```

## API Endpoints

### Health & Status
- `GET /` - Root endpoint
- `GET /health` - Health check

### Stock Screening
- `GET /screener/run` - Run stock screener
- `GET /screener/signals` - Get Heikin Ashi signals

### Predictions
- `GET /predictions/history` - Get prediction history
- `GET /predictions/summary` - Get prediction summary
- `GET /predictions/ticker/{ticker}` - Get ticker predictions

### Stock Data
- `GET /stocks` - Get all stocks
- `GET /stocks/{ticker}` - Get stock details
- `GET /stocks/{ticker}/prices` - Get stock price history
- `GET /stocks/{ticker}/prices/latest` - Get latest price

### Sentiment Analysis
- `GET /sentiment/{ticker}` - Get sentiment by ticker
- `GET /sentiment/summary` - Get sentiment summary

### Data Management
- `POST /data/update/realtime` - Update real-time data
- `POST /data/update/screening` - Update screening data
- `POST /data/update/predictions` - Generate predictions
- `POST /data/update/all` - Update all data

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Active Spring profiles | `docker` |
| `SPRING_DATASOURCE_URL` | Database URL | `jdbc:postgresql://postgres:5432/stockpulse` |
| `SPRING_DATASOURCE_USERNAME` | Database username | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | Database password | `postgres` |
| `SPRING_DATA_REDIS_HOST` | Redis host | `redis` |
| `SPRING_DATA_REDIS_PORT` | Redis port | `6379` |
| `JAVA_OPTS` | JVM options | `-Xmx2g -Xms1g -XX:+UseG1GC` |

### Application Properties

Key configuration options in `application.yml`:

```yaml
app:
  scheduling:
    enabled: true
    screening:
      cron: "0 */15 * * * *"  # Every 15 minutes
    predictions:
      cron: "0 */30 * * * *"  # Every 30 minutes
    price-updates:
      cron: "0 */5 * * * *"   # Every 5 minutes
```

## Database Schema

The application uses the existing TimescaleDB schema with these main tables:

- `stocks` - Basic stock information
- `stock_prices` - Time-series price data (hypertable)
- `technicals` - Technical indicators (hypertable)
- `signal_predictions` - Trading signals (hypertable)
- `news_articles` - News articles
- `sentiment_scores` - Sentiment analysis results
- `predictions` - Long-term predictions

## Development

### Building the Application

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Build Docker image
./gradlew dockerBuild
```

### Database Migrations

The application uses Hibernate DDL auto-update. For production, consider using Flyway or Liquibase.

### Adding New Indicators

1. Extend `TechnicalIndicatorService`
2. Add new indicators using TA4J library
3. Update `Technical` entity with new fields
4. Add repository methods if needed

## Monitoring

### Health Checks

- Application: `http://localhost:8181/api/v1/health`
- Database: Built-in PostgreSQL health check
- Redis: Built-in Redis health check

### Logs

- Application logs: `./logs/` directory
- Access logs: Nginx access logs
- Database logs: PostgreSQL logs

### Metrics

Spring Boot Actuator endpoints are available:
- `/actuator/health`
- `/actuator/info`
- `/actuator/metrics`
- `/actuator/prometheus`

## Performance Optimization

### Caching Strategy

- Stock quotes: 5 minutes TTL
- Stock info: 5 minutes TTL
- Screening results: 15 minutes TTL
- Predictions: 30 minutes TTL

### Database Optimization

- TimescaleDB hypertables for time-series data
- Proper indexing on frequently queried columns
- Compression policies for old data
- Retention policies for data lifecycle

### JVM Tuning

The application is configured with G1GC for better performance:
```
-XX:+UseG1GC
-XX:+UseStringDeduplication
-Xmx2g
-Xms1g
```

## Troubleshooting

### Common Issues

1. **Database Connection Error**
   - Check PostgreSQL is running
   - Verify connection string
   - Check network connectivity

2. **Redis Connection Error**
   - Check Redis is running
   - Verify Redis configuration
   - Check memory limits

3. **Yahoo Finance API Errors**
   - Check internet connectivity
   - Verify API rate limits
   - Check ticker symbols

### Debug Mode

Enable debug logging:
```yaml
logging:
  level:
    com.stockpulse.agent: DEBUG
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests
5. Submit a pull request

## License

This project is licensed under the MIT License.