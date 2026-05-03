package com.bogoai.booknow.util;

import com.bogoai.api.client.BinanceApiRestClient;
import com.bogoai.api.client.domain.general.ExchangeInfo;
import com.bogoai.api.client.domain.general.SymbolFilter;
import com.bogoai.api.client.domain.general.SymbolInfo;
import com.bogoai.booknow.model.SymbolRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.TimeUnit;

/**
 * Service to manage Binance Exchange filters (Min Notional, Lot Size, etc.)
 * Caches rules in Redis to avoid repeated API calls.
 */
@Slf4j
@Service
public class BinanceFilterService {

    private static final String REDIS_KEY_PREFIX = "BINANCE:SYMBOL:";

    @Autowired
    private BinanceApiRestClient prodBinanceApiARestClient;

    @Autowired
    private RedisTemplate<String, SymbolRule> redisTemplateSymbolRule;

    @Autowired
    private BinanceRateLimitGuard rateLimitGuard;

    @PostConstruct
    public void init() {
        log.info("[FilterService] Initializing startup cache...");
        refreshCache();
    }

    /**
     * Load all symbols from Binance and cache only USDT pairs.
     */
    @Scheduled(fixedRate = 3600000) // Every 1 hour
    public void refreshCache() {
        if (rateLimitGuard.isBanned()) {
            log.warn("[FilterService] Skipping exchangeInfo refresh — Binance ban active for {}s",
                rateLimitGuard.banRemainingSeconds());
            return;
        }
        try {
            log.info("[FilterService] Refreshing ALL symbols from Binance...");
            ExchangeInfo info = prodBinanceApiARestClient.getExchangeInfo();
            if (info != null && info.getSymbols() != null) {
                int count = 0;
                for (SymbolInfo s : info.getSymbols()) {
                    if (s.getSymbol().endsWith("USDT")) {
                        SymbolRule rule = extractRule(s);
                        saveToRedis(s.getSymbol(), rule);
                        count++;
                    }
                }
                log.info("[FilterService] Redis cache updated. Loaded {} USDT symbols.", count);
            }
        } catch (Exception e) {
            if (rateLimitGuard.reportIfBanned(e)) return;
            log.error("[FilterService] CRITICAL: Failed to refresh exchange info: {}", e.getMessage());
        }
    }

    /**
     * Get rules for a specific symbol.
     */
    public SymbolRule getOrFetch(String symbol) {
        if (!symbol.endsWith("USDT")) {
            throw new RuntimeException("❌ Aborted: Only USDT pairs are supported for trading.");
        }
        
        SymbolRule rule = redisTemplateSymbolRule.opsForValue().get(REDIS_KEY_PREFIX + symbol);
        if (rule != null) {
            log.debug("[FilterService] [CACHE_HIT] [{}] Rules loaded", symbol);
            return rule;
        }

        if (rateLimitGuard.isBanned()) {
            // Don't even attempt the on-demand fetch — it will fail and
            // potentially extend the ban. Surface a clear error to the
            // caller so the trade aborts cleanly.
            throw new RuntimeException("❌ Binance ban active (" + rateLimitGuard.banRemainingSeconds()
                + "s remaining). Cannot fetch filter data for " + symbol + ". Trade aborted for safety.");
        }

        log.debug("[FilterService] [CACHE_MISS] [{}] Fetching on-demand...", symbol);
        try {
            ExchangeInfo info = prodBinanceApiARestClient.getExchangeInfo();
            if (info != null && info.getSymbols() != null) {
                for (SymbolInfo s : info.getSymbols()) {
                    if (s.getSymbol().equals(symbol)) {
                        rule = extractRule(s);
                        saveToRedis(symbol, rule);
                        log.debug("[FilterService] [ON_DEMAND_SUCCESS] [{}] Cached successfully", symbol);
                        return rule;
                    }
                }
            }
        } catch (Exception e) {
            if (rateLimitGuard.reportIfBanned(e)) {
                throw new RuntimeException("❌ Binance ban triggered while fetching " + symbol
                    + ". Trade aborted; cool-down: " + rateLimitGuard.banRemainingSeconds() + "s.", e);
            }
            log.error("[FilterService] [ON_DEMAND_ERROR] [{}] Fetch failed: {}", symbol, e.getMessage());
        }

        throw new RuntimeException("❌ CRITICAL: No Binance filter data found for symbol: " + symbol + ". Trade aborted for safety.");
    }

    private void saveToRedis(String symbol, SymbolRule rule) {
        redisTemplateSymbolRule.opsForValue().set(REDIS_KEY_PREFIX + symbol, rule, 24, TimeUnit.HOURS);
    }

    private SymbolRule extractRule(SymbolInfo symbol) {
        SymbolRule rule = new SymbolRule();
        rule.setSymbol(symbol.getSymbol());

        // Extract LOT_SIZE
        SymbolFilter lotSize = symbol.getSymbolFilter("LOT_SIZE");
        if (lotSize != null) {
            rule.setStepSize(new BigDecimal(lotSize.getStepSize()));
            rule.setMinQty(new BigDecimal(lotSize.getMinQty()));
            rule.setMaxQty(new BigDecimal(lotSize.getMaxQty()));
        } else {
            rule.setStepSize(new BigDecimal("0.00000001"));
            rule.setMinQty(new BigDecimal("0.00000001"));
            rule.setMaxQty(new BigDecimal("999999999"));
        }

        // Extract PRICE_FILTER
        SymbolFilter priceFilter = symbol.getSymbolFilter("PRICE_FILTER");
        if (priceFilter != null) {
            rule.setTickSize(new BigDecimal(priceFilter.getTickSize()));
        } else {
            rule.setTickSize(new BigDecimal("0.00000001"));
        }

        // Extract MIN_NOTIONAL or NOTIONAL
        SymbolFilter minNotionalFilter = symbol.getSymbolFilter("MIN_NOTIONAL");
        if (minNotionalFilter == null) {
            minNotionalFilter = symbol.getSymbolFilter("NOTIONAL");
        }

        if (minNotionalFilter != null && minNotionalFilter.getMinNotional() != null) {
            rule.setMinNotional(new BigDecimal(minNotionalFilter.getMinNotional()));
        } else {
            // Default to 5.0 USDT if filter is missing (safe floor for Binance)
            rule.setMinNotional(new BigDecimal("5.0"));
        }

        return rule;
    }

    /**
     * Rounds price to symbol's tickSize.
     */
    public BigDecimal roundPrice(String symbol, BigDecimal price) {
        SymbolRule rule = getOrFetch(symbol);
        BigDecimal tickSize = rule.getTickSize();
        return price.divide(tickSize, 0, RoundingMode.FLOOR).multiply(tickSize);
    }

    /**
     * Rounds quantity to symbol's stepSize.
     */
    public BigDecimal roundQuantity(String symbol, BigDecimal qty) {
        SymbolRule rule = getOrFetch(symbol);
        BigDecimal stepSize = rule.getStepSize();
        return qty.divide(stepSize, 0, RoundingMode.FLOOR).multiply(stepSize);
    }

    /**
     * Validate if the trade meets MIN_NOTIONAL.
     */
    public void validateNotional(String symbol, BigDecimal qty, BigDecimal price) {
        SymbolRule rule = getOrFetch(symbol);
        BigDecimal notional = qty.multiply(price);
        BigDecimal minNotional = rule.getMinNotional() != null ? rule.getMinNotional() : new BigDecimal("5.0");
        if (notional.compareTo(minNotional) < 0) {
            throw new RuntimeException("❌ Trade size too small! Notional " + notional + " < minNotional " + rule.getMinNotional());
        }
    }
}
