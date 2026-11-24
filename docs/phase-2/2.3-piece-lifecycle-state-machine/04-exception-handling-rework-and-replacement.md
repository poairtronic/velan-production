# Part 2.3 – Piece Lifecycle State Machine
## Document 04: Exception Handling, Rework Iterations, and Replacement Lineage

---

### 1. The Quality Control Exception Lifecycle

When a precision valve component is inspected by a Quality Control (QC) Inspector and any critical dimensional parameter falls outside the approved tolerance band (defined in `tolerance_rules`), the piece immediately enters the exception management lifecycle:

```
[ Active Machining ] ──► [ QC Inspection: FAILED ] ──► [ Piece: ON_HOLD_QC_FAILED ]
                                                                   │
                                                      [ qc_exceptions: PENDING ]
                                                                   │
                                                                   ▼
                                                       [ PM Binding Disposition ]
                                                                   │
                                    ┌──────────────────┬───────────┴──────────┬──────────────────┐
                                    ▼                  ▼                      ▼                  ▼
                              [ CONTINUE ]     [ RETURN TO OP ]       [ EXTRA PROCESS ]     [ REJECT/SCRAP ]
```

#### 1.1 Automated Quarantine Mechanics
1. **Immediate Logical Lock:** The backend immediately transitions the affected operation in `piece_operations` to `FAILED` and sets `pieces.status = 'ON_HOLD_QC_FAILED'`.
2. **Shop-Floor Terminal Lockout:** The barcode scanning interface blocks any machine operator from opening, advancing, or entering data for that piece. The UI displays a persistent amber quarantine banner: *"PIECE QUARANTINED: QC Inspection Failed at Step {Seq}. Awaiting PM Disposition."*
3. **Automated Ticket Creation:** The system creates a record in `qc_exceptions` with status `PENDING_PM_DISPOSITION`, linking the physical piece, the failed operation, and the QC inspection session.
4. **Real-Time PM Alerting:** An urgent event is broadcast over Server-Sent Events (SSE) to the Production Manager's dashboard, placing the item in the PM's Priority Exception Worklist.

---

### 2. Deep Dive: PM Disposition 1 – `CONTINUE` (Concession Progression)

#### 2.1 The Engineering Concession Concept
In precision manufacturing, certain non-critical dimensions may be slightly out of standard nominal limits without compromising the structural integrity or functional safety of the valve assembly (e.g., a chamfer angle deviation of $0.5^\circ$ or a non-mating clearance). The PM, with engineering authority, may determine that the part is acceptable for use as-is without physical rework.

#### 2.2 Mandatory Concession Invariants
To maintain absolute compliance with ISO 9001 and nuclear manufacturing traceability standards, `CONTINUE` enforces five non-negotiable architectural invariants:

1. **Measurement Immutability:** Both the original operator measurements and the failed QC measurements in `operation_measurements` and `measurement_values` remain strictly immutable. They are **never modified, overwritten, or deleted**.
2. **Failure History Permanence:** The `qc_inspections` record remains permanently marked with `verdict = 'FAILED'`. The failure is **never silently converted into a pass**.
3. **Mandatory PM Justification:** The PM must enter a detailed engineering justification in `pm_dispositions.justification_notes` (e.g., *"Concession granted per Design NCR-402: non-mating outer flange thickness within allowable structural margin"*). Blank or whitespace entries are rejected by database check constraints.
4. **Explicit Operation Status `CONCESSION_ACCEPTED`:** The failed operation in `piece_operations` transitions from `FAILED` to **`CONCESSION_ACCEPTED`**, recording `completed_at = NOW()`. It is never given the standard `PASSED` status, ensuring full distinction between nominal conformity and accepted concessions in quality reporting.
5. **Sequential Progression:** Only upon recording `CONCESSION_ACCEPTED` does the next sequential operation in the routing (sequence N+1) transition from `PENDING` to `READY` (`ready_at = NOW()`). The piece status returns to `IN_PRODUCTION`, and `qc_exceptions` transitions to `RESOLVED_CONCESSION`.

