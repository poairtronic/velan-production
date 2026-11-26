# Part 3.6 — Frontend Standards
## Document 05: Frontend Performance, Code Splitting, Component Tests, and Playwright E2E Journeys

---

### 1. Confirmed Rules

#### 1.1 Route-Based Code Splitting
To guarantee sub-second page loads on shop-floor tablets, all top-level module viewports are split into lazy-loaded chunks using `React.lazy()`:

```tsx
const StationWorklistPage = lazy(() => import('@/modules/execution/pages/StationWorklistPage'));
const BlindQCInspectionPage = lazy(() => import('@/modules/quality/pages/BlindQCInspectionPage'));
const PMExceptionCockpitPage = lazy(() => import('@/modules/exceptions/pages/PMExceptionCockpitPage'));
```
* **Suspense Fallbacks:** Every lazy route is wrapped in `<Suspense fallback={<PageSkeleton />}>` to avoid jarring blank screens during chunk downloads.
* **Vendor Chunking:** Vite `rollupOptions` partitions large third-party dependencies (Radix UI, TanStack Query, Axios, Lucide icons) into cached vendor chunks.

#### 1.2 List & Grid Virtualization Policy
* **Threshold:** Any table exceeding **50 rows** (or bulk grids exceeding **25 piece rows**) must implement row virtualization via **`@tanstack/react-virtual`**.
* Only visible DOM elements plus a 5-row overscan buffer are rendered, keeping DOM node count under 1,500 nodes and guaranteeing 60 FPS scrolling on mobile tablets.

#### 1.3 High-Frequency Render Performance Rules
1. **Grid Cell Memoization:** Measurement grid cells must be wrapped in `React.memo` with a custom comparison function checking only cell value, status, and edit state.
2. **Stable Handler Callbacks:** All cell change handlers passed into grid rows must use `useCallback` or pass coordinate identifiers (`(rowIdx, colIdx, val) => ...`), preventing full-grid re-renders on every keystroke.
3. **No Inline Object Allocations:** Avoid inline closures and array allocations inside JSX loops (`style={{ ... }}`, `onClick={() => ...}`).

#### 1.4 Component Unit & Integration Testing Standard
* **Tools:** **Vitest**, **React Testing Library**, and **MSW (Mock Service Worker)**.
* **Testing Policy:** Tests interact exclusively through user-visible elements (`getByRole`, `getByLabelText`, `userEvent.click`). Testing component internal state or private methods is strictly prohibited.
* **MSW Network Mocking:** API requests are intercepted at the network level by MSW, verifying query states, loading skeletons, and server error toasts.

---

### 2. Proposed Design

#### 2.1 Prescriptive Playwright End-to-End Testing Journeys (10 Journeys)

Playwright runs in CI against containerized backend environments, verifying critical Phase 1 and Phase 2 workflows:

| # | Playwright E2E Journey | Starting Role | Actions Performed | Verification & Completion Invariant |
|:---:|:---|:---|:---|:---|
| **J-01** | **Operator Worklist & Job Start** | `ROLE_OPERATOR` | Logs in, selects lathe station, scans piece serial `V-1024-001`, clicks "Start Operation". | Operation transitions `READY` $\to$ `OPENED`; start timestamp recorded; screen switches to active execution mode. |
| **J-02** | **Bulk Measurement & Submit** | `ROLE_OPERATOR` | Opens bulk entry grid for 5 pieces, pastes readings from clipboard, verifies tolerance colors, clicks "Submit". | Validation summary dialog appears; operation transitions `OPENED` $\to$ `QC_PENDING`; piece routed to QC inspection queue. |
| **J-03** | **Blind QC Pass Verdict** | `ROLE_QC` | Opens QC Pending queue, claims piece `V-1024-001`. Input fields are **completely blank** (no operator values). Enters in-spec readings, clicks "Approve". | `qc_inspections.verdict = 'PASSED'`; current operation marked `PASSED`; next sequential operation unlocked to `READY`. |
| **J-04** | **QC Failure & Instant P1 Alert** | `ROLE_QC` | Inspects piece `V-1024-002`, enters out-of-spec dimension (+0.15mm), submits non-conformance. | Operation marked `FAILED`; piece marked `ON_HOLD_QC_FAILED`; SSE broadcasts `QC_FAILED_ALERT`; alert toast rings in PM Cockpit. |
| **J-05** | **PM Concession Disposition** | `ROLE_PM` | Opens PM Cockpit, reviews out-of-spec piece, selects "CONTINUE" concession, types mandatory engineering reason, clicks "Apply". | Exception closed; operation marked `CONCESSION_ACCEPTED`; next operation unlocked to `READY`; piece status returned to `IN_PRODUCTION`. |
| **J-06** | **PM Scrap & Replacement Piece** | `ROLE_PM` | Selects "REJECT AND RESTART" disposition on unrecoverable bore, confirms scrap modal. | Physical piece marked `SCRAPPED`; new replacement piece `V-1024-002-R1` instantiated; ECR outbox scrap event created; lineage linked. |
| **J-07** | **PM Rework Rewind** | `ROLE_PM` | Selects "RETURN TO EARLIER OP" disposition, targets Operation 10, confirms rewind. | Operation 10 marked `READY`; iteration counter incremented (1 $\to$ 2); historical failed measurements preserved; piece status `IN_PRODUCTION_REWORK`. |
| **J-08** | **RM Material Gate Block & Override**| `ROLE_OPERATOR` / `ROLE_PM` | Operator attempts to release unissued piece (blocked with HTTP 412). PM clicks manual material override with reason. | Operator station button unlocks immediately via real-time SSE event; piece transitions to `READY_FOR_PRODUCTION`. |
| **J-09** | **Excel Contract Import & Validation**| `ROLE_DESIGNER` | Uploads contract Excel file with invalid quantity on row 4. Reviews row-level error banner, fixes file, re-uploads. | Contract and product line items created; audit event emitted; summary reflects 100% success. |
| **J-10** | **Vendor Return & Inward Inward** | `ROLE_OPERATOR` / `ROLE_QC` | Scans piece returned from external heat treatment vendor, completes inspection. | Operation completed out-of-sequence; subsequent in-house sequential operations unlocked. |

#### 2.2 Automated Accessibility & Visual Regression Checks
* **Accessibility Audits:** Every Playwright journey executes `@axe-core/playwright` scanning on rendered viewports, asserting zero critical or serious WCAG 2.1 AA violations.
* **Visual Regression:** Golden snapshot tests (`expect(page).toHaveScreenshot()`) executed on key screens (Bulk Grid, PM Cockpit, Blind QC View) to catch unintended CSS regressions.

---

### 3. Technical Assumptions
1. **ASSUMPTION:** Initial JavaScript entry bundle size is bounded to **$< 200\text{ KB}$ gzipped**; vendor chunk is bounded to **$< 350\text{ KB}$ gzipped**; total initial load is **$< 500\text{ KB}$ gzipped**.
2. **ASSUMPTION:** Playwright tests run headlessly in Chromium inside Docker CI pipelines with execution times $< 5\text{ minutes}$ across all 10 journeys.

---

### 4. Open Questions
* **C-1 (Completion Workflow Model):** Playwright E2E journey for assembled set packing checklist and loading dock gate release remains conditioned on Conflict C-1.
