"""
Complete Cryptocurrency Sentiment Analysis Pipeline
Combines three layers: Market Data (via CoinGecko), News Scraping, and Sentiment Analysis (via VADER)
"""

import requests
import redis
import json
import logging
from datetime import datetime
from typing import Dict, Tuple, Optional, List
from bs4 import BeautifulSoup
from vaderSentiment.vaderSentiment import SentimentIntensityAnalyzer
import time

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)


class CryptoSentimentPipeline:
    """
    A complete pipeline for analyzing cryptocurrency sentiment.

    Combines:
    - CoinGecko API for market data (price)
    - News scraping for articles
    - VADER sentiment analysis
    - Redis storage for results
    """

    def __init__(self, redis_host='localhost', redis_port=6379, redis_db=0):
        """
        Initialize the pipeline.

        Args:
            redis_host: Redis server hostname
            redis_port: Redis server port
            redis_db: Redis database number
        """
        try:
            self.r = redis.Redis(
                host=redis_host,
                port=redis_port,
                db=redis_db,
                decode_responses=True,
                socket_connect_timeout=5
            )
            # Test connection
            self.r.ping()
            logger.info(f"Connected to Redis at {redis_host}:{redis_port}")
        except redis.ConnectionError as e:
            logger.error(f"Failed to connect to Redis: {e}")
            raise

        # Initialize VADER sentiment analyzer
        self.analyzer = SentimentIntensityAnalyzer()

        # Set user agent to avoid blocking
        self.headers = {
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36'
        }

        # CoinGecko API settings
        self.coingecko_base_url = "https://api.coingecko.com/api/v3"

    def fetch_price(self, coin_id: str) -> Dict[str, any]:
        """
        Gets current price and market data from CoinGecko API.

        Args:
            coin_id: CoinGecko coin ID (e.g., 'bitcoin', 'ethereum')

        Returns:
            Dictionary with price and market data
        """
        try:
            # CoinGecko API endpoint
            url = f"{self.coingecko_base_url}/simple/price"
            params = {
                'ids': coin_id.lower(),
                'vs_currencies': 'usd',
                'include_market_cap': 'true',
                'include_24hr_vol': 'true',
                'include_24hr_change': 'true'
            }

            response = requests.get(url, params=params, headers=self.headers, timeout=10)
            response.raise_for_status()
            data = response.json()

            if coin_id.lower() in data:
                coin_data = data[coin_id.lower()]
                return {
                    'price_usd': coin_data.get('usd', 'N/A'),
                    'market_cap': coin_data.get('usd_market_cap', 'N/A'),
                    'volume_24h': coin_data.get('usd_24h_vol', 'N/A'),
                    'change_24h': coin_data.get('usd_24h_change', 0.0),
                    'source': 'coingecko'
                }
            else:
                logger.warning(f"Coin {coin_id} not found on CoinGecko")
                return {
                    'price_usd': 'N/A',
                    'market_cap': 'N/A',
                    'volume_24h': 'N/A',
                    'change_24h': 0.0,
                    'source': 'coingecko',
                    'error': f'Coin {coin_id} not found'
                }
        except requests.RequestException as e:
            logger.error(f"Failed to fetch price for {coin_id}: {e}")
            return {
                'price_usd': 'Data Unavailable',
                'market_cap': 'N/A',
                'volume_24h': 'N/A',
                'change_24h': 0.0,
                'source': 'coingecko',
                'error': str(e)
            }

    def scrape_google_news(self, coin_name: str, max_articles: int = 10) -> List[Dict]:
        """
        Scrapes Google News for cryptocurrency articles.

        Args:
            coin_name: Cryptocurrency name (e.g., 'Bitcoin', 'Ethereum')
            max_articles: Maximum number of articles to fetch

        Returns:
            List of article dictionaries with title and source
        """
        articles = []
        try:
            # Google News search URL
            query = f"{coin_name}+crypto+news"
            url = f"https://news.google.com/search?q={query}"

            response = requests.get(url, headers=self.headers, timeout=10)
            response.raise_for_status()

            soup = BeautifulSoup(response.content, 'html.parser')

            # Extract headlines from Google News structure
            # Article items in Google News are typically in 'article' tags
            article_items = soup.find_all('article')

            for item in article_items[:max_articles]:
                try:
                    # Extract headline
                    headline_elem = item.find('h3')
                    if headline_elem:
                        headline = headline_elem.get_text(strip=True)

                        # Extract source
                        source_elem = item.find('small')
                        source = source_elem.get_text(strip=True) if source_elem else 'Unknown'

                        articles.append({
                            'title': headline,
                            'source': source,
                            'coin': coin_name
                        })
                except Exception as e:
                    logger.debug(f"Failed to parse article item: {e}")
                    continue

            logger.info(f"Scraped {len(articles)} articles for {coin_name} from Google News")
        except Exception as e:
            logger.error(f"Failed to scrape Google News for {coin_name}: {e}")

        return articles

    def scrape_cryptonews(self, coin_name: str, max_articles: int = 10) -> List[Dict]:
        """
        Scrapes Crypto News for articles.

        Args:
            coin_name: Cryptocurrency name
            max_articles: Maximum number of articles to fetch

        Returns:
            List of article dictionaries
        """
        articles = []
        try:
            # Try to fetch from cryptonews RSS or main page
            url = f"https://cryptonews.com/news/{coin_name.lower()}/"
            response = requests.get(url, headers=self.headers, timeout=10)
            response.raise_for_status()

            soup = BeautifulSoup(response.content, 'html.parser')

            # Find article headlines (structure may vary)
            article_links = soup.find_all('a', {'class': 'article-link'})

            for link in article_links[:max_articles]:
                try:
                    title = link.get_text(strip=True)
                    if title:
                        articles.append({
                            'title': title,
                            'source': 'CryptoNews',
                            'coin': coin_name
                        })
                except Exception as e:
                    logger.debug(f"Failed to parse cryptonews article: {e}")
                    continue

            logger.info(f"Scraped {len(articles)} articles for {coin_name} from CryptoNews")
        except Exception as e:
            logger.debug(f"Failed to scrape CryptoNews for {coin_name}: {e}")

        return articles

    def analyze_news_sentiment(self, coin_name: str) -> Tuple[float, str, List[Dict]]:
        """
        Scrapes news headlines and analyzes sentiment using VADER.

        Args:
            coin_name: Cryptocurrency name (e.g., 'Bitcoin')

        Returns:
            Tuple of (avg_sentiment_score, sentiment_label, analyzed_headlines)
        """
        try:
            # Collect articles from multiple sources
            all_articles = []
            all_articles.extend(self.scrape_google_news(coin_name, max_articles=5))
            all_articles.extend(self.scrape_cryptonews(coin_name, max_articles=5))

            if not all_articles:
                logger.warning(f"No articles found for {coin_name}")
                return 0.0, "Neutral", []

            # Analyze sentiment for each headline
            analyzed_articles = []
            scores = []

            for article in all_articles:
                title = article.get('title', '')
                scores_dict = self.analyzer.polarity_scores(title)
                compound_score = scores_dict['compound']
                scores.append(compound_score)

                # Classify sentiment
                if compound_score >= 0.05:
                    sentiment = "Bullish"
                elif compound_score <= -0.05:
                    sentiment = "Bearish"
                else:
                    sentiment = "Neutral"

                analyzed_articles.append({
                    'headline': title,
                    'source': article.get('source', 'Unknown'),
                    'sentiment_score': compound_score,
                    'sentiment_label': sentiment,
                    'vader_scores': scores_dict
                })

            # Calculate average sentiment
            avg_score = sum(scores) / len(scores) if scores else 0.0

            # Determine overall sentiment
            if avg_score >= 0.05:
                overall_sentiment = "Bullish"
            elif avg_score <= -0.05:
                overall_sentiment = "Bearish"
            else:
                overall_sentiment = "Neutral"

            logger.info(f"Analyzed {len(analyzed_articles)} headlines for {coin_name}: {overall_sentiment} (score: {avg_score:.4f})")

            return round(avg_score, 4), overall_sentiment, analyzed_articles

        except Exception as e:
            logger.error(f"Failed to analyze news sentiment for {coin_name}: {e}")
            return 0.0, "Error", []

    def process_coin(self, coin_name: str, coin_id: str) -> Dict:
        """
        Main execution flow: Fetch price -> Analyze news -> Store in Redis.

        Args:
            coin_name: Display name (e.g., 'Bitcoin')
            coin_id: CoinGecko ID (e.g., 'bitcoin')

        Returns:
            Dictionary with complete analysis results
        """
        logger.info(f"--- Processing {coin_name} ---")

        try:
            # Fetch market data
            price_data = self.fetch_price(coin_id)

            # Analyze news sentiment
            sentiment_score, sentiment_label, headlines = self.analyze_news_sentiment(coin_name)

            # Prepare comprehensive entry for Redis
            entry = {
                "name": coin_name,
                "id": coin_id,
                "price_usd": str(price_data.get('price_usd', 'N/A')),
                "market_cap": str(price_data.get('market_cap', 'N/A')),
                "volume_24h": str(price_data.get('volume_24h', 'N/A')),
                "price_change_24h": str(price_data.get('change_24h', 0.0)),
                "sentiment_score": sentiment_score,
                "sentiment_label": sentiment_label,
                "num_articles_analyzed": len(headlines),
                "timestamp": datetime.now().isoformat(),
                "last_update": f"Analysis for {coin_name} as of {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}"
            }

            # Store main entry as Redis Hash
            redis_key = f"crypto_sentiment:{coin_id}"
            self.r.hset(redis_key, mapping=entry)
            logger.info(f"Stored {coin_name} to Redis at key: {redis_key}")

            # Store detailed headlines as JSON
            headlines_key = f"crypto_sentiment:{coin_id}:headlines"
            self.r.set(headlines_key, json.dumps(headlines))
            logger.info(f"Stored {len(headlines)} headlines for {coin_name}")

            # Add to a sorted set of recent coins for easy retrieval
            self.r.zadd("crypto_sentiment:recent", {coin_id: time.time()})

            return {
                "success": True,
                "data": entry,
                "headlines": headlines,
                "redis_keys": {
                    "main": redis_key,
                    "headlines": headlines_key
                }
            }

        except Exception as e:
            logger.error(f"Failed to process {coin_name}: {e}")
            return {
                "success": False,
                "coin": coin_name,
                "error": str(e)
            }

    def get_coin_analysis(self, coin_id: str) -> Optional[Dict]:
        """
        Retrieve stored analysis for a coin.

        Args:
            coin_id: CoinGecko ID (e.g., 'bitcoin')

        Returns:
            Dictionary with stored analysis or None
        """
        try:
            redis_key = f"crypto_sentiment:{coin_id}"
            data = self.r.hgetall(redis_key)

            if data:
                # Get headlines too
                headlines_key = f"crypto_sentiment:{coin_id}:headlines"
                headlines_data = self.r.get(headlines_key)
                headlines = json.loads(headlines_data) if headlines_data else []

                return {
                    "data": data,
                    "headlines": headlines
                }
            else:
                logger.warning(f"No analysis found for {coin_id}")
                return None

        except Exception as e:
            logger.error(f"Failed to retrieve analysis for {coin_id}: {e}")
            return None

    def get_all_coins_analysis(self) -> Dict:
        """
        Retrieve analysis for all tracked coins.

        Returns:
            Dictionary mapping coin_id to analysis data
        """
        try:
            all_analysis = {}

            # Get all coins from recent set
            coins = self.r.zrange("crypto_sentiment:recent", 0, -1)

            for coin_id in coins:
                analysis = self.get_coin_analysis(coin_id)
                if analysis:
                    all_analysis[coin_id] = analysis

            logger.info(f"Retrieved analysis for {len(all_analysis)} coins")
            return all_analysis

        except Exception as e:
            logger.error(f"Failed to retrieve all analysis: {e}")
            return {}

    def clear_coin_data(self, coin_id: str) -> bool:
        """
        Clear stored data for a specific coin.

        Args:
            coin_id: CoinGecko ID

        Returns:
            True if successful, False otherwise
        """
        try:
            redis_key = f"crypto_sentiment:{coin_id}"
            headlines_key = f"crypto_sentiment:{coin_id}:headlines"

            self.r.delete(redis_key)
            self.r.delete(headlines_key)
            self.r.zrem("crypto_sentiment:recent", coin_id)

            logger.info(f"Cleared data for {coin_id}")
            return True

        except Exception as e:
            logger.error(f"Failed to clear data for {coin_id}: {e}")
            return False


