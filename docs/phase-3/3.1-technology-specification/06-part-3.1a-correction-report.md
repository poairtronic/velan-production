# Part 3.1a — Correction Pass Report
## Audit of Corrections, Official Sources, and Unverified Items

**Date:** 2026-11-26  
**Scope:** Part 3.1 Documentation Correction Pass (`docs/phase-3/3.1-technology-specification/`)  
**Status:** Completed in strict conformance with `SKILLS.md` and Phase 1–2 baselines.

---

### 1. Executive Summary

A targeted correction pass (Part 3.1a) was executed across the five technology specification documents under `docs/phase-3/3.1-technology-specification/` to eliminate speculative claims, resolve architectural discrepancies, align Neon database pooling with official documentation, correct Spring Boot support terminology, document the multi-semantic usage of the term "SC", and audit all engineering assumptions.

No application code, SQL migrations, build scripts, or Phase 1 / Phase 2 documents were modified.

---

### 2. Itemized Audit of Corrections Applied

#### Correction 1: Neon PostgreSQL Connection Strings & Advisory Lock Invariant
* **What was wrong previously:** Earlier drafts referenced port `6432` for PgBouncer and did not accurately cite Neon's official connection string structure or the precise reason why Flyway fails when routed through transaction pooling.
* **What was corrected:** 
  - Rewrote Section 4 of `02-environments-and-hosting.md` and updated `03-environment-configuration.md`.
  - Confirmed that **both** Direct compute and Pooled endpoints listen on standard PostgreSQL port **`5432`** (Neon's PgBouncer binds to port 5432).
  - Clarified hostname formats: Pooled includes `-pooler` (e.g. `ep-xxx-pooler.region.aws.neon.tech`); Direct does not (e.g. `ep-xxx.region.aws.neon.tech`).
  - Documented the exact failure mechanism: Neon PgBouncer operates in **Transaction Mode**, releasing connections upon transaction commit/rollback. Flyway requires **session-level advisory locks** (`pg_advisory_lock`, `pg_try_advisory_lock`) to prevent concurrent migration collisions. In transaction pooling, session locks are lost across statement boundaries, causing Flyway to fail, hang, or throw locking exceptions. Therefore, Flyway (`SPRING_FLYWAY_URL`) must strictly target the Direct connection.
* **Official Source Used:** [Neon Official Documentation: Connection Pooling and Flyway Migrations](https://neon.tech/docs/postgresql).

---

#### Correction 2: Upstream Version Verification & Spring Boot Support Terminology
* **What was wrong previously:** Spring Boot was labeled as "LTS" (an OpenJDK term not used by Spring Boot governance), and some library versions were based on speculative memory rather than live upstream package registries.
* **What was corrected:**
  - Removed all occurrences of "LTS" applied to Spring Boot across Documents 01, 04, and 05.
  - Documented Spring Boot's official governance terminology: **Active OSS Support**, **Commercial Support**, and **End of Life (EOL)**.
  - Re-checked Spring Boot support schedules: Spring Boot 3.3.x and 3.4.x reached community OSS EOL on June 30, 2025 and December 31, 2025, respectively (available only via commercial VMware Tanzu / Broadcom licensing). Spring Boot 4.0.x is currently under Active OSS Support through December 31, 2026.
  - Formally submitted the decision to the project owner without unilateral selection.
  - Re-verified all backend, frontend, and testing library versions against Maven Central, npm registry, Adoptium, and Node.js release pages as of November 26, 2026, recording exact URLs and verification dates.
* **Official Sources Used:** 
  - [Spring Boot Supported Versions Wiki](https://github.com/spring-projects/spring-boot/wiki/Supported-Versions)
  - [Adoptium Temurin 21 Releases](https://adoptium.net/temurin/releases/?version=21)
  - [Maven Central Repository](https://repo1.maven.org/maven2/)
  - [npm Registry](https://www.npmjs.com/)

---

#### Correction 3: Scope Conflict C-1 (Assembly vs. Manufacturing Scope Boundaries)
* **What was wrong previously:** Document 05 asserted an invented interpretation that "Dispatch Challans represent a legal handover of subcontract lots back to Stores/Logistics," attempting to reconcile Phase 1 D-10 with Phase 2.6.
* **What was corrected:**
  - Completely removed the claim regarding legal handover of subcontract lots.
  - Replaced with the unvarnished factual discrepancy:
    * **Decision D-10 (Confirmed):** A piece is Packed when all its operations pass; a product is complete when all its pieces are Packed; the SC becomes Delivered automatically when all products are complete. No packing/dispatch screens, statuses, roles or dispatch confirmation.
    * **Phase 2 (Parts 2.2 and 2.6):** Defines assembled valve sets (`assembled_sets`), set completion tracking (`is_production_complete`), final QC verification (`is_final_qc_passed`), physical packing checklists (`packing_records`), staging status (`DISPATCH_PENDING`), and loading dock gate releases (`dispatch_confirmations`).
    * **Production never creates vendor DCs:** RM owns all external vendor DC movements (`D-05, D-09`).
  - Recorded formally as **OPEN Conflict C-1** awaiting project owner determination.
* **Controlling Source Ref:** `docs/phase-1/00_DECISION_LOG.md` (D-05, D-09, D-10) and `SKILLS.md` (Known open conflicts C-1).

---

#### Correction 4: Terminology Finding — Multi-Semantic Usage of "SC"
* **What was wrong previously:** The documents did not record that the abbreviation "SC" carries contradictory definitions across the system ecosystem.
* **What was corrected:** Added a dedicated Terminology Finding section in `05-part-3.1-report.md` documenting all four usages with full file and line citations:
  1. **RM screen label "Size Card (SC Code)":** `docs/phase-1/09_Integration_Specification.md:36`, `docs/updates/DOCS_UPDATE_PACK.md:449`.
  2. **Phase 1 & Phase 2 prose "Size Card":** `docs/PROJECT_CONTEXT_FOR_CLAUDE.md:70, 88, 101, 304, 383, 429, 490, 546, 583, 790, 791`; `docs/phase-2/2.2-domain-data-model/01-domain-model-and-hierarchy.md:19, 46, 64, 69, 122, 134, 259`; `docs/phase-2/2.2-domain-data-model/02-entity-catalogue-and-attributes.md:36, 59, 229, 686`; `docs/phase-2/2.6-sc-and-completion-state-machine/01-sc-lifecycle-overview-and-states.md:8, 11, 30, 173, 196`.
  3. **Phase 2 relational schema table names with "subcontract":** `subcontracts`, `subcontract_products`, `subcontract_operations` across `docs/phase-2/2.1/01...`, `docs/phase-2/2.2/01...`, `docs/phase-2/2.6/01...`.
  4. **ECR exception intake field "Size Card number":** `docs/phase-1/09_Integration_Specification.md:42, 62` (`scNumber`).
  - Left open without choosing a canonical term, per prompt instructions.

---

#### Correction 5: Email Infrastructure Alignment (Standard SMTP Baseline per D-13)
* **What was wrong previously:** References to hosted third-party email APIs (e.g., Resend) were mixed into the baseline environment configuration.
* **What was corrected:**
  - Aligned `03-environment-configuration.md` with Decision `D-13`: **Standard SMTP email is the sole baseline delivery driver.**
  - Added explicit standard Spring Mail SMTP environment variables:
    * `SPRING_MAIL_HOST`
    * `SPRING_MAIL_PORT` (port 587 STARTTLS / port 465 SSL)
    * `SPRING_MAIL_USERNAME`
    * `SPRING_MAIL_PASSWORD`
    * `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH`
    * `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE`
    * `APP_MAIL_FROM_ADDRESS`
  - Created a separate, clearly designated section: *"Future Hosted Email API Configuration (Deferred — Post-Go-Live per D-13)"* reserving `RESEND_API_KEY` and related variables for post-go-live evaluation.

---

#### Correction 6: Assumptions Audit and Sourcing
* **What was wrong previously:** Several operational tuning parameters, sizing tiers, and internal placeholder hostnames were presented without explicit `ASSUMPTION:` tags.
* **What was corrected:**
  - Scanned all five documents and explicitly labeled all engineering assumptions with `ASSUMPTION: [Parameter] is an engineering assumption / tuning baseline; not defined in Phase 1-2 documents`:
    * Render compute instances (`Starter` 0.5 CPU / 512 MB for dev/test; `Standard` 1 CPU / 2 GB for prod).
    * Neon serverless autoscaling (0.25 to 2.0 CUs).
    * Internal domain placeholders (`production.velan.internal`, `api.production.velan.internal`).
    * Outbox poller parameters (10s delay, 20 batch size, 5 retries).
    * In-process Caffeine cache TTL durations (15m permissions, 30m tolerances, 60m templates).
    * JWT access token (60m) and refresh token (7d) lifespans.

---

### 3. Register of Items Still UNVERIFIED

In accordance with strict verification rules, the following items cannot be confirmed from public upstream repositories and are marked `UNVERIFIED`:

| Unverified Item | Reason for Unverified Status | Target Verification Event |
| :--- | :--- | :--- |
| **RM REST API Response JSON Property Casing** | While database table and column names were verified during the Phase 1 live code audit, exact camelCase vs snake_case JSON response keys are unverified against running endpoints. | Must be confirmed via live `curl` against V-SMART as the Step 0 task of Phase 13. |
| **Cloudflare R2 Account ID & Bucket Tokens** | Specific tenant account IDs and access keys do not exist in open documentation. | Must be provisioned by project owner in Phase 6. |
| **Production Corporate SMTP Gateway & Auth Mode** | Specific plant IP/hostname, STARTTLS certificate authority, and relay authentication requirements are internal to Velan IT. | Must be provided by plant IT during staging deployment. |

---

### 4. Open Questions Submitted to the Project Owner

1. **O-TECH-01 (Spring Boot Support Line):** Does the owner approve adopting **Spring Boot 4.0.x / 4.1.x** (Active OSS Support) or freezing on **Spring Boot 3.4.x** (commercial support required)?
2. **Conflict C-1 (Completion & Scope Model):** Does the owner confirm the simplified automatic cascade of Decision D-10 (piece Packed $\rightarrow$ product Packed $\rightarrow$ SC Delivered automatically; no packing/dispatch screens) or the multi-component assembled set and dispatch staging bay workflow defined in Phase 2.6?
3. **O-ENV-01 (Production Ingress):** Will shop-floor tablets access the app over public Internet via Render domains, or via private VPN / reverse proxy (`production.velan.internal`)?
4. **O-CFG-01 (SMTP Gateway Authentication):** Does enterprise production SMTP require username/password credentials, or internal static IP whitelisting without authentication?
5. **O-BLD-01 (Artifact Proxying):** Will Render and GitHub Actions have direct outbound access to Maven Central and npmjs.org, or require internal corporate artifact mirrors?
