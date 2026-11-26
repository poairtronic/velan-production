# Part 3.3 — Database and Naming Conventions
## Document 05: Phase 2 Data Model Conformance Audit and Deviation Findings

---

### 1. Overview and Audit Scope

This document provides a systematic audit comparing the entity and attribute definitions in **Phase 2 Part 2.2** (`docs/phase-2/2.2-domain-data-model/`) against the database naming and data type conventions established in **Part 3.3** (Documents 01 through 04).

* **Strict Non-Destructive Policy:** In adherence to Phase 3 common rules, Phase 2 documents are frozen baselines and have **not** been modified.
* **Implementation Target:** The recommended corrections recorded below will be applied during **Phase 7 (Database Design and Migrations)** when authoring physical Flyway migration scripts.

---

### 2. Systematic Conformance Audit Matrix

| # | Part 2.2 Document Reference | Entity / Column Name in Part 2.2 | Identified Deviation from Part 3.3 Standard | Recommended Correction | Target Phase |
|---|---|---|---|---|:---:|
| 1 | `02-entity-catalogue...:11-15` | `created_by`, `updated_by` (`VARCHAR(100)`) | Audit user columns modeled as text username rather than standard foreign key UUID to `users.id`. | Change to `created_by_user_id UUID NOT NULL` and `updated_by_user_id UUID NOT NULL`. | Phase 7 |
| 2 | `02-entity-catalogue...:15` | `version` (`BIGINT`) | Version column declared as `BIGINT`. Standard JPA `@Version` default maps to 32-bit `INTEGER`. | Change to `version INTEGER NOT NULL DEFAULT 0`. | Phase 7 |
| 3 | `02-entity-catalogue...:36` | Table: `subcontracts` | Table name uses "subcontract" which connotes outsourced vendor work, conflicting with factory work order scope. | Rename table to `sc_headers` (or `sales_contracts`), keeping `sc_id` and `sc_number`. | Phase 7 |
| 4 | `02-entity-catalogue...:49` | Table: `subcontract_products` | Uses "subcontract" prefix. | Rename table to `sc_products` (or `sales_contract_products`). | Phase 7 |
| 5 | `02-entity-catalogue...:72` | Table: `sc_revisions` | Uses abbreviated `sc_` while parent was `subcontracts`. | Rename table to `sc_header_revisions` for structural parity with `sc_headers`. | Phase 7 |
| 6 | `02-entity-catalogue...:67` | Column: `material_ready` | Boolean readiness flag missing standard `is_` prefix. | Rename column to `is_material_ready BOOLEAN NOT NULL DEFAULT false`. | Phase 7 |
| 7 | `02-entity-catalogue...:86` | Column: `approved_by_pm` (`BOOLEAN`) | Boolean flag does not record which user approved the revision or at what timestamp. | Replace with `approved_by_user_id UUID` and `approved_at TIMESTAMPTZ`. | Phase 7 |
| 8 | `02-entity-catalogue...:133`| Column: `process_templates.approved_by` | String text (`VARCHAR(100)`) instead of relational foreign key. | Change to `approved_by_user_id UUID REFERENCES users(id)`. | Phase 7 |
| 9 | `02-entity-catalogue...:199`| Table: `drawing_documents` | Generic noun `documents` for engineering drawings and CAD blueprints. | Rename table to `engineering_drawings` (clear domain noun). | Phase 7 |
| 10| `02-entity-catalogue...:231`| Column: `pieces.snapshot_id` | Foreign key does not follow `<target_table_singular>_id` pattern (`process_template_snapshots`). | Rename to `process_template_snapshot_id UUID NOT NULL`. | Phase 7 |
| 11| `02-entity-catalogue...:233`| Column: `pieces.replaced_piece_id` | Self-referencing FK represents parent scrapped piece in scrap/restart workflow. | Rename to `parent_scrapped_piece_id UUID` (or preserve as documented domain alias). | Phase 7 |
| 12| `02-entity-catalogue...:313`| Column: `qc_inspections.inspector_id` | Column name omits `_user_id` suffix. | Rename to `inspector_user_id UUID NOT NULL REFERENCES users(id)`. | Phase 7 |
| 13| `02-entity-catalogue...:332`| Column: `qc_exceptions.disposition` | String representation instead of explicit foreign key pointer to `pm_dispositions`. | Rename to `pm_disposition_id UUID REFERENCES pm_dispositions(id)`. | Phase 7 |
| 14| `02-entity-catalogue...:377`| Table: `piece_replacement_lineage` | Abstract singular noun `lineage` for junction table linking parent piece to child replacement. | Rename table to `piece_replacements` (standard plural entity naming). | Phase 7 |
| 15| `02-entity-catalogue...:408`| Table: `vendor_dc_trackers` | Uses agentive noun "trackers" rather than plural entity noun. | Rename table to `vendor_dc_records` or `vendor_delivery_challans`. | Phase 7 |
| 16| `02-entity-catalogue...:481`| Column: `packing_records.verified_by` | Text string instead of relational user ID. | Rename to `verified_by_user_id UUID NOT NULL REFERENCES users(id)`. | Phase 7 |
| 17| `02-entity-catalogue...:495`| Column: `dispatch_confirmations.dispatched_by`| Text string instead of relational user ID. | Rename to `dispatched_by_user_id UUID NOT NULL REFERENCES users(id)`. | Phase 7 |
| 18| `06-constraints...:11` | Constraint: `uq_subcontract_products_sc_line` | Constraint name references legacy table name `subcontract_products`. | Align constraint name to `uq_sc_products_sc_line` upon table renaming in Phase 7. | Phase 7 |

