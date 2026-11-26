# Part 3.4 — API Standards
## Document 01: REST Conventions, Request/Response Envelopes, and Serialization

---

### 1. Confirmed Rules

#### 1.1 Base URI and Resource Naming
1. **Base Path:** All REST API endpoints must be prefixed with `/api/v1`.
2. **Resource Paths:**
   * Resource identifiers must use lowercase, plural nouns in `kebab-case`:
     - `/api/v1/sales-contracts`
     - `/api/v1/products`
     - `/api/v1/pieces`
     - `/api/v1/piece-operations`
     - `/api/v1/qc-inspections`
     - `/api/v1/qc-exceptions`
     - `/api/v1/drawing-documents`
   * Singular nouns in collection paths are strictly prohibited (e.g. `/api/v1/piece` is forbidden).
   * Path parameters must use standard `camelCase` identifiers matching the DTO/entity identifier:
     - `/api/v1/pieces/{pieceId}`
     - `/api/v1/pieces/{pieceId}/operations/{operationId}`
3. **No Database Entity Leaks:** API paths must reflect domain aggregate roots rather than internal relational junction tables.

#### 1.2 HTTP Verbs and Status Codes
Every endpoint must adhere to standard HTTP semantics:

| HTTP Verb | Primary Usage | Success Status | Semantics & Body Requirements |
|:---|:---|:---:|:---|
| `GET` | Read single resource or collection | `200 OK` | Safe, idempotent. No request body. Returns data envelope. |
| `POST` | Create resource | `201 Created` | Non-idempotent without `Idempotency-Key`. Returns `Location` header and created entity. |
| `POST` | Execute domain action / state transition | `200 OK` | Non-safe command. Returns updated aggregate state in data envelope. |
| `PUT` | Full replacement / idempotent overwrite | `200 OK` | Idempotent. Replaces complete resource representation. |
| `PATCH` | Partial resource update | `200 OK` | Non-idempotent. Applies field-level modifications. |
| `DELETE` | Soft delete / cancellation (Admin only) | `204 No Content` | Idempotent. Empty response body. Restricted exclusively to `ADMIN`. |

#### 1.3 State-Changing Action Pattern on Aggregates
Domain entities undergo complex state machine transitions (piece progression, operation completion, QC failure, PM disposition). Raw `PATCH /pieces/{id} { status: '...' }` is strictly prohibited because state transitions require business invariants, audit reasons, and side effects.
* **Standard Pattern:** `POST /api/v1/{resource}/{id}/actions/{action-name}`
* **Examples:**
  - `POST /api/v1/pieces/{pieceId}/actions/release`
  - `POST /api/v1/piece-operations/{operationId}/actions/start`
  - `POST /api/v1/piece-operations/{operationId}/actions/submit`
  - `POST /api/v1/qc-inspections/{inspectionId}/actions/evaluate`
  - `POST /api/v1/qc-exceptions/{exceptionId}/actions/disposition`
  - `POST /api/v1/pieces/{pieceId}/actions/scrap`

#### 1.4 JSON Naming & Timestamp Serialization
1. **Field Naming:** All JSON request and response payloads must use strict `camelCase` keys:
   ```json
   {
     "serialNumber": "V-1024-001",
     "operationSequence": 10,
     "operatorId": "c4b1e5a2-3f8d-4e92-b2a1-9876543210ab"
   }
   ```
2. **Timestamps:** All date-time properties must be serialized as ISO-8601 strings in UTC ending with `Z`:
   * Format: `YYYY-MM-DDTHH:mm:ss.SSSZ` (e.g. `2026-11-26T04:30:00.000Z`).
   * Local time strings without timezone offsets are strictly forbidden.
3. **Dates:** Calendar dates without times must be formatted as `YYYY-MM-DD` (e.g. `2026-11-26`).

#### 1.5 Decimal & Measurement Serialization Standard
* **Decision:** All physical dimensional measurements, engineering tolerances, deviations, and weights (`NUMERIC` columns in PostgreSQL) must be serialized as **JSON Strings** (e.g. `"150.005"`, `"-0.020"`).
* **Technical Justification:**
  1. JavaScript and browser runtimes parse JSON numbers into IEEE-754 64-bit binary floating-point numbers (`double`). Binary floating-point representation cannot precisely represent all base-10 decimal fractions (e.g. `0.1 + 0.2 = 0.30000000000000004`).
  2. In Velan high-precision valve manufacturing, tolerances are specified to $0.001\text{ mm}$ (microns). A floating-point rounding artifact can turn an in-spec measurement into a false out-of-spec failure on the client or vice versa.
  3. String serialization maps seamlessly to Java `BigDecimal` (Jackson `JsonFormat.Shape.STRING` or `@JsonSerialize(using = ToStringSerializer.class)`), guaranteeing 100% precision across network boundaries.
  4. Integer counts, sequence numbers, pagination offsets, and HTTP status codes remain standard JSON numbers (`integer`).

