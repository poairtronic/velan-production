# Part 2.4 – Operation Lifecycle State Machine
## Document 05: PM Controls, Timing Metrics, Concurrency, and Traceability

---

### 1. Material Readiness Gate and Sequential Operation Activation

#### 1.1 Strict Separation: Material Gate vs. Operation Readiness
A core structural requirement of Velan Production is the absolute separation between raw material availability and shop-floor operation eligibility:
* **Material Readiness:** Managed exclusively at the **Subcontract Product Line Item level** (`subcontract_products.material_ready`). It indicates that RM Stores has physically issued the raw forgings, castings, or bar stock required to fabricate that product.
* **Operation Readiness:** Managed exclusively at the **Individual Operation level** (`piece_operations.status = 'READY'`). It indicates that a specific machine station and operator are authorized to begin physical cutting, turning, milling, or grinding on a specific serialized piece.

#### 1.2 Initial Activation Rules (Sequence 10)
1. When an SC is approved, all operations for all pieces are instantiated with `status = 'PENDING'`.
2. When RM emits a material issue event or the PM manually confirms readiness (`material_ready = true` on `subcontract_products`), the system queries all pieces belonging to that line item.
3. **Sequence 10 Exclusivity:** The system transitions **only Sequence 10 (the first operation)** to `READY` (`ready_at = NOW()`).
4. **Prohibition of Simultaneous Readiness:** Operations 20, 30, 40, etc., remain strictly in `PENDING`. They are **never made `READY` simultaneously** simply because raw material has arrived.

#### 1.3 Downstream Sequential Gating (Sequence 20+)
1. Downstream operations remain locked in `PENDING`. No operator can scan, open, or enter measurements for an operation in `PENDING` status.
2. Sequence 20 becomes `READY` **if and only if** Sequence 10 reaches a valid resolved terminal state:
   - `PASSED` (certified by QC Inspector), OR
   - `CONCESSION_ACCEPTED` (authorized by PM with mandatory engineering justification), OR
   - `BYPASSED` (formally skipped by PM with documented engineering reason).
3. If Sequence 10 fails QC (`FAILED`), Sequence 20 remains locked in `PENDING` indefinitely until the exception is resolved.

#### 1.4 Material Readiness Revocation Mid-Production (Open Policy Question)
* *Scenario:* What occurs if RM Stores revokes material readiness (`material_ready` toggled from `true` to `false`) due to a raw material recall, defective mill batch alert, or heat code discrepancy after shop-floor machining has already commenced?
* *Architectural Recommendation:* To prevent creating non-standard rollback states, the system should:
  1. Block any unstarted operations (status `READY`) from being opened.
  2. Flag active operations (status `OPENED` or `QC_PENDING`) with an amber material alert banner on the operator and PM screens.
  3. Prevent any piece from transitioning to `COMPLETED` until RM confirms material resolution.
* *Status:* **Recorded as Open Question 1** in Section 6 awaiting final business confirmation.

---

### 2. Production Manager (PM) Operation-Level Controls

The Production Manager (`ROLE_PM`) possesses supervisory authority to govern, adjust, and remediate in-flight routing operations. Every PM control is audited with mandatory engineering justification:

```
+-----------------------------------------------------------------------------------------+
| PM OPERATION CONTROLS ARCHITECTURE                                                      |
+-----------------------------------------------------------------------------------------+
| 1. ADD EXTRA OPERATION     | Inserts remedial step (is_extra_operation = true)           |
| 2. BYPASS OPERATION        | Skips step with mandatory reason (is_bypassed = true)      |
| 3. RESEQUENCE OPERATIONS   | Reorders unstarted PENDING operations                       |
| 4. REOPEN OPERATION        | Reopens completed step under incremented iteration          |
| 5. REASSIGN STATION/USER   | Reallocates job to alternate machine/operator               |
| 6. UPDATE INSTRUCTIONS     | Modifies shop-floor notes without altering snapshot         |
+-----------------------------------------------------------------------------------------+
```

#### Detailed PM Control Specifications

