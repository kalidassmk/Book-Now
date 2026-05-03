#!/usr/bin/env python3
"""
volume_price_analyzer.py
─────────────────────────────────────────────────────────────────────────────
Multi-Timeframe Volume & Price Trading Algorithm for Binance.

Analyzes 12 timeframes (5m → 1M) to produce a 0–100 BUY score.
Uses direct Binance REST API (no ccxt dependency for data fetching).

Usage:
  python3 volume_price_analyzer.py --symbol KITE/USDT
  python3 volume_price_analyzer.py --scan
  python3 volume_price_analyzer.py --symbols BTC/USDT ETH/USDT SOL/USDT
─────────────────────────────────────────────────────────────────────────────
"""

import time
import json
import logging
import requests
import redis
import pandas as pd
import numpy as np
import urllib3
from datetime import datetime
from typing import Dict, List, Optional, Tuple

# Disable SSL warnings
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# ─── Logging ──────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s │ %(levelname)-7s │ %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger("VolumePriceAnalyzer")

# ═══════════════════════════════════════════════════════════════════════════════
#  CONFIGURATION
# ═══════════════════════════════════════════════════════════════════════════════

BINANCE_BASE = "https://api.binance.com"

# (display_name, binance_interval, candle_limit, weight, category)
TIMEFRAME_CONFIG = [
    ("1m",  "1m",  60,  0.25, "micro"), # High weight for micro-trend
    ("5m",  "5m",  60,  0.15, "short"),
    ("15m", "15m", 48,  0.10, "short"),
    ("1h",  "1h",  48,  0.10, "short"),
    ("4h",  "4h",  48,  0.10, "mid"),
    ("1d",  "1d",  60,  0.10, "long"),
    ("1w",  "1w",  30,  0.10, "long"),
    ("1M",  "1M",  24,  0.10, "long"),
]

BUY_THRESHOLD  = 70
HOLD_THRESHOLD = 40
REDIS_PREFIX   = "sentiment:market:volume"

# ═══════════════════════════════════════════════════════════════════════════════
#  DATA FETCHING — Direct Binance REST API
# ═══════════════════════════════════════════════════════════════════════════════

def fetch_klines(symbol: str, interval: str, limit: int = 50) -> pd.DataFrame:
    """
    Fetch OHLCV klines directly from Binance REST API.
    Symbol format: "BTCUSDT" (no slash).
    """
    api_symbol = symbol.replace("/", "")
    url = f"{BINANCE_BASE}/api/v3/klines"
    params = {"symbol": api_symbol, "interval": interval, "limit": limit}

    for attempt in range(3):
        try:
            resp = requests.get(url, params=params, timeout=10, verify=False)
            if resp.status_code == 429:
                wait = 2 ** (attempt + 1)
                logger.warning(f"Rate limited, waiting {wait}s …")
                time.sleep(wait)
                continue
            resp.raise_for_status()
            data = resp.json()

            df = pd.DataFrame(data, columns=[
                "open_time", "open", "high", "low", "close", "volume",
                "close_time", "quote_vol", "trades", "taker_buy_vol",
                "taker_buy_quote_vol", "ignore",
            ])
            for col in ["open", "high", "low", "close", "volume"]:
                df[col] = df[col].astype(float)
            df["timestamp"] = pd.to_datetime(df["open_time"], unit="ms")
            return df[["timestamp", "open", "high", "low", "close", "volume"]]

        except requests.exceptions.RequestException as e:
            logger.warning(f"Network error fetching {symbol} {interval}: {e}")
            time.sleep(2)
        except Exception as e:
            logger.error(f"Error fetching {symbol} {interval}: {e}")
            break

    return pd.DataFrame()

# ═══════════════════════════════════════════════════════════════════════════════
#  INDICATOR COMPUTATION
# ═══════════════════════════════════════════════════════════════════════════════

def compute_indicators(df: pd.DataFrame) -> Optional[Dict]:
    """Compute volume ratios, price momentum, SMA cross, divergence flags."""
    if df.empty or len(df) < 5:
        return None

    volumes = df["volume"].values
    closes  = df["close"].values
    opens   = df["open"].values

    avg_volume   = float(np.mean(volumes[:-1])) if len(volumes) > 1 else float(volumes[0])
    latest_vol   = float(volumes[-1])
    volume_ratio = latest_vol / avg_volume if avg_volume > 0 else 1.0

    # Price change of latest candle
    price_change_pct = ((closes[-1] - opens[-1]) / opens[-1] * 100) if opens[-1] != 0 else 0.0
    # Overall trend
    trend_pct = ((closes[-1] - opens[0]) / opens[0] * 100) if opens[0] != 0 else 0.0

    price_increasing   = trend_pct > 0
    volume_above_avg   = volume_ratio > 1.2
    bearish_divergence = volume_above_avg and price_change_pct < -0.5

    sma_bullish = False
    if len(closes) >= 20:
        sma_bullish = float(np.mean(closes[-10:])) > float(np.mean(closes[-20:]))

    return {
        "volume_ratio":       round(float(volume_ratio), 4),
        "price_change_pct":   round(float(price_change_pct), 4),
        "trend_pct":          round(float(trend_pct), 4),
        "price_increasing":   bool(price_increasing),
        "volume_above_avg":   bool(volume_above_avg),
        "bearish_divergence": bool(bearish_divergence),
        "sma_bullish":        bool(sma_bullish),
    }

