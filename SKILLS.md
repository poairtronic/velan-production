# SKILLS.md — Velan Production Agent Operating Manual

**Version:** 1.0 (2026-11-26)  
**Applies to:** every coding and documentation agent working in this repository (Antigravity, Gemini models, any other agent).  
**Purpose:** build Velan Production exactly to the written requirements, with optimised code, zero build errors, and correct linking between database, backend, API, frontend and documentation.

This file replaces `docs/phase-1/14_SKILLS.md` for day-to-day agent behaviour. Where this file and a Phase 3 standard (`docs/phase-3/**`) cover the same topic, the Phase 3 standard wins for *how to code*; the decision log and approved designs win for *what to build*.

---

## 0. Mandatory start routine (every task, every session)

Do these in order before changing anything. Do not skip, do not summarise from memory.

1. Read this file completely.
2. Read `docs/PROJECT_CONTEXT_FOR_CLAUDE.md` (project context; the name is historical, it is for any agent).
3. Read `docs/phase-1/00_DECISION_LOG.md`.
4. Read the documents relevant to the task (use the map in Appendix A).
5. Read `docs/phase-3/**` standards that exist for the topic.
6. Read the task prompt again and list: scope, out of scope, deliverables, success criteria.
7. Write a short plan: files you will create or change, commands you will run to verify, assumptions, open questions. Then start.

If you are continuing a long session, repeat steps 1, 3, 4 and 6 before each new task.

### Source of truth order

| Question | Order of authority |
|---|---|
| What must be built | 1. Decision log (D-xx) → 2. Approved Phase 2 designs → 3. Phase 1 documents → 4. This file |
| How to write code | 1. Phase 3 standards → 2. This file → 3. Framework defaults |
| What this task covers | The current prompt (scope and deliverables) — but it cannot silently override the decision log. If it conflicts, **stop and report** |
| Anything not written anywhere | Do not invent it. Write it as an assumption or an open question |

### Known open conflicts — DO NOT decide them, stop and ask if your task touches them

| ID | Conflict |
|---|---|
| C-1 | Completion model: decision D-10 (piece Packed, SC Delivered automatically, no packing or dispatch screens) versus Phase 2 Part 2.6 (sets, final QC, packing records, Dispatch Pending, dock release). |
| C-2 | Piece count source: decision D-02 (quantity sent by RM per product) versus BOM ratio × set quantity in Part 2.2. |
| C-3 | RM and ECR endpoint and field names: use the audited names in `docs/phase-1/09_Integration_Specification.md`; any other name in a document is **VERIFY**. |
| O-xx | Every open item in the decision log stays OPEN until the owner decides. |

*(Owner: update this table when a conflict is decided, and record the decision in the decision log.)*

---

## 1. Non-negotiable domain rules

These come from the decision log and approved designs. Never break them, never "improve" them.

1. **Six roles only:** Admin, PM, Designer, Operator, QC, Management. No Stores, Packing, Dispatch, Vendor or Department Head roles. Packing/dispatch-type actions, if kept, are permissions.
2. **Individual login** per employee. No shared accounts.
3. **RBAC:** Admin individual override first, then role grants; an explicit deny wins; no grant means deny. Enforced in the backend on every endpoint.
4. **Production never writes to RM.** RM is read-only through a service account. Production never creates or receives vendor DCs. Production writes to ECR only to send exceptions.
5. **ECR:** each exception is sent once through the database outbox; never auto-resend after an ambiguous timeout; store the returned report number; costing and responsibility never return to Production; Production never waits for ECR.
6. **Piece-level tracking.** Every piece has a unique serial and is in exactly one status at a time. Counts are derived from piece records, never typed in. A replacement piece gets a new linked serial; serials are never reused.
7. **Material gate:** pieces of a product cannot start or take data entry until the material-issued flag is set (RM sync, or PM/Admin manually).
8. **Measurements are append-only.** Operator and QC values are separate records. QC fields are never pre-filled with operator values. Tolerance revisions never rewrite old measurements.
9. **QC on every operation.** A QC flag raises a non-blocking alert; the flagged piece waits; **only the PM decides**: CONTINUE (mandatory reason), EXTRA PROCESS, RETURN TO EARLIER OPERATION, REJECT AND RESTART. Reject and restart scraps the piece and creates a replacement that does not increase the ordered quantity and is blocked until released.
10. **Vendor:** Production reads RM DC details and return status (received / partial / not received); no vendor dates; no vendor selection in Production; returned pieces may complete any pending operation, in-house operations stay sequential.
11. **SC intake:** RM sync, manual entry and Excel import are all permanent and follow identical rules after creation.
12. **Stack:** Java 25 LTS, Spring Boot, React + TypeScript, PostgreSQL on Neon, Cloudflare R2, Docker on Render, Caffeine cache (**no Redis**), database outbox (**no RabbitMQ or Kafka**), SSE for real-time, modular monolith (**no microservices**). Billing is outside Production.
13. **Admin-only:** deletion or cancellation, and user notification settings.

