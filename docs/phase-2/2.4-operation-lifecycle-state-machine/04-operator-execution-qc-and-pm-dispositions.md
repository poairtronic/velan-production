# Part 2.4 – Operation Lifecycle State Machine
## Document 04: Operator Execution, Independent QC Lifecycle, and PM Dispositions

---

### 1. Machine Operator Execution Architecture

#### 1.1 Filtered Shop-Floor Worklist Display
* **RBAC & Assignment Filtering:** When an operator logs in with `ROLE_OPERATOR`, the interface displays only actionable operations where:
  1. `piece_operations.status == 'READY'`.
  2. The operation matches the operator’s assigned machine station (`station_id`), department, or direct user assignment (`assigned_operator_id`).
  3. Parent product material readiness is satisfied (`subcontract_products.material_ready == true`).
  4. The parent piece is in `IN_PRODUCTION`, `IN_PRODUCTION_REWORK`, or `IN_PRODUCTION_EXTRA_PROCESS` (not on hold or scrapped).
* **Worklist Priority Ranking:** Jobs are sorted by PM priority ranking, contract delivery date, and operation sequence. Operations in `PENDING` status are hidden or visually greyed out as locked.

#### 1.2 Job Opening & Context Presentation
* **Trigger & Timestamp:** An operator selects an eligible job or scans the physical QR code on the piece traveler sheet. The system atomically executes transition `OP-T04`:
  ```sql
  UPDATE piece_operations 
  SET status = 'OPENED', 
      opened_at = NOW(), 
      assigned_operator_id = :operator_user_id 
  WHERE id = :operation_id 
    AND status = 'READY';
  ```
* **Context Displayed to Operator:**
  1. **Engineering Blueprints:** Integrated viewer displaying 2D PDF blueprints and inspection sketches stored in Cloudflare R2 (`drawing_documents`).
  2. **Process Instructions:** Step-by-step machining instructions, spindle speeds, feed rates, tooling codes, and fixture notes from `process_template_snapshots`.
  3. **Approved Tolerance Parameters:** Static nominal values and approved dynamic tolerance bands (Nominal, Upper Limit, Lower Limit) derived from `tolerance_rules`.
  4. **Machine Station Details:** Designated CNC machine code, calibration status, and cutting fluid specifications.

#### 1.3 Bulk-Entry Grid & Excel Paste Support
For high-volume production, the operator interface provides a spreadsheet-style bulk-entry data grid:
* **Grid Topology:**
  - **Rows:** Represent individual serialized physical pieces belonging to the batch/set.
  - **Columns:** Represent the specific required inspection dimensions (e.g., Outer Diameter, Bore Depth, Flange Thickness, Concentricity).
* **Excel Copy/Paste Compatibility:** Operators or shop-floor data clerks can copy a block of measured readings from digital micrometers, CMM export files, or Microsoft Excel and paste directly into the grid. The client-side parser automatically parses tab-delimited and newline-delimited data into the respective row/column cells.

#### 1.4 Tolerance Validation & Non-Blocking Alert Mechanics
* **Instant Client-Side Evaluation:** As values are typed or pasted, the application compares each reading against the active approved tolerance rules:
  $$\text{Lower Limit} \le \text{Value} \le \text{Upper Limit}$$
* **Non-Blocking Visual Warning (`SRS FR-010, BR-032`):**
  - **In-Tolerance Cells:** Highlighted in soft green.
  - **Out-of-Tolerance Cells:** Highlighted in soft amber/orange with an alert icon showing the exact deviation (e.g., `+0.04 mm above upper limit`).
  - **Approved Non-Blocking Rule:** Operators are **not mechanically blocked** from submitting out-of-tolerance values. Machining deviations happen in reality; blocking the operator would incentivize entering falsified "in-spec" data. The system allows the operator to submit actual honest readings, displays a clear warning confirmation modal (*"One or more dimensions exceed nominal limits. Submit for QC inspection?"*), and routes the piece to QC for formal verification.

#### 1.5 Edge Case Handling & Policy Rules

