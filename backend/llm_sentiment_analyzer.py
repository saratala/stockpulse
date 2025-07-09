#!/usr/bin/env python3
"""
LLM-Powered Market Sentiment Analysis Engine
Advanced sentiment analysis using Large Language Models for alpha generation
"""

import asyncio
import aiohttp
import openai
import logging
import json
import pandas as pd
import numpy as np
from datetime import datetime, timedelta
from dataclasses import dataclass, asdict
from typing import Dict, List, Optional, Any
import re
from textblob import TextBlob
import yfinance as yf
import requests
from concurrent.futures import ThreadPoolExecutor
import hashlib
import os
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

@dataclass
class SentimentSignal:
    """Structured sentiment signal with comprehensive market context"""
    timestamp: datetime
    ticker: str
    source: str
    content: str
    sentiment_score: float  # -1.0 to 1.0
    confidence: float       # 0.0 to 1.0
    key_topics: List[str]
    market_impact_prediction: str  # immediate, short-term, long-term, negligible
    reasoning: str
    raw_content: str
    content_hash: str
    
    def to_dict(self) -> Dict:
        """Convert to dictionary for database storage"""
        return asdict(self)

class MarketSentimentAnalyzer:
    """
    Advanced LLM-powered sentiment analysis system for financial markets
    """
    
    def __init__(self, model_name: str = "gpt-3.5-turbo"):
        self.model_name = model_name
        self.client = openai.OpenAI(api_key=os.getenv('OPENAI_API_KEY'))
        self.logger = logging.getLogger(__name__)
        
        # Cache for avoiding duplicate analysis
        self.analysis_cache = {}
        
        # Market context data
        self.market_regimes = {
            'bull': 'Strong upward market trend with high confidence',
            'bear': 'Significant downward pressure with risk-off sentiment',
            'neutral': 'Sideways market with mixed signals',
            'volatile': 'High volatility with uncertain direction'
        }
        
        # Fallback traditional analyzer
        self.traditional_analyzer = None
        
    async def analyze_batch(self, texts: List[str], context: Dict[str, Any] = None) -> List[SentimentSignal]:
        """Analyze multiple texts in parallel with rate limiting"""
        try:
            # Create semaphore for rate limiting
            semaphore = asyncio.Semaphore(5)  # Max 5 concurrent requests
            
            tasks = [
                self._analyze_single_text_with_semaphore(semaphore, text, context or {})
                for text in texts
            ]
            
            results = await asyncio.gather(*tasks, return_exceptions=True)
            
            # Filter out exceptions and return valid results
            valid_results = [r for r in results if not isinstance(r, Exception)]
            
            # Log any errors
            errors = [r for r in results if isinstance(r, Exception)]
            if errors:
                self.logger.warning(f"Failed to analyze {len(errors)} texts: {errors[:3]}")
            
            return valid_results
            
        except Exception as e:
            self.logger.error(f"Batch analysis failed: {e}")
            return []
    
    async def _analyze_single_text_with_semaphore(self, semaphore: asyncio.Semaphore, text: str, context: Dict[str, Any]) -> SentimentSignal:
        """Analyze single text with rate limiting"""
        async with semaphore:
            return await self._analyze_single_text(text, context)
    
    async def _analyze_single_text(self, text: str, context: Dict[str, Any] = None) -> SentimentSignal:
        """Analyze single text with LLM and create structured signal"""
        try:
            # Create content hash for caching
            content_hash = hashlib.md5(text.encode()).hexdigest()
            
            # Check cache first
            if content_hash in self.analysis_cache:
                cached_result = self.analysis_cache[content_hash]
                self.logger.debug(f"Using cached analysis for content hash {content_hash[:8]}")
                return cached_result
            
            # Build context-aware prompt
            prompt = self._build_sentiment_prompt(text, context or {})
            
            # Get LLM analysis
            response = await self._call_llm_async(prompt)
            
            # Parse LLM response
            signal = self._parse_llm_response(response, text, context or {})
            signal.content_hash = content_hash
            
            # Cache the result
            self.analysis_cache[content_hash] = signal
            
            return signal
            
        except Exception as e:
            self.logger.error(f"LLM analysis failed: {e}")
            return self._create_fallback_signal(text, context or {})
    
    def _build_sentiment_prompt(self, text: str, context: Dict[str, Any]) -> str:
        """Build sophisticated prompt with financial context"""
        
        ticker = context.get('ticker', 'MARKET')
        market_conditions = context.get('market_conditions', 'neutral')
        sector = context.get('sector', 'General')
        volatility = context.get('volatility_regime', 'normal')
        
        base_prompt = f"""
You are a quantitative analyst specializing in market sentiment analysis for algorithmic trading systems.

Analyze the following market-related content and provide a JSON response with:

1. **sentiment_score**: Float between -1.0 (extremely bearish) and 1.0 (extremely bullish)
2. **confidence**: Float between 0.0 and 1.0 (how confident you are in the assessment)
3. **key_topics**: Array of 3-5 key themes identified
4. **market_impact**: One of: "immediate", "short-term", "long-term", "negligible"
5. **reasoning**: Brief explanation of your analysis

**Context:**
- Target: {ticker}
- Market Conditions: {market_conditions}
- Sector: {sector}
- Volatility Regime: {volatility}
- Analysis Time: {context.get('timestamp', datetime.now())}

**Content to analyze:**
{text}

**Instructions:**
- Consider the source credibility and timing
- Weight recent news higher than historical references
- Factor in market regime when assessing impact
- Be conservative with extreme scores (-1.0 or 1.0)
- Focus on actionable trading implications

Respond ONLY with valid JSON in this format:
{{
    "sentiment_score": 0.0,
    "confidence": 0.0,
    "key_topics": ["topic1", "topic2"],
    "market_impact": "short-term",
    "reasoning": "Your analysis reasoning here"
}}
"""
        
        return base_prompt.strip()
    
    async def _call_llm_async(self, prompt: str) -> str:
        """Make async call to LLM with error handling"""
        try:
            response = await asyncio.to_thread(
                self.client.chat.completions.create,
                model=self.model_name,
                messages=[{"role": "user", "content": prompt}],
                temperature=0.1,
                max_tokens=500,
                timeout=30
            )
            return response.choices[0].message.content
            
        except Exception as e:
            self.logger.error(f"LLM API call failed: {e}")
            raise
    
    def _parse_llm_response(self, response: str, original_text: str, context: Dict[str, Any]) -> SentimentSignal:
        """Parse LLM response into structured signal"""
        try:
            # Extract JSON from response
            json_match = re.search(r'\{.*\}', response, re.DOTALL)
            if not json_match:
                raise ValueError("No JSON found in LLM response")
            
            parsed = json.loads(json_match.group())
            
            # Validate and clamp values
            sentiment_score = max(-1.0, min(1.0, float(parsed.get('sentiment_score', 0.0))))
            confidence = max(0.0, min(1.0, float(parsed.get('confidence', 0.5))))
            
            return SentimentSignal(
                timestamp=datetime.now(),
                ticker=context.get('ticker', 'MARKET'),
                source=context.get('source', 'unknown'),
                content=original_text[:500],  # Truncate for storage
                sentiment_score=sentiment_score,
                confidence=confidence,
                key_topics=parsed.get('key_topics', []),
                market_impact_prediction=parsed.get('market_impact', 'negligible'),
                reasoning=parsed.get('reasoning', ''),
                raw_content=original_text,
                content_hash=''  # Will be set by caller
            )
            
        except Exception as e:
            self.logger.error(f"Failed to parse LLM response: {e}")
            return self._create_fallback_signal(original_text, context)
    
    def _create_fallback_signal(self, text: str, context: Dict[str, Any]) -> SentimentSignal:
        """Create fallback signal using traditional methods"""
        try:
            # Use TextBlob as fallback
            blob = TextBlob(text)
            sentiment_score = blob.sentiment.polarity
            confidence = 0.3  # Lower confidence for fallback
            
            return SentimentSignal(
                timestamp=datetime.now(),
                ticker=context.get('ticker', 'MARKET'),
                source=context.get('source', 'unknown'),
                content=text[:500],
                sentiment_score=sentiment_score,
                confidence=confidence,
                key_topics=['fallback_analysis'],
                market_impact_prediction='negligible',
                reasoning='Fallback analysis using traditional sentiment',
                raw_content=text,
                content_hash=''
            )
            
        except Exception as e:
            self.logger.error(f"Fallback analysis failed: {e}")
            # Return neutral signal as last resort
            return SentimentSignal(
                timestamp=datetime.now(),
                ticker=context.get('ticker', 'MARKET'),
                source=context.get('source', 'unknown'),
                content=text[:500],
                sentiment_score=0.0,
                confidence=0.1,
                key_topics=['error'],
                market_impact_prediction='negligible',
                reasoning='Analysis failed, returning neutral',
                raw_content=text,
                content_hash=''
            )

