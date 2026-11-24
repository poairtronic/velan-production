# Velan Production — Agent Skills and Repository Instructions

This file is a project-level instruction set for coding agents working on Velan Production. It is newly drafted from the business requirements; it is not a recovered repository-specific `skills.md`. Adapt paths and commands to the actual repository after inspection.

## 1. Mission and scope
Build and maintain Velan Production as a manufacturing execution and traceability application. The application begins with RM V-SMART SC/requisition intake and ends with actual dispatch confirmation. Do not implement AI Agent/Velan AI, billing, inventory transactions, Vendor DC transactions, MERC integration or Velan Dashboard integration.

## 2. Source-of-truth hierarchy
When instructions conflict, apply this order:
1. Latest explicit business-owner clarification.
2. Approved Phase 1 documents.
3. Revised requirement analysis source document.
4. Older drafts and proposed technical recommendations.
5. Agent assumptions (never silently promote assumptions to requirements).

Mark unresolved points as `OPEN — BUSINESS DECISION REQUIRED`.

## 3. Non-negotiable business rules
- RM owns SC/requisition source, stock, material issue/return/additional requests and Vendor DC transactions.
- ECR owns responsibility and costing; do not return ECR costing to Production.
- Preserve PO → SC → manufactured product → sub-product/BOM → set → component lineage.
- A set completes only when every required BOM component for that set completes required operations and QC.
- Partial dispatch means selected complete sets only; never dispatch an incomplete BOM set.
- Packing confirmation means Dispatch Pending. Only actual dispatch confirmation means Delivered.
- Operator work/data entry is blocked until RM confirms material issue readiness for material-dependent operations.
- Operator measurements and QC measurements are separate records.
- QC failure notifies PM; PM controls disposition.
- PM approval is required for tolerance revisions. Never overwrite existing measurements.
- Restart routes affected quantity to the first operation of the applicable process while preserving history.
- Additional process requires QC.
- RM inward automatically notifies Production; Production user records QC measurements for confirmed returned quantity.
- Only Admin manages notification settings for users and controls deletion/cancellation.
- PM or Admin can provide final business acceptance.

## 4. Architecture discipline
- Follow the approved architecture document. If no stack is frozen, treat `13_Tech_Stack.md` as a proposal, not an implementation mandate.
- Prefer modular monolith boundaries initially.
- Keep business logic in backend/domain services.
- Keep external integration logic in dedicated adapters.
- Keep database migrations versioned and forward-only unless a documented rollback plan exists.
- Use explicit DTOs and validation; do not expose persistence entities directly as API contracts.
- Use transactions for quantity movement, QC release, dispatch and integration event processing.

## 5. Data integrity and traceability
- Never delete or overwrite operator/QC measurements, tolerance revisions, approvals or historical quantity movements as a normal edit.
- Every quantity transition must have source state, destination state, quantity, actor, timestamp and reason/reference as applicable.
- Enforce quantity conservation and prevent duplicate or overlapping active quantities.
- Preserve RM external IDs exactly; do not generate substitute source IDs.
- Use stable internal IDs for sets/components and retain parent-child lineage.
- Protect against concurrent operator submissions and duplicate QR scans.

## 6. Integration rules
- Inspect actual RM/ECR API docs and existing code before implementing.
- Preserve exact external property names, casing and types in integration adapters.
- Use idempotency keys, durable event inbox/outbox, acknowledgements, retries and integration history.
- Never claim a vendor DC inward unless RM confirms it.
- Never create a stock or DC transaction from Production.
- Do not invent endpoints, payload fields, authentication schemes or webhook signatures.
- Do not log passwords, tokens, API keys or secrets.

## 7. Security and permissions
- Backend authorization is mandatory for every protected operation.
- Deny by default.
- Apply individual Admin override before role evaluation; absent override, explicit role deny wins over role grant.
- Do not rely on hidden buttons or client-side route guards.
- Protect files by authorization checks and short-lived access mechanisms where appropriate.
- Audit role changes, privileged actions, approvals, cancellation/deletion, overrides, quantity changes and integration administration.

## 8. UI and workflow expectations
- Show PO/SC/product/BOM hierarchy consistently.
- Use set/component identifiers in worklists and history.
- Show material readiness and last RM issue time where available.
- Show active tolerance revision and parameter units beside measurement inputs.
- Make operator and QC entry screens visibly distinct.
- Show quantity buckets and totals so users can reconcile physical units.
- Display a clear block reason when work cannot proceed.
- Packing and dispatch must be separate screens/actions/statuses.

## 9. Testing requirements
For each change, add/update:
- Unit tests for business rules and quantity calculations.
- Integration tests for repository transactions and external adapters.
- Authorization tests for allowed and denied roles.
- Negative tests for invalid state transitions and missing material readiness.
- Concurrency/idempotency tests for quantity movement and repeated events.
- End-to-end tests for the complete affected workflow.
- Regression tests proving historical measurements and tolerance revisions are preserved.

## 10. Agent workflow
1. Read this file and relevant Phase 1 documents before coding.
2. Inspect the existing repository and current conventions; do not assume paths or stack.
3. Identify affected modules, data entities, API contracts and business rules.
4. State assumptions and unresolved decisions before implementation.
5. Propose a small implementation plan.
6. Implement the smallest coherent change.
7. Add migrations and tests where needed.
8. Run formatter, lint, unit, integration and build checks available in the repo.
9. Report changed files, tests run, results, risks and remaining work.
10. Never modify unrelated modules or secrets.

## 11. Forbidden shortcuts
- No hard-coded BOM composition for every product; use SC/BOM data.
- No advancing failed or material-blocked quantities.
- No copying operator values into QC fields.
- No automatic Delivered status on packing alone.
- No silent tolerance updates or measurement rewrites.
- No stock or Vendor DC transaction duplication.
- No ECR cost calculations in Production.
- No unapproved AI feature or microservice decomposition.
