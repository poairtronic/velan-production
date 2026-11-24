# 08 — Business Rules Document

| ID | Rule |
|---|---|
| BR-001 | A submitted RM requisition/SC creates a corresponding Production record automatically. |
| BR-002 | Preserve the original RM SC/requisition ID and source references. |
| BR-003 | Duplicate integration events must not create duplicate production records, sets or movements. |
| BR-004 | Preserve PO → SC → manufactured product → sub-product/BOM → set → component relationships. |
| BR-005 | Set quantity is the SC target quantity where RM defines it as the number of sets. |
| BR-006 | BOM component requirement is calculated from the SC BOM; example quantities are not hard-coded globally. |
| BR-007 | Each set and each physical component has a unique traceable identity. Exact serial syntax remains a configuration/approval item. |
| BR-008 | A set is complete only when every required BOM component for that set completes required operations and passes required final QC. |
| BR-009 | Individual sets can progress independently. |
| BR-010 | Partial dispatch is allowed only for complete, QC-passed sets. An incomplete BOM set cannot be dispatched. |
| BR-011 | A component retains its own operation and measurement history. |
| BR-012 | Component replacement/transfer must preserve old identity and lineage; never reuse or erase an ID. |
| BR-013 | Quantity splitting must conserve quantity across passed, pending, rework, restart, vendor-pending and terminal states. |
| BR-014 | A quantity cannot be simultaneously active in two incompatible workflow states. |
| BR-015 | Only eligible passed/approved quantity advances to the next operation. |
| BR-016 | PM may add, skip, resequence, reopen or reassign operations only with permission and a reason. |
| BR-017 | A skipped operation remains in history and has no measurement entry for the skipped work. |
| BR-018 | Template revisions must not silently alter completed historical work. |
| BR-019 | RM is the system of record for stock and material issue/return/additional requests. |
| BR-020 | Production displays RM material data and does not create inventory transactions. |
| BR-021 | Operator cannot start or enter operation data for a material-dependent operation until RM confirms required material issue readiness. |
| BR-022 | Record RM issue reference and issue timestamp when supplied. |
| BR-023 | Standard/static parameters come from process templates; SC-specific dynamic tolerance comes from Design's approved sheet. |
| BR-024 | Excel is the primary tolerance upload format; PDF is also supported. |
| BR-025 | Extracted tolerance values must be reviewed/verified by Design before submission. |
| BR-026 | PM approval is required before a tolerance revision becomes active. |
| BR-027 | Post-start tolerance change is a controlled request; Design cannot overwrite active values directly. |
| BR-028 | New approved tolerance values apply to eligible data not yet entered/unfinished quantities as approved. |
| BR-029 | Existing measurement values are never overwritten by a tolerance revision. |
| BR-030 | Completed quantities retain the tolerance revision used when completed. |
| BR-031 | Affected unfinished quantities are evaluated against the new approved revision; newly failing quantities are flagged/blocked for QC review. |
| BR-032 | Operator and QC measurements are separate records; QC must independently enter values. |
| BR-033 | QC failure notifies PM; PM decides the next disposition. |
| BR-034 | An override requires an authorized approver and mandatory reason; approval never changes the original measurement. |
| BR-035 | Rework may use a temporary operation or route to an earlier existing operation with instructions/tolerance. |
| BR-036 | After rework passes QC, quantity rejoins normal sequence immediately after the original failed operation. |
| BR-037 | Restart returns affected quantity to the first operation of the applicable production process and preserves history. |
| BR-038 | Additional process requires QC inspection. |
| BR-039 | ECR owns responsibility and costing; Production sends relevant exception information but does not calculate ECR costs. |
| BR-040 | RM owns Vendor DC creation, dispatch and inward. Production does not create or receive DC transactions. |
| BR-041 | RM inward automatically notifies Production; only RM-confirmed returned quantity is eligible for internal QC. |
| BR-042 | Production user enters internal QC values for returned vendor quantity. |
| BR-043 | Vendor partial returns must reconcile returned quantity and vendor-pending balance. |
| BR-044 | Vendor has no Production login and does not enter measurements. |
| BR-045 | Packing requires every required BOM component complete and final QC passed. |
| BR-046 | Packing confirmation changes state to Dispatch Pending, not Delivered. |
| BR-047 | Delivered requires actual dispatch confirmation by an authorized dispatch user. |
| BR-048 | Partial dispatch means selected complete sets can be delivered while remaining sets stay active. |
| BR-049 | Admin alone controls user notification preferences; ordinary users cannot independently disable notification types, including critical types. |
| BR-050 | Admin alone can cancel/delete records; cancellation/deletion must preserve audit history and reason. |
| BR-051 | PM or Admin may accept the final business deliverable. |
| BR-052 | Backend APIs enforce permissions; absent grant means deny. |
| BR-053 | Admin permission override takes precedence; absent override, deny wins among conflicting roles. |
| BR-054 | Every approval, override, cancellation, reschedule, bypass, reassignment and material/exception change is audited. |
| BR-055 | PM sees production activity permitted to PM but not security/admin logs; Admin sees all relevant logs. |
| BR-056 | Billing is outside Production. |
| BR-057 | MERC and Velan Dashboard are not integrated. |
| BR-058 | AI Agent/Velan AI is excluded from this application project. |
