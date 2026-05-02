package com.bogoai.booknow.controller;

import com.bogoai.booknow.model.CoinAnalysisResult;
import com.bogoai.booknow.model.CurrentPrice;
import com.bogoai.booknow.repository.BookNowRepository;
import com.bogoai.booknow.service.BookNowService;
import com.bogoai.booknow.service.BookNowServiceImpl;
import com.bogoai.booknow.util.CoinAnalyzer;
import com.bogoai.booknow.util.TradeExecutor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static com.bogoai.booknow.util.Constant.CURRENT_PRICE;


/**
 * REST controller to start / stop the trading pipeline.
 *
 *   GET  /api/v1/start          → starts all workers
 *   GET  /api/v1/stop           → gracefully shuts all workers down
 *   GET  /api/v1/health         → quick ping
 *   GET  /api/v1/sell/{symbol}  → manual sell a bot position from the dashboard
 */
@RestController
@RequestMapping("/api/v1")
public class BookNowController {

    @Autowired private BookNowService     bookNowService;
    @Autowired private BookNowServiceImpl bookNowServiceImpl;  // for stop()
    @Autowired private BookNowRepository  repository;
    @Autowired private TradeExecutor      tradeExecutor;
    @Autowired private CoinAnalyzer       coinAnalyzer;


    @GetMapping("/start")
    public ResponseEntity<String> start() {
        boolean started = bookNowService.allRollingWindowTicker();
        return started
            ? ResponseEntity.ok("Pipeline started successfully.")
            : ResponseEntity.badRequest().body("Pipeline already running.");
    }

    @GetMapping("/stop")
    public ResponseEntity<String> stop() {
        bookNowServiceImpl.stop();
        return ResponseEntity.ok("Pipeline stopped.");
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("BookNow is up.");
    }

    /**
     * Manual sell from the dashboard.
     * Reads the live price from Redis, then delegates to TradeExecutor.trySell()
     *
     * @param symbol  e.g. "SOLUSDT"
     * @param qty     optional quantity to sell
     */
    @GetMapping("/sell/{symbol}")
    public ResponseEntity<String> manualSell(
            @PathVariable String symbol,
            @RequestParam(required = false) Double qty) {
        Map<String, CurrentPrice> prices = repository.getAllCurrentPrice(CURRENT_PRICE);
        CurrentPrice cp = prices.get(symbol);
        if (cp == null) {
            return ResponseEntity.badRequest()
                .body("No live price found for " + symbol + ". Is the pipeline running?");
        }
        tradeExecutor.tryManualSell(symbol, cp, qty, "MANUAL_DASHBOARD");
        return ResponseEntity.ok("Sell executed for " + symbol + " @ " + cp.getPrice());
    }

    /**
     * Manual market-buy from the dashboard.
     *
     * @param symbol  coin pair, e.g. "SOLUSDT"
     * @param qty     quantity to buy
     */
    @GetMapping("/order/buy/{symbol}")
    public ResponseEntity<?> manualMarketBuy(
            @PathVariable String symbol,
            @RequestParam double qty) {

        Map<String, CurrentPrice> prices = repository.getAllCurrentPrice(CURRENT_PRICE);
        CurrentPrice cp = prices.get(symbol);
        if (cp == null) {
            return ResponseEntity.badRequest()
                .body("No live price for " + symbol + ". Is the pipeline running?");
        }
        com.bogoai.api.client.domain.account.NewOrderResponse resp = tradeExecutor.tryManualMarketBuy(symbol, cp, qty);
        if (resp == null) {
            return ResponseEntity.badRequest().body("Failed to place market order.");
        }
        return ResponseEntity.ok(resp);
    }

    /**
     * Manual limit-buy + immediate limit-sell from the dashboard.
     *
     * @param symbol    coin pair, e.g. "SOLUSDT"
     * @param qty       quantity to buy
     * @param offsetPct % below current price for the buy order
     * @param profitPct % above buy price for the sell order
     */
    @GetMapping("/order/limit-buy/{symbol}")
    public ResponseEntity<?> manualLimitBuy(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "0") double qty,
            @RequestParam(defaultValue = "0.3") double offsetPct,
            @RequestParam(defaultValue = "2.0") double profitPct) {

        Map<String, CurrentPrice> prices = repository.getAllCurrentPrice(CURRENT_PRICE);
        CurrentPrice cp = prices.get(symbol);
        if (cp == null) {
            return ResponseEntity.badRequest()
                .body("No live price for " + symbol + ". Is the pipeline running?");
        }
        com.bogoai.api.client.domain.account.NewOrderResponse resp = tradeExecutor.tryManualLimitBuy(symbol, cp, qty, offsetPct, profitPct);
        if (resp == null) {
            return ResponseEntity.badRequest().body("Failed to place limit order.");
        }
        return ResponseEntity.ok(resp);
    }

    /**
     * 2-month coin analysis endpoint.
     * Returns full JSON with price history stats, volume metrics, trend, and buy recommendation.
     *
     * Example: GET /api/v1/analyze/SOLUSDT
     */
    @GetMapping("/analyze/{symbol}")
    public ResponseEntity<CoinAnalysisResult> analyze(@PathVariable String symbol) {
        Map<String, CurrentPrice> prices = repository.getAllCurrentPrice(CURRENT_PRICE);
        CurrentPrice cp = prices.get(symbol);
        double price = cp != null ? cp.getPrice().doubleValue() : 0.0;
        CoinAnalysisResult result = coinAnalyzer.analyze(symbol, price);
        return ResponseEntity.ok(result);
    }

    /**
     * Fetch order status from Binance.
     * GET /api/v1/order/status/SOLUSDT/12345
     */
    @GetMapping("/order/status/{symbol}/{orderId}")
    public ResponseEntity<com.bogoai.api.client.domain.account.Order> getOrderStatus(
            @PathVariable String symbol,
            @PathVariable Long orderId) {
        com.bogoai.api.client.domain.account.Order order = tradeExecutor.getOrderStatus(symbol, orderId);
        return order != null ? ResponseEntity.ok(order) : ResponseEntity.notFound().build();
    }

    /**
     * Cancel an open order.
     * GET /api/v1/order/cancel/SOLUSDT/12345
     */
    @GetMapping("/order/cancel/{symbol}/{orderId}")
    public ResponseEntity<String> cancelOrder(
            @PathVariable String symbol,
            @PathVariable Long orderId) {
        try {
            tradeExecutor.cancelOrder(symbol, orderId);
            return ResponseEntity.ok("Order cancelled successfully.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Cancel failed: " + e.getMessage());
        }
    }

    /**
     * Get all open orders from Binance.
     * GET /api/v1/orders/open
     */
    @GetMapping("/orders/open")
    public ResponseEntity<?> getOpenOrders() {
        try {
            List<com.bogoai.api.client.domain.account.Order> orders = tradeExecutor.getOpenOrders();
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to fetch open orders: " + e.getMessage());
        }
    }
}
