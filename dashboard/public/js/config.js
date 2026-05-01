/**
 * public/js/config.js
 * ─────────────────────────────────────────────────────────────────────────────
 * Auto-trade configuration form handler.
 *
 * Responsibilities:
 *   - Apply incoming config (from socket) to HTML form fields
 *   - Read form values and POST to /api/config when any field changes
 *   - Update the mode badge (SIM / LIVE) and stats display on change
 */

// ── Public API ────────────────────────────────────────────────────────────────

/**
 * Apply a config object received from the server to the HTML form.
 * Called on socket 'config' event.
 *
 * @param {object} cfg  Full autoConfig from server
 */
function applyConfig(cfg) {
    _el('ck-auto').checked   = cfg.enabled;
    _el('cfg-profit').value  = cfg.profitPct;
    _el('cfg-stop').value    = cfg.stopLossPct;
    _el('cfg-maxpos').value  = cfg.maxPositions;
    _el('cfg-limit').value   = cfg.displayLimit || 10;
    _el('ck-live').checked   = !cfg.simulationMode;

    _updateModeBadge(cfg.simulationMode);
}

/**
 * Read all form values and POST them to the server.
 * Called by onchange handlers in the HTML form fields.
 */
async function saveConfig() {
    const body = {
        enabled:        _el('ck-auto').checked,
        profitPct:      parseFloat(_el('cfg-profit').value),
        stopLossPct:    parseFloat(_el('cfg-stop').value),
        maxPositions:   parseInt(_el('cfg-maxpos').value, 10),
        displayLimit:   parseInt(_el('cfg-limit').value, 10) || 10,
        simulationMode: !_el('ck-live').checked,
    };

    try {
        const res = await fetch('/api/config', {
            method:  'POST',
            headers: { 'Content-Type': 'application/json' },
            body:    JSON.stringify(body),
        });
        const data = await res.json();
        if (data.ok) {
            _updateModeBadge(body.simulationMode);
            toast(body.enabled ? '⚡ Auto-trade ON' : 'Auto-trade paused', body.enabled ? 'g' : 'y');
        }
    } catch (err) {
        toast('Failed to save config', 'r');
        console.error('[Config] Save error:', err);
    }
}

// ── Private helpers ───────────────────────────────────────────────────────────

/** Shorthand element selector */
function _el(id) { return document.getElementById(id); }

/**
 * Update the SIM/LIVE badge text and colour class.
 * @param {boolean} isSimulation
 */
function _updateModeBadge(isSimulation) {
    const badge = _el('mode-badge');
    badge.textContent = isSimulation ? 'SIM' : 'LIVE';
    badge.className   = 'mode-badge ' + (isSimulation ? 'mode-sim' : 'mode-live');
}
