package com.bogoai.booknow.service;

import com.bogoai.booknow.model.CurrentPrice;
import com.bogoai.booknow.repository.BookNowRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.bogoai.booknow.util.Constant.CURRENT_PRICE;

/**
 * Service that calculates a "Global Health Index" for coins using external signals.
 * Logic: (Social Sentiment * 0.5) + (Pump Potential * 0.5).
 */
@Slf4j
@Service
public class CoinIntelligenceService {

    @Autowired
    private BookNowRepository repository;

    private static final double HEALTH_THRESHOLD = 0.6;

    /**
     * Periodically refresh the Health Index for all symbols in Redis.
     * Called at startup and can be scheduled.
     */
    @PostConstruct
    public void initHealthIndex() {
        log.info("[Intelligence] Initializing Global Health Index for all symbols...");
        
        Map<String, CurrentPrice> prices = repository.getAllCurrentPrice(CURRENT_PRICE);
        if (prices.isEmpty()) return;

        prices.keySet().forEach(symbol -> {
            CompletableFuture.runAsync(() -> {
                double sentiment = fetchSocialSentiment(symbol);
                double pumpPotential = fetchPumpPotential(symbol);
                
                double index = (sentiment * 0.5) + (pumpPotential * 0.5);
                
                // Update Redis Hash (CurrentPrice)
                CurrentPrice cp = prices.get(symbol);
                if (cp != null) {
                    cp.setHealthIndex(index);
                    repository.saveCurrentPrice(CURRENT_PRICE, symbol, cp);
                    log.debug("[Intelligence] {} Health Index updated: {}", symbol, String.format("%.2f", index));
                }
            }).exceptionally(ex -> {
                log.error("[Intelligence] Error updating index for {}: {}", symbol, ex.getMessage());
                return null;
            });
        });
    }

    /**
     * Decision Gate: Returns true if the coin's health index is above the safe threshold.
     */
    public boolean shouldProceedWithBuy(String symbol) {
        Map<String, CurrentPrice> prices = repository.getAllCurrentPrice(CURRENT_PRICE);
        CurrentPrice cp = prices.get(symbol);
        
        if (cp == null) return false;
        
        boolean isHealthy = cp.getHealthIndex() >= HEALTH_THRESHOLD;
        if (!isHealthy) {
            log.warn("[Intelligence] {} BLOCKED: Health Index {} is below threshold {}", 
                symbol, String.format("%.2f", cp.getHealthIndex()), HEALTH_THRESHOLD);
        }
        return isHealthy;
    }

    // ── Placeholder External API Calls ────────────────────────────────────────

    private double fetchSocialSentiment(String symbol) {
        // TODO: Replace with real API (LunarCrush / StockGeist)
        // returns -1.0 to 1.0
        return 0.5; 
    }

    private double fetchPumpPotential(String symbol) {
        // TODO: Replace with real API (CoinGecko / CMC)
        // returns 0.0 to 1.0
        return 0.7;
    }
}
