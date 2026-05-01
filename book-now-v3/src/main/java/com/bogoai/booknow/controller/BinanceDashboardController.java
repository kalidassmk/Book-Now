package com.bogoai.booknow.controller;

import com.bogoai.api.client.BinanceApiRestClient;
import com.bogoai.api.client.domain.account.Account;
import com.bogoai.api.client.domain.account.Order;
import com.bogoai.api.client.domain.account.Trade;
import com.bogoai.api.client.domain.account.request.AllOrdersRequest;
import com.bogoai.api.client.domain.account.request.CancelOrderRequest;
import com.bogoai.api.client.domain.account.request.OrderRequest;
import com.bogoai.api.client.domain.account.NewOrder;
import com.bogoai.api.client.domain.account.NewOrderResponse;
import com.bogoai.api.client.domain.TimeInForce;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/binance")
@CrossOrigin(origins = "*")
public class BinanceDashboardController {

    private static final Logger log = LoggerFactory.getLogger(BinanceDashboardController.class);

    @Autowired
    private BinanceApiRestClient prodBinanceApiARestClient;

    /**
     * Fetch Account Information (GET /api/v3/account)
     */
    @GetMapping("/account")
    public ResponseEntity<?> getAccount() {
        try {
            Account account = prodBinanceApiARestClient.getAccount();
            return ResponseEntity.ok(account);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to fetch account info: " + e.getMessage());
        }
    }

    /**
     * Fetch Open Orders (GET /api/v3/openOrders)
     */
    @GetMapping("/open-orders")
    public ResponseEntity<?> getOpenOrders(@RequestParam(required = false) String symbol) {
        try {
            List<Order> openOrders = prodBinanceApiARestClient.getOpenOrders(new OrderRequest(symbol));
            return ResponseEntity.ok(openOrders);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to fetch open orders: " + e.getMessage());
        }
    }

    /**
     * Fetch Trade History (GET /api/v3/myTrades)
     */
    @GetMapping("/trade-history")
    public ResponseEntity<?> getTradeHistory(@RequestParam String symbol) {
        try {
            List<Trade> trades = prodBinanceApiARestClient.getMyTrades(symbol);
            return ResponseEntity.ok(trades);
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Failed to fetch trades for " + symbol + ": " + e.getMessage());
        }
    }

    /**
     * Cancel Order (DELETE /api/v3/order)
     */
    @DeleteMapping("/trade/cancel")
    public ResponseEntity<?> cancelOrder(@RequestParam String symbol, @RequestParam Long orderId) {
        try {
            prodBinanceApiARestClient.cancelOrder(new CancelOrderRequest(symbol, orderId));
            return ResponseEntity.ok(Map.of("ok", true, "symbol", symbol, "orderId", orderId));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    /**
     * Modify Order (Cancel + Replace)
     */
    @PostMapping("/trade/modify")
    public ResponseEntity<?> modifyOrder(@RequestBody Map<String, Object> req) {
        try {
            String symbol = (String) req.get("symbol");
            Long oldOrderId = Long.valueOf(req.get("orderId").toString());
            String price = req.get("price").toString();
            String quantity = req.get("quantity").toString();
            String side = req.get("side").toString();

            // 1. Cancel old
            prodBinanceApiARestClient.cancelOrder(new CancelOrderRequest(symbol, oldOrderId));

            // 2. Place new
            NewOrder newOrder = side.equalsIgnoreCase("BUY") 
                ? NewOrder.limitBuy(symbol, TimeInForce.GTC, quantity, price)
                : NewOrder.limitSell(symbol, TimeInForce.GTC, quantity, price);
            
            NewOrderResponse resp = prodBinanceApiARestClient.newOrder(newOrder);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    /**
     * Order History (GET /api/v3/allOrders)
     */
    @GetMapping("/trade/order-history")
    public ResponseEntity<?> getOrderHistory(@RequestParam String symbol) {
        try {
            List<Order> orders = prodBinanceApiARestClient.getAllOrders(new AllOrdersRequest(symbol));
            return ResponseEntity.ok(orders);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    /**
     * Trade History (GET /api/v3/myTrades)
     */
    @GetMapping("/trade/trade-history")
    public ResponseEntity<?> getTradeHistoryV2(
            @RequestParam String symbol,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime) {
        
        log.info("===============================================================");
        log.info("[BINANCE] ======= START TRADE HISTORY FETCH: {} =======", symbol);
        log.info("[BINANCE] Params: startTime={}, endTime={}", startTime, endTime);
        log.info("===============================================================");

        try {
            List<Trade> trades = prodBinanceApiARestClient.getMyTrades(symbol, 500, null, startTime, endTime, 60000L, System.currentTimeMillis());
            
            log.info("===============================================================");
            log.info("[BINANCE] ======= SUCCESS: RECEIVED {} TRADES =======", trades.size());
            log.info("===============================================================");
            
            return ResponseEntity.ok(trades);
        } catch (Exception e) {
            log.error("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
            log.error("[BINANCE] ======= ERROR FETCHING TRADES: {} =======", e.getMessage());
            log.error("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", e.getMessage()));
        }
    }

    @GetMapping("/trade/order-list")
    public ResponseEntity<?> getOrderList(
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime) {
        log.info("[OrderList] Request startTime={}, endTime={}", startTime, endTime);
        try {
            List<Object> lists = prodBinanceApiARestClient.getAllOrderList(startTime, endTime, 500);
            return ResponseEntity.ok(lists);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("ok", false, "error", e.getMessage()));
        }
    }
}
