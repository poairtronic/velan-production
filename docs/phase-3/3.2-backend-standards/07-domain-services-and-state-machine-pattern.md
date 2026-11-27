# Part 3.2 — Backend Architecture and Coding Standards
## Document 07: Domain Services, Unified State Machine Pattern, and Append-Only Protection

> **Status & Scope Note:** Added in Phase 4.7 to supply the complete, Part 3.4–aligned domain service and state machine specification indexed under Part 3.2. Reconciles and supersedes discrepancies in [02-domain-services-and-state-machine-pattern.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.2-backend-standards/02-domain-services-and-state-machine-pattern.md) (which omitted `tolerance_revisions` and `packing_records` from the 10-machine table, omitted the transition table and audit entry from its code example, and used non-Part-3.4 error codes).

---

### 1. Confirmed Rules (Frozen Baseline)

* **Single Authoritative State (`BR-013`, `BR-068`):** Every piece, operation, tolerance revision, QC inspection, exception, outbox event, SC header, and SC product line exists in exactly one status at any time. Counts and statuses are derived from persisted piece/entity records, never typed manually.
* **No Direct Status Setters (`ARCH-10`):** Public `setStatus(...)` methods on aggregates and direct SQL status updates bypassing domain services are strictly prohibited.
* **Immutable History & Append-Only Records (`D-06`, `BR-029`, `BR-032`, `BR-034`, `BR-054`):** Measurements (`operation_measurements`, `measurement_values`), QC verdicts (`qc_inspections`), PM dispositions (`pm_dispositions`), tolerance revisions (`tolerance_revisions`), status-history tables, and `audit_logs` are strictly append-only.
* **Independent Blind QC (`D-06`):** QC inspection is mandatory on every operation; operator and QC measurements are stored separately, and QC inputs are never pre-populated.
* **PM-Only Exception Authority (`D-07`, `D-19`):** Flagged pieces wait for `ROLE_PM`, who chooses one of four dispositions: `CONTINUE` (mandatory reason), `EXTRA_PROCESS`, `RETURN_TO_EARLIER_OPERATION`, or `REJECT_AND_RESTART`.
* **No Network Calls in Transactions (`ADR-02`, `ARCH-14`):** External calls (RM, ECR, R2, SMTP) never execute inside `@Transactional`.

---

### 2. Proposed Design: One Standard Pattern for All 10 State Machines

All 10 state machines in Phase 2 (Parts 2.3–2.6) follow one uniform architectural pattern:

1. **Java `enum` for States (and Triggers/Events):**
   * Uppercase `UPPER_SNAKE_CASE` enum values matching PostgreSQL `VARCHAR(50) CHECK (...)` constraints verbatim (`3.3-02 §2.2`, `SKILLS.md §5`).
   * Terminal states (`COMPLETED`, `SCRAPPED`, `DELIVERED`, `CANCELLED`, `PUBLISHED`/`SENT`, `CLOSED`, `VOIDED`, `REJECTED`, `SUPERSEDED`) declare zero outgoing transitions.
2. **Declarative Transition Table (`EnumMap` / `Map<Status, Set<Status>>`):**
   * Each aggregate (or package-private transition policy) defines an immutable `Map<Status, Set<Status>>` mapping every source state to its permitted target states.
   * Any transition not declared in the map fails immediately before state mutation or side effects occur.
3. **Precondition Guards:**
   * Guard predicates run before mutation: e.g. optimistic `@Version` match, `is_material_ready == true`, predecessor operation in `{PASSED, CONCESSION_ACCEPTED, BYPASSED}`, calibrated gauge valid (`calibration_valid_until >= now`), non-blank justification reason, or required role/permission authority.
4. **Single Entry Point per Aggregate Transition:**
   * One intention-revealing domain method on the aggregate root (e.g., `pieceOperation.submitByOperator(operatorId, submittedAt)`) invoked by a single `@Transactional` domain service method per command (`POST /api/v1/{resource}/{id}/actions/{action-name}`).
   * No public `setStatus(...)` setter exists.