---

## 2. Task workflow

1. **Plan** (section 0, step 7).
2. **Work in small verified steps.** After each step run the relevant build or test command. Do not stack ten unverified changes.
3. **Make minimal diffs.** Edit only what the task needs. Do not rewrite whole files, do not reformat unrelated code, do not rename things the task does not mention.
4. **Keep everything linked** (section 5) as you go, not at the end.
5. **Verify** (section 4 commands) and fix every failure at the root cause.
6. **Self-review** against the checklist in section 4.4.
7. **Report** with the template in section 13, then **stop**. Do not start the next part or phase.

---

## 3. Scope discipline

- One prompt = one part. Do not start the next part, add "nice to have" features, or change approved documents unless the prompt says so.
- Documentation phases (1, 2, 3): write documents only. No code, migrations, pom, package.json, Dockerfile or CI files.
- No Flyway migration before the database-design phase; no feature code before the foundation phases. Follow the 51-phase roadmap order.
- Do not touch the RM or ECR repositories. Do not change anything outside this repository unless the prompt names it.
- Do not leave `TODO`, stubs, mock data, commented-out code, or "implement later" in anything you report as done. If something cannot be finished, say so in the report.

---

## 4. Correctness: zero errors

### 4.1 Verification commands
Use the exact commands in `README.md` once they exist. Until then use these defaults:

| Area | Command |
|---|---|
| Backend | `./mvnw -B verify` (compile, unit and integration tests, static analysis) |
| Frontend | `npm ci && npm run lint && npm run typecheck && npm test -- --run && npm run build` |
| Database | Flyway migrates a **fresh empty** database and the app starts; also `validate` passes |
| Full stack | `docker compose up --build`; health endpoint returns UP; the main page loads |
| Docs | Mermaid blocks parse; links resolve; IDs referenced exist |

### 4.2 Rules
- Never report a task as done unless the commands above pass. Include the last lines of the output in the report. If a command cannot run, say exactly why.
- Never disable, skip or weaken a test, lint rule, type check or constraint to make something pass.
- Fix the cause of an error. If the same error survives **three** attempts, stop, write down what you tried and the likely root cause, and ask.
- No compiler warnings introduced. No unused code, imports or dependencies. No `any` (TypeScript) or raw types and unchecked casts (Java) without a comment explaining why.
- No swallowed exceptions. No `catch` that only logs and continues on a business path. No `null` returned where `Optional` or an exception is the standard.
- Handle empty, loading, error and permission-denied states in every screen. Handle null, empty list, duplicate and concurrent calls in every service.
- Every date and time is UTC in storage and ISO-8601 in APIs; every measurement value is `BigDecimal` (backend) and a string or decimal-safe value (frontend) — never floating point.

### 4.3 Definition of done
A piece of work is done only when: the requirement IDs it covers are implemented, backend validation and permissions exist, the UI handles every state, audit entries exist for changes that need them, tests exist and pass, docs and the OpenAPI spec match the code, and the verification commands pass.

