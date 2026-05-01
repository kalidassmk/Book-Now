"""
Integration example: Using Crypto Sentiment Pipeline with existing News Analyzer
Shows how to integrate the new sentiment pipeline into the existing system
"""

import asyncio
import logging
from typing import List, Dict, Any
from crypto_sentiment_pipeline import CryptoSentimentPipeline
from redis_client import RedisClient
from sentiment.sentiment_analyzer import SentimentAnalyzer

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class IntegratedCryptoAnalyzer:
    """
    Combines the existing CryptoNewsAnalyzer with the new sentiment pipeline.

    This class demonstrates how to use both systems together:
    - Existing system: Complex analysis with multiple sources
    - New pipeline: Quick sentiment snapshot via VADER
    """

    def __init__(self):
        self.sentiment_pipeline = CryptoSentimentPipeline()
        self.redis_client = RedisClient()
        self.existing_analyzer = SentimentAnalyzer()

    def get_quick_sentiment(self, coin_name: str, coin_id: str) -> Dict:
        """
        Get quick sentiment using the new VADER-based pipeline.

        This is faster than the full analysis and useful for:
        - Quick market pulse checks
        - Real-time decision support
        - Portfolio sentiment overview

        Args:
            coin_name: Display name (e.g., 'Bitcoin')
            coin_id: CoinGecko ID (e.g., 'bitcoin')

        Returns:
            Dictionary with sentiment and price data
        """
        logger.info(f"Getting quick sentiment for {coin_name}...")
        result = self.sentiment_pipeline.process_coin(coin_name, coin_id)

        if result["success"]:
            data = result["data"]
            return {
                "coin": coin_name,
                "quick_sentiment": data["sentiment_label"],
                "sentiment_score": data["sentiment_score"],
                "price": data["price_usd"],
                "timestamp": data["timestamp"],
                "source": "VADER Pipeline"
            }
        else:
            return {"error": result["error"]}

    def compare_sentiment_methods(self, text: str) -> Dict:
        """
        Compare sentiment analysis between:
        1. Existing TextBlob analyzer
        2. New VADER analyzer

        Useful for understanding differences in approaches.
        """
        # Existing TextBlob method
        existing_sentiment = self.existing_analyzer.analyze_sentiment(text)

        # New VADER method
        vader_scores = self.sentiment_pipeline.analyzer.polarity_scores(text)

        return {
            "text": text[:100] + "..." if len(text) > 100 else text,
            "textblob": {
                "polarity": existing_sentiment["polarity"],
                "subjectivity": existing_sentiment["subjectivity"],
                "combined_score": existing_sentiment["combined_score"]
            },
            "vader": {
                "compound": vader_scores["compound"],
                "positive": vader_scores["pos"],
                "negative": vader_scores["neg"],
                "neutral": vader_scores["neu"]
            }
        }

    def store_pipeline_sentiment_to_redis(self, coin_name: str, coin_id: str) -> bool:
        """
        Store sentiment pipeline results in Redis using existing patterns.

        This shows how to integrate with the existing redis_client patterns.
        """
        try:
            result = self.sentiment_pipeline.process_coin(coin_name, coin_id)

            if result["success"]:
                data = result["data"]

                # Create analysis in the format expected by existing system
                analysis = {
                    "coin": coin_name,
                    "sentiment_score": float(data["sentiment_score"]),
                    "sentiment_label": data["sentiment_label"],
                    "price": data["price_usd"],
                    "articles_count": int(data["num_articles_analyzed"]),
                    "source": "VADER Sentiment Pipeline",
                    "timestamp": data["timestamp"]
                }

                # Store using existing redis_client pattern
                key = f"pipeline_sentiment:{coin_id}"
                self.redis_client.client.set(key, str(analysis))
                logger.info(f"Stored pipeline sentiment for {coin_name}")

                return True
            else:
                logger.error(f"Sentiment pipeline failed: {result['error']}")
                return False

        except Exception as e:
            logger.error(f"Failed to store sentiment: {e}")
            return False

    def batch_analyze_portfolio(self, coins: List[Dict[str, str]]) -> List[Dict]:
        """
        Analyze a portfolio of cryptocurrencies using the new pipeline.

        Args:
            coins: List of dicts with 'name' and 'id' keys

        Returns:
            List of sentiment results for each coin
        """
        import time

        results = []
        logger.info(f"Analyzing portfolio of {len(coins)} coins...")

        for coin in coins:
            result = self.sentiment_pipeline.process_coin(
                coin["name"],
                coin["id"]
            )

            if result["success"]:
                results.append({
                    "coin": coin["name"],
                    "sentiment": result["data"]["sentiment_label"],
                    "score": result["data"]["sentiment_score"],
                    "price": result["data"]["price_usd"]
                })

            time.sleep(2)  # Rate limiting

        logger.info(f"Portfolio analysis complete: {len(results)} results")
        return results

    def get_sentiment_distribution(self) -> Dict[str, int]:
        """
        Get distribution of sentiment across all tracked coins.

        Returns:
            Dictionary with sentiment counts
        """
        all_data = self.sentiment_pipeline.get_all_coins_analysis()

        distribution = {
            "bullish": 0,
            "neutral": 0,
            "bearish": 0
        }

        for coin_id, analysis in all_data.items():
            sentiment = analysis["data"]["sentiment_label"].lower()
            if sentiment in distribution:
                distribution[sentiment] += 1

        return distribution

    def get_market_pulse(self) -> Dict:
        """
        Get overall market pulse - average sentiment across portfolio.

        Returns:
            Dictionary with market-wide sentiment metrics
        """
        all_data = self.sentiment_pipeline.get_all_coins_analysis()

        if not all_data:
            return {"error": "No data available"}

        scores = [
            float(analysis["data"]["sentiment_score"])
            for analysis in all_data.values()
        ]

        avg_score = sum(scores) / len(scores) if scores else 0.0

        # Determine overall market sentiment
        if avg_score >= 0.05:
            overall = "Bullish"
        elif avg_score <= -0.05:
            overall = "Bearish"
        else:
            overall = "Neutral"

        return {
            "overall_sentiment": overall,
            "average_score": round(avg_score, 4),
            "coins_tracked": len(all_data),
            "distribution": {
                "bullish": len([s for s in scores if s >= 0.05]),
                "neutral": len([s for s in scores if -0.05 < s < 0.05]),
                "bearish": len([s for s in scores if s <= -0.05])
            }
        }


