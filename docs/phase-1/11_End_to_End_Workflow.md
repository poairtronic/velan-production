# 11 — End-to-End Workflow (Narrative)

This document explains the application in operational language, alongside the diagrams in Document 07.

## Stage 1 — PO and SC originate in RM
1. Customer/PO information is maintained in the source business process.
2. Designer creates the RM requisition/SC in V-SMART.
3. Designer enters manufactured product, sub-products/components, BOM and raw-material requirements.
4. RM submission is the trigger for Production intake. Production does not create a second SC.
5. RM sends the SC and available source data to Production.

## Stage 2 — Production intake and identity creation
1. Production authenticates and validates the RM event.
2. It checks the stable RM SC/requisition ID and event ID to prevent duplicate imports.
3. It creates the Production record while preserving the RM source ID.
4. It displays PO number/customer, SC number, manufactured product and BOM rows in the hierarchy reflected in the supplied screen.
5. It creates set records and individual component records based on target set quantity and BOM quantity-per-set.
6. Each set/component receives a unique identity and QR traceability reference. Exact ID syntax is configured/approved separately.

## Stage 3 — Process template, drawing and tolerance setup
1. Authorized user selects the approved process template for each manufactured product/component flow.
2. Template defines operation order, instructions, standard parameters and standard durations.
3. Design uploads SC-specific tolerance sheet (Excel primarily, PDF also) and applicable drawing/CAD PDF.
4. System extracts values and maps each value to SC + product/component + operation + parameter.
5. Design verifies/corrects extraction and submits it.
6. PM approves or rejects the tolerance revision. Only approved revision is released to production.
7. Any later change is a revision request; PM is notified and approval is required. Existing measurements are never overwritten.

## Stage 4 — PM planning and daily allocation
1. New SCs enter the planning queue.
2. PM reviews delivery date, SC age, manager priority, current workload and capacity.
3. System calculates workload from template operation times and employee availability.
4. PM confirms priorities, schedule and employee assignments.
5. Operator sees a priority-ordered worklist.

## Stage 5 — Material issue and readiness
1. Stores performs material issue in RM only.
2. RM sends issue status, issued/pending quantities, transaction reference and issue timestamp where available.
3. Production updates material visibility and calculates whether the relevant operation is ready.
4. If required material is not confirmed, the operator cannot enter data/start the material-dependent operation.
5. Production never creates stock issue, return or additional material request.

## Stage 6 — Operator execution
1. Operator opens assigned worklist and selects PO/SC, set/component and operation.
2. System displays instructions, active approved tolerance and material readiness.
3. Operator selects eligible pending quantity and in-house/vendor route as permitted.
4. Operator performs the operation and enters actual measurements per piece/component as configured.
5. System validates mandatory fields and records actual values, including out-of-tolerance values; it never silently edits them.
6. Submitted quantity becomes available for QC where QC is required.

## Stage 7 — QC inspection and normal progression
1. QC opens inspection worklist.
2. QC physically inspects and enters independent measurement values.
3. System compares QC values with approved tolerance revision.
4. Pass quantity is released to next operation.
5. Failed quantity is blocked and PM is notified.
6. Quantity splitting preserves pass, pending, rework, restart and other statuses without loss/duplication.

## Stage 8 — QC failure, rework and restart
1. QC records failed quantity and reason.
2. PM decides disposition.
3. Rework can use a temporary rework operation or route back to an earlier operation with revised instructions.
4. After rework passes QC, quantity rejoins normal sequence immediately after original failed operation.
5. Restart routes quantity to first operation of the applicable process while preserving all earlier history.
6. Override requires an authorized approver and mandatory reason; approval does not alter measurements.
7. Applicable exception data is sent to RM and ECR. ECR handles responsibility/costing separately.

## Stage 9 — Vendor processing
1. Authorized user selects vendor route, operation and quantity.
2. Production records vendor tracking, expected return and status.
3. Production sends a vendor-processing alert/reference to RM.
4. RM creates and manages Vendor DC dispatch and inward transactions.
5. When RM records inward, it automatically notifies Production.
6. Production updates returned/pending quantities.
7. Internal QC user enters QC measurements for RM-confirmed returned quantity.
8. Partial returns are inspected independently; balance remains vendor-pending.

## Stage 10 — Complete set, packing and dispatch
1. Each component continues through its required operations and QC.
2. A set is production-complete only when every required BOM component for that set is complete and passes required final QC.
3. Packing verifies every required component of the complete set.
4. Packing confirmation changes status to Dispatch Pending.
5. Dispatch employee confirms actual dispatch and records quantity/date-time/responsible employee.
6. Only dispatched complete sets become Delivered.
7. Other sets in the same SC may remain in production. Billing remains outside Production.

## Stage 11 — Management monitoring
Management dashboards show live SC/set/component/operation progress, WIP, material shortages, QC blocks, rework, vendor pending/returns, packing-ready, dispatch-pending and delivered quantities. Reports and exports are permission-filtered.
