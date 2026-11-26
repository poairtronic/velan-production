# Part 3.8 — Testing Standards, Git Workflow, Code Review, and Phase 3 Closure
## Document 03: Code Review Checklists and Multi-Disciplinary Definition of Done

---

### 1. Confirmed Rules

#### 1.1 The Six Mandatory Code Review Checklists
Before any Pull Request is approved or merged to `main`, it must satisfy six systematic review checklists:

#### A. Business Rules Checklist (Phase 1 Compliance)
* [ ] Does the change strictly adhere to approved business rules (`BR-001` through `BR-070`)?
* [ ] Does it respect established role exclusions (Stores, Packing, Dispatch are NOT roles; D-03)?
* [ ] Are the 4 PM dispositions implemented exactly as specified (Continue, Extra, Return, Scrap)?
* [ ] Does the piece replacement logic preserve complete parent-child lineage traceability (D-08)?
* [ ] Is raw material issuance verified before allowing operation start (D-04, D-09)?

#### B. Permissions & RBAC Checklist
* [ ] Is every new endpoint protected by `@PreAuthorize("hasAuthority('MODULE:ACTION')")`?
* [ ] Are permission codes formatted strictly as `MODULE:ACTION` in `UPPER_SNAKE_CASE`?
* [ ] Are UI route guards and action buttons driven **strictly by permission codes and NEVER by role names**?
* [ ] Does the change respect the multi-tier evaluation order (Admin override $\to$ Role deny $\to$ Role grant $\to$ Default deny)?

#### C. Immutable Audit Checklist
* [ ] Do sensitive actions (approvals, concessions, cancellations, overrides, reassignments) emit audit events?
* [ ] Does the audit event include the mandatory `reason` text, `actorUserId`, and `correlationId`?
* [ ] Is the audit write executed synchronously in-transaction with the domain mutation?

#### D. The End-to-End "Linking Chain" Checklist
* [ ] **DB Column:** Does the database column match Part 3.3 data type and naming standards?
* [ ] **JPA Entity:** Does the Java entity property map cleanly to the database column with `@Column`?
* [ ] **Backend DTO:** Does the request/response DTO expose the property in `camelCase` with validation annotations?
* [ ] **OpenAPI Spec:** Does `springdoc-openapi` generate the matching schema under `/v3/api-docs`?
* [ ] **TypeScript Type:** Does `openapi-typescript` generate the matching type in `schema.d.ts`?
* [ ] **React UI Component:** Does the React component bind the typed property in forms and tables?

#### E. Performance & Query Efficiency Checklist
* [ ] Are there zero N+1 query hazards (verified via test query counting or `JOIN FETCH` / `@EntityGraph`)?
* [ ] Are all foreign keys, query filter fields, and sort columns backed by appropriate database indexes?
* [ ] Are list queries bounded by pagination (default: 20, max: 100)?
* [ ] Do grids and tables exceeding 50 rows implement virtualization (`@tanstack/react-virtual`)?

#### F. Security Hardening Checklist
* [ ] Are all incoming payloads validated using Jakarta Bean Validation (`@Valid`)?
* [ ] Are there zero hardcoded secrets, tokens, or passwords in code or configuration files?
* [ ] Are file uploads gated by Cloudflare R2 pre-signed URLs with S3 `HeadObject` verification?
* [ ] Is raw user input sanitized to prevent XSS (zero `dangerouslySetInnerHTML`)?

---

### 2. Proposed Design

#### 2.1 Multi-Disciplinary Definition of Done (DoD)

| Type of Work | Mandatory Completion Criteria (Definition of Done) |
|:---|:---|
| **Backend Feature** | 1. Clean architecture separation (domain entities free of framework / adapter leaks).<br/>2. Unit test coverage $\ge 90\%$ on state transitions and business rules.<br/>3. Slice / Testcontainers integration tests verifying repository and controller behavior.<br/>4. All error paths throw typed domain exceptions returning standard `ApiErrorResponse`.<br/>5. ArchUnit rules pass without violations. |
| **Frontend Feature** | 1. 100% TypeScript compile with strict mode enabled (zero `any`).<br/>2. React Hook Form + Zod validation matching backend constraints.<br/>3. UI access governed strictly by permission codes (`<RequirePermission>`).<br/>4. Handles Loading, Empty, Error, and Permission-Denied viewport states.<br/>5. Vitest component tests pass with MSW network mocking.<br/>6. Zero critical or serious accessibility violations (`axe-core`). |
| **Database Migration** | 1. Flyway script follows `V{n}__description.sql` naming with monotonic numbering.<br/>2. Tested against clean Testcontainers PostgreSQL 16 database.<br/>3. Foreign keys, check constraints, and indexes adhere to Part 3.3 standards.<br/>4. Migration executes in $< 10\text{ seconds}$ without taking long-lived table locks.<br/>5. Zero destructive down-migrations (forward-only policy). |
| **Integration Adapter** | 1. Resides strictly within `integration.rm` or `integration.ecr` packages.<br/>2. Zero in-transaction HTTP calls.<br/>3. WireMock contract tests pass against sanitized sample payloads.<br/>4. External fields mapped in the master VERIFY register.<br/>5. Gated by feature flags (`RM_SYNC_ENABLED`, `ECR_SEND_ENABLED`). |
| **Documentation** | 1. Prescriptive Markdown under 400 lines per document.<br/>2. Categorized into Confirmed rules, Proposed design, Technical assumptions, Open questions.<br/>3. Zero unilateral decisions on open conflicts (C-1, C-2, C-3, O-xx).<br/>4. All file and line citations verified against actual repository files. |

#### 2.2 Standard Completion Report Template
Every development phase or session must conclude with the standard Section 13 completion report:
```markdown
### Completion Report: Phase X, Part Y — Title

#### 1. Task Completed
* **Phase & Part:**
* **Scope Adherence:**

#### 2. Deliverables Created or Modified
* [file-path](file:///...) (line count)

#### 3. Decisions Applied
* Decisions from 00_DECISION_LOG.md and approved design parts applied.

#### 4. Open Items & Conflicts Recorded
* Open questions or conflicts marked formally OPEN.

#### 5. Errors Found in Previous Work
* Specific discrepancies identified with exact citations.

#### 6. Git Status
* Branch, clean status, or uncommitted files listed.

#### 7. Next Recommended Step
* Immediate next sequential phase or review checkpoint.
```

---

### 3. Technical Assumptions
1. **ASSUMPTION:** The developer and review agents enforce these checklists on 100% of pull requests prior to merging to `main`.

---

### 4. Open Questions
* **O-DOD-01 (Automated PR Checklist Validation):** Should a GitHub Action bot automatically block PR merge if the markdown checklist items in the PR description are not 100% checked?
