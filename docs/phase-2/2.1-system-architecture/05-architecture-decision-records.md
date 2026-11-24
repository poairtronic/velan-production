# Part 2.1 – System Architecture Design
## Document 05: Architecture Decision Records (ADRs)

This document formalizes the fundamental technical decisions governing the Velan Production platform. Each record details the context, chosen decision, rationale, evaluated alternatives, system impact, and current approval status.

---

### ADR-01: Adoption of Modular Monolith Architecture over Microservices

* **Status:** Proposed (Pending Review)
* **Context:**
  Velan Production requires tracking complex industrial components across multi-stage operations, piece state transitions, dual operator/QC measurements, PM failure dispositions, and BOM set completeness checks. The technology stack mandates Java 21, Spring Boot, PostgreSQL on Neon, and Docker on Render without distributed message brokers.
* **Decision:**
  Build Velan Production as a **Modular Monolith** in a single deployable artifact, organized strictly around discrete business modules with package-private boundaries and well-defined API contracts.
* **Reason:**
  1. Manufacturing execution requires absolute transactional consistency (e.g., atomically updating piece status, creating replacement pieces, and inserting outbox events). A modular monolith achieves this via standard local ACID database transactions, avoiding 2-Phase Commit (2PC) or Saga orchestrations.
  2. Eliminates network latency, serialization overhead, and partial network failure handling between internal domain services.
  3. Significantly reduces operational cost and infrastructure complexity for Render deployment.
* **Alternatives Considered:**
  * *Microservices Architecture:* Rejected due to severe operational complexity, distributed transaction overhead, network latency on the shop floor, and deployment cost violations.
  * *Layered/Traditional Monolith (N-Tier without module boundaries):* Rejected due to the risk of "spaghetti code", high coupling between entities, and unmaintainable long-term architecture.
* **Impact:**
  Developers must respect module boundaries and use public API interfaces or domain events. Tooling (such as ArchUnit) will be configured in later phases to prevent illegal cross-module dependencies.

---

### ADR-02: Transactional Outbox Pattern for ECR Integration (No Message Broker)

* **Status:** Proposed (Pending Review)
* **Context:**
  When a piece is rejected or requires an engineering concession, an exception ticket must be sent to the external ECR system. The technology direction explicitly prohibits external message brokers (RabbitMQ, Kafka) and distributed caches (Redis). If the external ECR API is temporarily slow or unreachable, shop-floor PM decisions must not fail or hang.
* **Decision:**
  Implement the **Transactional Outbox Pattern** utilizing a dedicated PostgreSQL table (`outbox_events`) within the core database, polled by a Spring `@Scheduled` background worker that transmits payloads over HTTPS to ECR with unique idempotency keys.
* **Reason:**
  1. Guarantees "at-least-once" delivery of exception notifications to ECR without requiring an external broker.
  2. Commits the outbox record in the same database transaction as the PM scrap/disposition action, guaranteeing 100% data consistency.
  3. Isolates shop-floor operators and PMs from external network latencies or outages of the ECR system.
* **Alternatives Considered:**
  * *Synchronous REST Call during PM Action:* Rejected because an ECR network timeout or outage would abort the PM's shop-floor transaction or lock the browser.
  * *External Message Broker (RabbitMQ / Kafka):* Explicitly excluded by approved technology direction.
* **Impact:**
  ECR receives events asynchronously. The background outbox processor handles retry backoff and dead-letter queueing internally.

---

### ADR-03: In-Process Caffeine Cache over Distributed Cache (No Redis)

* **Status:** Proposed (Pending Review)
* **Context:**
  Certain queries—such as RM material issuance status, process sequence templates, static tolerance bands, and role permissions—are read frequently during shop-floor shifts. The approved direction strictly mandates **No Redis**.
* **Decision:**
  Use **Caffeine Cache** directly within the Spring Boot JVM heap memory with explicit sizing bounds and time-to-live (TTL) expiration policies.
* **Reason:**
  1. Sub-microsecond read performance without network overhead.
  2. Fully compliant with the approved constraint of zero external cache servers.
  3. Automatic bounded eviction (LRU) prevents Out-Of-Memory (OOM) errors in Render containers.
* **Alternatives Considered:**
  * *Redis:* Explicitly prohibited by project technology direction.
  * *No Caching (Direct Database and RM Queries Every Time):* Rejected due to excessive load on Neon Postgres and risk of rate-limiting/overloading the external RM (V-SMART) server.
* **Impact:**
  Cache memory is local to the JVM container. In multi-container environments, each container maintains its own cache, which is acceptable since cached data (process templates, tolerances) changes infrequently and carries short TTLs (5–60 minutes).

---

### ADR-04: Read-Only Service Account Connector for RM (V-SMART) System

* **Status:** Proposed (Pending Review)
* **Context:**
  Velan Production must verify whether raw material has been physically issued and track vendor delivery challan (DC) status from RM (V-SMART). RM must remain completely unchanged, and Production must not own inventory transactions or DC creation.
* **Decision:**
  Velan Production communicates with RM exclusively via HTTP GET requests authenticated through a dedicated **Read-Only Service Account** using JWT / API tokens. Velan Production will never execute write operations against RM.
* **Reason:**
  1. Preserves absolute system independence and data integrity for RM.
  2. Enforces the business boundary: RM remains the single source of truth for physical stock, heat numbers, and official DCs.
  3. Eliminates any possibility of accidental stock corruption or double-booking from the production execution layer.
* **Alternatives Considered:**
  * *Direct Shared Database Access (SQL views/cross-DB queries):* Rejected because it tightly couples the database schemas, bypasses RM domain rules, and creates severe security risks.
  * *Bi-Directional Read/Write REST Sync:* Rejected because the business rules state RM remains unchanged and Production does not own inventory.