| Edge Case Scenario | System Behavior & Defensive Policy |
| :--- | :--- |
| **Missing Mandatory Values** | The submission is rejected atomically. Incomplete cells are highlighted in red with validation errors. Partial submission of half-measured pieces is prohibited. |
| **Malformed / Invalid Data Pasted** | If non-numeric characters (e.g. text, symbols) or negative values for physical lengths are pasted, the client and server reject the payload with HTTP 422. The invalid cells are outlined in red, and the submit button remains disabled until corrected. |
| **Mixed Valid & Invalid Rows in Batch** | **Atomic Batch Policy:** The system enforces complete batch integrity. If Row 1 to 4 are valid but Row 5 has malformed data, the entire batch submission is blocked. Partial auto-submission of valid rows while dropping invalid rows is strictly prohibited to prevent orphaned operations. |
| **Duplicate Submission Attempts** | If an operator double-clicks "Submit" or network lag causes repeated HTTP requests, the API relies on the optimistic `@Version` counter and unique request idempotency keys. The first request succeeds (`QC_PENDING`); the second request is safely discarded with HTTP 409 Conflict. |
| **Operation No Longer `READY` or `OPENED`** | If a PM dispositioned the parent piece (e.g. placed on hold or scrapped) while an operator was typing measurements, the backend rejects the submission with HTTP 409: *"Operation status has changed. Refreshing worklist."* No corrupt writes occur. |
| **Network Disconnection During Entry** | The React frontend continuously caches unsaved grid inputs in local browser storage (`IndexedDB` draft cache). If the Wi-Fi drops, entered numbers are not lost. An offline indicator is displayed. When connection restores, the operator reviews the cached data and submits. |

---

### 2. Independent Quality Control (QC) Lifecycle

#### 2.1 The Principle of Independent Verification (`BR-032`)
Quality Control operates as an independent audit gate. It is not an operator assistance tool:
* **Separate Entity Records:** Operator measurements and QC measurements are stored in separate rows in `operation_measurements`:
  - `recorded_by_role = 'OPERATOR'` vs `recorded_by_role = 'QC_INSPECTOR'`.
* **Zero Pre-Filling Invariant:** When a QC Inspector opens an inspection sheet, the measurement input cells are **completely blank**. QC inspectors are never shown operator values as default inputs. QC must measure the actual metal piece with calibrated measuring equipment (Vernier calipers, bore gauges, height masters, optical comparators).

#### 2.2 Detailed QC State Progression
1. **`QC_PENDING` (In Inspection Bay):**
   - The operation enters this state immediately upon operator submission (`submitted_at = NOW()`).
   - The piece is physically moved to the shop-floor inspection bay.
   - Operator measurement inputs are completely locked from further editing.
2. **`QC_IN_PROGRESS` (Inspection Claimed):**
   - A QC Inspector clicks "Inspect Piece" on their queue.
   - System executes atomic lock:
     ```sql
     UPDATE piece_operations 
     SET status = 'QC_IN_PROGRESS', 
         qc_started_at = NOW(), 
         assigned_qc_id = :qc_user_id 
     WHERE id = :operation_id 
       AND status = 'QC_PENDING';
     ```
   - This atomic update prevents two QC inspectors from accidentally inspecting the same physical piece simultaneously.
3. **Measurement Input & Instrument Recording:**
   - The QC Inspector enters their calibrated instrument identification (e.g., `MITUTOYO-MIC-042`, calibration validity date).
   - The inspector enters independent numerical readings for each required tolerance parameter.
4. **Automated Compliance Verification:**
   - The system evaluates QC readings against approved tolerance rules (`tolerance_rules`).
   - **Scenario A: 100% Within Tolerance:**
     - QC Inspector clicks "Approve Operation".
     - System sets `qc_inspections.verdict = 'PASSED'`.
     - Sets `piece_operations.status = 'PASSED'` and `completed_at = NOW()`.
     - Automatically evaluates and unlocks the next sequential operation to `READY` (`OP-T03`).
   - **Scenario B: Out-of-Tolerance Parameter:**
     - System flags the failing dimension in red.
     - QC Inspector has **no authority to override or approve** an out-of-spec reading (`BR-033`).
     - QC Inspector enters inspection notes and clicks "Submit Non-Conformance".
     - System sets `qc_inspections.verdict = 'FAILED'`.
     - Sets `piece_operations.status = 'FAILED'` and `completed_at = NOW()`.
     - Immediately escalates to the PM Exception subsystem.

---

### 3. Deep-Dive: The Four Approved PM Exception Dispositions

