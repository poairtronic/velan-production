# Part 2.3 – Piece Lifecycle State Machine
## Document 01: Piece Lifecycle Overview, Principles, and State Catalogue

---

### 1. Executive Summary & Objective

In the Velan Production application, the **Piece** represents the physical unit of shop-floor manufacturing—an actual, serialized component of metal (e.g., Flange `ARG`, Spindle `SPG`, Valve Body `VBM`, Jig Foot `JIG`) machined, measured, inspected, and eventually assembled into a complete valve set.

The primary objective of **Part 2.3 – Piece Lifecycle State Machine** is to specify the complete, deterministic lifecycle of physical pieces from their initial creation at Size Card (SC) intake to their final completion or retirement (scrapping). This state machine governs:
1. Material readiness gating before machining.
2. Sequential operation progression and measurement recording.
3. Quality Control (QC) failure holds and Project Manager (PM) dispositions.
4. Rework execution iteration tracking.
5. Scrap lineage and replacement piece creation.
6. Aggregation into parent set completion and dispatch readiness.

---

### 2. Domain Definition & Identity Boundaries

#### 2.1 The Concept of a Physical Piece
* A **Piece** is not an abstract concept or an anonymous bulk tally. It is an individual physical part with an immutable identity stamped or tagged on the shop floor.
* A Piece is derived from a **BOM Line** (`bom_lines`) belonging to a specific catalog product, ordered under a specific subcontract product line item (`subcontract_products`).
* A Piece is deterministically bound to exactly one **Assembled Set** (`assembled_sets`) at the moment of intake.

