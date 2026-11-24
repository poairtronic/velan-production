# Part 2.1 – System Architecture Design
## Document 04: Request Flows and Error-Handling Architecture

### 1. End-to-End Request and Response Flows

To illustrate how data and commands travel through the system across client, backend modules, database, and external systems, the four most critical production flows are detailed below.

---

#### 1.1 Flow 1: Operator Opens and Submits an Operation with Measurements

```mermaid
sequenceDiagram
    autonumber
    actor Op as Shop-Floor Operator
    participant UI as React UI (Station View)
    participant SEC as Spring Security Filter
    participant CTRL as Execution Controller
    participant SVC as Execution Service
    participant DB as Neon PostgreSQL
    participant BUS as Spring EventPublisher

    Op->>UI: Scans piece barcode / enters serial number
    UI->>SEC: POST /api/v1/pieces/{serial}/operations/{opId}/start
    SEC->>SEC: Validate JWT & OPERATOR role
    SEC->>CTRL: Forward request
    CTRL->>SVC: startOperation(serial, opId, operatorId, stationId)
    SVC->>DB: SELECT piece, operation (FOR UPDATE optimistic lock)
    SVC->>SVC: Assert current state == READY
    SVC->>DB: UPDATE piece_operations SET status='OPENED', started_at=NOW()
    SVC-->>CTRL: Return OperationDTO (status: OPENED)
    CTRL-->>UI: 200 OK (Screen switches to active recording mode)

    Op->>UI: Enters operator measurements (e.g., Flange Outer Diameter: 150.2mm)
    Op->>UI: Clicks "Submit Operation"
    UI->>SEC: POST /api/v1/pieces/{serial}/operations/{opId}/submit<br/>{measurements: [{paramId, value}]}
    SEC->>CTRL: Forward request
    CTRL->>SVC: submitOperation(serial, opId, measurements)
    SVC->>DB: INSERT INTO operation_measurements (recorded_by='OPERATOR', ...)
    SVC->>DB: UPDATE piece_operations SET status='SUBMITTED', submitted_at=NOW()
    SVC->>BUS: publishEvent(new OperationSubmittedForQCEvent(pieceId, opId))
    SVC-->>CTRL: Return OperationDTO (status: QC_PENDING)
    CTRL-->>UI: 200 OK (Piece routed to QC queue)
```

**Plain-Language Explanation:**
1. The operator identifies a piece at their station and opens the operation.
2. The system locks the row optimistically, verifies the operation is in a `READY` state, and updates the status to `OPENED`.
3. The operator performs machining, enters their dimensional measurements, and submits the job.
4. The system records the operator measurements, updates the status to `SUBMITTED`, flags it as `QC_PENDING`, and broadcasts an internal domain event notifying QC inspectors.

---

#### 1.2 Flow 2: QC Inspection, Automated Tolerance Validation, and Failure Branching

```mermaid
sequenceDiagram
    autonumber
    actor QC as Quality Inspector
    participant UI as React UI (QC View)
    participant QCSVC as Quality Service
    participant TOL as Tolerance Engine
    participant DB as Neon PostgreSQL
    participant BUS as Spring EventPublisher
    participant SSE as SSE Notification Service

    QC->>UI: Selects piece from QC Pending list
    UI->>QCSVC: GET /api/v1/qc/inspections/{opId}/parameters
    QCSVC->>DB: Fetch nominal dimensions, static & dynamic tolerance rules
    QCSVC-->>UI: Return parameters with acceptable tolerance limits

    QC->>UI: Enters QC measurements & gauge IDs, clicks "Evaluate"
    UI->>QCSVC: POST /api/v1/qc/inspections/{opId}/evaluate<br/>{measurements: [{paramId, qcValue, instrumentId}]}
    QCSVC->>TOL: validateMeasurements(parameters, qcValues)
    
    alt In-Spec (All dimensions within tolerance)
        TOL-->>QCSVC: ValidationResult(PASSED)
        QCSVC->>DB: INSERT INTO qc_inspections (status='PASSED')
        QCSVC->>DB: UPDATE piece_operations SET status='PASSED'
        QCSVC->>BUS: publishEvent(new OperationQCPassedEvent(pieceId, opId))
        QCSVC-->>UI: 200 OK (Verdict: PASSED, next operation unlocked)
    else Out-of-Spec (At least one dimension exceeds tolerance)
        TOL-->>QCSVC: ValidationResult(FAILED, [Param: OD, Value: 152.8mm, Max: 151.0mm])
        QCSVC->>DB: INSERT INTO qc_inspections (status='FAILED', reasons, notes)
        QCSVC->>DB: UPDATE piece_operations SET status='FAILED'
        QCSVC->>DB: UPDATE pieces SET status='ON_HOLD_QC_FAILED'
        QCSVC->>DB: INSERT INTO qc_exceptions (status='PENDING_PM_DISPOSITION')
        QCSVC->>BUS: publishEvent(new PieceFailedQCEvent(pieceId, opId, exceptionId))
        BUS->>SSE: Broadcast alert to PM Cockpit
        QCSVC-->>UI: 200 OK (Verdict: FAILED, piece placed on hold, sent to PM)
    end
```

