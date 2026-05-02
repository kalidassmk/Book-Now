package com.bogoai.booknow.model;

import lombok.Data;
import java.io.Serializable;

/**
 * Global dynamic configuration for the trading bot.
 * Stored in Redis (Key: TRADING_CONFIG) and modifiable via the dashboard.
 */
@Data
public class TradingConfig implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Toggle automated trading */
    private boolean autoBuyEnabled;

    /** USDT amount to spend per trade */
    private double buyAmountUsdt;

    /** Target profit percentage (e.g., 1.9 for $0.20 profit on $12) */
    private double profitPct;

    /** Target profit amount in USDT (e.g., 0.20). If > 0, this overrides profitPct. */
    private double profitAmountUsdt;

    /** Percentage below market price to place limit buy (e.g., 0.3) */
    private double limitBuyOffsetPct;

    /** Trailing stop loss percentage (e.g., 2.0) */
    private double tslPct;

    public TradingConfig() {
        // Default safe values
        this.autoBuyEnabled = false;
        this.buyAmountUsdt = 12.0;
        this.profitPct = 0; // Set to 0 to prioritize profitAmountUsdt
        this.profitAmountUsdt = 0.20;
        this.limitBuyOffsetPct = 0.3;
        this.tslPct = 2.0;
    }
}
