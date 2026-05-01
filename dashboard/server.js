const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const Redis = require('ioredis');
const path = require('path');
const { spawn } = require('child_process');
// Node v20+ has native fetch built-in — no extra package needed


// ─── Config ─────────────────────────────────────────────────────────────────
const PORT = 3000;
const REDIS_HOST = '127.0.0.1';
const REDIS_PORT = 6379;
const POLL_MS = 400;   // 1 second polling
const SPRING_PORT = 8083;
const SPRING_BASE = `http://localhost:${SPRING_PORT}/api/v1`;
const SPRING_DIR = path.resolve(__dirname, '../book-now-v3');

// ─── Spring Boot process handle ──────────────────────────────────────────────
let springProc = null;   // ChildProcess when we own the process

// ─── Auto-trade defaults (user can change via dashboard) ────────────────────
let autoConfig = {
    enabled: false,    // auto-trade on/off
    profitPct: 1.5,      // sell when gain reaches X%
    stopLossPct: 0.8,      // sell if loss exceeds X%
    maxPositions: 5,        // max simultaneous open positions
    simulationMode: true,     // true = paper trade only
    displayLimit: 30,       // max fast movers to display
};

// ─── Redis keys (must match Constant.java exactly) ──────────────────────────
const FAST_MOVE_KEY = 'FAST_MOVE';       // Constant.FAST_MOVE
const LT2MIN_KEY    = 'LT2MIN_0>3';     // Constant.LT2MIN_0_TO_3
const UF_0_2_KEY    = 'ULTRA_FAST0>2';  // Constant.ULTRA_FAST_0_TO_2
const UF_2_3_KEY    = 'ULTRA_FAST2>3';  // Constant.ULTRA_FAST_2_TO_3
const UF_0_3_KEY    = 'ULTRA_FAST0>3';  // Constant.ULTRA_FAST_0_TO_3
const UF_3_5_KEY    = 'ULTRA_FAST>3<5';  // Constant.ULTRA_FAST_3_5
const SF_2_3_KEY    = 'SUPER_FAST>2<3';  // Constant.SUPER_FAST_2_3
const USF_5_7_KEY   = 'ULTRA_SUPER_FAST>5<7'; // Constant.ULTRA_SUPER_FAST_5_7
const CURRENT_PRICE = 'CURRENT_PRICE';
const BUY_KEY       = 'BUY';
const SELL_KEY      = 'SELL';


// ─── State ───────────────────────────────────────────────────────────────────
const positions = new Map();   // symbol → { buyPrice, qty, buyTime, target, stopLoss }
const tradeLog = [];          // all completed/active trades
let totalPnL = 0;
const USDT_INR_RATE = 92; // Approximate rate for decision making
const tradeStatusMap = new Map(); // Track symbol -> status to detect execution
const analysisCache = new Map(); // symbol → { high2m, low2m, avg2m, vol30dAvg, vol7dAvg, lastUpdated }
const activeLimitOrders = new Map(); // symbol → { orderId, symbol, side, limitPrice, qty, status, time }
const CACHE_TTL = 30 * 60 * 1000; // 30 mins

// ─── Setup ───────────────────────────────────────────────────────────────────
const app = express();
const server = http.createServer(app);
const io = new Server(server, { cors: { origin: '*' } });
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// ── Binance Worker ───────────────────────────────────────────────────────────
const binanceWorker = require('./binance-worker');
binanceWorker.start(io);

const redis = new Redis({ host: REDIS_HOST, port: REDIS_PORT, lazyConnect: true });
redis.on('error', e => console.error('[Server] Redis Error:', e.message));
redis.on('connect', () => console.log('[Server] Redis Connected ✅'));

io.on('connection', (socket) => {
    console.log(`[Socket] Client connected: ${socket.id}`);
    socket.on('disconnect', () => console.log(`[Socket] Client disconnected: ${socket.id}`));
});

/**
 * Check Binance via Spring Boot for real order status (FILLED, PARTIAL, etc.)
 */