# ═══════════════════════════════════════════════════════════════════════════════
#  SCORING ENGINE
# ═══════════════════════════════════════════════════════════════════════════════

def score_timeframe(ind: Dict, category: str) -> float:
    """Score a single timeframe 0–100."""
    score = 0.0

    # A. Volume Strength (0–35)
    vr = ind["volume_ratio"]
    if   vr >= 3.0: score += 35
    elif vr >= 2.0: score += 28
    elif vr >= 1.5: score += 22
    elif vr >= 1.2: score += 15
    elif vr >= 1.0: score += 8
    else:           score += max(0, vr * 8)

    # B. Price Momentum (0–35)
    t = ind["trend_pct"]
    if   t > 5:  score += 35
    elif t > 2:  score += 28
    elif t > 0.5: score += 20
    elif t > 0:  score += 12
    elif t > -1: score += 5

    # C. SMA Confirmation (0–20)
    if ind["sma_bullish"]:       score += 20
    elif ind["price_increasing"]: score += 10

    # D. Bearish Divergence Penalty
    if ind["bearish_divergence"]:
        score -= min(20, abs(ind["price_change_pct"]) * 5)

    # E. Long-term bearish override
    if category == "long" and t < -3:
        score = min(score, 25)

    return max(0, min(100, score))


def compute_final_score(tf_scores):
    """Weighted final score → (score, decision)."""
    weighted_sum = sum(s * w for _, w, s, _, _ in tf_scores)
    total_weight = sum(w for _, w, _, _, _ in tf_scores)
    final = weighted_sum / total_weight if total_weight > 0 else 50

    if   final > BUY_THRESHOLD:  decision = "BUY 🟢"
    elif final >= HOLD_THRESHOLD: decision = "HOLD 🟡"
    else:                         decision = "AVOID 🔴"

    return round(final, 2), decision

# ═══════════════════════════════════════════════════════════════════════════════
#  MAIN ANALYZER
# ═══════════════════════════════════════════════════════════════════════════════

