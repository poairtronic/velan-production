package com.velan.production.shared.cache;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.cache")
public record CacheProperties(
        @NotNull Duration defaultTtl,
        @Min(1) long defaultMaxSize,
        Map<String, CacheSpec> specs) {

    public CacheProperties {
        if (defaultTtl == null) {
            defaultTtl = Duration.ofMinutes(10);
        }
        if (defaultMaxSize <= 0) {
            defaultMaxSize = 1000;
        }
        if (specs == null) {
            specs = Map.of();
        }
    }

    public record CacheSpec(Duration ttl, Long maxSize) {
    }
}
