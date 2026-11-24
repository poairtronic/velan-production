# Velan Production — Documentation Update Report (2026-11-24)

## 1. Document Mapping

All 15 baseline Phase 1 documents and the Phase 1 README are located in `docs/phase-1/`. The new decision log was added directly next to them:

| Expected File | Repository Path | Action Taken |
|---|---|---|
| `00_DECISION_LOG.md` | `docs/phase-1/00_DECISION_LOG.md` | **Added** (copied from PART 00) |
| `01_BRD.md` | `docs/phase-1/01_BRD.md` | **Updated** per PART 01 |
| `02_SRS.md` | `docs/phase-1/02_SRS.md` | **Updated** per PART 02 |
| `03_Application_Scope.md` | `docs/phase-1/03_Application_Scope.md` | **Updated** per PART 03 |
| `04_Feature_List.md` | `docs/phase-1/04_Feature_List.md` | **Updated** per PART 04 |
| `05_User_Stories.md` | `docs/phase-1/05_User_Stories.md` | **Updated** per PART 05 |
| `06_Role_Matrix.md` | `docs/phase-1/06_Role_Matrix.md` | **Updated** per PART 06 |
| `07_Workflow_Diagrams.md` | `docs/phase-1/07_Workflow_Diagrams.md` | **Updated** per PART 07 |
| `08_Business_Rules.md` | `docs/phase-1/08_Business_Rules.md` | **Updated** per PART 08 |
| `09_Integration_Specification.md` | `docs/phase-1/09_Integration_Specification.md` | **Updated** per PART 09 |
| `10_Acceptance_Criteria.md` | `docs/phase-1/10_Acceptance_Criteria.md` | **Updated** per PART 09 |
| `11_End_to_End_Workflow.md` | `docs/phase-1/11_End_to_End_Workflow.md` | **Updated** per PART 11 |
| `12_Requirement_Analysis.md` | `docs/phase-1/12_Requirement_Analysis.md` | **Updated** per PART 12 |
| `13_Tech_Stack.md` | `docs/phase-1/13_Tech_Stack.md` | **Updated** per PART 13 |
| `14_SKILLS.md` | `docs/phase-1/14_SKILLS.md` | **Updated** per PART 14 |
| `15_Application_Master_Plan.md` | `docs/phase-1/15_Application_Master_Plan.md` | **Updated** per PART 15 |
| `README.md` | `docs/phase-1/README.md` | **Updated** per PART 16 |
| `DOCS_UPDATE_PACK.md` | `docs/updates/DOCS_UPDATE_PACK.md` | **Added** (single source of changes) |

---

## 2. Row-by-Row Changes Applied

### PART 01 — BRD (`01_BRD.md`)
- **Header / Status:** Applied. Added update notice referencing D-01 to D-19.
- **§1 Purpose:** Applied. Replaced last clause with automatic completion (Packed, SC Delivered) and billing outside Production (D-10).
- **§3 Obj. 1:** Applied. Added manual SC entry and Excel import (D-01, D-11).
- **§3 Obj. 3:** Applied. Updated to individual piece tracking and completion calculation (D-02).
- **§3 Obj. 6:** Applied. Added PM daily priority list per operator (D-15).
- **§3 Obj. 8:** Applied. Added replacement pieces with linked serials (D-08).
- **§3 Obj. 9:** Applied. Updated vendor processing and RM DC ownership (D-05).
- **§3 Obj. 10:** Applied. Updated material gate (issued yes/no per product) (D-04).
- **§3 Obj. 11:** Applied. Exceptions to ECR only (removed RM) (D-09).
- **§3 Obj. 12:** Applied. Replaced with automatic completion (D-10).
- **§3 New Obj. 14–15:** Applied. Added operator bulk grid and PM mistake addition.
- **§4 Included:** Applied. Added manual/Excel intake, PM priority, bulk grid, replacement pieces, 9 AM report. Removed QR scanning (O-08), vendor alerts, RM shortage analytics, packing verification, Dispatch Pending, dispatch confirmation, RM exception sending.
- **§4 Excluded:** Applied. Added packing/dispatch screens, QR/scanning, writes to RM, MERC/Velan Dashboard read-later notes (D-12).
- **§5 Table (RM & ECR rows):** Applied. Production sends nothing to RM; ECR receives exceptions and returns report number only.
- **§6 Product and piece model:** Applied. Replaced with piece model (D-02), proposed serial `SC-2579-APG-001` (O-02), marked set completion OPEN (O-03).
- **§7 Users:** Applied. Confirmed 6 roles (Admin, PM, Designer, Operator, QC, Management) (D-03).
- **§8 Lifecycle:** Applied. Replaced with new end-to-end lifecycle through automatic delivery.
- **§9 Outcomes:** Applied. Replaced packing/dispatch wording with piece-level completion and replacements history.
- **§10 Assumptions:** Applied. Closed decisions D-01 to D-13; preserved open items O-01, O-02, O-03, O-05, O-06, O-11.
- **§11 Approval:** Kept.

