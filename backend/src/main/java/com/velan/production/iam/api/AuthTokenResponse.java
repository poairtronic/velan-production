package com.velan.production.iam.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        Instant accessTokenExpiresAt,
        UserSummary user) {

    public record UserSummary(
            UUID id,
            String employeeCode,
            String name,
            List<String> roles,
            boolean mustChangePassword) {
    }
}
