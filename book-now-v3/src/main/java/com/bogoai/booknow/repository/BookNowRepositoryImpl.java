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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BookNowRepositoryImpl implements BookNowRepository {

    private static final Logger log = LoggerFactory.getLogger(BookNowRepositoryImpl.class);

    @Autowired
    RedisTemplate redisTemplatePercentage;


    @Autowired
    RedisTemplate redisTemplateRWTicker1HResponse;

    @Autowired
    RedisTemplate redisTemplateHLA;

    @Autowired
    RedisTemplate redisTemplateCurrentPrice;

    @Autowired
    RedisTemplate redisTemplateWatchList;


    @Autowired
    RedisTemplate redisTemplateShortestTime;

    @Autowired
    RedisTemplate redisTemplateFastMove;

    @Autowired
    RedisTemplate redisTemplateRule;


    @Autowired
    RedisTemplate redisTemplateBuy;

    @Autowired
    RedisTemplate redisTemplateSell;
    
    @Autowired
    RedisTemplate<String, String> redisTemplateString;


    @Autowired
    public void setRedisTemplate() {
    }


    // new Student(a,a,d,getMarks())
    // Marks getMark(double tamil, double e){
    // double p = tamil+e/2
    // new Mara
    // }

    // new Marks(80, 90, (80+90)/2)

    @Override
    public void saveCurrentPrice(String key, String symbol, CurrentPrice obj) {
        redisTemplateCurrentPrice.opsForHash().put(key, symbol, obj);
    }

    @Override
    public void saveAllCurrentPrice(String key, Map<String, CurrentPrice> map) {
        if (map == null || map.isEmpty()) return;
        redisTemplateCurrentPrice.opsForHash().putAll(key, map);
    }

    @Override
    public void savePercentage(String key, String symbol, Percentage obj) {
        String lookupKey = "PREV_CURRENT_INC_%".equals(key) ? "PREV_CURRENT_INC_%" : key;
        Percentage model = (Percentage) redisTemplatePercentage.opsForHash().get(lookupKey, symbol);

        // Always ensure obj has a mutable list before we try to add to it
        if (obj.getPreviousCountList() == null) {
            obj.setPreviousCountList(new ArrayList<>());
        } else {
            // Wrap in ArrayList in case it came in as an immutable list (e.g. List.of)
            obj.setPreviousCountList(new ArrayList<>(obj.getPreviousCountList()));
        }

        if (model != null && model.getPreviousCountList() != null) {
            obj.getPreviousCountList().addAll(model.getPreviousCountList());
        }
        redisTemplatePercentage.opsForHash().put(key, symbol, obj);
    }

    @Override
    public void saveTemplateRWTicker1HResponse(String key, String symbol, RollingWindowTicker1HResponse obj) {
        redisTemplateRWTicker1HResponse.opsForHash().put(key, symbol, obj);
    }

    @Override
    public boolean getSuperFast(String symbol) {
        return !redisTemplatePercentage.opsForHash().hasKey(">0<1", symbol)
                && !redisTemplatePercentage.opsForHash().hasKey(">1<2", symbol);

    }

    @Override
    public boolean getUltraFast(String symbol) {
        return !redisTemplatePercentage.opsForHash().hasKey(">1<2", symbol)
                && !redisTemplatePercentage.opsForHash().hasKey(">2<3", symbol);
    }

    @Override
    public boolean getUltraSuperFast(String symbol) {
        return !redisTemplatePercentage.opsForHash().hasKey(">2<3", symbol)
                && !redisTemplatePercentage.opsForHash().hasKey(">3<5", symbol);
    }


    @Override
    public Map<String, Percentage> getAllEntryPercentage(String key) {
        Map<String, Percentage> map = redisTemplatePercentage.opsForHash().entries(key);
        return map;
    }

    @Override
    public Map<String, CurrentPrice> getAllCurrentPrice(String key) {
        return redisTemplateCurrentPrice.opsForHash().entries(key);

    }

    @Override
    public Map<String, RollingWindowTicker1HResponse> getAllRWBasePrice(String key) {
        return redisTemplateRWTicker1HResponse.opsForHash().entries(key);

    }

    @Override
    public Map<String, HLA> getAllEntryHLA(String key) {
        return redisTemplateHLA.opsForHash().entries(key);
    }

    @Override
    public HLA getHLA(String key, String symbol) {
        return (HLA) redisTemplateHLA.opsForHash().get(key, symbol);
    }

    @Override
    public void saveHLA(String key, String symbol, HLA obj) {
        redisTemplateHLA.opsForHash().put(key, symbol, obj);
    }

    @Override
    public void updateHLA(String key, String symbol, HLA obj) {
        redisTemplateHLA.opsForHash().delete(key, symbol);
        redisTemplateHLA.opsForHash().put(key, symbol, obj);
    }

    @Override
    public void saveWatchList(String key, String symbol, WatchList obj) {
        redisTemplateWatchList.opsForHash().put(key, symbol, obj);
    }

    @Override
    public void saveAllWatchList(String key, Map<String, WatchList> map) {
        if (map == null || map.isEmpty()) return;
        redisTemplateWatchList.opsForHash().putAll(key, map);
    }

    @Override
    public void saveAllShortestTime(String key, String symbol, TimeAnalyse obj) {
        try {
            // Always ensure a mutable list before calling addAll
            if (obj.getShortestTimeList() == null) {
                obj.setShortestTimeList(new ArrayList<>());
            } else {
                obj.setShortestTimeList(new ArrayList<>(obj.getShortestTimeList()));
            }

            TimeAnalyse stored = (TimeAnalyse) redisTemplateShortestTime.opsForHash().get(key, symbol);
            if (stored != null && stored.getShortestTimeList() != null) {
                obj.getShortestTimeList().addAll(stored.getShortestTimeList());
                obj.getShortestTimeList().sort(
                    Comparator.comparing(ShortestTime::getTimeTook,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
            }
            redisTemplateShortestTime.opsForHash().put(key, symbol, obj);
        } catch (Exception e) {
            log.error("saveAllShortestTime error [{}:{}]: {}", key, symbol, e.getMessage());
        }
    }


    @Override
    public Map<String, WatchList> getEntryWatchList(String key) {
        Map<String, WatchList> map = redisTemplateWatchList.opsForHash().entries(key);
        return map;
    }

    @Override
    public void deleteWatchList(String key, String symbol) {
        redisTemplateWatchList.opsForHash().delete(key, symbol);
    }

    @Override
    public void saveFastMove(String key, String symbol, FastMove obj) {
        redisTemplateFastMove.opsForHash().put(key, symbol, obj);
    }

    @Override
    public void saveAllFastMove(String key, Map<String, FastMove> map) {
        if (map == null || map.isEmpty()) return;
        redisTemplateFastMove.opsForHash().putAll(key, map);
    }

    @Override
    public void deleteFastMove(String key) {
        try {
            Boolean bl = redisTemplateFastMove.hasKey(key);
            if (bl) {
                redisTemplateFastMove.delete(key);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public FastMove getFastMove(String key, String symbol) {
        return (FastMove) redisTemplateFastMove.opsForHash().get(key, symbol);
    }

    @Override
    public Map<String, FastMove> getFastMoveEntry(String key) {
        Map<String, FastMove> map = redisTemplateFastMove.opsForHash().entries(key);
        return map;
    }


    @Override
    public TimeAnalyse getShortestTime(String key, String symbol) {
        try {
            return (TimeAnalyse) redisTemplateShortestTime.opsForHash().get(key, symbol);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new TimeAnalyse();
    }


    @Override
    public void saveRules(String key, String symbol, R obj) {
        redisTemplateRule.opsForHash().put(key, symbol, obj);
    }

    @Override
    public void saveBuy(String key, String symbol, Buy obj) {
        redisTemplateBuy.opsForHash().put(key, symbol, obj);
    }

    @Override
    public void saveSell(String key, String symbol, Sell obj) {
        redisTemplateSell.opsForHash().put(key, symbol, obj);
    }

    @Override
    public void deleteBuy(String key, String symbol) {
        redisTemplateBuy.opsForHash().delete(key, symbol);
    }

    @Override
    public String getNewsAnalysis(String coin) {
        try {
            return redisTemplateString.opsForValue().get("analysis:" + coin + ":detailed");
        } catch (Exception e) {
            log.error("Failed to get news analysis for {}: {}", coin, e.getMessage());
            return null;
        }
    }

    @Override
    public String getBehavioralSentiment(String symbol) {
        try {
            // Python writes: sentiment:market:adaptive:BTC/USDT
            // Spring symbol format: BTCUSDT → convert to BTC/USDT
            String coin = symbol.replace("USDT", "");
            String key = "sentiment:market:adaptive:" + coin + "/USDT";
            return redisTemplateString.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Failed to get behavioral sentiment for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    @Override
    public String getVolumeScore(String symbol) {
        try {
            // Python writes: VOLUME_SCORE hash with field "KITEUSDT"
            Object val = redisTemplateString.opsForHash().get("VOLUME_SCORE", symbol);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            log.error("Failed to get volume score for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    @Override
    public String getDashboardScore(String symbol) {
        try {
            // Node.js writes: DASHBOARD_SCORE hash with field "KITEUSDT"
            Object val = redisTemplateString.opsForHash().get("DASHBOARD_SCORE", symbol);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            log.error("Failed to get dashboard score for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

    @Override
    public String getFinalConsensus(String symbol) {
        try {
            // Python writes: FINAL_CONSENSUS_STATE hash with field "BTCUSDT"
            Object val = redisTemplateString.opsForHash().get("FINAL_CONSENSUS_STATE", symbol);
            return val != null ? val.toString() : null;
        } catch (Exception e) {
            log.error("Failed to get final consensus for {}: {}", symbol, e.getMessage());
            return null;
        }
    }

}
