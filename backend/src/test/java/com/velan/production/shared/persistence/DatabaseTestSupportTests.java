package com.velan.production.shared.persistence;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.opentest4j.TestAbortedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DatabaseTestSupportTests {

    @Test
    void skipsWhenDatabaseUrlUnsetAndRequireDbTestsUnset() {
        Map<String, String> env = Map.of();
        assertThrows(TestAbortedException.class, () ->
                DatabaseTestSupport.resolveConfig("DATABASE_URL", false, env::get, k -> null));
    }

    @Test
    void failsWhenRequireDbTestsIsTrueAndDatabaseUrlUnset() {
        Map<String, String> env = Map.of("REQUIRE_DB_TESTS", "true");
        assertThrows(AssertionFailedError.class, () ->
                DatabaseTestSupport.resolveConfig("DATABASE_URL", false, env::get, k -> null));
    }

    @Test
    void returnsConfigWhenDatabaseUrlPresent() {
        Map<String, String> env = Map.of(
                "DATABASE_URL", "jdbc:postgresql://localhost:5432/velan_ci",
                "DB_USER", "velan_ci",
                "DB_PASSWORD", "pw");
        var cfg = DatabaseTestSupport.resolveConfig("DATABASE_URL", false, env::get, k -> null);
        assertEquals("jdbc:postgresql://localhost:5432/velan_ci", cfg.jdbcUrl());
        assertEquals("velan_ci", cfg.username());
        assertEquals("pw", cfg.password());
    }
}
