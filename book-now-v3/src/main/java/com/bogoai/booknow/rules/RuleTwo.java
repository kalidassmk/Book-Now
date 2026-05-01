package com.bogoai.booknow.rules;

import com.bogoai.booknow.agent.ConsensusCoordinator;
import com.bogoai.booknow.model.CurrentPrice;
import com.bogoai.booknow.model.RTwo;
import com.bogoai.booknow.model.ShortestTime;
import com.bogoai.booknow.model.TimeAnalyse;
import com.bogoai.booknow.repository.BookNowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.bogoai.booknow.util.BookNowUtility.getHMS;
import static com.bogoai.booknow.util.Constant.*;

/**
 * Rule 2 — Sustained 1%→2%→3%→5% Ladder
 *
 * Triggers a BUY when a coin climbs steadily through three consecutive bands:
 *
 *   Pattern R2-FULL    : 1→2 (≤150s) AND 2→3 (≤150s) AND 3→5 (≤1500s)
 *                        → BUY with +7% profit target
 *
 *   Pattern R2-PARTIAL : 2→3 (≤150s) AND 3→5 (≤1500s)  (entered at 2% band)
 *                        → BUY with +7% profit target
 *
 * Higher sell target (7%) vs Rule 1 because the sustained, multi-step
 * confirmation indicates stronger underlying momentum.
 */
public class RuleTwo implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(RuleTwo.class);

    // Timing thresholds (seconds)
    private static final double MAX_1T2 =  150.0;
    private static final double MAX_2T3 =  150.0;
    private static final double MAX_3T5 = 1500.0;

    private final BookNowRepository    repository;
    private final ConsensusCoordinator consensusCoordinator;

    private final Set<String> triggered = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public RuleTwo(BookNowRepository repository, ConsensusCoordinator consensusCoordinator) {
        this.repository    = repository;
        this.consensusCoordinator = consensusCoordinator;
    }

    @Override
    public void run() {
        log.info("=== RuleTwo starting ===");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                evaluate();
            } catch (Exception e) {
                log.error("RuleTwo error: {}", e.getMessage(), e);
            }
        }
    }

    private void evaluate() {
        TimeAnalyse st1_1T2 = repository.getShortestTime(ST1, "1T2");
        TimeAnalyse st2_2T3 = repository.getShortestTime(ST2, "2T3");
        TimeAnalyse st3_3T5 = repository.getShortestTime(ST3, "3T5");
        Map<String, CurrentPrice> prices = repository.getAllCurrentPrice(CURRENT_PRICE);

        Map<String, Double> map1T2 = indexBySymbol(st1_1T2);
        Map<String, Double> map2T3 = indexBySymbol(st2_2T3);
        Map<String, Double> map3T5 = indexBySymbol(st3_3T5);

        // Iterate over coins that reached the 3→5 band (final confirmation)
        for (String symbol : map3T5.keySet()) {
            if (triggered.contains(symbol)) continue;
            if (!prices.containsKey(symbol))  continue;

            double t3t5 = map3T5.get(symbol);
            double t2t3 = map2T3.getOrDefault(symbol, 0.0);
            double t1t2 = map1T2.getOrDefault(symbol, 0.0);

            boolean has1T2 = t1t2 > 0 && t1t2 <= MAX_1T2;
            boolean has2T3 = t2t3 > 0 && t2t3 <= MAX_2T3;
            boolean has3T5 = t3t5 > 0 && t3t5 <= MAX_3T5;

            if (!has3T5) continue;

            String pattern = null;
            if (has1T2 && has2T3) {
                pattern = "R2-FULL";
            } else if (has2T3) {
                pattern = "R2-PARTIAL";
            }

            if (pattern != null) {
                RTwo rTwo = buildRTwo(symbol, t1t2, t2t3, t3t5);
                rTwo.setType(pattern);
                repository.saveRules(RULE_2,     symbol, rTwo);
                repository.saveRules(RULE_2_HIT, symbol, rTwo);
                triggered.add(symbol);
                log.info("{}: {} | 1T2={:.1f}s 2T3={:.1f}s 3T5={:.1f}s",
                    pattern, symbol, t1t2, t2t3, t3t5);
                consensusCoordinator.coordinate(symbol, prices.get(symbol));
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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

    private RTwo buildRTwo(String symbol, double t1t2, double t2t3, double t3t5) {
        RTwo r = new RTwo();
        r.setSymbol(symbol);
        r.setOneToTwo(t1t2);
        r.setTwoToThree(t2t3);
        r.setThreeToFive(t3t5);
        r.setHms(getHMS());
        return r;
    }
}
