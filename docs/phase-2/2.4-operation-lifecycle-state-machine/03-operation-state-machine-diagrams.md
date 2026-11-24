# Part 2.4 – Operation Lifecycle State Machine
## Document 03: Comprehensive Operation State Machine Diagrams

---

### Diagram 1: Complete Operation State Machine Topology

The following Mermaid state diagram captures all ten approved states and their permissible transition pathways:

```mermaid
stateDiagram-v2
    [*] --> PENDING : Instantiated from Template Snapshot

    PENDING --> READY : 1. Material Ready (Op 10)\n2. Predecessor Passed / Concession (Op 20+)
    PENDING --> BYPASSED : PM Administrative Bypass (Authorized Reason)
    PENDING --> CANCELLED : Parent Piece Scrapped

    READY --> OPENED : Operator Opens Job Card on Terminal
    READY --> BYPASSED : PM Administrative Bypass
    READY --> CANCELLED : Parent Piece Scrapped

    OPENED --> QC_PENDING : Operator Submits Measurements via Bulk Grid
    OPENED --> READY : Operator Releases Job (Shift End / Station Shift)
    OPENED --> CANCELLED : Parent Piece Scrapped

    QC_PENDING --> QC_IN_PROGRESS : QC Inspector Claims Inspection Card
    QC_PENDING --> CANCELLED : Parent Piece Scrapped

    QC_IN_PROGRESS --> PASSED : QC Approves (100% Within Tolerances)
    QC_IN_PROGRESS --> FAILED : QC Rejects (Out-of-Tolerance Parameter)

    FAILED --> CONCESSION_ACCEPTED : PM Disposition: CONTINUE (Mandatory Justification)
    FAILED --> READY : PM Disposition: RETURN TO EARLIER OP (Iteration + 1)
    FAILED --> CANCELLED : PM Disposition: REJECT AND RESTART (Piece Scrapped)

    PASSED --> READY : PM Reopens Operation (Engineering Justification)
    CONCESSION_ACCEPTED --> READY : PM Reopens Operation (Engineering Justification)

    PASSED --> [*] : Unlocks Next Sequential Operation
    CONCESSION_ACCEPTED --> [*] : Unlocks Next Sequential Operation
    BYPASSED --> [*] : Unlocks Next Sequential Operation
    CANCELLED --> [*] : Permanent Retirement
```

---

### Diagram 2: Normal Operation Execution Flow

Linear happy-path execution of a single routing operation on the shop floor:

```mermaid
flowchart TD
    A["Predecessor Op Passed or Material Ready<br/>(status: PENDING)"] --> B["System Evaluates Preconditions<br/>• Material Gate Checked<br/>• Predecessor QC Passed"]
    B --> C["Operation Transitions to READY<br/>ready_at = NOW()<br/>Appears on Operator Priority Worklist"]
    C --> D["Operator Scans Barcode & Opens Job Card<br/>opened_at = NOW()<br/>status: OPENED"]
    D --> E["Operator Performs Machining & Enters Values<br/>• Bulk Grid Data Entry<br/>• Non-blocking Out-of-Tolerance Warnings"]
    E --> F["Operator Clicks 'Submit Operation Measurements'<br/>submitted_at = NOW()<br/>status: QC_PENDING"]
    F --> G["QC Inspector Claims Piece from Queue<br/>qc_started_at = NOW()<br/>status: QC_IN_PROGRESS"]
    G --> H["QC Inspector Records Independent Readings<br/>System Checks Compliance vs. Tolerances"]
    H --> I{"100% Within Approved<br/>Tolerance Band?"}
    I -->|Yes| J["QC Submits Verdict = PASSED<br/>completed_at = NOW()<br/>status: PASSED"]
    J --> K["System Automatically Activates<br/>Next Sequential Op to READY"]

    classDef pending fill:#e0f2fe,stroke:#0284c7,stroke-width:2px;
    classDef ready fill:#fef3c7,stroke:#d97706,stroke-width:2px;
    classDef inprog fill:#ede9fe,stroke:#7c3aed,stroke-width:2px;
    classDef qc fill:#fef9c3,stroke:#ca8a04,stroke-width:2px;
    classDef pass fill:#dcfce7,stroke:#16a34a,stroke-width:2px;

    class A pending;
    class B,C ready;
    class D,E inprog;
    class F,G,H,I qc;
    class J,K pass;
```

