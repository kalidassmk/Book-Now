import asyncio
import logging
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.interval import IntervalTrigger
import time

logger = logging.getLogger(__name__)

class NewsAnalysisScheduler:
    def __init__(self, analyzer):
        self.analyzer = analyzer
        self.scheduler = AsyncIOScheduler()
        self.is_running = False

    async def start(self, interval_minutes=15):
        """Start the scheduler with specified interval."""
        if self.is_running:
            logger.warning("Scheduler is already running")
            return

        # Add job to run analysis
        self.scheduler.add_job(
            self._run_analysis_job,
            trigger=IntervalTrigger(minutes=interval_minutes),
            id='news_analysis',
            name='Cryptocurrency News Analysis',
            max_instances=1
        )

        self.scheduler.start()
        self.is_running = True
        logger.info(f"Scheduler started with {interval_minutes} minute intervals")

        # Run initial analysis
        await self._run_analysis_job()

    async def stop(self):
        """Stop the scheduler."""
        if self.is_running:
            self.scheduler.shutdown(wait=True)
            self.is_running = False
            logger.info("Scheduler stopped")

    async def _run_analysis_job(self):
        """Run the complete news analysis cycle."""
        try:
            start_time = time.time()
            logger.info("Starting news analysis cycle")

            await self.analyzer.run_analysis_cycle()

            duration = time.time() - start_time
            logger.info(f"News analysis cycle completed in {duration:.2f} seconds")

        except Exception as e:
            logger.error(f"Error in analysis cycle: {e}")

    async def run_once(self):
        """Run analysis once without scheduling."""
        await self._run_analysis_job()

    def get_status(self):
        """Get scheduler status."""
        return {
            'running': self.is_running,
            'jobs': len(self.scheduler.get_jobs()) if self.is_running else 0
        }
