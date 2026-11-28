# Part 3.7 — Synthesis Report, Integration Governance, and Gap Register
## Document 06: Part 3.7 Synthesis Report, Inconsistency Audit, and Open Questions

---

### 1. Executive Summary & Deliverables Completed

Part 3.7 establishes the definitive architectural conventions, boundaries, connector specifications, outbox protocols, contract testing standards, and reconciliation requirements for integrating Velan Production with external enterprise systems (**RM – V-SMART** and **ECR**).

The following six specification documents have been authored under `docs/phase-3/3.7-integration-conventions/`:
1. **[01-integration-architecture-and-boundaries.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.7-integration-conventions/01-integration-architecture-and-boundaries.md):** Dedicated adapter packages (`com.velan.production.integration.rm` and `integration.ecr`); Anti-Corruption Layer (ACL) preventing external schemas from leaking into domain models; strict boundary rules (Production never writes to RM, never creates DCs, never auto-resends ECR tickets); zero in-transaction HTTP calls; feature flags `RM_SYNC_ENABLED` and `ECR_SEND_ENABLED` (default off); failure isolation architecture.
2. **[02-rm-read-connector-standard.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.7-integration-conventions/02-rm-read-connector-standard.md):** Service account JWT lifecycle; 180s background polling schedule with 60s throttled "Refresh Now"; detailed specifications for the 4 core read flows:
   - *Flow 1 (SC Ingestion):* `sales_order_components`, `purchase_orders`, `rm_items` with revision-by-diff rule (BR-064).
   - *Flow 2 (Material Issuance):* `material_issue_items` quantity summing to toggle `is_material_ready` (D-04).
   - *Flow 3 (Vendor DC Tracking):* Read strictly `type = 'PRODUCTION_PROCESS_OUTWARD'`; critical matching rule by $\text{SC} + \text{part\_name}$ (since `rm_item_id` is missing and `part_number` is overwritten with the SC code); manual "map this line" fallback; return status derived purely from quantities (`actual_received_qty` vs `quantity_dispatched`).
   - *Flow 4 (ECR Replacement Material):* `ecr_material_pending` matching `report_number` to unlock replacement pieces.
   - Sync log pattern in `rm_sync_history`.
3. **[03-ecr-outbox-standard.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.7-integration-conventions/03-ecr-outbox-standard.md):** Transactional Outbox pattern (`outbox_events`) committed in the exact same ACID transaction as the PM disposition; background sweeper using `FOR UPDATE SKIP LOCKED`; real confirmed intake contract (`POST /api/internal/rm-intake`, headers `x-internal-secret` and `x-idempotency-key`); type mapping (`REJECTION`, `REWORK`, `[ADDITIONAL PROCESS]`); report number capture (`AGIPL-YYYY-ERR-XXXXX`); strict no-auto-resend rule on ambiguous timeouts (BR-062); dead-letter manual re-drive; exhaustive 6-point reconciliation matrix rectifying Phase 2 Part 2.5 Document 04 discrepancies.
4. **[04-contract-testing-and-sample-capture.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.7-integration-conventions/04-contract-testing-and-sample-capture.md):** Safe live sample capture protocol with mandatory data sanitization (scrubbing secrets, commercial pricing, and PII); storage in `src/test/resources/integration-samples/{rm,ecr}/`; WireMock automated contract testing; master **VERIFY Register** classifying every external endpoint, header, and field as `CONFIRMED` (audited from `09_Integration_Specification.md`) or `VERIFY` with the assigned implementation task.
5. **[05-reconciliation-monitoring-and-phase-2-7-gap-list.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.7-integration-conventions/05-reconciliation-monitoring-and-phase-2-7-gap-list.md):** Cross-system reconciliation console (Phase 42); custom Spring Boot Actuator Prometheus metrics and 4 automated alerting threshold rules; catalogue of 7 design gaps that a dedicated **Part 2.7 (Integration Architecture)** must resolve before implementing phases 13, 14, 18, 22, 31, 32, 35, and 42.
6. **[06-part-3.7-report.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.7-integration-conventions/06-part-3.7-report.md):** (This document) Comprehensive synthesis report, conflict register, technical assumptions audit, and open questions.

---

### 2. Comprehensive Conflict Register

In accordance with `SKILLS.md §0`, all known architectural conflicts remain formally OPEN without unilateral engineering decisions:

#### Conflict C-1: Completion Model — D-10 vs. Phase 2 Parts 2.2 and 2.6 (OPEN)
* **Conflict Statement:** Decision `D-10` specifies that a piece is Packed when all operations pass; a product is complete when all its pieces are Packed; the SC becomes Delivered automatically without packing or dispatch screens/statuses/roles. Conversely, Phase 2 Parts 2.2 and 2.6 specify multi-component assembled sets (`assembled_sets`), physical packing checklists (`packing_records`), staging bays (`DISPATCH_PENDING`), and loading dock gate releases (`dispatch_confirmations`).
* **Integration Standards Status:** **OPEN.** No external DC or packing messages are transmitted to RM or ECR regardless of the resolution of C-1.

#### Conflict C-2: Piece Count Derivation Source (OPEN)
* **Conflict Statement:** Decision `D-02` specifies that piece count per product is derived directly from the quantity RM sends for that product line. Phase 2 Part 2.2 calculates piece demand as target assembled sets $\times$ component BOM ratio.
* **Integration Standards Status:** **OPEN.** Flow 1 ingests `rm_items.quantity` as the authoritative source quantity, allowing downstream production to map directly (D-02) or compute BOM ratios.

