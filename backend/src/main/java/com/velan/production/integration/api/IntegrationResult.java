package com.velan.production.integration.api;

import com.velan.production.integration.api.domain.IntegrationResultState;
import java.util.Objects;
import java.util.Optional;

/**
 * Wrapper for external integration results so callers never confuse
 * a disabled or failed integration with "no data" or "not issued".
 */
public record IntegrationResult<T>(
        IntegrationResultState state,
        T data,
        String message) {

    public IntegrationResult {
        Objects.requireNonNull(state, "state must not be null");
        if (state == IntegrationResultState.AVAILABLE && data == null) {
            throw new IllegalArgumentException("data must not be null when state is AVAILABLE");
        }
        if (state != IntegrationResultState.AVAILABLE && data != null) {
            throw new IllegalArgumentException("data must be null when state is " + state);
        }
        if (state == IntegrationResultState.FAILED && (message == null || message.isBlank())) {
            throw new IllegalArgumentException("message must not be blank when state is FAILED");
        }
    }

    public static <T> IntegrationResult<T> available(T data) {
        return new IntegrationResult<>(IntegrationResultState.AVAILABLE, data, null);
    }

    public static <T> IntegrationResult<T> disabled() {
        return new IntegrationResult<>(IntegrationResultState.DISABLED, null, "Integration is disabled");
    }

    public static <T> IntegrationResult<T> failed(String safeMessage) {
        return new IntegrationResult<>(IntegrationResultState.FAILED, null, safeMessage);
    }

    public boolean isAvailable() {
        return state == IntegrationResultState.AVAILABLE;
    }

    public boolean isDisabled() {
        return state == IntegrationResultState.DISABLED;
    }

    public boolean isFailed() {
        return state == IntegrationResultState.FAILED;
    }

    public Optional<T> optionalData() {
        return isAvailable() ? Optional.ofNullable(data) : Optional.empty();
    }
}
