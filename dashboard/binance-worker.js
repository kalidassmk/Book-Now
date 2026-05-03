const WebSocket = require('ws');
const Redis = require('ioredis');
require('dotenv').config();

let API_KEY = process.env.BINANCE_API_KEY;
let SECRET_KEY = process.env.BINANCE_SECRET_KEY;
const BASE_URL = 'https://api.binance.com';

const redis = new Redis({
    host: process.env.REDIS_HOST || '127.0.0.1',
    port: process.env.REDIS_PORT || 6379
});

let listenKey = null;
let ws = null;
let io = null;
let executionCallback = null;
let keepAliveTimer = null;

// 24-hour ticker cache, populated by the !miniTicker@arr WebSocket stream.
// Replaces per-symbol REST calls to /api/v3/ticker/24hr (which were paid per
// symbol and capped by Binance's IP weight limit). The mini-ticker stream
// arrives once per second with every spot pair's close/volume — so reads
// from this map are O(1) and free.
//
// Shape matches the REST 24h-ticker response so existing call sites can
// drop in without reshaping their parsing logic:
//   { lastPrice, quoteVolume, openPrice, highPrice, lowPrice, volume, ts }
const tickerCache = new Map();
let tickerWs = null;
let tickerReconnectTimer = null;

async function loadCredentials() {
    const rApiKey = await redis.get('BINANCE_API_KEY');
    const rSecretKey = await redis.get('BINANCE_SECRET_KEY');
    
    API_KEY = rApiKey || process.env.BINANCE_API_KEY;
    SECRET_KEY = rSecretKey || process.env.BINANCE_SECRET_KEY;
    
    if (!API_KEY || !SECRET_KEY) {
        console.warn('⚠️ API Keys are missing! Check your .env file.');
    }
}

// 🔹 Step 1: Create Listen Key
async function createListenKey() {
    try {
        console.log('🔑 Requesting UserDataStream ListenKey...');
        const response = await fetch(`${BASE_URL}/api/v3/userDataStream`, {
            method: 'POST',
            headers: { 'X-MBX-APIKEY': API_KEY }
        });
        
        const rawText = await response.text();
        let data;
        try {
            data = JSON.parse(rawText);
        } catch (e) {
            console.error('❌ Failed to parse Binance response as JSON. Raw response:', rawText.substring(0, 500));
            throw new Error(`Invalid JSON response (Status: ${response.status})`);
        }
        
        if (!response.ok) throw new Error(data.msg || `Failed to create ListenKey (Status: ${response.status})`);
        
        listenKey = data.listenKey;
        console.log('✅ UserDataStream ListenKey created:', listenKey);
        return listenKey;
    } catch (err) {
        console.error('❌ Failed to create ListenKey:', err.message);
        if (err.message.includes('418')) {
            console.error('🚫 IP Banned (418). Cooling down for 60s before retry...');
            setTimeout(createListenKey, 60000);
        }
        return null;
    }
}

// 🔹 Step 2: Keep Alive (every 25 mins)
async function keepAlive() {
    if (!listenKey) return;
    try {
        const url = `${BASE_URL}/api/v3/userDataStream?listenKey=${listenKey}`;
        const response = await fetch(url, {
            method: 'PUT',
            headers: { 'X-MBX-APIKEY': API_KEY }
        });
        
        if (response.ok) {
            console.log('🔄 UserDataStream ListenKey refreshed');
        } else {
            const data = await response.json();
            throw new Error(data.msg || 'KeepAlive failed');
        }
    } catch (err) {
        console.error('⚠️ KeepAlive Error:', err.message);
        // If expired (404), try to recreate
        if (err.message.includes('not found') || err.message.includes('404')) {
            console.log('🔄 ListenKey expired, recreating...');
            const key = await createListenKey();
            if (key) connectWS();
        }
    }
}

