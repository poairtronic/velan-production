# Part 3.6 — Synthesis Report, Frontend Governance, and Conformance Findings
## Document 06: Part 3.6 Synthesis Report, Frontend Conformance Audit, and Open Questions

---

### 1. Executive Summary & Deliverables Completed

Part 3.6 establishes the definitive frontend engineering architecture, project structure, data fetching and mutation lifecycle, state partitioning, UI component systems, the shop-floor bulk measurement grid specification, the blind QC inspection invariant, real-time SSE query invalidation, permission-driven route guards, and Playwright E2E testing journeys for the Velan Production platform.

The following six specification documents have been authored under `docs/phase-3/3.6-frontend-standards/`:
1. **[01-project-structure-and-conventions.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.6-frontend-standards/01-project-structure-and-conventions.md):** Feature-modular directory layout under `src/` (`app/`, `modules/<module>/`, `shared/`, `api/`, `hooks/`, `routes/`, `types/`); explicit path aliases; strict module encapsulation boundary rules (no cross-module deep imports); TypeScript strict compiler settings (`strict: true`, `noUncheckedIndexedAccess: true`); tooling governance (ESLint 9 flat config, Prettier, typescript-eslint); functional component and custom hook architecture; centralized Zod startup validation for `VITE_*` environment variables.
2. **[02-data-fetching-state-and-forms.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.6-frontend-standards/02-data-fetching-state-and-forms.md):** Centralized Axios client with automatic `Authorization: Bearer` injection, correlation ID tracing, CSRF defense headers, transparent 401 token refresh queues, and typed `ApiError` unboxing; TanStack Query v5 hierarchical key factories; strict mutation invalidation; prescriptive optimistic update policy (allowed only for non-critical reversible UI states; strictly forbidden for physical state transitions, measurements, QC verdicts, and dispositions); three-tier state partitioning (Server state in TanStack Query, global UI state in Zustand, transient state in React Hook Form / local state); consuming generated OpenAPI TypeScript types; automatic mapping of backend HTTP 422 `details[]` into React Hook Form field errors.
3. **[03-ui-system-and-bulk-entry-grid.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.6-frontend-standards/03-ui-system-and-bulk-entry-grid.md):** Tailwind CSS industrial design tokens (Slate neutral scale, emerald/amber/rose/blue semantic status tokens); accessible shadcn/ui and Radix UI component primitives; 44px touch ergonomics for 10-inch rugged shop-floor tablets; WCAG 2.1 AA accessibility standards; 4 standard viewport states (Loading, Empty, Error, Permission-Denied); **Comprehensive Bulk Measurement Grid Specification:** Rows = pieces, columns = parameters, 5 parameter types (numeric, yes/no, text, signature, image), Excel clipboard copy/paste parsing, full keyboard navigation (arrows, enter, tab), row virtualization above 25 rows (`@tanstack/react-virtual`), IndexedDB local draft auto-save, client-side non-blocking tolerance highlighting (BR-032); **The Blind QC Screen Rule:** Quality Control inspection screens must **NEVER** pre-fill or display operator measurements to eliminate cognitive anchoring bias (BR-032, BR-033).
4. **[04-realtime-permissions-and-routing.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.6-frontend-standards/04-realtime-permissions-and-routing.md):** Real-time Server-Sent Events (SSE) client using `@microsoft/fetch-event-source` with exponential backoff (1s to 30s), `Last-Event-ID` tracking, and automatic query cache invalidation matrix; **Strict Permission-Driven UI Access Rule:** Navigation menus, route guards (`<RequirePermission>`), and action buttons (`<PermissionGate>`) are driven **strictly by permission codes (`MODULE:ACTION`) and never by role strings**; session expiry redirection; React ErrorBoundary crash recovery; slide-over notification centre drawer with audible P1 alerts.
5. **[05-frontend-performance-and-testing.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.6-frontend-standards/05-frontend-performance-and-testing.md):** Route-level code splitting with `React.lazy()` and Suspense skeletons; bundle budget benchmarks; list virtualization thresholds (> 50 rows); high-frequency grid cell memoization (`React.memo`) and stable callback references; Vitest + React Testing Library + MSW component testing; **10 Prescriptive Playwright E2E Journeys** covering operator execution, bulk grid submit, blind QC inspection, QC failure and instant SSE P1 alert, PM concession disposition, scrap and replacement piece generation, rework rewind, RM material gate block and manual override, Excel contract import, and vendor return processing; automated accessibility audits (`@axe-core/playwright`) and golden visual regression snapshots.
6. **[06-part-3.6-report.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.6-frontend-standards/06-part-3.6-report.md):** (This document) Comprehensive synthesis report, conflict register, technical assumptions audit, and open questions.

---

### 2. Comprehensive Conflict Register

In accordance with `SKILLS.md §0`, all known architectural conflicts remain formally OPEN without unilateral engineering decisions:

#### Conflict C-1: Completion Model — D-10 vs. Phase 2 Parts 2.2 and 2.6 (OPEN)
* **Conflict Statement:** Decision `D-10` specifies that a piece is Packed when all operations pass; a product is complete when all its pieces are Packed; the SC becomes Delivered automatically without packing or dispatch screens/statuses/roles. Conversely, Phase 2 Parts 2.2 and 2.6 specify multi-component assembled sets (`assembled_sets`), physical packing checklists (`packing_records`), staging bays (`DISPATCH_PENDING`), and loading dock gate releases (`dispatch_confirmations`).
* **Frontend Standards Status:** **OPEN.** Specialized routes (`/dispatch/sets`), menu items, and packing checklist forms are catalogued in Documents 03, 04, and 05 but explicitly marked as conditional and OPEN under Conflict C-1.

#### Conflict C-2: Piece Count Derivation Source (OPEN)
* **Conflict Statement:** Decision `D-02` specifies that piece count per product is derived directly from the quantity RM sends for that product line. Phase 2 Part 2.2 calculates piece demand as target assembled sets $\times$ component BOM ratio.
* **Frontend Standards Status:** **OPEN.** Contract import UI screens and summary dialogs support displaying either direct piece counts or BOM-calculated quantities.

#### Conflict C-3: The "SC" Terminology Problem (OPEN with Recommendation)
* **Conflict Statement:** "SC" carries four different meanings across systems: RM screen "Size Card (SC Code)", Phase 1/2 prose "Size Card", Phase 2 tables `subcontracts`, and ECR field "Size Card number".
* **Recommendation:** Adopt user-facing UI labels **"Size Card (SC)"** and internal route paths `/orders` or `/sales-contracts`.
* **Status:** **OPEN.** Submitted for project owner direction before Phase 6 frontend implementation.

---

### 3. Consolidated Technical Assumptions Register

1. **ASSUMPTION:** Vite 6 / React 19 SPAs target evergreen desktop and tablet browsers (Chrome 120+, Edge 120+, Firefox 120+, Safari 17+) installed on shop-floor terminals.
2. **ASSUMPTION:** Bundle budget targets for production builds: initial entry JS bundle $< 200\text{ KB}$ gzipped; vendor chunk $< 350\text{ KB}$ gzipped; total initial load $< 500\text{ KB}$ gzipped.
3. **ASSUMPTION:** Rugged shop-floor touch tablets are configured with minimum $1280 \times 800$ screen resolution and support standard clipboard API events (`navigator.clipboard.readText()`).
4. **ASSUMPTION:** Playwright end-to-end tests execute headlessly inside Docker CI pipelines with execution times $< 5\text{ minutes}$ across all 10 journeys.

---

### 4. Errors & Inconsistencies Found in Previous Work

The following frontend architecture discrepancies were identified in earlier documentation and reconciled in Part 3.6:

1. **React Version Baseline Discrepancy:**
   * *Citation:* `docs/phase-2/2.1-system-architecture/03-frontend-and-infrastructure-architecture.md:5`
   * *Issue:* Document referenced "React 18+".
   * *Correction:* Reconciled and standardized across Part 3.1 and Part 3.6 to **React 19**, Vite 6, and TypeScript strict mode.
2. **State Management Scope Clarification:**
   * *Citation:* `docs/phase-2/2.1-system-architecture/03-frontend-and-infrastructure-architecture.md:24,54`
   * *Issue:* Proposed using Zustand for scanner buffers without explicit separation from server entity state.
   * *Correction:* Formalized the three-tier state partitioning model in Part 3.6 Document 02: TanStack Query owns 100% of server state, Zustand owns global UI state (station lock, scanner buffer, modal state), and React Hook Form owns form state.
3. **Column Naming Reference in Execution Specs:**
   * *Citation:* `docs/phase-2/2.4-operation-lifecycle-state-machine/04-operator-execution-qc-and-pm-dispositions.md:12`
   * *Issue:* Referenced `subcontract_products.material_ready`.
   * *Correction:* Aligned with Part 3.3 Document 05 conformance findings: boolean properties exposed to the frontend use `isMaterialReady` on `sc_products`.

---

### 5. Open Questions Submitted for the Project Owner

* **O-FE-01 (Repository Structure):** Should the React frontend and Spring Boot backend be maintained as a Maven/Node monorepo in a single repository with coordinated CI builds, or split into two separate repositories?
* **O-09 (Shop-Floor Credential Policy — OPEN):** Depending on the project owner's resolution of O-09, should the operator login screen provide a numeric touch-PIN keypad widget, or standard username/password inputs?
* **Conflict C-1 (Completion Workflow Model — OPEN):** Should frontend route `/dispatch/sets` and packing checklist screens be built, or will the automatic completion cascade of Decision D-10 eliminate packing and dispatch screens entirely?
