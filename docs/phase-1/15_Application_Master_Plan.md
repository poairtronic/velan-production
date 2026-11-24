# 15 — Complete Application Details and Development Plan

Updated 2026-11-24 — decisions D-01 to D-19 (see 00_DECISION_LOG.md)

## 1. Product definition
Velan Production is the manufacturing execution and traceability application between RM V-SMART and the final customer handoff. It converts RM SC information (or direct manual/Excel entries) into traceable products and individual pieces, plans operations and operator daily priorities, records bulk measurements per piece, controls independent QC and PM exception decisions, tracks vendor processing and material readiness, and completes automatically to Delivered. (D-10)

## 2. Product boundaries
- **Inputs from RM:** SC, PO, customer, product lines and quantities, material issued yes/no, vendor DC details and return status, ECR material issue status (all read-only via service account).
- **Outputs to RM:** **None** (Production never sends anything to RM; D-09, BR-067).
- **Outputs to ECR:** Exceptions (rejection, rework, additional process) sent via database outbox; ECR returns an acknowledgement report number.
- **Not returned from ECR:** Costing/responsibility results (ECR defect costing remains outside Production).
- **Outside this application:** Billing, stock/inventory transactions, Vendor DC transactions, MERC, Velan Dashboard, and AI Agent.

## 3. Main application modules
### 3.1 Identity and administration
Login with individual credentials per employee, six confirmed roles (Admin, PM, Designer, Operator, QC, Management), dynamic RBAC, custom permissions, user overrides, suspension, audit/security logs, and Admin-managed notification settings.

### 3.2 SC intake (manual entry, Excel import, RM sync)
Intake via read-only RM sync, permanent manual SC entry, and Excel SC import with validation, preview, and audit trail. Deduplication on RM SC id and SC number; diff-based RM revision tracking; in-progress SC import on PM request. (D-01, D-17)

### 3.3 Product and piece traceability
PO → SC → product → piece hierarchy. Unique piece serials, product-level piece generation, `replaced_piece_id` scrap lineage, search by PO/SC/product/serial. (QR labels and scanning deferred under OPEN — BUSINESS DECISION REQUIRED: O-08). Set concept is OPEN — BUSINESS DECISION REQUIRED (O-03).

### 3.4 Process templates and flow snapshots
Reusable process templates, Designer selection of process flow per product, fixed machine and operator (or allowed vendor) per operation, five parameter types (numeric, yes/no, text, signature, image), and immutable flow snapshots per product. (D-14)

### 3.5 Design drawing and tolerance management
Drawing/CAD PDF upload, Excel/PDF tolerance upload, extraction/mapping, Designer verification, PM approval, revisions, notifications, and immutable historical measurements.

### 3.6 Planning, scheduling and PM daily priority
SC queue, priority, employee workload/capacity, shift/calendar/holiday setup, PM daily priority list per operator, rescheduling with reason, bottlenecks, 7/15/30-day forecasts, and performance targets. (D-15)

### 3.7 Operator execution
Operator queue ordered by PM daily priority, operation instructions, material readiness gate, bulk measurement entry grid (rows = pieces, columns = parameters) with paste from Excel, ready/opened/submitted timestamps, no start/stop buttons, and submission to QC. (D-16)

### 3.8 QC and exception management
Independent QC inspection on every operation (blind QC), tolerance comparison, non-blocking failure alerts, piece waiting for PM decision, four binding PM outcomes (CONTINUE with reason, EXTRA PROCESS, RETURN TO EARLIER OP, REJECT AND RESTART), replacement piece generation, and full audit. (D-06, D-07, D-08, D-19)

### 3.9 Vendor tracking
Vendor tracking from RM DC details (vendor, DC number, process, product, quantity), RM return status (received/partial/not received), tagging returned pieces at inward, QC inspection on return, and sequential progression. No vendor selection, no alerts to RM, no vendor dates. (D-05)

### 3.10 Material gate
Read-only RM material-issued flag (yes/no per product) from RM sync or manual PM/Admin setting; blocks product pieces from starting or taking data entry until set. No inventory transactions or shortage analytics. (D-04)

### 3.11 RM connector (read) and ECR sender
Read-only RM polling connector with service account; database outbox for ECR with at-most-once delivery (never auto-resend after ambiguous timeout); sync log; reconciliation screen. (D-09, D-11, BR-062)

