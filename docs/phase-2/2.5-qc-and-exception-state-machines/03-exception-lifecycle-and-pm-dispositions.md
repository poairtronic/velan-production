# Part 2.5 – QC and Exception State Machines
## Document 03: Quality Exception State Machine and PM Disposition Governance

---

### 1. Non-Conformance Case Creation & Quarantine Locking

When a Quality Control inspection receives a `verdict = 'FAILED'`, the platform executes an immediate, atomic quarantine procedure:

```
[ QC Inspection FAILED ]
           │
           ▼
[ Atomic Quarantine Transaction ]
   ├── 1. piece_operations.status = 'FAILED'
   ├── 2. pieces.status = 'ON_HOLD_QC_FAILED'
   ├── 3. INSERT INTO qc_exceptions (status = 'PENDING_PM_DISPOSITION', opened_at = NOW())
   ├── 4. Downstream operations remain strictly PENDING
   ├── 5. Physical barcode locked on shop-floor terminals
   └── 6. Real-time SSE broadcast to PM Priority Exception Queue
```

#### 1.1 Shop-Floor Quarantine Controls
* **Terminal Lockout:** If an operator scans the piece traveler barcode at any machine station while in `ON_HOLD_QC_FAILED`, the terminal screen turns amber and displays:
  > **PIECE QUARANTINED ON QUALITY HOLD**  
  > **Serial:** `SC002-1 SPG-1` | **Failed Step:** Sequence 20 (CNC Turning)  
  > **Defect:** Outer diameter 50.18 mm exceeds upper tolerance limit 50.10 mm (+0.08 mm).  
  > **Action Required:** Physical part must remain in Quarantine Bay awaiting PM Disposition.
* **Downstream Freezing:** Succeeding operations in the routing sequence remain in `PENDING` status. No downstream job can be opened or executed.

---

### 2. Deep-Dive: The Four Approved PM Exception Dispositions

Only an authorized user holding `ROLE_PM` (Production Manager) possesses the system authority to resolve an exception ticket (`BR-033, BR-034`). The PM selects one of four binding dispositions:

```
                               ┌──────────────────────────────────────────────┐
                               │     qc_exceptions: PENDING_PM_DISPOSITION    │
                               └──────────────────────┬───────────────────────┘
                                                      │
                    ┌──────────────────┬──────────────┴─────┬──────────────────┐
                    ▼                  ▼                    ▼                  ▼
             [ CONTINUE ]      [ RETURN TO OP ]     [ EXTRA PROCESS ]   [ REJECT/SCRAP ]
             (Concession)          (Rework)             (Remedial)          (Replace)
                    │                  │                    │                  │
                    ▼                  ▼                    ▼                  ▼
             RESOLVED_CONCESS    RESOLVED_REWORK      RESOLVED_EXTRA      RESOLVED_SCRAP
                    │                  │                    │                  │
                    └──────────────────┴──────────────┬─────┴──────────────────┘
                                                      │
                                                      ▼
                                           [ qc_exceptions: CLOSED ]
```

#### Disposition 1: `CONTINUE` (Engineering Concession)
* **Operational Intent:** Used when the dimensional deviation is technically acceptable (e.g. non-mating feature, cosmetic clearance) and does not compromise performance or pressure integrity.
* **Mandatory Justification Gate:** The PM must enter detailed, non-empty engineering rationale in `pm_dispositions.justification_notes`. The system rejects blank or whitespace entries (`CHECK (justification_notes <> '')`).
* **Preservation of Failure History:** The `qc_inspections.verdict` stays permanently **`FAILED`**. The system **never converts a failure into a pass**.
* **Operation Status Transition:** The failed operation in `piece_operations` transitions to **`CONCESSION_ACCEPTED`** (`completed_at = NOW()`), which cleanly differentiates accepted non-conformances from nominal passes in customer data books.
* **Downstream Activation:** The next sequential operation (sequence N+1) is unlocked from `PENDING` to `READY` (`ready_at = NOW()`).
* **Piece Status:** Transitions from `ON_HOLD_QC_FAILED` back to `IN_PRODUCTION`.
* **Exception Status:** `qc_exceptions.status` transitions to `RESOLVED_CONCESSION` and subsequently to `CLOSED`.