class NewsDataFetcher:
    """
    Fetches financial news and market data for sentiment analysis
    """
    
    def __init__(self):
        self.logger = logging.getLogger(__name__)
        self.news_sources = {
            'alpha_vantage': {
                'url': 'https://www.alphavantage.co/query',
                'api_key': os.getenv('ALPHA_VANTAGE_API_KEY'),
                'rate_limit': 5  # calls per minute
            },
            'finnhub': {
                'url': 'https://finnhub.io/api/v1',
                'api_key': os.getenv('FINNHUB_API_KEY'),
                'rate_limit': 60
            }
        }
    
    async def fetch_ticker_news(self, ticker: str, hours_back: int = 24) -> List[Dict[str, Any]]:
        """Fetch recent news for a specific ticker"""
        try:
            # Use yfinance for news (free and reliable)
            stock = yf.Ticker(ticker)
            news_data = stock.news
            
            # Filter recent news
            cutoff_time = datetime.now() - timedelta(hours=hours_back)
            
            filtered_news = []
            for item in news_data:
                # Convert timestamp
                news_time = datetime.fromtimestamp(item.get('providerPublishTime', 0))
                
                if news_time > cutoff_time:
                    filtered_news.append({
                        'ticker': ticker,
                        'title': item.get('title', ''),
                        'summary': item.get('summary', ''),
                        'url': item.get('link', ''),
                        'source': item.get('publisher', ''),
                        'timestamp': news_time,
                        'content': f"{item.get('title', '')} {item.get('summary', '')}"
                    })
            
            self.logger.info(f"Fetched {len(filtered_news)} recent news items for {ticker}")
            return filtered_news
            
        except Exception as e:
            self.logger.error(f"Error fetching news for {ticker}: {e}")
            return []
    
    async def fetch_market_news(self, limit: int = 50) -> List[Dict[str, Any]]:
        """Fetch general market news"""
        try:
            # Get market-wide news from general financial sources
            market_tickers = ['SPY', 'QQQ', 'DJI', 'VIX']  # Major market indicators
            
            all_news = []
            for ticker in market_tickers:
                ticker_news = await self.fetch_ticker_news(ticker, hours_back=12)
                all_news.extend(ticker_news)
            
            # Sort by timestamp and limit
            all_news.sort(key=lambda x: x.get('timestamp', datetime.min), reverse=True)
            
            self.logger.info(f"Fetched {len(all_news[:limit])} market news items")
            return all_news[:limit]
            
        except Exception as e:
            self.logger.error(f"Error fetching market news: {e}")
            return []

