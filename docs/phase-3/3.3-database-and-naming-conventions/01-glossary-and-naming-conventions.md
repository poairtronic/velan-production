# Part 3.3 — Database and Naming Conventions
## Document 01: Domain Glossary, SC Terminology Resolution, and System Naming Conventions

---

### 1. Confirmed Rules (Frozen Baseline)
* **Single Authoritative Vocabulary (`SKILLS.md §5`):** Domain entities, enums, error codes, and permission identifiers must possess exactly one canonical spelling across database constraints, Java classes, OpenAPI specs, TypeScript types, and documentation.
* **Six Primary Roles Only (`D-03`):** The system recognizes exactly six roles: `ROLE_ADMIN`, `ROLE_PM`, `ROLE_DESIGNER`, `ROLE_OPERATOR`, `ROLE_QC`, and `ROLE_MANAGEMENT`. Stores, Packing, Dispatch, Vendor, and Department Head are strictly forbidden as roles.
* **Strict Physical Traceability (`D-02, BR-007`):** Every piece has a unique system-wide physical serial number. Quantities and completion counts must be derived from piece records, never typed into manual buckets (`BR-013, BR-068`).
* **Unidirectional Integration Boundaries (`D-09, D-11, BR-067`):** Production never writes to RM. External shipping Delivery Challans (DCs) are owned, dispatched, and inwarded strictly by RM.

---

### 2. Proposed Design: Domain Glossary

