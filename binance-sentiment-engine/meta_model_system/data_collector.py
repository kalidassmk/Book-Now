import asyncio
import aiohttp
import logging
# Optional imports
try:
    import pandas as pd
    import numpy as np
    HAS_PANDAS = True
except ImportError:
    HAS_PANDAS = False

log = logging.getLogger("meta_model.collector")

class DataCollector:
    """
    Collects features from multiple trading sub-systems and Binance API.
    """
    BASE_URL = "https://api.binance.com"
    FUTURES_URL = "https://fapi.binance.com"

    def __init__(self, session: aiohttp.ClientSession):
        self.session = session

    async def fetch_all_features(self, symbol):
        """
        Gathers features across all categories.
        """
        # In a real system, these would call the respective engine classes
        # For this implementation, we aggregate essential market data
        try:
            klines = await self._fetch_klines(symbol)
            funding = await self._fetch_funding(symbol)
            oi = await self._fetch_oi(symbol)
            
            if not klines: return None
            
            # Use pandas for RSI if available, else simple fallback
            current_price = float(klines[-1][4])
            
            if HAS_PANDAS:
                df = pd.DataFrame(klines, columns=['t', 'o', 'h', 'l', 'c', 'v', 'ct', 'qv', 'tr', 'tbv', 'tbq', 'i'])
                df[['o','h','l','c','v']] = df[['o','h','l','c','v']].astype(float)
                rsi = self._calculate_rsi(df['c'])
                volatility = df['h'].iloc[-20:].max() - df['l'].iloc[-20:].min()
                volume_spike = df['v'].iloc[-1] / df['v'].iloc[-20:-1].mean()
            else:
                # Minimal fallback logic
                rsi = 50.0 # Neutral
                volatility = 0.0
                volume_spike = 1.0

            features = {
                "symbol": symbol,
                "price": current_price,
                "rsi": rsi,
                "price_change_5m": (current_price - float(klines[-2][4])) / float(klines[-2][4]) if len(klines) > 1 else 0.0,
                "funding_rate": float(funding[0]['fundingRate']) if funding else 0.0,
                "oi_change": (float(oi['openInterest']) - 0.0) / 1.0 if oi else 0.0,
                "volatility": volatility,
                "volume_spike": volume_spike
            }
            return features
        except Exception as e:
            log.error(f"Error collecting features for {symbol}: {e}")
            return None

    async def _fetch_klines(self, symbol):
        url = f"{self.BASE_URL}/api/v3/klines?symbol={symbol}&interval=5m&limit=100"
        async with self.session.get(url) as resp:
            return await resp.json() if resp.status == 200 else None

    async def _fetch_funding(self, symbol):
        url = f"{self.FUTURES_URL}/fapi/v1/fundingRate?symbol={symbol}&limit=1"
        async with self.session.get(url) as resp:
            return await resp.json() if resp.status == 200 else None

    async def _fetch_oi(self, symbol):
        url = f"{self.FUTURES_URL}/fapi/v1/openInterest?symbol={symbol}"
        async with self.session.get(url) as resp:
            return await resp.json() if resp.status == 200 else None

    async def fetch_historical_klines(self, symbol, interval="5m", limit=1000):
        """Fetches a larger batch of klines for training."""
        url = f"{self.BASE_URL}/api/v3/klines?symbol={symbol}&interval={interval}&limit={limit}"
        log.info(f"[{symbol}] Fetching {limit} historical klines for training...")
        async with self.session.get(url) as resp:
            if resp.status == 200:
                data = await resp.json()
                log.info(f"[{symbol}] Successfully fetched {len(data)} klines.")
                return data
            else:
                log.error(f"[{symbol}] Failed to fetch klines: HTTP {resp.status}")
                return None

    def _calculate_rsi(self, series, period=14):
        delta = series.diff()
        gain = (delta.where(delta > 0, 0)).rolling(window=period).mean()
        loss = (-delta.where(delta < 0, 0)).rolling(window=period).mean()
        rs = gain / loss
        return 100 - (100 / (1 + rs)).iloc[-1]
