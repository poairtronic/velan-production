package com.velan.production.integration.api;

/**
 * Acknowledgement returned by ECR upon accepting an exception payload.
 */
public record EcrAck(String ecrReference) {

    public EcrAck {
        if (ecrReference == null || ecrReference.isBlank()) {
            throw new IllegalArgumentException("ecrReference must not be blank");
        }
    }
}
