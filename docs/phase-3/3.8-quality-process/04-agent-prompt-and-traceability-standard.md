# Part 3.8 — Testing Standards, Git Workflow, Code Review, and Phase 3 Closure
## Document 04: Agent Prompting Protocols, Traceability Matrix, and Decision Governance

---

### 1. Confirmed Rules

#### 1.1 The Standard Phase/Part Agent Prompt Structure
To eliminate ambiguity, prevent scope drift, and ensure high-precision autonomous execution by AI coding agents, all task prompts issued to agents must follow a standardized 6-section structure:

```markdown
TASK: Phase X, Part Y — <Title>. <STRICT SCOPE BOUNDARIES: e.g. DOCUMENTATION ONLY. Do not start part Y+1. Do not commit or push.>

COMMON RULES: Read first SKILLS.md, docs/PROJECT_CONTEXT_FOR_CLAUDE.md, docs/phase-1/00_DECISION_LOG.md, earlier Phase documents, then the documents listed below. Do not modify frozen documents; record errors in the report. Keep four categories separate in every document: Confirmed rules / Proposed design / Technical assumptions / Open questions. Never decide C-1, C-2 or any O-xx; mark them OPEN. Each document under 400 lines, Markdown, prescriptive, no duplicated text. Verify external facts or mark UNVERIFIED.

READ ALSO: <Specific Phase 1 and Phase 2 document paths relevant to the task>

KNOWN FACTS & FIXED DECISIONS: <Explicit non-negotiable boundaries, role exclusions, technology choices>

DELIVERABLES under docs/<path>/:
1. 01-<name>.md — <Detailed scope and requirements>
2. 02-<name>.md — ...
...
N. 06-part-X.Y-report.md — summary, assumptions, conflicts, errors found, open questions.

SUCCESS CRITERIA
- <Verifiable metric 1: e.g. All N files exist and are under 400 lines>
- <Verifiable metric 2: e.g. Specific matrix or invariant satisfied>
- Stop after writing the completion report.
```

#### 1.2 Mandatory Operational Linkage to `SKILLS.md`
Every prompt must strictly bind agent behavior to the root operating manual:
* **Section 0 Start Routine:** The agent must read `SKILLS.md`, `PROJECT_CONTEXT_FOR_CLAUDE.md`, and `00_DECISION_LOG.md` before making any workspace changes.
* **Section 13 Completion Report:** Every agent task must conclude with the Section 13 completion report template.

#### 1.3 Decision Log Append-Only Governance
Decisions recorded in `docs/phase-1/00_DECISION_LOG.md` are legally binding baselines:
1. **Historic Decisions are Frozen:** Decisions `D-01` through `D-19` must never be altered, rewritten, or deleted.
2. **Recording New Decisions:** When the project owner resolves an open question or conflict, a new decision entry (`D-20`, `D-21`, etc.) is appended to the decision log with date, resolution text, and affected documents.
3. **Recording Open Questions:** New design questions or ambiguities are appended to the open questions table (`O-xx`) with explicit options. Unilateral engineering choices on open items are strictly prohibited.

---

### 2. Proposed Design

#### 2.1 Requirement-to-Test Traceability Matrix Format
Traceability between business requirements, source code, and automated verification tests is maintained in `docs/traceability/TRACEABILITY_MATRIX.md`:

| Requirement ID | Description | Phase & Module | Primary Code Entity | Automated Test Class & Method | Test Type |
|:---|:---|:---|:---|:---|:---:|
| **BR-001** | Unique SC order number constraint | Phase 5 (`order`) | `SalesContract.java` | `SalesContractRepositoryIT#shouldRejectDuplicateOrderNumber` | Integration |
| **BR-004** | Unique heat number per SC product | Phase 5 (`order`) | `ScProduct.java` | `ScProductRepositoryIT#shouldEnforceUniqueHeatNumber` | Integration |
| **BR-007** | Alphanumeric piece serial standard | Phase 6 (`execution`) | `PieceSerialGenerator.java` | `PieceSerialGeneratorTest#shouldFormatDisplaySerial` | Unit |
| **BR-032** | Non-blocking operator tolerance alert | Phase 6 (`execution`) | `OperatorBulkGrid.tsx` | `operator-execution.spec.ts#testNonBlockingWarning` | Playwright E2E |
| **BR-033** | Blind QC inspection screen | Phase 6 (`quality`) | `BlindQCInspectionPage.tsx` | `blind-qc-inspection.spec.ts#testBlankInputs` | Playwright E2E |
| **BR-050** | Admin-only cancellation/deletion | Phase 8 (`admin`) | `AdminActionService.java` | `AdminSecurityTest#shouldDenyNonAdminRecordCancellation` | Integration |
| **BR-054** | Mandatory immutable audit logging | Phase 8 (`audit`) | `AuditEventListener.java` | `AuditLoggingIT#shouldPersistImmutableAuditEvent` | Integration |
| **BR-061** | ECR replacement material readiness | Phase 9 (`integration`)| `RmSyncWorker.java` | `RmSyncWorkerIT#shouldUnlockPieceOnEcrIssue` | Integration |
| **BR-062** | No auto-resend on ambiguous timeout | Phase 9 (`integration`)| `EcrOutboxSweeper.java` | `EcrOutboxSweeperTest#shouldFlagAmbiguousTimeout` | Unit |
| **BR-067** | Zero production writes to RM | Phase 9 (`integration`)| `RmClient.java` | `ArchUnitArchitectureTest#shouldEnforceZeroRmWriteMethods`| ArchUnit |

---

### 3. Technical Assumptions
1. **ASSUMPTION:** The traceability matrix is updated continuously at the end of each development phase during implementation.

---

### 4. Open Questions
* **O-TRC-01 (Automated Traceability Parsing):** Should a custom script or GitHub Action verify that every requirement ID in `08_Business_Rules.md` has at least one corresponding test method in the test suite?
