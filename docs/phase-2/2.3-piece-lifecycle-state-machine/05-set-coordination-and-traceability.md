# Part 2.3 – Piece Lifecycle State Machine
## Document 05: Set Coordination, Assembly Milestones, and Traceability Matrix

---

### 1. Piece-to-Set Aggregation Mechanics

While fabrication, machining, and intermediate quality inspections occur strictly at the individual **Piece** level, commercial delivery occurs strictly at the **Assembled Set** level. The Piece Lifecycle State Machine coordinates continuously with the parent Set entity to drive manufacturing milestones:

```
+-----------------------------------------------------------------------------------------+
| ASSEMBLED SET: Set #1 (SET-SC002-01)                                                    |
| Current Status: BUILDING | is_production_complete: false                                |
+-----------------------------------------------------------------------------------------+
  │
  ├── Piece 1: SC002-1 VBM (Valve Body)   ──► status: COMPLETED
  ├── Piece 2: SC002-1 ARG (Flange)       ──► status: COMPLETED
  ├── Piece 3: SC002-1 SPG-1 (Spindle 1)  ──► status: COMPLETED
  ├── Piece 4: SC002-1 SPG-2 (Spindle 2)  ──► status: COMPLETED
  ├── Piece 5: SC002-1 JIG-1 (Jig Foot 1) ──► status: COMPLETED
  ├── Piece 6: SC002-1 JIG-2 (Jig Foot 2) ──► status: COMPLETED
  └── Piece 7: SC002-1 JIG-3 (Jig Foot 3) ──► status: COMPLETED
  │
  ▼ [Automated Aggregation Evaluation Triggered upon Last Piece Transition]
+-----------------------------------------------------------------------------------------+
| ASSEMBLED SET: Set #1                                                                   |
| New Status: PRODUCTION_COMPLETE | is_production_complete: true                          |
| production_completed_at: NOW()                                                          |
| Next Operational Step: Final QC Inspection                                              |
+-----------------------------------------------------------------------------------------+
```

#### 1.1 The Deterministic Completeness Invariant (`BR-008`)
* **Strict 100% Rule:** A set transitions to `PRODUCTION_COMPLETE` (`is_production_complete = true`) if and only if **every pre-allocated component piece bound to that set** has reached `status = 'COMPLETED'`.
* **Zero Partial Completion:** If 6 out of 7 pieces are completed, but 1 piece is in `IN_PRODUCTION_REWORK` or `AWAITING_MATERIAL` (e.g. awaiting replacement stock after a scrap event), the set status remains strictly **`BUILDING`** (`is_production_complete = false`).
* **Scrapped Piece Exclusion:** When a piece is scrapped, its pre-allocation in `set_component_allocations` is transferred to the replacement child piece. The set’s completeness query evaluates the active child piece, **never the retired scrapped parent**.

#### 1.2 Automated Completeness Evaluation Logic
Every time an individual piece transitions to `COMPLETED` (via `FINAL_OPERATION_PASSED` or PM `CONTINUE` disposition on the final step), the backend executes an atomic in-process evaluation:

```sql
SELECT COUNT(*) 
FROM pieces p
JOIN set_component_allocations sca ON p.id = sca.piece_id
WHERE sca.set_id = :set_id
  AND p.status != 'COMPLETED';
```

* If `COUNT(*) == 0`:
  1. Sets `assembled_sets.status = 'PRODUCTION_COMPLETE'`.
  2. Sets `assembled_sets.is_production_complete = true`.
  3. Records timestamp `production_completed_at = NOW()`.
  4. Generates a notification to the QC Inspector worklist: *"Set {set_identifier} is production complete and ready for Final Set QC Inspection."*
  5. Logs audit event `SET_PRODUCTION_COMPLETE`.
* If `COUNT(*) > 0`:
  - Set remains `BUILDING`. No milestone transition occurs.

---

### 2. Independent Component Progression vs. Set Gating

The architecture balances two complementary operational requirements:
1. **Shop-Floor Flexibility (Piece Independence):** Operators on different work centres (lathes, CNC milling machines, grinding stations, drills) work at varying speeds without blocking each other. A delay on the spindle does not idle the flange operator (`BR-009`).
2. **Quality & Assembly Discipline (Set Gating):** No valve set can be partially assembled, boxed, or shipped with missing components (`BR-010, BR-045`).

