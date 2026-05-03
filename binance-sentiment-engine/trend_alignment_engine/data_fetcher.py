import ccxt.async_support as ccxt
import asyncio
import logging

log = logging.getLogger("trend_alignment.fetcher")

class DataFetcher:
    """
    Fetches multi-timeframe kline data from Binance using CCXT.
    """
    def __init__(self, apiKey=None, secret=None):
        self.client = ccxt.binance({
            'apiKey': apiKey,
            'secret': secret,
            'enableRateLimit': True,
            'options': {'defaultType': 'spot'}
        })

    async def fetch_timeframe(self, symbol, interval, limit=100):
        try:
            ccxt_symbol = symbol if "/" in symbol else f"{symbol[:-4]}/{symbol[-4:]}"
            data = await self.client.fetch_ohlcv(ccxt_symbol, timeframe=interval, limit=limit)
            return interval, data
        except Exception as e:
            log.error(f"CCXT Request failed for {symbol} @ {interval}: {e}")
            return interval, None

    async def fetch_multi_timeframe(self, symbol, intervals=["5m", "15m", "1h", "4h", "1d", "1w"]):
        tasks = [self.fetch_timeframe(symbol, interval) for interval in intervals]
        results = await asyncio.gather(*tasks)
        return {interval: data for interval, data in results if data}

    async def close(self):
        await self.client.close()
