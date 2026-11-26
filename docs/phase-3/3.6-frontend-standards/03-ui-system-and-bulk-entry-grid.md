# Part 3.6 — Frontend Standards
## Document 03: UI Design System, Accessibility, and Bulk Measurement Grid Specification

---

### 1. Confirmed Rules

#### 1.1 Design Tokens & Theming (Tailwind CSS)
The design system provides a clean, high-contrast, industrial theme suitable for both office desktops and rugged shop-floor touch terminals:
* **Palette:** Slate/Neutral scale (`slate-50` through `slate-950`).
* **Semantic Status Colors:**
  - **Success / In-Spec:** Emerald (`emerald-600`, `bg-emerald-50`, `border-emerald-300`).
  - **Warning / Near Boundary:** Amber (`amber-600`, `bg-amber-50`, `border-amber-300`).
  - **Error / Out-of-Spec / Failed:** Rose (`rose-600`, `bg-rose-50`, `border-rose-300`).
  - **Info / Actionable:** Blue (`blue-600`, `bg-blue-50`, `border-blue-300`).
* **Border Radius:** Default `0.5rem` (`rounded-lg`) for cards, dialogs, and inputs.
* **Typography:** `Inter` or system sans-serif for high legibility on high-DPI displays.

#### 1.2 Component Primitives (shadcn/ui & Radix UI)
* Base accessible primitives are imported from **shadcn/ui** (built on headless Radix UI) and located in `src/shared/components/ui/`.
* Components include: `Button`, `Input`, `Table`, `Dialog`, `Sheet`, `DropdownMenu`, `Tooltip`, `Badge`, `Alert`, `Skeleton`, `Tabs`.
* Direct styling is managed strictly through Tailwind utility classes.

#### 1.3 Viewport Responsiveness & Touch Ergonomics
1. **Desktop Viewports (1920x1080 / 1440x900):** Optimized for data density in PM Cockpit, Designer Studio, and Admin consoles. Dense tables, multi-column layouts, sticky headers.
2. **Shop-Floor Tablet & Touch Terminals (1024x768 / 1280x800):**
   * Optimized for rugged 10-inch tablets and touch kiosks used by Operators and QC Inspectors.
   * **Minimum Touch Target:** Buttons, table cells, and toggles must maintain a minimum touch target size of **44px $\times$ 44px**.
   * High-contrast borders and clear focus rings prevent mis-clicks with gloved hands.

#### 1.4 Accessibility Basics (WCAG 2.1 AA)
* Visual focus indicators (`ring-2 ring-primary ring-offset-2`) are enforced on all interactive elements.
* Screen-reader accessible ARIA labels (`aria-label`, `aria-describedby`) on icon-only buttons.
* Color is never the sole conveyor of information: out-of-spec cells include both an icon (`AlertCircle`) and explicit deviation text (`+0.04 mm`).

#### 1.5 Standard Viewport States
Every page and table must handle four standard states gracefully:
* **Loading State:** Skeleton loaders (`Skeleton`) that mirror the page layout to eliminate visual layout shift (CLS).
* **Empty State:** Clean illustration/icon, clear explanation text, and an action button (e.g. "No pending operations at this station. [Scan Next Piece]").
* **Error State:** Alert banner with the backend error code, human message, and a "Retry" button.
* **Permission-Denied State:** Centered card with a lock icon, clear notice (*"Permission Required: EXECUTION:OPERATION_START"*), and a link to return to the dashboard.

---

### 2. Proposed Design

#### 2.1 The Shop-Floor Bulk Measurement Grid Specification

```
+-------------------------------------------------------------------------------------------------------+
| PIECE SERIAL  | OD FLANGE (150.0 ±0.5) | BORE DEPTH (45.0 +0.2/-0.0) | VISUAL FINISH | STATUS / ACTION|
+---------------+------------------------+-----------------------------+---------------+----------------+
| V-1024-001    | [ 150.120 ] (In-Spec)  | [  45.080 ] (In-Spec)       | [ YES / NO ]  | [ READY ]      |
| V-1024-002    | [ 150.620 ] (+0.120) ! | [  45.010 ] (In-Spec)       | [ YES / NO ]  | [ WARN ]       |
| V-1024-003    | [ 149.950 ] (In-Spec)  | [         ] (Required) *    | [ YES / NO ]  | [ INCOMPLETE ] |
+-------------------------------------------------------------------------------------------------------+
```

