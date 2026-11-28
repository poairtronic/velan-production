package com.velan.production.shared.storage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        @NotBlank String type,
        @NotNull @Valid LocalConfig local) {

    public StorageProperties {
        if (type == null || type.isBlank()) {
            type = "disabled";
        }
        if (local == null) {
            local = new LocalConfig("./.data/files");
        }
    }

    @AssertTrue(message = "R2 adapter is not implemented yet")
    public boolean isNotR2() {
        return !"r2".equalsIgnoreCase(type.trim());
    }

    public record LocalConfig(@NotBlank String root) {
        public LocalConfig {
            if (root == null || root.isBlank()) {
                root = "./.data/files";
            }
        }
    }
}
