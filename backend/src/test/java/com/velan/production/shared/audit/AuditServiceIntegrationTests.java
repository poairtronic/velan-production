package com.velan.production.shared.audit;

import com.velan.production.shared.persistence.PageQuery;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditServiceIntegrationTests {

    @Test
    void recordAndQueryAndTransactionSemanticsWork() {
        String url = System.getenv("DATABASE_URL");
        String user = System.getenv("DB_USER");
        String pass = System.getenv("DB_PASSWORD");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "DATABASE_URL not set; skipping audit integration test");

        DriverManagerDataSource ds = new DriverManagerDataSource(url, user, pass);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        DataSourceTransactionManager txManager = new DataSourceTransactionManager(ds);
        TransactionTemplate tx = new TransactionTemplate(txManager);

        AuditLogRepository repo = new AuditLogRepository(new org.springframework.beans.factory.ObjectProvider<>() {
            @Override public JdbcTemplate getObject() { return jdbc; }
            @Override public JdbcTemplate getIfAvailable() { return jdbc; }
            @Override public JdbcTemplate getIfUnique() { return jdbc; }
        });
        AuditService service = new AuditService(repo, new AuditProperties(null), new ObjectMapper(),
                new org.springframework.beans.factory.ObjectProvider<>() {
                    @Override public Clock getObject() { return Clock.systemUTC(); }
                    @Override public Clock getIfAvailable() { return Clock.systemUTC(); }
                    @Override public Clock getIfUnique() { return Clock.systemUTC(); }
                });
        AuditQueryService queryService = new AuditQueryService(repo);

        String entityId = "test-" + UUID.randomUUID();
        UUID scId = UUID.randomUUID();
        try {
            // 1. Committed record
            tx.executeWithoutResult(status -> service.record(AuditEvent.of(
                    AuditAction.CREATE, "SC", entityId, scId, null, Map.of("status", "ACTIVE", "password", "secret"), "init")));

            var found = queryService.search("SC", entityId, scId, AuditService.SYSTEM_ACTOR_ID, AuditAction.CREATE,
                    null, null, PageQuery.of(0, 20, "created_at,desc"));
            assertEquals(1, found.totalElements());
            assertTrue(found.items().getFirst().detailsJson().contains("ACTIVE"));
            assertTrue(!found.items().getFirst().detailsJson().contains("secret"));

            // 2. Rollback removes record()
            String rolledBackId = "rb-" + UUID.randomUUID();
            tx.executeWithoutResult(status -> {
                service.record(AuditEvent.of(AuditAction.UPDATE, "SC", rolledBackId, scId, null, Map.of("k", "v"), null));
                status.setRollbackOnly();
            });
            assertEquals(0, queryService.search("SC", rolledBackId, null, null, null, null, null, PageQuery.of(0, 20, null)).totalElements());

            // 3. Invalid sort field rejected
            assertThrows(IllegalArgumentException.class, () ->
                    queryService.search("SC", entityId, null, null, null, null, null, PageQuery.of(0, 20, "bad_col")));
        } finally {
            jdbc.update("DELETE FROM audit_logs WHERE entity_id = ?", entityId);
        }
    }
}
