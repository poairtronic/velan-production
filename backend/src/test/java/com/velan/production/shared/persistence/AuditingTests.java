package com.velan.production.shared.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditingTests {

    @Test
    void createdAndUpdatedAuditFieldsArePopulatedInUtc() throws Exception {
        String url = System.getenv("DATABASE_URL");
        String user = System.getenv("DB_USER");
        String pass = System.getenv("DB_PASSWORD");
        Assumptions.assumeTrue(url != null && !url.isBlank(), "DATABASE_URL not set; skipping auditing test");

        String schema = "velan_audit_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        try (Connection conn = DriverManager.getConnection(url, user, pass);
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE SCHEMA " + schema);
            stmt.execute("""
                    CREATE TABLE %s.test_audited_items (
                        id UUID PRIMARY KEY,
                        name VARCHAR(64) NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL,
                        created_by UUID,
                        updated_at TIMESTAMPTZ NOT NULL,
                        updated_by UUID
                    )
                    """.formatted(schema));

            try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(TestAuditApp.class)
                    .profiles("audit-test")
                    .properties(
                            "spring.autoconfigure.exclude=",
                            "spring.datasource.url=" + url,
                            "spring.datasource.username=" + user,
                            "spring.datasource.password=" + pass,
                            "spring.jpa.properties.hibernate.default_schema=" + schema,
                            "spring.jpa.hibernate.ddl-auto=validate",
                            "spring.flyway.enabled=false",
                            "server.port=0")
                    .run("--spring.profiles.active=audit-test")) {

                TestAuditedItemRepository repo = ctx.getBean(TestAuditedItemRepository.class);
                UUID id = UUID.randomUUID();
                TestAuditedItem saved = repo.saveAndFlush(new TestAuditedItem(id, "initial"));

                assertNotNull(saved.getCreatedAt(), "createdAt must be populated on insert");
                assertNotNull(saved.getUpdatedAt(), "updatedAt must be populated on insert");
                Instant createdAt = saved.getCreatedAt();
                Instant firstUpdatedAt = saved.getUpdatedAt();

                Thread.sleep(20);
                saved.setName("updated");
                TestAuditedItem updated = repo.saveAndFlush(saved);

                assertEquals(createdAt, updated.getCreatedAt(), "createdAt must remain unchanged on update");
                assertTrue(!updated.getUpdatedAt().isBefore(firstUpdatedAt), "updatedAt must not move backward on update");
            } finally {
                stmt.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
            }
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = TestAuditedItem.class)
    @EnableJpaRepositories(basePackageClasses = TestAuditedItemRepository.class, considerNestedRepositories = true)
    @Import(JpaAuditingConfig.class)
    static class TestAuditApp {
    }

    @Entity
    @Table(name = "test_audited_items")
    static class TestAuditedItem extends AuditedEntity {
        @Id
        private UUID id;
        private String name;

        protected TestAuditedItem() {}

        TestAuditedItem(UUID id, String name) {
            this.id = id;
            this.name = name;
        }

        public UUID getId() { return id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }

    interface TestAuditedItemRepository extends JpaRepository<TestAuditedItem, UUID> {
    }
}
