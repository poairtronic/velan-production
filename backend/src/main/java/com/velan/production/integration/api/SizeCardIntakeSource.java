package com.velan.production.integration.api;

import java.time.Instant;
import java.util.List;

/**
 * Port for pulling Size Card intake notices from RM.
 */
public interface SizeCardIntakeSource {

    IntegrationResult<List<SizeCardIntakeNotice>> fetchSince(Instant since);
}