# --- Usage Example ---
if __name__ == "__main__":
    import sys

    logger.info("Crypto Sentiment Pipeline Starting")

    # Initialize pipeline
    pipeline = CryptoSentimentPipeline()

    # Define coins to track
    coins_to_track = [
        ("Bitcoin", "bitcoin"),
        ("Ethereum", "ethereum"),
        ("Solana", "solana"),
    ]

    # Process each coin
    results = []
    for name, cid in coins_to_track:
        result = pipeline.process_coin(name, cid)
        results.append(result)
        # Add delay to avoid rate limiting
        time.sleep(2)

    # Display results
    logger.info("\n===== Processing Complete =====")
    for result in results:
        if result.get("success"):
            data = result["data"]
            logger.info(f"\n{data['name']}:")
            logger.info(f"  Price: ${data['price_usd']}")
            logger.info(f"  Sentiment: {data['sentiment_label']} ({data['sentiment_score']})")
            logger.info(f"  Articles Analyzed: {data['num_articles_analyzed']}")
        else:
            logger.error(f"Failed to process: {result.get('error')}")

    # Example: Retrieve stored data
    logger.info("\n===== Retrieving Stored Data =====")
    bitcoin_data = pipeline.get_coin_analysis("bitcoin")
    if bitcoin_data:
        logger.info("\nBitcoin Data from Redis:")
        logger.info(f"  Main Data: {bitcoin_data['data']}")
        logger.info(f"  Headlines Count: {len(bitcoin_data['headlines'])}")