| PM Control Action | Authorized Role | Preconditions & Guard Checks | State Transition Impact | Mandatory Data Required | Audit Event Logged | Effect on Downstream Operations |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Add Extra Operation** | `ROLE_PM` | Piece is in `ON_HOLD_QC_FAILED` (or active production). Sequence order must be unique (e.g. 25). | Injects new row in `piece_operations` with `is_extra_operation = true`, status `READY`. | Operation name, station, instructions, PM justification. | `PM_ADD_EXTRA_OPERATION` | Downstream operations remain `PENDING` until extra operation passes QC. |
| **Bypass Operation** | `ROLE_PM` | Operation status must be `PENDING` or `READY`. Cannot bypass an operation currently `OPENED`, `SUBMITTED`, or `PASSED`. | Target operation transitions to `BYPASSED` (`completed_at = NOW()`, `is_bypassed = true`). | Non-empty PM bypass reason (e.g., *"Raw bar stock pre-faced by vendor"*). | `PM_BYPASS_OPERATION` | Unlocks immediately succeeding operation from `PENDING` to `READY`. |
| **Resequence Operations** | `ROLE_PM` | Operations to be resequenced must be strictly in `PENDING` status. Cannot resequence active, opened, or completed steps. | Updates `sequence_order` integers. Validates that sequence order remains strictly ascending without duplicates. | New sequence mapping array, PM justification. | `PM_RESEQUENCE_OPERATIONS` | Re-evaluates successor pointers; maintains strict sequential progression. |
| **Reopen Operation** | `ROLE_PM` | Operation is `PASSED` or `CONCESSION_ACCEPTED`. Sibling/downstream ops have not progressed past irreversible boundary (e.g., piece not yet assembled/packed). | Target operation resets to `READY`. Increments `iteration_number` ($N+1$). Previous measurements remain immutable. | Non-empty engineering reason for reopening (e.g. *"Subsequent assembly identified binding"*). | `PM_REOPEN_OPERATION` | Downstream completed operations are placed on hold or reverted to `PENDING`. |
| **Reassign Station / Operator** | `ROLE_PM` | Operation is in `READY` or `OPENED` status. | Updates `assigned_operator_id` and/or `station_id`. Status remains unchanged. | Target user ID or station ID, reason for transfer (e.g., machine breakdown). | `PM_REASSIGN_OPERATION` | Real-time SSE push transfers job from original operator's worklist to new operator. |
| **Update Process Instructions** | `ROLE_PM` | Operation is `PENDING`, `READY`, or `OPENED`. | Updates operation-specific `operator_notes` field. Does not mutate the frozen master template snapshot. | Updated instruction text, PM note. | `PM_UPDATE_OPERATION_NOTES` | Real-time SSE push refreshes instruction panel on operator's active terminal. |

---

### 3. Timing Metrics and Shop-Floor Performance Tracking

#### 3.1 Milestone Timestamps
Every record in `piece_operations` captures six discrete ISO-8601 timestamps:
1. `created_at`: Exact moment the operation row was generated from the snapshot.
2. `ready_at`: Exact moment all gating preconditions were satisfied and the operation became actionable.
3. `opened_at`: Exact moment the operator scanned/opened the job card on their tablet.
4. `submitted_at`: Exact moment the operator submitted measurement readings into the QC queue.
5. `qc_started_at`: Exact moment the QC Inspector claimed the inspection card.
6. `completed_at`: Exact moment the operation reached a resolved state (`PASSED`, `CONCESSION_ACCEPTED`, or `BYPASSED`).

#### 3.2 Measurable Lead Times & Durations
From these timestamps, the platform computes precise shop-floor performance metrics without manual timer buttons:

$$\text{Material Wait Time} = \text{ready\_at} - \text{created\_at} \quad \text{(for Sequence 10)}$$

$$\text{Operator Queue Wait Time} = \text{opened\_at} - \text{ready\_at}$$

$$\text{Active Machining Duration} = \text{submitted\_at} - \text{opened\_at}$$

$$\text{QC Queue Wait Time} = \text{qc\_started\_at} - \text{submitted\_at}$$

$$\text{Active QC Inspection Duration} = \text{completed\_at} - \text{qc\_started\_at}$$

$$\text{Total Operation Lead Time} = \text{completed\_at} - \text{ready\_at}$$

#### 3.3 Expected Cycle Time vs. Actual Elapsed Time
* **Expected Cycle Time (`expected_cycle_time_mins`):** Defined on each routing step in the frozen process template snapshot (e.g., 45 minutes for CNC Turning).
* **Automated Delay Calculation:**
  $$\text{Delay Variance} = \text{Active Machining Duration} - \text{Expected Cycle Time}$$
