# Part 3.8 — Testing Standards, Git Workflow, Code Review, and Phase 3 Closure
## Document 05: Master Document Index, Consolidated Conflict Register, and Phase 4 Readiness

---

### 1. Master Phase 3 Deliverable Index

Phase 3 establishes the complete technical specification, backend and database standards, REST API contracts, security architectures, frontend standards, integration adapter specifications, and quality governance for Velan Production across **51 specification documents**:

| Phase 3 Part | Topic & Focus Area | Deliverables Created | Status |
|:---|:---|:---:|:---:|
| **Part 3.1** | Technology Specification & Environments | `01`–`05` (Stack, Neon, Rendering, Build, Report) | Approved |
| **Part 3.1a**| Neon Connection & Version Corrections | Correction report and revised documents | Approved |
| **Part 3.2** | Backend Architecture & Coding Standards | `01`–`06` (Monolith, Layers, Transactions, Caching, Report) | Approved |
| **Part 3.3** | Database and Naming Conventions | `01`–`06` (Glossary, Schema, Flyway, Indexing, Audit, Report) | Approved |
| **Part 3.4** | REST API & Serialization Standards | `01`–`06` (REST, Errors, Pagination, SSE/R2, Endpoints, Report)| Approved |
| **Part 3.5** | Security, RBAC, Audit & Observability | `01`–`06` (Auth, RBAC, Audit, Logging, Checklist, Report) | Approved |
| **Part 3.6** | Frontend & UI Engineering Standards | `01`–`06` (Structure, Data/Forms, Grid, SSE, Testing, Report) | Approved |
| **Part 3.7** | Integration Adapter Conventions | `01`–`06` (Boundaries, RM, ECR, Contracts, Gaps, Report) | Approved |
| **Part 3.8** | Testing, Git Workflow, and Phase 3 Closure | `01`–`06` + `.github/pull_request_template.md` | **Current** |

---

### 2. Consolidated Register of All Open Conflicts and Decisions

Every open question, architectural conflict, and unverified item recorded across Phase 3 is consolidated below. **Zero unilateral decisions have been made; all items remain formally OPEN for the project owner:**

