# Part 3.8 — Testing Standards, Git Workflow, Code Review, and Phase 3 Closure
## Document 02: Git Branching Workflow, Conventional Commits, CI Gates, and Release Governance

---

### 1. Confirmed Rules

#### 1.1 Protected Main Branch Governance
1. **Zero Direct Pushes:** Direct commits and pushes to `main` are permanently blocked by branch protection rules.
2. **Pull Request Gate:** All code enters `main` exclusively through Pull Requests that pass all automated CI check suites.
3. **Linear History:** Merges to `main` use **Squash and Merge** exclusively, ensuring a clean, bisect-friendly commit log.

#### 1.2 Prescriptive Branch Naming Taxonomy
Branches must strictly follow standard naming conventions:
* **Features:** `feature/<phase>-<feature-slug>` (e.g. `feature/13-rm-read-connector`, `feature/24-operator-bulk-grid`).
* **Bug Fixes:** `fix/<issue-slug>` (e.g. `fix/tolerance-rounding-error`, `fix/outbox-timeout-handling`).
* **Documentation:** `docs/<topic-slug>` (e.g. `docs/phase-3-standards`).
* **Hotfixes:** `hotfix/<slug>` (branched from release tag for emergency production patches).

#### 1.3 Conventional Commits Standard
Commit messages on PR squashes and feature branches must follow the **Conventional Commits 1.0.0** specification:

```text
<type>(<scope>): <concise imperative description>

[optional body explaining technical rationale]

[optional footer referencing issue or requirement ID]
```

* **Allowed Types:**
  - `feat`: New user-facing feature or domain capability.
  - `fix`: Bug fix in application or UI logic.
  - `docs`: Documentation changes only.
  - `test`: Adding or refactoring tests.
  - `refactor`: Code change that neither fixes a bug nor adds a feature.
  - `perf`: Performance improvement or database index optimization.
  - `chore`: Build tooling, dependency bumps, or configuration changes.
* **Allowed Scopes:** `execution`, `quality`, `planning`, `orders`, `integration`, `security`, `db`, `frontend`.
* **Example:** `feat(execution): implement Excel paste parsing in bulk measurement grid (FR-010)`

#### 1.4 Database Migration Review Rule
* Every Flyway SQL script (`V{n}__description.sql`) must be reviewed against lock timeouts, statement timeouts, and backward compatibility.
* Every migration must pass automated execution against a clean Testcontainers PostgreSQL 16 database in CI prior to PR merge.

#### 1.5 Rollback Strategy
1. **Application Code Rollback:** Render provides instant 1-click redeployment of any previous Git commit hash or container image within 60 seconds.
2. **Database Rollback:** Database migrations are strictly **forward-only**. Running down-migrations (`undo` scripts) in production is forbidden. Rollbacks are executed by appending a new corrective migration (`V{n+1}__revert_...sql`).

---

### 2. Proposed Design

#### 2.1 Automated CI Check Suites (Phase 49 Implementation Gates)
During Phase 49 (CI/CD Pipeline Setup), GitHub Actions will enforce 9 mandatory automated gates:
1. **`Lint & Format Verification`** (ESLint 9, Prettier, Checkstyle).
2. **`TypeScript Strict Compilation Check`** (`tsc --noEmit`).
3. **`Backend Unit & Slice Tests`** (JUnit 5, Mockito).
4. **`Testcontainers PostgreSQL Integration Tests`** (Flyway, JPA Repositories).
5. **`ArchUnit Architecture Rule Verification`** (Modular monolith boundaries).
6. **`OpenAPI Breaking Change Detection`** (`oasdiff`).
7. **`Frontend Vitest Component Tests`** (React Testing Library, MSW).
8. **`Playwright End-to-End Suite`** (10 critical manufacturing journeys).
9. **`Dependency Security Vulnerability Audit`** (OWASP Dependency-Check, Snyk).

#### 2.2 Semantic Versioning and Release Tagging
* Production releases on `main` are tagged using **Semantic Versioning (SemVer 2.0.0)**: `v<MAJOR>.<MINOR>.<PATCH>`.
* Git tags trigger automated Render production deployments.

#### 2.3 Code Review Protocol for a Sole Developer Working with AI Agents
When developing as a sole engineer paired with AI coding agents, review rigor must not be compromised:
1. **Step 1 (AI Generation):** The agent generates feature code, unit tests, and documentation adhering to `SKILLS.md`.
2. **Step 2 (Self-Review Pass):** The developer reviews the Git diff against the `.github/pull_request_template.md` checklist.
3. **Step 3 (Agent Review Pass):** The developer invokes an independent AI review agent using a focused prompt:
   - *"Audit this pull request diff for: (1) business rule violations, (2) missing permission gates, (3) N+1 query hazards, (4) unhandled error codes, and (5) broken linking chains. Return a markdown review report."*
4. **Step 4 (Resolution & Merge):** All flagged discrepancies are resolved and verified before squash-merging to `main`.

---

### 3. Technical Assumptions
1. **ASSUMPTION:** GitHub repository settings enforce branch protection rules on `main` (requiring status checks to pass before merging).
2. **ASSUMPTION:** Render web service is connected to GitHub for automated deployment upon merging to `main`.

---

### 4. Open Questions
* **O-GIT-01 (Monorepo GitHub Actions Workflow):** Should backend and frontend CI workflows run concurrently or use path filters (`paths: ['backend/**', 'frontend/**']`) to optimize build minutes?