---

### Diagram 3: Operator Measurement Submission Flow

The operator data-entry validation, warning prompt, and submission lifecycle:

```mermaid
flowchart TD
    START["Operator Views Active Job in OPENED Status"] --> GRID["Operator Populates Bulk Entry Grid<br/>(Manual Entry or Excel Paste)"]
    GRID --> VAL_SCHEMA{"All Mandatory Fields<br/>Present and Formatted?"}
    
    VAL_SCHEMA -->|No| ERR_SCHEMA["Reject Input with Validation Error<br/>Highlight Missing/Malformed Cells<br/>(Status Remains OPENED)"]
    VAL_SCHEMA -->|Yes| EVAL_TOL{"Are Any Measurements<br/>Outside Tolerance Rules?"}

    EVAL_TOL -->|No: All Nominal| READY_SUBMIT["Enable Clean Submit Button"]
    EVAL_TOL -->|Yes: Out of Spec| WARN_PROMPT["Display Non-Blocking Warning Banner<br/>'Dimension X exceeds tolerance limit.'<br/>Prompt: Acknowledge & Submit for QC Review"]

    READY_SUBMIT --> SUBMIT_ACTION["Operator Confirms Submission"]
    WARN_PROMPT --> SUBMIT_ACTION

    SUBMIT_ACTION --> PERSIST["Atomic Database Transaction:<br/>1. Persist operation_measurements (role: OPERATOR)<br/>2. Persist granular measurement_values<br/>3. Stamp submitted_at = NOW()<br/>4. Transition status: QC_PENDING<br/>5. Append audit event OPERATOR_SUBMITTED"]

    PERSIST --> BROADCAST["Push SSE Notification to QC Inspector Worklist<br/>Lock Operator Edit Access"]

    classDef input fill:#f0f9ff,stroke:#0369a1,stroke-width:2px;
    classDef check fill:#fef3c7,stroke:#d97706,stroke-width:2px;
    classDef warn fill:#fee2e2,stroke:#dc2626,stroke-width:2px;
    classDef success fill:#dcfce7,stroke:#16a34a,stroke-width:2px;

    class START,GRID input;
    class VAL_SCHEMA,EVAL_TOL check;
    class ERR_SCHEMA,WARN_PROMPT warn;
    class READY_SUBMIT,SUBMIT_ACTION,PERSIST,BROADCAST success;
```

---

### Diagram 4: Independent QC Inspection Flow

Independent verification, instrument mapping, and verdict submission:

```mermaid
flowchart TD
    QUEUE["Piece Staged in Inspection Bay<br/>status: QC_PENDING"] --> CLAIM["QC Inspector Clicks 'Inspect Piece'<br/>• Atomic Lock: assigned_qc_id = qc_user_id<br/>• qc_started_at = NOW()<br/>• status: QC_IN_PROGRESS"]
    
    CLAIM --> VERIFY_TOOL["QC Inspector Enters Calibrated<br/>Instrument ID (Micrometer/Caliper)"]
    VERIFY_TOOL --> INPUT["QC Inspector Inputs Verification Readings<br/>• Separate QC Data Model<br/>• Blank Fields (Zero Pre-filling from Operator)"]
    
    INPUT --> COMPARE["System Computes Compliance:<br/>Lower Limit <= QC Reading <= Upper Limit"]
    
    COMPARE --> VERDICT{"Do 100% of Readings<br/>Pass Tolerances?"}

    VERDICT -->|Yes| PASS_SUBMIT["QC Submits: Verdict = PASSED<br/>• operation status: PASSED<br/>• completed_at = NOW()<br/>• Next Op transitions to READY"]
    
    VERDICT -->|No| FAIL_SUBMIT["QC Submits: Verdict = FAILED<br/>• operation status: FAILED<br/>• completed_at = NOW()<br/>• Piece quarantined (ON_HOLD_QC_FAILED)<br/>• qc_exceptions opened (PENDING_PM_DISPOSITION)"]

    classDef queue fill:#fef3c7,stroke:#d97706,stroke-width:2px;
    classDef claim fill:#ede9fe,stroke:#7c3aed,stroke-width:2px;
    classDef eval fill:#f0f9ff,stroke:#0284c7,stroke-width:2px;
    classDef pass fill:#dcfce7,stroke:#16a34a,stroke-width:2px;
    classDef fail fill:#fee2e2,stroke:#dc2626,stroke-width:2px;

    class QUEUE queue;
    class CLAIM,VERIFY_TOOL,INPUT claim;
    class COMPARE,VERDICT eval;
    class PASS_SUBMIT pass;
    class FAIL_SUBMIT fail;
```

