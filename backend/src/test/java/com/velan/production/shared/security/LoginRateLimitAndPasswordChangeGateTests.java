package com.velan.production.shared.security;

import com.velan.production.VelanProductionApplication;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.security.login-rate-limit.per-minute=3")
@ActiveProfiles("test")
@Import({
        LoginRateLimitAndPasswordChangeGateTests.GateTestController.class,
        LoginRateLimitAndPasswordChangeGateTests.ControllableClockConfig.class
})
class LoginRateLimitAndPasswordChangeGateTests {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AtomicReference<Instant> clockInstant;

    @MockitoBean
    private PermissionRepository permissionRepository;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void rateLimitBlocksExcessAttemptsPerIpAndResetsOnNextMinute() throws Exception {
        MockMvc mvc = mockMvc();
        clockInstant.set(Instant.parse("2026-11-28T10:00:10Z"));

        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/v1/auth/login")
                            .with(req -> { req.setRemoteAddr("10.10.1.1"); return req; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"login\":\"u1\",\"password\":\"p1\"}"))
                    .andExpect(status().isUnauthorized());
        }

        // 4th attempt in same minute from same IP -> 429 with Retry-After
        mvc.perform(post("/api/v1/auth/login")
                        .with(req -> { req.setRemoteAddr("10.10.1.1"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"u1\",\"password\":\"p1\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", notNullValue()))
                .andExpect(jsonPath("$.code").value("ERR_RATE_LIMITED"));

        // Different IP is unaffected
        mvc.perform(post("/api/v1/auth/login")
                        .with(req -> { req.setRemoteAddr("10.10.1.2"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"u1\",\"password\":\"p1\"}"))
                .andExpect(status().isUnauthorized());

        // Other paths are not rate-limited
        mvc.perform(get("/actuator/health/liveness")
                        .with(req -> { req.setRemoteAddr("10.10.1.1"); return req; }))
                .andExpect(status().isOk());

        // Next minute resets the window
        clockInstant.set(Instant.parse("2026-11-28T10:01:01Z"));
        mvc.perform(post("/api/v1/auth/login")
                        .with(req -> { req.setRemoteAddr("10.10.1.1"); return req; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"login\":\"u1\",\"password\":\"p1\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void passwordChangeGateBlocksOtherEndpointsAndAllowsFourAuthPaths() throws Exception {
        MockMvc mvc = mockMvc();
        UUID flaggedUserId = UUID.randomUUID();
        when(permissionRepository.isUserActive(flaggedUserId)).thenReturn(true);
        when(permissionRepository.mustChangePassword(flaggedUserId)).thenReturn(true);
        String flaggedToken = jwtService.issueAccessToken(flaggedUserId, "EMP-FLAG", List.of("OPERATOR"));

        // Protected business endpoint -> 403 ERR_PASSWORD_CHANGE_REQUIRED
        mvc.perform(get("/test-gate/protected")
                        .header("Authorization", "Bearer " + flaggedToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_PASSWORD_CHANGE_REQUIRED"));

        // Four allowed paths pass the gate
        mvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", "Bearer " + flaggedToken))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + flaggedToken))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/refresh")
                        .header("Authorization", "Bearer " + flaggedToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + flaggedToken))
                .andExpect(status().isOk());

        // Unflagged user is unaffected
        UUID normalUserId = UUID.randomUUID();
        when(permissionRepository.isUserActive(normalUserId)).thenReturn(true);
        when(permissionRepository.mustChangePassword(normalUserId)).thenReturn(false);
        String normalToken = jwtService.issueAccessToken(normalUserId, "EMP-OK", List.of("OPERATOR"));
        mvc.perform(get("/test-gate/protected")
                        .header("Authorization", "Bearer " + normalToken))
                .andExpect(status().isOk());

        // Unauthenticated still gets 401
        mvc.perform(get("/test-gate/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_UNAUTHORIZED"));
    }

    @TestConfiguration
    static class ControllableClockConfig {
        @Bean
        AtomicReference<Instant> clockInstant() {
            return new AtomicReference<>(Instant.parse("2026-11-28T10:00:00Z"));
        }

        @Bean
        @Primary
        Clock testClock(AtomicReference<Instant> ref) {
            return new Clock() {
                @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
                @Override public Clock withZone(java.time.ZoneId zone) { return this; }
                @Override public Instant instant() { return ref.get(); }
            };
        }
    }

    @RestController
    static class GateTestController {
        @GetMapping("/test-gate/protected")
        public String protectedEndpoint() { return "ok"; }

        @PostMapping("/api/v1/auth/change-password")
        public String changePasswordStub() { return "ok"; }

        @PostMapping("/api/v1/auth/logout")
        public String logoutStub() { return "ok"; }

        @PostMapping("/api/v1/auth/refresh")
        public String refreshStub() { return "ok"; }

        @GetMapping("/api/v1/auth/me")
        public String meStub() { return "ok"; }
    }
}
