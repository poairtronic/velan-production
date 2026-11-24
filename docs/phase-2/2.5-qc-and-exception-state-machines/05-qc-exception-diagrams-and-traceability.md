# Part 2.5 – QC and Exception State Machines: Diagrams & Traceability

## 1. Overview and Purpose

This document provides the visual state transition models, architectural flowcharts, Phase 1 business rule traceability matrices, and the open questions register for **Part 2.5 – QC and Exception State Machines**.

The state models and interaction diagrams formalize the behavior specified across:
* [01-qc-and-exception-overview-and-states.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/01-qc-and-exception-overview-and-states.md)
* [02-qc-inspection-state-machine-and-tolerances.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/02-qc-inspection-state-machine-and-tolerances.md)
* [03-exception-lifecycle-and-pm-dispositions.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/03-exception-lifecycle-and-pm-dispositions.md)
* [04-ecr-outbox-integration-state-machine.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/04-ecr-outbox-integration-state-machine.md)

---

## 2. Complete State Machine & Flow Diagrams

### Diagram 1: Tolerance Revision Lifecycle (`stateDiagram-v2`)

Formalizes the creation, verification, approval, rejection, and superseding of engineering tolerance parameter revisions (`BR-027, BR-028, BR-029`).

```mermaid
stateDiagram-v2
    [*] --> UPLOADED: Drawing / PDF / CSV Uploaded (Designer)
    
    UPLOADED --> EXTRACTED: Auto-Extraction Succeeded
    UPLOADED --> DESIGN_VERIFIED: Manual Entry (Designer Bypasses Auto-Extraction)
    
    EXTRACTED --> DESIGN_VERIFIED: Designer Verifies Extracted Values & Approves (TOL-T02)
    EXTRACTED --> REJECTED: Designer Rejects Extraction Artifacts (TOL-T04)
    
    DESIGN_VERIFIED --> PM_APPROVED: PM Approves Revision (TOL-T03)
    DESIGN_VERIFIED --> REJECTED: PM Rejects Revision (TOL-T04)
    
    PM_APPROVED --> SUPERSEDED: Newer Revision Reaches PM_APPROVED (TOL-T05)
    
    REJECTED --> [*]
    SUPERSEDED --> [*]

    note right of PM_APPROVED
        Active baseline for all unentered measurements.
        Historical measurements retain snapshot_revision_id.
    end note
```

---

### Diagram 2: QC Inspection State Machine (`stateDiagram-v2`)

Governs the independent Quality Control verification lifecycle for each manufacturing operation iteration (`BR-030, BR-031, BR-032, BR-033`).

```mermaid
stateDiagram-v2
    [*] --> SCHEDULED: Operation Reaches QC_PENDING (QC-T01)
    
    SCHEDULED --> CLAIMED_IN_PROGRESS: QC Inspector Claims Inspection (QC-T02)
    SCHEDULED --> CANCELLED: Upstream Scrap or Invalidation (QC-T05)
    
    CLAIMED_IN_PROGRESS --> SCHEDULED: Inspector Releases Claim / Reassigns (QC-T06)
    CLAIMED_IN_PROGRESS --> CANCELLED: Upstream Scrap (QC-T05)
    
    CLAIMED_IN_PROGRESS --> PASSED: All Measurements Within Spec & Submitted (QC-T03)
    CLAIMED_IN_PROGRESS --> FAILED: Any Out-Of-Spec Value / Defect Recorded (QC-T04)
    
    PASSED --> [*]
    FAILED --> [*]
    CANCELLED --> [*]

    note right of CLAIMED_IN_PROGRESS
        Inspector must select valid calibrated instrument.
        Zero pre-filling from operator grid.
    end note

    note right of FAILED
        Automatically triggers Non-Conformance Record
        and locks parent piece into QUARANTINED state.
    end note
```

---

### Diagram 3: QC Exception Management & PM Disposition State Machine (`stateDiagram-v2`)

Models non-conformance lifecycle, quarantine enforcement, and the four Project Manager resolution pathways (`BR-034, BR-035, BR-036, BR-037, BR-038`).

