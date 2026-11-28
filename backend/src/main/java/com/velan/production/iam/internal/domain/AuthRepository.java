package com.velan.production.iam.internal.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthRepository {

    Optional<AuthUserRecord> findByNormalizedLogin(String normalizedLogin);

    void resetLockout(UUID userId, Instant now);

    FailedAttemptResult recordFailedAttempt(UUID userId, int maxAttempts, Instant lockUntil, Instant now);

    List<String> findRoleNames(UUID userId);

    void insertRefreshToken(
            UUID id,
            UUID userId,
            String tokenHash,
            UUID familyId,
            Instant issuedAt,
            Instant expiresAt);

    record AuthUserRecord(
            UUID id,
            String name,
            String login,
            String passwordHash,
            String employeeId,
            boolean active,
            int failedLoginCount,
            Instant lockedUntil,
            boolean mustChangePassword) {
    }

    record FailedAttemptResult(int failedLoginCount, Instant lockedUntil) {
    }
}
