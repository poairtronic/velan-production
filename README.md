# Velan Production

Velan Production is a dedicated manufacturing execution and shop-floor tracking application built to govern the fabrication, assembly, inspection, and delivery of precision industrial valves and engineering sets.

---

## 1. Primary Documentation & Operating Manual

* **Agent Operating Manual:** [`SKILLS.md`](SKILLS.md) *(Mandatory reading for all coding and documentation agents)*
* **Master Technical Context & Handoff Guide:** [`docs/PROJECT_CONTEXT_FOR_CLAUDE.md`](docs/PROJECT_CONTEXT_FOR_CLAUDE.md)  
  *The authoritative reference covering domain models, 10 lifecycle state machines, 70 business rules, and the 51-phase roadmap.*
* **Phase 1 – Requirement Analysis:** [`docs/phase-1/`](docs/phase-1/) (BRD, SRS, 6-role matrix, business rules, integration spec, acceptance criteria)
* **Phase 2 – System Design:** [`docs/phase-2/`](docs/phase-2/) (Architecture ADRs, domain data model, piece/operation/QC/exception/SC state machines)
* **Phase 3 – Technical Specification & Standards:** [`docs/phase-3/`](docs/phase-3/) (Stack, deployment, backend, database, API, security, frontend, integration, testing)
* **Phase 4 – Project Creation Reports:** [`docs/phase-4/`](docs/phase-4/)
* **Project Updates & Review Reports:** [`docs/updates/`](docs/updates/)

---

## 2. Approved Technology Stack

In accordance with Phase 3 specifications (`docs/phase-3/3.1-technology-stack/`):

| Area | Approved Technology |
|---|---|
| **Backend Language & Runtime** | Java 25 (LTS) |
| **Backend Framework** | Spring Boot 3.4.x |
| **Backend Build Tool** | Maven (via Maven Wrapper `mvnw`) |
| **Frontend Framework** | React 19 |
| **Frontend Build Tool** | Vite 6 |
| **Frontend Language** | TypeScript (strict mode: `noUncheckedIndexedAccess: true`) |
| **Frontend Styling** | Tailwind CSS |
| **Database** | PostgreSQL 16 on Neon (pooled `-pooler` and direct, both port `:5432`) |
| **Object Storage** | Cloudflare R2 (S3-compatible API, append-only attachments) |
| **Deployment Platform** | Render (independent services) |
| **Local Containerization** | Docker & Docker Compose |
| **Caching** | In-process Caffeine cache (*strictly no Redis*) |
| **Messaging & Integration** | Database Outbox pattern (*strictly no RabbitMQ, Kafka, or microservices*) |
| **Version Control** | Git |

---

## 3. Repository Architecture & Deployment Model

The project is structured as a **single Git monorepo (`velan-production`)** containing two independent applications:

```text
velan-production/
│
├── backend/              # Java 25 + Spring Boot backend application
├── frontend/             # React 19 + Vite 6 + TypeScript frontend application
├── docs/                 # Authoritative project requirements, design, and standards
│   ├── phase-1/          # Requirements Analysis
│   ├── phase-2/          # System Design & State Machines
│   ├── phase-3/          # Technical Specifications & Architecture Standards
│   ├── phase-4/          # Phase 4 Project Creation Reports
│   └── updates/          # Progress updates and reviews
├── .github/              # GitHub repository templates & workflows
│   └── pull_request_template.md
├── .gitignore            # Monorepo ignore rules (Java/Maven, Node/Vite, IDE, OS)
├── .env.example          # Environment variable template (placeholders only)
├── README.md             # Repository documentation
├── SKILLS.md             # Agent operating manual
└── docker-compose.yml    # Local development container orchestration (Phase 4.5)
```

### Independent Render Deployments
Although co-located in one repository, the backend and frontend are **completely separate applications deployed independently on Render**:
* **Backend Application:** Deployed to Render as a **Web Service**, running Java 25 LTS / Spring Boot 4.x from the `backend/` directory.
* **Frontend Application:** Deployed to Render as a **Static Site**, serving production build assets (`dist/`) generated from the `frontend/` directory.
* **API Communication:** The frontend communicates with the backend exclusively over HTTPS REST APIs (`/api/v1`) and Server-Sent Events (SSE).

---

## 4. Current Development Status

