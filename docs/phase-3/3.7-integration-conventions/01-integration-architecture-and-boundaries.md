# Part 3.7 — Integration Adapter Conventions
## Document 01: Integration Architecture, Anti-Corruption Layer, and System Boundaries

---

### 1. Confirmed Rules

#### 1.1 Package Structure and Anti-Corruption Layer (ACL)
Integration with external systems is strictly partitioned into dedicated adapter packages within the modular monolith:
* `com.velan.production.integration.rm` (RM / V-SMART read connector)
* `com.velan.production.integration.ecr` (ECR outbox dispatcher)

1. **Anti-Corruption Layer (ACL) Invariant:**
   * External schema definitions, upstream HTTP response DTOs, and proprietary external field names (e.g. RM table fields, ECR JSON payloads) must **never leak into the core domain model**.
   * Upstream representations are restricted entirely to the `integration.*` packages.
   * Dedicated translator classes (e.g. `RmContractTranslator`, `EcrPayloadMapper`) map external data into clean, internal domain objects, commands, or application events.
2. **Domain Isolation:**
   * Domain aggregate entities (`SalesContract`, `Piece`, `QCException`, `Operation`) are strictly forbidden from importing or referencing classes under `com.velan.production.integration.*`.

#### 1.2 Non-Negotiable Operational Boundaries
In accordance with `BR-020, BR-039, BR-062, BR-067`, Decision `D-09`, and Decision `D-11`:

| System | What Velan Production MAY Do | What Velan Production MUST NEVER Do |
|:---|:---|:---|
| **Raw Material System (RM – V-SMART)** | • Query approved Size Cards and product quantities.<br/>• Query raw material issue status (`material_issued` flag).<br/>• Read vendor Delivery Challan (DC) outward tracking.<br/>• Read vendor DC inward return quantities.<br/>• Read ECR replacement material issuance status. | • **NEVER write, update, or post anything to RM (BR-067).**<br/>• NEVER create, dispatch, or inward vendor Delivery Challans.<br/>• NEVER adjust raw material inventory or bin ledgers.<br/>• NEVER send quality exception tickets to RM (INT-003/004/005 removed per D-09). |
| **Engineering Change Request (ECR)** | • Send scrap and rework exception reports via asynchronous transactional outbox (`POST /api/internal/rm-intake`).<br/>• Capture and persist returned ECR report numbers (`AGIPL-YYYY-ERR-XXXXX`). | • NEVER execute synchronous HTTP calls during shop-floor PM disposition transactions.<br/>• NEVER auto-resend an exception after an ambiguous socket timeout (BR-062).<br/>• NEVER import or process ECR defect costing or vendor chargeback data. |

#### 1.3 Strict Database Transaction Isolation (Zero In-Transaction HTTP Calls)
* **Core Reliability Invariant:** An outbound external network call (HTTP GET or POST) must **NEVER** be executed within an open database transaction (`@Transactional`).
* *Failure Mode Prevented:* External network latency, connection timeouts, or upstream outages holding database connections open in HikariCP, leading to connection pool exhaustion and complete application downtime.
* *Enforcement:*
  - RM queries run in isolated background polling threads using `WebClient` / `RestClient` with short timeouts.
  - ECR transmissions run via an asynchronous Scheduled Outbox Sweeper that reads committed rows from `outbox_events` and updates state after network completion.

#### 1.4 Feature Flags
Outbound integration capabilities must be gated by environment feature flags (default: **off**):
* **`RM_SYNC_ENABLED`** (boolean, default: `false`): Enables the scheduled background RM polling worker. When `false`, RM sync is completely disabled, and PMs rely on manual contract entry / manual material release overrides (D-04).
* **`ECR_SEND_ENABLED`** (boolean, default: `false`): Enables the background outbox sweeper to transmit HTTP calls to ECR. When `false`, outbox events remain safely accumulated in `PENDING` status in PostgreSQL without network dispatch.

---

### 2. Proposed Design

#### 2.1 Configuration Properties Schema
All integration settings are centralized in a strongly typed configuration record mapped to `app.integration`:

```java
@ConfigurationProperties(prefix = "app.integration")
public record IntegrationProperties(
    RmProperties rm,
    EcrProperties ecr
) {
    public record RmProperties(
        boolean syncEnabled,
        String baseUrl,
        String serviceAccountUsername,
        String serviceAccountPassword,
        Duration connectTimeout,    // Default: 3s
        Duration readTimeout,       // Default: 5s
        int pollIntervalSeconds,    // Default: 180s (3 min)
        int maxRetries              // Default: 2
    ) {}

    public record EcrProperties(
        boolean sendEnabled,
        String baseUrl,
        String internalSecret,
        Duration connectTimeout,    // Default: 3s
        Duration readTimeout,       // Default: 5s
        int sweeperIntervalSeconds, // Default: 10s
        int batchSize               // Default: 20
    ) {}
}
```

#### 2.2 Subsystem Failure Isolation Architecture
The platform is designed to guarantee continuous shop-floor manufacturing even during total upstream outages:
1. **RM Outage Isolation:**
   * If RM is offline, the operator station is not blocked from machining pieces whose material is already verified.
   * If a piece's material status check fails due to RM downtime, the PM can execute a manual material override with justification (D-04, BR-003).
   * Informational queries fall back to the most recent cached state in PostgreSQL / Caffeine with a UI warning badge: *"RM Sync Offline — Showing Cached State"*.
2. **ECR Outage Isolation:**
   * If ECR is offline, the PM can continue dispositioning failed pieces and generating replacement pieces without delay.
   * Events safely accumulate in the local `outbox_events` table until ECR recovers.

---

### 3. Technical Assumptions
1. **ASSUMPTION:** Spring Boot 3.4 `RestClient` or `WebClient` is configured with separate connection pools dedicated strictly to external HTTP calls, completely isolated from internal web container threads.
2. **ASSUMPTION:** Render environment variables (`RM_SERVICE_ACCOUNT_PASSWORD`, `ECR_INTERNAL_SECRET`) are injected securely at container startup.

---

### 4. Open Questions
* **O-04 (ECR Additional Process Category — OPEN):** Business confirmation whether ECR intake endpoint accepts a distinct `ADDITIONAL_PROCESS` type or requires sending as `REWORK` with text prefix `[ADDITIONAL PROCESS]` in `defectDescription` (Documented in Document 03).
