package com.velan.production.shared.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Computes oldValue/newValue maps for create, update, and delete audit events.
 */
public final class AuditDiff {

    private AuditDiff() {
    }

    public record DiffResult(Map<String, Object> oldValue, Map<String, Object> newValue) {
    }

    public static DiffResult of(Map<String, Object> before, Map<String, Object> after) {
        if (before == null && after == null) {
            return new DiffResult(null, null);
        }
        if (before == null) {
            return new DiffResult(null, new LinkedHashMap<>(after));
        }
        if (after == null) {
            return new DiffResult(new LinkedHashMap<>(before), null);
        }
        Map<String, Object> oldChanged = new LinkedHashMap<>();
        Map<String, Object> newChanged = new LinkedHashMap<>();
        for (String key : before.keySet()) {
            Object b = before.get(key);
            Object a = after.get(key);
            if (!Objects.equals(b, a)) {
                oldChanged.put(key, b);
                newChanged.put(key, a);
            }
        }
        for (String key : after.keySet()) {
            if (!before.containsKey(key)) {
                oldChanged.put(key, null);
                newChanged.put(key, after.get(key));
            }
        }
        return new DiffResult(oldChanged, newChanged);
    }
}
