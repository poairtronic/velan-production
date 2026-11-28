package com.velan.production.integration.api;

/**
 * Port for querying material issue readiness from RM.
 */
public interface MaterialIssueGate {

    IntegrationResult<Boolean> isIssued(String sizeCardNo, String productName);

    IntegrationResult<Boolean> isReplacementMaterialIssued(String productionRef);
}
