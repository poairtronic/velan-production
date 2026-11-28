package com.velan.production.shared.security;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Resolves effective permissions for the current or specified user:
 * (a) user override DENY -> deny
 * (b) user override ALLOW -> allow
 * (c) any of the user's roles grants it -> allow
 * (d) otherwise deny.
 */
@Service("permissions")
public class PermissionService {

    private final PermissionRepository repository;

    public PermissionService(PermissionRepository repository) {
        this.repository = repository;
    }

    public boolean has(String permissionCode) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            return false;
        }
        return hasPermission(user.userId(), permissionCode);
    }

    public boolean hasPermission(UUID userId, String permissionCode) {
        if (userId == null || permissionCode == null || permissionCode.isBlank()) {
            return false;
        }
        Map<String, String> overrides = repository.findUserOverrides(userId);
        String effect = overrides.get(permissionCode);
        if ("DENY".equals(effect)) {
            return false;
        }
        if ("ALLOW".equals(effect)) {
            return true;
        }
        Set<String> rolePerms = repository.findRolePermissions(userId);
        return rolePerms.contains(permissionCode);
    }
}
