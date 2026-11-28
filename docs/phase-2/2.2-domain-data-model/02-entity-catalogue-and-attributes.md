# Part 2.2 – Domain and Data Model Design
## Document 02: Entity Catalogue and Attribute Specifications

### 1. Overview and Design Conventions

This document provides the exhaustive Entity Catalogue for the Velan Production platform. Every entity is assigned to a specific module owner within the modular monolith and justified with its explicit manufacturing purpose.

#### Technical Modeling Conventions:
* **Primary Keys:** Every entity utilizes a surrogate primary key: `UUID` (`id`) for globally distributed uniqueness or `BIGSERIAL` where sequential ordering is critical.
* **Audit Columns:** Every domain entity includes mandatory auditing fields:
  * `created_at` (`TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()`)
  * `created_by` (`VARCHAR(100) NOT NULL`)
  * `updated_at` (`TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()`)
  * `updated_by` (`VARCHAR(100) NOT NULL`)
  * `version` (`BIGINT NOT NULL DEFAULT 0` for optimistic locking via `@Version`)
* **Soft Deletion Policy:** To guarantee tamper-evident traceability on the shop floor, business entities (such as pieces, operations, measurements, and exceptions) are **never permanently deleted**. They utilize `is_active` / `status` flags, or explicit cancellation workflows governed exclusively by the Admin role (BR-050).

---

### 2. Commercial and Order Module (`order`)

#### 2.1 Entity: `purchase_orders`
* **Owning Module:** `order`
* **Business Purpose:** Represents the overarching commercial purchase order from the customer (e.g., Shell Global, Saudi Aramco), grouping one or more production Size Cards.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `po_number` | `VARCHAR(100)` | No | UNIQUE | Commercial PO number issued by customer. |
| `customer_name` | `VARCHAR(255)` | No | INDEX | Name of customer organization. |
| `order_date` | `DATE` | No | - | Date PO was placed. |
| `delivery_due_date`| `DATE` | Yes | - | Customer's contractual required delivery date. |
| `status` | `VARCHAR(50)` | No | CHECK | Current state (`ACTIVE`, `COMPLETED`, `CANCELLED`). |

#### 2.2 Entity: `sc_headers` (Size Cards / SC)
* **Owning Module:** `order`
* **Business Purpose:** Represents the commercial manufacturing order header received from RM (V-SMART). Can contain one or multiple manufactured products (`sc_products`).
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `sc_number` | `VARCHAR(100)` | No | UNIQUE, INDEX | Official SC number assigned in RM V-SMART (e.g., `SC-2579`). |
| `rm_requisition_id`| `VARCHAR(100)` | No | INDEX | Source requisition identifier in V-SMART. |
| `po_id` | `UUID` | No | FK (`purchase_orders.id`) | Parent commercial purchase order. |
| `status` | `VARCHAR(50)` | No | INDEX | SC header state (`INTAKE`, `ACTIVE`, `COMPLETED`, `CANCELLED`). |

#### 2.3 Entity: `sc_products` (SC Products)
* **Owning Module:** `order`
* **Business Purpose:** Represents a specific manufactured product line item ordered under an SC. Governs target set quantity and acts as the **independent material readiness gate** for that product line.
* **Line-Item Identity & Multi-Line Support:** Commercial sc_headers frequently order the same catalog product across multiple line items (e.g., Line 1: 10 sets of Product A for October delivery with standard tolerance; Line 2: 5 sets of Product A for November delivery with custom test procedures). Therefore, line-item uniqueness is governed by `UNIQUE (sc_id, line_number)`, allowing the same `product_id` to appear on multiple distinct line items within the same SC.
* **Product-Level Material Gate Rule:** Material readiness is tracked per product line item (`sc_products.material_ready`). In multi-line SCs, material issued for one product line does **not** make unrelated lines ready. Each line item independently gates its constituent pieces.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `sc_id` | `UUID` | No | FK (`sc_headers.id`), INDEX | Parent Size Card header. |
| `line_number` | `INTEGER` | No | UNIQUE with `sc_id` | 1-indexed contract line item number (`1, 2, 3...`) within the SC header. |
| `rm_line_item_id` | `VARCHAR(100)` | Yes | - | External line-item identifier provided by RM (V-SMART). |
| `product_id` | `UUID` | No | FK (`products.id`), INDEX | Specific manufactured catalog product ordered. |
| `target_set_qty` | `INTEGER` | No | CHECK (`> 0`) | Total complete sets required for this product line item. |
| `completed_set_qty`| `INTEGER` | No | DEFAULT `0` | Count of sets 100% completed through production & final QC. |
| `packed_set_qty` | `INTEGER` | No | DEFAULT `0` | Count of sets verified and packed (`DISPATCH_PENDING`). |
| `dispatched_set_qty`| `INTEGER` | No | DEFAULT `0` | Count of sets confirmed dispatched (`DELIVERED`). |
| `material_ready` | `BOOLEAN` | No | DEFAULT `false` | Hard operational gate per product line: manual yes/no toggle in Production, OR auto-updated to true when RM confirms material issue for this product line. |
| `material_status` | `VARCHAR(50)` | No | DEFAULT `'PENDING'` | RM readiness state for this product (`PENDING`, `ISSUED`). |
| `material_issued_at`| `TIMESTAMPTZ` | Yes | - | Exact timestamp RM Stores issued raw materials or manual readiness was confirmed. |
| `status` | `VARCHAR(50)` | No | INDEX | Line item state (`INTAKE`, `PLANNED`, `IN_PRODUCTION`, `DISPATCH_PENDING`, `DELIVERED`, `CANCELLED`). |

#### 2.4 Entity: `sc_revisions`
* **Owning Module:** `order`
* **Business Purpose:** Maintains an immutable historical log of contract changes, target quantity revisions, or engineering specification updates transmitted by RM.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `sc_id` | `UUID` | No | FK (`sc_headers.id`) | Target SC being revised. |
| `sc_product_id` | `UUID` | Yes | FK (`sc_products.id`)| Specific SC product being revised (if product-specific). |
| `revision_number` | `INTEGER` | No | - | Sequential revision count (`1, 2, 3...`). |
| `previous_target_qty`| `INTEGER` | No | - | Target set quantity prior to change. |
| `new_target_qty` | `INTEGER` | No | - | Revised target set quantity. |
| `revision_reason` | `TEXT` | No | - | Mandatory explanation for contract amendment. |
| `approved_by_pm` | `BOOLEAN` | No | DEFAULT `false` | Whether PM acknowledged and approved change. |

