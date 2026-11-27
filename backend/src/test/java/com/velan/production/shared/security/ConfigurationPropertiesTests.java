package com.velan.production.shared.security;

import com.velan.production.VelanProductionApplication;
import com.velan.production.shared.config.AppProperties;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigurationPropertiesTests {

    @Test
    void prodProfileFailsWithoutJwtSecret() {
        assertThrows(Exception.class, () -> {
            try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(VelanProductionApplication.class)
                    .profiles("prod")
                    .properties("server.port=0", "app.security.jwt-secret=")
                    .run()) {
                assertNotNull(ctx);
            }
        });
    }

    @Test
    void localProfileStartsWithNoDatabaseVariables() {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(VelanProductionApplication.class)
                .profiles("local")
                .properties("server.port=0")
                .run()) {
            assertNotNull(ctx.getBean(JwtProperties.class));
        }
    }

    @Test
    void propertiesValidationRejectsShortJwtSecret() {
        String shortSecret = Base64.getEncoder().encodeToString("too-short".getBytes());
        assertThrows(Exception.class, () -> {
            try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(VelanProductionApplication.class)
                    .profiles("local")
                    .properties("server.port=0")
                    .run("--app.security.jwt-secret=" + shortSecret)) {
                assertNotNull(ctx);
            }
        });
    }

    @Test
    void prodProfileFailsWithoutCorsAllowedOrigins() {
        String validSecret = Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes());
        assertThrows(Exception.class, () -> {
            try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(VelanProductionApplication.class)
                    .profiles("prod")
                    .properties("server.port=0")
                    .run("--app.security.jwt-secret=" + validSecret, "--app.cors.allowed-origins=")) {
                assertNotNull(ctx);
            }
        });
    }

    @Test
    void prodProfileFailsWithWildcardCorsOrigin() {
        String validSecret = Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes());
        assertThrows(Exception.class, () -> {
            try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(VelanProductionApplication.class)
                    .profiles("prod")
                    .properties("server.port=0")
                    .run("--app.security.jwt-secret=" + validSecret, "--app.cors.allowed-origins=*")) {
                assertNotNull(ctx);
            }
        });
    }

    @Test
    void localProfileStartsWithoutCorsAllowedOrigins() {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(VelanProductionApplication.class)
                .profiles("local")
                .properties("server.port=0")
                .run()) {
            assertNotNull(ctx.getBean(AppProperties.class));
        }
    }
}