```mermaid
stateDiagram-v2
    [*] --> OPEN_UNDER_REVIEW: QC Inspection FAILED (EX-T01)
    
    OPEN_UNDER_REVIEW --> DISPOSITIONED_CONTINUE: PM Chooses CONTINUE (EX-T02)
    OPEN_UNDER_REVIEW --> DISPOSITIONED_REWORK: PM Chooses RETURN_TO_EARLIER_OP (EX-T03)
    OPEN_UNDER_REVIEW --> DISPOSITIONED_EXTRA_PROCESS: PM Chooses EXTRA_PROCESS (EX-T04)
    OPEN_UNDER_REVIEW --> DISPOSITIONED_SCRAP_REPLACE: PM Chooses REJECT_AND_RESTART (EX-T05)
    
    DISPOSITIONED_CONTINUE --> CLOSED: Concession Logged & Operation Unlocked (EX-T06)
    DISPOSITIONED_REWORK --> CLOSED: Prior Operation Re-entered & Iteration Incremented (EX-T06)
    DISPOSITIONED_EXTRA_PROCESS --> CLOSED: Ad-hoc Operation Injected & Routing Adjusted (EX-T06)
    DISPOSITIONED_SCRAP_REPLACE --> CLOSED: Defective Piece SCRAPPED, Replacement Spawned, ECR Outbox Queued (EX-T06)
    
    CLOSED --> [*]

    note right of OPEN_UNDER_REVIEW
        Parent piece locked in QUARANTINED.
        All shop-floor actions blocked on piece.
    end note

    note right of DISPOSITIONED_CONTINUE
        Requires mandatory engineering justification.
        Operation becomes CONCESSION_ACCEPTED.
    end note

    note right of DISPOSITIONED_SCRAP_REPLACE
        Emits outbox_events record for external ECR system.
        Replacement piece linked via replacement_piece_id.
    end note
```

---

### Diagram 4: Exception Integration Outbox State Machine (RM & ECR) (`stateDiagram-v2`)

Governs the reliable asynchronous dispatch of non-conformance, rework, and scrap rejection records to external enterprise systems (**Raw Material - RM** and **Engineering Change Request - ECR**) (`ADR-02, BR-039, BR-056`).

```mermaid
stateDiagram-v2
    [*] --> PENDING: Outbox Record Inserted in DB Transaction (BOX-T01)
    
    PENDING --> IN_FLIGHT: Sweeper Claims Record (SELECT FOR UPDATE SKIP LOCKED) (BOX-T02)
    
    IN_FLIGHT --> PUBLISHED: HTTP 200/201 Received from Target Endpoint (BOX-T03)
    
    IN_FLIGHT --> RETRY_SCHEDULED: Transient Failure (HTTP 5xx / Timeout) & retry_count < 5 (BOX-T04)
    
    RETRY_SCHEDULED --> PENDING: Backoff Delay Elapsed (next_retry_at <= NOW()) (BOX-T06)
    
    IN_FLIGHT --> DEAD_LETTER: Fatal HTTP 4xx Client Error OR retry_count >= 5 (BOX-T05)
    
    DEAD_LETTER --> PENDING: System Admin Diagnoses & Clicks "Re-Drive DLQ" (BOX-T07)

    PUBLISHED --> [*]

    note right of IN_FLIGHT
        Transmits HTTPS POST with Idempotency-Key.
        Destination: RM or ECR (independent queues).
    end note

    note right of RETRY_SCHEDULED
        Non-terminal transient state.
        Exponential backoff with jitter applied.
    end note

    note right of PUBLISHED
        Terminal success.
        RM owns inventory; ECR owns costing.
        Velan Production never performs costing or inventory tx.
    end note

    note right of DEAD_LETTER
        Terminal quarantine. High-priority admin alert.
        Re-drive appends administrative audit record
        without erasing historical attempt logs.
    end note
```

---

### Diagram 5: End-to-End QC Failure to RM & ECR Integration Flowchart (`flowchart TD`)

