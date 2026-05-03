package com.bogoai.booknow.util;

import com.bogoai.api.client.BinanceApiRestClient;
import com.bogoai.api.client.domain.account.Account;
import com.bogoai.booknow.model.WalletBalance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * Service to fetch Binance account balances and cache them in Redis.
 * Key: BINANCE:BALANCE:<ASSET>
 */
@Slf4j
@Service
public class BinanceBalanceService {

    private static final String REDIS_KEY_PREFIX = "BINANCE:BALANCE:";

    @Autowired
    private BinanceApiRestClient prodBinanceApiARestClient;

    @Autowired
    private RedisTemplate<String, WalletBalance> redisTemplateWalletBalance;

    @PostConstruct
    public void init() {
        refreshBalances();
    }

    /**
     * Reconciliation safety net.
     *
     * Real-time balance updates are pushed by {@link BinanceUserDataStreamService}
     * over the user-data-stream websocket, so this REST poll only needs to
     * exist as a guard against missed events (e.g. websocket reconnects, bot
     * restart before the first push lands). Cadence dropped from 15s to 5
     * minutes — that's ~5,760 → 288 calls/day for this loop alone.
     */
    @Scheduled(fixedRate = 300_000)
    public void refreshBalances() {
        try {
            log.info("[BalanceService] Refreshing account balances from Binance...");
            Account account = prodBinanceApiARestClient.getAccount();
            if (account != null && account.getBalances() != null) {
                List<com.bogoai.api.client.domain.account.AssetBalance> balances = account.getBalances();
                int count = 0;
                for (com.bogoai.api.client.domain.account.AssetBalance b : balances) {
                    double free = Double.parseDouble(b.getFree());
                    double locked = Double.parseDouble(b.getLocked());

                    if (free > 0 || locked > 0) {
                        WalletBalance model = new WalletBalance(
                                b.getAsset(),
                                b.getFree(),
                                b.getLocked(),
                                System.currentTimeMillis()
                        );
                        String key = REDIS_KEY_PREFIX + b.getAsset();
                        redisTemplateWalletBalance.opsForValue().set(key, model);
                        count++;
                    }
                }
                log.info("[BalanceService] Cached {} assets with non-zero balances in Redis.", count);
            }
        } catch (Exception e) {
            log.error("[BalanceService] Failed to fetch balances: {}", e.getMessage());
        }
    }
}
