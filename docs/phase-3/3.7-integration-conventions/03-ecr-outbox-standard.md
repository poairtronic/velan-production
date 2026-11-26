# Part 3.7 — Integration Adapter Conventions
## Document 03: ECR Outbox Pattern, Intake Contract, and Reconciliation with Phase 2

---

### 1. Confirmed Rules

#### 1.1 The Transactional Outbox Pattern Architecture (ADR-02)
1. **Local Atomic Commit:** When a Project Manager dispositions an exception (`REJECT_AND_RESTART`, `RETURN_TO_EARLIER_OP`, or `EXTRA_PROCESS`), the domain mutation (piece status change, replacement piece creation) and the insertion of a single event row into `outbox_events` are committed **within the exact same PostgreSQL ACID transaction**.
2. **Zero In-Transaction HTTP Calls:** No network call to ECR is executed during the user's web request.
3. **Outbox Table States:**
   * `PENDING`: Committed locally; waiting for background sweeper pickup.
   * `SENDING`: Claimed by sweeper under `FOR UPDATE SKIP LOCKED`.
   * `SENT`: Successfully acknowledged by ECR with HTTP 201; report number captured.
   * `AMBIGUOUS_TIMEOUT`: HTTP socket timed out mid-flight; auto-retry blocked by BR-062.
   * `DEAD_LETTER`: Fatal 4xx error or manually flagged unrecoverable record.

#### 1.2 The ECR Intake Request Contract
* **Real Confirmed Endpoint (`09_Integration_Specification.md:32`):** `POST {ECR_API_URL}/api/internal/rm-intake`
* **Mandatory HTTP Headers:**
  - `x-internal-secret: <SECRET>` (Pre-shared key injected via Render environment variable `ECR_INTERNAL_SECRET`).
  - `x-idempotency-key: <UUID>` (The `outbox_events.id` UUID).
  - `Content-Type: application/json`
* **Real Confirmed JSON Request Body:**
  ```json
  {
    "type": "REJECTION",
    "scNumber": "SC-1024",
    "poNumber": "PO-88219",
    "component": "VALVE_BODY",
    "quantityAffected": 1,
    "defectDescription": "Ref PRD-EXC-00102: Unrecoverable bore oversize (+0.18mm) on Operation 20",
    "raisedByName": "John Doe (PM)"
  }
  ```

#### 1.3 Production Exception to ECR Type Mapping
1. **`REJECT_AND_RESTART` (Scrap):** Mapped to ECR `type = "REJECTION"`.
2. **`RETURN_TO_EARLIER_OP` (Rework):** Mapped to ECR `type = "REWORK"`.
3. **`EXTRA_PROCESS` (Ad-Hoc Remedial Step):** Mapped to ECR `type = "REWORK"`, with the text prefix `"[ADDITIONAL PROCESS] "` prepended to `defectDescription` *(OPEN: O-04)*.
4. **Traceability Reference:** The text `"Ref {exceptionId}: "` is always prepended to `defectDescription` to allow manual cross-system correlation.

#### 1.4 Response Processing & Report Number Persistence
* ECR responds with HTTP 201:
  ```json
  {
    "reportId": "65b8c2e1f4a9b2001c3e4a5d",
    "reportNumber": "AGIPL-2026-ERR-00142"
  }
  ```
* The worker extracts `reportNumber`, updates `outbox_events.status = 'SENT'`, sets `outbox_events.sent_at = NOW()`, and copies the report number into `qc_exceptions.ecr_report_number` and child replacement piece records (`pieces.ecr_report_number`).
* ECR automatically places the created ticket in status `PENDING_ACCOUNTS_REVIEW`.

#### 1.5 Ambiguous Timeout Handling (Strict No-Auto-Resend Rule — BR-062)
* **The Constraint:** ECR maintains only an **ephemeral in-memory idempotency cache (~10 minutes)**. It possesses no persistent deduplication.
* **The Hazard:** If an HTTP call times out after the 5-second socket read timeout, ECR may have successfully created the accounting ticket. Resending the request after the 10-minute cache expires creates duplicate scrap costing ledgers.
* **The Rule (BR-062):** The sweeper **must never automatically resend** an outbox event that encounters an ambiguous timeout or network drop.
* **Protocol:** The event transitions immediately to **`AMBIGUOUS_TIMEOUT`**, and an urgent alert is raised on the Admin dashboard.

