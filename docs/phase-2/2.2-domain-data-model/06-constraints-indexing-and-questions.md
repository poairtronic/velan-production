# Part 2.2 – Domain and Data Model Design
## Document 06: Database Constraints, Indexing Strategy, and Review Traceability

### 1. Database Integrity Constraints Recommendations

To prevent corrupted records, duplicate piece generation, and illegal status progressions at the database engine level, the following constraints are designed for PostgreSQL (Neon):

#### 1.1 Unique Constraints (Data Deduplication)
* `purchase_orders(po_number)`: Prevents duplicate commercial orders.
* `subcontracts(sc_number)`: Guarantees exact 1:1 mapping with RM (V-SMART) contracts.
* `subcontract_products(sc_id, line_number)`: Enforces line-item uniqueness per Size Card. Because subcontracts may order the same manufactured catalog product across multiple line items (with different delivery dates, target set quantities, or engineering configurations), uniqueness is defined on `(sc_id, line_number)` rather than `(sc_id, product_id)`.
* `products(product_code)`: Prevents duplicate engineering catalog entries.
* `process_templates(template_code, version)`: Enforces unique versioning per template.
* `pieces(serial_number)`: **Absolute Invariant:** Every manufactured physical piece must possess a system-wide unique physical serial number.
* `pieces(assembled_set_id, component_code, component_sequence_in_set, replacement_sequence)`: **Deterministic Set Slot Invariant:** Ensures that within a specific Set, exactly one piece occupies each allocated component slot (unless a replacement sequence is incremented upon scrap).
* `set_component_allocations(piece_id)`: **Critical Assembly Invariant:** A physical piece can be allocated to **at most one** assembled set. This database constraint physically prevents double-allocating a spindle or flange to multiple valve sets.
* `assembled_sets(sc_product_id, set_sequence_number)`: Enforces deterministic set numbering (`Set #1, Set #2...`) per SC product.
* `packing_records(set_id)`: A set can have only one confirmed packing record.
* `drawing_documents(r2_file_key)`: Prevents duplicate storage pointers in Cloudflare R2.
* `outbox_events(id)`: Acts as the unique idempotency key when communicating with ECR.
* `departments(department_code)`: Prevents duplicate department master codes.
* `machines(machine_code)`: Enforces unique identification of shop-floor machines and stations.
* `users(employee_code)` and `users(username)`: Guarantees unique user accounts and payroll identification.
* `roles(role_code)`: Enforces unique system role codes.
* `user_roles(user_id, role_id)`: Prevents assigning the same role twice to a user.

#### 1.2 Referential Integrity and Delete Restrictions
* **Zero Cascade Deletes on Production Assets:** All foreign keys pointing to `purchase_orders`, `subcontracts`, `subcontract_products`, `products`, `bom_lines`, `pieces`, `piece_operations`, and `operation_measurements` specify **`ON DELETE RESTRICT`**.
* **Rationale:** Aligns with the project's data-loss prevention rules. Physical production records, inspection measurements, and scrap lineage must never be wiped out through cascading parent deletions.

#### 1.3 Check Constraints (Business Guardrails)
* `subcontract_products.target_set_qty > 0`: An SC product must order at least 1 complete set.
* `bom_lines.qty_per_set > 0`: Component ratios must be positive integers.
* `vendor_dc_trackers.dispatched_qty >= returned_qty`: Returned piece count cannot exceed dispatched piece count.
* `pm_dispositions.decision IN ('CONTINUE', 'EXTRA_PROCESS', 'RETURN_TO_EARLIER_OPERATION', 'REJECT_AND_RESTART')`: Restricts PM decisions strictly to the four approved Phase 1 outcomes.
* `pm_dispositions.justification_notes <> ''`: Mandatory PM engineering justification (cannot be empty or whitespace).
* `operation_measurements.recorded_by_role IN ('OPERATOR', 'QC_INSPECTOR')`: Preserves strict role boundaries in measurement records.
* `qc_inspections.verdict IN ('PASSED', 'FAILED')`: Enforces binary quality verdicts.
* `piece_operations.status IN ('PENDING', 'READY', 'OPENED', 'SUBMITTED', 'QC_PENDING', 'PASSED', 'FAILED', 'CONCESSION_ACCEPTED', 'BYPASSED', 'REOPENED')`: Restricts operation lifecycle states to valid values, ensuring concessions are explicitly tagged.
* `assembled_sets.status IN ('BUILDING', 'PRODUCTION_COMPLETE', 'FINAL_QC_PASSED', 'PACKING_CONFIRMED', 'DISPATCH_PENDING', 'DELIVERED')`: Enforces valid sequential lifecycle states for assembled sets.
* `roles.role_code IN ('ROLE_ADMIN', 'ROLE_PM', 'ROLE_DESIGNER', 'ROLE_OPERATOR', 'ROLE_QC', 'ROLE_MANAGEMENT')`: Enforces the six confirmed primary system roles.

