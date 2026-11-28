package com.velan.production.shared.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager(CacheProperties properties, ObjectProvider<Ticker> tickerProvider) {
        Ticker ticker = tickerProvider.getIfAvailable(Ticker::systemTicker);
        List<CaffeineCache> caches = CacheNames.ALL.stream()
                .map(name -> buildCache(name, properties, ticker))
                .toList();
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(caches);
        manager.afterPropertiesSet();
        return manager;
    }

    private static CaffeineCache buildCache(String name, CacheProperties properties, Ticker ticker) {
        CacheProperties.CacheSpec spec = properties.specs().get(name);
        Duration ttl = spec != null && spec.ttl() != null ? spec.ttl() : properties.defaultTtl();
        long maxSize = spec != null && spec.maxSize() != null ? spec.maxSize() : properties.defaultMaxSize();
        var nativeCache = Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(maxSize)
                .recordStats()
                .ticker(ticker)
                .build();
        return new CaffeineCache(name, nativeCache, false);
    }
}
