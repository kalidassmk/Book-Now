import redis
import json
import logging

logger = logging.getLogger(__name__)

class RedisClient:
    def __init__(self, host='localhost', port=6379, db=0):
        self.client = redis.Redis(host=host, port=port, db=db, decode_responses=True)

    def get_usdt_symbols(self):
        """Get all Binance USDT trading symbols from Redis."""
        symbols, _ = self.get_binance_symbols()
        return symbols

    def store_analysis(self, coin, analysis_data):
        """Store analysis results in Redis."""
        key = f"analysis:{coin}"
        try:
            self.client.set(key, json.dumps(analysis_data))
            logger.info(f"Stored analysis for {coin}")
        except Exception as e:
            logger.error(f"Failed to store analysis for {coin}: {e}")

    def get_cached_article(self, url):
        """Get cached article content."""
        key = f"article:{url}"
        try:
            return self.client.get(key)
        except Exception as e:
            logger.error(f"Failed to get cached article {url}: {e}")
            return None

    def cache_article(self, url, content):
        """Cache article content with 1 hour expiry."""
        key = f"article:{url}"
        try:
            self.client.setex(key, 3600, content)  # 1 hour
        except Exception as e:
            logger.error(f"Failed to cache article {url}: {e}")

    def is_article_processed(self, url):
        """Check if article has been processed."""
        key = f"processed:{url}"
        try:
            return self.client.exists(key)
        except Exception as e:
            logger.error(f"Failed to check processed status for {url}: {e}")
            return False

    def mark_article_processed(self, url):
        """Mark article as processed with 24 hour expiry."""
        key = f"processed:{url}"
        try:
            self.client.setex(key, 86400, '1')  # 24 hours
        except Exception as e:
            logger.error(f"Failed to mark article processed {url}: {e}")

    def get_cached_analysis(self, coin):
        """Get cached analysis for a coin."""
        key = f"analysis:{coin}"
        try:
            data = self.client.get(key)
            return json.loads(data) if data else None
        except Exception as e:
            logger.error(f"Failed to get cached analysis for {coin}: {e}")
            return None

    def store_detailed_analysis(self, coin, detailed_analysis_data):
        """Store detailed analysis results in Redis."""
        key = f"analysis:{coin}:detailed"
        try:
            self.client.set(key, json.dumps(detailed_analysis_data))
            logger.info(f"Stored detailed analysis for {coin}")
        except Exception as e:
            logger.error(f"Failed to store detailed analysis for {coin}: {e}")

    def get_detailed_analysis(self, coin):
        """Get detailed analysis for a coin."""
        key = f"analysis:{coin}:detailed"
        try:
            data = self.client.get(key)
            return json.loads(data) if data else None
        except Exception as e:
            logger.error(f"Failed to get detailed analysis for {coin}: {e}")
            return None

    def get_all_analysis_keys(self):
        """Get all coin analysis keys."""
        try:
            keys = self.client.keys('analysis:*:detailed')
            coins = [key.replace('analysis:', '').replace(':detailed', '') for key in keys]
            return sorted(list(set(coins)))
        except Exception as e:
            logger.error(f"Failed to get analysis keys: {e}")
            return []

    def clear_all_analysis(self):
        """Clear all existing analysis data and processed status from Redis."""
        try:
            # Clear all analysis result keys
            analysis_keys = self.client.keys('analysis:*')
            if analysis_keys:
                self.client.delete(*analysis_keys)
            
            # Clear processed article tracking so articles can be re-analyzed
            processed_keys = self.client.keys('processed:*')
            if processed_keys:
                self.client.delete(*processed_keys)
            
            logger.info(f"Cleared {len(analysis_keys)} analysis keys and {len(processed_keys)} processed trackers from Redis")
            return True
        except Exception as e:
            logger.error(f"Failed to clear analysis data: {e}")
            return False

    def get_binance_symbols(self):
        """Return a list of BINANCE USDT symbol strings and a details map.

        Looks for keys matching 'binance:usdt_symbols' first, then falls back to
        'BINANCE:SYMBOL:*' or 'BINANCE:*USDT' patterns for backward compatibility.
        """
        try:
            client = self.client
            symbols = []
            details = {}

            # Primary method: Get from the main symbols list
            symbols_data = client.get('binance:usdt_symbols')
            if symbols_data:
                try:
                    symbols_list = json.loads(symbols_data)
                    for symbol_data in symbols_list:
                        symbol = symbol_data['symbol']
                        symbols.append(symbol)
                        details[symbol] = symbol_data
                    symbols = sorted(list(set(symbols)))
                    return symbols, details
                except Exception as e:
                    logger.warning(f"Failed to parse binance:usdt_symbols: {e}")

            # Fallback: Original pattern used in this environment: BINANCE:SYMBOL:<SYMBOL>
            keys = client.keys('BINANCE:SYMBOL:*')
            if keys:
                for k in keys:
                    try:
                        # symbol part is after last ':'
                        sym = k.split(':')[-1].upper()
                        if sym.endswith('USDT'):
                            symbols.append(sym)
                            val = client.get(k)
                            try:
                                details[sym] = json.loads(val) if val else None
                            except Exception:
                                details[sym] = val
                    except Exception:
                        continue

            # Fallback: keys like BINANCE:<SYMBOL> or BINANCE:*USDT
            if not symbols:
                keys2 = client.keys('BINANCE:*USDT')
                for k in keys2:
                    try:
                        parts = k.split(':')
                        sym = parts[-1].upper()
                        if sym.endswith('USDT') and sym not in symbols:
                            symbols.append(sym)
                            val = client.get(k)
                            try:
                                details[sym] = json.loads(val) if val else None
                            except Exception:
                                details[sym] = val
                    except Exception:
                        continue

            symbols = sorted(list(set(symbols)))
            return symbols, details
        except Exception as e:
            logger.error(f"Failed to fetch BINANCE symbols from Redis: {e}")
            return [], {}
