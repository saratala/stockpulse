# Multi-stage Dockerfile for StockPulse (Backend + Frontend in one container)

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
# Stage 3: Production Runtime
# ============================================
FROM eclipse-temurin:21-jre

# Install nginx and other utilities
RUN apt-get update && apt-get install -y \
    nginx \
    curl \
    supervisor \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Copy Java backend JAR
COPY --from=backend-builder /app/backend/build/libs/*.jar app.jar

# Copy React frontend build
COPY --from=frontend-builder /app/frontend/build /usr/share/nginx/html

# Configure nginx to serve frontend and proxy API requests
RUN echo 'server { \n\
    listen 80; \n\
    \n\
    # Serve React frontend \n\
    location / { \n\
        root /usr/share/nginx/html; \n\
        index index.html; \n\
        try_files $uri $uri/ /index.html; \n\
    } \n\
    \n\
    # Proxy API requests to Java backend \n\
    location /api/ { \n\
        proxy_pass http://127.0.0.1:8181; \n\
        proxy_http_version 1.1; \n\
        proxy_set_header Upgrade $http_upgrade; \n\
        proxy_set_header Connection "upgrade"; \n\
        proxy_set_header Host $host; \n\
        proxy_set_header X-Real-IP $remote_addr; \n\
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for; \n\
        proxy_set_header X-Forwarded-Proto $scheme; \n\
        proxy_connect_timeout 60s; \n\
        proxy_send_timeout 60s; \n\
        proxy_read_timeout 60s; \n\
    } \n\
    \n\
    # Proxy SSE stream endpoint \n\
    location /stream/ { \n\
        proxy_pass http://127.0.0.1:8181; \n\
        proxy_http_version 1.1; \n\
        proxy_set_header Connection ""; \n\
        proxy_set_header Cache-Control "no-cache"; \n\
        proxy_buffering off; \n\
        chunked_transfer_encoding off; \n\
    } \n\
}' > /etc/nginx/sites-available/default

# Configure supervisor to run both nginx and Java
RUN echo '[supervisord] \n\
nodaemon=true \n\
\n\
[program:nginx] \n\
command=nginx -g "daemon off;" \n\
autostart=true \n\
autorestart=true \n\
stdout_logfile=/dev/stdout \n\
stdout_logfile_maxbytes=0 \n\
stderr_logfile=/dev/stderr \n\
stderr_logfile_maxbytes=0 \n\
\n\
[program:java] \n\
command=java %(ENV_JAVA_OPTS)s -jar /app/app.jar \n\
autostart=true \n\
autorestart=true \n\
stdout_logfile=/dev/stdout \n\
stdout_logfile_maxbytes=0 \n\
stderr_logfile=/dev/stderr \n\
stderr_logfile_maxbytes=0 \n\
' > /etc/supervisor/conf.d/stockpulse.conf

# Environment variables
ENV JAVA_OPTS="-Xmx1g -Xms512m -XX:+UseG1GC"
ENV SPRING_PROFILES_ACTIVE=docker
ENV PORT=80

# Expose port
EXPOSE 80

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD curl -f http://localhost/api/v1/health || exit 1

# Start supervisor (runs both nginx and java)
CMD ["/usr/bin/supervisord", "-c", "/etc/supervisor/supervisord.conf"]
