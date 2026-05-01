import aiohttp
import asyncio
import logging

log = logging.getLogger("trend_alignment.fetcher")

class DataFetcher:
    """
    Fetches multi-timeframe kline data from Binance REST API in parallel.
    """
    BASE_URL = "https://api.binance.com"

    def __init__(self, session: aiohttp.ClientSession):
        self.session = session

    async def fetch_timeframe(self, symbol, interval, limit=100):
        url = f"{self.BASE_URL}/api/v3/klines"
        params = {
            "symbol": symbol.upper(),
            "interval": interval,
            "limit": limit
        }
        try:
            async with self.session.get(url, params=params) as resp:
                if resp.status == 200:
                    data = await resp.json()
                    return interval, data
                else:
                    log.error(f"Error fetching {interval} for {symbol}: {resp.status}")
                    return interval, None
        except Exception as e:
            log.error(f"Request failed for {interval}: {e}")
            return interval, None

    async def fetch_multi_timeframe(self, symbol, intervals=["5m", "15m", "1h", "4h", "1d", "1w"]):
        tasks = [self.fetch_timeframe(symbol, interval) for interval in intervals]
        results = await asyncio.gather(*tasks)
        return {interval: data for interval, data in results if data}