Illustrates the end-to-end execution sequence from shop-floor measurement rejection through PM disposition, transactional outbox dispatch, and external delivery to both RM and ECR.

```mermaid
flowchart TD
    subgraph ShopFloor["Shop Floor Execution"]
        OP_DONE["Operator Completes Operation\n(Status: QC_PENDING)"] --> QC_CLAIM["QC Inspector Claims Task\n(Selects Calibrated Instrument)"]
        QC_CLAIM --> QC_INPUT["QC Enters Blind Measurements\n(Zero Pre-fill from Operator)"]
        QC_INPUT --> QC_EVAL{"Any Measurement\nOut of Spec?"}
    end

    subgraph Evaluation["Automated Evaluation & Quarantine"]
        QC_EVAL -- "No (All In Spec)" --> QC_PASS["Inspection PASSED\nOperation: PASSED\nPiece: IN_PROGRESS / READY"]
        QC_EVAL -- "Yes (Deviation Detected)" --> QC_FAIL["Inspection FAILED\nOperation: FAILED\nPiece: QUARANTINED"]
        QC_FAIL --> NCR_CREATE["System Automatically Creates\nqc_exceptions (OPEN_UNDER_REVIEW)"]
        NCR_CREATE --> PM_ALERT["Project Manager Alerted\n(Non-Conformance Dashboard)"]
    end

    subgraph PM_Review["Project Manager Decision"]
        PM_ALERT --> PM_CHOICE{"PM Reviews Deviation\n& Selects Disposition"}
        PM_CHOICE -- "1. Accept Deviation" --> DISP_CONT["CONTINUE (Concession)\n- Engineering Justification Required\n- Operation: CONCESSION_ACCEPTED\n- Next Operation Unlocked"]
        PM_CHOICE -- "2. Machine Again" --> DISP_REW["RETURN_TO_EARLIER_OP\n- Target Op: READY\n- Iteration Incremented (n+1)\n- Earlier Data Frozen"]
        PM_CHOICE -- "3. Unplanned Step" --> DISP_EXT["EXTRA_PROCESS\n- Injects Ad-hoc Operation\n- Dedicated QC Inspection\n- Piece: IN_PROGRESS"]
        PM_CHOICE -- "4. Irrecoverable Scrap" --> DISP_SCRAP["REJECT_AND_RESTART\n- Current Piece: SCRAPPED\n- Spawn Replacement Piece (1:1)\n- Status: AWAITING_MATERIAL"]
    end

    subgraph Outbox_Dual["Transactional Outbox (Neon Postgres ACID Tx)"]
        DISP_SCRAP --> DB_TX["Postgres ACID Transaction Commit\n1. UPDATE pieces (SCRAPPED)\n2. INSERT replacement piece (AWAITING_MATERIAL)\n3. UPDATE qc_exceptions (RESOLVED_SCRAP)\n4. INSERT outbox_events (RM Rejection Notice)\n5. INSERT outbox_events (ECR Scrap Ticket)"]
        
        DB_TX --> OUTBOX_RM["outbox_events (RM Notice)\ndestination_system: 'RM'\nstatus: PENDING"]
        DB_TX --> OUTBOX_ECR["outbox_events (ECR Ticket)\ndestination_system: 'ECR'\nstatus: PENDING"]
    end

    subgraph Integration_Dispatch["Asynchronous Outbox Sweeper & Dispatch"]
        OUTBOX_RM --> SWEEP_RM["Sweeper Worker (SKIP LOCKED)\nHTTPS POST /api/v1/rm/exceptions\nHeader: Idempotency-Key"]
        OUTBOX_ECR --> SWEEP_ECR["Sweeper Worker (SKIP LOCKED)\nHTTPS POST /api/v1/ecr/intake\nHeader: Idempotency-Key"]
        
        SWEEP_RM -- "HTTP 200/201" --> RM_PUB["RM Record: PUBLISHED"]
        SWEEP_ECR -- "HTTP 200/201" --> ECR_PUB["ECR Record: PUBLISHED"]
        
        SWEEP_RM -- "Network Error / 5xx" --> RM_RETRY["RM Retry Loop\n(RETRY_SCHEDULED -> Max 5 -> DLQ)"]
        SWEEP_ECR -- "Network Error / 5xx" --> ECR_RETRY["ECR Retry Loop\n(RETRY_SCHEDULED -> Max 5 -> DLQ)"]
    end

    subgraph External_Boundaries["External System Responsibilities"]
        RM_PUB --> RM_ACTION["Raw Material System (RM - V-SMART)\n• Records Material Rejection\n• Updates Raw Material Heat Ledger\n• Stages & Issues Replacement Stock\n• RM OWNS INVENTORY & DC (Production Does Not)"]
        
        ECR_PUB --> ECR_ACTION["Engineering Change Request (ECR)\n• Root-Cause Analysis (Machining vs Material)\n• Calculates Scrap Costing & Vendor Debits\n• ECR OWNS COSTING (Production Does Not)"]
    end

    classDef shop fill:#f8fafc,stroke:#475569,stroke-width:2px;
    classDef eval fill:#eff6ff,stroke:#2563eb,stroke-width:2px;
    classDef pm fill:#fef3c7,stroke:#d97706,stroke-width:2px;
    classDef outbox fill:#ecfdf5,stroke:#059669,stroke-width:2px;
    classDef failed fill:#fee2e2,stroke:#dc2626,stroke-width:2px;
    classDef ext fill:#f0f9ff,stroke:#0284c7,stroke-width:2px;

    class OP_DONE,QC_CLAIM,QC_INPUT shop;
    class QC_PASS,NCR_CREATE,PM_ALERT eval;
    class QC_FAIL failed;
    class DISP_CONT,DISP_REW,DISP_EXT,DISP_SCRAP pm;
    class DB_TX,OUTBOX_RM,OUTBOX_ECR,SWEEP_RM,SWEEP_ECR,RM_PUB,ECR_PUB,RM_RETRY,ECR_RETRY outbox;
    class RM_ACTION,ECR_ACTION ext;
```

