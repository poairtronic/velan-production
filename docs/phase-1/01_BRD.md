# 01 — Business Requirements Document (BRD)

Updated 2026-11-24 — decisions D-01 to D-19 (see 00_DECISION_LOG.md)

**Project:** Velan Production Management Application  
**Status:** Updated 2026-11-24 with decisions D-01 to D-19.  
**Scope:** Production application only; AI Agent is explicitly excluded.

## 1. Purpose
Velan Production is the manufacturing execution and traceability application that manages work after an RM requisition/SC is submitted by V-SMART. It provides visibility and control from PO/SC intake through process setup, planning, shop-floor operations, measurements, QC, exceptions, vendor-operation tracking and automatic completion (Packed, then SC Delivered). Billing is outside Production. (D-10)

## 2. Business background and problem
Manufacturing work spans multiple products, components, operations, departments, material dependencies and quality checks. A single requisition can contain multiple SCs and products. The business needs a reliable way to know which piece is at which operation, who performed the work, what was measured, whether QC passed, what is waiting for material or a vendor, and when pieces and products reach completion.

## 3. Business objectives
1. Receive SC data from RM automatically, and also allow manual SC entry and Excel import. (D-01, D-11)
2. Preserve the PO → SC → product → piece hierarchy.
3. Track every **piece** individually and calculate product and SC completion from piece status. (D-02)
4. Apply reusable, product-specific process templates and operation sequences.
5. Manage standard/static parameters and SC-specific tolerance values with approval and revision history.
6. Allocate work, show PM daily priority list per operator, plan capacity and show priorities to operators. (D-15)
7. Capture operator measurements and independent QC measurements.
8. Control quantity splits, rework, restart, rejection and additional processes without losing quantity history, including replacement pieces with linked serials. (D-08)
9. Show pieces processed by vendors and whether the return is received; all DC transactions stay in RM. (D-05)
10. Show whether Stores has issued material (issued yes/no per product). (D-04)
11. Send rework, rejection and additional-process information to **ECR**. (remove RM) (D-09)
12. Complete automatically: piece Packed → product complete → SC Delivered. (D-10)
13. Provide role-appropriate dashboards, reports, notifications and audit history.
14. Reduce operator data-entry effort (bulk grid, no start/stop). (D-16)
15. Let the PM add a process to a piece after a mistake without a slow rework tab.

## 4. Business scope
### Included
- RM SC/requisition intake and updates; synchronization monitoring.
- Manual SC entry and Excel SC import.
- PO/SC/product/piece identity and search.
- Product masters, operation masters and reusable process templates.
- Design drawing and tolerance file management; extraction, review and approval.
- PM daily priority list per operator; scheduling, capacity, workload and employee assignment.
- Operator worklists, bulk measurement entry per piece, and operation execution.
- QC inspection, disposition, PM QC decisions (CONTINUE with reason, EXTRA PROCESS, RETURN TO EARLIER OPERATION, REJECT AND RESTART).
- Replacement pieces with linked serials.
- Vendor-operation tracking based on RM DC details and return status (received/partial/not received).
- Material-issued flag per product (RM sync or manual PM/Admin flag).
- Production-to-ECR exception communication.
- Role-appropriate dashboards, daily 9 AM report (Excel + charts), targets, notifications, and audit.

### Excluded
- QR codes and scanning (deferred; OPEN — BUSINESS DECISION REQUIRED: O-08).
- Packing and dispatch screens, statuses, roles or dispatch confirmation.
- Stock/inventory balances and stock issue, return, adjustment or additional-material-request transactions.
- Vendor DC creation, dispatch, inward/receipt or correction transactions.
- Vendor alert to RM or tracking of vendor expected return dates.
- RM material shortage/issue-timing analytics (keep only the issued flag and issue time if RM supplies it).
- Production-to-RM exception communication; any writes to RM.
- Vendor login or vendor-entered production/QC measurements.
- ECR responsibility/cost calculation inside Production or return of ECR costing to Production.
- Billing, invoicing and billing approval.
- MERC/Velan Dashboard: none now; read access later; write only if required in future. (D-12)
- AI Agent / Velan AI.

