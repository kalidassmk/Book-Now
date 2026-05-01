package com.bogoai.booknow.config;

import com.bogoai.booknow.model.Buy;
import com.bogoai.booknow.model.CurrentPrice;
import com.bogoai.booknow.model.FastMove;
import com.bogoai.booknow.model.HLA;
import com.bogoai.booknow.model.Percentage;
import com.bogoai.booknow.model.WalletBalance;
import com.bogoai.booknow.model.DustAsset;
import com.bogoai.booknow.model.R;
import com.bogoai.booknow.model.Sell;
import com.bogoai.booknow.model.ShortestTime;
import com.bogoai.booknow.model.SymbolRule;
import com.bogoai.booknow.model.TimeAnalyse;
import com.bogoai.booknow.model.WatchList;
import com.bogoai.booknow.response.RollingWindowTicker1HResponse;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
@EnableCaching
public class RedisConfig {

    @Value("${spring.redis.host}")
    private String host;
    @Value("${spring.redis.port}")
    private Integer port;

    @Bean
    public JedisConnectionFactory jedisConnectionFactory() {
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(host);
        redisConfig.setPort(port);
        return new JedisConnectionFactory(redisConfig);
    }

    @Bean(name = "redisTemplateShortestTime")
    public RedisTemplate<String, TimeAnalyse> redisTemplateShortestTime() {
        RedisTemplate<String, TimeAnalyse> template = new RedisTemplate<>();
        template.setConnectionFactory(jedisConnectionFactory());
        Jackson2JsonRedisSerializer<TimeAnalyse> jacksonSeial = new Jackson2JsonRedisSerializer<>(TimeAnalyse.class);
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        jacksonSeial.setObjectMapper(om);
        StringRedisSerializer stringSerial = new StringRedisSerializer();
        template.setKeySerializer(stringSerial);
        template.setValueSerializer(jacksonSeial);
        template.setHashKeySerializer(stringSerial);
        template.setHashValueSerializer(jacksonSeial);
        template.afterPropertiesSet();
        return template;
    }

    @Bean(name = "redisTemplateCurrentPrice")
    public RedisTemplate<String, CurrentPrice> redisTemplateCurrentPrice() {
        RedisTemplate<String, CurrentPrice> template = new RedisTemplate<>();
        template.setConnectionFactory(jedisConnectionFactory());
        Jackson2JsonRedisSerializer<CurrentPrice> jacksonSeial = new Jackson2JsonRedisSerializer<>(CurrentPrice.class);
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        jacksonSeial.setObjectMapper(om);
        StringRedisSerializer stringSerial = new StringRedisSerializer();
        template.setKeySerializer(stringSerial);
        template.setValueSerializer(jacksonSeial);
        template.setHashKeySerializer(stringSerial);
        template.setHashValueSerializer(jacksonSeial);
        template.afterPropertiesSet();
        return template;
    }

    @Bean(name = "redisTemplatePercentage")
    public RedisTemplate<String, Percentage> redisTemplatePercentage() {
        RedisTemplate<String, Percentage> template = new RedisTemplate<>();
        template.setConnectionFactory(jedisConnectionFactory());
        Jackson2JsonRedisSerializer<Percentage> jacksonSeial = new Jackson2JsonRedisSerializer<>(Percentage.class);
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        jacksonSeial.setObjectMapper(om);
        StringRedisSerializer stringSerial = new StringRedisSerializer();
        template.setKeySerializer(stringSerial);
        template.setValueSerializer(jacksonSeial);
        template.setHashKeySerializer(stringSerial);
        template.setHashValueSerializer(jacksonSeial);
        template.afterPropertiesSet();
        return template;
    }