class SentimentBacktester:
    """
    Backtesting framework for LLM sentiment signals
    """
    
    def __init__(self, price_data: pd.DataFrame, sentiment_data: pd.DataFrame):
        self.price_data = price_data
        self.sentiment_data = sentiment_data
        self.logger = logging.getLogger(__name__)
    
    def run_correlation_analysis(self, lookforward_periods: List[int] = [1, 5, 10, 20]) -> Dict[str, float]:
        """Analyze correlation between sentiment and future returns"""
        try:
            correlations = {}
            
            for period in lookforward_periods:
                # Calculate forward returns
                forward_returns = self.price_data['close'].pct_change(period).shift(-period)
                
                # Merge sentiment data with price data
                merged_data = pd.merge_asof(
                    self.sentiment_data.sort_values('timestamp'),
                    pd.DataFrame({
                        'timestamp': self.price_data.index,
                        'forward_return': forward_returns
                    }).sort_values('timestamp'),
                    on='timestamp'
                )
                
                # Calculate correlation
                correlation = merged_data['sentiment_score'].corr(merged_data['forward_return'])
                correlations[f'{period}_day'] = correlation
                
                self.logger.info(f"{period}-day correlation: {correlation:.3f}")
            
            return correlations
            
        except Exception as e:
            self.logger.error(f"Correlation analysis failed: {e}")
            return {}
    
    def generate_performance_report(self) -> Dict[str, Any]:
        """Generate comprehensive performance report"""
        try:
            report = {
                'analysis_date': datetime.now().isoformat(),
                'total_signals': len(self.sentiment_data),
                'sentiment_distribution': {
                    'bullish': len(self.sentiment_data[self.sentiment_data['sentiment_score'] > 0.1]),
                    'bearish': len(self.sentiment_data[self.sentiment_data['sentiment_score'] < -0.1]),
                    'neutral': len(self.sentiment_data[abs(self.sentiment_data['sentiment_score']) <= 0.1])
                },
                'confidence_stats': {
                    'mean': self.sentiment_data['confidence'].mean(),
                    'std': self.sentiment_data['confidence'].std(),
                    'high_confidence_count': len(self.sentiment_data[self.sentiment_data['confidence'] > 0.7])
                },
                'correlations': self.run_correlation_analysis()
            }
            
            return report
            
        except Exception as e:
            self.logger.error(f"Performance report generation failed: {e}")
            return {'error': str(e)}

