package com.bogoai.booknow.util;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

/**
 * In-memory state shared across all rule threads and the PositionMonitor.
 *
 * Tracks every active (bought-but-not-yet-sold) position with:
 *   - which rule triggered it
 *   - entry price and timestamp (for max-hold and PnL)
 *   - the Binance order id of the open limit-sell (so we can cancel it
 *     before forcing a market exit)
 */
@Component
public class TradeState {

    /** Per-symbol position metadata. */
    public static final class Position {
        public final String symbol;
        public final String rule;
        public final BigDecimal buyPrice;
        public final Instant entryTime;
        /** Order id of the open GTC limit-sell, or null if none was placed. */
        public volatile Long openSellOrderId;

        public Position(String symbol, String rule, BigDecimal buyPrice) {
            this.symbol = symbol;
            this.rule = rule;
            this.buyPrice = buyPrice;
            this.entryTime = Instant.now();
        }
    }

    private final ConcurrentHashMap<String, Position> positions = new ConcurrentHashMap<>();

    /**
     * Listeners notified when a position closes (either via the GTC fill
     * or a forced exit). Rules use this to re-arm their per-symbol "triggered"
     * guard so the same coin can be scalped again.
     */
    private final CopyOnWriteArrayList<Consumer<String>> sellListeners = new CopyOnWriteArrayList<>();

    public boolean isAlreadyBought(String symbol) {
        return positions.containsKey(symbol);
    }

    public void markBought(String symbol, String rule) {
        // Backward-compat overload: callers without buyPrice still work.
        positions.putIfAbsent(symbol, new Position(symbol, rule, BigDecimal.ZERO));
    }

    public void markBought(String symbol, String rule, BigDecimal buyPrice) {
        positions.put(symbol, new Position(symbol, rule, buyPrice));
    }

    public void recordOpenSellOrder(String symbol, Long orderId) {
        Position p = positions.get(symbol);
        if (p != null) {
            p.openSellOrderId = orderId;
        }
    }

    public Optional<Position> getPosition(String symbol) {
        return Optional.ofNullable(positions.get(symbol));
    }

    public Map<String, Position> snapshot() {
        return new java.util.HashMap<>(positions);
    }

    public void markSold(String symbol) {
        Position removed = positions.remove(symbol);
        if (removed != null) {
            for (Consumer<String> l : sellListeners) {
                try {
                    l.accept(symbol);
                } catch (Exception ignored) {
                    // listeners must not break each other
                }
            }
        }
    }

    public void addSellListener(Consumer<String> listener) {
        sellListeners.add(listener);
    }

    /**
     * @deprecated Kept for callers that still read the legacy symbol→rule map.
     *             Returns a fresh view; mutations do not affect the source.
     */
    @Deprecated
    public ConcurrentHashMap<String, String> getActiveBuyMap() {
        ConcurrentHashMap<String, String> view = new ConcurrentHashMap<>();
        positions.forEach((k, p) -> view.put(k, p.rule));
        return view;
    }
}