**Plain-Language Explanation:**
1. The QC inspector opens the piece inspection screen, which pulls the required specifications and dynamic tolerance formulas.
2. The inspector measures the physical part and enters the numbers.
3. The backend **Tolerance Engine** tests every value against nominal, upper, and lower limits (plus any dynamic formula based on mating components).
4. If everything passes, the operation is stamped `PASSED`, and the piece advances to the next step.
5. If any parameter is out of spec, the operation is stamped `FAILED`, the entire piece is placed `ON_HOLD`, an exception record is created, and an instant alert is pushed to the PM Cockpit via Server-Sent Events.

---

#### 1.3 Flow 3: PM Disposition "Reject & Scrap" and Asynchronous ECR Outbox

```mermaid
sequenceDiagram
    autonumber
    actor PM as Project Manager
    participant UI as React UI (PM Cockpit)
    participant EXSVC as Exception Service
    participant REPSVC as Replacement Service
    participant DB as Neon PostgreSQL
    participant SCHED as Scheduled Outbox Worker
    participant ECR as ECR System (External API)

    PM->>UI: Selects exception, chooses disposition: "REJECT_AND_SCRAP"
    UI->>EXSVC: POST /api/v1/exceptions/{id}/disposition<br/>{decision: 'REJECT_AND_SCRAP', reason: 'Unrecoverable bore oversize'}
    
    critical Atomic Database Transaction (Local ACID)
        EXSVC->>DB: UPDATE qc_exceptions SET disposition='REJECT_AND_SCRAP', status='CLOSED'
        EXSVC->>DB: UPDATE pieces SET status='SCRAPPED', scrapped_at=NOW()
        EXSVC->>REPSVC: createReplacementPiece(scrappedPieceId)
        REPSVC->>DB: INSERT INTO pieces (new_serial, status='CREATED', replaced_piece_id=scrappedId)
        REPSVC->>DB: INSERT INTO piece_replacement_lineage (parent_id, child_id)
        EXSVC->>DB: INSERT INTO outbox_events (aggregate_type='SCRAP_EXCEPTION', payload=JSON, status='PENDING')
    end
    
    EXSVC-->>UI: 200 OK (Scrapped confirmed, replacement piece #V-1024-R1 created)

    Note over SCHED,ECR: Background Asynchronous Processing (Decoupled from User Request)
    loop Every 5 seconds
        SCHED->>DB: SELECT * FROM outbox_events WHERE status='PENDING' ORDER BY created_at LIMIT 20 FOR UPDATE SKIP LOCKED
        SCHED->>ECR: POST /api/external/ecr/exceptions<br/>(Header: Idempotency-Key: {outbox_uuid}, Body: payload)
        alt ECR Returns 200/201 Success
            SCHED->>DB: UPDATE outbox_events SET status='PUBLISHED', published_at=NOW()
        else ECR Offline or Returns 5xx Error
            SCHED->>DB: UPDATE outbox_events SET status='RETRY', retry_count=retry_count+1, next_retry_at=NOW()+interval
        end
    end
```

**Plain-Language Explanation:**
1. The PM decides that an out-of-spec piece cannot be salvaged and must be scrapped.
2. In a single, atomic database transaction:
   - The exception is closed.
   - The original physical piece is marked `SCRAPPED`.
   - A new replacement piece is instantiated with full parent-child lineage traceability.
   - An event is inserted into the local `outbox_events` table.
3. The PM immediately gets a success response without waiting for any external network call.
4. In the background, a dedicated scheduler reads the outbox table, calls ECR's intake API with an idempotency key, and records the delivery. If ECR is down, the system automatically retries with exponential backoff.

---

#### 1.4 Flow 4: RM Material Issue Readiness Gate

```mermaid
sequenceDiagram
    autonumber
    actor PM as Project Manager / Operator
    participant UI as React UI
    participant SVC as Execution Service
    participant RM_CONN as RM Read-Only Connector
    participant CACHE as Caffeine Cache (JVM)
    participant RM as RM System (V-SMART)

    PM->>UI: Clicks "Release Piece to Production"
    UI->>SVC: POST /api/v1/pieces/{serial}/release
    SVC->>RM_CONN: isMaterialIssued(scNumber, componentCode)
    
    RM_CONN->>CACHE: getIfPresent("rm-material:" + scNumber + ":" + compCode)
    alt Cache Hit
        CACHE-->>RM_CONN: MaterialStatus(ISSUED, HeatNo: 'H-9821')
    else Cache Miss
        RM_CONN->>RM: GET /api/v1/materials/issue-status?sc={scNumber}&comp={compCode}<br/>(Authorization: Bearer [ServiceAccountJWT])
        RM-->>RM_CONN: 200 OK {status: 'ISSUED', heatCode: 'H-9821', issueDate: '2026-11-20'}
        RM_CONN->>CACHE: put(key, status, TTL: 5 min)
    end

    alt Material IS Issued in RM
        SVC->>SVC: Assert RM material status == 'ISSUED'
        SVC->>SVC: Update piece status to 'READY_FOR_PRODUCTION'
        SVC-->>UI: 200 OK (Piece ready for machining, heat code attached)
    else Material NOT Issued in RM
        SVC-->>UI: 412 Precondition Failed<br/>("Material not issued in RM V-SMART. Physical stock release required.")
    end
```

