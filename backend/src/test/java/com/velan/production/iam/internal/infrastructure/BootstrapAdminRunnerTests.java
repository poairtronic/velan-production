package com.velan.production.iam.internal.infrastructure;

import com.velan.production.shared.audit.AuditProperties;
import com.velan.production.shared.audit.AuditService;
import com.velan.production.shared.persistence.DatabaseTestSupport;
import com.velan.production.shared.security.PasswordPolicy;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class BootstrapAdminRunnerTests {

    private final PasswordPolicy passwordPolicy = new PasswordPolicy();
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @Test
    void createsAdminOnceWithAuditAndRespectsMustChangePasswordFlag(CapturedOutput output) throws Exception {
        var cfg = DatabaseTestSupport.requireDirectOrPooledUrl();
        DatabaseTestSupport.withMigratedSchema(cfg, "velan_boot_ok", (conn, schema) -> {
            setSearchPath(conn, schema);
            SingleConnectionDataSource ds = new SingleConnectionDataSource(conn, true);
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            DataSourceTransactionManager txManager = new DataSourceTransactionManager(ds);
            String rawPassword = "BootAdminPass123";

            BootstrapAdminRunner runner = createRunner(
                    new BootstrapAdminProperties("admin.boot", rawPassword, "EMP-ADMIN-1", "System Admin", true),
                    jdbc, txManager);

            runner.run(new DefaultApplicationArguments());
            runner.run(new DefaultApplicationArguments()); // idempotent on second run

            Integer adminCount = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM users u
                    JOIN user_roles ur ON ur.user_id = u.id
                    JOIN roles r ON r.id = ur.role_id
                    WHERE r.name = 'ADMIN'
                    """, Integer.class);
            assertEquals(1, adminCount);

            var userRow = jdbc.queryForMap(
                    "SELECT login, password_hash, employee_id, name, active, must_change_password, password_changed_at "
                    + "FROM users WHERE login = 'admin.boot'");
            assertEquals("EMP-ADMIN-1", userRow.get("employee_id"));
            assertEquals("System Admin", userRow.get("name"));
            assertEquals(Boolean.TRUE, userRow.get("active"));
            assertEquals(Boolean.TRUE, userRow.get("must_change_password"));
            assertNull(userRow.get("password_changed_at"));
            assertTrue(passwordEncoder.matches(rawPassword, (String) userRow.get("password_hash")));

            Integer auditCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM audit_logs WHERE action = 'CREATE' AND entity_type = 'USER'",
                    Integer.class);
            assertEquals(1, auditCount);
            String details = jdbc.queryForObject(
                    "SELECT details::text FROM audit_logs WHERE action = 'CREATE' AND entity_type = 'USER'",
                    String.class);
            assertFalse(details.contains(rawPassword));
            assertFalse(details.contains("password_hash"));
            assertFalse(output.getOut().contains(rawPassword));
        });
    }

    @Test
    void doesNothingWhenAdminAlreadyExists() throws Exception {
        var cfg = DatabaseTestSupport.requireDirectOrPooledUrl();
        DatabaseTestSupport.withMigratedSchema(cfg, "velan_boot_exists", (conn, schema) -> {
            setSearchPath(conn, schema);
            SingleConnectionDataSource ds = new SingleConnectionDataSource(conn, true);
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            DataSourceTransactionManager txManager = new DataSourceTransactionManager(ds);

            UUID deptId = jdbc.queryForObject("SELECT id FROM departments WHERE code = 'SYSTEM'", UUID.class);
            UUID roleId = jdbc.queryForObject("SELECT id FROM roles WHERE name = 'ADMIN'", UUID.class);
            UUID existingId = UUID.randomUUID();
            jdbc.update("INSERT INTO users (id, name, login, password_hash, employee_id, department_id) "
                    + "VALUES (?, 'Existing', 'existing.admin', 'hash', 'EMP-EX', ?)", existingId, deptId);
            jdbc.update("INSERT INTO user_roles (id, user_id, role_id) VALUES (?, ?, ?)",
                    UUID.randomUUID(), existingId, roleId);

            BootstrapAdminRunner runner = createRunner(
                    new BootstrapAdminProperties("new.admin", "NewAdminPass123", "EMP-NEW", "New Admin", false),
                    jdbc, txManager);
            runner.run(new DefaultApplicationArguments());

            Integer totalUsers = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
            assertEquals(1, totalUsers);
            Integer auditCount = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs", Integer.class);
            assertEquals(0, auditCount);
        });
    }

    @Test
    void warnsWithoutValuesWhenConfigMissingAndRejectsWeakPassword(CapturedOutput output) throws Exception {
        var cfg = DatabaseTestSupport.requireDirectOrPooledUrl();
        DatabaseTestSupport.withMigratedSchema(cfg, "velan_boot_skip", (conn, schema) -> {
            setSearchPath(conn, schema);
            SingleConnectionDataSource ds = new SingleConnectionDataSource(conn, true);
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            DataSourceTransactionManager txManager = new DataSourceTransactionManager(ds);

            BootstrapAdminRunner missingRunner = createRunner(
                    new BootstrapAdminProperties("admin.only", "", "", "Admin Name", true),
                    jdbc, txManager);
            missingRunner.run(new DefaultApplicationArguments());
            assertTrue(output.getOut().contains("app.bootstrap.admin.password"));
            assertTrue(output.getOut().contains("app.bootstrap.admin.employee-id"));
            assertFalse(output.getOut().contains("admin.only"));

            String weakPass = "weak";
            BootstrapAdminRunner weakRunner = createRunner(
                    new BootstrapAdminProperties("admin.weak", weakPass, "EMP-W", "Weak Admin", true),
                    jdbc, txManager);
            weakRunner.run(new DefaultApplicationArguments());
            assertTrue(output.getOut().contains(PasswordPolicy.CODE_TOO_SHORT));
            assertFalse(output.getOut().contains(weakPass));

            Integer totalUsers = jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
            assertEquals(0, totalUsers);
        });
    }

    @Test
    void concurrentRunsCreateExactlyOneAdmin() throws Exception {
        var cfg = DatabaseTestSupport.requireDirectOrPooledUrl();
        DatabaseTestSupport.withMigratedSchema(cfg, "velan_boot_race", (conn, schema) -> {
            setSearchPath(conn, schema);
            var ds = new org.springframework.jdbc.datasource.DriverManagerDataSource(
                    cfg.jdbcUrl(), cfg.username(), cfg.password());
            ds.setSchema(schema);
            JdbcTemplate jdbc = new JdbcTemplate(ds);
            DataSourceTransactionManager txManager = new DataSourceTransactionManager(ds);

            BootstrapAdminRunner r1 = createRunner(
                    new BootstrapAdminProperties("race.admin", "RaceAdminPass1", "EMP-R1", "Race Admin", false),
                    jdbc, txManager);
            BootstrapAdminRunner r2 = createRunner(
                    new BootstrapAdminProperties("race.admin", "RaceAdminPass1", "EMP-R1", "Race Admin", false),
                    jdbc, txManager);

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch go = new CountDownLatch(1);
            try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
                Future<?> f1 = pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    r1.run(new DefaultApplicationArguments());
                    return null;
                });
                Future<?> f2 = pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    r2.run(new DefaultApplicationArguments());
                    return null;
                });
                ready.await();
                go.countDown();
                f1.get();
                f2.get();
            }

            Integer adminCount = jdbc.queryForObject("""
                    SELECT COUNT(*)
                    FROM users u
                    JOIN user_roles ur ON ur.user_id = u.id
                    JOIN roles r ON r.id = ur.role_id
                    WHERE r.name = 'ADMIN'
                    """, Integer.class);
            assertEquals(1, adminCount);
        });
    }

    private BootstrapAdminRunner createRunner(
            BootstrapAdminProperties props,
            JdbcTemplate jdbc,
            PlatformTransactionManager txManager) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(JdbcTemplate.class, () -> jdbc);
        ctx.registerBean(AuditProperties.class, () -> new AuditProperties(
                List.of("password", "passwordHash", "password_hash", "token", "secret")));
        ctx.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        ctx.registerBean(Clock.class, Clock::systemUTC);
        ctx.scan("com.velan.production.shared.audit");
        ctx.refresh();
        AuditService auditService = ctx.getBean(AuditService.class);
        return new BootstrapAdminRunner(
                props,
                passwordPolicy,
                passwordEncoder,
                auditService,
                providerOf(jdbc),
                providerOf(txManager));
    }

    private static void setSearchPath(Connection conn, String schema) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute("SET search_path TO " + schema);
        }
    }

    private static <T> ObjectProvider<T> providerOf(T instance) {
        return new ObjectProvider<>() {
            @Override public T getObject(Object... args) { return instance; }
            @Override public T getIfAvailable() { return instance; }
            @Override public T getIfUnique() { return instance; }
            @Override public T getObject() { return instance; }
        };
    }
}