### PART 02 — SRS (`02_SRS.md`)
- **§2 Actors:** Applied. Added Designer role; removed "Design-side actors remain in RM" (D-03).
- **FR-001:** Applied. Added six roles, individual login, configurable credential length (O-09).
- **FR-002:** Applied. Replaced with RM sync, manual entry, Excel import, diff-based revisions, go-live rule (D-17).
- **FR-003:** Applied. PO → SC → product → piece hierarchy; removed QR/scanning (O-08); added `replaced_piece_id`.
- **FR-004:** Applied. Replaced BOM/set calculation with piece generation (D-02); set completion marked OPEN (O-03).
- **FR-005:** Applied. Added Designer flow selection, fixed machine/operator, 5 parameter types, flow snapshots (D-14).
- **FR-006:** Applied. Updated to Designer role; tolerance sheet format noted (O-06).
- **FR-007:** Applied. Added PM daily priority list per operator (D-15); due date noted (O-01).
- **FR-008:** Applied. Ready/opened/submitted timestamps; no start/stop buttons; operator picks from queue (D-16).
- **FR-009:** Applied. Replaced with material-issued gate (yes/no per product) (D-04).
- **FR-010:** Applied. Bulk grid entry with paste from Excel; five parameter types (D-14, D-16).
- **FR-011:** Applied. QC on every process; blind QC; removed separate override request (D-06, D-07).
- **FR-012:** Applied. Four PM decisions (CONTINUE with reason, EXTRA PROCESS, RETURN TO EARLIER OP, REJECT AND RESTART); replacement release gate (D-08, BR-061); send to ECR only (D-09).
- **FR-013:** Applied. Replaced with reading RM DC details and return status; tagging returned pieces; sequential in-house routing (D-05).
- **FR-014:** Applied. Replaced with automatic completion (Packed, SC Delivered) (D-10).
- **FR-015:** Applied. Dashboard updated; daily 9 AM report added; Admin-managed notifications kept.
- **FR-016:** Kept.
- **§4 NFR:** Applied. In-process Caffeine cache (no Redis); database outbox (no RabbitMQ); sync log + idempotent upsert.
- **§5 Entities:** Applied. Removed QR, set lineage, packing/dispatch confirmations; added pieces, `replaced_piece_id`, flow snapshot, qc_decisions, exception_records, ecr_outbox, vendor_batches, daily_priority, import_batches, material_issued flag.

### PART 03 — Application Scope (`03_Application_Scope.md`)
- **§2 In-scope modules:** Applied. All 17 module items updated matching pack requirements.
- **§3 Out of scope:** Applied. Added QR/scanning, packing/dispatch screens, writes to RM, vendor selection/dates; updated MERC/Velan Dashboard (D-12).
- **§4 Responsibility matrix:** Applied. Updated rows for vendor DC, exception costing, completion, and material issued.
- **§5 Boundary rules:** Applied. Replaced packing/dispatch distinctness with automatic completion (D-10); added no writes to RM; ECR costing boundary kept.
- **§6 Scope-change control:** Kept.