### 3.12 Dashboards, reports and notifications
Live dashboard (machines in use, vendors, out-of-tolerance values, SCs near completion, rejection quantity, planned vs actual), daily 9 AM report (Excel + charts), in-app/email alerts, and Admin-managed preferences.

### 3.13 Completion (Packed, Delivered)
Automatic completion engine: a piece is Packed when all its operations pass; a product completes when all its pieces are Packed; the SC is Delivered automatically when all products are complete. Billing is outside Production. No packing or dispatch screens, statuses, roles or dispatch confirmation. (D-10)

## 4. Core data model
Recommended domains (logical entities):
- Identity: users, roles, permissions, user_roles, role_permissions, user_permission_overrides, user_status_history, sessions.
- Commercial intake: purchase_orders, sales_contracts, sc_revisions, sc_products, import_batches, sync_log.
- Product & piece: products, pieces, piece_lineage (`replaced_piece_id`), flow_snapshots.
- Process: process_templates, template_versions, operations, operation_parameters, standard_times.
- Tolerance: tolerance_uploads, extraction_items, tolerance_revisions, tolerance_values, tolerance_approvals.
- Execution: daily_priority, work_assignments, operation_executions (ready/opened/submitted timestamps), operator_measurements.
- Quality: qc_inspections, qc_measurements, qc_decisions.
- Exceptions & outbox: qc_exceptions, pm_dispositions, ecr_outbox, exception_records (with ECR report number).
- Vendor: vendor_batches, vendor_batch_pieces.
- Material gate: material_issued_flags, rm_issue_snapshots.
- Planning: shifts, work_calendar, holidays, employee_capacity, schedules, schedule_changes, production_targets.
- Operations support: notifications, notification_settings, audit_events.
*(Removed: sets/BOM lines pending O-03, qr_labels, packing_checks, packing_confirmations, dispatch_confirmations, integration_inbox for RM).*

## 5. Status and quantity model principles
Candidate piece states: Pending, Ready (material issued), Submitted, QC Pending, Flagged (waiting PM), At Vendor, Passed (per operation), Scrapped, Packed.  
Product states: In Progress, Complete.  
SC states: In Progress, Delivered, Cancelled. (Final status vocabulary is OPEN — BUSINESS DECISION REQUIRED: O-10).  
Quantities are strictly derived from piece records, never edited by hand. (BR-068) Every piece is in exactly one state at any time. (BR-013)

## 6. Development phases — The 51-Phase Master Plan
### A Planning
- **Phase 1:** Requirement analysis (completed; updated per decision log 2026-11-24).
- **Phase 2:** System design (in progress; state machines, ERD, architecture).
- **Phase 3:** Standards and tech spec.

### B Foundation
- **Phase 4:** Create project (Java 21, Spring Boot, React, Vite).
- **Phase 5:** Folder structure and modular monolith packages.
- **Phase 6:** Configuration (Neon Postgres, Cloudflare R2, Render).
- **Phase 7:** Database design and Flyway migrations.
- **Phase 8:** Backend foundation (error handling, base entities, DTOs).

### C Security and master data
- **Phase 9:** Authentication (individual login, password policies, JWT/session).
- **Phase 10:** Users and employees management.
- **Phase 11:** RBAC (six primary roles: Admin, PM, Designer, Operator, QC, Management).
- **Phase 12:** Master data (departments, operations, machines, units, reasons).

### D Integration adapter
- **Phase 13:** RM read connector (polling, service account, JWT, endpoint mapping verification).
- **Phase 14:** ECR sender (HTTP intake client, secret header, payload formatting).

### E Production core
- **Phase 15:** Process templates and operation sequences.
- **Phase 16:** SC intake manual entry.
- **Phase 17:** SC intake Excel import.
- **Phase 18:** SC intake from RM (sync worker, deduplication, diff-based revisions).
- **Phase 19:** Piece generation (unique serials, product linking).
- **Phase 20:** Files and drawings (Cloudflare R2 attachment management).
- **Phase 21:** Dynamic tolerance (Excel/PDF extraction, Designer verification, PM approval).
- **Phase 22:** Material-issued gate (read RM issue status, manual override, piece blocking).
- **Phase 23:** PM daily priority list.
- **Phase 24:** Work assignment and worklists.