### 4.4 Self-review checklist (answer each in the report)
- [ ] Does every change trace to a requirement or rule ID?
- [ ] Any rule in section 1 touched? If yes, is it still true?
- [ ] Permissions enforced in the backend for each new endpoint?
- [ ] State changes only through the module's domain service, with status history and audit?
- [ ] History preserved (append-only records not updated or deleted)?
- [ ] Concurrency considered (optimistic version, locking order, idempotency)?
- [ ] Any new dependency, table, env var, error code, permission code or enum? Is it documented in the right place?
- [ ] No unrelated file changed (`git status` checked)?

---

## 5. Linking: everything must connect

Build and check this chain for every feature. A break anywhere is a defect.

```text
Requirement ID (FR / BR / AC / FEAT)
  → Flyway migration (table, constraints, indexes)
  → JPA entity (columns, @Version, enums)
  → Repository (queries, projections)
  → Domain service (state transition, validation, audit, events)
  → Controller (route, DTOs, permission code)
  → OpenAPI spec (generated, committed or published)
  → TypeScript types (generated from OpenAPI, never hand-copied)
  → API client + TanStack Query hook
  → Page / component (all states) with permission-aware UI
  → Tests (unit, integration, permission, E2E where the flow is user-facing)
```

Specific consistency rules:
- **Enums** have one spelling everywhere: database check constraint, Java enum, OpenAPI enum, TypeScript union, documentation. Add a test that compares them.
- **Permission codes** are defined once (one catalogue). Every controller method has an authorization annotation; the frontend reads the user's permissions from the API and never hard-codes role names for access decisions.
- **Error codes** come from one catalogue; the frontend maps codes to messages.
- **DTOs** are separate from entities. Never return an entity.
- **Foreign keys, unique constraints and check constraints** in the database match the validation in services and DTOs.
- **Environment variables** exist in code, in `.env.example` and in the environment documentation, all three, with the same name.
- **Feature flags** (for example `RM_SYNC_ENABLED`, `ECR_SEND_ENABLED`) default to off and are documented.
- **Imports, routes, menu entries, API paths and Mermaid diagrams** must resolve; no dead links, no orphan files.
- When you add or change a thing, update every place in this chain in the same change.

---

## 6. Optimisation

### Backend
- `spring.jpa.open-in-view=false`. Lazy loading by default. No N+1: use fetch joins, entity graphs or DTO projections, and check the generated SQL in tests for list endpoints.
- Every list endpoint is paginated (default 20, max 100) and sorted by an indexed column.
- Bulk work (for example generating hundreds of pieces) uses JDBC/Hibernate batching, not one insert at a time. Set and document the batch size.
- Short transactions. **No network call to RM, ECR, R2 or SMTP inside a database transaction.** Use the outbox or run the call after commit.
- Use optimistic locking (`@Version`) on aggregates that users edit concurrently; lock rows in a fixed order (ascending id) when you must lock several; use `SELECT … FOR UPDATE SKIP LOCKED` for outbox and queue workers.
- Cache only master data and permissions in Caffeine, with a size limit, a TTL and eviction on change. **Never cache piece, operation or quantity state.**
- Streams and SSE: heartbeat, bounded per-user connections, clean up on disconnect.
- Neon: small connection pool, retry on connection-acquire failure (cold start), migrations use the direct connection.

### Database
- Index every foreign key and every column used in filters, joins or ordering; use the partial and composite indexes specified in Part 2.2. Check query plans for the heavy screens (worklists, dashboards).
- Use the correct column types (numeric with explicit precision for measurements, `timestamptz`, `uuid`, `jsonb` only where the design says so).

### Frontend
- Route-level code splitting; lazy-load heavy screens and charts.
- Virtualise any grid or list that can exceed about 100 rows; the bulk entry grid must stay responsive with hundreds of rows and paste from Excel.
- Server state only in TanStack Query (set `staleTime`, invalidate precisely); UI state in Zustand or local state. No polling where SSE exists. Debounce search inputs.
- Avoid re-render storms: stable keys, memoised selectors, no object or function re-creation in hot lists.
- Respect the bundle size budget set in the Phase 3 standards.

---

## 7. Security rules

