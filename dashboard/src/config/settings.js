/**
 * src/config/settings.js
 * ─────────────────────────────────────────────────────────────────────────────
 * Central configuration for the BookNow Dashboard.
 *
 * All tuneable values live here. Never scatter magic numbers across files.
 * Values are read from environment variables (via .env) with safe defaults.
 */

require('dotenv').config();

module.exports = {

    // ── Server ──────────────────────────────────────────────────────────────
    PORT: parseInt(process.env.PORT, 10) || 3000,

    // ── Redis connection ────────────────────────────────────────────────────
    REDIS: {
        host: process.env.REDIS_HOST || '127.0.0.1',
        port: parseInt(process.env.REDIS_PORT, 10) || 6379,
    },

    // ── Spring Boot API ─────────────────────────────────────────────────────
    SPRING_API: process.env.SPRING_API || 'http://localhost:8083/api/v1',

    // ── Polling interval ────────────────────────────────────────────────────
    /** How often (ms) the server reads Redis and pushes updates to clients */
    POLL_MS: 1000,

    // ── Auto-trade defaults ─────────────────────────────────────────────────
    /** These are the initial values. Users can override them via the UI. */
    AUTO_TRADE_DEFAULTS: {
        enabled:        false,
        profitPct:      parseFloat(process.env.DEFAULT_PROFIT_PCT)      || 1.5,
        stopLossPct:    parseFloat(process.env.DEFAULT_STOP_LOSS_PCT)    || 0.8,
        maxPositions:   parseInt(process.env.DEFAULT_MAX_POSITIONS, 10)  || 5,
        displayLimit:   parseInt(process.env.DEFAULT_DISPLAY_LIMIT, 10)  || 10,
        simulationMode: process.env.DEFAULT_SIMULATION_MODE !== 'false',
        /** Auto-exit profitable positions after this many minutes */
        timeLimitMinutes: 5,
    },

    // ── Redis key names ─────────────────────────────────────────────────────
    /** Must exactly match Constant.java in the Spring Boot bot */
    REDIS_KEYS: {
        FAST_MOVE:      'FAST_MOVE',
        LT2MIN:         'LT2MIN_0_TO_3',
        ULTRA_FAST_0_2: 'ULTRA_FAST_0_TO_2',
        ULTRA_FAST_2_3: 'ULTRA_FAST_2_TO_3',
        ULTRA_FAST_0_3: 'ULTRA_FAST_0_TO_3',
        CURRENT_PRICE:  'CURRENT_PRICE',
        BOT_BUYS:       'BUY',
    },

    /** Priority order for signal type (lower = higher priority) */
    SIGNAL_PRIORITY: {
        'LT2MIN_0>3': 0,
        'UF_0>3':     1,
        'UF_0>2':     2,
        'UF_2>3':     3,
        'FAST_MOVE':  4,
    },
};
