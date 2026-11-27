# Part 2.2 – Domain and Data Model Design
## Document 04: Comprehensive Entity-Relationship (ER) Diagram

### 1. Complete Enterprise ER Diagram

The following diagram defines the physical and logical schema design for the Velan Production platform. It illustrates the complete relational topology across the commercial, engineering, execution, quality, exception, vendor, and dispatch clusters.

```mermaid
erDiagram
    purchase_orders ||--o{ subcontracts : "contains"
    subcontracts ||--o{ subcontract_products : "contains_products"
    subcontracts ||--o{ sc_revisions : "amended_by"
    products ||--o{ subcontract_products : "specified_as"
    products ||--o{ bom_lines : "composed_of"
    
    bom_lines ||--o{ process_templates : "governed_by"
    process_templates ||--o{ process_template_steps : "defined_by"
    process_templates ||--o{ process_template_snapshots : "snapshotted_as"
    
    subcontracts ||--o{ tolerance_revisions : "has_tolerances"
    tolerance_revisions ||--o{ tolerance_rules : "specifies"
    
    subcontract_products ||--o{ pieces : "spawns_pieces"
    assembled_sets ||--|{ pieces : "deterministically_contains"
    bom_lines ||--o{ pieces : "categorizes"
    process_template_snapshots ||--o{ pieces : "routes"
    
    pieces ||--o{ piece_operations : "executes"
    piece_operations ||--o{ operation_measurements : "measured_in"
    operation_measurements ||--o{ measurement_values : "contains_values"
    piece_operations ||--o{ qc_inspections : "inspected_by"
    
    qc_inspections ||--o| qc_exceptions : "triggers_if_failed"
    qc_exceptions ||--|| pm_dispositions : "resolved_by"
    
    pieces ||--o| piece_replacement_lineage : "scrapped_as_parent"
    pieces ||--o| piece_replacement_lineage : "spawned_as_child"
    pm_dispositions ||--o| piece_replacement_lineage : "authorizes"
    
    subcontract_products ||--o{ assembled_sets : "assembled_into"
    assembled_sets ||--o{ set_component_allocations : "groups"
    pieces ||--o| set_component_allocations : "fulfills"
    bom_lines ||--o{ set_component_allocations : "specifies_role"
    
    assembled_sets ||--o| packing_records : "verified_and_packed"
    subcontracts ||--o{ dispatch_confirmations : "dispatched_under"
    
    vendors ||--o{ vendor_dc_trackers : "assigned_to"
    subcontracts ||--o{ vendor_dc_trackers : "tracks_dc"
    piece_operations ||--o| vendor_dc_trackers : "linked_to_dc"
    
    subcontracts ||--o{ drawing_documents : "attaches_drawings"
    
    departments ||--o{ users : "employs"
    departments ||--o{ machines : "houses"
    users ||--o{ user_roles : "assigned"
    roles ||--o{ user_roles : "grants"
    departments ||--o{ production_targets : "assigned_target"
    subcontracts ||--o{ production_targets : "has_target"
    
    purchase_orders {
        uuid id PK
        varchar po_number UK
        varchar customer_name
        date order_date
        varchar status
    }
    
    subcontracts {
        uuid id PK
        varchar sc_number UK
        varchar rm_requisition_id
        uuid po_id FK
        varchar status
    }
    
    subcontract_products {
        uuid id PK
        uuid sc_id FK
        int line_number UK
        varchar rm_line_item_id
        uuid product_id FK
        int target_set_qty
        int completed_set_qty
        int packed_set_qty
        int dispatched_set_qty
        boolean material_ready
        varchar material_status
        timestamptz material_issued_at
        varchar status
    }
    
    products {
        uuid id PK
        varchar product_code UK
        varchar product_name
        varchar product_category
    }
    
    bom_lines {
        uuid id PK
        uuid product_id FK
        varchar component_code
        varchar component_name
        int qty_per_set
        boolean is_mandatory
    }
    
    process_templates {
        uuid id PK
        varchar template_code UK
        varchar component_code
        int version
        boolean is_active
    }
    
    process_template_steps {
        uuid id PK
        uuid template_id FK
        int sequence_order
        varchar operation_name
        varchar processing_route
        boolean requires_qc
    }
    
    process_template_snapshots {
        uuid id PK
        uuid template_id FK
        int template_version
        jsonb snapshot_json
        timestamptz frozen_at
    }
    
    tolerance_revisions {
        uuid id PK
        uuid sc_id FK
        varchar component_code
        int revision_number
        varchar status
    }
    
    tolerance_rules {
        uuid id PK
        uuid revision_id FK
        varchar parameter_code
        varchar parameter_type
        numeric nominal_value
        numeric lower_tolerance
        numeric upper_tolerance
    }
    
    pieces {
        uuid id PK
        varchar serial_number UK
        uuid sc_id FK
        uuid sc_product_id FK
        uuid assembled_set_id FK
        int set_sequence_number
        uuid bom_line_id FK
        varchar component_code
        int component_sequence_in_set
        int replacement_sequence
        uuid snapshot_id FK
        uuid replaced_piece_id FK
        varchar status
    }
    
    piece_operations {
        uuid id PK
        uuid piece_id FK
        int sequence_order
        varchar operation_name
        varchar processing_route
        boolean is_extra_operation
        timestamptz opened_at
        timestamptz submitted_at
        timestamptz completed_at
        varchar status
    }
    
    operation_measurements {
        uuid id PK
        uuid piece_operation_id FK
        varchar recorded_by_role
        uuid recorded_by_user_id FK
        uuid tolerance_revision_id FK
        timestamptz recorded_at
    }
    
    measurement_values {
        uuid id PK
        uuid measurement_id FK
        varchar parameter_code
        numeric numeric_value
        boolean boolean_value
        text text_value
        boolean is_in_tolerance
    }
    
    qc_inspections {
        uuid id PK
        uuid piece_operation_id FK
        uuid inspector_id FK
        varchar verdict
        text failure_reason
        timestamptz inspected_at
    }
    
    qc_exceptions {
        uuid id PK
        uuid piece_id FK
        uuid piece_operation_id FK
        uuid qc_inspection_id FK
        varchar status
    }
    
    pm_dispositions {
        uuid id PK
        uuid exception_id FK
        uuid pm_user_id FK
        varchar decision
        text justification_notes
        varchar ecr_report_number
        timestamptz decided_at
    }
    
    piece_replacement_lineage {
        uuid id PK
        uuid scrapped_parent_piece_id FK
        uuid replacement_child_piece_id FK
        uuid disposition_id FK
        timestamptz created_at
    }
    
    assembled_sets {
        uuid id PK
        varchar set_identifier UK
        uuid sc_id FK
        uuid sc_product_id FK
        int set_sequence_number
        boolean is_production_complete
        boolean is_final_qc_passed
        boolean is_packed
        boolean is_delivered
        timestamptz production_completed_at
        timestamptz final_qc_passed_at
        uuid final_qc_passed_by FK
        timestamptz packing_confirmed_at
        uuid packed_by_user_id FK
        timestamptz dispatch_pending_at
        timestamptz delivered_at
        uuid dispatched_by_user_id FK
        varchar status
    }
    
    set_component_allocations {
        uuid id PK
        uuid set_id FK
        uuid piece_id FK
        uuid bom_line_id FK
        timestamptz allocated_at
    }
    
    packing_records {
        uuid id PK
        uuid set_id FK
        varchar packing_slip_number UK
        uuid packed_by_user_id FK
        timestamptz packed_at
    }
    
    dispatch_confirmations {
        uuid id PK
        uuid sc_id FK
        varchar dispatch_challan_no UK
        uuid dispatched_by_user_id FK
        timestamptz dispatched_at
    }
    
    vendors {
        uuid id PK
        varchar vendor_code UK
        varchar vendor_name
        boolean is_active
    }
    
    vendor_dc_trackers {
        uuid id PK
        uuid vendor_id FK
        varchar rm_dc_number
        uuid sc_id FK
        int dispatched_qty
        int returned_qty
        varchar status
    }
    
    drawing_documents {
        uuid id PK
        uuid sc_id FK
        varchar document_name
        varchar r2_file_key UK
        bigint file_size_bytes
    }
    
    departments {
        uuid id PK
        varchar department_code UK
        varchar department_name
        boolean is_active
    }
    
    machines {
        uuid id PK
        varchar machine_code UK
        varchar machine_name
        uuid department_id FK
        boolean is_operational
    }
    
    users {
        uuid id PK
        varchar employee_code UK
        varchar username UK
        varchar full_name
        uuid department_id FK
        boolean is_active
        boolean is_suspended
    }
    
    roles {
        uuid id PK
        varchar role_code UK
        varchar role_name
        boolean is_system_role
    }
    
    user_roles {
        uuid id PK
        uuid user_id FK
        uuid role_id FK
    }
    
    production_targets {
        uuid id PK
        uuid sc_id FK
        uuid department_id FK
        varchar target_period
        date target_date
        int target_set_qty
        int target_piece_qty
        int actual_set_qty
        int actual_piece_qty
    }
```