5. **Side Effects in Same Transaction:**
   * Atomic child or sibling entity mutations (e.g. unlocking next sequential operation `PENDING -> READY`, inserting `qc_exceptions`, spawning `-R1` replacement piece, or inserting an `outbox_events` row for ECR) execute inside the same `@Transactional` boundary using `Clock`-derived UTC timestamps (`Instant.now(clock)`).
6. **Status-History Record in Same Transaction:**
   * Insert an immutable status-history row (`<aggregate>_status_history`, e.g. `piece_operation_history`, `piece_status_history`) capturing `aggregateId`, `oldStatus`, `newStatus`, `changedByUserId`, `changeReason`, and `recordedAt`.
7. **Audit Entry in Same Transaction:**
   * Append an immutable `audit_logs` entry via `AuditTrailFacade` (or synchronous in-transaction `@EventListener`) capturing `actorUserId`, `actionType`, `entityName`, `entityId`, `previousState`, `newState`, `reason`, `timestamp`, and `correlationId` (`BR-054`).
8. **Post-Commit Domain Event (`AFTER_COMMIT`):**
   * Publish an immutable domain event record (`api/event/`) via `ApplicationEventPublisher`.
   * Asynchronous notifications (SSE push, SMTP dispatch, outbox sweeper wakeup) listen with `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` so external side effects never observe rolled-back state.
9. **Illegal Transition Failure Semantics:**
   * Illegal state transitions throw `InvalidStateTransitionException` (subclass of `VelanException`), mapped by `@RestControllerAdvice` to **HTTP `409 Conflict`** with the Part 3.4 `ApiErrorResponse` payload and `MODULE_REASON` error code (e.g. `EXECUTION_INVALID_STATE_TRANSITION`, `QUALITY_INSPECTION_ALREADY_COMPLETED`, `EXCEPTION_ALREADY_RESOLVED`).
   * Missing readiness gates (material not issued, predecessor not passed) throw `ReadinessGateViolationException` (**HTTP `412 Precondition Failed`**, e.g. `EXECUTION_MATERIAL_NOT_READY`, `EXECUTION_PREDECESSOR_NOT_PASSED`).
   * The `@Transactional` boundary rolls back cleanly—no state change, status history, audit entry, or `AFTER_COMMIT` event is emitted.

---

### 3. Example Snippet (Aggregate Transition Table + Domain Service)

```java
// 1. Aggregate Root (inside com.velan.production.execution.internal.domain)
package com.velan.production.execution.internal.domain;

import com.velan.production.common.error.ErrorCode;
import com.velan.production.common.exception.InvalidStateTransitionException;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "piece_operations")
public class PieceOperation {

    private static final Map<OperationStatus, Set<OperationStatus>> ALLOWED_TRANSITIONS =
        new EnumMap<>(Map.of(
            OperationStatus.PENDING,             Set.of(OperationStatus.READY, OperationStatus.BYPASSED, OperationStatus.CANCELLED),
            OperationStatus.READY,               Set.of(OperationStatus.OPENED, OperationStatus.BYPASSED, OperationStatus.CANCELLED),
            OperationStatus.OPENED,              Set.of(OperationStatus.QC_PENDING, OperationStatus.READY, OperationStatus.CANCELLED),
            OperationStatus.QC_PENDING,          Set.of(OperationStatus.QC_IN_PROGRESS, OperationStatus.CANCELLED),
            OperationStatus.QC_IN_PROGRESS,      Set.of(OperationStatus.PASSED, OperationStatus.FAILED, OperationStatus.QC_PENDING),
            OperationStatus.FAILED,              Set.of(OperationStatus.CONCESSION_ACCEPTED, OperationStatus.READY),
            OperationStatus.PASSED,              Set.of(OperationStatus.READY), // PM reopen only
            OperationStatus.CONCESSION_ACCEPTED, Set.of(OperationStatus.READY), // PM reopen only
            OperationStatus.BYPASSED,            Set.of(),
            OperationStatus.CANCELLED,           Set.of()
        ));

    @Id
    private UUID id;

    @Column(name = "piece_id", nullable = false)
    private UUID pieceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    private OperationStatus status;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    // NO public setStatus(...) setter! Single intention-revealing entry point:
    public OperationStatus submitMeasurements(boolean allMandatoryParametersPresent, Instant now) {
        OperationStatus target = OperationStatus.QC_PENDING;
        if (!ALLOWED_TRANSITIONS.getOrDefault(this.status, Set.of()).contains(target)) {
            throw new InvalidStateTransitionException(
                ErrorCode.EXECUTION_INVALID_STATE_TRANSITION,
                "Cannot transition operation " + id + " from " + status + " to " + target);
        }
        if (!allMandatoryParametersPresent) {
            throw new BusinessValidationException(
                ErrorCode.EXECUTION_MEASUREMENT_MISSING,
                "All mandatory parameters must be recorded before submission.");
        }
        OperationStatus previous = this.status;
        this.status = target;
        this.submittedAt = now;
        return previous;
    }
}
```

