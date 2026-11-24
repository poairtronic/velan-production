# 12 — Requirement Analysis (Consolidated Baseline)

**Source basis:** Revised `requirement analysis.pdf` (58 pages), `Velan_Production_Requirements_and_Development_Plan.docx`, `Velan_Production_All_Flowcharts_and_Explanation.pdf`, `Velan_Operator_QC_Tolerance_Workflow.pdf`, plus the latest business clarifications in the conversation. Latest clarifications override older conflicting draft statements.

## 1. Application purpose
Velan Production manages the manufacturing lifecycle after RM submits the requisition/SC. It is the operational source for production routing, operator work, measurements, QC, rework, vendor-operation monitoring, packing and dispatch.

## 2. Confirmed system boundaries
- RM V-SMART creates SC/requisition, owns BOM source and all material/stock transactions and Vendor DC transactions.
- Production receives RM data, tracks manufacturing and sends applicable exception information to RM/ECR.
- ECR owns responsibility/costing. ECR costing does not return to Production.
- Billing, MERC and Velan Dashboard integration are excluded.
- AI Agent/Velan AI is excluded.

## 3. Confirmed role structure and authority
Primary roles: Admin, PM, Operator, QC, Management.
- Admin: user/role/permission administration, notification settings for users, cancellation/deletion, administrative/security logs.
- PM: production planning, assignment, scheduling, QC failure disposition, tolerance revision approval, rework/restart routing and production oversight.
- Operator: assigned operation work and operator measurement entry.
- QC: independent measurements and inspection decisions; notify PM on failure; request override where applicable.
- Management: permitted dashboards, reports and performance visibility.
- Final business acceptance: PM or Admin.

## 4. PO/SC/product/BOM hierarchy
The supplied UI screenshot shows PO number and customer at the top, multiple SC cards, each SC with manufactured product name and target quantity, then required raw-material/BOM rows with part name, part number, optional specification/description and quantity per unit. Production must preserve this hierarchy and all source identifiers.

## 5. Set/component quantity identity
A set contains all required components in the SC's BOM. The screenshot's SC002 example has target 4 and illustrative per-set quantities ARG 1, SPG 2, VBM 1, Jig Feet 3; totals are 4, 8, 4 and 12. The set is complete only when every required BOM component for that set is complete. Component identity must show SC, set/unit and component. Example labels discussed include `SC002-1 ARG`, `SC002-1 SPG`, `SC002-2 ARG`; proposed explicit set/component serial syntax remains to be finalized.

## 6. Material readiness
Material issue occurs in RM. RM sends issued quantity/status and issue timing. Production displays and calculates readiness; operator operation data entry is blocked until material issue is confirmed for the relevant material-dependent operation. No stock issue/return/additional request screens exist in Production.

## 7. Tolerance and measurements
- Reusable template supplies standard/static operation parameters.
- Design uploads SC-specific tolerance sheet; Excel preferred, PDF supported.
- System extracts/maps values; Design verifies/corrects; PM approves.
- After start, revision requires PM approval and PM notification.
- Changed values apply to data not yet entered/eligible unfinished work; existing measurement values are not overwritten. Completed history retains prior revision.
- Operator and QC values are independent records; QC enters its own values.

## 8. QC and exceptions
- QC failure notifies PM; PM controls failure/rework decision.
- Restart means return to first operation of applicable production process.
- Rework may be temporary operation or earlier operation; after QC pass, return to normal sequence immediately after original failed operation.
- Additional process requires QC.
- Override requires authorized approval and reason; measurement is unchanged.

## 9. Vendor processing
Production tracks vendor, operation, quantity, expected return and status. RM creates/dispatches/receives Vendor DC. RM inward automatically notifies Production; internal Production user enters QC values for confirmed returned quantity. Partial inward is supported; unreturned balance remains vendor-pending. Vendor does not log in.

## 10. Packing and dispatch
Only complete BOM sets with required QC complete are eligible. Packing verifies components and confirms packing → Dispatch Pending. Dispatch employee confirms actual dispatch → Delivered. Partial dispatch means some complete sets from an SC can be dispatched while others remain in production; no incomplete set is dispatchable.

## 11. Notifications and deletion
Only Admin manages notification settings for users; users cannot independently disable any notification type, including critical. Admin alone controls deletion/cancellation. Preserve audit history and reason.

## 12. Important source conflicts resolved by latest clarification
| Older draft statement | Latest controlling rule |
|---|---|
| Packing confirmation automatically marks Delivered (in one prior working plan) | Packing sets Dispatch Pending; dispatch employee confirms actual dispatch before Delivered |
| Users may disable critical notifications (older selected rule) | Only Admin manages user notification settings; users cannot disable notifications themselves |
| Role lists include Design/Stores/Packing/Dispatch/Department Head as separate primaries | Five confirmed primary roles; other functions can be permission sets/custom role assignments |
| Vendor return manually recorded in Production (older draft) | RM inward automatically notifies Production; Production user enters QC values |
| Tolerance revisions apply to in-progress measurements broadly | Latest clarification: PM approval; changed values apply to data not yet entered; never overwrite existing measurements |
| ECR status/costing return path shown in one older workflow | No ECR costing/responsibility return to Production under confirmed boundary |

## 13. Items still open
1. Exact RM/ECR API endpoints, authentication, event IDs, schema and external field names.
2. Exact set/component ID and QR label format.
3. Precise material readiness granularity (whole BOM versus material needed for a specific operation) where not explicit in RM contract.
4. Exact treatment of already-started quantities after SC cancellation or quantity reduction.
5. Manual provisional SC fallback and reconciliation when RM is unavailable.
6. Whether restart is the only meaning of rejection or whether permanent scrap is a separate terminal disposition.
7. Detailed parameter data types, units, extraction rules and scanned-PDF handling.
8. Final status vocabulary, reason master and QC decision matrix.
9. Final role/action permissions within the five primary role groups.
10. Performance SLOs, backup/retention and deployment sizing.

## 14. Source fidelity note
The integration field names in the revised PDF are explicitly proposed business fields, not verified external RM column/property names. The actual exact field names must be taken from the live API/schema. Do not fabricate them.