---

### 2. ER Diagram Walkthrough by Domain Cluster

#### Cluster 1: Commercial Order & Hierarchy
* **`purchase_orders` → `subcontracts` → `subcontract_products` → `products`:** Groups client purchase orders into specific Size Cards. An SC can contain one or multiple manufactured product line items (`subcontract_products`), uniquely identified by `line_number` (`UNIQUE (sc_id, line_number)`). This allows the same catalog product (`product_id`) to appear on multiple distinct lines within an SC with different target quantities, specifications, or delivery schedules.
* **`subcontracts` → `sc_revisions`:** Captures contract amendments and quantity revisions emitted from RM V-SMART without losing baseline parameters.

#### Cluster 2: Engineering & Bill of Materials
* **`products` → `bom_lines`:** Defines the multi-component set ratio (e.g., 1 Valve Body, 2 Flanges, 1 Spindle, 3 Jig Feet).
* **`bom_lines` → `process_templates` → `process_template_steps`:** Masters that define the standard operational routes for each part type.
* **`process_templates` → `process_template_snapshots`:** Captures point-in-time frozen snapshots for active production pieces.
* **`subcontracts` → `tolerance_revisions` → `tolerance_rules`:** SC-specific static dimensional tolerances (nominal, lower limit, upper limit) extracted from Excel/PDF and approved by the PM. Formula-based dynamic expressions are removed from approved scope pending review of sample files.

