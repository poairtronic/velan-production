package com.velan.production.shared.error;

import java.time.Instant;
import java.util.List;

/**
 * Standard API error envelope.
 */
public record ErrorResponse(
        int status,
        String code,
        String message,
        List<FieldErrorItem> fieldErrors,
        String correlationId,
        Instant timestamp) {

    public record FieldErrorItem(String field, String code, String message) {
    }
}
