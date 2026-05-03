#!/bin/bash

# ──────────────────────────────────────────────────────────────────────────────
# restart_all.sh - Auto-restart for BookNow Trading Suite
#
# Spring Boot backend has been removed (project is migrating to Python).
# This script now only manages the Node dashboard. The Python trading
# engine that replaces book-now-v3/ will be added here once it's built.
# ──────────────────────────────────────────────────────────────────────────────

echo "--------------------------------------------------"
echo "🛑 STOPPING EXISTING SERVICES..."
echo "--------------------------------------------------"

# 1. Kill Dashboard (Port 3000)
DASH_PID=$(lsof -t -i:3000)
if [ ! -z "$DASH_PID" ]; then
  kill -9 $DASH_PID 2>/dev/null
  echo "✅ Stopped Dashboard on port 3000"
fi

# 2. (legacy) kill anything left on the old Spring Boot port 8083 just in case
SB_PID=$(lsof -t -i:8083)
if [ ! -z "$SB_PID" ]; then
  kill -9 $SB_PID 2>/dev/null
  echo "✅ Stopped legacy process on port 8083 (was Spring Boot)"
fi

# 3. Extra cleanup for safety
pkill -f "node server.js" 2>/dev/null

echo ""
echo "--------------------------------------------------"
echo "🚀 STARTING SERVICES..."
echo "--------------------------------------------------"

BASE_DIR=$(cd "$(dirname "$0")" && pwd)

# 0. Check for Delistings (Python Script)
echo "🔍 Checking for Binance Delistings..."
python3 "$BASE_DIR/delist_detector.py"
echo ""

# Start Dashboard in background
echo "🖥️  Starting Node.js Dashboard (dashboard)..."
cd "$BASE_DIR/dashboard"
bash -l -c "node server.js" > dashboard.log 2>&1 &
echo "   -> Logging to: dashboard/dashboard.log"

echo ""
echo "--------------------------------------------------"
echo "🎉 RESTART COMPLETE!"
echo "📡 URL: http://localhost:3000"
echo ""
echo "⚠️  NOTE: Spring Boot backend has been removed. Trading-related"
echo "    dashboard endpoints (buy/sell/cancel/balances) will return"
echo "    503 until the Python trading engine is in place."
echo "--------------------------------------------------"
