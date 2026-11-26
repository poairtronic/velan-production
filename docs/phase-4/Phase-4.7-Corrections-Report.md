# Velan Production — Phase 4.7: Corrections and Reconciliation Report

**Document Path:** `docs/phase-4/Phase-4.7-Corrections-Report.md`  
**Date:** 2026-11-26  
**Branch:** `docs/phase-4.7-corrections` (branched from `main` at `44cc12e`; uncommitted changes in `docs/` only until final commit; NOT pushed, NOT merged)  
**Scope:** Documentation corrections, reconciliation, missing Phase 3.2 backend standards, and read-only audits of Decision Log, Neon connection rules, Dockerfiles, and Git workflow. Zero edits under `backend/`, `frontend/`, `pom.xml`, `package.json`, `.env.example`, or root `README.md`.

---

## 1. Executive Summary of Deliverables

1. **Corrected Phase 4 Reports:** Audited and fixed all Phase 1–3 misstatements, nonexistent Phase 3 file references, fabricated ADR titles/numbering, and `:6543` Neon errors across [`Phase-4-Accumulated-Master-Report.md`](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-4/Phase-4-Accumulated-Master-Report.md) and [`Phase_4.0`](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-4/Phase_4.0_Preflight_Audit_Report.md) through [`Phase-4.6`](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-4/Phase-4.6-Completion-Report.md).
2. **Module Reconciliation:** Authored [`docs/phase-4/Phase-4.7-Module-Reconciliation.md`](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-4/Phase-4.7-Module-Reconciliation.md) comparing Phase 2 (`2.1-02` / `2.2-02`), Phase 3 (`3.2-01`), and Phase 4 Master Report §9 without taking a decision.
3. **Missing Phase 3.2 Content:** Added [`07-domain-services-and-state-machine-pattern.md`](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.2-backend-standards/07-domain-services-and-state-machine-pattern.md) and [`08-coding-conventions-and-layering.md`](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.2-backend-standards/08-coding-conventions-and-layering.md), updated [`06-part-3.2-report.md`](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.2-backend-standards/06-part-3.2-report.md), and updated [`docs/phase-3/README.md`](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/README.md).
4. **Read-Only Audits Completed:** Decision log (`git log`/`git diff`), Neon official docs verification, static Dockerfile audit (Docker unavailable on host), and Git standard conflict audit.

---

## 2. Task 1 — Itemized Corrections Applied to Phase 4 Reports & Phase 3 Index

### 2.1 `docs/phase-4/Phase-4-Accumulated-Master-Report.md`
Replaced the inaccurate Phases 1–3 retrospective in Section 2 with a concise factual summary linking to [`docs/phase-3/README.md`](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/README.md) and [`docs/PROJECT_CONTEXT_FOR_CLAUDE.md`](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/PROJECT_CONTEXT_FOR_CLAUDE.md), preserved all genuine Phase 4 facts and verbatim Maven/npm command outputs, and fixed the following wrong claims:

| # | False / Wrong Claim in Master Report | Verified Fact from Real Documents | Action Taken |
|:-:|:---|:---|:---|
| 1 | `00_DECISION_LOG.md`: `"D-01 through D-20"` | Contains **`D-01` through `D-19`** and **`O-01` through `O-12`**; no `D-20` exists. | Removed `D-20`; replaced with `D-01–D-19` / `O-01–O-12`. |
| 2 | `06_Role_Matrix.md`: `"12 distinct employee roles"` | **`D-03` & `06_Role_Matrix.md`:** strictly **6 confirmed roles** (`Admin`, `PM`, `Designer`, `Operator`, `QC`, `Management`). | Replaced with 6 confirmed roles. |
| 3 | `08_Business_Rules.md`: `"BR-001 through BR-045"` | Spans **`BR-001` through `BR-070`** (63 active; 7 marked `[REMOVED]` per `D-10`/`O-03`). | Corrected range to `BR-001–BR-070`. |
| 4 | `15_Application_Master_Plan.md`: `"28-phase master engineering roadmap"` | Authoritative **51-phase master roadmap** (Phases 1–51). | Corrected to 51-phase roadmap. |
| 5 | `01_BRD.md` & `02_SRS.md`: `"covering 9 core production modules"` | `02_SRS.md` specifies `FR-001`–`FR-016`; `03_Application_Scope.md` lists 17 scope modules (`15_Application_Master_Plan.md §3` has 13 subsections). | Removed `"9 core production modules"` claim. |
| 6 | `"15 Docs"` in Phase 1 Mermaid node | `docs/phase-1/` has 17 files (`00_DECISION_LOG.md`, `01`–`15`, `README.md`). | Updated Mermaid label. |
| 7 | `ADR-001`–`ADR-008` numbering and wrong titles (`ADR-002` Caffeine, `ADR-003` Outbox, `ADR-004` Neon, `ADR-006` React SPA, `ADR-007` Render, `ADR-008` `"Dual-port Neon connectivity ... port 6543"`) | Real IDs in `2.1-05` are `ADR-01`–`ADR-08`: **ADR-01** Modular Monolith, **ADR-02** ECR Transactional Outbox, **ADR-03** Caffeine Cache, **ADR-04** Read-Only RM Connector, **ADR-05** Cloudflare R2 Pre-Signed URLs, **ADR-06** SSE Notifications, **ADR-07** Spring Application Events, **ADR-08** Unified Neon PostgreSQL with Schema Isolation. React/Render are in `13_Tech_Stack.md`, `2.1-03`, `3.1-01/02`. | Removed fabricated ADR mapping & `:6543`; fixed §6 Invariants table to `ADR-01`, `ADR-03`, `ADR-02`, and `13_Tech_Stack.md / 2.1-03 / 3.6-01`. |
| 8 | `"2.2 Domain Data Model: 18 domain entities"` | `2.2-02` catalogues **38 entities** (18 was the count of conformance deviations in `3.3-05`). | Corrected to 38 entities. |
| 9 | `"2.3 to 2.6 State Machines"` listing only 4 themes (Piece, Operation, Quality Gate, Rework/Scrap) | Parts `2.3`–`2.6` define **10 state machines** (`pieces`, `piece_operations`, `tolerance_revisions`, `qc_inspections`, `qc_exceptions`, `outbox_events`, `subcontracts`, `subcontract_products`, `assembled_sets`, `packing_records`). | Corrected to 10 state machines across Parts `2.3`–`2.6`. |
| 10 | `"51 documents, 5,691 lines of code and rules"` | Phase 3 baseline (`daf3941`) had **49 markdown files (4,650 lines)** of documentation only—zero code. (Now 51 files after adding 3.2 `07` & `08`.) | Corrected in §2 and Mermaid diagram. |
| 11 | `"Part 3.2 ... Clean Architecture layering (Domain -> Application -> Infrastructure -> Presentation)"` | `3.2-01` defines `api/` (`command`, `query`, `event`, `facade`) and `internal/` (`domain`, `repository`, `service`, `controller`, `mapper`, `listener`). | Replaced with factual summary of `3.2`. |
| 12 | `"Part 3.6 ... blind double-entry QC verification"` | `D-06` & `3.6-03 §2.2` define **independent blind QC** (operator and QC enter separate records; QC inputs start blank), not double-entry QC. | Replaced with independent blind QC. |
| 13 | `"Part 3.7: RM read-only integration via staging tables"` | `3.7-02` polls RM REST APIs through an ACL (`RmContractTranslator`), upserting into `sc_headers`/`sc_products`, logging in `rm_sync_history`, and storing only unmatched DC lines in `unmatched_vendor_dc_items`. No staging-table architecture. | Removed `"staging tables"` claim. |
| 14 | `"Part 3.8 ... >= 80% line coverage ... trunk-based with feature branches"` | `3.8-01 §2.6` requires **`>= 90%` domain/state-machine coverage** and `>= 75%` overall; `3.8-02` enforces **protected `main` with PRs and Squash & Merge**, not trunk-based direct commits. | Corrected to `>= 90%` / `>= 75%` and PR-gated `main`. |
| 15 | §3.5 (`backend/Dockerfile`): claimed `"runs mvn dependency:go-offline"` and `"velan (UID 10001)"` | Actual `backend/Dockerfile` runs `./mvnw dependency:go-offline -B \|\| true` and `useradd -r -g velan ...` (OS-assigned UID, no `10001`). | Corrected §3.5 and cross-referenced §5 Dockerfile audit. |
| 16 | §3.6 & §5: `"Zero commits and zero pushes have been made"` | Phase 4 was subsequently committed & pushed directly to `main` in `44cc12e`. | Added Phase 4.7 git status note. |
| 17 | §7.3: `"C-1, C-2, C-3 ... tracked in docs/phase-1/00_DECISION_LOG.md"` | `00_DECISION_LOG.md` tracks `D-01–D-19` & `O-01–O-12`; `C-1–C-3` are in `SKILLS.md §0` & Phase 3 reports. | Corrected source references. |
| 18 | §9: Proposed `com.velan.production.modules.* + shared + infrastructure` as Phase 5 scope | Unapproved departure from `3.2-01`. | Replaced §9 transition diagram to reference `Phase-4.7-Module-Reconciliation.md` & `O-MOD-02` as pending owner decision. |