**Plain-Language Explanation:**
1. Before any piece can physically be cut or machined, the system checks whether raw material has been issued in RM (V-SMART).
2. It queries an in-process Caffeine cache first. If missing, it queries RM using its read-only service account.
3. If RM confirms material is issued, the piece transitions to `READY_FOR_PRODUCTION`.
4. If RM reports material has not been issued, the transition is strictly blocked, preventing unauthorized phantom production on the shop floor.

---

### 2. Error-Handling Architecture

The application adopts a standardized error-handling strategy based on **RFC 7807 (Problem Details for HTTP APIs)**.

```mermaid
flowchart TD
    Req["Incoming HTTP Request"] --> Controller["Controller Layer"]
    Controller --> DomainLogic["Domain Module Logic"]
    DomainLogic -->|Throws Exception| ExDispatcher["@RestControllerAdvice Global Handler"]
    
    subgraph ExceptionTypes ["Domain Exception Taxonomy"]
        E1["EntityNotFoundException"] -->|Map to| H404["404 Not Found"]
        E2["InvalidStateTransitionException"] -->|Map to| H409["409 Conflict"]
        E3["ToleranceValidationException"] -->|Map to| H422["422 Unprocessable Entity"]
        E4["ReadinessGateViolationException"] -->|Map to| H412["412 Precondition Failed"]
        E5["AccessDeniedException"] -->|Map to| H403["403 Forbidden"]
        E6["IntegrationException"] -->|Map to| H502["502 Bad Gateway"]
    end

    ExDispatcher --> ProblemDetailsJSON["RFC 7807 JSON Response Payload"]
```

#### 2.1 RFC 7807 Standard Error Format
All error responses from the backend return a consistent, machine-readable JSON structure:
```json
{
  "type": "https://velan-production.com/errors/tolerance-validation-failed",
  "title": "Dimensional Tolerance Validation Failed",
  "status": 422,
  "detail": "Measured value 152.8mm exceeds maximum upper tolerance limit of 151.0mm for Parameter 'Outer Flange Diameter'.",
  "instance": "/api/v1/qc/inspections/op-842/evaluate",
  "errorCode": "QC_OUT_OF_SPEC",
  "timestamp": "2026-11-24T15:30:00Z",
  "invalidParameters": [
    {
      "parameterCode": "OD_FLANGE",
      "nominal": 150.0,
      "lowerLimit": 149.0,
      "upperLimit": 151.0,
      "actualValue": 152.8,
      "deviation": 1.8
    }
  ]
}
```

#### 2.2 Domain Exception Hierarchy
Every exception thrown in business logic extends from an unchecked base class `VelanBaseException`:
* **`EntityNotFoundException` (404):** Resource does not exist (e.g., unknown piece serial, missing BOM).
* **`InvalidStateTransitionException` (409):** Action violates state machine constraints (e.g., attempting to submit an operation that is already `PASSED` or `CANCELLED`).
* **`ToleranceValidationException` (422):** Measurement evaluation failure containing granular parameter breakdown.
* **`ReadinessGateViolationException` (412):** Prerequisites not satisfied (e.g., attempting production when RM material is unissued, or attempting dispatch when a BOM set is incomplete).
* **`AccessDeniedException` (403):** Role lacks permission (e.g., Operator attempting to issue a PM disposition).
* **`IntegrationException` (502/504):** External system communication failure (e.g., RM V-SMART API unavailable).

#### 2.3 Resilient HTTP Client for RM (V-SMART)
The RM read-only connector is protected against network instability:
* **Connect Timeout:** 3 seconds.
* **Read Timeout:** 5 seconds.
* **Retry Strategy:** Max 2 retries with exponential backoff for HTTP 5xx errors or connection timeouts.
* **Graceful Degradation:** If RM is completely unreachable during non-blocking informational queries, the system serves the last cached state with a `stale: true` warning flag. For hard gating decisions (material issuance check), it safely fails closed and prompts the user to verify RM connectivity.

#### 2.4 Outbox Dead-Letter Strategy for ECR
* **Retry Cadence:** 1 min, 5 min, 15 min, 30 min, 1 hour.
* **Dead-Letter State (`DEAD_LETTER`):** If an outbox event fails 5 consecutive times, it is marked as `DEAD_LETTER`.
* **Alerting:** An alert is raised in the Admin Console, allowing the system administrator or PM to inspect the payload, review the ECR response error, and trigger a manual re-dispatch once ECR is restored.
