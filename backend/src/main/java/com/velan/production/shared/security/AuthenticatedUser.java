package com.velan.production.shared.security;

import java.util.List;
import java.util.UUID;

/**
 * Authenticated user principal extracted from a validated access JWT.
 */
public record AuthenticatedUser(
        UUID userId,
        String employeeCode,
        List<String> roles,
        boolean mustChangePassword) {
}
