# Multi-stage Dockerfile for StockPulse
# All-in-one: PostgreSQL + Redis + Java Backend + React Frontend

# ============================================
# Stage 1: Build Java Backend
# ============================================
FROM gradle:8.5-jdk21 AS backend-builder

WORKDIR /app/backend

# Copy backend build files
COPY java-backend/build.gradle .
COPY java-backend/settings.gradle .

# Download dependencies
RUN gradle dependencies --no-daemon || true

# Copy backend source
COPY java-backend/src src

# Build the application
RUN gradle build -x test --no-daemon

# ============================================
# Stage 2: Build React Frontend
# ============================================
FROM node:18-alpine AS frontend-builder

WORKDIR /app/frontend

# Copy package files
COPY frontend/package*.json ./

# Install dependencies
RUN npm install --legacy-peer-deps

# Copy frontend source
COPY frontend/ .

# Set API URL for production build
ENV REACT_APP_API_URL=/api/v1

# Build for production
RUN npm run build

# ============================================
# Stage 3: Production Runtime (All-in-One)
# ============================================
FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

# Install all required packages
RUN apt-get update && apt-get install -y \
    # Java runtime
    openjdk-21-jre-headless \
    # PostgreSQL
    postgresql-14 \
    postgresql-contrib-14 \
    # Redis
    redis-server \
    # Nginx
    nginx \
    # Utilities
    curl \
    supervisor \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# ============================================
# Configure PostgreSQL
# ============================================
USER postgres

# Initialize PostgreSQL and create database
RUN /etc/init.d/postgresql start && \
    psql --command "ALTER USER postgres PASSWORD 'postgres';" && \
    createdb -O postgres stockpulse && \
    /etc/init.d/postgresql stop

# Copy database schema and data
COPY db/init.sql /docker-entrypoint-initdb.d/01-init.sql
COPY db/backup/stockpulse_data.sql /docker-entrypoint-initdb.d/02-data.sql

# Configure PostgreSQL to accept connections
RUN echo "host all all 0.0.0.0/0 md5" >> /etc/postgresql/14/main/pg_hba.conf && \
    echo "listen_addresses='*'" >> /etc/postgresql/14/main/postgresql.conf

USER root

# ============================================
# Configure Redis
# ============================================
RUN sed -i 's/bind 127.0.0.1/bind 0.0.0.0/' /etc/redis/redis.conf && \
    echo "maxmemory 256mb" >> /etc/redis/redis.conf && \
    echo "maxmemory-policy allkeys-lru" >> /etc/redis/redis.conf

