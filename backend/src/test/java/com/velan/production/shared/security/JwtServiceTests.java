package com.velan.production.shared.security;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTests {

    private static final String SECRET_1 = Base64.getEncoder().encodeToString("01234567890123456789012345678901".getBytes());
    private static final String SECRET_2 = Base64.getEncoder().encodeToString("abcdefghijklmnopqrstuvwxyz123456".getBytes());

    private JwtService service(String secret, Duration accessTtl, Duration refreshTtl) {
        return new JwtService(new SecurityProperties(
                new SecurityProperties.JwtConfig(secret, accessTtl, refreshTtl),
                new SecurityProperties.PasswordConfig(12)));
    }

    @Test
    void validAccessTokenContainsExpectedClaimsAndNoPermissionsClaim() {
        JwtService jwtService = service(SECRET_1, Duration.ofHours(1), Duration.ofHours(12));
        UUID userId = UUID.randomUUID();
        String token = jwtService.issueAccessToken(userId, "EMP001", List.of("PM", "QC"));

        Jwt jwt = jwtService.validateAccessToken(token);
        assertEquals(userId.toString(), jwt.getSubject());
        assertEquals("EMP001", jwt.getClaimAsString(JwtService.CLAIM_EMPLOYEE_CODE));
        assertEquals(List.of("PM", "QC"), jwt.getClaimAsStringList(JwtService.CLAIM_ROLES));
        assertEquals(JwtService.TYP_ACCESS, jwt.getClaimAsString(JwtService.CLAIM_TYP));
        assertNotNull(jwt.getId());
        assertNotNull(jwt.getIssuedAt());
        assertNotNull(jwt.getExpiresAt());
        assertFalse(jwt.hasClaim("permissions"));
    }

    @Test
    void expiredTamperedWrongSignatureAndRefreshTokensAreRejected() {
        JwtService jwtService = service(SECRET_1, Duration.ofHours(1), Duration.ofHours(12));
        UUID userId = UUID.randomUUID();

        String refresh = jwtService.issueRefreshToken(userId, "EMP001", List.of("OPERATOR"));
        assertThrows(JwtException.class, () -> jwtService.validateAccessToken(refresh));

        String access = jwtService.issueAccessToken(userId, "EMP001", List.of("OPERATOR"));
        String tampered = access.substring(0, access.length() - 2) + "xx";
        assertThrows(JwtException.class, () -> jwtService.validateAccessToken(tampered));

        JwtService otherKeyService = service(SECRET_2, Duration.ofHours(1), Duration.ofHours(12));
        assertThrows(JwtException.class, () -> otherKeyService.validateAccessToken(access));

        JwtService expiredService = service(SECRET_1, Duration.ofMillis(50), Duration.ofHours(12));
        String expired = expiredService.issueAccessToken(userId, "EMP001", List.of("OPERATOR"));
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        assertThrows(JwtException.class, () -> expiredService.validateAccessToken(expired));
    }
}
