package com.bogoai.booknow.processor;

import com.bogoai.booknow.model.ShortestTime;
import com.bogoai.booknow.model.TimeAnalyse;
import com.bogoai.booknow.model.WatchList;
import com.bogoai.booknow.repository.BookNowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import static com.bogoai.booknow.util.BookNowUtility.toSeconds;
import static com.bogoai.booknow.util.Constant.*;

/**
 * TimeAnalyser — measures how long (in seconds) a coin takes to move
 * between percentage bands.
 *
 * Each transition is stored once per symbol in Redis under keys:
 *   ST0/0T1  = seconds from crossing 0% band to 1% band
 *   ST0/0T2  = seconds from crossing 0% band to 2% band
 *   ST0/0T3  = seconds from crossing 0% band to 3% band
 *   ST0/0T5  = seconds from crossing 0% band to 5% band
 *   ST0/0T7  = seconds from crossing 0% band to 7% band
 *   ST1/1T2  = seconds from 1% to 2%
 *   ST1/1T3  = seconds from 1% to 3%
 *   ST1/1T5  = seconds from 1% to 5%
 *   ST1/1T7  = seconds from 1% to 7%
 *   ST2/2T3  = seconds from 2% to 3%
 *   ST2/2T5  = seconds from 2% to 5%
 *   ST2/2T7  = seconds from 2% to 7%
 *   ST3/3T5  = seconds from 3% to 5%
 *   ST3/3T7  = seconds from 3% to 7%
 *
 * The RuleOne/Two/Three engines poll these times to detect fast-moving coins.
 */
public class TimeAnalyser implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(TimeAnalyser.class);

    private final BookNowRepository repository;

    // Per-symbol guards so each transition is saved only once
    private final Set<String> saved = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public TimeAnalyser(BookNowRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run() {
        log.info("=== TimeAnalyser starting ===");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                analyse();
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("TimeAnalyser error: {}", e.getMessage());
                try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
        log.info("=== TimeAnalyser stopped ===");
    }

    private void analyse() {
        // Load all band watch-lists once per iteration
        Map<String, WatchList> g0l1  = repository.getEntryWatchList(WATCH_PREFIX + BUCKET_G0L1  + WATCH_SUFFIX);
        Map<String, WatchList> g1l2  = repository.getEntryWatchList(WATCH_PREFIX + BUCKET_G1L2  + WATCH_SUFFIX);
        Map<String, WatchList> g2l3  = repository.getEntryWatchList(WATCH_PREFIX + BUCKET_G2L3  + WATCH_SUFFIX);
        Map<String, WatchList> g3l5  = repository.getEntryWatchList(WATCH_PREFIX + BUCKET_G3L5  + WATCH_SUFFIX);
        Map<String, WatchList> g5l7  = repository.getEntryWatchList(WATCH_PREFIX + BUCKET_G5L7  + WATCH_SUFFIX);
        Map<String, WatchList> g7l10 = repository.getEntryWatchList(WATCH_PREFIX + BUCKET_G7L10 + WATCH_SUFFIX);

        // Measure transitions rooted at band 0 (>0<1)
        for (Map.Entry<String, WatchList> e : g0l1.entrySet()) {
            String    sym = e.getKey();
            Timestamp t0  = e.getValue().getTimestamp();

            saveTransition(sym, t0, g1l2,  ST0, "0T1");
            saveTransition(sym, t0, g2l3,  ST0, "0T2");
            saveTransition(sym, t0, g3l5,  ST0, "0T3");
            saveTransition(sym, t0, g5l7,  ST0, "0T5");
            saveTransition(sym, t0, g7l10, ST0, "0T7");

            // Transitions rooted at band 1 (>1<2)
            Timestamp t1 = timestampOf(g1l2, sym);
            if (t1 != null) {
                saveTransition(sym, t1, g2l3,  ST1, "1T2");
                saveTransition(sym, t1, g3l5,  ST1, "1T3");
                saveTransition(sym, t1, g5l7,  ST1, "1T5");
                saveTransition(sym, t1, g7l10, ST1, "1T7");
            }

            // Transitions rooted at band 2 (>2<3)
            Timestamp t2 = timestampOf(g2l3, sym);
            if (t2 != null) {
                saveTransition(sym, t2, g3l5,  ST2, "2T3");
                saveTransition(sym, t2, g5l7,  ST2, "2T5");
                saveTransition(sym, t2, g7l10, ST2, "2T7");
            }

            // Transitions rooted at band 3 (>3<5)
            Timestamp t3 = timestampOf(g3l5, sym);
            if (t3 != null) {
                saveTransition(sym, t3, g5l7,  ST3, "3T5");
                saveTransition(sym, t3, g7l10, ST3, "3T7");
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * If the symbol is present in targetMap and this transition hasn't been
     * saved yet, compute the seconds elapsed and store in Redis.
     */
    private void saveTransition(String symbol, Timestamp from,
                                Map<String, WatchList> targetMap,
                                String storeKey, String label) {
        if (!targetMap.containsKey(symbol)) return;
        String guardKey = symbol + "::" + storeKey + "::" + label;
        if (!saved.add(guardKey)) return;   // already saved

        Timestamp to      = targetMap.get(symbol).getTimestamp();
        double    seconds = toSeconds(from.getTime(), to.getTime());

        if (seconds <= 0) return;  // clock skew guard

        List<ShortestTime> list = List.of(new ShortestTime(symbol, seconds));
        repository.saveAllShortestTime(storeKey, label, new TimeAnalyse(label, list));
        log.debug("Transition {} {} → {} = {:.1f}s", symbol, storeKey, label, seconds);
    }

    private Timestamp timestampOf(Map<String, WatchList> map, String symbol) {
        WatchList w = map.get(symbol);
        return w != null ? w.getTimestamp() : null;
    }
}