* **Development Roadmap:** Governed by the approved 51-Phase Master Development Roadmap (`docs/phase-1/15_Application_Master_Plan.md`).
* **Completed Sub-Phases:**
  * **Phase 4.0 (Pre-flight Decisions and Repository Audit):** Completed & Approved ([`docs/phase-4/Phase_4.0_Preflight_Audit_Report.md`](docs/phase-4/Phase_4.0_Preflight_Audit_Report.md)).
  * **Phase 4.1 (Root Repository Setup):** Completed & Approved ([`docs/phase-4/Phase-4.1-Completion-Report.md`](docs/phase-4/Phase-4.1-Completion-Report.md)).
  * **Phase 4.2 (Backend Starter Project):** Completed & Approved ([`docs/phase-4/Phase-4.2-Completion-Report.md`](docs/phase-4/Phase-4.2-Completion-Report.md)).
  * **Phase 4.3 (Frontend Starter Project):** Completed & Approved ([`docs/phase-4/Phase-4.3-Completion-Report.md`](docs/phase-4/Phase-4.3-Completion-Report.md)).
  * **Phase 4.4 (Initial Neon Connectivity):** Prepared; live Neon database provisioning and connection verification deferred to Phase 7 per owner decision ([`docs/phase-4/Phase-4.4-Completion-Report.md`](docs/phase-4/Phase-4.4-Completion-Report.md)).
* **Active Sub-Phase:** **Phase 4.5 – Docker & Local Development Environment**
  * `backend/Dockerfile` and `frontend/Dockerfile` established.
  * Root `docker-compose.yml` configured for multi-container local development.

---

## 5. Local Development Workflow

Both applications are configured to run either via Docker Compose or standalone on the host machine. Zero database services are required for local starter execution (Neon PostgreSQL integration is deferred to Phase 7).

### Option A: Running with Docker Compose (Recommended once Docker is installed)

From the repository root:

* **Start both applications (with image build):**
  ```bash
  docker compose up --build
  ```
* **Start in detached mode:**
  ```bash
  docker compose up --build -d
  ```
* **View streaming container logs:**
  ```bash
  docker compose logs -f
  ```
* **Stop containers:**
  ```bash
  docker compose down
  ```
* **Start backend independently:**
  ```bash
  docker compose up --build backend
  ```
* **Start frontend independently:**
  ```bash
  docker compose up --build frontend
  ```

### Option B: Running Standalone (Without Docker)

* **Backend (Spring Boot 4.x / Java 25 LTS):**
  ```powershell
  cd backend
  .\mvnw.cmd spring-boot:run
  # Backend runs on http://localhost:8080
  ```
* **Frontend (React 19 / Vite 6 / TypeScript):**
  ```powershell
  cd frontend
  npm run dev
  # Frontend runs on http://localhost:5173
  ```

### Development URLs
* **Frontend Application:** `http://localhost:5173`
* **Backend Application:** `http://localhost:8080`


---

## 6. Module & Frontend Structure (Phase 5)

### Backend (`backend/src/main/java/com/velan/production/`)
- **9 Domain Modules:** `iam`, `order`, `engineering`, `execution`, `quality`, `qcexception`, `vendor`, `notification`, `integration`
  - Each module has: `api/`, `internal/domain/`, `internal/application/`, `internal/infrastructure/`
- **Shared Kernel:** `shared/` (`audit`, `error`, `id`, `idempotency`, `scheduling`, `security`, `time` — no `api`/`internal` split)

### Frontend (`frontend/src/`)
- `app/App.tsx`, `routes/`, `components/ui/`, `hooks/`, `lib/{api,utils}/`, `types/`, `styles/index.css`
- `features/{iam,order,engineering,execution,quality,qcexception,vendor,notification,integration}/index.ts`
- Path alias `@/*` maps to `frontend/src/*`.

### Git Workflow Rule
- **PR-only to `main`:** Direct commits and direct pushes to `main` are forbidden. Every change must go through a topic branch, Pull Request, CI pass, and Squash Merge (`docs/phase-3/3.8-quality-process/02-git-workflow-and-release.md`).

### Frontend Environment Variables (Phase 6.3)
- `VITE_API_BASE_URL` (required in production builds; dev mode falls back to `http://localhost:8080/api/v1` from `frontend/.env.example`).
- Display timezone is fixed to `Asia/Kolkata` in `frontend/src/lib/env.ts`.
- **Run dev:** `cd frontend && npm run dev`
- **Build production:** `cd frontend && VITE_API_BASE_URL=https://<api-host>/api/v1 npm run build`

### Docker Compose & CI Environment Wiring (Phase 6.4)
- **Local with Compose:** Copy `.env.example` to `.env`, set `JWT_SECRET` (Base64, >= 256 bits) and optional `DATABASE_URL`, then run `docker compose up --build`.
- **CI Values:** `.github/workflows/ci.yml` runs the backend with `SPRING_PROFILES_ACTIVE=local` and a CI-only dummy `JWT_SECRET` (no `DATABASE_URL`, so `NeonConnectivityTests` skips), and sets `VITE_API_BASE_URL=http://localhost:8080/api/v1` for the frontend build.

### Flyway Migrations & MigrationTests (Phase 7.2)
- **Run `MigrationTests` locally against Neon dev branch:**
  Set `DATABASE_DIRECT_URL` (direct `:5432` URL, not `-pooler`), `DB_USER`, and `DB_PASSWORD`, then run:
  `cd backend; .\mvnw.cmd -B clean verify`
- When `DATABASE_DIRECT_URL` is unset, `MigrationTests` and `NeonConnectivityTests` are skipped automatically.
