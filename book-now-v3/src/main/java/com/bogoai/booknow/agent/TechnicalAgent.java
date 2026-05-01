package com.bogoai.booknow.agent;

import com.bogoai.booknow.model.CoinAnalysisResult;
import com.bogoai.booknow.util.CoinAnalyzer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Agent specialized in technical indicators (RSI, EMA, Volume).
 */
@Service
public class TechnicalAgent implements TradingAgent {

    @Autowired
    private CoinAnalyzer coinAnalyzer;

    @Override
    public double evaluateSignal(String symbol, double price) {
        // Reuse existing CoinAnalyzer logic
        CoinAnalysisResult analysis = coinAnalyzer.analyze(symbol, price);
        
        // Convert 0-7 score to -1.0 to 1.0 range
        // 4 is Neutral/Buy start -> 0.0
        // 7 is Strong Buy -> 1.0
        // 0 is Strong Sell -> -1.0
        double score = (analysis.getBuyScore() - 3.5) / 3.5;
        
        // If blocked by parabolic safety, force negative
        if (analysis.getRecommendation().equals("RISKY_PARABOLIC")) {
            return -1.0;
        }
        
        return Math.max(-1.0, Math.min(1.0, score));
    }

    @Override
    public String getAgentName() {
        return "TechnicalAgent";
    }
}
