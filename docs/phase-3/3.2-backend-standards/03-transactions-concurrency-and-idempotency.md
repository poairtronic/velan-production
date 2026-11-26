# Part 3.2 — Backend Architecture and Coding Standards
## Document 03: Transactions, Concurrency Control, and Idempotency Policy

---

### 1. Confirmed Rules (Frozen Baseline)
* **Short Transaction Lifecycles (`ADR-01`):** Database transactions must be kept as short as possible to minimize lock holding times and preserve connection availability on Neon PostgreSQL.
* **Prohibition of External Calls in Transactions (`ARCH-14`):** Network calls to RM (V-SMART), ECR, Cloudflare R2, or SMTP must **never** execute within an active database transaction.
* **Outbox Pattern for External Mutations (`ADR-02, D-13`):** Outbound communication with ECR must route through an atomic local PostgreSQL `outbox_events` table within the originating business transaction.
* **Optimistic Locking by Default (`Phase 2 Data Model`):** Concurrent user edits to pieces, operations, and subcontracts must be governed by optimistic locking (`@Version`).

---

### 2. Proposed Design: Transaction Boundaries & Concurrency

#### 2.1 Transaction Boundaries & Layering
1. **Service Layer Demarcation:** The `@Transactional` annotation is permitted strictly on methods of domain and application services (or public facade implementations).
2. **Prohibited Placement:**
   - **Controllers:** Controllers must never be annotated with `@Transactional`. Holding database transactions open during JSON parsing or network serialization exhausts connection pools.
   - **Repositories:** Repositories must rely on transactions initiated by the calling service layer.
3. **Read-Only Transaction Optimization:**
   - All query and lookup methods must declare `@Transactional(readOnly = true)`.
   - **Technical Mechanism:** Sets Hibernate session flush mode to `FlushMode.MANUAL`, bypassing snapshot dirty-checking across entity graphs, reducing memory footprint and providing hints to Neon PostgreSQL.

---

#### 2.2 Optimistic Locking with `@Version` & Conflict Handling
1. **Entity Declaration:**
   All mutable aggregate roots (`subcontracts`, `subcontract_products`, `assembled_sets`, `pieces`, `piece_operations`) include an optimistic version attribute:
   ```java
   @Version
   @Column(name = "version", nullable = false)
   private Integer version;
   ```
2. **Conflict Interception & Response Contract:**
   When two operators or automated tasks attempt concurrent updates on the same row, the second commit fails with Hibernate's `OptimisticLockException` (wrapped by Spring as `OptimisticLockingFailureException`).
3. **Global Controller Advice Mapping:**
   The exception is intercepted by `GlobalExceptionHandler` and translated into HTTP 409 Conflict:
   ```json
   {
     "status": 409,
     "errorCode": "CONCURRENCY_MODIFICATION_CONFLICT",
     "message": "The target piece operation was modified concurrently by another station. Please refresh and retry.",
     "timestamp": "2026-11-26T10:00:00Z",
     "path": "/api/v1/pieces/SC-2579-APG-001/operations/10/submit",
     "retryable": true
   }
   ```

---

#### 2.3 Row-Lock Ordering Rule (Deadlock Prevention)
When a complex operation requires pessimistic locking on multiple rows (e.g. bulk allocating multiple pieces to an assembled set or re-sequencing operations):
1. **Mandatory Sorting Rule:** All target row IDs must be sorted in ascending lexicographical/numerical order (`ORDER BY id ASC`) prior to acquiring locks.
2. **Mathematical Guarantee:** Acquiring locks in an identical global sequence across all threads mathematically prevents cyclic wait conditions, eliminating database deadlocks.

---

#### 2.4 High-Concurrency `SKIP LOCKED` Worker Pattern
Background pollers (ECR transactional outbox worker, RM sync scheduler) must not lock table partitions or contend with API requests:
1. **Prescriptive SQL Query:**
   ```sql
   SELECT * FROM outbox_events 
   WHERE status = 'PENDING' 
     AND (next_retry_at IS NULL OR next_retry_at <= NOW()) 
   ORDER BY created_at ASC 
   LIMIT :batchSize 
   FOR UPDATE SKIP LOCKED;
   ```
2. **Operational Behavior:**
   - Worker threads instantly lock and fetch up to `:batchSize` pending rows.
   - Any rows currently locked by another worker instance are skipped immediately without blocking.
   - Multiple background threads or scaled container instances can poll the table concurrently with zero lock contention.

---

