# Part 3.1 — Technology Specification and Environments
## Document 03: Environment Variables and Configuration Specification

---

### 1. Confirmed Rules (Frozen Baseline)
* **Zero Hardcoded Secrets (`BR-054, D-13`):** Passwords, API tokens, JWT keys, and integration secrets must never be committed to Git, embedded in code, or written to build artifacts.
* **Environment Sourcing:** Configuration values must be injected exclusively via system environment variables configured in the Render service management console.
* **Zero Secret Logging:** Tokens, passwords, pre-signed URLs, and external headers (e.g., `x-internal-secret`) must never be logged to stdout, files, or Sentry breadcrumbs.
* **Feature Flag Defaults:** `RM_SYNC_ENABLED` and `ECR_SEND_ENABLED` must default to **`false`** to ensure safe, disconnected local and test execution.
* **Standard SMTP Baseline (`D-13`):** Email notifications in baseline are governed exclusively by standard SMTP. Hosted email APIs (e.g., Resend, SendGrid) are deferred to post-go-live.

---

### 2. Comprehensive Environment Variable Specification Table

| Variable Name | Description & Functional Purpose | Data Type | Default Value | Req? | Dev / Test / Prod Notes |
| :--- | :--- | :--- | :--- | :---: | :--- |
| **`SERVER_PORT`** | HTTP listening port for Spring Boot container | Integer | `8080` | Yes | Fixed to `8080` for Render Docker container binding. |
| **`SPRING_PROFILES_ACTIVE`** | Active Spring profile (`dev`, `test`, `prod`) | String | `dev` | Yes | Controls logging verbosity and mock vs live adapters. |
| **`SPRING_DATASOURCE_URL`** | Neon PostgreSQL **Pooled** JDBC URL | String (JDBC) | `jdbc:postgresql://localhost:5432/velan_dev` | Yes | Prod uses `-pooler` hostname on port 5432 (PgBouncer). |
| **`SPRING_DATASOURCE_USERNAME`** | Database user for application runtime queries | String | `postgres` | Yes | Dedicated role with restricted DDL permissions in prod. |
| **`SPRING_DATASOURCE_PASSWORD`** | Database password for application runtime queries | String | `postgres` | Yes | Injected securely via Render secret environment variable. |
| **`SPRING_DATASOURCE_HIKARI_MAX_POOL`** | Maximum connections in HikariCP pool | Integer | `10` | No | Sized to 10–20 per container (ASSUMPTION: pool sizing). |
| **`SPRING_FLYWAY_URL`** | Neon PostgreSQL **Direct (Unpooled)** JDBC URL | String (JDBC) | `${SPRING_DATASOURCE_URL}` | Yes | **Bypasses PgBouncer** (direct compute on port 5432). |
| **`SPRING_FLYWAY_USER`** | Database user for Flyway migrations | String | `${SPRING_DATASOURCE_USERNAME}` | Yes | Requires DDL permissions to create/alter tables and indexes. |
| **`SPRING_FLYWAY_PASSWORD`** | Database password for Flyway migrations | String | `${SPRING_DATASOURCE_PASSWORD}` | Yes | Injected securely via Render secret environment variable. |
| **`SECURITY_JWT_SECRET`** | HMAC-SHA512 key for JWT signing & verification | String | `min-64-char-dev-secret-key-for-local-development-only-replace-in-prod` | Yes | Cryptographically random string (min 64 chars) in prod. |
| **`SECURITY_JWT_ACCESS_EXPIRATION_MIN`** | Access token lifespan in minutes | Integer | `60` | No | ASSUMPTION: 60 minutes for shop-floor shift workers. |
| **`SECURITY_JWT_REFRESH_EXPIRATION_DAYS`**| Refresh token lifespan in days | Integer | `7` | No | ASSUMPTION: 7-day refresh token for persistent terminals. |
| **`CORS_ALLOWED_ORIGINS`** | Comma-separated list of allowed frontend origins | String | `http://localhost:5173` | Yes | Set to `https://production.velan.internal` in production. |
| **`CLOUDFLARE_R2_ACCOUNT_ID`** | Cloudflare account identifier | String | `unverified-dev-account` | Yes | Extracted from Cloudflare dashboard for S3 endpoint URL. |
| **`CLOUDFLARE_R2_ACCESS_KEY_ID`** | S3-compatible API access key for R2 | String | `unverified-dev-key` | Yes | Configured as secret in Render console. |
| **`CLOUDFLARE_R2_SECRET_ACCESS_KEY`** | S3-compatible API secret key for R2 | String | `unverified-dev-secret` | Yes | Configured as secret in Render console. |
| **`CLOUDFLARE_R2_BUCKET_NAME`** | Name of the environment-isolated R2 bucket | String | `velan-r2-dev` | Yes | `velan-r2-dev` (dev), `velan-r2-test` (test), `velan-r2-prod` (prod). |
| **`CLOUDFLARE_R2_PRESIGNED_EXPIRATION_MIN`**| Pre-signed PUT/GET URL lifetime in minutes | Integer | `15` | No | Fixed to 15 minutes to prevent stale link hijacking. |
| **`SPRING_MAIL_HOST`** | SMTP server hostname | String | `localhost` | Yes | Corporate SMTP gateway in prod; Mailhog/Ethereal in dev/test. |
| **`SPRING_MAIL_PORT`** | SMTP server port | Integer | `1025` | Yes | Port 587 (STARTTLS) or 465 (SSL) in production; 1025 in dev. |
| **`SPRING_MAIL_USERNAME`** | SMTP authentication username | String | `none` | No | Blank if unauthenticated internal relay. |
| **`SPRING_MAIL_PASSWORD`** | SMTP authentication password | String | `none` | No | Injected via Render secret environment variable. |
| **`SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH`** | Enable SMTP authentication | Boolean | `false` | No | Set to `true` when username/password credentials required. |
| **`SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE`** | Enable STARTTLS command | Boolean | `false` | No | Set to `true` for port 587 STARTTLS encryption. |
| **`APP_MAIL_FROM_ADDRESS`** | Sender email address for automated notifications | String | `notifications@velan.internal` | Yes | Must be authorized on enterprise SPF/DKIM records. |
| **`RM_API_BASE_URL`** | Base URL for Raw Material (V-SMART) REST APIs | String (URL) | `http://localhost:8081` | Yes | Internal corporate HTTPS URL in prod (e.g. `https://vsmart.velan.internal`). |
| **`RM_SERVICE_ACCOUNT_TOKEN`** | Bearer JWT for read-only RM service account | String | `mock-rm-service-token` | Yes | Injected via Render secret; rotated semi-annually. |
| **`RM_SYNC_ENABLED`** | Feature flag to enable/disable automated RM sync | Boolean | `false` | Yes | **Defaults to `false`**; toggle to `true` when RM connector is live. |
| **`RM_POLLING_INTERVAL_CRON`** | Cron schedule for RM requisition & DC sync | String | `0 */5 * * * *` | No | ASSUMPTION: Every 5 minutes during operational hours. |
| **`ECR_API_BASE_URL`** | Base URL for Engineering Change Request intake | String (URL) | `http://localhost:8082` | Yes | HTTPS intake endpoint URL in prod (e.g. `https://ecr.velan.internal`). |
| **`ECR_INTERNAL_SECRET`** | Shared secret header (`x-internal-secret`) value | String | `mock-ecr-shared-secret` | Yes | Injected via Render secret; verified by ECR intake controller. |
| **`ECR_SEND_ENABLED`** | Feature flag to enable/disable outbox ECR posts | Boolean | `false` | Yes | **Defaults to `false`**; toggle to `true` when ECR intake is active. |
| **`OUTBOX_POLL_DELAY_MS`** | Polling frequency for `outbox_events` table (ms) | Long | `10000` | No | ASSUMPTION: 10 seconds fixed delay between poller passes. |
| **`OUTBOX_BATCH_SIZE`** | Maximum outbox events processed per poll loop | Integer | `20` | No | ASSUMPTION: 20 events per batch to bound memory. |
| **`OUTBOX_MAX_RETRIES`** | Maximum attempts before event moves to DEAD_LETTER| Integer | `5` | No | Bounded retry with exponential backoff (ADR-02). |
| **`CACHE_TTL_PROCESS_TEMPLATES_MIN`** | Caffeine cache TTL for process templates | Long | `60` | No | ASSUMPTION: 60 minutes TTL; evicted on edit. |
| **`CACHE_TTL_TOLERANCES_MIN`** | Caffeine cache TTL for active tolerances | Long | `30` | No | ASSUMPTION: 30 minutes TTL; short-lived. |
| **`CACHE_TTL_PERMISSIONS_MIN`** | Caffeine cache TTL for user roles & grants | Long | `15` | No | ASSUMPTION: 15 minutes TTL for prompt role updates. |
| **`SENTRY_DSN`** | Sentry project Data Source Name for error alerts | String | `none` | No | Blank in dev; configured in test and prod. |
| **`LOGGING_LEVEL_ROOT`** | Root log level | String | `INFO` | No | `INFO` in prod; `DEBUG` in dev for local troubleshooting. |
| **`LOGGING_LEVEL_APP`** | Application package log level (`com.velan`) | String | `DEBUG` | No | `INFO` in prod; `DEBUG` in dev/test. |
| **`LOGGING_LEVEL_SQL`** | Hibernate SQL query log level | String | `WARN` | No | Set to `WARN` in prod to prevent log flooding and secret leakage. |

