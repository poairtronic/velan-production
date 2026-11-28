package com.velan.production.integration.internal.infrastructure;

import com.velan.production.integration.api.EcrGateway;
import com.velan.production.integration.api.MaterialIssueGate;
import com.velan.production.integration.api.SizeCardIntakeSource;
import com.velan.production.integration.api.VendorReturnSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires disabled integration stubs and fails fast if an integration flag is
 * enabled before its phase 13-14 connector exists.
 */
@Configuration
public class IntegrationConfig {

    private static final String RM_NOT_IMPLEMENTED =
            "RM integration enabled but connector is not implemented yet (phases 13–14)";
    private static final String ECR_NOT_IMPLEMENTED =
            "ECR integration enabled but connector is not implemented yet (phases 13–14)";

    @Bean
    @ConditionalOnMissingBean(SizeCardIntakeSource.class)
    public SizeCardIntakeSource sizeCardIntakeSource(IntegrationProperties properties) {
        if (properties.rm().enabled()) {
            throw new IllegalStateException(RM_NOT_IMPLEMENTED);
        }
        return new DisabledSizeCardIntakeSource();
    }

    @Bean
    @ConditionalOnMissingBean(MaterialIssueGate.class)
    public MaterialIssueGate materialIssueGate(IntegrationProperties properties) {
        if (properties.rm().enabled()) {
            throw new IllegalStateException(RM_NOT_IMPLEMENTED);
        }
        return new DisabledMaterialIssueGate();
    }

    @Bean
    @ConditionalOnMissingBean(VendorReturnSource.class)
    public VendorReturnSource vendorReturnSource(IntegrationProperties properties) {
        if (properties.rm().enabled()) {
            throw new IllegalStateException(RM_NOT_IMPLEMENTED);
        }
        return new DisabledVendorReturnSource();
    }

    @Bean
    @ConditionalOnMissingBean(EcrGateway.class)
    public EcrGateway ecrGateway(IntegrationProperties properties) {
        if (properties.ecr().enabled()) {
            throw new IllegalStateException(ECR_NOT_IMPLEMENTED);
        }
        return new DisabledEcrGateway();
    }
}
