package com.velan.production;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Phase 4.4: Standalone Neon PostgreSQL Connectivity Verification.
 *
 * Verifies live connection to the dedicated Velan Production Neon database
 * by executing a harmless 'SELECT 1' query with SSL enabled.
 *
 * Strict constraint: Does NOT create or query any application business tables or schemas.
 */
public class NeonConnectivityTests {

    @Test
    @DisplayName("Verify Neon PostgreSQL Connectivity via SELECT 1")
    void testNeonConnectivity() {
        String dbUrl = System.getenv("DATABASE_URL");
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = System.getProperty("DATABASE_URL");
        }

        boolean hasCredentials = dbUrl != null && !dbUrl.isBlank()
                && !dbUrl.contains("USER:PASSWORD")
                && !dbUrl.contains("placeholder")
                && !dbUrl.contains("HOST");
        Assumptions.assumeTrue(
                hasCredentials,
                "DATABASE_URL is not configured with live Neon credentials; skipping Neon connectivity test.");

        // Normalize JDBC protocol prefix if necessary
        String jdbcUrl = dbUrl;
        if (!jdbcUrl.startsWith("jdbc:")) {
            jdbcUrl = "jdbc:" + jdbcUrl;
        }

        // Ensure SSL mode is enabled
        if (!jdbcUrl.contains("sslmode=")) {
            jdbcUrl = jdbcUrl.contains("?") ? jdbcUrl + "&sslmode=require" : jdbcUrl + "?sslmode=require";
        }

        System.out.println("Attempting connection to Neon database...");
        try (Connection conn = DriverManager.getConnection(jdbcUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {

            assertTrue(rs.next(), "SELECT 1 query returned no rows");
            int result = rs.getInt(1);
            assertEquals(1, result, "SELECT 1 did not return expected value 1");

            System.out.println("================================================================================");
            System.out.println("[PHASE 4.4 SUCCESS] Successfully connected to Neon PostgreSQL!");
            System.out.println("Query executed: SELECT 1");
            System.out.println("Result verified: " + result);
            System.out.println("Database Product: " + conn.getMetaData().getDatabaseProductName() + " " + conn.getMetaData().getDatabaseProductVersion());
            System.out.println("Connection URL (masked): " + jdbcUrl.replaceAll(":[^:@]+@", ":****@"));
            System.out.println("Resources released successfully.");
            System.out.println("================================================================================");

        } catch (SQLException e) {
            System.err.println("[PHASE 4.4 FAILURE] Failed to connect to Neon database: " + e.getMessage());
            fail("Live Neon connectivity verification failed: " + e.getMessage());
        }
    }
}
