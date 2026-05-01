#!/usr/bin/env python3
"""
Generate Summary Report for All Binance USDT Coins

This script generates a comprehensive summary report for all Binance USDT trading pairs
in the requested tabular format:

Project/Event | Key Information | Sentiment

Usage:
    python generate_summary_report.py [--refresh] [--limit N] [--output FILE]

Options:
    --refresh: Force fresh analysis instead of using cached results
    --limit N: Limit to first N coins (default: all)
    --output FILE: Save results to file (default: print to stdout)
"""

import asyncio
import json
import sys
import argparse
from typing import List, Dict, Any, Optional
from datetime import datetime
import logging

from redis_client import RedisClient
from scraper.news_scraper import NewsScraper
from parsers.article_parser import ArticleParser
from sentiment.sentiment_analyzer import SentimentAnalyzer
from decision_engine import DecisionEngine

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

class SummaryReportGenerator:
    def __init__(self):
        self.redis_client = RedisClient()
        self.scraper = NewsScraper()
        self.parser = ArticleParser()
        self.sentiment_analyzer = SentimentAnalyzer()
        self.decision_engine = DecisionEngine()

    def get_coin_info(self, symbol: str, details: Dict) -> Dict[str, Any]:
        """Get basic information about a coin from symbol details."""
        symbol_data = details.get(symbol, {})
        base_asset = symbol_data.get('base_asset', symbol.replace('USDT', ''))

        return {
            'symbol': symbol,
            'base_asset': base_asset,
            'min_qty': symbol_data.get('min_qty', 'N/A'),
            'max_qty': symbol_data.get('max_qty', 'N/A'),
            'status': symbol_data.get('status', 'UNKNOWN')
        }

    def generate_key_information(self, coin_name: str, coin_info: Dict, analysis_data: Optional[Dict] = None) -> str:
        """Generate key information summary for a coin."""
        base_asset = coin_info['base_asset']
        symbol = coin_info['symbol']

        # Start with basic trading info
        key_info_parts = []

        # Add trading status
        if coin_info['status'] == 'TRADING':
            key_info_parts.append("Active trading pair on Binance")
        else:
            key_info_parts.append(f"Trading status: {coin_info['status']}")

        # Add trading limits if available
        if coin_info['min_qty'] != 'N/A':
            key_info_parts.append(f"Min trade: {coin_info['min_qty']}")

        # Add analysis insights if available
        if analysis_data:
            articles_count = analysis_data.get('articles_analyzed', 0)
            if articles_count > 0:
                key_info_parts.append(f"{articles_count} articles analyzed")

            final_score = analysis_data.get('final_score', 0)
            if final_score != 0:
                key_info_parts.append(f"Analysis score: {final_score:.4f}")

        # If no analysis data, add basic market cap tier estimation
        else:
            # Estimate market cap tier based on symbol patterns
            if symbol in ['BTCUSDT', 'ETHUSDT']:
                key_info_parts.append("Major cryptocurrency")
            elif len(base_asset) <= 3 and base_asset.isupper():
                key_info_parts.append("Established altcoin")
            elif '1000' in symbol or '1M' in symbol or '1B' in symbol:
                key_info_parts.append("Meme token or high-supply token")
            elif len(base_asset) > 10:
                key_info_parts.append("New or specialized token")
            else:
                key_info_parts.append("Cryptocurrency trading pair")

        return "; ".join(key_info_parts)

    def determine_sentiment(self, analysis_data: Optional[Dict] = None) -> str:
        """Determine sentiment based on analysis data."""
        if not analysis_data:
            return "No Data"

        decision = analysis_data.get('decision', 'HOLD')
        final_score = analysis_data.get('final_score', 0)
        articles_count = analysis_data.get('articles_analyzed', 0)

        # If insufficient data
        if articles_count < 3:
            return "Insufficient Data"

        # Map decision to sentiment
        if decision == 'BUY':
            if final_score > 0.3:
                return "Highly Bullish"
            else:
                return "Bullish"
        elif decision == 'SELL':
            if final_score < -0.3:
                return "Highly Bearish"
            else:
                return "Bearish"
        else:  # HOLD
            if abs(final_score) < 0.1:
                return "Neutral"
            elif final_score > 0:
                return "Slightly Bullish"
            else:
                return "Slightly Bearish"

    async def analyze_coin_if_needed(self, coin_name: str, refresh: bool = False) -> Optional[Dict]:
        """Get analysis for a coin, either from cache or fresh analysis."""
        if not refresh:
            # Try to get cached analysis first
            analysis = self.redis_client.get_detailed_analysis(coin_name)
            if analysis:
                return analysis

        # Perform fresh analysis
        try:
            logger.info(f"Analyzing {coin_name}...")

            # Scrape articles for this coin
            articles = await self._scrape_coin_articles(coin_name)
            if not articles:
                return None

            # Process articles
            processed_articles = await self._process_articles(articles)

            # Generate analysis
            analysis = self.decision_engine.analyze_coin_detailed(
                coin_name,
                processed_articles,
                search_query=f"{coin_name} crypto news",
                fetched_urls=[a.get('url') for a in processed_articles]
            )

            if analysis:
                # Store the analysis
                self.redis_client.store_detailed_analysis(coin_name, analysis)

            return analysis

        except Exception as e:
            logger.error(f"Failed to analyze {coin_name}: {e}")
            return None

    async def _scrape_coin_articles(self, coin_name: str) -> List[Dict]:
        """Scrape articles for a specific coin."""
        articles = []

        async with self.scraper:
            # Scrape from multiple sources
            sources = ['coindesk', 'cointelegraph', 'theblock', 'decrypt']

            for source in sources:
                try:
                    source_articles = await self.scraper.scrape_source(source, coin=coin_name)
                    articles.extend(source_articles)
                except Exception as e:
                    logger.warning(f"Failed to scrape {source}: {e}")

        return articles[:10]  # Limit to 10 articles per coin

    async def _process_articles(self, articles: List[Dict]) -> List[Dict]:
        """Process and enrich articles with sentiment analysis."""
        processed_articles = []

        for article in articles:
            try:
                # Get full content if needed
                if not article.get('content') or len(article['content']) < 100:
                    article['content'] = await self.scraper.get_article_content(article['url'])

                # Parse article
                parsed = self.parser.parse_article(article['url'], article.get('content', ''))
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

                # Cache content
                self.redis_client.cache_article(article['url'], article.get('content', ''))

            except Exception as e:
                logger.error(f"Failed to process article {article.get('url')}: {e}")

        return processed_articles

    def format_table_row(self, project_event: str, key_info: str, sentiment: str) -> str:
        """Format a single table row."""
        # Ensure proper spacing and alignment
        return f"{project_event}\t{key_info}\t{sentiment}"

    async def generate_report(self, refresh: bool = False, limit: Optional[int] = None, output_file: Optional[str] = None, specific_coins: List[str] = None, major_coins_only: bool = False) -> str:
        """Generate the complete summary report."""
        logger.info("Starting summary report generation...")

        # Get all Binance symbols
        symbols, details = self.redis_client.get_binance_symbols()
        if not symbols:
            return "No Binance symbols found in Redis. Run fetch_binance_symbols.py first."

        # Filter specific coins if requested
        if specific_coins:
            symbols = [s for s in symbols if s.replace('USDT', '') in specific_coins]

        # Filter major coins only if requested
        if major_coins_only:
            major_coins = ['BTC', 'ETH', 'BNB', 'ADA', 'SOL', 'DOGE']
            symbols = [s for s in symbols if s.replace('USDT', '') in major_coins]

        # Limit if specified
        if limit:
            symbols = symbols[:limit]

        logger.info(f"Generating report for {len(symbols)} symbols")

        # Header
        report_lines = [
            "Project/Event\tKey Information\tSentiment",
            "-" * 80
        ]

        processed_count = 0

        # Process each symbol
        for symbol in symbols:
            try:
                coin_name = symbol.replace('USDT', '')
                coin_info = self.get_coin_info(symbol, details)

                # Get analysis data
                analysis_data = await self.analyze_coin_if_needed(coin_name, refresh)

                # Generate report components
                project_event = f"{coin_name} ({symbol})"
                key_info = self.generate_key_information(coin_name, coin_info, analysis_data)
                sentiment = self.determine_sentiment(analysis_data)

                # Add to report
                report_lines.append(self.format_table_row(project_event, key_info, sentiment))

                processed_count += 1

                # Progress logging
                if processed_count % 50 == 0:
                    logger.info(f"Processed {processed_count}/{len(symbols)} coins")

            except Exception as e:
                logger.error(f"Failed to process {symbol}: {e}")
                continue

        # Footer with summary
        report_lines.extend([
            "-" * 80,
            f"Report generated on: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}",
            f"Total coins processed: {processed_count}",
            f"Analysis mode: {'Fresh' if refresh else 'Cached'}"
        ])

        report = "\n".join(report_lines)

        # Output
        if output_file:
            with open(output_file, 'w', encoding='utf-8') as f:
                f.write(report)
            logger.info(f"Report saved to {output_file}")
        else:
            print(report)

        return report

async def main():
    """Main entry point."""
    parser = argparse.ArgumentParser(description="Generate crypto summary report")
    parser.add_argument('--refresh', action='store_true', help='Force fresh analysis')
    parser.add_argument('--limit', type=int, help='Limit number of coins to process')
    parser.add_argument('--output', type=str, help='Output file path')
    parser.add_argument('--coins', nargs='+', help='Specific coins to analyze (e.g., BTC ETH ADA)')
    parser.add_argument('--major', action='store_true', help='Analyze only major coins (BTC, ETH, BNB, ADA, SOL, DOGE)')

    args = parser.parse_args()

    generator = SummaryReportGenerator()
    await generator.generate_report(
        refresh=args.refresh,
        limit=args.limit,
        output_file=args.output,
        specific_coins=args.coins,
        major_coins_only=args.major
    )

if __name__ == "__main__":
    asyncio.run(main())
