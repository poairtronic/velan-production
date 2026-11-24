# 15 — Complete Application Details and Development Plan

## 1. Product definition
Velan Production is the manufacturing execution and traceability application between RM V-SMART and the final dispatch handoff. It converts RM SC/BOM information into traceable sets and components, plans operations, records work and measurements, controls QC and exceptions, tracks vendor processing and material readiness, and records packing plus actual dispatch.

## 2. Product boundaries
**Inputs from RM:** SC/requisition, PO/customer references, products/components/BOM, required material, material issue state/quantity/reference/time, vendor DC reference and inward/returned quantity/status.  
**Outputs to RM:** applicable rejection, rework and additional-process information; vendor-processing alert/reference.  
**Outputs to ECR:** applicable exception information for its responsibility/costing workflow.  
**Not returned from ECR:** costing/responsibility result.  
**Outside this application:** billing, stock transactions, Vendor DC transactions, MERC, Velan Dashboard and AI Agent.

## 3. Main application modules
### 3.1 Identity and administration
Login, users, role assignment, custom permissions, user overrides, suspension, audit/security logs and Admin-managed notification settings.

### 3.2 PO/SC intake
Inbound RM event validation, deduplication, production record creation, source ID preservation, SC update/cancellation handling and integration monitoring.

### 3.3 Product/BOM/set/component traceability
PO and SC navigation, manufactured main product and sub-product hierarchy, BOM requirements, per-set completion, component IDs, QR codes, scanning, search, history, component transfer/replacement lineage.

### 3.4 Process template and operation master
Reusable templates per product flow, ordered operations, standard parameters, instructions, expected durations, template revisions and authorized PM/Admin management.

### 3.5 Design drawing and tolerance management
Drawing/CAD PDF upload, Excel/PDF tolerance upload, extraction/mapping, Design verification, PM approval, revisions, notifications and impact review.

### 3.6 Planning and scheduling
SC queue, priority, employee workload/capacity, shift/calendar/holiday setup, department worklists, PM assignments, rescheduling, bottlenecks, forecasts and targets.

### 3.7 Operator execution
Priority worklist, operation instructions, material readiness gate, in-house/vendor route, actual measurements, split quantities and submission to QC.

### 3.8 QC and exception management
Independent QC measurements, tolerance comparison, pass/fail, PM escalation, override, rework, restart, additional process and full audit.

### 3.9 Vendor tracking
Vendor assignment, operation/quantity, expected return, RM DC reference, RM inward event, partial return balance, delay tracking and internal QC.

### 3.10 Material visibility
Read-only RM material status, required/issued/pending quantities, shortages, issue timestamp and readiness calculation. No inventory transactions.

### 3.11 Integrations
RM inbound/outbound, ECR exception handoff, durable event history, retry/reconciliation and failure notifications.

### 3.12 Dashboards, reports and notifications
Company/department drilldowns, set/component/operation metrics, targets, delays, vendor/material/QC blockers, in-app/email alerts and permission-aware Excel/PDF reports.

### 3.13 Packing and dispatch
Complete-set eligibility, BOM checklist, packing confirmation, Dispatch Pending queue, actual dispatch confirmation and Delivered status.

## 4. Core data model
Recommended domains (logical entities; final schema follows detailed ERD and migration design):
- Identity: users, roles, permissions, user_roles, role_permissions, user_permission_overrides, user_status_history.
- Commercial intake: purchase_orders, sales_contracts, sc_revisions, external_id_mappings.
- Product: products, product_components, bom_headers, bom_lines, sets, components, component_lineage, qr_labels.
- Process: process_templates, template_versions, operations, operation_parameters, standard_times.
- Tolerance: tolerance_uploads, extraction_items, tolerance_revisions, tolerance_values, tolerance_approvals.
- Execution: work_assignments, operation_instances, quantity_lots, quantity_movements, operation_skips, operation_reopens.
- Quality: operator_measurements, qc_measurements, qc_decisions, override_requests, override_decisions.
- Exceptions: rework_cases, rework_operations, restart_events, additional_processes, exception_records.
- Vendor: vendors, vendor_operations, vendor_tracking_records, vendor_returns, vendor_attachments.
- RM material: material_status_snapshots, material_issue_events, rm_references.
- Integrations: integration_inbox, integration_outbox, integration_events, integration_errors, ecr_links.
- Planning: shifts, work_calendar, holidays, employee_capacity, schedules, schedule_changes, production_targets.
- Operations support: notifications, notification_settings, notification_delivery, audit_events, packing_checks, packing_confirmations, dispatch_confirmations.

## 5. Status and quantity model principles
Use explicit state transitions rather than free-text status edits. Candidate states include Pending, Ready, Assigned, In Progress, Submitted for QC, QC Passed, QC Failed/Blocked, Rework, Restart, Vendor Pending, Vendor Partially Returned, Packing Ready, Dispatch Pending, Delivered, Cancelled. Final enumerations must be approved before schema freeze.

