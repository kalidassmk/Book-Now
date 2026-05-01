package com.bogoai.booknow.util;

import com.bogoai.booknow.model.FastMove;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import static com.bogoai.booknow.util.Constant.*;

/**
 * Pure static utility methods — no Spring dependencies, fully testable.
 */
public final class BookNowUtility {

    private static final Logger log = LoggerFactory.getLogger(BookNowUtility.class);

    private BookNowUtility() {}

    // ── Percentage calculations ───────────────────────────────────────────────

    /**
     * Returns how much currentPercentage has changed relative to basePercentage.
     * Handles negative base correctly (e.g. base=-2%, current=+1% → gain=3%).
     */
    public static double getPercentage(double basePercentage, double currentPercentage) {
        if (basePercentage < 0 && currentPercentage < 0 && basePercentage < currentPercentage) {
            // Both negative, current is less negative → improvement
            return new BigDecimal(basePercentage).subtract(new BigDecimal(currentPercentage)).abs().doubleValue();
        }
        return new BigDecimal(currentPercentage).subtract(new BigDecimal(basePercentage)).doubleValue();
    }

    /** Raw price delta: currentPrice - basePrice */
    public static BigDecimal getPrice(BigDecimal basePrice, BigDecimal currentPrice) {
        if (basePrice.compareTo(currentPrice) < 0 && currentPrice.compareTo(BigDecimal.ZERO) < 0) {
            return basePrice.subtract(currentPrice).abs();
        }
        return currentPrice.subtract(basePrice);
    }

    // ── Bucket & momentum scoring ─────────────────────────────────────────────

    /**
     * Maps an increased-percentage value to a Redis bucket key and accumulates
     * the momentum score into the FastMove counter.
     *
     * Bucket thresholds:
     *   0.30–0.90 % → >0<1
     *   1–2 %       → >1<2
     *   2–3 %       → >2<3
     *   3–5 %       → >3<5
     *   5–7 %       → >5<7
     *   7–10 %      → >7<10
     *   ≥10 %       → >10
     *
     * @return bucket key, or null if below the 0.30 % threshold
     */
    public static String getBucket(double increasedPercentage, FastMove fastMove, double count) {
        String key = null;

        if (increasedPercentage >= 0.30 && increasedPercentage < 1.0) {
            key = BUCKET_G0L1;
            fastMove.setCountG0L1(fastMove.getCountG0L1() + count);
        } else if (increasedPercentage >= 1.0 && increasedPercentage < 2.0) {
            key = BUCKET_G1L2;
            fastMove.setCountG1L2(fastMove.getCountG1L2() + count);
        } else if (increasedPercentage >= 2.0 && increasedPercentage < 3.0) {
            key = BUCKET_G2L3;
            fastMove.setCountG2L3(fastMove.getCountG2L3() + count);
        } else if (increasedPercentage >= 3.0 && increasedPercentage < 5.0) {
            key = BUCKET_G3L5;
            fastMove.setCountG3L5(fastMove.getCountG3L5() + count);
        } else if (increasedPercentage >= 5.0 && increasedPercentage < 7.0) {
            key = BUCKET_G5L7;
            fastMove.setCountG5L7(fastMove.getCountG5L7() + count);
        } else if (increasedPercentage >= 7.0 && increasedPercentage < 10.0) {
            key = BUCKET_G7L10;
            fastMove.setCountG7L10(fastMove.getCountG7L10() + count);
        } else if (increasedPercentage >= 10.0) {
            key = BUCKET_G10;
            fastMove.setCountG10(fastMove.getCountG10() + count);
        }

        if (key != null) {
            fastMove.setOverAllCount(fastMove.getOverAllCount() + count);
        }
        return key;
    }

    /**
     * Weighted positive momentum score for a tick-to-tick percentage gain.
     * Larger moves score higher; used to rank coins by speed.
     */
    public static double getMomentumScorePositive(double delta) {
        if (delta >= 3.0)               return 4.0;
        else if (delta >= 2.0)          return 3.0;
        else if (delta >= 1.5)          return 2.0;
        else if (delta >= 1.0)          return 1.5;
        else if (delta >= 0.75)         return 1.0;
        else if (delta >= 0.50)         return 0.75;
        else if (delta >= 0.30)         return 0.50;
        else if (delta >= 0.10)         return 0.25;
        else if (delta >= 0.05)         return 0.075;
        else if (delta >= 0.01)         return 0.05;
        return 0;
    }

