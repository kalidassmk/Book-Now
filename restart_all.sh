#!/bin/bash

# ──────────────────────────────────────────────────────────────────────────────
# restart_all.sh - Auto-restart for BookNow Trading Suite
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

# 2. Kill Spring Boot (Port 8083)
SB_PID=$(lsof -t -i:8083)
if [ ! -z "$SB_PID" ]; then
  kill -9 $SB_PID 2>/dev/null
  echo "✅ Stopped Spring Boot on port 8083"
fi

# 3. Extra cleanup for safety
pkill -f "node server.js" 2>/dev/null
pkill -f "spring-boot:run" 2>/dev/null

echo ""
echo "--------------------------------------------------"
echo "🚀 STARTING SERVICES..."
echo "--------------------------------------------------"

BASE_DIR=$(cd "$(dirname "$0")" && pwd)

# 0. Check for Delistings (Python Script)
echo "🔍 Checking for Binance Delistings..."
python3 "$BASE_DIR/delist_detector.py"
echo ""

# Initialize SDKMAN for Java environment if it exists
if [ -f "$HOME/.sdkman/bin/sdkman-init.sh" ]; then
  source "$HOME/.sdkman/bin/sdkman-init.sh"
fi

# Start Spring Boot in background
echo "🌱 Starting Spring Boot Backend (book-now-v3)..."
cd "$BASE_DIR/book-now-v3"
bash -l -c "mvn spring-boot:run" > backend.log 2>&1 &
echo "   -> Logging to: book-now-v3/backend.log"

# Small delay
sleep 5

# Start Dashboard in background
echo "🖥️  Starting Node.js Dashboard (dashboard)..."
cd "$BASE_DIR/dashboard"
bash -l -c "node server.js" > dashboard.log 2>&1 &
echo "   -> Logging to: dashboard/dashboard.log"

echo ""
echo "--------------------------------------------------"
echo "🎉 RESTART COMPLETE!"
echo "📡 URL: http://localhost:3000"
echo "--------------------------------------------------"
