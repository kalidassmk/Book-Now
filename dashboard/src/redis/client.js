/**
 * src/redis/client.js
 * ─────────────────────────────────────────────────────────────────────────────
 * Redis connection singleton and low-level data helpers.
 *
 * Responsibilities:
 *   - Create and export a single ioredis client (shared across all services)
 *   - Provide helper: getAllHash(key) → { symbol: parsedObject }
 *   - Provide helper: extractPrice(hashEntry) → number
 *
 * Why a singleton?
 *   Redis connections are expensive. All services import this file and reuse
 *   the same connection rather than opening their own.
 */

const Redis    = require('ioredis');
const { REDIS } = require('../config/settings');

// ── Create client ─────────────────────────────────────────────────────────────
const client = new Redis({
    host:        REDIS.host,
    port:        REDIS.port,
    lazyConnect: true,      // don't connect until connect() is called
    retryStrategy: (times) => {
        // Retry with exponential backoff, max 10 seconds
        return Math.min(times * 500, 10_000);
    },
});

client.on('connect', () => console.log('[Redis] Connected ✅'));
client.on('error',   (err) => console.error('[Redis] Error:', err.message));
client.on('close',   () => console.warn('[Redis] Connection closed'));

// ── Helpers ───────────────────────────────────────────────────────────────────

/**
 * Safely parse a JSON string. Returns null on failure instead of throwing.
 * @param {string} raw
 * @returns {object|null}
 */
function safeJson(raw) {
    try { return raw ? JSON.parse(raw) : null; }
    catch { return null; }
}

/**
 * Read an entire Redis Hash and parse every value as JSON.
 * Returns an empty object if the key doesn't exist.
 *
 * @param {string} key  Redis hash key
 * @returns {Promise<Record<string, object>>}
 */
async function getAllHash(key) {
    const raw = await client.hgetall(key);
    if (!raw) return {};

    const result = {};
    for (const [field, value] of Object.entries(raw)) {
        // Try to parse as JSON; fall back to the raw string
        result[field] = safeJson(value) || value;
    }
    return result;
}

/**
 * Extract the current numeric price from a CURRENT_PRICE hash entry.
 * The entry can be a plain number string, or a JSON object with various field names.
 *
 * @param {string|object} entry  Value from the CURRENT_PRICE hash
 * @returns {number}  0 if not parseable
 */
function extractPrice(entry) {
    if (!entry) return 0;
    if (typeof entry === 'object') {
        return parseFloat(entry.currentPrice || entry.price || 0);
    }
    return parseFloat(entry) || 0;
}

module.exports = { client, getAllHash, extractPrice };
