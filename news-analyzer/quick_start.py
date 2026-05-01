#!/usr/bin/env python3
"""
Quick Start Script for Crypto Sentiment Pipeline
Simple example to get started in minutes
"""

import sys
from crypto_sentiment_pipeline import CryptoSentimentPipeline
import logging
import time

logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


def quick_start():
    """Quick start example."""
    print("\n" + "=" * 60)
    print("CRYPTO SENTIMENT PIPELINE - QUICK START")
    print("=" * 60 + "\n")

    try:
        # Step 1: Initialize
        print("Step 1: Initializing pipeline...")
        pipeline = CryptoSentimentPipeline()
        print("✓ Pipeline ready!\n")

        # Step 2: Process Bitcoin
        print("Step 2: Analyzing Bitcoin...")
        result = pipeline.process_coin("Bitcoin", "bitcoin")

        if result["success"]:
            data = result["data"]
            print(f"✓ Analysis complete!\n")
            print(f"  Coin: {data['name']}")
            print(f"  Price: ${data['price_usd']}")
            print(f"  Sentiment: {data['sentiment_label']} ({data['sentiment_score']})")
            print(f"  Articles analyzed: {data['num_articles_analyzed']}\n")
        else:
            print(f"✗ Failed: {result['error']}\n")
            return False

        time.sleep(2)

        # Step 3: Process Ethereum
        print("Step 3: Analyzing Ethereum...")
        result = pipeline.process_coin("Ethereum", "ethereum")

        if result["success"]:
            data = result["data"]
            print(f"✓ Analysis complete!")
            print(f"  Coin: {data['name']}")
            print(f"  Price: ${data['price_usd']}")
            print(f"  Sentiment: {data['sentiment_label']} ({data['sentiment_score']})")
            print(f"  Articles analyzed: {data['num_articles_analyzed']}\n")
        else:
            print(f"✗ Failed: {result['error']}\n")
            return False

        # Step 4: Retrieve data
        print("Step 4: Retrieving stored data...")

        bitcoin_data = pipeline.get_coin_analysis("bitcoin")
        if bitcoin_data:
            print("✓ Bitcoin data retrieved from Redis!")
            print(f"  Price: ${bitcoin_data['data']['price_usd']}")
            print(f"  Sentiment: {bitcoin_data['data']['sentiment_label']}")

            if bitcoin_data["headlines"]:
                print(f"\n  Top headlines:")
                for i, headline in enumerate(bitcoin_data["headlines"][:3], 1):
                    print(f"    {i}. {headline['headline'][:60]}...")
                    print(f"       → {headline['sentiment_label']} ({headline['sentiment_score']})")

        print("\n" + "=" * 60)
        print("✓ QUICK START COMPLETE!")
        print("=" * 60)
        print("\nNext steps:")
        print("1. Run full test suite: python test_crypto_sentiment.py")
        print("2. Read guide: CRYPTO_SENTIMENT_GUIDE.md")
        print("3. Check data in Redis: redis-cli")
        print("   Example: redis-cli HGETALL crypto_sentiment:bitcoin\n")

        return True

    except Exception as e:
        logger.error(f"Quick start failed: {e}")
        print(f"\n✗ Error: {e}")
        print("\nTroubleshooting:")
        print("1. Ensure Redis is running: redis-cli ping")
        print("2. Check internet connection")
        print("3. Verify dependencies: pip install -r requirements.txt")
        print()
        return False


def check_redis():
    """Check Redis connectivity."""
    print("\n" + "=" * 60)
    print("REDIS CONNECTION CHECK")
    print("=" * 60 + "\n")

    try:
        pipeline = CryptoSentimentPipeline()
        info = pipeline.r.info()

        print("✓ Redis connected!")
        print(f"  Version: {info['redis_version']}")
        print(f"  Uptime: {info['uptime_in_seconds']} seconds")
        print(f"  Connected clients: {info['connected_clients']}")
        print(f"  Used memory: {info['used_memory_human']}")
        print(f"  Total keys: {pipeline.r.dbsize()}")

        # Check for existing crypto sentiment data
        keys = pipeline.r.keys("crypto_sentiment:*")
        if keys:
            print(f"  Crypto sentiment records: {len([k for k in keys if ':' not in k[18:]])}")

        print("\n✓ Redis is healthy!\n")
        return True

    except Exception as e:
        print(f"✗ Redis connection failed: {e}")
        print("\nSolutions:")
        print("1. Start Redis:")
        print("   - macOS: brew services start redis")
        print("   - Linux: sudo systemctl start redis-server")
        print("   - Docker: docker run -d -p 6379:6379 redis")
        print("2. Test connection: redis-cli ping")
        print()
        return False


def main():
    """Main entry point."""
    if len(sys.argv) > 1:
        command = sys.argv[1].lower()

        if command == "check":
            return check_redis()
        elif command == "start":
            return quick_start()
        elif command == "help":
            print("""
Quick Start Script - Usage:
  python quick_start.py              # Run quick start
  python quick_start.py start        # Run quick start (explicit)
  python quick_start.py check        # Check Redis connection
  python quick_start.py help         # Show this help
            """)
            return True
        else:
            print(f"Unknown command: {command}")
            print("Use 'python quick_start.py help' for usage info")
            return False
    else:
        return quick_start()


if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)

