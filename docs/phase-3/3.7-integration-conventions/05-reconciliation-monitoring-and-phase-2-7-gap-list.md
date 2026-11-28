# Part 3.7 — Integration Adapter Conventions
## Document 05: Reconciliation Architecture, Observability, and Phase 2.7 Integration Gap Catalogue

---

### 1. Confirmed Rules

#### 1.1 Cross-System Reconciliation Requirements (Phase 42)
To maintain commercial data integrity between independent enterprise databases without distributed two-phase commits, the platform provides a dedicated Admin/PM **Reconciliation Console**:
1. **Size Card & Quantity Discrepancy Auditing:**
   * Automated diff scans run daily comparing active `sc_headers` and `sc_products` against RM `sales_order_components` and `rm_items`.
   * Flags:
     - SCs approved in RM that failed to intake into Production.
     - Product line quantity differences (e.g. RM updated target from 50 to 45 after scrap concession).
     - Missing drawing numbers or revision mismatches.
2. **Unmatched Vendor Delivery Challan Resolution:**
   * Inward vendor delivery challans from RM whose lines failed the automated $\text{SC} + \text{part\_name}$ matching rule are displayed in the reconciliation queue.
   * PMs can manually map the line to the correct physical piece batch with mandatory audit attribution.
3. **ECR Outbox Discrepancy Queue:**
   * Centralizes all outbox events in `AMBIGUOUS_TIMEOUT` or `DEAD_LETTER` status.
   * Displays the Production exception ID, piece serial, defect description, timestamp of attempt, and last error.
   * Provides single-click manual resolution:
     - *"Link Existing ECR Ticket"*: PM/Admin inputs verified `AGIPL-YYYY-ERR-XXXXX` report number.
     - *"Force Single Re-drive"*: Re-executes single HTTP POST to `/api/internal/rm-intake`.

#### 1.2 Integration Observability & Actuator Metrics
Spring Boot Actuator tracks custom integration metrics scraped via `/actuator/prometheus`:
* `integration.rm.poll.success.total`: Monotonically increasing counter of successful RM polls.
* `integration.rm.poll.failure.total`: Counter of failed RM polling attempts.
* `integration.rm.poll.duration.seconds`: Timer recording HTTP round-trip latency to RM.
* `integration.ecr.outbox.count{status="PENDING"}`: Active queue depth waiting for sweeper.
* `integration.ecr.outbox.count{status="SENT"}`: Successfully dispatched exceptions.
* `integration.ecr.outbox.count{status="AMBIGUOUS_TIMEOUT"}`: Trapped events requiring manual verification.
* `integration.ecr.outbox.count{status="DEAD_LETTER"}`: Fatal errors requiring administrative re-drive.

#### 1.3 Prescriptive Alerting Rules Matrix

| Alert Trigger | Severity | Threshold Condition | Target Audience | Resolution Action |
|:---|:---:|:---|:---|:---|
| **ECR Ambiguous Timeout** | **P1 — High** | `outbox_events.status = 'AMBIGUOUS_TIMEOUT'` count $> 0$ | Admin / PM | Verify ECR intake log; link report number or re-drive. |
| **ECR Dead-Letter Event** | **P1 — High** | `outbox_events.status = 'DEAD_LETTER'` count $> 0$ | Admin / Engineering | Inspect fatal 4xx payload error; correct data and re-drive. |
| **RM Polling Outage** | **P2 — Medium** | Failed RM polls $> 3$ consecutive times ($> 9$ min) | Admin / IT | Verify RM network reachability and service account credentials. |
| **Unmatched Vendor DC Spike** | **P2 — Medium** | Unmatched DC items count $> 5$ | PM | Review and execute manual "map this line" actions. |

---

### 2. Proposed Design: The Missing Phase 2.7 "Integration Architecture" Gap Catalogue

*Context:* Phase 2 system design formally documented Parts 2.1 through 2.6. Part 2.7 ("Integration Architecture") was never authored. The following design questions must be resolved before proceeding with physical implementation:

#### 2.1 Critical Architectural Gaps for Part 2.7
1. **RM Authentication & Session Maintenance:**
   * What is the exact authentication endpoint URL, request body shape, and token renewal protocol for RM (V-SMART)?
   * How should the connector detect token invalidation vs. credential expiration?
2. **Concrete RM API JSON Response Schemas:**
   * What are the exact JSON property names (camelCase vs. snake_case), nesting wrappers, and nullability rules across all 13 read endpoints?
3. **Material Readiness Gate Business Criteria (Open O-10):**
   * Does any partial raw material issuance ($\sum \text{qty} > 0$) satisfy `is_material_ready`, or must 100% of the required raw material quantity be issued?
4. **Post-Machining Contract Line Deletions in RM:**
   * What is the authoritative state machine transition for active physical pieces if RM hard-deletes a product line after pieces have already passed Operation 10?
5. **Vendor DC Discrepancy Escalation Protocol:**
   * If an inward DC line cannot be mapped to an SC or product name, and the PM cannot physically identify the parts, what formal quarantine status applies to the physical pieces?
6. **ECR Additional Process Intake Protocol (Open O-04):**
   * Will ECR update its schema to accept an explicit `ADDITIONAL_PROCESS` enumeration, or must Production permanently send it as `REWORK` with text labels?
7. **Cross-System Incident Management SLA:**
   * What is the formal operational SLA for Plant Administrators to resolve items trapped in `AMBIGUOUS_TIMEOUT` before night-shift accounting reconciliations run in ECR?

#### 2.2 Recommendation for Project Sequencing
* **Strong Recommendation:** Author and approve a dedicated **Phase 2 Part 2.7 (Integration Architecture)** design document before initiating code implementation for:
  - Phase 13 (RM Read Connector)
  - Phase 14 (ECR Outbox Dispatcher)
  - Phase 18 (SC Intake from RM)
  - Phase 22 (Material Gate Verification)
  - Phase 31 (ECR Outbox Sending)
  - Phase 32 (Replacement Piece Release)
  - Phase 35 (Vendor DC Processing from RM)
  - Phase 42 (Cross-System Reconciliation Console)

---

### 3. Technical Assumptions
1. **ASSUMPTION:** Spring Boot Actuator Prometheus metrics provide sufficient granularity for Render and external dashboards without custom telemetry agents.
2. **ASSUMPTION:** Reconciliation diff queries execute during low-traffic periods (e.g. 02:00 UTC) to minimize database contention on Neon.

---

### 4. Open Questions
* **O-04 (ECR Additional Process Category — OPEN):** Awaiting ECR team confirmation.
* **O-10 (Material Issued Threshold — OPEN):** Awaiting manufacturing leadership confirmation on partial vs. complete material issuance.
