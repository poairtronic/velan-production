# Part 2.4 – Operation Lifecycle State Machine
## Document 02: Operation State Transitions, Events, and Guard Conditions

---

### 1. Comprehensive Operation State Transition Matrix

The table below specifies every permissible state transition for an individual operation instance (`piece_operations`), documenting all twelve required technical parameters per transition:

| Transition ID | Current State | Next State | Triggering Event | Authorized Role | Preconditions & Guard Conditions | Required Data Payload | Timestamp Updates | Audit Event Name | Notification Requirements | Reversible? | Failure / Rejection Behavior |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :---: | :--- |
| **OP-T01** | `None` | `PENDING` | `SC_APPROVAL_INTAKE` | System / PM | 1. SC approved.<br/>2. Process template snapshot frozen.<br/>3. Physical pieces instantiated. | `piece_id`, `sequence_order`, `operation_name`, `processing_route`, `snapshot_step_id` | `created_at = NOW()` | `OPERATION_CREATED` | None | No | SC approval rolled back; zero operations created. |
| **OP-T02** | `PENDING` | `READY` | `RM_MATERIAL_ISSUED` | System / PM Toggle | 1. Operation is sequence 10 (first step).<br/>2. `subcontract_products.material_ready == true`.<br/>3. Piece status is `AWAITING_MATERIAL`. | `sc_product_id`, `rm_issue_reference` | `ready_at = NOW()` | `OPERATION_READY_MATERIAL` | SSE push to Operator Station: *"Material Ready: Op 10 unlocked."* | No | Remains `PENDING`. Operator cannot open job. |
| **OP-T03** | `PENDING` | `READY` | `PREDECESSOR_COMPLETED` | System | 1. Operation is sequence > 10.<br/>2. Preceding operation status is `PASSED`, `CONCESSION_ACCEPTED`, or `BYPASSED`.<br/>3. Preceding operation QC is fully resolved. | `preceding_operation_id` | `ready_at = NOW()` | `OPERATION_READY_SEQUENTIAL` | SSE push to assigned Operator/Station worklist. | No | Remains `PENDING`. Operator barcode scan blocked. |
| **OP-T04** | `READY` | `OPENED` | `OPERATOR_OPEN_JOB` | Machine Operator (`ROLE_OPERATOR`) | 1. Current status is `READY`.<br/>2. Operator is assigned or station is authorized.<br/>3. Parent piece is `IN_PRODUCTION` (not on hold). | `operator_user_id`, `station_id` | `opened_at = NOW()` | `OPERATION_OPENED` | UI indicates active execution on shop-floor terminal. | Yes (via OP-T15) | HTTP 409 Conflict if already opened by another operator. |
| **OP-T05** | `OPENED` | `QC_PENDING` | `OPERATOR_SUBMIT_MEASUREMENTS` | Machine Operator (`ROLE_OPERATOR`) | 1. Current status is `OPENED`.<br/>2. All mandatory tolerance dimensions entered.<br/>3. Out-of-tolerance values acknowledged. | Array of measured values, `instrument_id`, `tolerance_revision_id` | `submitted_at = NOW()` | `OPERATOR_MEASUREMENTS_SUBMITTED` | SSE push to QC Inspector queue: *"Piece {serial} ready for QC inspection."* | No | HTTP 422 if mandatory values missing; measurements rejected. |
| **OP-T06** | `QC_PENDING` | `QC_IN_PROGRESS` | `QC_CLAIM_INSPECTION` | QC Inspector (`ROLE_QC`) | 1. Current status is `QC_PENDING`.<br/>2. User has `ROLE_QC`.<br/>3. No other QC inspector has claimed this piece. | `qc_user_id` | `qc_started_at = NOW()` | `QC_INSPECTION_CLAIMED` | UI locks inspection interface to claiming inspector. | Yes (Release lock) | HTTP 409 Conflict if already claimed by another QC inspector. |
| **OP-T07** | `QC_IN_PROGRESS` | `PASSED` | `QC_VERDICT_PASS` | QC Inspector (`ROLE_QC`) | 1. All mandatory QC dimensions independently entered.<br/>2. 100% of readings within approved upper/lower tolerance limits.<br/>3. Instrument calibrated. | Array of QC measured values, `qc_instrument_id`, `verdict = 'PASSED'` | `completed_at = NOW()` | `OPERATION_QC_PASSED` | SSE push to downstream Operator and PM dashboard. | No (Except formal PM reopen) | Reverted to `QC_IN_PROGRESS` if required fields incomplete. |
| **OP-T08** | `QC_IN_PROGRESS` | `FAILED` | `QC_VERDICT_FAIL` | QC Inspector (`ROLE_QC`) | 1. At least one dimensional reading outside approved tolerance band.<br/>2. Independent QC readings entered. | Array of QC measured values, out-of-spec parameter keys, `verdict = 'FAILED'` | `completed_at = NOW()` | `OPERATION_QC_FAILED` | Urgent SSE alert to PM Priority Worklist. Piece quarantined. | No | Reverted to `QC_IN_PROGRESS` if inspection submission invalid. |
| **OP-T09** | `FAILED` | `CONCESSION_ACCEPTED` | `PM_DISPOSITION_CONTINUE` | Production Manager (`ROLE_PM`) | 1. Operation is `FAILED`.<br/>2. PM enters non-empty `justification_notes`.<br/>3. Exception ticket active. | `pm_user_id`, `justification_notes`, `decision = 'CONTINUE'` | `completed_at = NOW()` (Updated) | `PM_DISPOSITION_CONTINUE` | SSE notification to Operator; unlocks sequence N+1 to `READY`. | No | HTTP 400 Bad Request if justification notes blank or whitespace. |
| **OP-T10** | `FAILED` | `READY` (Iteration + 1)| `PM_DISPOSITION_RETURN_TO_OP`| Production Manager (`ROLE_PM`) | 1. Operation is `FAILED` (or downstream).<br/>2. Target operation sequence $\le$ failed sequence.<br/>3. PM enters rework instructions. | `target_operation_id`, `pm_user_id`, `justification_notes`, `decision = 'RETURN_TO_EARLIER_OPERATION'` | `ready_at = NOW()`, `iteration_number += 1` | `PM_DISPOSITION_RETURN_TO_OP` | SSE alert to target station operator with rework instructions. | No | Rejection if target operation sequence is invalid. |
| **OP-T11** | `None` | `READY` (Extra Step) | `PM_DISPOSITION_EXTRA_PROCESS` | Production Manager (`ROLE_PM`) | 1. Current operation is `FAILED`.<br/>2. PM specifies valid intermediate sequence (e.g. 25).<br/>3. PM specifies station & instructions. | `piece_id`, `sequence_order`, `operation_name`, `station_id`, `is_extra_operation = true` | `created_at = NOW()`, `ready_at = NOW()` | `PM_DISPOSITION_EXTRA_PROCESS` | SSE push to designated remedial station operator. | No | HTTP 409 if sequence order already exists in route. |
| **OP-T12** | `PENDING` / `READY` | `BYPASSED` | `PM_BYPASS_OPERATION` | Production Manager (`ROLE_PM`) | 1. Operation not yet opened (`status IN ('PENDING', 'READY')`).<br/>2. PM enters mandatory bypass reason.<br/>3. Step not marked non-bypassable. | `pm_user_id`, `bypass_reason` | `completed_at = NOW()` | `OPERATION_BYPASSED` | Logs audit event; unlocks sequence N+1 to `READY`. | No | HTTP 400 if reason empty; HTTP 409 if operator has already opened job. |
| **OP-T13** | `PASSED` / `CONCESSION_ACCEPTED` | `READY` (Reopened) | `PM_REOPEN_OPERATION` | Production Manager (`ROLE_PM`) | 1. Downstream operations have not progressed past reversible point.<br/>2. PM enters mandatory engineering reason. | `pm_user_id`, `reopen_reason` | `ready_at = NOW()`, `iteration_number += 1` | `OPERATION_REOPENED` | SSE alert to shop floor and QC. Downstream ops locked back to `PENDING`. | No | HTTP 409 if downstream operation is already completed or piece is packed. |
| **OP-T14** | `PENDING` / `READY` / `OPENED` / `QC_PENDING` | `CANCELLED` | `PARENT_PIECE_SCRAPPED` | System / PM | 1. Parent piece scrapped via `REJECT_AND_RESTART`. (Contract cancellation held as future consideration). | `parent_piece_id`, `scrap_disposition_id` | `completed_at = NOW()` | `OPERATION_CANCELLED` | Operation permanently retired from active queues. | No | None. Immutable termination. |
| **OP-T15** | `OPENED` | `READY` | `OPERATOR_RELEASE_JOB` | Machine Operator (`ROLE_OPERATOR`) | 1. Current status is `OPENED`.<br/>2. No measurements have been submitted.<br/>3. Operator needs to release job (shift end/station reassignment). | `operator_user_id`, `release_reason` | `opened_at = NULL` | `OPERATION_RELEASED` | Job returns to general worklist for other operators. | Yes | Blocked if partial measurements have been permanently committed. |

