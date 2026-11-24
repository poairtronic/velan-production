# 02 — Software Requirements Specification (SRS)

Updated 2026-11-24 — decisions D-01 to D-19 (see 00_DECISION_LOG.md)

**Status:** Updated 2026-11-24 with decisions D-01 to D-19.

## 1. System overview
Velan Production is a web-based manufacturing execution application integrated with RM V-SMART and ECR. It receives RM-originated SC data (with permanent manual entry and Excel import capabilities), maintains piece-level identities and histories, executes configured operations, records operator and QC measurements in bulk grids, manages exceptions via PM decisions, tracks vendor work from RM DC records, and completes automatically (piece Packed → product complete → SC Delivered).

## 2. Actors
Confirmed primary roles: **Admin, PM, Designer, Operator, QC, Management**. (D-03)
- **Admin:** System configuration, user management, RBAC, notification policies, deletion/cancellation.
- **PM (Production Manager):** Work scheduling, daily priority list per operator, QC flag decisions (CONTINUE, EXTRA PROCESS, RETURN TO EARLIER OP, REJECT AND RESTART), manual material gate / replacement release overrides, tolerance approval.
- **Designer:** Manual SC entry and Excel import, selects process flow per product, uploads drawings and tolerance sheets, verifies extracted values. Cannot approve tolerances, edit reusable templates, enter operator/QC values, or decide QC flags.
- **Operator:** Views own assigned queue in PM priority order, records bulk measurements per piece, submits completed operations.
- **QC:** Independent inspection on every process, records separate measurements, flags non-conforming dimensions.
- **Management:** Read-only dashboard view, business performance tracking, daily 9 AM reports.
- *Non-roles:* Vendor users do not log in. RM Stores, Packing, Dispatch, Vendor Coordinator and Department Head are not roles; Stores actions stay in RM. Individual login per employee.

## 3. Functional requirements
### FR-001 Authentication and access
- Authenticate users and maintain secure sessions with individual login per employee. (D-03)
- Credential type and minimum length configurable (OPEN — BUSINESS DECISION REQUIRED: O-09).
- Enforce RBAC in backend APIs and UI across six confirmed roles (Admin, PM, Designer, Operator, QC, Management).
- Support multiple roles per user, custom roles, module/action permissions and individual Admin overrides.
- If no explicit grant exists, deny. If role grants conflict, deny wins unless an individual override applies.
- Log permission changes, privileged actions, login/security events and administrative corrections.
- Support activate/deactivate/suspend and scheduled suspension/reactivation.

### FR-002 PO/SC intake
- (a) RM sync: Production polls RM's existing read APIs with a read-only service account and provides a "refresh now" action.
- (b) Manual SC entry: Designer, PM, or Admin can enter SC, PO, customer, product lines, quantities, and optional due date.
- (c) Excel SC import: Support Excel SC import with schema validation, preview, and import audit trail.
- One Production SC per RM SC; deduplicate repeated polls or imports on RM SC id and SC number.
- Apply RM revisions and quantity changes by diff on each poll and log an audit trail.
- At go-live, process new SCs only; an in-progress SC is imported from RM only on explicit PM request. (D-17)

### FR-003 Hierarchy and traceability
- Represent PO → SC → product → piece. (D-02)
- Remove QR labels, scanning and QR search (deferred; OPEN — BUSINESS DECISION REQUIRED: O-08).
- Support search by PO, SC, product, and piece serial.
- Maintain immutable lineage for piece history; link replacement pieces to scrapped parents via `replaced_piece_id`.

### FR-004 Piece generation
- Generate individual pieces per product based on RM product quantity (one RM line per product; SRG is a set = 2 numbers and RM already sends the number; e.g. APG 50 means 100 SRG). (D-02)
- Assign a unique serial to every piece (proposed format `SC-2579-APG-001`; OPEN — BUSINESS DECISION REQUIRED: O-02).
- Attach an immutable snapshot of the process flow template per product.
- Set-level completion and partial dispatch of sets is OPEN — BUSINESS DECISION REQUIRED (O-03). SC completion is calculated automatically when all products are complete (D-10).