// 🔹 Step 3: Connect WebSocket
function connectWS() {
    if (!listenKey) return;
    if (ws) {
        try { ws.terminate(); } catch (e) {}
    }

    console.log(`[WS] Connecting to User Data Stream: wss://stream.binance.com:9443/ws/${listenKey}`);
    ws = new WebSocket(`wss://stream.binance.com:9443/ws/${listenKey}`);

    ws.on('open', () => {
        console.log('🟢 User Data Stream Connected');
    });

    ws.on('message', async (data) => {
        try {
            const msg = JSON.parse(data);

            // 🔥 Handle Order Execution Updates
            if (msg.e === 'executionReport') {
                const executionData = { 
                    symbol: msg.s, 
                    orderId: msg.i, 
                    status: msg.X, 
                    executedQty: msg.z, 
                    price: msg.p 
                };
                console.log(`📈 Order Update: ${msg.s} | ${msg.X} | Qty: ${msg.z}`);
                
                await redis.set('BINANCE:LAST_ORDER_UPDATE', JSON.stringify(msg));
                if (io) io.emit('order-execution', executionData);
                if (executionCallback) executionCallback(executionData);
            }

            // 🔥 Handle Balance Updates
            if (msg.e === 'outboundAccountPosition' || msg.e === 'balanceUpdate') {
                console.log('💰 Balance Update Received');
                const balances = msg.B.map(b => ({
                    asset: b.a,
                    free: b.f,
                    locked: b.l
                })).filter(b => parseFloat(b.free) > 0 || parseFloat(b.locked) > 0);
                
                await redis.set('BINANCE:BALANCES:ALL', JSON.stringify(balances));
                if (io) io.emit('balance-update', balances);
            }

        } catch (err) {
            console.error('[WS] Message Error:', err.message);
        }
    });

    ws.on('close', () => {
        console.log('🔴 User Data Stream Closed. Reconnecting in 10s...');
        setTimeout(connectWS, 10000);
    });

    ws.on('error', (err) => {
        console.error('❌ WS Error:', err.message);
        if (err.message.includes('418')) {
            console.error('🚫 IP Banned (418). Cooling down for 60s...');
            setTimeout(connectWS, 60000);
        }
    });
}

// 🔹 Step 4: 24h Mini-Ticker Stream (replaces per-symbol REST /api/v3/ticker/24hr)
//
// The !miniTicker@arr stream pushes a snapshot of every spot pair's 24h
// rolling stats every second. We unpack and store a per-symbol entry so
// callers (e.g. server.js refreshAnalysisMetrics) can do a sync map lookup
// instead of issuing a REST call per symbol.
//
// Mini-ticker payload field mapping (Binance docs):
//   s = symbol           c = close (last) price
//   o = open price       h = high price
//   l = low price        v = base asset volume (24h)
//   q = quote asset volume (24h, USDT for *USDT pairs) ← what we mainly need
function connectTickerStream() {
    if (tickerWs) {
        try { tickerWs.terminate(); } catch (_) {}
    }
    const url = 'wss://stream.binance.com:9443/ws/!miniTicker@arr';
    console.log('[WS] Connecting to 24h mini-ticker stream:', url);
    tickerWs = new WebSocket(url);

    tickerWs.on('open', () => {
        console.log('🟢 24h mini-ticker stream connected');
    });

    tickerWs.on('message', (raw) => {
        try {
            const arr = JSON.parse(raw);
            if (!Array.isArray(arr)) return;
            const now = Date.now();
            for (const t of arr) {
                if (!t || !t.s) continue;
                tickerCache.set(t.s, {
                    lastPrice:   t.c,
                    quoteVolume: t.q,
                    openPrice:   t.o,
                    highPrice:   t.h,
                    lowPrice:    t.l,
                    volume:      t.v,
                    ts: now,
                });
            }
        } catch (err) {
            // Malformed frames are rare; don't spam the log.
        }
    });

    tickerWs.on('close', () => {
        console.log('🔴 24h mini-ticker stream closed. Reconnecting in 5s...');
        clearTimeout(tickerReconnectTimer);
        tickerReconnectTimer = setTimeout(connectTickerStream, 5000);
    });

    tickerWs.on('error', (err) => {
        console.error('❌ Mini-ticker WS error:', err.message);
    });
}

/**
 * Synchronously read a cached 24h ticker for a symbol (e.g. "BTCUSDT").
 * Returns null until the first WS frame arrives (~1 second after start).
 *
 * Object shape matches Binance's REST /api/v3/ticker/24hr (subset):
 *   { lastPrice, quoteVolume, openPrice, highPrice, lowPrice, volume, ts }
 */
function getTicker24h(symbol) {
    if (!symbol) return null;
    return tickerCache.get(symbol.toUpperCase()) || null;
}

async function start(socketIo, onExecution = null) {
    io = socketIo;
    executionCallback = onExecution;
    await loadCredentials();

    // 24h mini-ticker stream is public — start it before user-data so the
    // analysis cache has data to read by the time symbols are scored.
    connectTickerStream();

    const key = await createListenKey();
    if (key) {
        connectWS();
        clearInterval(keepAliveTimer);
        keepAliveTimer = setInterval(keepAlive, 25 * 60 * 1000);
    }
}

module.exports = { start, getTicker24h };
