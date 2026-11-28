package com.velan.production.shared.audit;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Append-only JDBC data access for audit_logs.
 * Only classes in shared.audit may access this repository.
 */
@Repository
class AuditLogRepository {

    private final JdbcTemplate jdbcTemplate;

    AuditLogRepository(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    void insert(
            UUID id,
            UUID scId,
            String requestId,
            UUID actorId,
            String actorRole,
            String action,
            String entityType,
            String entityId,
            String detailsJson,
            Instant createdAt) {
        if (jdbcTemplate == null) {
            throw new IllegalStateException("JdbcTemplate is not available");
        }
        jdbcTemplate.update("""
                INSERT INTO audit_logs
                    (id, sc_id, request_id, actor_id, actor_role, action, entity_type, entity_id, details, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                """,
                id,
                scId,
                requestId,
                actorId,
                actorRole,
                action,
                entityType,
                entityId,
                detailsJson,
                Timestamp.from(createdAt));
    }

    List<AuditQueryService.AuditRecord> query(String sql, Object[] args) {
        if (jdbcTemplate == null) {
            return List.of();
        }
        return jdbcTemplate.query(sql, (rs, rowNum) -> new AuditQueryService.AuditRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("sc_id", UUID.class),
                rs.getString("request_id"),
                rs.getObject("actor_id", UUID.class),
                rs.getString("actor_role"),
                rs.getString("action"),
                rs.getString("entity_type"),
                rs.getString("entity_id"),
                rs.getString("details"),
                rs.getTimestamp("created_at").toInstant()
        ), args);
    }

    long count(String sql, Object[] args) {
        if (jdbcTemplate == null) {
            return 0L;
        }
        Long total = jdbcTemplate.queryForObject(sql, Long.class, args);
        return total == null ? 0L : total;
    }
}
