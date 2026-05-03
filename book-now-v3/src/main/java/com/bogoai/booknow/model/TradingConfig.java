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

    /**
     * Fast-scalp mode. When true, the bot bypasses the 2-month CoinAnalyzer
     * gate and the multi-agent ConsensusCoordinator on the buy path — rules
     * call TradeExecutor.tryBuy() directly. Designed for "buy fast, +0.20
     * USDT, sell immediately" trading where decision latency matters more
     * than fundamental confirmation.
     */
    private boolean fastScalpMode;

    /**
     * Max time to hold a position (seconds) before a forced market exit.
     * The PositionMonitor cancels the open limit-sell and dumps at market
     * once this is exceeded. 0 disables the timeout.
     */
    private int maxHoldSeconds;

    /**
     * If true, exits triggered by TSL or max-hold are placed as MARKET sells
     * (immediate fill). If false, fall back to the existing limit-sell logic.
     */
    private boolean marketExitOnTimeout;

    public TradingConfig() {
        // Default safe values
        this.autoBuyEnabled = false;
        this.buyAmountUsdt = 100.0;
        this.profitPct = 0; // Set to 0 to prioritize profitAmountUsdt
        this.profitAmountUsdt = 0.20;
        this.limitBuyOffsetPct = 0.3;
        this.tslPct = 2.0;
        // Fast-scalp defaults — match user intent (buy fast, +$0.20, exit fast).
        this.fastScalpMode = true;
        this.maxHoldSeconds = 300;       // 5 minutes
        this.marketExitOnTimeout = true;
    }
}