### F Execution
- **Phase 25:** Operator execution (bulk entry grid, paste from Excel, ready/opened/submitted timestamps).
- **Phase 26:** Tolerance validation engine.
- **Phase 27:** QC module (independent blind inspection on every process).
- **Phase 28:** QC decision (non-blocking alerts, piece waiting for PM).
- **Phase 29:** Extra process and return to earlier operation (rework iterations).
- **Phase 30:** Rejection and restart (scrap, linked replacement piece creation).
- **Phase 31:** ECR exception sending (database outbox, once-only delivery, report number storage).
- **Phase 32:** Replacement release gate (RM ECR material issue matching, manual release).
- **Phase 33:** PM operation controls (add, skip, resequence, reopen with mandatory reason).
- **Phase 34:** Vendor tracking manual entry.
- **Phase 35:** Vendor tracking from RM (read DC details, return status, piece tagging).
- **Phase 36:** Completion engine (piece Packed, product complete, SC Delivered automatically).

### G Management and UI
- **Phase 37:** Notifications (in-app, priorities, Admin-managed policy).
- **Phase 38:** Email notifications (SMTP with database queue).
- **Phase 39:** Dashboard (live metrics, machines, vendors, out-of-tolerance values).
- **Phase 40:** Reports and daily 9 AM report (Excel + charts).
- **Phase 41:** Scheduling, targets, bottleneck detection and forecast engine.
- **Phase 42:** Reconciliation screen (Production vs RM and ECR).
- **Phase 43:** Frontend pages per role (Tailwind CSS, shadcn/ui).
- **Phase 44:** Frontend and API connection.

### H Quality and release
- **Phase 45:** Security and audit hardening.
- **Phase 46:** Performance tuning and load testing.
- **Phase 47:** Testing (unit, integration, E2E).
- **Phase 48:** Documentation and user guides.
- **Phase 49:** CI/CD, Render deployment, backup automation.
- **Phase 50:** Monitoring and maintenance.

### I Later
- **Phase 51:** MERC and Velan Dashboard read access (write only if required in future; D-12).

## 7. Testing strategy
- Unit: Piece status progression, automatic completion, state transitions, tolerance comparison, role permissions.
- Integration: RM SC poll, material gate, vendor DC read, ECR outbox once-only send, retries, and duplicate event prevention.
- Database: Flyway migrations, constraints, row-level locking (`SELECT ... FOR UPDATE SKIP LOCKED`), and piece status conservation.
- UI: Role-based bulk grids, worklists, Excel paste, forms, and error states.
- End-to-end: SC intake (manual/Excel/RM) → material gate → operator execution → QC inspection → PM exception resolution → vendor return → automatic completion (Packed, Delivered).
- Security: Direct backend API authorization, session security, and immutable audit logs.
- Negative tests: Material not issued, replacement piece not released, duplicate ECR send, RM revision after submit, Excel import errors.

## 8. Delivery governance
- Maintain a requirement-to-feature-to-test traceability matrix.
- Every change has an owner, reason, impact and acceptance test.
- Freeze interface contracts before building each integration.
- Demo each phase to business users before moving to the next phase.
- PM or Admin provides final business acceptance.

## 9. Main project risks
| Risk | Mitigation |
|---|---|
| RM line edited or deleted after submit | Sync diffs every poll; preserves work already completed in Production (AC-024) |
| DC lines without `rm_item_id` | Match by SC + part_name; unmatched lines route to manual map-this-line screen |
| ECR intake without persistent deduplication | Production outbox sends once and never auto-resends after ambiguous timeout (BR-062) |
| Polling delay for material or DC updates | Provide manual "refresh now" action and PM/Admin manual override capability |
| Quantity duplication or loss | Quantities strictly derived from piece records; row locking and database transactions |
| Tolerance extraction errors | Mandatory Designer verification and PM approval before activation |
| Role permission gaps | Backend API authorization on every endpoint; deny by default |
| Scope creep | Enforce out-of-scope boundaries and formal change request governance |

## 10. Definition of done
A module is done only when its business rules, backend validations, UI, permissions, audit, integration behaviour, tests, documentation and acceptance evidence are complete.
