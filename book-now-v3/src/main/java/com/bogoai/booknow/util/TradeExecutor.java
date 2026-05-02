package com.bogoai.booknow.util;

import com.bogoai.api.client.BinanceApiRestClient;
import com.bogoai.api.client.domain.account.NewOrderResponse;
import com.bogoai.booknow.model.Buy;
import com.bogoai.booknow.model.CurrentPrice;
import com.bogoai.booknow.model.Sell;
import com.bogoai.booknow.repository.BookNowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.bogoai.api.client.domain.account.NewOrder.limitBuy;
import static com.bogoai.api.client.domain.account.NewOrder.limitSell;
import static com.bogoai.api.client.domain.account.NewOrder.marketBuy;
import static com.bogoai.api.client.domain.account.NewOrder.marketSell;
import static com.bogoai.api.client.domain.TimeInForce.GTC;
import static com.bogoai.booknow.util.BookNowUtility.getHMS;
import static com.bogoai.booknow.util.Constant.BUY_KEY;
import static com.bogoai.booknow.util.Constant.SELL_KEY;
import com.bogoai.api.client.exception.BinanceApiException;
import javax.annotation.PostConstruct;


/**
 * Single, reusable buy/sell executor used by all three Rules.
 * Eliminates the copy-paste of buy() + setProfitPrice() across RuleOne, RuleTwo, RuleThree.
 *
 * Toggle live trading via application.properties:  trading.live-mode=true
 */
@Component
public class TradeExecutor {

    private static final Logger log = LoggerFactory.getLogger(TradeExecutor.class);

    @Autowired
    private BookNowRepository repository;

    @Autowired
    private BinanceApiRestClient prodBinanceApiARestClient;

    @Autowired
    private TradeState tradeState;

    @Autowired
    private BinanceFilterService filterService;

    /**
     * CoinAnalyzer runs a 2-month history check before allowing any buy.
     * Injected here so tryBuy() can gate on shouldBuy().
     */
    @Autowired
    private CoinAnalyzer coinAnalyzer;

    @Autowired
    private BinanceDelistService delistService;

    @Autowired
    private TrailingStopLossService tslService;

    @Autowired
    private BinanceDustService binanceDustService;

    @Autowired
    private TradingConfigService configService;

    /** Toggle: false = Paper trade, true = Live orders */
    @Value("${trading.live-mode:false}")
    private boolean liveMode;

    @Value("${prod.api.key}")
    private String prodApiKey;

    @Value("${prod.secret.key}")
    private String prodSecretKey;

    @PostConstruct
    public void init() {
        String maskedKey = "N/A";
        String maskedSecret = "N/A";
        if (prodApiKey != null && prodApiKey.length() > 8) {
            maskedKey = prodApiKey.substring(0, 4) + "..." + prodApiKey.substring(prodApiKey.length() - 4);
        }
        if (prodSecretKey != null && prodSecretKey.length() > 8) {
            maskedSecret = prodSecretKey.substring(0, 4) + "..." + prodSecretKey.substring(prodSecretKey.length() - 4);
        }
        log.info("[Binance API CONFIG] Loaded API Key: {}", maskedKey);
        log.info("[Binance API CONFIG] Loaded Secret Key: {}", maskedSecret);
        log.info("[Binance API CONFIG] Live Mode: {}", liveMode);
        
        if (liveMode && ("N/A".equals(maskedKey) || "N/A".equals(maskedSecret))) {
            log.error("[Binance API CONFIG] CRITICAL: API Key or Secret is missing or too short! Trading will fail.");
        }
    }


