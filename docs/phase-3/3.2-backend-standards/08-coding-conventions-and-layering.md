# Part 3.2 — Backend Architecture and Coding Standards
## Document 08: Coding Conventions, Layering, Error Mapping, and Entity Model Policy

> **Status & Scope Note:** Added in Phase 4.7 to close gaps in Part 3.2, aligning the exception hierarchy and JSON error envelope with Part 3.4 (`02-error-handling-and-error-catalogue.md`), consolidating coding conventions, and explicitly recording the JPA-annotations-on-domain-entities decision as **OPEN** for the owner. Supplements [04-coding-conventions-and-layering.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.2-backend-standards/04-coding-conventions-and-layering.md).

---

### 1. Confirmed Rules (Frozen Baseline)

* **Java 21 & Spring Boot Modular Monolith (`D-13`, `ADR-01`):** Single deployable artifact; cross-module interaction only via `api/` facades or domain events (`ARCH-01`–`ARCH-14`).
* **Decimal Precision (`SKILLS.md §4.2`, `3.4-01 §1.5`):** Never use `float` or `double` for measurements or tolerances. Use `BigDecimal` in Java, `NUMERIC` in PostgreSQL, and JSON Strings in REST payloads.
* **UTC Storage (`SKILLS.md §4.2`, `3.3-02 §2.1`):** All timestamps stored as UTC (`TIMESTAMPTZ` / `java.time.Instant`), serialized as ISO-8601 UTC (`...Z`), and formatted to IST on the frontend only.
* **Backend Authorization & Audit (`D-03`, `BR-052`, `BR-054`):** Every public `@RestController` method requires `@PreAuthorize` (`ARCH-03`); state changes and privileged actions write immutable `audit_logs`.
* **Zero Entity Leakage (`ARCH-04`, `SKILLS.md §5`):** Controllers must never accept or return JPA `@Entity` classes; DTOs and entities are strictly separated.

---

### 2. Proposed Design: Coding Conventions & Layering

#### 2.1 DTOs as Immutable Java Records
* All inbound commands, outbound query responses, and domain events in `<module>.api.{command,query,event}` **must** be Java `record` types (Java 21 LTS).
* Collection fields inside records must be copied defensively (`List.copyOf(...)`) when instantiated outside Jackson, or validated via Jakarta annotations.

#### 2.2 Entity-to-DTO Mapping Strategy
* **Tool:** **MapStruct** (`org.mapstruct:mapstruct`) with `@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)` placed in `<module>.internal.mapper`.
* **Rule:** Compile-time code generation only (zero runtime reflection). Hand-written mapping helpers are permitted only for non-trivial aggregate projections where JPQL record constructor expressions (`SELECT new ...`) fetch read views directly.

#### 2.3 Request Validation
* Controller endpoints validate request bodies with `@Valid` and path/query parameters with Jakarta Bean Validation (`@NotNull`, `@NotBlank`, `@Positive`, `@DecimalMin`, `@Size`, `@Pattern`).
* `@RestControllerAdvice` catches `MethodArgumentNotValidException` and `ConstraintViolationException` and maps field violations into `ApiErrorResponse.details[]` (`3.4-02 §3.1`).

#### 2.4 Exception Hierarchy Mapped to Part 3.4 Error Codes
All domain exceptions extend abstract `VelanException` (in `common`), holding `ErrorCode code` (`MODULE_REASON`), `HttpStatus status`, and `List<ErrorDetail> details` (aligned strictly with [3.4 / 02-error-handling-and-error-catalogue.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.4-api-standards/02-error-handling-and-error-catalogue.md)):