#### 1.6 Standard Success Response Envelope (`ApiResponse<T>`)
All successful HTTP responses (except `204 No Content` and direct binary streams) must wrap payloads inside a standard response envelope:

```typescript
export interface ApiResponse<T> {
  data: T;
  meta: ResponseMeta;
}

export interface ResponseMeta {
  timestamp: string;          // ISO-8601 UTC
  correlationId: string;      // UUIDv4 from request header / MDC
  pagination?: PageMeta;      // Present only for paginated collection queries
}

export interface PageMeta {
  pageNumber: number;         // Zero-based page index (e.g. 0)
  pageSize: number;           // Page size (e.g. 20)
  totalElements: number;      // Total record count across all pages
  totalPages: number;         // Total available pages
  isFirst: boolean;
  isLast: boolean;
}
```

Example JSON Payload:
```json
{
  "data": {
    "id": "7b19a3b6-52c3-4f9e-8c6b-9c2e0a4f5d1e",
    "serialNumber": "V-1024-001",
    "status": "OPENED",
    "componentCode": "BODY",
    "currentOperation": "TURNING_OP_1"
  },
  "meta": {
    "timestamp": "2026-11-26T04:30:00.000Z",
    "correlationId": "c4b1e5a2-3f8d-4e92-b2a1-9876543210ab"
  }
}
```

#### 1.7 Required Permission Code per Endpoint
Every endpoint in the application must declare a mandatory permission code in the format `MODULE_ACTION` in `UPPER_SNAKE_CASE` enforced via Spring Security `@PreAuthorize("hasAuthority('MODULE_ACTION')")`.
* The documented permission code must be published in OpenAPI documentation so frontend route guards and action buttons can conditionally render.

---

### 2. Proposed Design

#### 2.1 API Versioning and Deprecation Policy
1. **URI Path Versioning:** Versioning is explicit in the URI path (`/api/v1/`).
2. **Backward-Compatible Changes (No Version Bump):**
   * Adding new endpoints.
   * Adding optional request parameters or headers.
   * Adding new fields to response payloads (clients must be lenient readers).
3. **Breaking Changes (Requires Version Bump to `/api/v2/`):**
   * Removing or renaming endpoints, fields, or query parameters.
   * Changing field data types (e.g. number to string or vice versa).
   * Adding mandatory request body fields.
   * Altering state machine transition semantics.
4. **Deprecation Protocol:**
   * Deprecated endpoints return RFC 8594 / RFC 9110 standard HTTP headers:
     - `Deprecation: @1735689600` (Unix epoch timestamp of deprecation).
     - `Sunset: Sat, 30 Jun 2027 00:00:00 GMT` (Date of planned decommission).
     - `Link: </api/v2/pieces>; rel="successor-version"`
   * A minimum 6-month deprecation window is enforced before endpoint removal.

---

### 3. Technical Assumptions
1. **ASSUMPTION:** Browser clients use standard JSON parsing with helper parsing functions for `BigDecimal` string values when executing arithmetic calculations.
2. **ASSUMPTION:** Spring Boot 3.4 `@RestControllerAdvice` and Jackson `ObjectMapper` are configured globally to serialize Java `Instant` as ISO-8601 UTC strings (`SerializationFeature.WRITE_DATES_AS_TIMESTAMPS = false`).
3. **ASSUMPTION:** The correlation ID is propagated via the `X-Correlation-Id` HTTP header; if omitted by the client, the backend gateway generates a new UUIDv4.

---

### 4. Open Questions
* **C-1 (Completion Workflow Model):** Endpoints related to assembled set packing records and dispatch bay management remain conditioned on the resolution of Conflict C-1.
* **C-2 (Piece Count Derivation Source):** Generation of piece records upon contract import remains conditioned on Conflict C-2.
* **C-3 (The "SC" Terminology Problem):** Endpoints currently referenced as `/api/v1/sales-contracts` vs `/api/v1/subcontracts` remain subject to final terminology approval.