    /**
     * Weighted negative momentum score (penalty) for a tick-to-tick drop.
     */
    public static double getMomentumScoreNegative(double delta) {
        // delta is negative
        if (delta <= -3.0)              return -4.0;
        else if (delta <= -2.0)         return -3.0;
        else if (delta <= -1.5)         return -2.0;
        else if (delta <= -1.0)         return -1.5;
        else if (delta <= -0.75)        return -1.0;
        else if (delta <= -0.50)        return -0.75;
        else if (delta <= -0.30)        return -0.50;
        else if (delta <= -0.09)        return -0.25;
        else if (delta <= -0.05)        return -0.075;
        else if (delta <= -0.01)        return -0.05;
        return 0;
    }

    // ── Notification ──────────────────────────────────────────────────────────

    /** Send a macOS system notification (no-op on non-Mac systems). */
    public static void runNotification(String message) {
        try {
            String script = String.format(
                "display notification \"%s\" with title \"BookNow\" sound name \"Crystal\"",
                message.replace("\"", "'")
            );
            Runtime.getRuntime().exec(new String[]{"osascript", "-e", script});
            log.info("NOTIFICATION: {}", message);
        } catch (Exception e) {
            log.warn("Could not send notification: {}", e.getMessage());
        }
    }

    // ── Time helpers ──────────────────────────────────────────────────────────

    /** Human-readable HH:MM:SS for logging. */
    public static String getHMS() {
        Calendar now = Calendar.getInstance();
        return String.format("%02d:%02d:%02d",
            now.get(Calendar.HOUR_OF_DAY),
            now.get(Calendar.MINUTE),
            now.get(Calendar.SECOND));
    }

    /** Milliseconds between two timestamps, converted to seconds. */
    public static double toSeconds(long fromMillis, long toMillis) {
        return (toMillis - fromMillis) / 1000.0;
    }

    // ── Coin lists ────────────────────────────────────────────────────────────

    /**
     * Coins that have been delisted or flagged as unsuitable (price too high, etc.).
     * Kept as a static map for O(1) lookup during the WebSocket event loop.
     */
    @NotNull
    public static Map<String, String> deListCoins() {
        Map<String, String> delist = new HashMap<>();
        delist.put("MITHUSDT",  "20221223");
        delist.put("TRIBEBUSD", "20221223");
        delist.put("REPUSDT",   "20221223");
        delist.put("BTCSTBUSD", "20221223");
        delist.put("BUSDUSDT",  "Amount too high");
        delist.put("BTCUSDT",   "Amount too high");
        delist.put("ETHUSDT",   "Amount too high");
        delist.put("BTTCUSDT",  "Amount too high");
        delist.put("CHESSUSDT", "2022-12-29 03:00 UTC");
        delist.put("CVPUSDT",   "2022-12-19 03:00 UTC");
        delist.put("MDTUSDT",   "2022-12-19 03:00 UTC");
        delist.put("RAREUSDT",  "2022-12-19 03:00 UTC");
        delist.put("DREPUSDT",  "2022-12-19 03:00 UTC");
        delist.put("LOKAUSDT",  "2022-12-19 03:00 UTC");
        delist.put("MOBUSDT",   "2022-12-19 03:00 UTC");
        delist.put("TORNUSDT",  "2022-12-19 03:00 UTC");
        delist.put("FETUSDT",   "2022-12-19 03:00 UTC");
        delist.put("FORTHUSDT", "2022-12-19 03:00 UTC");
        delist.put("KEYUSDT",   "2022-12-19 03:00 UTC");
        delist.put("MBOXUSDT",  "2022-12-19 03:00 UTC");
        delist.put("WINUSDT",   "2022-12-19 03:00 UTC");
        delist.put("AIONUSDT",  "2022-12-19 03:00 UTC");
        delist.put("BTSUSDT",   "2022-12-19 03:00 UTC");
        delist.put("GALAUSD",   "2022-12-19 03:00 UTC");
        delist.put("ZILUSD",    "2022-12-19 03:00 UTC");
        delist.put("VETUSD",    "2022-12-19 03:00 UTC");
        delist.put("TKOUSDT",   "2022-12-19 03:00 UTC");
        delist.put("ATAUSDT",   "2022-12-19 03:00 UTC");
        delist.put("DEXEUSDT",  "2022-12-19 03:00 UTC");
        delist.put("HIGHUSDT",  "2022-12-19 03:00 UTC");
        delist.put("STPTUSDT",  "2022-12-19 03:00 UTC");
        delist.put("WANUSDT",   "2022-12-19 03:00 UTC");
        delist.put("HOOKUSDT",  "2022-12-19 03:00 UTC");
        delist.put("RAYUSDT",   "2022-12-19 03:00 UTC");
        return delist;
    }
}