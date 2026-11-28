package com.velan.production.shared.error;

import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        List<ErrorResponse.FieldErrorItem> fieldErrors = ex instanceof ValidationException ve
                ? ve.getFieldErrors()
                : List.of();
        return build(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), fieldErrors);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<ErrorResponse.FieldErrorItem> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorResponse.FieldErrorItem(
                        fe.getField(),
                        fe.getCode() != null ? fe.getCode() : ErrorCode.ERR_VALIDATION.name(),
                        fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value"))
                .toList();
        return build(HttpStatus.BAD_REQUEST, ErrorCode.ERR_VALIDATION, "Validation failed", fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        List<ErrorResponse.FieldErrorItem> fields = ex.getConstraintViolations().stream()
                .map(cv -> new ErrorResponse.FieldErrorItem(
                        cv.getPropertyPath().toString(),
                        ErrorCode.ERR_VALIDATION.name(),
                        cv.getMessage()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, ErrorCode.ERR_VALIDATION, "Validation failed", fields);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, ErrorCode.ERR_VALIDATION, "Malformed or invalid request", List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ErrorCode.ERR_UNSUPPORTED, "HTTP method not supported", List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ErrorCode.ERR_UNSUPPORTED, "Media type not supported", List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ErrorCode.ERR_NOT_FOUND, "Resource not found", List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex) {
        SQLException sqlEx = findSqlException(ex);
        if (sqlEx != null) {
            String state = sqlEx.getSQLState();
            String msg = sqlEx.getMessage() != null ? sqlEx.getMessage() : "";
            if ("23505".equals(state) || "23503".equals(state)) {
                return build(HttpStatus.CONFLICT, ErrorCode.ERR_CONFLICT, "Data conflict or reference violation", List.of());
            }
            if ("23514".equals(state)) {
                return build(HttpStatus.BAD_REQUEST, ErrorCode.ERR_VALIDATION, "Constraint validation failed", List.of());
            }
            if (msg.contains("operator_values_immutable_guard")) {
                return build(HttpStatus.CONFLICT, ErrorCode.ERR_CONFLICT, "Submitted operator values cannot be changed", List.of());
            }
        }
        return build(HttpStatus.CONFLICT, ErrorCode.ERR_CONFLICT, "Data integrity violation", List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return build(HttpStatus.FORBIDDEN, ErrorCode.ERR_FORBIDDEN, "Access denied", List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception correlationId={}", MDC.get(CorrelationIdFilter.MDC_KEY), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.ERR_INTERNAL, "An unexpected error occurred", List.of());
    }

    private ResponseEntity<ErrorResponse> build(
            HttpStatus status,
            ErrorCode code,
            String message,
            List<ErrorResponse.FieldErrorItem> fieldErrors) {
        ErrorResponse body = new ErrorResponse(
                status.value(),
                code.name(),
                message,
                fieldErrors,
                MDC.get(CorrelationIdFilter.MDC_KEY),
                Instant.now());
        return ResponseEntity.status(status).body(body);
    }

    private static SQLException findSqlException(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            if (cur instanceof SQLException sqlEx) {
                return sqlEx;
            }
            cur = cur.getCause();
        }
        return null;
    }
}