async function checkBinanceOrderStatus(symbol, orderId) {
    try {
        const url = `${SPRING_BASE}/order/status/${symbol}/${orderId}`;
        console.log(`[Spring Boot] GET /order/status/${symbol}/${orderId}`);
        const res = await fetch(url);
        if (!res.ok) {
            console.warn(`[Spring Boot] Status check failed: ${res.status}`);
            return;
        }

        const data = await res.json();
        console.log(`[Spring Boot] Status check result for ${symbol}: ${data.status}`);
        const status = data.status; // e.g. "FILLED", "PARTIALLY_FILLED", "NEW"
        
        const order = activeLimitOrders.get(orderId);
        if (order && (order.status !== status || order.executedQty !== data.executedQty)) {
            order.status = status;
            order.executedQty = data.executedQty;
            order.origQty = data.origQty;
            console.log(`[Order] ${symbol} updated to ${status} (Executed: ${data.executedQty}/${data.origQty})`);
            io.emit('order-update', order);

            if (status === 'FILLED' || status === 'CANCELED' || status === 'EXPIRED') {
                activeLimitOrders.delete(orderId);
                if (status === 'FILLED') {
                    // Refresh positions to show the new position
                }
            }
        }
    } catch (e) {
        console.error(`[Order Monitor] Failed to check status for ${symbol}:`, e.message);
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────
function safeJson(raw) {
    try { return raw ? JSON.parse(raw) : null; } catch { return null; }
}

async function getAllHash(key) {
    const raw = await redis.hgetall(key);
    if (!raw) return {};
    const out = {};
    for (const [k, v] of Object.entries(raw)) {
        out[k] = safeJson(v) || v;
    }
    return out;
}

function currentPriceOf(sym, priceHash) {
    const entry = priceHash[sym];
    if (!entry) return 0;
    if (typeof entry === 'object') {
        // Handle cases where price is a BigDecimal object or nested
        const p = entry.price?.value ?? entry.price ?? entry.currentPrice ?? 0;
        return typeof p === 'object' ? parseFloat(p.value ?? 0) : parseFloat(p);
    }
    return parseFloat(entry) || 0;
}

/**
 * Fetch 60-day klines and 24h ticker for a symbol and cache the metrics.
 */
async function refreshAnalysisMetrics(symbol) {
    try {
        const [klinesRes, tickerRes] = await Promise.all([
            fetch(`https://api.binance.com/api/v3/klines?symbol=${symbol}&interval=1d&limit=60`),
            fetch(`https://api.binance.com/api/v3/ticker/24hr?symbol=${symbol}`),
        ]);
        const klines = await klinesRes.json();
        const ticker = await tickerRes.json();

        if (!Array.isArray(klines) || klines.length < 14) return null;

        const closes    = klines.map(k => parseFloat(k[4]));
        const highs     = klines.map(k => parseFloat(k[2]));
        const lows      = klines.map(k => parseFloat(k[3]));
        const quoteVols = klines.map(k => parseFloat(k[7]));

        const vol24h = tickerRes.ok ? parseFloat(ticker.quoteVolume || 0) : 0;

        const metrics = {
            high2m:    Math.max(...highs),
            low2m:     Math.min(...lows),
            low7d:     Math.min(...lows.slice(-7)),
            low14d:    Math.min(...lows.slice(-14)),
            low30d:    Math.min(...lows.slice(-30)),
            low60d:    Math.min(...lows),
            avg2m:     closes.reduce((a, b) => a + b, 0) / closes.length,
            vol30dAvg: quoteVols.slice(-30).reduce((a, b) => a + b, 0) / 30,
            vol7dAvg:  quoteVols.slice(-7).reduce((a, b) => a + b, 0) / 7,
            vol24h,
            quoteVols, // keep for rising vol check
            lastUpdated: Date.now()
        };
        analysisCache.set(symbol, metrics);
        return metrics;
    } catch (e) {
        console.error(`[Analysis] Failed for ${symbol}:`, e.message);
        return null;
    }
}

/**
 * Calculate the 0-7 score based on live price and cached metrics.
 */
function calculateScore(symbol, currentPrice, metrics) {
    if (!metrics || !currentPrice) return { score: 0, rec: '...' };

    let score = 0;
    const pricePos = (metrics.high2m - metrics.low2m) > 0
        ? ((currentPrice - metrics.low2m) / (metrics.high2m - metrics.low2m) * 100)
        : 50;

    if (currentPrice < metrics.avg2m) score++;
    if (pricePos < 40) score++;
    // Simple volume check (vs 30d avg)
    if (metrics.vol30dAvg > 0) {
        // We don't have live 24h vol here easily without extra fetch,
        // but we can assume if it's in FAST_MOVE, it has volume.
        // For simplicity in the loop, we focus on price metrics.
        score++;
    }

    const rec = score >= 5 ? 'STRONG_BUY' : score === 4 ? 'BUY' : score === 3 ? 'NEUTRAL' : score === 2 ? 'WAIT' : 'DONT_BUY';

    // Publish score to Redis so Spring Boot ConsensusCoordinator can read it
    redis.hset('DASHBOARD_SCORE', symbol, JSON.stringify({
        score, recommendation: rec, pricePosition: Math.round(pricePos),
        priceBelowAvg: currentPrice < metrics.avg2m,
        timestamp: Date.now()
    })).catch(() => {});

    return { score, rec };
}

function now() { return Date.now(); }

// ─── Auto-trade Engine ───────────────────────────────────────────────────────
async function runAutoTrade(fastCoins, priceHash) {
    if (!autoConfig.enabled) return;

    for (const [symbol, data] of Object.entries(fastCoins)) {
        const price = currentPriceOf(symbol, priceHash);
        if (!price) continue;

        // ── AUTO BUY ─────────────────────────────────────────────────────────
        if (!positions.has(symbol) && positions.size < autoConfig.maxPositions) {
            const qty = 10 / price;   // invest $10 per position
            const target = price * (1 + autoConfig.profitPct / 100);
            const stop = price * (1 - autoConfig.stopLossPct / 100);

            positions.set(symbol, {
                symbol, buyPrice: price, qty,
                buyTime: now(),
                target, stop,
                signal: data.signal || 'FAST_MOVE',
            });

            const trade = {
                id: `${symbol}-${now()}`,
                symbol, action: 'BUY', price, qty,
                target, stop,
                time: now(),
                status: 'OPEN',
                simulation: autoConfig.simulationMode,
                pnl: 0,
            };
            tradeLog.unshift(trade);
            if (tradeLog.length > 200) tradeLog.pop();

            console.log(`[AUTO BUY]  ${symbol} @ ${price} | target ${target.toFixed(6)} | stop ${stop.toFixed(6)}`);
            io.emit('trade', trade);
        }
    }

    // ── AUTO SELL ─────────────────────────────────────────────────────────────
    for (const [symbol, pos] of positions) {
        const price = currentPriceOf(symbol, priceHash);
        if (!price) continue;

        const pnlPct = ((price - pos.buyPrice) / pos.buyPrice) * 100;
        const hitTarget = price >= pos.target;
        const hitStop = price <= pos.stop;
        const heldTooLong = (now() - pos.buyTime) > 5 * 60 * 1000;  // 5 min timeout

        let reason = null;
        if (hitTarget) reason = `PROFIT_TARGET (+${pnlPct.toFixed(2)}%)`;
        else if (hitStop) reason = `STOP_LOSS (${pnlPct.toFixed(2)}%)`;
        else if (heldTooLong && pnlPct > 0) reason = `TIME_EXIT (+${pnlPct.toFixed(2)}%)`;

        if (reason) {
            const realized = (price - pos.buyPrice) * pos.qty;
            totalPnL += realized;
            positions.delete(symbol);

            const trade = {
                id: `${symbol}-sell-${now()}`,
                symbol, action: 'SELL', price,
                qty: pos.qty,
                buyPrice: pos.buyPrice,
                pnlPct: pnlPct.toFixed(2),
                realizedPnL: realized.toFixed(4),
                reason, time: now(),
                status: 'CLOSED',
                simulation: autoConfig.simulationMode,
            };
            tradeLog.unshift(trade);
            if (tradeLog.length > 200) tradeLog.pop();

            console.log(`[AUTO SELL] ${symbol} @ ${price} | ${reason} | PnL: $${realized.toFixed(4)}`);
            io.emit('trade', trade);
        }
    }
}

// ─── Main Data Loop ──────────────────────────────────────────────────────────
async function tick() {
    try {
        // 1. Fast-move sources
        const [fastMove, lt2min, uf02, uf23, uf03, uf35, sf23, usf57, priceHash, botBuys, botSellsRaw] = await Promise.all([
            getAllHash(FAST_MOVE_KEY),
            getAllHash(LT2MIN_KEY),
            getAllHash(UF_0_2_KEY),
            getAllHash(UF_2_3_KEY),
            getAllHash(UF_0_3_KEY),
            getAllHash(UF_3_5_KEY),
            getAllHash(SF_2_3_KEY),
            getAllHash(USF_5_7_KEY),
            getAllHash(CURRENT_PRICE),
            getAllHash(BUY_KEY),
            getAllHash(SELL_KEY),
        ]);

        // 2. Merge all fast signals into one map
        const fastCoins = {};

        const mergeSignal = (map, signalName, scoreField) => {
            for (const [sym, d] of Object.entries(map)) {
                let score = 0;
                if (d && typeof d === 'object') {
                    score = parseFloat(d[scoreField] || 0);
                } else if (d) {
                    score = parseFloat(d) || 0;
                }

                if (!fastCoins[sym] || score > (fastCoins[sym].score || 0)) {
                    fastCoins[sym] = {
                        ...fastCoins[sym],
                        signal: signalName,
                        raw: d,
                        score: score
                    };
                }
            }
        };

        mergeSignal(usf57, 'USF_5>7', 'increasedPercentage');
        mergeSignal(uf35,  'UF_3>5',  'increasedPercentage');
        mergeSignal(sf23,  'SF_2>3',  'increasedPercentage');
        mergeSignal(lt2min, 'LT2MIN_0>3', 'increasedPercentage');
        mergeSignal(uf03,   'UF_0>3',     'increasedPercentage');
        mergeSignal(uf02,   'UF_0>2',     'increasedPercentage');
        mergeSignal(uf23,   'UF_2>3',     'increasedPercentage');
        mergeSignal(fastMove, 'FAST_MOVE', 'overAllCount');

        // 3. Convert to list and perform initial sort by signal/score
        const priority = { 
            'USF_5>7': 0, 'UF_3>5': 1, 'SF_2>3': 2, 
            'LT2MIN_0>3': 3, 'UF_0>3': 4, 'UF_0>2': 5, 'UF_2>3': 6, 
            'FAST_MOVE': 7 
        };
        const allCoins = Object.entries(fastCoins).map(([symbol, data]) => ({
            symbol,
            signal: data.signal,
            score: data.score || 0,
            hasPosition: (positions.get(symbol)?.status === 'FILLED' || botBuys[symbol]?.status === 'FILLED')
        }));

        allCoins.sort((a, b) => {
            if (a.hasPosition && !b.hasPosition) return -1;
            if (b.hasPosition && !a.hasPosition) return 1;
            const pA = priority[a.signal] ?? 9;
            const pB = priority[b.signal] ?? 9;
            if (pA !== pB) return pA - pB;
            return (b.score || 0) - (a.score || 0);
        });

        // 4. Slice to display limit
        const limit = autoConfig.displayLimit || 10;
        const displayedRaw = allCoins.slice(0, limit);

        // 5. Enrich only displayed coins with live price and analysis
        const displayed = await Promise.all(displayedRaw.map(async (c) => {
            const { symbol } = c;
            const price = currentPriceOf(symbol, priceHash);
            const botBuy = botBuys[symbol];
            const myPos = positions.get(symbol);
            
            // 🔹 1. Core Rule & 4. P&L Calculation
            const status = myPos?.status || botBuy?.status || 'NEW';
            const isExecuted = status === 'FILLED';
            const bp = myPos?.buyPrice || (botBuy ? parseFloat(botBuy.buyPrice || 0) : null);
            
            const pnlPct = (isExecuted && bp && price) ? ((price - bp) / bp * 100) : 0;

            // Analysis scoring
            let metrics = analysisCache.get(symbol);
            if (!metrics || (Date.now() - metrics.lastUpdated > CACHE_TTL)) {
                refreshAnalysisMetrics(symbol).catch(() => {});
            }
            const { score: aScore, rec } = calculateScore(symbol, price, metrics);

            const isPos = isExecuted;
            let buyUsdt = 0;
            let curUsdt = 0;

            if (isExecuted) {
                if (myPos) {
                    buyUsdt = myPos.qty * myPos.buyPrice;
                    curUsdt = myPos.qty * price;
                } else if (botBuy) {
                    buyUsdt = 12; // Standard bot buy
                    curUsdt = bp > 0 ? (price / bp * buyUsdt) : 0;
                }
            }
            const profitUsdt = isExecuted ? (curUsdt - buyUsdt) : 0;

            let newsAnalysis = null;
            try {
                const coinName = symbol.replace('USDT', '');
                const rawNews = await redis.get(`analysis:${coinName}:detailed`);
                if (rawNews) {
                    newsAnalysis = JSON.parse(rawNews);
                }
            } catch(e) {}

            return {
                ...c,
                analysisScore: aScore,
                recommendation: rec,
                currentPrice: price,
                buyPrice: bp,
                targetPrice:  myPos?.targetPrice || myPos?.target || null,
                pnlPct: isExecuted ? parseFloat(pnlPct.toFixed(3)) : null,
                isPosition: isPos,
                status: status,
                buyUsdt: parseFloat(buyUsdt.toFixed(2)),
                currentUsdt: parseFloat(curUsdt.toFixed(2)),
                profitUsdt: parseFloat(profitUsdt.toFixed(2)),
                profitInr: parseFloat((profitUsdt * USDT_INR_RATE).toFixed(2)),
                botSignal: !!botBuys[symbol],
                vol24h: metrics ? metrics.vol24h : 0,
                heldMs: (isExecuted && myPos) ? (now() - myPos.buyTime) : null,
                newsAnalysis: newsAnalysis,
            };
        }));

        // 4. Dashboard (Node) active positions enriched
        const activePositions = Array.from(positions.values()).map(p => {
            const price = currentPriceOf(p.symbol, priceHash);
            const status = p.status || 'FILLED';
            const isExecuted = status === 'FILLED';
            const pnlPct = (isExecuted && price) ? ((price - p.buyPrice) / p.buyPrice * 100) : 0;
            const buyUsdt = p.qty * p.buyPrice;
            const curUsdt = p.qty * (price || p.buyPrice);
            const profitUsdt = isExecuted ? (curUsdt - buyUsdt) : 0;

            return {
                ...p,
                source: 'DASHBOARD',
                status: status,
                currentPrice: price,
                targetPrice:  p.target || null,
                pnlPct: parseFloat(pnlPct.toFixed(3)),
                buyUsdt: parseFloat(buyUsdt.toFixed(2)),
                currentUsdt: parseFloat(curUsdt.toFixed(2)),
                profitUsdt: parseFloat(profitUsdt.toFixed(2)),
                profitInr: parseFloat((profitUsdt * USDT_INR_RATE).toFixed(2)),
                heldSec: isExecuted ? Math.floor((now() - p.buyTime) / 1000) : 0,
                hms: p.hms || new Date(p.buyTime).toLocaleTimeString(),
            };
        });

        // 4b. Spring Boot bot positions — read directly from BUY Redis hash
        //     Buy fields: buyPrice, selP (sell target %), status, hms, buyPercentage
        const botPositions = Object.entries(botBuys)
            .map(([symbol, buy]) => {
                const status = buy?.status ?? 'NEW';
                const isExecuted = status === 'FILLED';
                const bp = parseFloat(
                    buy?.buyPrice?.value ?? buy?.buyPrice ?? 0
                );
                const selP = parseFloat(buy?.selP ?? 0);
                const currentPrice = currentPriceOf(symbol, priceHash);
                const targetPrice  = bp > 0 ? bp * (1 + selP / 100) : null;
                const pnlPct = (isExecuted && bp > 0 && currentPrice)
                    ? parseFloat(((currentPrice - bp) / bp * 100).toFixed(3))
                    : 0;
                const buyUsdt = 12; // Standard bot buy is $12
                const curUsdt = (isExecuted && bp > 0 && currentPrice) ? (currentPrice / bp * buyUsdt) : 0;
                const profitUsdt = isExecuted ? (curUsdt - buyUsdt) : 0;

                return {
                    symbol,
                    source:       'BOT',
                    buyPrice:     bp,
                    selP,
                    targetPrice:  targetPrice ? parseFloat(targetPrice.toFixed(8)) : null,
                    currentPrice,
                    pnlPct,
                    buyUsdt:      parseFloat(buyUsdt.toFixed(2)),
                    currentUsdt:  parseFloat(curUsdt.toFixed(2)),
                    profitUsdt:   parseFloat(profitUsdt.toFixed(2)),
                    profitInr:    parseFloat((profitUsdt * USDT_INR_RATE).toFixed(2)),
                    status:       status,
                    hms:          buy?.hms ?? null,
                    buyPct:       parseFloat(buy?.buyPercentage ?? 0),
                    orderId:      buy?.orderId ?? null,
                    executedQty:  buy?.executedQty ?? null,
                    origQty:      buy?.origQty ?? null,
                };
            })
            .filter(b => b.buyPrice > 0 && b.status !== 'Y')   // ignore sold/empty entries
            .sort((a, b) => (b.pnlPct ?? -999) - (a.pnlPct ?? -999));

        // Detect Status Changes (Execution Alert)
        botPositions.forEach(p => {
            const oldStatus = tradeStatusMap.get(p.symbol);
            if (oldStatus === 'PENDING' && p.status !== 'PENDING') {
                io.emit('trade-executed', { ...p, action: 'BUY' });
                console.log(`[Alert] ${p.symbol} order filled!`);
            }
            tradeStatusMap.set(p.symbol, p.status);
        });
        // Cleanup old status tracking
        for (const sym of tradeStatusMap.keys()) {
            if (!botPositions.find(p => p.symbol === sym)) tradeStatusMap.delete(sym);
        }

        // ── 3. Monitor Active Limit Orders ──────────────────────────────────
        for (const order of activeLimitOrders.values()) {
            const cp = prices[order.symbol];
            if (!cp) continue;

            const current = parseFloat(cp.price);
            const limit = order.limitPrice;
            const isHit = order.side === 'BUY' ? current <= limit : current >= limit;

            if (isHit) {
                // Price hit! Check Binance for real status
                checkBinanceOrderStatus(order.symbol, order.orderId);
            }
        }

        // 4c. Spring Boot sell history — from SELL Redis hash
        //     Sell fields: sellingPoint (sell price), status, timestamp, sellMaxPercentage, buy (nested)
        const botSells = Object.entries(botSellsRaw)
            .map(([symbol, sell]) => {
                const sellPrice = parseFloat(sell?.sellingPoint ?? 0);
                const buyPrice  = parseFloat(
                    sell?.buy?.buyPrice?.value ?? sell?.buy?.buyPrice ?? 0
                );
                const sellPct   = parseFloat(sell?.buy?.selP ?? 0);
                const pnlPct    = (buyPrice > 0 && sellPrice > 0)
                    ? parseFloat(((sellPrice - buyPrice) / buyPrice * 100).toFixed(3))
                    : null;
                const pnlUsdt   = (buyPrice > 0 && sellPrice > 0)
                    ? parseFloat(((sellPrice - buyPrice) / buyPrice * 12).toFixed(4))  // $12 position
                    : 0;
                const ts        = sell?.timestamp ?? null;
                return {
                    symbol,
                    sellPrice,
                    buyPrice,
                    sellPct,
                    pnlPct,
                    pnlUsdt,
                    pnlInr: parseFloat((pnlUsdt * USDT_INR_RATE).toFixed(2)),
                    status: sell?.status ?? 'Y',
                    ts,
                };
            })
            .filter(s => s.sellPrice > 0)
            .sort((a, b) => {
                // Sort by timestamp descending (most recent first)
                if (a.ts && b.ts) return b.ts - a.ts;
                return 0;
            });


        // 5. Account Data (from Binance Worker)
        console.log("[Data Flow] Step 4: Reading Binance Account Data from Redis...");
        const [binanceOrdersRaw, binanceTradesRaw, binanceOrderListsRaw, binanceBalancesRaw, usdtFree] = await Promise.all([
            redis.get('BINANCE:OPEN_ORDERS:ALL'),
            redis.get('BINANCE:TRADE_HISTORY:ALL'),
            redis.get('BINANCE:ORDER_LISTS:ALL'),
            redis.get('BINANCE:BALANCES:ALL'),
            redis.get('BINANCE:BALANCE:USDT')
        ]);

        const openOrders = binanceOrdersRaw ? JSON.parse(binanceOrdersRaw) : [];
        const tradeHistory = binanceTradesRaw ? JSON.parse(binanceTradesRaw) : [];
        const orderLists = binanceOrderListsRaw ? JSON.parse(binanceOrderListsRaw) : [];
        const balances = binanceBalancesRaw ? JSON.parse(binanceBalancesRaw) : [];

        // Enrich balances with current prices to calculate USDT value
        balances.forEach(b => {
            if (b.asset === 'USDT') {
                b.valueUsdt = (parseFloat(b.free) + parseFloat(b.locked)).toFixed(2);
            } else {
                const price = currentPriceOf(b.asset + 'USDT', priceHash);
                if (price > 0) {
                    b.valueUsdt = ((parseFloat(b.free) + parseFloat(b.locked)) * price).toFixed(2);
                } else {
                    b.valueUsdt = '0.00';
                }
            }
        });
        
        console.log(`[Data Flow] Step 4 Info: Fetched ${balances.length} assets, ${openOrders.length} orders, ${tradeHistory.length} trades.`);

        let usdtAmount = 0;
        if (usdtFree) {
            try {
                const parsed = JSON.parse(usdtFree);
                usdtAmount = parseFloat(parsed.free || 0);
            } catch (e) {
                usdtAmount = parseFloat(usdtFree); // Fallback if it's still a plain string
            }
        }

        const stats = {
            fastCount: allCoins.length,
            positions: positions.size,
            botPositions: botPositions.length,
            botSells: botSells.length,
            totalPnL: parseFloat(totalPnL.toFixed(4)),
            usdtBalance: usdtAmount,
            autoEnabled: autoConfig.enabled,
            simulation: autoConfig.simulationMode,
            redisOk: true,
            walletAssets: balances.length,
            ts: now(),
        };

        // 6. Auto-trade (runs on ALL fast coins, not just displayed ones)
        await runAutoTrade(fastCoins, priceHash);

        // Update stat to show total detected vs displayed
        stats.fastCount = Object.keys(fastCoins).length;
        stats.fastDisplayed = displayed.length;

        console.log(`[Data Flow] Step 5: Broadcasting update...`);
        const behavioralSentiment = await getAdaptiveSentiment();
        const volumeScores = await getVolumeScores();
        
        // [AUDIT] Line 630: Final broadcast packet sent to all connected dashboards
        io.emit('update', { 
            coins: displayed, 
            activePositions: Array.from(positions.values()), 
            botPositions, 
            botSells, 
            openOrders, 
            tradeHistory,
            orderLists,
            balances, 
            stats, 
            behavioralSentiment,
            volumeScores,
            ts: now() 
        });

    } catch (e) {
        console.error('[Tick]', e.message);
    }
}

async function getAdaptiveSentiment() {
    try {
        const keys = await redis.keys('sentiment:market:adaptive:*');
        if (keys.length === 0) return {};
        const pipeline = redis.pipeline();
        keys.forEach(k => pipeline.get(k));
        const results = await pipeline.exec();
        const data = {};
        results.forEach((r, i) => {
            if (r && r[1]) {
                const parsed = JSON.parse(r[1]);
                data[parsed.symbol.replace('/USDT', '').replace('/', '')] = parsed;
            }
        });
        return data;
    } catch (e) {
        console.error('[Adaptive Sentiment] Error:', e.message);
        return {};
    }
}

async function getVolumeScores() {
    try {
        const raw = await redis.hgetall('VOLUME_SCORE');
        if (!raw || Object.keys(raw).length === 0) return {};
        const data = {};
        for (const [field, val] of Object.entries(raw)) {
            try {
                const parsed = JSON.parse(val);
                // Key by coin name without USDT (e.g. "KITE")
                const coinKey = field.replace('USDT', '');
                data[coinKey] = parsed;
            } catch { /* skip malformed entries */ }
        }
        return data;
    } catch (e) {
        console.error('[Volume Scores] Error:', e.message);
        return {};
    }
}

setInterval(tick, POLL_MS);

// Serve Pro Dashboard (Enterprise Terminal)
app.get('/pro', (req, res) => res.sendFile(path.join(__dirname, 'public', 'pro.html')));

// BTC Price Proxy (via Spring Boot)
app.get('/api/btc-price', async (req, res) => {
    try {
        const response = await fetch(`${SPRING_BASE}/binance/btc-price`);
        const data = await response.json();
        res.json(data);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// Order History Proxy
app.get('/api/trade/order-history', async (req, res) => {
    try {
        const { symbol } = req.query;
        const response = await fetch(`${SPRING_BASE}/binance/trade/order-history?symbol=${symbol}`);
        const data = await response.json();
        res.json(data);
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

// Order List Proxy (OCO)
app.get('/api/trade/order-list', async (req, res) => {
    try {
        const { startTime, endTime, limit } = req.query;
        let url = `${SPRING_BASE}/binance/trade/order-list?limit=${limit || 500}`;
        if (startTime) url += `&startTime=${startTime}`;
        if (endTime) url += `&endTime=${endTime}`;

        console.log(`[Proxy] GET Order List: ${url}`);
        const response = await fetch(url);
        const data = await response.json();
        res.json(data);
    } catch (e) {
        console.error(`[Proxy] Order List Error: ${e.message}`);
        res.status(500).json({ error: e.message });
    }
});


// ─── REST API ─────────────────────────────────────────────────────────────────
// ─── Binance Account APIs (Node-native) ───────────────────────────────────────
// 1. Get Open Orders
app.get('/api/open-orders', async (req, res) => {
    const symbol = req.query.symbol;
    const key = symbol ? `BINANCE:OPEN_ORDERS:${symbol}` : 'BINANCE:OPEN_ORDERS:ALL';
    
    try {
        const raw = await redis.get(key);
        let orders = raw ? JSON.parse(raw) : [];

        // Check against DELIST list for safety
        const delistRaw = await redis.get('BINANCE:DELIST');
        const delisted = delistRaw ? JSON.parse(delistRaw) : [];

        orders = orders.map(o => ({
            ...o,
            atRisk: delisted.includes(o.symbol)
        }));

        res.json(orders);
    } catch (err) {
        res.status(500).json({ error: 'Failed to fetch open orders' });
    }
});

// 2. Get Trade History
app.get('/api/trade-history', async (req, res) => {
    const symbol = req.query.symbol;
    const key = symbol ? `BINANCE:TRADE_HISTORY:${symbol}` : 'BINANCE:TRADE_HISTORY:ALL';
    
    try {
        const raw = await redis.get(key);
        const trades = raw ? JSON.parse(raw) : [];
        res.json(trades);
    } catch (err) {
        res.status(500).json({ error: 'Failed to fetch trade history' });
    }
});

// 3. Cancel Order (Node-native)
app.post('/api/open-orders/cancel', async (req, res) => {
    const { symbol, orderId } = req.body;
    if (!symbol || !orderId) return res.status(400).json({ error: 'symbol + orderId required' });

    console.log(`[API] Node Cancel Order | symbol=${symbol} orderId=${orderId}`);
    
    try {
        const data = await binanceWorker.binanceFetch('/api/v3/order', 'DELETE', { symbol, orderId });
        if (data && data.status === 'CANCELED') {
            // Remove from Redis cache immediately for better UX
            const key = `BINANCE:OPEN_ORDERS:${symbol}`;
            const raw = await redis.get(key);
            if (raw) {
                let orders = JSON.parse(raw);
                orders = orders.filter(o => o.orderId !== orderId);
                await redis.set(key, JSON.stringify(orders));
            }
            // Also update the ALL list
            const allRaw = await redis.get('BINANCE:OPEN_ORDERS:ALL');
            if (allRaw) {
                let allOrders = JSON.parse(allRaw);
                allOrders = allOrders.filter(o => o.orderId !== orderId);
                await redis.set('BINANCE:OPEN_ORDERS:ALL', JSON.stringify(allOrders));
            }

            io.emit('order-cancelled', { symbol, orderId });
            return res.json({ ok: true, data });
        }
        res.status(400).json({ error: 'Cancel failed', detail: data });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// Get/update auto-trade config
app.get('/api/config', (req, res) => res.json(autoConfig));
app.post('/api/config', (req, res) => {
    autoConfig = { ...autoConfig, ...req.body };
    console.log('[Config]', autoConfig);
    io.emit('config', autoConfig);
    res.json({ ok: true, config: autoConfig });
});

// ─── Dust Transfer (Convert to BNB) ──────────────────────────────────────────
app.post('/api/wallet/dust-transfer', async (req, res) => {
    const { asset } = req.body;
    if (!asset) return res.status(400).json({ error: 'asset required' });

    console.log(`[API] Dust Transfer | asset=${asset}`);
    try {
        // Binance API: POST /sapi/v1/asset/dust (asset can be a comma-separated list)
        const data = await binanceWorker.binanceFetch('/sapi/v1/asset/dust', 'POST', { asset });
        if (data && data.transferResult && data.transferResult.length > 0) {
            return res.json({ ok: true, data });
        }
        res.status(400).json({ error: 'Transfer failed', detail: data });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

// ─── Manual Limit-Buy ─────────────────────────────────────────────────────────
app.post('/api/buy', async (req, res) => {
    // Support both field names to prevent breakage
    const symbol = req.body.symbol;
    const limitPrice = req.body.limitPrice || req.body.price;
    const targetPrice = req.body.targetPrice || req.body.target;
    const customSelPct = req.body.selPct;

    console.log(`[API] POST /api/buy | symbol=${symbol} limitPrice=${limitPrice} targetPrice=${targetPrice}`);
    
    const p = parseFloat(limitPrice);
    const q = req.body.qty || (12 / p); // Use provided qty or $12 default
    const selPct = customSelPct || 2.0;

    if (!symbol || !p) return res.status(400).json({ error: 'symbol + price required' });
    if (positions.has(symbol)) return res.status(409).json({ error: 'Position already open' });

    // ── 1. Cascade to Spring Boot (handles real Binance orders) ──────────────
    try {
        const url = `${SPRING_BASE}/order/limit-buy/${encodeURIComponent(symbol)}` +
                    `?limitPrice=${p}&targetPrice=${targetPrice || 0}&profitPct=${selPct}`;
        
        console.log(`[Spring Boot] POST /order/limit-buy | symbol=${symbol} price=${p}`);
        const sbRes = await Promise.race([
            fetch(url),
            new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), 4000)),
        ]);

        if (!sbRes.ok) {
            const errText = await sbRes.text();
            throw new Error(errText || `Spring Boot returned ${sbRes.status}`);
        }

        const text = await sbRes.text();
        if (!text) throw new Error('Spring Boot returned an empty response');
        
        const data = JSON.parse(text);
        if (!data || !data.orderId) throw new Error('Spring Boot response missing orderId');

        const orderId       = data.orderId;
        const limitBuyPrice = p;
        const target        = targetPrice || (limitBuyPrice * (1 + selPct / 100));
        const stop          = limitBuyPrice * (1 - (autoConfig.stopLossPct || 0.8) / 100);

        // Register for monitoring
        activeLimitOrders.set(orderId, {
            orderId, symbol, side: 'BUY', limitPrice: p, qty: q, status: 'NEW', time: now(), source: 'MANUAL'
        });

        positions.set(symbol, {
            symbol, buyPrice: limitBuyPrice, qty: q,
            buyTime: now(), target, stop, signal: 'LIMIT_MANUAL', orderId,
            status: 'NEW'
        });

        const trade = { symbol, action: 'BUY', price: limitBuyPrice, qty: q,
                        target, stop, time: now(), status: data.status || 'NEW', simulation: false,
                        orderId, executedQty: data.executedQty, origQty: data.origQty,
                        note: `Manual Limit @ ${limitBuyPrice}` };
        tradeLog.unshift(trade);
        io.emit('trade', trade);
        io.emit('order-update', activeLimitOrders.get(orderId));
        console.log(`[Server] Buy Registered: ${symbol} #${orderId}`);

        setTimeout(() => tick().catch(e => console.error('[Refresh]', e.message)), 100);
        return res.json({ ok: true, trade, source: 'spring_boot', orderId });
    } catch (e) {
        console.warn(`[Server] Spring Boot unreachable (${e.message}), writing Redis directly`);
    }

    // ── 2. Fallback: record in Redis BUY hash directly ────────────────────────
    const limitBuyPrice = p;
    const target        = targetPrice || (limitBuyPrice * (1 + selPct / 100));
    const stop          = limitBuyPrice * (1 - (autoConfig.stopLossPct || 0.8) / 100);
    const hms           = new Date().toLocaleTimeString('en-GB');

    positions.set(symbol, {
        symbol, buyPrice: limitBuyPrice, qty: q,
        buyTime: now(), target, stop, signal: 'LIMIT_MANUAL',
        status: 'NEW'
    });

    const buyRecord = {
        buyPrice:    limitBuyPrice,
        selP:        targetPrice ? parseFloat(((targetPrice - p) / p * 100).toFixed(2)) : selPct,
        status:      'NEW',
        hms,
        source:      'DASHBOARD',
        limitOffset: 0,
    };

    try {
        await redis.hset(BUY_KEY, symbol, JSON.stringify(buyRecord));
        console.log(`[Redis] BUY HSET ${symbol} manualLimit@${limitBuyPrice.toFixed(6)} target@${target.toFixed(6)}`);
        setTimeout(() => tick().catch(e => console.error('[Refresh]', e.message)), 100);
    } catch (e) {
        console.warn(`[Redis] BUY write failed: ${e.message}`);
    }

    const trade = { symbol, action: 'BUY', price: limitBuyPrice, qty: q, target, stop,
                    time: now(), status: 'NEW', simulation: false,
                    note: `Manual Limit @ ${limitBuyPrice}` };
    tradeLog.unshift(trade);
    io.emit('trade', trade);
    res.json({ ok: true, trade, source: 'redis_fallback' });
});

// Manual sell
app.post('/api/sell', async (req, res) => {
    const { symbol, price } = req.body;
    console.log(`[API] POST /api/sell | symbol=${symbol} price=${price}`);
    if (!symbol || !price) return res.status(400).json({ error: 'symbol + price required' });
    
    try {
        const url = `${SPRING_BASE}/sell/${encodeURIComponent(symbol)}`;
        console.log(`[Spring Boot] GET /sell/${symbol}`);
        const sbRes = await fetch(url);
        if (sbRes.ok) {
            positions.delete(symbol);
            io.emit('trade', { symbol, action: 'SELL', price, time: now() });
            setTimeout(() => tick().catch(e => console.error('[Refresh]', e.message)), 100);
            return res.json({ ok: true, message: 'SELL order sent via Spring Boot' });
        }
    } catch (e) { /* fall through to direct fallback */ }

    let pos = positions.get(symbol);
    if (!pos) {
        try {
            const raw = await redis.hget(BUY_KEY, symbol);
            if (raw) {
                const buy = JSON.parse(raw);
                pos = {
                    symbol,
                    buyPrice: parseFloat(buy.buyPrice?.value ?? buy.buyPrice ?? 0),
                    qty: 12 / parseFloat(buy.buyPrice?.value ?? buy.buyPrice ?? 1),
                    buyTime: Date.now(),
                    target: 0, stop: 0, source: 'BOT'
                };
            }
        } catch (e) { console.error('[Redis] Lookup failed in /api/sell', e); }
    }

    if (!pos || !pos.buyPrice) return res.status(404).json({ error: 'No open position found for ' + symbol });

    const p        = parseFloat(price);
    const pnlPct   = ((p - pos.buyPrice) / pos.buyPrice * 100).toFixed(2);
    const realized = ((p - pos.buyPrice) * pos.qty).toFixed(4);
    totalPnL += parseFloat(realized);
    positions.delete(symbol);

    try {
        const hms = new Date().toLocaleTimeString('en-GB');
        const sellRecord = {
            sellingPoint: String(p), buyPrice: String(pos.buyPrice), pnlPct, pnlUsdt: realized,
            status: 'Y', source: 'DASHBOARD', timestamp: { time: Date.now() }, hms,
        };
        await redis.hset(SELL_KEY, symbol, JSON.stringify(sellRecord));
        await redis.hdel(BUY_KEY, symbol);
        console.log(`[Redis] SELL HSET ${symbol} @ ${p} | P&L ${pnlPct}%`);
        setTimeout(() => tick().catch(e => console.error('[Refresh]', e.message)), 100);
    } catch (e) {
        console.warn(`[Redis] SELL write failed: ${e.message}`);
    }

    const trade = { symbol, action: 'SELL', price: p, qty: pos.qty, buyPrice: pos.buyPrice, pnlPct, realizedPnL: realized, time: now(), status: 'CLOSED', simulation: false, reason: 'MANUAL' };
    tradeLog.unshift(trade);
    io.emit('trade', trade);
    res.json({ ok: true, trade });
});

app.post('/api/order/cancel', async (req, res) => {
    const { symbol, orderId } = req.body;
    console.log(`[API] POST /api/order/cancel | symbol=${symbol} orderId=${orderId}`);
    if (!symbol || !orderId) return res.status(400).json({ error: 'symbol + orderId required' });

    try {
        const url = `${SPRING_BASE}/order/cancel/${encodeURIComponent(symbol)}/${orderId}`;
        console.log(`[Spring Boot] GET /order/cancel/${symbol}/${orderId}`);
        const sbRes = await fetch(url);
        const msg = await sbRes.text();

        if (sbRes.ok) {
            activeLimitOrders.delete(orderId);
            positions.delete(symbol);
            try { await redis.hdel(BUY_KEY, symbol); } catch (e) {}
            io.emit('order-update', { orderId, symbol, status: 'CANCELED' });
            return res.json({ ok: true, message: msg });
        } else {
            return res.status(sbRes.status).json({ error: msg });
        }
    } catch (err) {
        console.error('[Server] Cancel error:', err.message);
        res.status(500).json({ error: 'Failed to proxy cancel' });
    }
});

app.get('/api/analyze/:symbol', async (req, res) => {
    const { symbol } = req.params;
    console.log(`[API] GET /api/analyze/${symbol}`);

    try {
        const url = `${SPRING_BASE}/analyze/${encodeURIComponent(symbol)}`;
        console.log(`[Spring Boot] GET ${url}`);
        const sbRes = await Promise.race([
            fetch(url),
            new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), 5000)),
        ]);
        if (sbRes.ok) {
            const data = await sbRes.json();
            return res.json({ ok: true, source: 'spring_boot', analysis: data });
        }
    } catch (_) { /* fall through to direct Binance */ }

    // ── Fallback: call public Binance API directly ───────────────────────────
    try {
        const [klinesRes, tickerRes] = await Promise.all([
            fetch(`https://api.binance.com/api/v3/klines?symbol=${symbol}&interval=1d&limit=60`),
            fetch(`https://api.binance.com/api/v3/ticker/24hr?symbol=${symbol}`),
        ]);
        const klines = await klinesRes.json();
        const ticker = await tickerRes.json();

        if (!Array.isArray(klines) || klines.length < 14) {
            return res.json({ ok: false, error: 'Not enough candle data from Binance' });
        }

        // kline format: [openTime, open, high, low, close, volume, closeTime, quoteVol, ...]
        const closes    = klines.map(k => parseFloat(k[4]));
        const highs     = klines.map(k => parseFloat(k[2]));
        const lows      = klines.map(k => parseFloat(k[3]));
        const quoteVols = klines.map(k => parseFloat(k[7]));   // USDT volume

        const high2m = Math.max(...highs);
        const low2m  = Math.min(...lows);
        const avg2m  = closes.reduce((a, b) => a + b, 0) / closes.length;
        const n      = closes.length;

        const avgClose = (from, to) => {
            const sl = closes.slice(Math.max(0, from), to);
            return sl.length ? sl.reduce((a, b) => a + b, 0) / sl.length : 0;
        };

        const trend7d  = avgClose(n-14, n-7) ? ((avgClose(n-7, n) - avgClose(n-14, n-7)) / avgClose(n-14, n-7) * 100) : 0;
        const trend30d = avgClose(n-60, n-30) ? ((avgClose(n-30, n) - avgClose(n-60, n-30)) / avgClose(n-60, n-30) * 100) : 0;

        const vol24h     = parseFloat(ticker.quoteVolume || 0);
        const vol30dAvg  = quoteVols.slice(-30).reduce((a, b) => a + b, 0) / 30;
        const vol7dAvg   = quoteVols.slice(-7).reduce((a, b) => a + b, 0) / 7;
        const volRatio   = vol30dAvg > 0 ? vol24h / vol30dAvg : 1;
        const currentPrice = parseFloat(ticker.lastPrice || 0);
        const pricePos   = (high2m - low2m) > 0 ? ((currentPrice - low2m) / (high2m - low2m) * 100) : 50;

        // Scoring (0–7)
        let score = 0; const reasons = [];
        if (currentPrice < avg2m)  { score++; reasons.push('✅ Price below 2m avg'); }
        else                       { reasons.push('⚠️ Price above 2m avg'); }
        if (pricePos < 40)         { score++; reasons.push('✅ Lower 40% of 2m range'); }
        else if (pricePos > 80)    { reasons.push('🔴 Near 2m high (risky)'); }
        if (trend7d > 0)           { score++; reasons.push(`✅ 7d trend +${trend7d.toFixed(2)}%`); }
        else                       { reasons.push(`⚠️ 7d trend ${trend7d.toFixed(2)}%`); }
        if (trend30d > 0)          { score++; reasons.push(`✅ 30d trend +${trend30d.toFixed(2)}%`); }
        else                       { reasons.push(`⚠️ 30d trend ${trend30d.toFixed(2)}%`); }
        if (volRatio >= 1.5)       { score++; reasons.push(`✅ Volume ${volRatio.toFixed(1)}× above 30d avg`); }
        if (vol24h > vol7dAvg)     { score++; reasons.push('✅ Today volume > 7d avg'); }
        let volInc = 0;
        for (let i = n-6; i < n; i++) if (quoteVols[i] > quoteVols[i-1]) volInc++;
        if (volInc >= 3)           { score++; reasons.push(`✅ Volume rising ${volInc}/6 days`); }

        const rec = score >= 5 ? 'STRONG_BUY' : score === 4 ? 'BUY' : score === 3 ? 'NEUTRAL' : score === 2 ? 'WAIT' : 'DONT_BUY';

        const analysis = {
            symbol, currentPrice, high2m, low2m, avg2m,
            pricePosition: Math.round(pricePos * 100) / 100,
            trend7d: Math.round(trend7d * 100) / 100,
            trend30d: Math.round(trend30d * 100) / 100,
            vol24hUsdt: Math.round(vol24h),
            vol30dAvgUsdt: Math.round(vol30dAvg),
            vol7dAvgUsdt: Math.round(vol7dAvg),
            volumeRatio: Math.round(volRatio * 100) / 100,
            buyScore: score,
            recommendation: rec,
            shouldBuy: score >= 4,
            reason: reasons.join(' | '),
            daysAnalyzed: n,
        };

        let newsAnalysis = null;
        try {
            const coinName = symbol.replace('USDT', '');
            const rawNews = await redis.get(`analysis:${coinName}:detailed`);
            if (rawNews) {
                newsAnalysis = JSON.parse(rawNews);
                analysis.newsAnalysis = newsAnalysis;
            }
        } catch(e) {}

        return res.json({ ok: true, source: 'binance_direct', analysis });

    } catch (e) {
        return res.status(500).json({ ok: false, error: e.message });
    }
});

// ─── Debug: dump all Redis keys + fast-move hashes ───────────────────────────
app.get('/api/debug/redis', async (req, res) => {
    try {
        const allKeys = await redis.keys('*');
        const keyInfo = {};
        for (const k of allKeys) {
            const type = await redis.type(k);
            if (type === 'hash') {
                const len = await redis.hlen(k);
                const fields = len <= 5 ? await redis.hgetall(k) : `(${len} fields)`;
                keyInfo[k] = { type, len, fields };
            } else if (type === 'string') {
                const val = await redis.get(k);
                keyInfo[k] = { type, val: val?.substring(0, 100) };
            } else {
                keyInfo[k] = { type };
            }
        }
        res.json({
            totalKeys: allKeys.keys,
            keys: allKeys,
            details: keyInfo,
            serverConstants: {
                FAST_MOVE_KEY, LT2MIN_KEY, UF_0_2_KEY, UF_2_3_KEY, UF_0_3_KEY,
                CURRENT_PRICE, BUY_KEY, SELL_KEY,
            },
        });
    } catch (e) {
        res.status(500).json({ error: e.message });
    }
});

app.get('/api/trades', (req, res) => res.json({ trades: tradeLog, totalPnL }));



// ─── Bot Manual Sell (from dashboard Bot Positions panel) ─────────────────────

/**
 * POST /api/bot/sell
 * Body: { symbol: "SOLUSDT" }
 *
 * Flow:
 *   1. Try Spring Boot GET /api/v1/sell/{symbol}  (handles TradeState + Redis correctly)
 *   2. If Spring Boot is unreachable, fall back to direct Redis manipulation:
 *      - Read current price from CURRENT_PRICE hash
 *      - Write a SELL record to SELL hash
 *      - Delete symbol from BUY hash
 *   3. Emit 'bot-sell' socket event so all dashboard tabs update immediately
 */
app.post('/api/bot/sell', async (req, res) => {
    const { symbol } = req.body;
    if (!symbol) return res.status(400).json({ ok: false, error: 'symbol required' });

    // ── Step 1: Try Spring Boot sell endpoint ────────────────────────────────
    try {
        const sbRes = await Promise.race([
            fetch(`${SPRING_BASE}/sell/${encodeURIComponent(symbol)}`),
            new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), 3000)),
        ]);
        if (sbRes.ok) {
            const msg = await sbRes.text();
            console.log(`[BotSell] Spring Boot sold ${symbol}: ${msg}`);
            positions.delete(symbol);
            io.emit('bot-sell', { symbol, source: 'SPRING_BOOT' });
            return res.json({ ok: true, symbol, source: 'spring_boot', message: msg });
        }
        console.warn(`[BotSell] Spring Boot sell returned ${sbRes.status}, falling back to Redis`);
    } catch (e) {
        console.warn(`[BotSell] Spring Boot unreachable (${e.message}), falling back to Redis`);
    }

    // ── Step 2: Fallback — manipulate Redis directly ─────────────────────────
    try {
        // Read buy record
        const buyRaw = await redis.hget(BUY_KEY, symbol);
        const buy    = safeJson(buyRaw);

        // Read current price
        const priceRaw  = await redis.hget(CURRENT_PRICE, symbol);
        const priceData = safeJson(priceRaw);
        const sellPrice = typeof priceData === 'object'
            ? parseFloat(priceData?.currentPrice ?? priceData?.price ?? 0)
            : parseFloat(priceData) || 0;

        const buyPrice = parseFloat(buy?.buyPrice?.value ?? buy?.buyPrice ?? 0);
        const pnlPct   = (buyPrice > 0 && sellPrice > 0)
            ? ((sellPrice - buyPrice) / buyPrice * 100)
            : 0;

        // Write sell record (plain JSON — dashboard will read it)
        const sellRecord = {
            sellingPoint: String(sellPrice),
            buyPrice:     String(buyPrice),
            buy,
            pnlPct:       pnlPct.toFixed(3),
            status:       'Y',
            source:       'MANUAL_DASHBOARD',
            timestamp:    { time: Date.now() },
        };
        await redis.hset(SELL_KEY, symbol, JSON.stringify(sellRecord));
        await redis.hdel(BUY_KEY, symbol);
        positions.delete(symbol);

        console.log(`[BotSell] Redis direct sell ${symbol} @ ${sellPrice} | P&L ${pnlPct.toFixed(3)}%`);
        io.emit('bot-sell', { symbol, sellPrice, buyPrice, pnlPct: pnlPct.toFixed(3), source: 'REDIS_FALLBACK' });
        return res.json({ ok: true, symbol, sellPrice, buyPrice, pnlPct: pnlPct.toFixed(3), source: 'redis_fallback' });

    } catch (err) {
        console.error('[BotSell] Error:', err.message);
        return res.status(500).json({ ok: false, error: err.message });
    }
});



/**
 * POST /api/redis/flush
 * Deletes ALL keys in every Redis database (FLUSHALL),
 * then disconnects and reconnects the ioredis client so
 * the polling loop resumes cleanly without a server restart.
 */
app.post('/api/redis/flush', async (req, res) => {
    try {
        await redis.flushall();
        
        // Also clear internal Node.js memory state
        positions.clear();
        tradeLog.length = 0;
        totalPnL = 0;
        activeLimitOrders.clear();
        tradeStatusMap.clear();

        console.log('[Redis] FLUSHALL executed ✅ — Internal state cleared.');

        io.emit('redis-flushed', { ts: Date.now() });
        res.json({ ok: true, message: 'All Redis keys and internal state deleted.' });
    } catch (err) {
        console.error('[Redis] Flush error:', err.message);
        res.status(500).json({ ok: false, error: err.message });
    }
});

// ─── Spring Boot Service Control ─────────────────────────────────────────────

/**
 * GET /api/service/status
 * Returns { running: boolean } — checks if port 8083 is answering.
 */
app.get('/api/service/status', async (req, res) => {
    try {
        const r = await Promise.race([
            fetch(`${SPRING_BASE}/health`),
            new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), 2000)),
        ]);
        res.json({ running: r.ok || r.status < 500 });
    } catch {
        res.json({ running: false });
    }
});

/**
 * POST /api/service/stop
 * Calls the Spring Boot /stop endpoint, then SIGKILL any mvn process
 * started by this dashboard (so it never leaves orphans).
 */
app.post('/api/service/stop', async (req, res) => {
    try {
        // Ask Spring Boot to shut itself down gracefully
        try {
            await fetch(`${SPRING_BASE}/stop`, { method: 'GET' });
            console.log('[Spring] /stop called ✅');
        } catch (e) {
            console.warn('[Spring] /stop unreachable (already down?):', e.message);
        }

        // Also kill our own spawned process if present
        if (springProc && !springProc.killed) {
            springProc.kill('SIGTERM');
            setTimeout(() => { if (springProc && !springProc.killed) springProc.kill('SIGKILL'); }, 4000);
        }

        io.emit('service-status', { running: false });
        res.json({ ok: true, message: 'Spring Boot stop signal sent.' });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
});

/**
 * POST /api/service/start
 * Spawns `mvn spring-boot:run` inside book-now-v3/.
 * If the service is already listening on port 8083, returns 409.
 */
app.post('/api/service/start', async (req, res) => {
    // Check if already running
    try {
        const probe = await Promise.race([
            fetch(`${SPRING_BASE}/health`),
            new Promise((_, rej) => setTimeout(() => rej(new Error('timeout')), 1500)),
        ]);
        if (probe.ok || probe.status < 500) {
            return res.status(409).json({ ok: false, error: 'Spring Boot is already running.' });
        }
    } catch { /* not running — proceed to start */ }

    try {
        springProc = spawn('mvn', ['spring-boot:run'], {
            cwd: SPRING_DIR,
            stdio: ['ignore', 'pipe', 'pipe'],
            env: { ...process.env },
        });

        springProc.stdout.on('data', c => process.stdout.write('[SpringBoot] ' + c));
        springProc.stderr.on('data', c => process.stderr.write('[SpringBoot] ' + c));
        springProc.on('exit', (code, sig) => {
            console.log(`[Spring] Process exited code=${code} sig=${sig}`);
            springProc = null;
            io.emit('service-status', { running: false });
        });
        springProc.on('error', err => {
            console.error('[Spring] Spawn error:', err.message);
            springProc = null;
        });

        // Give it a moment to fail fast (bad cwd / mvn not found)
        await new Promise(r => setTimeout(r, 700));
        if (!springProc || springProc.killed) {
            return res.status(500).json({ ok: false, error: 'mvn process failed to start. Is mvn in PATH and book-now-v3 present?' });
        }

        io.emit('service-status', { running: true, pid: springProc.pid });
        console.log('[Spring] Started ✅ pid:', springProc.pid);
        res.json({ ok: true, message: 'Spring Boot starting…', pid: springProc.pid });
    } catch (err) {
        res.status(500).json({ ok: false, error: err.message });
    }
});

// ─── Socket ───────────────────────────────────────────────────────────────────
io.on('connection', async socket => {
    console.log('[WS] Client:', socket.id);
    socket.emit('config', autoConfig);
    socket.emit('trades', { trades: tradeLog, totalPnL });
    socket.on('disconnect', () => console.log('[WS] Disconnected:', socket.id));
});

// ─── Start ───────────────────────────────────────────────────────────────────
redis.connect().catch(() => { });
server.listen(PORT, () => {
    console.log(`\n🚀 BookNow Fast Dashboard → http://localhost:${PORT}`);
    console.log(`📡 Polling Redis every ${POLL_MS}ms\n`);
});
