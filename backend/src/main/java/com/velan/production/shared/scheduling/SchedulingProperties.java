package com.velan.production.shared.scheduling;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.scheduling")
public record SchedulingProperties(
        boolean enabled,
        @NotBlank String zone) {

    public SchedulingProperties {
        if (zone == null || zone.isBlank()) {
            zone = "Asia/Kolkata";
        }
    }
}
