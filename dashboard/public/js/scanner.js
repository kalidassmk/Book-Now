/**
 * public/js/scanner.js
 * ─────────────────────────────────────────────────────────────────────────────
 * Renders the fast-mover coin scanner table.
 *
 * Receives an array of enriched coin objects from the server and renders
 * each as a table row with signal badge, prices, P&L, and action buttons.
 *
 * Signal CSS class map:
 *   LT2MIN_0>3 → s-lt2min  (highest intensity — red)
 *   UF_0>3     → s-uf03    (orange)
 *   UF_0>2     → s-uf02    (yellow)
 *   UF_2>3     → s-uf23    (purple)
 *   FAST_MOVE  → s-fm      (blue baseline)
 */

/** CSS class for each signal type */
const SIGNAL_CLASS = {
    'USF_5>7':    's-usf57',
    'UF_3>5':     's-uf35',
    'SF_2>3':     's-sf23',
    'LT2MIN_0>3': 's-lt2min',
    'UF_0>3':     's-uf03',
    'UF_0>2':     's-uf02',
    'UF_2>3':     's-uf23',
    'FAST_MOVE':  's-fm',
};

const REC_CLASSES = {
    'STRONG_BUY': 'rec-strong-buy',
    'BUY':        'rec-buy',
    'NEUTRAL':    'rec-neutral',
    'WAIT':       'rec-wait',
    'DONT_BUY':   'rec-dont-buy',
};

/** Browser-side map to store user's manual price edits in the table rows */
const manualPriceMap = new Map();

/**
 * Re-render the scanner table body with fresh coin data.
 * Called every time a WebSocket 'update' event arrives.
 *
 * @param {Array<object>} coins  Coin objects from server
 */
function renderScanner(coins) {
    const tbody = document.getElementById('scanner-body');
    const isLimit = (typeof orders !== 'undefined' && orders.getMode() === 'LIMIT');
    const displayStyle = isLimit ? 'table-cell' : 'none';

    if (!coins || coins.length === 0) {
        tbody.innerHTML = '';
        return;
    }

    const coinMap = new Map(coins.map(c => [c.symbol, c]));

    // 1. Remove stale rows
    Array.from(tbody.rows).forEach(row => {
        const symbol = row.getAttribute('data-symbol');
        if (symbol && !coinMap.has(symbol)) row.remove();
    });

    // 2. Update or Add rows
    coins.forEach(c => {
        let row = tbody.querySelector(`tr[data-symbol="${c.symbol}"]`);
        
        if (!row) {
            row = document.createElement('tr');
            row.setAttribute('data-symbol', c.symbol);
            row.onclick = () => orders.fillManual(c.symbol, c.currentPrice || 0);
            tbody.appendChild(row);
        }

        // Check if editing
        const active = document.activeElement;
        const isEditingThisRow = active && active.id === `limit-${c.symbol}`;

        const sigClass = SIGNAL_CLASS[c.signal] || 's-fm';
        const pnlClass = c.pnlPct == null ? 'nil' : c.pnlPct > 0 ? 'pos' : c.pnlPct < 0 ? 'neg' : 'nil';
        const pnlStr   = c.pnlPct == null ? '—' : (c.pnlPct > 0 ? '+' : '') + c.pnlPct + '%';
        const scoreStr = c.analysisScore != null ? `${c.analysisScore}/7` : '';
        const recClass = REC_CLASSES[c.recommendation] || '';

        row.className = c.hasPosition ? 'has-pos' : '';
        
        if (!row.innerHTML) {
            row.innerHTML = _buildRowTemplate(c, sigClass, pnlClass, pnlStr, scoreStr, recClass, displayStyle);
        } else {
            // Update live data
            const volCell = row.querySelector('.vol-live');
            if (volCell) volCell.textContent = window.utils.fmtVol(c.vol24h);

            const priceCell = row.querySelector('.price-live');
            if (priceCell) priceCell.textContent = fmt(c.currentPrice);

            const pnlCell = row.querySelector('.pnl');
            if (pnlCell) {
                pnlCell.className = `pnl ${pnlClass}`;
                pnlCell.querySelector('div').textContent = pnlStr;
                const profitDiv = pnlCell.querySelectorAll('div')[1];
                if (profitDiv) {
                    profitDiv.textContent = currentCurrency === 'USDT' ? '$' + (c.profitUsdt || 0) : '₹' + (c.profitInr || 0);
                }
            }

            const buyAt = row.querySelector('.buy-at');
            if (buyAt) buyAt.textContent = c.buyPrice ? fmt(c.buyPrice) : '—';

            const limitTd = row.querySelector('.td-limit-price');
            if (limitTd) limitTd.style.display = displayStyle;
            
            const buyBtn = row.querySelector('.btn-buy');
            if (buyBtn) buyBtn.textContent = isLimit ? 'LIMIT BUY' : 'BUY';
        }
    });
}