---

### 3. Proposed Design: Reference `.env.example` Specification

The following block defines the structural specification for the environment configuration file:

```bash
# ==============================================================================
# VELAN PRODUCTION — ENVIRONMENT CONFIGURATION SPECIFICATION (.env.example)
# Note: Physical template created in Phase 6. Never commit live production credentials.
# ==============================================================================

# --- Container & Profile ---
SERVER_PORT=8080
SPRING_PROFILES_ACTIVE=dev

# --- Database: Neon PostgreSQL ---
# Runtime traffic uses Pooled connection (PgBouncer, Port 5432)
SPRING_DATASOURCE_URL=jdbc:postgresql://ep-example-pooler.us-east-2.aws.neon.tech:5432/velan_dev?sslmode=require
SPRING_DATASOURCE_USERNAME=velan_app_user
SPRING_DATASOURCE_PASSWORD=replace_with_secure_neon_password
SPRING_DATASOURCE_HIKARI_MAX_POOL=10

# Schema Migrations must use Direct compute (Port 5432, Advisory Lock requirement)
SPRING_FLYWAY_URL=jdbc:postgresql://ep-example.us-east-2.aws.neon.tech:5432/velan_dev?sslmode=require
SPRING_FLYWAY_USER=velan_app_user
SPRING_FLYWAY_PASSWORD=replace_with_secure_neon_password

# --- Security & JWT ---
SECURITY_JWT_SECRET=replace_with_cryptographically_random_64_character_hex_secret_key
SECURITY_JWT_ACCESS_EXPIRATION_MIN=60
SECURITY_JWT_REFRESH_EXPIRATION_DAYS=7
CORS_ALLOWED_ORIGINS=http://localhost:5173,https://velan-fe-test.onrender.com

# --- Object Storage: Cloudflare R2 ---
CLOUDFLARE_R2_ACCOUNT_ID=replace_with_cloudflare_account_id
CLOUDFLARE_R2_ACCESS_KEY_ID=replace_with_r2_access_key
CLOUDFLARE_R2_SECRET_ACCESS_KEY=replace_with_r2_secret_key
CLOUDFLARE_R2_BUCKET_NAME=velan-r2-dev
CLOUDFLARE_R2_PRESIGNED_EXPIRATION_MIN=15

# --- Standard SMTP Configuration (D-13 Baseline) ---
SPRING_MAIL_HOST=smtp.velan.internal
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=notifications@velan.internal
SPRING_MAIL_PASSWORD=replace_with_smtp_password
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true
APP_MAIL_FROM_ADDRESS=notifications@velan.internal

# --- RM System (V-SMART) Integration ---
RM_API_BASE_URL=https://vsmart.velan.internal
RM_SERVICE_ACCOUNT_TOKEN=replace_with_read_only_service_account_jwt
RM_SYNC_ENABLED=false
RM_POLLING_INTERVAL_CRON=0 */5 * * * *

# --- ECR System Integration ---
ECR_API_BASE_URL=https://ecr.velan.internal
ECR_INTERNAL_SECRET=replace_with_shared_secret_matching_ecr_header
ECR_SEND_ENABLED=false

# --- Outbox & Async Settings ---
OUTBOX_POLL_DELAY_MS=10000
OUTBOX_BATCH_SIZE=20
OUTBOX_MAX_RETRIES=5

# --- In-Process Caffeine Cache TTLs (Minutes) ---
CACHE_TTL_PROCESS_TEMPLATES_MIN=60
CACHE_TTL_TOLERANCES_MIN=30
CACHE_TTL_PERMISSIONS_MIN=15

# --- Observability & Logging ---
SENTRY_DSN=https://examplePublicKey@o0.ingest.sentry.io/0
LOGGING_LEVEL_ROOT=INFO
LOGGING_LEVEL_APP=DEBUG
LOGGING_LEVEL_SQL=WARN
```