---

### Diagram 6: Rework Iteration vs Measurement Immutability Data Model Flow (`flowchart TD`)

Demonstrates how multiple machining iterations maintain strictly immutable measurement histories without row overwrites (`BR-029, BR-034, BR-036`).

```mermaid
flowchart TD
    subgraph Iteration1["Iteration 1: Initial Machining & QC Failure"]
        OP20_IT1["operation_executions\nid: op-20-it1\noperation_number: 20\niteration_number: 1\nstatus: FAILED"]
        
        OP_M_IT1["operator_measurements (Immutable)\niteration_number: 1\nmeasured_value: 50.18 mm\nwithin_tolerance: true"]
        
        QC_M_IT1["qc_measurements (Immutable)\niteration_number: 1\nmeasured_value: 50.45 mm\nwithin_tolerance: false (FAILED)\ninstrument_id: CAL-MIC-004"]
        
        EX_REC["qc_exceptions\nid: exc-9901\nstatus: DISPOSITIONED_REWORK\ndisposition: RETURN_TO_EARLIER_OP\ntarget_operation_number: 20"]

        OP20_IT1 --> OP_M_IT1
        OP20_IT1 --> QC_M_IT1
        QC_M_IT1 --> EX_REC
    end

    subgraph Transition["PM Disposition & Iteration Fork"]
        EX_REC -- "Triggers Rework\nIteration + 1" --> OP20_IT2
    end

    subgraph Iteration2["Iteration 2: Rework Execution & QC Clearance"]
        OP20_IT2["operation_executions\nid: op-20-it2\noperation_number: 20\niteration_number: 2\nstatus: PASSED"]
        
        OP_M_IT2["operator_measurements (Immutable)\niteration_number: 2\nmeasured_value: 50.12 mm\nwithin_tolerance: true"]
        
        QC_M_IT2["qc_measurements (Immutable)\niteration_number: 2\nmeasured_value: 50.14 mm\nwithin_tolerance: true (PASSED)\ninstrument_id: CAL-MIC-004"]

        OP20_IT2 --> OP_M_IT2
        OP20_IT2 --> QC_M_IT2
    end

    subgraph PieceProgression["Downstream Routing"]
        QC_M_IT2 --> UNLOCK_OP30["Operation 30 Becomes READY\nPiece Released from Quarantine\nPiece: IN_PROGRESS"]
    end

    classDef it1 fill:#fee2e2,stroke:#dc2626,stroke-width:2px;
    classDef it2 fill:#eff6ff,stroke:#2563eb,stroke-width:2px;
    classDef trans fill:#fef3c7,stroke:#d97706,stroke-width:2px;
    classDef next fill:#ecfdf5,stroke:#059669,stroke-width:2px;

    class OP20_IT1,OP_M_IT1,QC_M_IT1 it1;
    class EX_REC trans;
    class OP20_IT2,OP_M_IT2,QC_M_IT2 it2;
    class UNLOCK_OP30 next;
```

