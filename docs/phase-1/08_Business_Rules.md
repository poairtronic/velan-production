# 08 — Business Rules Document

Updated 2026-11-24 — decisions D-01 to D-19 (see 00_DECISION_LOG.md)

| ID | Rule |
|---|---|
| BR-001 | A submitted RM SC creates a Production record through the RM sync; a Designer/PM/Admin can also create an SC by manual entry or Excel import. |
| BR-002 | Preserve the original RM SC/requisition ID and source references. |
| BR-003 | Duplicate sync, import or entry must not create duplicate SCs, products or pieces (dedupe on RM SC id and SC number). |
| BR-004 | Preserve PO → SC → product → piece relationships. |
| BR-005 | [REMOVED from baseline — OPEN — BUSINESS DECISION REQUIRED: O-03] Set quantity is the SC target quantity where RM defines it as the number of sets. |
| BR-006 | Piece count per product is the quantity RM supplies for that product (SRG already counted in numbers); never hard-code product composition. |
| BR-007 | Every piece has a unique serial (proposed `SC-2579-APG-001`, OPEN — BUSINESS DECISION REQUIRED: O-02). |
| BR-008 | [REMOVED from baseline — replaced by BR-059 and BR-060 per D-10] |
| BR-009 | Pieces progress independently. |
| BR-010 | [REMOVED from baseline — partial dispatch of sets; OPEN — BUSINESS DECISION REQUIRED: O-03] |
| BR-011 | A piece retains its own operation and measurement history. |
| BR-012 | A replacement piece gets a new serial linked to the scrapped piece; serials are never reused or erased. |
| BR-013 | Piece statuses must conserve quantity: every piece is in exactly one status at any time. |
| BR-014 | A quantity cannot be simultaneously active in two incompatible workflow states. |
| BR-015 | Only pieces passed by QC, or continued by the PM with a reason, advance. |
| BR-016 | PM may add, skip, resequence, reopen or reassign operations only with permission and a reason. |
| BR-017 | A skipped operation remains in history and has no measurement entry for the skipped work. |
| BR-018 | The process flow is snapshotted per product; template revisions never alter existing work. |
| BR-019 | RM is the system of record for stock and material issue/return/additional requests. |
| BR-020 | Production displays RM material data and does not create inventory transactions. |
| BR-021 | Pieces of a product cannot start or take data entry until the material-issued flag is set (RM sync or manual by PM/Admin). |
| BR-022 | Record the RM issue reference and time when RM supplies them. |
| BR-023 | Standard/static parameters come from process templates; SC-specific dynamic tolerance comes from Design's approved sheet. |
| BR-024 | Excel is the primary tolerance upload format; PDF is also supported. |
| BR-025 | Designer verifies extracted values before submission. |
| BR-026 | PM approval is required before a tolerance revision becomes active. |
| BR-027 | Post-start tolerance change is a controlled request; Design cannot overwrite active values directly. |
| BR-028 | New approved tolerance values apply to eligible data not yet entered/unfinished quantities as approved. |
| BR-029 | Existing measurement values are never overwritten by a tolerance revision. |
| BR-030 | Completed quantities retain the tolerance revision used when completed. |
| BR-031 | Affected unfinished quantities are evaluated against the new approved revision; newly failing quantities are flagged/blocked for QC review. |
| BR-032 | Operator and QC measurements are separate records; QC must independently enter values. |
| BR-033 | A QC flag raises a non-blocking alert; the flagged piece waits and the PM decides. |
| BR-034 | A PM CONTINUE decision requires a mandatory reason; it never changes the original measurement. |
| BR-035 | Rework may use a temporary operation or route to an earlier existing operation with instructions/tolerance. |
| BR-036 | After rework passes QC, quantity rejoins normal sequence immediately after the original failed operation. |
| BR-037 | Reject and restart scraps the piece and creates a replacement piece that starts at operation 1; history is preserved. |
| BR-038 | Additional process requires QC inspection. |
| BR-039 | ECR owns responsibility and costing; Production sends exceptions to ECR only and stores the returned report number. |
| BR-040 | RM owns vendor DC creation, dispatch and inward; Production reads DC details and return status only. |
| BR-041 | Only pieces covered by an RM return status of received or partial are eligible for QC after vendor return. |
| BR-042 | Production user enters internal QC values for returned vendor quantity. |
| BR-043 | A partial return leaves the remaining pieces at the vendor. |
| BR-044 | Vendor has no Production login and does not enter measurements. |
| BR-045 | [REMOVED: Packing verification per set eliminated per D-10] |
| BR-046 | [REMOVED: Dispatch Pending status eliminated per D-10] |
| BR-047 | [REMOVED: Dispatch confirmation workflow eliminated per D-10] |
| BR-048 | [REMOVED: Partial dispatch of sets eliminated per D-10; see O-03] |
| BR-049 | Admin alone controls user notification preferences; ordinary users cannot independently disable notification types, including critical types. |
| BR-050 | Admin alone can cancel/delete records; cancellation/deletion must preserve audit history and reason. |
| BR-051 | PM or Admin may accept the final business deliverable. |
| BR-052 | Backend APIs enforce permissions; absent grant means deny. |
| BR-053 | Admin permission override takes precedence; absent override, deny wins among conflicting roles. |
| BR-054 | Every approval, override, cancellation, reschedule, bypass, reassignment and material/exception change is audited. |
| BR-055 | PM sees production activity permitted to PM but not security/admin logs; Admin sees all relevant logs. |
| BR-056 | Billing is outside Production. |
| BR-057 | MERC and Velan Dashboard: no integration now; read access later; write only if required in future. |
| BR-058 | AI Agent/Velan AI is excluded from this application project. |
| BR-059 | A piece becomes Packed automatically when all its operations are passed. |
| BR-060 | A product is complete when all its pieces are Packed; the SC becomes Delivered automatically when all products are complete. Billing is outside Production. |
| BR-061 | A replacement piece is blocked until RM shows the ECR material as issued (matched by ECR report number) or the PM releases it manually with a reason. |
| BR-062 | Each exception is sent to ECR once; Production never waits for ECR and never auto-resends after an ambiguous timeout. |
| BR-063 | Manual, Excel and RM-sourced SCs follow identical rules after creation. |
| BR-064 | RM revisions after submit (quantity changes, deleted lines) are applied by diff and audited. |
| BR-065 | A piece returned from a vendor may complete any pending operation (usually the next); in-house operations stay sequential. |
| BR-066 | QC is required on every operation. |
| BR-067 | Production never writes to RM; it writes to ECR only to send exceptions. |
| BR-068 | Quantities and statuses are derived from piece records, never edited by hand. |
| BR-069 | An in-progress SC is imported from RM only when the PM requests it. |
| BR-070 | An extra process creates a new operation with its own tolerance list; after QC pass the piece continues after the originally failed operation. |
