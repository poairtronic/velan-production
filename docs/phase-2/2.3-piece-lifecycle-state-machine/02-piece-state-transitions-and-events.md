# Part 2.3 – Piece Lifecycle State Machine
## Document 02: Piece State Transitions, Triggers, and Guard Conditions

---

### 1. Complete Transition Matrix

The table below defines every permissible state transition for a physical piece, the triggering domain event, authorized actors, pre-flight guard conditions, and resulting system side effects:

| # | Current State | Target State | Triggering Event | Authorized Actor | Guard Conditions (Pre-flight Checks) | Side Effects & State Mutations |
| :- | :--- | :--- | :--- | :--- | :--- | :--- |
| **T01** | `None` | `CREATED` | `SC_INTAKE_APPROVED` | System / PM | 1. Approved SC header and lines exist.<br/>2. BOM lines and process templates active.<br/>3. Target sets defined. | 1. Creates `pieces` records with deterministic `assembled_set_id`, `bom_line_id`, `set_sequence_number`.<br/>2. Spawns routing operations (`piece_operations`) from template snapshot.<br/>3. Stamps `created_at`.<br/>4. Logs audit event `PIECE_CREATED`. |
| **T02** | `CREATED` | `AWAITING_MATERIAL` | `MATERIAL_PENDING_GATE` | System | 1. Parent product line `material_ready == false`.<br/>2. RM has not issued stock. | 1. `pieces.status = 'AWAITING_MATERIAL'`.<br/>2. All child operations remain strictly `PENDING`.<br/>3. Blocks barcode job opening on operator UI.<br/>4. Logs audit event `PIECE_AWAITING_MATERIAL`. |
| **T03** | `AWAITING_MATERIAL` | `IN_PRODUCTION` | `MATERIAL_ISSUED_CONFIRMED` | RM Webhook / PM Toggle | 1. RM material issue payload verified OR PM manual override confirmed.<br/>2. `subcontract_products.material_ready == true`. | 1. `pieces.status = 'IN_PRODUCTION'`.<br/>2. First operation (sequence 10) transitions from `PENDING` to `READY` (`ready_at = NOW()`).<br/>3. Subsequent operations (seq 20, 30...) remain `PENDING`.<br/>4. Emits SSE event `PIECE_READY_FOR_MACHINING`.<br/>5. Logs audit event `MATERIAL_GATE_UNLOCKED`. |
| **T04** | `IN_PRODUCTION` | `IN_PRODUCTION` | `OPERATION_QC_PASSED` | QC Inspector | 1. Current operation (seq N) receives QC `verdict = 'PASSED'`.<br/>2. More routing operations exist (seq N+1). | 1. Current operation marked `PASSED` (`completed_at = NOW()`).<br/>2. Next sequential operation (seq N+1) transitions to `READY` (`ready_at = NOW()`).<br/>3. Piece remains `IN_PRODUCTION`.<br/>4. Logs audit event `OPERATION_PASSED`. |
| **T05** | `IN_PRODUCTION` | `ON_HOLD_QC_FAILED` | `OPERATION_QC_FAILED` | QC Inspector | 1. Current operation (seq N) receives QC `verdict = 'FAILED'`.<br/>2. QC measurements recorded independently. | 1. Current operation marked `FAILED`.<br/>2. `pieces.status = 'ON_HOLD_QC_FAILED'`.<br/>3. Automatically creates `qc_exceptions` record (`status = 'PENDING_PM_DISPOSITION'`).<br/>4. Blocks all downstream operations.<br/>5. Emits urgent SSE alert to PM dashboard.<br/>6. Logs audit event `PIECE_ON_HOLD_QC_FAILED`. |
| **T06** | `ON_HOLD_QC_FAILED` | `IN_PRODUCTION` | `PM_DISPOSITION_CONTINUE` | Production Manager (PM) | 1. PM provides non-empty `justification_notes`.<br/>2. Exception is active (`PENDING_PM_DISPOSITION`). | 1. Records `pm_dispositions` with `decision = 'CONTINUE'`.<br/>2. Original measurements and failed QC records remain **strictly immutable**.<br/>3. Failed operation transitions to `CONCESSION_ACCEPTED` (`completed_at = NOW()`).<br/>4. Next sequential operation (seq N+1) transitions to `READY` (`ready_at = NOW()`).<br/>5. `pieces.status = 'IN_PRODUCTION'`.<br/>6. `qc_exceptions.status = 'RESOLVED_CONCESSION'`.<br/>7. Logs audit event `PM_DISPOSITION_CONTINUE`. |
| **T07** | `ON_HOLD_QC_FAILED` | `IN_PRODUCTION_REWORK` | `PM_DISPOSITION_RETURN_TO_OP`| Production Manager (PM) | 1. PM selects valid `target_operation_id` (must be $\le$ failed sequence).<br/>2. PM enters rework instructions. | 1. Records `pm_dispositions` with `decision = 'RETURN_TO_EARLIER_OPERATION'`.<br/>2. Target operation reset to `READY` for rework.<br/>3. Rework measurement session initialized with incremented `iteration_number` ($N+1$).<br/>4. Prior failure measurements preserved permanently.<br/>5. `pieces.status = 'IN_PRODUCTION_REWORK'`.<br/>6. `qc_exceptions.status = 'RESOLVED_REWORK'`.<br/>7. Logs audit event `PM_DISPOSITION_RETURN_TO_OP`. |
| **T08** | `ON_HOLD_QC_FAILED` | `IN_PRODUCTION_EXTRA_PROCESS`| `PM_DISPOSITION_EXTRA_PROCESS` | Production Manager (PM) | 1. PM specifies extra operation details and station.<br/>2. PM enters remedial instructions. | 1. Records `pm_dispositions` with `decision = 'EXTRA_PROCESS'`.<br/>2. Dynamically inserts ad-hoc remedial operation (`is_extra_operation = true`) with status `READY`.<br/>3. Prior failure measurements preserved permanently.<br/>4. `pieces.status = 'IN_PRODUCTION_EXTRA_PROCESS'`.<br/>5. `qc_exceptions.status = 'RESOLVED_REWORK'`.<br/>6. Logs audit event `PM_DISPOSITION_EXTRA_PROCESS`. |
| **T09** | `IN_PRODUCTION_REWORK` | `IN_PRODUCTION` | `REWORK_QC_PASSED` | QC Inspector | 1. Rework operation passes QC inspection under new iteration.<br/>2. Reworked piece has caught up to failure point. | 1. Rework operation marked `PASSED`.<br/>2. Normal sequential operation following the original failed step transitions to `READY`.<br/>3. `pieces.status = 'IN_PRODUCTION'`.<br/>4. Emits SSE event to shop-floor operator.<br/>5. Logs audit event `REWORK_COMPLETED_SUCCESS`. |
| **T10** | `IN_PRODUCTION_REWORK` | `ON_HOLD_QC_FAILED` | `REWORK_QC_FAILED` | QC Inspector | 1. Rework operation measurements fail QC inspection again. | 1. Rework operation marked `FAILED`.<br/>2. `pieces.status = 'ON_HOLD_QC_FAILED'`.<br/>3. New `qc_exceptions` ticket generated with rework history.<br/>4. Re-escalates urgently to PM dashboard.<br/>5. Logs audit event `REWORK_QC_FAILED`. |
| **T11** | `IN_PRODUCTION_EXTRA_PROCESS`| `IN_PRODUCTION` | `EXTRA_PROCESS_QC_PASSED` | QC Inspector | 1. Extra process operation passes QC inspection. | 1. Extra operation marked `PASSED`.<br/>2. Normal sequential operation following the original failure step transitions to `READY`.<br/>3. `pieces.status = 'IN_PRODUCTION'`.<br/>4. Logs audit event `EXTRA_PROCESS_PASSED`. |
| **T12** | `IN_PRODUCTION_EXTRA_PROCESS`| `ON_HOLD_QC_FAILED` | `EXTRA_PROCESS_QC_FAILED` | QC Inspector | 1. Extra process measurements fail QC inspection. | 1. Extra operation marked `FAILED`.<br/>2. `pieces.status = 'ON_HOLD_QC_FAILED'`.<br/>3. Re-escalates to PM.<br/>4. Logs audit event `EXTRA_PROCESS_FAILED`. |
| **T13** | `ON_HOLD_QC_FAILED` | `SCRAPPED` | `PM_DISPOSITION_REJECT_RESTART`| Production Manager (PM) | 1. PM selects scrap outcome.<br/>2. PM enters scrap engineering reason. | 1. `pieces.status = 'SCRAPPED'` (terminal immutable).<br/>2. `qc_exceptions.status = 'RESOLVED_SCRAP'`.<br/>3. Enqueues `SCRAP_EXCEPTION` event in `outbox_events` for ECR.<br/>4. Automatically spawns child replacement piece with `replacement_sequence = parent.replacement_sequence + 1`.<br/>5. Replacement piece inherits `assembled_set_id` and enters `AWAITING_MATERIAL`.<br/>6. Records parent-child genealogy in `piece_replacement_lineage`.<br/>7. Logs audit event `PIECE_SCRAPPED`. |
| **T14** | `IN_PRODUCTION` | `COMPLETED` | `FINAL_OPERATION_PASSED` | QC Inspector / PM | 1. Final routing operation (seq Last) passes QC or is accepted on concession (`CONCESSION_ACCEPTED`).<br/>2. 100% of routing operations for this piece are resolved. | 1. `pieces.status = 'COMPLETED'`.<br/>2. Stamped `completed_at = NOW()`.<br/>3. Piece transferred to assembly staging.<br/>4. Evaluates parent set completeness asynchronously.<br/>5. If all sibling pieces in set are `COMPLETED`, transitions parent set to `PRODUCTION_COMPLETE` (`is_production_complete = true`).<br/>6. Logs audit event `PIECE_PRODUCTION_COMPLETED`. |

