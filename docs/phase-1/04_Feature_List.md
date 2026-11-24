# 04 — Feature List

Feature IDs are stable references for planning, user stories and testing.

| ID | Module | Feature | Main outcome |
|---|---|---|---|
| FEAT-001 | Identity | Login/logout and session handling | Authenticated users |
| FEAT-002 | RBAC | Five primary roles, custom roles, multi-role assignment, action permissions and user overrides | Controlled access |
| FEAT-003 | Admin | Employee activation, suspension, scheduling and reassignment alerts | Managed workforce |
| FEAT-004 | PO/SC | Automatic RM requisition/SC intake | Production record created from RM |
| FEAT-005 | Integration | Deduplication, retry, acknowledgement, event history and failed-sync alerts | Reliable sync |
| FEAT-006 | Hierarchy | PO → SC → manufactured product → sub-product/BOM | Correct business structure |
| FEAT-007 | Set tracking | Per-set records and completion calculation | Complete-set visibility |
| FEAT-008 | Component tracking | Unique component identity and parent-set link | Component traceability |
| FEAT-009 | QR | Set/component QR labels, camera/scanner, manual search | Fast identification |
| FEAT-010 | Master data | Product, component, operation, department, unit, vendor, reason masters | Standard data |
| FEAT-011 | Templates | Reusable product-specific operation templates and revisions | Repeatable routing |
| FEAT-012 | Design files | CAD/drawing PDF attachment and controlled access | Drawing availability |
| FEAT-013 | Tolerance upload | Excel-first and PDF tolerance upload | SC-specific tolerance intake |
| FEAT-014 | Tolerance extraction | Extract/map by SC + product + operation + parameter | Structured tolerance data |
| FEAT-015 | Tolerance verification | Design review/correction and PM approval | Approved tolerance revision |
| FEAT-016 | Tolerance change | PM approval, impact analysis, notification and version history | Controlled changes |
| FEAT-017 | Planning | Calendar, workdays, holidays, shift, workload and capacity | Realistic schedule |
| FEAT-018 | Priority | PM-confirmed operator priority list | Clear daily work order |
| FEAT-019 | Assignment | Assign/reassign operator/department and track workload | Ownership of work |
| FEAT-020 | Operation execution | Start/complete operation with instructions and timestamps | Operation history |
| FEAT-021 | Quantity split | Pass/pending/rework/restart/vendor-pending quantities | Conservation and traceability |
| FEAT-022 | Material view | RM-issued/required/pending/shortage status and issue timestamp | Material readiness |
| FEAT-023 | Operator measurements | Per-piece/component values and tolerance link | Operator record |
| FEAT-024 | QC measurements | Independent QC values and inspection decisions | Quality release control |
| FEAT-025 | QC escalation | PM notification and disposition | Controlled failure handling |
| FEAT-026 | Override | Authorized request/approve/reject with mandatory reason | Exception governance |
| FEAT-027 | Rework | Temporary operation or route to earlier operation | Rework path |
| FEAT-028 | Restart | Restart from first operation in applicable process | Reprocessing traceability |
| FEAT-029 | Additional process | PM-authorized operation and mandatory QC | Controlled extra work |
| FEAT-030 | Vendor tracking | Vendor, operation, qty, expected return, status and delay | External processing visibility |
| FEAT-031 | RM vendor inward | Automatic notification on RM inward; Production QC data entry | Returned quantity enters QC |
| FEAT-032 | ECR integration | Send applicable exception data and retain reference | ECR costing workflow handoff |
| FEAT-033 | RM exception integration | Send applicable rejection/rework/additional process reference | RM coordination |
| FEAT-034 | Dashboard | Company/department/SC/set/component/operation metrics | Live visibility |
| FEAT-035 | Targets | Daily/weekly/monthly/custom targets for sets/components | Performance monitoring |
| FEAT-036 | Notifications | In-app/email centre, priorities, read/unread; Admin-managed preferences | Timely alerts |
| FEAT-037 | Reports | Role-controlled Excel/PDF exports | Operational reporting |
| FEAT-038 | Audit | Production, approval, integration and admin/security audit | Accountability |
| FEAT-039 | Packing | Complete-BOM verification and packing confirmation | Dispatch readiness |
| FEAT-040 | Dispatch | Dispatch-pending list and actual dispatch confirmation | Delivered status |
| FEAT-041 | Cancellation | Admin-only cancellation/deletion workflow with audit | Controlled record closure |

## Feature exclusions
No stock issue/return/request screens, no Vendor DC transaction screens, no vendor login, no billing, no MERC/Velan Dashboard integration, no AI Agent.