#### Disposition 2: `RETURN_TO_EARLIER_OPERATION` (Routing Rewind & Rework)
* **Operational Intent:** Used when an out-of-tolerance condition can be physically corrected by re-machining an existing step (e.g., skim-cutting an oversized diameter down to nominal size).
* **Target Step Selection:** The PM selects an earlier routing operation ($\text{sequence} \le \text{failed sequence}$) from the piece's history.
* **Rework Iteration Invariant (`iteration_number = 2, 3...`):**
  - Original measurements from iteration 1 remain **100% immutable and permanently preserved**.
  - The target operation re-enters `READY` with an incremented counter: `iteration_number += 1`.
  - Machine operators and QC inspectors record rework measurements under this new iteration.
* **Piece Status:** Transitions from `ON_HOLD_QC_FAILED` to `IN_PRODUCTION_REWORK`.
* **Rejoining Routing Sequence:** Once the rework operation passes QC under the new iteration, the piece rejoins normal routing at the step immediately following the original failure point (`BR-036`).
* **External Integration Notifications (RM & ECR):** Automatically enqueues asynchronous outbox notification events:
  - **RM Outbox Event (`destination_system = 'RM'`):** Transmits `REWORK_ADVISORY` to RM so the material system remains informed of piece re-machining. RM continues to own all material records.
  - **ECR Outbox Event (`destination_system = 'ECR'`):** Transmits `REWORK_NOTICE` for engineering tracking if rework parameter thresholds require engineering review.
* **Exception Status:** Transitions to `RESOLVED_REWORK` and subsequently to `CLOSED`.

#### Disposition 3: `EXTRA_PROCESS` (Ad-Hoc Remedial Step)
* **Operational Intent:** Used when remediation requires an ad-hoc specialized operation not present in the standard process template (e.g. hand deburring, selective electro-plating, chemical polishing, or bench skimming).
* **Dynamic Operation Insertion:** The PM injects a new operation record into `piece_operations`:
  - `sequence_order`: Set to an intermediate sequence (e.g., `25` between `20` and `30`).
  - `is_extra_operation`: Set to `true`.
  - `status`: Initialized directly to `READY`.
* **Dedicated QC Verification Gate (`BR-038`):** The ad-hoc operation **must undergo independent QC inspection**. The piece cannot bypass QC on remedial operations.
* **Piece Status:** Transitions from `ON_HOLD_QC_FAILED` to `IN_PRODUCTION_EXTRA_PROCESS`.
* **Rejoining Standard Route:** When the extra operation receives `verdict = 'PASSED'` from QC, the original failed step is marked resolved, and the downstream standard operation (Sequence 30) transitions to `READY`.
* **External Integration Notifications (RM & ECR):** Enqueues `EXTRA_PROCESS_ADVISORY` to **RM** (to document the additional processing cycle) and `EXTRA_PROCESS_NOTICE` to **ECR** (for engineering sign-off).
* **Exception Status:** Transitions to `RESOLVED_EXTRA_PROCESS` and subsequently to `CLOSED`.

#### Disposition 4: `REJECT_AND_RESTART` (Scrap & Replacement Lineage)
* **Operational Intent:** Used when a defect is fatal and cannot be reworked (e.g. casting porosity, wall thickness cut below minimum, cracked metal).
* **Terminal Piece Retirement:** The physical piece status transitions permanently to **`SCRAPPED`**. The piece is physically marked scrap and removed from the active line. Historical measurements and failure records are preserved immutable forever.
* **Quantity Conservation & 1:1 Replacement Invariant (`BR-012, BR-013`):**
  - Spawning a replacement piece replaces the retired parent 1:1 within the designated set slot.
  - It does **not** increase the commercial target set quantity of the SC or total component demand.
  - The replacement piece inherits the parent’s exact `assembled_set_id`, `set_sequence_number`, and component role, with incremented `replacement_sequence = parent.replacement_sequence + 1` (e.g., `SC002-1 SPG-1-R1`).
