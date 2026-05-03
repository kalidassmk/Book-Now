package com.bogoai.booknow.util;

import com.bogoai.api.client.BinanceApiRestClient;
import com.bogoai.api.client.domain.account.Account;
import com.bogoai.api.client.domain.account.AssetBalance;
import com.bogoai.booknow.model.DustAsset;
import com.bogoai.booknow.model.SymbolRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Set;

/**
 * Service to detect "dust" assets (non-tradable small balances).
 * Key: BINANCE:DUST:<ASSET>
 */
@Slf4j
@Service
public class BinanceDustService {

    private static final String REDIS_KEY_PREFIX = "BINANCE:DUST:";

    @Autowired
    private BinanceApiRestClient prodBinanceApiARestClient;

    @Autowired
    private RedisTemplate<String, DustAsset> redisTemplateDustAsset;

    @Autowired
    private BinanceFilterService filterService;

    @PostConstruct
    public void init() {
        detectDust();
    }

    /**
     * Dust scanner — formerly polled {@code getAccount()} every 60s
     * (~1,440 calls/day). The user-data-stream now pushes balance changes,
     * so this only runs every 5 minutes as a safety net for missed events.
     * For dust to materialise we need a balance change anyway, and those
     * arrive over the stream.
     */
    @Scheduled(fixedRate = 300_000)
    public void detectDust() {
        try {
            log.info("[DustService] Scanning for dust balances...");
            Account account = prodBinanceApiARestClient.getAccount();
            if (account == null || account.getBalances() == null) return;

            List<AssetBalance> balances = account.getBalances();
            int count = 0;
            for (AssetBalance b : balances) {
                if ("USDT".equalsIgnoreCase(b.getAsset())) continue;
                
                double free = Double.parseDouble(b.getFree());
                if (free <= 0) continue;

                // Only check for USDT pairs as that's our trading base
                String symbol = b.getAsset() + "USDT";
                try {
                    SymbolRule rule = filterService.getOrFetch(symbol);
                    if (rule == null) continue;

                    double minQty = rule.getMinQty().doubleValue();
                    double minNotional = rule.getMinNotional().doubleValue();
                    
                    // TODO: Get current price for notional check
                    // For now, if free < minQty, it's definitely dust
                    if (free < minQty) {
                        DustAsset dust = new DustAsset(
                                b.getAsset(),
                                b.getFree(),
                                "0.0", // Placeholder for USDT value
                                "DUST",
                                System.currentTimeMillis()
                        );
                        redisTemplateDustAsset.opsForValue().set(REDIS_KEY_PREFIX + b.getAsset(), dust);
                        count++;
                        log.debug("[DustService] Detected dust for {}: {} (minQty={})", b.getAsset(), b.getFree(), minQty);
                    } else {
                        // If it was dust before but now isn't, remove it
                        redisTemplateDustAsset.delete(REDIS_KEY_PREFIX + b.getAsset());
                    }
                } catch (Exception e) {
                    // Symbol might not exist as USDT pair (e.g. USDT itself or obscure coins)
                    continue;
                }
            }
            log.info("[DustService] Scan complete. Found {} dust assets.", count);
        } catch (Exception e) {
            log.error("[DustService] Error scanning for dust: {}", e.getMessage());
        }
    }

    public void removeDust(String asset) {
        redisTemplateDustAsset.delete(REDIS_KEY_PREFIX + asset);
    }

    /**
     * Automatically transfer dust assets to Spot wallet.
     * Runs every 10 seconds to avoid hitting Binance Universal Transfer rate limits.
     */
    @Scheduled(fixedRate = 10000)
    public void autoTransferDust() {
        try {
            Set<String> keys = redisTemplateDustAsset.keys(REDIS_KEY_PREFIX + "*");
            if (keys == null || keys.isEmpty()) return;

            log.info("[DustService] Auto-transfer job found {} dust assets to process...", keys.size());
            for (String key : keys) {
                DustAsset d = redisTemplateDustAsset.opsForValue().get(key);
                if (d == null) continue;

                String asset = d.getAsset();
                if ("USDT".equalsIgnoreCase(asset)) {
                    continue;
                }
                log.info("[DustService] Auto-transferring {} (qty: {})...", asset, d.getFree());

                try {
                    // FUNDING_MAIN moves from Funding to Spot
                    com.bogoai.api.client.domain.account.UniversalTransferResult result = 
                        prodBinanceApiARestClient.universalTransfer(asset, com.bogoai.api.client.domain.UniversalTransferType.FUNDING_MAIN, d.getFree());
                    
                    if (result != null && result.getTranId() != null) {
                        log.info("[DustService] SUCCESS: Transferred {} to Spot Wallet. ID: {}", asset, result.getTranId());
                        removeDust(asset);
                    }
                } catch (Exception e) {
                    log.error("[DustService] Transfer failed for {}: {}", asset, e.getMessage());
                    // Keep in Redis to retry next time
                }
                
                // Small sleep to be safe between multiple transfers
                Thread.sleep(500);
            }
        } catch (Exception e) {
            log.error("[DustService] Auto-transfer job encountered an error: {}", e.getMessage());
        }
    }

    /**
     * Convert a specific asset's dust balance to BNB.
     * @param asset e.g. "SOL"
     */
    public void sweepToBnb(String asset) {
        if ("BNB".equalsIgnoreCase(asset) || "USDT".equalsIgnoreCase(asset)) return;
        
        try {
            log.info("[DustService] Requesting BNB conversion for {}...", asset);
            Object result = prodBinanceApiARestClient.dustTransfer(java.util.Collections.singletonList(asset));
            log.info("[DustService] SUCCESS: Converted {} dust to BNB. Result: {}", asset, result);
        } catch (Exception e) {
            log.error("[DustService] BNB conversion failed for {}: {}", asset, e.getMessage());
        }
    }
}
