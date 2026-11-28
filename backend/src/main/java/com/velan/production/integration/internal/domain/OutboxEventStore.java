package com.velan.production.integration.internal.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistence abstraction for appending rows to outbox_events.
 */
public interface OutboxEventStore {

    void insert(
            UUID id,
            String aggregateType,
            String aggregateId,
            String eventType,
            String payloadJson,
            OutboxEventStatus status,
            Instant now,
            UUID scId);
}
