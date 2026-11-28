package com.velan.production.shared.security;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Port for reading user active status, role permissions, per-user overrides, and section scope.
 */
public interface PermissionRepository {

    boolean isUserActive(UUID userId);

    default boolean mustChangePassword(UUID userId) {
        return false;
    }

    Set<String> findRolePermissions(UUID userId);

    /**
     * Map of permission code -> effect ("ALLOW" or "DENY").
     */
    Map<String, String> findUserOverrides(UUID userId);

    Set<UUID> findUserSections(UUID userId);
}