---

### Diagram 5: QC Failure to PM Disposition Flow

Exception routing and PM decision gateway:

```mermaid
flowchart TD
    FAIL_EVENT["Operation Fails QC (status: FAILED)<br/>Piece Status: ON_HOLD_QC_FAILED"] --> ESCALATE["Urgent SSE Alert Sent to PM Dashboard<br/>Ticket Added to PM Priority Exception Queue"]
    
    ESCALATE --> PM_REVIEW["PM Inspects Non-Conformance Details:<br/>• Drawing Blueprint & Specification<br/>• Measured Readings vs. Upper/Lower Limits<br/>• Operator & QC Inspection Timestamps"]

    PM_REVIEW --> PM_DECISION{"Production Manager (PM)<br/>Selects Binding Disposition"}

    PM_DECISION -->|CONTINUE| DISP_CONT["PM Concession Authorization<br/>(See Diagram 1 & 4)"]
    PM_DECISION -->|RETURN TO EARLIER OP| DISP_REWIND["Routing Rewind for Rework<br/>(See Diagram 6)"]
    PM_DECISION -->|EXTRA PROCESS| DISP_EXTRA["Insert Remedial Operation<br/>(See Diagram 6)"]
    PM_DECISION -->|REJECT AND RESTART| DISP_SCRAP["Scrap Piece & Spawn Replacement<br/>(See Diagram 7)"]

    classDef fail fill:#fee2e2,stroke:#dc2626,stroke-width:2px;
    classDef review fill:#ede9fe,stroke:#7c3aed,stroke-width:2px;
    classDef opt fill:#e0f2fe,stroke:#0284c7,stroke-width:2px;

    class FAIL_EVENT,ESCALATE fail;
    class PM_REVIEW,PM_DECISION review;
    class DISP_CONT,DISP_REWIND,DISP_EXTRA,DISP_SCRAP opt;
```

---

### Diagram 6: EXTRA PROCESS and RETURN TO EARLIER OPERATION Flows

Remedial routing pathways:

```mermaid
flowchart TD
    subgraph ReturnFlow ["PM Disposition: RETURN TO EARLIER OPERATION (Rewind)"]
        R1["PM Selects Target Operation (e.g. Op 20)<br/>from Previous Completed Steps"] --> R2["Target Op Resets to READY<br/>• iteration_number incremented (1 -> 2)<br/>• ready_at = NOW()<br/>• Prior measurements preserved immutable"]
        R2 --> R3["Operator Machines Rework & Submits<br/>under iteration_number = 2"]
        R3 --> R4["QC Inspects Rework Measurements<br/>under iteration_number = 2"]
        R4 -->|Pass| R5["Rework Operation Certified PASSED<br/>Piece Advances to Next Op<br/>Following the Failure Point"]
        R4 -->|Fail Again| R6["Operation Fails Again (Iteration 2 FAILED)<br/>Piece Returns to ON_HOLD_QC_FAILED"]
    end

    subgraph ExtraFlow ["PM Disposition: EXTRA PROCESS (Ad-Hoc Step)"]
        E1["PM Injects New Remedial Operation<br/>(e.g. Op 25: Skim Grinding)<br/>• is_extra_operation = true<br/>• status: READY"] --> E2["Operator Executes Extra Operation<br/>status: OPENED -> QC_PENDING"]
        E2 --> E3["QC Inspects Extra Operation<br/>status: QC_IN_PROGRESS"]
        E3 -->|Pass| E4["Extra Op Certified PASSED<br/>Original Failed Op Marked Resolved<br/>Downstream Op 30 Becomes READY"]
        E3 -->|Fail Again| E5["Extra Op Fails QC<br/>Re-escalates to PM Exception Queue"]
    end

    classDef rewind fill:#fef3c7,stroke:#d97706,stroke-width:2px;
    classDef extra fill:#e0e7ff,stroke:#4338ca,stroke-width:2px;
    classDef pass fill:#dcfce7,stroke:#16a34a,stroke-width:2px;
    classDef fail fill:#fee2e2,stroke:#dc2626,stroke-width:2px;

    class R1,R2,R3,R4 rewind;
    class E1,E2,E3 extra;
    class R5,E4 pass;
    class R6,E5 fail;
```

---

### Diagram 7: REJECT AND RESTART (Scrap & Replacement) Flow

Retirement of defective piece and substitution lifecycle:

```mermaid
flowchart TD
    SCRAP_DECISION["PM Approves REJECT AND RESTART<br/>Enters Mandatory Scrap Engineering Rationale"] --> SCRAP_EXEC["Atomic Database Execution:<br/>1. Current piece_operations marked CANCELLED<br/>2. Parent piece marked SCRAPPED (Permanent)<br/>3. All failure measurement records frozen immutable"]
    
    SCRAP_EXEC --> OUTBOX["Transactional Outbox Enqueues Event:<br/>• aggregate_type = 'SCRAP_EXCEPTION'<br/>• payload = SC, piece serial, failed op, deviations<br/>• Background worker posts to external ECR"]
    
    SCRAP_EXEC --> SPAWN["System Spawns Child Replacement Piece:<br/>• Inherits assembled_set_id and set_sequence_number<br/>• replacement_sequence = parent.replacement_sequence + 1<br/>• Serial: SC002-1 SPG-1-R1<br/>• status: AWAITING_MATERIAL"]

    SPAWN --> LINEAGE["Record Genealogy in piece_replacement_lineage<br/>Update set_component_allocations Manifest"]
    
    SPAWN --> WAIT_RM["Replacement Piece Gated in AWAITING_MATERIAL<br/>Machining Op 10 Blocked Until RM Issues New Stock"]

    WAIT_RM --> RM_STOCK["RM Confirms Material Issue for Replacement<br/>Replacement Piece Op 10 Transitions to READY"]

    classDef scrap fill:#fee2e2,stroke:#dc2626,stroke-width:2px;
    classDef outbox fill:#fef3c7,stroke:#d97706,stroke-width:2px;
    classDef spawn fill:#e0f2fe,stroke:#0284c7,stroke-width:2px;
    classDef ready fill:#dcfce7,stroke:#16a34a,stroke-width:2px;

    class SCRAP_DECISION,SCRAP_EXEC scrap;
    class OUTBOX outbox;
    class SPAWN,LINEAGE,WAIT_RM spawn;
    class RM_STOCK ready;
```

---

### Diagram 8: Sequential Operation Activation and Piece Coordination

How individual operations gate one another and coordinate with the parent piece:

