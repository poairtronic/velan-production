package com.velan.production.shared.error;

import java.util.List;
import org.springframework.http.HttpStatus;

public class ValidationException extends ApiException {

    private final List<ErrorResponse.FieldErrorItem> fieldErrors;

    public ValidationException(String message) {
        this(ErrorCode.ERR_VALIDATION, message, List.of());
    }

    public ValidationException(ErrorCode code, String message, List<ErrorResponse.FieldErrorItem> fieldErrors) {
        super(HttpStatus.BAD_REQUEST, code, message);
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public List<ErrorResponse.FieldErrorItem> getFieldErrors() {
        return fieldErrors;
    }
}