    /**
     * Attempt to buy a symbol if it is not already held.
     *
     * @param symbol      e.g. "SOLUSDT"
     * @param currentPrice latest price data from Redis
     * @param sellPct     profit target percentage for the limit-sell (e.g. 5.0 = 5%)
     * @param ruleLabel   which rule triggered (for logging/notification)
     */
    public void tryBuy(String symbol, CurrentPrice currentPrice, double sellPct, String ruleLabel) {
        if (!configService.isAutoBuyEnabled()) {
            log.info("[{}] Auto-buy is DISABLED via config. Skipping buy for {}.", ruleLabel, symbol);
            return;
        }

        if (tradeState.isAlreadyBought(symbol)) {
            log.debug("[{}] Skip buy {} — already in position", ruleLabel, symbol);
            return;
        }

        if (delistService.isDelisted(symbol)) {
            log.warn("[{}] CRITICAL: Skip buy {} — Symbol is marked as DELISTED in Binance announcements!", ruleLabel, symbol);
            return;
        }

        // ── Buy-gate: analyse 2-month history before placing any order ──────────
        double currentPriceDouble = currentPrice.getPrice().doubleValue();
        if (!coinAnalyzer.shouldBuy(symbol, currentPriceDouble)) {
            log.info("[{}] Skip buy {} — analysis gate rejected (score < 4)", ruleLabel, symbol);
            return;
        }

        try {
            BigDecimal price = currentPrice.getPrice();
            String qty = calculateQty(symbol, price);

            NewOrderResponse orderResponse = placeBuyOrder(symbol, qty, price);
            BigDecimal buyPrice = new BigDecimal(orderResponse.getPrice());

            Buy buy = new Buy();
            buy.setStatus(orderResponse.getStatus() != null ? orderResponse.getStatus().name() : "FILLED");
            buy.setBuyPercentage(currentPrice.getPercentage());
            buy.setBuyPrice(buyPrice);
            buy.setSelP(sellPct);
            buy.setHms(getHMS());
            buy.setOrderId(orderResponse.getOrderId());
            buy.setExecutedQty(orderResponse.getExecutedQty());
            buy.setOrigQty(orderResponse.getOrigQty());

            repository.saveBuy(BUY_KEY, symbol, buy);
            tradeState.markBought(symbol, ruleLabel);
            tslService.startTracking(symbol, buyPrice);

            if (liveMode) {
                placeLimitSell(orderResponse, buyPrice, sellPct);
            }

            String message = String.format("%s BUY %s @ %s (target +%.1f%%)", ruleLabel, symbol, buyPrice, sellPct);
            log.info(message);
            BookNowUtility.runNotification(message);

        } catch (Exception e) {
            log.error("[{}] Error executing buy for {}: {}", ruleLabel, symbol, e.getMessage(), e);
        }
    }

    /**
     * Cancel an open order on Binance.
     * @param symbol  e.g. "SOLUSDT"
     * @param orderId Binance order ID
     */
    public void cancelOrder(String symbol, Long orderId) {
        log.info("[Cancel] Attempting to cancel order {} for {}", orderId, symbol);
        try {
            // CRITICAL VALIDATION: Check if order is already filled
            com.bogoai.api.client.domain.account.Order order = getOrderStatus(symbol, orderId);
            if (order != null && "FILLED".equals(order.getStatus().name())) {
                throw new RuntimeException("Cannot cancel a filled order");
            }

            log.info("[Binance API REQUEST] cancelOrder | symbol={} orderId={}", symbol, orderId);
            prodBinanceApiARestClient.cancelOrder(new com.bogoai.api.client.domain.account.request.CancelOrderRequest(symbol, orderId));
            log.info("[Binance API RESPONSE] Order #{} for {} was successfully cancelled.", orderId, symbol);
        } catch (BinanceApiException e) {
            log.error("[Binance API ERROR] Cancellation failed for {} #{}. Binance says: {}. (Check if your API key has 'Enable Spot & Margin Trading' permissions)", symbol, orderId, e.getMessage());
            throw new RuntimeException("Cancel failed: " + e.getMessage());
        } catch (Exception e) {
            log.error("[Binance API ERROR] An unexpected error occurred while cancelling {} #{}: {}", symbol, orderId, e.getMessage());
            throw new RuntimeException("Cancel failed: " + e.getMessage());
        }
    }

