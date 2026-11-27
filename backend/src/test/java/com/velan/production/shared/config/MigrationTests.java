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
            "departments", "sections", "roles", "role_permissions", "users", "user_roles",
            "user_permission_overrides", "user_sections",
            "parameters", "parameter_options", "checklists", "checklist_parameters",
            "operations", "operation_parameters", "operation_checklists",
            "process_templates", "products", "process_template_steps",
            "purchase_orders", "sc_headers", "sc_products", "sc_revisions",
            "import_records", "sc_archive_summary",
            "sc_product_template_steps", "sc_product_node_statuses",
            "sc_product_step_parameters", "sc_product_dynamic_values", "drawing_documents"
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
                assertEquals(6, first.migrationsExecuted, "Expected V001 through V006 migrations executed");

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

                    // 7. STATIC NUMERIC parameter without expected_numeric rejected
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO parameters (id, code, name, kind, data_type) "
                            + "VALUES ('" + UUID.randomUUID() + "', 'P_NUM_NULL', 'NumNull', 'STATIC', 'NUMERIC')"));

                    // 8. expected_option_id belonging to another parameter rejected
                    UUID otherSelParamId = UUID.randomUUID();
                    UUID otherOptId = UUID.randomUUID();
                    st.execute("INSERT INTO parameters (id, code, name, kind, data_type) "
                            + "VALUES ('" + otherSelParamId + "', 'P_SEL2', 'Sel2', 'STATIC', 'SINGLE_SELECT')");
                    st.execute("INSERT INTO parameter_options (id, parameter_id, code, label, position) "
                            + "VALUES ('" + otherOptId + "', '" + otherSelParamId + "', 'OPT_B', 'Option B', 1)");
                    assertThrows(SQLException.class, () -> st.execute(
                            "UPDATE parameters SET expected_option_id = '" + otherOptId + "' WHERE id = '" + selParamId + "'"));

                    // 9. Duplicate product name differing only by case or spaces rejected
                    UUID userId = UUID.randomUUID();
                    st.execute("INSERT INTO users (id, name, login, password_hash, employee_id, department_id) "
                            + "VALUES ('" + userId + "', 'U3', 'user3', 'hash3', 'EMP-3', '" + deptId + "')");
                    UUID prodId = UUID.randomUUID();
                    st.execute("INSERT INTO products (id, name) VALUES ('" + prodId + "', 'Shaft A')");
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO products (id, name) VALUES ('" + UUID.randomUUID() + "', '  shaft a ')"));

                    // 10. OPERATION step without operation_id rejected
                    UUID tplId = UUID.randomUUID();
                    st.execute("INSERT INTO process_templates (id, code, name) VALUES ('" + tplId + "', 'TPL1', 'Template 1')");
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO process_template_steps (id, template_id, position, node_type) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + tplId + "', 1, 'OPERATION')"));

                    // 11. Duplicate sc_number rejected
                    UUID sc1Id = UUID.randomUUID();
                    UUID sc2Id = UUID.randomUUID();
                    st.execute("INSERT INTO sc_headers (id, sc_number, source, status, created_by) "
                            + "VALUES ('" + sc1Id + "', 'SC-100', 'MANUAL', 'ACTIVE', '" + userId + "')");
                    st.execute("INSERT INTO sc_headers (id, sc_number, source, status, created_by) "
                            + "VALUES ('" + sc2Id + "', 'SC-200', 'MANUAL', 'ACTIVE', '" + userId + "')");
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO sc_headers (id, sc_number, source, status, created_by) "
                            + "VALUES ('" + UUID.randomUUID() + "', 'SC-100', 'MANUAL', 'ACTIVE', '" + userId + "')"));

                    // 12. sc_products quantity 0 rejected
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO sc_products (id, sc_id, product_id, quantity) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc1Id + "', '" + prodId + "', 0)"));

                    // 13. Duplicate (sc_id, product_id) rejected
                    UUID scProdId = UUID.randomUUID();
                    st.execute("INSERT INTO sc_products (id, sc_id, product_id, quantity) "
                            + "VALUES ('" + scProdId + "', '" + sc1Id + "', '" + prodId + "', 5)");
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO sc_products (id, sc_id, product_id, quantity) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc1Id + "', '" + prodId + "', 10)"));

                    // 14. Child row whose sc_id differs from parent's rejected
                    UUID stepId = UUID.randomUUID();
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO sc_product_template_steps (id, sc_id, sc_product_id, step_order, node_type) "
                            + "VALUES ('" + stepId + "', '" + sc2Id + "', '" + scProdId + "', 1, 'DESIGN')"));

                    st.execute("INSERT INTO sc_product_template_steps (id, sc_id, sc_product_id, step_order, node_type) "
                            + "VALUES ('" + stepId + "', '" + sc1Id + "', '" + scProdId + "', 1, 'DESIGN')");
                    UUID stepParamId = UUID.randomUUID();
                    st.execute("INSERT INTO sc_product_step_parameters (id, sc_id, sc_product_id, snapshot_step_id, code, name, kind, data_type, param_order) "
                            + "VALUES ('" + stepParamId + "', '" + sc1Id + "', '" + scProdId + "', '" + stepId + "', 'P1', 'Param 1', 'DYNAMIC', 'NUMERIC', 1)");

                    // 15. Dynamic value with min > max rejected
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO sc_product_dynamic_values (id, sc_id, sc_product_id, snapshot_step_id, step_parameter_id, min_value, max_value, target_value, updated_by) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc1Id + "', '" + scProdId + "', '" + stepId + "', '" + stepParamId + "', 10.0, 5.0, 7.5, '" + userId + "')"));

                    // 16. Duplicate (snapshot_step_id, step_parameter_id) rejected
                    st.execute("INSERT INTO sc_product_dynamic_values (id, sc_id, sc_product_id, snapshot_step_id, step_parameter_id, min_value, max_value, target_value, updated_by) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc1Id + "', '" + scProdId + "', '" + stepId + "', '" + stepParamId + "', 5.0, 10.0, 7.5, '" + userId + "')");
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO sc_product_dynamic_values (id, sc_id, sc_product_id, snapshot_step_id, step_parameter_id, min_value, max_value, target_value, updated_by) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc1Id + "', '" + scProdId + "', '" + stepId + "', '" + stepParamId + "', 5.0, 10.0, 7.5, '" + userId + "')"));

                    // 17 & 18. PRODUCT_DRAWING with sc_id rejected, SC_DRAWING without sc_id rejected
                    UUID fileId = UUID.randomUUID();
                    st.execute("INSERT INTO files (id, storage_key, file_name, content_type, size_bytes, checksum_sha256, uploaded_by) "
                            + "VALUES ('" + fileId + "', 'k1', 'd.pdf', 'application/pdf', 100, '" + "a".repeat(64) + "', '" + userId + "')");
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO drawing_documents (id, sc_id, product_id, kind, file_id, created_by) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc1Id + "', '" + prodId + "', 'PRODUCT_DRAWING', '" + fileId + "', '" + userId + "')"));
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO drawing_documents (id, product_id, kind, file_id, created_by) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + prodId + "', 'SC_DRAWING', '" + fileId + "', '" + userId + "')"));
                }
            } finally {
                try (Statement st = conn.createStatement()) {
                    st.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
                }
            }
        }
    }
}
