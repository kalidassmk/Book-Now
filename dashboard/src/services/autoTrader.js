/**
 * src/services/autoTrader.js
 * ─────────────────────────────────────────────────────────────────────────────
 * Automatic buy/sell decision engine.
 *
 * How it works:
 *   Every tick, the broadcaster calls `evaluate()` with the latest fast-coin
 *   map and price hash. This module decides whether to buy or sell positions
 *   and records the result in tradeStore.
 *
 * Buy conditions (ALL must be met):
 *   1. autoConfig.enabled is true
 *   2. The coin is in the fast-signal map
 *   3. No existing position is open for that coin
 *   4. We have not exceeded maxPositions
 *   5. The current price is available
 *
 * Sell conditions (ANY triggers a sell):
 *   - Price ≥ target price        → PROFIT_TARGET
 *   - Price ≤ stop-loss price     → STOP_LOSS
 *   - Held > timeLimitMinutes AND profitable → TIME_EXIT
 *
 * Position sizing:
 *   Currently uses a flat $10 per position. For production,
 *   replace with a proper risk-management formula.
 */

const store = require('./tradeStore');
const { extractPrice } = require('../redis/client');
const { AUTO_TRADE_DEFAULTS } = require('../config/settings');

// ── Runtime config (mutable — updated by REST API) ───────────────────────────
let autoConfig = { ...AUTO_TRADE_DEFAULTS };

// ── Public API ────────────────────────────────────────────────────────────────

/**
 * Main entry point called every tick.
 * Returns an array of trade events (buys/sells) that happened this tick.
 *
 * @param {object} fastCoins  Map of symbol → { signal, raw } from scanner
 * @param {object} priceHash  Map of symbol → price entry from Redis
 * @returns {Promise<Array<object>>}  All trade events emitted this tick
 */
async function evaluate(fastCoins, priceHash) {
    if (!autoConfig.enabled) return [];

    const events = [];

    // 1. Evaluate buys for all fast movers
    _evaluateBuys(fastCoins, priceHash, events);

    // 2. Evaluate sells for all open positions
    _evaluateSells(priceHash, events);

    return events;
}

/**
 * Update the runtime configuration (called from REST API).
 * Merges partial updates — you only need to send changed fields.
 *
 * @param {object} patch  Partial autoConfig fields
 * @returns {object}  The full updated config
 */
function updateConfig(patch) {
    autoConfig = { ...autoConfig, ...patch };
    return autoConfig;
}

/** Return a copy of the current auto-trade configuration. */
function getConfig() {
    return { ...autoConfig };
}

// ── Private helpers ───────────────────────────────────────────────────────────

/**
 * Check each fast-moving coin for a buy opportunity.
 * Mutates `events` array if a buy is executed.
 */
function _evaluateBuys(fastCoins, priceHash, events) {
    for (const [symbol, data] of Object.entries(fastCoins)) {
        // Skip: already have this position
        if (store.positions.has(symbol)) continue;

        // Skip: position limit reached
        if (store.positions.size >= autoConfig.maxPositions) continue;

        const price = extractPrice(priceHash[symbol]);
        if (!price) continue;   // Skip: no price available

        // Calculate position parameters
        const qty    = 10 / price;   // $10 per position (flat sizing)
        const target = price * (1 + autoConfig.profitPct  / 100);
        const stop   = price * (1 - autoConfig.stopLossPct / 100);

        // Execute buy
        const tradeEntry = store.recordBuy({
            symbol, buyPrice: price, qty, target, stop,
            signal:     data.signal,
            simulation: autoConfig.simulationMode,
        });

        console.log(
            `[AutoTrader] BUY  ${symbol} @ ${price.toFixed(8)}` +
            ` | target ${target.toFixed(8)} | stop ${stop.toFixed(8)}`
        );

        events.push(tradeEntry);
    }
}

/**
 * Check each open position for a sell condition.
 * Mutates `events` array if a sell is executed.
 */
function _evaluateSells(priceHash, events) {
    for (const [symbol, pos] of store.positions) {
        const price = extractPrice(priceHash[symbol]);
        if (!price) continue;

        const pnlPct      = (price - pos.buyPrice) / pos.buyPrice * 100;
        const heldMs      = Date.now() - pos.buyTime;
        const timeLimitMs = autoConfig.timeLimitMinutes * 60 * 1000;

        // Determine sell reason
        const reason = _decideSellReason(price, pos, pnlPct, heldMs, timeLimitMs);
        if (!reason) continue;

        // Execute sell
        const tradeEntry = store.recordSell({
            symbol, sellPrice: price, reason,
            simulation: autoConfig.simulationMode,
        });

        console.log(
            `[AutoTrader] SELL ${symbol} @ ${price.toFixed(8)}` +
            ` | ${reason} | PnL: $${tradeEntry?.realizedPnL}`
        );

        if (tradeEntry) events.push(tradeEntry);
    }
}

/**
 * Decide whether to sell and return the reason string, or null to hold.
 *
 * @param {number} price
 * @param {object} pos        Open position object
 * @param {number} pnlPct     Current P&L percentage
 * @param {number} heldMs     How long position has been held (ms)
 * @param {number} timeLimitMs  Max hold time (ms)
 * @returns {string|null}  Sell reason or null
 */
function _decideSellReason(price, pos, pnlPct, heldMs, timeLimitMs) {
    if (price >= pos.target)                     return `PROFIT_TARGET (+${pnlPct.toFixed(2)}%)`;
    if (price <= pos.stop)                       return `STOP_LOSS (${pnlPct.toFixed(2)}%)`;
    if (heldMs > timeLimitMs && pnlPct > 0)     return `TIME_EXIT (+${pnlPct.toFixed(2)}%)`;
    return null;
}

module.exports = { evaluate, updateConfig, getConfig };
