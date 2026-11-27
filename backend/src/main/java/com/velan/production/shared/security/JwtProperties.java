package com.velan.production.shared.security;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security")
public record JwtProperties(
        String jwtSecret,
        @Min(value = 1, message = "app.security.jwt-expiration-minutes must be at least 1")
        long jwtExpirationMinutes,
        boolean requireJwtSecret) {

    @AssertTrue(message = "JWT_SECRET is required in prod profile and must not be blank")
    public boolean isJwtSecretPresentWhenRequired() {
        return !requireJwtSecret || (jwtSecret != null && !jwtSecret.isBlank());
    }

    @AssertTrue(message = "JWT_SECRET must be valid Base64 encoding at least 256 bits (32 bytes)")
    public boolean isJwtSecretValidBase64AndLength() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            return true;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(jwtSecret.trim());
            return decoded.length >= 32;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
