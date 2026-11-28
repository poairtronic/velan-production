package com.velan.production.shared.scheduling;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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
        String url = System.getenv("DATABASE_URL");
        String user = System.getenv("DB_USER");
        String pass = System.getenv("DB_PASSWORD");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "DATABASE_URL not set; skipping ShedLock DB test");

        DriverManagerDataSource ds = new DriverManagerDataSource(url, user, pass);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
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
