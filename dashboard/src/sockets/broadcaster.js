/**
 * src/sockets/broadcaster.js
 * ─────────────────────────────────────────────────────────────────────────────
 * The heart of the real-time pipeline.
 *
 * Every POLL_MS milliseconds:
 *   1. scanner.scanFastMovers()   → reads Redis, returns enriched coin list
 *   2. autoTrader.evaluate()      → checks buy/sell conditions, emits trades
 *   3. io.emit('update', ...)     → pushes full state snapshot to all clients
 *
 * On each new WebSocket connection:
 *   - Send the current config so the client form is pre-populated
 *   - Send the full trade log immediately
 */

const { POLL_MS }        = require('../config/settings');
const { scanFastMovers } = require('../services/scanner');
const autoTrader         = require('../services/autoTrader');
const store              = require('../services/tradeStore');
const { extractPrice }   = require('../redis/client');
const { fetchBalances }   = require('../services/balances');
const { fetchOpenOrders } = require('../services/orders');

// ── Setup ─────────────────────────────────────────────────────────────────────

/**
 * Initialise the broadcaster.
 * Must be called once after the Socket.io server is ready.
 *
 * @param {import('socket.io').Server} io  The Socket.io server instance
 */
function init(io) {
    // ── Periodic broadcast loop ─────────────────────────────────────────────
    setInterval(() => _tick(io), POLL_MS);

    // ── New client connection ───────────────────────────────────────────────
    io.on('connection', (socket) => {
        console.log('[WS] Client connected:', socket.id);

        // Send current config so the client form matches server state
        socket.emit('config', autoTrader.getConfig());

        // Send the full trade history immediately on connect
        socket.emit('trades', {
            trades:   store.tradeLog,
            totalPnL: store.totalPnL,
        });

        socket.on('disconnect', () => {
            console.log('[WS] Client disconnected:', socket.id);
        });
    });
}

// ── Private helpers ───────────────────────────────────────────────────────────

/**
 * One tick of the data pipeline:
 *   1. Scan Redis for fast movers
 *   2. Run auto-trade engine
 *   3. Broadcast full update + any new trade events
 */
async function _tick(io) {
    try {
        const config = autoTrader.getConfig();

        // 1. Scan Redis — returns raw signal map, display list, and price hash
        const { fastCoins, displayedCoins, priceHash } = await scanFastMovers(
            config.displayLimit
        );

        // 2. Run auto-trader — returns array of new trade events this tick
        const newTrades = await autoTrader.evaluate(fastCoins, priceHash);

        // 3. Enrich open positions with live price and P&L
        const activePositions = store.getEnrichedPositions(
            (sym) => extractPrice(priceHash[sym])
        );

        // 4. Fetch balances and open orders for the account
        const [balances, openOrders] = await Promise.all([
            fetchBalances(),
            fetchOpenOrders(),
        ]);

        // 5. Build stats summary for the topbar
        const stats = _buildStats(fastCoins, displayedCoins, activePositions, config);

        // 6. Broadcast full state to all connected clients
        io.emit('update', {
            coins:           displayedCoins,
            activePositions,
            stats,
            ts:              Date.now(),
            balances,
            openOrders,
        });

        // 7. Emit each new trade event individually so the client can toast it
        for (const trade of newTrades) {
            io.emit('trade', trade);
        }

    } catch (err) {
        console.error('[Broadcaster] Tick error:', err.message);
        io.emit('update', {
            coins: [], activePositions: [], stats: { redisOk: false }, ts: Date.now(),
        });
    }
}

/**
 * Build the stats object shown in the dashboard topbar.
 */
function _buildStats(fastCoins, displayedCoins, activePositions, config) {
    return {
        fastCount:      Object.keys(fastCoins).length,    // total detected in Redis
        fastDisplayed:  displayedCoins.length,            // after display limit
        positions:      activePositions.length,
        totalPnL:       parseFloat(store.totalPnL.toFixed(4)),
        autoEnabled:    config.enabled,
        simulation:     config.simulationMode,
        redisOk:        true,
    };
}

module.exports = { init };