### PART 04 — Feature List (`04_Feature_List.md`)
- **FEAT-001 to FEAT-006:** Applied. Individual login, six roles, RM sync + manual + Excel, sync log, piece hierarchy.
- **FEAT-007:** Applied. Set tracking removed from baseline; marked OPEN (O-03).
- **FEAT-008:** Applied. Unique piece serial with link to product and scrapped parent.
- **FEAT-009:** Applied. QR/scanning removed; manual search retained.
- **FEAT-011 to FEAT-016:** Applied. Designer selection, snapshots, Designer upload, verification, PM approval.
- **FEAT-018 to FEAT-025:** Applied. PM daily priority, bulk grid, timestamps, material gate, blind QC, non-blocking alerts.
- **FEAT-026:** Applied. Removed separate override; replaced by FEAT-044.
- **FEAT-027 to FEAT-032:** Applied. Extra process, reject and restart (replacement piece), vendor tracking from RM DC, RM inward tag, ECR send only.
- **FEAT-033:** Applied. Removed Production-to-RM exception integration (D-09).
- **FEAT-034 to FEAT-038:** Applied. Updated dashboard metrics, targets, notifications, reports, audit.
- **FEAT-039 to FEAT-040:** Applied. Packing and dispatch removed; replaced by FEAT-045.
- **FEAT-041:** Kept. Admin cancellation.
- **FEAT-042 to FEAT-050 (New features):** Applied. Added FEAT-042 (Manual SC entry), FEAT-043 (Excel SC import), FEAT-044 (PM QC decision), FEAT-045 (Automatic completion), FEAT-046 (Bulk entry grid), FEAT-047 (Replacement release gate), FEAT-048 (Daily 9 AM report), FEAT-049 (Reconciliation screen), FEAT-050 (In-progress SC import).
- **Feature exclusions:** Applied. Added no QR/scanning, no packing/dispatch screens, no writes to RM.

### PART 05 — User Stories (`05_User_Stories.md`)
- **Designer section:** Applied. Added DSG-US-001, DSG-US-002, DSG-US-003.
- **Admin section:** Applied. Updated ADM-US-001 with individual login credentials; kept ADM-US-002 to 006.
- **PM section:** Applied. PM-US-001 (RM/manual/Excel), PM-US-002 (daily priority), replaced PM-US-005 (4 PM decisions), removed PM-US-007 (added continue with reason), PM-US-008 (vendor return status), added PM-US-010 (manual material flag), PM-US-011 (manual replacement release), PM-US-012 (in-progress SC import).
- **Operator section:** Applied. OP-US-002 (pick from queue, no scanning), OP-US-003 (material issued block), OP-US-005 (ready pieces), added OP-US-007 (bulk grid and Excel paste).
- **QC section:** Applied. Removed QC-US-005; QC-US-006 (RM inward tag); added QC-US-008 (independent QC on every process).
- **Management section:** Applied. MGT-US-001 updated metrics; added MGT-US-005 (daily 9 AM report).
- **Packing & Dispatch section:** Applied. Removed PK-US-001/002 and DSP-US-001/002; added CMP-US-001 (automatic completion).
- **Acceptance note:** Applied. Added negative tests.

### PART 06 — Role Matrix (`06_Role_Matrix.md`)
- **§1 Title:** Applied. Six confirmed roles.
- **§1 Table:** Applied. Added Designer column; updated rows for manual SC/Excel, RM in-progress import, sync status, material flag, replacement release, daily priority list, flow selection, template management (O-12), vendor tracking, QC decisions. Removed override approval, packing confirmation, dispatch confirmation.
- **§2–§3 RBAC & Suspension:** Kept.
- **§4 Approval authority:** Applied. Removed QC override approval; added PM decisions with mandatory reason.
- **§5 Role naming note:** Applied. Clarified Designer as primary; Stores, Packing, Dispatch, Vendor Coordinator not roles.

### PART 07 — Workflow Diagrams (`07_Workflow_Diagrams.md`)
- **Diagram 1 (Overall lifecycle):** Applied. Replaced with SC → Production → Delivered.
- **Diagram 2 (Material gate):** Applied. Replaced with issued flag check.
- **Diagram 3 (Operator/QC loop):** Applied. Replaced with bulk grid, blind QC, 4 PM decisions.
- **Diagram 4 (Tolerance revision):** Applied. Kept diagram with "Design" changed to "Designer".
- **Diagram 5 (Vendor operation):** Applied. Replaced with RM DC read, return status, piece tagging.
- **Diagram 6 (Exception to ECR):** Applied. Replaced with ECR outbox send; nothing to RM.
- **Diagram 7 (Completion):** Applied. Replaced packing/dispatch with automatic piece Packed → SC Delivered.
- **Diagram 8 (RM sync):** Applied. Replaced with polling, read-only service account, diff-based revisions.
- **§9 Status principles:** Applied. Replaced with 6 explicit status rules.

