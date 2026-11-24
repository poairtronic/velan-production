# 03 — Application Scope

Updated 2026-11-24 — decisions D-01 to D-19 (see 00_DECISION_LOG.md)

## 1. Purpose
This document defines what Velan Production will and will not do, and clarifies ownership between RM V-SMART, Production and ECR.

## 2. In-scope modules
1. Authentication, user/role/permission administration (six roles, individual login). (D-03)
2. SC intake: RM sync (read), manual entry, Excel import; production record creation. (D-01, D-11)
3. Product and piece hierarchy (PO → SC → product → piece). (D-02)
4. Unique piece serials, lineage, and search (QR labels and scanning deferred; OPEN — BUSINESS DECISION REQUIRED: O-08).
5. Process template and operation master management.
6. Design drawing/tolerance upload, extraction, Designer verification and PM approval.
7. PM daily priority, calendar, capacity, forecast and work allocation. (D-15)
8. Operator worklists and operation execution (bulk entry grid, ready/opened/submitted timestamps, no start/stop buttons). (D-16)
9. Operator measurement entry (five parameter types). (D-14)
10. Independent QC inspection on every process and PM decisions on flags. (D-06, D-07)
11. Quantity splitting, extra process, return to earlier operation, reject and restart, replacement pieces. (D-08, D-19)
12. Vendor tracking from RM DC details and return status (received/partial/not received); no vendor dates. (D-05)
13. Material-issued flag per product (RM sync or manual). (D-04)
14. Production-to-ECR exception sending. (D-09)
15. Dashboard, reports, targets, performance, daily 9 AM report (Excel + charts), and notifications.
16. Automatic completion: Packed, SC Delivered. (D-10)
17. Audit, integration monitoring, reconciliation of Production against RM and ECR, and administrative correction/cancellation.

## 3. Out of scope
- QR codes and scanning (deferred; OPEN — BUSINESS DECISION REQUIRED: O-08).
- Packing and dispatch screens, statuses, roles or dispatch confirmation.
- Production-to-RM messages; any write to RM.
- Vendor selection in Production; vendor expected return dates; vendor alerts to RM.
- Inventory ledger, stock balances and stock transactions.
- Material issue/return/additional request creation.
- Vendor DC creation, dispatch, inward, receipt and corrections.
- Vendor portal/login and vendor-entered values.
- ECR costing/responsibility engine or ECR costing returned into Production.
- Billing, invoice, invoice register or billing approvals.
- MERC or Velan Dashboard integrations: none now; read access later; write only if required in future. (D-12)
- AI Agent / Velan AI.
- DN/RMA unless approved through a separate scope change.

## 4. System responsibility matrix
| Capability | RM | Production | ECR |
|---|---|---|---|
| Create SC/requisition | Owns | Receives (plus manual entry & Excel import) | — |
| BOM/material source | Owns source | Reads/displays | — |
| Stock and issue/return | Owns | No transactions | — |
| Material issued | Owns | Reads yes/no or PM sets manually | — |
| Production routing/measurements | — | Owns | — |
| QC/rework tracking | — | Owns | Receives relevant issue data |
| Vendor DC dispatch/inward | Owns | Reads DC details and return status; no alerts to RM | — |
| Exception costing | — | Sends exception to ECR; stores ECR report number | Owns |
| Completion (Packed/Delivered) | — | Owns (automatic) | — |
| Billing | Outside Production | Outside Production | — |

## 5. Boundary rules
- RM is the source of truth for SC, material transaction and vendor DC facts.
- Production must not create a second stock/DC transaction system.
- Production does not write to RM. It writes to ECR only to send exceptions.
- Production may maintain derived production readiness and quantity calculations from RM-supplied data; it must label source and last-update time.
- ECR is authoritative for responsibility/costing; Production stores the outbound reference and the returned ECR report number.
- No ECR costing or responsibility result is returned to Production under the currently confirmed scope.
- A piece is Packed when all operations pass; the SC is Delivered automatically when all products are complete. Billing is outside Production. (D-10)

## 6. Scope-change control
Any proposed new integration, stock transaction, vendor portal, billing feature, AI capability or additional application boundary must be documented as a change request with business owner, impact, data ownership, security impact, cost and acceptance approval. Do not implement by assumption.
