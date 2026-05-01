package com.bogoai.booknow.agent;

import com.bogoai.booknow.repository.BookNowRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Agent that pulls the Master Consensus from the 10-algorithm Python engine.
 * This is the ultimate 'Professional' confirmation gate.
 */
@Slf4j
@Service
public class PythonConsensusAgent implements TradingAgent {

    @Autowired
    private BookNowRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public double evaluateSignal(String symbol, double price) {
        try {
            String json = repository.getFinalConsensus(symbol);

            if (json == null || json.isEmpty()) {
                log.info("  [PythonAgent] No master consensus data for {}. Returning neutral (0.0).", symbol);
                return 0.0;
            }

            JsonNode node = objectMapper.readTree(json);
            double score = node.get("score").asDouble(); // 0 to 100
            boolean isBlocked = node.get("is_blocked").asBoolean();

            if (isBlocked) {
                String reason = node.get("block_reason").asText();
                log.info("  [PythonAgent] 🛑 VETO for {}: {}", symbol, reason);
                return -1.0; // Hard rejection
            }

            // Normalize 0-100 to 0.0-1.0
            double normalizedScore = score / 100.0;
            
            log.info("  [PythonAgent] {} Master Consensus Score: {}", symbol, String.format("%.2f", normalizedScore));
            return normalizedScore;

        } catch (Exception e) {
            log.error("  [PythonAgent] Error evaluating signal for {}: {}", symbol, e.getMessage());
            return 0.0;
        }
    }

    @Override
    public String getAgentName() {
        return "PythonMasterConsensus";
    }
}