#### Cluster 3: Shop-Floor Piece Execution
* **`subcontract_products` + `bom_lines` → `pieces`:** Generates individual physical pieces deterministically bound to their target `assembled_set_id` and numbered with SC, set sequence, component code, unit index, and replacement sequence.
* **Product Material Readiness & Sequential Operation Gating:**
  - Material readiness is tracked per product (`subcontract_products.material_ready`). In multi-product SCs, material issued for one product does not unlock unrelated products.
  - Material readiness is the mandatory prerequisite before machining can begin.
  - When material is issued for a product, **only the first eligible operation (sequence 10)** transitions to `READY`. Subsequent operations remain strictly in `PENDING` and transition to `READY` only sequentially after previous operations pass QC.
  - Measurement entry is strictly blocked until the material gate is satisfied and the operation reaches `READY` or `OPENED`.
  - The model does not make unverified assertions about RM piece-level heat codes; exact RM material traceability granularity is pending inspection of actual RM API responses.
* **`pieces` → `piece_operations`:** Sequential execution instances of operations derived from the frozen template snapshot.

#### Cluster 4: Quality Control & Exception Handling
* **`piece_operations` → `operation_measurements` → `measurement_values`:** Separate measurement sessions for Operators and QC Inspectors, retaining all readings and preventing overwrites.
* **`piece_operations` → `qc_inspections`:** Official pass/fail quality verdicts.
* **`qc_inspections` → `qc_exceptions` → `pm_dispositions`:** Out-of-spec failures place the piece on hold and escalate exclusively to the PM for disposition.
  - When PM selects **`CONTINUE`**: original measurements and the `FAILED` inspection verdict remain immutable and permanently recorded. The PM enters mandatory engineering justification notes. The operation status in `piece_operations` transitions to **`CONCESSION_ACCEPTED`** (never silently converted to `PASSED`), which unlocks the next sequential operation to `READY`.
* **`pm_dispositions` → `piece_replacement_lineage`:** Scrapping a piece spawns a child replacement piece with strict parent-child genealogy, retaining the scrapped piece's deterministic set allocation.

#### Cluster 5: Assembly, Packing, and Dispatch
* **`subcontract_products` → `assembled_sets`:** Tracks completion of logical sets based on target quantities for each ordered product line item.
* **`assembled_sets` + `pieces` → `set_component_allocations`:** Represents the deterministic pre-allocation manifest created at intake. Discrete milestone flags independently track set progression:
  - `is_production_complete`: 100% of pre-allocated pieces completed all machining operations.
  - `is_final_qc_passed`: final set inspection certified by QC.
  - `is_packed`: physical packing verified and slip generated.
  - `is_delivered`: dispatch employee confirmed vehicle gate exit.
* **Confirmed Sequential Lifecycle Progression:** `BUILDING` → `PRODUCTION_COMPLETE` → `FINAL_QC_PASSED` → `PACKING_CONFIRMED` → `DISPATCH_PENDING` → `DELIVERED`.
  - Final QC completion does not mean Packed.
  - Packing confirmation moves set to `PACKING_CONFIRMED` and `DISPATCH_PENDING`, capturing packing checklist and slip.
  - Dispatch employee explicitly confirms dispatch with gate pass / challan, and this confirmation records the delivery event, transitioning the set status to `DELIVERED`.
  - Complete sets can be dispatched independently. Partial dispatch is strictly forbidden for incomplete sets.
  - Billing is strictly external to Velan Production.

#### Cluster 6: External Processing & Infrastructure
* **`vendors` + `subcontracts` → `vendor_dc_trackers`:** Tracks pieces undergoing external vendor operations against RM Delivery Challan numbers. Production does not implement DC creation, dispatch, or inward transactions; RM remains the exclusive owner.
* **`drawing_documents`:** Links CAD blueprints and inspection diagrams to Cloudflare R2 object keys.

#### Cluster 7: IAM, Departments & Production Targets
* **`departments` → `users` + `machines`:** Factory departmental organization grouping personnel and physical machine assets.
* **`users` + `roles` → `user_roles`:** Enforces RBAC across the **six confirmed primary system roles**: `ROLE_ADMIN`, `ROLE_PM`, `ROLE_DESIGNER`, `ROLE_OPERATOR`, `ROLE_QC`, and `ROLE_MANAGEMENT`.
* **`departments` + `subcontracts` → `production_targets`:** Daily, weekly, and monthly set and piece production throughput quotas set by the PM.
