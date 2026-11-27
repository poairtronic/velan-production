package com.velan.production.shared.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationTests {

    private static final List<String> EXPECTED_TABLES = List.of(
            "files", "audit_logs", "shedlock", "idempotency_keys",
            "departments", "sections", "roles", "users", "user_roles",
            "user_permission_overrides", "user_sections",
            "parameters", "parameter_options", "checklists", "checklist_parameters",
            "operations", "operation_parameters", "operation_checklists"
    );

    @Test
    void flywayMigrationsApplyValidateAndEnforceConstraints() throws Exception {
        String rawUrl = System.getenv("DATABASE_DIRECT_URL");
        if (rawUrl == null || rawUrl.isBlank()) {
            rawUrl = System.getProperty("DATABASE_DIRECT_URL");
        }
        boolean hasUrl = rawUrl != null && !rawUrl.isBlank()
                && !rawUrl.contains("USER:PASSWORD")
                && !rawUrl.contains("ep-xxx")
                && !rawUrl.contains("placeholder");
        Assumptions.assumeTrue(hasUrl, "DATABASE_DIRECT_URL is not configured; skipping MigrationTests.");

        String jdbcUrl = rawUrl.startsWith("jdbc:") ? rawUrl : "jdbc:" + rawUrl;
        String dbUser = System.getenv("DB_USER");
        String dbPass = System.getenv("DB_PASSWORD");
        String schema = "velan_mig_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        try (Connection conn = (dbUser != null && !dbUser.isBlank())
                ? DriverManager.getConnection(jdbcUrl, dbUser, dbPass != null ? dbPass : "")
                : DriverManager.getConnection(jdbcUrl)) {
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE SCHEMA " + schema);
            }
            try {
                Flyway flyway = Flyway.configure()
                        .dataSource(jdbcUrl, dbUser, dbPass)
                        .schemas(schema)
                        .locations("classpath:db/migration")
                        .validateOnMigrate(true)
                        .cleanDisabled(true)
                        .baselineOnMigrate(false)
                        .load();

                MigrateResult first = flyway.migrate();
                assertEquals(3, first.migrationsExecuted, "Expected V001, V002, V003 migrations executed");

                for (String table : EXPECTED_TABLES) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?")) {
                        ps.setString(1, schema);
                        ps.setString(2, table);
                        try (ResultSet rs = ps.executeQuery()) {
                            assertTrue(rs.next(), "Expected table " + table + " in schema " + schema);
                        }
                    }
                }

                MigrateResult second = flyway.migrate();
                assertEquals(0, second.migrationsExecuted, "Second migrate must be a no-op");
                flyway.validate();

                try (Statement st = conn.createStatement()) {
                    st.execute("SET search_path TO " + schema);

                    UUID deptId = UUID.randomUUID();
                    UUID secId = UUID.randomUUID();
                    st.execute("INSERT INTO departments (id, code, name) VALUES ('" + deptId + "', 'D1', 'Dept 1')");
                    st.execute("INSERT INTO sections (id, code, name) VALUES ('" + secId + "', 'S1', 'Sec 1')");
                    st.execute("INSERT INTO users (id, name, login, password_hash, employee_id, department_id) "
                            + "VALUES ('" + UUID.randomUUID() + "', 'U1', 'Admin1', 'hash1', 'EMP-1', '" + deptId + "')");

                    // 1. Duplicate login (different case) rejected
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO users (id, name, login, password_hash, employee_id, department_id) "
                            + "VALUES ('" + UUID.randomUUID() + "', 'U2', 'admin1', 'hash2', 'EMP-2', '" + deptId + "')"));

                    // 2. DYNAMIC parameter with expected value rejected
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO parameters (id, code, name, kind, data_type, expected_numeric) "
                            + "VALUES ('" + UUID.randomUUID() + "', 'P_DYN', 'Dyn', 'DYNAMIC', 'NUMERIC', 1.5)"));

                    // 3. STATIC NUMERIC parameter with expected_bool rejected
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO parameters (id, code, name, kind, data_type, expected_bool) "
                            + "VALUES ('" + UUID.randomUUID() + "', 'P_STAT', 'Stat', 'STATIC', 'NUMERIC', true)"));

                    // 4. Duplicate option code on one parameter rejected
                    UUID selParamId = UUID.randomUUID();
                    st.execute("INSERT INTO parameters (id, code, name, kind, data_type) "
                            + "VALUES ('" + selParamId + "', 'P_SEL', 'Sel', 'STATIC', 'SINGLE_SELECT')");
                    st.execute("INSERT INTO parameter_options (id, parameter_id, code, label, position) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + selParamId + "', 'OPT_A', 'Option A', 1)");
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO parameter_options (id, parameter_id, code, label, position) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + selParamId + "', 'OPT_A', 'Option A2', 2)"));

                    // 5. Invalid operation kind rejected
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO operations (id, code, name, section_id, kind) "
                            + "VALUES ('" + UUID.randomUUID() + "', 'OP_BAD', 'Bad Op', '" + secId + "', 'OUTSOURCED')"));

                    // 6. Duplicate parameter in one checklist rejected
                    UUID clId = UUID.randomUUID();
                    st.execute("INSERT INTO checklists (id, code, name) VALUES ('" + clId + "', 'CL1', 'Checklist 1')");
                    st.execute("INSERT INTO checklist_parameters (id, checklist_id, parameter_id, position) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + clId + "', '" + selParamId + "', 1)");
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO checklist_parameters (id, checklist_id, parameter_id, position) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + clId + "', '" + selParamId + "', 2)"));
                }
            } finally {
                try (Statement st = conn.createStatement()) {
                    st.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
                }
            }
        }
    }
}
