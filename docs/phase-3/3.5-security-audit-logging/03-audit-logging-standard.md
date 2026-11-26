# Part 3.5 — Security, RBAC, Audit and Logging Standards
## Document 03: Immutable Audit Logging Standards, Event Schemas, and Access Governance

---

### 1. Confirmed Rules

#### 1.1 The Mandatory Audited Actions List (BR-054)
Every privileged, sensitive, or irreversible state-change in the system must emit an immutable audit event:
1. **Approvals:** Process template approvals (`PLANNING:TEMPLATE_APPROVE`), tolerance revision approvals (`QUALITY:TOLERANCE_APPROVE`), final piece acceptance (BR-051).
2. **Concessions:** PM choosing "CONTINUE" on an out-of-spec piece with mandatory engineering reason (D-07, BR-054).
3. **Cancellations / Soft-Deletes:** Admin cancellation of contracts, pieces, or work assignments with mandatory reason (BR-050).
4. **Operation Bypasses:** Explicit bypass of an operation in a piece routing.
5. **Reassignments:** Workstation reassignments, shift transfers, and PM project reassignments upon employee suspension (06_Role_Matrix §3).
6. **Material Flag Changes:** Manual toggling of the RM material-issued readiness flag by PM/Admin (D-04).
7. **Status Changes:** All piece transitions, operation completions, and QC pass/fail verdicts.
8. **Permission & Role Modifications:** Role creation, role permission changes, user-role assignments, and individual overrides (BR-053).
9. **Authentication & Security Events:** Successful logins, failed login lockouts, password reset requests, session revocations.
10. **Integration Admin Actions:** Manual retry of failed ECR outbox events, manual trigger of RM material synchronization.

#### 1.2 Audit Log Read Access Governance (BR-055)
Access to audit records is partitioned strictly by role:
* **`ROLE_ADMIN`:** Full, unrestricted visibility across all audit records, including security, user lifecycle, permission overrides, and integration outbox events.
* **`ROLE_PM`:** Visibility restricted strictly to **production activity logs** (piece progression, operator measurements, QC evaluations, exception dispositions, tolerance revisions) for their assigned SCs. PMs are strictly prohibited from viewing security, user-management, or permission-change logs (BR-055).
* **`ROLE_OPERATOR`, `ROLE_QC`, `ROLE_DESIGNER`, `ROLE_MANAGEMENT`:** Strictly barred from accessing the administrative audit log console.

#### 1.3 Append-Only Relational Protection (Defense-in-Depth)
Audit records are legally binding manufacturing and quality evidence (critical for nuclear, defense, and oil/gas valve traceability). They must be tamper-proof against both application bugs and unauthorized database operations:
1. **Database Role Privileges:**
   ```sql
   REVOKE UPDATE, DELETE, TRUNCATE ON audit_logs FROM velan_app_user;
   ```
2. **Database Trigger Defense:**
   A PostgreSQL `BEFORE UPDATE OR DELETE` trigger on `audit_logs` raises an uncatchable exception:
   ```sql
   RAISE EXCEPTION 'TAMPER_DETECTED: Records in audit_logs are permanently immutable.';
   ```

---

### 2. Proposed Design

#### 2.1 Audit Event Schema

```typescript
export interface AuditEvent {
  id: string;                 // UUIDv4 primary key
  actorUserId: string;        // UUID of the authenticated user
  actorUsername: string;      // Username at time of action
  actorRole: string;          // Primary active role of actor
  action: string;             // Prescriptive action code (e.g. TOLERANCE_APPROVE)
  entityType: string;         // Domain aggregate type (e.g. PIECE, TOLERANCE)
  entityId: string;           // Identifier of target entity (UUID or serial)
  oldValue: Record<string, unknown> | null; // Previous state diff (JSONB)
  newValue: Record<string, unknown> | null; // New state diff (JSONB)
  reason: string | null;      // Mandatory justification for privileged actions
  timestamp: string;          // ISO-8601 UTC string (YYYY-MM-DDTHH:mm:ss.SSSZ)
  correlationId: string;      // Traceable request correlation UUID
  sourceIp: string;           // Client IP address
  userAgent: string;          // Browser client user-agent string
}
```

#### 2.2 Performance Architecture & Write Guarantees
Audit logging must never degrade shop-floor operator throughput:
1. **Critical Domain Transactions (Synchronous In-Transaction):**
   * Actions modifying pieces, measurements, QC verdicts, PM dispositions, and permission overrides write the `audit_logs` record inside the **same local ACID database transaction** (`Propagation.MANDATORY` or via Spring `@TransactionalEventListener(phase = BEFORE_COMMIT)`).
   * *Rationale:* If the business transaction commits, the audit record is 100% guaranteed to exist; if the transaction rolls back, no phantom audit event is recorded.
2. **Informational / Security Events (Asynchronous):**
   * Successful logins, viewing dashboards, and download events are published to an asynchronous Spring `@Async` executor queue, persisting audit entries with zero impact on response latency (< 3ms).
3. **Indexing for Fast Filter Queries:**
   * Compound indexes: `(entity_type, entity_id, timestamp DESC)` and `(actor_user_id, timestamp DESC)`.

#### 2.3 Compliance Export Standard
* Administrators can export filtered audit logs via `POST /api/v1/admin/audit-logs/export`.
* Output formats: **CSV** (for regulatory spreadsheets) or **JSON Lines** (for SIEM ingestion).
* Exports are capped at 50,000 records per query, streamed directly without loading the entire dataset into container heap memory, and the export action itself is audited.

---

### 3. Technical Assumptions
1. **ASSUMPTION:** PostgreSQL JSONB columns `old_value` and `new_value` store minimal field-level diffs rather than full database table snapshots, conserving storage capacity.
2. **ASSUMPTION:** Actor metadata (`actorUserId`, `actorRole`, `sourceIp`, `correlationId`) is populated automatically via a shared JPA EntityListener (`AuditEntityListener`) inspecting Spring Security context and SLF4J MDC.

---

### 4. Open Questions
* **O-11 (Audit Retention Policy — OPEN):** What is the mandatory retention period for production and quality audit logs?
  - *Option A:* 7 years (standard enterprise manufacturing statutory compliance).
  - *Option B:* 10 years (nuclear / high-pressure ASME Section III valve requirements).
  - *Option C:* Indefinite cold storage in Cloudflare R2 / S3 Glacier after 3 years active in PostgreSQL.
  *(This policy remains formally OPEN per Decision Log O-11).*
