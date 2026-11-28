package com.velan.production.shared.security;

import com.velan.production.shared.error.ErrorCode;
import com.velan.production.shared.error.ErrorResponse;
import com.velan.production.shared.error.ValidationException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Enforces owner password policy P2:
 * 8 to 72 UTF-8 bytes (never truncated), at least 1 letter and 1 digit,
 * and not equal to login, employee_id, or current password.
 * Never echoes the password in validation messages.
 */
@Component
public class PasswordPolicy {

    public static final int MIN_BYTES = 8;
    public static final int MAX_BYTES = 72;

    public static final String CODE_REQUIRED = "PASSWORD_REQUIRED";
    public static final String CODE_TOO_SHORT = "PASSWORD_TOO_SHORT";
    public static final String CODE_TOO_LONG = "PASSWORD_TOO_LONG";
    public static final String CODE_MISSING_LETTER = "PASSWORD_MISSING_LETTER";
    public static final String CODE_MISSING_DIGIT = "PASSWORD_MISSING_DIGIT";
    public static final String CODE_EQUALS_LOGIN = "PASSWORD_EQUALS_LOGIN";
    public static final String CODE_EQUALS_EMPLOYEE_ID = "PASSWORD_EQUALS_EMPLOYEE_ID";
    public static final String CODE_EQUALS_CURRENT = "PASSWORD_EQUALS_CURRENT";

    public List<ErrorResponse.FieldErrorItem> validate(
            String password,
            String login,
            String employeeId,
            String currentPassword) {
        List<ErrorResponse.FieldErrorItem> violations = new ArrayList<>();
        if (password == null || password.isEmpty()) {
            violations.add(new ErrorResponse.FieldErrorItem(
                    "password", CODE_REQUIRED, "Password is required"));
            return List.copyOf(violations);
        }

        int utf8Bytes = password.getBytes(StandardCharsets.UTF_8).length;
        if (utf8Bytes < MIN_BYTES) {
            violations.add(new ErrorResponse.FieldErrorItem(
                    "password", CODE_TOO_SHORT, "Password must be at least 8 bytes"));
        }
        if (utf8Bytes > MAX_BYTES) {
            violations.add(new ErrorResponse.FieldErrorItem(
                    "password", CODE_TOO_LONG, "Password must not exceed 72 UTF-8 bytes"));
        }

        boolean hasLetter = password.codePoints().anyMatch(Character::isLetter);
        if (!hasLetter) {
            violations.add(new ErrorResponse.FieldErrorItem(
                    "password", CODE_MISSING_LETTER, "Password must contain at least one letter"));
        }

        boolean hasDigit = password.codePoints().anyMatch(Character::isDigit);
        if (!hasDigit) {
            violations.add(new ErrorResponse.FieldErrorItem(
                    "password", CODE_MISSING_DIGIT, "Password must contain at least one digit"));
        }

        if (login != null && !login.isBlank() && password.equalsIgnoreCase(login.trim())) {
            violations.add(new ErrorResponse.FieldErrorItem(
                    "password", CODE_EQUALS_LOGIN, "Password must not equal login"));
        }

        if (employeeId != null && !employeeId.isBlank() && password.equalsIgnoreCase(employeeId.trim())) {
            violations.add(new ErrorResponse.FieldErrorItem(
                    "password", CODE_EQUALS_EMPLOYEE_ID, "Password must not equal employee ID"));
        }

        if (currentPassword != null && !currentPassword.isEmpty() && password.equals(currentPassword)) {
            violations.add(new ErrorResponse.FieldErrorItem(
                    "password", CODE_EQUALS_CURRENT, "New password must not equal current password"));
        }

        return List.copyOf(violations);
    }

    public void validateOrThrow(
            String password,
            String login,
            String employeeId,
            String currentPassword) {
        List<ErrorResponse.FieldErrorItem> violations = validate(password, login, employeeId, currentPassword);
        if (!violations.isEmpty()) {
            throw new ValidationException(
                    ErrorCode.ERR_VALIDATION,
                    "Password does not meet policy requirements",
                    violations);
        }
    }
}
