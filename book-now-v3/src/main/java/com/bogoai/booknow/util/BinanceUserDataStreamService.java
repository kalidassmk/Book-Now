package com.bogoai.booknow.util;

import com.bogoai.api.client.BinanceApiWebSocketClient;
import com.bogoai.api.client.domain.OrderStatus;
import com.bogoai.api.client.domain.event.AccountUpdateEvent;
import com.bogoai.api.client.domain.event.BalanceUpdateEvent;
import com.bogoai.api.client.domain.event.OrderTradeUpdateEvent;
import com.bogoai.api.client.domain.event.UserDataUpdateEvent;
import com.bogoai.booknow.model.CurrentPrice;
import com.bogoai.booknow.model.Sell;
import com.bogoai.booknow.model.WalletBalance;
import com.bogoai.booknow.repository.BookNowRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.Closeable;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static com.bogoai.booknow.util.Constant.BUY_KEY;
import static com.bogoai.booknow.util.Constant.SELL_KEY;

/**
 * Subscribes to the Binance spot user-data-stream and dispatches events to
 * the rest of the bot, replacing two REST polling loops:
 *
 *   - {@link BinanceBalanceService}'s 15-second {@code getAccount()} poll
 *     (~5,760 weight/day) → real-time {@code outboundAccountPosition} pushes.
 *   - On-demand {@code getOrderStatus()} polling for limit-sell fills
 *     → real-time {@code executionReport} pushes.
 *
 * Lifecycle:
 *   1. {@link #start()} (PostConstruct) sends {@code userDataStream.start}
 *      over Binance's WS-API to obtain a 60-minute listenKey, then opens a
 *      websocket subscription on the streams endpoint.
 *   2. A scheduled task sends {@code userDataStream.ping} every 25 minutes
 *      to extend the key's TTL (Binance recommends 30m; 25m gives a margin).
 *   3. On websocket failure, the next keepalive tick rebuilds the key + socket.
 *   4. {@link #stop()} (PreDestroy) closes the socket and {@code .stop}'s
 *      the listenKey so Binance can reclaim the slot — each account has a
 *      small cap on concurrent listenKeys.
 *
 * Note: Binance retired the legacy REST {@code POST/PUT/DELETE
 * /api/v3/userDataStream} endpoints (returns 410 Gone). The bundled SDK's
 * {@code BinanceApiRestClient.startUserDataStream()} still hits those dead
 * URLs, so we go through {@link BinanceWsApiClient} instead.
 */
@Slf4j
@Service
public class BinanceUserDataStreamService {

    private static final String REDIS_KEY_PREFIX = "BINANCE:BALANCE:";

    @Autowired private BinanceApiWebSocketClient   prodBinanceApiWebSocketClient;
    @Autowired private RedisTemplate<String, WalletBalance> redisTemplateWalletBalance;
    @Autowired private TradeState                  tradeState;
    @Autowired private TrailingStopLossService     tslService;
    @Autowired private BookNowRepository           repository;
    @Autowired private BinanceDustService          binanceDustService;

    /** Same toggle TradeExecutor uses — disable the stream entirely in paper mode. */
    @Value("${trading.live-mode:false}")
    private boolean liveMode;

    @Value("${prod.api.key}")
    private String prodApiKey;

    private final BinanceWsApiClient wsApi = new BinanceWsApiClient();

    /**
     * Epoch-ms after which it is OK to talk to Binance again. While
     * {@link BinanceWsApiClient.BinanceIpBannedException} is being thrown,
     * we set this to (now + Retry-After) so subsequent keepalive ticks
     * skip the connect attempt instead of digging the ban deeper.
     */
    private final java.util.concurrent.atomic.AtomicLong banUntilMs = new java.util.concurrent.atomic.AtomicLong(0);

    /** Latest listenKey we obtained from Binance. */
    private final AtomicReference<String> listenKey = new AtomicReference<>();

    /** Currently-open websocket subscription (closeable). */
    private final AtomicReference<Closeable> socket = new AtomicReference<>();

    @PostConstruct
    public void start() {
        if (!liveMode) {
            log.info("[UserDataStream] Live mode is OFF — skipping user-data-stream subscription.");
            return;
        }
        connect();
    }

    @PreDestroy
    public void stop() {
        closeSocketQuietly();
        String key = listenKey.getAndSet(null);
        if (key != null) {
            try {
                wsApi.closeUserDataStream(prodApiKey, key);
                log.info("[UserDataStream] Closed listenKey on shutdown");
            } catch (Exception e) {
                log.warn("[UserDataStream] Error closing listenKey on shutdown: {}", e.getMessage());
            }
        }
    }

