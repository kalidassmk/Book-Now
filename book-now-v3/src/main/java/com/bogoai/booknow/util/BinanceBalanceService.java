package com.bogoai.booknow.util;

import com.bogoai.api.client.BinanceApiRestClient;
import com.bogoai.api.client.domain.account.Account;
import com.bogoai.api.client.domain.account.AssetBalance;
import com.bogoai.booknow.model.WalletBalance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;

/**
 * Owner of the {@code BINANCE:BALANCE:<ASSET>} Redis cache.
 *
 * The 15-second / 5-minute REST polling that used to refresh this cache is
 * gone. Real-time balance updates are pushed by
 * {@link BinanceUserDataStreamService} over the user-data-stream WebSocket
 * (events {@code outboundAccountPosition} and {@code balanceUpdate}); that
 * service now calls {@link #applyAccountSnapshot} and
 * {@link #applyBalanceDelta} on this bean instead of writing Redis itself.
 *
 * The only REST call left is a one-shot {@link #seedFromRest()} on startup
 * so the cache has a baseline before the first WS frame arrives. That
 * single call is guard-protected, so even during an active IP ban it will
 * skip cleanly.
 */
@Slf4j
@Service
public class BinanceBalanceService {

    private static final String REDIS_KEY_PREFIX = "BINANCE:BALANCE:";

    @Autowired
    private BinanceApiRestClient prodBinanceApiARestClient;

    @Autowired
    private RedisTemplate<String, WalletBalance> redisTemplateWalletBalance;

    @Autowired
    private BinanceRateLimitGuard rateLimitGuard;

    @PostConstruct
    public void init() {
        seedFromRest();
    }

    // ── Public API used by the WebSocket service ─────────────────────────────

    /**
     * Apply a full account snapshot (the {@code outboundAccountPosition}
     * payload). Replaces every balance row Binance reported; leaves
     * everything else as-is.
     */
    public void applyAccountSnapshot(List<AssetBalance> balances) {
        if (balances == null) return;
        long now = System.currentTimeMillis();
        int changed = 0;
        for (AssetBalance b : balances) {
            double free   = parseDouble(b.getFree());
            double locked = parseDouble(b.getLocked());
            if (free <= 0 && locked <= 0) {
                redisTemplateWalletBalance.delete(REDIS_KEY_PREFIX + b.getAsset());
                continue;
            }
            WalletBalance model = new WalletBalance(b.getAsset(), b.getFree(), b.getLocked(), now);
            redisTemplateWalletBalance.opsForValue().set(REDIS_KEY_PREFIX + b.getAsset(), model);
            changed++;
        }
        log.debug("[BalanceService] WS account snapshot applied — {} non-zero assets", changed);
    }

    /**
     * Apply a single-asset delta (the {@code balanceUpdate} payload).
     * If we don't have the asset cached yet, we skip — the next snapshot
     * will fill it in.
     */
    public void applyBalanceDelta(String asset, String balanceDelta) {
        if (asset == null || balanceDelta == null) return;
        String key = REDIS_KEY_PREFIX + asset;
        WalletBalance cur = redisTemplateWalletBalance.opsForValue().get(key);
        if (cur == null) {
            log.debug("[BalanceService] balanceUpdate {} delta={} (no cached row, will sync on next snapshot)",
                asset, balanceDelta);
            return;
        }
        try {
            BigDecimal newFree = new BigDecimal(cur.getFree()).add(new BigDecimal(balanceDelta));
            cur.setFree(newFree.toPlainString());
            cur.setUpdatedAt(System.currentTimeMillis());
            redisTemplateWalletBalance.opsForValue().set(key, cur);
        } catch (NumberFormatException e) {
            log.warn("[BalanceService] Bad balance delta for {}: {}", asset, balanceDelta);
        }
    }

    // ── One-shot seed ────────────────────────────────────────────────────────

    /**
     * Single REST call at startup to populate the cache before the WS
     * subscription delivers its first {@code outboundAccountPosition}.
     * Honours the rate-limit guard so a startup during an active ban
     * doesn't generate a 418 of its own.
     */
    public void seedFromRest() {
        if (rateLimitGuard.isBanned()) {
            log.warn("[BalanceService] Initial seed skipped — Binance ban active for {}s. "
                + "WS pushes will fill the cache once the listenKey reconnects.",
                rateLimitGuard.banRemainingSeconds());
            return;
        }
        try {
            log.info("[BalanceService] Seeding balances via single REST getAccount() — WS will own updates afterwards.");
            Account account = prodBinanceApiARestClient.getAccount();
            if (account != null && account.getBalances() != null) {
                applyAccountSnapshot(account.getBalances());
                long count = account.getBalances().stream()
                    .filter(b -> parseDouble(b.getFree()) > 0 || parseDouble(b.getLocked()) > 0)
                    .count();
                log.info("[BalanceService] Seed complete — {} assets with non-zero balances cached.", count);
            }
        } catch (Exception e) {
            if (rateLimitGuard.reportIfBanned(e)) {
                return;
            }
            log.error("[BalanceService] Initial seed failed: {}. WS pushes will fill the cache once it connects.",
                e.getMessage());
        }
    }

    private static double parseDouble(String s) {
        try { return s == null || s.isEmpty() ? 0.0 : Double.parseDouble(s); }
        catch (NumberFormatException e) { return 0.0; }
    }
}
