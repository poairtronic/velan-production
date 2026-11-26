# Velan Production – Phase 3: Technical Specification and Architecture Standards

## 1. Executive Summary

Phase 3 establishes the complete, production-grade engineering standards, architectural baselines, coding conventions, database schemas, REST API contracts, security architectures, frontend standards, integration adapter protocols, quality processes, and definition of done for the **Velan Production System**.

The platform is engineered as a high-reliability, modular monolithic manufacturing execution system (MES) designed to govern the fabrication, dimensional tolerance inspection, rework routing, scrap tracking, and delivery of precision industrial valves. Phase 3 bridges the abstract business requirements from Phase 1 and the domain models from Phase 2 into concrete, prescriptive technical rules ready for physical implementation starting in Phase 4.

---

## 2. Phase 3 Part-by-Part Architecture Breakdown

Phase 3 is organized into eight sequentially authored and reviewed parts comprising **51 documents** *(49 documents at Phase 3 closure `daf3941`, plus 2 documents added to Part 3.2 in Phase 4.7)*:

| Part | Title | Core Scope & Deliverables | Status |
|:---|:---|:---|:---:|
| **[Part 3.1](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.1-technology-specification/)** | **Technology Specification & Environments** | Java 21, Spring Boot 3.4, React 19, Vite 6, Tailwind CSS; Neon connection architecture (Flyway direct vs PgBouncer `-pooler` pooled, both on port `:5432`); Cloudflare R2 pre-signed uploads (15-min expiry); in-process Caffeine cache; transactional outbox; Render deployment. | **Approved** |
| **[Part 3.1a](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.1-technology-specification/05-part-3.1-report.md)** | **Neon Connection & Version Corrections** | Correction pass reconciling Neon pooled vs direct connection strings and precise versioning. | **Approved** |
| **[Part 3.2](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.2-backend-standards/)** | **Backend Architecture & Coding Standards** | Modular monolith packaging (`api/` vs `internal/`); unified 10-state-machine domain service pattern (`07`); coding conventions, Part 3.4 error mapping, and OPEN JPA entity policy (`08`); Spring Application Events; transactional boundaries; JPA `@Version` optimistic locking; bounded Caffeine caching. | **Approved (07–08 added in 4.7)** |
| **[Part 3.3](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.3-database-and-naming-conventions/)** | **Database & Naming Conventions** | Domain glossary; "SC" terminology resolution; cross-tier naming rules; forbidden names; PostgreSQL 16 types; native enum ban; append-only relational triggers; Flyway monotonic forward-only migrations; 18 Phase 2 conformance deviations catalogued. | **Approved** |
| **[Part 3.4](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.4-api-standards/)** | **REST API & Serialization Standards** | Base `/api/v1`; measurement decimal serialization as **JSON Strings**; `ApiResponse<T>` envelope; aggregate action pattern; standardized error envelope `{ code, message, details, timestamp, path, correlationId }`; 68 domain error codes; SSE stream; R2 pre-signed uploads; 49 primary endpoints. | **Approved** |
| **[Part 3.5](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.5-security-audit-logging/)** | **Security, RBAC, Audit & Observability** | Individual login (D-03); Argon2id password hashing; JWT (15 min) + HttpOnly sliding refresh cookie (7 days); PM Reassignment Rule; strictly 6 roles; multi-tier RBAC evaluation order (Admin override $\to$ Role deny $\to$ Role grant $\to$ Default deny); 10 mandatory audited actions; append-only audit defense; structured JSON logs; Sentry 5xx tracking; OWASP checklist. | **Approved** |
| **[Part 3.6](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.6-frontend-standards/)** | **Frontend Engineering Standards** | React 19, Vite 6, TypeScript strict mode, Tailwind CSS; feature-modular `src/modules/`; Axios interceptors with 401 refresh queue; TanStack Query v5; three-tier state partitioning; **Bulk Measurement Grid Specification** (5 parameter types, Excel paste, virtualization, local draft, non-blocking tolerance alert BR-032); **The Blind QC Screen Rule** (blank inputs BR-032/BR-033); permission-driven routing (zero role strings); 10 Playwright E2E journeys. | **Approved** |
| **[Part 3.7](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.7-integration-conventions/)** | **Integration Adapter Conventions** | `integration.rm` and `integration.ecr` with Anti-Corruption Layer (ACL); strict boundaries (**Production never writes to RM BR-067**, never creates DCs); 4 inbound RM flows; return status derived from quantities; ECR outbox `POST /api/internal/rm-intake` with `x-internal-secret` and `x-idempotency-key`; **no-auto-resend on ambiguous timeout (BR-062)**; Phase 2 Doc 04 reconciliation; master VERIFY register; 7 design gaps for missing Part 2.7. | **Approved** |
| **[Part 3.8](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.8-quality-process/)** | **Quality Process & Phase 3 Closure** | Test pyramid (70/20/10); Test Data Builders; parameterized transition-matrix testing for all 10 state machines; multi-threaded concurrency tests; 2D permission matrix (294 assertions); ArchUnit rules; peak load baseline (15–20 users); protected `main`, Conventional Commits, Squash merge; 6 review checklists; DoD per work type; agent prompt protocol linked to `SKILLS.md`; master index & consolidated conflict register; `.github/pull_request_template.md`. | **Approved** |

---

## 3. Directory Structure of Phase 3