class VolumePriceAnalyzer:
    def __init__(self, use_redis=True):
        self.use_redis = use_redis
        if use_redis:
            try:
                self.redis = redis.Redis(host="localhost", port=6379, db=0, decode_responses=True)
                self.redis.ping()
            except Exception:
                logger.warning("Redis not available — results will only be printed.")
                self.use_redis = False

    def analyze(self, symbol: str) -> Optional[Dict]:
        # logger.info(f"  Analyzing: {symbol}")

        tf_results = []
        tf_breakdown = {}

        for tf_name, interval, limit, weight, category in TIMEFRAME_CONFIG:
            # logger.info(f"  ⏱  Fetching {tf_name:>4s} ({limit} candles) …")
            df = fetch_klines(symbol, interval, limit)
            indicators = compute_indicators(df)

            if indicators is None:
                logger.warning(f"  ⚠  {tf_name}: insufficient data, skipping")
                continue

            raw_score = score_timeframe(indicators, category)
            tf_results.append((tf_name, weight, raw_score, category, indicators))
            tf_breakdown[tf_name] = {
                "category": category, "weight": weight, "score": raw_score,
                "volume_ratio": indicators["volume_ratio"],
                "price_change": indicators["price_change_pct"],
                "trend_pct": indicators["trend_pct"],
                "vol_above_avg": indicators["volume_above_avg"],
                "bearish_div": indicators["bearish_divergence"],
                "sma_bullish": indicators["sma_bullish"],
            }
            time.sleep(0.2)

        if not tf_results:
            logger.error(f"  No valid data for {symbol}")
            return None

        final_score, decision = compute_final_score(tf_results)

        # Category sub-scores
        cat_scores = {}
        for cat in ("short", "mid", "long"):
            entries = [(w, s) for (_, w, s, c, _) in tf_results if c == cat]
            if entries:
                cw = sum(w for w, _ in entries)
                cat_scores[cat] = round(sum(w * s for w, s in entries) / cw, 2) if cw > 0 else 0

        result = {
            "symbol": symbol, "decision": decision, "final_score": final_score,
            "category_scores": cat_scores, "timeframes": tf_breakdown,
            "timestamp": datetime.now().isoformat(),
        }

        # Only print full breakdown for BUY decisions
        if "BUY" in decision:
            self._print_breakdown(result)
            logger.info(f"🎯 BUY SIGNAL FOUND: {symbol} (Score: {final_score})")

        if self.use_redis:
            self._store_redis(symbol, result)

        return result

    def _store_redis(self, symbol: str, result: Dict):
        """
        Store volume analysis results in Redis:
          1. Full JSON per symbol  → sentiment:market:volume:{SYMBOL}  (10 min TTL)
          2. Summary hash entry   → VOLUME_SCORE hash field {SYMBOL}  (for dashboard)
        """
        try:
            # 1. Full result as a JSON string key (with TTL)
            key = f"{REDIS_PREFIX}:{symbol}"
            self.redis.set(key, json.dumps(result), ex=600)

            # 2. Also store in a Redis hash for fast dashboard lookups
            #    Key: VOLUME_SCORE, Field: KITEUSDT, Value: JSON summary
            hash_field = symbol.replace("/", "")
            summary = {
                "symbol":       symbol,
                "decision":     result["decision"],
                "score":        result["final_score"],
                "short":        result["category_scores"].get("short", 0),
                "mid":          result["category_scores"].get("mid", 0),
                "long":         result["category_scores"].get("long", 0),
                "timestamp":    result["timestamp"],
            }
            self.redis.hset("VOLUME_SCORE", hash_field, json.dumps(summary))

            logger.info(f"  💾 Redis: stored {key} + VOLUME_SCORE:{hash_field} (score={result['final_score']})")
        except Exception as e:
            logger.error(f"  ❌ Redis write error for {symbol}: {e}")

    def _print_breakdown(self, result: Dict):
        print(f"\n  ╔{'═' * 62}╗")
        print(f"  ║  {result['symbol']:^58s}  ║")
        print(f"  ╠{'═' * 62}╣")
        print(f"  ║  Decision:    {result['decision']:<46s}  ║")
        print(f"  ║  Final Score: {result['final_score']:<46.2f}  ║")
        print(f"  ╠{'═' * 62}╣")

        for cat, s in result["category_scores"].items():
            label = {"short": "Short-term", "mid": "Mid-term", "long": "Long-term"}[cat]
            bar = "█" * int(s / 100 * 30)
            print(f"  ║  {label:>12s}: {s:>6.2f}  {bar:<30s}  ║")

        print(f"  ╠{'═' * 62}╣")
        print(f"  ║  {'TF':>4s} │ {'Score':>5s} │ {'VolRatio':>8s} │ {'PriceChg':>8s} │ {'Trend':>7s} │ Flags        ║")
        print(f"  ╟{'─' * 62}╢")

        for tf, d in result["timeframes"].items():
            flags = []
            if d["vol_above_avg"]: flags.append("📊")
            if d["bearish_div"]:   flags.append("⚠️")
            if d["sma_bullish"]:   flags.append("✅")
            f_str = " ".join(flags) if flags else "—"
            print(f"  ║  {tf:>4s} │ {d['score']:>5.1f} │ {d['volume_ratio']:>8.2f} │ "
                  f"{d['price_change']:>+7.2f}% │ {d['trend_pct']:>+6.2f}% │ {f_str:<12s} ║")

        print(f"  ╚{'═' * 62}╝\n")


def scan_fast_movers(analyzer, symbols=None):
    """Scan multiple symbols and print ranked summary."""
    if symbols is None:
        try:
            r = redis.Redis(host="localhost", port=6379, db=0, decode_responses=True)
            fm = r.hkeys("FAST_MOVE")
            symbols = [k.replace("USDT", "/USDT") for k in fm[:20]] if fm else ["BTC/USDT", "ETH/USDT", "SOL/USDT"]
        except Exception:
            symbols = ["BTC/USDT", "ETH/USDT", "SOL/USDT"]

    results = []
    for sym in symbols:
        try:
            res = analyzer.analyze(sym)
            if res: results.append(res)
            time.sleep(1)
        except Exception as e:
            logger.error(f"Error analyzing {sym}: {e}")

    if not results: return

    results.sort(key=lambda r: r["final_score"], reverse=True)
    print("\n" + "═" * 72)
    print(f"  {'RANK':>4s}  {'SYMBOL':<12s}  {'SCORE':>6s}  {'DECISION':<12s}  {'SHORT':>6s}  {'MID':>6s}  {'LONG':>6s}")
    print("─" * 72)
    for i, r in enumerate(results, 1):
        cs = r.get("category_scores", {})
        print(f"  {i:>4d}  {r['symbol']:<12s}  {r['final_score']:>6.1f}  {r['decision']:<12s}  "
              f"{cs.get('short', 0):>6.1f}  {cs.get('mid', 0):>6.1f}  {cs.get('long', 0):>6.1f}")
    print("═" * 72 + "\n")


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="Multi-Timeframe Volume & Price Analyzer")
    parser.add_argument("--symbol", "-s", default=None)
    parser.add_argument("--scan", action="store_true")
    parser.add_argument("--symbols", nargs="+")
    parser.add_argument("--no-redis", action="store_true")
    args = parser.parse_args()

    analyzer = VolumePriceAnalyzer(use_redis=not args.no_redis)

    if args.symbol:
        analyzer.analyze(args.symbol)
    elif args.scan or args.symbols:
        scan_fast_movers(analyzer, args.symbols)
    else:
        sym = input("Enter symbol (e.g. BTC/USDT): ").strip().upper() or "BTC/USDT"
        analyzer.analyze(sym)
