# Part 3.6 — Frontend Standards
## Document 02: Data Fetching, State Architecture, Forms, and Server Error Mapping

---

### 1. Confirmed Rules

#### 1.1 Centralized Axios Client & Interceptor Lifecycle
All HTTP communication with the Spring Boot backend (`/api/v1`) flows through a configured singleton Axios instance in `src/api/client.ts`:

1. **Request Interceptor:**
   * **Authentication:** Injects the short-lived JWT access token into the `Authorization: Bearer <token>` header from in-memory authentication state.
   * **Tracing:** Injects `X-Correlation-Id: <UUIDv4>` if not already set.
   * **CSRF Defense:** Adds `X-Requested-With: XMLHttpRequest`.
   * **Idempotency:** For mutating actions requiring idempotency, attaches `Idempotency-Key: <UUIDv4>`.
2. **Response Interceptor & Token Refresh Flow:**
   * **Success:** Automatically unboxes the success envelope (`ApiResponse<T>`), passing `{ data, meta }` cleanly to caller functions.
   * **401 Unauthorized Interception:**
     - If a 401 occurs, the client pauses failing requests in a memory queue.
     - Automatically issues a single `POST /api/v1/auth/refresh` request (the browser automatically includes the `HttpOnly; Secure; SameSite=Strict` refresh token cookie).
     - Upon receiving a new access token, it retries all queued requests with the updated token.
     - If the refresh request itself fails (e.g. refresh token expired or revoked), all state is cleared, and the user is redirected to `/login?session_expired=true`.
   * **Error Unboxing:** Intercepts non-2xx responses, parses the standardized backend JSON payload (`{ code, message, details[], timestamp, path, correlationId }`), and throws a strongly typed `ApiError` class.

#### 1.2 TanStack Query v5 Cache Conventions & Key Factories
To eliminate typo bugs and guarantee deterministic cache invalidation, all query keys must be authored using hierarchical **Query Key Factories**:

```typescript
export const pieceKeys = {
  all: ['pieces'] as const,
  lists: () => [...pieceKeys.all, 'list'] as const,
  list: (filters: PieceFilters) => [...pieceKeys.lists(), filters] as const,
  details: () => [...pieceKeys.all, 'detail'] as const,
  detail: (id: string) => [...pieceKeys.details(), id] as const,
};
```

* **Stale Time Policy:**
  - Standard queries: `staleTime: 30_000` (30 seconds).
  - Shop-floor station worklists: `staleTime: 10_000` (10 seconds), combined with real-time SSE cache invalidation.
  - Master data (process templates, work stations): `staleTime: 5 * 60_000` (5 minutes).

#### 1.3 Mutation Invalidation & Optimistic Update Policies
1. **Targeted Invalidation:**
   * Successful mutations must explicitly invalidate related query keys via `queryClient.invalidateQueries({ queryKey: ... })`.
   * Example: Submitting an operation measurement invalidates `pieceKeys.detail(id)`, `pieceOperationKeys.stationWorklist(stationId)`, and `qcInspectionKeys.pending()`.
2. **Optimistic Updates — When Allowed:**
   * Optimistic UI updates are permitted **only** for non-critical, reversible UI states:
     - Drag-and-drop daily priority sequence reordering (`PLANNING:PRIORITY_MANAGE`).
     - Marking a notification as read.
     - Local bookmarking or pinning of contract cards.
3. **Optimistic Updates — When Strictly Prohibited:**
   * Optimistic updates are **strictly forbidden** for machine state transitions and quality actions:
     - Starting an operation (`READY` $\to$ `OPENED`).
     - Submitting dimensional measurements.
     - QC pass/fail evaluations.
     - PM exception dispositions (`CONTINUE`, `EXTRA_PROCESS`, `RETURN`, `SCRAP`).
     - Releasing contracts or pieces to production.
   * *Rationale:* Physical manufacturing state transitions must be confirmed by the ACID transaction in Neon PostgreSQL before shop-floor screens advance. An optimistic update that rolls back creates confusion and physical safety risks on the factory floor.

#### 1.4 State Partitioning: Server vs. Client vs. Local
The application strictly enforces clear state ownership:

| State Archetype | Technology | What Lives Here |
|:---|:---|:---|
| **Server State** | **TanStack Query** | Pieces, worklists, drawings, tolerances, exceptions, audit logs, user lists. |
| **Global Client State** | **Zustand** | Active station selection lock, USB barcode scanner buffer stream, active modal drawer state, collapsed sidebar preference. |
| **Local Ephemeral State** | **`useState` / `useReducer`** | Form inputs before submit, table pagination/sorting controls, accordion open/close, hover states. |

---

### 2. Proposed Design

#### 2.1 React Hook Form + Zod Integration Standard
All forms must be implemented using **React Hook Form** with **Zod** schema validation via `@hookform/resolvers/zod`:
* Zod schemas enforce client-side constraints matching backend Jakarta Bean Validation rules before network submission.
* Form state exposes `isSubmitting` to disable action buttons and prevent double-clicks.

#### 2.2 Consuming Generated OpenAPI TypeScript Types
Types generated by `openapi-typescript` (`src/api/generated/schema.d.ts`) are aliased in domain modules:
```typescript
import type { components } from '@/api/generated/schema';

export type PieceSummaryDTO = components['schemas']['PieceSummaryDTO'];
export type SubmitMeasurementsRequestDTO = components['schemas']['SubmitMeasurementsRequestDTO'];
export type OperationExecutionDTO = components['schemas']['OperationExecutionDTO'];
```
This guarantees 100% end-to-end type safety: altering a backend Java DTO breaks the frontend build at compile time if types mismatch.

#### 2.3 Server Validation Error Mapping to Form Fields
When a mutation fails with HTTP 422 (`EXECUTION_VALIDATION_FAILED`), the mutation's `onError` callback automatically maps backend `details[]` directly into React Hook Form field errors:

```typescript
onError: (error: ApiError) => {
  if (error.status === 422 && error.details?.length) {
    error.details.forEach((detail) => {
      if (detail.field) {
        form.setError(detail.field as any, {
          type: 'server',
          message: detail.message,
        });
      }
    });
  } else {
    toast.error(error.message || 'Operation failed');
  }
}
```
Field inputs are highlighted in red with the server-provided message rendered beneath the corresponding input box.

---

### 3. Technical Assumptions
1. **ASSUMPTION:** Axios interceptors handle token refreshing transparently without breaking concurrent in-flight requests.
2. **ASSUMPTION:** `openapi-typescript` generates pure ambient `.d.ts` types, adding zero runtime overhead to the production JavaScript bundle.

---

### 4. Open Questions
* **C-1 (Completion Workflow Model):** Queries and mutations for assembled set components and loading dock packing records remain conditioned on Conflict C-1.
