# Part 3.6 — Frontend Standards
## Document 01: Project Structure, Architecture Conventions, and TypeScript Standards

---

### 1. Confirmed Rules

#### 1.1 Directory Layout (`src/`)
The frontend client is structured as a feature-modular Single Page Application (SPA) built with **React 19**, **TypeScript**, and **Vite**:

```text
frontend/
├── src/
│   ├── app/                      # Application root, providers, router configuration
│   │   ├── App.tsx
│   │   ├── main.tsx
│   │   ├── providers.tsx         # TanStack Query, Auth, Theme, Tooltip providers
│   │   └── router.tsx            # Route declarations and permission guards
│   ├── modules/                  # Feature domain modules (matching backend monolith)
│   │   ├── auth/                 # Login, password reset, session recovery
│   │   ├── orders/               # Size Card intake, import, release
│   │   ├── planning/             # Process routing templates, workstation queues
│   │   ├── execution/            # Operator station view, barcode scan, bulk grid
│   │   ├── quality/              # Blind QC inspection bay, tolerance review
│   │   ├── exceptions/           # PM Exception Cockpit, 4 disposition dialogs
│   │   ├── documents/            # CAD/PDF viewer, R2 pre-signed upload widgets
│   │   ├── notifications/        # Real-time SSE listener, alert drawer
│   │   └── admin/                # User lifecycle, custom roles, audit log viewer
│   ├── shared/                   # Cross-cutting presentation components and utilities
│   │   ├── components/           # Generic buttons, badges, tables, modal dialogs
│   │   │   └── ui/               # shadcn/ui primitive components (Radix UI)
│   │   ├── hooks/                # Generic hooks (useDebounce, useMediaQuery, useKeyboard)
│   │   ├── layouts/              # AppShell, Sidebar, Header, StationLockLayout
│   │   └── utils/                # Date formatting, math helpers, cn() Tailwind merge
│   ├── api/                      # Base HTTP infrastructure and generated OpenAPI schemas
│   │   ├── client.ts             # Axios instance with interceptors and error unboxing
│   │   └── generated/            # TypeScript types emitted by openapi-typescript
│   │       └── schema.d.ts
│   ├── routes/                   # Route path constants and breadcrumb definitions
│   └── types/                    # Global ambient types and utility declarations
│       └── env.d.ts              # Strongly typed Vite environment variables
├── index.html
├── package.json
├── tsconfig.json
└── vite.config.ts
```

#### 1.2 Path Aliases
Path aliases are configured in both `tsconfig.json` and `vite.config.ts`:
* `@/app` $\to$ `src/app/*`
* `@/modules/*` $\to$ `src/modules/*`
* `@/shared/*` $\to$ `src/shared/*`
* `@/api/*` $\to$ `src/api/*`
* `@/hooks/*` $\to$ `src/shared/hooks/*`
* `@/types/*` $\to$ `src/types/*`

#### 1.3 Module Encapsulation and Boundary Import Rules
To prevent frontend spaghetti dependencies and preserve modular monolith boundaries:
1. **No Cross-Module Deep Imports:** A feature module (`modules/execution/`) is strictly forbidden from importing internal components or hooks from another feature module (`modules/quality/internal/...`).
2. **Public API Exports:** If module A requires capabilities from module B, module B must expose them explicitly via its top-level `index.ts` file.
3. **Shared Imports:** All shared UI primitives, layouts, hooks, and API client utilities reside in `@/shared/` or `@/api/` and may be imported freely by any module.
4. **Enforcement:** Enforced in CI via `eslint-plugin-import` boundary rules.

#### 1.4 Naming Conventions
* **React Components & Viewports:** `PascalCase.tsx` (e.g. `OperatorBulkGrid.tsx`, `PMDispositionDialog.tsx`).
* **Custom Hooks:** `camelCase.ts` prefixed with `use` (e.g. `useOperationExecution.ts`, `useBarcodeScanner.ts`).
* **Utility & Helper Files:** `camelCase.ts` (e.g. `formatMeasurements.ts`, `toleranceCalculator.ts`).
* **TypeScript Types & Interfaces:** `PascalCase` (e.g. `PieceExecutionDTO`, `ToleranceValidationResult`).
* **Zod Schemas:** `camelCase` ending with `Schema` (e.g. `submitMeasurementsSchema`, `applyDispositionSchema`).