# --- Usage Examples ---

async def example_1_quick_sentiment():
    """Example 1: Get quick sentiment for a coin."""
    print("\n" + "=" * 60)
    print("EXAMPLE 1: Quick Sentiment Check")
    print("=" * 60)

    analyzer = IntegratedCryptoAnalyzer()

    sentiment = analyzer.get_quick_sentiment("Bitcoin", "bitcoin")
    print(f"\nBitcoin Sentiment:")
    print(f"  Sentiment: {sentiment['quick_sentiment']}")
    print(f"  Score: {sentiment['sentiment_score']}")
    print(f"  Price: ${sentiment['price']}")


async def example_2_compare_methods():
    """Example 2: Compare sentiment analysis methods."""
    print("\n" + "=" * 60)
    print("EXAMPLE 2: Compare Sentiment Methods")
    print("=" * 60)

    analyzer = IntegratedCryptoAnalyzer()

    sample_text = "Bitcoin surges to new all-time high as institutional adoption grows"

    comparison = analyzer.compare_sentiment_methods(sample_text)

    print(f"\nText: {comparison['text']}")
    print(f"\nTextBlob:")
    print(f"  Polarity: {comparison['textblob']['polarity']}")
    print(f"  Subjectivity: {comparison['textblob']['subjectivity']}")
    print(f"\nVADER:")
    print(f"  Compound: {comparison['vader']['compound']}")
    print(f"  Positive: {comparison['vader']['positive']}")
    print(f"  Negative: {comparison['vader']['negative']}")


async def example_3_portfolio_analysis():
    """Example 3: Analyze a cryptocurrency portfolio."""
    print("\n" + "=" * 60)
    print("EXAMPLE 3: Portfolio Analysis")
    print("=" * 60)

    analyzer = IntegratedCryptoAnalyzer()

    portfolio = [
        {"name": "Bitcoin", "id": "bitcoin"},
        {"name": "Ethereum", "id": "ethereum"},
        {"name": "Solana", "id": "solana"},
    ]

    results = analyzer.batch_analyze_portfolio(portfolio)

    print("\nPortfolio Analysis:")
    print("-" * 60)
    for result in results:
        print(f"\n{result['coin']}:")
        print(f"  Sentiment: {result['sentiment']}")
        print(f"  Score: {result['score']}")
        print(f"  Price: ${result['price']}")


async def example_4_market_pulse():
    """Example 4: Get overall market pulse."""
    print("\n" + "=" * 60)
    print("EXAMPLE 4: Market Pulse")
    print("=" * 60)

    analyzer = IntegratedCryptoAnalyzer()

    pulse = analyzer.get_market_pulse()

    if "error" not in pulse:
        print("\nMarket Pulse:")
        print(f"  Overall Sentiment: {pulse['overall_sentiment']}")
        print(f"  Average Score: {pulse['average_score']}")
        print(f"  Coins Tracked: {pulse['coins_tracked']}")
        print(f"\n  Distribution:")
        print(f"    Bullish: {pulse['distribution']['bullish']}")
        print(f"    Neutral: {pulse['distribution']['neutral']}")
        print(f"    Bearish: {pulse['distribution']['bearish']}")
    else:
        print(f"\nNo data available: {pulse['error']}")


async def main():
    """Run all examples."""
    try:
        await example_1_quick_sentiment()
        await example_2_compare_methods()
        await example_3_portfolio_analysis()
        await example_4_market_pulse()

        print("\n" + "=" * 60)
        print("✓ All examples completed!")
        print("=" * 60 + "\n")

    except Exception as e:
        logger.error(f"Example failed: {e}")


if __name__ == "__main__":
    asyncio.run(main())

