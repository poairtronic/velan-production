package com.velan.production.integration.internal.application;

import com.velan.production.integration.internal.domain.OutboxEventStatus;
import com.velan.production.integration.internal.domain.OutboxEventStore;
import com.velan.production.integration.internal.domain.OutboxEventType;
import com.velan.production.shared.audit.AuditProperties;
import com.velan.production.shared.audit.AuditRedactor;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Transactional outbox enqueue service.
 * Always participates in the caller's transaction so a rollback removes the event.
 */
@Service
public class OutboxService {

    private final OutboxEventStore store;
    private final ObjectMapper objectMapper;
    private final List<String> redactKeys;
    private final Clock utcClock;

    public OutboxService(
            OutboxEventStore store,
            ObjectMapper objectMapper,
            ObjectProvider<AuditProperties> auditPropertiesProvider,
            ObjectProvider<Clock> utcClockProvider) {
        this.store = store;
        this.objectMapper = objectMapper;
        AuditProperties auditProps = auditPropertiesProvider.getIfAvailable();
        this.redactKeys = auditProps != null ? auditProps.redactKeys() : AuditProperties.DEFAULT_REDACT_KEYS;
        this.utcClock = utcClockProvider.getIfAvailable(Clock::systemUTC);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public UUID enqueue(
            OutboxEventType eventType,
            String aggregateType,
            String aggregateId,
            UUID scId,
            Map<String, Object> payload) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        if (aggregateType == null || aggregateType.isBlank()) {
            throw new IllegalArgumentException("aggregateType must not be blank");
        }
        if (aggregateId == null || aggregateId.isBlank()) {
            throw new IllegalArgumentException("aggregateId must not be blank");
        }
        Map<String, Object> sanitized = payload == null
                ? Map.of()
                : AuditRedactor.redact(payload, redactKeys);
        String payloadJson = objectMapper.writeValueAsString(sanitized);
        UUID id = UUID.randomUUID();
        Instant now = Instant.now(utcClock);
        store.insert(
                id,
                aggregateType,
                aggregateId,
                eventType.name(),
                payloadJson,
                OutboxEventStatus.PENDING,
                now,
                scId);
        return id;
    }
}
