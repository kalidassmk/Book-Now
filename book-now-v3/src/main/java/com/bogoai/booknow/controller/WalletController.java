package com.bogoai.booknow.controller;

import com.bogoai.booknow.model.DustAsset;
import com.bogoai.booknow.model.WalletBalance;
import com.bogoai.booknow.util.BinanceDelistService;
import com.bogoai.booknow.util.BinanceDustService;
import com.bogoai.api.client.BinanceApiRestClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/wallet")
@CrossOrigin(origins = "*")
public class WalletController {

    private static final String REDIS_KEY_PREFIX = "BINANCE:BALANCE:";

    @Autowired
    private RedisTemplate<String, WalletBalance> redisTemplateWalletBalance;

    @Autowired
    private BinanceDelistService delistService;

    @Autowired
    private RedisTemplate<String, DustAsset> redisTemplateDustAsset;

    /**
     * Fetch all balances from Redis.
     */
    @GetMapping("/balances")
    public List<WalletBalance> getBalances() {
        Set<String> keys = redisTemplateWalletBalance.keys(REDIS_KEY_PREFIX + "*");
        List<WalletBalance> balances = new ArrayList<>();
        if (keys != null) {
            for (String key : keys) {
                WalletBalance b = redisTemplateWalletBalance.opsForValue().get(key);
                if (b != null) {
                    balances.add(b);
                }
            }
        }
        return balances;
    }

    @Autowired
    private BinanceDustService dustService;

    @Autowired
    private BinanceApiRestClient prodBinanceApiARestClient;

    /**
     * Fetch all dust assets from Redis.
     */
    @GetMapping("/dust")
    public List<DustAsset> getDust() {
        Set<String> keys = redisTemplateDustAsset.keys("BINANCE:DUST:*");
        List<DustAsset> dustList = new ArrayList<>();
        if (keys != null) {
            for (String key : keys) {
                DustAsset d = redisTemplateDustAsset.opsForValue().get(key);
                if (d != null) dustList.add(d);
            }
        }
        return dustList;
    }

    /**
     * Execute a Universal Transfer to move dust to the main wallet.
     */
    @PostMapping("/dust-transfer")
    public String dustTransfer(@RequestParam String asset) {
        String key = "BINANCE:DUST:" + asset;
        DustAsset d = redisTemplateDustAsset.opsForValue().get(key);
        if (d == null) return "Error: Asset not found in dust cache";

        try {
            // Transfer from FUNDING to SPOT (or vice versa depending on where the dust is)
            // Using MAIN_FUNDING (Spot to Funding) as an example, or FUNDING_MAIN (Funding to Spot)
            // The user requested: From Funding, To Fiat & Spot
            com.bogoai.api.client.domain.account.UniversalTransferResult result = 
                prodBinanceApiARestClient.universalTransfer(asset, com.bogoai.api.client.domain.UniversalTransferType.FUNDING_MAIN, d.getFree());
            
            if (result != null && result.getTranId() != null) {
                dustService.removeDust(asset);
                return "Success: Transferred " + d.getFree() + " " + asset + " to Spot Wallet. ID: " + result.getTranId();
            }
            return "Error: Transfer failed on Binance side";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
