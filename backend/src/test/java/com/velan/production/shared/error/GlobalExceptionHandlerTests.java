package com.velan.production.shared.error;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.velan.production.shared.api.ApiPaths;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Import(GlobalExceptionHandlerTests.TestErrorController.class)
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTests {

    @Autowired
    private WebApplicationContext wac;

    @Autowired
    private CorrelationIdFilter correlationIdFilter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .addFilters(correlationIdFilter)
                .build();
    }

    @Test
    void validationExceptionReturns400() throws Exception {
        mockMvc.perform(get("/test-errors/validation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("ERR_VALIDATION"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(jsonPath("$.timestamp", endsWith("Z")));
    }

    @Test
    void notFoundExceptionReturns404() throws Exception {
        mockMvc.perform(get("/test-errors/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ERR_NOT_FOUND"));
    }

    @Test
    void conflictExceptionReturns409() throws Exception {
        mockMvc.perform(get("/test-errors/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_CONFLICT"));
    }

    @Test
    void businessRuleExceptionReturns422() throws Exception {
        mockMvc.perform(get("/test-errors/business-rule"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ERR_BUSINESS_RULE"));
    }

    @Test
    void beanValidationReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/test-errors/bean-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_VALIDATION"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    void malformedJsonAndMissingParamReturn400() throws Exception {
        mockMvc.perform(post("/test-errors/bean-validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{bad-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_VALIDATION"));

        mockMvc.perform(get("/test-errors/missing-param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_VALIDATION"));
    }

    @Test
    void unsupportedMethodAndMediaTypeReturn405And415() throws Exception {
        mockMvc.perform(delete("/test-errors/validation"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("ERR_UNSUPPORTED"));

        mockMvc.perform(post("/test-errors/bean-validation")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("ERR_UNSUPPORTED"));
    }

    @Test
    void dataIntegritySqlStatesAndImmutableGuardMapCorrectly() throws Exception {
        mockMvc.perform(get("/test-errors/sql-23505"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_CONFLICT"));

        mockMvc.perform(get("/test-errors/sql-23514"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("ERR_VALIDATION"));

        mockMvc.perform(get("/test-errors/immutable-guard"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ERR_CONFLICT"))
                .andExpect(jsonPath("$.message", containsString("cannot be changed")));
    }

    @Test
    void unexpectedExceptionReturns500WithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/test-errors/internal"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("ERR_INTERNAL"))
                .andExpect(jsonPath("$.message", not(containsString("SELECT * FROM"))))
                .andExpect(jsonPath("$.message", not(containsString("RuntimeException"))));
    }

    @Test
    void correlationIdIsPreservedOrGeneratedAndAuthHeaderNeverLogged(CapturedOutput output) throws Exception {
        String validUuid = UUID.randomUUID().toString();
        mockMvc.perform(get("/test-errors/ok")
                        .header(CorrelationIdFilter.HEADER_NAME, validUuid)
                        .header("Authorization", "Bearer SECRET_TOKEN_VALUE"))
                .andExpect(status().isOk())
                .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, validUuid));

        mockMvc.perform(get("/test-errors/ok")
                        .header(CorrelationIdFilter.HEADER_NAME, "not-a-uuid"))
                .andExpect(status().isOk())
                .andExpect(header().exists(CorrelationIdFilter.HEADER_NAME));

        assertFalse(output.getOut().contains("SECRET_TOKEN_VALUE"), "Authorization header must never be logged");
    }

    @Test
    void accessLogCarriesCorrelationIdInMdc() throws Exception {
        Logger accessLogger = (Logger) LoggerFactory.getLogger("ACCESS_LOG");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        accessLogger.addAppender(appender);
        try {
            String cid = UUID.randomUUID().toString();
            mockMvc.perform(get("/test-errors/ok").header(CorrelationIdFilter.HEADER_NAME, cid))
                    .andExpect(status().isOk());

            boolean found = appender.list.stream()
                    .anyMatch(e -> cid.equals(e.getMDCPropertyMap().get(CorrelationIdFilter.MDC_KEY)));
            org.junit.jupiter.api.Assertions.assertTrue(found, "ACCESS_LOG event must carry correlationId in MDC");
        } finally {
            accessLogger.detachAppender(appender);
        }
    }

    @Test
    void jacksonSerializesInstantAsIso8601Utc() throws Exception {
        mockMvc.perform(get("/test-errors/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.now", endsWith("Z")));
    }

    @RestController
    static class TestErrorController {

        @GetMapping("/test-errors/ok")
        public OkDto ok() {
            return new OkDto(ApiPaths.V1, Instant.now());
        }

        @GetMapping("/test-errors/validation")
        public void validation() {
            throw new ValidationException("Invalid input");
        }

        @GetMapping("/test-errors/not-found")
        public void notFound() {
            throw new NotFoundException("Missing item");
        }

        @GetMapping("/test-errors/conflict")
        public void conflict() {
            throw new ConflictException("Already exists");
        }

        @GetMapping("/test-errors/business-rule")
        public void businessRule() {
            throw new BusinessRuleException("Rule violated");
        }

        @PostMapping("/test-errors/bean-validation")
        public void beanValidation(@Valid @RequestBody NameReq req) {
        }

        @GetMapping("/test-errors/missing-param")
        public void missingParam(@RequestParam String requiredParam) {
        }

        @GetMapping("/test-errors/sql-23505")
        public void sql23505() {
            throw new DataIntegrityViolationException("dup", new SQLException("dup", "23505"));
        }

        @GetMapping("/test-errors/sql-23514")
        public void sql23514() {
            throw new DataIntegrityViolationException("chk", new SQLException("chk", "23514"));
        }

        @GetMapping("/test-errors/immutable-guard")
        public void immutableGuard() {
            throw new DataIntegrityViolationException("guard",
                    new SQLException("operator_values_immutable_guard triggered", "P0001"));
        }

        @GetMapping("/test-errors/internal")
        public void internal() {
            throw new RuntimeException("SELECT * FROM secret_table failed");
        }

        record NameReq(@NotBlank String name) {}
        record OkDto(String api, Instant now) {}
    }
}
