package com.velan.production.shared.audit;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.audit")
public record AuditProperties(List<String> redactKeys) {

    public static final List<String> DEFAULT_REDACT_KEYS = List.of(
            "password",
            "passwordHash",
            "password_hash",
            "tokenHash",
            "token_hash",
            "token",
            "secret",
            "pin"
    );

    public AuditProperties {
        if (redactKeys == null || redactKeys.isEmpty()) {
            redactKeys = DEFAULT_REDACT_KEYS;
        }
    }
}
