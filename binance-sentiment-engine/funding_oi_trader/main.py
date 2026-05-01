import asyncio
import aiohttp
import logging
import json
import redis
import time
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
from symbols_config import ACTIVE_SYMBOLS
from data_fetcher import DataFetcher
from indicators import IndicatorCalculator
from strategy import FundingOIStrategy

# Logging Setup
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-7s | %(name)s | %(message)s",
)
log = logging.getLogger("funding_oi.main")

class FundingOIBot:
    def __init__(self, symbols=None, interval_sec=60):
        if symbols is None:
            symbols = ACTIVE_SYMBOLS
        self.symbols = symbols
        self.interval_sec = interval_sec
        self.strategy = FundingOIStrategy()
        self.redis_client = redis.Redis(host='127.0.0.1', port=6379, decode_responses=True)

    async def run(self):
        log.info(f"🚀 Starting Funding/OI Bot for symbols: {self.symbols}")
        connector = aiohttp.TCPConnector(ssl=False)
        async with aiohttp.ClientSession(connector=connector) as session:
            fetcher = DataFetcher(session)
            while True:
                tasks = [self.process_symbol(fetcher, symbol) for symbol in self.symbols]
                await asyncio.gather(*tasks)
                log.info(f"Sleeping for {self.interval_sec}s...")
                await asyncio.sleep(self.interval_sec)

    async def process_symbol(self, fetcher, symbol):
        try:
            # 1. Fetch Data
            klines, oi_hist, funding_data = await asyncio.gather(
                fetcher.fetch_klines(symbol),
                fetcher.fetch_open_interest_hist(symbol),
                fetcher.fetch_funding_rate(symbol, limit=1)
            )

            if not klines or not oi_hist or not funding_data:
                log.error(f"Missing data for {symbol}")
                return

            # 2. Compute Metrics
            price, trend, trend_strength = IndicatorCalculator.calculate_price_trend(klines)
            oi, oi_change = IndicatorCalculator.calculate_oi_change(oi_hist)
            funding_status, funding_rate = IndicatorCalculator.normalize_funding(funding_data[0]['fundingRate'])

            # 3. Evaluate Strategy
            signal_data = self.strategy.evaluate(
                symbol, price, trend, trend_strength, oi_change, funding_status, funding_rate
            )

            # 4. Output & Store in Redis
            log.info(f"[{symbol}] Price: {price} | Trend: {trend} | Signal: {signal_data['signal']} | Reason: {signal_data['reason']}")
            
            # Save to Redis
            self.redis_client.hset("FUNDING_OI_SIGNALS", symbol, json.dumps(signal_data))
            self.redis_client.set(f"raw:funding:{symbol}", json.dumps(funding_data))
            
        except Exception as e:
            log.error(f"Error processing {symbol}: {e}", exc_info=True)

if __name__ == "__main__":
    bot = FundingOIBot()  # uses ACTIVE_SYMBOLS from symbols_config.py
    try:
        asyncio.run(bot.run())
    except KeyboardInterrupt:
        log.info("Bot stopped by user.")
