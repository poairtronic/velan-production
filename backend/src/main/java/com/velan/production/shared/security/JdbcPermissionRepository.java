package com.velan.production.shared.security;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC implementation of PermissionRepository reading users, role_permissions,
 * user_roles, user_permission_overrides, and user_sections.
 * User-level rows (user_roles, overrides, active, user_sections) are never cached.
 */
@Repository
public class JdbcPermissionRepository implements PermissionRepository {

    private final JdbcTemplate jdbcTemplate;
    private final RolePermissionGrantCache rolePermissionGrantCache;

    public JdbcPermissionRepository(
            ObjectProvider<JdbcTemplate> jdbcTemplateProvider,
            RolePermissionGrantCache rolePermissionGrantCache) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
        this.rolePermissionGrantCache = rolePermissionGrantCache;
    }

    @Override
    public boolean isUserActive(UUID userId) {
        if (jdbcTemplate == null) {
            return false;
        }
        Boolean active = jdbcTemplate.query(
                "SELECT active FROM users WHERE id = ?",
                rs -> rs.next() ? rs.getBoolean(1) : Boolean.FALSE,
                userId);
        return Boolean.TRUE.equals(active);
    }

    @Override
    public Set<String> findRolePermissions(UUID userId) {
        if (jdbcTemplate == null || userId == null) {
            return Set.of();
        }
        List<UUID> roleIds = jdbcTemplate.queryForList(
                "SELECT role_id FROM user_roles WHERE user_id = ?",
                UUID.class,
                userId);
        Set<String> permissions = new HashSet<>();
        for (UUID roleId : roleIds) {
            permissions.addAll(rolePermissionGrantCache.findPermissionsByRoleId(roleId));
        }
        return permissions;
    }

    @Override
    public Map<String, String> findUserOverrides(UUID userId) {
        if (jdbcTemplate == null) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        jdbcTemplate.query(
                "SELECT permission, effect FROM user_permission_overrides WHERE user_id = ?",
                rs -> {
                    result.put(rs.getString("permission"), rs.getString("effect"));
                },
                userId);
        return result;
    }

    @Override
    public Set<UUID> findUserSections(UUID userId) {
        if (jdbcTemplate == null) {
            return Set.of();
        }
        return new HashSet<>(jdbcTemplate.queryForList(
                "SELECT section_id FROM user_sections WHERE user_id = ?",
                UUID.class,
                userId));
    }
}
