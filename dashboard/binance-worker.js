/**
 * dashboard/binance-worker.js
 * ─────────────────────────────────────────────────────────────────────────────
 * Background worker to fetch Open Orders and Trade History from Binance.
 * Caches data in Redis for the UI.
 */

const crypto = require('crypto');
const Redis = require('ioredis');
require('dotenv').config();

const API_KEY = process.env.BINANCE_API_KEY;
const SECRET_KEY = process.env.BINANCE_SECRET_KEY;
const BASE_URL = 'https://api.binance.com';

const WebSocket = require('ws'); // Need to check if 'ws' is available

const redis = new Redis({
    host: process.env.REDIS_HOST || '127.0.0.1',
    port: process.env.REDIS_PORT || 6379
});

let userStreamWs = null;
let listenKey = null;
let io = null; // Socket.io instance passed from server.js

/**
 * Generate HMAC SHA256 signature for Binance API
 */
function getSignature(queryString) {
    return crypto
        .createHmac('sha256', SECRET_KEY)
        .update(queryString)
        .digest('hex');
}

/**
 * Generic Binance Signed API Caller
 */
async function binanceFetch(endpoint, method = 'GET', params = {}) {
    const timestamp = Date.now();
    const query = new URLSearchParams({ ...params, timestamp, recvWindow: 60000 }).toString();
    const signature = getSignature(query);
    const url = `${BASE_URL}${endpoint}?${query}&signature=${signature}`;

    console.log(`[Binance API Request] ${method} ${endpoint} (params: ${JSON.stringify(params)})`);
    try {
        const res = await fetch(url, {
            method,
            headers: { 'X-MBX-APIKEY': API_KEY }
        });
        const data = await res.json();
        if (!res.ok) {
            console.error(`[Binance API Response Error] ${endpoint}:`, data);
            throw new Error(data.msg || 'Binance API Error');
        }
        console.log(`[Binance API Response Success] ${endpoint}: Received ${Array.isArray(data) ? data.length : 'object'} data points.`);
        return data;
    } catch (err) {
        console.error(`[Binance Worker Network Error] ${endpoint}:`, err.message);
        return null;
    }
}

/**
 * 1. Fetch Open Orders
 * Polls every 10s. Fetches for all symbols (no symbol param).
 */
async function refreshOpenOrders() {
    console.log('[Data Flow] Step 1: Refreshing Open Orders from Binance...');
    const orders = await binanceFetch('/api/v3/openOrders');
    if (!orders) return;

    const grouped = {};
    orders.forEach(o => {
        if (!grouped[o.symbol]) grouped[o.symbol] = [];
        grouped[o.symbol].push({
            orderId: o.orderId,
            price: o.price,
            quantity: o.origQty,
            executedQty: o.executedQty,
            status: o.status,
            type: o.type,
            time: o.time
        });
    });

    const pipeline = redis.pipeline();
    pipeline.set('BINANCE:OPEN_ORDERS:ALL', JSON.stringify(orders));
    
    Object.keys(grouped).forEach(symbol => {
        pipeline.set(`BINANCE:OPEN_ORDERS:${symbol}`, JSON.stringify(grouped[symbol]));
    });
    
    await pipeline.exec();
    console.log(`[Data Flow] Step 1 Complete: Cached ${orders.length} orders in Redis.`);
}

/**
 * 2. Fetch All Order Lists (OCO)
 */
async function refreshOrderLists() {
    const lists = await binanceFetch('/api/v3/allOrderList', 'GET', { limit: 100 });
    if (lists) {
        await redis.set('BINANCE:ORDER_LISTS:ALL', JSON.stringify(lists));
    }
}

/**
 * 2b. Silent Trade History Hunter
 * [AUDIT] This finds regular trades (DOGE, ZBT, etc.) SILENTLY.
 */
async function refreshTradeHistory() {
    const balancesRaw = await redis.get('BINANCE:BALANCES:ALL');
    if (!balancesRaw) return;
    
    const assets = JSON.parse(balancesRaw);
    const symbols = assets.map(a => a.asset + 'USDT').filter(s => s !== 'USDTUSDT' && s.length > 4);

    const allTrades = [];
    for (const symbol of symbols) {
        try {
            const trades = await binanceFetch('/api/v3/myTrades', 'GET', { symbol, limit: 50 });
            if (Array.isArray(trades)) {
                allTrades.push(...trades);
                await redis.set(`BINANCE:TRADE_HISTORY:${symbol}`, JSON.stringify(trades));
            }
        } catch (err) {
            // [AUDIT] SILENT SKIP: No logs for invalid symbols.
        }
        await new Promise(r => setTimeout(r, 100));
    }

    if (allTrades.length > 0) {
        allTrades.sort((a, b) => b.time - a.time);
        await redis.set('BINANCE:TRADE_HISTORY:ALL', JSON.stringify(allTrades.slice(0, 500)));
    }
}

