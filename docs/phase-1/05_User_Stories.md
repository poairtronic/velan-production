# 05 — User Stories

The stories below use the five confirmed role groups. Permissions remain enforced by RBAC; a role name alone does not automatically grant every action.

## Admin
- **ADM-US-001:** As Admin, I want to create, activate, deactivate and suspend users so that access reflects employment status.
- **ADM-US-002:** As Admin, I want to create custom roles and assign module/action permissions so that the application can match the organization structure.
- **ADM-US-003:** As Admin, I want to assign multiple roles and individual permission overrides so that exceptional access can be managed explicitly.
- **ADM-US-004:** As Admin, I want to manage notification settings for employees, including critical notification policy, so that users cannot disable notification types themselves.
- **ADM-US-005:** As Admin, I want to cancel/delete records through a controlled, reasoned and audited action so that only authorized administration can perform it.
- **ADM-US-006:** As Admin, I want to see integration failures and security/admin logs so that I can maintain application integrity.

## PM — Production Manager
- **PM-US-001:** As PM, I want new RM SCs to appear automatically in the planning queue so that production can be scheduled promptly.
- **PM-US-002:** As PM, I want to confirm priorities and assign work to operators so that daily execution follows business urgency.
- **PM-US-003:** As PM, I want to see set, component, operation, material and vendor status so that I can identify blockers.
- **PM-US-004:** As PM, I want to approve tolerance revisions before new values apply so that changes are controlled.
- **PM-US-005:** As PM, I want to decide QC failure disposition and create rework/restart routes so that failed quantities have a controlled path.
- **PM-US-006:** As PM, I want to add, skip, resequence, reopen or reassign operations with reasons so that the route can be managed without losing history.
- **PM-US-007:** As PM, I want to approve or reject override requests with a mandatory reason so that out-of-tolerance work is governed.
- **PM-US-008:** As PM, I want to see vendor delays and RM inward notifications so that returned quantities can enter internal QC.
- **PM-US-009:** As PM, I want to reschedule work and manage targets with an audit trail so that capacity and delivery priorities remain visible.

## Operator
- **OP-US-001:** As Operator, I want to see my assigned work in PM-defined priority order so that I know what to process next.
- **OP-US-002:** As Operator, I want to select the SC, set/component and operation and see its instructions and approved tolerance so that I perform the correct process.
- **OP-US-003:** As Operator, I want the system to prevent operation data entry until RM confirms required material issue so that work is not recorded before material readiness.
- **OP-US-004:** As Operator, I want to enter actual measurements for each selected piece/component so that each unit has traceable production data.
- **OP-US-005:** As Operator, I want to submit only a quantity that is available at the current operation so that quantity is not over-processed.
- **OP-US-006:** As Operator, I want to see whether a quantity is in-house or vendor processing so that I follow the correct route.

## QC
- **QC-US-001:** As QC, I want to see quantities submitted for inspection so that I can independently inspect completed operations.
- **QC-US-002:** As QC, I want to enter my own measurements separately from operator measurements so that the inspection remains independent.
- **QC-US-003:** As QC, I want the system to compare QC values against the active approved tolerance revision so that pass/fail results are consistent.
- **QC-US-004:** As QC, I want to record a failure and notify PM so that the PM decides the next route.
- **QC-US-005:** As QC, I want to request an override with a reason so that an authorized approver can decide without changing original measurements.
- **QC-US-006:** As QC, I want RM-confirmed vendor-returned quantities to appear for inspection so that only physically inwarded quantity is inspected.
- **QC-US-007:** As QC, I want additional-process quantities to require QC inspection so that extra work is quality-verified.

## Management
- **MGT-US-001:** As Management, I want a consolidated dashboard of SC, set, component, operation, QC, vendor, packing and dispatch status so that I can monitor business performance.
- **MGT-US-002:** As Management, I want to filter metrics by date, product, department, PO and SC so that I can investigate delays and output.
- **MGT-US-003:** As Management, I want target-versus-actual reporting for sets and components so that performance can be reviewed.
- **MGT-US-004:** As Management, I want historical trends and exports subject to my permissions so that I can review production outcomes.

## Packing and dispatch tasks
Packing/dispatch actions are capabilities assigned by Admin through RBAC to the appropriate employees; they are not additional confirmed primary role groups.
- **PK-US-001:** As an authorized packing user, I want to see only sets whose complete BOM and final QC are complete so that incomplete sets cannot be packed.
- **PK-US-002:** As an authorized packing user, I want to verify each required component and confirm packing so that the set moves to Dispatch Pending.
- **DSP-US-001:** As an authorized dispatch user, I want to see packed sets awaiting dispatch so that I can prepare actual dispatch.
- **DSP-US-002:** As an authorized dispatch user, I want to record actual dispatched complete sets and dispatch time so that only dispatched sets become Delivered.

## Acceptance note
Each story must be expanded into Given–When–Then tests. Negative tests must verify unauthorized access, missing measurements, material-not-issued, incomplete BOM, duplicate integration events and quantity overrun.
