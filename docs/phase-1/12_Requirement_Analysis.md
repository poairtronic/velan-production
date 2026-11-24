# 12 — Requirement Analysis (Consolidated Baseline)

Updated 2026-11-24 — decisions D-01 to D-19 (see 00_DECISION_LOG.md); RM and ECR code audits.

**Source basis:** Revised `requirement analysis.pdf`, `Velan_Production_Requirements_and_Development_Plan.docx`, live RM and ECR codebase audits (2026-11-24), and confirmed decisions D-01 to D-19 in `00_DECISION_LOG.md`. The decision log overrides older conflicting draft statements.

## 1. Application purpose
Velan Production manages the manufacturing lifecycle after RM submits the requisition/SC (with permanent manual entry and Excel import capabilities). It is the operational source for production routing, operator work, measurements, QC, rework, vendor-operation monitoring, and automatic completion (piece Packed → product complete → SC Delivered). Billing is outside Production. (D-10)

## 2. Confirmed system boundaries
- RM V-SMART is accessed read-only by Production using a read-only service account. Production reads SC data, product quantities, material-issued flag, and vendor DC details/return status. Production sends nothing to RM. (D-05, D-09, D-11, BR-067)
- ECR receives exception information from Production through its existing intake endpoint; returns an acknowledgement report number. ECR owns responsibility and defect costing independently; costing results do not return to Production. (D-09)
- MERC and Velan Dashboard: no integration now; read access later; write only if required in future. (D-12)
- AI Agent / Velan AI is excluded.

## 3. Confirmed role structure and authority
Confirmed six primary roles: **Admin, PM, Designer, Operator, QC, Management**. (D-03)
- **Admin:** User/role/permission administration, individual login credentials, notification policy, deletion/cancellation, administrative/security logs.
- **PM:** Production planning, operator daily priority lists, QC failure decisions (CONTINUE with reason, EXTRA PROCESS, RETURN TO EARLIER OP, REJECT AND RESTART), manual material gate / replacement release overrides, tolerance approval.
- **Designer:** Manual SC entry and Excel import, selects process flow per product, uploads drawings and tolerance sheets, verifies extracted values. Cannot approve tolerances, edit reusable templates, enter operator/QC values, or decide QC flags.
- **Operator:** Assigned operation work and bulk measurement entry; no start/stop buttons.
- **QC:** Independent measurements on every process; flags non-conformance.
- **Management:** Permitted dashboards, reports, and daily 9 AM report.
- Final business acceptance: PM or Admin. Individual login per employee.

## 4. PO/SC/product/piece hierarchy
The business hierarchy is PO → SC → product → piece. (D-02)  
The supplied UI screenshot represents the RM source view (PO number and customer, multiple SC cards, manufactured product name and target quantity, and raw material items). Production maps each RM line item directly to a manufactured product and generates individual pieces.

## 5. Piece model and identity
Tracking is at the individual piece level. Piece count per product = the quantity RM sends for that product (one RM line per product; SRG is a set = 2 numbers and RM already sends the number; e.g. APG 50 means 100 SRG). (D-02)  
Proposed serial syntax: `SC-2579-APG-001` (OPEN — BUSINESS DECISION REQUIRED: O-02).  
*Set concept:* The previous drafts defined set-level completion and partial dispatch of complete sets. Confirmed rule D-10 completes the SC automatically when all products are complete. Whether "set" remains as a logical grouping (e.g. per SC target quantity) or the SC is the only delivery unit is OPEN — BUSINESS DECISION REQUIRED (O-03). The earlier `SC002-1 ARG` label discussion is superseded.

## 6. Material readiness gate
Stores marks material issued in RM. Production only needs issued yes/no per product. (D-04) Until the RM sync is live or if delayed, PM/Admin sets the flag manually. Operators cannot start or enter data until the flag is set. No stock issue/return/adjustment screens exist in Production.

## 7. Tolerance and measurements
- Reusable templates supply standard/static operation parameters.
- Designer selects process flow per product; flow is snapshotted per product. (D-14)
- Designer uploads SC-specific tolerance sheet (Excel primarily, PDF supported).
- Five parameter types supported: numeric, yes/no, text, signature, image. (D-14)
- System extracts/maps values; Designer verifies/corrects; PM approves.
- Operator measurements are entered in a bulk grid per piece (rows = pieces, columns = parameters) with paste from Excel. (D-16)
- QC enters independent measurements on every process (blind QC). (D-06)

