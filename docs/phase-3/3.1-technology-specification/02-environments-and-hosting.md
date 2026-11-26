# Part 3.1 — Technology Specification and Environments
## Document 02: Environments, Hosting, and Infrastructure Topology

---

### 1. Confirmed Rules (Frozen Baseline)
* **Hosting Platform:** Render cloud infrastructure for all compute services (`ADR-01, D-13`).
* **Deployment Decoupling:** Frontend and Backend are deployed independently (`D-13`):
  - Frontend: Render Static Site (served via Render Global CDN).
  - Backend: Render Web Service (Docker container running Spring Boot on Java 21).
* **Managed Database:** PostgreSQL on Neon serverless architecture (`ADR-08`).
* **Binary Object Store:** Cloudflare R2 (S3-compatible) with pre-signed URLs (`ADR-05`).
* **Connection Invariant:** Schema migrations (Flyway) must execute against **direct unpooled database endpoints**; application runtime traffic routes through **pooled endpoints** (`ADR-08`).

---

### 2. Environment Strategy and Render Topology

Velan Production provisions three isolated tiers: `dev` (development/PR preview), `test` (staging/QA/UAT), and `prod` (live shop floor).

```mermaid
flowchart TD
    subgraph Users ["Shop-Floor Terminals & Office Desktops"]
        Browser["Chrome / Edge Browser (HTTPS)"]
    end

    subgraph RenderPlatform ["Render Cloud Infrastructure"]
        subgraph StaticCDN ["Frontend Static CDN"]
            FE_PROD["velan-production-frontend (Static Site)<br/>Origin: dist/ (Vite + React)"]
        end

        subgraph ComputeService ["Backend Docker Web Service"]
            BE_PROD["velan-production-backend (Docker Web Service)<br/>Java 21 + Spring Boot (Port 8080)<br/>Health: /actuator/health"]
        end
    end

    subgraph NeonCloud ["Neon Serverless PostgreSQL"]
        subgraph NeonBranches ["Project: velan-production-neon"]
            POOL_PROD["main branch - Pooled Endpoint (Port 5432 / PgBouncer)<br/>Hostname: ep-xxx-pooler.neon.tech<br/>Runtime Application Traffic (HikariCP)"]
            DIRECT_PROD["main branch - Direct Endpoint (Port 5432 / Compute)<br/>Hostname: ep-xxx.neon.tech<br/>Flyway Schema Migrations Only"]
        end
    end

    subgraph CloudflareCloud ["Cloudflare R2 Object Storage"]
        R2_PROD["Bucket: velan-production-r2-prod<br/>Drawings, Blueprints, Photos, Attachments"]
    end

    subgraph ExternalTiers ["External Enterprise Systems"]
        RM["RM System (V-SMART) - Read Only"]
        ECR["ECR System - HTTPS Intake"]
        SMTP["Enterprise SMTP Gateway"]
    end

    Browser -->|1. Load Static Assets (HTTPS)| FE_PROD
    Browser -->|2. REST Commands & SSE Stream (/api/v1)| BE_PROD
    Browser <-->|3. Direct Binary PUT/GET via Pre-Signed URLs| R2_PROD

    BE_PROD -->|4. Runtime SQL Queries (Pooled :5432)| POOL_PROD
    BE_PROD -->|5. Flyway Migrations (Direct :5432)| DIRECT_PROD
    BE_PROD -->|6. S3 SDK Pre-Signed URL Signing| R2_PROD
    BE_PROD -->|7. Poll Requisitions (HTTP GET)| RM
    BE_PROD -->|8. Outbox Exception Posts (HTTPS)| ECR
    BE_PROD -->|9. Dispatch 9 AM Reports (SMTP)| SMTP

    classDef client fill:#eff6ff,stroke:#1d4ed8,stroke-width:2px;
    classDef render fill:#f8fafc,stroke:#334155,stroke-width:2px;
    classDef neon fill:#f0fdf4,stroke:#15803d,stroke-width:2px;
    classDef r2 fill:#fefce8,stroke:#a16207,stroke-width:2px;
    classDef ext fill:#fee2e2,stroke:#dc2626,stroke-width:2px;

    class Browser client;
    class FE_PROD,BE_PROD render;
    class POOL_PROD,DIRECT_PROD neon;
    class R2_PROD r2;
    class RM,ECR,SMTP ext;
```

