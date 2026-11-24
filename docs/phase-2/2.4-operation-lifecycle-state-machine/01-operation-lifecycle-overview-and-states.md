# Part 2.4 – Operation Lifecycle State Machine
## Document 01: Operation Lifecycle Overview, Principles, and State Catalogue

---

### 1. Executive Summary & Objective

In the Velan Production platform, an **Operation** (`piece_operations`) represents an individual, ordered manufacturing or inspection routing step executed on a specific physical piece (e.g., Piece `SC002-1 SPG-1` – Sequence 10: CNC Facing & Centering; Sequence 20: CNC Turning; Sequence 30: Cylindrical Grinding).

The primary objective of **Part 2.4 – Operation Lifecycle State Machine** is to specify the complete, deterministic lifecycle of an individual operation instance from its creation via frozen process template snapshot through material gating, operator opening, dimensional measurement recording, independent Quality Control (QC) inspection, failure handling, rework loops, PM controls, and final completion.

#### Architectural Separation of Concerns
To ensure strict modularity and eliminate conceptual confusion, the system enforces a clear separation of lifecycle concerns:
* **Piece Lifecycle (Part 2.3):** Governs the physical piece instance (`pieces`), tracking its overall progression from raw material issuance (`AWAITING_MATERIAL`), active production (`IN_PRODUCTION`), quarantine holds (`ON_HOLD_QC_FAILED`), rework/extra process states, to final physical completion (`COMPLETED`) or retirement (`SCRAPPED`).
* **Operation Lifecycle (Part 2.4):** Governs a single routing step (`piece_operations`) on a piece, tracking its transition from `PENDING`, `READY`, `OPENED`, `QC_PENDING`, `QC_IN_PROGRESS`, to completion (`PASSED`, `CONCESSION_ACCEPTED`, `BYPASSED`) or failure (`FAILED`).
* **QC and Exception Lifecycle (Part 2.5):** Governs the non-conformance ticket (`qc_exceptions`), tolerance evaluations, and the formal PM disposition workflow (`pm_dispositions`).
* **Set Lifecycle (Part 2.2 / 2.3 / 2.6):** Governs the logical assembly unit (`assembled_sets`), tracking deterministic component aggregation (`PRODUCTION_COMPLETE`), final assembly verification (`FINAL_QC_PASSED`), packaging (`PACKING_CONFIRMED`), warehouse staging (`DISPATCH_PENDING`), and physical gate release (`DELIVERED`).

---

### 2. Core Architectural Principles & Invariants

The Operation Lifecycle State Machine strictly enforces six non-negotiable architectural principles established in Phase 1 and Part 2:

#### Principle 1: Strict Sequential Operation Activation
* An operation is never activated in parallel with its preceding or succeeding routing steps.
* **Material Gate for Sequence 10:** The first routing operation (sequence 10) can transition from `PENDING` to `READY` **only after** raw material readiness is confirmed for the parent SC product line item (`subcontract_products.material_ready = true`).
* **Sequential Progression for Sequence 20+:** Downstream operations (sequence 20, 30, etc.) remain strictly in `PENDING` and become `READY` **only sequentially after** the immediately preceding operation reaches an approved completed state: either `PASSED` (certified by QC) or `CONCESSION_ACCEPTED` (authorized by PM).
* **Zero Premature Readiness:** Operations are **never made `READY` simultaneously** across an entire route. Availability of raw material does not unlock sequence 20 or 30 before sequence 10 is executed and certified.
* **Measurement Entry Gate:** No operator measurement entry or job opening is permitted for any operation in `PENDING` status.

#### Principle 2: Role Boundary & Input Separation
* Machine Operators and QC Inspectors operate under strict role boundaries (`ROLE_OPERATOR` vs `ROLE_QC`).
* Operator measurements and QC measurements are stored in **separate, distinct database records** in `operation_measurements` tagged with `recorded_by_role`.
* **Zero Pre-filling:** QC measurement entry fields are **never pre-populated** with operator-submitted values. QC must perform genuine, independent physical measurements using calibrated instruments (`BR-032`).

#### Principle 3: Immutability of Submitted Data and Failure Records
* Once submitted by an operator (`QC_PENDING`), operator measurements are permanently locked and cannot be silently overwritten or modified.
* When QC records an out-of-tolerance reading resulting in `FAILED`, the inspection record and the failure verdict remain permanently immutable.
* If the PM authorizes a concession (`CONTINUE`), the failure is **never silently converted into a pass**. The operation status transitions to `CONCESSION_ACCEPTED`, retaining the underlying failed inspection record in audit history.
* If the PM authorizes rework (`RETURN_TO_EARLIER_OPERATION`), previous measurement sessions remain untouched; new measurements are recorded under an incremented iteration counter (`iteration_number = 2, 3...`).