---

### 2. Prohibited and Invalid State Transitions

To guarantee data integrity and physical manufacturing consistency, the following state transitions are **strictly rejected** by the backend domain validation layer:

```
+-----------------------------------------------------------------------------------------+
| PROHIBITED TRANSITION                     | REASON & INTEGRITY GUARDRAIL                |
+-----------------------------------------------------------------------------------------+
| AWAITING_MATERIAL ──► IN_PRODUCTION       | BLOCKED: Cannot bypass raw material gate.   |
| (when material_ready == false)            | Operators cannot machine nonexistent metal. |
+-----------------------------------------------------------------------------------------+
| AWAITING_MATERIAL ──► COMPLETED           | BLOCKED: A piece cannot complete operations |
|                                           | without raw material and machining steps.   |
+-----------------------------------------------------------------------------------------+
| ON_HOLD_QC_FAILED ──► IN_PRODUCTION       | BLOCKED: Operators and QC Inspectors cannot |
| (without PM Concession disposition)       | bypass an out-of-spec failure hold.         |
+-----------------------------------------------------------------------------------------+
| ON_HOLD_QC_FAILED ──► COMPLETED           | BLOCKED: A failed piece cannot complete     |
|                                           | without explicit PM disposition.            |
+-----------------------------------------------------------------------------------------+
| COMPLETED ──► IN_PRODUCTION               | BLOCKED: Completed piece records are frozen |
|                                           | and cannot be arbitrarily reopened.         |
+-----------------------------------------------------------------------------------------+
| SCRAPPED ──► IN_PRODUCTION / COMPLETED    | BLOCKED: Scrapped status is absolute and    |
|                                           | terminal. A retired piece cannot be revived.|
+-----------------------------------------------------------------------------------------+
| ANY ACTIVE STATE ──► PACKED / DELIVERED   | BLOCKED: Packing and Delivery belong        |
|                                           | strictly to the assembled_sets entity.      |
+-----------------------------------------------------------------------------------------+
```