* **Impact:**
  If an operator requires additional raw material due to scrap, the physical stock requisition must be initiated in RM by inventory personnel; Production then detects the issued status via read sync.

---

### ADR-05: Direct-to-Cloudflare R2 File Transfers via Pre-Signed URLs

* **Status:** Proposed (Pending Review)
* **Context:**
  High-resolution CAD files, technical drawings (PDF/DXF), and inspection photos must be uploaded and viewed by Designers, PMs, Operators, and QC. Passing large binary files through the Spring Boot backend consumes heap memory and network bandwidth on Render.
* **Decision:**
  Implement pre-signed URL generation on the backend. The React browser client uploads and downloads binary assets directly to/from **Cloudflare R2** using short-lived (15-minute) pre-signed URLs. Spring Boot stores only file metadata and R2 object keys in PostgreSQL.
* **Reason:**
  1. Offloads high-bandwidth, high-memory I/O entirely from the application container.
  2. Cloudflare R2 provides an S3-compatible API with zero egress fees, significantly reducing operational expenses.
  3. Security is maintained because pre-signed URLs are cryptographically signed, role-authorized, and expire quickly.
* **Alternatives Considered:**
  * *Streaming Files Through Spring Boot Multipart Endpoints:* Rejected due to high memory consumption, container CPU load, and potential request timeout issues on Render.
  * *Storing Files as BYTEA / BLOB in PostgreSQL:* Rejected due to severe database bloat, backup slowness, and degraded query performance.
* **Impact:**
  The frontend client manages direct HTTP PUT/GET calls to Cloudflare R2. File upload confirmation endpoints are required on the backend to verify object existence before saving database records.

---

### ADR-06: Server-Sent Events (SSE) for Real-Time Shop-Floor Notifications

* **Status:** Proposed (Pending Review)
* **Context:**
  The shop floor requires timely awareness of events: when an operation fails QC, the PM must see it immediately; when RM issues material, the operator station must unlock; when an operation is submitted, the QC inspection queue must update.
* **Decision:**
  Adopt **Server-Sent Events (SSE)** over standard HTTPS (`/api/v1/notifications/stream`) to stream real-time domain notifications from the backend to the React frontend.
* **Reason:**
  1. Native browser support via the `EventSource` API with automatic reconnection and Last-Event-ID support.
  2. Unidirectional (server-to-client) communication is sufficient for notifications, avoiding the stateful connection complexity and heartbeat overhead of WebSockets.
  3. Works seamlessly through standard HTTP/2 proxies and Render load balancers without requiring custom WebSocket proxy upgrades.
  4. Lower resource consumption compared to short-interval client polling.
* **Alternatives Considered:**
  * *Short Polling (e.g. every 3 seconds):* Rejected due to excessive database and HTTP request volume across multiple shop-floor terminals.
  * *Full-Duplex WebSockets (STOMP/SockJS):* Rejected as unnecessary overkill since the client already issues commands via standard REST APIs; full-duplex socket overhead adds needless state management complexity.
* **Impact:**
  The frontend maintains a persistent SSE connection. Pushed events trigger invalidation of TanStack Query caches, prompting instantaneous UI background updates.

---

### ADR-07: In-Process Spring Application Events for Inter-Module Decoupling

* **Status:** Proposed (Pending Review)
* **Context:**
  Modules within the monolith must communicate without creating circular dependencies or direct class coupling (e.g., Execution must notify QC, QC must notify Exception, Exception must trigger Audit).
* **Decision:**
  Use Spring's built-in `ApplicationEventPublisher` to publish strongly-typed, immutable domain events across module boundaries.
* **Reason:**
  1. Cleanly decouples the publisher module from multiple listener modules.
  2. Supports `@TransactionalEventListener`, allowing side effects (audit logging, SSE push, outbox triggering) to execute strictly after the primary business transaction has committed successfully.
  3. Preserves testability by enabling modules to be unit-tested using event publisher mocks.
* **Alternatives Considered:**
  * *Direct Module-to-Module Service Invocations for Everything:* Rejected because it creates tight coupling, potential circular references, and makes module refactoring difficult.
* **Impact:**
  Domain events must be defined in public `api` packages. Developers must distinguish between synchronous within-transaction listeners and post-commit listeners.

---

### ADR-08: Unified Relational Data Storage on Serverless PostgreSQL (Neon) with Schema Isolation

* **Status:** Proposed (Pending Review)
* **Context:**
  Velan Production needs a robust, ACID-compliant database supporting relational integrity across the PO → SC → Product → BOM → Piece hierarchy, with optimistic/pessimistic locking, JSON document storage (for dynamic tolerance formulas and measurement payloads), and outbox reliability.
* **Decision:**
  Use a single managed **PostgreSQL** database on **Neon**, organizing module tables with clean naming prefixes (e.g. `ord_`, `eng_`, `exec_`, `qc_`, `exp_`, `vnd_`, `dsp_`, `sys_`) or dedicated PostgreSQL schemas within one database.
* **Reason:**
  1. Enables transactional joins where strictly needed (e.g., BOM completeness reports) while maintaining clear module table ownership.
  2. Neon provides automated branching, scaling, and zero-maintenance backups.
  3. Supports JSONB columns for flexible, versioned measurement logs alongside strict relational tables for state machines.
* **Alternatives Considered:**
  * *Separate Database per Module:* Rejected because it forces distributed transactions, eliminates cross-entity foreign key constraints, and dramatically increases hosting cost.
* **Impact:**
  Modules own their designated tables. No module may perform direct SQL joins or updates against tables owned by another module without going through that module's API or domain views.
