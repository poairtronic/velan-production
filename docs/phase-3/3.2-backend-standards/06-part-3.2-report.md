# Part 3.2 — Synthesis Report and Architectural Verification
## Document 06: Part 3.2 Synthesis Report, Conflict Register, and Open Questions

---

### 1. Executive Summary & Deliverables Completed

Part 3.2 establishes the definitive backend architecture and coding standards for the Velan Production modular monolith. It bridges the domain state machines from Phase 2 into prescriptive, testable software engineering contracts for implementation phases.

The following six specification documents have been authored under `docs/phase-3/3.2-backend-standards/`:
1. **[01-module-structure-and-dependency-rules.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.2-backend-standards/01-module-structure-and-dependency-rules.md):** Defines the 11 domain modules, standard `api/` vs `internal/` package layout, public exported facades, and a numbered catalogue of 14 prescriptive ArchUnit rules (enforcing zero entity leakage, mandatory authorization, repository isolation, and DAG dependency directions).
2. **[02-domain-services-and-state-machine-pattern.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.2-backend-standards/02-domain-services-and-state-machine-pattern.md):** Standardizes the 7-step domain service state transition flow across all 10 state machines; provides a complete Java code example; maps every state machine to its aggregate and exception type; establishes code-level protection for append-only records; documents completion logic as an open architectural pattern.
3. **[03-transactions-concurrency-and-idempotency.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.2-backend-standards/03-transactions-concurrency-and-idempotency.md):** Codifies transaction boundaries at the service layer, `@Transactional(readOnly = true)` optimization, optimistic locking (`@Version`) with HTTP 409 conflict responses, row-lock ordering for deadlock prevention, high-concurrency `SKIP LOCKED` worker pattern, and the strict prohibition of remote network calls inside database transactions.
4. **[04-coding-conventions-and-layering.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.2-backend-standards/04-coding-conventions-and-layering.md):** Establishes immutable Java records for DTOs, MapStruct compile-time mapping, Jakarta Bean Validation, `VelanException` domain hierarchy mapped to standard JSON error responses, injectable Clock for testability, `BigDecimal(scale = 3)` for measurements, strict Lombok usage rules (forbidding `@Data` and `@EqualsAndHashCode` on JPA entities), and structured MDC logging.
5. **[05-performance-and-caching-rules.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.2-backend-standards/05-performance-and-caching-rules.md):** Enforces `spring.jpa.open-in-view=false`, lazy loading by default, $N+1$ query prevention strategies, Hibernate batching (50 rows) for bulk piece generation, strict pagination bounds (max 100), Caffeine named in-process caches with explicit prohibitions against caching piece status or counts, and SSE 15-second heartbeat streaming.
6. **[06-part-3.2-report.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.2-backend-standards/06-part-3.2-report.md):** (This document) Comprehensive synthesis, conflict register, assumptions audit, and open questions.

---

### 2. Comprehensive Conflict and Inconsistency Register

In accordance with `SKILLS.md` rules, no open conflict has been silently resolved:

#### Conflict C-1: Completion Model — D-10 vs. Phase 2 Parts 2.2 and 2.6 (OPEN)
* **Conflict Statement:** Decision `D-10` specifies that when all operations on all pieces of an SC pass, the SC becomes `Delivered` automatically without packing or dispatch screens/statuses/roles. Conversely, Phase 2 Parts 2.2 and 2.6 specify multi-component assembled sets (`assembled_sets`), physical packing records (`packing_records`), staging bays (`DISPATCH_PENDING`), and loading dock gate releases (`dispatch_confirmations`). Production never creates vendor DCs.
* **Backend Standards Status:** **OPEN.** The state machine pattern provides the structural foundation (aggregate methods, events, transactional boundaries) without deciding the business rule. The completion trigger remains an open architectural decision for the project owner.

#### Conflict C-2: Piece Count Derivation Source (OPEN)
* **Conflict Statement:** Decision `D-02` specifies that piece count per product is derived directly from the quantity RM sends for that product line. Phase 2 Part 2.2 calculates piece demand as target assembled sets $\times$ component BOM ratio.
* **Backend Standards Status:** **OPEN.** Document 02 defines piece generation as an event-driven domain service method capable of accepting either direct quantity counts or calculated set ratios based on owner direction.