---

### 3. Detailed Side-Effect Specifications

#### 3.1 Material Gate Transition (`T03: AWAITING_MATERIAL` $\rightarrow$ `IN_PRODUCTION`)
* **Trigger:** Received webhook `RM_MATERIAL_ISSUED` for `sc_product_id`, or authorized PM manually clicks "Confirm Material Ready" on the Production line UI.
* **Database Action (Atomic Transaction):**
  1. Updates `subcontract_products`:
     ```sql
     UPDATE subcontract_products 
     SET material_ready = true, 
         material_status = 'ISSUED', 
         material_issued_at = NOW() 
     WHERE id = :sc_product_id;
     ```
  2. Updates all pre-allocated pieces for that product line:
     ```sql
     UPDATE pieces 
     SET status = 'IN_PRODUCTION' 
     WHERE sc_product_id = :sc_product_id 
       AND status = 'AWAITING_MATERIAL';
     ```
  3. Activates the first routing operation for each piece:
     ```sql
     UPDATE piece_operations 
     SET status = 'READY', 
         ready_at = NOW() 
     WHERE piece_id IN (:piece_ids) 
       AND sequence_order = 10;
     ```
  4. Note: Operations with sequence 20, 30, etc., remain in `PENDING`. They are never made `READY` simultaneously.

#### 3.2 QC Failure Quarantine (`T05: IN_PRODUCTION` $\rightarrow$ `ON_HOLD_QC_FAILED`)
* **Trigger:** QC Inspector records out-of-tolerance measurements and submits inspection with verdict `FAILED`.
* **Database Action (Atomic Transaction):**
  1. Sets operation status to `FAILED`:
     ```sql
     UPDATE piece_operations 
     SET status = 'FAILED' 
     WHERE id = :operation_id;
     ```
  2. Quarantines the physical piece:
     ```sql
     UPDATE pieces 
     SET status = 'ON_HOLD_QC_FAILED' 
     WHERE id = :piece_id;
     ```
  3. Automatically opens an exception case:
     ```sql
     INSERT INTO qc_exceptions (id, piece_id, piece_operation_id, qc_inspection_id, status, opened_at)
     VALUES (gen_random_uuid(), :piece_id, :operation_id, :inspection_id, 'PENDING_PM_DISPOSITION', NOW());
     ```
  4. Appends audit event `PIECE_ON_HOLD_QC_FAILED`.
  5. Pushes SSE notification to PM: *"Piece {serial_number} failed QC at {operation_name}. PM disposition required."*

