package com.bogoai.booknow.processor;

import com.bogoai.booknow.model.FastMove;
import com.bogoai.booknow.repository.BookNowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

/**
 * FastMoveFilter — every 500ms publishes the top-5 coins by overall
 * momentum score to the FM-5 Redis key.
 *
 * This gives downstream consumers (dashboards, alerts) a real-time
 * "hottest coins" snapshot without scanning the full FAST_MOVE map.
 */
public class FastMoveFilter implements Runnable {

    private static final Logger log    = LoggerFactory.getLogger(FastMoveFilter.class);
    private static final int    TOP_N  = 5;
    private static final long   SLEEP_MS = 500L;

    private final BookNowRepository repository;

    public FastMoveFilter(BookNowRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run() {
        log.info("=== FastMoveFilter starting (refresh every {}ms) ===", SLEEP_MS);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                refresh();
                Thread.sleep(SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.info("FastMoveFilter interrupted — stopping.");
            } catch (Exception e) {
                log.error("FastMoveFilter error: {}", e.getMessage(), e);
            }
        }
    }

    private void refresh() {
        Map<String, FastMove> allMoves = repository.getFastMoveEntry("FAST_MOVE");
        if (allMoves.isEmpty()) return;

        // Sort by overAllCount descending, take top-N
        LinkedHashMap<String, FastMove> top = allMoves.entrySet().stream()
            .sorted(Map.Entry.<String, FastMove>comparingByValue(
                Comparator.comparingDouble(FastMove::getOverAllCount).reversed()))
            .limit(TOP_N)
            .collect(toMap(Map.Entry::getKey, Map.Entry::getValue,
                           (a, b) -> a, LinkedHashMap::new));

        // Atomically replace FM-5
        repository.deleteFastMove("FM-5");
        top.forEach((sym, fm) -> repository.saveFastMove("FM-5", sym, fm));

        log.debug("FM-5 updated: {}", top.keySet());
    }
}