```java
// 2. Domain Service (inside com.velan.production.execution.internal.service)
package com.velan.production.execution.internal.service;

import com.velan.production.common.audit.AuditTrailFacade;
import com.velan.production.common.error.ErrorCode;
import com.velan.production.common.exception.EntityNotFoundException;
import com.velan.production.execution.api.event.OperationSubmittedEvent;
import com.velan.production.execution.internal.domain.*;
import com.velan.production.execution.internal.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OperationStateService {

    private final PieceOperationRepository operationRepository;
    private final PieceOperationHistoryRepository historyRepository;
    private final AuditTrailFacade auditTrailFacade;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public void submitOperatorOperation(UUID operationId, UUID actorUserId, boolean allMandatoryPresent, String remarks) {
        // 1. Load aggregate (optimistic @Version protected)
        PieceOperation op = operationRepository.findById(operationId)
            .orElseThrow(() -> new EntityNotFoundException(
                ErrorCode.EXECUTION_PIECE_NOT_FOUND, "Piece operation not found: " + operationId));

        Instant now = Instant.now(clock);

        // 2. Transition table + guard check + single-entry-point mutation (throws 409/422 on failure)
        OperationStatus oldStatus = op.submitMeasurements(allMandatoryPresent, now);
        operationRepository.save(op);

        // 3. Append status-history record in same transaction
        historyRepository.save(PieceOperationHistory.of(
            op.getId(), op.getPieceId(), oldStatus, OperationStatus.QC_PENDING, actorUserId, remarks, now));

        // 4. Append immutable audit_logs entry in same transaction
        auditTrailFacade.recordTransition(
            actorUserId, "OPERATOR_MEASUREMENTS_SUBMITTED", "piece_operations",
            op.getId().toString(), oldStatus.name(), OperationStatus.QC_PENDING.name(), remarks, now);

        // 5. Publish domain event (downstream SSE / QC queue listeners use AFTER_COMMIT)
        eventPublisher.publishEvent(new OperationSubmittedEvent(
            op.getId(), op.getPieceId(), oldStatus, OperationStatus.QC_PENDING, actorUserId, now));
    }
}
```

---

### 4. Table of All 10 State Machines

Owning module names below reflect the Phase 2/3 baseline (`dispatch` vs `completion` and package naming remain subject to owner resolution in [Phase-4.7-Module-Reconciliation.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-4/Phase-4.7-Module-Reconciliation.md)).

