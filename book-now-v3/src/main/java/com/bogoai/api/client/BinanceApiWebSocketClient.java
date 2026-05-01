package com.bogoai.api.client;

import com.bogoai.api.client.domain.general.DepthStreamInterval;
import com.bogoai.api.client.domain.market.CandlestickInterval;
import com.bogoai.api.client.domain.market.OrderBook;

import com.bogoai.api.client.domain.event.AggTradeEvent;
import com.bogoai.api.client.domain.event.BookTickerEvent;
import com.bogoai.api.client.domain.event.CandlestickEvent;
import com.bogoai.api.client.domain.event.DepthEvent;
import com.bogoai.api.client.domain.event.TickerEvent;
import com.bogoai.api.client.domain.event.UserDataUpdateEvent;
import java.io.Closeable;
import java.util.List;

/**
 * Binance API data streaming facade, supporting streaming of events through web sockets.
 */
public interface BinanceApiWebSocketClient extends Closeable {

    /**
     * Open a new web socket to receive {@link com.bogoai.api.client.domain.event.DepthEvent depthEvents} on a callback.
     *
     * @param symbols  market (one or coma-separated) symbol(s) to subscribe to
     * @param callback the callback to call on new events
     * @return a {@link Closeable} that allows the underlying web socket to be closed.
     */
    Closeable onDepthEvent(String symbols, BinanceApiCallback<DepthEvent> callback);

    Closeable onPartialDepthEvent(String symbol, int depth, DepthStreamInterval interval, BinanceApiCallback<OrderBook> callback);

    /**
     * Open a new web socket to receive {@link com.bogoai.api.client.domain.event.CandlestickEvent candlestickEvents} on a callback.
     *
     * @param symbols  market (one or coma-separated) symbol(s) to subscribe to
     * @param interval the interval of the candles tick events required
     * @param callback the callback to call on new events
     * @return a {@link Closeable} that allows the underlying web socket to be closed.
     */
    Closeable onCandlestickEvent(String symbols, CandlestickInterval interval, BinanceApiCallback<CandlestickEvent> callback);

    /**
     * Open a new web socket to receive {@link com.bogoai.api.client.domain.event.AggTradeEvent aggTradeEvents} on a callback.
     *
     * @param symbols  market (one or coma-separated) symbol(s) to subscribe to
     * @param callback the callback to call on new events
     * @return a {@link Closeable} that allows the underlying web socket to be closed.
     */
    Closeable onAggTradeEvent(String symbols, BinanceApiCallback<AggTradeEvent> callback);

    /**
     * Open a new web socket to receive {@link com.bogoai.api.client.domain.event.UserDataUpdateEvent userDataUpdateEvents} on a callback.
     *
     * @param listenKey the listen key to subscribe to.
     * @param callback  the callback to call on new events
     * @return a {@link Closeable} that allows the underlying web socket to be closed.
     */
    Closeable onUserDataUpdateEvent(String listenKey, BinanceApiCallback<UserDataUpdateEvent> callback);

    /**
     * Open a new web socket to receive {@link com.bogoai.api.client.domain.event.TickerEvent tickerEvents} on a callback.
     *
     * @param symbols  market (one or coma-separated) symbol(s) to subscribe to
     * @param callback the callback to call on new events
     * @return a {@link Closeable} that allows the underlying web socket to be closed.
     */
    Closeable onTickerEvent(String symbols, BinanceApiCallback<TickerEvent> callback);

    /**
     * Open a new web socket to receive {@link List<TickerEvent> allMarketTickersEvents} on a callback.
     *
     * @param callback the callback to call on new events
     * @return a {@link Closeable} that allows the underlying web socket to be closed.
     */
    Closeable onAllMarketTickersEvent(BinanceApiCallback<List<TickerEvent>> callback);

    /**
     * Open a new web socket to receive {@link com.bogoai.api.client.domain.event.BookTickerEvent bookTickerEvents} on a callback.
     *
     * @param symbols  market (one or coma-separated) symbol(s) to subscribe to
     * @param callback the callback to call on new events
     * @return a {@link Closeable} that allows the underlying web socket to be closed.
     */
    Closeable onBookTickerEvent(String symbols, BinanceApiCallback<BookTickerEvent> callback);

    /**
     * Open a new web socket to receive {@link TickerEvent allBookTickersEvents} on a callback.
     *
     * @param callback the callback to call on new events
     * @return a {@link Closeable} that allows the underlying web socket to be closed.
     */
    Closeable onAllBookTickersEvent(BinanceApiCallback<BookTickerEvent> callback);

    /**
     * @deprecated This method is no longer functional. Please use the returned {@link Closeable} from any of the other methods to close the web socket.
     */
    @Deprecated
    void close();
}
