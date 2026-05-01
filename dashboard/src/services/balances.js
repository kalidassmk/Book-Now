/**
 * src/services/balances.js
 * ─────────────────────────────────────────────────────────────────────────────
 * Fetches wallet balances from the Spring Boot backend.
 */

const SPRING_BASE = 'http://localhost:8083';

/**
 * Fetch all wallet balances from Spring API.
 * @returns {Promise<Array>} Array of balance objects
 */
async function fetchBalances() {
    try {
        const response = await fetch(`${SPRING_BASE}/api/wallet/balances`);
        if (!response.ok) {
            console.warn('[Balances] Fetch failed:', response.status);
            return [];
        }
        const balances = await response.json();
        console.log(`[Balances] Fetched ${balances.length} balances`);
        return balances;
    } catch (err) {
        console.error('[Balances] Error:', err.message);
        return [];
    }
}

module.exports = { fetchBalances };