| # | State Machine | Owning Module | Aggregate Root (`Table`) | Phase 2 Transition Document | Illegal Transition Exception & Part 3.4 Error Code |
|:-:|:---|:---|:---|:---|:---|
| 1 | **Physical Piece** | `execution` | `Piece` (`pieces`) | [2.3 / 02-piece-state-transitions-and-events.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.3-piece-lifecycle-state-machine/02-piece-state-transitions-and-events.md) | `InvalidStateTransitionException` (`EXECUTION_INVALID_STATE_TRANSITION`, `EXECUTION_PIECE_ON_HOLD`, `EXECUTION_PIECE_SCRAPPED`); `ReadinessGateViolationException` (`EXECUTION_MATERIAL_NOT_READY`) |
| 2 | **Routing Operation** | `execution` | `PieceOperation` (`piece_operations`) | [2.4 / 02-operation-state-transitions-and-events.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.4-operation-lifecycle-state-machine/02-operation-state-transitions-and-events.md) | `InvalidStateTransitionException` (`EXECUTION_INVALID_STATE_TRANSITION`, `EXECUTION_OPERATION_ALREADY_OPEN`, `EXECUTION_OPERATION_NOT_OPEN`); `ReadinessGateViolationException` (`EXECUTION_PREDECESSOR_NOT_PASSED`) |
| 3 | **Tolerance Revision** | `engineering` *(or `planning`/`quality`)* | `ToleranceRevision` (`tolerance_revisions`) | [2.5 / 02-qc-inspection-state-machine-and-tolerances.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/02-qc-inspection-state-machine-and-tolerances.md) | `InvalidStateTransitionException` (`409`; `PLANNING_TEMPLATE_VERSION_LOCKED` / new code needed in Part 3.4, e.g., `QUALITY_TOLERANCE_INVALID_TRANSITION`) |
| 4 | **QC Inspection Session** | `quality` | `QCInspection` (`qc_inspections`) | [2.5 / 02-qc-inspection-state-machine-and-tolerances.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/02-qc-inspection-state-machine-and-tolerances.md) | `InvalidStateTransitionException` (`QUALITY_INSPECTION_ALREADY_COMPLETED`); `ReadinessGateViolationException` (`QUALITY_OPERATION_NOT_SUBMITTED`) |
| 5 | **QC Exception Case** | `exception` *(pending reconciliation)* | `QCException` (`qc_exceptions`) | [2.5 / 03-exception-lifecycle-and-pm-dispositions.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/03-exception-lifecycle-and-pm-dispositions.md) | `InvalidStateTransitionException` (`EXCEPTION_ALREADY_RESOLVED`); `BusinessValidationException` (`EXCEPTION_INVALID_DISPOSITION`, `EXCEPTION_CONCESSION_NOTES_MISSING`) |
| 6 | **Transactional Outbox Event** | `integration` | `OutboxEvent` (`outbox_events`) | [2.5 / 04-ecr-outbox-integration-state-machine.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/04-ecr-outbox-integration-state-machine.md) *(reconciled in [3.7-03](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.7-integration-conventions/03-ecr-outbox-standard.md))* | `InvalidStateTransitionException` (`INTEGRATION_OUTBOX_DEAD_LETTER`, plus `AMBIGUOUS_TIMEOUT` guard per `BR-062`) |
| 7 | **Size Card (SC) Header** | `order` | `SalesContract` / `Subcontract` (`subcontracts` / `sc_headers`, C-3 OPEN) | [2.6 / 01](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.6-sc-and-completion-state-machine/01-sc-lifecycle-overview-and-states.md) & [2.6 / 04-completion-calculation-and-transition-matrices.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.6-sc-and-completion-state-machine/04-completion-calculation-and-transition-matrices.md) | `InvalidStateTransitionException` (`ORDER_ALREADY_RELEASED` / new Part 3.4 code `ORDER_INVALID_STATE_TRANSITION`) *(Completion cascade OPEN: C-1)* |
| 8 | **SC Product Line Item** | `order` | `ScProduct` / `SubcontractProduct` (`subcontract_products` / `sc_products`, C-3 OPEN) | [2.6 / 01](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.6-sc-and-completion-state-machine/01-sc-lifecycle-overview-and-states.md) & [2.6 / 04-completion-calculation-and-transition-matrices.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.6-sc-and-completion-state-machine/04-completion-calculation-and-transition-matrices.md) | `InvalidStateTransitionException` (`ORDER_INVALID_STATE_TRANSITION`, pending Part 3.4 addition) *(OPEN: C-1, C-2)* |
| 9 | **Assembled Set** | `dispatch` / `completion` *(OPEN C-1)* | `AssembledSet` (`assembled_sets`) | [2.6 / 03](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.6-sc-and-completion-state-machine/03-packing-and-dispatch-state-machines.md) & [2.6 / 04-completion-calculation-and-transition-matrices.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.6-sc-and-completion-state-machine/04-completion-calculation-and-transition-matrices.md) | `InvalidStateTransitionException` (`409` — conditional on C-1) |
| 10 | **Packing Record** | `dispatch` / `completion` *(OPEN C-1)* | `PackingRecord` (`packing_records`) | [2.6 / 03](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.6-sc-and-completion-state-machine/03-packing-and-dispatch-state-machines.md) & [2.6 / 04-completion-calculation-and-transition-matrices.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.6-sc-and-completion-state-machine/04-completion-calculation-and-transition-matrices.md) | `InvalidStateTransitionException` (`409` — conditional on C-1) |

