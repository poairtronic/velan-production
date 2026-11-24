# Part 2.3 – Piece Lifecycle State Machine
## Document 03: Comprehensive Piece State Machine Diagrams

---

### 1. Complete Piece Lifecycle State Diagram

The following Mermaid state diagram specifies the complete state transition topology for physical pieces in the Velan Production platform:

```mermaid
stateDiagram-v2
    [*] --> CREATED : SC Intake & Deterministic Piece Pre-Allocation

    CREATED --> AWAITING_MATERIAL : Parent Product Material Not Ready
    CREATED --> IN_PRODUCTION : Material Already Ready at Intake

    AWAITING_MATERIAL --> IN_PRODUCTION : RM Confirms Material Issuance / PM Toggle\n(Op 10 Becomes READY)

    state IN_PRODUCTION {
        [*] --> Operation_Ready
        Operation_Ready --> Operation_Opened : Operator Opens Job
        Operation_Opened --> Operation_Submitted : Operator Enters Measurements
        Operation_Submitted --> QC_Pending : Submitted to QC Queue
        QC_Pending --> Operation_Passed : QC Approves (Within Tolerance)
        Operation_Passed --> Operation_Ready : Advance to Next Sequential Op
    }

    IN_PRODUCTION --> ON_HOLD_QC_FAILED : Operation QC Verdict = FAILED\n(Quarantine & Escalate to PM)

    state ON_HOLD_QC_FAILED {
        [*] --> Pending_PM_Review
        Pending_PM_Review --> PM_Decision_Made : PM Evaluates Concession / Rework / Scrap
    }

    ON_HOLD_QC_FAILED --> IN_PRODUCTION : 1. PM CONTINUE (Concession)\n[Op = CONCESSION_ACCEPTED; Next Op = READY]
    ON_HOLD_QC_FAILED --> IN_PRODUCTION_REWORK : 2. PM RETURN TO EARLIER OP\n[Rewind Sequence; Increment Rework Iteration]
    ON_HOLD_QC_FAILED --> IN_PRODUCTION_EXTRA_PROCESS : 3. PM EXTRA PROCESS\n[Insert Ad-Hoc Op; Increment Iteration]
    ON_HOLD_QC_FAILED --> SCRAPPED : 4. PM REJECT AND RESTART\n[Retire Piece; Trigger ECR; Spawn Replacement]

    IN_PRODUCTION_REWORK --> IN_PRODUCTION : Rework QC Passed\n(Rejoins Standard Routing)
    IN_PRODUCTION_REWORK --> ON_HOLD_QC_FAILED : Rework QC Failed Again\n(Re-escalate to PM)

    IN_PRODUCTION_EXTRA_PROCESS --> IN_PRODUCTION : Extra Process QC Passed\n(Rejoins Standard Routing)
    IN_PRODUCTION_EXTRA_PROCESS --> ON_HOLD_QC_FAILED : Extra Process QC Failed\n(Re-escalate to PM)

    IN_PRODUCTION --> COMPLETED : Final Routing Operation Passes QC\n(All Operations Resolved)

    SCRAPPED --> [*] : Permanent Immutable Retirement\n(Replacement spawns into AWAITING_MATERIAL)

    state COMPLETED {
        [*] --> Piece_Done
        Piece_Done --> Staged_For_Assembly : Staged in Assembly Area
        Staged_For_Assembly --> Set_Aggregated : All Sibling Pieces Complete
    }

    COMPLETED --> [*] : Assembled into Set for Final QC
```

---

### 2. Standard Happy-Path Execution Flow

In standard operation without quality failures, the physical piece moves linearly from intake to completion:

```mermaid
flowchart TD
    A["SC Approved & Pieces Pre-Allocated<br/>(status: CREATED)"] --> B{"Is Parent Product<br/>Material Ready?"}
    
    B -->|No| C["AWAITING_MATERIAL<br/>(Machining Blocked)"]
    C -->|RM Material Issued Webhook<br/>or Manual PM Toggle| D["material_ready = true<br/>(status: IN_PRODUCTION)"]
    B -->|Yes| D

    D --> E["Op 10 (CNC Turning)<br/>status: READY"]
    E --> F["Operator Opens Job &<br/>Enters Dimensional Measurements"]
    F --> G["QC Inspector Independently<br/>Measures & Approves (PASSED)"]

    G --> H["Op 20 (Drilling)<br/>status: READY"]
    H --> I["Operator Opens Job &<br/>Enters Measurements"]
    I --> J["QC Inspector Independently<br/>Approves (PASSED)"]

    J --> K["Op 30 (Final Grinding)<br/>status: READY"]
    K --> L["Operator Opens Job &<br/>Enters Measurements"]
    L --> M["QC Inspector Independently<br/>Approves (PASSED)"]

    M --> N["All Operations Resolved<br/>status: COMPLETED"]
    N --> O["Evaluate Parent Set Completeness<br/>(Sibling Synchronization)"]

    classDef created fill:#e0f2fe,stroke:#0284c7,stroke-width:2px;
    classDef awaiting fill:#fef3c7,stroke:#d97706,stroke-width:2px;
    classDef prod fill:#dcfce7,stroke:#16a34a,stroke-width:2px;
    classDef complete fill:#bbf7d0,stroke:#15803d,stroke-width:2px;

    class A created;
    class C awaiting;
    class D,E,F,G,H,I,J,K,L,M prod;
    class N,O complete;
```

---

### 3. Detailed Exception Handling & PM Disposition Pathways

When an operation measurement falls outside approved tolerances, the piece is diverted into the exception handling subsystem:

```mermaid
flowchart TD
    FAIL["QC Inspector Enters Measurements<br/>Verdict: FAILED"] --> HOLD["Piece placed in ON_HOLD_QC_FAILED<br/>qc_exceptions Ticket Opened<br/>Downstream Ops Locked"]
    
    HOLD --> PM{"Production Manager (PM)<br/>Evaluates Deviation"}

    PM -->|Option 1: CONTINUE| CONT["PM Enters Justification Notes<br/>• Op marked CONCESSION_ACCEPTED<br/>• Next Op transitions to READY<br/>• Piece status: IN_PRODUCTION<br/>• Prior measurements preserved"]
    
    PM -->|Option 2: RETURN TO OP| REWORK["PM Selects Target Operation<br/>• Target Op reset to READY<br/>• iteration_number incremented (e.g. 2)<br/>• Piece status: IN_PRODUCTION_REWORK<br/>• Prior measurements preserved"]
    
    PM -->|Option 3: EXTRA PROCESS| EXTRA["PM Inserts Ad-Hoc Op (Seq 25)<br/>• Extra Op marked READY<br/>• iteration_number incremented<br/>• Piece status: IN_PRODUCTION_EXTRA_PROCESS<br/>• Prior measurements preserved"]
    
    PM -->|Option 4: REJECT & RESTART| SCRAP["PM Enters Scrap Engineering Reason<br/>• Piece status permanently SCRAPPED<br/>• ECR Outbox Event Queued (SCRAP_EXCEPTION)<br/>• Child Replacement Piece Spawned (e.g. -R1)<br/>• Replacement inherits set slot in AWAITING_MATERIAL"]

    CONT --> RESUME["Piece Continues Standard Routing"]
    
    REWORK --> REWORK_EXEC["Operator Executes Rework<br/>& Submits New Measurements"]
    REWORK_EXEC --> REWORK_QC{"QC Inspects Rework"}
    REWORK_QC -->|Pass| RESUME
    REWORK_QC -->|Fail Again| HOLD

    EXTRA --> EXTRA_EXEC["Operator Executes Extra Op<br/>& Submits Measurements"]
    EXTRA_EXEC --> EXTRA_QC{"QC Inspects Extra Op"}
    EXTRA_QC -->|Pass| RESUME
    EXTRA_QC -->|Fail Again| HOLD

    classDef fail fill:#fee2e2,stroke:#dc2626,stroke-width:2px;
    classDef hold fill:#fef2f2,stroke:#b91c1c,stroke-width:3px;
    classDef pm fill:#ede9fe,stroke:#7c3aed,stroke-width:2px;
    classDef path fill:#e0e7ff,stroke:#4338ca,stroke-width:2px;
    classDef scrap fill:#cbd5e1,stroke:#475569,stroke-width:2px;
    classDef resume fill:#dcfce7,stroke:#16a34a,stroke-width:2px;

    class FAIL fail;
    class HOLD hold;
    class PM pm;
    class CONT,REWORK,EXTRA path;
    class SCRAP scrap;
    class RESUME resume;
```

