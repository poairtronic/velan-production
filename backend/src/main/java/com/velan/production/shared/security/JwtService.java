package com.velan.production.shared.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

/**
 * Issues and validates HS256 access and refresh JWTs using Spring Security's Nimbus encoder/decoder.
 */
@Service
public class JwtService {

    public static final String CLAIM_EMPLOYEE_CODE = "employeeCode";
    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_TYP = "typ";
    public static final String TYP_ACCESS = "access";
    public static final String TYP_REFRESH = "refresh";

    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final SecurityProperties properties;

    public JwtService(SecurityProperties properties) {
        this.properties = properties;
        byte[] keyBytes = Base64.getDecoder().decode(properties.jwt().secret().trim());
        SecretKey key = new SecretKeySpec(keyBytes, "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    public String issueAccessToken(UUID userId, String employeeCode, List<String> roles) {
        return issue(userId, employeeCode, roles, TYP_ACCESS, properties.jwt().accessTtl());
    }

    public String issueRefreshToken(UUID userId, String employeeCode, List<String> roles) {
        return issue(userId, employeeCode, roles, TYP_REFRESH, properties.jwt().refreshTtl());
    }

    public Jwt validateAccessToken(String token) {
        Jwt jwt = decoder.decode(token);
        String typ = jwt.getClaimAsString(CLAIM_TYP);
        if (!TYP_ACCESS.equals(typ)) {
            throw new JwtException("Token is not an access token");
        }
        return jwt;
    }

    private String issue(UUID userId, String employeeCode, List<String> roles, String typ, java.time.Duration ttl) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim(CLAIM_EMPLOYEE_CODE, employeeCode)
                .claim(CLAIM_ROLES, roles == null ? List.of() : List.copyOf(roles))
                .claim(CLAIM_TYP, typ)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