---

### 3. Deep Dive: PM Disposition 2 – `RETURN_TO_EARLIER_OPERATION` (Routing Rewind & Rework)

#### 3.1 Rewind Architecture
When a defect can be remediated by re-machining (e.g., an undersized bore or rough surface finish), the PM rewinds the piece back to a designated earlier operation (specified by `target_operation_id`):

```
Step 10 (Turning: PASSED) ──► Step 20 (Milling: PASSED) ──► Step 30 (Grinding: FAILED)
                                    ▲                                     │
                                    └──────── PM Disposition: Rewind ─────┘
                                           (iteration_number: 1 ──► 2)
```

#### 3.2 Monotonically Incrementing Rework Iterations
* **The Iteration Invariant:** A piece undergoing rework never overwrites previous measurements. Every execution cycle is tracked by an explicit integer counter: `iteration_number` on `operation_measurements`.
  - `iteration_number = 1`: The original, baseline execution of the operation.
  - `iteration_number = 2`: First rework execution.
  - `iteration_number = 3`: Subsequent rework execution (if required).
* **Database Representation:**
  ```
  operation_measurements (id: M01, op_id: Op20, role: OPERATOR, iteration: 1, val: 50.12)
  operation_measurements (id: M02, op_id: Op20, role: QC,       iteration: 1, val: 50.15) -> FAILED
  -- PM Dispositions Rewind to Op20 --
  operation_measurements (id: M03, op_id: Op20, role: OPERATOR, iteration: 2, val: 50.04)
  operation_measurements (id: M04, op_id: Op20, role: QC,       iteration: 2, val: 50.02) -> PASSED
  ```
* **Re-Inspection Sequence:** The piece must be executed and pass QC inspection at the rework operation under `iteration_number = 2`.
* **Rejoining Normal Routing:** Once the reworked operation passes QC, the piece progresses to the next sequential step following the original failure point, and piece status returns from `IN_PRODUCTION_REWORK` to `IN_PRODUCTION`.

---

### 4. Deep Dive: PM Disposition 3 – `EXTRA_PROCESS` (Ad-Hoc Remediation)

#### 4.1 Dynamic Operation Insertion
In certain non-conformance scenarios, remediation cannot be achieved by repeating an existing routing step, but requires a specialized, ad-hoc remedial process (e.g., skim grinding, manual deburring, chemical etching, or hand lapping):

```
Step 20 (Milling: FAILED) ──► [ Step 25: Skim Grinding (EXTRA) ] ──► Step 30 (Final Turning)
                                            │
                                  QC Inspector Approves
```

#### 4.2 Extra Process Rules
1. **Dynamic Step Injection:** The PM defines the operation name, station, and instructions. The system dynamically inserts a new record into `piece_operations`:
   - `sequence_order`: Interpolated sequence (e.g., `25` between `20` and `30`).
   - `is_extra_operation`: Set to `true`.
   - `status`: Initialized directly to `READY`.
2. **Piece State:** `pieces.status` transitions to `IN_PRODUCTION_EXTRA_PROCESS`.
3. **Dedicated QC Gate:** The extra process operation **must undergo independent QC inspection** (`BR-038`).
4. **Rejoining Standard Routing:** Upon receiving `verdict = 'PASSED'` from QC on the extra process, the original failed operation (Step 20) is updated to `COMPLETED` (remedied), and Step 30 becomes `READY`. The piece status returns to `IN_PRODUCTION`.

---

### 5. Deep Dive: PM Disposition 4 – `REJECT_AND_RESTART` (Scrap & Replacement Lineage)

