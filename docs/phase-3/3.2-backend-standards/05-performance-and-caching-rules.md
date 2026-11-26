# Part 3.2 — Backend Architecture and Coding Standards
## Document 05: Performance Optimization, Database Tuning, and In-Process Caching

---

### 1. Confirmed Rules (Frozen Baseline)
* **No Redis Caching (`D-13, ADR-03`):** Caching is strictly in-process using Caffeine on the JVM heap. No external cache infrastructure is permitted.
* **Derived Quantities Invariant (`BR-013, BR-068`):** Piece counts, operation tallies, and completion states must be derived directly from transactional database records, never stored in manual buckets or retrieved from stale caches.
* **Server-Sent Events for Real-Time Updates (`ADR-06`):** Real-time dashboard and terminal updates route unilaterally via Server-Sent Events (SSE). No full-duplex WebSockets.
* **Serverless Connection Discipline (`ADR-08`):** HikariCP connection pools on Render must be tuned conservatively to prevent connection saturation against Neon serverless PostgreSQL.

---

### 2. Proposed Design: High-Performance Data Access & JPA Rules

#### 2.1 Mandatory `open-in-view = false`
* **Configuration:** `spring.jpa.open-in-view=false` is enforced across all environments.
* **Architectural Rationale:** Prevents Spring Boot from keeping the database connection open during HTTP response serialization in controllers. Eliminates hidden $N+1$ lazy-loading database queries during Jackson JSON rendering.

#### 2.2 Lazy Loading by Default
* All relationship annotations (`@ManyToOne`, `@OneToMany`, `@OneToOne`, `@ManyToMany`) must explicitly declare `fetch = FetchType.LAZY`.
* Eager fetching (`FetchType.EAGER`) is strictly prohibited across all domain entities.

#### 2.3 Elimination of N+1 Queries
To prevent catastrophic query flooding across worklists and dashboards, data retrieval must use one of three approved strategies:
1. **Explicit `JOIN FETCH` in JPQL:**
   ```java
   @Query("SELECT p FROM Piece p JOIN FETCH p.operations WHERE p.scProductId = :scProductId")
   List<Piece> findAllWithOperations(@Param("scProductId") UUID scProductId);
   ```
2. **Spring Data JPA Entity Graphs:**
   ```java
   @EntityGraph(attributePaths = {"operations", "allocations"})
   Optional<Piece> findWithDetailsById(UUID id);
   ```
3. **Record DTO Projections:**
   Direct constructor expressions selecting only required fields into immutable records:
   ```java
   @Query("SELECT new com.velan.production.execution.api.query.StationWorklistEntryRecord(" +
          "p.serialNumber, op.operationName, op.status, op.readyAt) " +
          "FROM PieceOperation op JOIN op.piece p WHERE op.stationId = :stationId")
   List<StationWorklistEntryRecord> findStationWorklist(@Param("stationId") UUID stationId);
   ```

---

#### 2.4 Batching for Bulk Piece Generation
When an SC is approved, generating hundreds of pieces and thousands of routing operations must not execute single-row `INSERT` statements:
1. **Hibernate JDBC Batch Configuration:**
   ```properties
   spring.jpa.properties.hibernate.jdbc.batch_size=50
   spring.jpa.properties.hibernate.order_inserts=true
   spring.jpa.properties.hibernate.order_updates=true
   spring.jpa.properties.hibernate.jdbc.batch_versioned_data=true
   ```
2. **Execution Standard:** Ingestion loops must call `pieceRepository.saveAll(pieces)` in batches of 50, allowing the JDBC driver to bundle 50 inserts into a single network transmission to Neon.

---

#### 2.5 Prescriptive Pagination Limits
All list, search, and history endpoints must enforce pagination:
* **Default Page Size:** 20 records.
* **Maximum Allowable Page Size:** 100 records.
* **Mandatory Index Sorting:** Every paginated query must enforce sorting on an indexed column (e.g. `ORDER BY op.readyAt ASC`, `ORDER BY p.id ASC`). Unsorted queries are rejected.

---

### 3. In-Process Caffeine Caching Catalogue

Caching on the JVM heap provides sub-microsecond retrieval for static and slow-moving master data without external infrastructure costs.