### 2.2 Other Phase 4 Reports (`Phase_4.0` through `Phase-4.6`) & `docs/phase-3/README.md`
* **`docs/phase-4/Phase_4.0_Preflight_Audit_Report.md`:**
  * Fixed 6 nonexistent Phase 3 paths (`3.1-technology-stack/01-technology-stack.md`, `3.1a-environment-and-deployment/01-environments-and-deployment-strategy.md`, `3.2-backend-standard/01-modular-monolith-package-structure.md`, `3.3-database-and-naming-conventions/02-database-design-and-schema-conventions.md`, `3.8-quality-process/02-git-workflow-and-branching.md`, `3.8-quality-process/04-definition-of-done.md`) across §§C.3, D.3, G.4.1–G.4.5 to real Phase 3 paths.
  * Fixed `"ADRs 001 to 008"` $\to$ `ADR-01 to ADR-08`; clarified `BR-001–BR-070` (`63` active, `7` removed).
  * Fixed `"hexagonal architecture"` (`3.2-01` uses `api/` vs `internal/` modular monolith) and `"RFC 7807 problem details"` (`3.4-02` uses `{ code, message, details, timestamp, path, correlationId }`).
  * Fixed misattributed Decision IDs in §D.4: `"D-08 / O-FE-01"` $\to$ `O-FE-01` & `D-13` (`D-08` is Reject & Restart); `"D-12 (No External Messaging/Cache)"` $\to$ `D-13` (`D-12` is MERC/Velan Dashboard).
  * Replaced all 7 `:6543` occurrences and invented `aws-*-pooler.neon.tech` / `aws-*.neon.tech` patterns with verified Neon `-pooler` vs direct hostname patterns on port `5432`.
* **`docs/phase-4/Phase-4.1-Completion-Report.md`:** Fixed `ADRs 001–008` $\to$ `ADR-01–ADR-08`, fixed all 4 nonexistent Phase 3 file paths in §2.B, and added a Phase 4.7 note documenting that `.env.example` still holds `:6543` outside `docs/`.
* **`docs/phase-4/Phase-4.2-Completion-Report.md` & `Phase-4.3-Completion-Report.md`:** Fixed `ADRs 001–008` $\to$ `ADR-01–ADR-08`.
* **`docs/phase-4/Phase-4.4-Completion-Report.md`:** Clarified in §3 that both Neon Direct and Pooled (`-pooler`) endpoints listen on port `5432`.
* **`docs/phase-4/Phase-4.5-Completion-Report.md`:** Fixed §2 citations: `"D-08 monorepo layout"` $\to$ `O-FE-01 monorepo layout, D-13 stack & Render deployment`; `"ADR-08 Neon pooling"` $\to$ `ADR-08 unified Neon storage, Part 3.1 (3.1-02) Neon pooled vs direct endpoints`.
* **`docs/phase-4/Phase-4.6-Completion-Report.md`:** Fixed `ADRs 001–008` $\to$ `ADR-01–ADR-08`; updated §5 `.env.example` summary to note the `:6543` error in `.env.example` (flagged outside `docs/`); annotated pre-commit `daf3941` status vs later commit `44cc12e` in §§1, 4, 17.
* **`docs/phase-3/README.md`:** Corrected `:6543` $\to$ `:5432` in Part 3.1 table row; fixed stale filenames in the `3.1` and `3.2` directory tree (`03-environment-configuration.md`, `06-part-3.1a-correction-report.md`, `01-module-structure-and-dependency-rules.md`, `02-domain-services-and-state-machine-pattern.md`, `03-transactions-concurrency-and-idempotency.md`, `04-coding-conventions-and-layering.md`); added `07` and `08` to Part 3.2; updated doc count description (`51 documents`, noting `49` at `daf3941` + `2` added in 4.7).

