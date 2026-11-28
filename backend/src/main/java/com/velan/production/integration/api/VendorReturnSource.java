package com.velan.production.integration.api;

import java.time.Instant;
import java.util.List;

/**
 * Port for pulling vendor DC return statuses from RM.
 */
public interface VendorReturnSource {

    IntegrationResult<List<VendorReturnNotice>> fetchSince(Instant since);
}
