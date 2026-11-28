package com.velan.production.shared.persistence;

import com.velan.production.engineering.internal.domain.OperationKind;
import com.velan.production.engineering.internal.domain.ParameterDataType;
import com.velan.production.engineering.internal.domain.ParameterKind;
import com.velan.production.engineering.internal.domain.TemplateNodeType;
import com.velan.production.execution.internal.domain.DrawingKind;
import com.velan.production.execution.internal.domain.OperatorValueResult;
import com.velan.production.execution.internal.domain.OperatorValueStatus;
import com.velan.production.execution.internal.domain.PieceOperationStatus;
import com.velan.production.execution.internal.domain.PieceStatus;
import com.velan.production.iam.internal.domain.PermissionEffect;
import com.velan.production.integration.internal.domain.OutboxEventStatus;
import com.velan.production.integration.internal.domain.RmSyncStatus;
import com.velan.production.order.internal.domain.ImportSource;
import com.velan.production.order.internal.domain.IntakeSource;
import com.velan.production.order.internal.domain.ScArchiveStatus;
import com.velan.production.order.internal.domain.ScStatus;
import com.velan.production.qcexception.internal.domain.EcrSyncStatus;
import com.velan.production.qcexception.internal.domain.PmDispositionType;
import com.velan.production.qcexception.internal.domain.QcExceptionStatus;
import com.velan.production.qcexception.internal.domain.QcExceptionType;
import com.velan.production.quality.internal.domain.MeasurementResult;
import com.velan.production.quality.internal.domain.QcInspectionDecision;
import com.velan.production.vendor.internal.domain.VendorDcStatus;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class EnumToDatabaseTests {

    private static final Pattern QUOTED_VALUE = Pattern.compile("'([^']+)'");

    private record EnumCheckMapping(String table, String column, Class<? extends Enum<?>> enumClass) {}

    private static final List<EnumCheckMapping> MAPPINGS = List.of(
            new EnumCheckMapping("user_permission_overrides", "effect", PermissionEffect.class),
            new EnumCheckMapping("parameters", "kind", ParameterKind.class),
            new EnumCheckMapping("parameters", "data_type", ParameterDataType.class),
            new EnumCheckMapping("operations", "kind", OperationKind.class),
            new EnumCheckMapping("process_template_steps", "node_type", TemplateNodeType.class),
            new EnumCheckMapping("purchase_orders", "source", IntakeSource.class),
            new EnumCheckMapping("sc_headers", "source", IntakeSource.class),
            new EnumCheckMapping("sc_headers", "status", ScStatus.class),
            new EnumCheckMapping("import_records", "source", ImportSource.class),
            new EnumCheckMapping("sc_archive_summary", "status", ScArchiveStatus.class),
            new EnumCheckMapping("sc_product_template_steps", "node_type", TemplateNodeType.class),
            new EnumCheckMapping("sc_product_step_parameters", "kind", ParameterKind.class),
            new EnumCheckMapping("sc_product_step_parameters", "data_type", ParameterDataType.class),
            new EnumCheckMapping("drawing_documents", "kind", DrawingKind.class),
            new EnumCheckMapping("pieces", "status", PieceStatus.class),
            new EnumCheckMapping("piece_operations", "status", PieceOperationStatus.class),
            new EnumCheckMapping("operator_values", "status", OperatorValueStatus.class),
            new EnumCheckMapping("operator_values", "result", OperatorValueResult.class),
            new EnumCheckMapping("qc_inspections", "decision", QcInspectionDecision.class),
            new EnumCheckMapping("measurement_values", "result", MeasurementResult.class),
            new EnumCheckMapping("qc_exceptions", "exception_type", QcExceptionType.class),
            new EnumCheckMapping("qc_exceptions", "status", QcExceptionStatus.class),
            new EnumCheckMapping("qc_exceptions", "ecr_status", EcrSyncStatus.class),
            new EnumCheckMapping("pm_dispositions", "disposition", PmDispositionType.class),
            new EnumCheckMapping("vendor_dc_trackers", "status", VendorDcStatus.class),
            new EnumCheckMapping("outbox_events", "status", OutboxEventStatus.class),
            new EnumCheckMapping("rm_sync_history", "status", RmSyncStatus.class)
    );

    @Test
    void allEnumsMatchDatabaseCheckConstraints() throws Exception {
        String url = System.getenv("DATABASE_DIRECT_URL");
        if (url == null || url.isBlank()) {
            url = System.getenv("DATABASE_URL");
        }
        String user = System.getenv("DB_USER");
        String pass = System.getenv("DB_PASSWORD");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "DATABASE_URL not set; skipping DB enum test");

        String schema = "velan_enum_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA " + schema);
            try {
                Flyway.configure()
                        .dataSource(url, user, pass)
                        .schemas(schema)
                        .locations("classpath:db/migration")
                        .load()
                        .migrate();

                for (EnumCheckMapping m : MAPPINGS) {
                    Set<String> dbValues = readAllowedValues(conn, schema, m.table(), m.column());
                    assertFalse(dbValues.isEmpty(), "No CHECK IN values found for " + m.table() + "." + m.column());
                    Set<String> enumValues = Arrays.stream(m.enumClass().getEnumConstants())
                            .map(Enum::name)
                            .collect(Collectors.toSet());
                    assertEquals(dbValues, enumValues,
                            "Mismatch for " + m.table() + "." + m.column() + " vs " + m.enumClass().getSimpleName());
                }
            } finally {
                stmt.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    private Set<String> readAllowedValues(Connection conn, String schema, String table, String column) throws Exception {
        String sql = """
                SELECT pg_get_constraintdef(c.oid) AS def
                FROM pg_constraint c
                JOIN pg_class t ON c.conrelid = t.oid
                JOIN pg_namespace n ON t.relnamespace = n.oid
                WHERE n.nspname = ?
                  AND t.relname = ?
                  AND c.contype = 'c'
                  AND (pg_get_constraintdef(c.oid) LIKE ? OR pg_get_constraintdef(c.oid) LIKE ?)
                """;
        Set<String> values = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            ps.setString(3, "%(" + column + " %ANY%");
            ps.setString(4, "%((" + column + ")::text%ANY%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String def = rs.getString("def");
                    Matcher matcher = QUOTED_VALUE.matcher(def);
                    while (matcher.find()) {
                        String val = matcher.group(1);
                        if (!val.equals("character varying")) {
                            values.add(val);
                        }
                    }
                }
            }
        }
        return values;
    }
}
