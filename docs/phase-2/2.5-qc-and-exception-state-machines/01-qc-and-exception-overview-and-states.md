# Part 2.5 – QC and Exception State Machines
## Document 01: QC and Exception Overview, Principles, and State Catalogues

---

### 1. Executive Summary & Objective

In the Velan Production platform, Quality Control (QC) and Exception Management represent the central integrity boundary of manufacturing operations. Precision valve components manufactured for nuclear, oil & gas, and industrial cryogenic applications require uncompromising dimensional compliance, complete traceability of deviations, independent inspection verification, and formal engineering disposition governance.

The objective of **Part 2.5 – QC and Exception State Machines** is to define the interconnected lifecycle models governing quality and non-conformance:
1. **Tolerance Revision Lifecycle:** Governs the intake, extraction, Design verification, and PM approval of SC-specific dimensional inspection rules.
2. **QC Inspection State Machine:** Governs the independent inspection sessions conducted by certified QC inspectors on shop-floor pieces.
3. **Quality Exception State Machine:** Governs the quarantine, escalation, and PM disposition workflows for out-of-tolerance non-conformances.
4. **ECR Outbox Event State Machine:** Governs the reliable, asynchronous, transactional transmission of scrap and rework notices to the external Engineering Change Request (ECR) system without external message brokers.

---

### 2. Architectural Separation & Core Invariants

Part 2.5 coordinates with the preceding architectural parts while preserving strict boundaries:

```
+-----------------------------------------------------------------------------------------+
| PART 2.2: DOMAIN & DATA MODEL (PostgreSQL on Neon)                                      |
| Entities: tolerance_revisions, tolerance_rules, operation_measurements,                 |
|           qc_inspections, qc_exceptions, pm_dispositions, outbox_events                 |
+-----------------------------------------------------------------------------------------+
       │
       ▼
+-----------------------------------------------------------------------------------------+
| PART 2.3: PIECE LIFECYCLE (pieces: ON_HOLD_QC_FAILED, REWORK, SCRAPPED)                 |
+-----------------------------------------------------------------------------------------+
       │
       ▼
+-----------------------------------------------------------------------------------------+
| PART 2.4: OPERATION LIFECYCLE (piece_operations: QC_PENDING, QC_IN_PROGRESS, FAILED)   |
+-----------------------------------------------------------------------------------------+
       │
       ▼
+-----------------------------------------------------------------------------------------+
| PART 2.5: QC & EXCEPTION STATE MACHINES (Focus of this subpart)                         |
|   1. Tolerance Revision Lifecycle (tolerance_revisions)                                 |
|   2. QC Inspection Lifecycle (qc_inspections)                                           |
|   3. QC Exception Lifecycle (qc_exceptions)                                             |
|   4. ECR Outbox Event Lifecycle (outbox_events)                                         |
+-----------------------------------------------------------------------------------------+
```

#### Core Architectural Principles & Invariants

#### Principle 1: Independent QC Verification and Zero Pre-Filling (`BR-032`)
* Quality inspection is conducted as a separate audit event by a certified user holding `ROLE_QC`.
* Operator measurements and QC measurements are stored in separate rows in `operation_measurements` with distinct foreign keys and roles (`OPERATOR` vs `QC_INSPECTOR`).
* **Zero Pre-Filling Invariant:** QC measurement entry fields are **never pre-populated** with operator-submitted values. QC inspectors must perform physical measurements using calibrated instruments and enter actual values into completely blank fields.

#### Principle 2: Non-Blocking Operator Warnings vs. Blocking QC Gates
* During operator data entry, out-of-tolerance readings trigger non-blocking visual warnings (`SRS FR-010`). This encourages honest measurement entry rather than falsifying data to bypass software blocks.
* In contrast, the QC inspection gate is **strictly blocking**: any out-of-tolerance QC reading immediately issues a `FAILED` verdict, transitions the operation to `FAILED`, and quarantines the piece into `ON_HOLD_QC_FAILED`.