/**
 * 3. Fetch Account Balances
 * Polls every 15s.
 */
async function refreshAccountBalances() {
    console.log('[Data Flow] Step 3: Refreshing Account Balances from Binance...');
    const accountInfo = await binanceFetch('/api/v3/account');
    if (!accountInfo || !accountInfo.balances) return;

    const balances = accountInfo.balances.filter(b => parseFloat(b.free) > 0 || parseFloat(b.locked) > 0);
    
    await redis.set('BINANCE:BALANCES:ALL', JSON.stringify(balances));
    
    const pipeline = redis.pipeline();
    balances.forEach(b => {
        const balanceObj = {
            asset: b.asset,
            free: b.free,
            locked: b.locked,
            updatedAt: Date.now()
        };
        pipeline.set(`BINANCE:BALANCE:${b.asset}`, JSON.stringify(balanceObj));
    });
    await pipeline.exec();

    console.log(`[Data Flow] Step 3 Complete: Cached ${balances.length} non-zero assets in Redis.`);
}

/**
 * 4. User Data Stream (WebSocket)
 */
async function startUserStream(socketIo) {
    io = socketIo;
    try {
        console.log('[UserStream] Initializing User Data Stream...');
        const res = await binanceFetch('/api/v3/userDataStream', 'POST');
        if (!res || !res.listenKey) {
            console.error('[UserStream] Failed to get listenKey. Falling back to polling.');
            return;
        }
        listenKey = res.listenKey;
        console.log(`[UserStream] Received listenKey: ${listenKey}`);

        connectWs();
        
        // Keep-alive every 30 minutes
        setInterval(async () => {
            console.log('[UserStream] Sending keep-alive...');
            await binanceFetch('/api/v3/userDataStream', 'PUT', { listenKey });
        }, 30 * 60 * 1000);

    } catch (err) {
        console.error('[UserStream] Error starting user stream:', err.message);
    }
}

function connectWs() {
    const wsUrl = `wss://stream.binance.com:9443/ws/${listenKey}`;
    console.log(`[UserStream] Connecting to ${wsUrl}...`);
    
    userStreamWs = new WebSocket(wsUrl);

    userStreamWs.on('open', () => {
        console.log('[UserStream] WebSocket Connected ✅');
    });

    userStreamWs.on('message', (data) => {
        const event = JSON.parse(data);
        handleUserEvent(event);
    });

    userStreamWs.on('error', (err) => {
        console.error('[UserStream] WebSocket Error:', err.message);
    });

    userStreamWs.on('close', () => {
        console.warn('[UserStream] WebSocket Closed. Reconnecting in 5s...');
        setTimeout(connectWs, 5000);
    });
}

function handleUserEvent(e) {
    if (e.e === 'executionReport') {
        console.log(`[UserStream] Order Update: ${e.s} ${e.S} ${e.X} (Price: ${e.p}, Qty: ${e.q})`);
        io.emit('order-execution', {
            symbol: e.s,
            side: e.S,
            orderId: e.i,
            status: e.X,
            price: e.p,
            qty: e.q,
            executedQty: e.z,
            time: e.T
        });
        // Refresh orders in Redis immediately
        refreshOpenOrders();
    } else if (e.e === 'outboundAccountPosition') {
        console.log('[UserStream] Account Balance Update');
        refreshAccountBalances();
    }
}

/**
 * Start Polling + WebSocket
 */
function start(socketIo) {
    io = socketIo;
    refreshOpenOrders();
    refreshOrderLists();
    refreshTradeHistory();
    refreshAccountBalances();

    setInterval(refreshOpenOrders, 10000);    // 10s fallback
    setInterval(refreshOrderLists, 60000);     // 60s
    setInterval(refreshTradeHistory, 60000);   // 60s silent hunter
    setInterval(refreshAccountBalances, 15000); // 15s
    
    startUserStream(socketIo);
}

module.exports = { start, binanceFetch };
