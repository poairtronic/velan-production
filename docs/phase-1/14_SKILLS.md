Superseded for agent behaviour by /SKILLS.md; kept for history.

# Velan Production — Agent Skills and Repository Instructions

Updated 2026-11-24 — decisions D-01 to D-19 (see 00_DECISION_LOG.md)

This file is a project-level instruction set for coding agents working on Velan Production. Adapt paths and commands to the actual repository after inspection.

## 1. Mission and scope
Build and maintain Velan Production as a manufacturing execution and traceability application. The application begins with RM V-SMART SC/requisition intake (plus permanent manual entry and Excel import) and ends when the SC is Delivered automatically. (D-10) Do not modify the RM or ECR code bases. Do not implement AI Agent/Velan AI, billing, inventory transactions, Vendor DC transactions, MERC integration or Velan Dashboard integration.

## 2. Source-of-truth hierarchy
When instructions conflict, apply this order:
1. `00_DECISION_LOG.md` (decisions D-01 to D-19).
2. Latest explicit business-owner clarification.
3. Approved Phase 1 documents.
4. Revised requirement analysis source document.
5. Older drafts and proposed technical recommendations.
6. Agent assumptions (never silently promote assumptions to requirements).

Mark unresolved points as `OPEN — BUSINESS DECISION REQUIRED` (with their O-xx IDs).

## 3. Non-negotiable business rules
- RM owns SC/requisition source, stock, material issue/return/additional requests and Vendor DC transactions. Production never writes to RM. (D-09, BR-067)
- ECR owns responsibility and costing; do not return ECR costing to Production. Exceptions go to ECR only. (D-09)
- Preserve PO → SC → product → piece lineage.
- Product/SC completion is derived from individual piece completion (a piece is Packed when all operations pass; SC is Delivered automatically when all products are complete; no packing or dispatch screens; D-10). Set concept and partial dispatch of sets is OPEN — BUSINESS DECISION REQUIRED (O-03).
- Operator work/data entry is blocked until material-issued flag is set per product (RM sync or PM/Admin manual flag; D-04).
- Six confirmed primary roles: Admin, PM, Designer, Operator, QC, Management. (D-03)
- Individual login per employee. (D-03)
- Manual entry and Excel import stay permanent alongside RM sync. (D-01)
- No QR codes or scanning in the baseline (operators pick pieces from queue; OPEN — BUSINESS DECISION REQUIRED: O-08).
- Operator measurements and QC measurements are separate records; QC inspects every operation independently (blind QC). (D-06)
- QC failure raises non-blocking alert; piece waits for PM; PM decides: CONTINUE with reason, EXTRA PROCESS, RETURN TO EARLIER OPERATION, REJECT AND RESTART. (D-07)
- Reject and restart: scraps piece and creates a replacement piece with a new linked serial (`replaced_piece_id`) at operation 1; replacement is blocked until RM shows ECR material issued or PM releases it manually with a reason. (D-08, BR-061)
- PM approval is required for tolerance revisions. Never overwrite existing measurements.
- Production reads RM DC details and return status (received / partial / not received); no vendor dates; no alerts to RM. (D-05)
- Only Admin manages notification settings for users and controls deletion/cancellation.
- PM or Admin can provide final business acceptance.

## 4. Architecture discipline
- Modular monolith architecture in Java 21 LTS + Spring Boot with React + TypeScript + Vite. (D-13)
- No Redis (use in-process Caffeine cache); no RabbitMQ (use database outbox/sync log). (D-13)
- Keep business logic in backend/domain services.
- Keep external integration logic in dedicated adapters.
- Keep database migrations versioned (Flyway) and forward-only.
- Use explicit DTOs and validation; do not expose persistence entities directly as API contracts.
- Use transactions for piece status movement, outbox event generation, and integration processing.

## 5. Data integrity and traceability
- Never delete or overwrite operator/QC measurements, tolerance revisions, approvals or historical movements.
- Derive counts from piece status; every piece is in exactly one status at any time; no manual quantity buckets. (BR-013, BR-068)
- Replacement pieces get new serials linked to the scrapped parent via `replaced_piece_id`; never reuse or erase serials. (BR-012)
- Preserve RM external IDs exactly; do not generate substitute source IDs.
- Protect against concurrent operator submissions.

## 6. Integration rules
- Use the read-only RM service account and existing RM read endpoints; use ECR's existing intake; verify JSON property names against live responses (VERIFY items in document 09). (D-11)
- Never send anything to RM. (D-09, BR-067)
- ECR outbox sends once (at-most-once delivery) and never auto-resends after an ambiguous timeout; flags for Admin manual review. (BR-062)
- Never claim a vendor DC inward unless RM confirms it.
- Never create a stock or DC transaction from Production.
- Do not invent endpoints, payload fields, authentication schemes or webhook signatures.
- Do not log passwords, tokens, API keys or secrets.

## 7. Security and permissions
- Backend authorization is mandatory for every protected operation.
- Deny by default.
- Apply individual Admin override before role evaluation; absent override, explicit role deny wins over role grant.
- Do not rely on hidden buttons or client-side route guards.
- Protect files by authorization checks and short-lived access mechanisms.
- Audit role changes, privileged actions, approvals, cancellation/deletion, overrides, and integration administration.

## 8. UI and workflow expectations
- Show PO/SC/product/piece hierarchy consistently.
- Use piece serials in worklists and history.
- Show material readiness flag per product.
- Show active tolerance revision and parameter units beside measurement inputs.
- Make operator and QC entry screens visibly distinct.
- Operator entry is a bulk grid (rows = pieces, columns = parameters) with paste from Excel. (D-16)
- No start/stop buttons; capture ready/opened/submitted timestamps. (D-16)
- Display a clear block reason when work cannot proceed.
- No packing or dispatch screens. (D-10)

## 9. Testing requirements
For each change, add/update:
- Unit tests for business rules and piece status calculations.
- Integration tests for repository transactions and external adapters.
- Authorization tests for allowed and denied roles across the six roles.
- Negative tests: material not issued, replacement piece not released, duplicate ECR send, RM revision after submit.
- Tests for replacement release, ECR once-only send, RM revision by diff, and material gate.
- Concurrency/idempotency tests for piece status transitions and repeated polls.
- Regression tests proving historical measurements and tolerance revisions are preserved.

## 10. Agent workflow
1. Read `00_DECISION_LOG.md` and relevant Phase 1 documents before coding.
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
- No hard-coded product composition; use SC data.
- No advancing failed or material-blocked pieces.
- No copying operator values into QC fields (blind QC).
- No manual editing of Packed or Delivered; they are derived automatically from piece status. (D-10)
- No silent tolerance updates or measurement rewrites.
- No writes to RM. (D-09, BR-067)
- No auto-resend to ECR after ambiguous timeout. (BR-062)
- No matching DC lines by part number (overwritten by RM); match by SC + part_name.
- No stock or Vendor DC transaction duplication.
- No ECR cost calculations in Production.
- No unapproved AI feature or microservice decomposition.