---

### 2. Strategic Indexing Recommendations

Because the application runs on **PostgreSQL on Neon** and handles high-frequency shop-floor operations (barcode scans, measurement inputs, QC queues, set completion queries), strategic indexes are required to guarantee sub-millisecond query performance:

#### 2.1 Foreign Key B-Tree Indexes
Every foreign key column in PostgreSQL requires an explicit index to eliminate sequential scans during relational joins:
* `idx_sc_po_id` ON `subcontracts(po_id)`
* `idx_scp_sc_id` ON `subcontract_products(sc_id)`
* `idx_scp_sc_line` ON `subcontract_products(sc_id, line_number)`
* `idx_scp_product_id` ON `subcontract_products(product_id)`
* `idx_bom_product_id` ON `bom_lines(product_id)`
* `idx_pieces_sc_id` ON `pieces(sc_id)`
* `idx_pieces_scp_id` ON `pieces(sc_product_id)`
* `idx_pieces_bom_line_id` ON `pieces(bom_line_id)`
* `idx_sets_scp_id` ON `assembled_sets(sc_product_id)`
* `idx_piece_ops_piece_id` ON `piece_operations(piece_id)`
* `idx_measurements_op_id` ON `operation_measurements(piece_operation_id)`
* `idx_allocations_set_id` ON `set_component_allocations(set_id)`

#### 2.2 High-Throughput Shop-Floor Partial Indexes
Partial indexes index only the active, pending subset of rows, keeping index size small and fully in-memory:
* **Operator Worklist Queue:**
  ```sql
  CREATE INDEX idx_active_station_queue 
  ON piece_operations(station_id, status) 
  WHERE status IN ('READY', 'OPENED');
  ```
* **QC Inspection Queue:**
  ```sql
  CREATE INDEX idx_pending_qc_queue 
  ON piece_operations(status, sequence_order) 
  WHERE status = 'QC_PENDING';
  ```
* **PM Exception Queue:**
  ```sql
  CREATE INDEX idx_active_exceptions 
  ON qc_exceptions(status) 
  WHERE status = 'PENDING_PM_DISPOSITION';
  ```
* **Transactional Outbox Dispatcher:**
  ```sql
  CREATE INDEX idx_pending_outbox_events 
  ON outbox_events(status, next_retry_at) 
  WHERE status = 'PENDING';
  ```

#### 2.3 GIN Indexes for JSONB Columns
For flexible query performance inside template snapshots, audit diffs, and defect parameter lists:
* `idx_snapshot_json` ON `process_template_snapshots USING GIN(snapshot_json)`
* `idx_audit_new_state` ON `audit_logs USING GIN(new_state)`
* `idx_outbox_payload` ON `outbox_events USING GIN(payload)`

---

### 3. Concurrency Control and Locking Strategy

To ensure absolute quantity conservation, deterministic set integrity, and eliminate race conditions on the shop floor:

1. **Optimistic Locking (`@Version`):**
   * Configured on `pieces`, `piece_operations`, and `subcontracts`.
   * If two operators simultaneously attempt to open or submit the same operation, the second transaction fails with an optimistic locking exception, preventing double-submissions.
2. **Deterministic Set Gating (No Arbitrary Pairing):**
   * Set membership is **deterministic and pre-allocated upon SC intake**, eliminating the risk of arbitrary runtime pairing collisions.
   * When evaluating whether a Set is complete, the query verifies that 100% of the pieces carrying `assembled_set_id = :setId` have reached `status = 'COMPLETED'`.
   * Row-level locking (`SELECT ... FOR UPDATE`) is applied to the target `assembled_sets` record during packing confirmation to prevent concurrent packing attempts on the same set.

---

### 4. Phase 1 Traceability and Invariant Verification

