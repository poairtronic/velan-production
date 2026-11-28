package com.velan.production.shared.idempotency;

import com.velan.production.shared.error.ConflictException;
import com.velan.production.shared.persistence.DatabaseTestSupport;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdempotencyServiceTests {

    @Test
    void beginCompleteReplayAndConflictsWork() {
        var cfg = DatabaseTestSupport.requireDatabaseUrl();
        JdbcTemplate jdbc = DatabaseTestSupport.openJdbcTemplate(cfg);
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
