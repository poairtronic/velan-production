# Part 3.3 — Database and Naming Conventions
## Document 04: Database Constraints, Indexing Standards, and Query Plan Governance

---

### 1. Confirmed Rules (Frozen Baseline)
* **Single Authoritative Constraint Vocabulary (`Part 2.2 §1`):** Business invariants, role scopes, and state validity must be enforced at the database engine level via constraints, not merely in application code.
* **Mandatory Indexing on Foreign Keys (`Part 2.2 §2.1`):** Every foreign key column in PostgreSQL requires an explicit index to eliminate sequential scans during joins and prevent lock escalation.
* **Immutable Measurement Separation (`D-06, BR-032`):** Operator and QC measurements are stored in separate rows; check constraints restrict `recorded_by_role` to approved values.
* **Non-Blocking PM Dispositions (`D-07, BR-034`):** Check constraints restrict PM exception decisions strictly to the four approved outcomes: `CONTINUE`, `EXTRA_PROCESS`, `RETURN_TO_EARLIER_OPERATION`, or `REJECT_AND_RESTART`.

---

### 2. Proposed Design: Constraint Naming Standards

To maintain clean, searchable metadata across PostgreSQL system catalogs (`pg_constraint`, `pg_indexes`), constraints adhere to strict prefix standards:

| Constraint Type | Prefix | Syntax Pattern | Concrete Example |
| :--- | :---: | :--- | :--- |
| **Primary Key** | `pk_` | `pk_<table_name>` | `pk_pieces`, `pk_piece_operations` |
| **Foreign Key** | `fk_` | `fk_<table_name>_<target_table>` | `fk_pieces_subcontract_products`, `fk_subcontracts_purchase_orders` |
| **Unique Constraint** | `uq_` | `uq_<table_name>_<column_names>` | `uq_pieces_serial_number`, `uq_subcontract_products_sc_line` |
| **Check Constraint** | `ck_` | `ck_<table_name>_<rule_descriptor>`| `ck_roles_role_code`, `ck_pm_dispositions_decision` |
| **Standard B-Tree Index**| `ix_` | `ix_<table_name>_<column_names>` | `ix_pieces_sc_product_id`, `ix_piece_operations_piece_id` |
| **Partial Index** | `ix_` | `ix_<table_name>_<cols>_<predicate>` | `ix_piece_operations_active_station`, `ix_outbox_events_pending` |
| **GIN Index (JSONB)** | `gin_`| `gin_<table_name>_<column_name>` | `gin_outbox_events_payload`, `gin_audit_logs_diff_json` |

---

### 3. Business Rule Enforcement via Unique Constraints

Unique constraints physically enforce core Phase 1 business invariants:

| Constraint Name | Target Table | Columns Covered | Enforced Business Invariant |
| :--- | :--- | :--- | :--- |
| **`uq_subcontracts_sc_number`** | `subcontracts` | `(sc_number)` | Exactly one production record per RM Size Card (`BR-001, BR-003`). |
| **`uq_subcontract_products_sc_line`**| `subcontract_products`| `(sc_id, line_number)` | Unique line item per SC; permits duplicate products across lines (`BR-004`). |
| **`uq_pieces_serial_number`** | `pieces` | `(serial_number)` | System-wide unique physical identity for every steel piece (`BR-007`). |
| **`uq_set_allocations_piece`** | `set_component_allocations`| `(piece_id)` | A piece can be allocated to at most one assembled set (`BR-007`). |
| **`uq_assembled_sets_sc_product_seq`**| `assembled_sets` | `(sc_product_id, set_sequence_number)` | Deterministic, sequential set numbering per contract line (`BR-006`). |
| **`uq_packing_records_set_id`** | `packing_records` | `(set_id)` | A set can have only one confirmed physical packing verification (`BR-046`). |
| **`uq_templates_code_version`** | `process_templates` | `(template_code, version)` | Strict revision tracking per manufacturing process template (`BR-018`). |
| **`uq_users_employee_code`** | `users` | `(employee_code)` | Individual login per employee; no shared user accounts (`D-03`). |
| **`uq_user_roles_user_role`** | `user_roles` | `(user_id, role_id)` | Prevents duplicate role assignments to a single user account. |

---

### 4. Check Constraints for Domain States and Integrity Guardrails

Database check constraints guarantee that invalid states cannot be persisted:

```sql
-- Enforce the six confirmed system roles (D-03)
ALTER TABLE roles ADD CONSTRAINT ck_roles_role_code 
CHECK (role_code IN ('ROLE_ADMIN', 'ROLE_PM', 'ROLE_DESIGNER', 'ROLE_OPERATOR', 'ROLE_QC', 'ROLE_MANAGEMENT'));

-- Enforce the four approved PM dispositions (D-07, BR-034)
ALTER TABLE pm_dispositions ADD CONSTRAINT ck_pm_dispositions_decision 
CHECK (decision IN ('CONTINUE', 'EXTRA_PROCESS', 'RETURN_TO_EARLIER_OPERATION', 'REJECT_AND_RESTART'));

-- Mandatory PM justification notes (BR-034)
ALTER TABLE pm_dispositions ADD CONSTRAINT ck_pm_dispositions_justification 
CHECK (justification_notes IS NOT NULL AND BTRIM(justification_notes) <> '');

-- Enforce measurement recording roles (D-06, BR-032)
ALTER TABLE operation_measurements ADD CONSTRAINT ck_measurements_recorded_by_role 
CHECK (recorded_by_role IN ('OPERATOR', 'QC_INSPECTOR'));

-- Enforce binary quality inspection verdicts
ALTER TABLE qc_inspections ADD CONSTRAINT ck_qc_inspections_verdict 
CHECK (verdict IN ('PASSED', 'FAILED'));

-- Enforce positive target quantities
ALTER TABLE subcontract_products ADD CONSTRAINT ck_subcontract_products_target_qty 
CHECK (target_set_qty > 0);

ALTER TABLE bom_lines ADD CONSTRAINT ck_bom_lines_qty_per_set 
CHECK (qty_per_set > 0);

-- Enforce vendor DC quantity conservation (BR-040)
ALTER TABLE vendor_dc_trackers ADD CONSTRAINT ck_vendor_dc_quantities 
CHECK (dispatched_qty >= returned_qty AND returned_qty >= 0);
```

---

### 5. High-Throughput Partial and Composite Indexes

Partial indexes index only the active, pending subset of rows, keeping index size small and fully in-memory on Neon:

#### 5.1 Operator Active Worklist Queue
* **Index Definition:**
  ```sql
  CREATE INDEX ix_piece_operations_active_station 
  ON piece_operations(station_id, status) 
  WHERE status IN ('READY', 'OPENED');
  ```
* **Target Query:** Station operator tablet fetching active jobs awaiting machining.

#### 5.2 QC Inspection Pending Queue
* **Index Definition:**
  ```sql
  CREATE INDEX ix_piece_operations_pending_qc 
  ON piece_operations(status, sequence_order) 
  WHERE status = 'QC_PENDING';
  ```
* **Target Query:** Quality inspector worklist ordered by operational routing sequence.

#### 5.3 PM Active Exception Queue
* **Index Definition:**
  ```sql
  CREATE INDEX ix_qc_exceptions_active 
  ON qc_exceptions(status) 
  WHERE status = 'PENDING_PM_DISPOSITION';
  ```
* **Target Query:** PM priority cockpit displaying quarantined pieces awaiting disposition.

#### 5.4 Transactional Outbox Dispatcher
* **Index Definition:**
  ```sql
  CREATE INDEX ix_outbox_events_pending 
  ON outbox_events(status, next_retry_at) 
  WHERE status = 'PENDING';
  ```
* **Target Query:** Background poller acquiring pending ECR events via `FOR UPDATE SKIP LOCKED`.

---

### 6. Query-Plan Review Checklist for Heavy Worklists

Before any heavy dashboard or worklist query is approved for production:
1. **Explain Analyze Protocol:** Execute `EXPLAIN (ANALYZE, BUFFERS)` on a staging Neon database populated with representative production volumes (e.g. 50,000 pieces, 250,000 operations).
2. **Review Checklist:**
   - [ ] **Zero Sequential Scans:** Core tables (`pieces`, `piece_operations`, `operation_measurements`) execute strictly via `Index Scan` or `Bitmap Heap Scan`. Zero `Seq Scan`.
   - [ ] **Buffer Cache Hit Ratio:** Shared buffer hit ratio exceeds **99.0%** (`Buffers: shared hit=...`).
   - [ ] **Bounded Execution Time:** Query execution time is under **10 milliseconds** for p95 shop-floor queries.
   - [ ] **Sort Elimination:** Query sorting aligns with an existing index order, eliminating expensive in-memory or on-disk `Sort` steps.

---

### 7. Technical Assumptions
1. **ASSUMPTION:** PostgreSQL 16 optimizer selects partial indexes automatically when query `WHERE` predicates match the index filter condition.
2. **ASSUMPTION:** Foreign key indexes prevent parent table share-locks during child inserts.

---

### 8. Open Questions
* **O-IDX-01:** Should `operation_measurements` include a composite index on `(piece_operation_id, parameter_name, recorded_at DESC)` to accelerate tolerance history rendering? (Documented in Document 06).
