# 07 — Workflow Diagrams

Updated 2026-11-24 — decisions D-01 to D-19 (see 00_DECISION_LOG.md)

Diagrams use Mermaid flowchart syntax and can be rendered in Markdown viewers that support Mermaid.

## 1. Overall SC → Production → Delivered (replaces diagram 1)
```mermaid
flowchart TD
  A["RM: SC created and submitted"] --> B["Production SC record"]
  A2["Manual entry or Excel import"] --> B
  B --> C["Create products and pieces with serials"]
  C --> D["Designer selects process flow per product"]
  D --> E["Designer uploads drawing and tolerance sheet"]
  E --> F["Extraction, Designer verification, PM approval"]
  F --> G["PM daily priority and assignment"]
  G --> H{"Stores marked material issued?"}
  H -->|No| H1["Pieces blocked at material gate"]
  H1 --> H
  H -->|Yes| I["Operator enters values per piece"]
  I --> J["QC enters independent values"]
  J --> K{"Within tolerance?"}
  K -->|Yes| L["Next operation"]
  K -->|No| M["Flag and alert; piece waits for PM"]
  M --> N{"PM decision"}
  N -->|"Continue with reason"| L
  N -->|"Extra process or earlier operation"| I
  N -->|"Reject and restart"| O["Scrap piece, create replacement, send to ECR"]
  O --> P["Replacement released when RM shows ECR material issued, or PM releases"]
  P --> I
  L --> Q{"All operations passed?"}
  Q -->|No| I
  Q -->|Yes| R["Piece Packed"]
  R --> S{"All pieces of all products Packed?"}
  S -->|No| T["SC stays in progress"]
  S -->|Yes| U["SC Delivered automatically"]
```

## 2. Material-issued gate (replaces diagram 2)
```mermaid
flowchart TD
  A["Stores issues material in RM"] --> B["Production sync reads issue status"]
  B --> C{"Material issued for this product?"}
  C -->|No| D["Product pieces stay blocked"]
  C -->|Yes| E["Set issued flag; pieces become ready"]
  F["PM or Admin sets flag manually when sync is delayed"] --> E
```

## 3. Operator and QC loop (replaces diagram 3)
```mermaid
flowchart TD
  A["Operator opens own queue in PM priority order"] --> B["Select pieces and operation"]
  B --> C["Check issued flag, instructions, drawing, static and dynamic tolerance"]
  C --> D["Enter values in bulk grid and submit"]
  D --> E["QC enters independent values"]
  E --> F{"Within tolerance?"}
  F -->|Yes| G["Piece passes; moves to next operation"]
  F -->|No| H["Non-blocking alert; piece waits for PM"]
  H --> I{"PM decision"}
  I -->|"Continue with reason"| G
  I -->|"Extra process"| J["New operation with own tolerance list"]
  I -->|"Return to earlier operation"| K["Earlier operation with new instructions"]
  I -->|"Reject and restart"| L["Scrap, replacement piece, ECR"]
  J --> D
  K --> D
  L --> D
```
After an extra process or earlier-operation rework passes QC, the piece continues after the originally failed operation.

## 4. Tolerance revision
```mermaid
flowchart TD
  A["Designer requests/uploads tolerance revision"] --> B["System extracts and maps values"]
  B --> C["Designer verifies/corrects"]
  C --> D["Submit revision to PM"]
  D --> E{"PM approves?"}
  E -->|No| F["Keep current active revision; notify Designer"]
  E -->|Yes| G["Create immutable approved revision"]
  G --> H["Notify PM and affected production users"]
  H --> I["Apply to data not yet entered / eligible unfinished quantities"]
  I --> J["Do not overwrite existing measurement values"]
  J --> K["Re-evaluate affected unfinished quantities and flag newly failing cases"]
```

## 5. Vendor operation (replaces diagram 5)
```mermaid
flowchart TD
  A["RM creates and dispatches vendor DC"] --> B["Production sync reads DC: vendor, DC number, process, product, quantity"]
  B --> C["Pieces shown as at vendor (quantity)"]
  C --> D["RM records inward; sync reads return status"]
  D --> E{"Return status"}
  E -->|"Not received"| C
  E -->|"Partial or full"| F["Production user tags returned pieces and selects the process"]
  F --> G["QC enters independent values"]
  G --> H{"Within tolerance?"}
  H -->|Yes| I["Piece continues with next pending operation"]
  H -->|No| J["Flag; PM decides"]
```

## 6. Exception to ECR (replaces diagram 6)
```mermaid
flowchart TD
  A["PM decision: extra process, earlier operation, or reject and restart"] --> B["Production creates exception record with unique id"]
  B --> C["Outbox sends once to ECR; store ECR report number"]
  B --> D["Production continues its own workflow without waiting"]
  C --> E["ECR identifies responsibility and costing independently"]
  E -.->|"material issued later in RM"| F["Replacement piece released if reject and restart"]
```
Mapping: extra process → ADDITIONAL_PROCESS (sent as REWORK with a label, O-04); return to earlier operation → REWORK; reject and restart → REJECTION. Nothing is sent to RM.

## 7. Completion (replaces the packing and dispatch diagram)
```mermaid
flowchart TD
  A["Piece passes its last operation"] --> B["Piece Packed"]
  B --> C{"All pieces of the product Packed?"}
  C -->|No| D["Product stays in progress"]
  C -->|Yes| E["Product complete"]
  E --> F{"All products of the SC complete?"}
  F -->|No| G["SC stays in progress"]
  F -->|Yes| H["SC Delivered automatically; billing is outside Production"]
```
Set-level completion and partial delivery are OPEN (O-03).

## 8. RM sync and failure handling (replaces diagram 8)
```mermaid
flowchart TD
  A["Scheduler or Refresh now"] --> B["Read RM data with read-only service account"]
  B --> C{"Call succeeded?"}
  C -->|No| D["Write sync log, retry with backoff, alert Admin and PM after repeated failure"]
  C -->|Yes| E["Compare with Production data by RM id"]
  E --> F["Idempotent upsert; apply revisions by diff"]
  F --> G["Write sync log"]
```

## 9. Status principles (replaces §9)
- Every piece status change is explicit and audited.
- Only pieces passed by QC, or continued by the PM with a reason, advance.
- Flagged pieces wait for the PM decision; other pieces keep working.
- A replacement piece is blocked until released.
- A piece is Packed when all operations pass; the SC is Delivered automatically when all products are complete.
- Quantities are always counted from piece status, never entered by hand.
