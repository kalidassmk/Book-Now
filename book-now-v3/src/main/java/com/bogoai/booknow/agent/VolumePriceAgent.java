package com.bogoai.booknow.agent;

import com.bogoai.booknow.repository.BookNowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Agent that reads the multi-timeframe Volume & Price analysis from Redis.
 * Data source: Python volume_price_analyzer.py → Redis hash: VOLUME_SCORE
 *
 * Signal mapping (score 0–100):
 *   score > 70 (BUY)   →  +0.8 to +1.0
 *   score 40–70 (HOLD)  →  -0.2 to +0.4
 *   score < 40 (AVOID)  →  -0.6 to -1.0
 */
@Slf4j
@Service
public class VolumePriceAgent implements TradingAgent {

    @Autowired
    private BookNowRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public double evaluateSignal(String symbol, double price) {
        try {
            String json = repository.getVolumeScore(symbol);

            if (json == null || json.isEmpty()) {
                log.info("  [VolumePriceAgent] No volume data for {}. Returning neutral (0.0).", symbol);
                return 0.0;
            }

            JsonNode node = objectMapper.readTree(json);
            double score = node.has("score") ? node.get("score").asDouble() : 50.0;
            String decision = node.has("decision") ? node.get("decision").asText() : "HOLD";

            // Map 0–100 score to -1.0 → +1.0
            // 50 = neutral (0.0), 100 = max bullish (+1.0), 0 = max bearish (-1.0)
            double signal = (score - 50.0) / 50.0;

            log.info("  [VolumePriceAgent] {} → {} (score={}, signal={})",
                symbol, decision, String.format("%.1f", score), String.format("%.2f", signal));

            return Math.max(-1.0, Math.min(1.0, signal));

        } catch (Exception e) {
            log.error("  [VolumePriceAgent] Error for {}: {}", symbol, e.getMessage());
            return 0.0;
        }
    }

    @Override
    public String getAgentName() {
        return "VolumePriceAgent";
    }
}
