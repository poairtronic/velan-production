# 10 — Acceptance Criteria

Use Given–When–Then. Each test should be automated where practical and linked to a requirement/feature ID.

## AC-001 — RM SC creates production record
**Given** RM has submitted a valid SC, **when** Production receives the event, **then** exactly one production record is created with the original RM identity and PO/SC/product/BOM data. Invalid events do not create active incomplete work. Duplicate delivery returns a prior acknowledgement without duplicate records.

## AC-002 — PO/SC/BOM hierarchy
**Given** one PO contains multiple SCs and each SC has products/BOM rows, **when** imported, **then** the hierarchy and source identifiers remain correctly linked and independently searchable.

## AC-003 — Set and component quantity calculation
**Given** SC002 target is 4 sets and the BOM per set is ARG 1, SPG 2, VBM 1 and Jig Feet 3, **when** Production creates the set/component records, **then** totals are ARG 4, SPG 8, VBM 4 and Jig Feet 12. Every component is linked to exactly one intended set/unit identity.

## AC-004 — Set completion
**Given** one set has multiple required BOM components, **when** only some components complete, **then** the set remains incomplete. Only when every required component completes applicable operations and required final QC does the set become production-complete.

## AC-005 — Partial dispatch
**Given** an SC has multiple sets and only some are complete, **when** an authorized user prepares dispatch, **then** only fully completed BOM sets are eligible. Incomplete sets cannot be packed or dispatched. Other complete sets can be dispatched independently.

## AC-006 — Material readiness gate
**Given** RM has not confirmed required material issue, **when** an operator attempts to start/enter operation data for a material-dependent operation, **then** the system blocks the action. When RM confirms issue, Production displays issued quantity, status and issue time and permits only eligible quantity.

## AC-007 — Quantity conservation
**Given** 10 units at an operation, **when** 2 pass, 1 is sent to rework and 7 remain pending, **then** these states reconcile to 10, no unit is duplicated and only 2 advance.

## AC-008 — Operator and QC measurement separation
**Given** an operator submits measurements, **when** QC inspects, **then** QC enters independent values in separate records. Operator values are visible for reference but are never copied into QC fields.

## AC-009 — QC failure to PM
**Given** QC measurement is outside approved tolerance, **when** QC records failure, **then** quantity is blocked, PM is notified and PM controls disposition. No normal progression occurs before an authorized decision.

## AC-010 — Tolerance approval and revision
**Given** Design uploads and verifies a tolerance revision, **when** it is submitted, **then** PM must approve before activation. On approval, not-yet-entered/eligible unfinished data uses the new revision; existing measurement values are not overwritten; completed history retains its previous revision; PM receives notification and affected newly failing quantities are flagged.

## AC-011 — Restart and rework
**Given** PM chooses restart, **when** the route is applied, **then** affected quantity returns to the first operation of the applicable process with history preserved. For rework, after QC pass, quantity rejoins normal flow immediately after the original failed operation.

## AC-012 — Additional process QC
**Given** an additional process is added for a quantity, **when** the operator completes it, **then** QC inspection is required before the quantity is released.

## AC-013 — Vendor inward notification
**Given** RM records inward of 6 out of 10 vendor-dispatched units, **when** the RM inward event reaches Production, **then** Production is automatically notified, 6 units become eligible for internal QC entry and 4 remain vendor-pending. Production does not create or receive a DC transaction.

## AC-014 — ECR boundary
**Given** an applicable exception is recorded, **when** Production sends it to ECR, **then** ECR receives the agreed references and performs its own responsibility/costing. Production does not calculate or receive ECR costing results.

## AC-015 — Admin notification controls
**Given** a user wants to disable a notification type, **when** the user opens preferences, **then** the user cannot independently change notification policy. Admin can manage settings for that user, including critical notifications.

## AC-016 — Admin-only cancellation/deletion
**Given** a non-Admin user attempts cancellation/deletion, **when** the action is submitted through UI or API, **then** it is denied. Admin action requires reason and creates an immutable audit record.

## AC-017 — RBAC enforcement
**Given** a user lacks permission, **when** the user attempts the action directly through an API, **then** the backend denies it and no data changes. Individual Admin override takes precedence; otherwise explicit role deny wins; no grant means deny.

## AC-018 — Packing versus dispatch
**Given** a complete set passes final QC, **when** Packing confirms it, **then** status becomes Dispatch Pending, not Delivered. Only actual dispatch confirmation records quantity/time/responsible user and changes the dispatched set to Delivered.

## AC-019 — Integration failure and retry
**Given** an integration event fails transiently, **when** retry occurs, **then** no duplicate production record or quantity movement is created. Failure and retry history is visible to authorized users.

## AC-020 — Dashboard reconciliation
**Given** production records have known set/component/operation states, **when** dashboard totals are calculated, **then** all displayed totals reconcile to source records and respect filters and RBAC.

## Acceptance authority
Final business acceptance may be provided by PM or Admin. Technical QA evidence and business sign-off should both be retained.
