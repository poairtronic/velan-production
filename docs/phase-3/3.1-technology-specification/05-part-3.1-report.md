# Part 3.1 — Technology Specification and Environments
## Document 05: Part 3.1 Synthesis Report, Conflict Register, and Verification Log

---

### 1. Executive Summary & Deliverables Completed

Part 3.1 establishes the authoritative technical specifications, environment topology, and dependency governance for the Velan Production platform. It bridges the system design baselines from Phase 2 into actionable engineering constraints for Phase 3 and Phase 4.

The following specification documents exist under `docs/phase-3/3.1-technology-specification/`:
1. **[01-technology-stack-and-versions.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.1-technology-specification/01-technology-stack-and-versions.md):** Verified technology matrix, official release sources, verification dates, owning modules, and the explicit "rejected technologies" table.
2. **[02-environments-and-hosting.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.1-technology-specification/02-environments-and-hosting.md):** Render hosting topology, Neon PostgreSQL project/branch strategy, direct vs pooled connection rules (port 5432 / advisory lock requirements), Cloudflare R2 bucket policies, and health probe configurations.
3. **[03-environment-configuration.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.1-technology-specification/03-environment-configuration.md):** Exhaustive environment variable catalogue, standard SMTP configuration baseline (`D-13`), deferred hosted email API section, and reference `.env.example` specification.
4. **[04-dependency-and-build-policy.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.1-technology-specification/04-dependency-and-build-policy.md):** Governance for third-party libraries, license scanning tools, reproducible build standards, and hardened multi-stage Docker policies.
5. **[05-part-3.1-report.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.1-technology-specification/05-part-3.1-report.md):** (This document) Comprehensive synthesis report, conflict register, terminology findings, verification audits, and open questions.
6. **[06-part-3.1a-correction-report.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.1-technology-specification/06-part-3.1a-correction-report.md):** Itemized audit and record of all corrections applied during Part 3.1a pass.

---

### 2. Comprehensive Conflict and Inconsistency Register

In accordance with execution rules, all discrepancies, evolutions, and conflicts discovered between Phase 1 requirements, Decision Log `00_DECISION_LOG.md`, and Phase 2 design documents are recorded below without unilateral modification:

#### Conflict C-1: Completion Model — D-10 vs. Phase 2 Parts 2.2 and 2.6 (OPEN)
* **Phase 1 Decision D-10 (Confirmed):** Explicitly states: *"A piece is Packed when all its operations are passed; a product is complete when all its pieces are Packed; the SC becomes Delivered automatically when all products are complete. No packing/dispatch screens, statuses, roles or dispatch confirmation."*
* **Phase 2 Design (Parts 2.2 and 2.6):** Defines assembled sets (`assembled_sets`), set completion tracking (`is_production_complete`), final QC verification (`is_final_qc_passed`), physical packing checklists (`packing_records`), staging status (`DISPATCH_PENDING`), and loading dock gate releases (`dispatch_confirmations`).
* **External Boundary Invariant:** Production never creates or receives vendor DCs; RM owns all external vendor DC movements (`D-05, D-09`).
* **Conflict Status:** **OPEN (Conflict C-1).** The engineering team must not unilaterally decide or invent meanings (such as interpreting dispatch challans as legal subcontract lot handovers). This conflict is recorded as an open architectural decision for the project owner to determine whether the completion model remains strictly the automatic piece-cascade of D-10 or the multi-set staging workflow of Phase 2.6.

#### Conflict 2: Spring Boot Version Baseline vs. Official Support Terminology
* **Phase 1 Baseline:** Specifies "Spring Boot 3.x" (`docs/phase-1/13_Tech_Stack.md`).
* **Official Support Status (Spring Boot Governance):** Spring Boot does **not** use the label "LTS". It designates releases as **Active OSS Support**, **Commercial Support**, or **End of Life (EOL)**.
  - Spring Boot 3.3.x and 3.4.x reached open-source community **End of Life (EOL)** on June 30, 2025 and December 31, 2025, respectively. Open-source bug and security patches have ceased (available only via commercial VMware Tanzu / Broadcom support).
  - Spring Boot 4.0.x (released November 2025) has **Active OSS Support** through December 31, 2026.
  - Spring Boot 4.1.x / 4.2.x maintain Active OSS Support through 2027.
* **Status:** Preserved as an open recommendation to the project owner: either stay on Spring Boot 3.4.x (adhering literally to Phase 1 text, with commercial support requirement) or adopt Spring Boot 4.0.x / 4.1.x for active community open-source maintenance.

#### Conflict 3: RM & ECR Integration Endpoints (Real Code Audit vs. Phase 2 Conceptual URIs)
* **Phase 1 Live Code Audit (`09_Integration_Specification.md`):** Discovered real, immutable endpoints in existing production code:
  - **RM Real Endpoints:** `GET /api/sc`, `/api/sc/:id`, `/api/sc/traceability/:poNumber`, `/api/rm`, `/api/rm/stores/queue`, `/api/material-issues`, `/api/material-issues/:id`, `/api/production/receipts`, `/api/delivery-challans`, `/api/delivery-challans/:id`, `/api/delivery-challans/movements`, `/api/ecr-issues`.
  - **ECR Real Endpoint:** `POST {ECR_API_URL}/api/internal/rm-intake` (with header `x-internal-secret`).