#### Detailed Execution Walkthrough
Consider an order for 2 complete Valve Sets (Set #1 and Set #2) where each set requires 1 Valve Body (`VBM`) and 1 Spindle (`SPG`):

```
Time T0: SC Approved. 4 physical pieces generated.
         Set #1: Piece P1 (VBM), Piece P2 (SPG)
         Set #2: Piece P3 (VBM), Piece P4 (SPG)

Time T1: RM confirms material readiness for line.
         P1, P2, P3, P4 transition to IN_PRODUCTION. Op 10 becomes READY for all 4.

Time T2: P1 (Set #1 VBM) finishes Op 10, 20, 30. Passes QC. P1 -> COMPLETED.
         P2 (Set #1 SPG) is undergoing Op 20 Milling.
         Set #1 remains BUILDING.

Time T3: P2 (Set #1 SPG) finishes Op 20 and Op 30. Passes QC. P2 -> COMPLETED.
         Set #1 has 100% pieces COMPLETED.
         Set #1 transitions to PRODUCTION_COMPLETE (is_production_complete = true).

Time T4: Set #1 undergoes Final QC Inspection. QC Inspector approves.
         Set #1 transitions to FINAL_QC_PASSED (is_final_qc_passed = true).

Time T5: Packaging team packs Set #1. Set #1 transitions to PACKING_CONFIRMED
         and DISPATCH_PENDING (is_packed = true).

Time T6: P3 (Set #2 VBM) fails QC at Op 20. Placed in ON_HOLD_QC_FAILED.
         P4 (Set #2 SPG) continues machining unhindered.
         Set #2 remains strictly BUILDING.

Time T7: Dispatch employee confirms vehicle handover for Set #1.
         Set #1 transitions to DELIVERED (is_delivered = true).
         PARTIAL DISPATCH ACHIEVED: Set #1 is shipped while Set #2 remains in production.
```

---

### 3. Partial Dispatch Rules and the Delivery Boundary

The state machine strictly distinguishes between physical release from the factory and downstream commercial billing:

1. **Partial Dispatch for Complete Sets Only (`BR-010, BR-048`):**
   - Partial dispatch is permitted at the **Set level**, never at the piece level.
   - If an SC orders 10 sets, and 3 sets have reached `DISPATCH_PENDING` (`is_final_qc_passed = true`, `is_packed = true`), those 3 complete sets may be released and marked `DELIVERED`.
   - The remaining 7 sets stay in active production without invalidating the contract.
2. **Prohibited Incomplete Dispatch:**
   - A set missing even a single washer, flange, or spindle cannot be packed, staged, or dispatched.
3. **Dispatch Confirmation Records Delivery (`BR-046, BR-047`):**
   - Packing confirmation moves status to `DISPATCH_PENDING`.
   - The physical gate exit and vehicle handover explicitly recorded by the dispatch employee transitions status to `DELIVERED`.
4. **Billing Boundary (`BR-056`):**
   - Velan Production records physical fabrication, quality verification, and gate departure.
   - Invoicing, accounts receivable, and customer billing remain strictly external in enterprise financial software.

---

### 4. Comprehensive Phase 1 Traceability Matrix

Every Phase 1 business rule and functional requirement governing piece lifecycles, exceptions, and set coordination has been verified against this state machine design:

| Rule ID | Phase 1 Requirement Summary | Part 2.3 State Machine Implementation | Verification & Compliance Result |
| :--- | :--- | :--- | :--- |
| **BR-004** | Preserve PO → SC → Product → BOM → Set → Piece hierarchy | Modeled in piece creation and set pre-allocation manifest. | **100% Compliant.** Complete hierarchy maintained without skipping levels. |
| **BR-007** | Unique traceable piece identity & set association | `pieces` stores SC ID, product line ID, set ID, set sequence, component code, unit index, replacement sequence. | **100% Compliant.** Separately models all identity fields; printed syntax format configurable. |
| **BR-008** | Set complete only when 100% of pieces finish operations & pass final QC | Aggregation gate evaluates 100% of pre-allocated pieces reaching `COMPLETED` before setting `is_production_complete = true`. | **100% Compliant.** Set completeness strictly gated on all BOM components. |
| **BR-009** | Individual pieces and sets progress independently | State machine runs per piece; pieces within a set do not cross-block during routing. | **100% Compliant.** Sibling components execute in parallel unblocked. |
| **BR-010** | Partial dispatch allowed only for complete sets | Dispatch confirmation requires set status `DISPATCH_PENDING` with all milestone booleans true. | **100% Compliant.** Partial set dispatch permitted; partial piece dispatch blocked. |
| **BR-011** | Component retains its own operation and measurement history | `operation_measurements` and `measurement_values` stored per piece operation. | **100% Compliant.** Complete historical record maintained per piece. |
| **BR-012** | Scrapped piece replacement preserves identity & lineage | Scrapped piece marked `SCRAPPED`; child piece spawned with incremented replacement sequence and linked via `piece_replacement_lineage`. | **100% Compliant.** Parent-child genealogy fully auditable; IDs never reused or erased. |
| **BR-013** | Quantity conservation across all workflow states | Scrapping and replacement maintains exact 1:1 set slot ratio; replacement does not increase set quantity. | **100% Compliant.** Total active component demand equals target sets × BOM ratio. |
| **BR-014** | Piece cannot be active in incompatible workflow states | Strict state enum prevents dual states (e.g. cannot be `COMPLETED` and `ON_HOLD_QC_FAILED`). | **100% Compliant.** Mutually exclusive state machine design. |
| **BR-021** | Cannot start machining until material readiness confirmed | `AWAITING_MATERIAL` state blocks barcode job opening until `subcontract_products.material_ready = true`. | **100% Compliant.** Hard operational material gate enforced. |
| **BR-032** | Operator and QC measurements are separate records | Separate rows in `operation_measurements` tagged with `recorded_by_role`. | **100% Compliant.** Independent input sessions preserved. |
| **BR-033** | QC failure notifies PM; PM decides disposition | Operation failure sets piece to `ON_HOLD_QC_FAILED`, opens `qc_exceptions`, pushes SSE alert to PM. | **100% Compliant.** Exclusive PM authority over exception resolution. |
| **BR-034** | PM concession override requires justification & retains original data | `CONTINUE` disposition requires mandatory justification, marks op `CONCESSION_ACCEPTED`, retains original measurements and failure verdict. | **100% Compliant.** Zero silent passes; failure preserved permanently. |
| **BR-035** | Rework may route to earlier operation with incremented iteration | `RETURN_TO_EARLIER_OPERATION` rewinds routing and increments `iteration_number` ($N+1$), preserving prior measurements. | **100% Compliant.** Rework iteration strictly tracked without data loss. |
| **BR-036** | Reworked piece rejoins normal sequence after passing QC | After passing rework QC, piece rejoins downstream operation following the failure point. | **100% Compliant.** Rejoining routing sequence automated. |
| **BR-038** | Extra process requires QC inspection | Ad-hoc operation (`is_extra_operation = true`) must be inspected and passed by QC before rejoining. | **100% Compliant.** Dedicated QC gate on remedial steps. |
| **BR-039** | ECR owns costing; Production sends outbox exception | Enqueues `SCRAP_EXCEPTION` in `outbox_events` for ECR intake; does not calculate costing. | **100% Compliant.** Clean transactional outbox boundary. |
| **BR-046** | Packing confirmation changes status to Dispatch Pending, not Delivered | Packing moves set status to `PACKING_CONFIRMED` and `DISPATCH_PENDING` (`is_packed = true`). | **100% Compliant.** Packing physically separated from delivery. |
| **BR-047** | Delivered requires actual dispatch confirmation by dispatch employee | Dispatch employee confirms gate pass, moving set status to `DELIVERED` (`is_delivered = true`). | **100% Compliant.** Single final delivery milestone. |

---

### 5. Architectural Integrity and Verification Summary

1. **Alignment with Part 2.1 (System Architecture):**
   - The state machine executes within the `execution` and `exception` modules of the Spring Boot Modular Monolith.
   - Cross-module communication (e.g. execution notifying dispatch upon set completeness) utilizes Spring In-Process Application Events (`ApplicationEventPublisher`), completely adhering to the **No Redis / No RabbitMQ / No Kafka** architectural constraint (`ADR-02`).
2. **Alignment with Part 2.2 (Domain and Data Model):**
   - All state transitions operate strictly against the approved database entities (`pieces`, `piece_operations`, `operation_measurements`, `qc_exceptions`, `pm_dispositions`, `assembled_sets`, `set_component_allocations`).
   - Line-item uniqueness (`UNIQUE (sc_id, line_number)`) and discrete set milestone flags (`is_production_complete`, `is_final_qc_passed`, `is_packed`, `is_delivered`) are respected and reinforced.
3. **No Code or Schema Violations:**
   - No application code, SQL migrations, or database tables have been created.
   - All design specifications are preserved in Markdown documentation and Mermaid diagrams.
