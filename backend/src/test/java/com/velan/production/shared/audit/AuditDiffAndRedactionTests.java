package com.velan.production.shared.audit;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditDiffAndRedactionTests {

    @Test
    void diffHandlesCreateUpdateAndDelete() {
        var create = AuditDiff.of(null, Map.of("name", "A", "qty", 5));
        assertNull(create.oldValue());
        assertEquals(Map.of("name", "A", "qty", 5), create.newValue());

        var update = AuditDiff.of(Map.of("name", "A", "qty", 5), Map.of("name", "A", "qty", 10));
        assertEquals(Map.of("qty", 5), update.oldValue());
        assertEquals(Map.of("qty", 10), update.newValue());

        var delete = AuditDiff.of(Map.of("name", "A"), null);
        assertEquals(Map.of("name", "A"), delete.oldValue());
        assertNull(delete.newValue());
    }

    @Test
    void redactorRemovesDefaultKeysTopLevelAndNestedAndSupportsCustomKeys() {
        Map<String, Object> input = Map.of(
                "username", "emp1",
                "password", "p1",
                "PASSWORD_HASH", "h1",
                "nested", Map.of("token", "t1", "pin", "1234", "safe", "ok")
        );
        Map<String, Object> redacted = AuditRedactor.redact(input, AuditProperties.DEFAULT_REDACT_KEYS);
        assertTrue(redacted.containsKey("username"));
        assertFalse(redacted.containsKey("password"));
        assertFalse(redacted.containsKey("PASSWORD_HASH"));
        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) redacted.get("nested");
        assertFalse(nested.containsKey("token"));
        assertFalse(nested.containsKey("pin"));
        assertEquals("ok", nested.get("safe"));

        Map<String, Object> custom = AuditRedactor.redact(Map.of("customSecret", "x", "keep", "y"), List.of("customsecret"));
        assertFalse(custom.containsKey("customSecret"));
        assertEquals("y", custom.get("keep"));
    }
}
