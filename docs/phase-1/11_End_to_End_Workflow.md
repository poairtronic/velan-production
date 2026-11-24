# 11 — End-to-End Workflow (Narrative)

Updated 2026-11-24 — decisions D-01 to D-19 (see 00_DECISION_LOG.md)

This document explains the application in operational language, alongside the diagrams in Document 07.

## Stage 1 — PO and SC origination
1. Customer/PO information is maintained in the source business process.
2. Designer creates the RM requisition/SC in V-SMART (or an SC can also be entered manually or imported from Excel by the Designer/PM/Admin). (D-01)
3. Designer enters manufactured product, quantities, and raw-material specifications.
4. RM submission is the trigger for Production intake; manual entry and Excel import are permanent intake alternatives. (D-01, D-11)
5. Production reads the SC and available source data from RM via read-only APIs.

## Stage 2 — Production intake and piece generation
1. Production's sync reads the submitted SC through RM's read APIs (or the user enters/imports it).
2. It matches by RM id and deduplicates by RM SC id and SC number.
3. It creates the Production SC record, products (one per RM line), and pieces (quantity per product). (D-02)
4. Each piece receives a unique serial (proposed `SC-2579-APG-001`, OPEN — BUSINESS DECISION REQUIRED: O-02).
5. It preserves original RM IDs and applies RM revisions by diff on each poll.
*(QR label creation and scanning are deferred under OPEN — BUSINESS DECISION REQUIRED: O-08).*

## Stage 3 — Process flow selection, drawing and tolerance setup
1. Designer selects the approved process flow per product (fixed machine and operator, or allowed vendor, per operation); the flow is snapshotted per product so future template changes never alter existing work. (D-14)
2. Process template defines operation order, instructions, standard parameters (5 types: numeric, yes/no, text, signature, image) and expected durations.
3. Designer uploads SC-specific tolerance sheet (Excel primarily, PDF also) and applicable drawing/CAD PDF.
4. System extracts values and maps each value to SC + product + operation + parameter.
5. Designer verifies/corrects extraction and submits it.
6. PM approves or rejects the tolerance revision. Only the approved revision is released to production.
7. Any later change requires PM approval; existing entered measurements are never overwritten.

## Stage 4 — PM planning and daily priority allocation
1. New SCs enter the planning queue.
2. PM reviews delivery date (OPEN — BUSINESS DECISION REQUIRED: O-01), SC age, manager priority, workload and capacity.
3. System calculates workload from template operation times and employee availability; detects bottlenecks; generates 7/15/30-day forecasts.
4. PM confirms priorities, schedule, and sets each operator's daily priority list. (D-15)
5. Operator sees a priority-ordered worklist.

## Stage 5 — Material readiness gate
1. Stores issues material in RM. (D-04)
2. Production reads the issue status (or PM/Admin sets the flag manually if sync is delayed).
3. Until the product is marked issued, its pieces are blocked at the material gate; operators cannot start work or enter data.
4. Production is strictly read/track only; no stock issue, return, adjustment or additional material requests.

## Stage 6 — Operator execution
1. Operator opens own queue in PM priority order and selects pieces and operation (no scanning; D-16).
2. System displays instructions, drawings, and approved static and dynamic tolerances.
3. Operator enters values per piece in a bulk grid (rows = pieces, columns = parameters) with paste from Excel capability.
4. No start/stop buttons; timestamps are captured at ready, opened, and submitted. (D-16)
5. Operator submits completed pieces to QC worklist.

## Stage 7 — QC inspection on every operation
1. QC inspection is required on every operation. (D-06)
2. QC independently inspects and enters separate measurement records (blind QC; operator values are never copied into QC fields).
3. System automatically compares QC values with active approved tolerance limits.
4. Pass piece is released to the next sequential operation.
5. Out of tolerance raises a non-blocking alert, and the flagged piece waits for the PM decision. (D-07)
6. Piece counts (pending, ready, submitted, QC, flagged, at vendor, packed) reconcile exactly; no manual quantity buckets.

## Stage 8 — QC failure, PM decisions, rework and restart
1. Flagged piece triggers non-blocking alert; piece waits for PM.
2. PM reviews failure details and selects one of four binding dispositions: (D-07)
   - **CONTINUE:** PM provides a mandatory engineering reason; piece advances to next operation without altering historical failed measurements.
   - **EXTRA PROCESS:** PM injects a new ad-hoc operation with its own tolerance list; requires mandatory QC; upon QC pass the piece continues after the originally failed operation. (D-19, BR-070)
   - **RETURN TO EARLIER OPERATION:** PM rewinds piece to an earlier operation with new instructions; rework tracked under an incremented iteration counter; upon QC pass the piece continues after the originally failed operation. (D-19)
   - **REJECT AND RESTART:** Scraps the piece (history kept); creates a replacement piece with a new linked serial (`replaced_piece_id`) starting at operation 1. (D-08)
3. Replacement piece is blocked in AWAITING_MATERIAL until RM shows the ECR material as issued (matched by ECR report number) or PM releases it manually with a reason. (D-08, BR-061)
4. Exceptions go to ECR only, once, via the database outbox; the returned ECR report number is stored; Production never waits for ECR and does not calculate/receive ECR costing. (D-09, BR-062)

## Stage 9 — Vendor processing
1. RM creates and dispatches the Vendor DC. (D-05)
2. Production reads vendor, DC number, process name, product, quantity, and later the return status (received / partial / not received).
3. Production shows pieces as at vendor. No vendor selection, no alert to RM, no vendor dates.
4. When RM records inward, a Production user tags returned pieces and selects the process.
5. QC enters independent measurement values.
6. A piece returned from a vendor may complete any pending operation (usually the next); in-house operations stay sequential. (D-05, BR-065)

## Stage 10 — Automatic completion
1. A piece becomes **Packed** automatically when all its operations are passed. (D-10, BR-059)
2. A product is complete when all its pieces are Packed.
3. The **SC becomes Delivered automatically** when all products are complete. (D-10, BR-060)
4. No packing or dispatch screens, statuses, roles or dispatch confirmation. Billing is outside Production.
5. Set-level completion and partial delivery are OPEN — BUSINESS DECISION REQUIRED (O-03).

## Stage 11 — Management monitoring and reporting
Management dashboards show live metrics: active SCs/POs, piece progress, WIP, machines in use, vendors, out-of-tolerance values, near-complete SCs, rejection quantity, and planned vs actual. Daily 9 AM report is generated in Excel with charts. Reports and exports are permission-filtered.
