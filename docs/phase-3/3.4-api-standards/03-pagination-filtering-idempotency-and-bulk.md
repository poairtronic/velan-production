# Part 3.4 — API Standards
## Document 03: Pagination, Filtering, Idempotency, and Bulk Operations

---

### 1. Confirmed Rules

#### 1.1 Pagination Standards
1. **Query Parameters:**
   * `page`: Zero-based page index (e.g. `page=0` for the first page). Default: `0`.
   * `size`: Number of records per page. Default: `20`. Maximum allowed limit: `100`.
   * Requests specifying `size > 100` are automatically clamped to `100` by the controller layer (or rejected with `400 Bad Request`).
2. **Spring Data JPA Integration:**
   * Backend controllers accept standard Spring Data `Pageable` objects (`@PageableDefault(page = 0, size = 20) Pageable pageable`).
3. **Response Structure:**
   * Paginated responses return records inside `data: T[]` with pagination metadata inside `meta.pagination`:
     ```json
     {
       "data": [ ... ],
       "meta": {
         "timestamp": "2026-11-26T04:30:00.000Z",
         "correlationId": "c4b1e5a2-3f8d-4e92-b2a1-9876543210ab",
         "pagination": {
           "pageNumber": 0,
           "pageSize": 20,
           "totalElements": 142,
           "totalPages": 8,
           "isFirst": true,
           "isLast": false
         }
       }
     }
     ```

#### 1.2 Sorting and Filtering Syntax
1. **Sorting:**
   * Parameter: `sort=fieldName,(asc|desc)`
   * Multi-column sorting is supported by repeating the parameter:
     - `GET /api/v1/pieces?sort=priority,desc&sort=createdAt,asc`
   * Allowed sort fields must be explicitly whitelisted in service query classes to prevent SQL injection or unindexed database column scans.
2. **Attribute Filtering:**
   * Exact matching: Query parameter matching entity field:
     - `GET /api/v1/pieces?status=QC_PENDING&scNumber=SC-1024`
   * Multi-value filtering: Comma-separated list for `IN` queries:
     - `GET /api/v1/pieces?status=READY,OPENED,QC_PENDING`
   * Date range filtering: Use explicit `From` and `To` parameters in ISO-8601 UTC:
     - `GET /api/v1/qc-exceptions?createdFrom=2026-11-01T00:00:00Z&createdTo=2026-11-26T23:59:59Z`
3. **Full-Text / Prefix Search:**
   * Parameter: `q={query}`
   * Executed against designated high-cardinality search columns (serial number, order number, drawing number):
     - `GET /api/v1/pieces?q=V-1024`

#### 1.3 Idempotency Standard for Mutating Endpoints
1. **HTTP Header:** `Idempotency-Key: <UUIDv4>`
2. **Applicability:** Mandatory for financial/contract imports and high-impact domain creation endpoints (`POST /api/v1/sales-contracts/import`, `POST /api/v1/pieces/{id}/actions/release`, `POST /api/v1/qc-exceptions/{id}/actions/disposition`). Recommended for all mutating `POST` endpoints.
3. **Storage & Verification Mechanism:**
   * The backend maintains an `idempotency_records` table and an in-memory Caffeine cache:
     - Columns: `idempotency_key` (UUID PK), `user_id`, `request_hash` (SHA-256 of request path and body), `status` (`IN_PROGRESS`, `COMPLETED`), `response_code`, `response_body` (JSONB), `created_at`, `expires_at`.
   * **Cache TTL:** Keys are retained for **24 hours**.
4. **Execution Flow & Concurrency Protection:**
   * **First Request:** Insert key in `IN_PROGRESS` status. Execute business transaction. Update record with HTTP response code and body in `COMPLETED` status.
   * **Replayed Request (Identical Payload):** If record is `COMPLETED` and request hash matches, short-circuit execution and return the cached status code and body with header `Idempotency-Replay: true`.
   * **Concurrent Duplicate Request:** If key is currently `IN_PROGRESS`, return `409 Conflict` (`code: "ADMIN_REQUEST_IN_PROGRESS"`).
   * **Mismatched Payload:** If identical key is submitted with differing payload or path, return `409 Conflict` (`code: "ADMIN_IDEMPOTENCY_KEY_REUSED"`).

