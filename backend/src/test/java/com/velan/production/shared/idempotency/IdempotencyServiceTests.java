package com.velan.production.shared.idempotency;

import com.velan.production.shared.error.ConflictException;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdempotencyServiceTests {

    @Test
    void beginCompleteReplayAndConflictsWork() {
        String url = System.getenv("DATABASE_URL");
        String user = System.getenv("DB_USER");
        String pass = System.getenv("DB_PASSWORD");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "DATABASE_URL not set; skipping idempotency test");

        DriverManagerDataSource ds = new DriverManagerDataSource(url, user, pass);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        IdempotencyService service = new IdempotencyService(jdbc, new IdempotencyProperties(Duration.ofHours(24)));

        String key = "test-key-" + UUID.randomUUID();
        String hash1 = "a".repeat(64);
        String hash2 = "b".repeat(64);
        try {
            assertTrue(service.begin(key, hash1).isEmpty());
            assertThrows(ConflictException.class, () -> service.begin(key, hash1));
            assertThrows(ConflictException.class, () -> service.begin(key, hash2));

            service.complete(key, "{\"ok\":true}");

            Optional<String> replay = service.begin(key, hash1);
            assertTrue(replay.isPresent());
            assertTrue(replay.get().contains("\"ok\""));

            assertEquals(replay, service.lookup(key));
            assertThrows(ConflictException.class, () -> service.begin(key, hash2));
        } finally {
            jdbc.update("DELETE FROM idempotency_keys WHERE idempotency_key = ?", key);
        }
    }
}
