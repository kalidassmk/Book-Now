/**
 * dashboard/binance-worker.js
 * ─────────────────────────────────────────────────────────────────────────────
 * Pure WebSocket-API v3 Worker (No ListenKey Logic).
 */

const crypto = require('crypto');
const Redis = require('ioredis');
const WebSocket = require('ws'); 
require('dotenv').config();

let API_KEY = process.env.BINANCE_API_KEY;
let SECRET_KEY = process.env.BINANCE_SECRET_KEY;
const BASE_URL = 'https://api.binance.com';

const redis = new Redis({
    host: process.env.REDIS_HOST || '127.0.0.1',
    port: process.env.REDIS_PORT || 6379
});

let wsApiSocket = null;
let io = null; 
let executionCallback = null;
let reconnectTimer = null;

/**
 * PRODUCTION-GRADE: Credential Loader
 */
async function loadCredentials() {
    try {
        const rApiKey = await redis.get('BINANCE_API_KEY');
        const rSecretKey = await redis.get('BINANCE_SECRET_KEY');
        if (rApiKey && rSecretKey) {
            API_KEY = rApiKey;
            SECRET_KEY = rSecretKey;
        }
    } catch (e) {}
}

/**
 * PURE WS-API: Handle LifeCycle and Events
 */
function connectWsApi() {
    if (wsApiSocket) {
        try { wsApiSocket.terminate(); } catch (e) {}
    }

    console.log('[WS-API] Connecting to wss://ws-api.binance.com/ws-api/v3...');
    wsApiSocket = new WebSocket('wss://ws-api.binance.com/ws-api/v3');

    wsApiSocket.on('open', () => {
        console.log('[WS-API] Socket Open. Logging on...');
        // 1. Session Logon
        wsApiSocket.send(JSON.stringify({
            id: "logon",
            method: "session.logon",
            params: { apiKey: API_KEY }
        }));
    });

    wsApiSocket.on('message', (data) => {
        try {
            const res = JSON.parse(data);
            
            // Handle Responses
            if (res.id === "logon") {
                console.log('[WS-API] Session Authenticated ✅');
                // 2. Start User Data Stream (Directly over this socket)
                wsApiSocket.send(JSON.stringify({
                    id: "start_stream",
                    method: "userDataStream.start"
                }));
            } else if (res.id === "start_stream") {
                console.log('[WS-API] User Data Stream Started 🚀');
            }

            // Handle Push Events (executionReport, etc)
            if (res.e === 'executionReport') {
                const executionData = { symbol: res.s, orderId: res.i, status: res.X, executedQty: res.z, price: res.p };
                console.log(`[WS-API] Execution: ${res.s} | ${res.X}`);
                if (io) io.emit('order-execution', executionData);
                if (executionCallback) executionCallback(executionData);
                refreshOpenOrders();
            } else if (res.e === 'outboundAccountPosition') {
                console.log('[WS-API] Balance Update');
                refreshAccountBalances();
            }

        } catch (err) {
            console.error('[WS-API] Message Error:', err.message);
        }
    });

    wsApiSocket.on('close', () => {
        console.warn('[WS-API] Connection Closed. Reconnecting in 5s...');
        clearTimeout(reconnectTimer);
        reconnectTimer = setTimeout(connectWsApi, 5000);
    });

    wsApiSocket.on('error', (err) => {
        console.error('[WS-API] Error:', err.message);
    });
}

/**
 * Standard Signed API Caller (For Orders/Balances)
 */
function getSignature(queryString) {
    return crypto.createHmac('sha256', SECRET_KEY).update(queryString).digest('hex');
}

async function binanceFetch(endpoint, method = 'GET', params = {}) {
    const timestamp = Date.now();
    const query = new URLSearchParams({ ...params, timestamp, recvWindow: 60000 }).toString();
    const signature = getSignature(query);
    const url = `${BASE_URL}${endpoint}?${query}&signature=${signature}`;

    try {
        const res = await fetch(url, {
            method,
            headers: { 'X-MBX-APIKEY': API_KEY }
        });
        const data = await res.json();
        if (!res.ok) throw new Error(data.msg || 'Binance API Error');
        return data;
    } catch (err) {
        console.error(`[Binance Network Error] ${endpoint}:`, err.message);
        return null;
    }
}

async function refreshOpenOrders() {
    const orders = await binanceFetch('/api/v3/openOrders');
    if (orders) {
        await redis.set('BINANCE:OPEN_ORDERS:ALL', JSON.stringify(orders));
    }
}

async function refreshAccountBalances() {
    const accountInfo = await binanceFetch('/api/v3/account');
    if (!accountInfo || !accountInfo.balances) return;
    const balances = accountInfo.balances.filter(b => parseFloat(b.free) > 0 || parseFloat(b.locked) > 0);
    await redis.set('BINANCE:BALANCES:ALL', JSON.stringify(balances));
}

async function start(socketIo, onExecution = null) {
    io = socketIo;
    executionCallback = onExecution;
    await loadCredentials();
    
    refreshOpenOrders();
    refreshAccountBalances();

    setInterval(refreshOpenOrders, 30000);
    setInterval(refreshAccountBalances, 60000);
    
    // Start Pure Socket Logic
    connectWsApi();
}

module.exports = { start, binanceFetch };
