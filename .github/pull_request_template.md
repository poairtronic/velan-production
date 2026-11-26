## Pull Request Description

### 1. Summary & Scope
* **Phase & Part Number:** Phase X, Part Y
* **Type of Change:**
  - [ ] `feat`: New user-facing feature or domain capability
  - [ ] `fix`: Bug fix in application or UI logic
  - [ ] `docs`: Documentation changes only
  - [ ] `db`: Flyway database migration
  - [ ] `test`: Adding or refactoring automated tests
  - [ ] `refactor`: Internal code improvements without functional change
  - [ ] `chore`: Tooling, build scripts, or dependency updates

---

### 2. Mandatory Quality & Definition of Done Checklist

#### A. Business Rules & Phase 1 Compliance
* [ ] Adheres strictly to approved business rules (`BR-001` through `BR-070`).
* [ ] Respects strict role boundaries (Stores, Packing, Dispatch, Vendor Coordinator are NOT roles; D-03).
* [ ] Production never writes to RM (BR-067) and never creates Delivery Challans (D-05).
* [ ] Piece replacement preserves complete parent-child lineage traceability (D-08).
* [ ] Zero unilateral decisions on open conflicts (C-1, C-2, C-3, O-xx).

#### B. Permissions & RBAC
* [ ] Every new endpoint is guarded by `@PreAuthorize("hasAuthority('MODULE:ACTION')")`.
* [ ] Permission codes follow `MODULE:ACTION` in `UPPER_SNAKE_CASE`.
* [ ] UI route guards and action buttons are driven **strictly by permissions, NEVER by role names**.
* [ ] Respects multi-tier evaluation order (Admin override $\to$ Role deny $\to$ Role grant $\to$ Default deny).

#### C. Immutable Audit Logging
* [ ] Sensitive actions (approvals, concessions, cancellations, overrides, reassignments) emit audit events.
* [ ] Mandatory justification `reason` text, `actorUserId`, and `correlationId` are captured.
* [ ] Audit event write occurs in the exact same local database transaction as the domain mutation.

#### D. End-to-End Linking Chain
* [ ] **DB Column:** Matches Part 3.3 data type and naming standards.
* [ ] **JPA Entity:** Maps cleanly with `@Column` and `@Version` integer optimistic locking.
* [ ] **Backend DTO:** Exposes properties in `camelCase` with Jakarta validation annotations.
* [ ] **OpenAPI Spec:** Spec updated under `/v3/api-docs` with descriptive examples.
* [ ] **TypeScript Type:** Generated in `schema.d.ts` via `openapi-typescript`.
* [ ] **React UI:** Binds typed property in forms and tables without type assertions (`as any`).

#### E. Performance & Query Efficiency
* [ ] Zero N+1 query hazards (verified via test query count or `JOIN FETCH` / `@EntityGraph`).
* [ ] Foreign keys, filter columns, and sort fields are backed by database indexes.
* [ ] List queries bounded by pagination (default: 20, max: 100).
* [ ] Tables and grids exceeding 50 rows implement virtualization (`@tanstack/react-virtual`).

#### F. Security Hardening
* [ ] All incoming DTOs validated with `@Valid` at controller boundaries.
* [ ] Zero hardcoded secrets, tokens, or credentials in code or configuration.
* [ ] File uploads gated by Cloudflare R2 pre-signed URLs with S3 `HeadObject` validation.
* [ ] Zero `dangerouslySetInnerHTML` in React components.

---

### 3. Automated Verification Evidence
* [ ] Unit tests pass (`mvn test` / `npm run test:unit`) with $\ge 90\%$ domain coverage.
* [ ] Testcontainers PostgreSQL integration tests pass (`mvn verify`).
* [ ] ArchUnit modular monolith architecture rules pass.
* [ ] OpenAPI breaking change checks pass (`oasdiff`).
* [ ] Playwright E2E journeys pass headlessly (`npm run test:e2e`).
* [ ] Tests make zero network calls to real external systems (RM, ECR, R2, SMTP).