#### 1.4 Optimistic Locking and Concurrency Control
* **Decision:** The system utilizes an explicit **`version` integer property** within JSON request/response payloads (matching JPA `@Version Integer version`).
* **Justification:** An explicit `version` property in JSON DTOs binds naturally to React Hook Form state and TanStack Query mutations, avoiding header parsing complexities across diverse client environments.
* **Update Verification Protocol:**
  1. Clients include the current `version` integer in mutating requests:
     ```json
     {
       "version": 3,
       "disposition": "CONTINUE",
       "engineeringNotes": "Acceptable deviation within tolerance concession."
     }
     ```
  2. The service layer verifies the submitted version matches the database record under JPA optimistic locking (`WHERE id = :id AND version = :version`).
  3. **Conflict Failure (409 Conflict):** If the entity version in the database is newer, Spring Data throws `OptimisticLockingFailureException`. The global handler intercepts and returns:
     ```json
     {
       "code": "ADMIN_OPTIMISTIC_LOCK_CONFLICT",
       "message": "The record was modified by another user. Please refresh and review latest changes.",
       "details": [
         {
           "field": "version",
           "rejectedValue": 3,
           "message": "Current database version is 4."
         }
       ],
       "timestamp": "2026-11-26T04:30:00.000Z",
       "path": "/api/v1/qc-exceptions/exc-1024/actions/disposition",
       "correlationId": "c4b1e5a2-3f8d-4e92-b2a1-9876543210ab"
     }
     ```

---

### 2. Proposed Design

#### 2.1 Bulk Operations Architecture

#### A. Shop-Floor Bulk Measurement Submission (Operation Grid)
On the shop floor, an operator records multiple dimensional measurements for a single operation at once:
* **Endpoint:** `POST /api/v1/piece-operations/{operationId}/actions/submit-measurements`
* **Transaction Semantics:** **Strictly Atomic (All-or-Nothing).**
  - All measurement entries in the payload are validated together in a single local ACID transaction.
  - If any measurement exceeds physical boundaries, is unparseable, or references an invalid parameter ID, the entire transaction rolls back. No partial measurements are stored.
* **Payload Structure:**
  ```json
  {
    "version": 2,
    "measurements": [
      { "parameterId": "p-01", "actualValue": "150.005", "instrumentId": "G-902" },
      { "parameterId": "p-02", "actualValue": "45.120", "instrumentId": "C-104" }
    ]
  }
  ```
* **Batch Size Limit:** Maximum **50 measurements** per operation submit.

#### B. Excel/CSV Contract and BOM Data Import
For importing Size Cards, Product Line Items, or Process Routings:
* **Endpoint:** `POST /api/v1/sales-contracts/import`
* **Transaction Semantics:** **Configurable Atomic vs Partial-Failure.**
  - Default: **Atomic.** If any row in the imported contract fails validation, the entire contract import aborts, returning a detailed row-by-row error report.
  - Optional query flag: `?allowPartial=true` for bulk master data loads.
* **Batch Size Limit:** Maximum **500 rows** (or 10 MB file size) per import request.
* **Partial-Failure Response Shape:**
  ```json
  {
    "data": {
      "summary": {
        "totalRows": 150,
        "succeededCount": 148,
        "failedCount": 2
      },
      "succeeded": [
        { "rowNumber": 2, "scNumber": "SC-1024", "status": "CREATED" }
      ],
      "failed": [
        {
          "rowNumber": 14,
          "identifier": "SC-1036",
          "errorCode": "ORDER_DUPLICATE_NUMBER",
          "message": "Order number SC-1036 already exists in database."
        },
        {
          "rowNumber": 89,
          "identifier": "SC-1111",
          "errorCode": "ORDER_INVALID_QUANTITY",
          "message": "Quantity must be greater than zero."
        }
      ]
    },
    "meta": {
      "timestamp": "2026-11-26T04:30:00.000Z",
      "correlationId": "c4b1e5a2-3f8d-4e92-b2a1-9876543210ab"
    }
  }
  ```

---

### 3. Technical Assumptions
1. **ASSUMPTION:** PostgreSQL `FOR UPDATE SKIP LOCKED` and database unique constraints prevent duplicate `idempotency_key` insertion under high concurrency.
2. **ASSUMPTION:** In-process Caffeine cache holds recent idempotency keys with a 24-hour expiration policy, reducing database lookup overhead for immediate retry loops.
3. **ASSUMPTION:** Spring Data JPA `@PageableDefault` guarantees deterministic pagination even if the client omits pagination parameters.

---

### 4. Open Questions
* **C-1 (Completion Workflow Model):** Bulk packing checklist endpoints and dispatch staging bay batch queries remain conditioned on Conflict C-1.
* **C-2 (Piece Count Derivation Source):** Bulk piece creation logic upon Excel contract import remains conditioned on Conflict C-2.
