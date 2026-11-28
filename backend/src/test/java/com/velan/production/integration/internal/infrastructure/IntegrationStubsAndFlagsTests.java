package com.velan.production.integration.internal.infrastructure;

import com.velan.production.VelanProductionApplication;
import com.velan.production.integration.api.EcrExceptionPayload;
import com.velan.production.integration.api.EcrGateway;
import com.velan.production.integration.api.IntegrationResult;
import com.velan.production.integration.api.MaterialIssueGate;
import com.velan.production.integration.api.SizeCardIntakeSource;
import com.velan.production.integration.api.VendorReturnNotice;
import com.velan.production.integration.api.VendorReturnSource;
import com.velan.production.integration.api.domain.IntegrationResultState;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationStubsAndFlagsTests {

    @Test
    void disabledStubsReturnDisabledAndNeverIssuedOrThrow() {
        SizeCardIntakeSource scSource = new DisabledSizeCardIntakeSource();
        MaterialIssueGate materialGate = new DisabledMaterialIssueGate();
        VendorReturnSource vendorSource = new DisabledVendorReturnSource();
        EcrGateway ecrGateway = new DisabledEcrGateway();

        var scRes = scSource.fetchSince(Instant.now());
        assertEquals(IntegrationResultState.DISABLED, scRes.state());
        assertTrue(scRes.isDisabled());
        assertFalse(scRes.isAvailable());
        assertNull(scRes.data());
        assertTrue(scRes.optionalData().isEmpty());

        IntegrationResult<Boolean> matRes = materialGate.isIssued("SC-100", "VALVE-A");
        assertEquals(IntegrationResultState.DISABLED, matRes.state());
        assertNull(matRes.data());
        assertFalse(Boolean.TRUE.equals(matRes.data()));

        IntegrationResult<Boolean> replRes = materialGate.isReplacementMaterialIssued("EXC-1");
        assertEquals(IntegrationResultState.DISABLED, replRes.state());
        assertNull(replRes.data());
        assertFalse(Boolean.TRUE.equals(replRes.data()));

        var vendorRes = vendorSource.fetchSince(Instant.now());
        assertEquals(IntegrationResultState.DISABLED, vendorRes.state());
        assertNull(vendorRes.data());

        var ecrRes = ecrGateway.send(new EcrExceptionPayload(
                UUID.randomUUID(), "SC-100", "VALVE-A", "SN-01", "MACH-01",
                "QC_FAIL", "REJECT_AND_RESTART", "Out of tolerance"));
        assertEquals(IntegrationResultState.DISABLED, ecrRes.state());
        assertNull(ecrRes.data());
    }

    @Test
    void vendorReturnNoticeHasNoDateFields() {
        RecordComponent[] components = VendorReturnNotice.class.getRecordComponents();
        for (RecordComponent c : components) {
            Class<?> type = c.getType();
            String name = c.getName().toLowerCase();
            assertFalse(type == Instant.class || type == LocalDate.class || type == LocalDateTime.class
                    || type == OffsetDateTime.class || type == ZonedDateTime.class || type == Date.class,
                    "VendorReturnNotice must not contain date/time fields: " + c.getName());
            assertFalse(name.contains("date") || name.endsWith("at"),
                    "VendorReturnNotice must not contain date-named fields: " + c.getName());
        }
        assertFalse(Arrays.stream(components).anyMatch(c -> c.getName().toLowerCase().contains("return_date")));
    }

    @Test
    void ecrExceptionPayloadRequiresProductionRef() {
        assertThrows(NullPointerException.class, () -> new EcrExceptionPayload(
                null, "SC-100", "VALVE-A", "SN-01", "MACH-01",
                "QC_FAIL", "REJECT_AND_RESTART", "Out of tolerance"));
    }

    @Test
    void flagsDefaultToFalseAndAppStartsWithAllPortsStubbed() {
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(VelanProductionApplication.class)
                .profiles("test")
                .properties("server.port=0")
                .run()) {
            IntegrationProperties props = ctx.getBean(IntegrationProperties.class);
            assertFalse(props.rm().enabled());
            assertFalse(props.ecr().enabled());
            assertNotNull(ctx.getBean(SizeCardIntakeSource.class));
            assertNotNull(ctx.getBean(MaterialIssueGate.class));
            assertNotNull(ctx.getBean(VendorReturnSource.class));
            assertNotNull(ctx.getBean(EcrGateway.class));
            assertTrue(ctx.getBean(SizeCardIntakeSource.class).fetchSince(Instant.EPOCH).isDisabled());
            assertTrue(ctx.getBean(EcrGateway.class).send(new EcrExceptionPayload(
                    UUID.randomUUID(), "SC-1", "P1", "S1", "OP1", "QC_FAIL", "CONTINUE", "ok")).isDisabled());
        }
    }

    @Test
    void rmEnabledWithoutRealConnectorFailsStartupWithExactMessage() {
        new ApplicationContextRunner()
                .withUserConfiguration(IntegrationConfig.class)
                .withBean(IntegrationProperties.class,
                        () -> new IntegrationProperties(new IntegrationProperties.Toggle(true),
                                new IntegrationProperties.Toggle(false)))
                .run(context -> {
                    Throwable ex = context.getStartupFailure();
                    assertNotNull(ex);
                    assertTrue(rootCauseMessage(ex)
                                    .contains("RM integration enabled but connector is not implemented yet (phases 13–14)"),
                            "Unexpected failure message: " + rootCauseMessage(ex));
                });
    }

    @Test
    void ecrEnabledWithoutRealConnectorFailsStartupWithExactMessage() {
        new ApplicationContextRunner()
                .withUserConfiguration(IntegrationConfig.class)
                .withBean(IntegrationProperties.class,
                        () -> new IntegrationProperties(new IntegrationProperties.Toggle(false),
                                new IntegrationProperties.Toggle(true)))
                .run(context -> {
                    Throwable ex = context.getStartupFailure();
                    assertNotNull(ex);
                    assertTrue(rootCauseMessage(ex)
                                    .contains("ECR integration enabled but connector is not implemented yet (phases 13–14)"),
                            "Unexpected failure message: " + rootCauseMessage(ex));
                });
    }

    private static String rootCauseMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        return cur.getMessage() == null ? "" : cur.getMessage();
    }
}
