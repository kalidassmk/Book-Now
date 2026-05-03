package com.bogoai.booknow.util;

import com.bogoai.api.client.BinanceApiRestClient;
import com.bogoai.api.client.domain.market.Candlestick;
import com.bogoai.api.client.domain.market.CandlestickInterval;
import com.bogoai.booknow.model.CoinAnalysisResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fetches 60 daily Binance candlesticks and produces a {@link CoinAnalysisResult}
 * that answers two questions:
 *
 *   1. Where is the coin relative to its 2-month price range?
 *   2. Is volume expanding (bullish signal) or contracting?
 *
 * Scoring rubric (0–7):
 *   +1  Current price is below the 2-month average (buy the dip)
 *   +1  Current price is in the lower 40% of the 2-month range
 *   +1  7-day trend is positive (momentum)
 *   +1  30-day trend is positive (macro uptrend)
 *   +1  24-h volume > 1.5× 30-day average (unusual activity)
 *   +1  24-h volume > 7-day average (volume building today)
 *   +1  Volume has increased at least 3 of the last 7 days
 *
 * Decision:
 *   5–7 → STRONG_BUY   (shouldBuy = true)
 *   4   → BUY          (shouldBuy = true)
 *   3   → NEUTRAL      (shouldBuy = false)
 *   2   → WAIT         (shouldBuy = false)
 *   0–1 → DONT_BUY     (shouldBuy = false)
 */