* **Replacement Material Gating:** The replacement piece enters `status = 'AWAITING_MATERIAL'`. Physical machining is blocked until RM Stores issues replacement raw stock against the defect notice.
* **Dual Integration Outbox Triggers (RM and ECR):** The PM scrap transaction atomically inserts two separate destination-specific records into `outbox_events`:
  1. **RM Outbox Event (`destination_system = 'RM', event_type = 'MATERIAL_REJECTION_NOTICE'`):** Notifies RM of the scrapped piece and raw material heat code. RM receives this exception and continues to own material and inventory transactions (staging replacement material, updating stock ledgers). Production does **not** perform RM inventory transactions or generate vendor DCs.
  2. **ECR Outbox Event (`destination_system = 'ECR', event_type = 'SCRAP_EXCEPTION'`):** Notifies ECR of non-conformance measurements and failure details. ECR handles engineering responsibility determination and scrap costing separately. Production does **not** bring defect costing into its manufacturing workflow.
* **Exception Status:** Transitions to `RESOLVED_SCRAP` and subsequently to `CLOSED`.

---

### 3. Quality Exception State Transition Matrix

The table below specifies the formal state transitions of the `qc_exceptions` entity:

| Transition ID | Current State | Target State | Triggering Event | Authorized Actor | Preconditions & Guard Checks | Side Effects & State Mutations |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **EX-T01** | `None` | `PENDING_PM_DISPOSITION`| `QC_INSPECTION_FAILED` | QC Inspector (`ROLE_QC`) | QC inspection submitted with `verdict = 'FAILED'`. | 1. Inserts `qc_exceptions` row.<br/>2. Sets `pieces.status = 'ON_HOLD_QC_FAILED'`.<br/>3. Pushes urgent SSE alert to PM worklist. |
| **EX-T02** | `PENDING_PM_DISPOSITION`| `RESOLVED_CONCESSION` | `PM_APPROVE_CONCESSION` | Production Manager (`ROLE_PM`) | Non-empty `justification_notes` provided. | 1. Inserts `pm_dispositions` (`decision = 'CONTINUE'`).<br/>2. Sets op status to `CONCESSION_ACCEPTED`.<br/>3. Unlocks next sequential op to `READY`.<br/>4. Sets `pieces.status = 'IN_PRODUCTION'`. |
| **EX-T03** | `PENDING_PM_DISPOSITION`| `RESOLVED_REWORK` | `PM_APPROVE_REWORK` | Production Manager (`ROLE_PM`) | Valid earlier target op selected; rework notes provided. | 1. Inserts `pm_dispositions` (`decision = 'RETURN_TO_EARLIER_OPERATION'`).<br/>2. Target op resets to `READY` (`iteration_number += 1`).<br/>3. Sets `pieces.status = 'IN_PRODUCTION_REWORK'`.<br/>4. Enqueues outbox notifications for RM and ECR. |
| **EX-T04** | `PENDING_PM_DISPOSITION`| `RESOLVED_EXTRA_PROCESS`| `PM_APPROVE_EXTRA_PROCESS` | Production Manager (`ROLE_PM`) | Valid extra op sequence and instructions provided. | 1. Inserts `pm_dispositions` (`decision = 'EXTRA_PROCESS'`).<br/>2. Injects extra op (`is_extra_operation = true`, status `READY`).<br/>3. Sets `pieces.status = 'IN_PRODUCTION_EXTRA_PROCESS'`.<br/>4. Enqueues outbox notifications for RM and ECR. |
| **EX-T05** | `PENDING_PM_DISPOSITION`| `RESOLVED_SCRAP` | `PM_APPROVE_SCRAP` | Production Manager (`ROLE_PM`) | Scrap engineering rationale provided. | 1. Inserts `pm_dispositions` (`decision = 'REJECT_AND_RESTART'`).<br/>2. Sets `pieces.status = 'SCRAPPED'`.<br/>3. Enqueues `MATERIAL_REJECTION_NOTICE` for RM and `SCRAP_EXCEPTION` for ECR in `outbox_events`.<br/>4. Spawns replacement child piece in `AWAITING_MATERIAL`. |
| **EX-T06** | `RESOLVED_*` | `CLOSED` | `REMEDIATION_CERTIFIED` | System / PM | Remedial operation certified or replacement piece material issued. | Sets `qc_exceptions.status = 'CLOSED'`. Archives exception record. |