---

## 3. Task 2 — Decision Log Audit (`docs/phase-1/00_DECISION_LOG.md`)

Executed read-only Git checks on `docs/phase-1/00_DECISION_LOG.md`:
* **`git log --stat -- docs/phase-1/00_DECISION_LOG.md`:** Only **1 commit** in repository history ever touched `00_DECISION_LOG.md`:
  * `20035311dd5f59d5cf51cd64a6d723795c16b80c` (`2026-11-24`, `docs: add 00_DECISION_LOG.md per decision log 2026-11-24`, `+47` lines).
* **`git log -p daf3941..HEAD -- docs/phase-1/00_DECISION_LOG.md` & `git diff daf3941 HEAD -- docs/phase-1/00_DECISION_LOG.md`:** **Empty output (0 commits, 0 lines changed)** since Phase 3 baseline `daf3941`.
* **Conclusion:** `00_DECISION_LOG.md` has **not changed** since `2026-11-24`. It contains **D-01 through D-19** and **O-01 through O-12** only. The Phase 4 Master Report claim `"D-01 through D-20"` was completely fabricated. (`docs/phase-1/00_DECISION_LOG.md` was NOT edited.)

---

## 4. Task 3 — Neon Connection Verification & `"6543"` Audit

### 4.1 Official Neon Documentation Verification
Verified directly against Neon's official connection pooling documentation (`neon.com/docs/connect/connection-pooling` via web search):

| Attribute | Pooled Connection String | Direct (Unpooled) Connection String |
|:---|:---|:---|
| **Hostname Format** | Endpoint ID + `-pooler` suffix before region/domain: `ep-<id>-pooler.<region>.aws.neon.tech` | Standard endpoint hostname without `-pooler`: `ep-<id>.<region>.aws.neon.tech` |
| **Port** | **`5432`** (Standard PostgreSQL port) | **`5432`** (Standard PostgreSQL port) |
| **Pooling Mode** | **PgBouncer in Transaction Mode** (up to 10,000 client connections multiplexed) | **None** (direct to Postgres compute, limited by `max_connections`) |
| **Intended Use** | Application runtime queries (`HikariCP`, serverless APIs) | Schema migrations (Flyway advisory locks `pg_advisory_lock`), `pg_dump`, session state (`SET`, `LISTEN/NOTIFY`) |

### 4.2 Complete Inventory of `"6543"` and Invented Hostname/Port Patterns

| File & Line(s) | Original Erroneous Text | Status in Phase 4.7 |
|:---|:---|:---|
| `docs/phase-3/README.md:17` | `Flyway direct :5432 vs HikariCP pooled :6543` | **CORRECTED** to both port `:5432` (`-pooler` vs direct). |
| `docs/phase-4/Phase-4-Accumulated-Master-Report.md:85` | `ADR-008: Dual-port Neon connectivity (port 5432 ... port 6543 ...)` | **REMOVED / CORRECTED** in §2 replacement summary (`ADR-08` is unified Neon storage; both endpoints use `:5432`). |
| `docs/phase-4/Phase_4.0_Preflight_Audit_Report.md:122,130,181,184,194,326,341` | `:6543` and invented wildcard pattern `aws-*-pooler.neon.tech:6543` / `aws-*.neon.tech:5432` | **CORRECTED** all 7 occurrences to `ep-<id>-pooler.<region>.aws.neon.tech:5432` / `ep-<id>.<region>.aws.neon.tech:5432`. |
| `docs/phase-4/Phase-4.6-Completion-Report.md:101` | Described `.env.example` as `Neon connectivity (pooled :6543 vs direct :5432)` | **CORRECTED** description to explicitly flag `.env.example`'s `:6543` error. |
| `.env.example:14-15` *(outside `docs/`)* | `# ... (port 6543)` and `DATABASE_URL=...@HOST-pooler.neon.tech:6543/...` | **NOT EDITED (out of scope — `docs/` only rule). Flagged for owner/PR cleanup.** *(Note: `HOST-pooler.neon.tech` also omits `ep-<id>.<region>.aws`.)* |
| `README.md:33` *(outside `docs/`)* | `PostgreSQL 16 on Neon (pooled :6543 for runtime, direct :5432 for Flyway)` | **NOT EDITED (out of scope — `docs/` only rule). Flagged for owner/PR cleanup.** |
| `docs/phase-3/3.1-02:39-40` & `3.3-03:10` | Shorthand `ep-xxx-pooler.neon.tech` / `ep-xxx.neon.tech:5432` | Preserved in frozen Phase 3 docs; full pattern `ep-<id>[-pooler].<region>.aws.neon.tech:5432` is already given in `3.1-02:102,106` & `3.1-03:80,86`. |
| `docs/phase-3/3.1-02:107`, `3.1-06:21`, `3.2-06:41` | Historical note rejecting `6432` | Preserved (already explicitly documents `6432` was wrong and `5432` is correct). |