### FR-005 Master data and process templates
- Maintain product, department, operation, unit, vendor, status and reason masters as authorized.
- Maintain reusable process templates with ordered operations, instructions, standard parameters, units and expected durations.
- Permit authorized Admin/PM to manage reusable templates (OPEN — BUSINESS DECISION REQUIRED: O-12).
- Designer selects the process flow per product; fixed machine and operator (or allowed vendor) per operation; five parameter types (numeric, yes/no, text, signature, image); flow is snapshotted per product so later template changes never alter existing work. (D-14)

### FR-006 Design drawings and tolerances
- Accept SC-specific tolerance sheets in Excel primarily and PDF also; tolerance sheet carries SC number, product name, and operation values (OPEN — BUSINESS DECISION REQUIRED: O-06).
- Support CAD/drawing PDF attachments against the relevant SC/product as required.
- Extract and map tolerance values using SC + product + operation + parameter.
- Show extraction confidence/errors and allow Designer to verify/correct before submission.
- Keep template standard/static parameters separate from SC-specific dynamic tolerance values.
- PM approval is required for tolerance revisions before the changed values become active.
- After production starts, Designer requests changes; Designer cannot overwrite active values directly.
- New approved revision applies to data not yet entered/unfinished quantities as approved; existing entered measurements are not rewritten. Completed history remains attached to the old revision. Notify PM and flag affected values for review.

### FR-007 Planning and scheduling
- Add new SCs to planning automatically.
- Provide the PM daily priority list per operator. (D-15)
- PM confirms priority, considering delivery date (source of delivery/due date is OPEN — BUSINESS DECISION REQUIRED: O-01), SC age, and manager priority.
- Calculate workload using template standard times and authorized adjustments.
- Calculate employee capacity using common company shift, working days, holidays and existing assignments.
- Support PM rescheduling with mandatory reason and audit.
- Detect bottlenecks and provide 7/15/30-day forecasts and production calendar targets.

### FR-008 Worklists and operation execution
- Show work by SC, product, piece, operation, department, assigned employee and status.
- PM assigns/reassigns work and defines each operator's daily priority list.
- Operator sees assigned work in priority order and picks pieces from their own queue (no scanning; D-16).
- Replace start/stop buttons with ready / opened / submitted timestamps; elapsed time is derived.
- Operator can process only eligible pending pieces and cannot exceed available quantity.
- Support in-house and vendor routes where permitted.
- Support split quantity outcomes without loss, duplication or overlap.
- PM can add, skip, resequence, reopen or adjust operations with permission and mandatory reason.

### FR-009 Material readiness gate
- Stores marks material issued in RM; Production sync reads issued yes/no per product. (D-04)
- Until the RM sync is live or if sync is delayed, PM/Admin sets the flag manually.
- Pieces of a product are blocked at the material gate; operators cannot start or enter data until the flag is set.
- Production is read/track only; no stock issue, return, adjustment or additional material request transaction. Remove material shortage/timing analytics.

### FR-010 Operator measurements
- Show approved tolerance values for the selected SC/product/operation/parameter.
- Capture measurements per piece in a bulk grid (rows = pieces, columns = parameters) with paste from Excel. (D-16)
- Support five parameter types: numeric, yes/no, text, signature, image. (D-14)
- Save actual entered values even when outside tolerance; do not silently change measurement or tolerance.
- Record units, tolerance revision, user, timestamp and piece/operation lineage.
- Submit completed pieces to QC worklist.

### FR-011 QC inspection
- QC inspection is required on every operation. (D-06)
- QC independently inspects and enters separate measurement records; never copy operator measurements into QC fields (blind QC).
- Compare QC values against the applicable approved tolerance revision.
- Record pass/fail, reason, inspector, timestamp and evidence where configured.
- Out of tolerance raises a non-blocking alert; the flagged piece waits for the PM decision. (D-07)
- Remove separate "override request/approval" workflow; an override is the PM choosing CONTINUE with a mandatory reason.

### FR-012 Exceptions, rework and restart
- Support four PM QC decisions: (D-07)
  1. **CONTINUE:** Requires a mandatory engineering reason; allows piece to advance to next operation without altering historical failed measurements.
  2. **EXTRA PROCESS:** Injects a new ad-hoc operation with its own tolerance list; requires mandatory QC inspection; upon QC pass the piece continues after the originally failed operation. (D-19, BR-070)
  3. **RETURN TO EARLIER OPERATION:** Rewinds piece to an earlier operation with new instructions; rework tracked via incremented iteration counter; upon QC pass the piece continues after the originally failed operation. (D-19)
  4. **REJECT AND RESTART:** Scraps the piece (history kept); creates a replacement piece with a new serial linked to the scrapped piece via `replaced_piece_id` starting at operation 1. (D-08)
