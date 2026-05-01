package com.bogoai.booknow.rules;

import com.bogoai.booknow.agent.ConsensusCoordinator;
import com.bogoai.booknow.model.CurrentPrice;
import com.bogoai.booknow.model.ROne;
import com.bogoai.booknow.model.ShortestTime;
import com.bogoai.booknow.model.TimeAnalyse;
import com.bogoai.booknow.repository.BookNowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.bogoai.booknow.util.BookNowUtility.getHMS;
import static com.bogoai.booknow.util.Constant.*;

/**
 * Rule 1 — Rapid 0%→1%→2%→3% Ladder
 *
 * Triggers a BUY when a coin shows fast sequential band crossings:
 *
 *   Pattern R1-FULL  : crossed 0→1 (≤60s) AND 1→2 (≤60s) AND 2→3 (≤180s)
 *                      → BUY with +5% profit target
 *
 *   Pattern R1-PARTIAL: crossed 1→2 (≤60s) AND 2→3 (≤180s) (missed 0→1)
 *                      → BUY with +5% profit target
 *
 *   Pattern R1-ULTRA : 0→1 in ≤20s AND 2→3 in ≤20s simultaneously
 *                      → BUY with +3.5% profit target (tighter, faster)
 */
public class RuleOne implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(RuleOne.class);

    // Timing thresholds (seconds)
    private static final double MAX_0T1_ULTRA  =  20.0;
    private static final double MAX_0T1_FULL   =  60.0;
    private static final double MAX_1T2_FULL   =  60.0;
    private static final double MAX_2T3_ULTRA  =  20.0;
    private static final double MAX_2T3_FULL   = 180.0;

    private final BookNowRepository    repository;
    private final ConsensusCoordinator consensusCoordinator;

    /** Guards so each symbol triggers at most one buy per session. */
    private final Set<String> triggered = java.util.Collections.newSetFromMap(
        new java.util.concurrent.ConcurrentHashMap<>());

    public RuleOne(BookNowRepository repository, ConsensusCoordinator consensusCoordinator) {
        this.repository    = repository;
        this.consensusCoordinator = consensusCoordinator;
    }

    @Override
    public void run() {
        log.info("=== RuleOne starting ===");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                evaluate();
            } catch (Exception e) {
                log.error("RuleOne error: {}", e.getMessage(), e);
            }
        }
    }

    private void evaluate() {
        TimeAnalyse st0_0T1 = repository.getShortestTime(ST0, "0T1");
        TimeAnalyse st1_1T2 = repository.getShortestTime(ST1, "1T2");
        TimeAnalyse st2_2T3 = repository.getShortestTime(ST2, "2T3");
        Map<String, CurrentPrice> prices = repository.getAllCurrentPrice(CURRENT_PRICE);

        // Index transitions by symbol
        Map<String, Double> map0T1 = indexBySymbol(st0_0T1);
        Map<String, Double> map1T2 = indexBySymbol(st1_1T2);
        Map<String, Double> map2T3 = indexBySymbol(st2_2T3);

        // ── Pattern R1-FULL and R1-PARTIAL ────────────────────────────────────
        for (String symbol : map2T3.keySet()) {
            if (triggered.contains(symbol)) continue;

            double t2t3 = map2T3.get(symbol);
            double t1t2 = map1T2.getOrDefault(symbol, 0.0);
            double t0t1 = map0T1.getOrDefault(symbol, 0.0);

            boolean has0T1 = t0t1 > 0 && t0t1 <= MAX_0T1_FULL;
            boolean has1T2 = t1t2 > 0 && t1t2 <= MAX_1T2_FULL;
            boolean has2T3 = t2t3 > 0 && t2t3 <= MAX_2T3_FULL;

            ROne rOne = buildROne(symbol, t0t1, t1t2, t2t3);

            if ((has0T1 && has1T2 && has2T3) || (has1T2 && has2T3)) {
                // R1-FULL or R1-PARTIAL
                rOne.setType("R1-FULL");
                repository.saveRules(RULE_1,     symbol, rOne);
                repository.saveRules(RULE_1_HIT, symbol, rOne);
                triggered.add(symbol);
                log.info("R1-FULL: {} | 0T1={:.1f}s 1T2={:.1f}s 2T3={:.1f}s",
                    symbol, t0t1, t1t2, t2t3);
                consensusCoordinator.coordinate(symbol, prices.get(symbol));
            }
        }

        // ── Pattern R1-ULTRA: 0→1 in ≤20s AND 2→3 in ≤20s ───────────────────
        for (String symbol : map0T1.keySet()) {
            if (triggered.contains(symbol)) continue;
            if (!map2T3.containsKey(symbol)) continue;

            double t0t1 = map0T1.get(symbol);
            double t2t3 = map2T3.get(symbol);

            if (t0t1 <= MAX_0T1_ULTRA && t2t3 <= MAX_2T3_ULTRA) {
                ROne rOne = buildROne(symbol, t0t1, map1T2.getOrDefault(symbol, 0.0), t2t3);
                rOne.setType("R1-ULTRA");
                repository.saveRules(RULE_1, symbol, rOne);
                triggered.add(symbol);
                log.info("R1-ULTRA: {} | 0T1={:.1f}s 2T3={:.1f}s", symbol, t0t1, t2t3);
                consensusCoordinator.coordinate(symbol, prices.get(symbol));
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Returns a map of symbol → shortest time from a TimeAnalyse object. */
    private Map<String, Double> indexBySymbol(TimeAnalyse ta) {
        Map<String, Double> result = new HashMap<>();
        if (ta == null || ta.getShortestTimeList() == null) return result;
        for (ShortestTime st : ta.getShortestTimeList()) {
            if (st.getTimeTook() > 0) {
                result.merge(st.getSymbol(), st.getTimeTook(), Math::min);
            }
        }
        return result;
    }

    private ROne buildROne(String symbol, double t0t1, double t1t2, double t2t3) {
        ROne r = new ROne();
        r.setSymbol(symbol);
        r.setZeroToOne(t0t1);
        r.setOneToTwo(t1t2);
        r.setTwoToThree(t2t3);
        r.setHms(getHMS());
        return r;
    }
}