# ============================================
# Copy Java backend
# ============================================
COPY --from=backend-builder /app/backend/build/libs/*.jar app.jar

# ============================================
# Copy React frontend
# ============================================
COPY --from=frontend-builder /app/frontend/build /usr/share/nginx/html

# ============================================
# Configure Nginx
# ============================================
RUN echo 'server { \n\
    listen 80; \n\
    \n\
    location / { \n\
        root /usr/share/nginx/html; \n\
        index index.html; \n\
        try_files $uri $uri/ /index.html; \n\
    } \n\
    \n\
    location /api/ { \n\
        proxy_pass http://127.0.0.1:8181; \n\
        proxy_http_version 1.1; \n\
        proxy_set_header Upgrade $http_upgrade; \n\
        proxy_set_header Connection "upgrade"; \n\
        proxy_set_header Host $host; \n\
        proxy_set_header X-Real-IP $remote_addr; \n\
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for; \n\
        proxy_connect_timeout 60s; \n\
        proxy_send_timeout 60s; \n\
        proxy_read_timeout 60s; \n\
    } \n\
    \n\
    location /stream/ { \n\
        proxy_pass http://127.0.0.1:8181; \n\
        proxy_http_version 1.1; \n\
        proxy_set_header Connection ""; \n\
        proxy_set_header Cache-Control "no-cache"; \n\
        proxy_buffering off; \n\
        chunked_transfer_encoding off; \n\
    } \n\
}' > /etc/nginx/sites-available/default

# ============================================
# Configure Supervisor
# ============================================
RUN echo '[supervisord] \n\
nodaemon=true \n\
logfile=/var/log/supervisor/supervisord.log \n\
pidfile=/var/run/supervisord.pid \n\
\n\
[program:postgresql] \n\
command=/usr/lib/postgresql/14/bin/postgres -D /var/lib/postgresql/14/main -c config_file=/etc/postgresql/14/main/postgresql.conf \n\
user=postgres \n\
autostart=true \n\
autorestart=true \n\
priority=1 \n\
stdout_logfile=/dev/stdout \n\
stdout_logfile_maxbytes=0 \n\
stderr_logfile=/dev/stderr \n\
stderr_logfile_maxbytes=0 \n\
\n\
[program:redis] \n\
command=/usr/bin/redis-server /etc/redis/redis.conf \n\
autostart=true \n\
autorestart=true \n\
priority=2 \n\
stdout_logfile=/dev/stdout \n\
stdout_logfile_maxbytes=0 \n\
stderr_logfile=/dev/stderr \n\
stderr_logfile_maxbytes=0 \n\
\n\
[program:nginx] \n\
command=/usr/sbin/nginx -g "daemon off;" \n\
autostart=true \n\
autorestart=true \n\
priority=3 \n\
stdout_logfile=/dev/stdout \n\
stdout_logfile_maxbytes=0 \n\
stderr_logfile=/dev/stderr \n\
stderr_logfile_maxbytes=0 \n\
\n\
[program:java] \n\
command=/bin/bash -c "sleep 10 && java %(ENV_JAVA_OPTS)s -jar /app/app.jar" \n\
autostart=true \n\
autorestart=true \n\
priority=4 \n\
stdout_logfile=/dev/stdout \n\
stdout_logfile_maxbytes=0 \n\
stderr_logfile=/dev/stderr \n\
stderr_logfile_maxbytes=0 \n\
' > /etc/supervisor/conf.d/stockpulse.conf

RUN mkdir -p /var/log/supervisor

# ============================================
# Startup script to initialize database
# ============================================
RUN echo '#!/bin/bash \n\
set -e \n\
\n\
# Start PostgreSQL temporarily to load data \n\
su - postgres -c "/usr/lib/postgresql/14/bin/pg_ctl -D /var/lib/postgresql/14/main -l /var/log/postgresql/startup.log start" \n\
sleep 5 \n\
\n\
# Load schema and data if not already loaded \n\
if [ ! -f /app/.db_initialized ]; then \n\
    echo "Initializing database with schema and data..." \n\
    su - postgres -c "psql stockpulse < /docker-entrypoint-initdb.d/01-init.sql" || true \n\
    su - postgres -c "psql stockpulse < /docker-entrypoint-initdb.d/02-data.sql" || true \n\
    touch /app/.db_initialized \n\
    echo "Database initialized!" \n\
fi \n\
\n\
# Stop PostgreSQL (supervisor will start it) \n\
su - postgres -c "/usr/lib/postgresql/14/bin/pg_ctl -D /var/lib/postgresql/14/main stop" \n\
sleep 2 \n\
\n\
# Start supervisor \n\
exec /usr/bin/supervisord -c /etc/supervisor/supervisord.conf \n\
' > /app/start.sh && chmod +x /app/start.sh

# ============================================
# Environment variables
# ============================================
# Use default profile (not docker) since services are on localhost
ENV JAVA_OPTS="-Xmx1g -Xms512m -XX:+UseG1GC"
ENV SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/stockpulse
ENV SPRING_DATASOURCE_USERNAME=postgres
ENV SPRING_DATASOURCE_PASSWORD=postgres
ENV SPRING_DATA_REDIS_HOST=localhost
ENV SPRING_DATA_REDIS_PORT=6379
ENV PORT=80

# Expose ports
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=120s --retries=3 \
    CMD curl -f http://localhost/api/v1/health || exit 1

# Start everything
CMD ["/app/start.sh"]
