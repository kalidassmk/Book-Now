import aiohttp
import logging

log = logging.getLogger("volume_profile.fetcher")

class DataFetcher:
    """
    Fetches kline data from Binance REST API.
    """
    BASE_URL = "https://api.binance.com"

    def __init__(self, session: aiohttp.ClientSession):
        self.session = session

    async def fetch_klines(self, symbol, interval="5m", limit=500):
        url = f"{self.BASE_URL}/api/v3/klines"
        params = {
            "symbol": symbol.upper(),
            "interval": interval,
            "limit": limit
        }
        async with self.session.get(url, params=params) as resp:
            if resp.status == 200:
                return await resp.json()
            else:
                log.error(f"Error fetching klines: {resp.status}")
                return None