#### Principle 4: Absence of Start/Stop Timers
* The system design explicitly avoids micro-managing operators with manual "Start" and "Stop" timer buttons.
* Instead, operational duration and queue lead times are tracked naturally via state transition timestamps: `ready_at`, `opened_at`, `submitted_at`, `qc_started_at`, and `completed_at` (`SRS FR-010`).

#### Principle 5: Point-in-Time Process Snapshot Protection
* When an SC is approved, each piece binds permanently to a frozen `process_template_snapshots` record (`pieces.snapshot_id`).
* Active routing operations in `piece_operations` are generated from this frozen snapshot.
* Subsequent modifications to master process templates by Engineering Designers do not alter in-flight operations, eliminating the risk of mid-production process corruption (`BR-018`).

#### Principle 6: Atomic Concurrency and Optimistic Locking
* Every operation entity contains an optimistic locking counter (`@Version Integer version`).
* Simultaneous conflicting shop-floor actions (e.g., two operators opening the same job, or PM editing routing while an operator submits data) are detected atomically and resolved deterministically without data corruption.

---

### 3. State Catalogue Evaluation and Justification

The user prompt requests an evaluation of potential operation states to determine which are required, eliminating redundancy and ambiguity:

| Candidate State | Verdict | Design Decision & Justification |
| :--- | :---: | :--- |
| `PENDING` | **Retained** | **Mandatory.** The initial state for all operations awaiting predecessor completion or material readiness. Guarantees sequential gating. |
| `READY` | **Retained** | **Mandatory.** Signals that all preconditions (material readiness for Op 1; QC pass of predecessor for Op N) are satisfied. The operation is actionable and appears on the operator's priority worklist. |
| `OPENED` | **Retained** | **Mandatory.** Signals that an operator has actively scanned or selected the job on their terminal. Captures `opened_at` timestamp. Prevents duplicate operator collision. |
| `SUBMITTED` | **Consolidated into `QC_PENDING`** | **Redundant as separate state.** When an operator submits measurements, the operation immediately enters the QC inspection backlog. Having both `SUBMITTED` and `QC_PENDING` creates an unnecessary intermediate step with no distinct business meaning. The system captures `submitted_at` timestamp and sets status directly to `QC_PENDING`. |
| `QC_PENDING` | **Retained** | **Mandatory.** The operation is waiting in the QC Inspector queue. Operator measurements are locked. Physical piece is staged in the inspection area. |
| `QC_IN_PROGRESS` | **Retained** | **Mandatory.** Signals that a QC Inspector has opened the inspection card and is actively entering verification readings. Captures `qc_started_at`. Prevents two QC inspectors from inspecting the same piece concurrently. |
| `PASSED` | **Retained** | **Mandatory.** Terminal successful state for standard execution. Certified within approved tolerances by QC. Triggers activation of sequence N+1 to `READY`. |
| `FAILED` | **Retained** | **Mandatory.** Terminal failure state pending PM review. QC recorded out-of-spec readings. Quarantines the piece into `ON_HOLD_QC_FAILED` and opens `qc_exceptions`. |
| `CONCESSION_ACCEPTED` | **Retained** | **Mandatory.** Terminal concession state. PM authorized `CONTINUE` with mandatory engineering justification. Distinguishes approved non-conformances from standard nominal passes. Triggers activation of sequence N+1 to `READY`. |
| `REWORK_REQUIRED` | **Rejected for Operation Entity** | **Belongs to Exception Entity.** Non-conformance status belongs to `qc_exceptions` (`status = 'PENDING_PM_DISPOSITION'`). On the operation entity, a failed step is `FAILED`. If PM rewinds routing, the target step re-enters `READY` under an incremented `iteration_number`. |
| `REWORK_IN_PROGRESS` | **Consolidated into `OPENED` with Iteration Flag** | **Redundant as separate enum.** Rework execution uses the standard execution lifecycle (`READY` $\rightarrow$ `OPENED` $\rightarrow$ `QC_PENDING` $\rightarrow$ `PASSED`) distinguished cleanly by `iteration_number > 1` and `is_rework = true`. This avoids duplicating the entire lifecycle with "REWORK_" prefixes. |
| `EXTRA_PROCESS_PENDING` / `EXTRA_PROCESS_IN_PROGRESS` | **Consolidated into Standard Lifecycle** | **Redundant as separate enums.** An extra process is simply a new operation inserted into `piece_operations` with `is_extra_operation = true`. It follows standard `READY` $\rightarrow$ `OPENED` $\rightarrow$ `QC_PENDING` $\rightarrow$ `PASSED`. |
| `BYPASSED` | **Retained** | **Mandatory.** PM explicitly authorized skipping this operation (e.g. redundant test or pre-machined raw stock) with mandatory reason. Unlocks sequence N+1 without measurement entry. |
| `CANCELLED` | **Retained** | **Mandatory.** Operation retired without execution due to parent piece being scrapped (`REJECT_AND_RESTART`). (Note: Whole-contract commercial cancellation is kept as a future consideration pending formal business requirements). |

