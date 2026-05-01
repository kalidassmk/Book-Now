#!/usr/bin/env python3
"""
Fetch Binance USDT trading symbols and store in Redis.

This script fetches all available trading pairs from Binance API,
filters for USDT pairs, and stores them in Redis for use by the
news-analyzer project.

Usage:
    python fetch_binance_symbols.py

Requirements:
    - requests
    - redis
"""

import requests
import json
import logging
import sys
from redis_client import RedisClient

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

class BinanceSymbolFetcher:
    def __init__(self):
        self.base_url = "https://api.binance.com/api/v3"
        self.redis_client = RedisClient()

    def fetch_exchange_info(self):
        """Fetch exchange information from Binance API."""
        try:
            url = f"{self.base_url}/exchangeInfo"
            logger.info("Fetching exchange info from Binance API...")
            response = requests.get(url, timeout=30)
            response.raise_for_status()
            return response.json()
        except requests.exceptions.RequestException as e:
            logger.error(f"Failed to fetch exchange info: {e}")
            return None

    def extract_usdt_symbols(self, exchange_info):
        """Extract USDT trading pairs from exchange info."""
        if not exchange_info or 'symbols' not in exchange_info:
            logger.error("Invalid exchange info response")
            return []

        usdt_symbols = []
        for symbol_info in exchange_info['symbols']:
            symbol = symbol_info['symbol']
            if symbol.endswith('USDT') and symbol_info['status'] == 'TRADING':
                # Extract base asset (remove USDT suffix)
                base_asset = symbol[:-4]  # Remove 'USDT'
                usdt_symbols.append({
                    'symbol': symbol,
                    'base_asset': base_asset,
                    'quote_asset': 'USDT',
                    'status': symbol_info['status'],
                    'base_asset_precision': symbol_info.get('baseAssetPrecision', 8),
                    'quote_asset_precision': symbol_info.get('quoteAssetPrecision', 8),
                    'min_qty': symbol_info.get('filters', [{}])[1].get('minQty', '0.00000001'),
                    'max_qty': symbol_info.get('filters', [{}])[1].get('maxQty', '10000000'),
                    'step_size': symbol_info.get('filters', [{}])[1].get('stepSize', '0.00000001')
                })

        logger.info(f"Found {len(usdt_symbols)} active USDT trading pairs")
        return usdt_symbols

    def store_symbols_in_redis(self, symbols):
        """Store symbols in Redis with multiple key structures."""
        try:
            # Store as a single JSON array for easy retrieval
            self.redis_client.client.set('binance:usdt_symbols', json.dumps(symbols))

            # Store individual symbol details
            for symbol_data in symbols:
                symbol = symbol_data['symbol']
                # Store under BINANCE:SYMBOL:<SYMBOL> pattern
                key = f"BINANCE:SYMBOL:{symbol}"
                self.redis_client.client.set(key, json.dumps(symbol_data))

                # Also store under BINANCE:<SYMBOL> for compatibility
                key2 = f"BINANCE:{symbol}"
                self.redis_client.client.set(key2, json.dumps(symbol_data))

            # Store metadata
            metadata = {
                'total_symbols': len(symbols),
                'last_updated': json.dumps({'timestamp': None, 'count': len(symbols)}),
                'source': 'binance_api'
            }
            self.redis_client.client.set('binance:metadata', json.dumps(metadata))

            logger.info(f"Stored {len(symbols)} symbols in Redis")
            return True

        except Exception as e:
            logger.error(f"Failed to store symbols in Redis: {e}")
            return False

    def get_stored_symbols_count(self):
        """Get count of stored symbols."""
        try:
            data = self.redis_client.client.get('binance:usdt_symbols')
            if data:
                symbols = json.loads(data)
                return len(symbols)
            return 0
        except Exception as e:
            logger.error(f"Failed to get stored symbols count: {e}")
            return 0

    def run(self):
        """Main execution method."""
        logger.info("Starting Binance USDT symbols fetch...")

        # Fetch exchange info
        exchange_info = self.fetch_exchange_info()
        if not exchange_info:
            logger.error("Failed to fetch exchange info. Exiting.")
            return False

        # Extract USDT symbols
        symbols = self.extract_usdt_symbols(exchange_info)
        if not symbols:
            logger.error("No USDT symbols found. Exiting.")
            return False

        # Store in Redis
        success = self.store_symbols_in_redis(symbols)
        if not success:
            logger.error("Failed to store symbols in Redis. Exiting.")
            return False

        # Verify storage
        stored_count = self.get_stored_symbols_count()
        logger.info(f"Verification: {stored_count} symbols stored in Redis")

        # Show sample symbols
        sample_symbols = [s['symbol'] for s in symbols[:10]]
        logger.info(f"Sample symbols: {', '.join(sample_symbols)}")

        logger.info("Binance USDT symbols fetch completed successfully!")
        return True

def main():
    """Main entry point."""
    fetcher = BinanceSymbolFetcher()
    success = fetcher.run()

    if success:
        print("\n✅ Successfully fetched and stored Binance USDT symbols!")
        print("You can now use these symbols in your news-analyzer project.")
        print("\nTo verify, run:")
        print("  python inspect_binance_redis.py")
        print("  python -c \"from redis_client import RedisClient; rc = RedisClient(); symbols, _ = rc.get_binance_symbols(); print(f'Total symbols: {len(symbols)}')\"")
    else:
        print("\n❌ Failed to fetch and store Binance USDT symbols.")
        sys.exit(1)

if __name__ == "__main__":
    main()
