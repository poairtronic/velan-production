# 03 — Application Scope

## 1. Purpose
This document defines what Velan Production will and will not do, and clarifies ownership between RM V-SMART, Production and ECR.

## 2. In-scope modules
1. Authentication, user/role/permission administration.
2. PO/SC intake, synchronization and production record creation.
3. Product, BOM, set and component hierarchy.
4. Unique set/component IDs, QR labels, scanning and traceability.
5. Process template and operation master management.
6. Design drawing/tolerance upload, extraction, verification and approval.
7. Production planning, calendar, capacity, priority and work allocation.
8. Operator worklists and operation execution.
9. Operator measurement entry.
10. Independent QC inspection and decisions.
11. Quantity splitting, rework, restart, override and additional processes.
12. Vendor operation tracking with RM DC references and RM inward notifications.
13. RM material status, shortage, issue quantity and issue timestamp visibility.
14. Production-to-RM and Production-to-ECR exception notifications/data exchange.
15. Dashboard, reports, targets, performance and notifications.
16. Packing verification, Dispatch Pending and dispatch confirmation.
17. Audit, integration monitoring and administrative correction/cancellation.

## 3. Out of scope
- Inventory ledger, stock balances and stock transactions.
- Material issue/return/additional request creation.
- Vendor DC creation, dispatch, inward, receipt and corrections.
- Vendor portal/login and vendor-entered values.
- ECR costing/responsibility engine or ECR costing returned into Production.
- Billing, invoice, invoice register or billing approvals.
- MERC or Velan Dashboard integrations.
- AI Agent/Velan AI.
- DN/RMA unless approved through a separate scope change.

## 4. System responsibility matrix
| Capability | RM | Production | ECR |
|---|---|---|---|
| Create SC/requisition | Owns | Receives | — |
| BOM/material source | Owns source | Reads/displays | — |
| Stock and issue/return | Owns | No transactions | — |
| Production routing/measurements | — | Owns | — |
| QC/rework tracking | — | Owns | Receives relevant issue data |
| Vendor DC dispatch/inward | Owns | Reads event/status and tracks operation | — |
| Exception costing | — | Sends event/reference | Owns |
| Packing/dispatch status | — | Owns | — |
| Billing | Outside Production | Outside Production | — |

## 5. Boundary rules
- RM is the source of truth for SC, material transaction and vendor DC facts.
- Production must not create a second stock/DC transaction system.
- Production may maintain derived production readiness and quantity calculations from RM-supplied data; it must label source and last-update time.
- ECR is authoritative for responsibility/costing; Production stores the outbound reference and any explicitly agreed non-costing status needed for traceability only.
- No ECR costing or responsibility result is returned to Production under the currently confirmed scope.
- Packing and dispatch are distinct. A set becomes Delivered only after dispatch confirmation.

## 6. Scope-change control
Any proposed new integration, stock transaction, vendor portal, billing feature, AI capability or additional application boundary must be documented as a change request with business owner, impact, data ownership, security impact, cost and acceptance approval. Do not implement by assumption.
