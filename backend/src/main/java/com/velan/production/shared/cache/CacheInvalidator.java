package com.velan.production.shared.cache;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Central cache eviction helper supporting immediate and after-commit invalidation.
 */
@Component
public class CacheInvalidator {

    private final CacheManager cacheManager;

    public CacheInvalidator(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evict(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }

    public void evictAll(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    public void clearAll() {
        for (String name : cacheManager.getCacheNames()) {
            evictAll(name);
        }
    }

    public void evictAfterCommit(String cacheName, Object key) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(cacheName, key);
                }
            });
        } else {
            evict(cacheName, key);
        }
    }
}