---

### 2. Deep-Dive Transition Logic & Guard Preconditions

#### 2.1 Transition OP-T02: Material Gate Activation (First Operation)
* **Precondition Check:**
  ```java
  if (operation.getSequenceOrder() == 10) {
      SubcontractProduct scProduct = subcontractProductRepository.findById(piece.getScProductId());
      if (!scProduct.isMaterialReady()) {
          throw new MaterialGateException("Cannot activate Operation 10: Parent product raw material is not marked ready.");
      }
      operation.setStatus(OperationStatus.READY);
      operation.setReadyAt(Instant.now());
  }
  ```
* **Strict Invariant:** Operation 20, 30, etc., are never evaluated by OP-T02. They remain strictly in `PENDING`.

#### 2.2 Transition OP-T03: Sequential Activation (Downstream Operations)
* **Precondition Check:**
  ```java
  PieceOperation predecessor = pieceOperationRepository.findPredecessor(piece.getId(), operation.getSequenceOrder());
  if (predecessor.getStatus() != OperationStatus.PASSED 
      && predecessor.getStatus() != OperationStatus.CONCESSION_ACCEPTED 
      && predecessor.getStatus() != OperationStatus.BYPASSED) {
      throw new OperationSequencingException("Cannot activate Operation " + operation.getSequenceOrder() 
          + ": Preceding operation " + predecessor.getSequenceOrder() + " is not complete.");
  }
  operation.setStatus(OperationStatus.READY);
  operation.setReadyAt(Instant.now());
  ```

