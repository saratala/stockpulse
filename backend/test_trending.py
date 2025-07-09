#!/usr/bin/env python3
"""
Test script for the trending stocks functionality
"""

import sys
import os

# Add the backend directory to Python path
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from trending_signals import TrendingStockSignals
import json

def test_trending_signals():
    """Test the trending signals functionality"""
    print("🚀 Testing StockPulse Trending Signals...")
    
    # Initialize analyzer
    analyzer = TrendingStockSignals()
    
    # Test with a small set of popular stocks
    test_tickers = ['AAPL', 'MSFT', 'GOOGL', 'TSLA', 'NVDA']
    
    print(f"\n📊 Analyzing {len(test_tickers)} stocks: {', '.join(test_tickers)}")
    
    try:
        # Get trending stocks with signals
        results = analyzer.get_trending_stocks_with_signals(test_tickers)
        
        print(f"\n✅ Successfully analyzed {len(results)} stocks!")
        
        # Display results
        for stock in results:
            print(f"\n🏢 {stock['ticker']} - {stock['name']}")
            print(f"   💰 Price: ${stock['current_price']}")
            print(f"   📈 Signal: {stock['signal']} (Confidence: {stock['confidence']}%)")
            print(f"   📊 5d Return: {stock['return_5d']}%")
            print(f"   📊 RSI: {stock['rsi']}")
            print(f"   📊 Volume Ratio: {stock['volume_ratio']}x")
            
            if stock['bullish_reasons']:
                print(f"   🟢 Bullish: {stock['bullish_reasons'][0]}")
            if stock['bearish_reasons']:
                print(f"   🔴 Bearish: {stock['bearish_reasons'][0]}")
        
        # Test weekly report generation
        print(f"\n📋 Generating weekly report...")
        report = analyzer.generate_weekly_report(test_tickers)
        
        print(f"\n📊 WEEKLY REPORT SUMMARY:")
        print(f"   Total Analyzed: {report['summary']['total_analyzed']}")
        print(f"   Strong Buys: {report['summary']['strong_buys']}")
        print(f"   Buys: {report['summary']['buys']}")
        print(f"   Holds: {report['summary']['holds']}")
        print(f"   Sells: {report['summary']['sells']}")
        print(f"   Strong Sells: {report['summary']['strong_sells']}")
        
        print("\n✅ All tests completed successfully!")
        return True
        
    except Exception as e:
        print(f"\n❌ Error during testing: {e}")
        import traceback
        traceback.print_exc()
        return False

def test_api_endpoints():
    """Test the API endpoints (requires running server)"""
    import requests
    
    base_url = "http://localhost:8000"
    
    print(f"\n🌐 Testing API endpoints at {base_url}...")
    
    try:
        # Test health endpoint
        response = requests.get(f"{base_url}/health", timeout=5)
        if response.status_code == 200:
            print("✅ Health endpoint working")
        else:
            print(f"❌ Health endpoint failed: {response.status_code}")
            return False
        
        # Test trending weekly endpoint
        response = requests.get(f"{base_url}/trending/weekly", timeout=30)
        if response.status_code == 200:
            data = response.json()
            print(f"✅ Weekly trending endpoint working")
            print(f"   Stocks analyzed: {data.get('total_stocks_analyzed', 0)}")
        else:
            print(f"❌ Weekly trending endpoint failed: {response.status_code}")
            print(f"   Response: {response.text}")
        
        # Test market movers endpoint
        response = requests.get(f"{base_url}/trending/movers", timeout=30)
        if response.status_code == 200:
            data = response.json()
            print(f"✅ Market movers endpoint working")
            print(f"   Gainers: {len(data.get('gainers', []))}")
            print(f"   Losers: {len(data.get('losers', []))}")
        else:
            print(f"❌ Market movers endpoint failed: {response.status_code}")
        
        return True
        
    except requests.exceptions.ConnectionError:
        print("❌ Cannot connect to API server. Make sure it's running on port 8000")
        print("   Run: cd /Users/saratala/Projects/stockpulse && docker-compose up")
        return False
    except Exception as e:
        print(f"❌ Error testing API endpoints: {e}")
        return False

if __name__ == "__main__":
    print("="*80)
    print("STOCKPULSE TRENDING FUNCTIONALITY TEST")
    print("="*80)
    
    # Test the trending signals functionality
    signals_success = test_trending_signals()
    
    print("\n" + "-"*80)
    
    # Test API endpoints (optional)
    api_success = test_api_endpoints()
    
    print("\n" + "="*80)
    if signals_success:
        print("🎉 Trending signals functionality is working correctly!")
        print("\n📝 NEXT STEPS:")
        print("1. Start the StockPulse server: docker-compose up")
        print("2. Access weekly trending: http://localhost:8000/trending/weekly")
        print("3. Access market movers: http://localhost:8000/trending/movers")
        print("4. Run the trending analysis: python trending_signals.py")
    else:
        print("❌ Some tests failed. Please check the error messages above.")
    print("="*80)