# Part 3.5 — Security, RBAC, Audit and Logging Standards
## Document 04: Application Logging, MDC Context, Metrics, and Observability Architecture

---

### 1. Confirmed Rules

#### 1.1 Structured JSON Logging
1. **Format:** In non-local environments (staging and production on Render), application logs must be output as single-line structured JSON using `logstash-logback-encoder`.
2. **Logback Standard Output:**
   Logs are emitted to `STDOUT` where Render log aggregators collect and forward them without container filesystem disk writes.
3. **MDC (Mapped Diagnostic Context) Fields:**
   Every log statement must automatically inherit contextual metadata populated via a Spring `OncePerRequestFilter`:
   * `correlationId`: Unique UUIDv4 assigned to the HTTP request or background task.
   * `userId`: Authenticated user UUID (or `anonymous`).
   * `userRole`: Primary role of the actor.
   * `module`: Current domain module (`AUTH`, `ORDER`, `EXECUTION`, `QUALITY`, `EXCEPTION`, `INTEGRATION`, `ADMIN`).
   * `tenantId`: Tenant identifier (default: `tenant-default`).
   * `clientIp`: Origin client IP address.

#### 1.2 Log Levels and Prescriptive Usage

| Level | Severity | When to Use | Production Policy |
|:---:|:---|:---|:---:|
| `ERROR` | Critical failure | Unhandled exceptions (HTTP 500), database connection pool exhaustion, external system integration failures, dead-letter outbox events, security trigger violations. | Always enabled; triggers Sentry alerts. |
| `WARN` | Recoverable anomaly | Validation failures (422), failed login attempts (401), rate-limit throttles (429), circuit breaker state changes, stale cache fallbacks. | Always enabled; monitored for sudden spikes. |
| `INFO` | Business lifecycle | Application startup/shutdown, Flyway migration completions, batch contract import summaries, scheduled job executions. | Always enabled; high-signal events only. |
| `DEBUG` | Diagnostic detail | Tolerance calculation formulas, detailed cache hits/misses, outbox polling queries. | Disabled in production; enabled in dev/test. |
| `TRACE` | Wire-level payload | Raw HTTP byte streams, database query parameter dumps. | **Strictly prohibited in production.** |

#### 1.3 Strict Data Sanitization ("What Must Never Be Logged")
Log records are frequently ingested into cloud SIEM and search indices. The following sensitive properties are strictly prohibited from appearing in log statements, MDC context, or Sentry breadcrumbs:
* Plaintext passwords, shop-floor PINs, and password reset tokens.
* Raw JWT strings, Bearer tokens, and refresh tokens.
* Cloudflare R2 access keys, secret keys, and pre-signed URL signatures.
* Database passwords and SMTP credentials.
* Personally Identifiable Information (PII) such as personal phone numbers or personal emails.
* Logback `MaskingConverter` or regex filters must redact matching patterns (e.g. `password=******`, `token=******`).

---

### 2. Proposed Design

#### 2.1 Sentry Exception Tracking Architecture
* **Library:** `sentry-spring-boot-starter-jakarta` (v8.x).
* **Scope:** Captures only unexpected `ERROR` level exceptions (HTTP 5xx). Client errors (400, 401, 403, 404, 422) must be excluded to prevent alert noise.
* **Environment Tagging:** Injects `environment` tag (`production`, `staging`, `local`) and `release` version matching the Git commit SHA.
* **PII Scrubbing Callback (`BeforeSendCallback`):**
  - Sanitizes request headers (redacting `Authorization`, `Cookie`, `X-Api-Key`).
  - Scrubs SQL query parameters and user request bodies before transmitting error payloads to Sentry.

#### 2.2 Health Checks & Render Health Probes
Spring Boot Actuator provides standard health endpoints configured in `application.yml`:
* **Liveness Probe (`/actuator/health/liveness`):**
  - Returns `200 OK` (`{"status":"UP"}`) if the JVM process is alive and internal event loops are responsive.
  - Used by Render container orchestrator to restart frozen containers.
* **Readiness Probe (`/actuator/health/readiness`):**
  - Verifies Neon PostgreSQL connectivity and Cloudflare R2 reachability.
  - Returns `503 Service Unavailable` if database connection pool is disconnected, preventing traffic routing during boots or migrations.

#### 2.3 Prometheus Metrics & Actuator Telemetry
Metrics are scraped via `/actuator/prometheus` and visualized in Grafana / Render dashboards:
1. **HikariCP Pool Telemetry:**
   * `hikaricp.connections.active`: Currently running SQL queries.
   * `hikaricp.connections.idle`: Warm connections waiting in pool.
   * `hikaricp.connections.pending`: Threads waiting for a free database connection (saturation indicator).
2. **JVM Telemetry:**
   * Heap memory utilization (`jvm.memory.used`), garbage collection pause duration (`jvm.gc.pause`), thread count (`jvm.threads.live`).
3. **HTTP Throughput & Latency:**
   * `http.server.requests`: Request rate, error rates (by HTTP status), and response latency percentiles (p50, p95, p99).

#### 2.4 Alerting Rules Matrix
The platform defines automated alerts for critical operational conditions:

| Alert Trigger | Threshold Condition | Target Audience | Severity |
|:---|:---|:---|:---:|
| **Dead-Letter Outbox Event** | `outbox_events.status = 'DEAD_LETTER'` count $> 0$ | Admin / PM | P1 — High |
| **RM Integration Outage** | RM connector 5xx error rate $> 5\%$ over 5 minutes | Admin / IT | P2 — Medium |
| **Database Pool Saturation** | HikariCP pending threads $> 5$ for $> 30$ seconds | Admin / DevOps | P1 — High |
| **Failed Login Spike** | $> 20$ failed login events across system in 5 minutes | Admin / Security | P2 — Medium |
| **Sentry Error Surge** | $> 10$ unhandled exceptions per minute | Engineering Team | P1 — High |

---

### 3. Technical Assumptions
1. **ASSUMPTION:** Render captures all `STDOUT` logs and streams them to configured log drains (e.g. Datadog, Papertrail, or Better Stack) without log dropping.
2. **ASSUMPTION:** Sentry DSN is injected via environment variable `SENTRY_DSN`; if omitted, the Sentry client initializes in no-op mode for local development.

---

### 4. Open Questions
* **O-MON-01 (External Monitoring Tool):** Does the plant IT team have an existing enterprise monitoring tool (Datadog, New Relic, Grafana Cloud) for Actuator metrics scraping, or should basic Render metrics suffice for initial rollout?
