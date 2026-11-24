# 01 — Business Requirements Document (BRD)

**Project:** Velan Production Management Application  
**Status:** Consolidated working baseline — Draft for business approval  
**Scope:** Production application only; AI Agent is explicitly excluded.

## 1. Purpose
Velan Production is the manufacturing execution and traceability application that manages work after an RM requisition/SC is submitted by V-SMART. It provides visibility and control from PO/SC intake through process setup, planning, shop-floor operations, measurements, QC, exceptions, vendor-operation tracking, packing and dispatch confirmation.

## 2. Business background and problem
Manufacturing work spans multiple products, components, operations, departments, material dependencies and quality checks. A single requisition can contain multiple SCs and product BOM structures. The business needs a reliable way to know which set/component is at which operation, who performed the work, what was measured, whether QC passed, what is waiting for material or a vendor, and which complete sets are ready for dispatch.

## 3. Business objectives
1. Automatically receive submitted RM requisitions and SC information.
2. Preserve the PO → SC → manufactured product → sub-product/component/BOM hierarchy.
3. Track every set and each component independently while calculating set completion.
4. Apply reusable, product-specific process templates and operation sequences.
5. Manage standard/static parameters and SC-specific tolerance values with approval and revision history.
6. Allocate work, plan capacity and show priorities to operators.
7. Capture operator measurements and independent QC measurements.
8. Control quantity splits, rework, restart, rejection and additional processes without losing quantity history.
9. Track vendor processing while keeping all Vendor DC transactions in RM.
10. Display RM material issue state and issue timing without duplicating inventory transactions.
11. Exchange applicable exception information with RM and ECR.
12. Verify complete BOM sets, packing and actual dispatch independently.
13. Provide role-appropriate dashboards, reports, notifications and audit history.

## 4. Business scope
### Included
- RM SC/requisition intake and updates; synchronization monitoring.
- PO/SC/product/BOM/set/component identity and QR traceability.
- Product masters, operation masters and reusable process templates.
- Design drawing and tolerance file management; extraction, review and approval.
- Scheduling, priority, workload and employee assignment.
- Operator worklists, operation execution, measurements and quantity splitting.
- QC inspection, disposition, overrides, rework and restart.
- Vendor-operation tracking and RM inward-triggered notifications.
- RM material status visibility and issue-time tracking.
- Production-to-RM and Production-to-ECR exception communication.
- Dashboards, targets, notifications, reports, audit, packing and dispatch.

### Excluded
- Stock/inventory balances and stock issue, return, adjustment or additional-material-request transactions.
- Vendor DC creation, dispatch, inward/receipt or correction transactions.
- Vendor login or vendor-entered production/QC measurements.
- ECR responsibility/cost calculation inside Production or return of ECR costing to Production.
- Billing, invoicing and billing approval.
- MERC and Velan Dashboard integrations.
- AI Agent / Velan AI.

## 5. Application boundaries
| System | Owns | Production relationship |
|---|---|---|
| RM — V-SMART | Requisition/SC, BOM source, stock, material issue/return, additional material requests, vendor DC dispatch and inward | Sends requisition, BOM, material status/timing and vendor inward/return status; receives applicable production exception references |
| Velan Production | Manufacturing planning, process routing, operations, operator/QC measurements, production quantities, rework, vendor-operation monitoring, packing and dispatch | Main application in this project |
| ECR | Responsibility assessment and rejection/rework/additional-process costing | Receives applicable exception information; Production does not calculate or store ECR costing as its own result |

MERC and Velan Dashboard remain independent. Billing remains outside Production.

## 6. Product and set business model
A PO can contain multiple SCs. An SC contains one or more manufactured products and their required BOM components. The screenshot supplied by the business shows PO number/customer, multiple SC cards, target quantity, manufactured product name and required raw-material/BOM rows.

A set is a logical production unit. A set is complete only when **every required BOM component for that set** has completed its required operations and passed required final QC. Example SC002 has target 4 sets and the illustrative BOM per set is 1 ARG, 2 SPG, 1 VBM and 3 Jig Feet. Four sets therefore require 4 ARG, 8 SPG, 4 VBM and 12 Jig Feet. Component names and quantities must come from the actual SC/BOM, not a hard-coded product assumption.

The system must identify components by SC, set/unit and component identity. The precise printed ID syntax is a configuration decision; a proposed pattern is `SC002-SET-001` with child component IDs. Do not finalize a serial format without business confirmation. Parent-child lineage must be unambiguous.

## 7. Users and governance
Confirmed primary roles: **Admin, PM (Production Manager), Operator, QC and Management**. Admin controls user creation, role/permission configuration, notification settings, deletion and cancellation. PM or Admin may provide final business acceptance. Detailed permissions are in Document 06 and remain configurable through RBAC.

## 8. High-level business lifecycle
RM submits SC → Production receives and validates it → product/BOM/set/component records are created → process templates and drawing/tolerance information are assigned → PM schedules and allocates work → material readiness is checked from RM → operator performs operation and enters measurements → QC independently inspects → passed quantity advances; failures follow PM-controlled disposition → vendor operations are tracked with RM as DC authority → all BOM components complete and pass final QC → packing verifies complete set → Dispatch Pending → dispatch employee confirms actual dispatch → Delivered.

## 9. Business outcomes and success measures
- Every accepted RM SC is traceable in Production.
- Set and component quantities reconcile exactly, including splits and exception routes.
- Operators cannot start material-dependent work before RM confirms required issue readiness.
- Every measurement is linked to the correct component/operation/tolerance revision and actor.
- QC decisions and PM approvals are auditable.
- Vendor pending/returned quantities reconcile with RM inward information.
- No incomplete BOM set can be packed/dispatched as a complete set.
- Packed status and Delivered status are distinct.
- Integration retries do not create duplicates or lose events.
- Dashboards reconcile with underlying production records.

## 10. Assumptions and unresolved items
- Exact RM and ECR API contracts, event names, authentication and external field names must be verified with the live systems.
- Product-specific BOM composition must be data-driven; the ARG/SPG/VBM/Jig Feet example is not a universal template.
- Exact component/set ID syntax needs final confirmation.
- Meaning of permanent scrap versus restart must be distinguished in the QC decision vocabulary; current confirmed restart means route from the beginning of the applicable production process.
- Performance targets, retention, backup objectives and deployment sizing require technical validation.

## 11. Approval
Business owner approval: Pending  
PM/Admin acceptance: Pending  
Version control: Update this document whenever a confirmed business decision changes.