---

## 3. Phase 1 Business Rules Traceability Matrix

This matrix maps every Quality Control, Tolerance, Exception, and Integration business rule from Phase 1 (`08_Business_Rules.md`, `02_SRS.md`, `09_Integration_Specification.md`) to the state models, entities, and transitions designed in Part 2.5.

| Business Rule ID | Phase 1 Rule Statement Summary | Part 2.5 Design Mapping & Implementation Entity | Covered In Document & Section |
| :--- | :--- | :--- | :--- |
| **BR-023** | Mandatory Operator measurement before operation completion. Operator must record all parameters before completing an operation. | `operator_measurements` table; `OP-T04` guard requiring count of entered parameters equals active tolerance parameter count. | [02-qc-inspection-state-machine-and-tolerances.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/02-qc-inspection-state-machine-and-tolerances.md) §3.1 |
| **BR-024** | Real-time tolerance validation on entry. System must validate entered values against lower/upper limits and display immediate PASS/FAIL feedback. | `ToleranceEngine.evaluate(value, param)` with static limits (`min_value`, `max_value`) and dynamic formula evaluation. | [02-qc-inspection-state-machine-and-tolerances.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/02-qc-inspection-state-machine-and-tolerances.md) §2.3 |
| **BR-025** | Tolerance values must be configurable per product, operation, and dimension parameter. | `operation_tolerance_parameters` linked to `operation_routing_templates` and scoped by `tolerance_revisions`. | [02-qc-inspection-state-machine-and-tolerances.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/02-qc-inspection-state-machine-and-tolerances.md) §2.1 |
| **BR-026** | Operator cannot complete operation if any required measurement is missing or out-of-spec without explicit override/exception. | `OP-T04` guard fails if `has_out_of_spec_operator_reading = true`. Forces operator to adjust or abort to PM. | [02-qc-inspection-state-machine-and-tolerances.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/02-qc-inspection-state-machine-and-tolerances.md) §3.1 |
| **BR-027** | Tolerance revisions require Designer verification and PM approval before activation. | `tolerance_revisions` lifecycle: `UPLOADED` $\rightarrow$ `EXTRACTED` $\rightarrow$ `DESIGN_VERIFIED` $\rightarrow$ `PM_APPROVED`. | [02-qc-inspection-state-machine-and-tolerances.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/02-qc-inspection-state-machine-and-tolerances.md) §2.2 |
| **BR-028** | Active tolerance revision changes apply only to future/unentered operations. Completed measurements retain historical revision. | Historical measurement records store `snapshot_revision_id` and frozen parameter snapshot values. Unentered operations link to active approved revision. | [02-qc-inspection-state-machine-and-tolerances.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/02-qc-inspection-state-machine-and-tolerances.md) §2.2 |
| **BR-029** | Measurement data is strictly immutable once submitted. No updates or deletes permitted. | PostgreSQL rule/trigger blocking `UPDATE` and `DELETE` on `operator_measurements` and `qc_measurements`. Rework tracked via `iteration_number`. | [02-qc-inspection-state-machine-and-tolerances.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/02-qc-inspection-state-machine-and-tolerances.md) §3.4 |
| **BR-030** | Independent QC measurement requirement. QC must record their own measurement values independently from the operator. | `qc_measurements` stored separately from `operator_measurements`. Independent timestamp, user ID, and instrument tracking. | [02-qc-inspection-state-machine-and-tolerances.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/02-qc-inspection-state-machine-and-tolerances.md) §3.2 |
| **BR-031** | Blind QC data entry. Operator measurements must not be pre-filled or visible in QC input fields. | UI rendering specification: QC data entry form displays empty numeric fields. API returns empty measurement template. | [02-qc-inspection-state-machine-and-tolerances.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/02-qc-inspection-state-machine-and-tolerances.md) §3.2 |
| **BR-032** | Calibrated instrument tracking. QC measurements must record the instrument ID and verify instrument calibration validity. | `qc_inspections.calibrated_instrument_id`; Guard `calibration_valid_until >= NOW()` enforced before inspection claim and submission. | [02-qc-inspection-state-machine-and-tolerances.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/02-qc-inspection-state-machine-and-tolerances.md) §3.3 |
| **BR-033** | Automatic QC Pass/Fail determination based on entered values against active tolerance limits. | Automatic verdict computation: if `count(within_tolerance = false) == 0` $\rightarrow$ `PASSED`; else $\rightarrow$ `FAILED`. No manual override by QC. | [02-qc-inspection-state-machine-and-tolerances.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/02-qc-inspection-state-machine-and-tolerances.md) §3.2 |
| **BR-034** | QC failure triggers quarantine. Piece is locked in quarantine and cannot proceed until PM decision. | `QC-T04` side-effect: `pieces.status = QUARANTINED`; `qc_exceptions` created in `OPEN_UNDER_REVIEW`. Blocks all downstream routing. | [03-exception-lifecycle-and-pm-dispositions.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/03-exception-lifecycle-and-pm-dispositions.md) §2 |
| **BR-035** | Four PM disposition options: Continue (Concession), Return to earlier op (Rework), Extra process, Reject & Restart (Scrap). | `qc_exceptions.disposition` enum with exact 4 options; transitions `EX-T02`, `EX-T03`, `EX-T04`, `EX-T05`. | [03-exception-lifecycle-and-pm-dispositions.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/03-exception-lifecycle-and-pm-dispositions.md) §3 |
| **BR-036** | Concession disposition requires mandatory justification and does not overwrite QC failure record. | `EX-T02` guard checks `engineering_justification IS NOT NULL`; QC inspection remains `FAILED`; operation state becomes `CONCESSION_ACCEPTED`. | [03-exception-lifecycle-and-pm-dispositions.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/03-exception-lifecycle-and-pm-dispositions.md) §3.1 |
| **BR-037** | Rework disposition increments iteration counter and reopens target operation. | `EX-T03` creates new `operation_executions` row with `iteration_number = n + 1` and `status = READY`. Piece returns to `IN_PROGRESS`. | [03-exception-lifecycle-and-pm-dispositions.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/03-exception-lifecycle-and-pm-dispositions.md) §3.2 |
| **BR-038** | Extra process disposition injects ad-hoc operation into routing with dedicated QC inspection. | `EX-T04` inserts ad-hoc `operation_executions` with `is_extra_operation = true`, assigns sequential display order, and creates QC inspection requirement. | [03-exception-lifecycle-and-pm-dispositions.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/03-exception-lifecycle-and-pm-dispositions.md) §3.3 |
| **BR-039** | Scrap disposition marks piece SCRAPPED, spawns replacement piece, and queues dual outbox notifications to RM and ECR. | `EX-T05` marks piece `SCRAPPED`, inserts replacement piece linked by `replacement_piece_id`, and inserts two dedicated `outbox_events` rows: one for RM (`MATERIAL_REJECTION_NOTICE`) and one for ECR (`SCRAP_EXCEPTION`). | [03-exception-lifecycle-and-pm-dispositions.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/03-exception-lifecycle-and-pm-dispositions.md) §3.4, [04-ecr-outbox-integration-state-machine.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/04-ecr-outbox-integration-state-machine.md) §1 |
| **BR-054** | Full audit trail for all measurements, inspections, exceptions, and dispositions. | Immutable tables `operator_measurements`, `qc_measurements`, `qc_inspections`, `qc_exceptions`, and `audit_log` with user, timestamp, and payload snapshots. | [02-qc-inspection-state-machine-and-tolerances.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/02-qc-inspection-state-machine-and-tolerances.md) §3.4 |
| **BR-056** | Multi-destination transactional outbox for external RM and ECR integration without distributed message brokers. | ADR-02 implementation: `outbox_events` table polled via `SELECT FOR UPDATE SKIP LOCKED`, exponential backoff retry, and independent HTTPS POST delivery to RM and ECR endpoints. | [04-ecr-outbox-integration-state-machine.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-2/2.5-qc-and-exception-state-machines/04-ecr-outbox-integration-state-machine.md) §2 |

