#!/usr/bin/env python3
"""
Simple test script for the crypto news analyzer
"""
import asyncio
import sys
import os

# Add the project root to Python path
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from main import CryptoNewsAnalyzer

async def test_basic_functionality():
    """Test basic functionality of the analyzer."""
    print("Testing Crypto News Analyzer...")

    analyzer = CryptoNewsAnalyzer()

    # Test Redis connection
    print("Testing Redis connection...")
    try:
        symbols = analyzer.redis_client.get_usdt_symbols()
        print(f"Found {len(symbols)} USDT symbols in Redis")
    except Exception as e:
        print(f"Redis connection failed: {e}")
        symbols = []

    if not symbols:
        print("WARNING: No symbols found in Redis. Testing components with mock data...")

    # Test sentiment analyzer
    print("Testing sentiment analysis...")
    test_text = "Bitcoin is showing strong growth and adoption in the market."
    sentiment = analyzer.sentiment_analyzer.analyze_sentiment(test_text)
    print(f"Sentiment analysis result: {sentiment}")

    # Test article parsing
    print("Testing article parsing...")
    test_url = "https://coindesk.com/learn/bitcoin-101/what-is-bitcoin/"
    try:
        parsed = analyzer.parser.parse_article(test_url)
        print(f"Parsed article: {parsed['title'][:50]}...")
        print(f"Coins mentioned: {parsed['coins_mentioned']}")
    except Exception as e:
        print(f"Article parsing failed: {e}")

    # Test decision engine
    print("Testing decision engine...")
    mock_articles = [
        {
            'coins_mentioned': ['BTC'],
            'sentiment_analysis': {'combined_score': 0.8},
            'source': 'coindesk',
            'keyword_signal': 0.3
        },
        {
            'coins_mentioned': ['BTC'],
            'sentiment_analysis': {'combined_score': 0.6},
            'source': 'cointelegraph',
            'keyword_signal': 0.2
        }
    ]

    analysis = analyzer.decision_engine.analyze_coin('BTC', mock_articles)
    print(f"Decision for BTC: {analysis}")

    print("Basic functionality test completed!")

if __name__ == "__main__":
    asyncio.run(test_basic_functionality())
