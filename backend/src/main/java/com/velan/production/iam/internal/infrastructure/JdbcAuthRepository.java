package com.velan.production.iam.internal.infrastructure;

import com.velan.production.iam.internal.domain.AuthRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAuthRepository implements AuthRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAuthRepository(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    @Override
    public Optional<AuthUserRecord> findByNormalizedLogin(String normalizedLogin) {
        if (jdbcTemplate == null || normalizedLogin == null) {
            return Optional.empty();
        }
        List<AuthUserRecord> rows = jdbcTemplate.query("""
                SELECT id, name, login, password_hash, employee_id, active,
                       failed_login_count, locked_until, must_change_password
                FROM users
                WHERE LOWER(login) = ?
                """,
                (rs, rowNum) -> {
                    Timestamp lockedTs = rs.getTimestamp("locked_until");
                    return new AuthUserRecord(
                            rs.getObject("id", UUID.class),
                            rs.getString("name"),
                            rs.getString("login"),
                            rs.getString("password_hash"),
                            rs.getString("employee_id"),
                            rs.getBoolean("active"),
                            rs.getInt("failed_login_count"),
                            lockedTs != null ? lockedTs.toInstant() : null,
                            rs.getBoolean("must_change_password"));
                },
                normalizedLogin);
        return rows.stream().findFirst();
    }

    @Override
    public void resetLockout(UUID userId, Instant now) {
        if (jdbcTemplate == null) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE users
                SET failed_login_count = 0,
                    locked_until = NULL,
                    updated_at = ?
                WHERE id = ?
                """,
                Timestamp.from(now),
                userId);
    }

    @Override
    public FailedAttemptResult recordFailedAttempt(UUID userId, int maxAttempts, Instant lockUntil, Instant now) {
        if (jdbcTemplate == null) {
            return new FailedAttemptResult(0, null);
        }
        return jdbcTemplate.queryForObject("""
                UPDATE users
                SET failed_login_count = failed_login_count + 1,
                    locked_until = CASE
                        WHEN failed_login_count + 1 >= ? THEN ?
                        ELSE locked_until
                    END,
                    updated_at = ?
                WHERE id = ?
                RETURNING failed_login_count, locked_until
                """,
                (rs, rowNum) -> {
                    Timestamp ts = rs.getTimestamp("locked_until");
                    return new FailedAttemptResult(
                            rs.getInt("failed_login_count"),
                            ts != null ? ts.toInstant() : null);
                },
                maxAttempts,
                Timestamp.from(lockUntil),
                Timestamp.from(now),
                userId);
    }

    @Override
    public List<String> findRoleNames(UUID userId) {
        if (jdbcTemplate == null) {
            return List.of();
        }
        return jdbcTemplate.queryForList("""
                SELECT r.name
                FROM user_roles ur
                JOIN roles r ON r.id = ur.role_id
                WHERE ur.user_id = ?
                ORDER BY r.name
                """,
                String.class,
                userId);
    }

    @Override
    public void insertRefreshToken(
            UUID id,
            UUID userId,
            String tokenHash,
            UUID familyId,
            Instant issuedAt,
            Instant expiresAt) {
        if (jdbcTemplate == null) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO refresh_tokens (id, user_id, token_hash, family_id, issued_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                id,
                userId,
                tokenHash,
                familyId,
                Timestamp.from(issuedAt),
                Timestamp.from(expiresAt));
    }
}
