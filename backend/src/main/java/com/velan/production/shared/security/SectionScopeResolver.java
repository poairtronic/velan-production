package com.velan.production.shared.security;

import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SectionScopeResolver {

    private final PermissionRepository repository;

    public SectionScopeResolver(PermissionRepository repository) {
        this.repository = repository;
    }

    public SectionScope resolve(UUID userId) {
        if (userId == null) {
            return SectionScope.all();
        }
        return SectionScope.of(repository.findUserSections(userId));
    }
}