#### 2.2 Deterministic Set Membership Invariant
* **Pre-Allocation at Intake:** When an SC is approved, the system generates the exact required number of physical pieces based on the target set quantity multiplied by the BOM component ratios (e.g., 4 sets of Gate Valves requiring 2 Spindles each creates exactly 8 Spindle pieces: 2 dedicated to Set #1, 2 to Set #2, 2 to Set #3, and 2 to Set #4).
* **No Late-Stage Pairing:** Pieces are never manufactured as unassigned floating inventory and arbitrarily paired into sets at packing. Every piece knows its target Set ID and Set Sequence Number from inception (`BR-007, BR-008`).
* **Independent Progress:** Sibling pieces within the same set progress independently through their respective routing operations. For example, Set #1's Flange can complete all milling operations while Set #1's Spindle is still undergoing turning.

---

### 3. Core Implementation and Design Principles

The design of the Piece Lifecycle State Machine strictly enforces five foundational architectural principles, including three mandatory implementation invariants:

#### Principle 1: Consistency of Lifecycle Status and Milestone Flags
* The lifecycle status enum (`pieces.status`) and all boolean milestone flags across the hierarchy (`subcontract_products.material_ready`, `assembled_sets.is_production_complete`, `assembled_sets.is_final_qc_passed`, `assembled_sets.is_packed`, `assembled_sets.is_delivered`) must remain mathematically and logically consistent at all times.
* A piece cannot enter `IN_PRODUCTION` if its parent product line item has `material_ready = false`.
* A parent set cannot reach `is_production_complete = true` unless **100% of its pre-allocated pieces** have reached `status = 'COMPLETED'`.
* A set cannot have `is_final_qc_passed = true` if any piece is in `ON_HOLD_QC_FAILED`, `IN_PRODUCTION_REWORK`, or `AWAITING_MATERIAL`.
* No database update or API response may present contradictory states (e.g., status `COMPLETED` while an operation remains `FAILED` or `OPENED`).

#### Principle 2: Monotonically Incrementing Rework Iterations
* Every time a piece undergoes rework—whether rewound to an earlier operation (`RETURN_TO_EARLIER_OPERATION`) or undergoing an ad-hoc extra operation (`EXTRA_PROCESS`)—the execution iteration counter must strictly increment (`iteration_number = 2, 3...`).
* **Zero Measurement Overwrites:** Prior operator measurements, prior QC inspection records, and prior failure verdicts are **never overwritten, modified, or deleted**.
* New measurements recorded during rework are stored in distinct measurement records tagged with the new `iteration_number`. This provides an unbroken, auditable history of non-conformance and subsequent remediation (`BR-032, BR-034`).

#### Principle 3: Quantity Conservation and Replacement Integrity
* When a non-conforming piece is scrapped via PM disposition (`REJECT_AND_RESTART`), the replacement piece must preserve the exact component role and BOM ratio of the parent set.
* **No Set Quantity Inflation:** Spawning a replacement piece replaces the retired parent 1:1 within the designated set slot. It does **not** increase the target set quantity of the SC or the required BOM component demand (`BR-005, BR-006, BR-012, BR-013`).
* **Lineage Preservation:** The original scrapped piece transitions permanently to `SCRAPPED` (retaining its serial number and full failure history). The replacement piece is assigned an incremented replacement index (e.g., `-R1`), links directly to `pieces.replaced_piece_id`, and inherits the exact `assembled_set_id` and `set_sequence_number` of its scrapped parent.
* **Replacement Material Gate:** Instantiating a replacement piece does **not** automatically unlock machining. The replacement piece enters `AWAITING_MATERIAL` until RM Stores issues replacement raw stock against the ECR ticket.

#### Principle 4: Parallel Non-Blocking Execution across Sibling Components
* Pieces belonging to different components or different sets progress through shop-floor operations in parallel without cross-blocking.
* If Piece A (e.g., Set #1 Valve Body) fails QC and enters `ON_HOLD_QC_FAILED`, sibling pieces in the same set (e.g., Set #1 Flanges and Spindles) continue their machining operations unhindered.
* Cross-component synchronization occurs strictly at the **Set Assembly Gate**: the set cannot be assembled or submitted for Final QC until all constituent pieces reach `COMPLETED`.

#### Principle 5: Immutable Auditability
* Every transition of a physical piece between lifecycle states triggers an append-only audit event in `audit_logs`.
* Audit records capture `event_timestamp`, `user_id`, `user_role`, `action_type`, `previous_state`, `new_state`, and any required justification notes (e.g., PM concession rationale).

---

### 4. Exhaustive Piece State Catalogue

The lifecycle of an individual physical piece is modeled across eight mutually exclusive, well-defined states:

```
[ CREATED ] ──► [ AWAITING_MATERIAL ] ──► [ IN_PRODUCTION ] ──► [ COMPLETED ]
                       ▲                         │
                       │                         ▼
                       │               [ ON_HOLD_QC_FAILED ]
                       │                   │         │
                       │                   ▼         ▼
                       │          [ IN_PRODUCTION_REWORK ] / [ IN_PRODUCTION_EXTRA_PROCESS ]
                       │                               │
                       └─────────────── [ SCRAPPED ] ◄─┘ (spawns replacement to AWAITING_MATERIAL)
```

| State Code | State Name | Category | Detailed Description & Business Meaning |
| :--- | :--- | :--- | :--- |
| `CREATED` | **Created / Initialized** | Transient / Initial | The physical piece entity has been generated in the database upon Size Card intake and approval. The piece is assigned its preliminary identity and pre-allocated to its specific Set and BOM slot. Routing operations have been generated from the frozen process template snapshot. |
| `AWAITING_MATERIAL` | **Awaiting Raw Material** | Gated / Blocked | The piece exists and is bound to its set, but physical machining is strictly blocked because raw material has not yet been issued by RM (V-SMART) for this product line (`subcontract_products.material_ready = false`). Also applies to newly spawned replacement pieces awaiting replacement material issuance from RM. Operators cannot open jobs or enter measurements. |
| `IN_PRODUCTION` | **In Active Production** | Active Execution | Raw material readiness has been confirmed (`material_ready = true`). The first routing operation (or current downstream operation) is `READY` or `OPENED`. Operators actively perform machining, turning, milling, drilling, and record measurement readings against approved tolerances. |
| `ON_HOLD_QC_FAILED` | **On Hold – QC Failed** | Suspended / Hold | A completed operation on this piece received an official `FAILED` verdict during independent QC inspection. Downstream operations are strictly locked. The piece is physically and logically placed on hold in the shop-floor quarantine zone, awaiting a binding resolution from the Production Manager (PM). |
| `IN_PRODUCTION_REWORK` | **In Rework (Routing Rewind)** | Active Remediation | The PM issued a `RETURN_TO_EARLIER_OPERATION` disposition. The piece has been returned to a designated earlier operation in the routing. The rework operation iteration has been incremented (`iteration_number = 2, 3...`). The operator performs remedial machining according to PM instructions. |
| `IN_PRODUCTION_EXTRA_PROCESS`| **In Extra Remedial Process** | Active Remediation | The PM issued an `EXTRA_PROCESS` disposition. An ad-hoc remedial operation (e.g., sequence 25: Skim Grinding, `is_extra_operation = true`) has been dynamically inserted into the piece's routing. The piece undergoes this specialized process and must pass a dedicated QC inspection before returning to standard routing. |
| `COMPLETED` | **Production Completed** | Terminal (Piece Level) | The physical piece has successfully finished 100% of its required sequential routing operations (each certified as either `PASSED` or accepted on concession as `CONCESSION_ACCEPTED`). Physical fabrication and individual component inspections are finished. The piece is transferred to the assembly staging area, awaiting completion of sibling pieces to form a complete set. |
| `SCRAPPED` | **Scrapped / Defective Retired** | Terminal / Retired | The PM issued a `REJECT_AND_RESTART` disposition because the defect cannot be reworked or accepted on concession. The physical piece is permanently retired, stamped rejected, and removed from the active line. Historical measurements and failure records remain permanently preserved. A replacement child piece is spawned to maintain set allocation. |

---

### 5. State Classification Matrix

To ensure rigorous handling by the backend business logic, UI views, and access controls, states are classified into distinct functional groups:

| State | Execution Allowed? | Measurements Acceptable? | Quarantine / Blocked? | Terminal? | Can Transition To |
| :--- | :---: | :---: | :---: | :---: | :--- |
| `CREATED` | No | No | Yes (Not launched) | No | `AWAITING_MATERIAL`, `IN_PRODUCTION` |
| `AWAITING_MATERIAL` | No | No | Yes (Material gate) | No | `IN_PRODUCTION` |
| `IN_PRODUCTION` | **Yes** | **Yes** | No | No | `ON_HOLD_QC_FAILED`, `COMPLETED` |
| `ON_HOLD_QC_FAILED` | No | No | **Yes (QC failure hold)** | No | `IN_PRODUCTION`, `IN_PRODUCTION_REWORK`, `IN_PRODUCTION_EXTRA_PROCESS`, `SCRAPPED` |
| `IN_PRODUCTION_REWORK` | **Yes** | **Yes** (New iteration)| No | No | `ON_HOLD_QC_FAILED`, `IN_PRODUCTION`, `COMPLETED` |
| `IN_PRODUCTION_EXTRA_PROCESS`| **Yes** | **Yes** (Ad-hoc step)| No | No | `ON_HOLD_QC_FAILED`, `IN_PRODUCTION`, `COMPLETED` |
| `COMPLETED` | No (Finished) | No (Locked) | No | **Yes** (At piece level)| *Participates in Set Assembly* |
| `SCRAPPED` | No (Retired) | No (Immutable) | **Yes (Physical scrap)**| **Yes** (Permanent) | *None (Terminal immutable state)* |

---

### 6. Alignment with Operational and Set Entities

The Piece state does not exist in isolation; it coordinates tightly with child operations and parent sets:

```
+-----------------------------------------------------------------------------------+
| SUBCONTRACT PRODUCT: material_ready = true                                        |
+-----------------------------------------------------------------------------------+
       │
       ▼
+-----------------------------------------------------------------------------------+
| PIECE: IN_PRODUCTION                                                              |
|   ├── Op 10 (CNC Turning): PASSED                                                |
|   ├── Op 20 (Drilling):    PASSED                                                |
|   └── Op 30 (Grinding):    READY / OPENED                                        |
+-----------------------------------------------------------------------------------+
       │ (When Op 30 passes)
       ▼
+-----------------------------------------------------------------------------------+
| PIECE: COMPLETED                                                                  |
+-----------------------------------------------------------------------------------+
       │ (When 100% of pre-allocated sibling pieces reach COMPLETED)
       ▼
+-----------------------------------------------------------------------------------+
| ASSEMBLED SET: PRODUCTION_COMPLETE (is_production_complete = true)                |
|   └── Next Step: Final QC Inspection (is_final_qc_passed = true)                 |
|         └── Next Step: Packing Confirmation (is_packed = true)                   |
|               └── Next Step: Dispatch Confirmation (is_delivered = true)         |
+-----------------------------------------------------------------------------------+
```

1. **Piece vs. Operation Alignment:**
   - A piece can only be in `IN_PRODUCTION`, `IN_PRODUCTION_REWORK`, or `IN_PRODUCTION_EXTRA_PROCESS` if exactly one of its operations is in `READY`, `OPENED`, `SUBMITTED`, or `QC_PENDING`.
   - If an operation fails QC (`FAILED`), the piece **must immediately transition** to `ON_HOLD_QC_FAILED`.
   - If all operations reach `PASSED` or `CONCESSION_ACCEPTED`, the piece **must immediately transition** to `COMPLETED`.
2. **Piece vs. Set Alignment:**
   - When a piece transitions to `COMPLETED`, the system triggers an asynchronous in-process set completeness check.
   - If all pre-allocated pieces for that set are `COMPLETED`, the set transitions to `PRODUCTION_COMPLETE` (`is_production_complete = true`).
   - If a piece is `SCRAPPED`, its set cannot progress to `PRODUCTION_COMPLETE` until the replacement child piece reaches `COMPLETED`.
