package com.velan.production.shared.config;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.database")
public record DatabaseProperties(
        String url,
        String directUrl,
        String username,
        String password,
        boolean requireDatabaseConfig) {

    @AssertTrue(message = "DATABASE_URL is required in prod profile and must not be blank")
    public boolean isUrlPresentWhenRequired() {
        return !requireDatabaseConfig || (url != null && !url.isBlank());
    }

    @AssertTrue(message = "DATABASE_DIRECT_URL is required in prod profile and must not be blank")
    public boolean isDirectUrlPresentWhenRequired() {
        return !requireDatabaseConfig || (directUrl != null && !directUrl.isBlank());
    }

    @AssertTrue(message = "DB_USER is required in prod profile and must not be blank")
    public boolean isUsernamePresentWhenRequired() {
        return !requireDatabaseConfig || (username != null && !username.isBlank());
    }

    @AssertTrue(message = "DB_PASSWORD is required in prod profile and must not be blank")
    public boolean isPasswordPresentWhenRequired() {
        return !requireDatabaseConfig || (password != null && !password.isBlank());
    }
}
