#!/usr/bin/env python3
"""
Test script to validate LLM sentiment analysis integration
"""

import asyncio
import os
from datetime import datetime
from llm_sentiment_analyzer import MarketSentimentAnalyzer, NewsDataFetcher

async def test_news_fetching():
    """Test news fetching functionality"""
    print("=" * 60)
    print("TESTING NEWS FETCHING")
    print("=" * 60)
    
    news_fetcher = NewsDataFetcher()
    
    # Test ticker news fetching
    test_ticker = "AAPL"
    print(f"\nFetching news for {test_ticker}...")
    
    ticker_news = await news_fetcher.fetch_ticker_news(test_ticker, hours_back=24)
    print(f"Found {len(ticker_news)} news items for {test_ticker}")
    
    if ticker_news:
        print(f"\nSample news item:")
        sample = ticker_news[0]
        print(f"Title: {sample.get('title', 'N/A')}")
        print(f"Source: {sample.get('source', 'N/A')}")
        print(f"Timestamp: {sample.get('timestamp', 'N/A')}")
        print(f"Content preview: {sample.get('content', 'N/A')[:200]}...")
    
    # Test market news fetching
    print(f"\nFetching market news...")
    market_news = await news_fetcher.fetch_market_news(limit=10)
    print(f"Found {len(market_news)} market news items")
    
    return len(ticker_news) > 0 or len(market_news) > 0

async def test_sentiment_analysis():
    """Test sentiment analysis functionality"""
    print("\n" + "=" * 60)
    print("TESTING SENTIMENT ANALYSIS")
    print("=" * 60)
    
    # Check if OpenAI API key is available
    if not os.getenv('OPENAI_API_KEY'):
        print("⚠️  OpenAI API key not found. Skipping LLM sentiment analysis test.")
        print("   Set OPENAI_API_KEY environment variable to test LLM functionality.")
        return False
    
    analyzer = MarketSentimentAnalyzer()
    
    # Test with sample financial news
    sample_texts = [
        "Apple reports record quarterly earnings, beating analyst expectations by 15%",
        "Tesla stock plummets after disappointing delivery numbers and production delays",
        "Federal Reserve hints at potential interest rate cuts in coming months",
        "Market volatility expected to continue amid geopolitical tensions"
    ]
    
    print(f"\nAnalyzing {len(sample_texts)} sample news items...")
    
    # Create context for sentiment analysis
    context = {
        'ticker': 'AAPL',
        'market_conditions': 'neutral',
        'sector': 'Technology',
        'volatility_regime': 'normal',
        'timestamp': datetime.now()
    }
    
    try:
        sentiment_signals = await analyzer.analyze_batch(sample_texts, context)
        
        print(f"Generated {len(sentiment_signals)} sentiment signals")
        
        for i, signal in enumerate(sentiment_signals):
            print(f"\nSignal {i+1}:")
            print(f"  Text: {signal.content[:100]}...")
            print(f"  Sentiment: {signal.sentiment_score:.3f}")
            print(f"  Confidence: {signal.confidence:.3f}")
            print(f"  Impact: {signal.market_impact_prediction}")
            print(f"  Key Topics: {', '.join(signal.key_topics)}")
        
        return len(sentiment_signals) > 0
        
    except Exception as e:
        print(f"❌ Error during sentiment analysis: {e}")
        return False

async def test_advanced_scheduler_integration():
    """Test advanced scheduler integration"""
    print("\n" + "=" * 60)
    print("TESTING ADVANCED SCHEDULER INTEGRATION")
    print("=" * 60)
    
    try:
        from advanced_scheduler import AdvancedPredictionScheduler
        
        scheduler = AdvancedPredictionScheduler()
        
        # Test sentiment analysis for a single ticker
        test_ticker = "AAPL"
        print(f"\nTesting sentiment analysis for {test_ticker}...")
        
        sentiment_data = await scheduler.analyze_sentiment_for_ticker(test_ticker)
        
        print(f"Sentiment Analysis Results:")
        print(f"  Sentiment Score: {sentiment_data.get('sentiment_score', 0):.3f}")
        print(f"  Confidence: {sentiment_data.get('sentiment_confidence', 0):.3f}")
        print(f"  Impact: {sentiment_data.get('sentiment_impact', 'negligible')}")
        print(f"  News Count: {sentiment_data.get('news_count', 0)}")
        print(f"  Sentiment Boost: {sentiment_data.get('sentiment_boost', 0):.1f}")
        
        # Test confidence adjustment
        original_confidence = 70.0
        adjusted_confidence = scheduler.adjust_confidence_with_sentiment(
            original_confidence, 'BULLISH', sentiment_data
        )
        
        print(f"\nConfidence Adjustment Test:")
        print(f"  Original Confidence: {original_confidence:.1f}%")
        print(f"  Adjusted Confidence: {adjusted_confidence:.1f}%")
        print(f"  Adjustment: {adjusted_confidence - original_confidence:+.1f}%")
        
        return True
        
    except Exception as e:
        print(f"❌ Error during scheduler integration test: {e}")
        return False

async def run_all_tests():
    """Run all integration tests"""
    print("🧪 LLM SENTIMENT ANALYSIS INTEGRATION TESTS")
    print("=" * 60)
    
    test_results = {}
    
    # Test 1: News Fetching
    try:
        test_results['news_fetching'] = await test_news_fetching()
    except Exception as e:
        print(f"❌ News fetching test failed: {e}")
        test_results['news_fetching'] = False
    
    # Test 2: Sentiment Analysis
    try:
        test_results['sentiment_analysis'] = await test_sentiment_analysis()
    except Exception as e:
        print(f"❌ Sentiment analysis test failed: {e}")
        test_results['sentiment_analysis'] = False
    
    # Test 3: Advanced Scheduler Integration
    try:
        test_results['scheduler_integration'] = await test_advanced_scheduler_integration()
    except Exception as e:
        print(f"❌ Scheduler integration test failed: {e}")
        test_results['scheduler_integration'] = False
    
    # Print summary
    print("\n" + "=" * 60)
    print("TEST SUMMARY")
    print("=" * 60)
    
    for test_name, passed in test_results.items():
        status = "✅ PASSED" if passed else "❌ FAILED"
        print(f"{test_name.replace('_', ' ').title()}: {status}")
    
    overall_success = all(test_results.values())
    print(f"\nOverall Result: {'✅ ALL TESTS PASSED' if overall_success else '❌ SOME TESTS FAILED'}")
    
    if not overall_success:
        print("\n📋 TROUBLESHOOTING:")
        if not test_results.get('news_fetching'):
            print("  • Check internet connection and yfinance library")
        if not test_results.get('sentiment_analysis'):
            print("  • Verify OPENAI_API_KEY environment variable is set")
            print("  • Check OpenAI API quota and permissions")
        if not test_results.get('scheduler_integration'):
            print("  • Verify all required modules are properly imported")
            print("  • Check database connection settings")
    
    return overall_success

if __name__ == "__main__":
    asyncio.run(run_all_tests())