package com.velan.production.shared.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @NotNull @Valid JwtConfig jwt,
        @NotNull @Valid PasswordConfig password,
        @NotNull @Valid LockoutConfig lockout,
        @NotNull @Valid LoginRateLimitConfig loginRateLimit) {

    @ConstructorBinding
    public SecurityProperties {
        if (lockout == null) {
            lockout = new LockoutConfig(5, Duration.ofMinutes(15));
        }
        if (loginRateLimit == null) {
            loginRateLimit = new LoginRateLimitConfig(30);
        }
    }

    public SecurityProperties(JwtConfig jwt, PasswordConfig password) {
        this(jwt, password, new LockoutConfig(5, Duration.ofMinutes(15)), new LoginRateLimitConfig(30));
    }

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

    public record LockoutConfig(
            @Min(1) int maxAttempts,
            @NotNull Duration duration) {

        public LockoutConfig {
            if (maxAttempts == 0) {
                maxAttempts = 5;
            }
            if (duration == null) {
                duration = Duration.ofMinutes(15);
            }
        }
    }

    public record LoginRateLimitConfig(
            @Min(1) int perMinute) {

        public LoginRateLimitConfig {
            if (perMinute == 0) {
                perMinute = 30;
            }
        }
    }
}