#### Conflict C-3: The "SC" Terminology Problem (OPEN with Recommendation)
* **Conflict Statement:** "SC" carries four different meanings across systems: RM screen "Size Card (SC Code)", Phase 1/2 prose "Size Card", Phase 2 tables `subcontracts`, and ECR field "Size Card number".
* **Recommendation:** In RM queries, match by `sc_number` and `part_name`; in ECR outbox payloads, populate field `scNumber`.
* **Status:** **OPEN.** Submitted for project owner direction before Phase 5 API implementation.

---

### 3. Consolidated Technical Assumptions Register

1. **ASSUMPTION:** Spring Boot 3.4 `RestClient` or `WebClient` utilizes isolated connection pools dedicated to external HTTP integration, preventing thread starvation in the core web container.
2. **ASSUMPTION:** RM legacy database stores and emits timestamps in UTC or ISO-8601 strings with explicit time zone offsets.
3. **ASSUMPTION:** Jackson `DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES = false` is enforced on integration mappers to prevent deserialization crashes when upstream systems add new fields.
4. **ASSUMPTION:** ECR intake API responds within 2,000ms under standard operational load.
5. **ASSUMPTION:** WireMock stubs and recorded sanitized JSON sample files are sufficient for executing 100% of contract tests in CI pipelines without requiring live network access to RM or ECR staging environments.

---

### 4. Errors & Inconsistencies Found in Previous Work

The systematic audit revealed significant discrepancies and invented endpoint paths across earlier documentation:

1. **Invented RM Endpoints in Project Context:**
   * *Citation:* `docs/PROJECT_CONTEXT_FOR_CLAUDE.md §10.1`
   * *Issue:* Listed 13 invented endpoint paths such as `/api/v1/purchase_orders`, `/api/v1/subcontracts`, `/api/v1/vendor_dcs`, `/api/v1/material_requests`.
   * *Correction:* Reconciled to the real audited RM endpoints confirmed in `09_Integration_Specification.md:15`: `/api/sc`, `/api/sc/:id`, `/api/rm`, `/api/material-issues`, `/api/delivery-challans`, `/api/ecr-issues`.
2. **Invented ECR Intake Paths & Headers in Phase 2:**
   * *Citation:* `docs/phase-2/2.5-qc-and-exception-state-machines/04-ecr-outbox-integration-state-machine.md:14,35`
   * *Issue:* Modeled ECR intake as `POST /api/v1/ecr/intake` (or `/api/external/ecr/exceptions`) using generic `Idempotency-Key` and `Authorization: Bearer`.
   * *Correction:* Reconciled to the real confirmed ECR intake contract in `09_Integration_Specification.md:32`: `POST /api/internal/rm-intake` using headers `x-internal-secret` and `x-idempotency-key`.
3. **Prohibited Dual Outbox Destination to RM in Phase 2:**
   * *Citation:* `docs/phase-2/2.5-qc-and-exception-state-machines/04-ecr-outbox-integration-state-machine.md:32-35`
   * *Issue:* Proposed inserting dual outbox records (`destination_system = 'RM'` and `'ECR'`) and sending exceptions to RM via `POST /api/v1/rm/exceptions`.
   * *Correction:* Decision `D-09` explicitly removed sending exceptions to RM (`INT-003/004/005` deleted; `BR-067`: "Production never sends anything to RM"). Outbox targets ECR exclusively.
4. **Automated Retry Policy Violating BR-062:**
   * *Citation:* `docs/phase-2/2.5-qc-and-exception-state-machines/04-ecr-outbox-integration-state-machine.md:41-43`
   * *Issue:* Proposed 5 automatic exponential retries for ECR events on network timeouts.
   * *Correction:* ECR maintains only a 10-minute in-memory duplicate cache without persistent deduplication. Under `BR-062`, auto-retrying after an ambiguous timeout is strictly forbidden to prevent duplicate financial scrap costing ledgers. Reconciled to `AMBIGUOUS_TIMEOUT` state for Admin review.
5. **Invented ECR Report Number Formats:**
   * *Citation:* `docs/phase-2/2.5-qc-and-exception-state-machines/04-ecr-outbox-integration-state-machine.md:45`
   * *Issue:* Modeled report numbers as `ECR-YYYY-####` or generic integers.
   * *Correction:* Reconciled to the audited format `AGIPL-YYYY-ERR-XXXXX`.

---

### 5. Open Questions Submitted for the Project Owner

* **O-01 (PO Reference Date as Contract Delivery Date — OPEN):** Should `purchase_orders.reference_date` in RM be mapped as the contract delivery deadline?
* **O-04 (ECR Additional Process Category — OPEN):** Confirmation from ECR technical leadership on whether `ADDITIONAL_PROCESS` will be supported natively as an enumeration, or permanently sent as `REWORK` with the prefix `[ADDITIONAL PROCESS]` in `defectDescription`.
* **O-10 (Material Issued Threshold — OPEN):** Does any partial raw material issuance ($\sum \text{qty} > 0$) satisfy the readiness gate, or is 100% issuance required?
* **O-INT-01 (WireMock Stub Packaging):** Should recorded WireMock JSON stubs be packaged in a shared test-jar library for cross-module integration tests?
* **Missing Phase 2.7 Recommendation:** Does the project owner approve commissioning a formal **Phase 2 Part 2.7 (Integration Architecture)** design before starting implementation phases 13, 14, 18, 22, 31, 32, 35, and 42?
