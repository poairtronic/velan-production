package com.velan.production.shared.idempotency;

import com.velan.production.shared.error.ConflictException;
import com.velan.production.shared.error.ErrorCode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * JDBC-backed idempotency store using idempotency_keys.
 */
@Service
@ConditionalOnExpression("'${spring.autoconfigure.exclude[0]:}' == ''")
public class IdempotencyService {

    private static final UUID SYSTEM_ACTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final JdbcTemplate jdbcTemplate;
    private final IdempotencyProperties properties;

    public IdempotencyService(JdbcTemplate jdbcTemplate, IdempotencyProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public Optional<String> begin(String key, String requestHash) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.ttl());
        try {
            jdbcTemplate.update("""
                    INSERT INTO idempotency_keys (id, actor_id, idempotency_key, request_hash, response_status, expires_at, created_at)
                    VALUES (?, ?, ?, ?, 0, ?, ?)
                    """,
                    UUID.randomUUID(),
                    SYSTEM_ACTOR_ID,
                    key,
                    requestHash,
                    Timestamp.from(expiresAt),
                    Timestamp.from(now));
            return Optional.empty();
        } catch (DuplicateKeyException ex) {
            return handleExisting(key, requestHash);
        }
    }

    public void complete(String key, String responseJson) {
        jdbcTemplate.update("""
                UPDATE idempotency_keys
                SET response_status = 200, response_body = ?::jsonb
                WHERE actor_id = ? AND idempotency_key = ?
                """,
                responseJson,
                SYSTEM_ACTOR_ID,
                key);
    }

    public Optional<String> lookup(String key) {
        List<String> rows = jdbcTemplate.query("""
                SELECT response_body::text
                FROM idempotency_keys
                WHERE actor_id = ? AND idempotency_key = ? AND response_status > 0
                """,
                (rs, rowNum) -> rs.getString(1),
                SYSTEM_ACTOR_ID,
                key);
        return rows.stream().findFirst();
    }

    private Optional<String> handleExisting(String key, String requestHash) {
        List<ExistingKey> rows = jdbcTemplate.query("""
                SELECT request_hash, response_status, response_body::text
                FROM idempotency_keys
                WHERE actor_id = ? AND idempotency_key = ?
                """,
                (rs, rowNum) -> new ExistingKey(rs.getString(1), rs.getInt(2), rs.getString(3)),
                SYSTEM_ACTOR_ID,
                key);
        if (rows.isEmpty()) {
            throw new ConflictException(ErrorCode.ERR_IDEMPOTENCY, "Idempotency key conflict");
        }
        ExistingKey existing = rows.getFirst();
        if (!existing.requestHash().equals(requestHash)) {
            throw new ConflictException(ErrorCode.ERR_IDEMPOTENCY, "Idempotency key reused with different request");
        }
        if (existing.responseStatus() == 0) {
            throw new ConflictException(ErrorCode.ERR_IDEMPOTENCY, "Request with this idempotency key is still in progress");
        }
        return Optional.ofNullable(existing.responseBody());
    }

    private record ExistingKey(String requestHash, int responseStatus, String responseBody) {
    }
}
