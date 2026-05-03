"""
main.py
─────────────────────────────────────────────────────────────────────────────
Single entrypoint for the BookNow Python engine.

Run:
    python -m booknow.main
    # or, after `pip install -e python-engine/`:
    booknow

This module is the orchestrator that boots every async task in the
system. Each migration phase wires one more piece in here.

Today (Phase 4):
  - Phase 1: logging + config + rate-limit guard
  - Phase 4: user-data-stream service (only when BOOKNOW_LIVE_MODE=true).
             Pulls listenKey via WS-API, opens stream subscription,
             writes balance updates into Redis.

Pending phases will add: market WS consumer (Phase 5), filter/dust/delist
services (Phase 6), processors (Phase 8), trade state + monitor (Phase 9),
trade executor (Phase 10), rules (Phase 11), sentiment integration
(Phase 12), and the FastAPI HTTP layer (Phase 14).
"""

from __future__ import annotations

import asyncio
import logging
import signal
import sys
from typing import List

from booknow.binance.balances import BalanceService
from booknow.binance.delist import DelistService
from booknow.binance.dust import DustService
from booknow.binance.filters import FilterService
from booknow.binance.rate_limit import get_default as get_rate_limit_guard
from booknow.binance.rest_api import RestApiClient
from booknow.binance.user_data import UserDataStreamService
from booknow.binance.ws_api import WsApiClient
from booknow.binance.ws_streams import MarketStreamService
from booknow.config.settings import get_settings
from booknow.processors.fast_analyse import FastAnalyse
from booknow.processors.fast_move_filter import FastMoveFilter
from booknow.processors.time_analyser import TimeAnalyser
from booknow.processors.ulf_0_to_3 import UlfZeroToThree
from booknow.repository.redis_client import close_redis, get_redis


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

    guard = get_rate_limit_guard()
    log.info("  rate-limit guard ready (banned=%s)", guard.is_banned())

    # Redis client (lazy singleton). Touching it now so any connection
    # error surfaces during boot rather than at the first event.
    redis = get_redis()
    try:
        await redis.ping()
        log.info("  redis ping OK")
    except Exception as e:
        log.warning("  redis ping FAILED: %s — continuing; tasks may fail until Redis is up", e)

    # ── Phase 6: shared REST client + cache services ─────────────────
    rest = RestApiClient(
        api_key=settings.binance_api_key,
        secret_key=settings.binance_secret_key,
    )

    # FilterService + DelistService run regardless of live_mode — both
    # only hit public endpoints and the rest of the engine reads their
    # caches before placing any order.
    filter_service = FilterService(redis_client=redis, rest=rest)
    await filter_service.start()
    log.info("  filter-service task started")

    delist_service = DelistService(redis_client=redis, rest=rest)
    await delist_service.start()
    log.info("  delist-service task started (current set: %d symbols)",
             len(await delist_service.get_set()))

    # ── Phase 5: market data fan-in (always on, public stream) ───────
    # Pulls its delist set from DelistService so newly-announced removals
    # propagate without a restart.
    market_stream = MarketStreamService(
        redis_client=redis,
        delist=await delist_service.get_set(),
    )
    await market_stream.start()
    log.info("  market-stream task started")

    # ── Phase 8: four processor loops ────────────────────────────────
    # All four read what market_stream writes and emit derived signals
    # the rules engine (Phase 11) and dashboards consume.
    ulf            = UlfZeroToThree(redis_client=redis)
    fast_analyse   = FastAnalyse(redis_client=redis)
    time_analyser  = TimeAnalyser(redis_client=redis)
    fast_move      = FastMoveFilter(redis_client=redis)
    for proc in (ulf, fast_analyse, time_analyser, fast_move):
        await proc.start()
    log.info("  processors started: ulf_0_to_3, fast_analyse, time_analyser, fast_move_filter")

    # ── Phase 4 + dust: live-mode-only services ──────────────────────
    user_data: UserDataStreamService | None = None
    dust_service: DustService | None = None
    if settings.live_mode:
        if not (settings.binance_api_key and settings.binance_secret_key):
            log.error("  live_mode=True but Binance keys missing — user-data-stream + dust disabled")
        else:
            ws_api = WsApiClient(
                api_key=settings.binance_api_key,
                secret_key=settings.binance_secret_key,
            )
            balance_service = BalanceService(redis_client=redis, ws_api=ws_api)
            dust_service = DustService(
                redis_client=redis, rest=rest, filter_service=filter_service,
            )
            await dust_service.start()

            # User-data-stream pushes balance updates to BalanceService
            # AND triggers DustService's per-account dust evaluation.
            async def _on_balance_snapshot(balances):
                await dust_service.evaluate_balances(balances)

            user_data = UserDataStreamService(
                ws_api=ws_api,
                balance_service=balance_service,
            )
            # Wrap balance-service.apply_account_snapshot so we also
            # tee snapshots into the dust evaluator.
            original_apply = balance_service.apply_account_snapshot
            async def _apply_and_dust(balances):
                await original_apply(balances)
                await dust_service.evaluate_balances(balances)
            balance_service.apply_account_snapshot = _apply_and_dust  # type: ignore[assignment]

            await balance_service.seed_from_rest()
            await user_data.start()
            log.info("  user-data-stream + dust-service started")
    else:
        log.info("  user-data-stream + dust-service skipped (live_mode=False)")

    log.info("Engine running. Press Ctrl-C to stop.")

    # Idle until interrupted. Subsequent phases will spawn their own
    # tasks here; main.py's job is to supervise them and shut down
    # cleanly on SIGINT/SIGTERM.
    stop = asyncio.Event()
    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, stop.set)
        except NotImplementedError:
            pass  # Windows; KeyboardInterrupt will bubble.
    try:
        await stop.wait()
    finally:
        log.info("BookNow Python engine stopping…")
        # Stop processors first — they read what market_stream writes,
        # so we'd rather they stop polling than read mid-shutdown.
        for proc in (fast_move, time_analyser, fast_analyse, ulf):
            try:
                await proc.stop()
            except Exception as e:
                log.warning("  %s stop error: %s", proc.name, e)
        try:
            await market_stream.stop()
        except Exception as e:
            log.warning("  market-stream stop error: %s", e)
        if user_data is not None:
            try:
                await user_data.stop()
            except Exception as e:
                log.warning("  user-data-stream stop error: %s", e)
        if dust_service is not None:
            try:
                await dust_service.stop()
            except Exception as e:
                log.warning("  dust-service stop error: %s", e)
        try:
            await delist_service.stop()
        except Exception as e:
            log.warning("  delist-service stop error: %s", e)
        try:
            await filter_service.stop()
        except Exception as e:
            log.warning("  filter-service stop error: %s", e)
        try:
            await rest.aclose()
        except Exception:
            pass
        try:
            await close_redis()
        except Exception:
            pass
        log.info("BookNow Python engine stopped.")


def run() -> None:
    """Sync entrypoint exposed via the `booknow` console script."""
    try:
        asyncio.run(_bootstrap())
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    run()
