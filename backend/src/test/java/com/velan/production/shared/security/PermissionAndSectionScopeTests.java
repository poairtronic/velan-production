package com.velan.production.shared.security;

import com.github.benmanes.caffeine.cache.Ticker;
import com.velan.production.shared.cache.CacheConfig;
import com.velan.production.shared.cache.CacheInvalidator;
import com.velan.production.shared.cache.CacheNames;
import com.velan.production.shared.cache.CacheProperties;
import com.velan.production.shared.persistence.DatabaseTestSupport;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionAndSectionScopeTests {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void permissionResolutionFollowsDenyAllowRoleDenyOrder() {
        UUID userId = UUID.randomUUID();
        InMemoryPermissionRepo repo = new InMemoryPermissionRepo();
        repo.rolePerms.put(userId, Set.of("SC_VIEW", "PIECE_VIEW"));
        repo.overrides.put(userId, Map.of("SC_VIEW", "DENY", "SC_CANCEL", "ALLOW"));

        PermissionService service = new PermissionService(repo);
        assertFalse(service.hasPermission(userId, "SC_VIEW"), "DENY override must beat role grant");
        assertTrue(service.hasPermission(userId, "SC_CANCEL"), "ALLOW override must grant without role grant");
        assertTrue(service.hasPermission(userId, "PIECE_VIEW"), "Role grant must allow");
        assertFalse(service.hasPermission(userId, "UNGRANTED_PERM"), "No grant must deny");
    }

    @Test
    void sectionScopeResolvesAllWhenEmptyAndFilteredWhenPresent() {
        UUID userId = UUID.randomUUID();
        UUID sec1 = UUID.randomUUID();
        InMemoryPermissionRepo repo = new InMemoryPermissionRepo();
        SectionScopeResolver resolver = new SectionScopeResolver(repo);

        SectionScope all = resolver.resolve(userId);
        assertTrue(all.allSections());
        assertTrue(all.sectionIds().isEmpty());

        repo.sections.put(userId, Set.of(sec1));
        SectionScope filtered = resolver.resolve(userId);
        assertFalse(filtered.allSections());
        assertEquals(Set.of(sec1), filtered.sectionIds());
    }

    @Test
    void passwordEncoderHashesAndMatches() {
        PasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String hash = encoder.encode("SecretPass123!");
        assertNotEquals("SecretPass123!", hash);
        assertTrue(encoder.matches("SecretPass123!", hash));
        assertFalse(encoder.matches("WrongPass!", hash));
    }

    @Test
    void auditorAwareReturnsUserIdWhenAuthenticatedAndEmptyOtherwise() {
        UUID userId = UUID.randomUUID();
        var auditorAware = new com.velan.production.shared.persistence.JpaAuditingConfig().auditorAware();

        assertTrue(auditorAware.getCurrentAuditor().isEmpty());

        AuthenticatedUser principal = new AuthenticatedUser(userId, "EMP001", List.of("PM"), false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        assertEquals(userId, auditorAware.getCurrentAuditor().orElseThrow());
    }

    @Test
    void roleChangeTakesEffectImmediatelyAndRolePermissionEvictionRespectsCommitAndRollback() throws Exception {
        var cfg = DatabaseTestSupport.requireDirectOrPooledUrl();
        DatabaseTestSupport.withMigratedSchema(cfg, "velan_perm_cache", (conn, schema) -> {
            try (var st = conn.createStatement()) {
                st.execute("SET search_path TO " + schema);
            }
            SingleConnectionDataSource ds = new SingleConnectionDataSource(conn, true);
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            DataSourceTransactionManager txManager = new DataSourceTransactionManager(ds);
            TransactionTemplate tx = new TransactionTemplate(txManager);

            try (AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext()) {
                ctx.registerBean(JdbcTemplate.class, () -> jdbc);
                ctx.registerBean(DataSourceTransactionManager.class, () -> txManager);
                ctx.registerBean(CacheProperties.class, () -> new CacheProperties(Duration.ofMinutes(10), 1000, Map.of()));
                ctx.registerBean(Ticker.class, Ticker::systemTicker);
                ctx.register(
                        TestTxConfig.class,
                        CacheConfig.class,
                        CacheInvalidator.class,
                        RolePermissionGrantCache.class,
                        JdbcPermissionRepository.class,
                        PermissionService.class);
                ctx.refresh();

                PermissionService permissionService = ctx.getBean(PermissionService.class);
                JdbcPermissionRepository permRepo = ctx.getBean(JdbcPermissionRepository.class);
                CacheInvalidator invalidator = ctx.getBean(CacheInvalidator.class);
                CacheManager cacheManager = ctx.getBean(CacheManager.class);

                UUID deptId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                UUID roleA = jdbc.queryForObject("SELECT id FROM roles WHERE name = 'ADMIN'", UUID.class);
                UUID roleB = jdbc.queryForObject("SELECT id FROM roles WHERE name = 'OPERATOR'", UUID.class);

                jdbc.update("INSERT INTO departments (id, code, name) VALUES (?, 'D-PERM', 'Perm Dept')", deptId);
                jdbc.update("INSERT INTO users (id, name, login, password_hash, employee_id, department_id) VALUES (?, 'U1', 'permuser', 'h', 'EMP-P1', ?)", userId, deptId);
                jdbc.update("INSERT INTO user_roles (id, user_id, role_id) VALUES (?, ?, ?)", UUID.randomUUID(), userId, roleA);

                assertFalse(permRepo.mustChangePassword(userId));
                jdbc.update("UPDATE users SET must_change_password = TRUE WHERE id = ?", userId);
                assertTrue(permRepo.mustChangePassword(userId));

                // ADMIN has RECORD_CANCEL_DELETE; OPERATOR does not, but has OPERATOR_MEASUREMENTS_WRITE
                assertTrue(permissionService.hasPermission(userId, "RECORD_CANCEL_DELETE"));
                assertFalse(permissionService.hasPermission(userId, "OPERATOR_MEASUREMENTS_WRITE"));
                // Cached by roleId, NOT by userId
                assertTrue(cacheManager.getCache(CacheNames.ROLE_PERMISSIONS).get(roleA) != null);
                assertTrue(cacheManager.getCache(CacheNames.ROLE_PERMISSIONS).get(userId) == null);

                // Switch user's role from ADMIN -> OPERATOR without clearing cache: takes effect on next request
                jdbc.update("DELETE FROM user_roles WHERE user_id = ?", userId);
                jdbc.update("INSERT INTO user_roles (id, user_id, role_id) VALUES (?, ?, ?)", UUID.randomUUID(), userId, roleB);
                assertFalse(permissionService.hasPermission(userId, "RECORD_CANCEL_DELETE"));
                assertTrue(permissionService.hasPermission(userId, "OPERATOR_MEASUREMENTS_WRITE"));

                // Warm roleB cache, then test rollback does NOT evict rolePermissions(roleB)
                assertFalse(permissionService.hasPermission(userId, "SC_EDIT"));
                tx.executeWithoutResult(status -> {
                    jdbc.update("INSERT INTO role_permissions (id, role_id, permission) VALUES (?, ?, 'SC_EDIT')", UUID.randomUUID(), roleB);
                    invalidator.evictAfterCommit(CacheNames.ROLE_PERMISSIONS, roleB);
                    status.setRollbackOnly();
                });
                assertFalse(permissionService.hasPermission(userId, "SC_EDIT"), "Rollback must not evict or persist");

                // Commit DOES evict rolePermissions(roleB) and makes new grant visible
                tx.executeWithoutResult(status -> {
                    jdbc.update("INSERT INTO role_permissions (id, role_id, permission) VALUES (?, ?, 'SC_EDIT')", UUID.randomUUID(), roleB);
                    invalidator.evictAfterCommit(CacheNames.ROLE_PERMISSIONS, roleB);
                });
                assertTrue(permissionService.hasPermission(userId, "SC_EDIT"), "Commit must evict rolePermissions cache");
            }
        });
    }

    @EnableTransactionManagement
    static class TestTxConfig {
    }

    private static class InMemoryPermissionRepo implements PermissionRepository {
        final Map<UUID, Set<String>> rolePerms = new HashMap<>();
        final Map<UUID, Map<String, String>> overrides = new HashMap<>();
        final Map<UUID, Set<UUID>> sections = new HashMap<>();

        @Override public boolean isUserActive(UUID userId) { return true; }
        @Override public Set<String> findRolePermissions(UUID userId) { return rolePerms.getOrDefault(userId, Set.of()); }
        @Override public Map<String, String> findUserOverrides(UUID userId) { return overrides.getOrDefault(userId, Map.of()); }
        @Override public Set<UUID> findUserSections(UUID userId) { return sections.getOrDefault(userId, Set.of()); }
    }
}
