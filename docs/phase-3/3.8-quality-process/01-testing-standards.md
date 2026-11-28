# Part 3.8 — Testing Standards, Git Workflow, Code Review, and Phase 3 Closure
## Document 01: Testing Pyramid, State Machine Verification, and Test Standards

---

### 1. Confirmed Rules

#### 1.1 The Testing Pyramid and Tooling Ecosystem
Quality assurance follows a strict three-tier test pyramid optimized for speed, reliability, and regression prevention:

| Test Layer | Share (%) | Execution Speed | Scope & Purpose | Core Tooling |
|:---|:---:|:---:|:---|:---|
| **Unit Tests** | **70%** | $< 10\text{ ms}$ / test | Fast, isolated verification of domain business logic, math calculations, and state machines. Zero database, zero network. | **JUnit 5**, **Mockito**, **AssertJ**, **Vitest** |
| **Slice & Integration Tests** | **20%** | $100\text{ ms} - 2\text{ s}$ / test | Spring Data repositories, Flyway migrations, API controllers, security filters, WireMock adapters, and component integration. | **Testcontainers (PostgreSQL 16)**, **WireMock**, **React Testing Library** |
| **End-to-End (E2E) Tests** | **10%** | $5\text{ s} - 30\text{ s}$ / journey | Cross-cutting user journeys executing against full containerized application. | **Playwright** (Chromium) |

#### 1.2 File Naming Conventions
* **Backend Unit Tests:** `*Test.java` (e.g. `PieceLifecycleTest.java`, `ToleranceEngineTest.java`).
* **Backend Integration Tests:** `*IT.java` (e.g. `PieceRepositoryIT.java`, `ExecutionControllerIT.java`).
* **Architecture Tests:** `*ArchitectureTest.java` (e.g. `ModularMonolithArchitectureTest.java`).
* **Frontend Component Tests:** `*.test.tsx` (e.g. `OperatorBulkGrid.test.tsx`).
* **Frontend E2E Journeys:** `*.spec.ts` (e.g. `operator-execution.spec.ts`).

#### 1.3 Strict Zero-External-Calls Rule in Tests
* **Core Rule:** Automated test suites must **NEVER** make outbound network calls to real external systems (Raw Material system, ECR intake, Cloudflare R2, or real SMTP servers).
* **Enforcement:**
  - RM and ECR endpoints are stubbed using **WireMock**.
  - Cloudflare R2 is mocked using **LocalStack S3** or simulated via mock S3 beans.
  - SMTP email delivery is intercepted using a mock JavaMailSender or GreenMail.

#### 1.4 Test Data Builders Pattern
Tests must construct domain entities using fluent, strongly typed **Test Data Builders** providing valid default values and chainable override methods:

```java
Piece piece = PieceTestDataBuilder.aPiece()
    .withSerialNumber("V-1024-001")
    .withStatus(PieceStatus.IN_PRODUCTION)
    .withMaterialReady(true)
    .build();
```

---

### 2. Proposed Design

#### 2.1 State Machine Verification Standards (The 10 State Machines)
Every state machine in the platform must be verified by an exhaustive, parameterized transition-matrix test verifying both valid transitions and illegal move rejections:

```java
@ParameterizedTest
@MethodSource("provideValidTransitions")
void shouldPermitValidTransitions(PieceStatus from, PieceEvent event, PieceStatus to) {
    Piece piece = PieceTestDataBuilder.aPiece().withStatus(from).build();
    piece.fire(event);
    assertThat(piece.getStatus()).isEqualTo(to);
}

@ParameterizedTest
@MethodSource("provideIllegalTransitions")
void shouldRejectIllegalTransitions(PieceStatus from, PieceEvent event) {
    Piece piece = PieceTestDataBuilder.aPiece().withStatus(from).build();
    assertThatThrownBy(() -> piece.fire(event))
        .isInstanceOf(InvalidStateTransitionException.class);
}
```

