package com.bogoai.booknow.agent;

import com.bogoai.booknow.model.CurrentPrice;
import com.bogoai.booknow.util.TradeExecutor;
import com.bogoai.booknow.util.TradingConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Coordinates consensus between multiple specialized agents.
 * Only triggers trades when agents reach a collective agreement.
 */
@Slf4j
@Service
public class ConsensusCoordinator {

    @Autowired
    private List<TradingAgent> agents;

    @Autowired
    private TradeExecutor tradeExecutor;

    @Autowired
    private TradingConfigService configService;

    private static final double CONSENSUS_THRESHOLD = 0.7;

    /**
     * Collects signals from all agents and decides whether to execute a buy.
     * 
     * @param symbol Symbol (e.g. "SOLUSDT")
     * @param cp     Current Price data
     */
    public void coordinate(String symbol, CurrentPrice cp) {
        double totalPrice = cp.getPrice().doubleValue();
        double totalScore = 0;

        log.info("[Consensus] ══════════════════════════════════════════════");
        log.info("[Consensus] Evaluating {} with {} agents...", symbol, agents.size());

        for (TradingAgent agent : agents) {
            double signal = agent.evaluateSignal(symbol, totalPrice);
            String emoji = signal > 0.5 ? "✅" : signal < -0.3 ? "❌" : "⚪";
            log.info("[Consensus]   {} {} → {}", emoji, agent.getAgentName(), String.format("%.2f", signal));
            totalScore += signal;
        }

        double averageScore = totalScore / agents.size();
        log.info("[Consensus] Final Average Score for {}: {}", symbol, String.format("%.2f", averageScore));

        if (averageScore > CONSENSUS_THRESHOLD) {
            log.info("[Consensus] ✅ AGREE! Score {} > threshold {}. Triggering BUY for {}", 
                String.format("%.2f", averageScore), CONSENSUS_THRESHOLD, symbol);
            tradeExecutor.tryBuy(symbol, cp, configService.getProfitPct(), "CONSENSUS_AGENT");
        } else {
            log.info("[Consensus] ⛔ DISAGREE. Score {} below threshold {}. Skipping {}.", 
                String.format("%.2f", averageScore), CONSENSUS_THRESHOLD, symbol);
        }
        log.info("[Consensus] ══════════════════════════════════════════════");
    }
}
