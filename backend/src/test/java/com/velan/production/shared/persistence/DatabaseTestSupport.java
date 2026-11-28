package com.velan.production.shared.persistence;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Assumptions;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Shared database test harness for Phase 8+ tests.
 * Skips when DATABASE_URL is unset unless REQUIRE_DB_TESTS=true, in which case it fails.
 */
public final class DatabaseTestSupport {

    private DatabaseTestSupport() {
    }

    public record DbConfig(String jdbcUrl, String username, String password) {
    }

    @FunctionalInterface
    public interface SchemaAction {
        void execute(Connection conn, String schema) throws Exception;
    }

    public static DbConfig requireDatabaseUrl() {
        return resolveConfig("DATABASE_URL", false);
    }

    public static DbConfig requireDirectOrPooledUrl() {
        return resolveConfig("DATABASE_DIRECT_URL", true);
    }

    static DbConfig resolveConfig(
            String primaryEnv,
            boolean fallbackToDatabaseUrl,
            java.util.function.Function<String, String> envReader,
            java.util.function.Function<String, String> propReader) {
        String rawUrl = readVar(primaryEnv, envReader, propReader);
        if ((rawUrl == null || rawUrl.isBlank()) && fallbackToDatabaseUrl) {
            rawUrl = readVar("DATABASE_URL", envReader, propReader);
        }

        boolean valid = isValidDbUrl(rawUrl);
        if (!valid) {
            String requireDb = readVar("REQUIRE_DB_TESTS", envReader, propReader);
            if ("true".equalsIgnoreCase(requireDb)) {
                fail("REQUIRE_DB_TESTS=true but " + primaryEnv + " / DATABASE_URL is not configured");
            }
            Assumptions.assumeTrue(false, primaryEnv + " is not configured; skipping database test");
        }

        String jdbcUrl = rawUrl.startsWith("jdbc:") ? rawUrl : "jdbc:" + rawUrl;
        if (!jdbcUrl.contains("sslmode=") && jdbcUrl.contains("neon.tech")) {
            jdbcUrl = jdbcUrl.contains("?") ? jdbcUrl + "&sslmode=require" : jdbcUrl + "?sslmode=require";
        }
        String user = readVar("DB_USER", envReader, propReader);
        String pass = readVar("DB_PASSWORD", envReader, propReader);
        return new DbConfig(jdbcUrl, user, pass != null ? pass : "");
    }

    private static DbConfig resolveConfig(String primaryEnv, boolean fallbackToDatabaseUrl) {
        return resolveConfig(primaryEnv, fallbackToDatabaseUrl, System::getenv, System::getProperty);
    }

    private static String readVar(
            String key,
            java.util.function.Function<String, String> envReader,
            java.util.function.Function<String, String> propReader) {
        String val = envReader.apply(key);
        if (val == null || val.isBlank()) {
            val = propReader.apply(key);
        }
        return val;
    }

    private static boolean isValidDbUrl(String url) {
        return url != null
                && !url.isBlank()
                && !url.contains("USER:PASSWORD")
                && !url.contains("ep-xxx")
                && !url.contains("placeholder")
                && !url.contains("HOST");
    }

    public static DataSource openDataSource(DbConfig cfg) {
        if (cfg.username() != null && !cfg.username().isBlank()) {
            return new DriverManagerDataSource(cfg.jdbcUrl(), cfg.username(), cfg.password());
        }
        return new DriverManagerDataSource(cfg.jdbcUrl());
    }

    public static JdbcTemplate openJdbcTemplate(DbConfig cfg) {
        return new JdbcTemplate(openDataSource(cfg));
    }

    public static Connection openConnection(DbConfig cfg) throws SQLException {
        if (cfg.username() != null && !cfg.username().isBlank()) {
            return DriverManager.getConnection(cfg.jdbcUrl(), cfg.username(), cfg.password());
        }
        return DriverManager.getConnection(cfg.jdbcUrl());
    }

    public static void withIsolatedSchema(DbConfig cfg, String prefix, SchemaAction action) throws Exception {
        String schema = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection conn = openConnection(cfg)) {
            try (Statement st = conn.createStatement()) {
                st.execute("CREATE SCHEMA " + schema);
            }
            try {
                action.execute(conn, schema);
            } finally {
                try (Statement st = conn.createStatement()) {
                    st.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
                }
            }
        }
    }

    public static MigrateResult migrateSchema(DbConfig cfg, String schema) {
        return Flyway.configure()
                .dataSource(cfg.jdbcUrl(), cfg.username(), cfg.password())
                .schemas(schema)
                .locations("classpath:db/migration")
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .baselineOnMigrate(false)
                .load()
                .migrate();
    }

    public static void withMigratedSchema(DbConfig cfg, String prefix, SchemaAction action) throws Exception {
        withIsolatedSchema(cfg, prefix, (conn, schema) -> {
            migrateSchema(cfg, schema);
            action.execute(conn, schema);
        });
    }
}