- Deny by default. Validate every request body, path and query parameter on the backend.
- Parameterised queries only. No string-built SQL.
- Never put secrets, tokens or passwords in code, logs, tests, documents, screenshots or git. Secrets come from environment variables only.
- Validate uploaded files by content type and size on the backend; use short-lived pre-signed URLs for R2.
- Rate limit login; lock out after repeated failures; hash passwords with the algorithm named in the Phase 3 standards.
- Log security events and privileged actions; never log personal data or secrets.
- CORS origins come from configuration, never `*` in production.
- Audit: actor, action, entity, old and new values, reason, timestamp, correlation id for every approval, override, cancellation, bypass, reassignment and status change.

---

## 8. Database and migrations (from the database phase onward)

- Flyway only; `V{n}__description.sql`; forward-only; **never edit an applied migration**.
- One concern per migration; schema changes and data fixes in separate migrations.
- Every migration runs on an empty database and on the previous release's schema.
- Constraints are part of the design: primary keys, foreign keys, unique, check and not-null follow Part 2.2. Do not weaken a constraint to make code easier.
- No destructive change (drop, truncate, delete) without an explicit instruction in the prompt.

---

## 9. Testing rules

For every change add or update:
- Unit tests for business rules and state transitions (valid and invalid).
- Integration tests with Testcontainers for repositories, transactions and migrations.
- Permission tests: allowed role passes, denied role gets 403, no grant gets 403.
- Concurrency and idempotency tests where quantities, states or outbox rows can race.
- History tests: append-only records stay unchanged; replacement pieces never raise ordered quantity.
- Architecture tests (ArchUnit) for module boundaries and "every controller method is authorised".
- Frontend component tests for forms and states; Playwright for the main user journeys.
- Tests use builders and realistic data, are independent, and do not call real RM, ECR, R2 or SMTP.

---

## 10. Integration rules (RM and ECR)

- All external calls live in `integration` adapters. External field names stay inside the adapter and are translated into Production objects (anti-corruption layer).
- RM: read-only service account, polling plus "refresh now", idempotent upserts by RM ids, a sync log, revision handling by diff. Never write to RM.
- ECR: outbox states PENDING → SENDING → SENT (or failed states), no auto-resend after an ambiguous timeout, Production exception id carried in the description, store the returned report number.
- Match RM DC lines by SC and product name (RM DC lines do not carry the requisition line id; the part number is overwritten with the SC code).
- Anything not confirmed against the real systems is marked **VERIFY** in code comments and docs. Do not guess a field, path or status value.
- Feature flags default to off. Timeouts, retry counts and backoff are configuration, not constants.

---

## 11. Working rules for Antigravity and Gemini agents

1. **Do not invent.** Do not invent library methods, config properties, versions, endpoints, table or column names, RM/ECR fields, or business rules. If you are unsure, look in the repository, the manifests (`pom.xml`, `package.json`), the official docs, or compile it. If still unsure, say so.
2. **Check versions before using an API.** Read the actual Spring Boot, Hibernate, Java and library versions from the manifest. Never mix APIs from different major versions. Use `jakarta.*` imports where the framework requires them.
3. **Verify by running.** Use the terminal to build, test and start the app. Do not claim something works because it "should".
4. **Small steps, fast feedback.** One change, one build. When a build fails, read the error and fix that error; do not try random alternatives.
5. **Never output partial files** that replace complete ones (no "rest of file unchanged" comments). Use targeted edits.
6. **Do not delete, skip or weaken tests, lint or types** to get green.
7. **Re-read the requirement** (section 0) when a task is long; state the constraints in your own words before coding if the task touches state machines, permissions, integrations or measurements.
8. **No silent decisions.** When two documents disagree, or a requirement is missing, stop and ask (section 12). Record assumptions explicitly in the report.
9. **Prefer the simplest correct design** that satisfies the documents; do not add layers, abstractions, libraries or patterns that the standards do not call for.
10. **Keep generated code readable:** clear names from the domain vocabulary, small methods, comments only for non-obvious rules (with the rule ID).

---

## 12. Stop and ask when

