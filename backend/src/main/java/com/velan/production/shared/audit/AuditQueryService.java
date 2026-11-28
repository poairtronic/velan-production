package com.velan.production.shared.audit;

import com.velan.production.shared.persistence.PageQuery;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only query service for audit_logs.
 */
@Service
public class AuditQueryService {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("created_at", "action", "entity_type");

    private final AuditLogRepository repository;

    public AuditQueryService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public PageQuery.PageResult<AuditRecord> search(
            String entityType,
            String entityId,
            UUID scId,
            UUID actorId,
            AuditAction action,
            Instant from,
            Instant to,
            PageQuery pageQuery) {

        pageQuery.validateSort(ALLOWED_SORT_FIELDS);

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (entityType != null && !entityType.isBlank()) {
            where.append(" AND entity_type = ?");
            params.add(entityType);
        }
        if (entityId != null && !entityId.isBlank()) {
            where.append(" AND entity_id = ?");
            params.add(entityId);
        }
        if (scId != null) {
            where.append(" AND sc_id = ?");
            params.add(scId);
        }
        if (actorId != null) {
            where.append(" AND actor_id = ?");
            params.add(actorId);
        }
        if (action != null) {
            where.append(" AND action = ?");
            params.add(action.name());
        }
        if (from != null) {
            where.append(" AND created_at >= ?");
            params.add(Timestamp.from(from));
        }
        if (to != null) {
            where.append(" AND created_at <= ?");
            params.add(Timestamp.from(to));
        }

        long total = repository.count("SELECT COUNT(*) FROM audit_logs" + where, params.toArray());

        String orderBy = resolveOrderBy(pageQuery.sort());
        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(pageQuery.size());
        queryParams.add((long) pageQuery.page() * pageQuery.size());

        String sql = "SELECT id, sc_id, request_id, actor_id, actor_role, action, entity_type, entity_id, details::text, created_at "
                + "FROM audit_logs" + where + " ORDER BY " + orderBy + " LIMIT ? OFFSET ?";
        List<AuditRecord> items = repository.query(sql, queryParams.toArray());
        int totalPages = (int) Math.ceil((double) total / pageQuery.size());
        return new PageQuery.PageResult<>(items, pageQuery.page(), pageQuery.size(), total, totalPages);
    }

    private static String resolveOrderBy(String sort) {
        if (sort == null || sort.isBlank()) {
            return "created_at DESC";
        }
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        String dir = parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim()) ? "ASC" : "DESC";
        return field + " " + dir;
    }

    public record AuditRecord(
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
    }
}
