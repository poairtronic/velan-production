package com.velan.production.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank(message = "app.timezone (TZ) must not be blank")
        String timezone,
        @NotNull @Valid
        CorsProperties cors) {

    public record CorsProperties(
            @NotNull(message = "app.cors.allowed-origins (CORS_ALLOWED_ORIGINS) must not be null")
            List<String> allowedOrigins,
            boolean requireCorsOrigins) {

        @AssertTrue(message = "CORS_ALLOWED_ORIGINS is required in prod profile and must not be blank")
        public boolean isCorsOriginsPresentWhenRequired() {
            return !requireCorsOrigins || allowedOrigins.stream().anyMatch(o -> o != null && !o.isBlank());
        }

        @AssertTrue(message = "CORS_ALLOWED_ORIGINS must not contain wildcard '*' in prod profile")
        public boolean isCorsWildcardRejectedWhenRequired() {
            return !requireCorsOrigins || allowedOrigins.stream().noneMatch(o -> "*".equals(o == null ? null : o.trim()));
        }
    }
}