| Exception Class (`VelanException` / Spring) | HTTP Status | Part 3.4 Catalogue Error Code Examples |
|:---|:---:|:---|
| `MalformedRequestException` | `400` | `ORDER_IMPORT_INVALID_FILE`, `EXECUTION_SERIAL_BARCODE_INVALID`, `DOCUMENT_UPLOAD_CONFIRM_FAILED` |
| Spring `AuthenticationException` | `401` | `AUTH_TOKEN_MISSING`, `AUTH_TOKEN_EXPIRED`, `AUTH_TOKEN_INVALID`, `DOCUMENT_PRESIGNED_URL_EXPIRED` |
| Spring `AccessDeniedException` / `ForbiddenException` | `403` | `AUTH_PERMISSION_DENIED`, `AUTH_USER_DISABLED`, `EXECUTION_STATION_MISMATCH`, `QUALITY_INDEPENDENT_QC_REQUIRED`, `EXCEPTION_DISPOSITION_UNAUTHORIZED` |
| `EntityNotFoundException` | `404` | `ORDER_NOT_FOUND`, `ORDER_PRODUCT_NOT_FOUND`, `PLANNING_TEMPLATE_NOT_FOUND`, `EXECUTION_PIECE_NOT_FOUND`, `QUALITY_INSPECTION_NOT_FOUND`, `QUALITY_TOLERANCE_NOT_FOUND`, `EXCEPTION_NOT_FOUND`, `DOCUMENT_NOT_FOUND`, `INTEGRATION_EXTERNAL_DC_NOT_FOUND` |
| `InvalidStateTransitionException` | `409` | `ORDER_ALREADY_RELEASED`, `PLANNING_TEMPLATE_VERSION_LOCKED`, `EXECUTION_INVALID_STATE_TRANSITION`, `EXECUTION_OPERATION_ALREADY_OPEN`, `EXECUTION_OPERATION_NOT_OPEN`, `EXECUTION_PIECE_ON_HOLD`, `EXECUTION_PIECE_SCRAPPED`, `QUALITY_INSPECTION_ALREADY_COMPLETED`, `QUALITY_MEASUREMENT_IMMUTABLE`, `EXCEPTION_ALREADY_RESOLVED`, `INTEGRATION_OUTBOX_DEAD_LETTER` |
| `OptimisticLockException` / `ConcurrencyConflictException` | `409` | `ADMIN_OPTIMISTIC_LOCK_CONFLICT`, `ADMIN_IDEMPOTENCY_KEY_REUSED`, `ORDER_DUPLICATE_NUMBER`, `DOCUMENT_REVISION_CONFLICT`, `ADMIN_USER_ALREADY_EXISTS` |
| `ReadinessGateViolationException` | `412` | `PLANNING_UNAPPROVED_ROUTING`, `EXECUTION_MATERIAL_NOT_READY`, `EXECUTION_PREDECESSOR_NOT_PASSED`, `QUALITY_OPERATION_NOT_SUBMITTED` |
| `BusinessValidationException` / `ToleranceValidationException` / `MethodArgumentNotValidException` | `422` | `ORDER_INVALID_QUANTITY`, `ORDER_IMPORT_SCHEMA_MISMATCH`, `PLANNING_INVALID_SEQUENCE`, `EXECUTION_MEASUREMENT_MISSING`, `QUALITY_OUT_OF_SPEC`, `QUALITY_INSTRUMENT_EXPIRED`, `QUALITY_DEFECT_REASON_MANDATORY`, `EXCEPTION_INVALID_DISPOSITION`, `EXCEPTION_TARGET_OP_INVALID`, `EXCEPTION_EXTRA_OP_SPEC_MISSING`, `EXCEPTION_CONCESSION_NOTES_MISSING`, `ADMIN_SYSTEM_CONFIG_INVALID` |
| `UnhandledSystemException` | `500` | `EXCEPTION_SCRAP_REPLACEMENT_FAILED`, `INTEGRATION_OUTBOX_EVENT_FAILED`, `ADMIN_AUDIT_LOG_TAMPER_DETECTED` |
| `ExternalServiceException` | `502` | `INTEGRATION_RM_UNAVAILABLE`, `INTEGRATION_RM_AUTH_FAILED`, `INTEGRATION_ECR_UNAVAILABLE` |
| `ExternalTimeoutException` | `504` | `INTEGRATION_RM_TIMEOUT` |

* `@RestControllerAdvice` serializes the exact Part 3.4 `ApiErrorResponse` envelope: `{ code, message, details, timestamp, path, correlationId }`.

#### 2.5 Clock and Time Rules (UTC Storage)
* Services **must** inject `java.time.Clock` (`Clock.systemUTC()` bean in production; `Clock.fixed(...)` in unit tests). Direct calls to `Instant.now()`, `LocalDateTime.now()`, or `LocalDate.now()` in domain/service classes are forbidden.
* Store instants in `TIMESTAMPTZ` (UTC) mapped to `java.time.Instant`; calendar-only dates (`order_date`, `target_date`) use `DATE` / `java.time.LocalDate`.

#### 2.6 BigDecimal and Scale Rules for Measurements
* Linear dimensional values and tolerances: `BigDecimal` with **scale = 3** and `RoundingMode.HALF_UP` (aligned with `3.3-02 §2.1` `NUMERIC(12, 3)`; note Phase 2 `2.2-02` listed `NUMERIC(12, 4)` — reconcile in Phase 7 Flyway migrations).
* Angles, degrees, and percentages: `BigDecimal` with **scale = 2**, `RoundingMode.HALF_UP` (`NUMERIC(10, 2)`).
* Never compare `BigDecimal` values with `.equals()` (scale-sensitive); always use `.compareTo(...) == 0`.
* Serialized over REST as JSON Strings (e.g. `"150.250"`).

#### 2.7 JSON Rules
* **REST APIs:** `camelCase` keys; `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS = false`; ISO-8601 UTC timestamps (`...Z`); measurements serialized as JSON strings.
* **PostgreSQL `JSONB`:** Permitted strictly on `process_template_snapshots.snapshot_json`, `audit_logs.diff_json`/`previous_state`/`new_state`, and `outbox_events.payload` (`3.3-02 §2.7`), using `snake_case` JSON properties.

