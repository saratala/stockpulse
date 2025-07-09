FROM node:18-alpine

WORKDIR /app

# Install dependencies for better compatibility
RUN apk add --no-cache git python3 make g++

# Copy package files first for better caching
COPY package*.json ./

# Install dependencies with legacy peer deps flag for compatibility
RUN npm install --legacy-peer-deps

# Copy source code
COPY . .

# Set environment variables
ENV NODE_ENV=development
ENV WATCHPACK_POLLING=true
ENV PORT=3010
ENV REACT_APP_API_URL=http://localhost:8000
ENV TSC_COMPILE_ON_ERROR=true
ENV DISABLE_ESLINT_PLUGIN=true

# Start the development server
CMD ["npm", "start"]

# Production stage
#FROM nginx:alpine

# Copy built assets from build stage
#COPY --from=build /app/build /usr/share/nginx/html

# Copy nginx configuration if needed
# COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 3010

#CMD ["nginx", "-g", "daemon off;"]
