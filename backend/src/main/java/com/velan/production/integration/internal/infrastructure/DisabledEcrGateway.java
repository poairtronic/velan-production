package com.velan.production.integration.internal.infrastructure;

import com.velan.production.integration.api.EcrAck;
import com.velan.production.integration.api.EcrExceptionPayload;
import com.velan.production.integration.api.EcrGateway;
import com.velan.production.integration.api.IntegrationResult;

/**
 * Disabled stub for ECR exception gateway.
 */
class DisabledEcrGateway implements EcrGateway {

    @Override
    public IntegrationResult<EcrAck> send(EcrExceptionPayload payload) {
        return IntegrationResult.disabled();
    }
}
