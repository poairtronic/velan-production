package com.velan.production.shared.cache;

import java.util.Set;

/**
 * Approved in-memory cache names (D8). No other cache name may exist.
 */
public final class CacheNames {

    public static final String ROLES = "roles";
    public static final String ROLE_PERMISSIONS = "rolePermissions";
    public static final String DEPARTMENTS = "departments";
    public static final String SECTIONS = "sections";
    public static final String OPERATIONS = "operations";
    public static final String MACHINES = "machines";

    public static final Set<String> ALL = Set.of(
            ROLES,
            ROLE_PERMISSIONS,
            DEPARTMENTS,
            SECTIONS,
            OPERATIONS,
            MACHINES
    );

    private CacheNames() {
    }
}