---

## 4. Open Questions and Clarifications Register

The following items are identified during Part 2.5 design and documented for business confirmation during subsequent implementation phases:

### Question 1: Automated Drawing Extraction Reliability & Manual Fallback Threshold
* **Context:** The tolerance revision lifecycle supports automated extraction of dimensional tolerances from engineering drawings (`PDF`/`CSV`) uploaded by the Designer.
* **Question:** What is the business protocol when the automated OCR/drawing parser has partial confidence or fails to extract certain dimensions?
* **Design Assumption:** The extraction pipeline flags extracted fields as `EXTRACTED`. The Designer must manually inspect and verify 100% of the extracted values in the UI before transitioning the revision to `DESIGN_VERIFIED`. If automated extraction fails completely, the Designer can manually enter all parameters from scratch.
* **Action:** To be confirmed with engineering design leads in Phase 3.

### Question 2: Calibrated Instrument Expiration During Active Inspection
* **Context:** A QC inspection requires a valid calibrated instrument (`calibration_valid_until >= NOW()`).
* **Question:** If an instrument passes calibration validation when claimed (`CLAIMED_IN_PROGRESS`), but expires at midnight during a multi-hour or overnight inspection shift, should submission be blocked?
* **Design Assumption:** The calibration validity is re-validated at final inspection submission (`QC-T03` and `QC-T04`). If expired at submission, the system blocks completion and instructs the QC inspector to either select a freshly calibrated instrument or obtain a calibration extension.
* **Action:** Review with Quality Assurance manager during shop-floor acceptance testing.

