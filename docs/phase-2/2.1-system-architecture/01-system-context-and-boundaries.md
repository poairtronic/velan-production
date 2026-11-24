# Part 2.1 – System Architecture Design
## Document 01: System Context and Enterprise Boundaries

### 1. Purpose and Overview

This document specifies the high-level system context, user personas, external application boundaries, and explicit data ownership rules for the **Velan Production System**. 

Velan Production is an execution-layer industrial platform designed to track manufactured components at an individual piece level across machining, heat treatment, vendor processing, assembly, and quality assurance. It operates within a strictly defined enterprise landscape alongside two existing, autonomous legacy systems: **RM (V-SMART)** and **ECR (Engineering Change Request)**.

---

### 2. User Roles and Personas

The application architecture accommodates six distinct user roles with discrete access controls and workflow capabilities:

| Role | Core Responsibilities | Key Architectural Touchpoints |
| :--- | :--- | :--- |
| **Admin** | System configuration, user account provisioning, role mapping, master data setup, system health monitoring. | Security/IAM module, audit logs, configuration registers. |
| **Project Manager (PM)** | Shop-floor production planning, job dispatching, QC failure disposition decisions (Continue, Extra Process, Return to Operation, Reject/Scrap), replacement piece authorization, packing and final dispatch clearance. | Production execution, QC disposition engine, exception lifecycle, dispatch gates. |
| **Designer (Engineering)** | Product structure definition, BOM management, drawing/spec uploads to Cloudflare R2, process template creation, static and dynamic dimensional tolerance configuration. | BOM & Engineering module, Cloudflare R2 pre-signed uploads, process templates. |
| **Operator (Machinist)** | Operation pickup, starting work at a specific station, submitting operation completion, recording primary operator measurements. | Shop-floor UI, operation lifecycle state machine, measurement intake. |
| **Quality Control (QC)** | Independent verification of operations, recording secondary QC measurements, automated tolerance validation, logging non-conformances, issuing Pass/Fail verdicts. | QC module, tolerance validation engine, inspection logs. |
| **Management** | High-level visibility into shop-floor throughput, stage-wise bottleneck analysis, yield/scrap rates, on-time delivery metrics. | Read-model dashboards, audit analytics, export reports. |

---

### 3. System Context Diagram (C4 Level 1)

The following diagram illustrates how Velan Production sits between users and external software systems:

```mermaid
flowchart TD
    subgraph Users ["Shop Floor & Engineering Users"]
        U_Admin["Admin"]
        U_PM["Project Manager (PM)"]
        U_Design["Designer"]
        U_Op["Shop Floor Operator"]
        U_QC["Quality Control Inspector"]
        U_Mgmt["Management / Executive"]
    end

    subgraph Boundaries ["Velan Production Ecosystem"]
        VP["<b>Velan Production System</b><br/>(Modular Monolith - Spring Boot 3 / Java 21)"]
        DB[("PostgreSQL Database<br/>(Neon Serverless)")]
        R2[("Cloudflare R2<br/>(Object Storage)")]
    end

    subgraph External ["External Enterprise Systems"]
        RM["<b>RM System (V-SMART)</b><br/>Raw Material & Inventory<br/><i>(Unchanged - Read-Only)</i>"]
        ECR["<b>ECR System</b><br/>Engineering Change Request<br/><i>(Unchanged - HTTP Intake)</i>"]
    end

    U_Admin -->|System administration & config| VP
    U_PM -->|Work orders, dispositions, dispatch clearance| VP
    U_Design -->|BOMs, tolerances, drawings| VP
    U_Op -->|Operation pickup, operator measurements| VP
    U_QC -->|QC measurements, pass/fail verdicts| VP
    U_Mgmt -->|Read-only dashboards & reports| VP

    VP <-->|Read & Write transactional state| DB
    VP -->|Generate Pre-signed URLs / Metadata| R2
    Users -.->|Direct upload/download via Pre-signed URL| R2

    VP -->|HTTPS GET: Fetch material issue & DC status<br/>(Read-Only Service Account)| RM
    VP -->|HTTPS POST: Dispatch non-conformance outbox events<br/>(Transactional Outbox)| ECR

    classDef user fill:#e0f2fe,stroke:#0369a1,stroke-width:2px,color:#0c4a6e;
    classDef vp fill:#dbeafe,stroke:#1d4ed8,stroke-width:3px,color:#1e3a8a;
    classDef ext fill:#fef3c7,stroke:#d97706,stroke-width:2px,color:#78350f;
    classDef store fill:#f1f5f9,stroke:#475569,stroke-width:2px,color:#1e293b;

    class U_Admin,U_PM,U_Design,U_Op,U_QC,U_Mgmt user;
    class VP vp;
    class RM,ECR ext;
    class DB,R2 store;
```