Quantity buckets must be mutually exclusive for a given physical unit at a given point in time. A split must record parent quantity, child quantity records and movement reason. The sum of all active/terminal outcomes must reconcile to the accepted source quantity, with scrap/replacement treated as explicit separately approved outcomes.

## 6. Recommended development phases
### Phase 0 — Discovery and contract validation
- Confirm source-of-truth boundaries and final business rules.
- Inspect RM and ECR APIs/schema, exact field names, authentication and test environments.
- Approve set/component ID strategy, status dictionary, QC disposition matrix and cancellation behaviour.
- Finalize technology stack, hosting, storage, security, backup and performance targets.
- Deliver: approved BRD/SRS, interface contracts, ERD, risk register and acceptance traceability matrix.

### Phase 1 — Technical foundation
- Repository structure, CI/CD, environments, secrets management and observability.
- Authentication, RBAC, audit framework and master data.
- PostgreSQL schema/migrations and object storage foundation.
- API conventions, error handling and test harness.
- Deliver: secure deployable application shell with tested permissions.

### Phase 2 — SC intake and traceability
- RM inbound SC integration and event inbox/outbox.
- PO/SC/product/BOM hierarchy.
- Set/component creation and IDs/QR/search/history.
- Quantity conservation foundation and SC update reconciliation.
- Deliver: traceable production records from RM.

### Phase 3 — Process templates and Design tolerances
- Product operation templates and revisions.
- Drawing/CAD upload.
- Excel/PDF tolerance extraction, mapping, Design review and PM approval.
- Tolerance revision impact handling.
- Deliver: released operation instructions and approved tolerance values.

### Phase 4 — Planning and production execution
- Planning queue, priority, capacity, calendar and assignments.
- Operator worklists, material readiness gate and operation execution.
- Measurement capture and quantity splitting.
- Deliver: controlled shop-floor execution.

### Phase 5 — QC, exceptions and rework
- Independent QC measurements and tolerance evaluation.
- PM escalation, override, rework, restart and additional process with QC.
- RM/ECR exception event payload implementation.
- Deliver: controlled quality and exception lifecycle.

### Phase 6 — Vendor and ECR integration
- Vendor operation tracking and RM alert.
- RM inward notification and partial return reconciliation.
- Internal QC after vendor return.
- ECR exception handoff and acknowledgement/error tracking.
- Deliver: external processing and ECR handoffs.

### Phase 7 — Material visibility, dashboards and notifications
- RM issue timing/status visibility and readiness analytics.
- Dashboards, targets, bottlenecks and forecasts.
- Admin-managed in-app/email notifications and reports.
- Deliver: operational control and management visibility.

### Phase 8 — Packing, dispatch and hardening
- Complete-BOM packing checks.
- Dispatch Pending and actual dispatch confirmation.
- End-to-end testing, concurrency/load/security tests.
- Backup/restore drill, UAT, training and production rollout.
- Deliver: production-ready system through Delivered state.

## 7. Testing strategy
- Unit: BOM quantity, set completion, state transitions, tolerance comparisons, role evaluation.
- Integration: RM SC, material issue, vendor inward, Production-to-RM/ECR, retries and duplicate events.
- Database: migrations, constraints, transactions, locking and quantity conservation.
- UI: role-based worklists, forms, filters, scanning and error states.
- End-to-end: SC intake → production → QC → exception/vendor → packing → dispatch.
- Security: direct API authorization, file access, session controls and audit.
- Performance: realistic concurrent operators, dashboard filters and event throughput.
- Recovery: database and object-storage restore.

## 8. Delivery governance
- Maintain a requirement-to-feature-to-test traceability matrix.
- Every change has an owner, reason, impact and acceptance test.
- Freeze interface contracts before building each integration.
- Demo each phase to business users before moving to the next phase.
- PM or Admin provides final business acceptance.
- Maintain a release checklist, migration plan, rollback plan and user training material.

## 9. Main project risks
| Risk | Mitigation |
|---|---|
| RM contract not finalized | Contract workshop, sample payloads and integration tests before coding |
| Quantity duplication/loss | Explicit quantity ledger, transactional transitions and concurrency tests |
| Incorrect set completion | BOM-driven component mapping and set-level acceptance tests |
| Tolerance extraction errors | Design verification and PM approval; preserve source file/revision |
| Material issue delay | RM event retry, source timestamp and clear blocked status |
| Vendor inward mismatch | RM remains authoritative; reconcile DC and returned quantities |
| Role permission gaps | Permission matrix review and backend authorization tests |
| Scope creep | Enforce out-of-scope list and formal change requests |
| Deployment/data loss | Backups, restore drills, monitoring and release rollback |

## 10. Definition of done
A module is done only when its business rules, backend validations, UI, permissions, audit, integration behaviour, tests, documentation and acceptance evidence are complete. A screen alone is not completion.