#### Principle 3: Permanent Immutability of Measurements and Failure Verdicts
* Submitted operator measurements, QC measurements, and inspection failure verdicts (`verdict = 'FAILED'`) are **permanent and immutable**.
* Under no circumstances can a QC Inspector or Operator overwrite, alter, or delete a measurement record or a failed inspection verdict (`BR-029, BR-034`).
* If the PM authorizes a concession (`CONTINUE`), the failure is **never silently converted into a pass**. The failed QC record remains `FAILED` in audit history, and the operation status is explicitly marked `CONCESSION_ACCEPTED`.

#### Principle 4: Monotonically Incrementing Rework Iterations
* When a piece undergoes rework via PM disposition (`RETURN_TO_EARLIER_OPERATION`), previous measurement sessions are preserved untouched.
* The operation re-enters execution with an incremented counter: `iteration_number = 2, 3...`. All rework measurements and QC re-inspections are tagged with this iteration counter, preserving an unbroken historical audit trail (`BR-035`).

#### Principle 5: Exclusive PM Authority over Dispositions (`BR-033, BR-034`)
* Operators and QC Inspectors have zero authority to override a quality failure.
* Only a user with `ROLE_PM` (Production Manager) can evaluate deviations and issue binding dispositions: `CONTINUE`, `RETURN_TO_EARLIER_OPERATION`, `EXTRA_PROCESS`, or `REJECT_AND_RESTART`.
* Every PM disposition requires mandatory, non-empty engineering justification notes recorded in `audit_logs`.

#### Principle 6: Clean ECR Integration Decoupling via Transactional Outbox
* Non-conformance costing, vendor chargebacks, and commercial defect responsibility belong strictly to the external **Engineering Change Request (ECR)** system (`BR-039, BR-056`).
* Velan Production automatically submits exception payloads to ECR via an asynchronous local **Transactional Outbox** table (`outbox_events`), adhering to **ADR-02 (No external message brokers / No RabbitMQ / No Kafka)**.
* ECR financial and costing calculations **never return to Velan Production**. Production remains focused entirely on shop-floor piece execution and set completeness.

---

### 3. Exhaustive State Catalogues

Part 2.5 defines four formal state machines:

#### 3.1 Tolerance Revision State Machine (`tolerance_revisions.status`)
Governs the lifecycle of an uploaded dimensional tolerance sheet for an SC:

```
[ UPLOADED ] ──► [ EXTRACTED ] ──► [ DESIGN_VERIFIED ] ──► [ PM_APPROVED ] (Active)
                                          │                      │
                                          ▼                      ▼
                                     [ REJECTED ]          [ SUPERSEDED ]
```

| State Code | State Name | Meaning & Operational Context | Entry Conditions | Exit Conditions |
| :--- | :--- | :--- | :--- | :--- |
| `UPLOADED` | **Sheet Uploaded** | Designer uploaded an Excel/PDF drawing tolerance file to Cloudflare R2. File metadata saved. | Designer uploads file via UI (`ROLE_DESIGNER`). | Background extraction worker parses sheet parameters. |
| `EXTRACTED` | **Values Extracted** | System background parser mapped nominal, lower, and upper limits for each routing operation. | Extraction engine completes parsing and populates draft rules. | Designer reviews extracted values on verification grid. |
| `DESIGN_VERIFIED` | **Design Verified** | Designer verified, corrected, and certified the extracted dimensions against engineering blueprints. | Designer clicks "Verify & Submit for PM Approval". | PM reviews and approves or rejects revision. |
| `PM_APPROVED` | **Active / Approved** | PM approved the tolerance sheet. Rules become the active evaluation baseline for unentered data. | PM clicks "Approve Tolerance Revision" (`ROLE_PM`). | Superseded by a subsequent approved revision; OR SC completed. |
| `REJECTED` | **Rejected by PM** | PM rejected the tolerance sheet due to engineering errors or missing revisions. Designer notified. | PM clicks "Reject Revision" with mandatory reason. | *Terminal for this revision.* Designer must upload new sheet. |
| `SUPERSEDED` | **Superseded** | A subsequent tolerance revision was approved for this SC. Retained immutable for historical audits. | A new revision for the same SC product reaches `PM_APPROVED`. | *Terminal.* Retained immutable for completed operations. |

