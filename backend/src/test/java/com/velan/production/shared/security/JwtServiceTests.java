package com.velan.production.shared.security;

import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtServiceTests {

    private static final String SECRET_1 = Base64.getEncoder()
            .encodeToString("01234567890123456789012345678901".getBytes());
    private static final String SECRET_2 = Base64.getEncoder()
            .encodeToString("abcdefghijklmnopqrstuvwxyz123456".getBytes());

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

        // Spring Security allows a 60-second default JWT timestamp clock skew.
        // Issue a token with issuedAt = now - 120s and expiresAt = now - 61s so it is
        // valid at issuance but expired past skew.
        byte[] keyBytes = Base64.getDecoder().decode(SECRET_1);
        NimbusJwtEncoder rawEncoder = new NimbusJwtEncoder(
                new ImmutableSecret<>(new SecretKeySpec(keyBytes, "HmacSHA256")));
        Instant past = Instant.now().minusSeconds(120);
        JwtClaimsSet expiredClaims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(past)
                .expiresAt(past.plusSeconds(59))
                .claim("employeeCode", "EMP001")
                .claim("roles", List.of("OPERATOR"))
                .claim("typ", "access")
                .build();
        String expired = rawEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), expiredClaims))
                .getTokenValue();
        assertThrows(JwtException.class, () -> jwtService.validateAccessToken(expired));
    }
}
