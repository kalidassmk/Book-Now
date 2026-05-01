import newspaper
import re
import logging
from bs4 import BeautifulSoup

logger = logging.getLogger(__name__)

class ArticleParser:
    def __init__(self):
        # Major coins only for efficiency
        self.coin_patterns = {
            'BTC': re.compile(r'\bBTC\b|\bBitcoin\b', re.IGNORECASE),
            'ETH': re.compile(r'\bETH\b|\bEthereum\b', re.IGNORECASE),
            'BNB': re.compile(r'\bBNB\b|\bBinance Coin\b', re.IGNORECASE),
            'ADA': re.compile(r'\bADA\b|\bCardano\b', re.IGNORECASE),
            'SOL': re.compile(r'\bSOL\b|\bSolana\b', re.IGNORECASE),
            'DOT': re.compile(r'\bDOT\b|\bPolkadot\b', re.IGNORECASE),
            'DOGE': re.compile(r'\bDOGE\b|\bDogecoin\b', re.IGNORECASE),
            'AVAX': re.compile(r'\bAVAX\b|\bAvalanche\b', re.IGNORECASE),
            'LTC': re.compile(r'\bLTC\b|\bLitecoin\b', re.IGNORECASE),
            'LINK': re.compile(r'\bLINK\b|\bChainlink\b', re.IGNORECASE),
            'UNI': re.compile(r'\bUNI\b|\bUniswap\b', re.IGNORECASE),
            'ALGO': re.compile(r'\bALGO\b|\bAlgorand\b', re.IGNORECASE),
            'VET': re.compile(r'\bVET\b|\bVeChain\b', re.IGNORECASE),
            'ICP': re.compile(r'\bICP\b|\bInternet Computer\b', re.IGNORECASE),
            'FIL': re.compile(r'\bFIL\b|\bFilecoin\b', re.IGNORECASE),
            'TRX': re.compile(r'\bTRX\b|\bTron\b', re.IGNORECASE),
            'ETC': re.compile(r'\bETC\b|\bEthereum Classic\b', re.IGNORECASE),
            'XLM': re.compile(r'\bXLM\b|\bStellar\b', re.IGNORECASE),
            'THETA': re.compile(r'\bTHETA\b|\bTheta\b', re.IGNORECASE),
            'FTT': re.compile(r'\bFTT\b|\bFTX Token\b', re.IGNORECASE),
            'HBAR': re.compile(r'\bHBAR\b|\bHedera\b', re.IGNORECASE),
            'NEAR': re.compile(r'\bNEAR\b|\bNear Protocol\b', re.IGNORECASE),
            'FLOW': re.compile(r'\bFLOW\b|\bFlow\b', re.IGNORECASE),
            'MANA': re.compile(r'\bMANA\b|\bDecentraland\b', re.IGNORECASE),
            'SAND': re.compile(r'\bSAND\b|\bThe Sandbox\b', re.IGNORECASE),
            'AXS': re.compile(r'\bAXS\b|\bAxie Infinity\b', re.IGNORECASE),
            'CHZ': re.compile(r'\bCHZ\b|\bChiliz\b', re.IGNORECASE),
            'ENJ': re.compile(r'\bENJ\b|\bEnjin\b', re.IGNORECASE),
            'BAT': re.compile(r'\bBAT\b|\bBasic Attention Token\b', re.IGNORECASE),
            'ZRX': re.compile(r'\bZRX\b|\b0x\b', re.IGNORECASE),
            'OMG': re.compile(r'\bOMG\b|\bOmiseGO\b', re.IGNORECASE),
            'LRC': re.compile(r'\bLRC\b|\bLoopring\b', re.IGNORECASE),
            'REP': re.compile(r'\bREP\b|\bAugur\b', re.IGNORECASE),
            'GNT': re.compile(r'\bGNT\b|\bGolem\b', re.IGNORECASE),
            'STORJ': re.compile(r'\bSTORJ\b|\bStorj\b', re.IGNORECASE),
            'ANT': re.compile(r'\bANT\b|\bAragon\b', re.IGNORECASE),
            'MKR': re.compile(r'\bMKR\b|\bMaker\b', re.IGNORECASE),
            'KNC': re.compile(r'\bKNC\b|\bKyber Network\b', re.IGNORECASE),
            'RLC': re.compile(r'\bRLC\b|\biExec\b', re.IGNORECASE),
            'MTL': re.compile(r'\bMTL\b|\bMetal\b', re.IGNORECASE),
            'POWR': re.compile(r'\bPOWR\b|\bPower Ledger\b', re.IGNORECASE),
            'WAVES': re.compile(r'\bWAVES\b|\bWaves\b', re.IGNORECASE),
            'LSK': re.compile(r'\bLSK\b|\bLisk\b', re.IGNORECASE),
            'ARK': re.compile(r'\bARK\b|\bArk\b', re.IGNORECASE),
            'STRAT': re.compile(r'\bSTRAT\b|\bStratis\b', re.IGNORECASE),
            'XEM': re.compile(r'\bXEM\b|\bNEM\b', re.IGNORECASE),
            'QTUM': re.compile(r'\bQTUM\b|\bQtum\b', re.IGNORECASE),
            'BTG': re.compile(r'\bBTG\b|\bBitcoin Gold\b', re.IGNORECASE),
            'ZEC': re.compile(r'\bZEC\b|\bZcash\b', re.IGNORECASE),
            'DASH': re.compile(r'\bDASH\b|\bDash\b', re.IGNORECASE),
            'XMR': re.compile(r'\bXMR\b|\bMonero\b', re.IGNORECASE),
            'USDT': re.compile(r'\bUSDT\b|\bTether\b', re.IGNORECASE),
            'BUSD': re.compile(r'\bBUSD\b|\bBinance USD\b', re.IGNORECASE),
            'USDC': re.compile(r'\bUSDC\b|\bUSD Coin\b', re.IGNORECASE),
            'DAI': re.compile(r'\bDAI\b|\bDai\b', re.IGNORECASE),
        }

    def parse_article(self, url, html_content=None):
        """Parse article using newspaper3k or fallback to BeautifulSoup."""
        try:
            if not html_content:
                article = newspaper.Article(url)
                article.download()
                article.parse()
            else:
                article = newspaper.Article(url)
                article.set_html(html_content)
                article.parse()

            parsed_data = {
                'title': article.title or '',
                'text': article.text or '',
                'authors': article.authors or [],
                'publish_date': article.publish_date.timestamp() if article.publish_date else None,
                'url': url,
                'coins_mentioned': self._detect_coins(article.title + ' ' + article.text)
            }

            return parsed_data

        except Exception as e:
            logger.error(f"Failed to parse article {url}: {e}")
            # Fallback parsing
            return self._fallback_parse(url, html_content)

    def _fallback_parse(self, url, html_content):
        """Fallback parsing using BeautifulSoup."""
        try:
            soup = BeautifulSoup(html_content or '', 'html.parser')

            # Extract title
            title = ''
            title_tag = soup.find('title')
            if title_tag:
                title = title_tag.get_text().strip()

            h1_tag = soup.find('h1')
            if h1_tag and len(h1_tag.get_text().strip()) > len(title):
                title = h1_tag.get_text().strip()

            # Extract text content
            text = ''
            content_selectors = ['article', '.article-content', '.post-content', '.entry-content', '.content', 'main']
            for selector in content_selectors:
                content_elem = soup.select_one(selector)
                if content_elem:
                    text = content_elem.get_text(separator=' ', strip=True)
                    break

            if not text:
                # Remove scripts and styles
                for script in soup(["script", "style"]):
                    script.decompose()
                text = soup.get_text(separator=' ', strip=True)

            return {
                'title': title,
                'text': text[:5000],  # Limit text length
                'authors': [],
                'publish_date': None,
                'url': url,
                'coins_mentioned': self._detect_coins(title + ' ' + text)
            }

        except Exception as e:
            logger.error(f"Fallback parsing failed for {url}: {e}")
            return {
                'title': '',
                'text': '',
                'authors': [],
                'publish_date': None,
                'url': url,
                'coins_mentioned': []
            }

    def _detect_coins(self, text):
        """Detect cryptocurrency mentions in text."""
        mentioned_coins = []
        text_lower = text.lower()

        for coin, pattern in self.coin_patterns.items():
            if pattern.search(text):
                mentioned_coins.append(coin)

        return list(set(mentioned_coins))  # Remove duplicates
