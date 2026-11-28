package com.velan.production.integration.internal.application;

import com.velan.production.integration.internal.domain.OutboxEventStatus;
import com.velan.production.integration.internal.domain.OutboxEventStore;
import com.velan.production.integration.internal.domain.OutboxEventType;
import com.velan.production.shared.audit.AuditProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboxServiceTests {

    @Test
    void enqueueRedactsSensitiveKeysAndSetsPendingStatus() {
        List<String> capturedPayloads = new ArrayList<>();
        List<OutboxEventStatus> capturedStatuses = new ArrayList<>();
        OutboxEventStore memoryStore = (id, aggType, aggId, eventType, payloadJson, status, now, scId) -> {
            capturedPayloads.add(payloadJson);
            capturedStatuses.add(status);
        };

        Instant fixedNow = Instant.parse("2026-11-28T09:00:00Z");
        OutboxService service = new OutboxService(
                memoryStore,
                new ObjectMapper(),
                providerOf(new AuditProperties(null)),
                providerOf(Clock.fixed(fixedNow, ZoneOffset.UTC)));

        UUID id = service.enqueue(
                OutboxEventType.ECR_EXCEPTION_RAISED,
                "QC_EXCEPTION",
                "exc-123",
                null,
                Map.of(
                        "productionRef", "exc-123",
                        "password", "hunter2",
                        "nested", Map.of("token", "secret-tok", "reason", "bad dimension")));

        assertNotNull(id);
        assertEquals(List.of(OutboxEventStatus.PENDING), capturedStatuses);
        String json = capturedPayloads.getFirst();
        assertTrue(json.contains("exc-123"));
        assertTrue(json.contains("bad dimension"));
        assertFalse(json.contains("hunter2"));
        assertFalse(json.contains("secret-tok"));
    }

    @Test
    void enqueuePersistsInCallerTransactionAndRollsBackCleanly() {
        String url = System.getenv("DATABASE_URL");
        String user = System.getenv("DB_USER");
        String pass = System.getenv("DB_PASSWORD");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "DATABASE_URL not set; skipping outbox DB test");

        DriverManagerDataSource ds = new DriverManagerDataSource(url, user, pass);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        DataSourceTransactionManager txManager = new DataSourceTransactionManager(ds);
        TransactionTemplate tx = new TransactionTemplate(txManager);

        OutboxEventStore jdbcStore = (id, aggType, aggId, eventType, payloadJson, status, now, scId) -> jdbc.update("""
                INSERT INTO outbox_events
                    (id, aggregate_type, aggregate_id, event_type, payload, status, attempts, next_retry_at, created_at, sc_id)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, 0, ?, ?, ?)
                """,
                id, aggType, aggId, eventType, payloadJson, status.name(),
                java.sql.Timestamp.from(now), java.sql.Timestamp.from(now), scId);

        OutboxService service = new OutboxService(
                jdbcStore,
                new ObjectMapper(),
                providerOf(new AuditProperties(null)),
                providerOf(Clock.systemUTC()));

        String committedAggId = "outbox-ok-" + UUID.randomUUID();
        String rolledBackAggId = "outbox-rb-" + UUID.randomUUID();
        UUID scId = UUID.randomUUID();

        try {
            UUID eventId = tx.execute(status -> service.enqueue(
                    OutboxEventType.ECR_EXCEPTION_RAISED,
                    "QC_EXCEPTION",
                    committedAggId,
                    scId,
                    Map.of("productionRef", committedAggId, "secret", "do-not-store", "reason", "failed")));

            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT id, aggregate_type, aggregate_id, event_type, payload::text AS payload, status, attempts, sc_id FROM outbox_events WHERE id = ?",
                    eventId);
            assertEquals("QC_EXCEPTION", row.get("aggregate_type"));
            assertEquals(committedAggId, row.get("aggregate_id"));
            assertEquals("ECR_EXCEPTION_RAISED", row.get("event_type"));
            assertEquals("PENDING", row.get("status"));
            assertEquals(0, ((Number) row.get("attempts")).intValue());
            assertEquals(scId, row.get("sc_id"));
            String payloadText = (String) row.get("payload");
            assertTrue(payloadText.contains("failed"));
            assertFalse(payloadText.contains("do-not-store"));

            // Rollback removes event
            tx.executeWithoutResult(status -> {
                service.enqueue(
                        OutboxEventType.ECR_EXCEPTION_RAISED,
                        "QC_EXCEPTION",
                        rolledBackAggId,
                        scId,
                        Map.of("reason", "rolled back"));
                status.setRollbackOnly();
            });

            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ?",
                    Integer.class,
                    rolledBackAggId);
            assertEquals(0, count);
        } finally {
            jdbc.update("DELETE FROM outbox_events WHERE aggregate_id IN (?, ?)", committedAggId, rolledBackAggId);
        }
    }

    private static <T> ObjectProvider<T> providerOf(T instance) {
        return new ObjectProvider<>() {
            @Override public T getObject() { return instance; }
            @Override public T getIfAvailable() { return instance; }
            @Override public T getIfUnique() { return instance; }
        };
    }
}