    /**
     * Refresh the listenKey every 25 minutes. Binance lets it live for 60
     * minutes after the last keepAlive, so 25 minutes leaves plenty of margin.
     * If the socket dropped (or never started), this is also where we
     * re-establish it.
     */
    @Scheduled(fixedRate = 25 * 60 * 1000L)
    public void keepAlive() {
        if (!liveMode) return;
        long until = banUntilMs.get();
        if (until > 0 && System.currentTimeMillis() < until) {
            long secsLeft = (until - System.currentTimeMillis()) / 1000;
            log.warn("[UserDataStream] IP-banned by Binance — sleeping ~{}s before retry", secsLeft);
            return;
        }
        String key = listenKey.get();
        if (key == null || socket.get() == null) {
            log.info("[UserDataStream] No active stream — reconnecting");
            connect();
            return;
        }
        try {
            wsApi.keepAliveUserDataStream(prodApiKey, key);
            log.debug("[UserDataStream] Keepalive ping OK");
        } catch (Exception e) {
            log.warn("[UserDataStream] Keepalive failed ({}). Forcing reconnect.", e.getMessage());
            closeSocketQuietly();
            listenKey.set(null);
            connect();
        }
    }

    // ── Connection ───────────────────────────────────────────────────────────

    private synchronized void connect() {
        try {
            String key = wsApi.startUserDataStream(prodApiKey);
            listenKey.set(key);
            log.info("[UserDataStream] Obtained listenKey={}...{}", safeHead(key), safeTail(key));

            Closeable c = prodBinanceApiWebSocketClient.onUserDataUpdateEvent(key, this::handle);
            Closeable old = socket.getAndSet(c);
            closeQuietly(old);
            log.info("[UserDataStream] Websocket subscribed — balance + order updates are now push-driven");
        } catch (BinanceWsApiClient.BinanceIpBannedException ban) {
            // Don't dump a stack trace — this is a known, expected,
            // operator-actionable condition. Mark the cool-down so the
            // keepalive scheduler skips the next N ticks.
            long retry = ban.retryAfterSeconds > 0 ? ban.retryAfterSeconds : 120;
            banUntilMs.set(System.currentTimeMillis() + retry * 1000L);
            log.error("[UserDataStream] Binance IP BAN active — will retry in ~{}s. "
                + "Cause: too many recent requests (HTTP 418/429). Stop other Binance clients "
                + "and let the cooldown elapse.", retry);
            closeSocketQuietly();
            listenKey.set(null);
        } catch (Exception e) {
            // Special-case ban exceptions wrapped in another runtime
            if (BinanceWsApiClient.isIpBan(e)) {
                long retry = BinanceWsApiClient.extractRetryAfterSeconds(e);
                if (retry <= 0) retry = 120;
                banUntilMs.set(System.currentTimeMillis() + retry * 1000L);
                log.error("[UserDataStream] Binance IP BAN active — will retry in ~{}s.", retry);
            } else {
                log.error("[UserDataStream] Failed to connect: {}. Will retry on next keepalive.", e.getMessage(), e);
            }
            closeSocketQuietly();
            listenKey.set(null);
        }
    }

    private void closeSocketQuietly() {
        Closeable c = socket.getAndSet(null);
        closeQuietly(c);
    }

    private static void closeQuietly(Closeable c) {
        if (c == null) return;
        try { c.close(); } catch (Exception ignored) { }
    }

    private static String safeHead(String s) { return s != null && s.length() > 4 ? s.substring(0, 4) : "?"; }
    private static String safeTail(String s) { return s != null && s.length() > 4 ? s.substring(s.length() - 4) : "?"; }

    // ── Event dispatch ───────────────────────────────────────────────────────

