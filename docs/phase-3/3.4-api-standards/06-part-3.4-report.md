# Part 3.4 — Synthesis Report, API Governance, and Conformance Findings
## Document 06: Part 3.4 Synthesis Report, Conformance Audit, and Open Questions

---

### 1. Executive Summary & Deliverables Completed

Part 3.4 establishes the definitive REST API conventions, error envelope architectures, serialization rules, pagination standards, idempotency controls, file transfer protocols, real-time SSE mechanisms, and endpoint master inventory for the Velan Production platform.

The following six specification documents have been authored under `docs/phase-3/3.4-api-standards/`:
1. **[01-rest-conventions-and-envelope.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.4-api-standards/01-rest-conventions-and-envelope.md):** Base path `/api/v1`; resource and action naming conventions; HTTP verb and status code mapping; `camelCase` JSON; ISO-8601 UTC timestamps; explicit decision and technical justification for serializing dimensional measurements and weights as JSON Strings to eliminate IEEE-754 binary floating-point roundoff errors; standard success envelope `ApiResponse<T> { data, meta }`; API versioning and RFC 8594 deprecation policy; canonical aggregate action pattern (`POST /{resource}/{id}/actions/{action-name}`); mandatory permission code requirement per endpoint.
2. **[02-error-handling-and-error-catalogue.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.4-api-standards/02-error-handling-and-error-catalogue.md):** Standardized error payload `{ code, message, details[], timestamp, path, correlationId }`; Jakarta Bean Validation error mapping into `details[]`; comprehensive HTTP exception mapping; error code format `MODULE_REASON`; starter catalogue of **68 unique domain error codes** across all 9 application modules with HTTP status and triggering conditions.
3. **[03-pagination-filtering-idempotency-and-bulk.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.4-api-standards/03-pagination-filtering-idempotency-and-bulk.md):** Zero-based pagination (`page=0`, `size=20`, max 100); multi-column sorting and filtering syntax; search query conventions; `Idempotency-Key` UUIDv4 specification with 24-hour cache TTL and replay semantics; JPA optimistic concurrency control utilizing explicit `version` integer in DTO payloads with `409 Conflict` resolution; bulk shop-floor measurement submission rules (strictly atomic) and Excel import rules (configurable partial failure).
4. **[04-files-sse-and-openapi.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.4-api-standards/04-files-sse-and-openapi.md):** Direct-to-Cloudflare R2 pre-signed URL upload and download lifecycle with 15-minute expiration (Phase 2 ADR-05); storage key deterministic partitioning; Real-Time Server-Sent Events (SSE) conventions over `/api/v1/notifications/stream` with single-use ticket authentication, structured event envelopes, 20-second heartbeat, `Last-Event-ID` reconnection ring-buffer, and per-user connection limits (ADR-06); OpenAPI 3.1 generation via `springdoc-openapi`; compile-time TypeScript type generation via `openapi-typescript`; CI breaking change detection tool governance (`oasdiff`, `openapi-diff`, `optic`).
5. **[05-endpoint-inventory.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.4-api-standards/05-endpoint-inventory.md):** Master inventory cataloguing 49 primary API endpoints across all modules, detailing HTTP method, URI path, business purpose, mandatory permission code (`MODULE_ACTION`), request DTO name, response DTO name, and explicit tagging of open item dependencies.
6. **[06-part-3.4-report.md](file:///c:/Users/Admin/OneDrive/Desktop/velan%20production/velan-production/docs/phase-3/3.4-api-standards/06-part-3.4-report.md):** (This document) Comprehensive synthesis report, conflict register, technical assumptions audit, and open questions.

---

### 2. Comprehensive Conflict Register

In accordance with `SKILLS.md §0`, all known architectural conflicts remain formally OPEN without unilateral engineering decisions:

#### Conflict C-1: Completion Model — D-10 vs. Phase 2 Parts 2.2 and 2.6 (OPEN)
* **Conflict Statement:** Decision `D-10` specifies that a piece is Packed when all operations pass; a product is complete when all its pieces are Packed; the SC becomes Delivered automatically without packing or dispatch screens/statuses/roles. Conversely, Phase 2 Parts 2.2 and 2.6 specify multi-component assembled sets (`assembled_sets`), physical packing checklists (`packing_records`), staging bays (`DISPATCH_PENDING`), and loading dock gate releases (`dispatch_confirmations`).
* **API Standards Status:** **OPEN.** Endpoints for assembled sets (`GET /api/v1/assembled-sets`), packing checklists (`POST /api/v1/packing-records`), and gate releases (`POST /api/v1/dispatch-confirmations`) are catalogued in Document 05 but flagged with `Open (C-1)`.

#### Conflict C-2: Piece Count Derivation Source (OPEN)
* **Conflict Statement:** Decision `D-02` specifies that piece count per product is derived directly from the quantity RM sends for that product line. Phase 2 Part 2.2 calculates piece demand as target assembled sets $\times$ component BOM ratio.
* **API Standards Status:** **OPEN.** Endpoints `POST /api/v1/sales-contracts/import` and `/api/v1/sales-contracts/{id}/actions/release` are catalogued to accept either input structure.

#### Conflict C-3: The "SC" Terminology Problem (OPEN with Recommendation)
* **Conflict Statement:** "SC" carries four different meanings across systems: RM screen "Sales Component (SC Code)", Phase 1/2 prose "Sales Contract", Phase 2 tables `subcontracts`, and ECR field "Subcontract order number".
* **Recommendation:** Adopt neutral technical API paths `/api/v1/sales-contracts` (or `/api/v1/sc-headers`), avoiding the term "subcontract" unless the business explicitly confirms it represents outsourced vendor fabrication.
* **Status:** **OPEN.** Submitted for project owner direction before Phase 5 API implementation.

---

### 3. Consolidated Technical Assumptions Register

1. **ASSUMPTION:** The maximum single file size limit for Cloudflare R2 uploads is **50 MB**; larger CAD packages must be compressed before upload.
2. **ASSUMPTION:** Dimensional measurements and tolerances are serialized as JSON strings (e.g. `"150.005"`) to preserve exact decimal precision against JavaScript IEEE-754 floating-point rounding errors.
3. **ASSUMPTION:** Spring Boot Jackson `ObjectMapper` is configured globally with `SerializationFeature.WRITE_DATES_AS_TIMESTAMPS = false`, serializing all `Instant` instances as ISO-8601 UTC strings ending with `Z`.
4. **ASSUMPTION:** Correlation IDs are passed via HTTP header `X-Correlation-Id` or generated by a servlet filter and placed in SLF4J MDC, ensuring every error and success response includes a traceable `correlationId`.
5. **ASSUMPTION:** Ephemeral SSE connection tickets expire after 30 seconds and are single-use, avoiding the security risk of passing long-lived JWTs in URL query strings.
6. **ASSUMPTION:** CI breaking change checks will be performed using `oasdiff` or `optic` against baseline OpenAPI schemas.

---

### 4. Errors & Inconsistencies Found in Previous Work

The following API path and schema discrepancies were identified in earlier Phase 2 documentation and reconciled in Part 3.4:

1. **Deeply Nested Operation URIs in Phase 2 Sequences:**
   * *Citation:* `docs/phase-2/2.1-system-architecture/04-request-flow-and-error-handling.md:24,36`
   * *Issue:* Modeled operation actions as `POST /api/v1/pieces/{serial}/operations/{opId}/start`.
   * *Correction:* Reconciled to the canonical aggregate action pattern `POST /api/v1/piece-operations/{operationId}/actions/start`. This decouples the operation command from the piece serial string and avoids 3-level URI nesting.
2. **Non-Standard Module Prefix in QC URI:**
   * *Citation:* `docs/phase-2/2.1-system-architecture/04-request-flow-and-error-handling.md:68`
   * *Issue:* Modeled inspection query as `GET /api/v1/qc/inspections/{opId}/parameters`.
   * *Correction:* Reconciled to canonical REST plural resource `GET /api/v1/qc-inspections/{inspectionId}`.
3. **Error Payload Property Discrepancy:**
   * *Citation:* `docs/phase-2/2.1-system-architecture/04-request-flow-and-error-handling.md:220-239`
   * *Issue:* RFC 7807 example included legacy property names `errorCode` and `invalidParameters`.
   * *Correction:* Standardized across Part 3.4 to `{ code, message, details: [...], timestamp, path, correlationId }`.
4. **Singular Exception Resource URI:**
   * *Citation:* `docs/phase-2/2.1-system-architecture/04-request-flow-and-error-handling.md:117`
   * *Issue:* Modeled PM disposition as `POST /api/v1/exceptions/{id}/disposition`.
   * *Correction:* Reconciled to canonical plural kebab-case resource and action pattern `POST /api/v1/qc-exceptions/{id}/actions/disposition`.

---

### 5. Open Questions Submitted for the Project Owner

* **Conflict C-1 (Completion Workflow):** Does the owner confirm the automatic cascade of Decision D-10 or the multi-component assembled set, packing checklist, and loading dock gate release workflow defined in Phase 2.6?
* **Conflict C-2 (Piece Count Derivation):** Does the owner confirm that piece counts are derived purely from RM product quantities (D-02) or calculated via target sets $\times$ component BOM ratio?
* **Conflict C-3 (The "SC" Terminology Problem):** Does the owner approve adopting `/api/v1/sales-contracts` as the standard API resource path rather than `/api/v1/subcontracts`?
* **O-API-01 (SSE Ticket vs. Fetch-Event-Source):** Should the React frontend use native browser `EventSource` with the 30-second single-use ticket pattern (`/api/v1/auth/sse-ticket`), or use `@microsoft/fetch-event-source` which supports standard `Authorization: Bearer <jwt>` HTTP headers?
* **O-API-02 (Measurement Serialization Validation):** Does the frontend engineering lead confirm accepting dimensional measurements as JSON Strings (e.g. `"150.005"`) to prevent IEEE-754 rounding discrepancies?
