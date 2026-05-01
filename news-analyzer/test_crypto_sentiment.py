"""
Test and demonstration script for the Crypto Sentiment Pipeline.
Shows how to use all features of the pipeline.
"""

import json
import sys
import time
from crypto_sentiment_pipeline import CryptoSentimentPipeline
import logging

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


def test_basic_operations():
    """Test basic pipeline operations."""
    logger.info("=" * 60)
    logger.info("TEST 1: Basic Pipeline Operations")
    logger.info("=" * 60)

    try:
        pipeline = CryptoSentimentPipeline()

        # Test 1: Process a single coin
        logger.info("\n1.1: Processing Bitcoin...")
        result = pipeline.process_coin("Bitcoin", "bitcoin")

        if result["success"]:
            logger.info("✓ Bitcoin processed successfully")
            logger.info(f"  - Price: ${result['data']['price_usd']}")
            logger.info(f"  - Sentiment: {result['data']['sentiment_label']}")
            logger.info(f"  - Score: {result['data']['sentiment_score']}")
        else:
            logger.error(f"✗ Failed to process Bitcoin: {result['error']}")

        time.sleep(2)  # Rate limiting

        # Test 2: Retrieve the data
        logger.info("\n1.2: Retrieving Bitcoin analysis from Redis...")
        retrieved = pipeline.get_coin_analysis("bitcoin")

        if retrieved:
            logger.info("✓ Data retrieved successfully")
            logger.info(f"  - Main data keys: {list(retrieved['data'].keys())}")
            logger.info(f"  - Headlines found: {len(retrieved['headlines'])}")
        else:
            logger.error("✗ Failed to retrieve Bitcoin analysis")

        return True

    except Exception as e:
        logger.error(f"Test failed: {e}")
        return False


def test_multiple_coins():
    """Test processing multiple cryptocurrencies."""
    logger.info("\n" + "=" * 60)
    logger.info("TEST 2: Multiple Cryptocurrency Processing")
    logger.info("=" * 60)

    try:
        pipeline = CryptoSentimentPipeline()

        coins = [
            ("Bitcoin", "bitcoin"),
            ("Ethereum", "ethereum"),
            ("Solana", "solana"),
            ("Cardano", "cardano"),
        ]

        results = []
        for name, coin_id in coins:
            logger.info(f"\nProcessing {name}...")
            result = pipeline.process_coin(name, coin_id)

            if result["success"]:
                results.append({
                    "name": name,
                    "coin_id": coin_id,
                    "sentiment": result["data"]["sentiment_label"],
                    "score": result["data"]["sentiment_score"],
                    "price": result["data"]["price_usd"]
                })
                logger.info(f"✓ {name}: {result['data']['sentiment_label']}")
            else:
                logger.error(f"✗ {name}: {result['error']}")

            time.sleep(2)  # Rate limiting

        # Display summary
        logger.info("\n--- Analysis Summary ---")
        for r in results:
            logger.info(f"{r['name']:12} | Sentiment: {r['sentiment']:8} | Score: {r['score']:7} | Price: ${r['price']}")

        return len(results) > 0

    except Exception as e:
        logger.error(f"Test failed: {e}")
        return False


def test_data_retrieval():
    """Test retrieving all stored data."""
    logger.info("\n" + "=" * 60)
    logger.info("TEST 3: Data Retrieval Operations")
    logger.info("=" * 60)

    try:
        pipeline = CryptoSentimentPipeline()

        # Test 1: Get all coins analysis
        logger.info("\n3.1: Retrieving all coins analysis...")
        all_data = pipeline.get_all_coins_analysis()

        logger.info(f"✓ Retrieved analysis for {len(all_data)} coins")

        if all_data:
            for coin_id, analysis in all_data.items():
                data = analysis["data"]
                logger.info(f"\n  {data.get('name', coin_id)}:")
                logger.info(f"    - Price: ${data['price_usd']}")
                logger.info(f"    - Sentiment: {data['sentiment_label']}")
                logger.info(f"    - Articles: {data['num_articles_analyzed']}")

        # Test 2: Display headlines for a coin
        logger.info("\n3.2: Displaying headlines for Bitcoin...")
        bitcoin_data = pipeline.get_coin_analysis("bitcoin")

        if bitcoin_data and bitcoin_data["headlines"]:
            logger.info(f"✓ Found {len(bitcoin_data['headlines'])} headlines")
            for i, headline in enumerate(bitcoin_data["headlines"][:3], 1):
                logger.info(f"\n  {i}. {headline['headline']}")
                logger.info(f"     Source: {headline['source']}")
                logger.info(f"     Sentiment: {headline['sentiment_label']}")
        else:
            logger.warning("✗ No headlines found")

        return True

    except Exception as e:
        logger.error(f"Test failed: {e}")
        return False


