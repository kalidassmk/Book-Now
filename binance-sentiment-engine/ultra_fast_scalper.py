import asyncio
import os
import pandas as pd
import ta
import logging
import json
from datetime import datetime
from binance.client import AsyncClient
from binance.exceptions import BinanceAPIException
from decimal import Decimal, ROUND_DOWN
from dotenv import load_dotenv

# ─── LOAD ENVIRONMENT ────────────────────────────────────────────────────────
# Load keys from the shared dashboard .env
dotenv_path = os.path.join(os.path.dirname(__file__), "..", "dashboard", ".env")
load_dotenv(dotenv_path)

API_KEY    = os.getenv("BINANCE_API_KEY")
API_SECRET = os.getenv("BINANCE_SECRET_KEY") # JS uses SECRET_KEY, Python typically SECRET

# ─── CONFIGURATION ───────────────────────────────────────────────────────────
EMA_FAST       = 9
EMA_MID        = 21
EMA_SLOW       = 50
RSI_PERIOD     = 7
VOL_PERIOD     = 20

# Redis Keys
CONFIG_KEY     = "booknow:config"
SYMBOL_LIST_KEY = "SYMBOLS:ACTIVE"
SIGNAL_PREFIX  = "SCALPER:SIGNAL:"
POSITIONS_KEY  = "SCALPER:POSITIONS" # Local positions for the scalper

# ─── LOGGING ──────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s | %(levelname)-7s | %(message)s",
    datefmt="%H:%M:%S"
)
log = logging.getLogger("MultiScalper")