- Two sources conflict (including the known conflicts in section 0).
- A requirement is missing, ambiguous, or touches an open item.
- The task needs a new dependency, a table or column not in Phase 2, a new role, a new integration, or a new environment variable not in the documents.
- The task would delete data, rewrite history, change an applied migration, or touch RM, ECR or any other repository.
- You need a real secret or a real RM/ECR response to continue.
- Verification fails three times for the same reason.

When you stop, write: what you were doing, what blocks you, the options, and your recommendation.

---

## 13. Completion report template

End every task with this report, then stop.

```text
TASK: <prompt title / part>
RESULT: Completed | Completed with open items | Blocked

FILES CHANGED
- <path> — <what and why> (<requirement ids>)

REQUIREMENTS COVERED: <FR / BR / AC / D-xx ids>

VERIFICATION
- <command> → <result, last lines>

LINKING CHECK: <chain from section 5, which links were created or updated>

ASSUMPTIONS: <list, each marked ASSUMPTION>
CONFLICTS FOUND: <list, nothing silently decided>
OPEN QUESTIONS: <list>
NOT DONE / DEFERRED: <list with reason>
git status: <only the intended files changed? yes/no>
```

---

## 14. Forbidden shortcuts

- Hard-coded product composition, role checks by name instead of permissions, or UI-only security.
- Updating or deleting measurements, QC records, approvals, audit entries or tolerance revisions.
- Copying operator values into QC fields.
- Advancing a flagged, failed or material-blocked piece.
- Typing quantities or statuses by hand where they must be derived.
- Writing to RM; creating DCs; sending anything to ECR twice; auto-resending after an ambiguous timeout.
- Adding Redis, RabbitMQ, Kafka, microservices, QR scanning or AI without an approved scope change.
- Calling external systems inside a database transaction.
- Committing secrets, `.env` files, build output or generated credentials.
- Editing an applied Flyway migration.
- Reporting success without running the verification commands.

---

## Appendix A — Which document to read for which task

| Topic | Read |
|---|---|
| Decisions and open items | `docs/phase-1/00_DECISION_LOG.md` |
| Business rules | `docs/phase-1/08_Business_Rules.md` |
| Functional requirements | `docs/phase-1/02_SRS.md`, `04_Feature_List.md`, `05_User_Stories.md` |
| Acceptance tests | `docs/phase-1/10_Acceptance_Criteria.md` |
| Roles and permissions | `docs/phase-1/06_Role_Matrix.md` |
| Integration | `docs/phase-1/09_Integration_Specification.md`, `docs/phase-2/2.5-…/04-ecr-outbox-…`, audited RM and ECR names |
| Architecture | `docs/phase-2/2.1-system-architecture/` |
| Data model and constraints | `docs/phase-2/2.2-domain-data-model/` |
| Piece, operation, QC, exception, SC, set states | `docs/phase-2/2.3` to `2.6` |
| Standards (how to code) | `docs/phase-3/**` |
| Project status and roadmap | `docs/PROJECT_CONTEXT_FOR_CLAUDE.md`, `docs/phase-1/15_Application_Master_Plan.md` |

## Appendix B — Change control for this file
Only the owner changes this file. When a conflict in section 0 is decided or a new rule is agreed, update section 0 or section 1, and record the decision in the decision log in the same change.


---

## Appendix C — Final Folder Structure & Module-Boundary Rules (Phase 5.1)

- **Backend (`com.velan.production`):**
  - **9 Domain Modules:** `iam`, `order`, `engineering`, `execution`, `quality`, `qcexception`, `vendor`, `notification`, `integration` (`<module>/api` and `<module>/internal/{domain,application,infrastructure}`).
  - **Shared Kernel:** `shared/{audit,error,id,idempotency,scheduling,security,time}` (no `api`/`internal` split; no dependency on domain modules).
  - **Boundary Rules (enforced by ArchUnit):** No module may import another module's `internal.*` packages; modules communicate only through `<other>.api` and `shared`. `domain` must not depend on `application` or `infrastructure`; `application` and `api` must not depend on `infrastructure`.
- **Frontend (`frontend/src/`):** `app/`, `features/<module>/`, `components/ui/`, `hooks/`, `lib/{api,utils}/`, `routes/`, `types/`, `styles/` using `@/*`.
