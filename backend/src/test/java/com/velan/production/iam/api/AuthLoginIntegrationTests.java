package com.velan.production.iam.api;

import com.velan.production.iam.internal.application.AuthService;
import com.velan.production.iam.internal.infrastructure.JdbcAuthRepository;
import com.velan.production.shared.audit.AuditProperties;
import com.velan.production.shared.audit.AuditService;
import com.velan.production.shared.error.GlobalExceptionHandler;
import com.velan.production.shared.persistence.DatabaseTestSupport;
import com.velan.production.shared.security.JwtService;
import com.velan.production.shared.security.PasswordPolicy;
import com.velan.production.shared.security.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthLoginIntegrationTests {

    private static final String TEST_SECRET =
            Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes(StandardCharsets.UTF_8));
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void loginSuccessCreatesHashedRefreshTokenResetsCountersAndAudits() throws Exception {
        var cfg = DatabaseTestSupport.requireDirectOrPooledUrl();
        DatabaseTestSupport.withMigratedSchema(cfg, "velan_auth_ok", (conn, schema) -> {
            try (Harness h = createHarness(cfg, schema, 5, Duration.ofMinutes(15))) {
                UUID userId = seedUser(h.jdbc, "op.one", "OpPass123", "EMP-OP1", "Operator One", "OPERATOR", true, false);
                h.jdbc.update("UPDATE users SET failed_login_count = 3 WHERE id = ?", userId);

                MvcResult res = h.mvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"login\":\"  OP.ONE  \",\"password\":\"OpPass123\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.tokenType").value("Bearer"))
                        .andExpect(jsonPath("$.expiresInSeconds").value(3600))
                        .andExpect(jsonPath("$.user.id").value(userId.toString()))
                        .andExpect(jsonPath("$.user.employeeCode").value("EMP-OP1"))
                        .andExpect(jsonPath("$.user.name").value("Operator One"))
                        .andExpect(jsonPath("$.user.roles[0]").value("OPERATOR"))
                        .andExpect(jsonPath("$.user.mustChangePassword").value(false))
                        .andReturn();

                String body = res.getResponse().getContentAsString();
                assertFalse(body.contains("passwordHash"));
                assertFalse(body.contains("password_hash"));

                JsonNode json = MAPPER.readTree(body);
                String accessToken = json.get("accessToken").asString();
                String refreshToken = json.get("refreshToken").asString();

                Jwt decodedAccess = h.jwtService.validateAccessToken(accessToken);
                assertNull(decodedAccess.getClaim("permissions"));

                byte[] keyBytes = Base64.getDecoder().decode(TEST_SECRET);
                var key = new javax.crypto.spec.SecretKeySpec(keyBytes, "HmacSHA256");
                Jwt decodedRefresh = NimbusJwtDecoder.withSecretKey(key)
                        .macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
                        .build()
                        .decode(refreshToken);
                assertEquals("refresh", decodedRefresh.getClaimAsString("typ"));

                String storedHash = h.jdbc.queryForObject(
                        "SELECT token_hash FROM refresh_tokens WHERE user_id = ?", String.class, userId);
                assertEquals(sha256(refreshToken), storedHash);
                assertFalse(storedHash.equals(refreshToken));

                Integer failedCount = h.jdbc.queryForObject(
                        "SELECT failed_login_count FROM users WHERE id = ?", Integer.class, userId);
                assertEquals(0, failedCount);

                Integer auditCount = h.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_logs WHERE action = 'LOGIN_SUCCESS' AND entity_id = ?",
                        Integer.class, userId.toString());
                assertEquals(1, auditCount);
            }
        });
    }

    @Test
    void allFailureModesReturnIdentical401AndInvokeBcryptOnce() throws Exception {
        var cfg = DatabaseTestSupport.requireDirectOrPooledUrl();
        DatabaseTestSupport.withMigratedSchema(cfg, "velan_auth_fail", (conn, schema) -> {
            try (Harness h = createHarness(cfg, schema, 5, Duration.ofMinutes(15))) {
                UUID activeId = seedUser(h.jdbc, "user.active", "RightPass1", "EMP-A", "Active User", "OPERATOR", true, false);
                UUID inactiveId = seedUser(h.jdbc, "user.inactive", "RightPass1", "EMP-I", "Inactive User", "OPERATOR", false, false);
                UUID lockedId = seedUser(h.jdbc, "user.locked", "RightPass1", "EMP-L", "Locked User", "OPERATOR", true, false);
                h.jdbc.update("UPDATE users SET locked_until = ? WHERE id = ?",
                        java.sql.Timestamp.from(h.now.get().plusSeconds(600)), lockedId);

                String over72 = "A1" + "b".repeat(80);
                List<String> payloads = List.of(
                        "{\"login\":\"unknown.user\",\"password\":\"SomePass1\"}",
                        "{\"login\":\"MySecretPass1!\",\"password\":\"SomePass1\"}",
                        "{\"login\":\"user.active\",\"password\":\"WrongPass1\"}",
                        "{\"login\":\"user.inactive\",\"password\":\"RightPass1\"}",
                        "{\"login\":\"user.locked\",\"password\":\"RightPass1\"}",
                        "{\"login\":\"user.active\",\"password\":\"" + over72 + "\"}"
                );

                String baselineNormalized = null;
                int expectedInvocations = 0;
                for (String payload : payloads) {
                    MvcResult res = h.mvc.perform(post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(payload))
                            .andExpect(status().isUnauthorized())
                            .andReturn();
                    expectedInvocations++;
                    verify(h.passwordEncoderSpy, times(expectedInvocations)).matches(anyString(), anyString());

                    String normalized = normalizeError(res.getResponse().getContentAsString());
                    if (baselineNormalized == null) {
                        baselineNormalized = normalized;
                    } else {
                        assertEquals(baselineNormalized, normalized);
                    }
                }

                // Audit reasons & secret safety
                String badFormatAudit = h.jdbc.queryForObject(
                        "SELECT details::text FROM audit_logs WHERE entity_id = '[invalid-format]' LIMIT 1",
                        String.class);
                assertFalse(badFormatAudit.contains("MySecretPass1!"));
                assertTrue(badFormatAudit.contains("UNKNOWN_USER"));

                assertEquals("BAD_PASSWORD", latestReason(h.jdbc, activeId));
                assertEquals("INACTIVE", latestReason(h.jdbc, inactiveId));
                assertEquals("LOCKED", latestReason(h.jdbc, lockedId));
            }
        });
    }

    @Test
    void lockoutAppliesToAdminPersistsCounterAcross401AndResetsAfterExpiry() throws Exception {
        var cfg = DatabaseTestSupport.requireDirectOrPooledUrl();
        DatabaseTestSupport.withMigratedSchema(cfg, "velan_auth_lock", (conn, schema) -> {
            try (Harness h = createHarness(cfg, schema, 3, Duration.ofMinutes(5))) {
                UUID adminId = seedUser(h.jdbc, "boss.admin", "AdminPass1", "EMP-ADM", "Boss Admin", "ADMIN", true, false);

                // 3 wrong attempts -> locked on 3rd
                for (int i = 0; i < 3; i++) {
                    h.mvc.perform(post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"login\":\"boss.admin\",\"password\":\"WrongPass1\"}"))
                            .andExpect(status().isUnauthorized());
                }

                Integer countAtLock = h.jdbc.queryForObject(
                        "SELECT failed_login_count FROM users WHERE id = ?", Integer.class, adminId);
                Instant lockedUntilAtLock = h.jdbc.queryForObject(
                        "SELECT locked_until FROM users WHERE id = ?", java.sql.Timestamp.class, adminId).toInstant();
                assertEquals(3, countAtLock);
                assertEquals(h.now.get().plus(Duration.ofMinutes(5)), lockedUntilAtLock);

                // 4th attempt with CORRECT password during lock still fails & does not alter counter/locked_until
                h.now.set(h.now.get().plusSeconds(60));
                h.mvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"login\":\"boss.admin\",\"password\":\"AdminPass1\"}"))
                        .andExpect(status().isUnauthorized());
                assertEquals(3, h.jdbc.queryForObject(
                        "SELECT failed_login_count FROM users WHERE id = ?", Integer.class, adminId));
                assertEquals(lockedUntilAtLock, h.jdbc.queryForObject(
                        "SELECT locked_until FROM users WHERE id = ?", java.sql.Timestamp.class, adminId).toInstant());

                // LOCKOUT audited once
                assertEquals(1, h.jdbc.queryForObject(
                        "SELECT COUNT(*) FROM audit_logs WHERE action = 'LOCKOUT' AND entity_id = ?",
                        Integer.class, adminId.toString()));

                // After lock expires: wrong attempt resets to 0 then counts as 1
                h.now.set(lockedUntilAtLock.plusSeconds(1));
                h.mvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"login\":\"boss.admin\",\"password\":\"WrongPass1\"}"))
                        .andExpect(status().isUnauthorized());
                assertEquals(1, h.jdbc.queryForObject(
                        "SELECT failed_login_count FROM users WHERE id = ?", Integer.class, adminId));
                assertNull(h.jdbc.queryForObject(
                        "SELECT locked_until FROM users WHERE id = ?", java.sql.Timestamp.class, adminId));

                // Correct password succeeds and resets counter to 0
                h.mvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"login\":\"boss.admin\",\"password\":\"AdminPass1\"}"))
                        .andExpect(status().isOk());
                assertEquals(0, h.jdbc.queryForObject(
                        "SELECT failed_login_count FROM users WHERE id = ?", Integer.class, adminId));
            }
        });
    }

    @Test
    void concurrentWrongPasswordsNeverLoseIncrements() throws Exception {
        var cfg = DatabaseTestSupport.requireDirectOrPooledUrl();
        DatabaseTestSupport.withMigratedSchema(cfg, "velan_auth_race", (conn, schema) -> {
            try (Harness h = createHarness(cfg, schema, 5, Duration.ofMinutes(15))) {
                UUID userId = seedUser(h.jdbc, "race.user", "RacePass1", "EMP-RACE", "Race User", "OPERATOR", true, false);
                int threads = 5;
                CountDownLatch ready = new CountDownLatch(threads);
                CountDownLatch go = new CountDownLatch(1);
                try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
                    List<Future<?>> futures = new java.util.ArrayList<>();
                    for (int i = 0; i < threads; i++) {
                        futures.add(pool.submit(() -> {
                            ready.countDown();
                            go.await();
                            h.mvc.perform(post("/api/v1/auth/login")
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .content("{\"login\":\"race.user\",\"password\":\"WrongPass1\"}"))
                                    .andExpect(status().isUnauthorized());
                            return null;
                        }));
                    }
                    ready.await();
                    go.countDown();
                    for (Future<?> f : futures) {
                        f.get();
                    }
                }

                assertEquals(5, h.jdbc.queryForObject(
                        "SELECT failed_login_count FROM users WHERE id = ?", Integer.class, userId));
                assertNotNull(h.jdbc.queryForObject(
                        "SELECT locked_until FROM users WHERE id = ?", java.sql.Timestamp.class, userId));
            }
        });
    }

    private static String latestReason(JdbcTemplate jdbc, UUID userId) {
        return jdbc.queryForObject(
                "SELECT details->>'reason' FROM audit_logs WHERE action = 'LOGIN_FAILURE' AND entity_id = ? ORDER BY created_at DESC LIMIT 1",
                String.class, userId.toString());
    }

    private static String normalizeError(String jsonBody) {
        JsonNode node = MAPPER.readTree(jsonBody);
        return node.get("status").asInt() + "|" + node.get("code").asString() + "|" + node.get("message").asString()
                + "|" + node.get("fieldErrors").toString();
    }

    private static UUID seedUser(
            JdbcTemplate jdbc,
            String login,
            String rawPassword,
            String employeeId,
            String name,
            String roleName,
            boolean active,
            boolean mustChange) {
        UUID deptId = jdbc.queryForObject("SELECT id FROM departments WHERE code = 'SYSTEM'", UUID.class);
        UUID roleId = jdbc.queryForObject("SELECT id FROM roles WHERE name = ?", UUID.class, roleName);
        UUID userId = UUID.randomUUID();
        String hash = new BCryptPasswordEncoder(4).encode(rawPassword);
        jdbc.update("""
                INSERT INTO users (id, name, login, password_hash, employee_id, department_id, active, must_change_password)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, userId, name, login, hash, employeeId, deptId, active, mustChange);
        jdbc.update("INSERT INTO user_roles (id, user_id, role_id) VALUES (?, ?, ?)",
                UUID.randomUUID(), userId, roleId);
        return userId;
    }

    private static String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private static Harness createHarness(
            DatabaseTestSupport.DbConfig cfg,
            String schema,
            int maxAttempts,
            Duration lockoutDuration) {
        DriverManagerDataSource ds = new DriverManagerDataSource(cfg.jdbcUrl(), cfg.username(), cfg.password());
        ds.setSchema(schema);
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        DataSourceTransactionManager txManager = new DataSourceTransactionManager(ds);
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-28T10:00:00Z"));
        Clock clock = new Clock() {
            @Override public ZoneId getZone() { return ZoneOffset.UTC; }
            @Override public Clock withZone(ZoneId zone) { return this; }
            @Override public Instant instant() { return now.get(); }
        };
        SecurityProperties props = new SecurityProperties(
                new SecurityProperties.JwtConfig(TEST_SECRET, Duration.ofHours(1), Duration.ofHours(12)),
                new SecurityProperties.PasswordConfig(4),
                new SecurityProperties.LockoutConfig(maxAttempts, lockoutDuration),
                new SecurityProperties.LoginRateLimitConfig(30));
        JwtService jwtService = new JwtService(props);
        PasswordEncoder encoderSpy = Mockito.spy(new BCryptPasswordEncoder(4));

        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.registerBean(JdbcTemplate.class, () -> jdbc);
        ctx.registerBean(PlatformTransactionManager.class, () -> txManager);
        ctx.registerBean(SecurityProperties.class, () -> props);
        ctx.registerBean(JwtService.class, () -> jwtService);
        ctx.registerBean(PasswordEncoder.class, () -> encoderSpy);
        ctx.registerBean(PasswordPolicy.class, PasswordPolicy::new);
        ctx.registerBean(AuditProperties.class, () -> new AuditProperties(
                List.of("password", "passwordHash", "password_hash", "token", "secret")));
        ctx.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        ctx.registerBean(Clock.class, () -> clock);
        ctx.register(TxConfig.class, JdbcAuthRepository.class, AuthService.class, AuthController.class);
        ctx.scan("com.velan.production.shared.audit");
        ctx.refresh();

        MockMvc mvc = MockMvcBuilders.standaloneSetup(ctx.getBean(AuthController.class))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        return new Harness(ctx, mvc, jdbc, jwtService, encoderSpy, now);
    }

    @EnableTransactionManagement
    static class TxConfig {
    }

    private record Harness(
            AnnotationConfigApplicationContext ctx,
            MockMvc mvc,
            JdbcTemplate jdbc,
            JwtService jwtService,
            PasswordEncoder passwordEncoderSpy,
            AtomicReference<Instant> now) implements AutoCloseable {
        @Override public void close() { ctx.close(); }
    }
}