#### 2.8 Null and `Optional` Rules
* Return `Optional<T>` **only** from single-record finder/query methods that may legitimately yield no result.
* Never use `Optional` as a method parameter, record component, `@Entity` field, or around collections.
* Collection-returning methods never return `null` (return `List.of()` / empty immutable collections).

#### 2.9 Lombok Policy
* **Permitted:** `@RequiredArgsConstructor` (services/controllers), `@Getter` (entities/domain classes), `@Slf4j` (services/listeners/workers), `@Builder` (DTOs/history/events only).
* **Strictly Forbidden on `@Entity` classes:** `@Data`, `@EqualsAndHashCode`, `@ToString` (prevents Hibernate proxy equality bugs, lazy-loading exceptions, and bidirectional recursion).
* **Restricted:** `@Setter` must never expose mutable entity `status` fields (`ARCH-10`).

#### 2.10 Logging Inside Services
* Use SLF4J (`@Slf4j` or `LoggerFactory.getLogger(...)`) with MDC keys populated per request (`correlationId`, `userId`, `pieceSerial`, `scNumber`).
* `INFO` for state transitions and business milestones; `WARN` for optimistic lock conflicts, readiness gate rejections, and outbox retries; `ERROR` for unhandled failures and `DEAD_LETTER` / `AMBIGUOUS_TIMEOUT` alerts.
* Never log passwords, JWTs, R2 pre-signed URLs, `x-internal-secret`, or employee PII (`3.5-04`, `SKILLS.md §7`).

#### 2.11 Formatter and Static-Analysis Tool Names
* **Code Formatter:** **Spotless** (with `google-java-format`).
* **Style Linter:** **Checkstyle** (Google Java Style).
* **Static Bug & Security Analysis:** **SpotBugs** with **FindSecBugs**.
* **Architecture Enforcement:** **ArchUnit** (`1.4.0+`, `ARCH-01`–`ARCH-14`).
* **Coverage & Dependency Security:** **JaCoCo** (`>= 90%` domain/state machines, `>= 75%` overall) and **OWASP Dependency-Check**.

#### 2.12 JPA Annotations on Domain Entities vs. Separate Persistence Model (**OPEN**)
* **The Question:** Are JPA annotations (`@Entity`, `@Table`, `@Column`, `@Version`, `@Enumerated`, `@ManyToOne`) permitted directly on domain entity classes in `<module>.internal.domain`, or must each module maintain separate pure-Java domain models plus separate `*JpaEntity` persistence classes and an extra mapper?
* **Current Status in Phase 3:** Existing documents (`3.2-01 §2.2`, `3.2-02 §2.2`, `3.3-02 §2.2`, `3.8-03 §1.1D`) already show JPA entities inside `internal/domain/` with `ARCH-04` forbidding `@Entity` leakage outside `internal/`, while `3.8-03 §2.1` references "domain entities free of framework / adapter leaks."
* **Recommendation (NOT a decision — OPEN for Owner):** **Prefer a single model with JPA annotations directly on domain entities inside `<module>.internal.domain`**, paired with strict guardrails (`open-in-view=false`, `FetchType.LAZY`, `@Version`, zero `@Data`/`@EqualsAndHashCode`/`@ToString`, package-private visibility, zero public `setStatus(...)`, and `ARCH-01`/`ARCH-04` preventing leakage to other modules or controllers). Maintaining a duplicate persistence model + domain POJO + extra mapper layer adds substantial boilerplate and synchronization bugs for minimal benefit in a modular monolith whose entities map 1:1 to Part 2.2 relational tables.
* **Status:** **OPEN (`O-MOD-02`)** — Owner confirmation required before Phase 8 entity implementation.

---

### 3. Technical Assumptions

1. **ASSUMPTION:** Jackson `ObjectMapper` and Jakarta Bean Validation run with spring-boot-starter-web defaults (`WRITE_DATES_AS_TIMESTAMPS = false`).
2. **ASSUMPTION:** MapStruct annotation processing and Lombok annotation processing are ordered cleanly in `maven-compiler-plugin` via `lombok-mapstruct-binding` when added in Phase 8.

---

### 4. Open Questions

* **O-MOD-02 (JPA Annotations on Domain Entities — OPEN):** Does the owner approve allowing JPA annotations directly on domain entities inside `<module>.internal.domain` (recommended to avoid duplicate mapping), or require a separate `*JpaEntity` persistence layer?
* **O-COD-01 (OPEN):** Display metric millimeters only vs. legacy fractional imperial notation on UI (`3.2-04 §4`).
* **C-1 / C-2 / C-3 (OPEN):** Completion workflow, piece count source, and `SC` naming remain open; error-code extensions depend on their resolution.
