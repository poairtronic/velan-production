package com.velan.production.integration.internal.domain;

public enum OutboxEventStatus {
    PENDING,
    IN_FLIGHT,
    PUBLISHED,
    RETRY_SCHEDULED,
    DEAD_LETTER
}
