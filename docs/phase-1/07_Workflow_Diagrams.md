# 07 — Workflow Diagrams

Diagrams use Mermaid flowchart syntax and can be rendered in Markdown viewers that support Mermaid.

## 1. Overall PO → SC → Production → Dispatch
```mermaid
flowchart TD
  A[PO and customer data] --> B[RM V-SMART creates/submits SC]
  B --> C[RM sends SC, product and BOM data]
  C --> D[Production validates and deduplicates]
  D --> E[Create production record, sets and components]
  E --> F[Assign process templates and drawings]
  F --> G[Design uploads tolerance sheet]
  G --> H[Extraction, Design verification and PM approval]
  H --> I[PM planning and operator priorities]
  I --> J[RM material issue status check]
  J --> K[Operation execution and operator measurements]
  K --> L[Independent QC measurements]
  L --> M{QC result}
  M -->|Pass| N[Next operation]
  M -->|Fail| O[PM disposition: rework, restart or authorized override]
  O --> K
  N --> P{All BOM components complete for this set?}
  P -->|No| K
  P -->|Yes| Q[Final QC and set completion]
  Q --> R[Packing component verification]
  R --> S[Dispatch Pending]
  S --> T[Dispatch employee confirms actual dispatch]
  T --> U[Delivered]
```

## 2. RM material issue readiness
```mermaid
flowchart TD
 A[RM Stores issues material in V-SMART] --> B[RM sends issued quantity, status, reference and timestamp]
 B --> C[Production validates and records source update]
 C --> D[Update required / issued / pending / shortage values]
 D --> E{Required material condition met?}
 E -->|No| F[Keep material-dependent operation blocked]
 E -->|Yes| G[Make eligible quantity available to operator]
```

## 3. Operator and QC operation loop
```mermaid
flowchart TD
 A[Operator opens assigned priority worklist] --> B[Select SC, set/component and operation]
 B --> C[Check material readiness and approved tolerance]
 C --> D[Enter actual operator measurements]
 D --> E[Submit operation quantity]
 E --> F[QC independently measures each piece]
 F --> G{QC within tolerance?}
 G -->|Yes| H[Record QC pass]
 H --> I[Move passed quantity to next operation]
 G -->|No| J[Block quantity and notify PM]
 J --> K[PM selects disposition]
 K -->|Rework| L[Temporary rework or earlier operation]
 K -->|Restart| M[Return to first applicable operation]
 K -->|Override approved| I
 L --> F
 M --> B
```

## 4. Tolerance revision
```mermaid
flowchart TD
 A[Design requests/uploads tolerance revision] --> B[System extracts and maps values]
 B --> C[Design verifies/corrects]
 C --> D[Submit revision to PM]
 D --> E{PM approves?}
 E -->|No| F[Keep current active revision; notify Design]
 E -->|Yes| G[Create immutable approved revision]
 G --> H[Notify PM and affected production users]
 H --> I[Apply to data not yet entered / eligible unfinished quantities]
 I --> J[Do not overwrite existing measurement values]
 J --> K[Re-evaluate affected unfinished quantities and flag newly failing cases]
```

## 5. Vendor operation and inward
```mermaid
flowchart TD
 A[PM/authorized user assigns vendor operation and quantity] --> B[Production records vendor tracking and expected return]
 B --> C[Production sends vendor-processing alert/reference to RM]
 C --> D[RM creates and dispatches Vendor DC]
 D --> E[Vendor performs operation]
 E --> F[RM records inward/returned quantity]
 F --> G[RM automatically notifies Production]
 G --> H[Production updates returned and pending quantities]
 H --> I[Production QC user enters independent QC measurements]
 I --> J{QC pass?}
 J -->|Yes| K[Release accepted returned quantity to next operation]
 J -->|No| L[Notify PM and follow QC disposition]
```

## 6. Exception communication
```mermaid
flowchart TD
 A[QC/authorized user records applicable exception] --> B[Production creates unique exception record]
 B --> C[Send relevant data to RM]
 B --> D[Send relevant data to ECR]
 C --> E[RM handles its own material/operational responsibility]
 D --> F[ECR performs responsibility and costing]
 E --> G[Production continues own workflow]
 F --> G
```

**Boundary:** ECR costing/responsibility results do not return to Production under the current confirmed scope.

## 7. Packing and dispatch
```mermaid
flowchart TD
 A[All required components complete] --> B[Final QC passes]
 B --> C[Set becomes eligible for packing]
 C --> D[Packing verifies every required BOM component]
 D --> E{Complete set verified?}
 E -->|No| F[Remain not ready; resolve missing/failed component]
 E -->|Yes| G[Confirm packing]
 G --> H[Dispatch Pending]
 H --> I[Dispatch employee confirms actual dispatch]
 I --> J[Record quantity, date/time and responsible user]
 J --> K[Delivered]
```

Partial dispatch means selected fully completed sets can be dispatched while other sets under the same SC remain in production. An incomplete set cannot be dispatched.

## 8. Integration failure and retry
```mermaid
flowchart TD
 A[Receive external event] --> B[Validate source ID and payload]
 B --> C{Already processed?}
 C -->|Yes| D[Return prior acknowledgement; no duplicate]
 C -->|No| E[Persist event in inbox]
 E --> F[Process transaction]
 F --> G{Success?}
 G -->|Yes| H[Mark success and acknowledge]
 G -->|No| I[Record error and retry state]
 I --> J[Notify Admin and PM where applicable]
 J --> K[Retry/reconcile with same idempotency key]
```

## 9. Status principles
- Each quantity movement must be explicit and auditable.
- Only passed/approved quantity advances.
- Pending quantity remains at its current operation.
- Rework/restart quantity is blocked from normal progression until released.
- RM-confirmed vendor return quantity alone becomes available for internal QC.
- Packing is not Delivered; dispatch confirmation is required.
