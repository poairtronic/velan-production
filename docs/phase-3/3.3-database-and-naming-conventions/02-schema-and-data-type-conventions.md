# Part 3.3 — Database and Naming Conventions
## Document 02: Database Schema, Data Types, and Append-Only Protection

---

### 1. Confirmed Rules (Frozen Baseline)
* **PostgreSQL Engine Baseline (`D-13, ADR-08`):** The relational persistence store is PostgreSQL (Version 16+) on Neon Serverless.
* **Deterministic Decimal Precision (`SKILLS.md §4.2`):** Dimensional measurements, nominal values, and tolerance limits must never use floating-point types (`float`, `double`). They must be stored as `NUMERIC` with explicit scale.
* **Append-Only Immutability (`D-06, BR-032, BR-049`):** Measurement values, QC inspection results, PM disposition records, tolerance revisions, and audit trails must never be updated or deleted in the database.
* **Zero Cascade Deletes on Production Assets (`Part 2.2 §1.2`):** Foreign keys referencing purchase orders, subcontracts, products, BOM lines, pieces, operations, and measurements specify `ON DELETE RESTRICT`.

---

### 2. Proposed Design: PostgreSQL Data Type Standards

#### 2.1 Prescriptive Data Type Mapping Table

| Logical Data Domain | PostgreSQL Data Type | Java 21 Type | TypeScript Type | Architectural Rules & Constraints |
| :--- | :--- | :--- | :--- | :--- |
| **Primary Keys (PK)** | `UUID` | `java.util.UUID` | `string` | Generated via `DEFAULT gen_random_uuid()`. Uniform 128-bit UUIDv4. |
| **Foreign Keys (FK)** | `UUID` | `java.util.UUID` | `string` | Matches referenced entity PK. Must have an explicit B-tree index. |
| **Timestamps** | `TIMESTAMPTZ` | `java.time.Instant` | `string` (ISO-8601) | Always UTC (`TIMESTAMP WITH TIME ZONE`). Suffix with `_at`. |
| **Calendar Dates** | `DATE` | `java.time.LocalDate` | `string` (YYYY-MM-DD)| Strictly for date-only attributes (e.g. `order_date`, `due_date`). |
| **Linear Dimensions** | `NUMERIC(12, 3)` | `java.math.BigDecimal` | `string` / decimal | 3 decimal places for metric millimeters (e.g. `150.250`). |
| **Angles & Offsets** | `NUMERIC(10, 2)` | `java.math.BigDecimal` | `string` / decimal | 2 decimal places for degrees or general percentages. |
| **Quantities & Counts**| `INTEGER` / `BIGINT` | `Integer` / `Long` | `number` | Positive integers for piece quantities, set counts, sequence orders. |
| **Bounded Codes** | `VARCHAR(n)` | `String` | `string` | Used for indexed codes: `VARCHAR(100)` for serials/POs, `VARCHAR(50)` for part codes. |
| **Free-Form Text** | `TEXT` | `String` | `string` | Used for unbounded text: `revision_reason`, `justification_notes`, `defect_description`. |
| **Boolean Flags** | `BOOLEAN` | `Boolean` | `boolean` | `NOT NULL DEFAULT false`. Prefixed with `is_` or `has_`. |
| **Domain Enums** | `VARCHAR(50)` + CHECK | Java `enum` | TypeScript union | Stored as text with `CHECK (status IN (...))`. Native PG enums forbidden. |
| **Semi-Structured Docs**| `JSONB` | Java `record` | TypeScript interface | Strictly restricted to template snapshots, audit diffs, outbox payloads. |

---

#### 2.2 The Prohibition of Native PostgreSQL Enums (`CREATE TYPE ... AS ENUM`)
Native PostgreSQL database enums are **strictly prohibited** in Velan Production schema migrations:
* **Reason 1 (Migration Friction):** Adding new enum values to native PG types requires exclusive table locks or cannot be executed inside a multi-statement transaction in older PostgreSQL versions. Renaming or removing an enum value requires dropping and recreating dependent columns.
* **Reason 2 (Flyway Compatibility):** `VARCHAR(50)` paired with a database `CHECK (status IN ('...'))` constraint allows atomic, rollback-safe alterations in Flyway using `ALTER TABLE ... DROP CONSTRAINT ... ADD CONSTRAINT ...` within standard transactions.
* **Reason 3 (JPA Mapping):** Direct, transparent mapping via `@Enumerated(EnumType.STRING)` without custom PostgreSQL JDBC user-type converters.

---

#### 2.3 Required Standard Columns Across Entity Categories

Every database table must adhere to one of three standard structural templates:

```text
1. Standard Mutable Aggregate Root (e.g. subcontracts, assembled_sets, pieces)
   ├── id                 UUID PRIMARY KEY DEFAULT gen_random_uuid()
   ├── version            INTEGER NOT NULL DEFAULT 0            <-- Optimistic Lock (@Version)
   ├── status             VARCHAR(50) NOT NULL                  <-- State Machine Status
   ├── created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
   ├── created_by_user_id UUID NOT NULL
   ├── updated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
   └── updated_by_user_id UUID NOT NULL

2. Append-Only Event / Record (e.g. operation_measurements, qc_inspections, pm_dispositions)
   ├── id                 UUID PRIMARY KEY DEFAULT gen_random_uuid()
   ├── <parent>_id        UUID NOT NULL REFERENCES ...
   ├── recorded_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
   ├── recorded_by_user_id UUID NOT NULL
   └── [Data Payload]     (ZERO version or updated_at columns)

3. Status History Table (e.g. piece_operation_history, piece_status_history)
   ├── id                 UUID PRIMARY KEY DEFAULT gen_random_uuid()
   ├── <aggregate>_id     UUID NOT NULL REFERENCES ...
   ├── old_status         VARCHAR(50)
   ├── new_status         VARCHAR(50) NOT NULL
   ├── changed_by_user_id UUID NOT NULL
   ├── change_reason      TEXT
   └── recorded_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
```

