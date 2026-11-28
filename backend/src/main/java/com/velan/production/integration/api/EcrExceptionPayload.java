package com.velan.production.integration.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Exception payload sent to ECR via the outbox worker.
 */
public record EcrExceptionPayload(
        UUID productionRef,
        String sizeCardNo,
        String productName,
        String pieceSerial,
        String operation,
        String exceptionType,
        String dispositionType,
        String reason) {

    public EcrExceptionPayload {
        Objects.requireNonNull(productionRef, "productionRef must not be null");
        if (sizeCardNo == null || sizeCardNo.isBlank()) {
            throw new IllegalArgumentException("sizeCardNo must not be blank");
        }
        if (productName == null || productName.isBlank()) {
            throw new IllegalArgumentException("productName must not be blank");
        }
        if (pieceSerial == null || pieceSerial.isBlank()) {
            throw new IllegalArgumentException("pieceSerial must not be blank");
        }
        if (operation == null || operation.isBlank()) {
            throw new IllegalArgumentException("operation must not be blank");
        }
        if (exceptionType == null || exceptionType.isBlank()) {
            throw new IllegalArgumentException("exceptionType must not be blank");
        }
        if (dispositionType == null || dispositionType.isBlank()) {
            throw new IllegalArgumentException("dispositionType must not be blank");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }
}
