# Part 3.4 — API Standards
## Document 05: Master Endpoint Inventory and Permission Matrix

---

### 1. Confirmed Rules & Inventory Scope
1. **Module Architecture:** Endpoints are catalogued across core modules: `AUTH`, `ORDER`, `PLANNING`, `EXECUTION`, `QUALITY`, `EXCEPTION`, `DOCUMENT`, `NOTIFICATION`, `INTEGRATION`, `ADMIN`, and `DISPATCH`.
2. **Permission Codes:** Every endpoint specifies an exact permission code (`MODULE_ACTION` in `UPPER_SNAKE_CASE`) enforced via Spring Security `@PreAuthorize`.
3. **Open Item Dependencies:** Endpoints dependent on unresolved architectural conflicts (specifically C-1, C-2, C-3) are explicitly flagged in the table.

---

### 2. Master Endpoint Inventory Table

| Module | Method | URI Path | Purpose & Action | Permission Code | Request DTO | Response DTO | Open Item Dependency |
|:---|:---:|:---|:---|:---|:---|:---|:---:|
| **AUTH** | `POST` | `/api/v1/auth/login` | Authenticate user credentials & issue JWT tokens | `PUBLIC` | `LoginRequestDTO` | `AuthTokenResponseDTO` | None |
| **AUTH** | `POST` | `/api/v1/auth/refresh` | Refresh expired access token using refresh token | `PUBLIC` | `RefreshTokenRequestDTO` | `AuthTokenResponseDTO` | None |
| **AUTH** | `POST` | `/api/v1/auth/sse-ticket` | Generate single-use ticket for SSE connection | `AUTHENTICATED` | None | `SseTicketResponseDTO` | None |
| **AUTH** | `GET` | `/api/v1/auth/me` | Fetch active user identity, roles, and permissions | `AUTHENTICATED` | None | `UserProfileResponseDTO` | None |
| **ORDER** | `GET` | `/api/v1/sales-contracts` | List contract orders with pagination & filters | `ORDER_SC_VIEW` | None | `PageResponse<SalesContractSummaryDTO>` | Open (C-3) |
| **ORDER** | `GET` | `/api/v1/sales-contracts/{id}` | Get full contract order details and product line items | `ORDER_SC_VIEW` | None | `SalesContractDetailDTO` | Open (C-3) |
| **ORDER** | `POST` | `/api/v1/sales-contracts` | Create new sales contract manually | `ORDER_SC_CREATE` | `CreateSalesContractRequestDTO` | `SalesContractDetailDTO` | Open (C-3) |
| **ORDER** | `POST` | `/api/v1/sales-contracts/import` | Bulk import contract orders from Excel/CSV file | `ORDER_SC_IMPORT` | `MultipartFile` | `ImportSummaryResponseDTO` | Open (C-2, C-3) |
| **ORDER** | `POST` | `/api/v1/sales-contracts/{id}/actions/release` | Release order products to shop floor | `ORDER_SC_RELEASE` | `ReleaseOrderRequestDTO` | `SalesContractDetailDTO` | Open (C-2, C-3) |
| **PLANNING** | `GET` | `/api/v1/process-templates` | List process routing templates | `PLANNING_TEMPLATE_VIEW` | None | `PageResponse<ProcessTemplateSummaryDTO>` | None |
| **PLANNING** | `GET` | `/api/v1/process-templates/{id}` | Get routing template with operations & parameters | `PLANNING_TEMPLATE_VIEW` | None | `ProcessTemplateDetailDTO` | None |
| **PLANNING** | `POST` | `/api/v1/process-templates` | Create new process routing template version | `PLANNING_TEMPLATE_CREATE` | `CreateProcessTemplateRequestDTO` | `ProcessTemplateDetailDTO` | None |
| **PLANNING** | `PUT` | `/api/v1/process-templates/{id}` | Update draft process routing template | `PLANNING_TEMPLATE_EDIT` | `UpdateProcessTemplateRequestDTO` | `ProcessTemplateDetailDTO` | None |
| **PLANNING** | `POST` | `/api/v1/process-templates/{id}/actions/approve` | PM approve and lock process routing template version | `PLANNING_TEMPLATE_APPROVE` | `ApproveTemplateRequestDTO` | `ProcessTemplateDetailDTO` | None |
| **PLANNING** | `GET` | `/api/v1/work-stations` | List active shop-floor work stations | `PLANNING_STATION_VIEW` | None | `List<WorkStationDTO>` | None |
| **EXECUTION** | `GET` | `/api/v1/pieces` | Query pieces with search, status, and heat filters | `EXECUTION_PIECE_VIEW` | None | `PageResponse<PieceSummaryDTO>` | None |
| **EXECUTION** | `GET` | `/api/v1/pieces/{id}` | Get piece execution history and current op state | `EXECUTION_PIECE_VIEW` | None | `PieceDetailDTO` | None |
| **EXECUTION** | `POST` | `/api/v1/pieces/barcode-scan` | Lookup piece details by scanned serial barcode | `EXECUTION_PIECE_VIEW` | `BarcodeScanRequestDTO` | `PieceDetailDTO` | None |
| **EXECUTION** | `GET` | `/api/v1/piece-operations/station-worklist` | Fetch active operations queue for operator station | `EXECUTION_OPERATION_VIEW` | None | `List<StationWorklistItemDTO>` | None |
| **EXECUTION** | `POST` | `/api/v1/piece-operations/{id}/actions/start` | Operator start operation (transitions READY $\to$ OPENED) | `EXECUTION_OPERATION_START` | `StartOperationRequestDTO` | `OperationExecutionDTO` | None |
| **EXECUTION** | `POST` | `/api/v1/piece-operations/{id}/actions/submit` | Operator submit operation with recorded measurements | `EXECUTION_MEASUREMENT_SUBMIT`| `SubmitMeasurementsRequestDTO` | `OperationExecutionDTO` | None |
| **EXECUTION** | `POST` | `/api/v1/pieces/{id}/actions/release` | Release piece to production (validates RM issue) | `EXECUTION_PIECE_RELEASE` | None | `PieceDetailDTO` | None |
| **QUALITY** | `GET` | `/api/v1/qc-inspections/pending` | QC queue of submitted operations awaiting inspection | `QUALITY_INSPECTION_VIEW` | None | `PageResponse<QCPendingItemDTO>` | None |
| **QUALITY** | `GET` | `/api/v1/qc-inspections/{id}` | Get inspection parameters, nominals, and tolerances | `QUALITY_INSPECTION_VIEW` | None | `QCInspectionDetailDTO` | None |
| **QUALITY** | `POST` | `/api/v1/qc-inspections/{id}/actions/evaluate` | Record QC measurements and evaluate verdict | `QUALITY_INSPECTION_EVALUATE`| `EvaluateQCMeasurementsRequestDTO`| `QCInspectionVerdictDTO` | None |
| **QUALITY** | `GET` | `/api/v1/tolerances/{productId}` | List product dimensional tolerances and formulas | `QUALITY_TOLERANCE_VIEW` | None | `List<ToleranceParameterDTO>` | None |
| **QUALITY** | `POST` | `/api/v1/tolerances/import` | Bulk import product tolerances from Excel | `QUALITY_TOLERANCE_IMPORT` | `MultipartFile` | `ImportSummaryResponseDTO` | None |
| **EXCEPTION** | `GET` | `/api/v1/qc-exceptions` | List QC exceptions awaiting PM disposition | `EXCEPTION_VIEW` | None | `PageResponse<QCExceptionSummaryDTO>` | None |
| **EXCEPTION** | `GET` | `/api/v1/qc-exceptions/{id}` | Get exception detail, out-of-spec dimensions, notes | `EXCEPTION_VIEW` | None | `QCExceptionDetailDTO` | None |
| **EXCEPTION** | `POST` | `/api/v1/qc-exceptions/{id}/actions/disposition` | PM apply disposition (Continue, Extra, Return, Scrap) | `EXCEPTION_DISPOSITION_APPLY` | `ApplyDispositionRequestDTO` | `QCExceptionDetailDTO` | None |
| **EXCEPTION** | `GET` | `/api/v1/piece-replacements/{pieceId}` | View replacement piece lineage and parent scrap reason | `EXCEPTION_VIEW` | None | `ReplacementLineageDTO` | None |
| **DOCUMENT** | `POST` | `/api/v1/documents/presigned-upload` | Request pre-signed PUT URL for direct R2 upload | `DOCUMENT_UPLOAD` | `PresignedUploadRequestDTO` | `PresignedUploadResponseDTO` | None |
| **DOCUMENT** | `POST` | `/api/v1/documents/confirm-upload` | Confirm R2 direct upload & persist document record | `DOCUMENT_CONFIRM` | `ConfirmUploadRequestDTO` | `DocumentMetadataDTO` | None |
| **DOCUMENT** | `GET` | `/api/v1/documents/{id}/presigned-download-url` | Generate pre-signed GET URL for viewing drawing in R2 | `DOCUMENT_VIEW` | None | `PresignedDownloadResponseDTO`| None |
| **DOCUMENT** | `GET` | `/api/v1/documents` | List drawing revisions by product/component code | `DOCUMENT_VIEW` | None | `List<DocumentMetadataDTO>` | None |
| **NOTIFICATION**| `GET` | `/api/v1/notifications/stream` | Persistent Server-Sent Events notification stream | `NOTIFICATION_STREAM` | None | `Text/Event-Stream` | None |
| **NOTIFICATION**| `GET` | `/api/v1/notifications/recent` | Fetch recent notifications history | `NOTIFICATION_VIEW` | None | `List<NotificationItemDTO>` | None |
| **INTEGRATION**| `GET` | `/api/v1/integrations/rm/material-status` | Query raw material issuance status from RM (V-SMART) | `INTEGRATION_RM_QUERY` | None | `RMMaterialStatusResponseDTO` | None |
| **INTEGRATION**| `POST` | `/api/v1/integrations/rm/sync-status` | Force refresh cached material issuance status | `INTEGRATION_RM_SYNC` | `RMSyncRequestDTO` | `RMSyncResponseDTO` | None |
| **INTEGRATION**| `GET` | `/api/v1/integrations/ecr/outbox-status` | Monitor transactional outbox dispatch events | `INTEGRATION_OUTBOX_VIEW` | None | `PageResponse<OutboxEventDTO>` | None |
| **INTEGRATION**| `POST` | `/api/v1/integrations/ecr/outbox/{id}/actions/retry`| Retry failed or dead-letter outbox event | `INTEGRATION_OUTBOX_RETRY`| None | `OutboxEventDTO` | None |
| **ADMIN** | `GET` | `/api/v1/admin/users` | List user accounts with role filtering | `ADMIN_USER_MANAGE` | None | `PageResponse<UserSummaryDTO>` | None |
| **ADMIN** | `POST` | `/api/v1/admin/users` | Create user account and assign roles | `ADMIN_USER_MANAGE` | `CreateUserRequestDTO` | `UserDetailDTO` | None |
| **ADMIN** | `PUT` | `/api/v1/admin/users/{id}` | Update user details, roles, or active status | `ADMIN_USER_MANAGE` | `UpdateUserRequestDTO` | `UserDetailDTO` | None |
| **ADMIN** | `GET` | `/api/v1/admin/audit-logs` | Query immutable audit log trail | `ADMIN_AUDIT_VIEW` | None | `PageResponse<AuditLogEntryDTO>` | None |
| **ADMIN** | `GET` | `/api/v1/admin/system/health` | Subsystem health status (DB, R2, RM connector) | `ADMIN_SYSTEM_VIEW` | None | `SystemHealthDTO` | None |
| **DISPATCH** | `GET` | `/api/v1/assembled-sets` | Query assembled sets and component readiness | `DISPATCH_SET_VIEW` | None | `PageResponse<AssembledSetSummaryDTO>` | Open (C-1) |
| **DISPATCH** | `POST` | `/api/v1/packing-records` | Record physical packing checklist | `DISPATCH_PACK_CREATE` | `CreatePackingRecordRequestDTO` | `PackingRecordDTO` | Open (C-1) |
| **DISPATCH** | `POST` | `/api/v1/dispatch-confirmations` | Confirm gate release at loading dock | `DISPATCH_GATE_RELEASE` | `CreateDispatchConfirmationDTO`| `DispatchConfirmationDTO` | Open (C-1) |

