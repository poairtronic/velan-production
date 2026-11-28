package com.velan.production.shared.security;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Import(SecurityFilterChainTests.TestSecuredController.class)
@ExtendWith(OutputCaptureExtension.class)
class SecurityFilterChainTests {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private PermissionRepository permissionRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void unauthenticatedAndInvalidTokensReturn401Envelope() throws Exception {
        mockMvc.perform(get("/test-sec/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_UNAUTHORIZED"));

        mockMvc.perform(get("/test-sec/protected")
                .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_UNAUTHORIZED"));
    }

    @Test
    void inactiveUserReturns401() throws Exception {
        UUID userId = UUID.randomUUID();
        when(permissionRepository.isUserActive(userId)).thenReturn(false);
        String token = jwtService.issueAccessToken(userId, "EMP002", List.of("PM"));

        mockMvc.perform(get("/test-sec/protected")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("ERR_UNAUTHORIZED"));
    }

    @Test
    void permissionCheckReturns403WhenDeniedAnd200WhenGrantedAndTokenNeverLogged(CapturedOutput output)
            throws Exception {
        UUID userId = UUID.randomUUID();
        when(permissionRepository.isUserActive(userId)).thenReturn(true);
        when(permissionRepository.findUserOverrides(userId)).thenReturn(Map.of());
        when(permissionRepository.findRolePermissions(userId)).thenReturn(Set.of());
        String token = jwtService.issueAccessToken(userId, "EMP003", List.of("OPERATOR"));

        mockMvc.perform(get("/test-sec/protected")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_FORBIDDEN"));

        when(permissionRepository.findRolePermissions(userId)).thenReturn(Set.of("SC_VIEW"));
        mockMvc.perform(get("/test-sec/protected")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertFalse(output.getOut().contains(token), "JWT token must never appear in logs");
    }

    @Test
    void principalReadsMustChangePasswordFromRepository() throws Exception {
        UUID userId = UUID.randomUUID();
        when(permissionRepository.isUserActive(userId)).thenReturn(true);
        when(permissionRepository.mustChangePassword(userId)).thenReturn(true);
        String token = jwtService.issueAccessToken(userId, "EMP004", List.of("OPERATOR"));

        mockMvc.perform(get("/test-sec/must-change")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ERR_PASSWORD_CHANGE_REQUIRED"));

        when(permissionRepository.mustChangePassword(userId)).thenReturn(false);
        mockMvc.perform(get("/test-sec/must-change")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(false));
    }

    @Test
    void publicPathsRemainAccessibleWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_VALIDATION"));
        mockMvc.perform(post("/api/v1/auth/refresh")).andExpect(status().isOk());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    }

    @RestController
    static class TestSecuredController {

        @GetMapping("/test-sec/protected")
        @PreAuthorize("@permissions.has('SC_VIEW')")
        public String protectedEndpoint() {
            return "ok";
        }

        @GetMapping("/test-sec/must-change")
        public boolean mustChangeEndpoint(org.springframework.security.core.Authentication auth) {
            return ((AuthenticatedUser) auth.getPrincipal()).mustChangePassword();
        }

        @PostMapping("/api/v1/auth/refresh")
        public String refreshStub() {
            return "refresh";
        }
    }
}
