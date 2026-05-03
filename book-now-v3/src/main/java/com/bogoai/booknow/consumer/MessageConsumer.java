package com.bogoai.booknow.consumer;

import com.bogoai.booknow.model.CurrentPrice;
import com.bogoai.booknow.model.FastMove;
import com.bogoai.booknow.model.Percentage;
import com.bogoai.booknow.model.PreviousPercentage;
import com.bogoai.booknow.model.WatchList;
import com.bogoai.booknow.repository.BookNowRepository;
import com.bogoai.booknow.response.RollingWindowTicker1HResponse;
import com.bogoai.booknow.websocket.client.impl.WebsocketClientImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.bogoai.booknow.util.BookNowUtility.*;
import static com.bogoai.booknow.util.Constant.*;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

/**
 * Connects to the Binance allRollingWindowTicker WebSocket (1h window).
 *
 * On every market event it:
 *  1. Filters to USDT pairs, excluding delisted coins.
 *  2. Calculates base→current % gain since first observation.
 *  3. Buckets each coin into a Redis percentage band.
 *  4. Updates the FAST_MOVE momentum score per coin.
 *  5. Records the first time a coin enters each band (for TimeAnalyser).
 */
public class MessageConsumer implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(MessageConsumer.class);
    private static final int    USDT_SUFFIX_LEN = 4;   // "USDT"
    private static final String USDT            = "USDT";

    private final BookNowRepository repository;
    private final ObjectMapper       mapper;
    private final Map<String, String> delistMap;

    /** Tracks which symbols have already crossed each band (one-log-per-band). */
    private final Set<String> bandCrossed = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** Local copy of the baseline snapshot to avoid round-tripping Redis every tick. */
    private Map<String, RollingWindowTicker1HResponse> baselineCache;

    /**
     * Set true when {@link #close()} is invoked (typically from
     * BookNowServiceImpl.@PreDestroy). Once true the run loop exits without
     * reconnecting and onMarketEvent silently drops further events instead
     * of dumping a Redis stack trace per tick — the OkHttp dispatcher may
     * still be flushing buffered messages while the JedisPool tears down.
     */
    private volatile boolean shuttingDown = false;

    /** WebSocket bookkeeping so close() can hang up cleanly. */
    private volatile WebsocketClientImpl wsClient;
    private volatile int                 wsConnectionId = -1;

    public MessageConsumer(BookNowRepository repository) {
        this.repository = repository;
        this.mapper     = new ObjectMapper().configure(FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.delistMap  = deListCoins();
    }

    /**
     * Stop the Binance WebSocket cleanly. Callable from any thread.
     *
     * Must run BEFORE Spring tears the Redis bean down — otherwise the
     * OkHttp reader keeps delivering market events into a closed
     * JedisPool and floods the log with "Pool not open" stack traces.
     */
    public void close() {
        shuttingDown = true;
        WebsocketClientImpl client = this.wsClient;
        int id = this.wsConnectionId;
        if (client != null && id >= 0) {
            try {
                client.closeConnection(id);
                log.info("[WS] MessageConsumer close() — Binance WS closed (id={})", id);
            } catch (Exception e) {
                log.warn("[WS] MessageConsumer close() failed cleanly: {}", e.getMessage());
            }
        }
    }

    @Override
    public void run() {
        log.info("=== MessageConsumer starting WebSocket lifecycle ===");
        baselineCache = repository.getAllRWBasePrice(RW_BASE_PRICE);

        while (!Thread.currentThread().isInterrupted() && !shuttingDown) {
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);

            try {
                log.info("[WS] Attempting to connect to Binance allRollingWindowTicker (1h)...");
                WebsocketClientImpl client = new WebsocketClientImpl();

                int connectionId = client.allRollingWindowTicker(
                    "1h",
                    open -> log.info("[WS] Connection Established ✅"),
                    this::onMarketEvent,
                    close -> {
                        log.warn("[WS] Connection Closing: {}", close);
                        latch.countDown();
                    },
                    fail -> {
                        if (shuttingDown) {
                            log.info("[WS] Connection ended during shutdown");
                        } else {
                            log.error("[WS] Connection Failed/Reset ❌");
                        }
                        latch.countDown();
                    }
                );
                this.wsClient        = client;
                this.wsConnectionId  = connectionId;

                // Block this thread until the connection closes or fails
                latch.await();
                this.wsClient        = null;
                this.wsConnectionId  = -1;

                if (shuttingDown) {
                    log.info("[WS] Shutdown requested — exiting reconnect loop");
                    break;
                }

                log.info("[WS] Connection lost. Reconnecting in 5 seconds...");
                Thread.sleep(5000);

            } catch (InterruptedException e) {
                log.info("[WS] MessageConsumer interrupted. Shutting down.");
                break;
            } catch (Exception e) {
                if (shuttingDown) {
                    log.debug("[WS] Connection error during shutdown: {}", e.getMessage());
                    break;
                }
                log.error("[WS] Unexpected error in connection loop: {}", e.getMessage());
                try { Thread.sleep(5000); } catch (InterruptedException ie) { break; }
            }
        }
    }

    // ── WebSocket event handler ───────────────────────────────────────────────

    private void onMarketEvent(String rawJson) {
        // Drop events that race in after close() — Spring may already have
        // torn the Redis bean down, so any repository read would explode
        // with "Pool not open" and flood the log with stack traces.
        if (shuttingDown) {
            return;
        }
        try {
            List<RollingWindowTicker1HResponse> tickers = parseAndFilter(rawJson);
            Map<String, CurrentPrice>           priceMap    = repository.getAllCurrentPrice(CURRENT_PRICE);
            Map<String, FastMove>               fastMoveMap = repository.getFastMoveEntry(FAST_MOVE);

            for (RollingWindowTicker1HResponse ticker : tickers) {
                processTicker(ticker, priceMap, fastMoveMap);
            }
        } catch (org.springframework.data.redis.RedisConnectionFailureException e) {
            // Pool dying mid-stream: log a single line, no stack trace,
            // no per-event spam. The reconnect path will recover or
            // shutdown will complete shortly.
            log.warn("[WS] Skip market event — Redis pool unavailable ({})", e.getMessage());
        } catch (Exception e) {
            log.error("Error processing market event: {}", e.getMessage(), e);
        }
    }

    // ── Per-ticker processing ─────────────────────────────────────────────────

    private void processTicker(RollingWindowTicker1HResponse ticker,
                               Map<String, CurrentPrice> priceMap,
                               Map<String, FastMove> fastMoveMap) {
        String     symbol     = ticker.getSymbol();
        double     curPct     = ticker.getPercentage();
        BigDecimal curPrice   = ticker.getPrice();
        Timestamp  curTs      = ticker.getTimestamp();

        // ── 1. Momentum delta (tick-to-tick change) ───────────────────────────
        double prevPct        = curPct;
        BigDecimal prevPrice  = curPrice;
        if (priceMap.containsKey(symbol)) {
            CurrentPrice prev = priceMap.get(symbol);
            prevPct   = prev.getPercentage();
            prevPrice = prev.getPrice();
        }
        double tickDelta      = getPercentage(prevPct, curPct);
        double momentumScore  = tickDelta >= 0
            ? getMomentumScorePositive(tickDelta)
            : getMomentumScoreNegative(tickDelta);

        // ── 2. Update current price in Redis ──────────────────────────────────
        repository.saveCurrentPrice(CURRENT_PRICE, symbol,
            new CurrentPrice(symbol, curPct, curPrice, curTs, getHMS(), 0.0));

        // ── 3. Initialise baseline if first time seeing this symbol ───────────
        if (!baselineCache.containsKey(symbol)) {
            repository.saveTemplateRWTicker1HResponse(RW_BASE_PRICE, symbol, ticker);
            baselineCache.put(symbol, ticker);
        }

        // ── 4. Compute base→current gain ──────────────────────────────────────
        RollingWindowTicker1HResponse baseline   = baselineCache.get(symbol);
        double     basePct        = baseline.getPercentage();
        BigDecimal basePrice      = baseline.getPrice();
        Timestamp  baseTs         = baseline.getTimestamp();

        double     gainPct        = getPercentage(basePct, curPct);
        BigDecimal gainPrice      = getPrice(basePrice, curPrice);
        double     intervalMins   = (curTs.getTime() - baseTs.getTime()) / 60_000.0;

        // ── 5. Update WatchList (all coins) ───────────────────────────────────
        WatchList watchList = new WatchList(symbol, basePct, curPct, gainPct,
            basePrice, curPrice, gainPrice, 0, curTs, getHMS());
        repository.saveWatchList(WATCH_ALL, symbol, watchList);

        // ── 6. Bucket the coin & update FastMove score ────────────────────────
        FastMove fastMove = fastMoveMap.getOrDefault(symbol, new FastMove());
        fastMove.setSymbol(symbol);

        String bucket = getBucket(gainPct, fastMove, momentumScore);
        if (bucket == null) {
            return; // below minimum threshold — nothing to record
        }

        repository.saveFastMove(FAST_MOVE, symbol, fastMove);

        // ── 7. Record first band-crossing (one-time per symbol per band) ──────
        String bandKey = symbol + "::" + bucket;
        if (!bandCrossed.contains(bandKey)) {
            bandCrossed.add(bandKey);

            String watchKey = WATCH_PREFIX + bucket + WATCH_SUFFIX;
            repository.saveWatchList(watchKey, symbol, watchList);
            log.debug("{} first crossed bucket {} at {} | gain={:.2f}%",
                symbol, bucket, getHMS(), gainPct);
        }

        // ── 8. Save Percentage detail (with history) ──────────────────────────
        PreviousPercentage prevEntry = new PreviousPercentage(
            prevPct, curPct, tickDelta,
            prevPrice, curPrice, gainPrice, curTs);

        List<PreviousPercentage> history = new ArrayList<>();
        history.add(prevEntry);

        Percentage percentage = new Percentage(
            symbol, basePct, curPct, gainPct,
            basePrice, curPrice, gainPrice,
            baseTs, curTs, intervalMins,
            history, getHMS());

        repository.savePercentage(bucket, symbol, percentage);
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    private List<RollingWindowTicker1HResponse> parseAndFilter(String json) throws Exception {
        return mapper.readValue(json, new TypeReference<List<RollingWindowTicker1HResponse>>() {})
            .stream()
            .filter(this::isValidUsdtPair)
            .sorted(comparing(RollingWindowTicker1HResponse::getPercentage).reversed())
            .collect(toList());
    }

    private boolean isValidUsdtPair(RollingWindowTicker1HResponse r) {
        String sym = r.getSymbol();
        if (sym == null || sym.length() <= USDT_SUFFIX_LEN) return false;
        String suffix = sym.substring(sym.length() - USDT_SUFFIX_LEN);
        return USDT.equals(suffix) && !delistMap.containsKey(sym);
    }
}