---

## 5. Task 6 — Static Dockerfile & Docker Availability Audit

**Docker CLI Check:** `docker --version` failed (`The term 'docker' is not recognized`). Docker / Docker Desktop is **not installed** on this host, so `docker build` could **not** be executed (`UNVERIFIED` at runtime). Neither Dockerfile was modified.

### 5.1 `backend/Dockerfile` (41 lines)
* **Maven Binary vs. `./mvnw`:** Stage 1 (`FROM eclipse-temurin:21-jdk-jammy AS builder`) has no pre-installed `mvn`, and the Dockerfile correctly copies `.mvn/`, `mvnw`, `pom.xml`, runs `chmod +x mvnw`, and invokes **`./mvnw`** (not `mvn`). Verified `backend/mvnw` uses `LF` line endings (no CRLF error).
* **Layer Caching Order:** Mostly correct (`COPY .mvn/`, `COPY mvnw pom.xml ./`, `RUN ./mvnw dependency:go-offline`, then `COPY src/ src/`, then `RUN ./mvnw clean package -DskipTests -B`), **but flawed** by:
  1. `RUN ./mvnw dependency:go-offline -B || true` (line 16) swallows failures, risking a broken cached layer.
  2. `clean` in `./mvnw clean package` (line 20) is redundant in a clean builder container.
  3. Deviates from `docs/phase-3/3.1-technology-specification/04-dependency-and-build-policy.md §4` (`eclipse-temurin:21-jdk-jammy` / `jre-jammy` instead of `maven:3.9.9-eclipse-temurin-21-alpine` / `eclipse-temurin:21-jre-alpine`, default GC instead of `-XX:+UseZGC`, unpinned image digests).
* **Non-Root Runtime:** **PASS.** Stage 2 (`eclipse-temurin:21-jre-jammy`) creates system group/user `velan` and runs `USER velan` (note: UID is OS-assigned, not `10001` as Phase 4 Master Report claimed; `COPY --chown=velan:velan` would avoid an extra layer from `RUN chown -R`, and `exec java` in `ENTRYPOINT` is recommended so SIGTERM reaches the JVM directly).

### 5.2 `frontend/Dockerfile` (23 lines)
* **Base & Build:** Single-stage dev container (`FROM node:22-alpine`) running Vite dev server (`npm run dev -- --host 0.0.0.0 --port 5173`).
* **Layer Caching Order:** **PASS.** Copies `package.json package-lock.json ./`, runs `RUN npm ci`, then copies `. .`.
* **Non-Root Runtime:** **FAIL.** Missing any `USER` directive (e.g., `USER node`), so the Vite dev server inside the container runs as **`root`**.
* **Production Stage:** No multi-stage static/Nginx stage (acceptable only if Render Static Site builds via Node directly without Docker, as specified in `3.1-02`).

---

## 6. Task 7 — Git Workflow Departure Audit & Rule Going Forward

Comparing Phase 4 commit `44cc12e` (`feat(phase-4): establish repository foundation, backend starter, frontend starter, and local docker config`) against [`docs/phase-3/3.8-quality-process/02-git-workflow-and-release.md`](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.8-quality-process/02-git-workflow-and-release.md):

