package com.bogoai.booknow.processor;

import com.bogoai.booknow.model.Percentage;
import com.bogoai.booknow.repository.BookNowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import static com.bogoai.booknow.util.Constant.*;

/**
 * FastAnalyse — speed-based signal classifier.
 *
 * For coins already in the 2–7% gain zone, it checks whether they skipped
 * lower bands entirely (signalling unusually fast momentum):
 *
 *   SUPER_FAST>2<3      → coin is at 2–3% but was never at 0–1% or 1–2%
 *   ULTRA_FAST>3<5      → coin is at 3–5% but was never at 1–2% or 2–3%
 *   ULTRA_SUPER_FAST>5<7 → coin is at 5–7% but was never at 2–3% or 3–5%
 *
 * These signals complement ULF0To3 and feed into the rule engines.
 */
public class FastAnalyse implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(FastAnalyse.class);

    private final BookNowRepository repository;

    private final Set<String> recordedSf  = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> recordedUf  = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> recordedUsf = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public FastAnalyse(BookNowRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run() {
        log.info("=== FastAnalyse starting ===");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                classify();
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("FastAnalyse error: {}", e.getMessage());
                try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        log.info("=== FastAnalyse stopped ===");
    }

    private void classify() {
        // ── SUPER_FAST: at 2–3% but skipped 0–1% and 1–2% ───────────────────
        Map<String, Percentage> sf = repository.getAllEntryPercentage(BUCKET_G2L3);
        for (Map.Entry<String, Percentage> entry : sf.entrySet()) {
            String symbol = entry.getKey();
            if (isSuperFast(symbol) && recordedSf.add(symbol)) {
                repository.savePercentage(SUPER_FAST_2_3, symbol, entry.getValue());
                log.info("SUPER_FAST>2<3: {} (skipped 0-1 and 1-2 bands)", symbol);
            }
        }

        // ── ULTRA_FAST: at 3–5% but skipped 1–2% and 2–3% ───────────────────
        Map<String, Percentage> uf = repository.getAllEntryPercentage(BUCKET_G3L5);
        for (Map.Entry<String, Percentage> entry : uf.entrySet()) {
            String symbol = entry.getKey();
            if (isUltraFast(symbol) && recordedUf.add(symbol)) {
                repository.savePercentage(ULTRA_FAST_3_5, symbol, entry.getValue());
                log.info("ULTRA_FAST>3<5: {} (skipped 1-2 and 2-3 bands)", symbol);
            }
        }

        // ── ULTRA_SUPER_FAST: at 5–7% but skipped 2–3% and 3–5% ─────────────
        Map<String, Percentage> usf = repository.getAllEntryPercentage(BUCKET_G5L7);
        for (Map.Entry<String, Percentage> entry : usf.entrySet()) {
            String symbol = entry.getKey();
            if (isUltraSuperFast(symbol) && recordedUsf.add(symbol)) {
                repository.savePercentage(ULTRA_SUPER_FAST_5_7, symbol, entry.getValue());
                log.info("ULTRA_SUPER_FAST>5<7: {} (skipped 2-3 and 3-5 bands)", symbol);
            }
        }
    }

    // ── Speed checks (delegate to repository) ────────────────────────────────

    /** True when the coin has NO entry in the 0–1% or 1–2% buckets. */
    private boolean isSuperFast(String symbol) {
        return repository.getSuperFast(symbol);
    }

    /** True when the coin has NO entry in the 1–2% or 2–3% buckets. */
    private boolean isUltraFast(String symbol) {
        return repository.getUltraFast(symbol);
    }

    /** True when the coin has NO entry in the 2–3% or 3–5% buckets. */
    private boolean isUltraSuperFast(String symbol) {
        return repository.getUltraSuperFast(symbol);
    }
}
