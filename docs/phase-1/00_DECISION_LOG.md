# 00_DECISION_LOG.md — Velan Production Decision Log

Updated 2026-11-24 — decisions D-01 to D-19 (see 00_DECISION_LOG.md)

**Date:** 2026-11-24  
**Rule:** these decisions override the conflicting statements in files 01–15. The `*_UPDATES.md` files reference the IDs below (D-xx = confirmed, O-xx = open).

## A. Confirmed decisions

| ID | Decision |
|---|---|
| D-01 | **Root record:** the SC created in RM (SC number). One Production SC record per RM SC. SCs can also be created in Production by **manual entry** and **Excel import**; both stay permanently, also after RM integration. |
| D-02 | **Tracking:** individual **piece** tracking. Piece count per product = the quantity RM sends for that product (one RM line per product; SRG is a set = 2 numbers and RM already sends the number; e.g. APG 50 means 100 SRG). Proposed serial: `SC-2579-APG-001` (see O-02). |
| D-03 | **Roles (6):** Admin, PM, Designer, Operator, QC, Management (dashboard view). RBAC rules unchanged (Admin override, deny wins, no grant = deny). Packing, Dispatch, Stores, Vendor Coordinator and Department Head are **not** roles. **Individual login** per employee. |
| D-04 | **Material gate:** Stores marks material issued in RM; Production only needs issued yes/no per product. Until the RM sync is live, PM/Admin sets the flag manually. Operators cannot start or enter data until it is set. |
| D-05 | **Vendor:** RM creates, dispatches and inwards the DC. Production does not choose the vendor, does not alert RM, and tracks **no vendor dates**. Production records DC details (vendor, DC number, process name, product, quantity) and whether the return is received / partial / not received. After return, QC enters values; pieces are tagged to the return at inward. A returned piece may complete any pending operation (usually the next); in-house operations stay sequential. |
| D-06 | **QC:** required on every process; QC enters independent values; operator and QC values are stored separately. RM's own DC QC gate stays in RM and only records the received quantity to close the DC; all measurements are recorded in Production only. |
| D-07 | **QC decision:** a flagged piece waits for the PM. Options: **CONTINUE** (mandatory reason), **EXTRA PROCESS**, **RETURN TO EARLIER OPERATION**, **REJECT AND RESTART**. Flags raise non-blocking alerts. There is no separate override request/approval workflow; an "override" is the PM choosing CONTINUE with a reason. |
| D-08 | **Reject and restart:** the piece is scrapped (history kept); a **replacement piece with a new serial linked to the old one** is created and starts at operation 1. It is blocked until RM shows the ECR material as issued (matched by ECR report number) or the PM releases it manually. |
| D-09 | **Exceptions go only to ECR** (not to RM). ECR returns only an acknowledgement (report number). No costing/responsibility returns. Production never waits for ECR. |
| D-10 | **Completion:** a piece is **Packed** when all its operations are passed; a product is complete when all its pieces are Packed; the **SC becomes Delivered automatically** when all products are complete. No packing/dispatch screens, statuses, roles or dispatch confirmation. Billing is outside Production. |
| D-11 | **Integration is built inside the flow, not at the end.** *(Proposed, confirm in O-04)* Production reads RM through its existing read APIs with a read-only service account (polling + "refresh now") and sends exceptions to ECR's existing intake. RM and ECR code stay unchanged; the earlier outbox work is parked on branch `integration-prep` as reference only. |
| D-12 | **MERC and Velan Dashboard:** read access only, later; write access only if required in future. |
| D-13 | **Stack:** Java 21 + Spring Boot, React + TypeScript + Vite, PostgreSQL on Neon, Cloudflare R2, Docker on Render, GitHub Actions, SMTP email. **No Redis** (in-process Caffeine cache). **No RabbitMQ** (database outbox/inbox). |
| D-14 | **Parameters:** types numeric, yes/no, text, signature, image. Tolerance Excel carries SC number, product name and operation values (format TBD). Machine and operator are fixed per operation; a vendor may occasionally do a process. |
| D-15 | **Planning:** the PM daily priority list per operator **and** the full scheduling / capacity / forecast engine are both in scope. |
| D-16 | **Operator UX:** no start/stop buttons; time comes from ready / opened / submitted timestamps. Operators pick pieces from their own queue (no scanning) and enter values in a bulk grid (rows = pieces). |
| D-17 | **Go-live:** new SCs only; an in-progress SC is imported only when the PM asks. |
| D-18 | **Unchanged from the documents:** notification settings are Admin-only; deletion/cancellation is Admin-only; final acceptance by PM or Admin. |
| D-19 | **Rework** supports both extra process (new operation with its own tolerance list) and return to an earlier operation with new instructions; after QC pass the piece continues after the originally failed operation. |

## B. Open items

| ID | Open item |
|---|---|
| O-01 | Source of the delivery/due date (RM `purchase_orders.reference_date` vs manual/Excel per SC). Scheduling priority depends on it. |
| O-02 | Confirm the piece serial format. |
| O-03 | **Set concept.** The documents define set-level completion and partial dispatch of complete sets. Confirmed rule D-10 completes the SC when all products are complete. Decide whether "set" remains as a grouping (e.g. per SC target quantity) or the SC is the only delivery unit. |
| O-04 | Confirm D-11 (RM read-only polling, ECR existing intake, ADDITIONAL_PROCESS sent to ECR as REWORK with a label). |
| O-05 | SC cancellation / quantity reduction after work has started. |
| O-06 | Excel formats: SC import sheet and tolerance sheet. |
| O-07 | Real-time mechanism: SSE vs WebSocket. |
| O-08 | QR codes and scanning are removed from the baseline (operators pick from their queue). Confirm they stay out. |
| O-09 | Credential type and minimum length for shop-floor roles. |
| O-10 | Final status vocabulary and reason master. |
| O-11 | Backup, retention and performance targets. |
| O-12 | Who may edit process templates (assumed Admin/PM; Designer selects only). |
