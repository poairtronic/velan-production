package com.velan.production.shared.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Objects;
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
            "sc_product_step_parameters", "sc_product_dynamic_values", "drawing_documents",
            "machines", "operation_machines", "pieces", "piece_operations",
            "piece_events", "operator_values", "qc_inspections", "measurement_values",
            "qc_exceptions", "pm_dispositions", "piece_replacement_lineage",
            "vendors", "vendor_dc_trackers", "user_notifications",
            "outbox_events", "rm_sync_history"
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

        String nonNullRawUrl = Objects.requireNonNull(rawUrl);
        String jdbcUrl = nonNullRawUrl.startsWith("jdbc:") ? nonNullRawUrl : "jdbc:" + nonNullRawUrl;
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
                assertEquals(17, first.migrationsExecuted, "Expected V001 through V017 migrations executed");

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

                    // 19. Duplicate serial_number rejected
                    UUID piece1Id = UUID.randomUUID();
                    st.execute("INSERT INTO pieces (id, sc_id, sc_product_id, serial_number, status) "
                            + "VALUES ('" + piece1Id + "', '" + sc1Id + "', '" + scProdId + "', 'SN-001', 'CREATED')");
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO pieces (id, sc_id, sc_product_id, serial_number, status) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc1Id + "', '" + scProdId + "', 'SN-001', 'CREATED')"));

                    // 20. Child row whose sc_id differs from parent's rejected (piece_operations -> pieces)
                    UUID poId = UUID.randomUUID();
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO piece_operations (id, sc_id, piece_id, snapshot_step_id, attempt_no, status) "
                            + "VALUES ('" + poId + "', '" + sc2Id + "', '" + piece1Id + "', '" + stepId + "', 1, 'PENDING')"));

                    // 21. Duplicate (piece_id, snapshot_step_id, attempt_no) rejected
                    st.execute("INSERT INTO piece_operations (id, sc_id, piece_id, snapshot_step_id, attempt_no, status) "
                            + "VALUES ('" + poId + "', '" + sc1Id + "', '" + piece1Id + "', '" + stepId + "', 1, 'PENDING')");
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO piece_operations (id, sc_id, piece_id, snapshot_step_id, attempt_no, status) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc1Id + "', '" + piece1Id + "', '" + stepId + "', 1, 'PENDING')"));

                    // 22. SUBMITTED value with two typed values rejected
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO operator_values (id, sc_id, piece_id, piece_operation_id, snapshot_step_id, step_parameter_id, status, val_numeric, val_bool, judged_target, result, submitted_by, submitted_at) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc1Id + "', '" + piece1Id + "', '" + poId + "', '" + stepId + "', '" + stepParamId + "', 'SUBMITTED', 5.0, true, 5.0, 'PASS', '" + userId + "', NOW())"));

                    // 23. SUBMITTED value without result rejected
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO operator_values (id, sc_id, piece_id, piece_operation_id, snapshot_step_id, step_parameter_id, status, val_numeric, judged_target, submitted_by, submitted_at) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc1Id + "', '" + piece1Id + "', '" + poId + "', '" + stepId + "', '" + stepParamId + "', 'SUBMITTED', 5.0, 5.0, '" + userId + "', NOW())"));

                    // 24. Allow DRAFT -> SUBMITTED, then reject UPDATE and DELETE without purge setting
                    UUID opValId = UUID.randomUUID();
                    st.execute("INSERT INTO operator_values (id, sc_id, piece_id, piece_operation_id, snapshot_step_id, step_parameter_id, status, val_numeric) "
                            + "VALUES ('" + opValId + "', '" + sc1Id + "', '" + piece1Id + "', '" + poId + "', '" + stepId + "', '" + stepParamId + "', 'DRAFT', 7.5)");
                    st.execute("UPDATE operator_values SET status = 'SUBMITTED', judged_target = 7.5, result = 'PASS', submitted_by = '" + userId + "', submitted_at = NOW() WHERE id = '" + opValId + "'");

                    assertThrows(SQLException.class, () -> st.execute(
                            "UPDATE operator_values SET val_numeric = 9.9 WHERE id = '" + opValId + "'"));
                    assertThrows(SQLException.class, () -> st.execute(
                            "DELETE FROM operator_values WHERE id = '" + opValId + "'"));

                    // 25. Allow DELETE of SUBMITTED row inside transaction after SET LOCAL velan.purge_active = 'true'
                    conn.setAutoCommit(false);
                    try {
                        st.execute("SET LOCAL velan.purge_active = 'true'");
                        int deleted = st.executeUpdate("DELETE FROM operator_values WHERE id = '" + opValId + "'");
                        assertEquals(1, deleted);
                        conn.commit();
                    } finally {
                        conn.setAutoCommit(true);
                    }

                    // 26. qc_exception whose sc_id differs from parent piece rejected
                    UUID excId = UUID.randomUUID();
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO qc_exceptions (id, sc_id, piece_id, piece_operation_id, exception_type, reason, status, ecr_status, raised_by) "
                            + "VALUES ('" + excId + "', '" + sc2Id + "', '" + piece1Id + "', '" + poId + "', 'QC_FAIL', 'bad', 'PENDING_PM_DISPOSITION', 'PENDING', '" + userId + "')"));

                    // 27. Duplicate replacement piece in piece_replacement_lineage rejected
                    UUID piece2Id = UUID.randomUUID();
                    UUID piece3Id = UUID.randomUUID();
                    st.execute("INSERT INTO pieces (id, sc_id, sc_product_id, serial_number, status) "
                            + "VALUES ('" + piece2Id + "', '" + sc1Id + "', '" + scProdId + "', 'SN-002', 'CREATED')");
                    st.execute("INSERT INTO pieces (id, sc_id, sc_product_id, serial_number, status) "
                            + "VALUES ('" + piece3Id + "', '" + sc1Id + "', '" + scProdId + "', 'SN-003', 'CREATED')");
                    st.execute("INSERT INTO qc_exceptions (id, sc_id, piece_id, piece_operation_id, exception_type, reason, status, ecr_status, raised_by) "
                            + "VALUES ('" + excId + "', '" + sc1Id + "', '" + piece1Id + "', '" + poId + "', 'QC_FAIL', 'bad', 'PENDING_PM_DISPOSITION', 'PENDING', '" + userId + "')");
                    st.execute("INSERT INTO piece_replacement_lineage (id, sc_id, qc_exception_id, original_piece_id, replacement_piece_id, created_by) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc1Id + "', '" + excId + "', '" + piece1Id + "', '" + piece2Id + "', '" + userId + "')");
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO piece_replacement_lineage (id, sc_id, qc_exception_id, original_piece_id, replacement_piece_id, created_by) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc1Id + "', '" + excId + "', '" + piece3Id + "', '" + piece2Id + "', '" + userId + "')"));

                    // 28. Invalid DC status & duplicate (vendor_id, dc_number) rejected
                    UUID vendorId = UUID.randomUUID();
                    st.execute("INSERT INTO vendors (id, code, name) VALUES ('" + vendorId + "', 'V1', 'Vendor 1')");
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO vendor_dc_trackers (id, sc_id, vendor_id, dc_number, quantity_sent, status) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc1Id + "', '" + vendorId + "', 'DC-1', 5, 'UNKNOWN')"));
                    UUID trackerSc2Id = UUID.randomUUID();
                    st.execute("INSERT INTO vendor_dc_trackers (id, sc_id, vendor_id, dc_number, quantity_sent, status) "
                            + "VALUES ('" + trackerSc2Id + "', '" + sc2Id + "', '" + vendorId + "', 'DC-1', 5, 'NOT_RECEIVED')");
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO vendor_dc_trackers (id, sc_id, vendor_id, dc_number, quantity_sent, status) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc1Id + "', '" + vendorId + "', 'DC-1', 3, 'PARTIAL')"));

                    // 29. piece_operation pointing at a tracker of another SC rejected
                    assertThrows(SQLException.class, () -> st.execute(
                            "UPDATE piece_operations SET vendor_dc_tracker_id = '" + trackerSc2Id + "' WHERE id = '" + poId + "'"));

                    // 30. Invalid outbox status rejected
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload, status) "
                            + "VALUES ('" + UUID.randomUUID() + "', 'QC_EXCEPTION', '" + excId + "', 'CREATED', '{}'::jsonb, 'RETRYING')"));

                    // 31. Allow user_notification and outbox_event with sc_id pointing at no existing SC
                    UUID ghostScId = UUID.randomUUID();
                    st.execute("INSERT INTO user_notifications (id, user_id, type, title, body, entity_type, entity_id, sc_id) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + userId + "', 'INFO', 'Title', 'Body', 'SC', '" + ghostScId + "', '" + ghostScId + "')");
                    st.execute("INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload, status, sc_id) "
                            + "VALUES ('" + UUID.randomUUID() + "', 'QC_EXCEPTION', '" + excId + "', 'CREATED', '{}'::jsonb, 'PENDING', '" + ghostScId + "')");

                    // 32. Role seed check: 6 roles, >= 1 permission each, 0 users in fresh DB (before test users)
                    try (ResultSet rsRoles = st.executeQuery("SELECT COUNT(*) FROM roles")) {
                        assertTrue(rsRoles.next());
                        assertEquals(6, rsRoles.getInt(1), "Expected 6 seeded roles");
                    }
                    try (ResultSet rsPerms = st.executeQuery(
                            "SELECT COUNT(*) FROM roles r WHERE NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id)")) {
                        assertTrue(rsPerms.next());
                        assertEquals(0, rsPerms.getInt(1), "Every seeded role must have at least one permission");
                    }

                    // 33. Guard test: every table with sc_id must be in PURGED_SC_TABLES or EXEMPT_SC_TABLES
                    List<String> purgedScTables = List.of(
                            "measurement_values", "qc_inspections", "pm_dispositions",
                            "piece_replacement_lineage", "qc_exceptions", "operator_values",
                            "piece_events", "piece_operations", "pieces",
                            "sc_product_dynamic_values", "sc_product_step_parameters",
                            "sc_product_node_statuses", "sc_product_template_steps",
                            "drawing_documents", "vendor_dc_trackers", "sc_revisions",
                            "import_records", "sc_products", "files"
                    );
                    List<String> exemptScTables = List.of(
                            "audit_logs", "sc_archive_summary", "user_notifications",
                            "outbox_events", "rm_sync_history"
                    );
                    try (ResultSet rsScCols = st.executeQuery(
                            "SELECT table_name FROM information_schema.columns WHERE table_schema = '" + schema + "' AND column_name = 'sc_id'")) {
                        while (rsScCols.next()) {
                            String tbl = rsScCols.getString(1);
                            assertTrue(purgedScTables.contains(tbl) || exemptScTables.contains(tbl),
                                    "Table with sc_id is neither purged nor exempt: " + tbl);
                        }
                    }

                    // 34. Complete SC graph + purge refusals + purge_sc execution
                    UUID poHdrId = UUID.randomUUID();
                    st.execute("INSERT INTO purchase_orders (id, po_number, source, created_by) "
                            + "VALUES ('" + poHdrId + "', 'PO-1', 'MANUAL', '" + userId + "')");
                    st.execute("UPDATE sc_headers SET po_id = '" + poHdrId + "' WHERE id = '" + sc1Id + "'");

                    // Refuse purge when SC is not ARCHIVED
                    assertThrows(SQLException.class, () -> st.execute("SELECT * FROM purge_sc('" + sc1Id + "')"));

                    // Refuse purge when ARCHIVED without PURGE_APPROVED summary
                    st.execute("UPDATE sc_headers SET status = 'ARCHIVED' WHERE id = '" + sc1Id + "'");
                    assertThrows(SQLException.class, () -> st.execute("SELECT * FROM purge_sc('" + sc1Id + "')"));

                    // Populate remaining SC-owned rows (submitted operator_value, event, qc, disposition, files, drawing, revision, import, node status, scoped_piece_id)
                    UUID scFileId = UUID.randomUUID();
                    UUID archiveExcelId = UUID.randomUUID();
                    UUID archivePdfId = UUID.randomUUID();
                    st.execute("INSERT INTO files (id, sc_id, storage_key, file_name, content_type, size_bytes, checksum_sha256, uploaded_by) "
                            + "VALUES ('" + scFileId + "', '" + sc1Id + "', 'sc1/sig.png', 'sig.png', 'image/png', 42, '" + "b".repeat(64) + "', '" + userId + "')");
                    st.execute("INSERT INTO files (id, sc_id, storage_key, file_name, content_type, size_bytes, checksum_sha256, uploaded_by) "
                            + "VALUES ('" + archiveExcelId + "', NULL, 'arch/sc1.xlsx', 'sc1.xlsx', 'application/vnd.ms-excel', 100, '" + "c".repeat(64) + "', '" + userId + "')");
                    st.execute("INSERT INTO files (id, sc_id, storage_key, file_name, content_type, size_bytes, checksum_sha256, uploaded_by) "
                            + "VALUES ('" + archivePdfId + "', NULL, 'arch/sc1.pdf', 'sc1.pdf', 'application/pdf', 200, '" + "d".repeat(64) + "', '" + userId + "')");

                    st.execute("INSERT INTO drawing_documents (id, product_id, kind, file_id, created_by) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + prodId + "', 'PRODUCT_DRAWING', '" + fileId + "', '" + userId + "')");
                    st.execute("INSERT INTO drawing_documents (id, sc_id, product_id, sc_product_id, kind, file_id, created_by) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc1Id + "', '" + prodId + "', '" + scProdId + "', 'SC_DRAWING', '" + scFileId + "', '" + userId + "')");
                    st.execute("INSERT INTO sc_product_node_statuses (id, sc_id, sc_product_id, snapshot_step_id, node_status, updated_by) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc1Id + "', '" + scProdId + "', '" + stepId + "', 'DONE', '" + userId + "')");
                    st.execute("UPDATE sc_product_template_steps SET scoped_piece_id = '" + piece1Id + "' WHERE id = '" + stepId + "'");
                    st.execute("INSERT INTO operator_values (id, sc_id, piece_id, piece_operation_id, snapshot_step_id, step_parameter_id, status, val_numeric, judged_target, result, submitted_by, submitted_at) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc1Id + "', '" + piece1Id + "', '" + poId + "', '" + stepId + "', '" + stepParamId + "', 'SUBMITTED', 7.5, 7.5, 'PASS', '" + userId + "', NOW())");
                    st.execute("INSERT INTO piece_events (id, sc_id, piece_id, piece_operation_id, event_type, actor_id) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc1Id + "', '" + piece1Id + "', '" + poId + "', 'COMPLETED', '" + userId + "')");
                    UUID qcInspId = UUID.randomUUID();
                    st.execute("INSERT INTO qc_inspections (id, sc_id, piece_id, piece_operation_id, inspector_id, decision) "
                            + "VALUES ('" + qcInspId + "', '" + sc1Id + "', '" + piece1Id + "', '" + poId + "', '" + userId + "', 'PASSED')");
                    st.execute("INSERT INTO measurement_values (id, sc_id, qc_inspection_id, piece_id, piece_operation_id, snapshot_step_id, step_parameter_id, val_numeric, judged_target, result, recorded_by) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc1Id + "', '" + qcInspId + "', '" + piece1Id + "', '" + poId + "', '" + stepId + "', '" + stepParamId + "', 7.5, 7.5, 'PASS', '" + userId + "')");
                    st.execute("INSERT INTO pm_dispositions (id, sc_id, qc_exception_id, piece_id, piece_operation_id, disposition, reason, decided_by) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc1Id + "', '" + excId + "', '" + piece1Id + "', '" + poId + "', 'CONTINUE', 'ok', '" + userId + "')");
                    st.execute("INSERT INTO sc_revisions (id, sc_id, revision_no, summary, created_by) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc1Id + "', 1, 'rev1', '" + userId + "')");
                    st.execute("INSERT INTO import_records (id, sc_id, source, imported_by) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc1Id + "', 'EXCEL', '" + userId + "')");
                    st.execute("INSERT INTO sc_archive_summary (id, sc_id, sc_number, status, excel_file_id, pdf_file_id, piece_count, operation_count, value_count, approved_by, approved_at) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc1Id + "', 'SC-100', 'PURGE_APPROVED', '" + archiveExcelId + "', '" + archivePdfId + "', 3, 1, 1, '" + userId + "', NOW())");

                    // Execute purge_sc
                    st.execute("SELECT * FROM purge_sc('" + sc1Id + "')");

                    // Verify 0 rows remain with sc_id = sc1Id in all purged tables + sc_headers
                    for (String tbl : purgedScTables) {
                        try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tbl + " WHERE sc_id = '" + sc1Id + "'")) {
                            assertTrue(rs.next());
                            assertEquals(0, rs.getInt(1), "Expected 0 rows for purged SC in " + tbl);
                        }
                    }
                    try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM sc_headers WHERE id = '" + sc1Id + "'")) {
                        assertTrue(rs.next());
                        assertEquals(0, rs.getInt(1));
                    }

                    // Verify kept records & STORAGE_DELETE outbox event
                    try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM purchase_orders WHERE id = '" + poHdrId + "'")) {
                        assertTrue(rs.next());
                        assertEquals(1, rs.getInt(1));
                    }
                    try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM audit_logs WHERE sc_id = '" + sc1Id + "' AND action = 'SC_PURGED'")) {
                        assertTrue(rs.next());
                        assertEquals(1, rs.getInt(1));
                    }
                    try (ResultSet rs = st.executeQuery("SELECT status FROM sc_archive_summary WHERE sc_id = '" + sc1Id + "'")) {
                        assertTrue(rs.next());
                        assertEquals("PURGED", rs.getString(1));
                    }
                    try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM drawing_documents WHERE kind = 'PRODUCT_DRAWING'")) {
                        assertTrue(rs.next());
                        assertEquals(1, rs.getInt(1));
                    }
                    try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM outbox_events WHERE sc_id = '" + sc1Id + "' AND event_type = 'STORAGE_DELETE'")) {
                        assertTrue(rs.next());
                        assertEquals(1, rs.getInt(1));
                    }

                    // 35. V017 Phase 2 status alignment rejections across all updated tables
                    UUID scProd2Id = UUID.randomUUID();
                    st.execute("INSERT INTO sc_products (id, sc_id, product_id, quantity) "
                            + "VALUES ('" + scProd2Id + "', '" + sc2Id + "', '" + prodId + "', 2)");
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO sc_headers (id, sc_number, source, status, created_by) "
                            + "VALUES ('" + UUID.randomUUID() + "', 'SC-BAD-1', 'MANUAL', 'DONE', '" + userId + "')"));
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO pieces (id, sc_id, sc_product_id, serial_number, status) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc2Id + "', '" + scProd2Id + "', 'SN-BAD-1', 'UNKNOWN_STATUS')"));
                    UUID pieceSc2Id = UUID.randomUUID();
                    st.execute("INSERT INTO pieces (id, sc_id, sc_product_id, serial_number, status) "
                            + "VALUES ('" + pieceSc2Id + "', '" + sc2Id + "', '" + scProd2Id + "', 'SN-SC2-1', 'IN_PRODUCTION_EXTRA_PROCESS')");
                    UUID stepSc2Id = UUID.randomUUID();
                    st.execute("INSERT INTO sc_product_template_steps (id, sc_id, sc_product_id, step_order, node_type) "
                            + "VALUES ('" + stepSc2Id + "', '" + sc2Id + "', '" + scProd2Id + "', 1, 'DESIGN')");
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO piece_operations (id, sc_id, piece_id, snapshot_step_id, attempt_no, status) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc2Id + "', '" + pieceSc2Id + "', '" + stepSc2Id + "', 1, 'INVALID_OP_STATUS')"));
                    UUID poSc2Id = UUID.randomUUID();
                    st.execute("INSERT INTO piece_operations (id, sc_id, piece_id, snapshot_step_id, attempt_no, status) "
                            + "VALUES ('" + poSc2Id + "', '" + sc2Id + "', '" + pieceSc2Id + "', '" + stepSc2Id + "', 1, 'QC_IN_PROGRESS')");
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO qc_inspections (id, sc_id, piece_id, piece_operation_id, inspector_id, decision) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc2Id + "', '" + pieceSc2Id + "', '" + poSc2Id + "', '" + userId + "', 'PASS')"));
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO qc_exceptions (id, sc_id, piece_id, piece_operation_id, exception_type, reason, status, ecr_status, raised_by) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc2Id + "', '" + pieceSc2Id + "', '" + poSc2Id + "', 'QC_FAIL', 'bad', 'OPEN', 'PENDING', '" + userId + "')"));
                    UUID excSc2Id = UUID.randomUUID();
                    st.execute("INSERT INTO qc_exceptions (id, sc_id, piece_id, piece_operation_id, exception_type, reason, status, ecr_status, raised_by) "
                            + "VALUES ('" + excSc2Id + "', '" + sc2Id + "', '" + pieceSc2Id + "', '" + poSc2Id + "', 'QC_FAIL', 'bad', 'PENDING_PM_DISPOSITION', 'PENDING', '" + userId + "')");
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO pm_dispositions (id, sc_id, qc_exception_id, piece_id, piece_operation_id, disposition, reason, decided_by) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc2Id + "', '" + excSc2Id + "', '" + pieceSc2Id + "', '" + poSc2Id + "', 'RETURN', 'rework', '" + userId + "')"));
                    st.execute("INSERT INTO pm_dispositions (id, sc_id, qc_exception_id, piece_id, piece_operation_id, disposition, reason, decided_by) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + sc2Id + "', '" + excSc2Id + "', '" + pieceSc2Id + "', '" + poSc2Id + "', 'RETURN_TO_EARLIER_OPERATION', 'rework step 1', '" + userId + "')");
                    assertThrows(SQLException.class, () -> st.execute(
                            "INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, payload, status) "
                            + "VALUES ('" + UUID.randomUUID() + "', 'QC_EXCEPTION', '" + excSc2Id + "', 'CREATED', '{}'::jsonb, 'SENT')"));

                    // 36. SC cancellation constraints + cancel -> archive -> purge_sc lifecycle
                    UUID scCancelId = UUID.randomUUID();
                    st.execute("INSERT INTO sc_headers (id, sc_number, source, status, created_by) "
                            + "VALUES ('" + scCancelId + "', 'SC-CANCEL-1', 'MANUAL', 'DRAFT_INTAKE', '" + userId + "')");
                    assertThrows(SQLException.class, () -> st.execute(
                            "UPDATE sc_headers SET status = 'CANCELLED', cancelled_by = '" + userId + "', cancelled_at = NOW() WHERE id = '" + scCancelId + "'"));
                    assertThrows(SQLException.class, () -> st.execute(
                            "UPDATE sc_headers SET status = 'CANCELLED', cancel_reason = 'Valid reason > 10 chars', cancelled_at = NOW() WHERE id = '" + scCancelId + "'"));
                    assertThrows(SQLException.class, () -> st.execute(
                            "UPDATE sc_headers SET status = 'CANCELLED', cancel_reason = 'Too short', cancelled_by = '" + userId + "', cancelled_at = NOW() WHERE id = '" + scCancelId + "'"));
                    st.execute("UPDATE sc_headers SET status = 'CANCELLED', cancel_reason = 'Customer terminated order', cancelled_by = '" + userId + "', cancelled_at = NOW() WHERE id = '" + scCancelId + "'");
                    st.execute("UPDATE sc_headers SET status = 'ARCHIVED' WHERE id = '" + scCancelId + "'");
                    st.execute("INSERT INTO sc_archive_summary (id, sc_id, sc_number, status, excel_file_id, pdf_file_id, piece_count, operation_count, value_count, approved_by, approved_at) "
                            + "VALUES ('" + UUID.randomUUID() + "', '" + scCancelId + "', 'SC-CANCEL-1', 'PURGE_APPROVED', '" + archiveExcelId + "', '" + archivePdfId + "', 0, 0, 0, '" + userId + "', NOW())");
                    st.execute("SELECT * FROM purge_sc('" + scCancelId + "')");
                    try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM sc_headers WHERE id = '" + scCancelId + "'")) {
                        assertTrue(rs.next());
                        assertEquals(0, rs.getInt(1));
                    }
                    try (ResultSet rs = st.executeQuery("SELECT status FROM sc_archive_summary WHERE sc_id = '" + scCancelId + "'")) {
                        assertTrue(rs.next());
                        assertEquals("PURGED", rs.getString(1));
                    }
                }
            } finally {
                try (Statement st = conn.createStatement()) {
                    st.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
                }
            }
        }
    }
}
