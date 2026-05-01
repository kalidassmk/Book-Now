package com.bogoai.booknow.repository;

import com.bogoai.booknow.model.Buy;
import com.bogoai.booknow.model.CurrentPrice;
import com.bogoai.booknow.model.FastMove;
import com.bogoai.booknow.model.HLA;
import com.bogoai.booknow.model.Percentage;
import com.bogoai.booknow.model.R;
import com.bogoai.booknow.model.Sell;
import com.bogoai.booknow.model.ShortestTime;
import com.bogoai.booknow.model.TimeAnalyse;
import com.bogoai.booknow.model.WatchList;
import com.bogoai.booknow.response.RollingWindowTicker1HResponse;
import java.util.List;
import java.util.Map;

public interface BookNowRepository {

    public void saveCurrentPrice(String key, String symbol, CurrentPrice obj);
    public void saveAllCurrentPrice(String key, Map<String, CurrentPrice> map);

    public Map<String, CurrentPrice> getAllCurrentPrice(String key);

    public void savePercentage(String key, String symbol, Percentage obj);

    public Map<String, Percentage> getAllEntryPercentage(String key);

    public void updateHLA(String key, String symbol, HLA obj);

    public HLA getHLA(String key, String symbol);

    public void saveHLA(String key, String symbol, HLA obj);

    public Map<String, HLA> getAllEntryHLA(String key);

    public Map<String, RollingWindowTicker1HResponse> getAllRWBasePrice(String key);

    public void saveTemplateRWTicker1HResponse(String key, String symbol, RollingWindowTicker1HResponse obj);

    public void saveWatchList(String key, String symbol, WatchList obj);
    public void saveAllWatchList(String key, Map<String, WatchList> map);

    public void deleteWatchList(String key, String symbol);

    public Map<String, WatchList> getEntryWatchList(String key);

    public boolean getUltraFast(String symbol);

    public boolean getSuperFast(String symbol);

    public boolean getUltraSuperFast(String symbol);

    public void saveAllShortestTime(String key, String symbol, TimeAnalyse obj);

    public void saveFastMove(String key, String symbol, FastMove obj);
    public void saveAllFastMove(String key, Map<String, FastMove> map);

    public FastMove getFastMove(String key, String symbol);

    public Map<String, FastMove> getFastMoveEntry(String key);

    public TimeAnalyse getShortestTime(String key, String symbol);

    public void saveRules(String key, String symbol, R obj);

    public void deleteFastMove(String key);

    public void saveBuy(String key, String symbol, Buy obj);

    /** Persist a sell record to the SELL Redis hash (symbol → SellRecord JSON). */
    public void saveSell(String key, String symbol, Sell obj);

    /** Remove a symbol from the BUY hash once it has been sold. */
    public void deleteBuy(String key, String symbol);

    /** Fetch news analysis JSON for a coin from Redis. */
    public String getNewsAnalysis(String coin);

    /** Fetch behavioral sentiment JSON from Python engine. Key: sentiment:market:adaptive:{SYMBOL} */
    public String getBehavioralSentiment(String symbol);

    /** Fetch volume-price score JSON from Python engine. Hash: VOLUME_SCORE, Field: {SYMBOL}USDT */
    public String getVolumeScore(String symbol);

    /** Fetch dashboard analysis score JSON from Node.js. Hash: DASHBOARD_SCORE, Field: {SYMBOL} */
    public String getDashboardScore(String symbol);

    /** Fetch final consensus JSON from Python Master Engine. Hash: FINAL_CONSENSUS_STATE, Field: {SYMBOL} */
    public String getFinalConsensus(String symbol);
}