#### Conflict C-3: RM and ECR Endpoint Audits vs. Conceptual Placeholders (Resolved by Rule)
* **Resolution:** In accordance with Decision `D-11` and `SKILLS.md §10`, the real endpoints audited from live external code in `docs/phase-1/09_Integration_Specification.md` (e.g. `GET /api/sc`, `GET /api/material-issues`, `POST {ECR_API_URL}/api/internal/rm-intake` with `x-internal-secret`) are binding on the `integration` module. Conceptual REST URIs in Phase 2 documents are treated as non-authoritative placeholders.

---

### 3. Discrepancies and Errors Noted in Earlier Documents

During the Part 3.2 review of Phase 2 architecture and state machine specifications, the following minor discrepancies were identified:
1. **Operation State Machine Count:** Phase 2.4 overview initially stated 9 states, while the transition matrix catalogued 10 distinct states (`PENDING`, `READY`, `OPENED`, `QC_PENDING`, `QC_IN_PROGRESS`, `PASSED`, `FAILED`, `CONCESSION_ACCEPTED`, `BYPASSED`, `CANCELLED`). Document 02 standardizes on the confirmed 10-state catalogue.
2. **Neon Pooled Port Discrepancy in Early Phase 2 Notes:** Early architectural drafts informally mentioned port 6432 for PgBouncer. As resolved in Part 3.1a, official Neon documentation confirms that both Direct compute and Pooled PgBouncer endpoints listen on standard PostgreSQL port `5432`.
3. **Spring Boot Version Terminology:** Phase 1 documents labeled Spring Boot 3.x as "LTS". As verified from official Spring Boot governance in Part 3.1a, Spring Boot releases use "Active OSS Support" and "Commercial Support" (LTS applies strictly to Java SE / OpenJDK).

---

### 4. Consolidated Technical Assumptions Register

The following operational assumptions are codified across the Part 3.2 standards:
1. **ASSUMPTION:** Spring Security method security (`@EnableMethodSecurity`) is active and evaluated before controller method execution.
2. **ASSUMPTION:** MapStruct annotation processing executes during `javac` compilation without requiring reflection at JVM runtime.
3. **ASSUMPTION:** Neon PgBouncer pooled connections support Spring Boot's transaction commit/rollback commands with zero session contamination.
4. **ASSUMPTION:** ShedLock with a dedicated Neon PostgreSQL table (`shedlock`) provides 100% mutual exclusion across multiple containers for scheduled cron jobs.
5. **ASSUMPTION:** In-process Caffeine caches consume less than 10 MB total JVM heap memory on Render Standard instances.
6. **ASSUMPTION:** Factory shop-floor terminals possess continuous network connectivity; offline client data caching is excluded from baseline scope.

---

### 5. Open Questions Submitted for the Project Owner

The following open decisions are formally submitted for business and architectural direction:

* **Conflict C-1 (Completion Workflow):** Does the owner confirm the simplified automatic cascade of Decision D-10 (all pieces complete $\rightarrow$ SC Delivered automatically) or the multi-component assembled set and dispatch staging bay workflow defined in Phase 2.6?
* **Conflict C-2 (Piece Count Derivation):** Does the owner confirm that piece counts are derived purely from RM product quantities (D-02) or calculated via target sets $\times$ component BOM ratio?
* **O-MOD-01 (Database Schema Separation):** Should `completion` and `vendor` modules maintain distinct database schemas within Neon PostgreSQL (e.g., `schema = 'completion'`), or share the public schema with table-prefix naming conventions?
* **O-STM-01 (Partitioning for Status History):** Should high-volume history tables (`piece_operation_history`, `audit_logs`) be partitioned monthly in PostgreSQL to maintain query performance as records reach millions of rows?
* **O-TX-01 (Dead-Letter Outbox Email Alerts):** Should outbox events that reach `DEAD_LETTER` generate automatic SMTP email alerts to system administrators in addition to appearing on the Admin UI reconciliation screen?
* **O-COD-01 (Imperial vs. Metric Display):** Should dimensional measurements on the operator UI display fractional imperial notation (e.g. $\pm 1/16"$) alongside metric millimeters for legacy drawings, or strictly metric millimeters?
* **O-PERF-01 (SSE Gateway Scalability):** If shop-floor terminal count expands beyond 100 concurrent tablets, should SSE streaming be offloaded to a dedicated lightweight gateway or maintained within the modular monolith container?
