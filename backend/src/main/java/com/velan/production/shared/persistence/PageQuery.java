package com.velan.production.shared.persistence;

import java.util.List;
import java.util.Set;

/**
 * Standard pagination request parameters (default size 20, max size 100).
 */
public record PageQuery(int page, int size, String sort) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    public PageQuery {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size <= 0) {
            size = DEFAULT_SIZE;
        }
        if (size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be <= " + MAX_SIZE);
        }
    }

    public static PageQuery of(Integer page, Integer size, String sort) {
        int p = page == null ? 0 : page;
        int s = size == null ? DEFAULT_SIZE : size;
        return new PageQuery(p, s, sort);
    }

    public void validateSort(Set<String> allowedFields) {
        if (sort == null || sort.isBlank()) {
            return;
        }
        String field = sort.split(",")[0].trim();
        if (!allowedFields.contains(field)) {
            throw new IllegalArgumentException("Unsupported sort field: " + field);
        }
    }

    public record PageResult<T>(
            List<T> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }
}
