# Part 3.3 — Synthesis Report, Database Governance, and Conflict Register
## Document 06: Part 3.3 Synthesis Report, Conformance Findings, and Open Questions

---

### 1. Executive Summary & Deliverables Completed

Part 3.3 establishes the definitive database design standards, data type mapping rules, constraint conventions, Flyway migration governance, and domain naming standards for the Velan Production platform. It provides the concrete relational persistence rules required before Phase 7 physical database migration authoring.

The following six specification documents have been authored under `docs/phase-3/3.3-database-and-naming-conventions/`:
1. **[01-glossary-and-naming-conventions.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.3-database-and-naming-conventions/01-glossary-and-naming-conventions.md):** Comprehensive domain glossary; evaluation and recommendation for the "SC" terminology problem (recommending neutral technical name `sc_headers`); naming standards for database objects, Java, TypeScript, API paths, permissions, and error codes; explicit "names that must never appear" catalogue.
2. **[02-schema-and-data-type-conventions.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.3-database-and-naming-conventions/02-schema-and-data-type-conventions.md):** Prescriptive PostgreSQL 16 data type matrix; justification for prohibiting native PG enums in favor of `VARCHAR(50)` + `CHECK`; required columns across entity categories; status-history table pattern; soft delete / cancellation protocol; defense-in-depth protection for append-only tables (privilege revocation + trigger); structured physical serial storage standard; JSONB constraints.
3. **[03-flyway-and-migration-standards.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.3-database-and-naming-conventions/03-flyway-and-migration-standards.md):** Flyway file naming (`V{n}__description.sql`), monotonic numbering, forward-only policy, schema vs data-fix separation, repeatable migrations policy, baseline configuration, 8-point pre-flight checklist, Testcontainers automated testing strategy, rollback disaster recovery policy, and the mandatory Neon Direct connection rule.
4. **[04-constraints-and-indexing-standards.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.3-database-and-naming-conventions/04-constraints-and-indexing-standards.md):** Constraint naming conventions (`pk_`, `fk_`, `uq_`, `ck_`, `ix_`, `gin_`); business rule unique constraints (BR-001, BR-004, BR-007, BR-046); check constraints for roles, dispositions, and measurements; high-throughput partial indexes for worklists; `EXPLAIN (ANALYZE, BUFFERS)` query-plan review checklist.
5. **[05-phase-2-conformance-findings.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.3-database-and-naming-conventions/05-phase-2-conformance-findings.md):** Exhaustive audit comparing Part 2.2 against Part 3.3 standards, cataloguing 18 specific naming and data type deviations with exact file/line citations, recommended corrections, and Phase 7 implementation target.
6. **[06-part-3.3-report.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.3-database-and-naming-conventions/06-part-3.3-report.md):** (This document) Comprehensive synthesis, conflict register, assumptions audit, and open questions.

---

### 2. Comprehensive Conflict Register

In accordance with `SKILLS.md §0`, all known conflicts remain formally OPEN without unilateral engineering decisions:

#### Conflict C-1: Completion Model — D-10 vs. Phase 2 Parts 2.2 and 2.6 (OPEN)
* **Conflict Statement:** Decision `D-10` specifies that a piece is Packed when all its operations pass; a product is complete when all its pieces are Packed; the SC becomes Delivered automatically without packing or dispatch screens/statuses/roles. Conversely, Phase 2 Parts 2.2 and 2.6 specify multi-component assembled sets (`assembled_sets`), physical packing checklists (`packing_records`), staging bays (`DISPATCH_PENDING`), and loading dock gate releases (`dispatch_confirmations`). Production never creates vendor DCs.
* **Database Standards Status:** **OPEN.** Tables supporting assembled sets and packing records are catalogued, but the completion trigger remains an open architectural decision for the project owner.

#### Conflict C-2: Piece Count Derivation Source (OPEN)
* **Conflict Statement:** Decision `D-02` specifies that piece count per product is derived directly from the quantity RM sends for that product line. Phase 2 Part 2.2 calculates piece demand as target assembled sets $\times$ component BOM ratio.
* **Database Standards Status:** **OPEN.** The schema supports both direct quantity inputs and calculated set ratios without schema-level structural modification.

#### Conflict C-3: The "SC" Terminology Problem (OPEN with Recommendation)
* **Conflict Statement:** "SC" carries four different meanings across systems: RM screen "Size Card (SC Code)", Phase 1/2 prose "Size Card", Phase 2 tables `subcontracts`, and ECR field "Size Card number".
* **Recommendation:** Adopt neutral technical table names `sc_headers` and `sc_products` (or `sales_contracts`), avoiding "subcontract" unless the business explicitly confirms it represents outsourced vendor fabrication.
* **Status:** **OPEN.** Submitted for project owner direction before Phase 7 migration authoring.

---

### 3. Consolidated Technical Assumptions Register

1. **ASSUMPTION:** PostgreSQL identifier length limit is 63 bytes; all table, column, index, and constraint names stay comfortably under 60 characters.
2. **ASSUMPTION:** PostgreSQL 16 `gen_random_uuid()` generates cryptographically random UUIDv4 identifiers without lock contention.
3. **ASSUMPTION:** Defense-in-depth protection combining PostgreSQL privilege revocation (`REVOKE UPDATE, DELETE`) with database triggers provides 100% tamper-evident immutability for measurement and audit tables.
4. **ASSUMPTION:** Neon Serverless PostgreSQL compute node supports statement timeouts of 60 seconds and lock timeouts of 10 seconds during Flyway DDL execution.
5. **ASSUMPTION:** Applying the 18 Phase 2 conformance corrections during Phase 7 Flyway authoring introduces zero breaking changes since no physical database tables currently exist.

---

### 4. Open Questions Submitted for the Project Owner

* **Conflict C-1 (Completion Workflow):** Does the owner confirm the simplified automatic cascade of Decision D-10 or the multi-component assembled set and dispatch staging bay workflow defined in Phase 2.6?
* **Conflict C-2 (Piece Count Derivation):** Does the owner confirm that piece counts are derived purely from RM product quantities (D-02) or calculated via target sets $\times$ component BOM ratio?
* **O-NAM-01 (Table Renaming):** Does the owner approve renaming table `subcontracts` to `sc_headers` (or `sales_contracts`) and `subcontract_products` to `sc_products` during Phase 7 migration authoring?
* **O-CONF-01 (Phase 2 Conformance Corrections):** Does the owner approve applying all 18 table and column corrections identified in Document 05 directly into the initial Flyway migration scripts during Phase 7?
* **O-DB-01 (Read-Only Database Role):** Should Flyway migrations create a dedicated read-only role (`velan_readonly_user`) in Neon for production monitoring and reporting dashboards?
* **O-MIG-01 (Production Flyway Execution):** Should production Flyway migrations be executed during Render Docker container startup (in-app migration), or triggered as a discrete Render pre-deploy release command?
