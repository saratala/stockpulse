# 🚀 StockPulse Docker Setup & Run Instructions

This guide will walk you through running the StockPulse application with the new Apple Glassmorphic frontend using Docker.

## 📋 Prerequisites

1. **Docker Desktop** installed on your machine
   - [Download for Mac](https://www.docker.com/products/docker-desktop/)
   - [Download for Windows](https://www.docker.com/products/docker-desktop/)
   - [Download for Linux](https://docs.docker.com/engine/install/)

2. **Docker Compose** (comes with Docker Desktop)

3. **At least 4GB of free RAM** allocated to Docker

## 🔧 Step-by-Step Setup Instructions

### Step 1: Clone or Navigate to Your Project

```bash
cd /Users/saratala/Projects/stockpulse
```

### Step 2: Create Environment File

Create a `.env` file in the root directory with your configuration:

```bash
# Create .env file
cat > .env << 'EOF'
# Database Configuration
DATABASE_URL=postgresql://postgres:postgres@db:5432/stockpulse
POSTGRES_DB=stockpulse
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres

# API Keys (Add your actual keys)
POLYGON_API_KEY=your_polygon_api_key_here
SERPAPI_KEY=your_serpapi_key_here

# Email Configuration (Optional)
SMTP_SERVER=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your_email@gmail.com
SMTP_PASSWORD=your_app_password
NOTIFICATION_EMAIL=alerts@yourdomain.com

# Slack/Discord (Optional)
SLACK_WEBHOOK=https://hooks.slack.com/services/YOUR/SLACK/WEBHOOK
DISCORD_WEBHOOK=https://discord.com/api/webhooks/YOUR/DISCORD/WEBHOOK

# Screening Configuration
MIN_SCREENING_SCORE=70
MAX_STOCKS_TO_ANALYZE=100
EOF
```

### Step 3: Build and Start All Services

#### Option A: Full Stack (Recommended)

```bash
# Build and start all services
docker-compose up --build

# Or run in detached mode (background)
docker-compose up --build -d
```

#### Option B: Frontend Development Only

If you only want to run the frontend with the backend:

```bash
# Start backend and database first
docker-compose up -d db backend

# Wait for backend to be healthy (about 30 seconds)
sleep 30

# Then start frontend
docker-compose up frontend
```

### Step 4: Access the Application

Once all services are running, you can access:

- **🎨 Glassmorphic Frontend**: http://localhost:3010
- **🔧 Backend API**: http://localhost:8000
- **📊 Dashboard**: http://localhost:8050
- **🗄️ Database**: localhost:5432

### Step 5: Navigate to New Features

1. Open your browser to **http://localhost:3010**
2. You'll see the enhanced StockPulse with glassmorphic design
3. Navigate to the new features:
   - **Screener**: http://localhost:3010/screener
   - **Analysis**: http://localhost:3010/analysis/AAPL (replace AAPL with any ticker)

## 🎯 Quick Commands Reference

### Start Everything
```bash
docker-compose up --build
```

### Start in Background
```bash
docker-compose up -d
```

### View Logs
```bash
# All services
docker-compose logs -f

# Specific service
docker-compose logs -f frontend
docker-compose logs -f backend
```

### Stop Everything
```bash
docker-compose down
```

### Stop and Remove Volumes (Clean Slate)
```bash
docker-compose down -v
```

### Rebuild Specific Service
```bash
docker-compose up --build frontend
```

## 🔍 Troubleshooting

### Issue: Frontend not loading
```bash
# Check frontend logs
docker-compose logs frontend

# Restart frontend
docker-compose restart frontend
```

### Issue: API connection errors
```bash
# Verify backend is running
docker-compose ps
docker-compose logs backend

# Check backend health
curl http://localhost:8000/health
```

### Issue: Database connection errors
```bash
# Check if database is running
docker-compose ps db

# View database logs
docker-compose logs db
```

### Issue: Port already in use
```bash
# Find and kill process using port 3010
lsof -ti:3010 | xargs kill -9

# Find and kill process using port 8000
lsof -ti:8000 | xargs kill -9
```

### Issue: Docker out of memory
```bash
# Increase Docker memory in Docker Desktop settings
# Recommended: 4GB minimum, 8GB optimal
```

## 🛠️ Development Workflow

### Frontend Hot Reload
The frontend supports hot reload. Any changes to frontend files will automatically reflect in the browser.

### Backend Changes
For backend changes:
```bash
# Restart backend service
docker-compose restart backend
```

### Database Reset
```bash
# Stop services and remove volumes
docker-compose down -v

# Start fresh
docker-compose up --build
```

## 📱 Mobile Testing

To test the mobile-first design:

1. Open Chrome DevTools (F12)
2. Toggle device toolbar (Ctrl+Shift+M or Cmd+Shift+M)
3. Select a mobile device preset
4. Navigate to http://localhost:3010/screener

## 🎨 Viewing New Features

### Screener Dashboard
- Navigate to: http://localhost:3010/screener
- Features:
  - Pull-to-refresh (simulate on desktop with mouse drag)
  - Real-time signal cards with liquid glass effects
  - Advanced filtering and search
  - Touch-optimized interactions

### Stock Analysis
- Navigate to: http://localhost:3010/analysis/AAPL
- Features:
  - Comprehensive technical indicators
  - Glassmorphic tabbed interface
  - Interactive charts
  - Signal confidence visualization

## 🔄 Daily Screening Schedule

To manually trigger the daily screening:

```bash
# Using curl
curl -X POST http://localhost:8000/screener/run-daily

# Or use the frontend button in the Screener page
```

## 📊 Monitoring Performance

### View Container Stats
```bash
docker stats
```

### Check Container Health
```bash
docker-compose ps
```

## 🚢 Production Deployment

For production deployment:

1. Update `.env` with production values
2. Uncomment nginx configuration in frontend/Dockerfile
3. Build production images:

```bash
docker-compose -f docker-compose.prod.yml up --build
```

## 💡 Tips

1. **First Run**: The first build might take 5-10 minutes as it downloads all dependencies
2. **Performance**: Allocate at least 4GB RAM to Docker for smooth operation
3. **Browser**: Use Chrome or Safari for best glassmorphic effects
4. **Mobile View**: The UI is optimized for mobile - try it on your phone!

## 🎉 Success Indicators

You'll know everything is working when:
- ✅ Frontend loads at http://localhost:3010
- ✅ You see the gradient background and glass effects
- ✅ The screener page shows stock cards with animations
- ✅ API endpoints respond at http://localhost:8000
- ✅ No error messages in `docker-compose logs`

## 📞 Need Help?

If you encounter issues:
1. Check the logs: `docker-compose logs -f`
2. Ensure all ports are free (3010, 8000, 8050, 5432)
3. Verify Docker has enough memory allocated
4. Try a clean rebuild: `docker-compose down -v && docker-compose up --build`

Enjoy your new Apple Glassmorphic StockPulse experience! 🚀✨