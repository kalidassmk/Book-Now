#!/usr/bin/env python3
"""Inspect Redis for BINANCE keys and list USDT trading symbols and details.

Usage:
  source venv/bin/activate
  python inspect_binance_redis.py
"""
import json
import sys
import os
from redis_client import RedisClient

rc = RedisClient()

def main():
    try:
        r = rc.client
        print("Connected to Redis")
    except Exception as e:
        print(f"Failed to connect to Redis: {e}")
        sys.exit(1)

    # Try different patterns: 'BINANCE', 'BINANCE:*', 'BINANCE:*:SYMBOL' etc.
    patterns = ['BINANCE', 'BINANCE:*']
    all_keys = set()
    for p in patterns:
        try:
            keys = r.keys(p)
            for k in keys:
                all_keys.add(k)
        except Exception as e:
            print(f"Error fetching keys for pattern {p}: {e}")

    if not all_keys:
        print("No BINANCE keys found. Try running: redis-cli KEYS 'BINANCE*'")
    else:
        print(f"Found {len(all_keys)} keys matching BINANCE patterns")

    usdt_symbols = []
    details = {}

    # Inspect each key
    for key in sorted(all_keys):
        try:
            t = r.type(key)
            print(f"\nKey: {key}  (type: {t})")
            if t == 'hash':
                h = r.hgetall(key)
                print("  Hash fields:")
                for field, val in list(h.items())[:10]:
                    print(f"    {field}: {val}")
                # If hash contains symbols as fields
                for field in h.keys():
                    if field.upper().endswith('USDT'):
                        usdt_symbols.append(field.upper())
                        details[field.upper()] = h.get(field)
            elif t == 'set':
                members = r.smembers(key)
                print(f"  Set members count: {len(members)}")
                # add members ending with USDT
                for m in members:
                    if m.upper().endswith('USDT'):
                        usdt_symbols.append(m.upper())
            elif t == 'list':
                items = r.lrange(key, 0, -1)
                print(f"  List length: {len(items)}")
                for it in items[:10]:
                    if isinstance(it, str) and it.upper().endswith('USDT'):
                        usdt_symbols.append(it.upper())
            elif t == 'string':
                val = r.get(key)
                print(f"  Value: {val if val and len(val)<200 else (val[:200]+ '...') if val else None}")
                if isinstance(val, str) and val.upper().endswith('USDT'):
                    usdt_symbols.append(val.upper())
            else:
                print('  (unknown or empty type)')
        except Exception as e:
            print(f"  Error inspecting key {key}: {e}")

    # Also check keys that directly are symbols under BINANCE namespace: e.g., 'BINANCE:BTCUSDT'
    try:
        sym_keys = r.keys('BINANCE:*USDT')
        for k in sym_keys:
            # Extract symbol part
            parts = k.split(':')
            sym = parts[-1].upper()
            if sym.endswith('USDT') and sym not in usdt_symbols:
                usdt_symbols.append(sym)
                try:
                    val = r.get(k)
                    details[sym] = val
                except Exception:
                    pass
    except Exception as e:
        print(f"Error listing BINANCE:*USDT keys: {e}")

    # Deduplicate
    usdt_symbols = sorted(list(set(usdt_symbols)))

    print(f"\nTotal USDT symbols discovered: {len(usdt_symbols)}")
    if len(usdt_symbols) > 0:
        print("Sample symbols:", usdt_symbols[:20])

    # Save a small JSON summary
    out = {
        'count': len(usdt_symbols),
        'symbols': usdt_symbols,
        'details_sample': {k: (details.get(k)[:500] + '...') if isinstance(details.get(k), str) and len(details.get(k))>500 else details.get(k) for k in list(details.keys())[:10]}
    }

    with open('binance_usdt_summary.json', 'w') as f:
        json.dump(out, f, indent=2)

    print("Wrote binance_usdt_summary.json")

if __name__ == '__main__':
    main()

