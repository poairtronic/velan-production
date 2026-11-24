# Part 2.1 – System Architecture Design
## Document 06: Traceability, Assumptions, and Review Checklist

### 1. Phase 1 Requirements Traceability & Conflict Verification

A rigorous cross-check of the **Part 2.1 System Architecture Design** against the frozen **Phase 1 Requirements** confirms 100% alignment with zero deviations or unauthorized scope changes:

| Phase 1 Requirement Specification | Part 2.1 Architectural Treatment | Status |
| :--- | :--- | :--- |
| **Six Specific Roles** (Admin, PM, Designer, Operator, QC, Management) | Enforced at Spring Security filter chain with discrete JWT claims and role-specific UI viewports in React. | **Aligned** |
| **Piece-Level Tracking** | `execution` module assigns unique UUIDs and physical serial numbers to every individual component piece. | **Aligned** |
| **PO → SC → Product → BOM → Piece Hierarchy** | Mirrored across `order`, `engineering`, and `execution` modules with strict relational integrity in Neon PostgreSQL. | **Aligned** |
| **Component-to-Set Relationship** (Multi-component sets) | `engineering` defines component set ratios; `dispatch` module governs set assembly completeness and allocation locks. | **Aligned** |
| **Product-Specific Process Templates** | `engineering` module maintains reusable and order-specific sequence templates with static/dynamic steps. | **Aligned** |
| **Static & Dynamic Tolerances** | Separate parameters engine in `engineering` and `quality` modules evaluating fixed limits and mathematical formula offsets. | **Aligned** |
| **Dual Operator & QC Measurements** | `operation_measurements` table retains immutable records tagged by role (`OPERATOR` vs `QC_INSPECTOR`). | **Aligned** |
| **PM-Controlled QC Failure Decisions** | QC failure places piece on hold; `exception` module limits resolution decisions strictly to the PM role. | **Aligned** |
| **Extra Process & Return to Earlier Op** | `exception` module dynamically injects remediation steps or rewinds operation sequence pointers. | **Aligned** |
| **Scrap & Linked Replacement Pieces** | Scrapping a piece triggers parent-child lineage creation and spawns a replacement piece within the same atomic transaction. | **Aligned** |
| **RM Material-Issued Readiness Gate** | `integration` module queries RM V-SMART read-only API; pieces are blocked from production until RM reports material issued. | **Aligned** |
| **Vendor Processing & RM DC Sync** | `vendor` module tracks vendor processing stages and links RM Delivery Challan numbers without owning inventory movements. | **Aligned** |
| **Packing & Dispatch Gating** | `dispatch` module prevents packing or shipping incomplete BOM sets or pieces with pending/failed operations. | **Aligned** |
| **Product & SC Completion Lifecycle** | Completion cascades from pieces to BOM sets to products to the overall Subcontract (SC). | **Aligned** |
| **Full Audit History** | `audit` module listens to all domain events and writes tamper-evident audit records. | **Aligned** |
| **Real-Time Notifications** | Server-Sent Events (SSE) stream backend domain events directly to browser viewports. | **Aligned** |
| **No Redis, No External Broker, Modular Monolith** | In-process Caffeine cache, Transactional Outbox table in PostgreSQL, single Spring Boot deployable container. | **Aligned** |
| **No Billing, No Inventory Ownership** | Explicitly excluded from domain boundaries; owned by external systems. | **Aligned** |
| **MERC Dashboard Deferred** | Confirmed deferred to Phase 51. | **Aligned** |

**Conflict Assessment Result:**
**Zero conflicts detected.** No business rules have been relaxed, simplified, or removed.

---

### 2. Architectural Assumptions

1. **RM (V-SMART) API Contract:**
   It is assumed that RM exposes a modern HTTPS REST endpoint accessible within the company network or via a secure reverse proxy, accepting a service-account Bearer token and returning JSON payloads with material issue status, heat codes, and DC numbers.
2. **ECR Intake API Contract:**
   It is assumed that ECR provides an HTTPS intake endpoint capable of receiving JSON non-conformance payloads and that it respects an `Idempotency-Key` HTTP header to prevent duplicate ticket creation during network retries.
