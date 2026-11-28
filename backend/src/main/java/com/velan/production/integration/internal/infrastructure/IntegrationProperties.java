package com.velan.production.integration.internal.infrastructure;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed feature flags for external integrations.
 * Both default to false in every profile until connectors are built in phases 13-14.
 */
@Validated
@ConfigurationProperties(prefix = "app.integration")
public record IntegrationProperties(
        @NotNull @Valid Toggle rm,
        @NotNull @Valid Toggle ecr) {

    public IntegrationProperties {
        if (rm == null) {
            rm = new Toggle(false);
        }
        if (ecr == null) {
            ecr = new Toggle(false);
        }
    }

    public record Toggle(boolean enabled) {
    }
}