| ID | Title & Open Item Statement | Options & Technical Recommendation | Target Phase | Blocks Phase 4? |
|:---:|:---|:---|:---:|:---:|
| **C-1** | **Completion Workflow Model:** D-10 automatic cascade vs. Phase 2.6 assembled sets, packing checklists, and dispatch bays. | • Option A: D-10 (automatic Packed/Delivered; zero screens).<br/>• Option B: Phase 2.6 (assembled sets, packing, bays). | Phase 7 / 26 | **NO** |
| **C-2** | **Piece Count Derivation Source:** D-02 RM line quantity vs. target sets $\times$ component BOM ratio. | • Option A: Direct RM product quantity (D-02).<br/>• Option B: Calculated target sets $\times$ BOM ratio. | Phase 7 / 18 | **NO** |
| **C-3** | **The "SC" Terminology Problem:** SC as Sales Contract vs. Subcontract vs. Sales Component. | • Adopt neutral technical names `sc_headers` and `sc_products` (or `sales_contracts`). | Phase 7 / 18 | **NO** |
| **O-01** | **PO Reference Date:** Map `purchase_orders.reference_date` as contract delivery deadline. | • Confirm mapping or add explicit delivery date field. | Phase 13 | **NO** |
| **O-04** | **ECR Additional Process:** Native intake vs. `[ADDITIONAL PROCESS]` prefix in `defectDescription`. | • Awaiting ECR team confirmation; fallback to prefix. | Phase 14 / 31 | **NO** |
| **O-09** | **Shop-Floor Credential Policy:** Credential type and length for Operator and QC roles. | • Option A: Username + password.<br/>• Option B: Employee ID + 6-digit PIN.<br/>• Option C: Badge tap + 4-digit PIN. | Phase 8 / 24 | **NO** |
| **O-10** | **Material Issued Threshold:** Partial ($\sum > 0$) vs. 100% issuance required for product readiness. | • Confirm whether partial issuance satisfies readiness gate. | Phase 22 | **NO** |
| **O-11** | **Audit Log Retention Duration:** Statutory retention for production/quality audit trails. | • Option A: 7 years.<br/>• Option B: 10 years.<br/>• Option C: Indefinite cold archive. | Phase 8 / 42 | **NO** |
| **O-12** | **Process Template Editing Authority:** Who may edit routing templates. | • Admin only vs. PM if granted (Designer selects only). | Phase 11 | **NO** |
| **O-NAM-01**| **Table Renaming:** Rename table `subcontracts` to `sc_headers` or `sales_contracts`. | • Approve renaming during Phase 7 migration authoring. | Phase 7 | **NO** |
| **O-CONF-01**| **Phase 2 Conformance Corrections:** Apply 18 data model corrections in Phase 7. | • Approve applying Document 05 deviations in Flyway. | Phase 7 | **NO** |
| **O-DB-01**| **Read-Only DB Role:** Provision `velan_readonly_user` in Neon. | • Approve dedicated read-only role for dashboards. | Phase 7 | **NO** |
| **O-MIG-01**| **Production Flyway Runner:** In-app container boot vs. Render pre-deploy release command. | • Recommend Render pre-deploy release command. | Phase 49 | **NO** |
| **O-API-01**| **SSE Authentication:** 30s single-use ticket vs. `@microsoft/fetch-event-source` Bearer header. | • Recommend `@microsoft/fetch-event-source` Bearer header. | Phase 6 / 24 | **NO** |
| **O-API-02**| **Decimal Serialization:** Confirm serialized measurements as JSON Strings (e.g. `"150.005"`). | • Strongly recommended to eliminate IEEE-754 errors. | Phase 5 | **NO** |
| **O-SEC-01**| **Antivirus Scanning:** Async antivirus scanning on Cloudflare R2 drawing uploads. | • Optional ClamAV scanner integration on R2 confirm. | Phase 10 | **NO** |
| **O-MON-01**| **Monitoring Tooling:** Enterprise APM (Datadog/Grafana) vs. native Render metrics. | • Native Render metrics for MVP; Datadog for scale. | Phase 49 | **NO** |
| **O-FE-01**| **Repository Architecture:** Maven/Node Monorepo in single Git repo vs. two repositories. | • **RECOMMENDATION: Monorepo** (`/backend` + `/frontend`). | **Phase 4** | **YES (Decision Needed)** |
| **O-INT-01**| **WireMock Stub Packaging:** Packaging JSON stubs in a shared test-jar library. | • Package stubs in `backend/src/test/resources/`. | Phase 13 | **NO** |
| **O-GIT-01**| **CI Path Filtering:** Run GitHub Actions on path filters (`paths: ['backend/**', ...]`). | • Enable path filtering to save GitHub Actions minutes. | Phase 49 | **NO** |
| **O-DOD-01**| **Automated PR Checklist Bot:** GitHub Action enforcing 100% checked PR template. | • Enable automated PR template verification bot. | Phase 49 | **NO** |
| **O-TRC-01**| **Automated Traceability Script:** Script verifying every requirement ID has a test. | • Add traceability verification script in Phase 48. | Phase 48 | **NO** |
| **VERIFY** | **RM JSON Schemas:** Property casing, enum values, response shapes across 13 read APIs. | • Verified via live sample capture during Phase 13. | Phase 13 | **NO** |

---

### 3. Consolidated Register of Errors Found Across Phase 1 and Phase 2

The systematic reviews conducted across all Phase 3 parts identified the following concrete discrepancies in baseline documents:

1. **Invented RM Endpoints in Project Context:** `docs/PROJECT_CONTEXT_FOR_CLAUDE.md §10.1` listed invented endpoint paths (`/api/v1/purchase_orders`, `/api/v1/subcontracts`, etc.). Reconciled to real audited endpoints (`/api/sc`, `/api/rm`, `/api/material-issues`, `/api/delivery-challans`, `/api/ecr-issues`).
2. **Invented ECR Intake Paths & Headers in Phase 2:** `docs/phase-2/2.5/.../04-ecr-outbox...md:14` modeled ECR intake as `POST /api/v1/ecr/intake` using generic headers. Reconciled to real audited path `POST /api/internal/rm-intake` with `x-internal-secret` and `x-idempotency-key`.
3. **Prohibited Dual Outbox Destination to RM in Phase 2:** Phase 2 Part 2.5 proposed sending exceptions to RM via `POST /api/v1/rm/exceptions`. Direct violation of Decision D-09 (`INT-003/004/005` deleted; `BR-067`: Production never writes to RM). Outbox targets ECR exclusively.
4. **Automated ECR Retry Policy Violating BR-062:** Phase 2 Part 2.5 proposed 5 automated retries on ECR timeouts. Reconciled: ECR has only a 10-minute in-memory cache without persistent deduplication; ambiguous timeouts transition to `AMBIGUOUS_TIMEOUT` for Admin review (BR-062).
5. **Invented Report Number Formats:** Phase 2 Part 2.5 modeled report numbers as `ECR-YYYY-####`. Reconciled to audited format `AGIPL-YYYY-ERR-XXXXX`.
6. **Deeply Nested Operation URIs:** Phase 2 Part 2.1 modeled operations as `POST /api/v1/pieces/{serial}/operations/{opId}/start`. Reconciled to canonical aggregate action `POST /api/v1/piece-operations/{operationId}/actions/start`.
7. **Non-Standard QC Resource Path:** Phase 2 Part 2.1 used `GET /api/v1/qc/inspections/{opId}/parameters`. Reconciled to standard plural resource `GET /api/v1/qc-inspections/{inspectionId}`.
8. **18 Data Model Deviations in Part 2.2:** Documented in Part 3.3 Document 05 (audit user columns using `VARCHAR(100)` instead of `UUID REFERENCES users(id)`, `version` using `BIGINT` instead of `INTEGER`, foreign keys missing target table singular prefix, booleans missing `is_` prefix). Scheduled for Flyway correction in Phase 7.
9. **Pessimistic Locking Syntax in Sequence Diagrams:** Phase 2 Part 2.1 stated `SELECT ... FOR UPDATE optimistic lock`. Reconciled to JPA `@Version` optimistic concurrency control.
10. **React Version Baseline:** Phase 2 Part 2.1 referenced React 18+. Standardized on **React 19**, Vite 6, and TypeScript strict mode.

---

### 4. Phase 4 Readiness Checklist

Phase 4 is: **Project Skeleton and Build Configuration** (directory scaffolding, Maven `pom.xml`, `package.json`, Dockerfiles, and basic build scripts).

#### Pre-Flight Decision Gate for Phase 4:
* [x] **Tech Stack Baseline Confirmed:** Java 21 LTS, Spring Boot 3.4.x, PostgreSQL 16 on Neon, React 19, Vite 6, Tailwind CSS.
* [x] **Database & Naming Standards Confirmed:** snake_case tables, camelCase JSON, UUID PKs, integer version.
* [x] **API & Security Architecture Confirmed:** `/api/v1`, `ApiResponse<T>`, Argon2id, `MODULE:ACTION` RBAC, immutable audit logging.
* [x] **Testing Standards Confirmed:** JUnit 5, Testcontainers, WireMock, ArchUnit, Vitest, Playwright.
* [ ] **THE SOLE DECISION REQUIRED BEFORE PHASE 4:**
  - **O-FE-01 (Repository Structure):** Does the project owner approve a **unified Monorepo layout** in this repository (`/backend` containing Maven Spring Boot project, `/frontend` containing Vite React project, with root coordination)?
  - *Recommendation:* **Approve Monorepo.** Simplifies end-to-end OpenAPI type generation, unified Git versioning, and single-click Render Docker deployment.
* [x] **All other open items (C-1, C-2, C-3, O-01 to O-12) can safely remain OPEN during Phase 4.** They do not block scaffolding the project build skeletons.