---

### 3. Engineering and BOM Module (`engineering`)

#### 3.1 Entity: `products`
* **Owning Module:** `engineering`
* **Business Purpose:** Catalog of finished manufactured goods (e.g., 2" Class 600 Cast Steel Gate Valve).
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `product_code` | `VARCHAR(100)` | No | UNIQUE | Internal product catalog code (e.g., `VLV-GT-02-600`). |
| `product_name` | `VARCHAR(255)` | No | INDEX | Descriptive name of manufactured product. |
| `product_category` | `VARCHAR(100)` | No | - | Engineering classification (e.g., `GATE_VALVE`, `GLOBE_VALVE`). |
| `pressure_rating` | `VARCHAR(50)` | Yes | - | Design pressure rating (e.g., `Class 600`, `Class 1500`). |
| `size_designation` | `VARCHAR(50)` | Yes | - | Nominal diameter (e.g., `2-inch`, `DN50`). |

#### 3.2 Entity: `bom_lines`
* **Owning Module:** `engineering`
* **Business Purpose:** Defines the hierarchical component makeup of a product. Specifies exactly how many units of each component are required to construct one complete Set.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `product_id` | `UUID` | No | FK (`products.id`) | Parent finished product. |
| `component_code` | `VARCHAR(50)` | No | INDEX | Engineering part code (e.g., `ARG`, `SPG`, `VBM`, `JIG`). |
| `component_name` | `VARCHAR(255)` | No | - | Full part name (e.g., `Flange`, `Spindle`, `Valve Body`). |
| `qty_per_set` | `INTEGER` | No | CHECK (`> 0`) | Ratio required per complete set (e.g., 2 Spindles per 1 Set). |
| `raw_material_type`| `VARCHAR(100)` | Yes | - | Specified metallurgy (e.g., `ASTM A216 WCB`, `SS316`). |
| `is_mandatory` | `BOOLEAN` | No | DEFAULT `true` | Must be completed for set completion gating. |

#### 3.3 Entity: `process_templates`
* **Owning Module:** `engineering`
* **Business Purpose:** Reusable master template defining the sequence of standard manufacturing operations, required machines, and expected cycle times for a component.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `template_code` | `VARCHAR(100)` | No | UNIQUE | Master template code (e.g., `TPL-SPG-REV02`). |
| `component_code` | `VARCHAR(50)` | No | INDEX | Component type this template governs. |
| `version` | `INTEGER` | No | - | Template revision number (`1, 2, 3...`). |
| `is_active` | `BOOLEAN` | No | DEFAULT `true` | Whether active for new production runs. |
| `approved_by` | `VARCHAR(100)` | Yes | - | PM or Designer who authorized template. |

#### 3.4 Entity: `process_template_steps`
* **Owning Module:** `engineering`
* **Business Purpose:** Ordered operations within a process template (e.g., Step 10: Rough Turning, Step 20: CNC Milling, Step 30: Heat Treatment).
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `template_id` | `UUID` | No | FK (`process_templates.id`) | Parent template. |
| `sequence_order` | `INTEGER` | No | INDEX | Step sequence (`10, 20, 30...`). |
| `operation_name` | `VARCHAR(150)` | No | - | Descriptive name (e.g., `OD Turning`, `Boring`). |
| `department_name` | `VARCHAR(100)` | No | - | Shop department (e.g., `Machining`, `Heat Treatment`). |
| `processing_route` | `VARCHAR(50)` | No | CHECK | `IN_HOUSE` or `VENDOR`. |
| `requires_qc` | `BOOLEAN` | No | DEFAULT `true` | Whether mandatory QC inspection follows step. |
| `std_duration_mins`| `INTEGER` | Yes | - | Standard baseline operation time. |

#### 3.5 Entity: `process_template_snapshots`
* **Owning Module:** `engineering`
* **Business Purpose:** An immutable JSON/relational snapshot of the process template frozen at the moment a piece is created. Prevents future master template changes from silently corrupting in-progress shop-floor jobs.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `template_id` | `UUID` | No | FK (`process_templates.id`) | Originating master template. |
| `template_version` | `INTEGER` | No | - | Version captured. |
| `snapshot_json` | `JSONB` | No | - | Full immutable serialization of steps, parameters, and rules. |
| `frozen_at` | `TIMESTAMPTZ` | No | DEFAULT `NOW()` | Timestamp piece inherited snapshot. |

#### 3.6 Entity: `tolerance_revisions`
* **Owning Module:** `engineering`
* **Business Purpose:** Governs SC-specific dimensional tolerance sheets uploaded by Designers and approved by PMs.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `sc_id` | `UUID` | No | FK (`sc_headers.id`) | Target SC. |
| `component_code` | `VARCHAR(50)` | No | - | Component covered. |
| `revision_number` | `INTEGER` | No | - | Revision count (`1, 2...`). |
| `source_filename` | `VARCHAR(255)` | No | - | Original uploaded Excel or PDF filename. |
| `status` | `VARCHAR(50)` | No | CHECK | `EXTRACTED`, `DESIGN_VERIFIED`, `APPROVED`, `REJECTED`. |
| `approved_by` | `VARCHAR(100)` | Yes | - | PM user who approved revision. |
| `approved_at` | `TIMESTAMPTZ` | Yes | - | Activation timestamp. |

#### 3.7 Entity: `tolerance_rules`
* **Owning Module:** `engineering`
* **Business Purpose:** Individual dimensional parameters and static tolerance boundaries (nominal, lower limit, upper limit) extracted from Design tolerance sheets (Excel/PDF) and approved by the PM.
* **Scope Note:** Formula-based dynamic mathematical expressions are removed from the approved scope and marked as an open question pending review of actual Design tolerance files.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `revision_id` | `UUID` | No | FK (`tolerance_revisions.id`)| Associated approved revision. |
| `operation_name` | `VARCHAR(150)` | No | - | Operation where dimension is measured. |
| `parameter_code` | `VARCHAR(100)` | No | INDEX | Unique code (e.g., `OD_FLANGE`, `BORE_DIA`). |
| `parameter_name` | `VARCHAR(255)` | No | - | Human-readable name. |
| `parameter_type` | `VARCHAR(50)` | No | CHECK | `NUMERIC`, `BOOLEAN`, `TEXT`, `SIGNATURE`, `IMAGE`. |
| `unit_of_measure` | `VARCHAR(20)` | Yes | - | Units (e.g., `mm`, `microns`, `HRC`). |
| `nominal_value` | `NUMERIC(12,4)`| Yes | - | Target design dimension. |
| `lower_tolerance` | `NUMERIC(12,4)`| Yes | - | Minimum allowable deviation (e.g., `-0.0500`). |
| `upper_tolerance` | `NUMERIC(12,4)`| Yes | - | Maximum allowable deviation (e.g., `+0.0500`). |

#### 3.8 Entity: `drawing_documents`
* **Owning Module:** `engineering`
* **Business Purpose:** Stores metadata and Cloudflare R2 object keys for CAD files, 2D PDF blueprints, and inspection guides.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `sc_id` | `UUID` | No | FK (`sc_headers.id`) | Target SC. |
| `component_code` | `VARCHAR(50)` | Yes | - | Linked component. |
| `document_name` | `VARCHAR(255)` | No | - | Filename. |
| `document_type` | `VARCHAR(50)` | No | CHECK | `CAD_2D_PDF`, `CAD_3D_STEP`, `INSPECTION_PLAN`. |
| `r2_file_key` | `VARCHAR(500)` | No | UNIQUE | Deterministic path in Cloudflare R2 bucket. |
| `file_size_bytes` | `BIGINT` | No | - | Document size. |
| `sha256_hash` | `VARCHAR(64)` | No | - | Integrity checksum. |

---

### 4. Production Execution Module (`execution`)

#### 4.1 Entity: `pieces`
* **Owning Module:** `execution`
* **Business Purpose:** The physical component instance manufactured on the shop floor. Bound deterministically to its designated Set upon SC intake, tracking progression from raw cutting to final set packing.
* **Identity Architecture:** Separately represents SC identity, set sequence, component type, component sequence within set, and replacement sequence.
* **Serial Number Syntax Status:** Exact printed syntax (e.g., `SC002-1 ARG`, `SC002-1 SPG-1`) is a configuration decision requiring business confirmation.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `sc_id` | `UUID` | No | FK (`sc_headers.id`), INDEX | Originating Size Card. |
| `sc_product_id` | `UUID` | No | FK (`sc_products.id`), INDEX | Specific SC Product line item. Material readiness derived from here. |
| `assembled_set_id` | `UUID` | No | FK (`assembled_sets.id`), INDEX | **Deterministic Parent Set.** Bound at SC intake. |
| `set_sequence_number`| `INTEGER` | No | INDEX | Set index (`1, 2, 3...`) within the SC product. |
| `bom_line_id` | `UUID` | No | FK (`bom_lines.id`) | Component type definition. |
| `component_code` | `VARCHAR(50)` | No | INDEX | Part code (e.g., `ARG`, `SPG`, `VBM`, `JIG`). |
| `component_sequence_in_set`| `INTEGER`| No | DEFAULT `1` | Unit sequence within set (e.g., `1` or `2` for 2 Spindles/set). |
| `replacement_sequence`| `INTEGER` | No | DEFAULT `0` | `0` = original piece; `1` = R1; `2` = R2 replacement piece. |
| `serial_number` | `VARCHAR(100)` | No | UNIQUE, INDEX | Formatted unique serial (e.g., `SC002-1 SPG-1`). |
| `snapshot_id` | `UUID` | No | FK (`process_template_snapshots.id`) | Frozen routing snapshot. |
| `replaced_piece_id`| `UUID` | Yes | FK (`pieces.id`) | Scrapped parent piece ID (if replacement). |
| `status` | `VARCHAR(50)` | No | INDEX | `CREATED`, `AWAITING_MATERIAL`, `IN_PRODUCTION`, `COMPLETED`, `ON_HOLD_QC_FAILED`, `SCRAPPED`. (Note: packing belongs strictly to the `assembled_sets` entity). |

#### 4.2 Entity: `piece_operations`
* **Owning Module:** `execution`
* **Business Purpose:** An individual routing operation assigned to a physical piece (e.g., Piece #1 - Step 20: CNC Milling).
* **Material Gate vs. Operation Sequencing:**
  - **Prerequisite Material Gate:** Machine operators cannot open or enter measurements for an operation until parent product material readiness is confirmed (`material_ready = true` on `sc_products`).
  - **Strict Sequential Progression:** When product material readiness is confirmed, **only the first eligible operation (sequence 10)** transitions from `PENDING` to `READY`. Subsequent operations (sequence 20, 30...) remain strictly in `PENDING` and transition to `READY` only sequentially after the preceding operation completes and passes its required QC inspection.
  - **No Simultaneous Ready State:** Operations are never made `READY` simultaneously.
  - **Measurement Entry Blockage:** Measurement entry is prohibited for any operation that is not in `READY` or `OPENED` status.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `piece_id` | `UUID` | No | FK (`pieces.id`) | Physical piece undergoing operation. |
| `sequence_order` | `INTEGER` | No | - | Routing sequence (`10, 20, 30...`). |
| `operation_name` | `VARCHAR(150)` | No | - | Operation name. |
| `processing_route` | `VARCHAR(50)` | No | - | `IN_HOUSE` or `VENDOR`. |
| `is_extra_operation`| `BOOLEAN` | No | DEFAULT `false` | True if inserted dynamically by PM disposition. |
| `station_id` | `VARCHAR(100)` | Yes | - | Physical shop-floor work station. |
| `assigned_operator_id`| `UUID`| Yes | FK (`users.id`) | Operator assigned. |
| `ready_at` | `TIMESTAMPTZ` | Yes | - | Timestamp operation became eligible to start (gated sequentially: Op 1 by product material ready; Op N by Op N-1 QC pass). |
| `opened_at` | `TIMESTAMPTZ` | Yes | - | Timestamp operator opened job on screen. |
| `submitted_at` | `TIMESTAMPTZ` | Yes | - | Timestamp operator submitted measurements. |
| `completed_at` | `TIMESTAMPTZ` | Yes | - | Timestamp QC passed operation or PM approved concession (`CONCESSION_ACCEPTED`). |
| `status` | `VARCHAR(50)` | No | INDEX | `PENDING`, `READY`, `OPENED`, `SUBMITTED`, `QC_PENDING`, `PASSED`, `FAILED`, `CONCESSION_ACCEPTED`, `BYPASSED`, `REOPENED`. |

---

### 5. Quality Control Module (`quality`)

#### 5.1 Entity: `operation_measurements`
* **Owning Module:** `quality`
* **Business Purpose:** Captures a recorded measurement session for an operation. Distinct records exist for Operator inputs vs. independent QC Inspector inputs.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `piece_operation_id`| `UUID` | No | FK (`piece_operations.id`) | Operation measured. |
| `recorded_by_role` | `VARCHAR(50)` | No | CHECK | Role boundary: `OPERATOR` or `QC_INSPECTOR`. |
| `recorded_by_user_id`| `UUID`| No | FK (`users.id`) | User who recorded values. |
| `tolerance_revision_id`| `UUID`| No | FK (`tolerance_revisions.id`)| Tolerance baseline evaluated against. |
| `instrument_id` | `VARCHAR(100)` | Yes | - | Calibrated measuring tool ID (micrometer/caliper). |
| `recorded_at` | `TIMESTAMPTZ` | No | DEFAULT `NOW()` | Timestamp. |

#### 5.2 Entity: `measurement_values`
* **Owning Module:** `quality`
* **Business Purpose:** Detailed parameter readings within a measurement session. Supports numeric, boolean, text, signatures, and photographic attachments.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `measurement_id` | `UUID` | No | FK (`operation_measurements.id`)| Parent session. |
| `parameter_code` | `VARCHAR(100)` | No | - | Parameter code. |
| `parameter_type` | `VARCHAR(50)` | No | - | `NUMERIC`, `BOOLEAN`, `TEXT`, `SIGNATURE`, `IMAGE`. |
| `numeric_value` | `NUMERIC(12,4)`| Yes | - | Value if numeric (e.g., `150.2500`). |
| `boolean_value` | `BOOLEAN` | Yes | - | Value if Yes/No check. |
| `text_value` | `TEXT` | Yes | - | Value if alphanumeric / serial note. |
| `attachment_url` | `VARCHAR(500)` | Yes | - | Cloudflare R2 URL for signature/photo. |
| `is_in_tolerance` | `BOOLEAN` | Yes | - | Automated evaluation result. |
| `deviation_amount` | `NUMERIC(12,4)`| Yes | - | Calculated offset from nominal. |

#### 5.3 Entity: `qc_inspections`
* **Owning Module:** `quality`
* **Business Purpose:** The official quality verdict for an operation, logged independently by a certified QC Inspector.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `piece_operation_id`| `UUID` | No | FK (`piece_operations.id`) | Target operation. |
| `inspector_id` | `UUID` | No | FK (`users.id`) | Certified QC Inspector. |
| `verdict` | `VARCHAR(50)` | No | CHECK | `PASSED` or `FAILED`. |
| `failure_reason` | `TEXT` | Yes | - | Mandatory explanation if verdict is FAILED. |
| `defective_parameters`| `JSONB` | Yes | - | List of parameters exceeding tolerance bands. |
| `inspected_at` | `TIMESTAMPTZ` | No | DEFAULT `NOW()` | Timestamp. |

---

### 6. Exception and Rework Module (`exception`)

#### 6.1 Entity: `qc_exceptions`
* **Owning Module:** `exception`
* **Business Purpose:** Raised automatically when an operation receives a `FAILED` verdict from QC. Blocks piece progression until the PM issues a disposition.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `piece_id` | `UUID` | No | FK (`pieces.id`) | Affected piece placed on hold. |
| `piece_operation_id`| `UUID` | No | FK (`piece_operations.id`) | Failed operation. |
| `qc_inspection_id` | `UUID` | No | FK (`qc_inspections.id`) | QC inspection record. |
| `status` | `VARCHAR(50)` | No | INDEX | `PENDING_PM_DISPOSITION`, `DISPOSED`, `CLOSED`. |
| `opened_at` | `TIMESTAMPTZ` | No | DEFAULT `NOW()` | Timestamp opened. |

#### 6.2 Entity: `pm_dispositions`
* **Owning Module:** `exception`
* **Business Purpose:** Records the Project Manager's binding decision on how to resolve an out-of-spec piece following a QC failure (`BR-033 to BR-038`).
* **PM Disposition Options and CONTINUE Behavior:**
  1. **`CONTINUE` (Concession Progression):**
     - **Measurement Immutability:** Original operator and QC measurements in `operation_measurements` and `measurement_values` are preserved permanently; they are never overwritten, modified, or deleted.
     - **QC Verdict Integrity:** The `qc_inspections` record remains permanently marked with `verdict = 'FAILED'`. The failure is **never silently converted into a pass**.
     - **Mandatory Justification:** The PM must enter non-empty engineering `justification_notes`. The system rejects blank or whitespace-only entries.
     - **Audit Trail:** An immutable audit record (`PM_DISPOSITION_CONTINUE`) is written to `audit_logs`, capturing `pm_user_id`, timestamp `decided_at`, the failed inspection reference, and the full justification text.
     - **Operation Status Transition:** The failed operation in `piece_operations` transitions from `FAILED` to **`CONCESSION_ACCEPTED`**, stamping `completed_at`.
     - **Next Sequential Operation Unlocked:** Only upon recording `CONCESSION_ACCEPTED`, the next sequential operation (sequence N+1) transitions from `PENDING` to `READY` (with timestamp `ready_at`).
     - **Piece & Exception Status:** The piece status in `pieces` transitions from `ON_HOLD_QC_FAILED` back to `IN_PRODUCTION`. The associated `qc_exceptions` status transitions to `RESOLVED_CONCESSION`.
  2. **`EXTRA_PROCESS` (Ad-Hoc Remediation):**
     - The PM inserts a new ad-hoc remedial operation (e.g., sequence 25: Skim Grinding, `is_extra_operation = true`) with status `READY`.
     - Original failed measurements and inspection verdicts remain permanently preserved in history.
     - `qc_exceptions` transitions to `RESOLVED_REWORK`. The piece remains `IN_PRODUCTION`.
  3. **`RETURN_TO_EARLIER_OPERATION` (Routing Rewind):**
     - The PM specifies `target_operation_id` to rewind routing. The target operation re-enters `READY` for rework under a new measurement iteration (`iteration_number = 2`).
     - All prior measurement logs and failure records remain permanently preserved and immutable.
     - `qc_exceptions` transitions to `RESOLVED_REWORK`.
  4. **`REJECT_AND_RESTART` (Scrap & Replacement):**
     - The physical piece is permanently marked `SCRAPPED` and retired from production.
     - Preserves all historical measurements and failure audits permanently.
     - Automatically enqueues a `SCRAP_EXCEPTION` event in `outbox_events` for external ECR notification.
     - Automatically spawns a new physical replacement piece with the same `assembled_set_id`, `bom_line_id`, and `set_sequence_number`, incrementing `replacement_sequence` (e.g., `-R1`), recorded in `piece_replacement_lineage`.
     - `qc_exceptions` transitions to `RESOLVED_SCRAP`.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `exception_id` | `UUID` | No | FK (`qc_exceptions.id`) | Target exception case. |
| `pm_user_id` | `UUID` | No | FK (`users.id`) | Authorized PM. |
| `decision` | `VARCHAR(50)` | No | CHECK | Exactly: `CONTINUE`, `EXTRA_PROCESS`, `RETURN_TO_EARLIER_OPERATION`, `REJECT_AND_RESTART`. |
| `justification_notes`| `TEXT` | No | - | Mandatory PM engineering reason (cannot be empty). |
| `target_operation_id`| `UUID` | Yes | - | Target step if returning to earlier operation. |
| `ecr_report_number` | `VARCHAR(100)`| Yes | - | External ECR non-conformance ticket reference. |
| `decided_at` | `TIMESTAMPTZ` | No | DEFAULT `NOW()` | Timestamp. |

#### 6.3 Entity: `piece_replacement_lineage`
* **Owning Module:** `exception`
* **Business Purpose:** Preserves parent-child genealogy when a physical piece is scrapped (`REJECT_AND_RESTART`) and replaced by a new physical serial number.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `scrapped_parent_piece_id`| `UUID`| No | FK (`pieces.id`) | Original scrapped piece. |
| `replacement_child_piece_id`| `UUID`| No | FK (`pieces.id`) | New replacement piece spawned. |
| `disposition_id` | `UUID` | No | FK (`pm_dispositions.id`)| Originating PM scrap decision. |
| `ecr_exception_ref` | `VARCHAR(100)`| Yes | - | Cross-reference to ECR outbox record. |
| `created_at` | `TIMESTAMPTZ` | No | DEFAULT `NOW()` | Timestamp. |

---

### 7. Vendor Tracking Module (`vendor`)

#### 7.1 Entity: `vendors`
* **Owning Module:** `vendor`
* **Business Purpose:** Master list of approved external processing vendors (e.g., commercial heat treaters, nitriding, hard chrome platers).
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `vendor_code` | `VARCHAR(50)` | No | UNIQUE | Internal vendor code. |
| `vendor_name` | `VARCHAR(255)` | No | - | Company name. |
| `capabilities` | `VARCHAR(255)` | Yes | - | Authorized operations (e.g., `HEAT_TREATMENT`, `PLATING`). |
| `is_active` | `BOOLEAN` | No | DEFAULT `true` | Active status. |

#### 7.2 Entity: `vendor_dc_trackers`
* **Owning Module:** `vendor`
* **Business Purpose:** Tracks pieces sent to external vendors. Links directly to RM (V-SMART) Delivery Challan (DC) numbers without creating or managing inventory transactions.
* **Strict Boundary Rule:** RM (V-SMART) is the sole and exclusive owner of Vendor DC creation, dispatch, inward receipt, and raw material inventory transactions. Production never creates, modifies, or receipts DCs; it purely synchronizes and displays vendor tracking status and DC references emitted by RM.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `vendor_id` | `UUID` | No | FK (`vendors.id`) | External vendor. |
| `rm_dc_number` | `VARCHAR(100)` | No | INDEX | Official DC number created and issued in RM. |
| `sc_id` | `UUID` | No | FK (`sc_headers.id`) | Associated SC. |
| `dispatched_qty` | `INTEGER` | No | - | Count of pieces dispatched on DC. |
| `returned_qty` | `INTEGER` | No | DEFAULT `0` | Count of pieces confirmed received by RM Inward. |
| `pending_balance_qty`| `INTEGER` | No | - | Remaining pieces at vendor (`dispatched - returned`). |
| `dispatched_date` | `DATE` | No | - | Date DC dispatched from factory. |
| `expected_return_date`| `DATE` | Yes | - | Target vendor turnaround date. |
| `rm_inward_reference`| `VARCHAR(100)`| Yes | - | Inward receipt reference number generated in RM. |
| `status` | `VARCHAR(50)` | No | CHECK | `DISPATCHED`, `PARTIALLY_RETURNED`, `FULLY_RETURNED`, `DELAYED`. |

---

### 8. Completion and Dispatch Module (`dispatch`)

#### 8.1 Entity: `assembled_sets`
* **Owning Module:** `dispatch`
* **Business Purpose:** Represents a specific, numbered assembled set unit (e.g., Set #1 of SC002 - Product 1) fulfilling one complete unit of the target product.
* **Deterministic Allocation Rule:** Set units are created deterministically upon SC intake based on `sc_products.target_set_qty`. Each piece generated for the product is immediately bound to its target `assembled_set_id`.
* **Confirmed Sequential Lifecycle Sequence:**
  `BUILDING` → `PRODUCTION_COMPLETE` → `FINAL_QC_PASSED` → `PACKING_CONFIRMED` → `DISPATCH_PENDING` → `DELIVERED`.
* **Strict Separation & Completion Rules:**
  - **Final QC Completion $\neq$ Packed:** Passing final QC verifies technical quality; packing is a distinct subsequent physical step.
  - **Packing Confirmation Moves to `DISPATCH_PENDING`:** Confirming packing moves the set into `PACKING_CONFIRMED` and `DISPATCH_PENDING`; it does not mean the set has left the factory.
  - **Dispatch Confirmation Records Delivery (`DELIVERED`):** The dispatch employee explicitly confirms physical dispatch from the loading dock, and this confirmation records the delivery event, moving status directly to `DELIVERED`.
  - **Independent Set Dispatch:** Each complete set can be packed and dispatched independently.
  - **Strict Partial Dispatch Guardrail:** Partial dispatch is permitted **only** for 100% complete sets. An incomplete set cannot be packed or dispatched.
  - **Billing Boundary:** Billing and commercial invoice generation are strictly outside Production.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `set_identifier` | `VARCHAR(100)` | No | UNIQUE, INDEX | Human-readable set code (e.g., `SET-SC002-01`). |
| `sc_id` | `UUID` | No | FK (`sc_headers.id`), INDEX | Associated SC header. |
| `sc_product_id` | `UUID` | No | FK (`sc_products.id`), INDEX | Specific SC Product line item. |
| `set_sequence_number`| `INTEGER` | No | - | Set index (`1, 2, 3...`) for this product. |
| `is_production_complete`| `BOOLEAN` | No | DEFAULT `false` | True when 100% of pre-allocated component pieces have completed all required machining operations (`PASSED` or `CONCESSION_ACCEPTED`). |
| `is_final_qc_passed` | `BOOLEAN` | No | DEFAULT `false` | True when the set has passed comprehensive Final QC inspection. |
| `is_packed` | `BOOLEAN` | No | DEFAULT `false` | True when packaging employee confirms parts checklist and seals container. |
| `is_delivered` | `BOOLEAN` | No | DEFAULT `false` | True when dispatch employee explicitly confirms loading dock gate pass and vehicle departure. |
| `production_completed_at`| `TIMESTAMPTZ`| Yes | - | Timestamp all pre-allocated component pieces completed all machining operations. |
| `final_qc_passed_at`| `TIMESTAMPTZ` | Yes | - | Timestamp final QC inspection verified and approved all components in the set. |
| `final_qc_passed_by`| `UUID` | Yes | FK (`users.id`) | QC Inspector who certified set inspection. |
| `packing_confirmed_at`| `TIMESTAMPTZ`| Yes | - | Timestamp packaging employee confirmed packing checklist and sealed the set. |
| `packed_by_user_id`| `UUID` | Yes | FK (`users.id`) | Packaging employee. |
| `dispatch_pending_at`| `TIMESTAMPTZ`| Yes | - | Timestamp set was staged in shipping area awaiting transport carrier. |
| `delivered_at` | `TIMESTAMPTZ` | Yes | - | Timestamp dispatch employee confirmed vehicle handover, gate pass, and factory exit. |
| `dispatched_by_user_id`| `UUID` | Yes | FK (`users.id`) | Dispatch employee who authorized release. |
| `status` | `VARCHAR(50)` | No | CHECK | Sequential lifecycle status: `BUILDING`, `PRODUCTION_COMPLETE`, `FINAL_QC_PASSED`, `PACKING_CONFIRMED`, `DISPATCH_PENDING`, `DELIVERED`. |

#### 8.2 Entity: `set_component_allocations`
* **Owning Module:** `dispatch`
* **Business Purpose:** Manifest entity recording the deterministic pre-allocation of each physical piece to its specific Set upon SC intake, ensuring exact fulfillment of approved BOM ratios.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `set_id` | `UUID` | No | FK (`assembled_sets.id`) | Target Set. |
| `piece_id` | `UUID` | No | UNIQUE, FK (`pieces.id`) | Allocated physical piece (UNIQUE: 1 piece belongs to max 1 set). |
| `bom_line_id` | `UUID` | No | FK (`bom_lines.id`) | Component role fulfilled. |
| `allocated_at` | `TIMESTAMPTZ` | No | DEFAULT `NOW()` | Timestamp pre-allocated upon SC intake. |

#### 8.3 Entity: `packing_records`
* **Owning Module:** `dispatch`
* **Business Purpose:** Verifies all components of a set before packing. Transition to packed moves set to `PACKING_CONFIRMED` and `DISPATCH_PENDING` (never directly to `DELIVERED` - BR-046).
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `set_id` | `UUID` | No | UNIQUE, FK (`assembled_sets.id`) | Packed Set. |
| `packing_slip_number`| `VARCHAR(100)`| No | UNIQUE | Internal packing slip reference. |
| `packed_by_user_id`| `UUID` | No | FK (`users.id`) | Packaging employee. |
| `packed_at` | `TIMESTAMPTZ` | No | DEFAULT `NOW()` | Timestamp packing confirmed. |
| `verification_checklist`| `JSONB`| No | - | Component physical verification checklist results. |

#### 8.4 Entity: `dispatch_confirmations`
* **Owning Module:** `dispatch`
* **Business Purpose:** Records explicit physical dispatch by a Dispatch employee from the warehouse dock. This confirmation records the delivery event and transitions status to `DELIVERED` (BR-047). Partial dispatch is strictly permitted only for complete sets. Billing is strictly outside Production.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `sc_id` | `UUID` | No | FK (`sc_headers.id`) | Associated SC. |
| `sc_product_id` | `UUID` | Yes | FK (`sc_products.id`)| Associated SC Product line item. |
| `dispatch_challan_no`| `VARCHAR(100)`| No | UNIQUE | Outgoing gate pass / delivery document number. |
| `dispatched_by_user_id`| `UUID` | No | FK (`users.id`) | Dispatch employee. |
| `carrier_name` | `VARCHAR(150)` | Yes | - | Logistics transport provider. |
| `vehicle_number` | `VARCHAR(50)` | Yes | - | Transport vehicle number. |
| `dispatched_set_ids`| `UUID[]` | No | - | Array of complete Set IDs dispatched. |
| `dispatched_at` | `TIMESTAMPTZ` | No | DEFAULT `NOW()` | Exact dispatch timestamp (sets `assembled_sets.delivered_at`). |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `sc_id` | `UUID` | No | FK (`sc_headers.id`) | Associated SC. |
| `dispatch_challan_no`| `VARCHAR(100)`| No | UNIQUE | Outgoing gate pass / delivery document number. |
| `dispatched_by_user_id`| `UUID` | No | FK (`users.id`) | Dispatch employee. |
| `carrier_name` | `VARCHAR(150)` | Yes | - | Logistics transport provider. |
| `vehicle_number` | `VARCHAR(50)` | Yes | - | Transport vehicle number. |
| `dispatched_set_ids`| `UUID[]` | No | - | Array of complete Set IDs dispatched. |
| `dispatched_at` | `TIMESTAMPTZ` | No | DEFAULT `NOW()` | Exact dispatch timestamp. |

---

### 9. Integration, Audit, and Infrastructure

#### 9.1 Entity: `outbox_events`
* **Owning Module:** `integration`
* **Business Purpose:** Transactional outbox table guaranteeing atomic delivery of exception payloads to ECR and webhook notifications without external brokers (ADR-02).
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier / Idempotency Key. |
| `aggregate_type` | `VARCHAR(100)` | No | INDEX | Event type (e.g., `SCRAP_EXCEPTION`, `REWORK_NOTICE`). |
| `aggregate_id` | `VARCHAR(100)` | No | - | Primary entity ID (e.g., `exception_id`). |
| `payload` | `JSONB` | No | - | Full JSON payload transmitted to external API. |
| `target_system` | `VARCHAR(50)` | No | CHECK | `ECR` or `RM`. |
| `status` | `VARCHAR(50)` | No | INDEX | `PENDING`, `PUBLISHED`, `FAILED`, `DEAD_LETTER`. |
| `retry_count` | `INTEGER` | No | DEFAULT `0` | Number of dispatch attempts. |
| `next_retry_at` | `TIMESTAMPTZ` | Yes | - | Exponential backoff scheduling timestamp. |
| `published_at` | `TIMESTAMPTZ` | Yes | - | Timestamp successful HTTP 200/201 response received. |
| `error_log` | `TEXT` | Yes | - | Last error message if failed. |

#### 9.2 Entity: `audit_logs`
* **Owning Module:** `audit`
* **Business Purpose:** Tamper-evident, immutable event log capturing all state changes, measurement inputs, user actions, and administrative overrides across the entire system.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `BIGSERIAL` | No | PK | Sequential auto-incrementing ID. |
| `event_timestamp` | `TIMESTAMPTZ` | No | DEFAULT `NOW()`, INDEX | Time of action. |
| `user_id` | `UUID` | Yes | - | User responsible (null for system jobs). |
| `username` | `VARCHAR(100)` | No | INDEX | Username snapshot. |
| `user_role` | `VARCHAR(50)` | No | - | Active role during transaction. |
| `action_type` | `VARCHAR(100)` | No | INDEX | Action (e.g., `MEASUREMENT_SUBMIT`, `QC_FAIL`, `DISPOSITION_SCRAP`). |
| `entity_name` | `VARCHAR(100)` | No | - | Target entity (e.g., `pieces`, `piece_operations`). |
| `entity_id` | `VARCHAR(100)` | No | INDEX | Target entity identifier. |
| `previous_state` | `JSONB` | Yes | - | Entity snapshot before mutation. |
| `new_state` | `JSONB` | Yes | - | Entity snapshot after mutation. |
| `client_ip` | `VARCHAR(45)` | Yes | - | Network IP address of client. |

#### 9.3 Entity: `user_notifications`
* **Owning Module:** `audit`
* **Business Purpose:** In-app notification queue feeding real-time Server-Sent Events (SSE) and persistent inbox messages to users based on role and department.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `recipient_user_id`| `UUID` | No | FK (`users.id`), INDEX | Targeted user. |
| `severity` | `VARCHAR(50)` | No | CHECK | `LOW`, `NORMAL`, `HIGH`, `CRITICAL`. |
| `category` | `VARCHAR(100)` | No | - | Category (e.g., `QC_FAIL_ALERT`, `MATERIAL_ISSUED`, `DELIVERY_DUE`). |
| `title` | `VARCHAR(255)` | No | - | Headline. |
| `message` | `TEXT` | No | - | Notification body text. |
| `target_url` | `VARCHAR(255)` | Yes | - | Deep-link to piece, exception, or worklist. |
| `is_read` | `BOOLEAN` | No | DEFAULT `false`, INDEX | Read status. |
| `read_at` | `TIMESTAMPTZ` | Yes | - | Read timestamp. |

---

### 10. IAM, Access Governance, and Shop-Floor Resources (`iam`)

#### 10.1 Entity: `users`
* **Owning Module:** `iam`
* **Business Purpose:** Master user account identity for employees across office and shop-floor roles.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `employee_code` | `VARCHAR(50)` | No | UNIQUE, INDEX | Factory payroll / employee ID badge number. |
| `username` | `VARCHAR(100)` | No | UNIQUE, INDEX | Login username. |
| `password_hash` | `VARCHAR(255)` | No | - | Argon2 / BCrypt cryptographic password hash. |
| `full_name` | `VARCHAR(255)` | No | - | Employee full legal name. |
| `department_id` | `UUID` | Yes | FK (`departments.id`) | Primary home department. |
| `is_active` | `BOOLEAN` | No | DEFAULT `true` | Active employment status. |
| `is_suspended` | `BOOLEAN` | No | DEFAULT `false` | Scheduled or administrative suspension (BR-FR001). |

#### 10.2 Entity: `roles`
* **Owning Module:** `iam`
* **Business Purpose:** Catalog of authorized system roles.
* **Confirmed Six-Role Governance Structure:** The system access model is built upon six confirmed primary system roles:
  1. `ROLE_ADMIN` – System administration, user lifecycle management, role assignments, audit log review, and administrative cancellations (BR-050, BR-052).
  2. `ROLE_PM` – Production planning, daily targets, SC approval, tolerance approval, exception disposition, and material readiness toggles (BR-034).
  3. `ROLE_DESIGNER` – Technical drawing attachments, dimensional tolerance sheet upload (Excel/PDF), and automated extraction verification (BR-023, BR-024).
  4. `ROLE_OPERATOR` – Shop-floor operation execution, opening assigned jobs, and recording operator-tier dimensional measurements (BR-032).
  5. `ROLE_QC` – Independent dimensional inspection, QC measurement recording, issuing official pass/fail verdicts, and logging non-conformance defect reports (BR-032, BR-033).
  6. `ROLE_MANAGEMENT` – Executive reporting, throughput analytics, scrap rate monitoring, and read-only visibility across all factory operations (BR-054).
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `role_code` | `VARCHAR(50)` | No | UNIQUE | Standardized code: `ROLE_ADMIN`, `ROLE_PM`, `ROLE_DESIGNER`, `ROLE_OPERATOR`, `ROLE_QC`, `ROLE_MANAGEMENT`. |
| `role_name` | `VARCHAR(100)` | No | - | Human-readable title (Admin, PM, Designer, Operator, QC Inspector, Management). |
| `is_system_role` | `BOOLEAN` | No | DEFAULT `false` | If true, protected system role that cannot be deleted. |

#### 10.3 Entity: `user_roles`
* **Owning Module:** `iam`
* **Business Purpose:** Junction entity mapping users to one or multiple roles.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `user_id` | `UUID` | No | FK (`users.id`) | Target user. |
| `role_id` | `UUID` | No | FK (`roles.id`) | Assigned role. |

#### 10.4 Entity: `user_permission_overrides`
* **Owning Module:** `iam`
* **Business Purpose:** Allows the Admin to explicitly grant or deny specific action permissions for an individual user, taking precedence over general role permissions (BR-053).
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `user_id` | `UUID` | No | FK (`users.id`) | Target user. |
| `permission_code` | `VARCHAR(100)`| No | - | Specific action grant (e.g., `OVERRIDE_APPROVE`, `SC_CANCEL`). |
| `is_granted` | `BOOLEAN` | No | - | True for explicit grant; False for explicit individual deny. |
| `reason` | `TEXT` | No | - | Mandatory Admin explanation. |

#### 10.5 Entity: `departments`
* **Owning Module:** `iam`
* **Business Purpose:** Master list of factory functional departments (e.g., CNC Machining, Milling, Grinding, Heat Treatment, Assembly, Quality Assurance, Packing & Shipping).
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `department_code`| `VARCHAR(50)` | No | UNIQUE | Internal code (e.g., `DEPT_MACH`, `DEPT_HT`, `DEPT_QC`). |
| `department_name`| `VARCHAR(150)`| No | - | Full name. |
| `is_active` | `BOOLEAN` | No | DEFAULT `true` | Active status. |

#### 10.6 Entity: `machines`
* **Owning Module:** `execution`
* **Business Purpose:** Master list of physical shop-floor machinery, CNC centers, lathes, and calibrated inspection benches where operations are executed.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `machine_code` | `VARCHAR(50)` | No | UNIQUE, INDEX | Physical asset code (e.g., `LATHE-01`, `VMC-04`, `CMM-01`). |
| `machine_name` | `VARCHAR(150)`| No | - | Machine model/name (e.g., `Mazak CNC Lathe Quick Turn`). |
| `department_id` | `UUID` | No | FK (`departments.id`) | Operating department location. |
| `is_operational` | `BOOLEAN` | No | DEFAULT `true` | Availability status for work scheduling. |

---

### 11. Synchronization, Import History, and Planning Targets (`integration` / `order`)

#### 11.1 Entity: `rm_sync_history`
* **Owning Module:** `integration`
* **Business Purpose:** Immutable log of inbound and outbound synchronizations with RM (V-SMART), recording event payloads, response codes, and material issue verification records.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `sync_type` | `VARCHAR(50)` | No | INDEX | `SC_INTAKE`, `MATERIAL_STATUS_POLL`, `VENDOR_INWARD_EVENT`. |
| `rm_reference_id`| `VARCHAR(100)`| No | INDEX | RM Requisition ID, SC Number, or RM DC Number. |
| `http_status` | `INTEGER` | No | - | HTTP response code (e.g., `200`, `201`, `502`). |
| `payload` | `JSONB` | No | - | Inbound or outbound JSON payload. |
| `synced_at` | `TIMESTAMPTZ` | No | DEFAULT `NOW()` | Timestamp. |

#### 11.2 Entity: `import_records`
* **Owning Module:** `order`
* **Business Purpose:** Tracks batch imports of Size Cards, tolerance sheets, or provisional manual contract intakes.
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `import_type` | `VARCHAR(50)` | No | CHECK | `RM_SC_INTAKE`, `EXCEL_TOLERANCE_SHEET`, `PROVISIONAL_MANUAL_SC`. |
| `source_filename`| `VARCHAR(255)`| Yes | - | Original uploaded file name if manual. |
| `imported_by` | `UUID` | Yes | FK (`users.id`) | User who triggered import (null for automated webhook). |
| `record_count` | `INTEGER` | No | - | Number of entities extracted. |
| `status` | `VARCHAR(50)` | No | CHECK | `SUCCESS`, `PARTIAL_SUCCESS`, `FAILED`. |
| `imported_at` | `TIMESTAMPTZ` | No | DEFAULT `NOW()` | Import execution timestamp. |

#### 11.3 Entity: `production_targets`
* **Owning Module:** `order`
* **Business Purpose:** Holds PM-defined daily, weekly, and monthly production throughput targets for complete sets and individual component pieces (FR-007, FEAT-035).
* **Attributes:**

| Attribute | Data Type | Nullable | Key / Constraint | Description & Business Meaning |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `UUID` | No | PK | Internal system identifier. |
| `sc_id` | `UUID` | Yes | FK (`sc_headers.id`) | Optional target linked to specific SC. |
| `department_id` | `UUID` | Yes | FK (`departments.id`) | Optional department-specific target. |
| `target_period` | `VARCHAR(50)` | No | CHECK | `DAILY`, `WEEKLY`, `MONTHLY`. |
| `target_date` | `DATE` | No | INDEX | Start date of target period. |
| `target_set_qty` | `INTEGER` | Yes | - | Target complete sets to finish. |
| `target_piece_qty`| `INTEGER` | Yes | - | Target component pieces to machine. |
| `actual_set_qty` | `INTEGER` | No | DEFAULT `0` | Actual complete sets achieved. |
| `actual_piece_qty`| `INTEGER` | No | DEFAULT `0` | Actual component pieces achieved. |