    @Bean(name = "redisTemplateRWTicker1HResponse")
    public RedisTemplate<String, RollingWindowTicker1HResponse> redisTemplateRWTicker1HResponse() {
        RedisTemplate<String, RollingWindowTicker1HResponse> template = new RedisTemplate<>();
        template.setConnectionFactory(jedisConnectionFactory());
        Jackson2JsonRedisSerializer<RollingWindowTicker1HResponse> jacksonSeial = new Jackson2JsonRedisSerializer<>(RollingWindowTicker1HResponse.class);
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        jacksonSeial.setObjectMapper(om);
        StringRedisSerializer stringSerial = new StringRedisSerializer();
        template.setKeySerializer(stringSerial);
        template.setValueSerializer(jacksonSeial);
        template.setHashKeySerializer(stringSerial);
        template.setHashValueSerializer(jacksonSeial);
        template.afterPropertiesSet();
        return template;
    }

    @Bean(name = "redisTemplateHLA")
    public RedisTemplate<String, HLA> redisTemplateHLA() {
        RedisTemplate<String, HLA> template = new RedisTemplate<>();
        template.setConnectionFactory(jedisConnectionFactory());
        Jackson2JsonRedisSerializer<HLA> jacksonSeial = new Jackson2JsonRedisSerializer<>(HLA.class);
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        jacksonSeial.setObjectMapper(om);
        StringRedisSerializer stringSerial = new StringRedisSerializer();
        template.setKeySerializer(stringSerial);
        template.setValueSerializer(jacksonSeial);
        template.setHashKeySerializer(stringSerial);
        template.setHashValueSerializer(jacksonSeial);
        template.afterPropertiesSet();
        return template;
    }

    @Bean(name = "redisTemplateWatchList")
    public RedisTemplate<String, WatchList> redisTemplateWatchList() {
        RedisTemplate<String, WatchList> template = new RedisTemplate<>();
        template.setConnectionFactory(jedisConnectionFactory());
        Jackson2JsonRedisSerializer<WatchList> jacksonSeial = new Jackson2JsonRedisSerializer<>(WatchList.class);
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        jacksonSeial.setObjectMapper(om);
        StringRedisSerializer stringSerial = new StringRedisSerializer();
        template.setKeySerializer(stringSerial);
        template.setValueSerializer(jacksonSeial);
        template.setHashKeySerializer(stringSerial);
        template.setHashValueSerializer(jacksonSeial);
        template.afterPropertiesSet();
        return template;
    }

    @Bean(name = "redisTemplateRule")
    public RedisTemplate<String, R> redisTemplateRule() {
        RedisTemplate<String, R> template = new RedisTemplate<>();
        template.setConnectionFactory(jedisConnectionFactory());
        Jackson2JsonRedisSerializer<R> jacksonSeial = new Jackson2JsonRedisSerializer<>(R.class);
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        jacksonSeial.setObjectMapper(om);
        StringRedisSerializer stringSerial = new StringRedisSerializer();
        template.setKeySerializer(stringSerial);
        template.setValueSerializer(jacksonSeial);
        template.setHashKeySerializer(stringSerial);
        template.setHashValueSerializer(jacksonSeial);
        template.afterPropertiesSet();
        return template;
    }

    @Bean(name = "redisTemplateFastMove")
    public RedisTemplate<String, FastMove> redisTemplateFastMove() {
        RedisTemplate<String, FastMove> template = new RedisTemplate<>();
        template.setConnectionFactory(jedisConnectionFactory());
        Jackson2JsonRedisSerializer<FastMove> jacksonSeial = new Jackson2JsonRedisSerializer<>(FastMove.class);
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        jacksonSeial.setObjectMapper(om);
        StringRedisSerializer stringSerial = new StringRedisSerializer();
        template.setKeySerializer(stringSerial);
        template.setValueSerializer(jacksonSeial);
        template.setHashKeySerializer(stringSerial);
        template.setHashValueSerializer(jacksonSeial);
        template.afterPropertiesSet();
        return template;
    }

    @Bean(name = "redisTemplateBuy")
    public RedisTemplate<String, Buy> redisTemplateBuy() {
        RedisTemplate<String, Buy> template = new RedisTemplate<>();
        template.setConnectionFactory(jedisConnectionFactory());
        Jackson2JsonRedisSerializer<Buy> jacksonSeial = new Jackson2JsonRedisSerializer<>(Buy.class);
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        jacksonSeial.setObjectMapper(om);
        StringRedisSerializer stringSerial = new StringRedisSerializer();
        template.setKeySerializer(stringSerial);
        template.setValueSerializer(jacksonSeial);
        template.setHashKeySerializer(stringSerial);
        template.setHashValueSerializer(jacksonSeial);
        template.afterPropertiesSet();
        return template;
    }