class MultiSymbolScalper:
    def __init__(self):
        self.client = None
        self.redis = None
        self.is_running = True
        self.symbols = []
        self.active_positions = {} # symbol -> {buy_price, qty}
        
        # Dynamic Settings
        self.auto_enabled = False
        self.buy_amount_usdt = 12.0
        self.profit_target_usdt = 0.20
        self.stop_loss_usdt = 0.10

        # Filter cache
        self.filters = {} # symbol -> {step_size, tick_size, min_notional}

    async def initialize(self):
        log.info("🚀 Initializing Multi-Symbol Scalper Engine...")
        
        # 1. Redis Connection
        try:
            import redis
            self.redis = redis.Redis(host='127.0.0.1', port=6379, decode_responses=True)
            self.redis.ping()
            log.info("🔗 Connected to Redis.")
        except Exception as e:
            log.error(f"❌ Redis Connection Failed: {e}")

        # 2. Fetch API Keys (Redis Priority -> Env Fallback)
        redis_key = None
        redis_secret = None
        if self.redis:
            try:
                redis_key = self.redis.get("BINANCE_API_KEY")
                redis_secret = self.redis.get("BINANCE_SECRET_KEY")
                if redis_key and redis_secret:
                    log.info("🔑 API Credentials loaded from Redis.")
            except Exception:
                pass

        final_key = redis_key or API_KEY
        final_secret = redis_secret or API_SECRET

        if not final_key or not final_secret:
            log.error("❌ API Keys missing! Check Redis or dashboard/.env file.")
            exit(1)

        self.client = await AsyncClient.create(final_key, final_secret)
        
        # 3. Load Symbols
        await self.refresh_symbols()
        log.info(f"📊 Initialized with {len(self.symbols)} USDT pairs from Redis.")

    async def refresh_symbols(self):
        """Fetch the latest Top USDT pairs from Redis."""
        if not self.redis: return
        try:
            raw = self.redis.get(SYMBOL_LIST_KEY)
            if raw:
                # Remove slashes if present (e.g., BTC/USDT -> BTCUSDT)
                self.symbols = [s.replace("/", "") for s in json.loads(raw)]
                # Pre-fetch filters for new symbols
                for sym in self.symbols[:50]: # Only fetch filters for top 50 to avoid rate limit at start
                    if sym not in self.filters:
                        await self.fetch_filters(sym)
        except Exception as e:
            log.error(f"⚠️ Symbol refresh failed: {e}")

    async def fetch_filters(self, symbol):
        """Fetch trading rules for a symbol."""
        try:
            info = await self.client.get_symbol_info(symbol)
            if not info: return
            
            f_data = {"step_size": 0.0, "tick_size": 0.0, "min_notional": 10.0}
            for f in info['filters']:
                if f['filterType'] == 'LOT_SIZE':
                    f_data["step_size"] = float(f['stepSize'])
                if f['filterType'] == 'PRICE_FILTER':
                    f_data["tick_size"] = float(f['tickSize'])
                if f['filterType'] == 'NOTIONAL' or f['filterType'] == 'MIN_NOTIONAL':
                    f_data["min_notional"] = float(f.get('minNotional', f.get('notional', 10.0)))
            self.filters[symbol] = f_data
        except Exception:
            pass

    async def sync_config(self):
        """Fetch global dashboard settings."""
        if not self.redis: return
        try:
            raw = self.redis.get(CONFIG_KEY)
            if raw:
                cfg = json.loads(raw)
                self.auto_enabled = cfg.get("autoBuyEnabled", False)
                self.buy_amount_usdt = cfg.get("buyAmountUsdt", 12.0)
                self.profit_target_usdt = cfg.get("profitAmountUsdt", 0.20)
                self.stop_loss_usdt = self.profit_target_usdt / 2.0
        except Exception:
            pass

    async def get_indicators(self, symbol: str):
        """Fetch data and compute indicators for a symbol."""
        try:
            klines = await self.client.get_klines(symbol=symbol, interval="1m", limit=60)
            df = pd.DataFrame(klines, columns=[
                'time', 'open', 'high', 'low', 'close', 'vol', 'close_time',
                'q_vol', 'trades', 't_buy_base', 't_buy_quote', 'ignore'
            ]).astype(float)

            df['ema9']  = ta.trend.ema_indicator(df['close'], window=EMA_FAST)
            df['ema21'] = ta.trend.ema_indicator(df['close'], window=EMA_MID)
            df['ema50'] = ta.trend.ema_indicator(df['close'], window=EMA_SLOW)
            df['rsi']   = ta.momentum.rsi(df['close'], window=RSI_PERIOD)
            df['v_avg'] = df['vol'].rolling(window=VOL_PERIOD).mean()
            return df
        except Exception:
            return None

    def evaluate_entry(self, symbol, df, btc_df):
        """Scalping Pullback Strategy."""
        if df is None or btc_df is None or len(df) < 50: return False
        
        curr = df.iloc[-1]
        btc  = btc_df.iloc[-1]

        # BTC Trend Filter (Must be healthy)
        btc_ok = btc['ema9'] > btc['ema21']
        
        # Symbol Trend
        uptrend = curr['ema9'] > curr['ema21'] > curr['ema50']
        # Pullback: Low touched EMA9 or EMA21
        pullback = curr['low'] <= (curr['ema9'] * 1.001) or curr['low'] <= (curr['ema21'] * 1.001)
        rsi_ok = 50 < curr['rsi'] < 75
        vol_ok = curr['vol'] > (curr['v_avg'] * 1.05)
        bullish = curr['close'] > curr['open']

        return all([uptrend, pullback, rsi_ok, vol_ok, bullish, btc_ok])

    async def process_symbol(self, symbol, btc_df):
        """Analyze and trade a single symbol."""
        # 1. Manage existing position
        if symbol in self.active_positions:
            df = await self.get_indicators(symbol)
            if df is None: return
            
            curr = df.iloc[-1]
            pos = self.active_positions[symbol]
            pnl = (curr['close'] - pos['buy_price']) * pos['qty']
            
            # Exit Conditions
            should_exit = False
            if pnl >= self.profit_target_usdt: 
                log.info(f"💰 [{symbol}] Profit Target Hit: +${pnl:.2f}")
                should_exit = True
            elif pnl <= -self.stop_loss_usdt:
                log.info(f"🛡️ [{symbol}] Stop Loss Hit: -${pnl:.2f}")
                should_exit = True
            elif curr['ema9'] < curr['ema21']:
                log.info(f"🔄 [{symbol}] Trend Reversal")
                should_exit = True
                
            if should_exit:
                await self.execute_sell(symbol, curr['close'])
            return

        # 2. Look for new entry
        df = await self.get_indicators(symbol)
        if self.evaluate_entry(symbol, df, btc_df):
            await self.execute_buy(symbol, df.iloc[-1]['close'])

    async def execute_buy(self, symbol, price):
        # Broadcast signal to Redis first
        self.broadcast_signal(symbol, "BUY", price)
        
        if not self.auto_enabled:
            return

        if len(self.active_positions) >= 5: # Safety limit: Max 5 concurrent scalps
            return

        try:
            if symbol not in self.filters: await self.fetch_filters(symbol)
            f = self.filters[symbol]
            
            qty = self.round_step(self.buy_amount_usdt / price, f['step_size'])
            if (qty * price) < f['min_notional']: return

            log.info(f"🛒 [SCALPER] Buying {symbol} @ {price}")
            order = await self.client.order_market_buy(symbol=symbol, quantity=qty)
            
            exec_price = float(order['fills'][0]['price']) if order['fills'] else price
            self.active_positions[symbol] = {'buy_price': exec_price, 'qty': qty}
        except Exception as e:
            log.error(f"❌ Buy {symbol} failed: {e}")

    async def execute_sell(self, symbol, price):
        if symbol not in self.active_positions: return
        try:
            qty = self.active_positions[symbol]['qty']
            log.info(f"⚡ [SCALPER] Selling {symbol} @ {price}")
            await self.client.order_market_sell(symbol=symbol, quantity=qty)
            del self.active_positions[symbol]
        except Exception as e:
            log.error(f"❌ Sell {symbol} failed: {e}")

    def broadcast_signal(self, symbol, status, price):
        if not self.redis: return
        payload = {"symbol": symbol, "signal": status, "price": price, "ts": datetime.now().isoformat()}
        self.redis.set(f"{SIGNAL_PREFIX}{symbol}", json.dumps(payload), ex=30)

    def round_step(self, qty, step):
        if not step: return float(int(qty))
        precision = str(Decimal(str(step)).normalize())
        p = int(precision.split('E-')[1]) if 'E-' in precision else len(precision.split('.')[1]) if '.' in precision else 0
        return float(Decimal(str(qty)).quantize(Decimal(str(10**-p)), rounding=ROUND_DOWN))

    async def start(self):
        await self.initialize()
        
        last_symbol_refresh = 0
        while self.is_running:
            try:
                await self.sync_config()
                
                # Refresh symbols every 5 minutes
                if time.time() - last_symbol_refresh > 300:
                    await self.refresh_symbols()
                    last_symbol_refresh = time.time()

                btc_df = await self.get_indicators("BTCUSDT")
                if btc_df is None: 
                    await asyncio.sleep(5)
                    continue

                # Process in small batches to avoid Binance Rate Limits
                batch_size = 10
                for i in range(0, len(self.symbols), batch_size):
                    batch = self.symbols[i : i + batch_size]
                    tasks = [self.process_symbol(s, btc_df) for s in batch]
                    await asyncio.gather(*tasks)
                    await asyncio.sleep(0.5) # Gentle spacing

            except Exception as e:
                log.error(f"🔥 Engine Error: {e}")
                await asyncio.sleep(5)

if __name__ == "__main__":
    import time
    scalper = MultiSymbolScalper()
    try:
        asyncio.run(scalper.start())
    except KeyboardInterrupt:
        pass
