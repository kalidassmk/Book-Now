package com.bogoai.booknow.util;

import com.bogoai.api.client.domain.market.Candlestick;
import java.util.List;

/**
 * Technical Analysis Indicators utility.
 */
public class Indicators {

    /**
     * Calculate Relative Strength Index (RSI) for a list of candlesticks.
     * Uses the standard 14-period Wilder's Smoothing.
     * 
     * @param candles List of daily candlesticks
     * @param period  RSI period (standard is 14)
     * @return RSI value or 50.0 if calculation is not possible
     */
    public static double calculateRSI(List<Candlestick> candles, int period) {
        if (candles == null || candles.size() <= period) return 50.0;

        double lastClose = Double.parseDouble(candles.get(0).getClose());
        double avgGain = 0;
        double avgLoss = 0;

        // 1. Initial SMA for Gains/Losses
        for (int i = 1; i <= period; i++) {
            double close = Double.parseDouble(candles.get(i).getClose());
            double diff = close - lastClose;
            if (diff >= 0) avgGain += diff;
            else avgLoss += Math.abs(diff);
            lastClose = close;
        }

        avgGain /= period;
        avgLoss /= period;

        // 2. Wilder's Smoothing for the rest of the candles
        for (int i = period + 1; i < candles.size(); i++) {
            double close = Double.parseDouble(candles.get(i).getClose());
            double diff = close - lastClose;
            
            double gain = diff >= 0 ? diff : 0;
            double loss = diff < 0 ? Math.abs(diff) : 0;

            avgGain = (avgGain * (period - 1) + gain) / period;
            avgLoss = (avgLoss * (period - 1) + loss) / period;
            
            lastClose = close;
        }

        if (avgLoss == 0) return 100.0;
        
        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    /**
     * Calculate Exponential Moving Average (EMA).
     * 
     * @param candles List of daily candlesticks
     * @param period  EMA period (e.g. 50, 200)
     * @return EMA value or current price if not enough data
     */
    public static double calculateEMA(List<Candlestick> candles, int period) {
        if (candles == null || candles.size() < period) {
            return candles != null && !candles.isEmpty() 
                ? Double.parseDouble(candles.get(candles.size() - 1).getClose()) 
                : 0.0;
        }

        double multiplier = 2.0 / (period + 1.0);
        
        // 1. Start with SMA for the first period
        double ema = candles.subList(0, period).stream()
                .mapToDouble(c -> Double.parseDouble(c.getClose()))
                .average().orElse(0);

        // 2. Apply EMA formula for the rest
        for (int i = period; i < candles.size(); i++) {
            double close = Double.parseDouble(candles.get(i).getClose());
            ema = (close - ema) * multiplier + ema;
        }

        return ema;
    }
}
