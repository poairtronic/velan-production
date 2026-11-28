package com.velan.production.integration.api;

/**
 * Port for pushing exception notifications to ECR.
 * Called only by the future outbox worker.
 */
public interface EcrGateway {

    IntegrationResult<EcrAck> send(EcrExceptionPayload payload);
}
