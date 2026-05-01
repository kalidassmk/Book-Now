/**
 * src/services/tradeStore.js
 * ─────────────────────────────────────────────────────────────────────────────
 * In-memory trade state management.
 *
 * Stores:
 *   - Open positions (Map: symbol → position object)
 *   - Trade history log (array, newest-first, capped at 200)
 *   - Cumulative realized P&L (USD)
 *
 * Why in-memory?
 *   For speed. The auto-trade engine runs every second and needs instant access.
 *   For production, replace with Redis persistence or a database.
 *
 * Exported interface:
 *   positions     → Map of open positions
 *   tradeLog      → Array of all trade events
 *   totalPnL      → Running P&L number
 *   recordBuy()   → Add a new open position + log entry
 *   recordSell()  → Close a position + log the result
 *   getSnapshot() → Return safe read-only copies for broadcasting
 */

// ── State ─────────────────────────────────────────────────────────────────────

/** @type {Map<string, object>} symbol → position details */
const positions = new Map();

/** @type {Array<object>} All trades (buy + sell), newest first */
const tradeLog = [];

/** Running sum of realized profit/loss in USD */
let totalPnL = 0;

/** Max trade history entries to keep in memory */
const MAX_LOG_SIZE = 200;

// ── Write operations ──────────────────────────────────────────────────────────

/**
 * Record a new BUY and open a position.
 *
 * @param {object} params
 * @param {string} params.symbol
 * @param {number} params.buyPrice    Entry price
 * @param {number} params.qty         Quantity purchased
 * @param {number} params.target      Take-profit price
 * @param {number} params.stop        Stop-loss price
 * @param {string} params.signal      Signal name that triggered the buy
 * @param {boolean} params.simulation Whether this was a simulated trade
 * @returns {object} The trade log entry
 */
function recordBuy({ symbol, buyPrice, qty, target, stop, signal, simulation }) {
    // 1. Open the position
    positions.set(symbol, {
        symbol,
        buyPrice,
        qty,
        target,
        stop,
        signal,
        buyTime: Date.now(),
    });

    // 2. Log the trade event
    const entry = {
        id:         `${symbol}-buy-${Date.now()}`,
        symbol,
        action:     'BUY',
        price:      buyPrice,
        qty,
        target,
        stop,
        time:       Date.now(),
        status:     'OPEN',
        simulation,
        pnlPct:     0,
    };

    _addToLog(entry);
    return entry;
}

/**
 * Record a SELL and close the position.
 *
 * @param {object} params
 * @param {string} params.symbol
 * @param {number} params.sellPrice   Exit price
 * @param {string} params.reason      Why we sold (PROFIT_TARGET | STOP_LOSS | TIME_EXIT | MANUAL)
 * @param {boolean} params.simulation
 * @returns {object} The trade log entry
 */
function recordSell({ symbol, sellPrice, reason, simulation }) {
    const pos = positions.get(symbol);
    if (!pos) return null;

    // 1. Calculate profit/loss
    const pnlPct  = ((sellPrice - pos.buyPrice) / pos.buyPrice) * 100;
    const realized = (sellPrice - pos.buyPrice) * pos.qty;

    // 2. Update running P&L and close position
    totalPnL += realized;
    positions.delete(symbol);

    // 3. Log the trade event
    const entry = {
        id:          `${symbol}-sell-${Date.now()}`,
        symbol,
        action:      'SELL',
        price:       sellPrice,
        qty:         pos.qty,
        buyPrice:    pos.buyPrice,
        pnlPct:      parseFloat(pnlPct.toFixed(3)),
        realizedPnL: parseFloat(realized.toFixed(6)),
        reason,
        time:        Date.now(),
        status:      'CLOSED',
        simulation,
    };

    _addToLog(entry);
    return entry;
}

// ── Read operations ───────────────────────────────────────────────────────────

/**
 * Returns a snapshot of all open positions enriched with current price and P&L.
 * Intended for broadcasting to clients — safe to mutate.
 *
 * @param {function} getPriceFn  (symbol) → current price number
 * @returns {Array<object>}
 */
function getEnrichedPositions(getPriceFn) {
    return Array.from(positions.values()).map((pos) => {
        const currentPrice = getPriceFn(pos.symbol);
        const pnlPct = currentPrice
            ? ((currentPrice - pos.buyPrice) / pos.buyPrice) * 100
            : 0;

        return {
            ...pos,
            currentPrice,
            pnlPct:  parseFloat(pnlPct.toFixed(3)),
            heldSec: Math.floor((Date.now() - pos.buyTime) / 1000),
        };
    });
}

// ── Private helpers ───────────────────────────────────────────────────────────

/** Prepend an entry to the log and enforce the size cap. */
function _addToLog(entry) {
    tradeLog.unshift(entry);
    if (tradeLog.length > MAX_LOG_SIZE) {
        tradeLog.pop();
    }
}

module.exports = {
    positions,
    tradeLog,
    get totalPnL() { return totalPnL; },
    recordBuy,
    recordSell,
    getEnrichedPositions,
};
