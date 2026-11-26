# Part 3.7 — Integration Adapter Conventions
## Document 02: Raw Material System (RM – V-SMART) Read Connector Specification

---

### 1. Confirmed Rules

#### 1.1 Service Account & Authentication Lifecycle
1. **Least-Privilege Service Account:** The connector authenticates against RM using a dedicated service account credentials (`ROLE_RM_CONNECTOR`).
2. **Read-Only Scope:** The service account possesses strictly read-only grants in RM (`Stores`, `Production`, or `Admin` read scopes).
3. **JWT Acquisition & Caching:**
   * The connector logs into RM via `POST /api/auth/login` (VERIFY) to obtain a short-lived Bearer JWT.
   * The token is cached in Caffeine with a 5-minute safety buffer before expiration.
   * All outbound read queries include the header `Authorization: Bearer <jwt>`.

#### 1.2 Polling Schedule and Manual "Refresh Now"
* **Automated Background Polling:** A Spring `@Scheduled` worker executes every **180 seconds (3 minutes)** when `RM_SYNC_ENABLED = true`.
* **On-Demand "Refresh Now" Action:** Users with permission `INTEGRATION:RM_SYNC` can trigger immediate polling via `POST /api/v1/integrations/rm/sync-status`.
* **Throttling Guard:** On-demand sync is throttled to at most once every **60 seconds** per tenant to prevent upstream load spikes.

#### 1.3 The Four Inbound Data Flows (INT-001, INT-002, INT-009, INT-010)

#### Flow 1: Sales Contract & Requisition Ingestion (INT-001)
* **Source Endpoints:** `GET /api/sc`, `/api/sc/:id`, `/api/sc/traceability/:poNumber`, `/api/rm`, `/api/rm/:id`.
* **Audited RM Source Entities & Fields (`09_Integration_Specification.md:20-25`):**
  - `sales_order_components`: `id`, `sc_number`, `po_id`, `product_name`, `target_quantity`, `status`, `drawing_number`. *(Only intake when status is `SUBMITTED`, `STORES_PENDING`, or `IN_PRODUCTION`).*
  - `purchase_orders`: `po_number`, `reference_date`, `external_reference`, `customer_id`.
  - `customers`: `code`, `name`.
  - `rm_requests`: `id`, `sc_id`, `status`, `revision_number`, `submitted_at`.
  - `rm_items` (one per product line, D-02): `id`, `part_name`, `part_number`, `material`, `material_type`, `grade`, `size`, `quantity`, `weight_unit`.
* **Mapping to Velan Production Entities:**
  - `sales_order_components.id` $\to$ `sc_headers.external_ref`
  - `sales_order_components.sc_number` $\to$ `sc_headers.sc_number`
  - `purchase_orders.po_number` $\to$ `sc_headers.po_number`
  - `customers.name` $\to$ `sc_headers.customer_name`
  - `rm_items.id` $\to$ `sc_products.external_ref`
  - `rm_items.part_name` $\to$ `sc_products.product_name`
  - `rm_items.quantity` $\to$ `sc_products.target_quantity`
* **Idempotent Upsert Key:** `sc_headers.sc_number` and `sc_products.external_ref`.
* **Revision-by-Diff Rule (BR-064):**
  - RM allows Administrators to edit line quantities or delete lines after contract submission.
  - On every poll, the connector performs an in-memory diff against existing `sc_products`.
  - If a quantity changes or a line is removed, the connector **never overwrites active pieces**. Instead, it inserts an audit diff into `sc_revisions` and raises an alert for the PM.

#### Flow 2: Raw Material Issuance Verification (INT-002)
* **Source Endpoints:** `GET /api/material-issues`, `/api/material-issues/:id`, `/api/production/receipts`.
* **Audited RM Source Entities & Fields (`09_Integration_Specification.md:26`):**
  - `material_issue_items`: `rm_item_id`, `quantity_issued`.
* **Derivation & Readiness Rule:**
  - `quantity_issued` is summed per `rm_item_id`.
  - If $\sum \text{quantity\_issued} > 0$ *(assumed: any issue = yes, OPEN O-10)* $\to$ sets `sc_products.is_material_ready = true`.
  - Emits internal `MaterialIssuedEvent`, broadcasting real-time SSE notifications that unlock the "Start Operation" button on operator stations.