3. **Single Plant Context for Initial Phases:**
   The current architecture focuses on single-plant manufacturing execution. Multi-plant aggregation and cross-facility dashboards are deferred to Phase 51 (MERC).
4. **Barcode / QR Scanner Hardware:**
   Shop-floor barcode and 2D QR scanners operate in standard Human Interface Device (HID) keyboard-wedge mode, inputting serial numbers directly into the active browser field, or interact via HTML5 camera-based scanning APIs.
5. **Shop-Floor Network Environment:**
   The shop floor provides continuous Wi-Fi/LAN connectivity. The architecture uses an online-first model with optimistic locking and automatic SSE reconnection. (An offline-capable local client buffer is not required under Phase 1 requirements).
6. **Cloudflare R2 Access:**
   Cloudflare R2 credentials (Access Key ID, Secret Key, Account Endpoint) will be available in the Render environment variables, allowing the AWS S3 SDK in Spring Boot to generate pre-signed PUT and GET URLs.

---

### 3. Open Questions for User / PM Review

Before proceeding to subsequent design parts, please review and clarify the following practical domain questions:

1. **RM Material Issuance Granularity:**
   * *Question:* Does RM indicate material issuance at the overall Subcontract (SC) level, at the specific component part level (e.g., Flange vs Stem), or against specific raw material heat/bar IDs?
   * *Architecture Assumption:* The architecture currently models issuance at the **component part level** within an SC, automatically capturing the associated mill heat code for metallurgical traceability.
2. **Drawing File Size Limits:**
   * *Question:* What is the anticipated maximum file size for engineering drawings and CAD files uploaded by Designers?
   * *Architecture Assumption:* We have set an initial threshold of **50 MB** per document for direct pre-signed uploads to Cloudflare R2.
3. **Vendor Delivery Challan (DC) Association:**
   * *Question:* When sending components out for external operations (e.g., heat treatment, plating), does RM generate one DC per individual piece or one DC covering a batch of pieces belonging to the same SC?
   * *Architecture Assumption:* The architecture supports a **many-to-one** relationship: multiple piece operations can link to a single RM DC tracking number.
4. **Management Reporting Refresh Cadence:**
   * *Question:* Should Management Dashboard metrics be aggregated in real-time on every state transition, or is an in-memory/materialized summary refreshed every 1–5 minutes sufficient?
   * *Architecture Assumption:* Real-time transactional counters for critical bottleneck alerts, with lightweight cached queries (TTL: 2 minutes) for heavy historical aggregation charts.

---

### 4. Part 2.1 Review Checklist

Use this checklist to verify that Part 2.1 meets all architectural standards before granting approval to advance to Part 2.2:

- [ ] **System Boundaries Validated:** Are the boundaries between RM (V-SMART), Velan Production, and ECR accurately delineated?
- [ ] **Data Ownership Confirmed:** Does the data ownership matrix correctly assign inventory to RM, production execution to Velan Production, and deviation approvals to ECR?
- [ ] **Modular Monolith Structure Approved:** Is the 10-module backend layout comprehensive, and are the package-private encapsulation rules clear?
- [ ] **Inter-Module Communication Accepted:** Are in-process Spring Application Events and synchronous Java API interfaces acceptable for decoupling?
- [ ] **Technology Constraints Honored:** Has the architecture strictly avoided Redis, RabbitMQ, Kafka, and microservices?
- [ ] **Outbox Pattern Approved:** Does the database-backed Transactional Outbox satisfy asynchronous ECR dispatch requirements?
- [ ] **Storage Strategy Approved:** Is the direct-to-Cloudflare R2 pre-signed URL approach acceptable for technical drawings and attachments?
- [ ] **Notification Pattern Approved:** Does Server-Sent Events (SSE) adequately satisfy real-time shop-floor operational requirements?
- [ ] **ADRs Reviewed:** Are Architecture Decision Records ADR-01 through ADR-08 complete and sound?
- [ ] **Zero Code / Migration Check:** Has the repository remained clean of premature application code, database migrations, and API implementations?