- Replacement piece release gate: Replacement piece is blocked until RM shows the ECR material as issued (matched by ECR report number) or the PM releases it manually with a reason. (D-08, BR-061)
- Send exception notifications to **ECR only** (remove RM); ECR returns an acknowledgement report number; Production never waits for ECR and does not calculate/store ECR costing. (D-09)

### FR-013 Vendor tracking
- Production reads RM Vendor DC details: vendor, DC number, process name, product, quantity. (D-05)
- Production reads RM DC return status: received / partial / not received. (D-05)
- Production does not select vendors, does not send alerts to RM, and tracks no vendor expected return dates.
- After return inward in RM, a Production user tags returned pieces and selects the process; QC enters independent values.
- Returned piece may complete any pending operation (usually the next); in-house operations stay sequential. (D-05, BR-065)

### FR-014 Completion
- A piece becomes **Packed** automatically when all its operations are passed. (D-10, BR-059)
- A product is complete when all its pieces are Packed.
- The **SC becomes Delivered automatically** when all products are complete. (D-10, BR-060)
- No packing or dispatch screens, statuses, roles or dispatch confirmation. Billing is outside Production.
- Set-level completion and partial delivery are OPEN — BUSINESS DECISION REQUIRED (O-03).

### FR-015 Dashboards, reports and notifications
- Provide central dashboard and role-specific drilldowns.
- Show active SCs/POs, piece progress, WIP, machines in use, vendors, out-of-tolerance values, blocked QC, rework, vendor pending, SCs near completion, rejection quantity, and planned vs actual. Remove "packing-ready" and "dispatch-pending".
- Add daily 9 AM report generated in Excel with charts matching dashboard totals.
- Support in-app and email notifications (Critical/High/Normal/Low priorities).
- Users cannot independently disable notification types. Admin manages notification settings for users.

### FR-016 Audit and administration
- Admin-only deletion/cancellation authority; ordinary users cannot permanently erase production history.
- Preserve cancellation/deletion audit, actor, reason, timestamp and affected records.
- Admin sees administrative/security logs; PM sees permitted production activity but not security/admin logs; employees see their permitted own activity.

## 4. Non-functional requirements
- Security: authentication, backend authorization, least privilege, secure file access, input validation.
- Integrity: transactional piece status movements, unique external references, referential integrity and no silent overwrites.
- Concurrency: prevent simultaneous actions from advancing the same piece twice.
- Resilience: sync log + idempotent upsert for RM polling; transactional database outbox for ECR with at-most-once delivery guarantee (never auto-resend after ambiguous timeout); duplicate protection and reconciliation screen.
- Cache: Caffeine in-process caching (no Redis).
- Messaging: Database outbox/sync log (no RabbitMQ).
- Performance: real-time worklists/dashboards; numeric service-level targets to be set after load testing (OPEN — BUSINESS DECISION REQUIRED: O-11).
- Observability: structured logs, integration logs, correlation IDs, health checks and error monitoring.
- Recovery: define database/file backup, point-in-time recovery and tested restoration objectives.

## 5. Data entities (logical)
Users, roles, permissions, user-role assignments, overrides, PO, SC, SC revisions, products, pieces, `replaced_piece_id` link, flow snapshot per product, process templates/versions, operations, parameters, tolerance uploads/revisions/values/approvals, daily_priority, work assignments, operation executions (with ready/opened/submitted timestamps), operator measurements, QC measurements, qc_decisions (CONTINUE, EXTRA PROCESS, RETURN TO EARLIER OP, REJECT AND RESTART), exception_records (with ECR report number), ecr_outbox, vendor_batches (+ tagged pieces), RM sync_log, import_batches, material_issued flag, notifications, audit events.
*(Removed: component lineage as sets, QR labels, packing_confirmations, dispatch_confirmations, vendor_tracking with dates, integration_inbox for RM).*

## 6. Acceptance and traceability
Every FR must map to at least one user story and one or more acceptance tests. Detailed criteria are in Document 10. External field mappings are specified in Document 09; JSON property names must be verified against live responses.
