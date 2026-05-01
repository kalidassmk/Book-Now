#!/usr/bin/env python3
"""
Analyze Once: Complete News-to-Buy-List Pipeline

This script runs the entire news analysis pipeline exactly once:
1. Clears existing data and cache.
2. Fetches latest Binance USDT symbols.
3. Performs news analysis for all symbols.
4. Generates a list of buyable coins based on sentiment.
5. Exits cleanly.

No server is started. This is ideal for scheduled tasks or manual runs.
"""

import asyncio
import logging
import sys
from main import CryptoNewsAnalyzer
from fetch_binance_symbols import BinanceSymbolFetcher
from generate_buy_list import BuyListGenerator
from generate_summary_report import SummaryReportGenerator

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler('news_analyzer_batch.log')
    ]
)
logger = logging.getLogger("analyze_once")

async def run_batch_pipeline():
    logger.info("======================================================")
    logger.info("🚀 STARTING BATCH NEWS ANALYSIS PIPELINE 🚀")
    logger.info("======================================================")
    
    analyzer = CryptoNewsAnalyzer()
    
    try:
        # Step 0: Clear data
        logger.info("\n[Step 0] Clearing existing data from Redis...")
        analyzer.redis_client.clear_all_analysis()
        
        # Step 1: Fetch Symbols
        logger.info("\n[Step 1] Fetching latest Binance USDT symbols...")
        fetcher = BinanceSymbolFetcher()
        fetcher.run()
        
        # Step 2: Run Analysis Cycle
        logger.info("\n[Step 2] Running news analysis cycle for all symbols...")
        await analyzer.run_analysis_cycle()
        
        # Step 3: Generate Summary Report
        logger.info("\n[Step 3] Generating summary report...")
        report_generator = SummaryReportGenerator()
        await report_generator.generate_report(major_coins_only=True)
        
        # Step 4: Generate Buy List
        logger.info("\n[Step 4] Finalizing buy decisions...")
        buy_generator = BuyListGenerator()
        buy_list = buy_generator.run()
        
        logger.info("\n" + "="*50)
        logger.info("✅ BATCH ANALYSIS COMPLETED SUCCESSFULLY ✅")
        logger.info(f"💰 Found {len(buy_list)} symbols with BUY signals.")
        logger.info("="*50)
        
        if buy_list:
            print("\nRecommended Symbols to Buy:")
            for sym in buy_list:
                print(f"  - {sym}")
        
    except Exception as e:
        logger.error(f"\n❌ Pipeline failed: {e}")
        sys.exit(1)

if __name__ == "__main__":
    try:
        asyncio.run(run_batch_pipeline())
    except KeyboardInterrupt:
        logger.info("\nStopped by user.")