*(Note: `dispatch_confirmations` in Part 2.6 Doc 04 §3.4 models an append-only confirmation event `TR-DSP-01`, also conditional on C-1.)*

---

### 5. Completion Logic for Sets, Products, and SC — Explicitly OPEN (`C-1`, `C-2`)

* **Conflict C-1 (OPEN — Completion Workflow Model):**
  * Decision `D-10` (Confirmed in `00_DECISION_LOG.md`): a piece becomes `Packed` automatically when all operations pass, a product completes when all its pieces are `Packed`, and the SC becomes `Delivered` automatically when all products complete—with no packing or dispatch screens, statuses, roles, or dispatch confirmation.
  * Phase 2 Parts 2.2 & 2.6: define `assembled_sets` (`BUILDING -> PRODUCTION_COMPLETE -> FINAL_QC_PASSED -> PACKING_CONFIRMED -> DISPATCH_PENDING -> DELIVERED`), `packing_records`, and `dispatch_confirmations`.
  * **Rule:** Backend domain services must NOT silently implement either cascade rule until the owner resolves **C-1**.
* **Conflict C-2 (OPEN — Piece Count Source):**
  * Decision `D-02` derives piece counts directly from the RM product line quantity (`rm_items.quantity`), whereas Part 2.2 derives piece demand from `target_set_qty x bom_lines.qty_per_set`. Piece generation and completion denominator logic remain **OPEN** pending owner resolution of **C-2**.

---

### 6. Technical Assumptions

1. **ASSUMPTION:** `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` is used strictly for post-commit side effects (SSE notifications, SMTP emails, outbox wakeups); intra-transaction state updates inside the same aggregate transaction call collaborators directly rather than relying on `AFTER_COMMIT`.
2. **ASSUMPTION:** Part 3.4 Doc 02 will be extended in Phase 8 with dedicated `MODULE_REASON` codes for Tolerance Revision, SC Header/Line transitions, and (if C-1 chooses Part 2.6) Assembled Set / Packing transitions.

---

### 7. Open Questions

* **C-1 (OPEN):** Confirm automatic completion cascade (`D-10`) vs. Phase 2.6 assembled sets, packing checklists, and dock dispatch confirmations.
* **C-2 (OPEN):** Confirm piece count source (`D-02` RM product quantity vs. Part 2.2 BOM ratio x target sets).
* **C-3 (OPEN):** Confirm canonical naming (`subcontracts` vs `sc_headers` vs `sales_contracts`).
* **O-MOD-01 / Module Reconciliation (OPEN):** Resolve module boundaries (`dispatch` vs `completion`, `exception`, `engineering` vs `planning`) per [Phase-4.7-Module-Reconciliation.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-4/Phase-4.7-Module-Reconciliation.md).
* **O-STM-01 (OPEN):** Confirm whether `<entity>_status_history` and `audit_logs` tables require PostgreSQL monthly partitioning.