### PART 08 — Business Rules (`08_Business_Rules.md`)
- **Modified rules:** Applied. BR-001, BR-003, BR-004, BR-006, BR-007, BR-009, BR-011, BR-012, BR-013, BR-015, BR-018, BR-021, BR-022, BR-025, BR-033, BR-034, BR-037, BR-039, BR-040, BR-041, BR-043, BR-057.
- **Removed rules:** Applied. Marked BR-005, BR-008, BR-010, BR-045, BR-046, BR-047, BR-048 as removed.
- **Unchanged rules:** Kept intact.
- **New rules (BR-059 to BR-070):** Applied. Added BR-059 (piece Packed), BR-060 (SC Delivered), BR-061 (replacement release gate), BR-062 (ECR once-only send), BR-063 (identical lifecycle), BR-064 (RM revision diffs), BR-065 (vendor return sequential progression), BR-066 (QC on every process), BR-067 (Production never writes to RM), BR-068 (quantities derived from piece status), BR-069 (in-progress SC import on PM request), BR-070 (extra process routing).

### PART 09 — Integration Specification (`09_Integration_Specification.md`)
- **Document Body:** Applied full rewrite according to the pack structure.
- **§1 Approach:** Applied. RM read-only polling, ECR intake, no writes to RM, built in the flow.
- **§2 Data flows:** Applied. Table INT-001 to INT-010 (exceptions to RM removed).
- **§3 RM read endpoints:** Applied. Listed all 13 endpoints.
- **§4 RM field mapping:** Applied. Table of real database entities/fields with API properties marked VERIFY.
- **§5 Production → ECR:** Applied. Header `x-internal-secret`, payload fields, report number storage, once-only idempotency.
- **§6 Reliability & security:** Applied. Sync log, backoff, read-only account, reconciliation screen.
- **§7 Field checklist:** Applied. Kept checklist columns; marked external names VERIFY.
- **§8 Excluded:** Applied. No RM writes, no DC/stock transactions, no billing, no MERC/Velan Dashboard.

### PART 10 — Acceptance Criteria (`10_Acceptance_Criteria.md`)
- **Modified/Replaced:** Applied. AC-001, AC-002, AC-003, AC-004, AC-006, AC-007, AC-009, AC-010, AC-011, AC-013, AC-014, AC-018, AC-019.
- **Removed:** Applied. AC-005 (partial dispatch of sets, O-03).
- **Unchanged:** Kept. AC-008, AC-012, AC-015, AC-016, AC-017, AC-020 (packing/dispatch totals removed).
- **New criteria (AC-021 to AC-028):** Applied. Added AC-021 (manual/Excel intake), AC-022 (bulk grid), AC-023 (replacement release gate), AC-024 (RM diff revisions), AC-025 (Designer role), AC-026 (PM daily priority), AC-027 (9 AM report), AC-028 (ambiguous ECR send).

### PART 11 — End-to-End Workflow (`11_End_to_End_Workflow.md`)
- **Stage 1 to Stage 11:** Applied. All 11 stages updated matching narrative flow in the pack.

### PART 12 — Requirement Analysis (`12_Requirement_Analysis.md`)
- **§2 to §10:** Applied. Boundaries, 6 roles, piece hierarchy, piece model, material gate, 5 parameter types, QC decisions, vendor flow, automatic completion.
- **§12 Conflict table:** Applied. All rows replaced with controlling decisions.
- **§13 Open items:** Applied. Closed decisions marked; open items O-01 to O-12 marked `OPEN — BUSINESS DECISION REQUIRED`.
- **§14 Source fidelity:** Applied. Field names from code audits noted.

### PART 13 — Tech Stack (`13_Tech_Stack.md`)
- **Header:** Applied. Decided baseline (D-13).
- **§1 Diagram:** Applied. Caffeine and database outbox added; Redis and RabbitMQ removed; RM read and ECR intake updated.
- **§2 Table:** Applied. Stack decisions updated (Java 21, Spring Boot, React, Vite, Neon, R2, Render, Caffeine, SMTP).
- **§3 Decisions status:** Applied. Closed vs open decisions categorized.
- **§4 Modules:** Applied. 17 modules aligned.
- **§5 Guardrails:** Applied. Additional guardrails added.