def test_sentiment_analysis_only():
    """Test sentiment analysis without full pipeline."""
    logger.info("\n" + "=" * 60)
    logger.info("TEST 4: Direct Sentiment Analysis")
    logger.info("=" * 60)

    try:
        pipeline = CryptoSentimentPipeline()

        test_headlines = [
            "Bitcoin Surges to New All-Time High",
            "Crypto Market Crashes Due to Regulatory Fears",
            "Ethereum Shows Moderate Growth",
            "XRP Faces Regulatory Challenges",
            "Solana Network Experiences Record Activity"
        ]

        logger.info("\nAnalyzing sample headlines...")
        logger.info("-" * 60)

        for headline in test_headlines:
            scores = pipeline.analyzer.polarity_scores(headline)
            compound = scores['compound']

            if compound >= 0.05:
                sentiment = "Bullish"
            elif compound <= -0.05:
                sentiment = "Bearish"
            else:
                sentiment = "Neutral"

            logger.info(f"\nHeadline: {headline}")
            logger.info(f"  Sentiment: {sentiment} (score: {compound:.4f})")
            logger.info(f"  VADER Scores: Positive={scores['pos']:.3f}, Negative={scores['neg']:.3f}, Neutral={scores['neu']:.3f}")

        return True

    except Exception as e:
        logger.error(f"Test failed: {e}")
        return False


def test_redis_operations():
    """Test direct Redis operations."""
    logger.info("\n" + "=" * 60)
    logger.info("TEST 5: Redis Storage and Retrieval")
    logger.info("=" * 60)

    try:
        pipeline = CryptoSentimentPipeline()

        # Process a coin first
        logger.info("\n5.1: Processing coin for Redis test...")
        result = pipeline.process_coin("Ripple", "ripple")

        if not result["success"]:
            raise Exception(f"Failed to process coin: {result['error']}")

        # Direct Redis retrieval
        logger.info("\n5.2: Retrieving directly from Redis...")

        # Get as hash
        hash_data = pipeline.r.hgetall("crypto_sentiment:ripple")
        logger.info(f"✓ Hash data keys: {list(hash_data.keys())}")

        # Get headlines as JSON
        headlines_json = pipeline.r.get("crypto_sentiment:ripple:headlines")
        if headlines_json:
            headlines = json.loads(headlines_json)
            logger.info(f"✓ Headlines retrieved: {len(headlines)} items")

        # Check if in recent set
        recent_coins = pipeline.r.zrange("crypto_sentiment:recent", 0, -1)
        logger.info(f"✓ Recent coins in Redis: {recent_coins}")

        # Test TTL
        logger.info("\n5.3: Checking key details...")
        ttl = pipeline.r.ttl("crypto_sentiment:ripple")
        logger.info(f"✓ TTL for crypto_sentiment:ripple: {ttl} seconds" if ttl >= 0 else "✓ No expiry set")

        # Count total keys
        all_keys = pipeline.r.keys("crypto_sentiment:*")
        logger.info(f"✓ Total crypto_sentiment keys in Redis: {len(all_keys)}")

        return True

    except Exception as e:
        logger.error(f"Test failed: {e}")
        return False


def cleanup_test_data():
    """Clean up test data from Redis."""
    logger.info("\n" + "=" * 60)
    logger.info("CLEANUP: Removing Test Data from Redis")
    logger.info("=" * 60)

    try:
        pipeline = CryptoSentimentPipeline()

        test_coins = ["bitcoin", "ethereum", "solana", "cardano", "ripple"]

        for coin_id in test_coins:
            if pipeline.clear_coin_data(coin_id):
                logger.info(f"✓ Cleared {coin_id}")
            else:
                logger.warning(f"- Could not clear {coin_id}")

        logger.info("\n✓ Cleanup complete")
        return True

    except Exception as e:
        logger.error(f"Cleanup failed: {e}")
        return False


def main():
    """Run all tests."""
    logger.info("\n" + "=" * 60)
    logger.info("CRYPTO SENTIMENT PIPELINE - COMPREHENSIVE TEST SUITE")
    logger.info("=" * 60)

    tests = [
        ("Basic Operations", test_basic_operations),
        ("Multiple Coins", test_multiple_coins),
        ("Data Retrieval", test_data_retrieval),
        ("Sentiment Analysis", test_sentiment_analysis_only),
        ("Redis Operations", test_redis_operations),
    ]

    results = []

    for test_name, test_func in tests:
        try:
            success = test_func()
            results.append((test_name, success))
        except Exception as e:
            logger.error(f"Test '{test_name}' crashed: {e}")
            results.append((test_name, False))

        logger.info("\n")

    # Summary
    logger.info("=" * 60)
    logger.info("TEST SUMMARY")
    logger.info("=" * 60)

    passed = sum(1 for _, success in results if success)
    total = len(results)

    for test_name, success in results:
        status = "✓ PASSED" if success else "✗ FAILED"
        logger.info(f"{test_name:30} {status}")

    logger.info(f"\nTotal: {passed}/{total} tests passed")
    logger.info("=" * 60)

    # Offer cleanup
    if passed > 0:
        logger.info("\nTest data has been stored in Redis.")
        logger.info("Run 'python test_crypto_sentiment.py cleanup' to remove test data.")

    return passed == total


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "cleanup":
        cleanup_test_data()
    else:
        success = main()
        sys.exit(0 if success else 1)

