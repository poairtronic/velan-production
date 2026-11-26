# Part 3.3 — Database and Naming Conventions
## Document 03: Flyway Versioning, Schema Migration Standards, and Deployment Policy

---

### 1. Confirmed Rules (Frozen Baseline)
* **Flyway Migration Engine (`SKILLS.md §8`):** All database schema modifications must be authored as Flyway versioned migrations (`V{n}__{description}.sql`).
* **Forward-Only Migrations (`SKILLS.md §8`):** Migrations are strictly forward-only. Down-migrations (`U...`) are prohibited in production. An error in an applied migration must be corrected via a subsequent forward migration (`V{n+1}__...`).
* **Never Edit an Applied Migration (`SKILLS.md §8`):** Once a migration file has been merged and applied to any test or production environment, its contents must never be modified. Modifying applied files causes checksum validation failures (`ValidateFailedException`) and blocks application startup.
* **Direct Compute Connection Invariant (`ADR-08, Part 3.1a`):** Flyway migrations must execute strictly against Neon's **Direct Compute Endpoint** (`ep-xxx.neon.tech:5432`), never through the PgBouncer transaction-pooled endpoint.

---

### 2. Proposed Design: Flyway Migration Standards

#### 2.1 File Naming and Monotonic Ordering
Flyway scripts reside in `src/main/resources/db/migration` and follow strict prefix syntax:

```text
V<VersionNumber>__<description_in_snake_case>.sql
```

1. **Version Numbering Convention:** 3-digit zero-padded monotonic integers:
   - `V001__init_schema_extensions.sql`
   - `V002__create_iam_tables.sql`
   - `V003__create_order_tables.sql`
   - `V004__create_engineering_tables.sql`
   - `V005__create_execution_tables.sql`
   - `V006__create_quality_tables.sql`
   - `V007__create_exception_tables.sql`
   - `V008__create_completion_tables.sql`
   - `V009__create_outbox_and_audit_tables.sql`
   - `V010__create_views_and_triggers.sql`
2. **Double Underscore Separator:** The version prefix is followed immediately by two underscores (`__`), followed by a descriptive lowercase `snake_case` action name.

---

#### 2.2 Migration Scope & Separation of Concerns
1. **One Concern per Migration:**
   - DDL schema changes (creating tables, altering columns, adding indexes) and DML data fixes (seeding master roles, patching corrupt data) must reside in **separate migration files**.
2. **DDL and DML Segregation:**
   - Schema creation: `V015__add_replacement_piece_sequence.sql`
   - Data fix/seed: `V016__seed_system_roles_and_permissions.sql`
3. **Repeatable Migrations (`R__<name>.sql`):**
   - Repeatable scripts are permitted **exclusively** for views, database functions, and trigger procedures:
     - Example: `R__prevent_append_only_mutation_trigger.sql`
     - Example: `R__station_worklist_view.sql`
   - Repeatable scripts must **never** contain table creation DDL, column alterations, or data modification statements.

---

#### 2.3 Flyway Configuration Standards
The backend `application.yml` configures Flyway properties:

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 0
    validate-on-migrate: true
    out-of-order: false
    table: flyway_schema_history
    connect-retries: 5
    init-sqls:
      - "SET statement_timeout = '60s';"
      - "SET lock_timeout = '10s';"
```

* `out-of-order: false`: Rejects migrations out of sequence, preventing accidental backfilling of old migrations.
* `lock-timeout: 10s`: Fails migration promptly if an exclusive lock cannot be acquired within 10 seconds, preventing shop-floor application lockouts.

---

#### 2.4 Pre-Flight Migration Review Checklist
Before any migration script is merged into `main`, it must pass this 8-point checklist:

- [ ] **Direct Connection Target:** Verified that migration executes against direct compute port `5432`.
- [ ] **Foreign Key Indexes:** Every new foreign key column has an accompanying B-tree index (`ix_...`).
- [ ] **Check Constraints:** Enums use `VARCHAR(50)` + `CHECK (col IN ('...'))` (zero native PG enums).
- [ ] **No Table Locks on Heavy Tables:** Large tables avoid unbounded `ALTER TABLE ... DEFAULT <expensive>` on legacy Postgres versions.
- [ ] **Immutability Protection:** Append-only tables have `REVOKE UPDATE, DELETE` applied.
- [ ] **Transactional Safety:** Script does not include statements that cannot run inside a transaction.
- [ ] **Idempotent / Forward-Safe:** Uses `CREATE TABLE IF NOT EXISTS` or standard deterministic DDL.
- [ ] **No Destructive DDL:** Script contains zero `DROP TABLE`, `DROP COLUMN`, or `TRUNCATE` commands.

---

#### 2.5 Automated Testing Strategy for Migrations
Every migration is validated automatically in the CI pipeline (`GitHub Actions`) via two automated Testcontainers integration tests:

1. **Test 1: Fresh Database Initialization Test:**
   - Testcontainers spins up a clean, empty PostgreSQL 16 container.
   - Flyway executes all migrations from `V001` to `V{latest}`.
   - Verifies that all tables, foreign keys, check constraints, and triggers initialize without error.
2. **Test 2: Incremental Upgrade Test:**
   - Testcontainers restores a schema dump representing the previous production release.
   - Flyway executes only the newly introduced migration scripts.
   - Verifies that existing data is preserved and new constraints succeed against populated tables.
3. **Test 3: Flyway Validate Check:**
   - `./mvnw flyway:validate` executes during CI to confirm that applied migration checksums match repository source files byte-for-byte.

---

#### 2.6 Rollback and Disaster Recovery Policy
1. **Forward-Fix Rule:** If an error is detected after a migration is deployed, developers must **never** alter the applied script or execute `flyway repair` in production.
2. **Remediation Procedure:**
   - Author a new forward migration script: `V{n+1}__revert_<feature>.sql`.
   - The forward script executes inverse DDL (e.g. `ALTER TABLE ... DROP CONSTRAINT ...`).
   - The application is re-deployed, and Flyway applies `V{n+1}` cleanly.

---

#### 2.7 Prohibited Actions in Migrations

| Prohibited Action | Risk & Failure Mode | Mandatory Alternative |
|---|---|---|
| **Editing applied migration files** | Causes `ValidateFailedException` on container startup; aborts Render deployment. | Author a new sequential migration script (`V{n+1}__...`). |
| **Executing on Pooled Endpoint** | PgBouncer transaction pooling loses session advisory locks (`pg_advisory_lock`); causes migrations to fail or hang. | Target Neon Direct Compute Endpoint (`ep-xxx.neon.tech:5432`) via `SPRING_FLYWAY_URL`. |
| **Destructive DDL (`DROP / TRUNCATE`)** | Causes irreversible data loss and destroys shop-floor audit history. | Prohibited by `SKILLS.md §8`. Soft-delete / deprecate columns instead. |
| **Native PG Enums (`CREATE TYPE ...`)** | Migration friction; table locking during alterations. | Use `VARCHAR(50)` with `CHECK` constraints. |

---

### 3. Technical Assumptions
1. **ASSUMPTION:** Neon PostgreSQL 16 compute node executes DDL migrations within standard 60-second statement timeouts.
2. **ASSUMPTION:** Spring Boot auto-configuration runs Flyway migrations before JPA `EntityManagerFactory` initializes, ensuring schema is up-to-date before Hibernate validates entity mappings.

---

### 4. Open Questions
* **O-MIG-01:** Should production Flyway migrations be executed during Render Docker container startup (in-app migration), or triggered as a discrete Render pre-deploy release command? (Documented in Document 06).
