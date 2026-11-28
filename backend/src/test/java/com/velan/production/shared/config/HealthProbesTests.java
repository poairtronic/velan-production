package com.velan.production.shared.config;

import com.velan.production.VelanProductionApplication;
import com.velan.production.shared.persistence.DatabaseTestSupport;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointProperties;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthProbesTests {

    private final HttpClient http = HttpClient.newHttpClient();

    @Test
    void livenessIsUpWithoutDatabaseAndOnlyHealthIsExposed() throws Exception {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(VelanProductionApplication.class)
                .profiles("local")
                .run("--spring.profiles.active=local", "--server.port=0")) {
            int port = Integer.parseInt(ctx.getEnvironment().getProperty("local.server.port"));
            HttpResponse<String> liveness = http.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health/liveness")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, liveness.statusCode());
            assertTrue(liveness.body().contains("\"UP\""));

            WebEndpointProperties webProps = ctx.getBean(WebEndpointProperties.class);
            assertEquals(Set.of("health"), webProps.getExposure().getInclude());
        }
    }

    @Test
    void readinessIsDownWhenDatabaseIsUnreachable() throws Exception {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(VelanProductionApplication.class)
                .profiles("test")
                .run(
                        "--spring.profiles.active=test",
                        "--server.port=0",
                        "--spring.datasource.url=jdbc:postgresql://127.0.0.1:59999/nonexistent",
                        "--spring.datasource.username=bad",
                        "--spring.datasource.password=bad",
                        "--spring.datasource.hikari.connection-timeout=1000",
                        "--spring.datasource.hikari.initialization-fail-timeout=-1",
                        "--app.storage.type=disabled",
                        "--spring.autoconfigure.exclude=org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration")) {
            int port = Integer.parseInt(ctx.getEnvironment().getProperty("local.server.port"));
            HttpResponse<String> readiness = http.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health/readiness")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(503, readiness.statusCode());
            assertTrue(readiness.body().contains("\"DOWN\""));
        }
    }

    @Test
    void readinessIsUpWithDatabase() throws Exception {
        var cfg = DatabaseTestSupport.requireDatabaseUrl();
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(VelanProductionApplication.class)
                .profiles("test")
                .properties(
                        "spring.datasource.url=" + cfg.jdbcUrl(),
                        "spring.datasource.username=" + cfg.username(),
                        "spring.datasource.password=" + cfg.password(),
                        "app.storage.type=disabled")
                .run("--spring.profiles.active=test", "--server.port=0", "--spring.autoconfigure.exclude=org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration")) {
            int port = Integer.parseInt(ctx.getEnvironment().getProperty("local.server.port"));
            HttpResponse<String> readiness = http.send(
                    HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health/readiness")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, readiness.statusCode());
            assertTrue(readiness.body().contains("\"UP\""));
        }
    }
}
