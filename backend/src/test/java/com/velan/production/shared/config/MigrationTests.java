package com.velan.production.shared.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationTests {

    @Test
    void flywayMigrationsApplyValidateAndIdempotent() throws Exception {
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
                assertTrue(first.migrationsExecuted >= 1, "Expected at least 1 migration executed");

                for (String table : List.of("files", "audit_logs", "shedlock", "idempotency_keys")) {
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
            } finally {
                try (Statement st = conn.createStatement()) {
                    st.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
                }
            }
        }
    }
}
