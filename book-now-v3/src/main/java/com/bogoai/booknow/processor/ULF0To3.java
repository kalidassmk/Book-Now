package com.bogoai.booknow.processor;

import com.bogoai.booknow.model.Percentage;
import com.bogoai.booknow.model.PreviousPercentage;
import com.bogoai.booknow.repository.BookNowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import static com.bogoai.booknow.util.Constant.*;

/**
 * ULF0To3 — Ultra-Low-Frequency 0→3% detector.
 *
 * Continuously checks whether a coin has crossed the 0→1%, 1→2%, and 2→3%
 * bands within a 2-minute window of each other.  When all three fire rapidly:
 *
 *   LT2MIN_0>3  → coin climbed 0% → 3% in under 2 minutes
 *
 * Also detects partial ultra-fast moves:
 *   ULTRA_FAST0>2  → coin skipped directly to 2% (no 0→1 entry)
 *   ULTRA_FAST2>3  → coin skipped 1→2, went directly 2→3
 *   ULTRA_FAST0>3  → coin is at 2→3 without ever being seen at 0→1
 *
 * BUG FIX: original code mutated G1L2Set/G2L3Set after they were used in
 * addAll(), causing incorrect set operations.  Now each filter uses an
 * independent copy of the set.
 */
public class ULF0To3 implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(ULF0To3.class);

    /** 2-minute gap limit between consecutive band crossings (ms). */
    private static final long MAX_GAP_MS = 2 * 60 * 1000L;

    private final BookNowRepository repository;

    // One-time trackers so we don't save duplicates to Redis
    private final Set<String> recordedLT2       = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> recordedUF0to2    = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> recordedUF2to3    = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> recordedUF0to3    = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public ULF0To3(BookNowRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run() {
        log.info("=== ULF0To3 starting ===");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                detect();
                Thread.sleep(500);   // Check every 500ms — prevents Redis pool exhaustion
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();   // Exit cleanly on shutdown
                break;
            } catch (Exception e) {
                log.error("ULF0To3 error: {}", e.getMessage());
                try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        log.info("=== ULF0To3 stopped ===");
    }


    private void detect() {
        Map<String, Percentage> g0l1 = repository.getAllEntryPercentage(BUCKET_G0L1);
        Map<String, Percentage> g1l2 = repository.getAllEntryPercentage(BUCKET_G1L2);
        Map<String, Percentage> g2l3 = repository.getAllEntryPercentage(BUCKET_G2L3);

        // ── Pattern 1: Full 0→1→2→3 within 2 min each step ──────────────────
        for (String symbol : g0l1.keySet()) {
            Percentage p1 = g0l1.get(symbol);
            Percentage p2 = g1l2.get(symbol);
            Percentage p3 = g2l3.get(symbol);

            if (p1 == null || p2 == null || p3 == null) continue;

            long t1 = lastTimestamp(p1);
            long t2 = lastTimestamp(p2);
            long t3 = lastTimestamp(p3);

            boolean step1Fast = (t2 - t1) <= MAX_GAP_MS;
            boolean step2Fast = (t3 - t2) <= MAX_GAP_MS;

            if (step1Fast && step2Fast && recordedLT2.add(symbol)) {
                repository.savePercentage(LT2MIN_0_TO_3, symbol, p3);
                log.info("LT2MIN_0>3 detected: {} ({} → {} → {} ms)", symbol, t1, t2, t3);
            }
        }

        // ── Pattern 2: ULTRA_FAST 0→2 (skipped >0<1 band) ───────────────────
        for (String symbol : g1l2.keySet()) {
            if (!g0l1.containsKey(symbol) && recordedUF0to2.add(symbol)) {
                repository.savePercentage(ULTRA_FAST_0_TO_2, symbol, g1l2.get(symbol));
                log.info("ULTRA_FAST0>2 detected: {}", symbol);
            }
        }

        // ── Pattern 3: ULTRA_FAST 2→3 (skipped >1<2 band) ───────────────────
        for (String symbol : g2l3.keySet()) {
            if (!g1l2.containsKey(symbol) && recordedUF2to3.add(symbol)) {
                repository.savePercentage(ULTRA_FAST_2_TO_3, symbol, g2l3.get(symbol));
                log.info("ULTRA_FAST2>3 detected: {}", symbol);
            }
        }

        // ── Pattern 4: ULTRA_FAST 0→3 (at 2→3 but never seen at 0→1) ────────
        for (String symbol : g2l3.keySet()) {
            if (!g0l1.containsKey(symbol) && recordedUF0to3.add(symbol)) {
                repository.savePercentage(ULTRA_FAST_0_TO_3, symbol, g2l3.get(symbol));
                log.info("ULTRA_FAST0>3 detected: {}", symbol);
            }
        }
    }

    /** Gets the timestamp of the most recent PreviousPercentage entry. */
    private long lastTimestamp(Percentage p) {
        if (p.getPreviousCountList() == null || p.getPreviousCountList().isEmpty()) {
            return p.getCurrentTimeStamp() != null ? p.getCurrentTimeStamp().getTime() : 0L;
        }
        PreviousPercentage last = p.getPreviousCountList().get(p.getPreviousCountList().size() - 1);
        return last.getTimestamp() != null ? last.getTimestamp().getTime() : 0L;
    }
}