---

### 3. Proposed Design: Service Matrix Across Environments

| Infrastructure Resource | Development (`dev`) | Staging / Test (`test`) | Production (`prod`) | Configuration Details |
| :--- | :--- | :--- | :--- | :--- |
| **Frontend (Render)** | `velan-fe-dev.onrender.com` | `velan-fe-test.onrender.com` | `production.velan.internal`* | Render Static Site; auto-deploy from `main` or preview branches. |
| **Backend (Render)** | `velan-api-dev.onrender.com` | `velan-api-test.onrender.com` | `api.production.velan.internal`* | Render Web Service (Docker); port 8080; zero public ingress except HTTPS. |
| **Neon Branch** | `branch: dev` | `branch: test` | `branch: main` | Isolated compute and storage per branch; copy-on-write branching. |
| **R2 Storage Bucket** | `velan-r2-dev` | `velan-r2-test` | `velan-r2-prod` | Dedicated buckets per environment; prevents test file contamination. |
| **SMTP Delivery** | Local Mailhog / Ethereal | Staging SMTP Relay | Production Corporate SMTP | Transactional notifications, password resets, daily 9 AM progress reports. |
| **RM Integration** | Mock RM Stub Server | V-SMART UAT Instance | V-SMART Live Instance | Controlled by `RM_SYNC_ENABLED` feature flag (defaults to `false`). |
| **ECR Integration** | Mock ECR Stub Server | ECR Staging Intake | ECR Live Production Intake | Controlled by `ECR_SEND_ENABLED` feature flag (defaults to `false`). |

*\*Note: Production domains are documented as architectural assumptions in Section 7.*

---

### 4. Direct vs. Pooled Connection Rules (Neon PostgreSQL)

According to official Neon documentation, Neon provides two distinct connection endpoints per database branch. Both endpoints listen on the standard PostgreSQL port **`5432`**:

#### 4.1 Connection String Specifications
* **Direct Connection String:**
  - **Hostname format:** Standard endpoint hostname **without** `-pooler` (e.g., `ep-cool-darkness-123456.us-east-2.aws.neon.tech`).
  - **Port:** Standard PostgreSQL port **`5432`**.
  - **Connection mode:** Connects directly to the dedicated Postgres compute engine without an intermediate proxy.
* **Pooled Connection String:**
  - **Hostname format:** Standard endpoint hostname with **`-pooler`** inserted before the region/domain (e.g., `ep-cool-darkness-123456-pooler.us-east-2.aws.neon.tech`).
  - **Port:** Standard PostgreSQL port **`5432`** (Neon PgBouncer listens on standard port 5432, not 6432).
  - **Connection mode:** Connects through Neon's built-in **PgBouncer running in Transaction Mode**.

#### 4.2 Component Allocation & Architectural Rationale
1. **Flyway Database Migrations (Direct Connection Required):**
   - **Assigned Component:** Flyway database migrations (`SPRING_FLYWAY_URL`).
   - **Reason (Official Neon Grounding):** Flyway relies heavily on **session-level state**, specifically **PostgreSQL session advisory locks** (`pg_advisory_lock`, `pg_try_advisory_lock`), to guarantee atomic schema migrations and prevent concurrent container migration collisions. In PgBouncer transaction mode, connections are returned to the pool immediately upon transaction completion (commit or rollback). Advisory locks are session-scoped; if an advisory lock is acquired on one backend connection and subsequent migration statements execute on a different pooled connection, the lock context is lost, causing Flyway migrations to fail, hang, or error out. Connecting directly to the compute node preserves the session context throughout migration execution.
