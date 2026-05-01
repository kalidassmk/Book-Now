package com.bogoai.api.client.impl;

import com.bogoai.api.client.BinanceApiMarginRestClient;
import com.bogoai.api.client.constant.BinanceApiConstants;
import com.bogoai.api.client.domain.TransferType;
import com.bogoai.api.client.domain.account.request.CancelOrderRequest;
import com.bogoai.api.client.domain.account.request.CancelOrderResponse;
import com.bogoai.api.client.domain.account.request.OrderRequest;
import com.bogoai.api.client.domain.account.request.OrderStatusRequest;

import com.bogoai.api.client.domain.account.LoanQueryResult;
import com.bogoai.api.client.domain.account.MarginAccount;
import com.bogoai.api.client.domain.account.MarginNewOrder;
import com.bogoai.api.client.domain.account.MarginNewOrderResponse;
import com.bogoai.api.client.domain.account.MarginTransaction;
import com.bogoai.api.client.domain.account.MaxBorrowableQueryResult;
import com.bogoai.api.client.domain.account.Order;
import com.bogoai.api.client.domain.account.RepayQueryResult;
import com.bogoai.api.client.domain.account.Trade;
import java.util.List;

import static com.bogoai.api.client.impl.BinanceApiServiceGenerator.createService;

/**
 * Implementation of Binance's Margin REST API using Retrofit with asynchronous/non-blocking method calls.
 */
public class BinanceApiMarginRestClientImpl implements BinanceApiMarginRestClient {

    private final BinanceApiService binanceApiService;

    public BinanceApiMarginRestClientImpl(String apiKey, String secret) {
        binanceApiService = BinanceApiServiceGenerator.createService(BinanceApiService.class, apiKey, secret);
    }

    @Override
    public MarginAccount getAccount() {
        long timestamp = System.currentTimeMillis();
        return BinanceApiServiceGenerator.executeSync(binanceApiService.getMarginAccount(BinanceApiConstants.DEFAULT_RECEIVING_WINDOW, timestamp));
    }

    @Override
    public List<Order> getOpenOrders(OrderRequest orderRequest) {
        return BinanceApiServiceGenerator.executeSync(binanceApiService.getOpenMarginOrders(orderRequest.getSymbol(), orderRequest.getRecvWindow(),
                orderRequest.getTimestamp()));
    }

    @Override
    public MarginNewOrderResponse newOrder(MarginNewOrder order) {
        return BinanceApiServiceGenerator.executeSync(binanceApiService.newMarginOrder(order.getSymbol(), order.getSide(), order.getType(),
                order.getTimeInForce(), order.getQuantity(), order.getPrice(), order.getNewClientOrderId(), order.getStopPrice(),
                order.getIcebergQty(), order.getNewOrderRespType(), order.getSideEffectType(), order.getRecvWindow(), order.getTimestamp()));
    }

    @Override
    public CancelOrderResponse cancelOrder(CancelOrderRequest cancelOrderRequest) {
        return BinanceApiServiceGenerator.executeSync(binanceApiService.cancelMarginOrder(cancelOrderRequest.getSymbol(),
                cancelOrderRequest.getOrderId(), cancelOrderRequest.getOrigClientOrderId(), cancelOrderRequest.getNewClientOrderId(),
                cancelOrderRequest.getRecvWindow(), cancelOrderRequest.getTimestamp()));
    }

    @Override
    public Order getOrderStatus(OrderStatusRequest orderStatusRequest) {
        return BinanceApiServiceGenerator.executeSync(binanceApiService.getMarginOrderStatus(orderStatusRequest.getSymbol(),
                orderStatusRequest.getOrderId(), orderStatusRequest.getOrigClientOrderId(),
                orderStatusRequest.getRecvWindow(), orderStatusRequest.getTimestamp()));
    }

    @Override
    public List<Trade> getMyTrades(String symbol) {
        return BinanceApiServiceGenerator.executeSync(binanceApiService.getMyTrades(symbol, null, null, null, null, BinanceApiConstants.DEFAULT_RECEIVING_WINDOW, System.currentTimeMillis()));
    }

    // user stream endpoints

    @Override
    public String startUserDataStream() {
        return BinanceApiServiceGenerator.executeSync(binanceApiService.startMarginUserDataStream()).toString();
    }

    @Override
    public void keepAliveUserDataStream(String listenKey) {
        BinanceApiServiceGenerator.executeSync(binanceApiService.keepAliveMarginUserDataStream(listenKey));
    }

    @Override
    public MarginTransaction transfer(String asset, String amount, TransferType type) {
        long timestamp = System.currentTimeMillis();
        return BinanceApiServiceGenerator.executeSync(binanceApiService.transfer(asset, amount, type.getValue(), BinanceApiConstants.DEFAULT_RECEIVING_WINDOW, timestamp));
    }

    @Override
    public MarginTransaction borrow(String asset, String amount) {
        long timestamp = System.currentTimeMillis();
        return BinanceApiServiceGenerator.executeSync(binanceApiService.borrow(asset, amount, BinanceApiConstants.DEFAULT_RECEIVING_WINDOW, timestamp));
    }

    @Override
    public LoanQueryResult queryLoan(String asset, String txId) {
        long timestamp = System.currentTimeMillis();
        return BinanceApiServiceGenerator.executeSync(binanceApiService.queryLoan(asset, txId, timestamp));
    }

    @Override
    public RepayQueryResult queryRepay(String asset, String txId) {
        long timestamp = System.currentTimeMillis();
        return BinanceApiServiceGenerator.executeSync(binanceApiService.queryRepay(asset, txId, timestamp));
    }

    @Override
    public RepayQueryResult queryRepay(String asset, long startTime) {
        long timestamp = System.currentTimeMillis();
        return BinanceApiServiceGenerator.executeSync(binanceApiService.queryRepay(asset, startTime, timestamp));
    }

    @Override
    public MaxBorrowableQueryResult queryMaxBorrowable(String asset) {
        long timestamp = System.currentTimeMillis();
        return BinanceApiServiceGenerator.executeSync(binanceApiService.queryMaxBorrowable(asset, timestamp));
    }

    @Override
    public MarginTransaction repay(String asset, String amount) {
        long timestamp = System.currentTimeMillis();
        return BinanceApiServiceGenerator.executeSync(binanceApiService.repay(asset, amount, BinanceApiConstants.DEFAULT_RECEIVING_WINDOW, timestamp));
    }
}