#### Diagram Explanation in Simple Language
1. **Users** access the Velan Production System via a responsive web client. Based on their role (Admin, PM, Designer, Operator, QC, Management), they execute authorized operations such as picking up an operation, logging measurements, evaluating quality, or issuing disposition orders.
2. **Velan Production System** acts as the central execution brain. It processes commands, validates business rules, computes tolerances, and coordinates state transitions.
3. **PostgreSQL on Neon** stores all relational data: purchase orders, subcontracts, products, BOMs, individual pieces, operations, measurements, audit logs, and outbox messages.
4. **Cloudflare R2** stores binary assets (engineering CAD files, component technical drawings, material test certificates, visual defect photographs). Uploads and downloads bypass the application server using secure, short-lived pre-signed URLs to preserve backend memory and bandwidth.
5. **RM (V-SMART)** provides raw material availability and DC information. Velan Production reads this data over secure HTTPS using a dedicated, read-only service account. Production never writes to RM.
6. **ECR System** receives non-conformance records when an engineering deviation requires structural review. Production dispatches these records asynchronously via an internal Transactional Outbox.

---

### 4. Enterprise Data Ownership Matrix

To ensure data integrity and avoid duplicate sources of truth across systems, data ownership is partitioned as follows:

| Business Domain | Master System (Owner) | Velan Production Access | Description & Guardrails |
| :--- | :--- | :--- | :--- |
| **Raw Material Master & Stock** | **RM (V-SMART)** | Read-Only (Synchronized via API) | RM maintains warehouse stock, stock allocations, GRNs, and mill heat certificates. Velan Production never updates raw material balances. |
| **Material Issuance Status** | **RM (V-SMART)** | Read-Only (Readiness Gate) | RM indicates whether raw material for a specific Subcontract (SC) / component has been physically issued. This acts as a hard readiness gate before pieces can enter production. |
| **Delivery Challan (DC) Creation & Inward** | **RM (V-SMART)** | Read-Only (Tracking Status) | When pieces go to external heat treatment or plating vendors, RM generates and receipts the official DC. Velan Production tracks the vendor operational stage and links RM's DC number, but does not own DC creation. |
| **PO & Subcontract (SC) Production Order** | **Velan Production** | Read / Write (System of Record) | Velan Production ingests purchase orders and subcontracts, assigning internal tracking sequences, target dates, and status progressions. |
| **BOM, Components & Sets** | **Velan Production** | Read / Write (System of Record) | Defines the parent product, sub-assemblies, individual component parts, quantities per set, and component-to-set correlation rules. |
| **Piece-Level Identity & Serial Numbers** | **Velan Production** | Read / Write (System of Record) | Velan Production generates unique serial numbers for each individual physical component piece, tracking it from raw stock cutting to final packing. |
| **Process Routing & Operations** | **Velan Production** | Read / Write (System of Record) | Defines sequence templates, standard operations, extra operations added by PM, operation states, and station assignments. |
| **Measurements & Tolerances** | **Velan Production** | Read / Write (System of Record) | Stores static dimensional rules, dynamic calculated tolerances based on mating parts, dual operator/QC measured values, and pass/fail verdicts. |
| **Shop-Floor Dispositions & Scraps** | **Velan Production** | Read / Write (System of Record) | PM dispositions (Continue, Extra Process, Return to Previous Op, Reject & Scrap) and parent-child linkage to replacement pieces are fully managed here. |
| **Engineering Change Requests (ECR)** | **ECR System** | Write via Outbox / Read Status | ECR owns the formal engineering deviation workflow. Production creates exception tickets in ECR via outbox and checks the concession outcome. |
| **Billing, Invoicing & Financials** | **External ERP / Accounts** | None (Out of Scope) | Invoicing and financial accounting are completely outside the boundaries of Velan Production. |
| **MERC & Executive Global Dashboards** | **MERC System** | Deferred to Phase 51 | Cross-plant enterprise metrics and centralized reporting integration are deferred to Phase 51. |

---

### 5. Boundary Guardrails and Invariants

1. **Read-Only RM Boundary:**
   Under no circumstances will Velan Production issue an `INSERT`, `UPDATE`, or `DELETE` request, directly or indirectly, to the RM (V-SMART) database or API. All interactions are non-mutating HTTP GET queries authenticated via a scoped service account token.
2. **Asynchronous ECR Boundary:**
   Velan Production does not perform synchronous blocking HTTP calls during user transactions to submit exceptions to ECR. If ECR is temporarily offline or slow, shop-floor operations must not fail or hang. Exception submissions must be committed locally to an `outbox_events` table and delivered by a reliable background worker with exponential backoff.
3. **No Direct Inventory Accounting:**
   Velan Production does not decrement raw material stock upon scrap, nor does it create vendor inward goods receipts upon DC return. It simply records the shop-floor operational reality and provides reconciled audit trails for inventory personnel operating RM.
4. **No Direct File Streaming Through Spring Boot:**
   High-resolution drawing files, CAD models, and inspection images are not buffered through the application heap. The Spring Boot backend acts strictly as an authorization and URL-signing authority, generating pre-signed URLs with constrained expiration (15 minutes) for direct client-to-R2 transport.
