package com.velan.production.integration.internal.domain;

/**
 * Known event types written to outbox_events.
 */
public enum OutboxEventType {
    ECR_EXCEPTION_RAISED,
    STORAGE_DELETE
}
