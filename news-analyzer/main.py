import asyncio
import logging
import sys
from typing import List, Dict, Any

from redis_client import RedisClient
from scraper.news_scraper import NewsScraper
from parsers.article_parser import ArticleParser
from sentiment.sentiment_analyzer import SentimentAnalyzer
from decision_engine import DecisionEngine
from scheduler import NewsAnalysisScheduler

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler('news_analyzer.log')
    ]
)

logger = logging.getLogger(__name__)

class CryptoNewsAnalyzer:
    def __init__(self):
        self.redis_client = RedisClient()
        self.scraper = NewsScraper()
        self.parser = ArticleParser()
        self.sentiment_analyzer = SentimentAnalyzer()
        self.decision_engine = DecisionEngine()
        self.scheduler = NewsAnalysisScheduler(self)

    async def run_analysis_cycle(self):
        """Run a complete analysis cycle for all coins."""
        logger.info("Starting news analysis cycle...")
        
        # 1. Fetch all symbols from Redis
        symbols, _ = self.redis_client.get_binance_symbols()
        if not symbols:
            logger.warning("No symbols found in Redis.")
            return
        
        # Extract coin names (e.g., BTC, ETH)
        coins = [s.replace('USDT', '') for s in symbols if s.endswith('USDT')]
        total_coins = len(coins)
        logger.info(f"Analyzing {total_coins} coins...")

        # Maintain a single session for the entire cycle
        async with self.scraper:
            # 2. Scrape general articles
            logger.info("Step 1: Scraping general crypto news feeds...")
            general_articles = await self._scrape_all_sources(coins)
            
            # 3. Analyze each coin
            logger.info("Step 2: Assigning and performing targeted analysis for each coin...")
            coin_articles = {coin: [] for coin in coins}
            
            # Assign general articles to coins
            for article in general_articles:
                for coin in coins:
                    text = (article.get('title', '') + ' ' + article.get('content', '')).upper()
                    if coin.upper() in text:
                        coin_articles[coin].append(article)

            # Perform targeted analysis for each coin
            for i, coin in enumerate(coins):
                try:
                    # If insufficient news from general feeds, do a targeted search
                    if len(coin_articles[coin]) < 2:
                        logger.debug(f"Insufficient news for {coin}, performing targeted search...")
                        symbol = f"{coin}USDT"
                        targeted_news = await self.scraper.scrape_symbol_news(symbol, coin)
                        
                        # Process and analyze these new articles
                        for article in targeted_news:
                            # Analyze sentiment
                            sentiment = self.sentiment_analyzer.analyze_sentiment(article.get('title', ''))
                            article['sentiment_analysis'] = sentiment
                            
                            # Analyze keywords
                            keyword_signal = self.sentiment_analyzer.analyze_keywords(article.get('title', ''))
                            article['keyword_signal'] = keyword_signal
                            
                            coin_articles[coin].append(article)
                            await asyncio.sleep(0.1)

                    # Final analysis for the coin
                    articles = coin_articles[coin]
                    if articles:
                        # Run detailed analysis
                        detailed_analysis = self.decision_engine.analyze_coin_detailed(
                            coin,
                            articles,
                            search_query=f"{coin} crypto news",
                            fetched_urls=[a.get('url') for a in articles]
                        )
                        
                        if detailed_analysis:
                            # 4. Generate and store AI Summary (Matching Google AI Search format)
                            ai_summary = self.decision_engine.generate_ai_summary(coin, detailed_analysis)
                            detailed_analysis['ai_summary'] = ai_summary
                            
                            self.redis_client.store_detailed_analysis(coin, detailed_analysis)
                            self.redis_client.store_analysis(coin, detailed_analysis)
                    
                    # Log progress
                    if (i + 1) % max(1, total_coins // 10) == 0 or (i + 1) == total_coins:
                        progress = (i + 1) / total_coins * 100
                        logger.info(f"Coin Analysis Progress: {progress:.1f}% ({i+1}/{total_coins})")

                except Exception as e:
                    logger.error(f"Failed to analyze coin {coin}: {e}")

        logger.info("Analysis cycle completed.")

    async def _scrape_all_sources(self, coins: List[str]) -> List[Dict[str, Any]]:
        """Scrape articles from all configured sources."""
        all_articles = []
        processed_articles = []

        # Session is now managed by the caller
        # 1. General Scraping (Fast, gets latest news)
        sources = ['coindesk', 'cointelegraph', 'theblock', 'decrypt', 'reddit', 'cmc', 'binance_gainers']
        for source in sources:
            try:
                articles = await self.scraper.scrape_source(source)
                all_articles.extend(articles)
                logger.info(f"Scraped {len(articles)} articles from {source}")
            except Exception as e:
                logger.error(f"Failed to scrape {source}: {e}")

            # 2. Identify coins with NO news coverage in general scraping
            coins_with_news = set()
            for article in all_articles:
                text = (article.get('title', '') + ' ' + article.get('content', '')).upper()
                for coin in coins:
                    if coin.upper() in text:
                        coins_with_news.add(coin)
            
            coins_missing_news = [c for c in coins if c not in coins_with_news]
            logger.info(f"{len(coins_with_news)} coins have news coverage. {len(coins_missing_news)} coins missing news.")

            # 3. Targeted Search for missing coins (Limit to first 30 to avoid rate limits)
            # Prioritize major coins or those missing news
            if coins_missing_news:
                logger.info(f"Performing targeted search for {min(len(coins_missing_news), 30)} missing coins...")
                for coin in coins_missing_news[:30]:
                    try:
                        search_articles = await self.scraper.search_coin_news(coin)
                        all_articles.extend(search_articles)
                        await asyncio.sleep(1) # Delay between searches
                    except Exception as e:
                        logger.error(f"Search failed for {coin}: {e}")

            # 4. Process and enrich all collected articles
            total_articles = len(all_articles)
            logger.info(f"Enriching and parsing {total_articles} collected articles...")
            
            for i, article in enumerate(all_articles):
                try:
                    # Check if already processed
                    if self.redis_client.is_article_processed(article['url']):
                        continue

                    # Get full content if needed (only for non-search articles or short ones)
                    if not article.get('content') or len(article['content']) < 100:
                        article['content'] = await self.scraper.get_article_content(article['url'])

                    # Parse article
                    parsed = self.parser.parse_article(article['url'], article.get('content'))
                    article.update(parsed)

                    # Analyze sentiment
                    sentiment = self.sentiment_analyzer.analyze_sentiment(
                        article.get('title', '') + ' ' + article.get('text', '')
                    )
                    article['sentiment_analysis'] = sentiment

                    # Analyze keywords
                    keyword_signal = self.sentiment_analyzer.analyze_keywords(
                        article.get('title', '') + ' ' + article.get('text', '')
                    )
                    article['keyword_signal'] = keyword_signal

                    processed_articles.append(article)

                    # Mark as processed
                    self.redis_client.mark_article_processed(article['url'])
                    self.redis_client.cache_article(article['url'], article.get('content', ''))
                    
                    # Log progress
                    if (i + 1) % max(1, total_articles // 10) == 0 or (i + 1) == total_articles:
                        progress = (i + 1) / total_articles * 100
                        logger.info(f"Article Processing Progress: {progress:.1f}% ({i+1}/{total_articles})")

                except Exception as e:
                    logger.error(f"Failed to process article {article.get('url')}: {e}")

            logger.info(f"Successfully processed {len(processed_articles)} articles")
            return processed_articles

    async def _analyze_coin(self, coin: str, articles: List[Dict[str, Any]]) -> Dict[str, Any]:
        """Analyze articles for a specific coin."""
        analysis = self.decision_engine.analyze_coin(coin, articles)

        if self.decision_engine.validate_analysis(analysis):
            logger.info(f"Analysis for {coin}: {analysis['decision']} (score: {analysis['score']})")
            return analysis
        else:
            logger.error(f"Invalid analysis generated for {coin}")
            return None

    async def get_analysis(self, coin: str) -> Dict[str, Any]:
        """Get stored analysis for a coin."""
        # Try to get from Redis first
        analysis = self.redis_client.get_cached_analysis(coin)
        if analysis:
            return analysis

        # If not found, run analysis
        symbols = self.redis_client.get_usdt_symbols()
        if f"{coin}USDT" not in symbols:
            return None

        articles = await self._scrape_all_sources([coin])
        return await self._analyze_coin(coin, articles)

    async def start_scheduler(self, interval_minutes=15):
        """Start the analysis scheduler."""
        await self.scheduler.start(interval_minutes)

    async def stop_scheduler(self):
        """Stop the analysis scheduler."""
        await self.scheduler.stop()

    def get_status(self):
        """Get system status."""
        return {
            'scheduler': self.scheduler.get_status(),
            'redis_connected': self.redis_client.client.ping() if self.redis_client.client else False
        }

async def main():
    """Main entry point."""
    analyzer = CryptoNewsAnalyzer()

    if len(sys.argv) > 1:
        command = sys.argv[1]

        if command == 'run-once':
            await analyzer.run_analysis_cycle()
        elif command == 'start':
            interval = int(sys.argv[2]) if len(sys.argv) > 2 else 15
            await analyzer.start_scheduler(interval)
            # Keep running
            try:
                while True:
                    await asyncio.sleep(60)
            except KeyboardInterrupt:
                await analyzer.stop_scheduler()
        elif command == 'status':
            status = analyzer.get_status()
            print(f"Scheduler running: {status['scheduler']['running']}")
            print(f"Redis connected: {status['redis_connected']}")
        else:
            print("Usage: python main.py [run-once|start [interval]|status]")
    else:
        # Default: run once
        await analyzer.run_analysis_cycle()

if __name__ == "__main__":
    asyncio.run(main())