```mermaid
flowchart TD
    subgraph ProductGate ["Parent Subcontract Product Gate"]
        RM_GATE{"Is subcontract_products<br/>material_ready == true?"}
    end

    subgraph OperationRouting ["Piece Routing Operations (Sequential Execution)"]
        OP10["Op 10: Rough Turning<br/>status: PENDING"]
        OP20["Op 20: CNC Milling<br/>status: PENDING"]
        OP30["Op 30: Cylindrical Grinding<br/>status: PENDING"]
    end

    subgraph PieceState ["Parent Physical Piece State"]
        PIECE_AWAIT["Piece Status: AWAITING_MATERIAL"]
        PIECE_PROD["Piece Status: IN_PRODUCTION"]
        PIECE_DONE["Piece Status: COMPLETED"]
    end

    RM_GATE -->|No| PIECE_AWAIT
    RM_GATE -->|Yes| PIECE_PROD
    
    PIECE_PROD -->|Unlocks Op 10 ONLY| OP10_ACT["Op 10 status -> READY<br/>(Op 20 & 30 Remain PENDING)"]
    
    OP10_ACT --> OP10_EXEC["Op 10 Execution & QC"]
    OP10_EXEC -->|PASSED or CONCESSION| OP20_ACT["Op 20 status -> READY<br/>(Op 30 Remains PENDING)"]
    
    OP20_ACT --> OP20_EXEC["Op 20 Execution & QC"]
    OP20_EXEC -->|PASSED or CONCESSION| OP30_ACT["Op 30 status -> READY"]
    
    OP30_ACT --> OP30_EXEC["Op 30 Execution & QC"]
    OP30_EXEC -->|PASSED or CONCESSION| ALL_DONE["100% Operations Resolved"]

    ALL_DONE --> PIECE_DONE

    classDef gate fill:#fef3c7,stroke:#d97706,stroke-width:2px;
    classDef op fill:#f0f9ff,stroke:#0284c7,stroke-width:2px;
    classDef piece fill:#dcfce7,stroke:#16a34a,stroke-width:2px;

    class RM_GATE,PIECE_AWAIT gate;
    class OP10,OP20,OP30,OP10_ACT,OP10_EXEC,OP20_ACT,OP20_EXEC,OP30_ACT,OP30_EXEC,ALL_DONE op;
    class PIECE_PROD,PIECE_DONE piece;
```

---

### Diagram 9: PM Operation-Control Governance Flow

Administrative controls exercised by the Production Manager:

```mermaid
flowchart TD
    PM_ACTION{"PM Administrative Action"}

    PM_ACTION -->|Add Operation| ADD_OP["Insert Operation Step<br/>• Define sequence order<br/>• Select template/custom step<br/>• Assign station/operator<br/>• Status: PENDING or READY"]
    
    PM_ACTION -->|Bypass Operation| BYPASS_OP["Authorize Bypass<br/>• Requires mandatory engineering reason<br/>• Precondition: status is PENDING or READY<br/>• Status -> BYPASSED<br/>• Unlocks downstream operation to READY"]
    
    PM_ACTION -->|Resequence Operations| RESEQ_OP["Resequence Unstarted Steps<br/>• Validates sequence numbers remain ascending<br/>• Precondition: affected ops are PENDING<br/>• Cannot resequence active or completed steps"]
    
    PM_ACTION -->|Reopen Operation| REOPEN_OP["Reopen Completed Operation<br/>• Requires mandatory engineering reason<br/>• Precondition: downstream ops not completed<br/>• Increments iteration_number<br/>• Status -> READY for re-machining"]
    
    PM_ACTION -->|Reassign Station/Operator| REASSIGN_OP["Change Machine / Operator<br/>• Update assigned_operator_id / station_id<br/>• Audited in audit_logs<br/>• Refreshes operator worklists via SSE"]

    ADD_OP --> AUDIT["Immutable Audit Record Written (audit_logs)<br/>SSE Event Dispatched to Shop Floor"]
    BYPASS_OP --> AUDIT
    RESEQ_OP --> AUDIT
    REOPEN_OP --> AUDIT
    REASSIGN_OP --> AUDIT

    classDef action fill:#ede9fe,stroke:#7c3aed,stroke-width:2px;
    classDef flow fill:#f0f9ff,stroke:#0369a1,stroke-width:2px;
    classDef audit fill:#dcfce7,stroke:#16a34a,stroke-width:2px;

    class PM_ACTION action;
    class ADD_OP,BYPASS_OP,RESEQ_OP,REOPEN_OP,REASSIGN_OP flow;
    class AUDIT audit;
```