### PART 14 — SKILLS.md (`14_SKILLS.md`)
- **§1 to §11:** Applied. Updated mission, source-of-truth hierarchy (00_DECISION_LOG.md #1), non-negotiable rules, architecture, data integrity, integration, UI, testing, and forbidden shortcuts.

### PART 15 — Application Master Plan (`15_Application_Master_Plan.md`)
- **§1 to §5:** Applied. Product definition, boundaries, modules, data model, and piece status model updated.
- **§6 51-Phase Plan:** Applied. Full 51-phase roadmap inserted replacing phases 0–8.
- **§7 to §9:** Applied. Testing, delivery governance, and updated risks table.

### PART 16 — README (`docs/phase-1/README.md`)
- **Intro & Table:** Applied. Intro updated; row 0 (`00_DECISION_LOG.md`) added; purpose descriptions updated.
- **Important notes:** Applied. Stack baseline updated; field names note updated; automatic completion (D-10) added.
- **Phase 2 roadmap:** Applied. Planned Phase 2 documents listed.

---

## 3. Skipped / Unmatched Pack Rows

**None.** Every row and instruction in `docs/updates/DOCS_UPDATE_PACK.md` was matched, applied, and verified against the corresponding document.

---

## 4. Consistency Search Results (Step 9)

An automated regex scan across `docs/phase-1/*.md` verified that no unintended deprecated concepts remain:

| Term Searched | Findings / Context | Evaluation |
|---|---|---|
| `Dispatch Pending` | Only present in BR-046 [REMOVED note] and 12_Requirement_Analysis conflict table (as superseded older draft). | **PASS** — allowed historical/removal reference only. |
| `dispatch confirmation` | Only present in D-10, negative exclusion lists ("No packing/dispatch screens, statuses, roles or dispatch confirmation"), BR-047 [REMOVED note], and Role Matrix note. | **PASS** — allowed negative/removal reference only. |
| `dispatch employee` | Only present in 12_Requirement_Analysis conflict table (superseded older draft). | **PASS** — allowed historical reference only. |
| `Packing confirmation` | Only present in Role Matrix note [REMOVED]. | **PASS** — allowed removal reference only. |
| `Redis` | Only present in D-13, stack exclusions ("No Redis", "Caffeine in-process cache; no Redis"), and conflict table. | **PASS** — allowed negative exclusion reference only. |
| `RabbitMQ` | Only present in D-13, stack exclusions ("No RabbitMQ", "Database outbox/sync log; no RabbitMQ"), and conflict table. | **PASS** — allowed negative exclusion reference only. |
| `five roles` / `5 roles` | 0 occurrences. (All documents now reference the confirmed six primary roles). | **PASS** — completely eliminated. |
| `override request` | Only present in D-07, SRS FR-011 removal note, and conflict table (marked as replaced by PM CONTINUE with reason). | **PASS** — allowed removal reference only. |
| `vendor alert` / `expected return` | Only present in negative exclusions lists ("Production tracks no vendor expected return dates and sends no alerts to RM"). | **PASS** — allowed negative exclusion reference only. |
| `QR` / `scanning` | Only present in deferred notes ("QR codes and scanning deferred under OPEN — BUSINESS DECISION REQUIRED: O-08") and negative exclusions. | **PASS** — allowed deferred/removal reference only. |
| `to RM` / `sending exceptions to RM` | Only present in explicit prohibitions ("Production sends nothing to RM", "Production never writes to RM", INT-003/004/005 [REMOVED]). | **PASS** — allowed prohibition reference only. |

---

## 5. Git Commit Log on Branch `docs/update-2026-11-24`

```text
2003531 docs: add 00_DECISION_LOG.md per decision log 2026-11-24
5c0b460 docs: update 01_BRD.md per decision log 2026-11-24
d773ee1 docs: update 02_SRS.md per decision log 2026-11-24
0d99c5d docs: update 03_Application_Scope.md per decision log 2026-11-24
e316629 docs: update 04_Feature_List.md per decision log 2026-11-24
7f61211 docs: update 05_User_Stories.md per decision log 2026-11-24
69f8ea2 docs: update 06_Role_Matrix.md per decision log 2026-11-24
382f761 docs: update 07_Workflow_Diagrams.md per decision log 2026-11-24
02e34df docs: update 08_Business_Rules.md per decision log 2026-11-24
cafd120 docs: update 09_Integration_Specification.md per decision log 2026-11-24
b671234 docs: update 10_Acceptance_Criteria.md per decision log 2026-11-24
4b3b765 docs: update 11_End_to_End_Workflow.md per decision log 2026-11-24
0993fca docs: update 12_Requirement_Analysis.md per decision log 2026-11-24
ab0c756 docs: update 13_Tech_Stack.md per decision log 2026-11-24
ba4213c docs: update 14_SKILLS.md per decision log 2026-11-24
012bae6 docs: update 15_Application_Master_Plan.md per decision log 2026-11-24
cdb9882 docs: update README.md per decision log 2026-11-24
```
