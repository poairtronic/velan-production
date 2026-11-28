package com.velan.production.shared.security;

import com.velan.production.shared.cache.CacheNames;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Caches role_permissions keyed strictly by roleId (never by userId).
 */
@Component
class RolePermissionGrantCache {

    private final JdbcTemplate jdbcTemplate;

    RolePermissionGrantCache(ObjectProvider<JdbcTemplate> jdbcTemplateProvider) {
        this.jdbcTemplate = jdbcTemplateProvider.getIfAvailable();
    }

    @Cacheable(cacheNames = CacheNames.ROLE_PERMISSIONS, key = "#roleId")
    public Set<String> findPermissionsByRoleId(UUID roleId) {
        if (jdbcTemplate == null || roleId == null) {
            return Set.of();
        }
        return new HashSet<>(jdbcTemplate.queryForList(
                "SELECT permission FROM role_permissions WHERE role_id = ?",
                String.class,
                roleId));
    }
}
