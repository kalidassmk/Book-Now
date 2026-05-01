import aiohttp
import asyncio
import logging
import time

log = logging.getLogger("funding_oi.fetcher")

class DataFetcher:
    """
    Fetches market data from Binance Futures API (fapi).
    """
    BASE_URL = "https://fapi.binance.com"

    def __init__(self, session: aiohttp.ClientSession):
        self.session = session

    async def fetch_klines(self, symbol, interval="5m", limit=100):
        url = f"{self.BASE_URL}/fapi/v1/klines"
        params = {"symbol": symbol.upper(), "interval": interval, "limit": limit}
        async with self.session.get(url, params=params) as resp:
            return await resp.json()

    async def fetch_funding_rate(self, symbol, limit=24):
        url = f"{self.BASE_URL}/fapi/v1/fundingRate"
        params = {"symbol": symbol.upper(), "limit": limit}
        async with self.session.get(url, params=params) as resp:
            return await resp.json()

    async def fetch_open_interest(self, symbol):
        url = f"{self.BASE_URL}/fapi/v1/openInterest"
        params = {"symbol": symbol.upper()}
        async with self.session.get(url, params=params) as resp:
            return await resp.json()

    async def fetch_open_interest_hist(self, symbol, interval="5m", limit=30):
        # Note: Open interest history has a different endpoint
        url = f"{self.BASE_URL}/futures/data/openInterestHist"
        params = {"symbol": symbol.upper(), "period": interval, "limit": limit}
        async with self.session.get(url, params=params) as resp:
            return await resp.json()