2. **Application Runtime Traffic (Pooled Connection Required):**
   - **Assigned Component:** HikariCP runtime datasource (`SPRING_DATASOURCE_URL`).
   - **Reason:** Application web requests, transactions, and outbox pollers execute discrete, transactional queries. Routing through Neon's PgBouncer pooler multiplexes dozens of application connections into a lean set of compute connections, preventing connection exhaustion and supporting serverless auto-scaling.

---

### 5. Cloudflare R2 Storage & CORS Policies

Each environment uses an isolated Cloudflare R2 bucket (`velan-r2-dev`, `velan-r2-test`, `velan-r2-prod`).

#### 5.1 Pre-Signed Upload & Download Mechanics
1. Frontend requests an upload URL: `POST /api/v1/documents/presigned-upload-url`.
2. Backend generates an AWS S3 SDK v2 pre-signed PUT URL with a strict **15-minute expiration window**.
3. Frontend streams binary bytes directly to Cloudflare R2 via HTTP PUT.
4. Frontend notifies backend of upload completion: `POST /api/v1/documents/confirm-upload`. Backend verifies object existence via S3 `HeadObject` and commits metadata to Neon PostgreSQL.

#### 5.2 R2 CORS Rules
To allow direct browser uploads/downloads across environments, each R2 bucket enforces CORS:
```json
[
  {
    "AllowedOrigins": [
      "https://production.velan.internal",
      "https://velan-fe-test.onrender.com",
      "http://localhost:5173"
    ],
    "AllowedMethods": ["GET", "PUT", "HEAD"],
    "AllowedHeaders": ["Content-Type", "x-amz-checksum-sha256"],
    "ExposeHeaders": ["ETag"],
    "MaxAgeSeconds": 3600
  }
]
```

---

### 6. Health Checks, Readiness, and Liveness

The Spring Boot backend exposes Spring Actuator endpoints configured for Render zero-downtime health probing:
* **Health Endpoint:** `GET /actuator/health`
* **Liveness Probe:** `GET /actuator/health/liveness` (returns HTTP 200 if JVM is running).
* **Readiness Probe:** `GET /actuator/health/readiness` (returns HTTP 200 only after database migrations complete, database pool is validated, and Caffeine cache is initialized).
* **Render Service Setting:** Health Check Path configured to `/actuator/health`. Deployments are blocked if the container fails health checks within 180 seconds.

---

### 7. Technical Assumptions (Sizing & Hostnames)

The following parameters are marked explicitly as **Engineering Assumptions** subject to validation during Phase 46 load testing:
* **ASSUMPTION:** Render backend compute sizing:
  - *Dev / Test:* `Starter` instance (0.5 CPU, 512 MB RAM).
  - *Prod:* `Standard` instance (1 CPU, 2 GB RAM). Estimated sufficient for JVM heap (`-Xmx1024m`) and Caffeine cache overhead.
* **ASSUMPTION:** Render frontend static site hosted on Render Global CDN (unmetered static asset bandwidth).
* **ASSUMPTION:** Neon PostgreSQL compute sizing:
  - *Dev / Test:* 0.25 to 1 Neon Compute Unit (CU), auto-suspending after 5 minutes of inactivity.
  - *Prod:* 0.5 to 2 CUs with auto-scaling enabled; zero auto-suspend during active factory shifts (6:00 AM – 10:00 PM IST).
* **ASSUMPTION:** Target concurrency of 50 active shop-floor terminals supported within 10–15 HikariCP pooled database connections.
* **ASSUMPTION:** `production.velan.internal` and `api.production.velan.internal` are placeholder internal network domain names for Velan IT reverse proxy routing.

---

### 8. Open Questions
* **O-ENV-01:** Should production factory terminals access the frontend over the public Internet via Render domains, or will Velan configure a private corporate VPN / reverse proxy with custom domain `production.velan.internal`? (Documented in Document 05).
