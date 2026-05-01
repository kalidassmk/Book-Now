package com.bogoai.booknow.service;

import com.bogoai.api.client.BinanceApiRestClient;
import com.bogoai.booknow.agent.ConsensusCoordinator;
import com.bogoai.booknow.consumer.MessageConsumer;
import com.bogoai.booknow.processor.FastAnalyse;
import com.bogoai.booknow.processor.FastMoveFilter;
import com.bogoai.booknow.processor.TimeAnalyser;
import com.bogoai.booknow.processor.ULF0To3;
import com.bogoai.booknow.repository.BookNowRepository;
import com.bogoai.booknow.rules.RuleOne;
import com.bogoai.booknow.rules.RuleThree;
import com.bogoai.booknow.rules.RuleTwo;
import com.bogoai.booknow.util.TradeExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.PreDestroy;

/**
 * Orchestrates all algorithm workers.
 * Calling allRollingWindowTicker() once starts the full pipeline.
 * Calling stop() gracefully shuts all threads down.
 *
 * @PreDestroy ensures threads are stopped BEFORE Spring destroys the
 * Jedis connection pool, preventing "Pool not open" errors on shutdown.
 */
@Service
public class BookNowServiceImpl implements BookNowService {

    private static final Logger log = LoggerFactory.getLogger(BookNowServiceImpl.class);

    @Autowired private BookNowRepository      repository;
    @Autowired private BinanceApiRestClient   prodBinanceApiARestClient;
    @Autowired private TradeExecutor          tradeExecutor;
    @Autowired private ConsensusCoordinator   consensusCoordinator;

    private ExecutorService    executor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Override
    public boolean allRollingWindowTicker() {
        if (!running.compareAndSet(false, true)) {
            log.warn("BookNow pipeline is already running.");
            return false;
        }

        log.info("=== BookNow pipeline starting ===");

        // Fixed thread pool: 1 per worker (8 total)
        executor = Executors.newFixedThreadPool(8, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);     // won't block JVM shutdown
            return t;
        });

        // ── Data ingestion ────────────────────────────────────────────────────
        executor.submit(new MessageConsumer(repository));

        // ── Processors ───────────────────────────────────────────────────────
        executor.submit(new ULF0To3(repository));
        executor.submit(new FastAnalyse(repository));
        executor.submit(new TimeAnalyser(repository));
        executor.submit(new FastMoveFilter(repository));

        // ── Trading rules (AI Consensus Enabled) ──────────────────────────────
        executor.submit(new RuleOne(repository, consensusCoordinator));
        executor.submit(new RuleTwo(repository, consensusCoordinator));
        executor.submit(new RuleThree(repository, consensusCoordinator));

        log.info("=== All 8 workers submitted ===");
        return true;
    }

    /**
     * Gracefully stop all workers.
     * @PreDestroy ensures this runs BEFORE Spring destroys Redis beans.
     */
    @PreDestroy
    public void stop() {
        if (running.compareAndSet(true, false) && executor != null) {
            log.info("=== BookNow pipeline stopping — interrupting threads ===");
            executor.shutdownNow();
            try {
                // Wait up to 5 seconds for threads to finish current Redis ops
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Some threads did not stop in time — forcing shutdown");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            log.info("=== BookNow pipeline stopped ===");
        }
    }
}