---

#### 3.2 QC Inspection State Machine (`qc_inspections.verdict` & Workflow)
Governs an individual physical inspection session performed on an operation:

```
[ SCHEDULED ] ──► [ CLAIMED_IN_PROGRESS ] ──► [ PASSED ]
                                        └──► [ FAILED ]
```

| State / Verdict | Operational Meaning | Entry Conditions | Exit Conditions |
| :--- | :--- | :--- | :--- |
| `SCHEDULED` | Operation is staged in inspection bay awaiting a QC Inspector (`piece_operations.status = 'QC_PENDING'`). | Machine operator submitted measurements. | A certified QC inspector claims the job card. |
| `CLAIMED_IN_PROGRESS`| QC Inspector opened the inspection card on terminal (`piece_operations.status = 'QC_IN_PROGRESS'`). | QC Inspector clicks "Inspect Piece". Timestamp `qc_started_at` stamped. | QC Inspector enters all readings and submits final verdict. |
| `PASSED` | Certified 100% compliant with approved tolerance rules. Operation complete (`completed_at`). | 100% of QC measurements fall within nominal upper/lower limits. | *Terminal.* Unlocks next sequential operation to `READY`. |
| `FAILED` | Non-conforming dimension detected. Operation failed; piece quarantined; exception ticket opened. | At least one QC measurement exceeds upper or lower tolerance limits. | *Terminal.* Piece placed on hold; escalates to PM. |
| `CANCELLED` | Inspection session cancelled because parent piece was scrapped or SC line cancelled. | Parent piece scrapped via PM `REJECT_AND_RESTART`. | *Terminal.* Historical audit preserved. |

---

#### 3.3 Quality Exception State Machine (`qc_exceptions.status`)
Governs the non-conformance case opened upon QC failure:

```
[ PENDING_PM_DISPOSITION ] ──► [ RESOLVED_CONCESSION ] ──► [ CLOSED ]
                           ├──► [ RESOLVED_REWORK ]     ──► [ CLOSED ]
                           ├──► [ RESOLVED_EXTRA_PROCESS ]──► [ CLOSED ]
                           └──► [ RESOLVED_SCRAP ]      ──► [ CLOSED ]
```

| State Code | State Name | Meaning & Operational Context | Entry Conditions | Exit Conditions |
| :--- | :--- | :--- | :--- | :--- |
| `PENDING_PM_DISPOSITION`| **Pending PM Decision** | Piece is quarantined in `ON_HOLD_QC_FAILED`. Real-time alert active on PM dashboard. | QC Inspection submitted with verdict `FAILED`. | PM issues one of the four binding dispositions. |
| `RESOLVED_CONCESSION` | **Resolved via Concession** | PM authorized `CONTINUE` with mandatory justification. Op marked `CONCESSION_ACCEPTED`. | PM clicks "Approve Concession (`CONTINUE`)". | Downstream operation activates; exception archives to `CLOSED`. |
| `RESOLVED_REWORK` | **Resolved via Rework** | PM authorized `RETURN_TO_EARLIER_OPERATION`. Routing rewound; iteration incremented. | PM clicks "Rewind for Rework". | Rework operation executed and certified; archives to `CLOSED`. |
| `RESOLVED_EXTRA_PROCESS`| **Resolved via Extra Step**| PM authorized `EXTRA_PROCESS`. Ad-hoc remedial operation dynamically injected. | PM clicks "Insert Extra Process". | Extra operation passes QC inspection; archives to `CLOSED`. |
| `RESOLVED_SCRAP` | **Resolved via Scrap** | PM authorized `REJECT_AND_RESTART`. Piece retired `SCRAPPED`; replacement spawned. | PM clicks "Reject & Restart (Scrap)". | Outbox event enqueued for ECR; archives to `CLOSED`. |
| `CLOSED` | **Archived Closed** | Exception ticket is formally closed. Complete failure and remediation record preserved immutable. | Downstream remediation certified or scrap replaced. | *Terminal.* Permanent audit record. |

