# Multi-stage build for Java Spring Boot application
FROM gradle:8.5-jdk21 AS builder

# Set working directory
WORKDIR /app

# Copy build files
COPY build.gradle .
COPY settings.gradle .

# Download dependencies (for better caching)
RUN gradle dependencies --no-daemon

# Copy source code
COPY src src

# Build the application
RUN gradle build -x test --no-daemon

# Runtime stage
FROM eclipse-temurin:21-jre

# Install necessary packages
RUN apt-get update && apt-get install -y \
    curl \
    netcat-openbsd \
    && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Create application user
RUN groupadd -r stockpulse && useradd -r -g stockpulse stockpulse

# Copy built jar from builder stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Create logs directory
RUN mkdir -p /app/logs && chown -R stockpulse:stockpulse /app

# Switch to application user
USER stockpulse

# Expose port
EXPOSE 8181

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8181/api/v1/actuator/health || exit 1

# Set JVM options for container
ENV JAVA_OPTS="-Xmx2g -Xms1g -XX:+UseG1GC -XX:+UseStringDeduplication -Dspring.profiles.active=docker"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]