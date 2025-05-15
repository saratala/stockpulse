FROM node:18-alpine

WORKDIR /app

# Copy package files
# Copy package files first for better caching
COPY package*.json ./


RUN npm install

# Copy source code
COPY . .

# Set environment variables
ENV NODE_ENV=development
ENV WATCHPACK_POLLING=true
ENV PORT=3000

# Build the app
CMD ["npm", "start"]

# Production stage
#FROM nginx:alpine

# Copy built assets from build stage
#COPY --from=build /app/build /usr/share/nginx/html

# Copy nginx configuration if needed
# COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 3000

#CMD ["nginx", "-g", "daemon off;"]
