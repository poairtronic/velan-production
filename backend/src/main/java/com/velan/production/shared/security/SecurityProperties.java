package com.velan.production.shared.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @NotNull @Valid JwtConfig jwt,
        @NotNull @Valid PasswordConfig password) {

    public record JwtConfig(
            String secret,
            @NotNull Duration accessTtl,
            @NotNull Duration refreshTtl) {

        public JwtConfig {
            if (accessTtl == null) {
                accessTtl = Duration.ofHours(1);
            }
            if (refreshTtl == null) {
                refreshTtl = Duration.ofHours(12);
            }
        }
    }

    public record PasswordConfig(
            @Min(4) @Max(31) int bcryptStrength) {

        public PasswordConfig {
            if (bcryptStrength == 0) {
                bcryptStrength = 12;
            }
        }
    }
}
