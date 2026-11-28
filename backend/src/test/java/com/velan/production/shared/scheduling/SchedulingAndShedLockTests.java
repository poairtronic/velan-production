package com.velan.production.shared.scheduling;

import com.velan.production.shared.persistence.DatabaseTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@ActiveProfiles("test")
class SchedulingAndShedLockTests {

    @Autowired
    private SchedulingProperties schedulingProperties;

    @Test
    void schedulingIsDisabledInTestProfileAndZoneIsAsiaKolkata() {
        assertFalse(schedulingProperties.enabled());
        assertEquals("Asia/Kolkata", schedulingProperties.zone());
    }

    @Test
    void shedLockAllowsOnlyOneExecutionAcrossCompetingCallers() {
        var dbCfg = DatabaseTestSupport.requireDatabaseUrl();
        JdbcTemplate jdbc = DatabaseTestSupport.openJdbcTemplate(dbCfg);
        LockProvider provider = new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(jdbc)
                        .withTableName("shedlock")
                        .usingDbTime()
                        .build()
        );

        String lockName = "test-lock-" + UUID.randomUUID();
        LockConfiguration cfg = new LockConfiguration(
                Instant.now(), lockName, Duration.ofMinutes(5), Duration.ofMinutes(1));
        AtomicInteger runs = new AtomicInteger(0);
        try {
            provider.lock(cfg).ifPresent(lock -> {
                try {
                    runs.incrementAndGet();
                    // Competing caller while lock is held
                    assertFalse(provider.lock(cfg).isPresent());
                } finally {
                    lock.unlock();
                }
            });
            assertEquals(1, runs.get());
        } finally {
            jdbc.update("DELETE FROM shedlock WHERE name = ?", lockName);
        }
    }
}
