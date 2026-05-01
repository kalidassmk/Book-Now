package com.bogoai.booknow.agent;

import com.bogoai.booknow.util.TrailingStopLossService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Agent specialized in Risk Assessment.
 * Checks for parabolic moves, liquidity, and portfolio exposure.
 */
@Service
public class RiskAgent implements TradingAgent {

    @Autowired
    private com.bogoai.booknow.service.CoinIntelligenceService intelligenceService;

    @Override
    public double evaluateSignal(String symbol, double price) {
        // Use Global Health Index from Redis (External Intelligence)
        boolean isHealthy = intelligenceService.shouldProceedWithBuy(symbol);
        
        if (!isHealthy) {
            return -1.0; // Strong block if unhealthy
        }

        return 0.8; // Safe to proceed
    }

    @Override
    public String getAgentName() {
        return "RiskAgent";
    }
}
