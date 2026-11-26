# Part 3.5 — Synthesis Report, Security Governance, and Conformance Findings
## Document 06: Part 3.5 Synthesis Report, Security Conformance Audit, and Open Questions

---

### 1. Executive Summary & Deliverables Completed

Part 3.5 establishes the definitive security, authentication, role-based access control (RBAC), immutable audit logging, application observability, and OWASP application hardening standards for the Velan Production platform.

The following six specification documents have been authored under `docs/phase-3/3.5-security-audit-logging/`:
1. **[01-authentication-and-session-standard.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.5-security-audit-logging/01-authentication-and-session-standard.md):** Mandatory individual employee authentication (D-03) with strict prohibition of shared logins; Argon2id password hashing algorithm recommendation with prescriptive parameters (64 MB RAM, 3 iterations, 1 parallelism); account lockout policy (5 failures in 15 min $\to$ 15 min lock); time-limited (15 min) SMTP password reset; JWT access token (15 min) in React memory paired with sliding refresh token (7 days) in an `HttpOnly; Secure; SameSite=Strict` cookie with Refresh Token Rotation (RTR); account suspension protocol with the mandatory PM reassignment rule (06_Role_Matrix §3); shop-floor credential policy options kept formally OPEN (O-09).
2. **[02-rbac-and-permission-catalogue.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.5-security-audit-logging/02-rbac-and-permission-catalogue.md):** Strict restriction to six primary system roles (Admin, PM, Designer, Operator, QC, Management) with no new roles introduced; permission code format `MODULE:ACTION`; exact multi-tier permission evaluation algorithm matching Decision D-03 and BR-052/BR-053 (Admin Individual Override wins $\to$ Role explicit deny wins $\to$ Role grant wins $\to$ Default deny); conceptual relational data model in words; master permission catalogue covering all application modules; default role-to-permission matrix derived strictly from Phase 1 06_Role_Matrix; packing and dispatch permissions flagged as OPEN (C-1); Caffeine permission caching and eviction rules; automated test matrix requirements (every endpoint $\times$ every role).
3. **[03-audit-logging-standard.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.5-security-audit-logging/03-audit-logging-standard.md):** Comprehensive audit event schema (`actorUserId`, `action`, `entityType`, `entityId`, `oldValue`, `newValue`, `reason`, `timestamp`, `correlationId`); 10-point mandatory audited actions list (BR-054); defense-in-depth append-only relational protection via PostgreSQL `REVOKE UPDATE, DELETE` plus uncatchable database triggers; role-based log visibility governance (BR-055: Admin sees all, PM sees production activity for assigned SCs only, no security logs for PM); audit retention policy kept formally OPEN (O-11); compliance export standard (CSV / JSONL); in-transaction synchronous persistence for critical domain mutations.
4. **[04-application-logging-and-observability.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.5-security-audit-logging/04-application-logging-and-observability.md):** Structured JSON logging on `STDOUT` via `logstash-logback-encoder` on Render; MDC context fields (`correlationId`, `userId`, `userRole`, `module`, `tenantId`, `clientIp`); prescriptive log levels (ERROR, WARN, INFO, DEBUG, TRACE); strict data sanitization catalogue ("What must never be logged"); Sentry exception tracking architecture with PII scrubbing callbacks; Spring Boot Actuator health checks (liveness/readiness for Render) and Prometheus telemetry; 5 automated alerting threshold rules.
5. **[05-security-checklist.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.5-security-audit-logging/05-security-checklist.md):** OWASP-adapted security hardening checklist: Jakarta input validation; JSX auto-escaping and prohibition of `dangerouslySetInnerHTML`; Cloudflare R2 pre-signed upload gating with S3 `HeadObject` validation; strict CORS whitelist (no wildcards); security response headers (CSP, HSTS, X-Content-Type-Options, X-Frame-Options); token bucket rate limiting (5 req/min on login, 120 req/min on API); zero hardcoded secrets with `.env.example` template; Apache POI zip bomb inflation limits (`ZipSecureFile.setMinInflateRatio(0.01)`) and XXE prevention; least privilege read-only credentials for the RM connector (BR-067).
6. **[06-part-3.5-report.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.5-security-audit-logging/06-part-3.5-report.md):** (This document) Comprehensive synthesis report, conflict register, technical assumptions audit, and open questions.

---

### 2. Comprehensive Conflict Register

In accordance with `SKILLS.md §0`, all known architectural conflicts remain formally OPEN without unilateral engineering decisions:

#### Conflict C-1: Completion Model — D-10 vs. Phase 2 Parts 2.2 and 2.6 (OPEN)
* **Conflict Statement:** Decision `D-10` specifies that a piece is Packed when all operations pass; a product is complete when all its pieces are Packed; the SC becomes Delivered automatically without packing or dispatch screens/statuses/roles. Conversely, Phase 2 Parts 2.2 and 2.6 specify multi-component assembled sets (`assembled_sets`), physical packing checklists (`packing_records`), staging bays (`DISPATCH_PENDING`), and loading dock gate releases (`dispatch_confirmations`).
* **RBAC Standards Status:** **OPEN.** Permissions `DISPATCH:SET_VIEW`, `DISPATCH:PACK_CREATE`, and `DISPATCH:GATE_RELEASE` are catalogued in Document 02 but explicitly marked as conditional and OPEN under Conflict C-1.

