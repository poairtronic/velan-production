# 09 — Integration Specification

Updated 2026-11-24 — decisions D-01 to D-19 (see 00_DECISION_LOG.md)

**Notice:** The audits of the live RM and ECR code (2026-11-24) provide real endpoint and database entity names. JSON property names in API responses (which may differ from database column names) must be confirmed by calling the live endpoints as the first task of phase 13; mark them "VERIFY" until then.

## 1. Approach (D-11, pending OPEN — BUSINESS DECISION REQUIRED: O-04)
- RM is read-only. Production polls RM's existing read APIs with a read-only service account, plus a "refresh now" action. No RM code change.
- ECR receives exceptions through its existing intake. No ECR code change.
- Production never sends anything to RM. (D-09, BR-067)
- Integration is built in the flow, not at the end: RM connector (phase 13), ECR sender (phase 14), SC intake from RM (phase 18), material gate (phase 22), ECR sending (phase 31), replacement release (phase 32), vendor from RM (phase 35), reconciliation (phase 42).

## 2. Data flows (replaces the event catalogue)
| ID | Flow | Direction | Mechanism |
|---|---|---|---|
| INT-001 | SC, PO, customer, product lines and quantities | RM → Production | Poll; idempotent upsert; revisions by diff |
| INT-002 | Material issued yes/no per product | RM → Production | Poll material issues |
| INT-009 | Vendor DC details and return status | RM → Production | Poll DCs |
| INT-010 | ECR material issued (for replacement release) | RM → Production | Poll ECR issues |
| INT-006/007/008 | REJECTION, REWORK, ADDITIONAL_PROCESS exceptions | Production → ECR | Outbox; HTTP POST once |
| INT-003/004/005 | Exceptions to RM | — | **Removed** (D-09) |

## 3. RM read endpoints (all need a JWT; use a service account)
`GET /api/sc`, `/api/sc/:id`, `/api/sc/traceability/:poNumber`, `/api/rm`, `/api/rm/:id`, `/api/rm/stores/queue`, `/api/material-issues`, `/api/material-issues/:id`, `/api/production/receipts`, `/api/delivery-challans`, `/api/delivery-challans/:id`, `/api/delivery-challans/movements`, `/api/ecr-issues` (Stores/Production/Admin roles).