* **Phase 2 Design Documents:** Used placeholder / standardized REST URIs (e.g., `GET /api/v1/requisitions/approved`, `POST /api/v1/exceptions/intake`).
* **Resolution Rule:** **Phase 1 real code audit endpoints (`09_Integration_Specification.md`) are authoritative.** RM and ECR code cannot be modified (`D-11, BR-067`). Phase 13 and Phase 14 implementation must bind strictly to the audited real endpoints.

#### Conflict 4: Real-Time Protocol (Phase 1 Decision Log O-07 vs. Phase 2 ADR-06)
* **Phase 1 Decision Log:** `O-07` is marked as `OPEN — BUSINESS DECISION REQUIRED`: *"Real-time mechanism: SSE vs WebSocket."*
* **Phase 2 Architecture:** `ADR-06` formally decided and selected **Server-Sent Events (SSE)** over WebSockets.
* **Status:** Reconciled in Phase 2 via ADR-06; `00_DECISION_LOG.md` O-07 should be formally closed upon review.

---

### 3. Terminology Finding: Variations in Meaning of "SC"

An exhaustive audit of the project documentation reveals that the abbreviation **"SC"** is used with four distinct semantic meanings across different subsystems and design phases:

| Context / Artifact | Term Used for "SC" | Exact Documentation References | Semantic Meaning in Context |
| :--- | :--- | :--- | :--- |
| **RM Integration (V-SMART)** | **"Size Card" (SC Code)** | `docs/phase-1/09_Integration_Specification.md:36`<br/>`docs/updates/DOCS_UPDATE_PACK.md:449` | In RM screens, "SC Code" represents the Size Card code (e.g. part code) stored in `delivery_challan_items.part_number`. |
| **Phase 1 & Phase 2 Prose** | **"Size Card"** | `docs/PROJECT_CONTEXT_FOR_CLAUDE.md:70, 88, 101, 304, 383, 429, 490, 546, 583, 790, 791`<br/>`docs/phase-2/2.2-domain-data-model/01-domain-model-and-hierarchy.md:19, 46, 64, 69, 122, 134, 259`<br/>`docs/phase-2/2.2-domain-data-model/02-entity-catalogue-and-attributes.md:36, 59, 229, 686`<br/>`docs/phase-2/2.2-domain-data-model/03-relationships-and-cardinality.md:287`<br/>`docs/phase-2/2.2-domain-data-model/04-complete-er-diagram.md:361`<br/>`docs/phase-2/2.2-domain-data-model/06-constraints-indexing-and-questions.md:11, 157`<br/>`docs/phase-2/2.3-piece-lifecycle-state-machine/01-piece-lifecycle-overview-and-states.md:10, 85`<br/>`docs/phase-2/2.6-sc-and-completion-state-machine/01-sc-lifecycle-overview-and-states.md:8, 11, 30, 173, 196`<br/>`docs/phase-2/2.6-sc-and-completion-state-machine/02-sc-set-and-component-completion-rules.md:49, 214`<br/>`docs/phase-2/2.6-sc-and-completion-state-machine/04-completion-calculation-and-transition-matrices.md:119`<br/>`docs/phase-2/2.6-sc-and-completion-state-machine/05-sc-completion-diagrams-and-traceability.md:16, 82, 296` | Represents the customer commercial Size Card or production order header governing the overall batch of manufactured products. |
| **Phase 2 Relational Schema** | **`subcontracts` / `subcontract_products`** | `docs/phase-2/2.1-system-architecture/01-system-context-and-boundaries.md:81`<br/>`docs/phase-2/2.1-system-architecture/02-modular-monolith-architecture.md:149`<br/>`docs/phase-2/2.2-domain-data-model/01-domain-model-and-hierarchy.md:55, 56, 123, 152, 153, 259, 260`<br/>`docs/phase-2/2.2-domain-data-model/02-entity-catalogue-and-attributes.md:36, 49, 59, 80, 81`<br/>`docs/phase-2/2.2-domain-data-model/03-relationships-and-cardinality.md:25, 33, 40, 287`<br/>`docs/phase-2/2.2-domain-data-model/04-complete-er-diagram.md:22, 361`<br/>`docs/phase-2/2.2-domain-data-model/05-data-dictionary-core-entities.md:11, 80`<br/>`docs/phase-2/2.2-domain-data-model/06-constraints-indexing-and-questions.md:11, 23, 157`<br/>`docs/phase-2/2.6-sc-and-completion-state-machine/01-sc-lifecycle-overview-and-states.md:8, 30, 173`<br/>`docs/phase-2/2.6-sc-and-completion-state-machine/02-sc-set-and-component-completion-rules.md:214`<br/>`docs/phase-2/2.6-sc-and-completion-state-machine/03-packing-and-dispatch-state-machines.md:214`<br/>`docs/phase-2/2.6-sc-and-completion-state-machine/04-completion-calculation-and-transition-matrices.md:89, 90, 92, 100, 103, 106, 109, 111, 119, 123, 124, 173`<br/>`docs/phase-2/2.6-sc-and-completion-state-machine/05-sc-completion-diagrams-and-traceability.md:165, 213, 215, 230, 284, 296, 298` | Relational table names chosen during Phase 2 domain data modeling to represent subcontract orders and product lines. |
| **ECR Exception Integration** | **"Size Card number"** | `docs/phase-1/09_Integration_Specification.md:42, 62` (`scNumber`) | External intake field on ECR intake forms where `scNumber` represents the "Size Card number" for cost center allocation. |

