# 09 — Integration Specification

**Important:** The requirement PDF lists proposed business fields, not a verified live API contract. The latest business instruction is to use exact field names. Therefore, external property names, casing, data types and endpoint paths must be copied from the actual RM/ECR contract when provided; do not invent or rename them in the integration adapter.

## 1. Systems and ownership
- RM V-SMART: source of SC/requisition, BOM/material and vendor DC/inward facts.
- Velan Production: production workflow, quantity lineage, measurements, QC, rework, vendor-operation monitoring, packing and dispatch.
- ECR: responsibility/costing workflow for applicable exceptions.

## 2. Integration event catalogue
| ID | Source → Destination | Trigger | Expected result |
|---|---|---|---|
| INT-001 | RM → Production | SC/requisition submitted | Create/refresh production record and hierarchy |
| INT-002 | RM → Production | Material issue/status updated | Update required/issued/pending/shortage and issue timing |
| INT-003 | Production → RM | Applicable rejection recorded | Send exception reference/details |
| INT-004 | Production → RM | Applicable rework recorded | Send exception reference/details |
| INT-005 | Production → RM | Additional process recorded | Send process reference/details |
| INT-006 | Production → ECR | Applicable rejection recorded | Create/link ECR issue |
| INT-007 | Production → ECR | Applicable rework recorded | Create/link ECR issue |
| INT-008 | Production → ECR | Additional process where applicable | Send agreed exception details |
| INT-009 | RM → Production | Vendor inward/return recorded | Automatically notify Production and expose returned quantity for QC entry |

No ECR costing/responsibility result returns to Production under current confirmed scope.

## 3. RM → Production: SC intake
### Trigger
RM successfully submits an SC/requisition.

### Business data needed
- RM requisition/SC unique identifier.
- PO reference and customer reference/name where available.
- SC number, creation/submission date and status.
- Product/main product and component/sub-product identifiers and names.
- Set target quantity and component/BOM quantities.
- BOM and material identifiers, descriptions, required quantities and units where available.
- Delivery date where available.
- Source revision/update reference.

### Processing rules
1. Authenticate the RM source.
2. Validate required source identity and payload structure.
3. Deduplicate by stable RM SC/requisition/event identifier.
4. Persist inbound event before business processing.
5. Create/update production hierarchy transactionally.
6. Acknowledge success/failure with correlation reference.
7. Preserve original external identifiers and exact payload/property names in integration logs.
8. Notify Admin and PM when SC synchronization fails.

## 4. RM → Production: material issue
### Trigger
Stores records issue or issue update in RM.

### Data required
RM requisition/SC reference, material code/ID, required quantity, issued quantity, pending quantity if supplied, issue status, RM transaction reference, issue date/time, Store reference if supplied.

### Production behaviour
- Update material visibility and last-source-update time.
- Calculate readiness only from source data and configured operation requirement.
- Block material-dependent operation data entry until required issue condition is met.
- Never create or mutate RM stock transactions.

## 5. RM → Production: vendor inward
### Trigger
RM records vendor return/inward.

### Data required
RM DC reference, SC/requisition reference, vendor reference, operation/product/component reference where available, dispatched/returned/pending quantities, inward date/time, status and event identifier.

### Production behaviour
- Automatically notify Production on RM inward event.
- Update vendor-returned and vendor-pending quantity.
- Make only RM-confirmed returned quantity available for internal QC.
- Production QC user independently enters QC measurements.
- Partial inward leaves unreturned balance vendor-pending.

## 6. Production → RM: exception information
Trigger only for applicable rejection, rework or additional-process records. Proposed business mapping includes Production record ID, RM requisition ID, SC, product/component, operation, unique exception ID, exception type, affected quantity, reason, QC reference, actor and timestamp. Exact RM field names and payload must be confirmed against RM's actual interface. RM acknowledges and handles its own material/stock responsibilities.

## 7. Production → ECR: exception information
Send only the relevant data needed for ECR to create/link its own record: source references, SC, product/component, operation, unique exception ID, exception type, affected quantity, relevant measurements/tolerance revision if agreed, QC reference, reason, supporting document references, actor and timestamp. ECR performs responsibility and costing independently. Production does not calculate, overwrite or consume ECR costing results.

## 8. Reliability and security
- Use authenticated service-to-service communication.
- Use stable event IDs and idempotency keys.
- Store inbound/outbound events durably.
- Retry transient failures with bounded backoff; expose exhausted failures for Admin/PM review.
- Return acknowledgements and record them.
- Maintain correlation IDs across RM, Production and ECR.
- Validate schema/version, source identity, quantities and references.
- Reconcile source and receiver records periodically or through an authorized reconciliation tool.
- Never log credentials, tokens or secrets.

## 9. Exact field mapping checklist
For every integration field, document: source system, exact external property name, exact casing, type, required/optional, nullability, unit, example (non-sensitive), destination field, transformation (if any), validation, event, owner and version. Until the RM/ECR contract is inspected, mark external names **TBD — do not guess**.

## 10. Explicitly excluded integrations
No Production stock issue/return/additional request; no Production Vendor DC creation/receipt; no ECR cost return; no billing exchange; no MERC; no Velan Dashboard.