When an operation receives a `FAILED` verdict, execution on that piece is immediately halted (`pieces.status = 'ON_HOLD_QC_FAILED'`). Only the Production Manager (`ROLE_PM`) has architectural authority to disposition the exception:

```
+-----------------------------------------------------------------------------------------+
| PM DISPOSITION GATEWAY (qc_exceptions: PENDING_PM_DISPOSITION)                          |
+-----------------------------------------------------------------------------------------+
       │
       ├──► 1. CONTINUE (Concession Progression)
       │      ├── Failure verdict remains FAILED permanently in history
       │      ├── Mandatory non-empty PM engineering justification notes
       │      ├── Operation status -> CONCESSION_ACCEPTED (completed_at = NOW())
       │      ├── Next sequential operation unlocked -> READY
       │      └── Piece status -> IN_PRODUCTION
       │
       ├──► 2. RETURN TO EARLIER OPERATION (Routing Rewind & Rework)
       │      ├── PM selects target operation sequence (<= failed sequence)
       │      ├── Operation iteration_number incremented (e.g. 1 -> 2)
       │      ├── Prior measurements and failure verdicts remain frozen immutable
       │      ├── Target operation status -> READY for re-machining
       │      └── Piece status -> IN_PRODUCTION_REWORK
       │
       ├──► 3. EXTRA PROCESS (Ad-Hoc Remedial Step)
       │      ├── PM injects new operation (is_extra_operation = true, seq e.g. 25)
       │      ├── Extra operation status -> READY with specialized instructions
       │      ├── Requires dedicated execution and independent QC inspection
       │      └── Piece status -> IN_PRODUCTION_EXTRA_PROCESS
       │
       └──► 4. REJECT AND RESTART (Scrap & Replacement)
              ├── Defective physical piece permanently marked SCRAPPED (Terminal)
              ├── All historical failure data frozen permanently for scrap analysis
              ├── ECR Outbox event queued (SCRAP_EXCEPTION) for external defect costing
              ├── Replacement child piece spawned inheriting set slot (-R1)
              └── Replacement enters AWAITING_MATERIAL pending new RM stock issue
```

---

### 4. PM Disposition Impact Matrix Across Architectural Entities

The following matrix defines how each PM disposition impacts the current operation, other routing steps, and the parent piece and set:

| Entity / Property | 1. CONTINUE | 2. RETURN TO EARLIER OP | 3. EXTRA PROCESS | 4. REJECT AND RESTART |
| :--- | :--- | :--- | :--- | :--- |
| **Failed Operation Status** | Transitions to `CONCESSION_ACCEPTED` | Remains `FAILED` (Iteration 1) | Remains `FAILED` (Pending extra step) | Transitions to `CANCELLED` |
| **Failed QC Inspection Record** | Permanently `FAILED` (Never altered) | Permanently `FAILED` (Never altered) | Permanently `FAILED` (Never altered) | Permanently `FAILED` (Never altered) |
| **Operator / QC Measurements** | 100% Immutable; never deleted | 100% Immutable; new iteration created | 100% Immutable; extra op measured | 100% Immutable; frozen for scrap audit |
| **Mandatory PM Justification** | Non-empty engineering reason required | Non-empty rework instructions required | Non-empty remedial step details required| Non-empty scrap engineering reason req. |
| **Operation `iteration_number`** | Remains current iteration | **Incremented ($N+1$)** on target op | Initialized to `1` on extra op | N/A (Operation terminated) |
| **Downstream Operation Impact** | Next sequential op unlocks to `READY` | Downstream ops remain locked in `PENDING`| Downstream ops remain locked in `PENDING`| All downstream ops marked `CANCELLED` |
| **Parent Piece Status** | `IN_PRODUCTION` | `IN_PRODUCTION_REWORK` | `IN_PRODUCTION_EXTRA_PROCESS` | **`SCRAPPED`** (Terminal retirement) |
| **Replacement Child Piece** | None spawned | None spawned | None spawned | **Spawned 1:1** in `AWAITING_MATERIAL` |
| **Parent Assembled Set Impact** | Unhindered; set progresses normally | Set remains `BUILDING` awaiting rework | Set remains `BUILDING` awaiting extra op | Set remains `BUILDING` awaiting child piece |
| **ECR Outbox Event** | None (Internal concession) | Optional rework notice | Optional rework notice | **Mandatory `SCRAP_EXCEPTION` enqueued** |
