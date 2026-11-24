# 05 — User Stories

Updated 2026-11-24 — decisions D-01 to D-19 (see 00_DECISION_LOG.md)

The stories below use the six confirmed primary roles: **Admin, PM, Designer, Operator, QC, Management**. Permissions remain enforced by RBAC; a role name alone does not automatically grant every action. Individual login per employee.

## Designer
- **DSG-US-001:** As Designer, I want to enter an SC manually or import it from Excel so that an SC can start without waiting for RM.
- **DSG-US-002:** As Designer, I want to select the process flow for each product so that every product follows the correct operations.
- **DSG-US-003:** As Designer, I want to upload drawings and the tolerance sheet, verify extracted values and submit them to the PM.

## Admin
- **ADM-US-001:** As Admin, I want to create, activate, deactivate and suspend users, including individual login credentials, so that access reflects employment status.
- **ADM-US-002:** As Admin, I want to create custom roles and assign module/action permissions so that the application can match the organization structure.
- **ADM-US-003:** As Admin, I want to assign multiple roles and individual permission overrides so that exceptional access can be managed explicitly.
- **ADM-US-004:** As Admin, I want to manage notification settings for employees, including critical notification policy, so that users cannot disable notification types themselves.
- **ADM-US-005:** As Admin, I want to cancel/delete records through a controlled, reasoned and audited action so that only authorized administration can perform it.
- **ADM-US-006:** As Admin, I want to see integration failures and security/admin logs so that I can maintain application integrity.

## PM — Production Manager
- **PM-US-001:** As PM, I want new SCs to appear automatically (RM sync) or after manual/Excel entry so that production can be scheduled promptly.
- **PM-US-002:** As PM, I want to confirm priorities, assign work to operators, and define each operator's daily priority list so that daily execution follows business urgency.
- **PM-US-003:** As PM, I want to see piece, operation, material and vendor status so that I can identify blockers.
- **PM-US-004:** As PM, I want to approve tolerance revisions before new values apply so that changes are controlled.
- **PM-US-005:** As PM, I want flagged pieces to wait for my decision (CONTINUE with reason, EXTRA PROCESS, RETURN TO EARLIER OPERATION, REJECT AND RESTART) so that failed work has a controlled path.
- **PM-US-006:** As PM, I want to add, skip, resequence, reopen or reassign operations with reasons so that the route can be managed without losing history.
- **PM-US-007:** As PM, I want to continue a flagged piece with a mandatory reason so that accepted non-conformances are strictly audited.
- **PM-US-008:** As PM, I want to see which pieces are at a vendor and whether the return is received so that returned pieces can be scheduled for internal QC.
- **PM-US-009:** As PM, I want to reschedule work and manage targets with an audit trail so that capacity and delivery priorities remain visible.
- **PM-US-010:** As PM, I want to set the material-issued flag manually if the RM sync is delayed so that shop-floor operations are not blocked unnecessarily.
- **PM-US-011:** As PM, I want to release a replacement piece manually if RM has not yet shown the ECR material issued so that urgent scrap replacement is not delayed.
- **PM-US-012:** As PM, I want to import an in-progress SC from RM when I decide so that historical orders can be brought into the application under controlled conditions.

## Operator
- **OP-US-001:** As Operator, I want to see my assigned work in PM-defined priority order so that I know what to process next.
- **OP-US-002:** As Operator, I want to select pieces from my own queue (no scanning) and see instructions and approved tolerance so that I perform the correct process.
- **OP-US-003:** As Operator, I want the system to prevent data entry until Stores has marked material issued so that work is not recorded before material readiness.
- **OP-US-004:** As Operator, I want to enter actual measurements for each selected piece so that each unit has traceable production data.
- **OP-US-005:** As Operator, I want to submit only pieces that are ready at the current operation so that work is not over-processed.
- **OP-US-006:** As Operator, I want to see whether a piece is in-house or vendor processing so that I follow the correct route.
- **OP-US-007:** As Operator, I want to enter values for many pieces in one grid and paste from Excel so that data entry is quick.

## QC
- **QC-US-001:** As QC, I want to see pieces submitted for inspection so that I can independently inspect completed operations.
- **QC-US-002:** As QC, I want to enter my own measurements separately from operator measurements so that the inspection remains independent.
- **QC-US-003:** As QC, I want the system to compare QC values against the active approved tolerance revision so that pass/fail results are consistent.
- **QC-US-004:** As QC, I want to record a failure and flag the piece so that it alerts and waits for the PM decision.
- **QC-US-006:** As QC, I want pieces returned from a vendor to appear for inspection after the return is marked received/partial so that only physically inwarded pieces are inspected.
- **QC-US-007:** As QC, I want additional-process operations to require QC inspection so that extra work is quality-verified.
- **QC-US-008:** As QC, I want to inspect every process and enter values independently so that 100% quality compliance is ensured.

## Management
- **MGT-US-001:** As Management, I want a consolidated dashboard showing machines, vendors, out-of-tolerance values, rejection quantity, and piece progress so that I can monitor business performance.
- **MGT-US-002:** As Management, I want to filter metrics by date, product, department, PO and SC so that I can investigate delays and output.
- **MGT-US-003:** As Management, I want target-versus-actual reporting for pieces and products so that performance can be reviewed.
- **MGT-US-004:** As Management, I want historical trends and exports subject to my permissions so that I can review production outcomes.
- **MGT-US-005:** As Management, I want a daily 9 AM report in Excel with charts so that management has daily visibility of production metrics.

## Completion (Replaces Packing and Dispatch Tasks)
Packing and dispatch screens, statuses, roles and manual dispatch confirmation are removed. (D-10)
- **CMP-US-001:** As PM/Management, I want Packed and Delivered to be set automatically from piece completion (piece Packed when all operations pass; SC Delivered when all products are complete).

## Acceptance note
Each story must be expanded into Given–When–Then tests. Negative tests must verify unauthorized access, missing measurements, material not issued, replacement piece not released, duplicate ECR send, RM revision after submit, duplicate integration events and quantity overrun.
