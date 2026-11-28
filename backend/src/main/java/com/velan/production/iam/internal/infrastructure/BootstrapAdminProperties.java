package com.velan.production.iam.internal.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for bootstrapping the initial ADMIN user (P3).
 * Missing values result in a no-op with a warning (no values logged).
 */
@Validated
@ConfigurationProperties(prefix = "app.bootstrap.admin")
public record BootstrapAdminProperties(
        String login,
        String password,
        String employeeId,
        String name,
        boolean mustChangePassword) {
}
