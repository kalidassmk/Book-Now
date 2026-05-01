/**
 * src/services/orders.js
 * ─────────────────────────────────────────────────────────────────────────────
 * Fetches open orders from the Spring Boot backend.
 */

const SPRING_BASE = 'http://localhost:8083';

/**
 * Fetch all open orders from Spring API.
 * @returns {Promise<Array>} Array of open order objects
 */
async function fetchOpenOrders() {
    try {
        const response = await fetch(`${SPRING_BASE}/api/v1/orders/open`);
        if (!response.ok) {
            console.warn('[Orders] Fetch failed:', response.status);
            return [];
        }
        const orders = await response.json();
        console.log(`[Orders] Fetched ${orders.length} open orders`);
        return orders;
    } catch (err) {
        console.error('[Orders] Error:', err.message);
        return [];
    }
}

module.exports = { fetchOpenOrders };
