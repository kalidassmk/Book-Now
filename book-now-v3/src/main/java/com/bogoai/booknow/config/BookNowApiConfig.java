package com.bogoai.booknow.config;

import com.bogoai.api.client.BinanceApiAsyncRestClient;
import com.bogoai.api.client.BinanceApiClientFactory;
import com.bogoai.api.client.BinanceApiRestClient;
import com.bogoai.api.client.BinanceApiWebSocketClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BookNowApiConfig {

    @Value("${test.api.key}")
    private String testAPIKey;
    @Value("${test.secret.key}")
    private String testSecretKey;

    @Value("${prod.api.key}")
    private String prodAPIKey;

    @Value("${prod.secret.key}")
    private String prodSecretKey;

    @Bean
    public BinanceApiAsyncRestClient testBinanceApiAsyncRestClient() {
        BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(testAPIKey, testSecretKey);
        BinanceApiAsyncRestClient client = factory.newAsyncRestClient();
        return client;
    }

    @Bean
    public BinanceApiAsyncRestClient prodBinanceApiAsyncRestClient() {
        BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(prodAPIKey, prodSecretKey);
        BinanceApiAsyncRestClient client = factory.newAsyncRestClient();
        return client;
    }


    @Bean
    public BinanceApiRestClient testBinanceApiRestClient() {
        BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(testAPIKey, testSecretKey);
        BinanceApiRestClient client = factory.newRestClient();
        return client;
    }

    @Bean
    public BinanceApiRestClient prodBinanceApiARestClient() {
        BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(prodAPIKey, prodSecretKey);
        BinanceApiRestClient client = factory.newRestClient();
        return client;
    }

    /**
     * Streaming client used by BinanceUserDataStreamService to subscribe to
     * the account's user-data-stream (balance + order updates), replacing the
     * 15s/60s REST polling loops that were burning ~7,200 weight/day.
     */
    @Bean
    public BinanceApiWebSocketClient prodBinanceApiWebSocketClient() {
        BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(prodAPIKey, prodSecretKey);
        return factory.newWebSocketClient();
    }
}
