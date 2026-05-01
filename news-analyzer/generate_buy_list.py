#!/usr/bin/env python3
"""
Generate Buy List based on News Analysis.

This script scans all detailed news analysis in Redis and identifies
coins that have a 'BUY' signal. It stores the final list of buyable
symbols in Redis for use by the trading bot and dashboard.

Usage:
    python generate_buy_list.py
"""

import json
import logging
from redis_client import RedisClient

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

class BuyListGenerator:
    def __init__(self):
        self.redis_client = RedisClient()
        self.buy_list_key = 'news:buy_list'
        self.buy_list_detailed_key = 'news:buy_list:detailed'

    def run(self, min_score=0.15):
        """
        Generate and store the buy list.
        
        Args:
            min_score: Minimum final_score to be included in the buy list.
        """
        logger.info(f"Generating buy list (min_score: {min_score})...")
        
        coins = self.redis_client.get_all_analysis_keys()
        if not coins:
            logger.warning("No analysis data found in Redis. Run analysis first.")
            return []

        buy_symbols = []
        buy_details = []

        for coin in coins:
            try:
                analysis = self.redis_client.get_detailed_analysis(coin)
                if not analysis:
                    continue

                decision = analysis.get('decision', 'HOLD')
                score = analysis.get('score', analysis.get('final_score', 0))

                # Criteria for "Can Buy"
                if decision == 'BUY' and score >= min_score:
                    symbol = f"{coin}USDT"
                    buy_symbols.append(symbol)
                    
                    buy_details.append({
                        'symbol': symbol,
                        'coin': coin,
                        'score': score,
                        'recommendation': analysis.get('recommendation', 'Bullish'),
                        'timestamp': analysis.get('timestamp')
                    })
                    logger.info(f"✅ Adding {symbol} to Buy List (Score: {score:.2f})")

            except Exception as e:
                logger.error(f"Error processing {coin}: {e}")

        # Store in Redis
        try:
            # Store simple list of symbols
            self.redis_client.client.set(self.buy_list_key, json.dumps(buy_symbols))
            
            # Store detailed list
            self.redis_client.client.set(self.buy_list_detailed_key, json.dumps(buy_details))
            
            logger.info(f"Successfully stored {len(buy_symbols)} symbols in {self.buy_list_key}")
            return buy_symbols
            
        except Exception as e:
            logger.error(f"Failed to store buy list in Redis: {e}")
            return []

def main():
    generator = BuyListGenerator()
    buy_list = generator.run()
    
    print("\n" + "="*40)
    print(f"📊 NEWS-BASED BUY LIST ({len(buy_list)} symbols)")
    print("="*40)
    if buy_list:
        for symbol in buy_list:
            print(f"  - {symbol}")
    else:
        print("  (No buy signals found based on current analysis)")
    print("="*40)

if __name__ == "__main__":
    main()