---

### 4. Parallel Sibling Piece Execution vs. Set Assembly Synchronization

In a manufactured valve assembly, individual BOM components progress along independent shop-floor paths and converge only at set assembly:

```mermaid
flowchart LR
    subgraph SiblingPieces ["Set #1: Independent Sibling Component Piece Execution"]
        P1["Piece: SC002-1 VBM (Valve Body)<br/>Status: COMPLETED (Op 10, 20, 30 Done)"]
        P2["Piece: SC002-1 ARG (Flange)<br/>Status: COMPLETED (Op 10, 20 Done)"]
        P3["Piece: SC002-1 SPG-1 (Spindle 1)<br/>Status: IN_PRODUCTION (Op 20 Milling)"]
        P4["Piece: SC002-1 SPG-2 (Spindle 2)<br/>Status: ON_HOLD_QC_FAILED (Awaiting PM)"]
        P5["Piece: SC002-1 JIG-1 (Jig Foot 1)<br/>Status: COMPLETED"]
        P6["Piece: SC002-1 JIG-2 (Jig Foot 2)<br/>Status: COMPLETED"]
        P7["Piece: SC002-1 JIG-3 (Jig Foot 3)<br/>Status: COMPLETED"]
    end

    subgraph SetSyncGate ["Set Completeness Evaluation Gate"]
        CHECK{"Are 100% of<br/>Pre-Allocated Pieces<br/>COMPLETED?"}
        BLOCKED["Set #1: BUILDING<br/>is_production_complete = false<br/>(Cannot Assemble / Cannot Pack)"]
        READY_SET["Set #1: PRODUCTION_COMPLETE<br/>is_production_complete = true<br/>(Ready for Final Set QC)"]
    end

    subgraph SetProgression ["Parent Set Sequential Milestones"]
        QC_STAGE["FINAL_QC_PASSED<br/>(is_final_qc_passed = true)"]
        PACK_STAGE["PACKING_CONFIRMED<br/>(is_packed = true)"]
        DELIV_STAGE["DELIVERED<br/>(is_delivered = true)"]
    end

    P1 --> CHECK
    P2 --> CHECK
    P3 --> CHECK
    P4 --> CHECK
    P5 --> CHECK
    P6 --> CHECK
    P7 --> CHECK

    CHECK -->|No: Spindle 1 in progress,<br/>Spindle 2 on hold| BLOCKED
    CHECK -->|Yes: When all 7 pieces<br/>reach COMPLETED| READY_SET

    READY_SET --> QC_STAGE
    QC_STAGE --> PACK_STAGE
    PACK_STAGE --> DELIV_STAGE

    classDef done fill:#dcfce7,stroke:#16a34a,stroke-width:2px;
    classDef inprog fill:#e0f2fe,stroke:#0284c7,stroke-width:2px;
    classDef hold fill:#fee2e2,stroke:#dc2626,stroke-width:2px;
    classDef gate fill:#fef3c7,stroke:#d97706,stroke-width:2px;
    classDef stage fill:#f0fdf4,stroke:#15803d,stroke-width:2px;

    class P1,P2,P5,P6,P7 done;
    class P3 inprog;
    class P4 hold;
    class CHECK,BLOCKED gate;
    class READY_SET,QC_STAGE,PACK_STAGE,DELIV_STAGE stage;
```