    private void handle(UserDataUpdateEvent event) {
        if (event == null || event.getEventType() == null) return;
        try {
            switch (event.getEventType()) {
                case ACCOUNT_UPDATE:
                case ACCOUNT_POSITION_UPDATE:
                    onAccountUpdate(event.getAccountUpdateEvent());
                    break;
                case BALANCE_UPDATE:
                    onBalanceUpdate(event.getBalanceUpdateEvent());
                    break;
                case ORDER_TRADE_UPDATE:
                    onOrderTradeUpdate(event.getOrderTradeUpdateEvent());
                    break;
                default:
                    log.debug("[UserDataStream] Ignoring event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("[UserDataStream] Handler error on {}: {}", event.getEventType(), e.getMessage(), e);
        }
    }

    /** Snapshot of all non-zero balances, pushed by Binance after every state change. */
    private void onAccountUpdate(AccountUpdateEvent ev) {
        if (ev == null || ev.getBalances() == null) return;
        long now = System.currentTimeMillis();
        int changed = 0;
        for (com.bogoai.api.client.domain.account.AssetBalance b : ev.getBalances()) {
            double free = parseDouble(b.getFree());
            double locked = parseDouble(b.getLocked());
            if (free <= 0 && locked <= 0) {
                // Asset went to zero — drop it from the cache so it's not stale.
                redisTemplateWalletBalance.delete(REDIS_KEY_PREFIX + b.getAsset());
                continue;
            }
            WalletBalance model = new WalletBalance(b.getAsset(), b.getFree(), b.getLocked(), now);
            redisTemplateWalletBalance.opsForValue().set(REDIS_KEY_PREFIX + b.getAsset(), model);
            changed++;
        }
        log.debug("[UserDataStream] account update — {} assets refreshed", changed);
    }

    /**
     * Single-asset delta. Apply to the cached WalletBalance if present;
     * otherwise the next account snapshot will fill it in.
     */
    private void onBalanceUpdate(BalanceUpdateEvent ev) {
        if (ev == null || ev.getAsset() == null) return;
        String key = REDIS_KEY_PREFIX + ev.getAsset();
        WalletBalance cur = redisTemplateWalletBalance.opsForValue().get(key);
        if (cur == null) {
            log.debug("[UserDataStream] balanceUpdate {} delta={} (no cached row, will sync on next snapshot)",
                ev.getAsset(), ev.getBalanceDelta());
            return;
        }
        try {
            BigDecimal newFree = new BigDecimal(cur.getFree()).add(new BigDecimal(ev.getBalanceDelta()));
            cur.setFree(newFree.toPlainString());
            cur.setUpdatedAt(System.currentTimeMillis());
            redisTemplateWalletBalance.opsForValue().set(key, cur);
        } catch (NumberFormatException e) {
            log.warn("[UserDataStream] Bad balance delta for {}: {}", ev.getAsset(), ev.getBalanceDelta());
        }
    }

    /**
     * Order lifecycle events. Two we care about:
     *   - The bot's GTC limit-sell at +$0.20 just FILLED → close the position
     *     in TradeState immediately so the rule can re-arm on this symbol and
     *     the position monitor doesn't try to "force exit" an already-closed
     *     position.
     *   - A buy order just FILLED → log it (the buy path already handles state
     *     synchronously, but we want a record).
     *
     * Without this, the bot only learned about its limit-sell fill by polling
     * {@code getOrderStatus()} or by attempting a force-exit and seeing
     * "Unknown order" come back from Binance.
     */
    private void onOrderTradeUpdate(OrderTradeUpdateEvent ev) {
        if (ev == null || ev.getSymbol() == null || ev.getOrderStatus() == null) return;
        String symbol = ev.getSymbol();
        OrderStatus status = ev.getOrderStatus();

        log.info("[UserDataStream] order {} {} {} status={} executedQty={} price={}",
            symbol, ev.getSide(), ev.getType(), status, ev.getAccumulatedQuantity(), ev.getPrice());

        if (status != OrderStatus.FILLED) return;

        // Was this our outstanding limit-sell? If so, the +$0.20 target hit —
        // close the position cleanly without waiting for the monitor.
        tradeState.getPosition(symbol).ifPresent(p -> {
            if (p.openSellOrderId != null && p.openSellOrderId.equals(ev.getOrderId())) {
                BigDecimal sellPrice;
                try {
                    sellPrice = new BigDecimal(ev.getPrice());
                    if (sellPrice.signum() == 0) {
                        // For some events Binance returns "0" in price — fall back to last filled price.
                        sellPrice = new BigDecimal(ev.getPriceOfLastFilledTrade());
                    }
                } catch (Exception parseErr) {
                    sellPrice = p.buyPrice; // best-effort fallback
                }

                Sell sell = new Sell();
                sell.setSellingPoint(sellPrice.toPlainString());
                sell.setStatus("Y");
                sell.setTimestamp(Timestamp.from(Instant.now()));

                CurrentPrice cp = repository.getCurrentPrice(Constant.CURRENT_PRICE, symbol);
                if (cp != null) {
                    sell.setSellMaxPercentage(cp.getPercentage());
                    sell.setSellAveragePercentage(cp.getPercentage());
                    sell.setSellLeastPercentage(cp.getPercentage());
                }

                repository.saveSell(SELL_KEY, symbol, sell);
                repository.deleteBuy(BUY_KEY, symbol);
                tradeState.markSold(symbol);
                tslService.reset(symbol);

                log.info("[UserDataStream] +TARGET HIT — limit-sell #{} for {} filled @ {}. Position closed.",
                    ev.getOrderId(), symbol, sellPrice);

                try {
                    binanceDustService.sweepToBnb(symbol.replace("USDT", ""));
                } catch (Exception e) {
                    log.warn("[UserDataStream] Dust sweep failed for {}: {}", symbol, e.getMessage());
                }
            }
        });
    }

    private static double parseDouble(String s) {
        try { return s == null || s.isEmpty() ? 0.0 : Double.parseDouble(s); }
        catch (NumberFormatException e) { return 0.0; }
    }
}
