package com.velan.production.shared.security;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionAndSectionScopeTests {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void permissionResolutionFollowsDenyAllowRoleDenyOrder() {
        UUID userId = UUID.randomUUID();
        InMemoryPermissionRepo repo = new InMemoryPermissionRepo();
        repo.rolePerms.put(userId, Set.of("SC_VIEW", "PIECE_VIEW"));
        repo.overrides.put(userId, Map.of("SC_VIEW", "DENY", "SC_CANCEL", "ALLOW"));

        PermissionService service = new PermissionService(repo);
        assertFalse(service.hasPermission(userId, "SC_VIEW"), "DENY override must beat role grant");
        assertTrue(service.hasPermission(userId, "SC_CANCEL"), "ALLOW override must grant without role grant");
        assertTrue(service.hasPermission(userId, "PIECE_VIEW"), "Role grant must allow");
        assertFalse(service.hasPermission(userId, "UNGRANTED_PERM"), "No grant must deny");
    }

    @Test
    void sectionScopeResolvesAllWhenEmptyAndFilteredWhenPresent() {
        UUID userId = UUID.randomUUID();
        UUID sec1 = UUID.randomUUID();
        InMemoryPermissionRepo repo = new InMemoryPermissionRepo();
        SectionScopeResolver resolver = new SectionScopeResolver(repo);

        SectionScope all = resolver.resolve(userId);
        assertTrue(all.allSections());
        assertTrue(all.sectionIds().isEmpty());

        repo.sections.put(userId, Set.of(sec1));
        SectionScope filtered = resolver.resolve(userId);
        assertFalse(filtered.allSections());
        assertEquals(Set.of(sec1), filtered.sectionIds());
    }

    @Test
    void passwordEncoderHashesAndMatches() {
        PasswordEncoder encoder = new BCryptPasswordEncoder(12);
        String hash = encoder.encode("SecretPass123!");
        assertNotEquals("SecretPass123!", hash);
        assertTrue(encoder.matches("SecretPass123!", hash));
        assertFalse(encoder.matches("WrongPass!", hash));
    }

    @Test
    void auditorAwareReturnsUserIdWhenAuthenticatedAndEmptyOtherwise() {
        UUID userId = UUID.randomUUID();
        var auditorAware = new com.velan.production.shared.persistence.JpaAuditingConfig().auditorAware();

        assertTrue(auditorAware.getCurrentAuditor().isEmpty());

        AuthenticatedUser principal = new AuthenticatedUser(userId, "EMP001", List.of("PM"), false);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        assertEquals(userId, auditorAware.getCurrentAuditor().orElseThrow());
    }

    private static class InMemoryPermissionRepo implements PermissionRepository {
        final Map<UUID, Set<String>> rolePerms = new HashMap<>();
        final Map<UUID, Map<String, String>> overrides = new HashMap<>();
        final Map<UUID, Set<UUID>> sections = new HashMap<>();

        @Override public boolean isUserActive(UUID userId) { return true; }
        @Override public Set<String> findRolePermissions(UUID userId) { return rolePerms.getOrDefault(userId, Set.of()); }
        @Override public Map<String, String> findUserOverrides(UUID userId) { return overrides.getOrDefault(userId, Map.of()); }
        @Override public Set<UUID> findUserSections(UUID userId) { return sections.getOrDefault(userId, Set.of()); }
    }
}
