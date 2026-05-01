/**
 * src/services/scanner.js
 * ─────────────────────────────────────────────────────────────────────────────
 * Fast-mover coin scanner.
 *
 * Reads all fast-signal Redis keys every tick, merges them into a unified
 * coin list, enriches each entry with live price and position data, then
 * returns a sorted, display-limited list ready for the client.
 *
 * Signal hierarchy (highest → lowest priority):
 *   LT2MIN_0>3  — coin rose 0→3% in under 2 minutes (strongest signal)
 *   UF_0>3      — ultra-fast full jump skipping bands
 *   UF_0>2      — skipped 0-1% band, already at 1-2%
 *   UF_2>3      — skipped 1-2% band, already at 2-3%
 *   FAST_MOVE   — algorithm-flagged fast mover (baseline)
 */

const { getAllHash, extractPrice } = require('../redis/client');
const { REDIS_KEYS, SIGNAL_PRIORITY } = require('../config/settings');
const store = require('./tradeStore');

// ── Public API ────────────────────────────────────────────────────────────────

/**
 * Fetch all fast-mover signals from Redis, merge them, and return an
 * enriched, sorted, and display-limited coin array.
 *
 * @param {number} displayLimit  Max coins to return (positions always included)
 * @returns {Promise<{ fastCoins: object, displayedCoins: Array, priceHash: object }>}
 *   - fastCoins:     Raw merged signal map (for autoTrader to consume)
 *   - displayedCoins: Array for the dashboard table
 *   - priceHash:     Raw price hash (for position enrichment)
 */
async function scanFastMovers(displayLimit = 10) {
    // 1. Fetch all signal hashes in parallel to minimise Redis round-trips
    const [fastMove, lt2min, uf02, uf23, uf03, priceHash, botBuys] = await Promise.all([
        getAllHash(REDIS_KEYS.FAST_MOVE),
        getAllHash(REDIS_KEYS.LT2MIN),
        getAllHash(REDIS_KEYS.ULTRA_FAST_0_2),
        getAllHash(REDIS_KEYS.ULTRA_FAST_2_3),
        getAllHash(REDIS_KEYS.ULTRA_FAST_0_3),
        getAllHash(REDIS_KEYS.CURRENT_PRICE),
        getAllHash(REDIS_KEYS.BOT_BUYS),
    ]);

    // 2. Merge all signals into one map.
    //    Lower-priority signals only set the signal label if one isn't already set.
    const fastCoins = _mergeSignals({ lt2min, uf03, uf02, uf23, fastMove });

    // 3. Enrich each coin with live price, position status, and P&L
    const allCoins = Object.entries(fastCoins).map(([symbol, data]) => {
        return _enrichCoin(symbol, data, priceHash, botBuys);
    });

    // 4. Sort: open positions first, then by signal priority
    allCoins.sort(_compareCoin);

    // 5. Apply display limit while always keeping open positions visible
    const displayedCoins = _applyDisplayLimit(allCoins, displayLimit);

    return { fastCoins, displayedCoins, priceHash };
}

// ── Private helpers ───────────────────────────────────────────────────────────

/**
 * Merge all five signal hashes into one map.
 * Each symbol gets the highest-priority signal it appeared in.
 */
function _mergeSignals({ lt2min, uf03, uf02, uf23, fastMove }) {
    const merged = {};

    const assign = (hash, label) => {
        for (const [sym, raw] of Object.entries(hash)) {
            // Only overwrite if no signal exists yet (sources added highest→lowest)
            if (!merged[sym]) {
                merged[sym] = { signal: label, raw };
            }
        }
    };

    // Order matters — highest priority first
    assign(lt2min,   'LT2MIN_0>3');
    assign(uf03,     'UF_0>3');
    assign(uf02,     'UF_0>2');
    assign(uf23,     'UF_2>3');
    assign(fastMove, 'FAST_MOVE');

    return merged;
}

/**
 * Enrich a single coin entry with live price, position data, and P&L.
 *
 * @param {string} symbol
 * @param {object} data    Signal data from merged map
 * @param {object} priceHash  CURRENT_PRICE hash
 * @param {object} botBuys    BUY hash (from Spring Boot bot)
 * @returns {object}  Coin display object
 */
function _enrichCoin(symbol, data, priceHash, botBuys) {
    const currentPrice = extractPrice(priceHash[symbol]);
    const myPos        = store.positions.get(symbol);
    const botBuy       = botBuys[symbol];

    // Calculate P&L for open positions
    const pnlPct = myPos && currentPrice
        ? parseFloat(((currentPrice - myPos.buyPrice) / myPos.buyPrice * 100).toFixed(3))
        : null;

    return {
        symbol,
        signal:      data.signal,
        currentPrice,
        buyPrice:    myPos?.buyPrice                                     // own position
                  || (botBuy ? parseFloat(botBuy.buyPrice || 0) : null), // bot's position
        target:      myPos?.target  || null,
        stop:        myPos?.stop    || null,
        pnlPct,
        hasPosition: store.positions.has(symbol),
        botSignal:   !!botBuys[symbol],
        heldMs:      myPos ? (Date.now() - myPos.buyTime) : null,
    };
}

/**
 * Sort comparator for the coin list.
 * Priority: open positions first, then by SIGNAL_PRIORITY.
 */
function _compareCoin(a, b) {
    if (a.hasPosition && !b.hasPosition) return -1;
    if (b.hasPosition && !a.hasPosition) return  1;
    const pa = SIGNAL_PRIORITY[a.signal] ?? 9;
    const pb = SIGNAL_PRIORITY[b.signal] ?? 9;
    return pa - pb;
}

/**
 * Limit the displayed coin list while always keeping open positions visible.
 *
 * @param {Array} sortedCoins
 * @param {number} limit
 * @returns {Array}
 */
function _applyDisplayLimit(sortedCoins, limit) {
    const withPos    = sortedCoins.filter(c => c.hasPosition);
    const withoutPos = sortedCoins.filter(c => !c.hasPosition);
    const remaining  = Math.max(0, limit - withPos.length);
    return [...withPos, ...withoutPos.slice(0, remaining)];
}

module.exports = { scanFastMovers };
