#!/usr/bin/env python3
"""
Regime Trader — Adaptive crypto trading system with market regime detection.

Usage:
  python regime_trader_main.py --symbol BTCUSDT --interval 5m
  python regime_trader_main.py --symbol ETHUSDT --interval 15m --live

Arguments:
  --symbol       Trading pair (default: BTCUSDT)
  --interval     Kline interval: 1m, 3m, 5m, 15m, 1h, 4h, 1d (default: 5m)
  --period        Indicator period (default: 14)
  --amount       Trade amount in USDT (default: 12)
  --live         Enable live trading (default: paper mode)
  --api-key      Binance API key (or set BINANCE_API_KEY env var)
  --api-secret   Binance API secret (or set BINANCE_SECRET_KEY env var)
  --redis-host   Redis host (default: 127.0.0.1)
  --redis-port   Redis port (default: 6379)
"""

import os
import sys
import signal
import argparse
import logging

# ── Logging Setup ─────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s │ %(levelname)-7s │ %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger("regime_trader")


def main():
    parser = argparse.ArgumentParser(
        description="Regime Trader — Adaptive crypto trading with market regime detection",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  # Paper trading BTCUSDT on 5m candles
  python regime_trader_main.py --symbol BTCUSDT --interval 5m

  # Paper trading ETHUSDT on 15m candles
  python regime_trader_main.py --symbol ETHUSDT --interval 15m

  # Live trading (requires API keys)
  python regime_trader_main.py --symbol BTCUSDT --interval 5m --live \\
      --api-key YOUR_KEY --api-secret YOUR_SECRET
        """,
    )
    parser.add_argument("--symbol", default="BTCUSDT", help="Trading pair (default: BTCUSDT)")
    parser.add_argument("--interval", default="5m", help="Kline interval (default: 5m)")
    parser.add_argument("--period", type=int, default=14, help="Indicator period (default: 14)")
    parser.add_argument("--amount", type=float, default=12.0, help="Trade amount in USDT (default: 12)")
    parser.add_argument("--live", action="store_true", help="Enable live trading")
    parser.add_argument("--api-key", default=os.getenv("BINANCE_API_KEY", ""), help="Binance API key")
    parser.add_argument("--api-secret", default=os.getenv("BINANCE_SECRET_KEY", ""), help="Binance API secret")
    parser.add_argument("--redis-host", default="127.0.0.1", help="Redis host")
    parser.add_argument("--redis-port", type=int, default=6379, help="Redis port")

    args = parser.parse_args()

    # Validate live mode requirements
    if args.live and (not args.api_key or not args.api_secret):
        log.error("❌ Live trading requires --api-key and --api-secret (or BINANCE_API_KEY/BINANCE_SECRET_KEY env vars)")
        sys.exit(1)

    # Create engine
    from regime_trader import RegimeTraderEngine

    engine = RegimeTraderEngine(
        symbol=args.symbol,
        interval=args.interval,
        indicator_period=args.period,
        api_key=args.api_key,
        api_secret=args.api_secret,
        live_trading=args.live,
        trade_amount_usdt=args.amount,
        redis_host=args.redis_host,
        redis_port=args.redis_port,
    )

    # Graceful shutdown
    def shutdown(signum, frame):
        log.info("\n⏹  Shutting down gracefully...")
        engine.stop()
        sys.exit(0)

    signal.signal(signal.SIGINT, shutdown)
    signal.signal(signal.SIGTERM, shutdown)

    # Start
    engine.start()


if __name__ == "__main__":
    main()