## 8. QC and exceptions
- QC flags raise non-blocking alerts; the flagged piece waits for the PM decision. (D-07)
- Four PM decisions: CONTINUE (mandatory reason), EXTRA PROCESS, RETURN TO EARLIER OPERATION, REJECT AND RESTART.
- Reject and restart: scraps the piece (history kept); creates a replacement piece with a new serial linked to the scrapped piece (`replaced_piece_id`) starting at operation 1. (D-08)
- Replacement piece release is gated until RM shows the ECR material as issued (matched by ECR report number) or PM releases it manually with a reason. (D-08, BR-061)
- Extra process creates a new operation with its own tolerance list; after QC pass the piece continues after the originally failed operation. (D-19, BR-070)
- Return to earlier operation rewinds the piece; rework tracked via incremented iteration counter; after QC pass the piece continues after the originally failed operation. (D-19)
- Override workflow as a separate process is removed; an override is the PM choosing CONTINUE with a mandatory reason. (D-07)
- Exceptions are sent to ECR only (never to RM). (D-09)

## 9. Vendor processing
RM creates, dispatches and inwards the Vendor DC. Production tracks no vendor dates and does not alert RM. Production reads DC details (vendor, DC number, process name, product, quantity) and return status (received / partial / not received). (D-05) After return inward in RM, a Production user tags returned pieces and selects the process; QC enters independent values. A returned piece may complete any pending operation (usually the next); in-house operations stay sequential. (D-05, BR-065)

## 10. Completion
A piece is **Packed** automatically when all its operations are passed. A product is complete when all its pieces are Packed. The **SC becomes Delivered automatically** when all products are complete. (D-10) No packing or dispatch screens, statuses, roles or dispatch confirmation. Billing is outside Production. Set-level completion and partial delivery are OPEN — BUSINESS DECISION REQUIRED (O-03).

## 11. Notifications and deletion
Only Admin manages notification settings for users; users cannot independently disable any notification type, including critical. Admin alone controls deletion/cancellation. Preserve audit history and reason.

## 12. Important source conflicts resolved by latest clarification
| Older draft statement | Latest controlling rule |
|---|---|
| Packing sets Dispatch Pending; dispatch employee confirms dispatch before Delivered | **Packed automatically, SC Delivered automatically; no dispatch step or screens** (D-10; supersedes older draft) |
| Five confirmed roles (Design assumed in RM) | **Six confirmed roles incl. Designer** (Admin, PM, Designer, Operator, QC, Management; D-03) |
| Vendor return manually recorded; vendor dates tracked; alerts sent to RM | **RM creates DC and inward; Production reads details and return status; no alert to RM, no dates** (D-05) |
| Production-to-RM exception communication | **Exceptions sent to ECR only; nothing sent to RM** (D-09, BR-067) |
| Separate override request/approval workflow | **PM decides; CONTINUE with mandatory reason** (D-07) |
| QR labels and scanning on shop floor | **Removed from baseline; operators pick pieces from queue** (OPEN — BUSINESS DECISION REQUIRED: O-08) |
| Redis cache and RabbitMQ message broker | **Removed; Caffeine in-process cache and database outbox/sync log** (D-13) |
| Users may disable critical notifications | Only Admin manages user notification settings; users cannot disable notifications themselves |
| Tolerance revisions overwrite active values broadly | PM approval required; changed values apply only to data not yet entered; never overwrite existing measurements |
| ECR costing returns to Production | No ECR costing or responsibility result returns to Production under confirmed boundary |

## 13. Open items (OPEN — BUSINESS DECISION REQUIRED)
- **O-01:** Source of the delivery/due date (RM `purchase_orders.reference_date` vs manual/Excel per SC).
- **O-02:** Confirm the piece serial format (`SC-2579-APG-001`).
- **O-03:** Set concept — decide whether "set" remains as a grouping (e.g. per SC target quantity) or the SC is the only delivery unit.
- **O-04:** Confirm D-11 (RM read-only polling, ECR existing intake, ADDITIONAL_PROCESS sent to ECR as REWORK with a label).
- **O-05:** SC cancellation / quantity reduction after work has started.
- **O-06:** Excel formats: SC import sheet and tolerance sheet.
- **O-07:** Real-time mechanism: SSE vs WebSocket.
- **O-08:** QR codes and scanning are removed from baseline (operators pick from their queue); confirm they stay out.
- **O-09:** Credential type and minimum length for shop-floor roles.
- **O-10:** Final status vocabulary and reason master.
- **O-11:** Backup, retention and performance targets.
- **O-12:** Who may edit process templates (assumed Admin/PM; Designer selects only).
*(Closed items: field names from audits; material granularity: per product yes/no; rejection = scrap + replacement; roles = six).*

## 14. Source fidelity note
RM and ECR field names come from code audits of the live apps (2026-11-24). API property names must still be verified against live responses during phase 13 (marked VERIFY in Document 09).
