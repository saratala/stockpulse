# 🚀 StockPulse Quick Start Guide

Get up and running with the Apple Glassmorphic StockPulse in under 5 minutes!

## 🎯 Quick Setup (3 Steps)

### 1️⃣ Set Up Environment
```bash
# Navigate to project
cd /Users/saratala/Projects/stockpulse

# Copy example environment file
cp .env.example .env

# Edit .env and add your API keys (minimum required):
# - POLYGON_API_KEY
# - SERPAPI_KEY
```

### 2️⃣ Start Everything with Docker
```bash
# Build and run all services
docker-compose up --build
```

### 3️⃣ Access the Application
Open your browser to: **http://localhost:3010**

## 🎨 Explore New Features

### Glassmorphic Screener
Navigate to: **http://localhost:3010/screener**
- 🔍 Advanced stock screening with EMA stack filters
- 💎 Liquid glass signal cards with animations
- 📱 Pull-to-refresh functionality
- 🎯 Real-time signal detection

### Stock Analysis
Navigate to: **http://localhost:3010/analysis/AAPL**
- 📊 Comprehensive technical analysis
- 🎨 Beautiful glassmorphic charts
- 📈 Heikin Ashi signals
- 🔔 Confidence scoring

## 📱 Mobile Experience

1. Open Chrome DevTools (F12)
2. Toggle device mode (Ctrl+Shift+M)
3. Experience the mobile-first design

## 🛑 Stop Services
```bash
# Stop all services
docker-compose down

# Stop and clean everything
docker-compose down -v
```

## 🔥 That's It!

You're now running a professional-grade stock screening application with:
- ✨ Apple-quality glassmorphic design
- 📱 Mobile-first responsive interface
- 🚀 Real-time data updates
- 🎯 Advanced signal detection
- 💎 Liquid glass animations

Enjoy your new StockPulse experience! 🎉