@Component
public class CoinAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(CoinAnalyzer.class);
    
    // Cache to prevent redundant heavy API calls for the same symbol
    private final java.util.concurrent.ConcurrentMap<String, CachedResult> analysisCache = 
        new java.util.concurrent.ConcurrentHashMap<>();
    
    private static class CachedResult {
        CoinAnalysisResult result;
        long timestamp;
        CachedResult(CoinAnalysisResult r) { this.result = r; this.timestamp = System.currentTimeMillis(); }
    }

    /** Number of daily candles to fetch (≈2 calendar months) */
    /** Number of daily candles to fetch (to support 200-EMA) */
    private static final int CANDLE_LIMIT = 250;

    @Autowired
    private BinanceApiRestClient prodBinanceApiARestClient;

    @Autowired
    private TradingSafetyService safetyService;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Analyse a symbol and return a full {@link CoinAnalysisResult}.
     * Safe to call even if Binance returns an error — will return a neutral result.
     *
     * @param symbol      e.g. "SOLUSDT"
     * @param currentPrice live price (from Redis)
     */
    public CoinAnalysisResult analyze(String symbol, double currentPrice) {
        // 1. Check Cache (1-hour TTL for daily candle analysis)
        CachedResult cached = analysisCache.get(symbol);
        if (cached != null && (System.currentTimeMillis() - cached.timestamp) < 3600000) {
            CoinAnalysisResult r = cached.result;
            // Update live price even on cached results
            r.setCurrentPrice(currentPrice);
            return r;
        }

        CoinAnalysisResult result = new CoinAnalysisResult();
        result.setSymbol(symbol);
        result.setCurrentPrice(currentPrice);

        try {
            // Pacing: Add a small delay to prevent rapid-fire requests across multiple threads
            Thread.sleep(500);
            
            log.info("[Binance API] Requesting the last {} daily candlesticks from Binance for {} to perform trend and volume analysis.", CANDLE_LIMIT, symbol);
            List<Candlestick> candles = prodBinanceApiARestClient
                    .getCandlestickBars(symbol, CandlestickInterval.DAILY, CANDLE_LIMIT, null, null);
            log.info("[Binance API] Received {} daily candlesticks for {}. Proceeding with analysis...", candles != null ? candles.size() : 0, symbol);

            if (candles == null || candles.size() < 14) {
                log.warn("[Analyzer] Not enough candles for {} (got {})", symbol,
                        candles == null ? 0 : candles.size());
                return neutralResult(result, "Insufficient history data");
            }

            // ── Price stats ───────────────────────────────────────────────────
            double high2m = candles.stream()
                    .mapToDouble(c -> Double.parseDouble(c.getHigh()))
                    .max().orElse(currentPrice);

            double low2m = candles.stream()
                    .mapToDouble(c -> Double.parseDouble(c.getLow()))
                    .min().orElse(currentPrice);

            double avg2m = candles.stream()
                    .mapToDouble(c -> Double.parseDouble(c.getClose()))
                    .average().orElse(currentPrice);

            double range       = high2m - low2m;
            double pricePos    = range > 0 ? ((currentPrice - low2m) / range) * 100.0 : 50.0;

            result.setHigh2m(high2m);
            result.setLow2m(low2m);
            result.setAvg2m(avg2m);
            result.setPricePosition(round2(pricePos));
            result.setDaysAnalyzed(candles.size());

            // ── Trend calculation ─────────────────────────────────────────────
            int n = candles.size();

            // 7-day trend: avg of last 7 closes vs avg of previous 7 closes
            double avgLast7  = avgClose(candles, n - 7,  n);
            double avgPrev7  = avgClose(candles, n - 14, n - 7);
            double trend7d   = pctChange(avgPrev7, avgLast7);

            // 30-day trend: avg of last 30 closes vs avg of previous 30 closes
            double avgLast30 = avgClose(candles, n - 30, n);
            double avgPrev30 = avgClose(candles, Math.max(0, n - 60), n - 30);
            double trend30d  = pctChange(avgPrev30, avgLast30);

            result.setTrend7d(round2(trend7d));
            result.setTrend30d(round2(trend30d));

            // ── Volume stats ──────────────────────────────────────────────────
            // Quote-asset volume = coin volume × price = USDT value traded
            double vol24h = Double.parseDouble(candles.get(n - 1).getQuoteAssetVolume());

            double vol30dAvg = candles.subList(n - 30, n).stream()
                    .mapToDouble(c -> Double.parseDouble(c.getQuoteAssetVolume()))
                    .average().orElse(vol24h);

            double vol7dAvg  = candles.subList(n - 7, n).stream()
                    .mapToDouble(c -> Double.parseDouble(c.getQuoteAssetVolume()))
                    .average().orElse(vol24h);

            double volumeRatio = vol30dAvg > 0 ? vol24h / vol30dAvg : 1.0;

            // Volume increasing: how many of last 7 days had volume > prior day
            long volIncDays = 0;
            for (int i = n - 6; i < n; i++) {
                double today     = Double.parseDouble(candles.get(i).getQuoteAssetVolume());
                double yesterday = Double.parseDouble(candles.get(i - 1).getQuoteAssetVolume());
                if (today > yesterday) volIncDays++;
            }

            result.setVol24hUsdt(round2(vol24h));
            result.setVol30dAvgUsdt(round2(vol30dAvg));
            result.setVol7dAvgUsdt(round2(vol7dAvg));
            result.setVolumeRatio(round2(volumeRatio));

            // ── RSI calculation ───────────────────────────────────────────────
            double rsi = Indicators.calculateRSI(candles, 14);
            result.setRsi(round2(rsi));

            // ── EMA calculations (Trend Following) ───────────────────────────
            double ema50  = Indicators.calculateEMA(candles, 50);
            double ema200 = Indicators.calculateEMA(candles, 200);
            result.setEma50(round2(ema50));
            result.setEma200(round2(ema200));

            // ── Safety Gate (Anti-Parabolic) ──────────────────────────────────
            boolean isSafe = safetyService.isSafeToBuy(symbol, currentPrice, avg2m, vol24h, vol30dAvg, rsi);

            // ── Scoring ───────────────────────────────────────────────────────
            int score = 0;
            StringBuilder reason = new StringBuilder();

            if (currentPrice < avg2m) {
                score++;
                reason.append("✅ Price below 2-month avg (buy the dip). ");
            } else {
                reason.append("⚠️ Price above 2-month avg. ");
            }

            if (pricePos < 40.0) {
                score++;
                reason.append("✅ Price in lower 40% of 2m range. ");
            } else if (pricePos > 80.0) {
                reason.append("🔴 Price near 2-month high (risky). ");
            }

            if (trend7d > 0) {
                score++;
                reason.append(String.format("✅ 7d trend +%.2f%%. ", trend7d));
            } else {
                reason.append(String.format("⚠️ 7d trend %.2f%%. ", trend7d));
            }

            if (trend30d > 0) {
                score++;
                reason.append(String.format("✅ 30d trend +%.2f%%. ", trend30d));
            } else {
                reason.append(String.format("⚠️ 30d trend %.2f%%. ", trend30d));
            }

            if (volumeRatio >= 1.5) {
                score++;
                reason.append(String.format("✅ Volume %.1f× above 30d avg (high activity). ", volumeRatio));
            } else if (volumeRatio < 0.5) {
                reason.append("⚠️ Very low volume (avoid). ");
            } else {
                reason.append(String.format("ℹ️ Volume ratio %.2f (normal). ", volumeRatio));
            }

            // ── RISK PENALTIES (Algorithm Improvement) ────────────────────────
            if (trend7d > 40.0) {
                score -= 2;
                reason.append(String.format("🔴 RISK: Overextended 7d trend (+%.1f%%) — likely top-heavy. ", trend7d));
            }

            if (volumeRatio > 4.0) {
                score -= 2;
                reason.append(String.format("🔴 RISK: Parabolic volume spike (%.1f×) — potential pump & dump. ", volumeRatio));
            }

            if (vol24h < 1000000.0) {
                score -= 1;
                reason.append("🔴 RISK: Low liquidity (< 1M USDT 24h volume). ");
            }

            if (vol24h > vol7dAvg) {
                score++;
                reason.append("✅ Today's volume above 7d average. ");
            }

            if (volIncDays >= 3) {
                score++;
                reason.append(String.format("✅ Volume increased %d/6 of last days (building). ", volIncDays));
            }

            // ── TREND FOLLOWING: Golden Cross (+1) ──────────────────────────
            if (ema50 > ema200 && rsi > 40 && rsi < 70) {
                score++;
                reason.append("✅ TREND: Golden Cross (EMA50 > EMA200) with solid momentum. ");
            }

            // ── Recommendation ────────────────────────────────────────────────
            result.setBuyScore(score);
            result.setReason(reason.toString().trim());

            if (score >= 5) {
                result.setRecommendation("STRONG_BUY");
                result.setShouldBuy(true);
            } else if (score == 4) {
                result.setRecommendation("BUY");
                result.setShouldBuy(true);
            } else if (score == 3) {
                result.setRecommendation("NEUTRAL");
                result.setShouldBuy(false);
            } else if (score == 2) {
                result.setRecommendation("WAIT");
                result.setShouldBuy(false);
            } else {
                result.setRecommendation("DONT_BUY");
                result.setShouldBuy(false);
            }

            // ── OVERRIDE: Safety Gate ────────────────────────────────────────
            if (!isSafe) {
                result.setShouldBuy(false);
                result.setRecommendation("RISKY_PARABOLIC");
                result.setReason(result.getReason() + " | ❌ BLOCKED: Parabolic Risk detected.");
            }

            log.info("[Analyzer] {} score={}/7 → {} | {}", symbol, score,
                    result.getRecommendation(), result.getReason());

            // Cache the result before returning
            analysisCache.put(symbol, new CachedResult(result));

        } catch (Exception e) {
            log.error("[Analyzer] Error analysing {}: {}", symbol, e.getMessage());
            return neutralResult(result, "Analysis error: " + e.getMessage());
        }

        return result;
    }

    /**
     * Quick boolean gate used by TradeExecutor before placing any order.
     * Returns true only if score ≥ 4 (BUY or STRONG_BUY).
     */
    public boolean shouldBuy(String symbol, double currentPrice) {
        return analyze(symbol, currentPrice).isShouldBuy();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private double avgClose(List<Candlestick> candles, int from, int to) {
        from = Math.max(0, from);
        to   = Math.min(candles.size(), to);
        if (from >= to) return 0;
        return candles.subList(from, to).stream()
                .mapToDouble(c -> Double.parseDouble(c.getClose()))
                .average().orElse(0);
    }

    private double pctChange(double from, double to) {
        return from == 0 ? 0 : ((to - from) / from) * 100.0;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private CoinAnalysisResult neutralResult(CoinAnalysisResult r, String reason) {
        r.setRecommendation("NEUTRAL");
        r.setReason(reason);
        r.setShouldBuy(false);
        r.setBuyScore(0);
        return r;
    }
}
