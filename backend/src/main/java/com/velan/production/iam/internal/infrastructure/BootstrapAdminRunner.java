package com.velan.production.iam.internal.infrastructure;

import com.velan.production.shared.audit.AuditAction;
import com.velan.production.shared.audit.AuditEvent;
import com.velan.production.shared.audit.AuditService;
import com.velan.production.shared.error.ErrorResponse;
import com.velan.production.shared.security.PasswordPolicy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates the initial ADMIN user at startup if no user holds the ADMIN role (P3).
 * Uses a PostgreSQL transaction-scoped advisory lock so concurrent instances cannot race.
 */
@Component
public class BootstrapAdminRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminRunner.class);
    private static final long ADVISORY_LOCK_KEY = 9020018L;
    private static final Pattern LOGIN_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{3,64}$");

    private final BootstrapAdminProperties properties;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public BootstrapAdminRunner(
            BootstrapAdminProperties properties,
            PasswordPolicy passwordPolicy,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            ObjectProvider<PlatformTransactionManager> txManagerProvider) {
        this.properties = properties;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        PlatformTransactionManager txManager = txManagerProvider.getIfAvailable();
        this.transactionTemplate = txManager == null ? null : new TransactionTemplate(txManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (jdbcTemplate == null || transactionTemplate == null) {
            return;
        }
        List<String> missing = missingRequiredProperties();
        if (!missing.isEmpty()) {
            log.warn("Bootstrap admin skipped; missing required properties: {}", missing);
            return;
        }
        transactionTemplate.executeWithoutResult(status -> bootstrapInTransaction());
    }

    void bootstrapInTransaction() {
        jdbcTemplate.query("SELECT pg_advisory_xact_lock(?)", rs -> null, ADVISORY_LOCK_KEY);

        Boolean adminExists = jdbcTemplate.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM user_roles ur
                    JOIN roles r ON r.id = ur.role_id
                    WHERE r.name = 'ADMIN'
                )
                """, Boolean.class);
        if (Boolean.TRUE.equals(adminExists)) {
            return;
        }

        List<String> missing = missingRequiredProperties();
        if (!missing.isEmpty()) {
            log.warn("Bootstrap admin skipped; missing required properties: {}", missing);
            return;
        }

        String login = properties.login().trim();
        String employeeId = properties.employeeId().trim();
        String name = properties.name().trim();

        if (!LOGIN_PATTERN.matcher(login).matches()) {
            log.warn("Bootstrap admin skipped; invalid format for property: [login]");
            return;
        }

        List<ErrorResponse.FieldErrorItem> violations = passwordPolicy.validate(
                properties.password(), login, employeeId, null);
        if (!violations.isEmpty()) {
            List<String> codes = violations.stream().map(ErrorResponse.FieldErrorItem::code).toList();
            log.warn("Bootstrap admin skipped; password policy violations: {}", codes);
            return;
        }

        UUID departmentId = jdbcTemplate.queryForObject(
                "SELECT id FROM departments WHERE code = 'SYSTEM'", UUID.class);
        UUID roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM roles WHERE name = 'ADMIN'", UUID.class);

        UUID userId = UUID.randomUUID();
        String encodedPassword = passwordEncoder.encode(properties.password());

        jdbcTemplate.update("""
                INSERT INTO users (
                    id, name, login, password_hash, employee_id, department_id,
                    active, failed_login_count, must_change_password, password_changed_at
                ) VALUES (?, ?, ?, ?, ?, ?, TRUE, 0, ?, NULL)
                """,
                userId, name, login, encodedPassword, employeeId, departmentId, properties.mustChangePassword());

        jdbcTemplate.update("""
                INSERT INTO user_roles (id, user_id, role_id, assigned_by)
                VALUES (?, ?, ?, NULL)
                """,
                UUID.randomUUID(), userId, roleId);

        Map<String, Object> newState = new LinkedHashMap<>();
        newState.put("login", login);
        newState.put("employeeId", employeeId);
        newState.put("name", name);
        newState.put("department", "SYSTEM");
        newState.put("role", "ADMIN");
        newState.put("mustChangePassword", properties.mustChangePassword());

        auditService.record(AuditEvent.of(
                AuditAction.CREATE,
                "USER",
                userId.toString(),
                null,
                null,
                newState,
                "Bootstrap initial ADMIN user"));
    }

    private List<String> missingRequiredProperties() {
        List<String> missing = new ArrayList<>();
        if (isBlank(properties.login())) {
            missing.add("app.bootstrap.admin.login");
        }
        if (isBlank(properties.password())) {
            missing.add("app.bootstrap.admin.password");
        }
        if (isBlank(properties.employeeId())) {
            missing.add("app.bootstrap.admin.employee-id");
        }
        if (isBlank(properties.name())) {
            missing.add("app.bootstrap.admin.name");
        }
        return missing;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
