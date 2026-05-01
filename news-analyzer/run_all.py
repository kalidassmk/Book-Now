import asyncio
import uvicorn
import logging
import json
import sys

from api import app
from main import CryptoNewsAnalyzer
from fetch_binance_symbols import BinanceSymbolFetcher
from generate_summary_report import SummaryReportGenerator
from generate_buy_list import BuyListGenerator
from scraper.google_ai_scraper import GoogleAIScraper

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger("run_all")

async def run_analysis(mode, server, interval=15):
    analyzer = CryptoNewsAnalyzer()
    
    if mode == 'run-once':
        logger.info("Starting a complete analysis run...")
        
        try:
            # 0. Clear existing data
            logger.info("Step 0: Clearing existing analysis data and cache from Redis...")
            analyzer.redis_client.clear_all_analysis()
            
            # 1. Fetch symbols
            logger.info("Step 1: Fetching latest Binance USDT symbols...")
            fetcher = BinanceSymbolFetcher()
            fetcher.run()
            
            # 2. Run analysis cycle
            logger.info("Step 2: Running news analysis cycle...")
            await analyzer.run_analysis_cycle()
            
            # 3. Generate summary report
            logger.info("Step 3: Generating summary report for major coins...")
            report_generator = SummaryReportGenerator()
            await report_generator.generate_report(major_coins_only=True)
            
            # 4. Generate buy list
            logger.info("Step 4: Generating buy list based on analysis...")
            buy_generator = BuyListGenerator()
            buy_list = buy_generator.run()
            
            # 5. Deep Google AI Analysis for Buy List
            if buy_list:
                logger.info(f"Step 5: Performing Deep Google AI Analysis for {len(buy_list)} buy-list coins...")
                async with GoogleAIScraper() as ai_scraper:
                    for coin_symbol in buy_list:
                        coin = coin_symbol.replace('USDT', '')
                        ai_data = await ai_scraper.get_ai_sentiment(coin)
                        if ai_data:
                            analyzer.redis_client.client.set(f"analysis:{coin}:ai_data", json.dumps(ai_data))
                            logger.info(f"✅ Stored Google AI Data for {coin}")
            
            logger.info("======================================================")
            logger.info("✅ COMPLETE ANALYSIS RUN COMPLETED SUCCESSFULLY ✅")
            if buy_list:
                logger.info(f"💰 BUY RECOMMENDATIONS: {', '.join(buy_list)}")
            else:
                logger.info("ℹ️ No strong BUY signals found in this cycle.")
            logger.info("======================================================")
            logger.info("Process finished. Shutting down...")
        except Exception as e:
            logger.error(f"Analysis run failed: {e}")
        finally:
            # Signal the Uvicorn server to shutdown so the script exits
            if server:
                server.should_exit = True
    else:
        logger.info(f"Starting background analysis scheduler with interval {interval} minutes...")
        await analyzer.start_scheduler(interval_minutes=interval)
        try:
            while True:
                await asyncio.sleep(60)
        except asyncio.CancelledError:
            logger.info("Scheduler task cancelled, shutting down...")
            await analyzer.stop_scheduler()

async def run_all():
    # Configure uvicorn
    config = uvicorn.Config(app=app, host="0.0.0.0", port=8000, log_level="info")
    server = uvicorn.Server(config)
    
    mode = 'run-once'
    interval = 15
    if len(sys.argv) > 1:
        if sys.argv[1] == 'start':
            mode = 'start'
            if len(sys.argv) > 2:
                try:
                    interval = int(sys.argv[2])
                except ValueError:
                    logger.error("Invalid interval provided, using default 15")
        elif sys.argv[1] == 'run-once':
            mode = 'run-once'
    
    # Start the analysis task (either one-off or scheduled)
    analysis_task = asyncio.create_task(run_analysis(mode, server, interval))
    
    # Start uvicorn server
    logger.info("Starting FastAPI server on port 8000...")
    try:
        await server.serve()
    except asyncio.CancelledError:
        logger.info("Server task cancelled")
    finally:
        # Cancel analysis task when server stops
        analysis_task.cancel()
        try:
            await analysis_task
        except asyncio.CancelledError:
            pass
        logger.info("All services stopped.")

if __name__ == "__main__":
    try:
        asyncio.run(run_all())
    except KeyboardInterrupt:
        print("\nInterrupted by user, shutting down.")