| Term | Domain Acronym | Technical Entity / Concept | Architectural Definition & Manufacturing Scope |
| :--- | :--- | :--- | :--- |
| **Purchase Order** | `PO` | `purchase_orders` | The overarching commercial contract issued by a customer (e.g. Shell, Aramco), grouping one or more manufacturing work orders. |
| **Size Card** | `SC` | `subcontracts` / `sc_headers`* | The commercial work order header transmitted from RM (V-SMART) or created manually/Excel. Groups one or more product line items. |
| **Product Line Item** | `SC Product` | `subcontract_products` | Distinct line item within an SC specifying catalog product, target sets, and independent raw material readiness gate. |
| **Catalog Product** | - | `products` | Standard finished industrial valve or engineered assembly in the engineering catalog (e.g. 2" Class 600 Gate Valve). |
| **Bill of Materials**| `BOM` | `bom_lines` | Hierarchical component formula defining part codes, metallurgy, and exact ratio required per complete valve set (e.g. 2 Flanges per Set). |
| **Assembled Set** | `Set` | `assembled_sets` | Logical grouping of pre-allocated manufactured components that assemble into one complete, deliverable valve set. |
| **Physical Piece** | `Piece` | `pieces` | An individual physical unit of steel with an immutable physical serial number (`serial_number`) tracked through routing operations. |
| **Routing Operation**| `Op` | `piece_operations` | A discrete manufacturing, machining, testing, or vendor processing step (e.g. Op 10 Rough Turning, Op 20 Milling). |
| **Measurement** | - | `operation_measurements` | Immutable numerical or boolean readings recorded independently by Operators or QC Inspectors against approved tolerance bands. |
| **QC Inspection** | - | `qc_inspections` | Independent quality verification evaluating QC measurements against tolerance limits, issuing a binary `PASSED` or `FAILED` verdict. |
| **QC Exception** | - | `qc_exceptions` | Incident ticket raised automatically when an operation fails QC, putting the piece on hold pending mandatory PM disposition. |
| **PM Disposition** | - | `pm_dispositions` | Authoritative decision by Production Manager: `CONTINUE` (with reason), `EXTRA_PROCESS`, `RETURN_TO_EARLIER_OPERATION`, or `REJECT_AND_RESTART`. |
| **Scrap & Replacement**| - | `piece_replacement_lineage` | Retires failed piece as `SCRAPPED` and generates child replacement piece with incremented replacement sequence, blocked on material. |
| **Transactional Outbox**| - | `outbox_events` | Local PostgreSQL table storing ECR exception payloads in same transaction as PM disposition for asynchronous transmission. |
| **Delivery Challan** | `DC` | `vendor_dc_trackers` | External shipping document for vendor processing. RM owns creation, dispatch, and inward; Production reads return status only. |

*\*Note: See Section 3 for the open SC terminology analysis and recommendation.*

---

### 3. Canonical Domain Glossary (CLOSED — Owner Decision)

- **PO = Purchase Order:** Top-level commercial order header (one PO has many Size Cards).
- **SC = Size Card:** Production order card under a PO listing products and quantities (`sc_headers`, `sc_products`, `sc_number`, `/api/v1/scs`). *(Resolves the historical "Size Card" / "Size Card" / "Subcontract" terminology conflict.)*

---

### 4. Prescriptive System Naming Conventions

#### 4.1 Database Object Naming Standards (PostgreSQL on Neon)
1. **Tables:** Lowercase `snake_case`, plural nouns:
   - Examples: `purchase_orders`, `pieces`, `piece_operations`, `qc_inspections`, `audit_logs`.
   - Junction / Allocation tables: `set_component_allocations`, `user_roles`.
   - History tables: `<entity>_status_history` (e.g. `piece_operation_history`).
2. **Columns:** Lowercase `snake_case`, singular:
   - Primary Key: strictly `id` of type `UUID`.
   - Foreign Keys: `<singular_referenced_table>_id` (e.g. `piece_id`, `station_id`, `po_id`).
   - Timestamps: `<event>_at` in UTC (e.g. `created_at`, `submitted_at`, `completed_at`, `scrapped_at`).
   - Boolean Flags: `is_<adjective>` or `has_<noun>` (e.g. `is_active`, `is_mandatory`, `material_ready`).
   - Counts & Quantities: `<descriptor>_qty` (e.g. `target_set_qty`, `dispatched_set_qty`).
   - User references: `<action>_by_user_id` (e.g. `created_by_user_id`, `approved_by_user_id`).

#### 4.2 Java Backend Naming Standards
1. **Packages:** Lowercase, dot-separated, singular: `com.velan.production.execution.internal.service`.
2. **Classes & Interfaces:** `PascalCase`: `PieceExecutionService`, `PieceOperationRepository`, `ToleranceRule`.
3. **Methods & Variables:** `camelCase`: `submitOperatorJob()`, `currentStatus`, `targetSetQuantity`.
4. **Constants & Enums:** `UPPER_SNAKE_CASE`: `OPERATION_QC_PASSED`, `MAX_PAGE_SIZE`, `DEFAULT_BATCH_SIZE`.
5. **Exceptions:** `PascalCase` ending in `Exception`: `IllegalStateTransitionException`, `MaterialGateException`.

#### 4.3 TypeScript & Frontend Naming Standards
1. **Component Files & React Components:** `PascalCase`: `PieceWorklistGrid.tsx`, `PMDispositionDialog.tsx`.
2. **Hooks, Utilities, & Functions:** `camelCase`: `useStationQueue.ts`, `formatMeasurementValue()`.
3. **Types & Interfaces:** `PascalCase`: `PieceDTO`, `OperationStatus`, `ToleranceValidationResult`.

#### 4.4 File Naming Standards
* Java source files: `PascalCase.java`
* TypeScript component files: `PascalCase.tsx`
* TypeScript utility/hook files: `camelCase.ts`
* Flyway migration scripts: `V{n}__{description}.sql` (e.g. `V001__init_schema.sql`)
* Markdown documentation: `kebab-case.md` with two-digit sequence prefixes (e.g. `01-glossary-and-naming-conventions.md`).

#### 4.5 Enums & Catalogues Naming Standards
Enums must possess an identical `UPPER_SNAKE_CASE` spelling across all tiers:
* Example: `QC_PENDING`, `CONCESSION_ACCEPTED`, `REJECT_AND_RESTART`, `DISPATCH_PENDING`.

#### 4.6 REST API Paths Naming Standards
* Resource paths: Lowercase, plural `kebab-case`:
  - `GET /api/v1/pieces`
  - `POST /api/v1/pieces/{serial}/operations/{opId}/submit`
  - `GET /api/v1/qc-inspections/pending`
  - `POST /api/v1/qc-exceptions/{id}/dispositions`

#### 4.7 Permission & Error Codes
* **Permission Codes:** Format `MODULE_ACTION` in `UPPER_SNAKE_CASE`:
  - `EXECUTION_OPERATION_START`, `EXECUTION_MEASUREMENT_SUBMIT`, `QUALITY_INSPECTION_SUBMIT`, `EXCEPTION_DISPOSITION_APPLY`, `ORDER_SC_MANUAL_CREATE`, `ADMIN_USER_OVERRIDE`.
* **Error Codes:** `UPPER_SNAKE_CASE` prefixed with domain:
  - `OPERATION_NOT_FOUND`, `INVALID_OPERATION_TRANSITION`, `MATERIAL_GATE_LOCKED`, `CONCURRENCY_MODIFICATION_CONFLICT`.

#### 4.8 Environment Variables & Feature Flags
* Environment variables: `UPPER_SNAKE_CASE` (e.g. `SERVER_PORT`, `SPRING_DATASOURCE_URL`).
* Feature flags: `UPPER_SNAKE_CASE` ending in `_ENABLED` (e.g. `RM_SYNC_ENABLED`, `ECR_SEND_ENABLED`).

---

### 5. Prohibited Names ("Names That Must Never Appear")

The following terms and concepts are strictly forbidden from appearing in code, schema, DTOs, or documentation:

| Category | Forbidden Name(s) | Architectural Reason for Strict Exclusion |
| :--- | :--- | :--- |
| **Prohibited Roles** | `ROLE_STORES`, `ROLE_PACKING`, `ROLE_DISPATCH`, `ROLE_VENDOR`, `ROLE_DEPT_HEAD`, `ROLE_OPERATOR_LEAD` | Violates D-03 (strictly six roles: Admin, PM, Designer, Operator, QC, Management). Stores/packing/dispatch actions are permissions. |
| **Prohibited Infrastructure** | `redis`, `jedis`, `lettuce`, `rabbitmq`, `amqp`, `kafka`, `microservice`, `eureka`, `zuul` | Prohibited by D-13 and ADR-01/02/03. System is a modular monolith using Caffeine and PostgreSQL outbox. |
| **Prohibited Hardware/AI** | `qr_code_scanner`, `barcode_hardware_driver`, `velan_ai`, `llm_agent`, `langchain` | Barcode hardware drivers and AI agents are explicitly excluded by BR-058 and O-08. |
| **Prohibited Modules** | `billing_invoice`, `accounts_receivable`, `merc_integration` | Billing and MERC integration are strictly outside Production boundaries (BR-056, BR-057). |
| **Prohibited Fields** | `rm_heat_code` on `pieces` entity | Removed in Part 2.2 baseline; unverified assumption. Piece heat code is not an approved attribute. |

---

### 6. Technical Assumptions
1. **ASSUMPTION:** PostgreSQL identifier length limit is 63 bytes; all table, column, and constraint names will stay under 60 characters.
2. **ASSUMPTION:** Java identifiers and TypeScript names conform strictly to standard compiler limits.

---

### 7. Open Questions
* **O-NAM-01:** Does the project owner approve renaming table `subcontracts` to `sc_headers` (or `sales_contracts`) during Phase 7 database migration authoring? (Documented in Document 06).