function _buildRowTemplate(c, sigClass, pnlClass, pnlStr, scoreStr, recClass, displayStyle) {
    const isLimit = (typeof orders !== 'undefined' && orders.getMode() === 'LIMIT');
    return `
      <td><span class="sym">${c.symbol}</span></td>
      <td><span class="signal-tag ${sigClass}">${c.signal}</span></td>
      <td>
        <div class="analysis-cell">
          <span class="rec-badge ${recClass}">${(c.recommendation || '...').replace('_',' ')}</span>
          <span class="score-small">${scoreStr}</span>
          ${c.newsAnalysis ? `<span class="rec-badge ${c.newsAnalysis.decision === 'BUY' ? 'rec-buy' : c.newsAnalysis.decision === 'SELL' ? 'rec-dont-buy' : 'rec-neutral'}" title="News Sentiment Score: ${Number(c.newsAnalysis.score || c.newsAnalysis.final_score || 0).toFixed(2)}" style="margin-left: 4px;">📰 ${c.newsAnalysis.decision || 'HOLD'}</span>` : ''}
        </div>
      </td>
      <td class="price vol-live">${window.utils.fmtVol(c.vol24h)}</td>
      <td class="price price-live">${fmt(c.currentPrice)}</td>
      <td class="price buy-at" style="color:var(--blue)">${c.buyPrice ? fmt(c.buyPrice) : '—'}</td>
      
      <td class="td-limit-price" style="display:${displayStyle}">
          <input type="number" class="row-limit-inp" id="limit-${c.symbol}" 
                 list="dl-${c.symbol}"
                 placeholder="Price..."
                 value="${manualPriceMap.get(c.symbol) || (c.metrics?.low7d) || (c.currentPrice * 0.997).toFixed(8)}" 
                 step="any"
                 onchange="saveRowPrice('${c.symbol}', this.value)"
                 onclick="event.stopPropagation()"/>
          <datalist id="dl-${c.symbol}">
            ${c.metrics ? `
              <option value="${c.metrics.low7d}">Weekly Low: ${fmt(c.metrics.low7d)}</option>
              <option value="${c.metrics.low14d}">Last Week Low: ${fmt(c.metrics.low14d)}</option>
              <option value="${c.metrics.low30d}">Monthly Low: ${fmt(c.metrics.low30d)}</option>
              <option value="${c.metrics.low60d}">60d Low: ${fmt(c.metrics.low60d)}</option>
            ` : ''}
          </datalist>
      </td>

      <td class="pnl ${pnlClass}">
        <div>${pnlStr}</div>
        ${c.isPosition ? `<div style="font-size:9px;opacity:0.8">${typeof currentCurrency !== 'undefined' && currentCurrency === 'USDT' ? '$' + (c.profitUsdt || 0) : '₹' + (c.profitInr || 0)}</div>` : ''}
      </td>
      <td class="held">${fmtTime(c.heldMs)}</td>

      <td>
        <div class="action-group">
          ${!c.hasPosition
              ? `<button class="btn btn-buy"
                         onclick="orders.quickBuy('${c.symbol}', event)">
                   ${isLimit ? 'LIMIT BUY' : 'BUY'}
                 </button>`
              : ''
          }
          ${c.hasPosition
              ? `<button class="btn btn-sell"
                         onclick="orders.quickSell('${c.symbol}', event)">
                   SELL
                 </button>`
              : ''
          }
          <button class=\"btn btn-binance\"
                  onclick=\"event.stopPropagation(); window.open('https://www.binance.com/en-IN/trade/${c.symbol.replace('USDT','')}_USDT?_from=markets&type=spot', '_blank')\"
                  title=\"Trade on Binance\">
            <img src=\"https://bin.bnbstatic.com/static/images/common/favicon.ico\" width=\"12\" height=\"12\" style=\"margin-bottom:2px\">
          </button>
          <button class=\"btn btn-analyze\"
                  onclick=\"event.stopPropagation();analyzeCoin('${c.symbol}')\"
                  title=\"2-month price + volume analysis\">
            📊
          </button>
        </div>
      </td>`;
}

function saveRowPrice(symbol, val) {
    manualPriceMap.set(symbol, val);
}