### Question 3: Real-Time Notification Delivery Channel for PM Quarantine Alerts
* **Context:** When a QC inspection fails, a non-conformance record is generated and the piece is quarantined. The PM must be notified immediately (`BR-034`).
* **Question:** Which notification channels are required beyond the in-app Project Manager exception dashboard (e.g., Email, SMS, WhatsApp, Webhooks)?
* **Design Assumption:** The core Modular Monolith publishes an internal Spring ApplicationEvent `QcInspectionFailedEvent`. The primary notification is the real-time in-app dashboard badge and priority alert queue. Outbound email notification is an optional plugin listener.
* **Action:** Confirm notification channel requirements with business stakeholders prior to Phase 4 (PM Exception Management UI).

### Question 4: Maximum Retry Limit & Escalation for Dead-Letter Outbox Events (RM & ECR)
* **Context:** If an external endpoint (RM or ECR) is unreachable or repeatedly fails with 5xx/timeout errors, records transition to `RETRY_SCHEDULED` and attempt re-delivery up to 5 times before transitioning to `DEAD_LETTER`.
* **Question:** What is the business SLA and escalation procedure for dead-letter records, and how is manual administrative intervention audited?
* **Design Assumption:** 
  1. Dead-letter events raise a high-priority system administrator alert in the monitoring console.
  2. Shop-floor operations and physical piece replacement proceed without blocking; external synchronization status is visually flagged.
  3. When an administrator resolves the root cause and triggers "Re-Drive DLQ", the system appends an administrative intervention record to the audit trail (recording admin ID, timestamp, and diagnosis notes) rather than silently erasing historical attempt logs.
* **Action:** Validate SLA with IT infrastructure team during Phase 2.7 (Integration Architecture).
