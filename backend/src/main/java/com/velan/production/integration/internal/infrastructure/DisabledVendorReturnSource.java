package com.velan.production.integration.internal.infrastructure;

import com.velan.production.integration.api.IntegrationResult;
import com.velan.production.integration.api.VendorReturnNotice;
import com.velan.production.integration.api.VendorReturnSource;
import java.time.Instant;
import java.util.List;

/**
 * Disabled stub for RM vendor DC return polling.
 */
class DisabledVendorReturnSource implements VendorReturnSource {

    @Override
    public IntegrationResult<List<VendorReturnNotice>> fetchSince(Instant since) {
        return IntegrationResult.disabled();
    }
}