---

#### 2.4 Soft Deletion and Cancellation Governance
* **Zero Physical Deletes on Production Records:** Tables governing commercial contracts, products, pieces, operations, measurements, exceptions, and audit logs forbid `DELETE` statements.
* **Cancellation Protocol (`D-18, BR-050`):**
  - Cancellation is restricted exclusively to `ROLE_ADMIN`.
  - Aggregates transition to a terminal status `CANCELLED`.
  - Mandatory audit columns: `cancelled_at TIMESTAMPTZ`, `cancelled_by_user_id UUID`, `cancellation_reason TEXT NOT NULL`.
  - All foreign keys enforce `ON DELETE RESTRICT`.

---

#### 2.5 Database-Level Protection for Append-Only Tables

To guarantee that measurements, QC results, and audit trails cannot be altered—even by rogue application queries—a **Defense-in-Depth Protection Strategy** is enforced:

| Protection Mechanism | Technical Implementation | Evaluation & Trade-offs | Recommendation |
| :--- | :--- | :--- | :--- |
| **Option 1: PostgreSQL Privilege Revocation** | `REVOKE UPDATE, DELETE ON operation_measurements FROM velan_app_user;` | Native, zero-runtime overhead. Perfectly blocks application user from modifying historical data. | **Recommended Primary Gate** |
| **Option 2: Database BEFORE Triggers** | `CREATE TRIGGER trg_no_update BEFORE UPDATE OR DELETE ON operation_measurements FOR EACH ROW EXECUTE FUNCTION abort_mutation();` | Intercepts all users including DB superusers; provides custom descriptive SQLSTATE error message. | **Recommended Defense-in-Depth** |
| **Option 3: PostgreSQL DO INSTEAD Rules** | `CREATE RULE no_del AS ON DELETE TO operation_measurements DO INSTEAD NOTHING;` | Silently ignores mutations; confusing to ORMs that check affected row counts. | **Rejected** |

* **Architectural Decision:** Combine **Option 1** (REVOKE on application database role) with **Option 2** (trigger raising exception `ERR_IMMUTABLE_RECORD`) to physically protect `operation_measurements`, `qc_inspections`, `pm_dispositions`, `tolerance_revisions`, and `audit_logs`.

---

#### 2.6 Physical Piece Serial Number Storage Standard

In accordance with Phase 2 Domain Modeling (`BR-007, Part 2.2 §1.1`), the `pieces` table separately stores discrete structural components alongside a generated unique serial string:

1. **Discrete Normalized Columns:**
   - `sc_id` (`UUID NOT NULL`): Originating Size Card.
   - `sc_product_id` (`UUID NOT NULL`): Parent product line item.
   - `assembled_set_id` (`UUID`): Allocated valve set (nullable until set assignment).
   - `set_sequence_number` (`INTEGER NOT NULL`): Set sequence index within contract line.
   - `component_code` (`VARCHAR(50) NOT NULL`): Engineering part code (e.g. `ARG`, `SPG`).
   - `component_sequence_in_set` (`INTEGER NOT NULL`): Component index within set (e.g. Spindle 1 of 2).
   - `replacement_sequence` (`INTEGER NOT NULL DEFAULT 0`): `0` for original piece; `1, 2...` for scrap replacements.
2. **Display Serial Column:**
   - `serial_number VARCHAR(100) UNIQUE NOT NULL`: The human-readable and barcode-printable identifier.
3. **Format Syntax Status:** **OPEN (`O-02`).** (Proposed: `SC-2579-APG-001` or `SC002-APG-S01-C01-R0`). The database schema supports any syntax because discrete components are preserved independently.

---

#### 2.7 JSONB Usage Rules & Guardrails
`JSONB` columns are strictly restricted to three specific entities:
1. `process_template_snapshots.snapshot_json`: Point-in-time immutable freeze of operation steps and nominal tolerances at SC approval (`BR-018`).
2. `audit_logs.diff_json`: Before/after delta of administrative overrides and state transitions (`BR-054`).
3. `outbox_events.payload`: Ephemeral JSON payload transmitted to ECR (`ADR-02`).
* **Guardrail:** GIN indexes (`USING GIN(payload)`) must be declared on JSONB columns that are queried directly. Relational business entities must never be collapsed into JSONB documents.

---

### 3. Technical Assumptions
1. **ASSUMPTION:** PostgreSQL 16 `gen_random_uuid()` executes within microsecond overhead and eliminates PK sequence contention.
2. **ASSUMPTION:** Application database connection pool on Render connects as role `velan_app_user` with restricted DML privileges, while Flyway executes migrations as a distinct DDL migration role.

---

### 4. Open Questions
* **O-DB-01:** Should Flyway migrations create a dedicated read-only role (`velan_readonly_user`) in Neon for production monitoring and reporting dashboards? (Documented in Document 06).
