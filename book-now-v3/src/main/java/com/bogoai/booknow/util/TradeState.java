package com.bogoai.booknow.util;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds in-memory state that must be shared across all rule threads.
 * Using ConcurrentHashMap instead of the old static HashMap in Constant interface.
 *
 * Spring bean (singleton) so it is injected wherever needed.
 */
import org.springframework.stereotype.Component;

@Component
public class TradeState {

    /**
     * Tracks symbols currently held (bought but not yet sold).
     * Key = symbol (e.g. "SOLUSDT"), Value = rule that triggered the buy.
     */
    private final ConcurrentHashMap<String, String> activeBuyMap = new ConcurrentHashMap<>();

    public boolean isAlreadyBought(String symbol) {
        return activeBuyMap.containsKey(symbol);
    }

    public void markBought(String symbol, String rule) {
        activeBuyMap.put(symbol, rule);
    }

    public void markSold(String symbol) {
        activeBuyMap.remove(symbol);
    }

    public ConcurrentHashMap<String, String> getActiveBuyMap() {
        return activeBuyMap;
    }
}
