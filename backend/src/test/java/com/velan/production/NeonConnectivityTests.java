package com.velan.production;

import com.velan.production.shared.persistence.DatabaseTestSupport;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
        var cfg = DatabaseTestSupport.requireDatabaseUrl();
        String jdbcUrl = cfg.jdbcUrl();

        System.out.println("Attempting connection to Neon database...");
        try (Connection conn = DatabaseTestSupport.openConnection(cfg);
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