class SentimentComparison:
    """
    Compare LLM sentiment analysis with traditional methods
    """
    
    def __init__(self):
        self.llm_analyzer = MarketSentimentAnalyzer()
        self.logger = logging.getLogger(__name__)
    
    async def comparative_analysis(self, texts: List[str], context: Dict[str, Any] = None) -> pd.DataFrame:
        """Compare LLM vs traditional sentiment analysis"""
        try:
            # Get traditional scores using TextBlob
            traditional_scores = []
            for text in texts:
                blob = TextBlob(text)
                traditional_scores.append(blob.sentiment.polarity)
            
            # Get LLM scores
            llm_signals = await self.llm_analyzer.analyze_batch(texts, context)
            llm_scores = [signal.sentiment_score for signal in llm_signals]
            
            # Create comparison DataFrame
            comparison_df = pd.DataFrame({
                'text': texts,
                'traditional_sentiment': traditional_scores,
                'llm_sentiment': llm_scores,
                'llm_confidence': [signal.confidence for signal in llm_signals],
                'difference': [llm - trad for llm, trad in zip(llm_scores, traditional_scores)]
            })
            
            return comparison_df
            
        except Exception as e:
            self.logger.error(f"Comparative analysis failed: {e}")
            return pd.DataFrame()

# Example usage and testing
async def main():
    """Example usage of the sentiment analysis system"""
    
    # Initialize components
    analyzer = MarketSentimentAnalyzer()
    news_fetcher = NewsDataFetcher()
    
    # Test with sample financial news
    sample_texts = [
        "Apple reports record quarterly earnings, beating analyst expectations by 15%",
        "Federal Reserve hints at potential interest rate cuts in coming months",
        "Tesla stock plummets after disappointing delivery numbers",
        "Market volatility expected to continue amid geopolitical tensions"
    ]
    
    # Analyze sentiment
    context = {
        'ticker': 'AAPL',
        'market_conditions': 'neutral',
        'sector': 'Technology',
        'volatility_regime': 'normal'
    }
    
    signals = await analyzer.analyze_batch(sample_texts, context)
    
    # Print results
    for signal in signals:
        print(f"Text: {signal.content[:100]}...")
        print(f"Sentiment: {signal.sentiment_score:.3f} (confidence: {signal.confidence:.3f})")
        print(f"Impact: {signal.market_impact_prediction}")
        print(f"Topics: {signal.key_topics}")
        print("-" * 50)

if __name__ == "__main__":
    asyncio.run(main())