---

### 4. Final Approved Operation State Catalogue

Based on the evaluation above, the Operation Lifecycle State Machine is defined across **ten robust, non-overlapping states**:

```
                       ┌───────────────────────────────┐
                       │            PENDING            │
                       └───────────────┬───────────────┘
                                       │ (Material Ready / Predecessor Passed)
                                       ▼
                       ┌───────────────────────────────┐
         ┌────────────►│             READY             │◄─────────────┐
         │             └───────────────┬───────────────┘              │
         │ (PM Rewind                  │ (Operator Opens Job)         │ (PM Reopen)
         │  Iteration+1)               ▼                              │
         │             ┌───────────────────────────────┐              │
         │             │            OPENED             │              │
         │             └───────────────┬───────────────┘              │
         │                             │ (Operator Submits Data)      │
         │                             ▼                              │
         │             ┌───────────────────────────────┐              │
         │             │          QC_PENDING           │              │
         │             └───────────────┬───────────────┘              │
         │                             │ (QC Inspector Claims Job)    │
         │                             ▼                              │
         │             ┌───────────────────────────────┐              │
         │             │        QC_IN_PROGRESS         │              │
         │             └───────┬───────────────┬───────┘              │
         │                     │               │                      │
         │       (QC Approves) │               │ (QC Fails)           │
         │                     ▼               ▼                      │
         │        ┌─────────────────┐    ┌─────────────────┐          │
         │        │     PASSED      │    │     FAILED      │          │
         │        └────────┬────────┘    └────────┬────────┘          │
         │                 │                      │                   │
         │                 │                      │ (PM Concession)   │
         │                 │                      ▼                   │
         │                 │             ┌─────────────────┐          │
         │                 │             │CONCESSION_ACCEPT│          │
         │                 │             └────────┬────────┘          │
         │                 │                      │                   │
         └─────────────────┴──────────┬───────────┴───────────────────┘
                                      │
                   [ Unlocks Next Sequential Op to READY ]
```

#### Detailed State Specifications