* **Shop-Floor Visual Indicators (PM Dashboard):**
  - **On-Schedule ($\text{Variance} \le 0$):** Displayed in green.
  - **Minor Variance ($0 < \text{Variance} \le 15\text{ mins}$):** Displayed in amber.
  - **Critical Bottleneck ($\text{Variance} > 15\text{ mins}$ or queue wait $> 4\text{ hours}$):** Displayed in pulsing red on the PM bottleneck monitor.

---

### 4. Concurrency Control, Reliability, and Audit Architecture

#### 4.1 Shop-Floor Concurrency Scenarios & Resolutions

| Concurrency Scenario | Conflict Description | Architectural Resolution |
| :--- | :--- | :--- |
| **Two Operators Opening Same Job** | Operator A and Operator B scan the same QR code traveler simultaneously. | **Optimistic Locking with Versioning:**<br/>`UPDATE piece_operations SET status = 'OPENED', opened_at = NOW(), assigned_operator_id = :userA, version = version + 1 WHERE id = :id AND version = :v AND status = 'READY'`<br/>Operator A succeeds (1 row updated). Operator B receives HTTP 409 Conflict with message: *"Job already opened by Operator A at Station 03."* |
| **Two QC Inspectors Claiming Same Job** | QC Inspector 1 and QC Inspector 2 click "Inspect" on the same piece in the QC queue. | **Atomic Conditional Claim:**<br/>`UPDATE piece_operations SET status = 'QC_IN_PROGRESS', qc_started_at = NOW(), assigned_qc_id = :qc1, version = version + 1 WHERE id = :id AND version = :v AND status = 'QC_PENDING'`<br/>Inspector 1 claims piece. Inspector 2 receives HTTP 409: *"Inspection already claimed by QC Inspector 1."* |
| **PM Edits Route while Operator Has Job Open** | PM attempts to bypass or resequence an operation while an operator is actively cutting metal. | **Precondition Block:**<br/>PM bypass/resequence APIs check: `IF status == 'OPENED' REJECT`. PM receives error: *"Operation is actively opened by Operator X. Operation cannot be bypassed or resequenced while machining is active."* |
| **Duplicate Measurement Submissions** | Operator double-clicks "Submit" or network retries HTTP request. | **Idempotent Transaction:**<br/>API checks `status == 'OPENED'`. First call transitions status to `QC_PENDING`. Second call sees `status == 'QC_PENDING'` and safely returns HTTP 200 with existing measurement record without duplicate insertion. |
| **Simultaneous Status Mutations across Sibling Operations** | Two operators complete operations on different pieces belonging to the same set at the exact same millisecond. | **Independent Entity Rows:**<br/>Operations belong to different `piece_id` rows; zero table-level lock contention. The parent set completion query runs under a read-committed transaction, guaranteeing correct atomic count of completed sibling pieces. |

#### 4.2 In-Process Asynchronous Reliability (No External Brokers)
* In strict adherence to architectural decisions **ADR-01, ADR-02, and ADR-03**, the application uses **zero Redis, zero RabbitMQ, and zero Kafka**.
* **Spring In-Process Events:** When an operation transitions to `PASSED`, the service publishes a lightweight Spring `ApplicationEvent` (`OperationCompletedEvent`).
* **Transactional Boundaries:** The operation completion and the unlocking of the next sequential operation occur within a single database `@Transactional` boundary on Neon Postgres. If the database commit fails, all state changes roll back cleanly.
* **Server-Sent Events (SSE):** Real-time worklist updates are pushed to connected browser clients over persistent HTTP SSE streams managed by Spring's `SseEmitter` registry.

---

### 5. Phase 1 Business Rules Traceability Matrix

Every requirement governing operation lifecycle, measurements, QC, and PM authority has been validated against this specification:

| Rule ID | Requirement Summary | Part 2.4 State Machine Implementation | Compliance Status |
| :--- | :--- | :--- | :--- |
| **BR-011** | Component retains its own operation and measurement history | Every operation captures separate operator and QC measurement sessions under discrete foreign keys. | **100% Compliant.** |
| **BR-014** | Quantity cannot be active in incompatible workflow states | Strict mutually exclusive state enum prevents dual states (e.g. cannot be `READY` and `PASSED` simultaneously). | **100% Compliant.** |
| **BR-015** | Only eligible passed/approved quantity advances to next operation | Operation N+1 unlocks to `READY` strictly when Operation N reaches `PASSED`, `CONCESSION_ACCEPTED`, or `BYPASSED`. | **100% Compliant.** |
| **BR-016** | PM may add, skip, resequence, reopen operations with permission & reason | Formal PM controls specify guard checks, mandatory justification notes, and immutable audit logs. | **100% Compliant.** |
| **BR-017** | Skipped operation remains in history with no measurement data | Bypassed operations transition to `BYPASSED` (`is_bypassed = true`), retain audit reason, and record zero measurements. | **100% Compliant.** |
| **BR-018** | Template revisions must not alter completed historical work | In-flight operations reference frozen point-in-time process template snapshots (`pieces.snapshot_id`). | **100% Compliant.** |
| **BR-021** | Operator cannot start or enter data before material readiness confirmed | Operation 10 is held in `PENDING` until `subcontract_products.material_ready = true`. No data entry permitted in `PENDING`. | **100% Compliant.** |
| **BR-032** | Operator and QC measurements are separate records; QC independently enters values | Separate rows in `operation_measurements` tagged with `recorded_by_role`. QC measurement grid is completely unpopulated. | **100% Compliant.** |
| **BR-033** | QC failure notifies PM; PM decides disposition | Operation failure transitions to `FAILED`, quaranines piece, and broadcasts real-time SSE alert to PM dashboard. | **100% Compliant.** |
| **BR-034** | PM concession override requires mandatory reason; approval never changes measurement | PM `CONTINUE` requires non-empty justification, sets operation to `CONCESSION_ACCEPTED`, retains original `FAILED` verdict and measurements. | **100% Compliant.** |
| **BR-035** | Rework routes to earlier operation with incremented iteration | PM `RETURN_TO_EARLIER_OPERATION` rewinds routing, target op re-enters `READY`, and `iteration_number` increments ($N+1$). | **100% Compliant.** |
| **BR-036** | Reworked piece rejoins normal sequence after passing QC | After passing rework QC, downstream operation following original failure point unlocks to `READY`. | **100% Compliant.** |
| **BR-038** | Additional process requires dedicated QC inspection | Dynamic remedial step (`is_extra_operation = true`) must be inspected and certified by QC before returning to routing. | **100% Compliant.** |
| **BR-040** | Vendor operations track RM DC references; Production does not create DCs | Vendor operations link to RM DC numbers via `vendor_dc_trackers`. Inward confirmation unlocks internal QC. | **100% Compliant.** |
| **BR-052** | Backend APIs enforce permissions; absent grant means deny | Spring Security method-level authorization (`@PreAuthorize`) validates specific role grants across all 15 transitions. | **100% Compliant.** |
| **BR-054** | Every approval, override, cancellation, bypass, reassignment is audited | Append-only `audit_logs` records user ID, timestamp, before/after JSON state, and mandatory justification notes. | **100% Compliant.** |

---

### 6. Genuine Open Business Questions Register

The following operational policy questions have been identified during the Part 2.4 state machine design and are held open for business stakeholder confirmation:

1. **Mid-Production Material Readiness Revocation Policy:**
   * *Question:* If RM Stores revokes material readiness (`material_ready` toggled from `true` to `false`) due to a raw material recall or heat code discrepancy after machining has started, should in-flight operations currently in `OPENED` be allowed to finish, or should they be immediately forced into a quarantined hold state?
   * *Design Baseline:* Unstarted `READY` operations are blocked; active `OPENED` operations display an amber alert banner and cannot transition to `COMPLETED` until RM confirms resolution.
2. **Operator Workstation Claim Lock Timeout:**
   * *Question:* If an operator opens a job card (`status = 'OPENED'`) but walks away from the terminal without entering measurements or releasing the job, what is the automatic release timeout before the job card is unlocked back to `READY` for other operators?
   * *Design Baseline:* Recommended 60-minute inactivity timeout, after which the operation releases back to `READY` with audit event `OPERATION_AUTO_RELEASED_INACTIVITY`.
3. **Irreversible Threshold for PM Reopening Operations:**
   * *Question:* How far downstream can a piece progress before PM reopening (`OP-T13`) of an earlier operation is strictly prohibited?
   * *Design Baseline:* An operation cannot be reopened if the parent piece has completed all operations (`pieces.status == 'COMPLETED'`) or if the parent set has passed Final QC (`is_final_qc_passed == true`).