#### 3.3 Concession Progression (`T06: ON_HOLD_QC_FAILED` $\rightarrow$ `IN_PRODUCTION`)
* **Trigger:** PM approves `CONTINUE` disposition with mandatory justification.
* **Database Action (Atomic Transaction):**
  1. Records PM disposition:
     ```sql
     INSERT INTO pm_dispositions (id, exception_id, pm_user_id, decision, justification_notes, decided_at)
     VALUES (gen_random_uuid(), :exception_id, :pm_user_id, 'CONTINUE', :justification_notes, NOW());
     ```
  2. Marks failed operation as accepted on concession:
     ```sql
     UPDATE piece_operations 
     SET status = 'CONCESSION_ACCEPTED', 
         completed_at = NOW() 
     WHERE id = :failed_operation_id;
     ```
  3. Unlocks next sequential operation:
     ```sql
     UPDATE piece_operations 
     SET status = 'READY', 
         ready_at = NOW() 
     WHERE piece_id = :piece_id 
       AND sequence_order = :next_sequence;
     ```
  4. Returns piece to production:
     ```sql
     UPDATE pieces 
     SET status = 'IN_PRODUCTION' 
     WHERE id = :piece_id;
     ```
  5. Closes exception ticket:
     ```sql
     UPDATE qc_exceptions 
     SET status = 'RESOLVED_CONCESSION' 
     WHERE id = :exception_id;
     ```
  6. Preserves prior failure records and measurements untouched.

#### 3.4 Scrap & Replacement Lineage (`T13: ON_HOLD_QC_FAILED` $\rightarrow$ `SCRAPPED`)
* **Trigger:** PM approves `REJECT_AND_RESTART` disposition.
* **Database Action (Atomic Transaction):**
  1. Marks original piece as permanently scrapped:
     ```sql
     UPDATE pieces 
     SET status = 'SCRAPPED' 
     WHERE id = :parent_piece_id;
     ```
  2. Spawns new physical replacement piece:
     ```sql
     INSERT INTO pieces (
       id, sc_id, sc_product_id, assembled_set_id, set_sequence_number, 
       bom_line_id, component_code, component_sequence_in_set, 
       replacement_sequence, serial_number, snapshot_id, replaced_piece_id, status
     ) VALUES (
       gen_random_uuid(), :sc_id, :sc_product_id, :assembled_set_id, :set_sequence_number,
       :bom_line_id, :component_code, :component_sequence_in_set,
       :parent_replacement_seq + 1, :new_serial_number, :snapshot_id, :parent_piece_id, 'AWAITING_MATERIAL'
     );
     ```
  3. Replaces parent piece allocation in set manifest:
     ```sql
     UPDATE set_component_allocations 
     SET piece_id = :new_piece_id, 
         allocated_at = NOW() 
     WHERE piece_id = :parent_piece_id;
     ```
  4. Records immutable lineage genealogy:
     ```sql
     INSERT INTO piece_replacement_lineage (
       id, scrapped_parent_piece_id, replacement_child_piece_id, disposition_id, created_at
     ) VALUES (
       gen_random_uuid(), :parent_piece_id, :new_piece_id, :disposition_id, NOW()
     );
     ```
  5. Enqueues Transactional Outbox event for ECR:
     ```sql
     INSERT INTO outbox_events (
       id, aggregate_type, aggregate_id, payload, status, retry_count, next_retry_at
     ) VALUES (
       gen_random_uuid(), 'SCRAP_EXCEPTION', :exception_id, :ecr_payload, 'PENDING', 0, NOW()
     );
     ```

---

### 4. Concurrency Control and Optimistic Locking

On a busy factory shop floor, multiple barcode scanners, machine operators, and QC inspectors may interact with the same piece or parent set simultaneously. To prevent race conditions, dirty writes, and illegal parallel progressions:

1. **Entity Optimistic Locking:**
   * The `pieces` entity includes a `@Version Integer version` column.
   * Any attempt to update piece status (e.g., from `IN_PRODUCTION` to `ON_HOLD_QC_FAILED`) while another operator transaction is modifying child operations will trigger an `OptimisticLockException` and fail cleanly.
2. **Pessimistic State Checking during Measurement Submission:**
   * Before persisting measurements, the backend checks:
     ```sql
     SELECT status FROM pieces WHERE id = :piece_id FOR UPDATE;
     ```
   * If `status != 'IN_PRODUCTION'` (or `IN_PRODUCTION_REWORK` / `IN_PRODUCTION_EXTRA_PROCESS`), the measurement entry is rejected immediately with HTTP 409 Conflict.
3. **Idempotent Transitions:**
   * If duplicate network events arrive (e.g., duplicate RM material issue webhooks), the transition logic detects `material_ready == true` and returns the existing state without repeating piece mutations (`ADR-02`).