    public com.bogoai.api.client.domain.account.Order getOrderStatus(String symbol, Long orderId) {
        try {
            log.info("[Binance API REQUEST] getOrderStatus | symbol={} orderId={}", symbol, orderId);
            com.bogoai.api.client.domain.account.Order order = prodBinanceApiARestClient.getOrderStatus(new com.bogoai.api.client.domain.account.request.OrderStatusRequest(symbol, orderId));
            log.info("[Binance API RESPONSE] getOrderStatus | symbol={} status={} orderId={} executedQty={} origQty={}", 
                symbol, order != null ? order.getStatus() : "NOT_FOUND", orderId, 
                order != null ? order.getExecutedQty() : "0", order != null ? order.getOrigQty() : "0");
            return order;
        } catch (BinanceApiException e) {
            log.error("[Binance API ERROR] Could not fetch status for {} #{}. Binance says: {}. (Check your API Key permissions and IP whitelist)", symbol, orderId, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("[Binance API ERROR] Unexpected error fetching status for {} #{}: {}", symbol, orderId, e.getMessage());
            return null;
        }
    }

    public List<com.bogoai.api.client.domain.account.Order> getOpenOrders() {
        try {
            log.info("[Binance API REQUEST] getOpenOrders | all symbols");
            List<com.bogoai.api.client.domain.account.Order> orders = prodBinanceApiARestClient.getOpenOrders(new com.bogoai.api.client.domain.account.request.OrderRequest(null));
            log.info("[Binance API RESPONSE] getOpenOrders | returned {} orders", orders != null ? orders.size() : 0);
            return orders != null ? orders : new ArrayList<>();
        } catch (BinanceApiException e) {
            log.error("[Binance API ERROR] Could not fetch open orders. Binance says: {}. (Check your API Key permissions and IP whitelist)", e.getMessage());
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("[Binance API ERROR] Unexpected error fetching open orders: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Record a sell for a symbol the bot currently holds.
     * Called by rules when a position hits its profit target.
     *
     * This method:
     *   1. Builds a Sell record (buy details + sell price + P&L)
     *   2. Saves it to the SELL Redis hash (so the dashboard can read it)
     *   3. Removes the symbol from the BUY hash
     *   4. Calls tradeState.markSold() so the same symbol can be bought again
     *
     * @param symbol        e.g. "SOLUSDT"
     * @param currentPrice  latest price (used as the sell price in paper mode)
     * @param ruleLabel     which rule triggered the sell (for logging)
     */
    public void trySell(String symbol, CurrentPrice currentPrice, String ruleLabel) {
        if (!tradeState.isAlreadyBought(symbol)) {
            log.debug("[{}] Skip sell {} — not in position", ruleLabel, symbol);
            return;
        }

        try {
            BigDecimal sellPrice = currentPrice.getPrice();

            // Fetch the original buy record so we can compute profit
            // (we stored it in BUY_KEY hash when we bought)
            // Build the Sell model
            Sell sell = new Sell();
            sell.setSellingPoint(sellPrice.toPlainString());
            sell.setStatus("Y");                              // Y = completed sell
            sell.setTimestamp(Timestamp.from(Instant.now()));
            sell.setSellMaxPercentage(currentPrice.getPercentage());
            sell.setSellAveragePercentage(currentPrice.getPercentage());
            sell.setSellLeastPercentage(currentPrice.getPercentage());

            // Persist sell record and clean up buy record
            repository.saveSell(SELL_KEY, symbol, sell);
            repository.deleteBuy(BUY_KEY, symbol);
            tradeState.markSold(symbol);
            tslService.reset(symbol);

            // Immediate Dust Transfer to BNB after sale
            String asset = symbol.replace("USDT", "");
            binanceDustService.sweepToBnb(asset);

            String message = String.format("%s SELL %s @ %s", ruleLabel, symbol, sellPrice);
            log.info(message);
            BookNowUtility.runNotification(message);

        } catch (Exception e) {
            log.error("[{}] Error executing sell for {}: {}", ruleLabel, symbol, e.getMessage(), e);
        }
    }


    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Place the actual buy order on Binance.
     *
     * Live mode  : LIMIT BUY at (currentPrice × (1 - limitBuyOffsetPct/100))
     *              This gives a better entry than market; the dip after a fast-move
     *              typically fills the order within seconds.
     * Paper mode : Simulate fill at the offset price (no real order placed).
     */
    private NewOrderResponse placeBuyOrder(String symbol, String qtyStr, BigDecimal currentPrice) {
        double offsetPct = configService.getLimitBuyOffsetPct();
        // Limit buy price = currentPrice × (1 - offset%)
        BigDecimal offset      = BigDecimal.valueOf(1.0 - offsetPct / 100.0);
        BigDecimal limitPrice  = currentPrice.multiply(offset);
        
        // DYNAMIC SCALING: Use tickSize from Binance
        limitPrice = filterService.roundPrice(symbol, limitPrice);
        String limitPriceStr = limitPrice.toPlainString();

        BigDecimal qty = new BigDecimal(qtyStr);
        // VALIDATION: Check min notional and filters (Throws exception if invalid)
        filterService.validateNotional(symbol, qty, limitPrice);

        NewOrderResponse response = new NewOrderResponse();
        if (liveMode) {
            log.info("LIVE limitBuy {} qty={} price={} ({}% below {})",
                symbol, qty, limitPriceStr, offsetPct, currentPrice);
            try {
                log.info("[Binance API REQUEST] newOrder (LIMIT BUY) | symbol={} quantity={} price={} timeInForce=GTC", symbol, qty, limitPriceStr);
                response = prodBinanceApiARestClient.newOrder(
                    limitBuy(symbol, GTC, qty.toPlainString(), limitPriceStr)
                );
                log.info("[Binance API RESPONSE] newOrder | symbol={} orderId={} status={} price={} origQty={} executedQty={}", 
                    symbol, response.getOrderId(), response.getStatus(), response.getPrice(), response.getOrigQty(), response.getExecutedQty());
            } catch (BinanceApiException e) {
                log.error("[Binance API ERROR] Failed to place LIMIT BUY for {}. Binance says: {}.", symbol, e.getMessage());
                log.error("--- TROUBLESHOOTING STEPS ---");
                log.error("1. Check Binance API Key Settings: Ensure 'Enable Spot & Margin Trading' is CHECKED.");
                log.error("2. IP Restrictions: If 'Restrict access to trusted IPs only' is enabled, add your IP to the whitelist.");
                log.error("3. Secret Key: Ensure the secret key in application.properties is exactly as shown on Binance (no extra spaces).");
                log.error("4. Pair Validity: Ensure the symbol (e.g., {}) is currently trading on Binance Spot.", symbol);
                log.error("-----------------------------");
                throw e;
            } catch (Exception e) {
                log.error("[Binance API ERROR] Unexpected system error during LIMIT BUY for {}: {}", symbol, e.getMessage());
                throw e;
            }
        } else {
            // Paper trade — simulate immediate fill at the limit price
            response.setPrice(limitPriceStr);
            response.setExecutedQty(qty.toPlainString());
            response.setSymbol(symbol);
            log.info("PAPER LIMIT-BUY {} qty={} price={} ({}% below market)",
                symbol, qty, limitPriceStr, configService.getLimitBuyOffsetPct());
        }
        return response;
    }


    /**
     * Calculates buy quantity so that total cost ≈ buyAmountUsdt.
     * Uses the coin's price and respects the price's own scale for precision.
     */
    private String calculateQty(String symbol, BigDecimal price) {
        BigDecimal amount = BigDecimal.valueOf(configService.getBuyAmountUsdt());
        BigDecimal qty = amount.divide(price, new MathContext(price.scale() + 4, RoundingMode.CEILING));
        
        // DYNAMIC SCALING: Use stepSize from Binance
        qty = filterService.roundQuantity(symbol, qty);
        
        return qty.toPlainString();
    }

    /**
     * Place a GTC limit-sell order to capture profit.
     *
     * Sell price = buyPrice × (1 + sellPct / 100)
     * e.g. buyPrice=100, sellPct=5 → sellPrice=105
     *
     * Always uses TimeInForce.GTC so the order stays open until filled or cancelled.
     */
    private void placeLimitSell(NewOrderResponse buyResponse, BigDecimal buyPrice, double sellPct) {
        try {
            String symbol = buyResponse.getSymbol();
            BigDecimal qtyBD = new BigDecimal(buyResponse.getExecutedQty());
            qtyBD = filterService.roundQuantity(symbol, qtyBD);
            String qty = qtyBD.toPlainString();

            BigDecimal sellPriceBD;
            double profitAmount = configService.getProfitAmountUsdt();

            if (profitAmount > 0) {
                // Calculate sell price based on fixed USDT profit amount
                // Formula: sellPrice = (cost + profit) / quantity
                BigDecimal totalCost = buyPrice.multiply(qtyBD);
                BigDecimal targetTotal = totalCost.add(BigDecimal.valueOf(profitAmount));
                sellPriceBD = targetTotal.divide(qtyBD, MathContext.DECIMAL128);
                log.info("[AmountMode] Target profit: ${}. Calculated sellPrice: {}", profitAmount, sellPriceBD);
            } else {
                // Fallback to percentage-based profit
                BigDecimal sellMult  = BigDecimal.valueOf(1.0 + sellPct / 100.0);
                sellPriceBD = buyPrice.multiply(sellMult);
                log.info("[PctMode] Target profit: {}%. Calculated sellPrice: {}", sellPct, sellPriceBD);
            }
            
            // DYNAMIC SCALING
            sellPriceBD = filterService.roundPrice(symbol, sellPriceBD);
            String sellPriceStr = sellPriceBD.toPlainString();

            log.info("LIVE limitSell {} qty={} sellPrice={} (Target: {})",
                buyResponse.getSymbol(), qty, sellPriceStr, profitAmount > 0 ? "$" + profitAmount : sellPct + "%");

            log.info("[Binance API REQUEST] newOrder (LIMIT SELL) | symbol={} quantity={} price={} timeInForce=GTC", buyResponse.getSymbol(), qty, sellPriceStr);
            NewOrderResponse sellResponse = prodBinanceApiARestClient.newOrder(
                limitSell(buyResponse.getSymbol(), GTC, qty, sellPriceStr)
            );
            log.info("[Binance API RESPONSE] newOrder | symbol={} orderId={} status={} price={} origQty={} executedQty={}", 
                sellResponse.getSymbol(), sellResponse.getOrderId(), sellResponse.getStatus(), sellResponse.getPrice(), sellResponse.getOrigQty(), sellResponse.getExecutedQty());
        } catch (Exception e) {
            log.error("Failed to place limit-sell for {}: {}", buyResponse.getSymbol(), e.getMessage(), e);
        }
    }

    /**
     * Manual limit-buy + auto limit-sell from the dashboard.
     *
     * Called by: GET /api/v1/order/limit-buy/{symbol}?offsetPct=0.3&profitPct=2.0
     *
     * Flow:
     *   1. Read current price from Redis CURRENT_PRICE hash
     *   2. Place limit-BUY  at currentPrice × (1 − offsetPct/100)  [GTC]
     *   3. Place limit-SELL at limitBuyPrice × (1 + profitPct/100) [GTC]
     *   4. Save BUY record to Redis so dashboard cards show the position
     *
     * @param symbol     e.g. "SOLUSDT"
     * @param offsetPct  how many % below current price to place the buy (default: limitBuyOffsetPct)
     * @param profitPct  desired profit % for the sell order
     */
    public NewOrderResponse tryManualLimitBuy(String symbol, CurrentPrice currentPrice,
                                   double manualQty, double offsetPct, double profitPct) {
        if (tradeState.isAlreadyBought(symbol)) {
            log.warn("[MANUAL] Skip limit-buy {} - already in position", symbol);
            return null;
        }

        if (delistService.isDelisted(symbol)) {
            log.warn("[MANUAL] CRITICAL: Skip limit-buy {} - Symbol is marked as DELISTED!", symbol);
            return null;
        }
        try {
            BigDecimal price     = currentPrice.getPrice();
            BigDecimal offset    = BigDecimal.valueOf(1.0 - offsetPct / 100.0);
            BigDecimal limitBuyPrice = price.multiply(offset);
            
            // DYNAMIC SCALING: Round price and quantity based on filters
            limitBuyPrice = filterService.roundPrice(symbol, limitBuyPrice);
            
            String qty = (manualQty > 0) 
                ? filterService.roundQuantity(symbol, new BigDecimal(manualQty)).toPlainString()
                : calculateQty(symbol, limitBuyPrice);
            
            // VALIDATION: Check min notional and filters (Throws exception if invalid)
            filterService.validateNotional(symbol, new BigDecimal(qty), limitBuyPrice);
            
            String limitBuyStr = limitBuyPrice.toPlainString();

            NewOrderResponse buyResponse = new NewOrderResponse();

            // --- Step 1: Place limit-BUY -------------------------------------------
            if (liveMode) {
                log.info("[MANUAL] LIVE limitBuy {} qty={} @ {} ({}% below {})",
                    symbol, qty, limitBuyStr, offsetPct, price);
                buyResponse = prodBinanceApiARestClient.newOrder(limitBuy(symbol, GTC, qty, limitBuyStr));
                log.info("[Binance API RESPONSE] newOrder | symbol={} orderId={} status={} price={} origQty={} executedQty={}", 
                    buyResponse.getOrderId(), buyResponse.getStatus(), buyResponse.getPrice(), buyResponse.getOrigQty(), buyResponse.getExecutedQty());
            } else {
                log.info("[MANUAL] PAPER limitBuy {} qty={} @ {} ({}% below {})",
                    symbol, qty, limitBuyStr, offsetPct, price);
                buyResponse.setSymbol(symbol);
                buyResponse.setOrderId(System.currentTimeMillis()); // Fake ID for paper
                buyResponse.setPrice(limitBuyStr);
                buyResponse.setExecutedQty(qty);
                buyResponse.setStatus(com.bogoai.api.client.domain.OrderStatus.FILLED);
            }

            // --- Step 2: Record in Redis -------------------------------------------
            recordManualBuy(buyResponse, currentPrice, profitPct);

            return buyResponse;
        } catch (Exception e) {
            log.error("[MANUAL] Limit buy failed for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * Manual market-buy from the dashboard.
     */
    public NewOrderResponse tryManualMarketBuy(String symbol, CurrentPrice currentPrice, double manualQty) {
        if (tradeState.isAlreadyBought(symbol)) {
            log.warn("[MANUAL] Skip market-buy {} - already in position", symbol);
            return null;
        }
        try {
            BigDecimal price = currentPrice.getPrice();
            String qty = filterService.roundQuantity(symbol, new BigDecimal(manualQty)).toPlainString();
            
            // Min notional check
            filterService.validateNotional(symbol, new BigDecimal(qty), price);

            NewOrderResponse buyResponse = new NewOrderResponse();
            if (liveMode) {
                log.info("[MANUAL] LIVE marketBuy {} qty={} @ ~{}", symbol, qty, price);
                buyResponse = prodBinanceApiARestClient.newOrder(marketBuy(symbol, qty));
                log.info("[Binance API RESPONSE] marketBuy | symbol={} orderId={} status={} price={} executedQty={}", 
                    buyResponse.getSymbol(), buyResponse.getOrderId(), buyResponse.getStatus(), buyResponse.getPrice(), buyResponse.getExecutedQty());
            } else {
                log.info("[MANUAL] PAPER marketBuy {} qty={} @ ~{}", symbol, qty, price);
                buyResponse.setSymbol(symbol);
                buyResponse.setOrderId(System.currentTimeMillis());
                buyResponse.setPrice(price.toPlainString());
                buyResponse.setExecutedQty(qty);
                buyResponse.setStatus(com.bogoai.api.client.domain.OrderStatus.FILLED);
            }
            
            // Record in Redis so it shows on Dashboard
            recordManualBuy(buyResponse, currentPrice, configService.getProfitPct());
            
            return buyResponse;
        } catch (Exception e) {
            log.error("[MANUAL] Market buy failed for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * Manual sell (Market) from the dashboard.
     */
    public void tryManualSell(String symbol, CurrentPrice cp, Double qty, String ruleLabel) {
        try {
            BigDecimal sellPrice = cp.getPrice();
            String qtyStr;

            if (qty != null && qty > 0) {
                qtyStr = filterService.roundQuantity(symbol, new BigDecimal(qty)).toPlainString();
            } else {
                // Fallback to what we have in Redis
                Buy buy = repository.getBuy(BUY_KEY, symbol);
                if (buy == null || buy.getExecutedQty() == null) {
                    log.error("[MANUAL SELL] No position found in Redis for {}", symbol);
                    return;
                }
                qtyStr = buy.getExecutedQty();
            }

            if (liveMode) {
                log.info("[MANUAL] LIVE marketSell {} qty={} @ ~{}", symbol, qtyStr, sellPrice);
                prodBinanceApiARestClient.newOrder(com.bogoai.api.client.domain.account.NewOrder.marketSell(symbol, qtyStr));
                
                // Sweep to BNB immediately
                binanceDustService.sweepToBnb(symbol.replace("USDT", ""));
            } else {
                log.info("[MANUAL] PAPER marketSell {} qty={} @ ~{}", symbol, qtyStr, sellPrice);
            }

            // Cleanup
            repository.deleteBuy(BUY_KEY, symbol);
            tradeState.markSold(symbol);

            // Log trade
            Sell sell = new Sell();
            sell.setSellingPoint(sellPrice.toPlainString());
            sell.setStatus("Y");
            sell.setTimestamp(Timestamp.from(Instant.now()));
            repository.saveSell(SELL_KEY, symbol, sell);

            log.info("[MANUAL SELL] Completed for {}", symbol);

        } catch (Exception e) {
            log.error("[MANUAL SELL] Failed for {}: {}", symbol, e.getMessage());
        }
    }

    private void recordManualBuy(NewOrderResponse buyResponse, CurrentPrice currentPrice, double profitPct) {
        String symbol = buyResponse.getSymbol();
        BigDecimal price = new BigDecimal(buyResponse.getPrice());
        
        Buy buy = new Buy();
        buy.setStatus(buyResponse.getStatus() != null ? buyResponse.getStatus().name() : "FILLED");
        buy.setBuyPercentage(currentPrice.getPercentage());
        buy.setBuyPrice(price);
        buy.setSelP(profitPct);
        buy.setHms(getHMS());
        buy.setOrderId(buyResponse.getOrderId());
        buy.setExecutedQty(buyResponse.getExecutedQty());
        buy.setOrigQty(buyResponse.getOrigQty());
        buy.setBuyTimeStamp(Timestamp.from(Instant.now()));
        
        repository.saveBuy(BUY_KEY, symbol, buy);
        tradeState.markBought(symbol, "MANUAL");
        tslService.startTracking(symbol, price);
    }


}
