package com.bogoai.booknow.processor;

import com.bogoai.booknow.model.CurrentPrice;
import com.bogoai.booknow.repository.BookNowRepository;
import com.bogoai.booknow.util.TradeExecutor;
import com.bogoai.booknow.util.TradeState;
import com.bogoai.booknow.util.TradingConfigService;
import com.bogoai.booknow.util.TrailingStopLossService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static com.bogoai.booknow.util.Constant.CURRENT_PRICE;

/**
 * Unified position monitor.
 *
 * Runs every second and, for every open position:
 *   1. Updates the trailing-stop-loss high-water mark and triggers a market
 *      exit if price has dropped past the configured TSL %.
 *   2. Enforces the max-hold timer — if a position hasn't hit its +$0.20
 *      limit-sell within {@code maxHoldSeconds}, force a market exit so we
 *      don't bag-hold a coin that's gone sideways or down.
 *
 * Both paths route through {@link TradeExecutor#forceMarketExit} which:
 *   - cancels the open GTC limit-sell (so qty is free for the market sell)
 *   - places a real MARKET SELL on Binance
 *   - cleans up Redis + in-memory state
 *
 * Replaces the older TSL-only loop that called {@code trySell()} — which
 * recorded a sell in Redis but never actually placed an order on Binance.
 */
@Slf4j
@Component
public class TrailingStopLossProcessor {

    @Autowired private TradeState              tradeState;
    @Autowired private BookNowRepository       repository;
    @Autowired private TrailingStopLossService tslService;
    @Autowired private TradeExecutor           tradeExecutor;
    @Autowired private TradingConfigService    configService;

    @Scheduled(fixedDelay = 1000)
    public void monitor() {
        Map<String, TradeState.Position> positions = tradeState.snapshot();
        if (positions.isEmpty()) return;

        Map<String, CurrentPrice> prices = repository.getAllCurrentPrice(CURRENT_PRICE);
        int maxHold = configService.getMaxHoldSeconds();
        Instant now = Instant.now();

        for (Map.Entry<String, TradeState.Position> e : positions.entrySet()) {
            String symbol = e.getKey();
            TradeState.Position p = e.getValue();
            CurrentPrice cp = prices.get(symbol);
            if (cp == null) continue;

            // 1) Trailing stop-loss
            if (tslService.checkAndTrack(symbol, cp.getPrice())) {
                log.info("[Monitor] TSL triggered for {} — forcing market exit", symbol);
                tradeExecutor.forceMarketExit(symbol, cp, "TSL");
                continue;
            }

            // 2) Max-hold timer — only if configured (>0) and we have a real entry time
            if (maxHold > 0 && p.entryTime != null) {
                long heldFor = Duration.between(p.entryTime, now).getSeconds();
                if (heldFor >= maxHold) {
                    log.info("[Monitor] Max-hold {}s exceeded for {} (held {}s) — forcing market exit",
                        maxHold, symbol, heldFor);
                    tradeExecutor.forceMarketExit(symbol, cp, "MAX_HOLD");
                }
            }
        }
    }
}
