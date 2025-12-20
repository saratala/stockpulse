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
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy Java backend
COPY --from=backend-builder /app/backend/build/libs/*.jar app.jar

# Copy React frontend
COPY --from=frontend-builder /app/frontend/build /usr/share/nginx/html

# Copy database files
COPY db/init.sql /app/init.sql
COPY db/backup/stockpulse_data.sql /app/data.sql

# ============================================
# Configure Nginx
# ============================================
RUN rm /etc/nginx/sites-enabled/default && \
    echo 'server { \
    listen 80; \
    location / { \
        root /usr/share/nginx/html; \
        index index.html; \
        try_files $uri $uri/ /index.html; \
    } \
    location /api/ { \
        proxy_pass http://127.0.0.1:8181; \
        proxy_http_version 1.1; \
        proxy_set_header Upgrade $http_upgrade; \
        proxy_set_header Connection "upgrade"; \
        proxy_set_header Host $host; \
        proxy_set_header X-Real-IP $remote_addr; \
        proxy_connect_timeout 60s; \
        proxy_send_timeout 60s; \
        proxy_read_timeout 60s; \
    } \
    location /stream/ { \
        proxy_pass http://127.0.0.1:8181; \
        proxy_http_version 1.1; \
        proxy_set_header Connection ""; \
        proxy_buffering off; \
    } \
}' > /etc/nginx/sites-enabled/stockpulse

# ============================================
# Configure Redis
# ============================================
RUN sed -i 's/^bind .*/bind 127.0.0.1/' /etc/redis/redis.conf && \
    sed -i 's/^daemonize yes/daemonize no/' /etc/redis/redis.conf && \
    echo "maxmemory 128mb" >> /etc/redis/redis.conf && \
    echo "maxmemory-policy allkeys-lru" >> /etc/redis/redis.conf

# ============================================
# Configure Supervisor
# ============================================
RUN mkdir -p /var/log/supervisor && \
    echo '[supervisord] \
nodaemon=true \
logfile=/var/log/supervisor/supervisord.log \
\
[program:postgresql] \
command=/usr/lib/postgresql/14/bin/postgres -D /var/lib/postgresql/14/main -c config_file=/etc/postgresql/14/main/postgresql.conf \
user=postgres \
autostart=true \
autorestart=true \
priority=1 \
\
[program:redis] \
command=/usr/bin/redis-server /etc/redis/redis.conf \
autostart=true \
autorestart=true \
priority=2 \
\
[program:nginx] \
command=/usr/sbin/nginx -g "daemon off;" \
autostart=true \
autorestart=true \
priority=3 \
\
[program:java] \
command=/bin/bash -c "sleep 15 && java -Xmx512m -Xms256m -XX:+UseG1GC -jar /app/app.jar" \
autostart=true \
autorestart=true \
priority=4 \
' > /etc/supervisor/conf.d/stockpulse.conf

# ============================================
# Startup script
# ============================================
RUN echo '#!/bin/bash \
set -e \
\
# Initialize PostgreSQL if needed \
if [ ! -f /var/lib/postgresql/14/main/PG_VERSION ]; then \
    echo "Initializing PostgreSQL..." \
    mkdir -p /var/lib/postgresql/14/main \
    chown -R postgres:postgres /var/lib/postgresql \
    sudo -u postgres /usr/lib/postgresql/14/bin/initdb -D /var/lib/postgresql/14/main \
    echo "host all all 0.0.0.0/0 md5" >> /var/lib/postgresql/14/main/pg_hba.conf \
    echo "local all all trust" >> /var/lib/postgresql/14/main/pg_hba.conf \
fi \
\
# Start PostgreSQL temporarily to setup database \
echo "Starting PostgreSQL for initialization..." \
sudo -u postgres /usr/lib/postgresql/14/bin/pg_ctl -D /var/lib/postgresql/14/main -l /tmp/pg.log start \
sleep 5 \
\
# Create database and load data if needed \
if ! sudo -u postgres psql -lqt | cut -d \| -f 1 | grep -qw stockpulse; then \
    echo "Creating database..." \
    sudo -u postgres createdb stockpulse \
    sudo -u postgres psql stockpulse -f /app/init.sql || true \
    sudo -u postgres psql stockpulse -f /app/data.sql || true \
    echo "Database created and data loaded!" \
fi \
\
# Stop PostgreSQL (supervisor will restart it) \
sudo -u postgres /usr/lib/postgresql/14/bin/pg_ctl -D /var/lib/postgresql/14/main stop \
sleep 2 \
\
# Start all services via supervisor \
exec /usr/bin/supervisord -c /etc/supervisor/supervisord.conf \
' > /app/start.sh && chmod +x /app/start.sh

# ============================================
# Environment variables
# ============================================
ENV SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/stockpulse
ENV SPRING_DATASOURCE_USERNAME=postgres
ENV SPRING_DATASOURCE_PASSWORD=postgres
ENV SPRING_DATA_REDIS_HOST=localhost
ENV SPRING_DATA_REDIS_PORT=6379
ENV PORT=80

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=10s --start-period=180s --retries=5 \
    CMD curl -f http://localhost/api/v1/health || exit 1

CMD ["/app/start.sh"]
