/**
 * public/js/orders.js
 * ─────────────────────────────────────────────────────────────────────────────
 * Manual order placement module.
 *
 * Exposes functions called by:
 *   - Quick BUY button in each scanner table row
 *   - Quick SELL button on position cards
 *   - The manual override form (bottom of right panel)
 *
 * Exported to the window.orders namespace so HTML onclick attributes
 * can call them without conflict.
 */

/** Namespace to avoid polluting window globals */
const orders = (() => {

    // ── Internal State ────────────────────────────────────────────────────────
    let currentMode = localStorage.getItem('tradeMode') || 'LIMIT'; 
    let selectedSymbol = '';

    // ── Public ────────────────────────────────────────────────────────────────

    /**
     * Quick BUY from a scanner table row button.
     * Pre-fills the manual form and sends the order immediately.
     *
     * @param {string} symbol
     * @param {Event}  event  Click event (stopped to prevent row selection)
     */
    async function quickBuy(symbol, event) {
        if (event) event.stopPropagation();
        
        let price = 0;
        if (currentMode === 'LIMIT') {
            const inp = document.getElementById(`limit-${symbol}`);
            price = inp ? parseFloat(inp.value) : 0;
        }

        toast(`Placing ${currentMode} Buy: ${symbol}...`, 'y');
        await _sendBuy(symbol, price, 10); // $10 buy
    }

    /**
     * Quick SELL from a scanner row or position card button.
     *
     * @param {Event}  e
     * @param {string} symbol
     * @param {number} price   Current market price
     */
    async function quickSell(e, symbol, price) {
        e.stopPropagation();
        await _sendSell(symbol, price);
    }

    /**
     * Fill the manual order form with a coin's symbol and price.
     * Called when a scanner table row is clicked.
     *
     * @param {string} symbol
     * @param {number} price
     */
    function fillManual(symbol, price) {
        updateSymbol(symbol);
        setMode('LIMIT'); 
        document.getElementById('m-price').value = price || '';
        if (price) _calcTarget(price);
    }

    /**
     * Update the active symbol labels in the terminal.
     */
    function updateSymbol(sym) {
        selectedSymbol = sym.toUpperCase();
        const labels = document.querySelectorAll('.term-sym-label');
        labels.forEach(l => l.textContent = selectedSymbol);

        // Update units (e.g. Amount in SOL)
        const baseCoin = selectedSymbol.replace('USDT', '').replace('BUSD', '');
        document.getElementById('m-qty-unit').textContent = baseCoin;
        document.getElementById('s-qty-unit').textContent = baseCoin;
    }

    /**
     * Fetch the current market price for the symbol and fill the price field.
     */
    async function useMarketPrice() {
        const symbol = document.getElementById('m-sym').value.trim().toUpperCase();
        if (!symbol) return toast('Enter symbol first', 'y');

        try {
            const res = await fetch(`/api/analyze/${symbol}`);
            const data = await res.json();
            if (data && data.currentPrice) {
                document.getElementById('m-price').value = data.currentPrice;
                _calcTarget(data.currentPrice);
                toast(`Market price: ${data.currentPrice}`, 'g');
            } else {
                toast('Price not available yet', 'r');
            }
        } catch (err) {
            toast('Failed to fetch market price', 'r');
        }
    }

    function _calcTarget(buyPrice) {
        const targetInput = document.getElementById('m-target');
        if (buyPrice > 0) {
            // Default +1.5% profit if empty
            targetInput.value = (parseFloat(buyPrice) * 1.015).toFixed(8);
        }
    }

    /**
     * Recalculate quantity or total based on inputs.
     * @param {string} side 'BUY' or 'SELL'
     * @param {string} trigger 'total' (default) or 'qty'
     */
    function recalc(side, trigger = 'total') {
        const prefix = (side === 'BUY') ? 'm' : 's';
        const priceInp = (side === 'BUY') ? 'm-price' : 'm-target';
        const price = parseFloat(document.getElementById(priceInp).value) || 0;
        
        const qtyInp = `${prefix}-qty`;
        const totalInp = `${prefix}-total`;
        const estDiv = `${prefix}-est`;
        const unit = document.getElementById(`${prefix}-qty-unit`).textContent;

        if (trigger === 'total') {
            const total = parseFloat(document.getElementById(totalInp).value) || 0;
            const qty = (price > 0 && total > 0) ? (total / price) : 0;
            document.getElementById(qtyInp).value = qty > 0 ? qty.toFixed(6) : '';
            document.getElementById(estDiv).textContent = `≈ ${qty > 0 ? qty.toFixed(6) : '0.00'} ${unit}`;
        } else {
            const qty = parseFloat(document.getElementById(qtyInp).value) || 0;
            const total = (price > 0 && qty > 0) ? (qty * price) : 0;
            document.getElementById(totalInp).value = total > 0 ? total.toFixed(2) : '';
            document.getElementById(estDiv).textContent = `≈ ${qty > 0 ? qty.toFixed(6) : '0.00'} ${unit}`;
        }
    }

    /**
     * Triggered on every WebSocket update to keep terminal prices fresh
     * if in Market mode or if the user hasn't edited the price yet.
     */
    function recalcLive(coins) {
        if (!selectedSymbol) return;
        const coin = coins.find(c => c.symbol === selectedSymbol);
        if (!coin) return;

        // If Market mode is active, always sync price
        if (currentMode === 'MARKET') {
            document.getElementById('m-price').value = coin.price;
            recalc('BUY');
        }
    }

    /**
     * Submit the manual BUY form (called by the BUY button in the form).
     */
    async function manualBuy() {
        const symbol = selectedSymbol;
        if (!symbol) return toast('Select a coin first', 'y');

        const price  = currentMode === 'LIMIT' ? parseFloat(document.getElementById('m-price').value) : 0;
        const target = parseFloat(document.getElementById('m-target').value);
        const qty    = parseFloat(document.getElementById('m-qty').value);

        if (currentMode === 'LIMIT' && !price) return toast('Enter limit buy price', 'r');
        if (!qty || qty <= 0) return toast('Enter quantity/amount', 'r');
        
        toast(`Sending ${currentMode} BUY ${selectedSymbol}...`, 'y');
        await _sendBuy(symbol, price, qty, target);
    }

    /**
     * Submit the manual SELL form (called by the SELL button in the form).
     */
    async function manualSell() {
        const symbol = selectedSymbol;
        if (!symbol) return toast('Select a coin first', 'y');

        const price = parseFloat(document.getElementById('m-target').value);
        const qty   = parseFloat(document.getElementById('s-qty').value);

        if (!price || price <= 0) return toast('Enter sell price', 'r');
        if (!qty || qty <= 0) return toast('Enter quantity to sell', 'r');

        toast(`Sending SELL ${selectedSymbol}...`, 'y');
        await _sendSell(symbol, price, qty);
    }

    /**
     * Set the trading mode (MARKET or LIMIT) and update UI.
     */
    function setMode(mode) {
        currentMode = mode;
        localStorage.setItem('tradeMode', mode);
        
        const isLimit = (mode === 'LIMIT');

        // Update tabs
        const tabs = document.querySelectorAll('.t-tab');
        tabs.forEach(t => {
            const match = t.textContent.toUpperCase() === mode;
            t.classList.toggle('active', match);
        });

        // Toggle price fields in terminal for symmetry
        const pField = document.getElementById('term-row-price');
        const sField = document.getElementById('term-row-sell-price');
        if (pField) pField.style.display = isLimit ? 'flex' : 'none';
        if (sField) sField.style.display = isLimit ? 'flex' : 'none';

        // Disable price input if Market
        const priceInp = document.getElementById('m-price');
        if (priceInp) priceInp.disabled = !isLimit;

        // Toggle table column
        const th = document.getElementById('th-limit-price');
        if (th) th.style.display = isLimit ? '' : 'none';
        
        const tds = document.querySelectorAll('.td-limit-price');
        tds.forEach(td => td.style.display = isLimit ? '' : 'none');

        // Update row buttons
        const buyBtns = document.querySelectorAll('.btn-buy');
        buyBtns.forEach(btn => btn.textContent = isLimit ? 'LIMIT BUY' : 'BUY');
    }

    // Initialize UI on load
    setTimeout(() => setMode(currentMode), 500);

    /**
     * Fetch the 2-month low for the symbol and fill the price field.
     */
    async function use2mLow() {
        const symbol = document.getElementById('m-sym').value.trim().toUpperCase();
        if (!symbol) return toast('Enter symbol first', 'y');

        try {
            toast(`Fetching analysis for ${symbol}...`, 'y');
            const res = await fetch(`/api/analyze/${symbol}`);
            const data = await res.json();
            if (data && data.metrics && data.metrics.low2m) {
                document.getElementById('m-price').value = data.metrics.low2m;
                toast(`2m Low found: ${data.metrics.low2m}`, 'g');
            } else {
                toast('Could not find 2m low in analysis', 'r');
            }
        } catch (err) {
            toast('Failed to fetch 2m low', 'r');
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    /** POST a BUY order to the server. */
    async function _sendBuy(symbol, price, qty, target) {
        try {
            const res  = await fetch('/api/buy', _post({ symbol, price, qty, target }));
            const data = await res.json();
            if (!data.ok) toast(data.error || 'Buy failed', 'r');
            else {
                const modeStr = price > 0 ? `Limit @ ${price}` : 'Market';
                toast(`✅ ${modeStr} Buy Placed: ${symbol}`, 'g');
            }
        } catch (err) {
            toast('Network error on BUY', 'r');
            console.error('[Orders] BUY error:', err);
        }
    }

    /** POST a SELL order to the server. */
    async function _sendSell(symbol, price) {
        try {
            const res  = await fetch('/api/sell', _post({ symbol, price }));
            const data = await res.json();
            if (!data.ok) toast(data.error || 'Sell failed', 'r');
        } catch (err) {
            toast('Network error on SELL', 'r');
            console.error('[Orders] SELL error:', err);
        }
    }

    /** Fill the manual order form fields. */
    function _fillForm(symbol, price) {
        document.getElementById('m-sym').value   = symbol;
        document.getElementById('m-price').value = price || '';
    }

    /** Build a standard fetch POST options object. */
    function _post(body) {
        return {
            method:  'POST',
            headers: { 'Content-Type': 'application/json' },
            body:    JSON.stringify(body),
        };
    }

    // Return public interface
    return { quickBuy, quickSell, fillManual, manualBuy, manualSell, use2mLow, useMarketPrice, setMode, updateSymbol, recalc, recalcLive, getMode: () => currentMode };
})();