1. **Topology & Structure:**
   * **Rows:** Individual serialized physical pieces belonging to the batch/set.
   * **Columns:** Specific required inspection parameters configured for that process operation.
2. **The Five Parameter Types:**
   * **`NUMERIC`:** High-precision physical measurement (e.g. 150.120 mm). Formatted to 3 decimal places.
   * **`YES_NO`:** Binary pass/fail or visual check toggle (e.g. burrs removed, threads clean).
   * **`TEXT`:** Alphanumeric annotation (e.g. heat stamp marking, machine operator remark).
   * **`SIGNATURE`:** Operator digital sign-off checkbox confirming execution.
   * **`IMAGE`:** Direct R2 photo upload widget for visual surface defects or weld seams.
3. **Excel Copy / Paste Support:**
   * Operators can copy multi-cell blocks from Excel or CMM digital caliper exports.
   * A custom `onPaste` handler on the grid container intercepts the clipboard, parses tab-separated (`\t`) and newline-separated (`\r?\n`) values, and populates the 2D grid cells starting from the currently focused cell.
4. **Keyboard Navigation:**
   * `Arrow Keys (Up, Down, Left, Right)`: Navigate between grid cells.
   * `Enter`: Commits the current cell edit and moves focus down one row.
   * `Tab` / `Shift+Tab`: Moves focus horizontally to the next / previous cell.
   * `Escape`: Cancels cell editing and reverts to the previous value.
5. **List Virtualization Threshold:**
   * When a batch exceeds **25 piece rows**, the grid automatically activates row virtualization via **`@tanstack/react-virtual`** to maintain fluid 60 FPS scrolling on low-power touch tablets.
6. **Local Draft Auto-Save:**
   * As values are entered, changes are debounced (500ms) and saved into browser `IndexedDB` under key `draft-grid:{operationId}`.
   * If power drops or the browser closes, the draft is restored with a prominent banner: *"Unsaved draft measurements restored."*
7. **Per-Cell Tolerance Highlighting (Non-Blocking for Operators):**
   * Values are evaluated client-side in real-time against active approved `tolerance_rules`:
     - In-Spec ($\text{Lower} \le \text{Value} \le \text{Upper}$): Soft emerald background.
     - Boundary Alert ($\ge 80\%$ of tolerance band): Soft amber background.
     - Out-of-Spec ($\text{Value} < \text{Lower}$ or $\text{Value} > \text{Upper}$): Soft rose background with alert badge showing exact deviation (e.g. `+0.12 mm`).
   * **The Non-Blocking Rule (BR-032):** Operators are **never mechanically blocked** from submitting out-of-spec readings. The submit dialog warns them (*"2 pieces contain out-of-spec readings. Submit to QC inspection bay?"*), and the backend cleanly routes the pieces to QC for formal verification.
8. **Save vs. Submit Semantics:**
   * **Save Draft:** Stores unvalidated entries in local draft storage without changing backend operation status.
   * **Submit Operation:** Validates all mandatory fields, displays a summary dialog, and executes `POST /api/v1/piece-operations/{id}/actions/submit`.

#### 2.2 The Blind QC Screen Rule (BR-032, BR-033)
Quality Control acts as an independent verification gate, not an operator confirmation step:
* **Invariant:** The QC inspection screen must **NEVER** pre-fill, pre-populate, or display the operator's measured values!
* When a QC Inspector opens an inspection sheet for an operation in `QC_PENDING`, all measurement input fields are rendered **completely blank**.
* The QC Inspector must physically measure the piece using calibrated instruments and enter their own independent readings.
* *Rationale:* Hiding operator readings prevents cognitive anchoring bias, ensuring authentic physical inspection compliance.

---

### 3. Technical Assumptions
1. **ASSUMPTION:** Grid clipboard paste parsing runs on standard clipboard text (`navigator.clipboard.readText()`), supported across all modern browsers.
2. **ASSUMPTION:** Rugged shop-floor touch tablets are configured with minimum $1280 \times 800$ screen resolution.

---

### 4. Open Questions
* **C-1 (Completion Workflow Model):** Specialized packing checklist grids and dispatch bay inspection forms remain conditioned on Conflict C-1.