---

### 3. Proposed Design

#### 3.1 DTO Package & Class Structure
In accordance with Part 3.2 modular monolith packaging, all request and response DTOs are declared in the `dto` package of their respective domain module:
* `com.velan.production.execution.dto.request.SubmitMeasurementsRequestDTO`
* `com.velan.production.execution.dto.response.OperationExecutionDTO`
* `com.velan.production.quality.dto.request.EvaluateQCMeasurementsRequestDTO`
* `com.velan.production.quality.dto.response.QCInspectionVerdictDTO`

---

### 4. Technical Assumptions
1. **ASSUMPTION:** Spring Security filters enforce `@PreAuthorize` method security before business services are invoked.
2. **ASSUMPTION:** Public authentication endpoints (`/api/v1/auth/login`, `/api/v1/auth/refresh`) are whitelisted in `SecurityFilterChain` permit-all configuration.

---

### 5. Open Questions
* **C-1 (Completion Workflow Model):** Endpoints `/api/v1/assembled-sets`, `/api/v1/packing-records`, and `/api/v1/dispatch-confirmations` are catalogued but remain strictly conditioned on the project owner's resolution of Conflict C-1.
* **C-2 (Piece Count Derivation Source):** Endpoint `/api/v1/sales-contracts/import` logic remains subject to piece derivation rules in Conflict C-2.
* **C-3 (The "SC" Terminology Problem):** Endpoints prefixed with `/api/v1/sales-contracts` remain subject to final nomenclature direction.
