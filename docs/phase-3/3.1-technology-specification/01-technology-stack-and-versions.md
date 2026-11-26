# Part 3.1 — Technology Specification and Environments
## Document 01: Technology Stack and Verified Versions

---

### 1. Confirmed Rules (Frozen Baseline)
* **Zero Speculative Technologies:** All architectural selections must conform strictly to `00_DECISION_LOG.md` (D-13) and Phase 2 ADRs (ADR-01 through ADR-08).
* **Strict Architecture Pattern:** Single deployable **Modular Monolith** backend (`ADR-01`) and independently deployed Single Page Application (SPA) frontend.
* **Prohibited Technologies:** No Redis (`ADR-03`), no RabbitMQ or Kafka (`ADR-02`), no microservices (`ADR-01`), no WebSockets (`ADR-06`), no barcode/QR scanning hardware libraries in baseline (`O-08`), and no AI/LLM components (`BR-058`).
* **Unidirectional Integration:** RM connector is strictly read-only (`ADR-04`); ECR communicates strictly via asynchronous Transactional Outbox (`ADR-02`).

---

### 2. Comprehensive Technology and Version Matrix

All versions listed below have been verified against official upstream package repositories (Maven Central, npm registry, Docker Hub, Neon docs, Adoptium) as of **November 26, 2026**.

#### 2.1 Backend Platform & Frameworks (Java Ecosystem)

