# 02 — Software Requirements Specification (SRS)

**Status:** Working baseline; detailed interface contracts and performance thresholds remain to be validated.

## 1. System overview
Velan Production is a web-based manufacturing execution application integrated with RM V-SMART and ECR. It receives RM-originated SC data, maintains production-specific identities and histories, executes configured operations, records operator and QC measurements, manages exceptions, tracks vendor work and completes packing/dispatch.

## 2. Actors
Admin, PM, Operator, QC, Management. Admin can configure custom roles and action permissions; the five named roles are the confirmed business role groups. Vendor users do not log in. RM Stores and Design-side actors remain in RM; Design-related tolerance upload/review access in Production must be granted through the configured role/permission model.

## 3. Functional requirements
### FR-001 Authentication and access
- Authenticate users and maintain secure sessions.
- Enforce RBAC in backend APIs and UI.
- Support multiple roles per user, custom roles, module/action permissions and individual Admin overrides.
- If no explicit grant exists, deny. If role grants conflict, deny wins unless an individual override applies.
- Log permission changes, privileged actions, login/security events and administrative corrections.
- Support activate/deactivate/suspend and scheduled suspension/reactivation.

### FR-002 PO/SC intake
- Receive submitted SC/requisition data from RM automatically.
- Preserve the original RM identifiers and exact source references.
- Create one Production record per RM SC/requisition according to the agreed source identity.
- Reject invalid/incomplete messages safely; retain integration errors.
- Deduplicate repeated events using stable RM record/event IDs.
- Apply RM updates and quantity changes according to approved change rules.
- If RM is unavailable, support controlled manual provisional entry and later reconciliation; provisional-to-RM ID reconciliation rules must be finalized.

### FR-003 Hierarchy and traceability
- Represent PO → SC → manufactured product/main product → sub-products/components/BOM items.
- Represent each set and its required components as parent-child records.
- Assign unique IDs to sets and physical components; support QR labels and scanning.
- Support search by PO, SC, product, set ID, component ID and QR token.
- Maintain immutable lineage for component replacement/transfer and full operation history.

### FR-004 BOM and set calculation
- Use SC target quantity as number of sets where that is the agreed source definition.
- Calculate component demand from BOM quantity per set × set quantity.
- Calculate set completion only when every required BOM component for that set has completed its required operations and passed required final QC.
- Support independent set progress and partial dispatch of complete sets only.
- Do not treat partial component completion as a completed set.

### FR-005 Master data and process templates
- Maintain product/component, department, operation, unit, vendor, status and reason masters as authorized.
- Maintain reusable process templates with ordered operations, instructions, standard parameters, units and expected durations.
- Permit authorized Admin/PM to manage templates subject to RBAC.
- Permit Design/Designer users to view/select approved templates but not edit reusable template definitions unless separately authorized.
- Version templates; historical operations must retain the template revision used.

### FR-006 Design drawings and tolerances
- Accept SC-specific tolerance sheets in Excel primarily and PDF also.
- Support CAD/drawing PDF attachments against the relevant SC/product/component as required.
- Extract and map tolerance values using SC + product/component + operation + parameter.
- Show extraction confidence/errors and allow Design to verify/correct before submission.
- Keep template standard/static parameters separate from SC-specific dynamic tolerance values.
- PM approval is required for tolerance revisions before the changed values become active.
- After production starts, Design requests changes; it cannot overwrite active values directly.
- New approved revision applies to data not yet entered/unfinished quantities as approved; existing entered measurements are not rewritten. Completed history remains attached to the old revision. Notify PM and flag affected values/quantities for review.

### FR-007 Planning and scheduling
- Add new SCs to planning automatically.
- PM confirms priority, considering delivery date, SC age and manager priority.
- Calculate workload using template standard times and authorized adjustments.
- Calculate employee capacity using common company shift, working days, holidays and existing assignments.
- Support PM rescheduling with mandatory reason and audit.
- Detect bottlenecks and provide 7/15/30-day forecasts.
- Support production calendar and targets at company, department, operation and employee levels.

### FR-008 Worklists and operation execution
- Show work by SC, set, component, operation, department, assigned employee and status.
- PM assigns/reassigns work and defines operator priority order.
- Operator sees assigned work in priority order.
- Operator can process only eligible pending quantity and cannot exceed available quantity.
- Support in-house and vendor routes where permitted.
- Capture start/end times, operator, operation, quantity, processing route and instructions.
- Support split quantity outcomes without loss, duplication or overlap.
- PM can add, skip, resequence, reopen or adjust operations/quantities with permission and mandatory reason.

