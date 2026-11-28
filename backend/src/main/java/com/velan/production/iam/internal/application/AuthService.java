package com.velan.production.iam.internal.application;

import com.velan.production.iam.api.AuthTokenResponse;
import com.velan.production.iam.internal.domain.AuthRepository;
import com.velan.production.iam.internal.domain.AuthRepository.AuthUserRecord;
import com.velan.production.iam.internal.domain.AuthRepository.FailedAttemptResult;
import com.velan.production.shared.audit.AuditAction;
import com.velan.production.shared.audit.AuditEvent;
import com.velan.production.shared.audit.AuditService;
import com.velan.production.shared.security.JwtService;
import com.velan.production.shared.security.PasswordPolicy;
import com.velan.production.shared.security.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles login authentication, timing-safe BCrypt comparison, atomic lockout tracking,
 * refresh token persistence, and security audit logging (P1, P5, P6, P8, P11, P12).
 */
@Service
public class AuthService {

    private static final Pattern LOGIN_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{3,64}$");
    private static final String OVER_72_DUMMY_CANDIDATE = "__velan_over_72_bytes_dummy_reject__";

    private final AuthRepository authRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SecurityProperties securityProperties;
    private final AuditService auditService;
    private final Clock utcClock;
    private final String dummyHash;

    public AuthService(
            AuthRepository authRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            SecurityProperties securityProperties,
            AuditService auditService,
            ObjectProvider<Clock> utcClockProvider) {
        this.authRepository = authRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.securityProperties = securityProperties;
        this.auditService = auditService;
        this.utcClock = utcClockProvider.getIfAvailable(Clock::systemUTC);
        this.dummyHash = passwordEncoder.encode("velan-dummy-timing-guard-" + UUID.randomUUID());
    }

    @Transactional
    public LoginResult login(String rawLogin, String rawPassword) {
        String normalizedLogin = rawLogin == null ? "" : rawLogin.trim().toLowerCase(Locale.ROOT);
        boolean validLoginFormat = LOGIN_PATTERN.matcher(normalizedLogin).matches();
        String auditLogin = validLoginFormat ? normalizedLogin : "[invalid-format]";

        AuthUserRecord user = validLoginFormat
                ? authRepository.findByNormalizedLogin(normalizedLogin).orElse(null)
                : null;

        boolean over72Bytes = rawPassword == null
                || rawPassword.getBytes(StandardCharsets.UTF_8).length > PasswordPolicy.MAX_BYTES;
        String candidate = over72Bytes ? OVER_72_DUMMY_CANDIDATE : rawPassword;
        String targetHash = (user == null || over72Bytes) ? dummyHash : user.passwordHash();
        boolean passwordMatches = passwordEncoder.matches(candidate, targetHash) && !over72Bytes;

        if (user == null) {
            auditFailure("UNKNOWN_USER", auditLogin, auditLogin);
            return new LoginResult.Failure();
        }

        Instant now = Instant.now(utcClock);
        if (!user.active()) {
            auditFailure("INACTIVE", user.id().toString(), auditLogin);
            return new LoginResult.Failure();
        }

        if (user.lockedUntil() != null) {
            if (user.lockedUntil().isAfter(now)) {
                auditFailure("LOCKED", user.id().toString(), auditLogin);
                return new LoginResult.Failure();
            }
            // Expired lock: reset before evaluating this attempt (P5).
            authRepository.resetLockout(user.id(), now);
        }

        if (!passwordMatches) {
            int maxAttempts = securityProperties.lockout().maxAttempts();
            Instant lockTarget = now.plus(securityProperties.lockout().duration());
            FailedAttemptResult updated = authRepository.recordFailedAttempt(user.id(), maxAttempts, lockTarget, now);
            auditFailure("BAD_PASSWORD", user.id().toString(), auditLogin);
            if (updated.failedLoginCount() == maxAttempts && updated.lockedUntil() != null) {
                Map<String, Object> lockoutDetails = new LinkedHashMap<>();
                lockoutDetails.put("login", auditLogin);
                lockoutDetails.put("failedLoginCount", updated.failedLoginCount());
                lockoutDetails.put("lockedUntil", updated.lockedUntil().toString());
                auditService.recordIndependent(AuditEvent.of(
                        AuditAction.LOCKOUT,
                        "USER",
                        user.id().toString(),
                        null,
                        null,
                        lockoutDetails,
                        "LOCKOUT"));
            }
            return new LoginResult.Failure();
        }

        if (user.failedLoginCount() > 0 || user.lockedUntil() != null) {
            authRepository.resetLockout(user.id(), now);
        }

        List<String> roles = authRepository.findRoleNames(user.id());
        String accessToken = jwtService.issueAccessToken(user.id(), user.employeeId(), roles);
        String refreshToken = jwtService.issueRefreshToken(user.id(), user.employeeId(), roles);
        Instant refreshExpiresAt = now.plus(securityProperties.jwt().refreshTtl());
        authRepository.insertRefreshToken(
                UUID.randomUUID(),
                user.id(),
                sha256Hex(refreshToken),
                UUID.randomUUID(),
                now,
                refreshExpiresAt);

        long expiresInSeconds = securityProperties.jwt().accessTtl().toSeconds();
        Instant accessExpiresAt = now.plusSeconds(expiresInSeconds);

        Map<String, Object> successDetails = new LinkedHashMap<>();
        successDetails.put("login", user.login());
        successDetails.put("roles", roles);
        successDetails.put("mustChangePassword", user.mustChangePassword());
        auditService.record(AuditEvent.of(
                AuditAction.LOGIN_SUCCESS,
                "USER",
                user.id().toString(),
                null,
                null,
                successDetails,
                "LOGIN_SUCCESS"));

        AuthTokenResponse response = new AuthTokenResponse(
                accessToken,
                refreshToken,
                "Bearer",
                expiresInSeconds,
                accessExpiresAt,
                new AuthTokenResponse.UserSummary(
                        user.id(),
                        user.employeeId(),
                        user.name(),
                        roles,
                        user.mustChangePassword()));
        return new LoginResult.Success(response);
    }

    private void auditFailure(String reason, String entityId, String auditLogin) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("login", auditLogin);
        details.put("reason", reason);
        auditService.recordIndependent(AuditEvent.of(
                AuditAction.LOGIN_FAILURE,
                "USER",
                entityId,
                null,
                null,
                details,
                reason));
    }

    static String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    public sealed interface LoginResult permits LoginResult.Success, LoginResult.Failure {
        record Success(AuthTokenResponse response) implements LoginResult {
        }

        record Failure() implements LoginResult {
        }
    }
}
