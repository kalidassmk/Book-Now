package com.bogoai.booknow.agent;

/**
 * Interface for all specialized trading agents.
 */
public interface TradingAgent {
    
    /**
     * Evaluates a symbol and returns a signal score.
     * 
     * @param symbol Symbol (e.g. "SOLUSDT")
     * @param price  Current market price
     * @return Score from -1.0 (Strong Sell) to 1.0 (Strong Buy)
     */
    double evaluateSignal(String symbol, double price);
    
    /**
     * Returns the name of the agent for logging.
     */
    String getAgentName();
}
