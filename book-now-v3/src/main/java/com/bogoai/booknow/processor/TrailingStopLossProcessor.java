package com.bogoai.booknow.processor;

import com.bogoai.booknow.model.CurrentPrice;
import com.bogoai.booknow.repository.BookNowRepository;
import com.bogoai.booknow.util.TradeExecutor;
import com.bogoai.booknow.util.TradeState;
import com.bogoai.booknow.util.TrailingStopLossService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.bogoai.booknow.util.Constant.CURRENT_PRICE;

/**
 * Periodically checks all active positions for Trailing Stop-Loss triggers.
 */
@Slf4j
@Component
public class TrailingStopLossProcessor {

    @Autowired
    private TradeState tradeState;

    @Autowired
    private BookNowRepository repository;

    @Autowired
    private TrailingStopLossService tslService;

    @Autowired
    private TradeExecutor tradeExecutor;

    /**
     * Monitor active positions every 1 second.
     * Uses the current price from Redis to check if TSL threshold is hit.
     */
    @Scheduled(fixedDelay = 1000)
    public void monitorTrailingStopLoss() {
        Map<String, String> activePositions = tradeState.getActiveBuyMap();
        if (activePositions.isEmpty()) return;

        Map<String, CurrentPrice> prices = repository.getAllCurrentPrice(CURRENT_PRICE);

        for (String symbol : activePositions.keySet()) {
            CurrentPrice cp = prices.get(symbol);
            if (cp == null) continue;

            // Update highest price and check for trigger
            boolean shouldSell = tslService.checkAndTrack(symbol, cp.getPrice());
            
            if (shouldSell) {
                log.info("[TSL PROCESSOR] Triggering SELL for {} due to Trailing Stop-Loss", symbol);
                tradeExecutor.trySell(symbol, cp, "TSL_TRIGGER");
            }
        }
    }
}
