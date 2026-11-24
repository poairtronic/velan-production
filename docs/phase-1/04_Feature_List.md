# 04 — Feature List

Updated 2026-11-24 — decisions D-01 to D-19 (see 00_DECISION_LOG.md)

Feature IDs are stable references for planning, user stories and testing (IDs FEAT-001 to FEAT-050).

| ID | Module | Feature | Main outcome |
|---|---|---|---|
| FEAT-001 | Identity | Individual login per employee, sessions, login history | Authenticated users |
| FEAT-002 | RBAC | **Six** roles (Admin, PM, Designer, Operator, QC, Management), custom roles, multi-role, action permissions, user overrides | Controlled access |
| FEAT-003 | Admin | Employee activation, suspension, scheduling and reassignment alerts | Managed workforce |
| FEAT-004 | PO/SC | SC intake from RM sync; also manual entry and Excel import (see FEAT-042, FEAT-043) | Production record created from RM or direct entry |
| FEAT-005 | Integration | RM sync log, refresh now, retry, failed-sync alerts; ECR outbox status | Reliable sync |
| FEAT-006 | Hierarchy | PO → SC → product → piece | Correct business structure |
| FEAT-007 | Set tracking | [REMOVED from baseline — OPEN — BUSINESS DECISION REQUIRED: O-03] Per-set records (keep only if set concept is confirmed) | Evaluated under O-03 |
| FEAT-008 | Piece tracking | Unique piece serial with link to product; replacement pieces linked to the scrapped piece | Complete piece traceability |
| FEAT-009 | Search | [REMOVED: QR labels and scanning deferred under O-08] Search by PO, SC, product and piece serial | Fast identification |
| FEAT-010 | Master data | Product, component, operation, department, unit, vendor, reason masters | Standard data |
| FEAT-011 | Templates | Templates with fixed machine/operator or vendor per operation; Designer selects the flow; flow snapshot per product | Repeatable routing |
| FEAT-012 | Design files | CAD/drawing PDF attachment and controlled access | Drawing availability |
| FEAT-013 | Tolerance upload | Excel-first and PDF tolerance upload; Designer uploads | SC-specific tolerance intake |
| FEAT-014 | Tolerance extraction | Extract/map by SC + product + operation + parameter | Structured tolerance data |
| FEAT-015 | Tolerance verification | Designer review/correction and PM approval | Approved tolerance revision |
| FEAT-016 | Tolerance change | PM approval, impact analysis, notification and version history; Designer requests changes | Controlled changes |
| FEAT-017 | Planning | Calendar, workdays, holidays, shift, workload and capacity | Realistic schedule |
| FEAT-018 | Priority | PM daily priority list per operator | Clear daily work order |
| FEAT-019 | Assignment | Assign/reassign operator/department and track workload | Ownership of work |
| FEAT-020 | Operation execution | Operation execution with ready/opened/submitted timestamps; no start/stop buttons | Operation history |
| FEAT-021 | Piece status counts | Piece status counts (pending, ready, submitted, QC, flagged, at vendor, packed); no manual quantity buckets | Conservation and traceability |
| FEAT-022 | Material view | Material-issued flag per product (RM sync or manual); blocks the product's pieces until set | Material gate enforcement |
| FEAT-023 | Operator measurements | Operator values per piece in a bulk grid; five parameter types | Operator measurement record |
| FEAT-024 | QC measurements | QC values on every process, independent of operator values | Quality release control |
| FEAT-025 | QC escalation | Flag raises a non-blocking alert; the piece waits for the PM decision | Controlled failure handling |
| FEAT-026 | Override | [REMOVED as separate workflow; replaced by CONTINUE with reason in FEAT-044] | Handled via FEAT-044 |
| FEAT-027 | Rework | Extra process, or return to an earlier operation with new instructions | Controlled rework path |
| FEAT-028 | Restart | Reject and restart: scrap, replacement piece with a new linked serial, starting at operation 1 | Reprocessing traceability |
| FEAT-029 | Additional process | Extra process (new operation with its own tolerance list); QC mandatory | Controlled extra work |
| FEAT-030 | Vendor tracking | Vendor tracking from RM DC details: vendor, DC number, process, product, quantity; shows pieces at vendor. No dates | External processing visibility |
| FEAT-031 | RM vendor inward | RM return status (received/partial/not received) read by Production; pieces tagged on return; QC enters values | Returned pieces enter QC |
| FEAT-032 | ECR integration | Send REWORK, REJECTION and additional-process exceptions to ECR; store the ECR report number; Production never waits | ECR exception handoff |
| FEAT-033 | RM exception integration | [REMOVED: Production sends no exceptions to RM (D-09)] | Eliminated per D-09 |
| FEAT-034 | Dashboard | Dashboard: machines, vendors, out-of-tolerance, near-complete SCs, rejection quantity, planned vs actual | Live visibility |
| FEAT-035 | Targets | Daily/weekly/monthly/custom targets for products/pieces | Performance monitoring |
| FEAT-036 | Notifications | In-app/email centre, priorities, read/unread; Admin-managed preferences | Timely alerts |
| FEAT-037 | Reports | Role-controlled Excel/PDF exports | Operational reporting |
| FEAT-038 | Audit | Production, approval, integration and admin/security audit | Accountability |
| FEAT-039 | Packing | [REMOVED: Replaced by automatic completion in FEAT-045 (D-10)] | Handled via FEAT-045 |
| FEAT-040 | Dispatch | [REMOVED: Replaced by automatic completion in FEAT-045 (D-10)] | Handled via FEAT-045 |
| FEAT-041 | Cancellation | Admin-only cancellation/deletion workflow with audit | Controlled record closure |
| FEAT-042 | Manual SC entry | Manual SC entry (SC, PO, customer, products, quantities, optional due date) | Direct production intake |
| FEAT-043 | Excel SC import | Excel SC import with validation, preview and audit trail | Bulk SC creation |
| FEAT-044 | PM QC decision | PM QC decision: CONTINUE (reason), EXTRA PROCESS, RETURN TO EARLIER OPERATION, REJECT AND RESTART | Binding exception resolution |
| FEAT-045 | Automatic completion | Automatic completion: Packed (all operations passed), product complete, SC Delivered | Automatic delivery transition |
| FEAT-046 | Bulk entry grid | Bulk entry grid with paste from Excel | High-efficiency operator entry |
| FEAT-047 | Replacement release | Replacement piece release (RM ECR material issued, or PM manual release with reason) | Replacement material gating |
| FEAT-048 | 9 AM report | Daily 9 AM report (Excel + charts) matching dashboard totals | Executive reporting |
| FEAT-049 | Reconciliation | Reconciliation screen: Production vs RM and ECR | Cross-system data integrity |
| FEAT-050 | In-progress SC import | Import of an in-progress SC on PM request | Controlled transition |

## Feature exclusions
No stock issue/return/request screens, no Vendor DC transaction screens, no vendor login, no billing, no MERC/Velan Dashboard integration, no AI Agent, no QR/scanning (deferred), no packing/dispatch screens, no writes to RM.
