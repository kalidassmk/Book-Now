package com.bogoai.booknow.agent;

import com.bogoai.booknow.repository.BookNowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Agent that reads the Dashboard Analysis Score (0–7) from Redis.
 * Data source: Node.js server.js calculateScore() → Redis hash: DASHBOARD_SCORE
 *
 * Signal mapping (score 0–7):
 *   7 (STRONG_BUY) →  +1.0
 *   5-6 (BUY)      →  +0.5 to +0.8
 *   3-4 (NEUTRAL)   →  -0.1 to +0.2
 *   0-2 (DONT_BUY)  →  -0.7 to -1.0
 */
@Slf4j
@Service
public class DashboardScoreAgent implements TradingAgent {

    @Autowired
    private BookNowRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public double evaluateSignal(String symbol, double price) {
        try {
            String json = repository.getDashboardScore(symbol);

            if (json == null || json.isEmpty()) {
                log.info("  [DashboardAgent] No dashboard score for {}. Returning neutral (0.0).", symbol);
                return 0.0;
            }

            JsonNode node = objectMapper.readTree(json);
            int score = node.has("score") ? node.get("score").asInt() : 0;
            String rec = node.has("recommendation") ? node.get("recommendation").asText() : "UNKNOWN";

            // Map 0–7 score to -1.0 → +1.0
            // 3.5 = neutral (0.0), 7 = max bullish (+1.0), 0 = max bearish (-1.0)
            double signal = (score - 3.5) / 3.5;

            log.info("  [DashboardAgent] {} → score={}/7 rec={} signal={}",
                symbol, score, rec, String.format("%.2f", signal));

            return Math.max(-1.0, Math.min(1.0, signal));

        } catch (Exception e) {
            log.error("  [DashboardAgent] Error for {}: {}", symbol, e.getMessage());
            return 0.0;
        }
    }

    @Override
    public String getAgentName() {
        return "DashboardScoreAgent";
    }
}