---

### 4. Future Hosted Email API Configuration (Deferred — Post-Go-Live per D-13)

Per Decision `D-13`, hosted third-party email API integrations (e.g., Resend, SendGrid) are explicitly deferred until post-go-live evaluation. If a future architectural review approves transitioning from standard SMTP to a hosted provider, the following configuration variables are reserved for subsequent integration:

| Variable Name | Description & Reserved Purpose | Status | Target Phase |
| :--- | :--- | :---: | :--- |
| **`EMAIL_PROVIDER`** | Email delivery driver toggle (`SMTP` vs `RESEND` vs `SENDGRID`) | DEFERRED | Future Revision |
| **`RESEND_API_KEY`** | Bearer API authentication token for Resend HTTP REST API | DEFERRED | Future Revision |
| **`RESEND_API_BASE_URL`** | REST endpoint base URL (`https://api.resend.com`) | DEFERRED | Future Revision |

---

### 5. Technical Assumptions
1. **ASSUMPTION:** Spring Boot Actuator `/actuator/env` will be configured with `management.endpoint.env.keys-to-sanitize=*password*,*secret*,*key*,*token*` to prevent credential exposure in inspection endpoints.
2. **ASSUMPTION:** `sslmode=require` is universally mandatory across both Direct and Pooled Neon endpoints.
3. **ASSUMPTION:** Outbox poller and Caffeine cache TTL durations are proposed operational defaults subject to tuning under factory production load.

---

### 6. Open Questions
* **O-CFG-01:** Will corporate SMTP in production require username/password authentication or IP-whitelisted relay without credentials? (Documented in Document 05).
