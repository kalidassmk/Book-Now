import aiohttp
import asyncio
import logging

log = logging.getLogger("btc_filter.fetcher")

class DataFetcher:
    """
    Fetches synchronized kline data for target symbol and BTCUSDT.
    """
    BASE_URL = "https://api.binance.com"

    def __init__(self, session: aiohttp.ClientSession):
        self.session = session

    async def fetch_pair(self, symbol, interval="15m", limit=100):
        """
        Fetches both the target symbol and BTC in parallel.
        """
        tasks = [
            self._fetch_klines(symbol, interval, limit),
            self._fetch_klines("BTCUSDT", interval, limit)
        ]
        results = await asyncio.gather(*tasks)
        return results[0], results[1]

    async def _fetch_klines(self, symbol, interval, limit):
        url = f"{self.BASE_URL}/api/v3/klines"
        params = {
            "symbol": symbol.upper(),
            "interval": interval,
            "limit": limit
        }
        try:
            async with self.session.get(url, params=params) as resp:
                if resp.status == 200:
                    return await resp.json()
                else:
                    log.error(f"Error fetching {symbol}: {resp.status}")
                    return None
        except Exception as e:
            log.error(f"Request failed for {symbol}: {e}")
            return None