#### 2.3 Transition OP-T05: Operator Measurement Submission
* **Validation & Guard Checks:**
  1. Verifies that the operation status is strictly `OPENED`.
  2. Validates that every mandatory dimensional rule for this operation in `tolerance_rules` has a corresponding numerical value in the submission payload. Missing dimensions reject the submission with HTTP 422 Unprocessable Entity.
  3. Evaluates values against nominal, upper, and lower limits:
     - In-tolerance: normal submission.
     - Out-of-tolerance: generates non-blocking operator warning prompt: *"Parameter X is outside nominal tolerance. Submit anyway for QC review?"*
  4. On confirmation, writes to `operation_measurements` and `measurement_values` tagged with `recorded_by_role = 'OPERATOR'` and `iteration_number = operation.getIterationNumber()`.
  5. Atomically sets operation status to `QC_PENDING` and records `submitted_at = NOW()`.

#### 2.4 Transition OP-T07 vs OP-T08: QC Verdict Evaluation
* **Independent Evaluation Gate:**
  - QC Inspector inputs readings into `operation_measurements` with `recorded_by_role = 'QC_INSPECTOR'`.
  - System automatically calculates compliance for every parameter:
    $$\text{Lower Limit} \le \text{QC Measured Value} \le \text{Upper Limit}$$
  - **If 100% compliant:**
    - `qc_inspections.verdict = 'PASSED'`
    - `piece_operations.status = 'PASSED'`
    - `piece_operations.completed_at = NOW()`
    - Triggers OP-T03 to unlock the next sequential operation to `READY`.
  - **If any parameter non-compliant:**
    - `qc_inspections.verdict = 'FAILED'`
    - `piece_operations.status = 'FAILED'`
    - `piece_operations.completed_at = NOW()`
    - Immediately triggers piece quarantine transition to `ON_HOLD_QC_FAILED` (Part 2.3, T05).
    - Opens `qc_exceptions` ticket with status `PENDING_PM_DISPOSITION`.

---

### 3. Prohibited Operation Transitions

The following state transitions are illegal and prevented at the API controller, service layer, and database check constraint levels:

```
+-----------------------------------------------------------------------------------------+
| ILLEGAL TRANSITION                        | INTEGRITY GUARDRAIL                         |
+-----------------------------------------------------------------------------------------+
| PENDING ──► OPENED / QC_PENDING           | Prohibited: Cannot execute an unready       |
|                                           | operation; violates sequential gating.      |
+-----------------------------------------------------------------------------------------+
| READY ──► PASSED / FAILED                 | Prohibited: Cannot certify quality without  |
|                                           | operator data and independent QC inspection.|
+-----------------------------------------------------------------------------------------+
| QC_PENDING ──► PASSED                     | Prohibited: Cannot bypass independent QC    |
|                                           | measurement session (must claim first).     |
+-----------------------------------------------------------------------------------------+
| FAILED ──► PASSED                         | Prohibited: A QC failure can NEVER be       |
|                                           | silently converted into a pass.             |
|                                           | PM must use CONCESSION_ACCEPTED.            |
+-----------------------------------------------------------------------------------------+
| PASSED ──► OPENED (without PM Reopen)     | Prohibited: Completed certified operations  |
|                                           | are locked against operator re-entry.       |
+-----------------------------------------------------------------------------------------+
| CANCELLED ──► READY / OPENED              | Prohibited: Cancelled operations are        |
|                                           | permanently dead.                           |
+-----------------------------------------------------------------------------------------+
```