### FR-009 Material readiness
- Receive material required/issued/pending/shortage status and issue references/timestamps from RM.
- Display material readiness and material issue timing in Production.
- Prevent operator data entry/start for material-dependent operations until RM confirms the required material issue condition.
- Production is read/track only; no stock issue, return, adjustment or additional material request transaction.

### FR-010 Operator measurements
- Show approved tolerance values for the selected SC/product/operation/parameter.
- Capture required measurements per physical component/piece or controlled quantity identity.
- Save actual entered values even when outside tolerance; do not silently change measurement or tolerance.
- Record units, tolerance revision, user, timestamp and component/operation lineage.
- Submit eligible quantity to QC worklist where QC is required.

### FR-011 QC
- QC independently inspects and enters separate measurement records; never copy operator measurements into QC fields.
- Compare QC values against the applicable approved tolerance revision.
- Record pass/fail, reason, inspector, timestamp and evidence where configured.
- Failed quantities remain blocked until PM-controlled disposition/authorized override.
- PM handles QC failure/rework decision according to the latest confirmed requirement.
- Additional processes require QC.

### FR-012 Exceptions, rework and restart
- Support rework as a temporary operation or return to an earlier operation with revised instructions.
- After rework passes QC, return quantity to the normal sequence immediately after the original failed operation.
- Support restart from the first operation of the applicable production process, retaining previous history and applicable original process/tolerance reference.
- Support additional process with PM authorization and QC inspection.
- Send applicable rejection/rework/additional-process data to RM and ECR; ECR owns responsibility/costing.
- Preserve all decisions, reasons, affected quantities and lineage.

### FR-013 Vendor tracking
- Record vendor, operation, assigned quantity, expected return, status and internal tracking reference.
- Production sends vendor-processing alert/reference to RM; RM creates and handles DC transactions.
- RM inward/return event automatically notifies Production.
- Production user enters/records the QC measurements after RM confirms inward quantity; only RM-confirmed returned quantity is eligible for QC.
- Support partial return and vendor-pending balance; no vendor login.

### FR-014 Packing and dispatch
- Only complete BOM sets with required production and final QC completion are eligible for packing.
- Packing verifies every required component and confirms packing.
- Packing confirmation changes status to Dispatch Pending, not Delivered.
- Dispatch employee confirms actual dispatch and quantity/date-time/responsible user.
- Only confirmed dispatched complete sets become Delivered.
- Partial dispatch means dispatching some fully completed sets from an SC while other sets remain in production; it does not mean dispatching an incomplete set.

### FR-015 Dashboards, reports and notifications
- Provide central dashboard and department drilldowns.
- Show active SCs/POs, sets, component progress, WIP, blocked QC, rework, vendor pending, material shortages, packing-ready, dispatch-pending and delivered quantities.
- Support date/product/department/PO/SC filters and Excel/PDF exports subject to permissions.
- Support in-app and email notifications, read/unread, priorities Critical/High/Normal/Low, search and department filters.
- Users cannot independently disable notification types. Admin manages notification settings for users, including critical notification policy.

### FR-016 Audit and administration
- Admin-only deletion/cancellation authority; ordinary users cannot permanently erase production history.
- Preserve cancellation/deletion audit, actor, reason, timestamp and affected records.
- Admin sees administrative/security logs; PM sees permitted production activity but not security/admin logs; employees see their permitted own activity.

## 4. Non-functional requirements
- Security: authentication, backend authorization, least privilege, secure file access, input validation.
- Integrity: transactional quantity movements, unique external references, referential integrity and no silent overwrites.
- Concurrency: prevent simultaneous actions/scans from advancing the same quantity twice.
- Resilience: durable integration inbox/outbox, retries, duplicate protection and reconciliation.
- Performance: real-time worklists/dashboards; numeric service-level targets to be set after load testing.
- Traceability: event linked to PO/SC/set/component/operation/user/time as applicable.
- File handling: validate Excel/PDF/CAD/attachments; enforce access and retention rules.
- Observability: structured logs, integration logs, correlation IDs, health checks and error monitoring.
- Recovery: define database/file backup, point-in-time recovery and tested restoration objectives.

## 5. Data entities (logical)
Users, roles, permissions, user-role assignments, overrides, PO, SC, SC revisions, products, BOM lines, sets, components, component lineage, QR labels, process templates/versions, operations, parameters, tolerance uploads/revisions/values/approvals, work assignments, operation instances, quantity lots/movements, operator measurements, QC measurements/decisions, override requests, rework cases, restart events, vendor tracking, RM material snapshots, integration inbox/outbox/events/errors, ECR links, schedule/calendar/targets, notifications, audit events, packing checks, dispatch confirmations.

## 6. Acceptance and traceability
Every FR must map to at least one user story and one or more acceptance tests. Detailed criteria are in Document 10. External field mappings must be completed against actual RM/ECR contracts before integration coding.
