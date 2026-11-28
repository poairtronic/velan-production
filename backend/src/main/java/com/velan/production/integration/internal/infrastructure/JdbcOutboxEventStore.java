package com.velan.production.integration.internal.infrastructure;

import com.velan.production.integration.internal.domain.OutboxEventStatus;
import com.velan.production.integration.internal.domain.OutboxEventStore;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC implementation of OutboxEventStore targeting outbox_events.
 */
@Repository
class JdbcOutboxEventStore implements OutboxEventStore {

    private final JdbcTemplate jdbcTemplate;

    JdbcOutboxEventStore(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    @Override
    public void insert(
            UUID id,
            String aggregateType,
            String aggregateId,
            String eventType,
            String payloadJson,
            OutboxEventStatus status,
            Instant now,
            UUID scId) {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate is not available");
        }
        Timestamp ts = Timestamp.from(now);
        jdbcTemplate.update("""
                INSERT INTO outbox_events
                    (id, aggregate_type, aggregate_id, event_type, payload, status, attempts, next_retry_at, created_at, sc_id)
                VALUES (?, ?, ?, ?, ?::jsonb, ?, 0, ?, ?, ?)
                """,
                id,
                aggregateType,
                aggregateId,
                eventType,
                payloadJson,
                status.name(),
                ts,
                ts,
                scId);
    }
}
