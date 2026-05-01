/**
 * src/routes/api.js
 * ─────────────────────────────────────────────────────────────────────────────
 * REST API routes for the BookNow Dashboard.
 *
 * Endpoints:
 *   GET  /api/config          → Return current auto-trade config
 *   POST /api/config          → Update auto-trade config
 *   POST /api/buy             → Manually open a position
 *   POST /api/sell            → Manually close a position
 *   GET  /api/trades          → Full trade history + total P&L
 *
 *   POST /api/redis/flush     → Flush ALL Redis keys + reconnect client
 *   POST /api/service/stop    → Stop the Spring Boot service
 *   POST /api/service/start   → Start the Spring Boot service
 *   GET  /api/service/status  → Return { running, pid, startedAt }
 *
 * All write endpoints also emit a Socket.io event so the UI updates instantly
 * without waiting for the next tick.
 */

const express        = require('express');
const autoTrader     = require('../services/autoTrader');
const store          = require('../services/tradeStore');
const springService  = require('../services/springService');
const { client, extractPrice } = require('../redis/client');

const router = express.Router();

// ── Redis Cache Control ───────────────────────────────────────────────────────

/**
 * POST /api/redis/flush
 * Flushes ALL Redis keys using FLUSHALL (all databases), then disconnects
 * and reconnects the ioredis client so the scanner resumes from a clean state.
 *
 * Emits 'redis-flushed' Socket.io event so all open tabs see the update.
 */
router.post('/redis/flush', async (req, res) => {
    try {
        // FLUSHALL clears every key in every database on this Redis instance
        await client.flushall();
        console.log('[API] Redis FLUSHALL executed ✅');

        // Soft-restart: disconnect then reconnect so the client is ready
        // immediately for the next broadcaster polling tick.
        await client.disconnect();
        await client.connect().catch(() => {});
        console.log('[API] Redis client reconnected ✅');

        req.io.emit('redis-flushed', { ts: Date.now() });
        res.json({ ok: true, message: 'All Redis keys deleted and connection restarted.' });
    } catch (err) {
        console.error('[API] Redis flush error:', err.message);
        res.status(500).json({ ok: false, error: err.message });
    }
});

// ── Spring Boot Service Control ───────────────────────────────────────────────

/**
 * GET /api/service/status
 * Returns { running, pid, startedAt }.
 * Safe to poll — no side effects.
 */
router.get('/service/status', (req, res) => {
    res.json(springService.status());
});

/**
 * POST /api/service/stop
 * Gracefully stops the Spring Boot process (SIGTERM → SIGKILL after 5s).
 * Returns 409 if the service is not running.
 */
router.post('/service/stop', async (req, res) => {
    try {
        const result = await springService.stop();
        console.log('[API] Spring Boot stopped ✅');
        req.io.emit('service-status', { running: false });
        res.json(result);
    } catch (err) {
        const code = err.message.includes('not running') ? 409 : 500;
        console.error('[API] Stop error:', err.message);
        res.status(code).json({ ok: false, error: err.message });
    }
});

/**
 * POST /api/service/start
 * Spawns `mvn spring-boot:run` inside the book-now-v3 directory.
 * Returns 409 if the service is already running.
 */
router.post('/service/start', async (req, res) => {
    try {
        const result = await springService.start();
        console.log('[API] Spring Boot started ✅ pid:', result.pid);
        req.io.emit('service-status', { running: true, pid: result.pid });
        res.json(result);
    } catch (err) {
        const code = err.message.includes('already running') ? 409 : 500;
        console.error('[API] Start error:', err.message);
        res.status(code).json({ ok: false, error: err.message });
    }
});

// ── Config ────────────────────────────────────────────────────────────────────

/**
 * GET /api/config
 * Returns the current auto-trade configuration.
 */
router.get('/config', (req, res) => {
    res.json(autoTrader.getConfig());
});

/**
 * POST /api/config
 * Body: Partial autoConfig (any fields). Merges into existing config.
 * Also emits 'config' event via Socket.io so all open tabs update.
 *
 * Example body: { "enabled": true, "profitPct": 2.0 }
 */
router.post('/config', (req, res) => {
    const config = autoTrader.updateConfig(req.body);
    console.log('[API] Config updated:', config);

    // Broadcast config change to all clients (attached by server.js via req.io)
    req.io.emit('config', config);

    res.json({ ok: true, config });
});

// ── Manual Orders ─────────────────────────────────────────────────────────────

/**
 * POST /api/buy
 * Body: { symbol, price, qty? }
 * Opens a manual position. Uses current profit/stop config for targets.
 * Returns 409 if a position is already open for that symbol.
 */
router.post('/buy', (req, res) => {
    const { symbol, price, qty } = req.body;

    if (!symbol || !price) {
        return res.status(400).json({ error: 'symbol and price are required' });
    }
    if (store.positions.has(symbol)) {
        return res.status(409).json({ error: `Position already open for ${symbol}` });
    }

    const cfg    = autoTrader.getConfig();
    const p      = parseFloat(price);
    const q      = parseFloat(qty) || (10 / p);   // default $10 position
    const target = p * (1 + cfg.profitPct  / 100);
    const stop   = p * (1 - cfg.stopLossPct / 100);

    const trade = store.recordBuy({
        symbol, buyPrice: p, qty: q, target, stop,
        signal:     'MANUAL',
        simulation: false,   // manual buys are always "real intent"
    });

    req.io.emit('trade', trade);
    console.log(`[API] Manual BUY: ${symbol} @ ${p}`);
    res.json({ ok: true, trade });
});

/**
 * POST /api/sell
 * Body: { symbol, price }
 * Closes an open position at the given price.
 * Returns 404 if no position is open for that symbol.
 */
router.post('/sell', (req, res) => {
    const { symbol, price } = req.body;

    if (!symbol || !price) {
        return res.status(400).json({ error: 'symbol and price are required' });
    }
    if (!store.positions.has(symbol)) {
        return res.status(404).json({ error: `No open position for ${symbol}` });
    }

    const trade = store.recordSell({
        symbol,
        sellPrice: parseFloat(price),
        reason:    'MANUAL',
        simulation: false,
    });

    req.io.emit('trade', trade);
    console.log(`[API] Manual SELL: ${symbol} @ ${price}`);
    res.json({ ok: true, trade });
});

// ── Trade History ─────────────────────────────────────────────────────────────

/**
 * GET /api/trades
 * Returns the full trade log and total realized P&L.
 */
router.get('/trades', (req, res) => {
    res.json({
        trades:   store.tradeLog,
        totalPnL: store.totalPnL,
    });
});

module.exports = router;