---

### 3. Detailed Analysis of Key Deviations

#### 3.1 User Identity References: String Usernames vs. Foreign Key UUIDs
* **Part 2.2 Baseline:** Several tables (`sc_revisions`, `process_templates`, `packing_records`, `dispatch_confirmations`) recorded user identification as text strings (e.g. `created_by VARCHAR(100)`, `verified_by VARCHAR(100)`).
* **Part 3.3 Rule:** All user references must be relational foreign keys of type `UUID` pointing to `users(id)`:
  - `created_by_user_id UUID NOT NULL REFERENCES users(id)`
  - `verified_by_user_id UUID NOT NULL REFERENCES users(id)`
* **Rationale:** Enforces referential integrity, guarantees individual login traceability (`D-03`), prevents orphan strings when users are renamed, and supports database-level audit queries.

#### 3.2 Foreign Key Naming Consistency (`<target_singular>_id`)
* **Part 2.2 Baseline:** `pieces.snapshot_id` referenced table `process_template_snapshots`.
* **Part 3.3 Rule:** Foreign keys must strictly mirror the singular target table name: `process_template_snapshot_id`.
* **Rationale:** Enables automated ORM reverse-engineering and clear schema self-documentation.

#### 3.3 Entity Table Pluralization Standards
* **Part 2.2 Baseline:** Used abstract collective or agentive nouns (`piece_replacement_lineage`, `vendor_dc_trackers`).
* **Part 3.3 Rule:** Table names must be standard plural domain entities: `piece_replacements`, `vendor_delivery_challans`.

---

### 4. Technical Assumptions
1. **ASSUMPTION:** Applying these naming alignments in Phase 7 Flyway migrations (`V001` through `V010`) introduces zero breaking changes because no database tables or migrations currently exist in the repository.
2. **ASSUMPTION:** JPA entity mappings authored in Phase 8 will bind directly to the corrected Phase 7 table and column names.

---

### 5. Open Questions
* **O-CONF-01:** Does the project owner approve applying all 18 table and column corrections directly into the initial Flyway migration scripts during Phase 7? (Documented in Document 06).
