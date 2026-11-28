# Phase 7 Completion Report — Data Model, Flyway Migrations & Volume Validation

**Date:** 2026-11-28  
**Branch:** `phase-7`  
**Status:** **COMPLETE** (`V001`–`V017` applied on Neon `dev` branch; ready for `main` merge)

---

## 1. Per-Part Summary (7.1 – 7.9)

| Part | Summary |
|---|---|
| **7.1** | Designed the 49-table data model in `docs/phase-7/7.1-data-model.md`, reconciled with `5.1-structure-decisions.md`, closed audit retention, vendor piece/serial timing, `operation_machines`, master/component grouping, and `SC = Size Card`. |
| **7.2** | Added `spring-boot-starter-jdbc`, `spring-boot-starter-flyway`, `flyway-database-postgresql`, `DatabaseProperties` (`prod` fail-fast), `V001__init_shared_kernel.sql`, `MigrationTests`, and `postgres:18` service in `.github/workflows/ci.yml`. |
| **7.3** | Added `V002` (`iam` + `role_permissions`) and `V003` (`engineering` master data + `STATIC`/`NUMERIC` & composite-FK option checks). |
| **7.4** | Added `V004` (`products`, `process_templates`), `V005` (`order`), and `V006` (Size Card snapshots, `drawing_documents`, `files.sc_id` FK). |
| **7.5** | Added `V007` (`machines`, `operation_machines`, `pieces`, `piece_operations`, `piece_events`, `operator_values` + `operator_values_immutable_guard` trigger + `scoped_piece_id` FK) and `V008` (`qc_inspections`, `measurement_values`). |
| **7.6** | Added `V009` (`qcexception`), `V010` (`vendor` + `piece_operations.vendor_dc_tracker_id`), `V011` (`notification`), and `V012` (`integration`). |
| **7.7** | Added `V013` (renamed 114 auto-generated constraints to `fk_`/`uq_`/`chk_`), `V014` (seeded 6 roles + 64 `role_permissions` + `7.7-permission-catalogue.md`), and `V015` (`purge_sc(uuid)` PL/pgSQL function). |
| **7.8** | Added `V016` (renamed 11 inline `CHECK` constraints from `V009`–`V012`), standardized `SC = Size Card` across 37 docs, executed Scale B volume test in throwaway `velan_vol_test`, and compiled this report. |
| **7.9** | Added `V017` (aligned `sc_headers`, `pieces`, `piece_operations`, `qc_inspections`, `qc_exceptions`, `pm_dispositions`, `outbox_events` with Phase 2 state machines; added SC cancellation columns and `chk_sc_headers_cancelled`), closed open items O-12, `qc_inspections` decision codes, and SC cancellation. |

---

## 2. Table Count Per Module (49 Application Tables)

`iam`: **8** | `order`: **6** | `engineering`: **16** | `execution`: **5** | `quality`: **2** | `qcexception`: **3** | `vendor`: **2** | `notification`: **1** | `integration`: **2** | `shared`: **4** (Total: **49**)

---

## 3. Migrations (`V001` – `V017`, All Applied on Neon `dev`)

1. `V001__init_shared_kernel.sql` — `files`, `audit_logs`, `shedlock`, `idempotency_keys`
2. `V002__create_iam_tables.sql` — `departments`, `sections`, `roles`, `role_permissions`, `users`, `user_roles`, `user_permission_overrides`, `user_sections`
3. `V003__create_engineering_master_data.sql` — `parameters`, `parameter_options`, `checklists`, `checklist_parameters`, `operations`, `operation_parameters`, `operation_checklists`
4. `V004__create_engineering_products_and_templates.sql` — `process_templates`, `products`, `process_template_steps`
5. `V005__create_order_tables.sql` — `purchase_orders`, `sc_headers`, `sc_products`, `sc_revisions`, `import_records`, `sc_archive_summary`
6. `V006__create_sc_snapshot_tables.sql` — `sc_product_template_steps`, `sc_product_node_statuses`, `sc_product_step_parameters`, `sc_product_dynamic_values`, `drawing_documents`, `fk_files_sc_id`
7. `V007__create_execution_and_operation_machines.sql` — `machines`, `operation_machines`, `pieces`, `piece_operations`, `piece_events`, `operator_values`, `fk_sc_pts_scoped_piece`, `operator_values_immutable_guard`
8. `V008__create_quality_tables.sql` — `qc_inspections`, `measurement_values`
9. `V009__create_qcexception_tables.sql` — `qc_exceptions`, `pm_dispositions`, `piece_replacement_lineage`
10. `V010__create_vendor_tables.sql` — `vendors`, `vendor_dc_trackers`, `piece_operations.vendor_dc_tracker_id`
11. `V011__create_notification_tables.sql` — `user_notifications`
12. `V012__create_integration_tables.sql` — `outbox_events`, `rm_sync_history`
13. `V013__rename_constraints_to_standard.sql` — renamed 114 auto-generated constraints from `V001`–`V008`
14. `V014__seed_roles_and_permissions.sql` — seeded 6 roles (`ADMIN`: 13, `PM`: 17, `DESIGNER`: 10, `OPERATOR`: 8, `QC`: 9, `MANAGEMENT`: 7; total 64 `role_permissions`)
15. `V015__create_purge_sc_function.sql` — `PURGE_APPROVED` status + `purge_sc(p_sc_id uuid)`
16. `V016__rename_v009_v012_check_constraints.sql` — renamed 11 inline `CHECK` constraints from `V009`–`V012`
17. `V017__align_statuses_with_phase2.sql` — aligned `sc_headers`, `pieces`, `piece_operations`, `qc_inspections`, `qc_exceptions`, `pm_dispositions`, `outbox_events` with Phase 2 state machines; added `cancel_reason`, `cancelled_by`, `cancelled_at` + `chk_sc_headers_cancelled`