#### Conflict C-2: Piece Count Derivation Source (OPEN)
* **Conflict Statement:** Decision `D-02` specifies that piece count per product is derived directly from the quantity RM sends for that product line. Phase 2 Part 2.2 calculates piece demand as target assembled sets $\times$ component BOM ratio.
* **Security & Audit Status:** **OPEN.** Audit schemas and import permissions accommodate both direct and calculated piece counts without structural modification.

#### Conflict C-3: The "SC" Terminology Problem (OPEN with Recommendation)
* **Conflict Statement:** "SC" carries four different meanings across systems: RM screen "Sales Component (SC Code)", Phase 1/2 prose "Sales Contract", Phase 2 tables `subcontracts`, and ECR field "Subcontract order number".
* **Recommendation:** Adopt neutral permission codes `ORDER:SC_VIEW`, `ORDER:SC_CREATE`, and `ORDER:SC_RELEASE`.
* **Status:** **OPEN.** Submitted for project owner direction before Phase 5 API implementation.

---

### 3. Consolidated Technical Assumptions Register

1. **ASSUMPTION:** Spring Security 6.4 `Argon2PasswordEncoder` runs within Render web service container limits (minimum 512 MB RAM allocation) using 64 MB per hash.
2. **ASSUMPTION:** Client IP addresses are extracted accurately from the `X-Forwarded-For` header set by Render reverse proxies.
3. **ASSUMPTION:** In-process Caffeine cache holds resolved user permissions with a 15-minute TTL, evicting entries immediately upon role or override modifications.
4. **ASSUMPTION:** PostgreSQL JSONB columns `old_value` and `new_value` in `audit_logs` store minimal field-level diffs rather than full table snapshots, conserving database capacity.
5. **ASSUMPTION:** Render captures all container `STDOUT` logs and streams them to configured log drains without log loss.
6. **ASSUMPTION:** The maximum single file size for Cloudflare R2 uploads is **50 MB**.

---

### 4. Errors & Inconsistencies Found in Previous Work

The following role and audit discrepancies were identified in earlier documentation and reconciled in Part 3.5:

1. **Packing & Dispatch Actions in Role Matrix vs. Decision Log:**
   * *Citation:* `docs/phase-1/06_Role_Matrix.md:38`
   * *Issue:* A footnote stated that packing confirmation and dispatch confirmation rows were removed per D-10. However, Phase 2 Part 2.2 and 2.6 reintroduced assembled sets and dispatch bays under Conflict C-1.
   * *Correction:* Reconciled in Part 3.5 Document 02 by explicitly retaining `DISPATCH:PACK_CREATE` and `DISPATCH:GATE_RELEASE` in the catalogue, but flagging them as conditional and **OPEN (C-1)**.
2. **Audit User Column Modeling:**
   * *Citation:* `docs/phase-2/2.2-domain-data-model/05-integration-ownership-and-audit-model.md:11-15`
   * *Issue:* Modeled audit columns `created_by` and `updated_by` across entities as `VARCHAR(100)` storing usernames rather than UUID foreign keys referencing `users.id`.
   * *Correction:* Standardized in Part 3.3 and Part 3.5 so that audit logs and relational entities reference immutable UUIDs (`actorUserId` / `created_by_user_id`), preserving integrity even if a username is edited.
3. **Pessimistic Locking Reference in Sequence Diagram:**
   * *Citation:* `docs/phase-2/2.1-system-architecture/04-request-flow-and-error-handling.md:28`
   * *Issue:* Sequence diagram stated `SVC->>DB: SELECT piece, operation (FOR UPDATE optimistic lock)`, incorrectly combining SQL pessimistic locking syntax (`FOR UPDATE`) with optimistic locking.
   * *Correction:* Reconciled in Part 3.2, 3.4, and 3.5 to standard JPA optimistic locking using the explicit `version` integer column without holding long-lived pessimistic database locks.

---

### 5. Open Questions Submitted for the Project Owner

* **O-09 (Shop-Floor Credential Policy — OPEN):** What is the confirmed credential type and minimum length for shop-floor roles (Operator, QC)?
  - *Option A:* Standard username + strong password (minimum 10 characters, mixed case, numbers).
  - *Option B:* Employee ID + 6-digit numeric PIN for rugged touch terminals.
  - *Option C:* Barcode/NFC badge tap + 4-digit verification PIN.
* **O-11 (Audit Log Retention Policy — OPEN):** What is the statutory retention period for production and quality audit logs?
  - *Option A:* 7 years (standard enterprise manufacturing compliance).
  - *Option B:* 10 years (nuclear / high-pressure ASME Section III valve requirements).
  - *Option C:* Indefinite cold storage in Cloudflare R2 / S3 Glacier after 3 years active in PostgreSQL.
* **O-SEC-01 (Antivirus File Scanning):** Should uploaded engineering drawings in Cloudflare R2 be asynchronously scanned by an external antivirus engine before being marked approved for shop-floor viewing?
* **O-MON-01 (External Monitoring Tool):** Does the plant IT team have an existing enterprise monitoring tool (Datadog, New Relic, Grafana Cloud) for Actuator metrics scraping, or should basic Render metrics suffice for initial rollout?