Every relevant Phase 1 requirement and business rule has been cross-checked against the corrected data model:

| Phase 1 Rule / Requirement | Source Document | Data Model Implementation | Traceability & Alignment Result |
| :--- | :--- | :--- | :--- |
| **BR-001 / BR-002:** SC intake & ID preservation | BRD §3, BR-001 | `subcontracts.rm_requisition_id`, `subcontracts.sc_number`, `rm_sync_history` | **Confirmed:** Exactly 1 production record per RM SC; source references preserved. |
| **BR-004:** PO → SC → Product → BOM → Set → Piece | BRD §6, BR-004 | `purchase_orders` → `subcontracts` → `subcontract_products` → `products` → `bom_lines` → `assembled_sets` → `pieces` | **Confirmed:** Unbroken 10-level hierarchy modeled without conflation; SC header cleanly separated from product line items. Line-item uniqueness is governed by `UNIQUE (sc_id, line_number)`, fully supporting duplicate catalog products across distinct contract lines. |
| **BR-005 & BR-006:** Set target & BOM ratio calculation | BRD §6, BR-006 | `subcontract_products.target_set_qty`, `bom_lines.qty_per_set` | **Confirmed:** Total component demand calculated per product as target sets × BOM ratio. |
| **BR-007:** Unique piece identity & set association | BRD §6, BR-007 | `pieces` with `sc_id`, `sc_product_id`, `assembled_set_id`, `set_sequence_number`, `component_code`, `component_sequence_in_set`, `replacement_sequence` | **Confirmed:** Separately stores all identity components. Printed syntax format held open for business confirmation. |
| **BR-008 & BR-009:** Deterministic set completeness & independent progress | BRD §6, BR-008 | `assembled_sets.is_production_complete`, `assembled_sets.is_final_qc_passed` | **Confirmed:** Independent milestone representation. Production complete when 100% of pre-allocated pieces pass all operations (`is_production_complete = true`); final QC verified independently (`is_final_qc_passed = true`). Pieces progress independently. |
| **BR-010:** Partial dispatch for complete sets only | BRD §6, BR-010 | `dispatch_confirmations` requires `assembled_sets.status = 'DISPATCH_PENDING'` | **Confirmed:** Partial dispatch allowed strictly for 100% complete sets; incomplete sets cannot be packed or dispatched. |
| **BR-012:** Scrapped piece lineage & replacement | SRS FR-012, BR-012 | `piece_replacement_lineage`, `pieces.replaced_piece_id`, `pieces.replacement_sequence` | **Confirmed:** Scrapped piece identity preserved; child replacement inherits parent set association. |
| **BR-018:** Process template version preservation | SRS FR-005, BR-018 | `process_template_snapshots` referenced by `pieces.snapshot_id` | **Confirmed:** Point-in-time frozen snapshot protects active pieces from future template edits. |
| **BR-019 & BR-020:** RM owns inventory; Production displays status | BRD §5, BR-020 | `subcontract_products.material_status`, `subcontract_products.material_ready` | **Confirmed:** Production has no inventory tables; RM tracks material issue per product line item. No unverified piece-level heat code assertions; exact RM material traceability granularity is pending inspection of actual RM API responses. Production maintains read-only relationship. |
| **BR-021:** Material readiness gate & operation sequencing | SRS FR-009, BR-021 | `subcontract_products.material_ready` checked as precondition before piece operation 1 transitions to `READY` | **Confirmed:** Machining data entry strictly blocked until parent product material is marked ready. Subsequent operations gated sequentially after previous QC pass. Operations are never made READY simultaneously. |
| **BR-023 to BR-031:** Tolerance sheet upload, verification & PM approval | SRS FR-006, BR-026 | `tolerance_revisions`, `tolerance_rules` (Nominal, Lower, Upper) | **Confirmed:** Excel/PDF extracted, verified by Design, approved by PM. Applied only to unentered data; existing measurements unchanged. Dynamic formulas held open. |
| **BR-032:** Operator and QC measurement separation | SRS FR-010/011, BR-032| `operation_measurements.recorded_by_role` (`OPERATOR` vs `QC_INSPECTOR`) | **Confirmed:** Stored in separate records; QC fields never pre-filled with operator data. |
| **BR-033 to BR-038:** QC failure to PM disposition | SRS FR-011/012, BR-033| `qc_exceptions`, `pm_dispositions` with 4 outcomes | **Confirmed:** Only PM can disposition failure: Continue (measurements immutable, verdict remains FAILED, mandatory justification, op transitions to `CONCESSION_ACCEPTED`, next op unlocks to `READY`), Extra Process, Return to Op, Reject & Restart (Scrap). |
| **BR-039:** ECR outbox integration | SRS FR-012, BR-039 | `outbox_events` (aggregates: `SCRAP_EXCEPTION`, `REWORK_NOTICE`), `pm_dispositions.ecr_report_number` | **Confirmed:** Outbox dispatches exception payload to ECR; ECR costing does not return to Production. |
| **BR-040 to BR-044:** Vendor tracking & RM DC boundary | SRS FR-013, BR-040 | `vendor_dc_trackers.rm_dc_number` | **Confirmed:** RM owns all DC creation, dispatch, and inward; Production tracks vendor progress and QC on returned goods. No vendor login. |
| **BR-046 & BR-047:** Sequential set lifecycle & dispatch confirmation | SRS FR-014, BR-046/047 | `assembled_sets.status`: `BUILDING` → `PRODUCTION_COMPLETE` → `FINAL_QC_PASSED` → `PACKING_CONFIRMED` → `DISPATCH_PENDING` → `DELIVERED` | **Confirmed:** Strict physical separation. Final QC passed does not mean packed; packing moves to `DISPATCH_PENDING`; dispatch employee explicitly confirms handover, which records delivery event (`DELIVERED`). Billing is strictly outside Production. |
| **BR-049 & BR-050:** Admin notification & cancellation governance | SRS FR-015/016, BR-049/050| `user_notifications`, `audit_logs` | **Confirmed:** Admin controls user notifications; cancellation/deletion strictly restricted to Admin with audit reason. |
| **BR-052 to BR-055:** Role governance & RBAC across 6 roles | SRS FR-001, BR-052/053| `users`, `roles`, `user_roles`, `user_permission_overrides` | **Confirmed:** Backend RBAC enforced across the six confirmed primary roles (`ROLE_ADMIN`, `ROLE_PM`, `ROLE_DESIGNER`, `ROLE_OPERATOR`, `ROLE_QC`, `ROLE_MANAGEMENT`) with individual user overrides. |
| **BR-056 to BR-058:** Explicit scope exclusions | BRD §4, BR-056/057/058| Architecture excludes billing, MERC/Velan Dashboard, and AI Agent | **Confirmed:** Boundaries strictly maintained. |