---

#### 3.4 Exception Integration Outbox State Machine (`outbox_events.status`)
Governs reliable, asynchronous event delivery to external systems (**RM** and **ECR**):

```
[ PENDING ] ──► [ IN_FLIGHT ] ──► [ PUBLISHED ] (Terminal Success)
                      │
                      ├──► (Transient Failure: retry_count < 5) ──► [ RETRY_SCHEDULED ]
                      │                                                   │ (Backoff Timer Expires: next_retry_at <= NOW())
                      │                                                   ▼
                      │                                             [ PENDING ] (Re-queued for Sweeper)
                      │
                      └──► (Max Retries Exceeded: retry_count >= 5 OR Fatal 4xx) ──► [ DEAD_LETTER ] (Terminal Quarantine)
                                                                                          │ (Admin Re-drive: Preserves Audit Log)
                                                                                          ▼
                                                                                    [ PENDING ]
```

* **Multi-Destination Architecture:** The `outbox_events` table explicitly tracks the target system via `destination_system` (`'RM'` or `'ECR'`). When a PM disposition requires notification (e.g., Scrap, Rework, Extra Process), separate destination-specific outbox records are created atomically in the same database transaction.
* **Separation of Responsibilities:**
  * **RM (Raw Material - V-SMART):** Receives relevant production exception notices (scrap/material rejection, rework, and additional process notifications) and continues to own all material and inventory transactions.
  * **ECR (Engineering Change Request):** Receives relevant defect notices and handles engineering root-cause analysis and defect costing separately.
  * **Velan Production:** Records the exception, spawns physical replacement pieces, and tracks dispatch status. Production **never** performs RM inventory movements or ECR costing.

| State Code | Meaning & Operational Context | Entry Conditions | Exit Conditions |
| :--- | :--- | :--- | :--- |
| `PENDING` | Event is committed in Postgres and ready for sweeper pickup (`next_retry_at <= NOW()`). | Inserted during PM disposition transaction; OR backoff delay elapsed from `RETRY_SCHEDULED`; OR re-driven from `DEAD_LETTER`. | Background sweeper thread claims the record via `SELECT ... FOR UPDATE SKIP LOCKED`. |
| `IN_FLIGHT` | Worker thread has claimed the event and is actively executing the HTTPS POST request to the destination API (RM or ECR) with an idempotency key. | Sweeper worker updates record: `status = 'IN_FLIGHT'`, `attempt_started_at = NOW()`. | Destination endpoint responds with HTTP success (2xx) OR delivery attempt fails. |
| `PUBLISHED` | Successfully delivered to destination endpoint and acknowledged. Response reference or ticket ID stored. | Destination responds with HTTP 200/201. `published_at = NOW()`. | *Terminal.* Retained as an immutable integration audit record. |
| `RETRY_SCHEDULED` | Delivery attempt failed due to transient network error, HTTP 503, or gateway timeout (`retry_count < 5`). Event is scheduled for another retry with exponential backoff + jitter. | HTTP transmission fails or returns 5xx error while `retry_count < 5`. Worker increments `retry_count` and calculates `next_retry_at`. | Backoff timer elapses (`next_retry_at <= NOW()`), transitioning event back to `PENDING` for another attempt. |
| `DEAD_LETTER` | Event has failed 5 consecutive attempts (or received a fatal 4xx non-retryable client error). Quarantined to prevent head-of-line blocking; raises High-Priority Admin Alert. | `retry_count >= 5` with repeated non-2xx failures, or unrecoverable client error. | System Admin investigates connectivity/endpoint, logs resolution note, and triggers "Re-Drive DLQ" (transitions to `PENDING` without overwriting historical attempt log). |
