package com.bogoai.booknow.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trailing Stop-Loss (TSL) Service.
 * Tracks the highest price seen since trade open and triggers sell if price drops by X%.
 */
@Slf4j
@Service
public class TrailingStopLossService {

    @Value("${trading.tsl.percentage:2.0}")
    private double trailingPercentage;

    /** Tracks the highest price seen for each active symbol */
    private final Map<String, BigDecimal> highestPrices = new ConcurrentHashMap<>();

    /**
     * Updates the highest price seen for a symbol and checks for TSL trigger.
     * 
     * @param symbol       Symbol (e.g. "SOLUSDT")
     * @param currentPrice Current market price
     * @return true if TSL is triggered (SELL), false otherwise
     */
    public boolean checkAndTrack(String symbol, BigDecimal currentPrice) {
        // Initialize if not present (start tracking at current price)
        highestPrices.putIfAbsent(symbol, currentPrice);
        
        BigDecimal highest = highestPrices.get(symbol);

        // 1. Update highestPrice if current is higher
        if (currentPrice.compareTo(highest) > 0) {
            highestPrices.put(symbol, currentPrice);
            highest = currentPrice;
            log.debug("[TSL] {} new highest price seen: {}", symbol, highest);
        }

        // 2. Calculate stopLossPrice (highest * (1 - trailingPercentage / 100))
        BigDecimal multiplier = BigDecimal.valueOf(1.0 - trailingPercentage / 100.0);
        BigDecimal stopLossPrice = highest.multiply(multiplier);

        // 3. Trigger SELL if currentPrice <= stopLossPrice
        if (currentPrice.compareTo(stopLossPrice) <= 0) {
            log.info("[TSL] TRIGGER SELL for {}! Current: {} <= StopLoss: {} (Highest: {})", 
                symbol, currentPrice, stopLossPrice.setScale(8, RoundingMode.HALF_UP), highest);
            return true;
        }

        return false;
    }

    /**
     * Start tracking a new symbol.
     */
    public void startTracking(String symbol, BigDecimal initialPrice) {
        highestPrices.put(symbol, initialPrice);
        log.info("[TSL] Started tracking {} at price {}", symbol, initialPrice);
    }

    /**
     * Stop tracking (cleanup after sell).
     */
    public void reset(String symbol) {
        highestPrices.remove(symbol);
        log.debug("[TSL] Reset tracker for {}", symbol);
    }
}