---

### 5. Review Decisions and Open Questions Register

#### 5.1 Confirmed Architecture Decisions (Resolved)
1. **Confirmed Six-Role Governance Structure:**
   * *Confirmed Baseline:* The six primary system roles are confirmed: **Admin**, **PM (Production Manager)**, **Designer**, **Operator**, **QC (Inspector)**, and **Management**.
   * *Status:* Designer-role discrepancy is **fully resolved**. Designer is an approved primary system role (`ROLE_DESIGNER`) governing CAD attachments, tolerance sheet uploads, and extraction verification. Fine-grained user overrides remain configurable via `user_permission_overrides`.
2. **Correct Set Lifecycle and Single Delivery Event:**
   * *Confirmed Baseline:* Set lifecycle is strictly sequential:
     $$\text{Building} \longrightarrow \text{Production Complete} \longrightarrow \text{Final QC Passed} \longrightarrow \text{Packing Confirmed} \longrightarrow \text{Dispatch Pending} \longrightarrow \text{Delivered}$$
   * *Status:* **Fully resolved**. Final QC completion does not mean Packed. Packing confirmation moves the set to `DISPATCH_PENDING`. A Dispatch employee explicitly confirms physical release via `dispatch_confirmations`, and this confirmation records the delivery event, setting status to `DELIVERED`. Each complete set can be dispatched independently; partial dispatch is forbidden for incomplete sets. Billing is strictly external to Production.
3. **Independent Product-Level Material Readiness Gating:**
   * *Confirmed Baseline:* Material readiness is tracked at the product line item level (`subcontract_products.material_ready`), settable via manual PM toggle or automatically updated when RM confirms material issuance.
   * *Status:* **Fully resolved**. In multi-product SCs, each product tracks readiness independently. Material issued for one product does not unlock unrelated products. All pieces belonging to that product receive readiness eligibility; individual pieces cannot bypass the material gate. Production is strictly read-only for RM inventory.
