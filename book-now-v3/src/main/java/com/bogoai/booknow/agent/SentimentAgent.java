package com.bogoai.booknow.agent;

import com.bogoai.booknow.repository.BookNowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Agent specialized in News Sentiment analysis.
 * Integrates results from the news-analyzer Python pipeline.
 */
@Slf4j
@Service
public class SentimentAgent implements TradingAgent {

    @Autowired
    private BookNowRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public double evaluateSignal(String symbol, double price) {
        try {
            // Convert SOLUSDT -> SOL
            String coin = symbol.replace("USDT", "");
            String json = repository.getNewsAnalysis(coin);

            if (json == null || json.isEmpty()) {
                log.info("  [NewsAgent] No news data for {}. Returning neutral (0.0).", coin);
                return 0.0;
            }

            JsonNode node = objectMapper.readTree(json);
            double score = node.get("score").asDouble();
            
            log.info("  [NewsAgent] {} News Score: {}", coin, String.format("%.2f", score));
            return score;

        } catch (Exception e) {
            log.error("  [NewsAgent] Error evaluating signal for {}: {}", symbol, e.getMessage());
            return 0.0;
        }
    }

    @Override
    public String getAgentName() {
        return "NewsSentimentAgent";
    }
}
