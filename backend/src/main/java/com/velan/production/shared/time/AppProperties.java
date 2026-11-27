package com.velan.production.shared.time;

import jakarta.validation.Valid;
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
            List<String> allowedOrigins) {
    }
}
