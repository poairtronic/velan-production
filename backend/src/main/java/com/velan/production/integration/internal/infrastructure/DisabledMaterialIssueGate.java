package com.velan.production.integration.internal.infrastructure;

import com.velan.production.integration.api.IntegrationResult;
import com.velan.production.integration.api.MaterialIssueGate;

/**
 * Disabled stub for RM material issue gate. Never returns issued=true.
 */
class DisabledMaterialIssueGate implements MaterialIssueGate {

    @Override
    public IntegrationResult<Boolean> isIssued(String sizeCardNo, String productName) {
        return IntegrationResult.disabled();
    }

    @Override
    public IntegrationResult<Boolean> isReplacementMaterialIssued(String productionRef) {
        return IntegrationResult.disabled();
    }
}
