package com.velan.production.shared.security;

import com.velan.production.shared.error.ErrorResponse;
import com.velan.production.shared.error.ValidationException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTests {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void rejectsShortAndAcceptsMinimumLength() {
        List<ErrorResponse.FieldErrorItem> shortResult = policy.validate("Abc1234", "operator1", "EMP01", null);
        assertEquals(List.of(PasswordPolicy.CODE_TOO_SHORT), codes(shortResult));

        List<ErrorResponse.FieldErrorItem> okResult = policy.validate("Abc12345", "operator1", "EMP01", null);
        assertTrue(okResult.isEmpty());
    }

    @Test
    void enforcesUtf8ByteLimitWithoutTruncation() {
        String exactly72Bytes = "a1" + "b".repeat(70);
        assertEquals(72, exactly72Bytes.getBytes(StandardCharsets.UTF_8).length);
        assertTrue(policy.validate(exactly72Bytes, "operator1", "EMP01", null).isEmpty());

        String seventyThreeBytes = "a1" + "b".repeat(71);
        assertEquals(73, seventyThreeBytes.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(List.of(PasswordPolicy.CODE_TOO_LONG), codes(policy.validate(seventyThreeBytes, "operator1", "EMP01", null)));

        // Multi-byte UTF-8 characters: 35 * 2 bytes ('é') + 2 ASCII bytes = 72 bytes (passes)
        String multiByte72 = "é".repeat(35) + "a1";
        assertEquals(37, multiByte72.length());
        assertEquals(72, multiByte72.getBytes(StandardCharsets.UTF_8).length);
        assertTrue(policy.validate(multiByte72, "operator1", "EMP01", null).isEmpty());

        // 36 * 2 bytes ('é') + 2 ASCII bytes = 74 bytes (fails even though char length is 38)
        String multiByte74 = "é".repeat(36) + "a1";
        assertEquals(38, multiByte74.length());
        assertEquals(74, multiByte74.getBytes(StandardCharsets.UTF_8).length);
        assertEquals(List.of(PasswordPolicy.CODE_TOO_LONG), codes(policy.validate(multiByte74, "operator1", "EMP01", null)));
    }

    @Test
    void requiresLetterAndDigit() {
        assertEquals(List.of(PasswordPolicy.CODE_MISSING_LETTER), codes(policy.validate("12345678", "op1", "E1", null)));
        assertEquals(List.of(PasswordPolicy.CODE_MISSING_DIGIT), codes(policy.validate("Abcdefgh", "op1", "E1", null)));
    }

    @Test
    void rejectsEqualsLoginEmployeeIdOrCurrentPassword() {
        assertEquals(List.of(PasswordPolicy.CODE_EQUALS_LOGIN),
                codes(policy.validate("Operator1", "operator1", "EMP001", null)));
        assertEquals(List.of(PasswordPolicy.CODE_EQUALS_EMPLOYEE_ID),
                codes(policy.validate("Emp00123", "operator1", "EMP00123", null)));
        assertEquals(List.of(PasswordPolicy.CODE_EQUALS_CURRENT),
                codes(policy.validate("Current1", "operator1", "EMP001", "Current1")));
    }

    @Test
    void violationMessagesNeverContainPassword() {
        String secret = "12345678";
        ValidationException ex = assertThrows(ValidationException.class,
                () -> policy.validateOrThrow(secret, "op1", "EMP1", secret));
        assertFalse(ex.getMessage().contains(secret));
        for (ErrorResponse.FieldErrorItem item : ex.getFieldErrors()) {
            assertFalse(item.message().contains(secret));
        }
    }

    private static List<String> codes(List<ErrorResponse.FieldErrorItem> items) {
        return items.stream().map(ErrorResponse.FieldErrorItem::code).toList();
    }
}
