package com.velan.production.shared.cache;

import com.github.benmanes.caffeine.cache.Ticker;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CacheAndInvalidatorTests {

    @Test
    void cacheStatsExpiryMaxSizeNullAndPerCacheOverrideWork() {
        AtomicLong nanos = new AtomicLong(0);
        Ticker ticker = nanos::get;

        CacheProperties props = new CacheProperties(
                Duration.ofMinutes(10),
                2,
                Map.of(CacheNames.ROLES, new CacheProperties.CacheSpec(Duration.ofSeconds(5), null)));

        CacheManager manager = new CacheConfig().cacheManager(props,
                new org.springframework.beans.factory.ObjectProvider<>() {
                    @Override public Ticker getObject() { return ticker; }
                    @Override public Ticker getIfAvailable() { return ticker; }
                    @Override public Ticker getIfUnique() { return ticker; }
                });

        CaffeineCache roles = (CaffeineCache) manager.getCache(CacheNames.ROLES);
        assertNotNull(roles);

        roles.put("k1", "v1");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> roles.put("nullKey", null));
        assertEquals("v1", roles.get("k1", String.class));
        assertNull(roles.get("missing"));

        var stats = roles.getNativeCache().stats();
        assertEquals(1, stats.hitCount());
        assertEquals(1, stats.missCount());

        // Advance past 5s override TTL
        nanos.addAndGet(Duration.ofSeconds(6).toNanos());
        assertNull(roles.get("k1"));

        // Max size = 2
        roles.put("a", "1");
        roles.put("b", "2");
        roles.put("c", "3");
        roles.getNativeCache().cleanUp();
        assertEquals(2, roles.getNativeCache().estimatedSize());
    }

    @Test
    void invalidatorEvictEvictAllClearAllAndAfterCommitWork() {
        CacheProperties props = new CacheProperties(Duration.ofMinutes(10), 100, Map.of());
        CacheManager manager = new CacheConfig().cacheManager(props,
                new org.springframework.beans.factory.ObjectProvider<>() {
                    @Override public Ticker getObject() { return Ticker.systemTicker(); }
                    @Override public Ticker getIfAvailable() { return null; }
                    @Override public Ticker getIfUnique() { return null; }
                });
        CacheInvalidator invalidator = new CacheInvalidator(manager);

        var roles = manager.getCache(CacheNames.ROLES);
        var depts = manager.getCache(CacheNames.DEPARTMENTS);
        roles.put("r1", "Admin");
        depts.put("d1", "Machining");

        invalidator.evictAfterCommit(CacheNames.ROLES, "r1");
        assertNull(roles.get("r1"), "Without active tx, evictAfterCommit evicts immediately");

        roles.put("r1", "Admin");
        invalidator.evictAll(CacheNames.ROLES);
        assertNull(roles.get("r1"));
        assertNotNull(depts.get("d1"));

        invalidator.clearAll();
        assertNull(depts.get("d1"));
    }

    @Test
    void evictAfterCommitRespectsTransactionCommitAndRollback() {
        String url = System.getenv("DATABASE_URL");
        org.junit.jupiter.api.Assumptions.assumeTrue(url != null && !url.isBlank(), "DATABASE_URL not set");

        DriverManagerDataSource ds = new DriverManagerDataSource(url, System.getenv("DB_USER"), System.getenv("DB_PASSWORD"));
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(ds));

        CacheProperties props = new CacheProperties(Duration.ofMinutes(10), 100, Map.of());
        CacheManager manager = new CacheConfig().cacheManager(props,
                new org.springframework.beans.factory.ObjectProvider<>() {
                    @Override public Ticker getObject() { return Ticker.systemTicker(); }
                    @Override public Ticker getIfAvailable() { return null; }
                    @Override public Ticker getIfUnique() { return null; }
                });
        CacheInvalidator invalidator = new CacheInvalidator(manager);
        var roles = manager.getCache(CacheNames.ROLES);

        roles.put("r1", "Admin");
        tx.executeWithoutResult(status -> {
            invalidator.evictAfterCommit(CacheNames.ROLES, "r1");
            status.setRollbackOnly();
        });
        assertNotNull(roles.get("r1"), "Rollback must NOT evict");

        tx.executeWithoutResult(status -> invalidator.evictAfterCommit(CacheNames.ROLES, "r1"));
        assertNull(roles.get("r1"), "Commit must evict");
    }
}
