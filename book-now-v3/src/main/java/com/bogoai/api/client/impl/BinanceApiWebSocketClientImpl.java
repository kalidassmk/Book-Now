package com.bogoai.api.client.impl;

import com.bogoai.api.client.BinanceApiCallback;
import com.bogoai.api.client.BinanceApiWebSocketClient;
import com.bogoai.api.client.config.BinanceApiConfig;
import com.bogoai.api.client.domain.general.DepthStreamInterval;
import com.bogoai.api.client.domain.market.CandlestickInterval;
import com.bogoai.api.client.domain.market.OrderBook;
import com.bogoai.api.client.domain.event.AggTradeEvent;
import com.bogoai.api.client.domain.event.BookTickerEvent;
import com.bogoai.api.client.domain.event.CandlestickEvent;
import com.bogoai.api.client.domain.event.DepthEvent;
import com.bogoai.api.client.domain.event.TickerEvent;
import com.bogoai.api.client.domain.event.UserDataUpdateEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;

import java.io.Closeable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Binance API WebSocket client implementation using OkHttp.
 */
public class BinanceApiWebSocketClientImpl implements BinanceApiWebSocketClient, Closeable {
    private final List<Integer> ALLOWED_PARTIAL_DEPTH = Arrays.asList(5, 10, 20);

    private final OkHttpClient client;

    public BinanceApiWebSocketClientImpl(OkHttpClient client) {
        this.client = client;
    }

    @Override
    public Closeable onDepthEvent(String symbols, BinanceApiCallback<DepthEvent> callback) {
        final String channel = Arrays.stream(symbols.toLowerCase().split(","))
                .map(String::trim)
                .map(s -> String.format("%s@depth", s))
                .collect(Collectors.joining("/"));
        return createNewWebSocket(channel, new BinanceApiWebSocketListener<>(callback, DepthEvent.class));
    }

    @Override
    public Closeable onPartialDepthEvent(String symbols, int depth, DepthStreamInterval interval, BinanceApiCallback<OrderBook> callback) {
        if (!ALLOWED_PARTIAL_DEPTH.contains(depth)) {
            throw new IllegalArgumentException(String.format("depth %s should be one of : [5, 10, 20]", depth));
        }

        Stream<String> stream = Arrays.stream(symbols.toLowerCase().split(","))
                .map(String::trim)
                .map(s -> String.format("%s@depth%d", s, depth));

        if (interval == DepthStreamInterval.MILLIS_100) {
            stream = stream.map(s -> s + "@100ms");
        }

        final String channel = stream.collect(Collectors.joining("/"));
        return createNewWebSocket(channel, new BinanceApiWebSocketListener<>(callback, OrderBook.class));
    }

    @Override
    public Closeable onCandlestickEvent(String symbols, CandlestickInterval interval, BinanceApiCallback<CandlestickEvent> callback) {
        final String channel = Arrays.stream(symbols.split(","))
                .map(String::trim)
                .map(s -> String.format("%s@kline_%s", s, interval.getIntervalId()))
                .collect(Collectors.joining("/"));
        return createNewWebSocket(channel, new BinanceApiWebSocketListener<>(callback, CandlestickEvent.class));
    }

    public Closeable onAggTradeEvent(String symbols, BinanceApiCallback<AggTradeEvent> callback) {
        final String channel = Arrays.stream(symbols.split(","))
                .map(String::trim)
                .map(s -> String.format("%s@aggTrade", s))
                .collect(Collectors.joining("/"));
        return createNewWebSocket(channel, new BinanceApiWebSocketListener<>(callback, AggTradeEvent.class));
    }

    public Closeable onUserDataUpdateEvent(String listenKey, BinanceApiCallback<UserDataUpdateEvent> callback) {
        return createNewWebSocket(listenKey, new BinanceApiWebSocketListener<>(callback, UserDataUpdateEvent.class));
    }

    @Override
    public Closeable onTickerEvent(String symbols, BinanceApiCallback<TickerEvent> callback) {
        final String channel = Arrays.stream(symbols.split(","))
                .map(String::trim)
                .map(s -> String.format("%s@ticker", s))
                .collect(Collectors.joining("/"));
        return createNewWebSocket(channel, new BinanceApiWebSocketListener<>(callback, TickerEvent.class));
    }

    public Closeable onAllMarketTickersEvent(BinanceApiCallback<List<TickerEvent>> callback) {
        final String channel = "!ticker@arr";
        return createNewWebSocket(channel, new BinanceApiWebSocketListener<>(callback, new TypeReference<List<TickerEvent>>() {
        }));
    }

    @Override
    public Closeable onBookTickerEvent(String symbols, BinanceApiCallback<BookTickerEvent> callback) {
        final String channel = Arrays.stream(symbols.split(","))
                .map(String::trim)
                .map(s -> String.format("%s@bookTicker", s))
                .collect(Collectors.joining("/"));
        return createNewWebSocket(channel, new BinanceApiWebSocketListener<>(callback, BookTickerEvent.class));
    }

    public Closeable onAllBookTickersEvent(BinanceApiCallback<BookTickerEvent> callback) {
        final String channel = "!bookTicker";
        return createNewWebSocket(channel, new BinanceApiWebSocketListener<>(callback, BookTickerEvent.class));
    }

    /**
     * @deprecated This method is no longer functional. Please use the returned {@link Closeable} from any of the other methods to close the web socket.
     */
    @Override
    public void close() {
    }

    private Closeable createNewWebSocket(String channel, BinanceApiWebSocketListener<?> listener) {
        String streamingUrl = String.format("%s/%s", BinanceApiConfig.getStreamApiBaseUrl(), channel);
        Request request = new Request.Builder().url(streamingUrl).build();
        final WebSocket webSocket = client.newWebSocket(request, listener);
        return () -> {
            final int code = 400;
            listener.onClosing(webSocket, code, null);
            webSocket.close(code, null);
            listener.onClosed(webSocket, code, null);
        };
    }
}
