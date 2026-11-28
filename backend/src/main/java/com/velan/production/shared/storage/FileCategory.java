package com.velan.production.shared.storage;

import java.util.Set;

/**
 * File categories and D9 limits.
 */
public enum FileCategory {
    DRAWING_DOCUMENT(25L * 1024 * 1024, Set.of("pdf", "png", "jpg", "jpeg", "xlsx")),
    SIGNATURE_IMAGE(5L * 1024 * 1024, Set.of("png", "jpg", "jpeg")),
    ARCHIVE_PACK(50L * 1024 * 1024, Set.of("zip", "pdf"));

    private final long maxBytes;
    private final Set<String> allowedExtensions;

    FileCategory(long maxBytes, Set<String> allowedExtensions) {
        this.maxBytes = maxBytes;
        this.allowedExtensions = allowedExtensions;
    }

    public long maxBytes() {
        return maxBytes;
    }

    public Set<String> allowedExtensions() {
        return allowedExtensions;
    }
}
