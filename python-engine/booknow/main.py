"""
main.py
─────────────────────────────────────────────────────────────────────────────
Single entrypoint for the BookNow Python engine.

Run:
    python -m booknow.main
    # or, after `pip install -e python-engine/`:
    booknow

This module is the orchestrator that will eventually boot every async
task in the system: market WS consumer, user-data-stream, trade
executor, processors, rules, position monitor, sentiment analyzers,
and the FastAPI HTTP layer. Each subsequent migration phase wires one
more piece in here.

Today (Phase 1) it just sets up logging + config + the rate-limit
guard, then idles. Verifies the skeleton boots cleanly. No I/O.
"""

from __future__ import annotations

import asyncio
import logging
import signal
import sys

from booknow.binance.rate_limit import get_default as get_rate_limit_guard
from booknow.config.settings import get_settings


def _configure_logging(debug: bool) -> None:
    logging.basicConfig(
        level=logging.DEBUG if debug else logging.INFO,
        format="%(asctime)s | %(levelname)-7s | %(name)s | %(message)s",
        datefmt="%H:%M:%S",
        stream=sys.stdout,
    )
    # Quiet down noisy libraries until we actually need their detail.
    for name in ("websockets", "asyncio", "urllib3", "httpx"):
        logging.getLogger(name).setLevel(logging.WARNING)


async def _bootstrap() -> None:
    settings = get_settings()
    _configure_logging(settings.debug)
    log = logging.getLogger("booknow.main")

    log.info("BookNow Python engine starting…")
    log.info(
        "  live_mode=%s  http_port=%d  redis=%s:%d  debug=%s",
        settings.live_mode, settings.http_port,
        settings.redis_host, settings.redis_port, settings.debug,
    )
    if not settings.binance_api_key:
        log.warning("  BINANCE_API_KEY is not set — trading endpoints will fail until configured.")

    # Touching the rate-limit guard now so its singleton is created on the
    # main thread. Subsequent phases will hand it to every Binance caller.
    guard = get_rate_limit_guard()
    log.info("  rate-limit guard ready (banned=%s)", guard.is_banned())

    log.info("Phase 1 skeleton up. No tasks are running yet — see migration plan.")
    log.info("Press Ctrl-C to exit cleanly.")

    # Idle until interrupted. Subsequent phases replace this with a real
    # supervisor that spawns and watches every async task.
    stop = asyncio.Event()
    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, stop.set)
        except NotImplementedError:
            # Windows; fall back to letting KeyboardInterrupt bubble.
            pass
    try:
        await stop.wait()
    finally:
        log.info("BookNow Python engine stopped.")


def run() -> None:
    """Sync entrypoint exposed via the `booknow` console script."""
    try:
        asyncio.run(_bootstrap())
    except KeyboardInterrupt:
        # Ctrl-C before the signal handler is wired (rare).
        pass


if __name__ == "__main__":
    run()
