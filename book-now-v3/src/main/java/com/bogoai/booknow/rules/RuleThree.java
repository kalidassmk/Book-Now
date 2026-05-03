package com.bogoai.booknow.rules;

import com.bogoai.booknow.agent.ConsensusCoordinator;
import com.bogoai.booknow.model.CurrentPrice;
import com.bogoai.booknow.model.RThree;
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
 * Rule 3 — Multi-Path Convergence to 5% Gain
 *
 * Highest-confidence signal. Requires that the coin's move to 5% is confirmed
 * by MORE THAN ONE timing path, reducing the chance of a false positive spike.
 *
 *   Pattern R3-STRONGEST : 3→5 (≤1500s) AND 2→5 (≤1500s) AND 1→5 (≤1500s) AND 0→5 (≤2000s)
 *   Pattern R3-STRONG    : 3→5 (≤1500s) AND 2→5 (≤1500s)
 *
 * Both patterns trigger BUY with +9% profit target (highest of the three rules).
 */
public class RuleThree implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(RuleThree.class);

    // Timing thresholds (seconds)
    private static final double MAX_3T5 = 1500.0;
    private static final double MAX_2T5 = 1500.0;
    private static final double MAX_1T5 = 1500.0;
    private static final double MAX_0T5 = 2000.0;

    private final BookNowRepository    repository;
    private final ConsensusCoordinator consensusCoordinator;

    private final Set<String> triggered = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public RuleThree(BookNowRepository repository, ConsensusCoordinator consensusCoordinator) {
        this.repository    = repository;
        this.consensusCoordinator = consensusCoordinator;
    }

    @Override
    public void run() {
        log.info("=== RuleThree starting ===");
        while (!Thread.currentThread().isInterrupted()) {
            try {
                evaluate();
                Thread.sleep(500);
            } catch (InterruptedException ie) {
                break;
            } catch (Exception e) {
                log.error("RuleThree error: {}", e.getMessage(), e);
                try { Thread.sleep(2000); } catch (InterruptedException ie) { break; }
            }
        }
    }

    private void evaluate() {
        TimeAnalyse st3_3T5 = repository.getShortestTime(ST3, "3T5");
        TimeAnalyse st2_2T5 = repository.getShortestTime(ST2, "2T5");
        TimeAnalyse st1_1T5 = repository.getShortestTime(ST1, "1T5");
        TimeAnalyse st0_0T5 = repository.getShortestTime(ST0, "0T5");
        Map<String, CurrentPrice> prices = repository.getAllCurrentPrice(CURRENT_PRICE);

        Map<String, Double> map3T5 = indexBySymbol(st3_3T5);
        Map<String, Double> map2T5 = indexBySymbol(st2_2T5);
        Map<String, Double> map1T5 = indexBySymbol(st1_1T5);
        Map<String, Double> map0T5 = indexBySymbol(st0_0T5);

        // Iterate only over coins that have confirmed 3→5 (minimum requirement)
        for (String symbol : map3T5.keySet()) {
            if (triggered.contains(symbol))   continue;
            if (!prices.containsKey(symbol))  continue;

            double t3t5 = map3T5.get(symbol);
            boolean has3T5 = t3t5 > 0 && t3t5 <= MAX_3T5;
            if (!has3T5) continue;

            double t2t5 = map2T5.getOrDefault(symbol, 0.0);
            double t1t5 = map1T5.getOrDefault(symbol, 0.0);
            double t0t5 = map0T5.getOrDefault(symbol, 0.0);

            boolean has2T5 = t2t5 > 0 && t2t5 <= MAX_2T5;
            boolean has1T5 = t1t5 > 0 && t1t5 <= MAX_1T5;
            boolean has0T5 = t0t5 > 0 && t0t5 <= MAX_0T5;

            String pattern = null;
            if (has3T5 && has2T5 && has1T5 && has0T5) {
                pattern = "R3-STRONGEST";
            } else if (has3T5 && has2T5) {
                pattern = "R3-STRONG";
            }

            if (pattern != null) {
                RThree rThree = buildRThree(symbol, t3t5, t2t5, t1t5, t0t5);
                rThree.setType(pattern);
                repository.saveRules(RULE_3,     symbol, rThree);
                repository.saveRules(RULE_3_HIT, symbol, rThree);
                triggered.add(symbol);
                log.info("{}: {} | 3T5={:.1f}s 2T5={:.1f}s 1T5={:.1f}s 0T5={:.1f}s",
                    pattern, symbol, t3t5, t2t5, t1t5, t0t5);
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

    private RThree buildRThree(String symbol, double t3t5, double t2t5, double t1t5, double t0t5) {
        RThree r = new RThree();
        r.setSymbol(symbol);
        r.setThreeToFive(t3t5);
        r.setTwoToFive(t2t5);
        r.setOneToFive(t1t5);
        r.setZeroToFive(t0t5);
        r.setHms(getHMS());
        return r;
    }
}
