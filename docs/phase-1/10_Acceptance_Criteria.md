# 10 — Acceptance Criteria

Updated 2026-11-24 — decisions D-01 to D-19 (see 00_DECISION_LOG.md)

Use Given–When–Then. Each test should be automated where practical and linked to a requirement/feature ID.

## AC-001 — SC creation from RM sync, manual entry or Excel
**Given** a valid RM SC (read by the sync), manual SC or Excel row, **when** Production receives or imports it, **then** exactly one Production SC is created with its source reference. Invalid input creates nothing active. Repeated polls or imports create no duplicates.

## AC-002 — PO/SC/product/piece hierarchy
**Given** one PO contains multiple SCs and each SC has products, **when** imported or created, **then** the hierarchy PO → SC → product → piece is correctly linked and searchable by PO, SC, product and piece serial.

## AC-003 — Piece generation
**Given** an SC with APG quantity 50 and SRG quantity 100, **when** pieces are generated, **then** 50 APG and 100 SRG pieces exist, each with a unique serial linked to its product.

## AC-004 — Completion rules
**Given** a product with several pieces, **when** only some are Packed, **then** the product stays in progress. When all are Packed the product is complete. When all products are complete the SC becomes Delivered automatically. (Set-level completion wording is pending OPEN — BUSINESS DECISION REQUIRED: O-03).

## AC-005 — [REMOVED from baseline]
[REMOVED: Partial dispatch of sets eliminated per D-10; see OPEN — BUSINESS DECISION REQUIRED: O-03]

## AC-006 — Material readiness gate
**Given** Stores has not marked material issued in RM, **when** an operator attempts to start or enter data, **then** pieces of that product cannot start or take data entry. When RM shows issued (or PM/Admin sets the flag manually) they become ready.

## AC-007 — Quantity conservation and piece status
**Given** 10 pieces at an operation, **when** 2 pass, 1 is flagged and 7 are pending, **then** the counts reconcile to 10 and only the 2 advance.

## AC-008 — Operator and QC measurement separation
**Given** an operator submits measurements, **when** QC inspects, **then** QC enters independent values in separate records (blind QC). Operator values are never copied into QC fields.

## AC-009 — QC failure to PM
**Given** a QC value is outside tolerance, **when** QC records failure, **then** an alert is raised, the piece waits, and only the PM can choose CONTINUE (reason), EXTRA PROCESS, RETURN TO EARLIER OPERATION or REJECT AND RESTART.

## AC-010 — Tolerance approval and revision
**Given** Designer uploads and verifies a tolerance revision, **when** it is submitted, **then** PM must approve before activation. On approval, not-yet-entered/eligible unfinished data uses the new revision; existing measurement values are not overwritten; completed history retains its previous revision; PM receives notification and affected newly failing quantities are flagged.

## AC-011 — Restart and rework
**Given** PM chooses reject and restart, **when** applied, **then** it scraps the piece, creates a replacement piece with a new linked serial at operation 1, keeps all history, and blocks the replacement until released. After an extra process or earlier-operation rework passes QC, the piece continues after the originally failed operation.

## AC-012 — Additional process QC
**Given** an extra process is added for a piece, **when** the operator completes it, **then** independent QC inspection is required before the piece is released.

## AC-013 — Vendor tracking from RM DC details
**Given** RM shows a vendor DC with 10 pieces and a return of 6, **when** read by Production, **then** Production shows 6 returned and 4 at vendor, the user tags 6 pieces, and QC enters values for them. Production creates no DC and sends nothing to RM.

## AC-014 — ECR exception sending
**Given** a PM decision creates an exception, **when** the outbox sends it, **then** the outbox sends it to ECR exactly once, stores the ECR report number, and Production neither waits for nor receives costing.

## AC-015 — Admin notification controls
**Given** a user wants to disable a notification type, **when** the user opens preferences, **then** the user cannot independently change notification policy. Admin manages settings for that user, including critical notifications.

## AC-016 — Admin-only cancellation/deletion
**Given** a non-Admin user attempts cancellation/deletion, **when** the action is submitted through UI or API, **then** it is denied. Admin action requires reason and creates an immutable audit record.

## AC-017 — RBAC enforcement
**Given** a user lacks permission, **when** the user attempts the action directly through an API, **then** the backend denies it and no data changes. Individual Admin override takes precedence; otherwise explicit role deny wins; no grant means deny.

## AC-018 — Automatic completion
**Given** a piece passes its last operation, **when** QC passes, **then** it becomes Packed automatically; no packing or dispatch action exists.

## AC-019 — Integration failure and retry
**Given** an RM poll fails, **when** retries occur, **then** retries create no duplicates and the sync log shows the failures.

## AC-020 — Dashboard reconciliation
**Given** production records have known piece/operation states, **when** dashboard totals are calculated, **then** all displayed totals reconcile to source records and respect filters and RBAC (no packing/dispatch totals).

## AC-021 — Manual SC entry and Excel import
**Given** valid manual SC input or a valid Excel import file, **when** submitted, **then** the system validates, previews and creates the exact same production records as the RM sync; Excel import is fully audited.

## AC-022 — Bulk grid measurement entry
**Given** up to 20 pieces at an operation, **when** the operator enters measurements or pastes rows from Excel, **then** values can be entered in one grid; out-of-tolerance cells are highlighted; values are saved even when outside tolerance.

## AC-023 — Replacement piece release gate
**Given** a replacement piece in AWAITING_MATERIAL, **when** RM shows the matching ECR report number as ISSUED (or PM releases it manually with a reason), **then** the replacement piece becomes ready for operation 1.

## AC-024 — RM revision by diff
**Given** an RM SC where lines are modified or deleted after initial submission, **when** the next poll occurs, **then** the sync applies the changes by diff and the audit log records it; work already completed in Production is never deleted.

## AC-025 — Designer role permissions
**Given** a user with Designer role, **when** accessing the system, **then** the user can enter SCs and select flows per product, but cannot approve tolerances, edit reusable templates, enter operator/QC values, or decide QC flags.

## AC-026 — PM daily priority list
**Given** the PM sets an operator's daily priority list, **when** the operator logs in, **then** the operator's queue orders pieces strictly by PM priority; operators see only their assigned pieces.

## AC-027 — Daily 9 AM report
**Given** active production records, **when** the daily 9 AM scheduled task fires, **then** a comprehensive Excel report with charts is generated and matches central dashboard metrics.

## AC-028 — Ambiguous ECR send handling
**Given** an ECR HTTP dispatch that encounters a network timeout, **when** retry logic evaluates it, **then** the record is not re-sent automatically (to prevent duplicate reports) and appears flagged for Admin manual review.

## Acceptance authority
Final business acceptance may be provided by PM or Admin. Technical QA evidence and business sign-off should both be retained.