#### 5.1 Physical Scrap and Terminal Retirement
When a physical component is damaged beyond recovery (e.g., cracked casting, excessive metal cut off, wall thickness below absolute minimum), the PM must scrap the piece:
* **Terminal Status:** `pieces.status` transitions permanently to `SCRAPPED`.
* **Zero Revival:** A piece marked `SCRAPPED` can **never be revived, reopened, or returned to production**. Its physical barcode is permanently dead on the shop floor.
* **Preservation of Failure Records:** All historical measurements, operator IDs, timestamps, and QC failure records remain stored permanently for scrap rate analysis, root-cause investigation, and vendor defect claims.

#### 5.2 Quantity Conservation Principle
* **Exact Set Slot Conservation:** Scrapping a component does **not** alter the commercial target set quantity of the SC, nor does it increase the required component demand (`BR-005, BR-006, BR-012, BR-013`).
* **1:1 Replacement Invariant:** The system spawns exactly **one physical replacement piece** to replace the scrapped piece within its designated set.
* **Example:**
  - Set #1 requires 1 Valve Body (`VBM`), 2 Flanges (`ARG`), and 2 Spindles (`SPG`).
  - Spindle #2 of Set #1 (`SC002-1 SPG-2`) is scrapped at Step 20.
  - The system spawns replacement piece `SC002-1 SPG-2-R1`.
  - Set #1 still requires exactly 2 Spindles. Total active piece demand for Set #1 remains exactly 5 pieces.

#### 5.3 Replacement Genealogy & Serial Numbering
The parent-child genealogy between the retired scrapped piece and the new replacement piece is recorded in `piece_replacement_lineage`:

| Attribute | Parent Piece (Scrapped) | Child Replacement Piece (Active) |
| :--- | :--- | :--- |
| `id` | `UUID_PARENT` | `UUID_CHILD` (New) |
| `sc_id` | `SC002` | `SC002` (Inherited) |
| `sc_product_id` | `SCP_LINE_1` | `SCP_LINE_1` (Inherited) |
| `assembled_set_id` | `SET_01` | `SET_01` (Inherited) |
| `set_sequence_number`| `1` | `1` (Inherited) |
| `bom_line_id` | `BOM_SPINDLE` | `BOM_SPINDLE` (Inherited) |
| `component_code` | `'SPG'` | `'SPG'` (Inherited) |
| `component_sequence_in_set` | `2` | `2` (Inherited) |
| `replacement_sequence` | `0` (Original) | **`1`** (`parent.replacement_sequence + 1`) |
| `serial_number` | `SC002-1 SPG-2` | **`SC002-1 SPG-2-R1`** |
| `replaced_piece_id` | `NULL` | **`UUID_PARENT`** |
| `status` | **`SCRAPPED`** (Terminal) | **`AWAITING_MATERIAL`** (Gated) |

#### 5.4 Replacement Material Readiness Gate
* **Critical Safety Rule:** Spawning a replacement piece does **not** allow machining to start immediately.
* **State at Creation:** The replacement piece enters `status = 'AWAITING_MATERIAL'`.
* **Gating Trigger:** The operator cannot open Step 10 until RM (V-SMART) issues new raw material stock matched to the replacement requirement, OR until the PM issues an explicit emergency raw material allocation override.

#### 5.5 ECR Outbox Integration for Defect Costing
When a piece is scrapped, the Velan Production platform notifies the external Engineering Change Request (ECR) system:
1. An event is written to `outbox_events`:
   - `aggregate_type`: `'SCRAP_EXCEPTION'`
   - `aggregate_id`: Foreign key to `qc_exceptions.id`
   - `payload`: JSON payload containing SC number, piece serial number, part code, failed operation, measured values vs. tolerances, and PM scrap justification.
2. The background outbox publisher dispatches the payload via HTTPS POST to ECR's intake endpoint using the outbox ID as the HTTP `Idempotency-Key`.
3. **Boundary Rule:** ECR evaluates vendor responsibility, calculates scrap costing, and manages financial chargebacks. ECR costing results **do not return to Velan Production** under the approved Phase 1 boundary (`BR-039, BR-056`).