#### 1.5 TypeScript Strict Configuration
The project enforces strict compiler settings in `tsconfig.json`:
* `"strict": true`
* `"noImplicitAny": true`
* `"strictNullChecks": true`
* `"exactOptionalPropertyTypes": true`
* `"noUncheckedIndexedAccess": true` (guards dynamic grid row/cell lookups)
* `"noImplicitReturns": true`
* `"noFallthroughCasesInSwitch": true`

#### 1.6 Linting & Formatting Standards (Tool Names Only)
Code quality and formatting are automated without developer friction:
* **Linter:** **ESLint 9** using flat config format (`eslint.config.js`).
* **Type-Aware Rules:** **`typescript-eslint`** (`@typescript-eslint/recommended-type-checked`).
* **React Hooks Rules:** **`eslint-plugin-react-hooks`** and **`eslint-plugin-react-refresh`**.
* **Code Formatter:** **Prettier** with **`prettier-plugin-tailwindcss`** for deterministic class ordering.
* **Pre-commit Hook:** **Husky** paired with **`lint-staged`** runs linting and formatting on staged files.

#### 1.7 Component and Hook Architecture Rules
1. **Functional Components Only:** Class components are strictly prohibited.
2. **Separation of Concerns:**
   * Presentational components receive pure props and emit events; they contain zero raw `fetch()` or `axios` calls.
   * Container/Page components orchestrate data fetching via TanStack Query custom hooks and pass data down.
3. **Hook Prefixing:** All custom hooks must start with `use` and be placed in `hooks/` directories.

#### 1.8 Environment Variable Access Standards
1. **Prefix:** All client-accessible environment variables must be prefixed with `VITE_` (e.g. `VITE_API_BASE_URL`, `VITE_SSE_URL`).
2. **Centralized Validation:** Direct inline `import.meta.env.VITE_XYZ` scattered across UI components is strictly prohibited. All environment variables are validated at startup through a Zod schema in `src/app/config.ts`:
   ```typescript
   export const env = envSchema.parse({
     apiBaseUrl: import.meta.env.VITE_API_BASE_URL,
     sseUrl: import.meta.env.VITE_SSE_URL,
   });
   ```

---

### 2. Proposed Design

#### 2.1 Feature Module Internal Architecture
Each domain module under `src/modules/<module-name>/` follows an identical, predictable file layout:
```text
modules/execution/
├── api/                  # TanStack Query hooks and axios mutation calls
│   ├── usePieceOperations.ts
│   └── useSubmitMeasurements.ts
├── components/           # Domain-specific UI widgets
│   ├── OperatorBulkGrid.tsx
│   ├── BarcodeScannerInput.tsx
│   └── ToleranceDeviationBadge.tsx
├── pages/                # Route-level viewports
│   ├── StationWorklistPage.tsx
│   └── PieceExecutionDetailPage.tsx
├── schemas/              # Zod form validation schemas
│   └── measurementInputSchema.ts
├── types/                # Domain-specific view models
│   └── gridTypes.ts
└── index.ts              # Public exports for other modules
```

---

### 3. Technical Assumptions
1. **ASSUMPTION:** Vite 6.x / React 19 builds run in modern evergreen browsers (Chrome 120+, Edge 120+, Firefox 120+, Safari 17+) installed on rugged shop-floor tablets and office workstations.
2. **ASSUMPTION:** Build output is a static single-page application bundle hosted via Render Static Sites or served through a lightweight static web server with client-side history fallback routing.

---

### 4. Open Questions
* **O-FE-01 (Monorepo vs Single Frontend Directory):** Should the frontend and backend reside in a single Maven/Node monorepo with automated build coordination, or be maintained as discrete directories?
