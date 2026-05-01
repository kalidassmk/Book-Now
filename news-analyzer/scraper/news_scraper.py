import asyncio
import aiohttp
import feedparser
import requests
from bs4 import BeautifulSoup
from fake_useragent import UserAgent
import logging
import time
import random
from urllib.parse import urljoin

logger = logging.getLogger(__name__)

class NewsScraper:
    def __init__(self):
        self.ua = UserAgent()
        self.session = None
        self.sources = {
            'coindesk': {
                'rss': 'https://www.coindesk.com/arc/outboundfeeds/rss/',
                'base_url': 'https://www.coindesk.com',
                'weight': 1.0
            },
            'cointelegraph': {
                'rss': 'https://cointelegraph.com/rss',
                'base_url': 'https://cointelegraph.com',
                'weight': 0.9
            },
            'theblock': {
                'rss': 'https://www.theblock.co/rss.xml',
                'base_url': 'https://www.theblock.co',
                'weight': 1.0
            },
            'decrypt': {
                'rss': 'https://decrypt.co/feed',
                'base_url': 'https://decrypt.co',
                'weight': 0.8
            },
            'cryptopanic': {
                'rss': 'https://cryptopanic.com/news/rss/',
                'base_url': 'https://cryptopanic.com',
                'weight': 1.3
            },
            'binance_feed': {
                'url_template': 'https://www.binance.com/en/feed/news?symbol={symbol}',
                'base_url': 'https://www.binance.com',
                'weight': 1.2
            },
            'cryptopanic_search': {
                'url_template': 'https://cryptopanic.com/news/{coin_lower}/',
                'base_url': 'https://cryptopanic.com',
                'weight': 1.1
            },
            'binance_gainers': {
                'url': 'https://www.binance.com/en-IN/price/top-gaining-crypto',
                'base_url': 'https://www.binance.com',
                'weight': 1.0
            }
        }

    async def __aenter__(self):
        self.session = aiohttp.ClientSession(headers={'User-Agent': self.ua.random})
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        if self.session:
            await self.session.close()

    def _get_random_delay(self):
        return random.uniform(1, 3)

    async def scrape_source(self, source_name, coin=None):
        """Scrape articles from a specific source."""
        source = self.sources.get(source_name)
        if not source:
            return []

        articles = []

        try:
            if 'rss' in source:
                articles = await self._scrape_rss(source['rss'], source_name, coin)
            else:
                articles = await self._scrape_html(source['url'], source_name, coin)
        except Exception as e:
            logger.error(f"Failed to scrape {source_name}: {e}")

        return articles[:50]  # Increased limit

    async def _scrape_rss(self, rss_url, source_name, coin=None):
        """Scrape articles from RSS feed."""
        articles = []
        try:
            async with self.session.get(rss_url) as response:
                if response.status == 200:
                    content = await response.text()
                    feed = feedparser.parse(content)

                    # Latest 50 articles
                    for entry in feed.entries[:50]:
                        title = entry.get('title', '')
                        url = entry.get('link', '')
                        published = entry.get('published_parsed')

                        # Filter by coin if provided
                        if coin and coin.upper() not in title.upper():
                            continue

                        articles.append({
                            'title': title,
                            'url': url,
                            'published': time.mktime(published) if published else time.time(),
                            'source': source_name,
                            'content': entry.get('summary', '')
                        })

                        await asyncio.sleep(0.1) # Shorter delay for batch
        except Exception as e:
            logger.error(f"RSS scrape failed for {source_name}: {e}")

        return articles

    async def search_coin_news(self, coin_name: str, max_results: int = 10):
        """Search for coin news using multiple engines and robust DOM parsing."""
        articles = []
        
        # 1. Try DuckDuckGo first (more scraper-friendly than Google)
        try:
            query = f"{coin_name} crypto news"
            url = f"https://duckduckgo.com/html/?q={query.replace(' ', '+')}"
            
            async with self.session.get(url, headers={'User-Agent': self.ua.random}) as response:
                if response.status == 200:
                    html = await response.text()
                    soup = BeautifulSoup(html, 'html.parser')
                    
                    # DuckDuckGo HTML items
                    items = soup.find_all('div', class_='result')
                    for item in items[:max_results]:
                        a = item.find('a', class_='result__a')
                        snippet = item.find('a', class_='result__snippet')
                        if a:
                            articles.append({
                                'title': a.get_text(strip=True),
                                'url': a['href'],
                                'published': time.time(),
                                'source': 'duckduckgo_search',
                                'content': snippet.get_text(strip=True) if snippet else ''
                            })
            
            if articles:
                logger.info(f"DuckDuckGo found {len(articles)} articles for {coin_name}")
                return articles
        except Exception as e:
            logger.debug(f"DuckDuckGo search failed for {coin_name}: {e}")

        # 2. Fallback to Google News (Basic search)
        try:
            query = f"{coin_name}+crypto+news"
            url = f"https://news.google.com/search?q={query}&hl=en-US&gl=US&ceid=US:en"
            
            async with self.session.get(url, headers={'User-Agent': self.ua.random}) as response:
                if response.status == 200:
                    html = await response.text()
                    soup = BeautifulSoup(html, 'html.parser')
                    
                    items = soup.find_all('article')
                    for item in items[:max_results]:
                        h3 = item.find('h3')
                        a = item.find('a', href=True)
                        if h3 and a:
                            articles.append({
                                'title': h3.get_text(strip=True),
                                'url': urljoin('https://news.google.com/', a['href']),
                                'published': time.time(),
                                'source': 'google_news_search',
                                'content': ''
                            })
            
            if articles:
                logger.info(f"Google News found {len(articles)} articles for {coin_name}")
        except Exception as e:
            logger.error(f"Google News search failed for {coin_name}: {e}")
            
        return articles

    async def _scrape_html(self, url, source_name, coin=None):
        """Scrape articles or data from HTML pages."""
        if source_name == 'binance_gainers':
            return await self._scrape_binance_gainers(url)
        return []

    async def scrape_symbol_news(self, symbol, coin_name):
        """Scrape news specifically for a symbol."""
        articles = []
        
        # 1. Try Binance Feed (Most relevant for Binance symbols)
        try:
            url = self.sources['binance_feed']['url_template'].format(symbol=symbol)
            async with self.session.get(url) as response:
                if response.status == 200:
                    html = await response.text()
                    soup = BeautifulSoup(html, 'html.parser')
                    # Extract news items from Binance Feed HTML
                    # (This is a heuristic as Binance uses dynamic loading, 
                    # but we can catch some pre-rendered or SEO content)
                    for item in soup.find_all(['h3', 'h4']):
                        text = item.get_text(strip=True)
                        if len(text) > 20:
                            articles.append({
                                'title': text,
                                'url': url,
                                'published': time.time(),
                                'source': 'binance_feed',
                                'content': ''
                            })
        except Exception as e:
            logger.debug(f"Binance feed failed for {symbol}: {e}")

        # 2. Try Google News Search (Fallback)
        if len(articles) < 3:
            search_results = await self.search_coin_news(coin_name, max_results=5)
            articles.extend(search_results)

        return articles[:10]

    async def _scrape_binance_gainers(self, url):
        """Specialized scraper for Binance Top Gainers page."""
        articles = []
        try:
            # Note: Binance pages often require JavaScript execution.
            # For now, we'll try a basic request and look for symbol patterns.
            async with self.session.get(url) as response:
                if response.status == 200:
                    html = await response.text()
                    soup = BeautifulSoup(html, 'html.parser')
                    
                    # Look for coin symbols in common Binance table structures
                    # Often they are in divs or spans with specific classes or data attributes
                    symbols = set()
                    
                    # Pattern 1: Look for text that looks like a symbol (e.g., BTC, ETH) 
                    # usually next to a name or in a specific column
                    for row in soup.find_all('tr'):
                        text = row.get_text()
                        # Very basic heuristic: find uppercase words that are symbols
                        import re
                        found = re.findall(r'\b([A-Z]{2,10})\b', text)
                        for sym in found:
                            if sym not in ['PRICE', 'CHANGE', 'MARKET', 'CAP', 'VOL', 'CHART', 'TRADE']:
                                symbols.add(sym)
                    
                    for sym in list(symbols)[:10]: # Top 10
                        articles.append({
                            'title': f"Binance Top Gainer: {sym}",
                            'url': url,
                            'published': time.time(),
                            'source': 'binance_gainers',
                            'content': f"{sym} is currently listed as a top gainer on Binance.",
                            'coins_mentioned': [sym]
                        })
        except Exception as e:
            logger.error(f"Binance gainers scrape failed: {e}")
        
        return articles

    async def get_article_content(self, url):
        """Extract full content from article URL."""
        try:
            async with self.session.get(url) as response:
                if response.status == 200:
                    html = await response.text()
                    soup = BeautifulSoup(html, 'html.parser')

                    # Remove scripts and styles
                    for script in soup(["script", "style"]):
                        script.decompose()

                    # Try to find main content
                    content_selectors = [
                        'article', '.article-content', '.post-content',
                        '.entry-content', '.content', 'main'
                    ]

                    content = ''
                    for selector in content_selectors:
                        elem = soup.select_one(selector)
                        if elem:
                            content = elem.get_text(separator=' ', strip=True)
                            break

                    if not content:
                        content = soup.get_text(separator=' ', strip=True)

                    return content[:5000]  # Limit content length
        except Exception as e:
            logger.error(f"Failed to get content from {url}: {e}")

        return ""
