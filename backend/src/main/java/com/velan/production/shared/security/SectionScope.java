package com.velan.production.shared.security;

import java.util.Set;
import java.util.UUID;

/**
 * Section visibility scope for a user:
 * allSections = true when user_sections has no rows; otherwise restricted to sectionIds.
 */
public record SectionScope(boolean allSections, Set<UUID> sectionIds) {

    public static SectionScope all() {
        return new SectionScope(true, Set.of());
    }

    public static SectionScope of(Set<UUID> sectionIds) {
        if (sectionIds == null || sectionIds.isEmpty()) {
            return all();
        }
        return new SectionScope(false, Set.copyOf(sectionIds));
    }
}
