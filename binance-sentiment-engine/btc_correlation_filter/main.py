import asyncio
import aiohttp
import logging
import json
import redis
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))
from symbols_config import BTC_FILTER_SYMBOLS
from data_fetcher import DataFetcher
from correlation import CorrelationEngine
from btc_filter import BTCFilter
from strategy_filter import StrategyFilter

# Logging Setup
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-7s | %(name)s | %(message)s",
)
log = logging.getLogger("btc_filter.main")

class BTCFilterBot:
    def __init__(self, symbols=None, interval_sec=60):
        if symbols is None:
            symbols = BTC_FILTER_SYMBOLS  # All alts (BTC excluded)
        self.symbols = symbols
        self.interval_sec = interval_sec
        self.redis_client = redis.Redis(host='127.0.0.1', port=6379, decode_responses=True)

    async def run(self):
        log.info(f"🚀 Starting BTC Correlation Filter for: {self.symbols}")
        connector = aiohttp.TCPConnector(ssl=False)
        async with aiohttp.ClientSession(connector=connector) as session:
            fetcher = DataFetcher(session)
            while True:
                tasks = [self.process_symbol(fetcher, symbol) for symbol in self.symbols]
                await asyncio.gather(*tasks)
                log.info(f"Filter cycle complete. Waiting {self.interval_sec}s...")
                await asyncio.sleep(self.interval_sec)

    async def process_symbol(self, fetcher, symbol):
        try:
            # 1. Fetch Parallel Data (Alt + BTC)
            alt_klines, btc_klines = await fetcher.fetch_pair(symbol)
            if not alt_klines or not btc_klines: return

            # 2. Calculate Correlation
            correlation = CorrelationEngine.calculate(alt_klines, btc_klines)

            # 3. Analyze BTC Context
            btc_trend, btc_score = BTCFilter.analyze(btc_klines)

            # 4. Apply Strategy Filters
            filter_data = StrategyFilter.apply(symbol, btc_trend, btc_score, correlation)

            # 5. Store in Redis
            self.redis_client.hset("BTC_CORRELATION_FILTERS", symbol, json.dumps(filter_data))
            
            status_icon = "✅" if filter_data['trade_allowed'] else "❌"
            log.info(f"{status_icon} [{symbol}] Corr: {correlation} | BTC: {btc_trend} ({btc_score}) | Allowed: {filter_data['trade_allowed']}")

        except Exception as e:
            log.error(f"Error filtering {symbol}: {e}", exc_info=True)

if __name__ == "__main__":
    bot = BTCFilterBot()
    try:
        asyncio.run(bot.run())
    except KeyboardInterrupt:
        log.info("Filter stopped.")
