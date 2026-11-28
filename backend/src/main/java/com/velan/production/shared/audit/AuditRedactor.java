package com.velan.production.shared.audit;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Case-insensitive recursive redaction of sensitive keys from audit payloads.
 */
public final class AuditRedactor {

    private AuditRedactor() {
    }

    public static Map<String, Object> redact(Map<String, Object> source, List<String> redactKeys) {
        if (source == null) {
            return null;
        }
        Set<String> lowerKeys = redactKeys.stream()
                .map(k -> k.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return redactMap(source, lowerKeys);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> redactMap(Map<String, Object> map, Set<String> lowerKeys) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (entry.getKey() != null && lowerKeys.contains(entry.getKey().toLowerCase(Locale.ROOT))) {
                continue;
            }
            Object val = entry.getValue();
            if (val instanceof Map<?, ?> nested) {
                out.put(entry.getKey(), redactMap((Map<String, Object>) nested, lowerKeys));
            } else if (val instanceof List<?> list) {
                out.put(entry.getKey(), redactList(list, lowerKeys));
            } else {
                out.put(entry.getKey(), val);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> redactList(List<?> list, Set<String> lowerKeys) {
        List<Object> out = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> nested) {
                out.add(redactMap((Map<String, Object>) nested, lowerKeys));
            } else if (item instanceof List<?> nestedList) {
                out.add(redactList(nestedList, lowerKeys));
            } else {
                out.add(item);
            }
        }
        return out;
    }
}
