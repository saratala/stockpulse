# Multi-stage Dockerfile for StockPulse
# All-in-one: PostgreSQL + Redis + Java Backend + React Frontend

# ============================================
# Stage 1: Build Java Backend
# ============================================
FROM gradle:8.5-jdk21 AS backend-builder

WORKDIR /app/backend

COPY java-backend/build.gradle .
COPY java-backend/settings.gradle .
RUN gradle dependencies --no-daemon || true
COPY java-backend/src src
RUN gradle build -x test --no-daemon

# ============================================
# Stage 2: Build React Frontend
# ============================================
FROM node:18-alpine AS frontend-builder

WORKDIR /app/frontend

COPY frontend/package*.json ./
RUN npm install --legacy-peer-deps
COPY frontend/ .
ENV REACT_APP_API_URL=/api/v1
RUN npm run build

# ============================================
# Stage 3: Production Runtime
# ============================================
FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive
ENV TZ=UTC

# Install packages
RUN apt-get update && apt-get install -y \
    openjdk-21-jre-headless \
    postgresql \
    postgresql-contrib \
    redis-server \
    nginx \
    curl \
    supervisor \
    sudo \
    gettext-base \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy Java backend
COPY --from=backend-builder /app/backend/build/libs/*.jar app.jar

# Copy React frontend
COPY --from=frontend-builder /app/frontend/build /usr/share/nginx/html

# Copy database files
COPY db/init.sql /app/init.sql
COPY db/backup/stockpulse_data.sql /app/data.sql

# Copy configuration files
COPY scripts/start.sh /app/start.sh
COPY scripts/supervisord.conf /etc/supervisor/conf.d/stockpulse.conf
COPY scripts/nginx.conf /app/nginx.conf.template

# Configure nginx - remove default site
RUN rm -f /etc/nginx/sites-enabled/default /etc/nginx/sites-available/default

# Configure Redis
RUN sed -i 's/^bind .*/bind 127.0.0.1/' /etc/redis/redis.conf && \
    sed -i 's/^daemonize yes/daemonize no/' /etc/redis/redis.conf && \
    echo "maxmemory 128mb" >> /etc/redis/redis.conf && \
    echo "maxmemory-policy allkeys-lru" >> /etc/redis/redis.conf

# Setup directories and permissions
RUN mkdir -p /var/log/supervisor && \
    mkdir -p /var/lib/postgresql/14/main && \
    chown -R postgres:postgres /var/lib/postgresql && \
    chmod +x /app/start.sh

# Environment variables
ENV SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/stockpulse
ENV SPRING_DATASOURCE_USERNAME=postgres
ENV SPRING_DATASOURCE_PASSWORD=postgres
ENV SPRING_DATA_REDIS_HOST=localhost
ENV SPRING_DATA_REDIS_PORT=6379
ENV PORT=80

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=10s --start-period=180s --retries=5 \
    CMD curl -f http://localhost:${PORT}/api/v1/health || exit 1

CMD ["/app/start.sh"]
