package com.bogoai.booknow.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Anti-Parabolic Safety Service.
 * Acts as a secondary gate to prevent buying into overextended trends.
 */
@Slf4j
@Service
public class TradingSafetyService {

    @Value("${trading.safety.price-spike-threshold:1.5}")
    private double priceSpikeThreshold; // e.g. 50% above SMA

    @Value("${trading.safety.volume-surge-threshold:5.0}")
    private double volumeSurgeThreshold; // e.g. 5x avg volume

    @Value("${trading.safety.rsi-overbought-limit:80.0}")
    private double rsiOverboughtLimit;

    /**
     * Evaluates if a coin is safe to buy based on parabolic risk metrics.
     * 
     * @param symbol       Symbol name for logging
     * @param currentPrice Current market price
     * @param sma          Simple Moving Average (e.g. 24h or 7d)
     * @param currentVol   Current 24h volume
     * @param avgVol       Baseline average volume
     * @param rsi          Relative Strength Index (14-period)
     * @return true if SAFE, false if RISK detected
     */
    public boolean isSafeToBuy(String symbol, double currentPrice, double sma, 
                               double currentVol, double avgVol, double rsi) {
        
        boolean isPriceParabolic = currentPrice > (sma * priceSpikeThreshold);
        boolean isVolumeAbnormal = currentVol > (avgVol * volumeSurgeThreshold);
        boolean isOverbought     = rsi > rsiOverboughtLimit;

        if (isPriceParabolic || isVolumeAbnormal || isOverbought) {
            log.warn("[SAFETY] {} blocked due to high parabolic risk!", symbol);
            if (isPriceParabolic) log.warn("  - Price Spike: {} is > {}% above SMA ({})", currentPrice, (priceSpikeThreshold-1)*100, sma);
            if (isVolumeAbnormal) log.warn("  - Volume Surge: {} is > {}x above Average ({})", currentVol, volumeSurgeThreshold, avgVol);
            if (isOverbought)     log.warn("  - Overbought: RSI is {}", rsi);
            return false;
        }

        return true;
    }
}