#### 2.5 Idempotency Key Policy
To ensure that network retries or double-clicks do not cause duplicate piece generation, duplicate ECR tickets, or duplicate scrap records:
1. **Inbound REST Idempotency:**
   - Sensitive mutating endpoints (e.g. `POST /api/v1/sc/import`, `POST /api/v1/exceptions/{id}/disposition`) accept an optional or mandatory `Idempotency-Key: <UUID>` HTTP header.
   - An interceptor records the key in an `idempotency_keys` table within the same transaction. If a key is duplicated within 24 hours, the cached response is returned immediately.
2. **ECR Outbox Idempotency:**
   - The primary key UUID of `outbox_events` is transmitted to ECR as `Idempotency-Key` or embedded in the payload description (`Ref PRD-EXC-000123`).
   - ECR de-duplicates incoming calls based on this identifier (`09_Integration_Specification.md:46`).

---

### 3. Rule: No External Network Calls Inside Database Transactions

#### 3.1 Architectural Mandate
Network calls to external APIs or remote services (RM REST API, ECR intake, Cloudflare R2, SMTP gateways) must **never** occur within a database transaction. Network delays, retries, and socket timeouts keep database connections open, rapidly exhausting the HikariCP pool and starving shop-floor operations.

#### 3.2 Approved Architectural Alternatives
When an external system interaction is required, developers must choose strictly from three approved patterns:

| Pattern | Intended Use Case | Architectural Flow |
|---|---|---|
| **1. Transactional Outbox** | Sending data to external systems (ECR exceptions, scrap notices) | 1. Save business entity + outbox event row in single local DB transaction.<br/>2. Transaction commits immediately.<br/>3. Background poller (`SKIP LOCKED`) reads outbox and invokes external REST API. |
| **2. Read-Outside-Then-Write**| Ingesting external data (RM polling, sync refresh) | 1. Invoke external REST API outside any transaction (holding zero DB connections).<br/>2. Receive and parse payload.<br/>3. Open short, local `@Transactional` method to compute diff and persist. |
| **3. Post-Commit Event Listener** | Fire-and-forget notifications (SSE push, SMTP dispatch) | 1. Service publishes domain event via `ApplicationEventPublisher`.<br/>2. Listener executes inside `@TransactionalEventListener(phase = AFTER_COMMIT)`.<br/>3. External call executes only after DB transaction is committed. |

---

### 4. Retry Policy for Transient Failures & Outbox Resilience

1. **Transient Failures Defined:** Temporary network disconnects, HTTP 502/503/504 gateway timeouts from ECR, and Neon compute wake-up pauses.
2. **Exponential Backoff Formula:**
   $$\text{Delay}(n) = \min\left(\text{InitialDelay} \times \text{Multiplier}^{(n-1)} + \text{Jitter}, \text{MaxDelay}\right)$$
   - `InitialDelay`: 2 seconds
   - `Multiplier`: 2.0
   - `MaxDelay`: 60 seconds
   - `MaxAttempts`: 5 attempts
3. **Dead-Letter Handling:**
   - If an outbox event fails 5 consecutive times, its status transitions to `DEAD_LETTER`.
   - The error stack trace and timestamp are recorded in `outbox_events.last_error`.
   - Generates an administrative alert on the Admin reconciliation dashboard.

---

### 5. Scheduled Background Job Safety (Scheduler Rules)

1. **Single-Instance Baseline:** The application is deployed as a single Render Web Service container in baseline.
2. **Multi-Instance Concurrency Guard (ShedLock):**
   - To remain 100% concurrency-safe if multiple container instances are started (e.g. during zero-downtime rolling deploys), all `@Scheduled` tasks must use **ShedLock with Neon PostgreSQL**:
   ```java
   @Scheduled(cron = "0 */5 * * * *")
   @SchedulerLock(name = "RM_SYNC_JOB", lockAtLeastFor = "PT30S", lockAtMostFor = "PT4M")
   public void executeRMSync() {
       rmSyncService.pollRequisitions();
   }
   ```
   - ShedLock records active locks in table `shedlock`, guaranteeing that exactly one container executes the scheduled task at any time.

---

### 6. Technical Assumptions
1. **ASSUMPTION:** PostgreSQL row-level locks acquired with `FOR UPDATE SKIP LOCKED` are released immediately upon transaction commit or rollback, with zero orphaned locks.
2. **ASSUMPTION:** Neon serverless compute suspend does not abort in-flight active transactions.

---

### 7. Open Questions
* **O-TX-01:** Should outbox events that reach `DEAD_LETTER` generate automatic email notifications to system administrators in addition to appearing in the UI reconciliation screen? (Documented in Document 06).
