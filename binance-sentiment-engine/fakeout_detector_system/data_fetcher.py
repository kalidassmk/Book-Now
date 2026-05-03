import ccxt.async_support as ccxt
import logging

log = logging.getLogger("fakeout.fetcher")

class DataFetcher:
    """
    Fetches kline data from Binance using CCXT.
    """
    def __init__(self, apiKey=None, secret=None):
        self.client = ccxt.binance({
            'apiKey': apiKey,
            'secret': secret,
            'enableRateLimit': True,
            'options': {'defaultType': 'spot'}
        })

    async def fetch_klines(self, symbol, interval="5m", limit=200):
        try:
            # CCXT expects symbol with slash (e.g. BTC/USDT)
            ccxt_symbol = symbol if "/" in symbol else f"{symbol[:-4]}/{symbol[-4:]}"
            
            klines = await self.client.fetch_ohlcv(ccxt_symbol, timeframe=interval, limit=limit)
            return klines
        except Exception as e:
            log.error(f"CCXT Request failed for {symbol}: {e}")
            return None

    async def close(self):
        await self.client.close()
