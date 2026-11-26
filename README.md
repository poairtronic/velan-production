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
| **Backend Language & Runtime** | Java 21 (LTS) |
| **Backend Framework** | Spring Boot 3.4.x |
| **Backend Build Tool** | Maven (via Maven Wrapper `mvnw`) |
| **Frontend Framework** | React 19 |
| **Frontend Build Tool** | Vite 6 |
| **Frontend Language** | TypeScript (strict mode: `noUncheckedIndexedAccess: true`) |
| **Frontend Styling** | Tailwind CSS |
| **Database** | PostgreSQL 16 on Neon (pooled `:6543` for runtime, direct `:5432` for Flyway) |
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
├── backend/              # Java 21 + Spring Boot 3.4.x backend application
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
* **Backend Application:** Deployed to Render as a **Web Service**, running Java 21 / Spring Boot 3.4.x from the `backend/` directory.
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

* **Backend (Spring Boot 3.4.x / Java 21):**
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
