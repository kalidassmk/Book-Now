import ccxt.async_support as ccxt
import asyncio
import logging

log = logging.getLogger("btc_filter.fetcher")

class DataFetcher:
    """
    Fetches synchronized kline data for target symbol and BTCUSDT using CCXT.
    """
    def __init__(self, apiKey=None, secret=None):
        self.client = ccxt.binance({
            'apiKey': apiKey,
            'secret': secret,
            'enableRateLimit': True,
            'options': {'defaultType': 'spot'}
        })

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
        try:
            ccxt_symbol = symbol if "/" in symbol else f"{symbol[:-4]}/{symbol[-4:]}"
            return await self.client.fetch_ohlcv(ccxt_symbol, timeframe=interval, limit=limit)
        except Exception as e:
            log.error(f"CCXT Request failed for {symbol}: {e}")
            return None

    async def close(self):
        await self.client.close()