```text
docs/phase-3/
├── README.md                                           <-- This roadmap & consolidated index
├── 3.1-technology-specification/
│   ├── 01-technology-stack-and-versions.md
│   ├── 02-environments-and-hosting.md
│   ├── 03-environment-configuration.md
│   ├── 04-dependency-and-build-policy.md
│   ├── 05-part-3.1-report.md
│   └── 06-part-3.1a-correction-report.md
├── 3.2-backend-standards/
│   ├── 01-module-structure-and-dependency-rules.md
│   ├── 02-domain-services-and-state-machine-pattern.md
│   ├── 03-transactions-concurrency-and-idempotency.md
│   ├── 04-coding-conventions-and-layering.md
│   ├── 05-performance-and-caching-rules.md
│   ├── 06-part-3.2-report.md
│   ├── 07-domain-services-and-state-machine-pattern.md
│   └── 08-coding-conventions-and-layering.md
├── 3.3-database-and-naming-conventions/
│   ├── 01-glossary-and-naming-conventions.md
│   ├── 02-schema-and-data-type-conventions.md
│   ├── 03-flyway-and-migration-standards.md
│   ├── 04-constraints-and-indexing-standards.md
│   ├── 05-phase-2-conformance-findings.md
│   └── 06-part-3.3-report.md
├── 3.4-api-standards/
│   ├── 01-rest-conventions-and-envelope.md
│   ├── 02-error-handling-and-error-catalogue.md
│   ├── 03-pagination-filtering-idempotency-and-bulk.md
│   ├── 04-files-sse-and-openapi.md
│   ├── 05-endpoint-inventory.md
│   └── 06-part-3.4-report.md
├── 3.5-security-audit-logging/
│   ├── 01-authentication-and-session-standard.md
│   ├── 02-rbac-and-permission-catalogue.md
│   ├── 03-audit-logging-standard.md
│   ├── 04-application-logging-and-observability.md
│   ├── 05-security-checklist.md
│   └── 06-part-3.5-report.md
├── 3.6-frontend-standards/
│   ├── 01-project-structure-and-conventions.md
│   ├── 02-data-fetching-state-and-forms.md
│   ├── 03-ui-system-and-bulk-entry-grid.md
│   ├── 04-realtime-permissions-and-routing.md
│   ├── 05-frontend-performance-and-testing.md
│   └── 06-part-3.6-report.md
├── 3.7-integration-conventions/
│   ├── 01-integration-architecture-and-boundaries.md
│   ├── 02-rm-read-connector-standard.md
│   ├── 03-ecr-outbox-standard.md
│   ├── 04-contract-testing-and-sample-capture.md
│   ├── 05-reconciliation-monitoring-and-phase-2-7-gap-list.md
│   └── 06-part-3.7-report.md
└── 3.8-quality-process/
    ├── 01-testing-standards.md
    ├── 02-git-workflow-and-release.md
    ├── 03-code-review-and-definition-of-done.md
    ├── 04-agent-prompt-and-traceability-standard.md
    ├── 05-phase-3-closure.md
    └── 06-part-3.8-report.md
```

---

## 4. Master Conflict Register (Formal Open Items)

In strict adherence to `SKILLS.md §0`, all known conflicts and open design questions remain formally OPEN without unilateral engineering decisions:

1. **Conflict C-1 (Completion Workflow Model — OPEN):** Decision D-10 automatic cascade vs. Phase 2.6 assembled sets, packing checklists, and dispatch bays. *(Target: Phase 7 / 26)*.
2. **Conflict C-2 (Piece Count Derivation Source — OPEN):** Decision D-02 direct RM quantity vs. target sets $\times$ component BOM ratio. *(Target: Phase 7 / 18)*.
3. **Conflict C-3 (The "SC" Terminology Problem — OPEN with Recommendation):** Adopt neutral technical name `sc_headers` and `sc_products`. *(Target: Phase 7 / 18)*.
4. **O-01 (PO Reference Date as Delivery Deadline — OPEN):** Confirm mapping of `purchase_orders.reference_date`. *(Target: Phase 13)*.
5. **O-04 (ECR Additional Process Category — OPEN):** Native intake enumeration vs. `[ADDITIONAL PROCESS]` prefix in `defectDescription`. *(Target: Phase 14 / 31)*.
6. **O-09 (Shop-Floor Credential Policy — OPEN):** Password vs. 6-digit numeric PIN vs. Badge tap + PIN. *(Target: Phase 8 / 24)*.
7. **O-10 (Material Issued Threshold — OPEN):** Any partial issuance ($\sum > 0$) vs. 100% issuance required for product readiness. *(Target: Phase 22)*.
8. **O-11 (Audit Log Retention Policy — OPEN):** 7 years vs. 10 years vs. 3-year hot / cold archive. *(Target: Phase 8 / 42)*.
9. **O-12 (Process Template Editing Authority — OPEN):** Admin only vs. PM if granted (Designer selects only). *(Target: Phase 11)*.
10. **O-FE-01 (Repository Architecture — THE SOLE DECISION BLOCKING PHASE 4):** Monorepo layout approval (`/backend` + `/frontend`). *(Target: Phase 4)*.

---

## 5. Phase 4 Readiness Gate

* **Readiness Status:** **GREEN / READY TO PROCEED**.
* **Pre-Flight Decision Required:** Approval of **`O-FE-01: Monorepo Architecture`**.
* **Next Step:** Upon owner approval of `O-FE-01`, proceed directly to **Phase 4 — Project Skeleton and Build Configuration**.