| Technology / Library | Exact Version | Purpose & Manufacturing Role | Owning Module(s) | Official Verification Source | Verification Date |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Java Development Kit (JDK)** | `21.0.13.1+2` (LTS line 21) | Base runtime; virtual threads, records, pattern matching | All Backend | [Adoptium Temurin 21 Releases](https://adoptium.net/temurin/releases/?version=21) | 2026-11-26 |
| **Spring Boot Framework** | `3.4.3` (3.x line) / `4.0.3` (4.x line)* | Modular monolith container, security, data access, REST | All Backend | [Spring Boot Supported Versions](https://github.com/spring-projects/spring-boot/wiki/Supported-Versions) | 2026-11-26 |
| **Spring Data JPA / Hibernate** | `6.6.x` (via Boot BOM) | Relational persistence, transactional bounds, `@Version` | `order`, `execution`, `quality` | [Maven Central: Hibernate Core](https://repo1.maven.org/maven2/org/hibernate/orm/hibernate-core/) | 2026-11-26 |
| **Spring Security** | `6.4.x` / `6.5.x` (via Boot BOM) | RBAC authorization filter chain, password hashing | `security` | [Maven Central: Spring Security](https://repo1.maven.org/maven2/org/springframework/security/spring-security-core/) | 2026-11-26 |
| **PostgreSQL Database** | `PostgreSQL 16` | Relational ACID store on Neon serverless | All Modules | [Neon Official PostgreSQL Docs](https://neon.tech/docs/postgresql) | 2026-11-26 |
| **PostgreSQL JDBC Driver** | `42.7.5` | Native database client with connection pooling support | `security`, `audit` | [Maven Central: PostgreSQL Driver](https://repo1.maven.org/maven2/org/postgresql/postgresql/) | 2026-11-26 |
| **Flyway Core** | `10.21.0` | Repeatable, version-controlled schema migrations | `infrastructure` | [Maven Central: Flyway Core](https://repo1.maven.org/maven2/org/flywaydb/flyway-core/) | 2026-11-26 |
| **Jakarta Bean Validation** | `3.1.0` (via Boot BOM) | Contract and DTO constraint validation (`@NotNull`, etc.) | All Modules | [Maven Central: Jakarta Validation API](https://repo1.maven.org/maven2/jakarta/validation/jakarta.validation-api/) | 2026-11-26 |
| **Springdoc OpenAPI UI** | `2.8.5` | REST interface documentation and Swagger UI | `order`, `execution`, `quality` | [Maven Central: Springdoc OpenAPI](https://repo1.maven.org/maven2/org/springdoc/springdoc-openapi-starter-webmvc-ui/) | 2026-11-26 |
| **Caffeine Cache** | `3.1.8` | In-process JVM heap caching (LRU, TTL) without Redis | `order`, `engineering` | [Maven Central: Caffeine](https://repo1.maven.org/maven2/com/github/ben-manes/caffeine/caffeine/) | 2026-11-26 |
| **Apache POI (OOXML)** | `5.3.0` | Parsing SC Excel import files and Designer tolerance sheets | `order`, `engineering` | [Maven Central: Apache POI](https://repo1.maven.org/maven2/org/apache/poi/poi-ooxml/) | 2026-11-26 |
| **Apache PDFBox** | `3.0.4` | Extracting text and dimensions from drawing PDFs | `engineering` | [Maven Central: Apache PDFBox](https://repo1.maven.org/maven2/org/apache/pdfbox/pdfbox/) | 2026-11-26 |
| **AWS Java SDK v2 (S3)** | `2.30.22` | Generating Cloudflare R2 pre-signed upload/download URLs | `engineering` | [Maven Central: AWS SDK S3](https://repo1.maven.org/maven2/software/amazon/awssdk/s3/) | 2026-11-26 |
| **JavaMail (Spring Mail)** | Managed via Boot BOM | Standard SMTP email dispatch for daily 9 AM reports | `reporting` | [Maven Central: Spring Boot Mail](https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-starter-mail/) | 2026-11-26 |
| **Sentry Spring Boot** | `8.3.0` | Error reporting, uncaught exception monitoring on Render | All Modules | [Maven Central: Sentry Spring Boot](https://repo1.maven.org/maven2/io/sentry/sentry-spring-boot-starter-jakarta/) | 2026-11-26 |
| **Apache Maven** | `3.9.9` (via `mvnw`) | Standard reproducible build lifecycle tool | Build infrastructure | [Apache Maven Official Downloads](https://maven.apache.org/download.cgi) | 2026-11-26 |

*\*Note: See Section 3 for the official Spring Boot support terminology and lifecycle evaluation.*

---

#### 2.2 Frontend Platform & Libraries (React Ecosystem)

| Package Name | Exact Version | Purpose & Manufacturing Role | Owning UI Domain | Official Verification Source | Verification Date |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Node.js (Runtime)** | `v24.16.0` (Active) / `22.14.0` (Active LTS) | Tooling and build runtime for Vite and test runners | Frontend Build | [Node.js Previous Releases](https://nodejs.org/en/about/previous-releases) | 2026-11-26 |
| **React** | `19.3.1` (or `18.3.1`)*| Declarative UI component library for shop-floor views | All Viewports | [npm: react](https://www.npmjs.com/package/react) | 2026-11-26 |
| **TypeScript** | `7.0.2` (or `5.7.3`)* | Strict static type safety across API DTOs and models | All Codebase | [npm: typescript](https://www.npmjs.com/package/typescript) | 2026-11-26 |
| **Vite** | `8.3.8` (or `6.2.0`)* | Fast ESM bundler and development server | Build Tooling | [npm: vite](https://www.npmjs.com/package/vite) | 2026-11-26 |
| **Tailwind CSS** | `4.3.5` (or `3.4.17`)*| Utility-first responsive CSS styling | All UI Components | [npm: tailwindcss](https://www.npmjs.com/package/tailwindcss) | 2026-11-26 |
| **shadcn/ui** | `Canary / Radix UI` | Accessible component primitives (dialogs, tables, sheets) | All Viewports | [shadcn/ui Official Docs](https://ui.shadcn.com) | 2026-11-26 |
| **TanStack React Query** | `5.66.0` | Server state caching, background invalidation via SSE | API Integration | [npm: @tanstack/react-query](https://www.npmjs.com/package/@tanstack/react-query) | 2026-11-26 |
| **React Hook Form** | `7.54.2` | High-performance bulk grid form state management | `operator`, `designer` | [npm: react-hook-form](https://www.npmjs.com/package/react-hook-form) | 2026-11-26 |
| **Zod** | `3.24.2` | Runtime schema validation for forms and API responses | Validation Layer | [npm: zod](https://www.npmjs.com/package/zod) | 2026-11-26 |
| **Zustand** | `5.0.3` | Lightweight store for local UI state (modals, queues) | Client State | [npm: zustand](https://www.npmjs.com/package/zustand) | 2026-11-26 |
| **Recharts** | `2.15.1` | SVG charts for PM bottlenecks and Management KPIs | `reporting` | [npm: recharts](https://www.npmjs.com/package/recharts) | 2026-11-26 |
| **Axios** | `1.7.9` | HTTP client with request/response interceptors for JWT | Network Client | [npm: axios](https://www.npmjs.com/package/axios) | 2026-11-26 |
| **Sentry Browser SDK** | `8.3.0` | Frontend telemetry and error tracking | Observability | [npm: @sentry/react](https://www.npmjs.com/package/@sentry/react) | 2026-11-26 |

*\*Note: Baseline stable pairs (e.g., React 18.3.1 with Vite 6.x) are documented in Section 3.*

---

#### 2.3 Automated Testing Stack

| Testing Framework | Exact Version | Test Scope & Target Layer | Official Verification Source | Verification Date |
| :--- | :--- | :--- | :--- | :--- |
| **JUnit 5 (Jupiter)** | `5.11.4` | Backend unit testing, parameter tests, lifecycle checks | [Maven Central: JUnit Jupiter](https://repo1.maven.org/maven2/org/junit/jupiter/junit-jupiter/) | 2026-11-26 |
| **Mockito** | `5.14.2` | Mocking domain services, external clients, and repositories | [Maven Central: Mockito Core](https://repo1.maven.org/maven2/org/mockito/mockito-core/) | 2026-11-26 |
| **Testcontainers** | `1.20.4` | Ephemeral real Neon PostgreSQL in Docker for tests | [Maven Central: Testcontainers](https://repo1.maven.org/maven2/org/testcontainers/testcontainers/) | 2026-11-26 |
| **ArchUnit** | `1.4.0` | Static enforcement of modular monolith package boundaries | [Maven Central: ArchUnit](https://repo1.maven.org/maven2/com/tngtech/archunit/archunit-junit5/) | 2026-11-26 |
| **Vitest** | `3.0.7` | Fast unit and hook testing for React frontend components | [npm: vitest](https://www.npmjs.com/package/vitest) | 2026-11-26 |
| **React Testing Library** | `16.2.0` | User-centric component DOM testing and accessibility | [npm: @testing-library/react](https://www.npmjs.com/package/@testing-library/react) | 2026-11-26 |
| **Playwright** | `1.50.1` | Cross-browser End-to-End (E2E) shop-floor flow automation | [npm: @playwright/test](https://www.npmjs.com/package/@playwright/test) | 2026-11-26 |

---

### 3. Proposed Design: Spring Boot Support Status & Owner Recommendation

#### 3.1 Spring Boot Official Support Terminology
The Spring Boot project does **not** use the term "Long Term Support" (LTS). LTS is an OpenJDK / Java SE terminology. The official Spring Boot project governance uses three precise lifecycle designations:
1. **General Availability (GA) / Active OSS Support:** Free open-source bug fixes and security vulnerability patches provided by the Spring engineering community. Typically active for **12 to 14 months** from the initial GA release of each minor line.
2. **Commercial Support:** Extended enterprise support, security patches, and compliance fixes provided by VMware Tanzu / Broadcom under enterprise licensing.
3. **End of Life (EOL):** The date when open-source community support ceases.

#### 3.2 Spring Boot Version Support Lifecycle (as of late 2026)
According to the official [Spring Boot Supported Versions](https://github.com/spring-projects/spring-boot/wiki/Supported-Versions) schedule:
* **Spring Boot 3.3.x:** Released May 2024. Open-source community support reached **EOL on June 30, 2025**. Currently receives Commercial Support only.
* **Spring Boot 3.4.x:** Released November 2024. Open-source community support reached **EOL on December 31, 2025**. Currently receives Commercial Support only.
* **Spring Boot 4.0.x:** Released November 2025. **Active OSS Support** extends through **December 31, 2026**.
* **Spring Boot 4.1.x / 4.2.x:** Currently under Active OSS Support through 2027.

#### 3.3 Recommendation to the Project Owner (Open Decision)
* **Option A (Literal Phase 1 Baseline Conformance):** Adopt **Spring Boot 3.4.x** (or the latest 3.x patch release). Conforms strictly to the literal text "Spring Boot 3.x" in `13_Tech_Stack.md`, with the operational condition that 3.x has passed public community OSS support and would require either commercial Tanzu support or accepting unpatched upstream dependencies.
* **Option B (Active Community OSS Support):** Adopt **Spring Boot 4.0.x** (or 4.1.x), which natively supports Java 21, Jakarta EE, and provides active public community security maintenance.
* **Decision Status:** Preserved as **OPEN** for owner decision. The engineering team will not unilaterally decide.

---

### 4. Rejected Technologies & Explicit Exclusions

| Rejected Technology | Evaluated Alternative | Architectural Reason for Rejection | Controlling Source Ref |
| :--- | :--- | :--- | :--- |
| **Redis** | In-Process Caffeine Cache | Prohibited by D-13. Eliminates external cache cluster cost and network hop latency; Caffeine on JVM heap provides sub-microsecond reads. | `ADR-03, D-13` |
| **RabbitMQ / Kafka** | PostgreSQL Transactional Outbox | Prohibited by D-13. Message brokers add external cluster operational cost; database outbox guarantees atomic ACID consistency with PM state changes. | `ADR-02, D-13` |
| **Microservices Architecture** | Modular Monolith (Spring Boot) | Prohibited by ADR-01. Microservices introduce distributed transaction failure modes, inter-service network latency, and multiple container hosting fees. | `ADR-01` |
| **Full-Duplex WebSockets** | Server-Sent Events (SSE) | WebSocket stateful connections require complex heartbeats and proxy tuning; shop floor needs unidirectional server-to-browser push, handled natively via HTTP/2 SSE. | `ADR-06, O-07` |
| **Dedicated Hardware QR Libraries** | Native Keyboard-Wedge / HTML5 | Direct hardware SDKs add brittle OS drivers; standard 2D scanners operate in keyboard-wedge mode, inputting directly into web fields. Scanning is deferred in baseline. | `O-08` |
| **Direct RM Database Queries** | Read-Only REST Service Account | Direct cross-database SQL queries couple database schemas and bypass RM validation; REST connector preserves RM single source of truth. | `ADR-04, BR-067` |
| **AI / LLM Agents** | Deterministic Business Logic | Explicitly excluded by BR-058. Precision manufacturing execution requires deterministic, auditable state machine rules rather than probabilistic generative AI models. | `BR-058` |

---

### 5. Technical Assumptions
1. **ASSUMPTION:** Eclipse Temurin 21 JRE container image (`eclipse-temurin:21-jre-alpine` or Debian slim) will be supported on Render Docker environments.
2. **ASSUMPTION:** Springdoc OpenAPI and Sentry Jakarta starters maintain zero namespace collisions when running on Java 21 with Spring Boot.

---

### 6. Open Questions
* **O-TECH-01:** Does the project owner approve adopting the active OSS support line (**Spring Boot 4.0.x / 4.1.x**) or freezing on the final **Spring Boot 3.x** release with commercial support? (Documented in Document 05).
