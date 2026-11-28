package com.velan.production.integration.internal.infrastructure;

import com.velan.production.integration.api.IntegrationResult;
import com.velan.production.integration.api.SizeCardIntakeNotice;
import com.velan.production.integration.api.SizeCardIntakeSource;
import java.time.Instant;
import java.util.List;

/**
 * Disabled stub for RM Size Card intake.
 */
class DisabledSizeCardIntakeSource implements SizeCardIntakeSource {

    @Override
    public IntegrationResult<List<SizeCardIntakeNotice>> fetchSince(Instant since) {
        return IntegrationResult.disabled();
    }
}