#### 3.1 Named Cache Catalogue and Eviction Policies

| Cache Name | Key | Value | Max Entries | TTL (Expiry) | Eviction Trigger(s) |
|---|---|---|---|---|---|
| **`permissions`** | `userId` | `Set<String>` (Grants) | 500 users | 15 minutes | Explicit eviction on `user_permission_overrides` update or role reassignment. |
| **`processTemplates`** | `templateId` | `ProcessTemplateDTO` | 200 templates | 60 minutes | Explicit eviction on new template version activation. |
| **`tolerances`** | `toleranceKey` | `ToleranceRulesDTO` | 1,000 rules | 30 minutes | Explicit eviction on Design tolerance sheet approval (`PM_DISPOSITION_CONTINUE` / revision). |
| **`masterData`** | `entityCode` | Machine / Dept DTO | 100 entries | 120 minutes | Infrequent updates; manual eviction via Admin action. |

#### 3.2 What Must NEVER Be Cached (Strict Caching Prohibitions)
To prevent corrupted shop-floor decisions and race conditions, the following data **must never be stored in Caffeine caches**:
1. **Physical Piece Status:** `pieces.status` must always be read fresh from Neon PostgreSQL.
2. **Operation Status & Measurements:** `piece_operations.status` and `operation_measurements` must never be cached.
3. **Quantity Buckets & Completion Counts:** Target sets, packed sets, and completed piece counts must always be computed transactionally.
4. **QC Inspection Verdicts:** Active inspection states must never be cached.

---

### 4. Server-Sent Events (SSE) Performance and Heartbeat Architecture

1. **Connection Limits:**
   - Maximum 1 active SSE connection per browser tab.
   - Maximum 3 concurrent SSE connections per authenticated user account.
2. **Periodic Heartbeat Stream:**
   - The SSE emitter publishes a comment heartbeat line (`:heartbeat\n\n`) every **15 seconds**.
   - **Rationale:** Prevents Render reverse proxies, cloud load balancers, and corporate firewalls from terminating idle HTTP/2 streaming connections (Render enforces a 100-second idle timeout).
3. **Resource Leak Prevention:**
   All `SseEmitter` instances register explicit lifecycle hooks:
   ```java
   emitter.onCompletion(() -> emitterRegistry.remove(userId, emitter));
   emitter.onTimeout(() -> {
       emitter.complete();
       emitterRegistry.remove(userId, emitter));
   });
   emitter.onError(e -> {
       emitter.completeWithError(e);
       emitterRegistry.remove(userId, emitter));
   });
   ```

---

### 5. HikariCP Connection Pool Guidance for Neon PostgreSQL

Neon Serverless PostgreSQL auto-scales compute and pauses when idle. HikariCP must be configured to accommodate cold-starts without connection exhaustion:

```properties
# Maximum active connections per container instance (Sized to prevent Neon exhaustion)
spring.datasource.hikari.maximum-pool-size=10

# Minimum idle connections kept warm
spring.datasource.hikari.minimum-idle=2

# Maximum wait time to acquire connection (Accommodates Neon compute wake-up)
spring.datasource.hikari.connection-timeout=30000

# Idle connection lifespan before release
spring.datasource.hikari.idle-timeout=600000

# Maximum lifespan of a connection in the pool (30 minutes)
spring.datasource.hikari.max-lifetime=1800000

# Connection leak detection threshold (Logs warning if connection held > 10s)
spring.datasource.hikari.leak-detection-threshold=10000
```

---

### 6. Technical Assumptions
1. **ASSUMPTION:** JVM heap allocation of 1024 MB (`-Xmx1024m`) on Render Standard instances provides sufficient memory for all 4 Caffeine caches combined (< 10 MB total cache heap consumption).
2. **ASSUMPTION:** Neon PgBouncer pooled endpoint multiplexes 10 HikariCP connections per container without exceeding Neon tier connection limits.

---

### 7. Open Questions
* **O-PERF-01:** If shop-floor terminal count expands past 100 tablets, should SSE streaming be offloaded to a lightweight dedicated gateway or maintained within the modular monolith container? (Documented in Document 06).
