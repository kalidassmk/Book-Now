package com.bogoai.booknow.agent;

import com.bogoai.booknow.repository.BookNowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Agent that reads the Adaptive Behavioral Sentiment from Redis.
 * Data source: Python market_sentiment_engine.py → Redis key: sentiment:market:adaptive:{SYMBOL}
 *
 * Signal mapping:
 *   "Strong Bullish"  →  +1.0
 *   "Bullish"         →  +0.6
 *   "Neutral"         →   0.0
 *   "Bearish"         →  -0.6
 *   "Strong Bearish"  →  -1.0
 */
@Slf4j
@Service
public class BehavioralSentimentAgent implements TradingAgent {

    @Autowired
    private BookNowRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public double evaluateSignal(String symbol, double price) {
        try {
            String json = repository.getBehavioralSentiment(symbol);

            if (json == null || json.isEmpty()) {
                log.info("  [BehavioralAgent] No sentiment data for {}. Returning neutral (0.0).", symbol);
                return 0.0;
            }

            JsonNode node = objectMapper.readTree(json);
            String sentiment = node.has("sentiment") ? node.get("sentiment").asText() : "";
            double score = node.has("score") ? node.get("score").asDouble() : 50.0;
            double confidence = node.has("confidence") ? node.get("confidence").asDouble() : 0.0;

            // Map sentiment label to -1.0 → +1.0 range
            double signal;
            if (sentiment.contains("Strong Bullish")) {
                signal = 1.0;
            } else if (sentiment.contains("Bullish")) {
                signal = 0.6;
            } else if (sentiment.contains("Strong Bearish")) {
                signal = -1.0;
            } else if (sentiment.contains("Bearish")) {
                signal = -0.6;
            } else {
                // Neutral — use the raw score (0-100) mapped to -1 to +1
                signal = (score - 50.0) / 50.0;
            }

            // Scale by confidence (higher confidence = stronger signal)
            if (confidence > 0) {
                signal *= Math.min(confidence / 100.0, 1.0);
            }

            log.info("  [BehavioralAgent] {} → {} (score={}, confidence={}, signal={})",
                symbol, sentiment, String.format("%.1f", score),
                String.format("%.1f", confidence), String.format("%.2f", signal));

            return Math.max(-1.0, Math.min(1.0, signal));

        } catch (Exception e) {
            log.error("  [BehavioralAgent] Error for {}: {}", symbol, e.getMessage());
            return 0.0;
        }
    }

    @Override
    public String getAgentName() {
        return "BehavioralSentimentAgent";
    }
}
