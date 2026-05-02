package com.bogoai.booknow.util;

import com.bogoai.booknow.model.TradingConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

import static com.bogoai.booknow.util.Constant.TRADING_CONFIG;

@Slf4j
@Service
public class TradingConfigService {

    @Autowired
    private RedisTemplate<String, TradingConfig> redisTemplateTradingConfig;

    private TradingConfig currentConfig;

    @PostConstruct
    public void init() {
        refreshConfig();
    }

    /**
     * Fetch the latest config from Redis. 
     * If not present, initialize with defaults and save.
     */
    public TradingConfig refreshConfig() {
        try {
            TradingConfig config = redisTemplateTradingConfig.opsForValue().get(TRADING_CONFIG);
            if (config == null) {
                log.info("[Config] No config found in Redis. Initializing with defaults...");
                config = new TradingConfig();
                saveConfig(config);
            }
            this.currentConfig = config;
            return config;
        } catch (Exception e) {
            log.error("[Config] Error fetching config from Redis: {}", e.getMessage());
            if (this.currentConfig == null) this.currentConfig = new TradingConfig();
            return this.currentConfig;
        }
    }

    public TradingConfig getConfig() {
        // We refresh every time we are asked to ensure the bot reacts immediately to dashboard changes
        return refreshConfig();
    }

    public void saveConfig(TradingConfig config) {
        try {
            redisTemplateTradingConfig.opsForValue().set(TRADING_CONFIG, config);
            this.currentConfig = config;
            log.info("[Config] Saved new configuration to Redis: {}", config);
        } catch (Exception e) {
            log.error("[Config] Failed to save config to Redis: {}", e.getMessage());
        }
    }

    // Convenience getters for the bot logic
    public boolean isAutoBuyEnabled() {
        return getConfig().isAutoBuyEnabled();
    }

    public double getBuyAmountUsdt() {
        return getConfig().getBuyAmountUsdt();
    }

    public double getProfitPct() {
        return getConfig().getProfitPct();
    }

    public double getProfitAmountUsdt() {
        return getConfig().getProfitAmountUsdt();
    }

    public double getLimitBuyOffsetPct() {
        return getConfig().getLimitBuyOffsetPct();
    }

    public double getTslPct() {
        return getConfig().getTslPct();
    }
}