## 4. RM field mapping (database names from the audit; API property names: VERIFY)
| RM source | RM field | Production field | Note |
|---|---|---|---|
| `sales_order_components` | `id`, `sc_number`, `po_id`, `product_name`, `target_quantity`, `status`, `drawing_number` | `sc.external_ref`, `sc.sc_number`, … | `status` (VERIFY): DRAFT, SUBMITTED, STORES_PENDING, PARTIALLY_ISSUED, ISSUED, IN_PRODUCTION, ADDITIONAL_REQUEST, COMPLETED, CLOSED, REJECTED. Only intake when submitted. |
| `purchase_orders` | `po_number`, `reference_date`, `external_reference`, `customer_id` | `sc.po_number`, optional due date | `reference_date` as delivery date is OPEN — BUSINESS DECISION REQUIRED: O-01. (VERIFY) |
| `customers` | `code`, `name` | `sc.customer` | (VERIFY) |
| `rm_requests` | `id`, `sc_id`, `status`, `revision_number`, `submitted_at` | revision tracking | RmRequestStatus (VERIFY): DRAFT, SUBMITTED, REVIEWED, COMPLETED, REJECTED. |
| `rm_items` (one per product, D-02) | `id`, `part_name`, `part_number`, `material`, `material_type`, `grade`, `size`, `quantity`, `weight_unit` | `sc_product.external_ref`, name, quantity | `quantity` = piece count. Lines can be edited by Admin or hard-deleted after submit: the sync must diff every poll. (VERIFY) |
| `material_issue_items` | `rm_item_id`, `quantity_issued` | `sc_product.material_issued` | Issued = sum(quantity_issued) per `rm_item_id`; issued yes/no rule to be fixed in phase 22 (assumed: any issue = yes; OPEN — BUSINESS DECISION REQUIRED: O-10). (VERIFY) |
| `delivery_challans` | `id`, `challan_number`, `type`, `status`, `vendor_id`, `process_name`, `sc_id` | `vendor_batch` | Read only `type = PRODUCTION_PROCESS_OUTWARD`. Number format `SDC/YYYY/####`. (VERIFY) |
| `delivery_challan_items` | `id`, `part_name`, `quantity_dispatched`, `actual_received_qty` | `vendor_batch` quantities | **No `rm_item_id`**: match by SC + `part_name`; unmatched lines go to a manual "map this line" screen. `part_number` is overwritten with the SC code, so never match on it. (VERIFY) |
| Return status | derived | received / partial / not received | `actual_received_qty` = 0 → not received; < dispatched → partial; ≥ dispatched → received. Do **not** use the DC `status` (after dock receipt it is always PARTIALLY_RETURNED until RM's QC closes it). |
| `ecr_material_pending` | `ecr_id`, `report_number`, `type`, `sc_number`, `component`, `quantity_affected`, `status`, `issued_at` | replacement release | `status` (VERIFY): PENDING, ISSUED. Match `report_number` with the stored ECR report number. |

## 5. Production → ECR
- `POST {ECR_API_URL}/api/internal/rm-intake`, header `x-internal-secret` (value from the Render environment, never in code or logs).
- Request fields: `type` (REWORK or REJECTION), `scNumber`*, `poNumber`*, `component`*, `quantityAffected`* (integer ≥ 1), `defectDescription`*, `raisedByName`*, and optional `stageOfFailure`, `errorType`, `operatorName` (not persisted by ECR), `reworkDescription`, `rejectionDescription`, `rejectionProcessTemplate`, `rejectionFailedStage`, `rejectionStageCosts`, `costEstimation`, `lossEstimation`, `alternativeNotes`. (* required)
- ADDITIONAL_PROCESS is sent as REWORK with the text "[ADDITIONAL PROCESS]" at the start of `defectDescription` (OPEN — BUSINESS DECISION REQUIRED: O-04).
- Put the Production exception id in `defectDescription` (for example "Ref PRD-EXC-000123") so a report can be matched manually.
- Response `{reportId, reportNumber}`; store `reportNumber` on the exception. ECR creates the report in status PENDING_ACCOUNTS_REVIEW.
- **Idempotency:** ECR only has a 10-minute in-memory duplicate cache. The Production outbox sends once (PENDING → SENDING → SENT) and never auto-resends after an ambiguous timeout; it flags the item for Admin to check ECR. (BR-062)
- `poNumber` is required by ECR, so Production must hold the PO number on every SC (manual and Excel entry included).

## 6. Reliability and security (replaces §8)
- Sync log table per poll: started, finished, counts, errors; idempotent upserts by RM ids.
- Retry with bounded backoff; alert Admin and PM after repeated failures; "refresh now" for users with permission.
- Read-only service account for RM; secrets in environment variables; never log tokens or secrets.
- Reconciliation screen (phase 42): SCs missing in either side, quantity mismatches, unsent or ambiguous ECR exceptions.

## 7. Exact field checklist (replaces §9)
| Source System | External Property Name | Casing | Type | Required | Nullable | Unit | Destination Field | Status |
|---|---|---|---|---|---|---|---|---|
| RM API | VERIFY in phase 13 | VERIFY | string/number | Yes | No | — | `sc.external_ref` | VERIFY |
| RM API | VERIFY in phase 13 | VERIFY | string | Yes | No | — | `sc.sc_number` | VERIFY |
| RM API | VERIFY in phase 13 | VERIFY | string | Yes | No | — | `sc.po_number` | VERIFY |
| RM API | VERIFY in phase 13 | VERIFY | number | Yes | No | — | `sc_product.quantity` | VERIFY |
| ECR API | `scNumber` | camelCase | string | Yes | No | — | Inbound from Prod | Confirmed |
| ECR API | `poNumber` | camelCase | string | Yes | No | — | Inbound from Prod | Confirmed |
| ECR API | `component` | camelCase | string | Yes | No | — | Inbound from Prod | Confirmed |
| ECR API | `quantityAffected` | camelCase | integer | Yes | No | — | Inbound from Prod | Confirmed |
| ECR API | `defectDescription` | camelCase | string | Yes | No | — | Inbound from Prod | Confirmed |
| ECR API | `raisedByName` | camelCase | string | Yes | No | — | Inbound from Prod | Confirmed |
*(Checklist values marked VERIFY must be confirmed against live responses during phase 13).*

## 8. Excluded (replaces §10)
No Production-to-RM messages, no stock or DC transactions, no ECR cost return, no billing, no MERC/Velan Dashboard integration now (read later, D-12).