#### Coverage Matrix for All 10 Application State Machines:
1. **Piece Lifecycle:** 10 states (`CREATED` $\to$ `AWAITING_MATERIAL` $\to$ `IN_PRODUCTION` $\dots$ `SCRAPPED`).
2. **Operation Lifecycle:** 10 states (`PENDING` $\to$ `READY` $\to$ `OPENED` $\dots$ `CANCELLED`).
3. **QC Inspection Lifecycle:** `QC_PENDING` $\to$ `QC_IN_PROGRESS` $\to$ `PASSED` / `FAILED`.
4. **QC Exception Lifecycle:** `PENDING_PM_DISPOSITION` $\to$ 4 approved outcomes.
5. **Replacement Piece Lineage:** Child piece generation inheriting set slot (`-R1`).
6. **Tolerance Revision Lifecycle:** `DRAFT` $\to$ `SUBMITTED` $\to$ `APPROVED` $\to$ `SUPERSEDED`.
7. **Process Template Versioning:** `DRAFT` $\to$ `APPROVED` $\to$ `ACTIVE` $\to$ `DEPRECATED`.
8. **Size Card Header:** `DRAFT` $\to$ `SUBMITTED` $\to$ `IN_PRODUCTION` $\dots$ `DELIVERED`.
9. **Product Line Item:** `PENDING_MATERIAL` $\to$ `IN_PRODUCTION` $\to$ `COMPLETED`.
10. **ECR Outbox Events:** `PENDING` $\to$ `SENDING` $\to$ `SENT` / `AMBIGUOUS_TIMEOUT` / `DEAD_LETTER`.

#### 2.2 Concurrency and Idempotency Testing Standards
1. **Concurrent Race Condition Tests:**
   * Multi-threaded integration tests utilize `ExecutorService` (10 concurrent threads) to simulate simultaneous operation claims or submits on the same piece.
   * **Assertion:** Exactly 1 thread succeeds (HTTP 200); remaining 9 threads fail gracefully with `409 Conflict` (`OptimisticLockException`).
2. **Idempotency Replay Verification:**
   * Submitting identical payloads with the same `Idempotency-Key` must return identical cached responses with header `Idempotency-Replay: true`.

#### 2.3 Automated Permission Test Matrix Generator
An automated test runner executes every REST API endpoint against all 6 primary roles:
* Generates a 2D test grid: **49 Endpoints $\times$ 6 Roles = 294 Assertions**.
* Asserts that permitted roles receive `200/201` and non-permitted roles receive `403 Forbidden`.

#### 2.4 History & Append-Only Immutability Tests
Integration tests execute direct SQL `UPDATE` and `DELETE` queries on `audit_logs` and `operation_measurements`:
* **Assertion:** The database trigger aborts the query, throwing `PSQLException: TAMPER_DETECTED`.

#### 2.5 Architecture Testing with ArchUnit
ArchUnit tests enforce modular monolith rules during `mvn test`:
* `noClasses().that().resideInAPackage("..domain..").should().dependOnClassesThat().resideInAPackage("..integration..")`
* `classes().that().resideInAPackage("..modules.execution..").should().onlyBeAccessedByClassesThat().resideInAnyPackage("..modules.execution..", "..api..")`

#### 2.6 Load Testing Baseline & Code Coverage Targets
1. **Load Test Target (k6 / Gatling):**
   * **Baseline User Concurrency:** **15 to 20 concurrent shop-floor operators** *(ASSUMPTION)*.
   * **Throughput Target:** $\ge 50$ requests/second.
   * **Latency Benchmark:** p95 $< 200\text{ ms}$, p99 $< 500\text{ ms}$ *(ASSUMPTION)*.
   * **Error Threshold:** $0.00\%$ 5xx server errors under peak load.
2. **Code Coverage Targets (JaCoCo & Vitest):**
   * Domain business logic and state machines: **$\ge 90\%$ line coverage** *(ASSUMPTION)*.
   * Controllers, security, and integration adapters: **$\ge 75\%$ line coverage** *(ASSUMPTION)*.
   * Overall platform code coverage: **$\ge 75\%$ line coverage** *(ASSUMPTION)*.

---

### 3. Technical Assumptions
1. **ASSUMPTION:** Testcontainers runs against Docker on developer machines and in GitHub Actions CI runners.
2. **ASSUMPTION:** Test execution in CI completes in under 10 minutes (Unit + Slice + ArchUnit $< 3\text{ min}$; Playwright E2E $< 5\text{ min}$).

---

### 4. Open Questions
* **C-1 (Completion Workflow Model):** E2E tests for assembled sets and packing checklists remain conditioned on Conflict C-1.