4. **Separation of Material Gate and Operation Sequencing:**
   * *Confirmed Baseline:* Material readiness is the mandatory prerequisite before beginning production. Once material is confirmed ready, **only the first eligible operation (sequence 10)** becomes `READY`. Subsequent operations remain strictly in `PENDING` and become `READY` only sequentially after previous operation completion and QC inspection pass. Operations are never made `READY` simultaneously. Operators cannot enter measurements before the material gate is satisfied.
5. **SC Line-Item Identity and Multi-Line Support:**
   * *Confirmed Baseline:* A Size Card can contain multiple line items ordering the same manufactured catalog product (e.g., separate lines with different delivery dates, quantities, or drawing revisions). Uniqueness is defined by `UNIQUE (sc_id, line_number)` on `subcontract_products` rather than `(sc_id, product_id)`.
   * *Status:* **Fully resolved**. The data model supports multi-line contracts with line-level identity (`line_number`) and external reference (`rm_line_item_id`).
6. **Separation of Production Completion from Quality, Packing, and Delivery Milestones:**
   * *Confirmed Baseline:* Set completeness is decomposed into distinct, independent boolean flags: `is_production_complete`, `is_final_qc_passed`, `is_packed`, and `is_delivered`.
   * *Status:* **Fully resolved**. Eliminates ambiguous overloading of `is_complete`. A set is marked `PRODUCTION_COMPLETE` (`is_production_complete = true`) when 100% of its component pieces complete machining; it transitions to `FINAL_QC_PASSED` (`is_final_qc_passed = true`) only upon explicit QC sign-off; packing sets `is_packed = true`; and physical dispatch sets `is_delivered = true`.
7. **PM CONTINUE Disposition Invariants:**
   * *Confirmed Baseline:* When the PM dispositions a QC failure as `CONTINUE`:
     - Original operator and QC measurements in `operation_measurements` and `measurement_values` remain strictly immutable and are never overwritten or deleted.
     - The `qc_inspections` record remains permanently marked `verdict = 'FAILED'`. Failure is never silently converted into a pass.
     - The PM must provide non-empty engineering `justification_notes`.
     - An immutable audit log entry (`PM_DISPOSITION_CONTINUE`) records `pm_user_id`, timestamp `decided_at`, and justification notes.
     - The failed operation in `piece_operations` transitions to **`CONCESSION_ACCEPTED`** (never `PASSED`), stamping `completed_at`.
     - Only upon recording `CONCESSION_ACCEPTED`, the next sequential operation in the routing transitions from `PENDING` to `READY` (stamping `ready_at`).
   * *Status:* **Fully resolved**. Preserves absolute quality traceability and harmonizes with `EXTRA_PROCESS`, `RETURN_TO_EARLIER_OPERATION`, and `REJECT_AND_RESTART`.

#### 5.2 Remaining Open Business Questions Awaiting Business Sign-Off
1. **Piece and Set Serial Number Syntax:**
   * *Business Decision Required:* What is the exact printed label and QR code format for shop-floor component pieces?
   * *Illustrative Patterns:* `SC002-1 ARG`, `SC002-1 SPG-1`, `SC002-2 ARG`, `SC002-1 SPG-1-R1`.
   * *Model Status:* The data model separately stores `sc_id`, `sc_product_id`, `set_sequence_number`, `component_code`, `component_sequence_in_set`, and `replacement_sequence`. The final formatted string remains configurable pending business sign-off.
2. **Advanced Dynamic Tolerance Mathematical Formulas:**
   * *Business Decision Required:* Do shop-floor tolerance sheets require live mathematical formula evaluation against mating part dimensions, or are static nominal values with upper/lower limits sufficient?
   * *Model Status:* Formula evaluation is removed from the active approved scope. The model strictly evaluates nominal, lower, and upper limits extracted from Excel/PDF. Advanced formula calculation is recorded as an open question pending review of actual Design tolerance files.
3. **RM Material Traceability Granularity (Heat-Code Level):**
   * *Business Decision Required:* What exact level of raw material identification (mill heat number, mill batch code, raw-bar ID) is emitted in RM V-SMART API responses?
   * *Model Status:* Held open pending inspection of actual RM API responses and payload contracts during integration development. The data model does not make unverified assertions about RM piece-level heat codes. Production remains strictly read-only for RM stock and issue transactions.
