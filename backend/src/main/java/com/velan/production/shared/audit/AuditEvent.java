package com.velan.production.shared.audit;

import java.util.Map;
import java.util.UUID;

/**
 * Immutable audit event payload.
 */
public record AuditEvent(
        AuditAction action,
        String entityType,
        String entityId,
        UUID scId,
        Map<String, Object> oldValue,
        Map<String, Object> newValue,
        String reason) {

    public static AuditEvent of(
            AuditAction action,
            String entityType,
            String entityId,
            UUID scId,
            Map<String, Object> oldValue,
            Map<String, Object> newValue,
            String reason) {
        return new AuditEvent(action, entityType, entityId, scId, oldValue, newValue, reason);
    }
}
