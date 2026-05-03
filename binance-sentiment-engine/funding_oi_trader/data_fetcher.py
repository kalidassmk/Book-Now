import ccxt.async_support as ccxt
import asyncio
import logging
import time

log = logging.getLogger("funding_oi.fetcher")

class DataFetcher:
    """
    Fetches market data from Binance Futures using CCXT.
    """
    def __init__(self, apiKey=None, secret=None):
        self.client = ccxt.binance({
            'apiKey': apiKey,
            'secret': secret,
            'enableRateLimit': True,
            'options': {'defaultType': 'swap'} # Perpetuals
        })

    async def fetch_klines(self, symbol, interval="5m", limit=100):
        try:
            ccxt_symbol = symbol if "/" in symbol else f"{symbol[:-4]}/{symbol[-4:]}"
            return await self.client.fetch_ohlcv(ccxt_symbol, timeframe=interval, limit=limit)
        except Exception as e:
            log.error(f"Error fetching klines for {symbol}: {e}")
            return None

    async def fetch_funding_rate(self, symbol, limit=1):
        try:
            ccxt_symbol = symbol if "/" in symbol else f"{symbol[:-4]}/{symbol[-4:]}"
            # CCXT fetch_funding_rate returns recent funding rate
            rates = await self.client.fetch_funding_rate_history(ccxt_symbol, limit=limit)
            return rates
        except Exception as e:
            log.error(f"Error fetching funding rate for {symbol}: {e}")
            return None

    async def fetch_open_interest(self, symbol):
        try:
            ccxt_symbol = symbol if "/" in symbol else f"{symbol[:-4]}/{symbol[-4:]}"
            return await self.client.fetch_open_interest(ccxt_symbol)
        except Exception as e:
            log.error(f"Error fetching OI for {symbol}: {e}")
            return None

    async def fetch_open_interest_hist(self, symbol, interval="5m", limit=30):
        try:
            ccxt_symbol = symbol if "/" in symbol else f"{symbol[:-4]}/{symbol[-4:]}"
            return await self.client.fetch_open_interest_history(ccxt_symbol, timeframe=interval, limit=limit)
        except Exception as e:
            log.error(f"Error fetching OI history for {symbol}: {e}")
            return None

    async def close(self):
        await self.client.close()
