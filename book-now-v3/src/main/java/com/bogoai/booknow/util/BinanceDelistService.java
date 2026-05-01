package com.bogoai.booknow.util;

import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service to detect delisted symbols from Binance announcements.
 * Handles nested links recursively to find trading pairs.
 */
@Slf4j
@Service
public class BinanceDelistService {

    private static final String LIST_API_URL = "https://www.binance.com/bapi/composite/v1/public/cms/article/list/query?type=1&catalogId=161&pageNo=1&pageSize=20";
    private static final String ARTICLE_API_URL = "https://www.binance.com/bapi/composite/v1/public/cms/article/detail/query?articleCode=";
    private static final String REDIS_KEY_PREFIX = "BINANCE:DELIST:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final RestTemplate restTemplate = new RestTemplate();
    private final Set<String> processedCodes = new HashSet<>();

    @PostConstruct
    public void init() {
        checkDelistings();
    }

    @Scheduled(fixedRate = 21600000)
    public void checkDelistings() {
        try {
            log.info("[DelistService] Checking Binance announcements for delistings...");
            processedCodes.clear();
            String response = restTemplate.getForObject(LIST_API_URL, String.class);
            if (response == null) return;

            JSONObject json = new JSONObject(response);
            JSONArray articles = json.getJSONObject("data").getJSONArray("catalogs").getJSONObject(0).getJSONArray("articles");

            for (int i = 0; i < articles.length(); i++) {
                JSONObject article = articles.getJSONObject(i);
                String title = article.getString("title");
                String code = article.getString("code");

                if (title.contains("Notice of Removal of Spot Trading Pairs")) {
                    processArticle(code, 0);
                }
            }
        } catch (Exception e) {
            log.error("[DelistService] Error checking delistings: {}", e.getMessage());
        }
    }

    private void processArticle(String code, int depth) {
        if (depth > 1 || processedCodes.contains(code)) return;
        processedCodes.add(code);

        try {
            String url = ARTICLE_API_URL + code;
            String response = restTemplate.getForObject(url, String.class);
            if (response == null) return;

            JSONObject json = new JSONObject(response);
            String body = json.getJSONObject("data").getString("body");

            // 1. Extract symbols from current article
            Set<String> pairs = extractUsdtPairs(body);
            for (String pair : pairs) {
                String symbol = pair.replace("/", "");
                stringRedisTemplate.opsForValue().set(REDIS_KEY_PREFIX + symbol, "true");
                log.info("[DelistService] Marked symbol as DELISTED: {}", symbol);
            }

            // 2. Look for nested links to other delisting articles
            // Pattern to find article codes in links like /en/support/announcement/...-c498db770fa8
            Pattern linkPattern = Pattern.compile("c[a-f0-9]{32}");
            Matcher matcher = linkPattern.matcher(body);
            while (matcher.find()) {
                String nestedCode = matcher.group();
                if (body.contains("Notice of Removal of Spot Trading Pairs") || body.contains("Removal of Spot Trading Pairs")) {
                    processArticle(nestedCode, depth + 1);
                }
            }
        } catch (Exception e) {
            log.error("[DelistService] Error processing article {}: {}", code, e.getMessage());
        }
    }

    private Set<String> extractUsdtPairs(String text) {
        Set<String> pairs = new HashSet<>();
        Pattern pattern = Pattern.compile("\\b[A-Z0-9]+(/USDT|USDT)\\b");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String match = matcher.group();
            if (match.endsWith("USDT")) {
                pairs.add(match);
            }
        }
        return pairs;
    }

    public boolean isDelisted(String symbol) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(REDIS_KEY_PREFIX + symbol));
    }
}