#### Flow 3: Vendor Delivery Challan (DC) & Return Tracking (INT-009)
* **Source Endpoints:** `GET /api/delivery-challans`, `/api/delivery-challans/:id`, `/api/delivery-challans/movements`.
* **Filter:** Polls strictly records where `type = 'PRODUCTION_PROCESS_OUTWARD'`. Challan number format matches `SDC/YYYY/####`.
* **Audited RM Source Entities & Fields (`09_Integration_Specification.md:27-29`):**
  - `delivery_challans`: `id`, `challan_number`, `type`, `status`, `vendor_id`, `process_name`, `sc_id`.
  - `delivery_challan_items`: `id`, `part_name`, `quantity_dispatched`, `actual_received_qty`.
* **Critical DC Line Matching Rule:**
  - **No Foreign Key:** `delivery_challan_items` has **no `rm_item_id` foreign key**.
  - **Overwritten Part Number:** In RM, `part_number` is overwritten with the SC code, so matching on `part_number` is **strictly prohibited**.
  - **Matching Rule:** The connector must match lines strictly by:
    $$\text{SC Number} + \text{part\_name}$$
* **Manual "Map This Line" Fallback:**
  - If an inward DC item cannot be matched by SC + `part_name` (e.g. spelling discrepancy in RM), the item is flagged in `unmatched_vendor_dc_items`.
  - The record appears on a dedicated PM reconciliation screen for one-click manual mapping to the correct product line item.
* **Return Status Derivation Rule:**
  - The RM DC `status` is **not used** because RM keeps DCs in `PARTIALLY_RETURNED` until RM QC performs final ledger closure.
  - Return status is derived purely from physical quantities:
    - `actual_received_qty == 0` $\to$ **`NOT_RECEIVED`**
    - `0 < actual_received_qty < quantity_dispatched` $\to$ **`PARTIALLY_RECEIVED`**
    - `actual_received_qty >= quantity_dispatched` $\to$ **`RECEIVED`**

#### Flow 4: ECR Replacement Material Issuance (INT-010)
* **Source Endpoint:** `GET /api/ecr-issues`.
* **Audited RM Source Entities & Fields (`09_Integration_Specification.md:30`):**
  - `ecr_material_pending`: `ecr_id`, `report_number`, `type`, `sc_number`, `component`, `quantity_affected`, `status`, `issued_at`.
* **Replacement Release Rule (D-09, BR-061):**
  - When an ECR item reaches `status == 'ISSUED'`, the connector matches `report_number` against `pieces.ecr_report_number` of replacement pieces (`-R1`).
  - Sets `pieces.is_material_ready = true`, automatically unlocking the replacement piece for machining without manual PM intervention.

---

### 2. Proposed Design

#### 2.1 Sync Log Pattern (`rm_sync_history`)
Every polling run records its operational telemetry in `rm_sync_history`:
* `id` (`UUID PK`)
* `sync_type` (`SC_IMPORT`, `MATERIAL_STATUS`, `VENDOR_DC`, `ECR_MATERIAL`)
* `started_at`, `finished_at`, `duration_ms`
* `records_read`, `records_upserted`, `records_unmatched`
* `status` (`SUCCESS`, `PARTIAL_SUCCESS`, `FAILED`)
* `error_message`, `http_status_code`

#### 2.2 Error, Retry, and Rate Limiting Policies
1. **Network Timeouts:** Connect timeout = 3s; Read timeout = 5s.
2. **Transient Retries:** On HTTP 5xx or connection timeout, retries up to **2 times** with exponential backoff (1s, 2s).
3. **Repeated Failure Alerting:** If 3 consecutive polling executions fail, an alert is pushed to the Admin dashboard and an incident is logged in Sentry.
4. **Rate Limiting:** Inbound requests to RM are paced with a minimum delay of **100ms between calls**, capping load to $\le 10$ requests per second.

---

### 3. Technical Assumptions
1. **ASSUMPTION:** The RM database uses UTC for all timestamps, or returns ISO-8601 strings with explicit time zone offsets.
2. **ASSUMPTION:** RM API responses use JSON property names that will be verified during live integration testing in Phase 13.

---

### 4. Open Questions
* **O-01 (PO Reference Date as Delivery Date — OPEN):** Should `purchase_orders.reference_date` be mapped as the contract delivery deadline?
* **O-10 (Material Issued Threshold — OPEN):** Does any partial raw material issuance ($\sum > 0$) satisfy the readiness gate, or is 100% issuance required?
