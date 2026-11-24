# Velan Production — Markdown Documentation Pack

Updated 2026-11-24 — decisions D-01 to D-19 (see 00_DECISION_LOG.md)

Read `00_DECISION_LOG.md` first; it overrides conflicting statements in files 01–15. Read `12_Requirement_Analysis.md` and `15_Application_Master_Plan.md` for the consolidated baseline and overall delivery plan.

| # | File | Purpose |
|---:|---|---|
| 0 | `00_DECISION_LOG.md` | Confirmed decisions and open items |
| 1 | `01_BRD.md` | Business goals, outcomes and boundaries |
| 2 | `02_SRS.md` | Detailed functional and non-functional requirements |
| 3 | `03_Application_Scope.md` | Included/excluded scope and system ownership |
| 4 | `04_Feature_List.md` | Feature inventory (IDs FEAT-001 to FEAT-050) |
| 5 | `05_User_Stories.md` | Role-based user stories |
| 6 | `06_Role_Matrix.md` | Six roles |
| 7 | `07_Workflow_Diagrams.md` | Mermaid workflow diagrams |
| 8 | `08_Business_Rules.md` | Numbered business rules |
| 9 | `09_Integration_Specification.md` | RM read / ECR send interface requirements |
| 10 | `10_Acceptance_Criteria.md` | Testable Given–When–Then criteria |
| 11 | `11_End_to_End_Workflow.md` | Narrative operational workflow |
| 12 | `12_Requirement_Analysis.md` | Consolidated requirement analysis and conflicts |
| 13 | `13_Tech_Stack.md` | Recommended stack and architecture decisions |
| 14 | `14_SKILLS.md` | Coding-agent project instructions |
| 15 | `15_Application_Master_Plan.md` | Modules, data domains and the 51-phase plan |

## Status conventions
- Confirmed: explicitly stated in the revised requirement source or latest business clarification (decisions D-01 to D-19).
- Proposed: recommendation, not yet approved.
- Open: requires business or technical confirmation (OPEN — BUSINESS DECISION REQUIRED: O-01 to O-12).

## Important notes
- The decided technology stack baseline is Java 21, Spring Boot, React, Vite, PostgreSQL on Neon, Cloudflare R2, Render, Caffeine in-process cache, and database outbox/sync log. No Redis, no RabbitMQ. (D-13)
- Field names come from code audits (2026-11-24); JSON property names must be verified against live responses.
- Delivered is automatic when all products are complete (D-10). Billing is outside Production.
- `14_SKILLS.md` is newly authored project guidance, not a file extracted from an existing source repository.

## Phase 2 documentation roadmap
The following system design documents are planned for Phase 2:
- `16_State_Machines.md` (piece, operation, QC decision, SC)
- `17_Data_Model_ERD.md`
- `18_Integration_Adapter_Design.md`
- `19_Architecture.md`