## 5. Application boundaries
| System | Owns | Production relationship |
|---|---|---|
| RM — V-SMART | Requisition/SC, stock, material issue/return, vendor DC dispatch and inward | Receives from RM (read): SC, product quantities, material-issued flag, vendor DC details and return status. Production sends nothing to RM. |
| Velan Production | Manufacturing planning, process routing, operations, operator/QC measurements, piece tracking, PM decisions, vendor-operation monitoring, and automatic completion (Packed, Delivered) | Main application in this project |
| ECR | Responsibility assessment and rejection/rework/additional-process costing | Receives exception information from Production; returns only an acknowledgement/report number. Costing/responsibility stays in ECR. |

MERC and Velan Dashboard remain independent (no integration now; read access later; write only if required in future). Billing remains outside Production.

## 6. Product and piece business model
A PO can contain multiple SCs. An SC contains one or more manufactured products. Under each product, individual pieces are tracked. (D-02)

Piece count per product is the quantity RM sends for that product (one RM line per product; SRG is a set = 2 numbers and RM already sends the number; e.g. APG 50 means 100 SRG). (D-02)

Proposed serial format: `SC-2579-APG-001` (OPEN — BUSINESS DECISION REQUIRED: O-02).

*Set concept:* The previous drafts defined set-level completion and partial dispatch of complete sets. Confirmed rule D-10 completes the SC automatically when all products are complete. Whether "set" remains as a logical grouping (e.g. per SC target quantity) or the SC is the only delivery unit is OPEN — BUSINESS DECISION REQUIRED (O-03). The earlier SC002 ARG/SPG/VBM/Jig-feet example is illustrative only.

Parent-child lineage for replacement pieces is tracked via `replaced_piece_id`.

## 7. Users and governance
Confirmed roles: **Admin, PM, Designer, Operator, QC, Management**. (D-03)  
Packing, Dispatch, Stores, Vendor Coordinator and Department Head are not roles. Stores actions stay in RM. Admin controls user creation, role/permission configuration, notification settings, deletion and cancellation. PM or Admin may provide final business acceptance. Detailed permissions are in Document 06 and remain configurable through RBAC. Individual login per employee.

## 8. High-level business lifecycle
SC from RM, manual entry or Excel → products and pieces → process flow and tolerance → PM priority → material issued gate → operator values → QC values → PM decision on flags → vendor pieces tracked via RM DC details → all operations passed: Packed → all pieces Packed: SC Delivered automatically. (D-10)

## 9. Business outcomes and success measures
- Every accepted RM SC, manual SC and imported SC is traceable in Production.
- Every piece has a complete history including replacements.
- Operators cannot start material-dependent work before material-issued flag is set.
- Every measurement is linked to the correct piece/operation/tolerance revision and actor.
- QC decisions and PM approvals are auditable.
- Vendor return status (received/partial/not received) matches RM.
- A piece becomes Packed automatically when all its operations pass; the SC becomes Delivered automatically when all products are complete.
- Integration retries do not create duplicates or lose events.
- Dashboards reconcile with underlying production records.

## 10. Assumptions and unresolved items
### Closed decisions (see 00_DECISION_LOG.md)
- Root record is RM SC, plus permanent manual entry and Excel import (D-01).
- Tracking is per individual piece (D-02).
- Six primary roles confirmed (D-03).
- Material gate: issued flag yes/no per product (D-04).
- Vendor tracking: read RM DC details and return status; no dates, no alerts to RM (D-05).
- QC on every process; blind QC entry (D-06).
- PM QC decisions: CONTINUE (reason), EXTRA PROCESS, RETURN TO EARLIER OP, REJECT AND RESTART (D-07).
- Rejection scraps piece and creates linked replacement piece at operation 1 (D-08).
- Exceptions sent to ECR only; no exceptions to RM (D-09).
- Automatic completion: piece Packed → product complete → SC Delivered (D-10).
- Stack: Java 21, Spring Boot, React, Vite, Postgres, Neon, R2, Render. No Redis, no RabbitMQ (D-13).

### Open items (OPEN — BUSINESS DECISION REQUIRED)
- O-01: Source of the delivery/due date (RM `purchase_orders.reference_date` vs manual/Excel per SC).
- O-02: Confirm piece serial format (`SC-2579-APG-001`).
- O-03: Set concept — decide whether "set" remains as a grouping or SC is the only delivery unit.
- O-05: SC cancellation / quantity reduction after work has started.
- O-06: Excel formats: SC import sheet and tolerance sheet.
- O-11: Backup, retention and performance targets.

## 11. Approval
Business owner approval: Pending  
PM/Admin acceptance: Pending  
Version control: Update this document whenever a confirmed business decision changes.