*Note: Per instruction, the engineering team records this terminology finding without unilaterally selecting a canonical term. Normalization is left for project owner direction.*

---

### 4. Verification Log: Verified Items vs. UNVERIFIED Items

Every technical component has been verified against official vendor documentation or marked `UNVERIFIED`:

#### 4.1 Fully Verified Technologies (Verified November 26, 2026)
* **Java SDK:** `21.0.13.1+2` (Adoptium Eclipse Temurin 21 Releases).
* **Spring Boot:** `3.4.3` (3.x line) and `4.0.3` (4.x line) (Spring Boot Supported Versions Wiki).
* **Flyway Core:** `10.21.0` (Maven Central).
* **Caffeine Cache:** `3.1.8` (Maven Central).
* **Springdoc OpenAPI:** `2.8.5` (Maven Central).
* **Apache POI:** `5.3.0` (Maven Central).
* **Apache PDFBox:** `3.0.4` (Maven Central).
* **PostgreSQL JDBC:** `42.7.5` (Maven Central).
* **Sentry Spring Boot Starter:** `8.3.0` (Maven Central).
* **Testcontainers:** `1.20.4` (Maven Central).
* **ArchUnit:** `1.4.0` (Maven Central).
* **Neon PostgreSQL:** Direct (:5432) vs Pooled (:5432, PgBouncer Transaction Mode) documented (Neon Official Docs).
* **Node.js:** `v24.16.0` (Active) / `22.14.0` (Active LTS) (Node.js Releases).
* **React:** `19.3.1` (npm registry).
* **Vite:** `8.3.8` (npm registry).
* **TypeScript:** `7.0.2` (npm registry).
* **Tailwind CSS:** `4.3.5` (npm registry).
* **TanStack React Query:** `5.66.0` (npm registry).
* **React Hook Form:** `7.54.2` (npm registry).
* **Zod:** `3.24.2` (npm registry).
* **Zustand:** `5.0.3` (npm registry).
* **Recharts:** `2.15.1` (npm registry).
* **Axios:** `1.7.9` (npm registry).
* **Vitest:** `3.0.7` (npm registry).
* **Playwright:** `1.50.1` (npm registry).

#### 4.2 Items Marked UNVERIFIED (Awaiting Live Environment Testing)
* **RM API Response JSON Properties:** `UNVERIFIED` — Marked in Phase 1 `09_Integration_Specification.md` Section 7. Exact property casing must be verified via curl during Phase 13.
* **Cloudflare R2 Account ID & Tokens:** `UNVERIFIED` — Awaits owner account provisioning in Phase 6.
* **Corporate Production SMTP Host & Auth:** `UNVERIFIED` — Awaits Velan IT confirmation of corporate relay host and authentication requirements.

---

### 5. Technical Assumptions Register

1. **ASSUMPTION:** Neon Serverless 0.5 to 2.0 CUs provides adequate throughput for 50 concurrent shop-floor terminals without query latency degradation.
2. **ASSUMPTION:** Render backend sizing (Starter for Dev/Test, Standard 2GB for Prod) provides adequate memory headroom for JVM heap and Apache POI spreadsheet parsing.
3. **ASSUMPTION:** Outbox poller parameters (10s delay, 20 batch size, 5 retries) and Caffeine cache TTLs (15-60 min) are initial tuning baselines subject to Phase 46 load testing.
4. **ASSUMPTION:** Shop floor possesses continuous LAN/Wi-Fi coverage; offline client data caching is excluded from baseline scope.

---

### 6. Open Questions for the Project Owner

* **O-TECH-01 (Spring Boot Support Window):** Does the owner approve adopting **Spring Boot 4.0.x / 4.1.x** (Active OSS Support) or freezing on **Spring Boot 3.4.x** (commercial support required)?
* **O-ENV-01 (Production Network Ingress):** Will shop-floor tablets access the app over public Internet via Render domains, or via private VPN / reverse proxy (`production.velan.internal`)?
* **O-CFG-01 (SMTP Gateway Authentication):** Does enterprise production SMTP require username/password credentials, or internal static IP whitelisting without authentication?
* **O-BLD-01 (Artifact Proxying):** Will Render and GitHub Actions have direct outbound access to Maven Central and npmjs.org, or require internal corporate artifact mirrors?