1. **Direct Commit & Push to `main` (Violates `3.8-02 §1.1.1 & §1.1.2`):** `44cc12e` was committed and pushed directly to `main`, bypassing branch protection and PR checks.
2. **Missing Topic Branch (Violates `3.8-02 §1.2`):** No branch following `feature/<phase>-<slug>`, `fix/<slug>`, `docs/<slug>`, or `hotfix/<slug>` was created for Phase 4.
3. **Bypassed PR Review & Squash-and-Merge (Violates `3.8-02 §1.1.3 & §2.3`):** No Pull Request, `.github/pull_request_template.md` self-review checklist, CI checks, or Squash-and-Merge took place.
4. **Conventional Commit Scope Drift (Violates `3.8-02 §1.3`):** `44cc12e` used scope `phase-4`, which is not in the allowed scopes list (`execution`, `quality`, `planning`, `orders`, `integration`, `security`, `db`, `frontend`).
5. **Stale Pre-Commit Claims in Reports:** `Phase-4.6` and `Phase-4-Accumulated-Master-Report.md` stated zero commits/pushes were made, because they were authored before `44cc12e` landed on `main`.

**Rule to Follow From Now On (History Preserved Unchanged):**
* **Do NOT rewrite history** (`44cc12e` remains intact on `main`).
* **Enable branch protection on `main`** on GitHub and **never commit or push directly to `main`**.
* Always branch from `main` using `feature/<phase>-<slug>`, `fix/<slug>`, `docs/<slug>`, or `hotfix/<slug>`; use Conventional Commits; run verification commands and `.github/pull_request_template.md` review; and land changes on `main` exclusively via **Pull Request + Squash and Merge**.

---

## 7. Complete Register of `UNVERIFIED` Items

| Item | Reason `UNVERIFIED` | Required Next Step |
|:---|:---|:---|
| **Docker Image Builds (`backend` & `frontend`)** | `docker` CLI / Docker Desktop is not installed on this Windows host. | Install Docker Desktop and run `docker build` / `docker compose up --build` (or run in CI). |
| **Live Neon Database Connectivity (`SELECT 1`)** | No Neon project/credentials provisioned yet (owner-deferred to Phase 7). | Owner creates dedicated `velan-production` Neon project in Phase 7 and validates `-pooler:5432` and direct `:5432`. |
| **Root `.env.example` & `README.md` `:6543` Fix** | Outside `docs/`; not touched due to Phase 4.7 `docs/`-only constraint. | Fix `:6543` $\to$ `:5432` in a separate PR/commit permitted to edit root config files. |
| **RM API JSON Property Casing & Auth Flow** | Carried from `09_Integration_Specification.md` & `3.1-05 §4.2`; RM live system not queried. | Verify via `curl` against live RM endpoints in Phase 13 (`Part 2.7` gap list). |
| **Cloudflare R2 Account ID / Keys & Prod SMTP Host** | Carried from `3.1-05 §4.2`; external tenant secrets not provisioned. | Owner / IT provides during Phase 6 environment setup. |

---

## 8. Open Questions for the Project Owner

1. **Module List & Package Layout (`Phase-4.7-Module-Reconciliation.md`):** Which module list and package root (`com.velan.production.<module>` per `3.2-01` vs. `com.velan.production.modules.* + shared + infrastructure` per Phase 4 §9) is approved for Phase 5?
2. **Conflict C-1 (Completion Model — Blocks `dispatch`/`completion` module & 10-state-machine completion cascade):** Decision `D-10` (automatic `Packed` $\to$ `Delivered`, no packing/dispatch screens) vs. Phase 2.6 (`assembled_sets`, `packing_records`, `dispatch_confirmations`)?
3. **Conflict C-2 (Piece Count Source) & C-3 (`SC` Table Naming):** Direct RM quantity (`D-02`) vs. BOM ratio $\times$ target sets, and `subcontracts` vs. `sc_headers` vs. `sales_contracts`?
4. **JPA Annotations on Domain Entities (`O-MOD-02` in `3.2 / 08`):** Approve single-model JPA annotations on `<module>.internal.domain` entities (recommended to avoid duplicate mapping) or require separate `*JpaEntity` persistence classes?
5. **Cleanup Outside `docs/` (Root Config & Dockerfiles):** Authorize a follow-up PR to fix `:6543` $\to$ `:5432` in `.env.example` and `README.md`, remove `|| true` / `clean` in `backend/Dockerfile`, and add a non-root `USER node` in `frontend/Dockerfile`?
6. **Conventional Commit Scopes (`3.8-02 §1.3`):** Add `docs`, `foundation`, `build`, `infra` to allowed Conventional Commit scopes alongside the domain module scopes?