#### 1.6 Dead-Letter Queue & Admin Manual Re-Drive
1. Records in `AMBIGUOUS_TIMEOUT` or `DEAD_LETTER` are inspected via the Admin Console.
2. The Administrator can:
   * **Mark Resolved:** Verify in ECR that the ticket exists, manually enter the `AGIPL-YYYY-ERR-XXXXX` report number, and transition outbox status to `SENT`.
   * **Force Re-Drive:** Confirm the ticket does not exist in ECR and trigger a single manual HTTP resend.
3. Every manual intervention is strictly logged in `audit_logs` with the Admin user ID and reason.

---

### 2. Reconciliation with Phase 2 Part 2.5 Document 04

The systematic audit reveals six concrete discrepancies between Phase 2 Part 2.5 Document 04 and the approved Phase 1 specification:

| # | Architecture Dimension | Phase 2 Part 2.5 Document 04 Specification | Approved Part 3.7 Standard (09_Integration_Specification.md) | Resolution & Alignment Action |
|:---:|:---|:---|:---|:---|
| 1 | **Outbox Destination Targets** | Proposed dual destinations (`destination_system = 'RM'` and `'ECR'`), sending exceptions to RM via `POST /api/v1/rm/exceptions`. | **RM destination is strictly removed (D-09, BR-067).** Production never sends exceptions to RM. Only ECR is an outbox destination. | RM outbox destination deleted. `outbox_events` targets ECR exclusively. |
| 2 | **ECR Endpoint URI Path** | Specified invented URI: `POST /api/v1/ecr/intake` (or `/api/external/ecr/exceptions`). | Confirmed audited endpoint: `POST /api/internal/rm-intake`. | Standardized to real audited path `/api/internal/rm-intake`. |
| 3 | **Authentication & Idempotency Headers** | Specified generic `Idempotency-Key` and `Authorization: Bearer`. | Confirmed audited headers: `x-internal-secret` and `x-idempotency-key`. | Standardized to exact audited headers. |
| 4 | **Retry & Backoff Policy** | Proposed automated 5-stage exponential retry (1m, 5m, 15m, 30m, 1h). | **Auto-retry after ambiguous timeout is strictly forbidden (BR-062).** | Replaced with `AMBIGUOUS_TIMEOUT` state and Admin manual verification. |
| 5 | **Report Number Format** | Illustrated generic ticket numbers (`ECR-YYYY-####` or integer IDs). | Confirmed audited format: `AGIPL-YYYY-ERR-XXXXX`. | Standardized to `AGIPL-YYYY-ERR-XXXXX` regex validation. |
| 6 | **Secondary Warehouse Alerts** | Mentioned warehouse dispatch alerts via outbox. | No approved business requirement for secondary warehouse outbox targets. | Excluded from outbox scope. |

---

### 3. Proposed Design

#### 3.1 Background Sweeper Implementation Standard
A Spring `@Scheduled(fixedDelayString = "${app.integration.ecr.sweeper-interval-seconds:10}000")` worker executes the outbox loop:
```sql
SELECT * FROM outbox_events 
WHERE status = 'PENDING'
ORDER BY created_at ASC 
LIMIT 20 
FOR UPDATE SKIP LOCKED;
```
* Each row is updated to `SENDING`, dispatched via `RestClient` outside the transaction, and updated to `SENT` or `AMBIGUOUS_TIMEOUT` in a distinct completion transaction.

---

### 4. Technical Assumptions
1. **ASSUMPTION:** ECR `POST /api/internal/rm-intake` responds within 2,000ms under normal operating conditions.
2. **ASSUMPTION:** The shared secret `ECR_INTERNAL_SECRET` is rotated annually via coordinated configuration deployment.

---

### 5. Open Questions
* **O-04 (ECR Additional Process Category — OPEN):** Confirmation from ECR technical team on whether `ADDITIONAL_PROCESS` will be supported natively or continues with `[ADDITIONAL PROCESS]` prefix in `defectDescription`.