---

## 4. Volume Test Results (Scale B: 10 Max Size Cards in `velan_vol_test`, Dropped Afterward)

- **Row Counts & Sizes (tables + indexes):**
  - `operator_values`: **3,888,017** rows — **1,291 MB**
  - `piece_operations`: **216,000** rows — **71 MB**
  - `sc_product_step_parameters`: **25,920** rows — **7,824 kB**
  - `sc_product_dynamic_values`: **21,600** rows — **7,336 kB**
  - `pieces`: **12,000** rows — **3,800 kB**
  - `sc_product_template_steps`: **1,440** rows — **568 kB**
  - `sc_products`: **80** rows — **88 kB** | `sc_headers`: **10** rows — **72 kB**
  - **Total Scale B:** ~**1.35 GB** $\to$ **100 active Size Cards projection:** ~**13.5 GB**
- **Query Timings (`EXPLAIN (ANALYZE, BUFFERS)`):**
  - **Q1** (next 50 pending `piece_operations`, 2 sections): **1.476 ms** (target `< 100 ms`) — Index Scan Backward + Memoize (**PASS**)
  - **Q2** (`operator_values` for 1 `piece_operation`): **0.163 ms** (target `< 100 ms`) — Bitmap Index Scan (**PASS**)
  - **Q3** (dynamic values + step params for 1 step): **0.134 ms** (target `< 100 ms`) — Bitmap Index Scan + Index Scan (**PASS**)
  - **Q4** (`pieces` by status per `sc_product`, 1 SC): **0.530 ms** (target `< 100 ms`) — Bitmap Index Scan (**PASS**)
  - **Q5** (`Packed` derivation for 1 SC): **0.256 ms** (target `< 500 ms`) — Nested Loop Anti Join + Index Only Scan (**PASS**)
  - **Q6** (`piece_operations` by status across all SCs): **61.609 ms** (target `< 500 ms`) — Parallel Seq Scan on 216k rows (**PASS**; note: for 100 active Size Cards, Phase 39 may add a partial index on active `piece_operations (status)` if needed)
  - **Batch insert (200 `operator_values`):** **63 ms**
  - **`purge_sc` (1 max Size Card):** **3,776 ms (3.78 s)** (target `< 120 s`), 9 SCs untouched (**PASS**)

---

## 5. Deviations, UNVERIFIED & Open Items

- **Deviations:**
  - `sc_headers.status` stores `DRAFT_INTAKE`, `ACTIVE`, `CANCELLED`, and `ARCHIVED`; `IN_PRODUCTION`, `PRODUCTION_COMPLETE`, `PARTIALLY_DELIVERED`, and `DELIVERED` are derived from child piece/delivery progress, and `PURGED` is recorded on `sc_archive_summary.status`.
  - Owner decisions (7.9): (1) only Admin cancels a Size Card (`cancel_reason` $> 10$ chars); (2) Admin and PM manage templates, operations, parameters, and checklists while Designer only selects the template per product; (3) the 9 `"Permission-based"` Admin cells stay outside the `ADMIN` role by default and are granted via `user_permission_overrides`.
- **UNVERIFIED:** CI workflow run on `main` (see §5 / merge status).
- **Open Items:**
  1. **ECR `scNumber` check:** Verify external ECR field mapping in Phase 14.