| State Code | State Name | Operational Meaning | Entry Conditions | Exit Conditions | Category | Causing Roles / Triggers |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `PENDING` | **Pending Eligibility** | Operation is queued in the piece's routing sequence. Not eligible to start. | Piece generated at SC intake; OR child operation created from template. | Predecessor operation passes QC (`PASSED`) or accepted on concession (`CONCESSION_ACCEPTED`); OR parent product material ready (for Op 10). | Inactive / Gated | System (Intake generator) |
| `READY` | **Ready for Execution** | Operation is eligible for physical shop-floor machining. Visible on assigned operator/station worklists. | Predecessor completed successfully; OR material readiness confirmed for Op 10; OR PM rewinds routing; OR PM adds extra op. | Operator selects/scans piece and opens job card on terminal; OR PM bypasses step. | Active / Actionable | System (Gating trigger); PM (Rewind/Extra) |
| `OPENED` | **Opened by Operator** | Operator has opened the operation job card on their terminal. Physical setup, machining, and measurement entry in progress. | Operator opens an eligible `READY` operation. `opened_at` is stamped. | Operator completes measurements and submits; OR operator cancels/backs out before entering data. | Active / In-Progress | Machine Operator (`ROLE_OPERATOR`) |
| `QC_PENDING` | **Submitted / Awaiting QC** | Machining is completed. Operator measurements are submitted and locked. Piece is staged in the inspection queue. | Operator clicks "Submit Operation Measurements" on the entry grid. `submitted_at` is stamped. | QC Inspector claims piece and opens inspection interface. | Quarantined for QC | Machine Operator (`ROLE_OPERATOR`) |
| `QC_IN_PROGRESS` | **QC Inspection Active** | QC Inspector has claimed the piece and is actively recording independent verification readings against tolerances. | QC Inspector opens the inspection card from the QC queue. `qc_started_at` is stamped. | QC Inspector submits inspection verdict (`PASSED` or `FAILED`). | Active / In-Progress | QC Inspector (`ROLE_QC`) |
| `PASSED` | **Quality Certified** | QC Inspector verified all parameters within approved nominal/tolerance limits and certified compliance. | QC Inspector submits inspection with `verdict = 'PASSED'`. `completed_at` is stamped. | *Terminal for this execution.* (Can only be reopened by PM with mandatory reason). | Completed / Successful | QC Inspector (`ROLE_QC`) |
| `FAILED` | **Quality Non-Conforming** | QC Inspector recorded out-of-tolerance readings resulting in non-conformance. Piece is quarantined on hold. | QC Inspector submits inspection with `verdict = 'FAILED'`. `completed_at` is stamped. | PM reviews exception and issues binding disposition (`CONTINUE`, `RETURN`, `EXTRA`, `SCRAP`). | Completed / Non-Conforming | QC Inspector (`ROLE_QC`) |
| `CONCESSION_ACCEPTED` | **Accepted on Concession** | QC inspection failed, but PM authorized progression without rework based on documented engineering justification. | PM approves `CONTINUE` disposition with mandatory justification. `completed_at` is stamped. | *Terminal for this execution.* Unlocks downstream routing. | Completed / Concession | Production Manager (`ROLE_PM`) |
| `BYPASSED` | **Administratively Bypassed** | Operation was formally skipped by the PM without shop-floor execution. No measurements recorded. | PM authorizes bypass with mandatory engineering reason. `completed_at` is stamped. | *Terminal for this execution.* Unlocks downstream routing. | Completed / Skipped | Production Manager (`ROLE_PM`) |
| `CANCELLED` | **Cancelled** | Operation was permanently retired without completion because the parent physical piece was scrapped (`REJECT_AND_RESTART`). | Parent piece scrapped via PM `REJECT_AND_RESTART`. | *Terminal.* Immutable historical record. | Retired / Cancelled | System / PM |

---

### 5. Operation Entity Attribute Schema Mapping

In `piece_operations`, these lifecycle states and their associated timing/audit properties map to the following concrete database attributes:

```
+-----------------------------------------------------------------------------------------+
| ENTITY: piece_operations                                                                |
+-----------------------------------------------------------------------------------------+
| id                     UUID PK            Internal unique identifier                    |
| piece_id               UUID FK (pieces)   Parent physical piece                         |
| sequence_order         INTEGER NOT NULL   Routing sequence (10, 20, 30...)              |
| operation_name         VARCHAR(150) NOT   Descriptive name (e.g. CNC Milling)           |
| processing_route       VARCHAR(50) NOT    'IN_HOUSE' or 'VENDOR'                        |
| station_id             VARCHAR(100) NULL  Shop-floor work station ID                    |
| assigned_operator_id   UUID FK (users)    Designated machine operator                   |
| assigned_qc_id         UUID FK (users)    Claiming QC inspector (when QC_IN_PROGRESS)   |
| is_extra_operation     BOOLEAN DEFAULT F  True if dynamically inserted by PM            |
| is_bypassed            BOOLEAN DEFAULT F  True if skipped via PM bypass authorization   |
| bypass_reason          TEXT NULL          Mandatory PM reason if bypassed               |
| iteration_number       INTEGER DEFAULT 1  Current execution iteration (1=orig, 2+=rework|
| ready_at               TIMESTAMPTZ NULL   Timestamp became eligible to start            |
| opened_at              TIMESTAMPTZ NULL   Timestamp operator opened job card            |
| submitted_at           TIMESTAMPTZ NULL   Timestamp operator submitted measurements     |
| qc_started_at          TIMESTAMPTZ NULL   Timestamp QC claimed and opened inspection    |
| completed_at           TIMESTAMPTZ NULL   Timestamp certified (Passed/Concession/Bypass)|
| version                INTEGER NOT NULL   Optimistic locking version counter            |
| status                 VARCHAR(50) NOT    State enum (PENDING, READY, OPENED,           |
|                                           QC_PENDING, QC_IN_PROGRESS, PASSED, FAILED,   |
|                                           CONCESSION_ACCEPTED, BYPASSED, CANCELLED)     |
+-----------------------------------------------------------------------------------------+
```