    @Bean(name = "redisTemplateSell")
    public RedisTemplate<String, Sell> redisTemplateSell() {
        RedisTemplate<String, Sell> template = new RedisTemplate<>();
        template.setConnectionFactory(jedisConnectionFactory());
        Jackson2JsonRedisSerializer<Sell> jacksonSeial = new Jackson2JsonRedisSerializer<>(Sell.class);
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        jacksonSeial.setObjectMapper(om);
        StringRedisSerializer stringSerial = new StringRedisSerializer();
        template.setKeySerializer(stringSerial);
        template.setValueSerializer(jacksonSeial);
        template.setHashKeySerializer(stringSerial);
        template.setHashValueSerializer(jacksonSeial);
        template.afterPropertiesSet();
        return template;
    }

    @Bean(name = "redisTemplateSymbolRule")
    public RedisTemplate<String, SymbolRule> redisTemplateSymbolRule() {
        RedisTemplate<String, SymbolRule> template = new RedisTemplate<>();
        template.setConnectionFactory(jedisConnectionFactory());
        Jackson2JsonRedisSerializer<SymbolRule> jacksonSeial = new Jackson2JsonRedisSerializer<>(SymbolRule.class);
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        jacksonSeial.setObjectMapper(om);
        StringRedisSerializer stringSerial = new StringRedisSerializer();
        template.setKeySerializer(stringSerial);
        template.setValueSerializer(jacksonSeial);
        template.setHashKeySerializer(stringSerial);
        template.setHashValueSerializer(jacksonSeial);
        template.afterPropertiesSet();
        return template;
    }

    @Bean(name = "redisTemplateWalletBalance")
    public RedisTemplate<String, WalletBalance> redisTemplateWalletBalance() {
        RedisTemplate<String, WalletBalance> template = new RedisTemplate<>();
        template.setConnectionFactory(jedisConnectionFactory());
        Jackson2JsonRedisSerializer<WalletBalance> jacksonSeial = new Jackson2JsonRedisSerializer<>(WalletBalance.class);
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        jacksonSeial.setObjectMapper(om);
        StringRedisSerializer stringSerial = new StringRedisSerializer();
        template.setKeySerializer(stringSerial);
        template.setValueSerializer(jacksonSeial);
        template.setHashKeySerializer(stringSerial);
        template.setHashValueSerializer(jacksonSeial);
        template.afterPropertiesSet();
        return template;
    }

    @Bean(name = "redisTemplateDustAsset")
    public RedisTemplate<String, DustAsset> redisTemplateDustAsset() {
        RedisTemplate<String, DustAsset> template = new RedisTemplate<>();
        template.setConnectionFactory(jedisConnectionFactory());
        Jackson2JsonRedisSerializer<DustAsset> jacksonSeial = new Jackson2JsonRedisSerializer<>(DustAsset.class);
        ObjectMapper om = new ObjectMapper();
        om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        jacksonSeial.setObjectMapper(om);
        StringRedisSerializer stringSerial = new StringRedisSerializer();
        template.setKeySerializer(stringSerial);
        template.setValueSerializer(jacksonSeial);
        template.setHashKeySerializer(stringSerial);
        template.setHashValueSerializer(jacksonSeial);
        template.afterPropertiesSet();
        return template;
    }

    @Bean(name = "redisTemplateString")
    public RedisTemplate<String, String> redisTemplateString() {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(jedisConnectionFactory());
        StringRedisSerializer stringSerial = new StringRedisSerializer();
        template.setKeySerializer(stringSerial);
        template.setValueSerializer(stringSerial);
        template.setHashKeySerializer(stringSerial);
        template.setHashValueSerializer(stringSerial);
        template.afterPropertiesSet();
        return template;
    }

}