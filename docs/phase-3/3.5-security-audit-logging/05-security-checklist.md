# Part 3.5 — Security, RBAC, Audit and Logging Standards
## Document 05: Application Security Hardening and OWASP Verification Checklist

---

### 1. Confirmed Rules

#### 1.1 Input Validation & Parameter Sanitization
* **Jakarta Bean Validation:** All DTO request bodies are validated at the controller boundary using `@Valid`.
* **String Constraints:** High-impact domain identifiers (`serialNumber`, `scNumber`, `heatNumber`, `componentCode`) must match strict alphanumeric regular expressions (`@Pattern(regexp = "^[A-Za-z0-9_-]+$")`).
* **Measurement Limits:** Operator and QC measurement inputs are bounded by `@DecimalMin` and `@DecimalMax` to reject nonsensical negative or astronomical values.
* **Length Bounds:** All free-text comments, notes, and defect reasons must specify maximum character lengths (`@Size(max = 1000)`).

#### 1.2 Output Encoding & Injection Defense
* **XSS Prevention:** React JSX automatically encodes all variables bound in template expressions before rendering to the DOM.
* **Prohibited React APIs:** The use of `dangerouslySetInnerHTML` is strictly prohibited across the entire frontend codebase.
* **SQL Injection Prevention:** Spring Data JPA and Hibernate use parameterized queries for 100% of database access. Dynamic string-concatenated SQL or raw `EntityManager.createNativeQuery("..." + input)` is strictly forbidden.

#### 1.3 File Upload Security (Cloudflare R2 Flow)
* **Pre-Signed URL Gating:** Files are never streamed through the Spring Boot application server. Upload permissions are gated by `POST /api/v1/documents/presigned-upload`.
* **MIME Whitelist:** Strictly restricted to engineering assets: `application/pdf`, `image/jpeg`, `image/png`, `application/dxf`, `application/dwg`. Executable extensions (`.exe`, `.bat`, `.sh`, `.vbs`, `.js`) are strictly rejected.
* **Size Enforcement:** Maximum upload file size is capped at **50 MB** *(ASSUMPTION)*.
* **HeadObject Verification:** Before persisting file metadata in PostgreSQL, the backend issues an S3 `HeadObject` call against R2 to verify that the file actually exists and that its content length and MIME type match the initial signed request.

#### 1.4 Strict Cross-Origin Resource Sharing (CORS) Policy
* **Origin Whitelist:** CORS is configured to accept requests exclusively from designated frontend domain origins (e.g. `https://velan-production.onrender.com` in production; `http://localhost:5173` in local dev).
* **Prohibited Configurations:** Wildcard origins (`Access-Control-Allow-Origin: *`) combined with credentials are strictly forbidden.
* **Allowed Methods:** `GET, POST, PUT, PATCH, DELETE, OPTIONS`.
* **Allowed Headers:** `Authorization, Content-Type, X-Correlation-Id, Idempotency-Key, X-Requested-With`.
* **Credentials:** `allowCredentials: true` (enables `HttpOnly` refresh token cookies).

#### 1.5 HTTP Security Response Headers
Spring Security `SecurityFilterChain` enforces mandatory defense-in-depth HTTP response headers:

| Header | Prescriptive Value | Security Purpose |
|:---|:---|:---|
| **Content-Security-Policy (CSP)** | `default-src 'self'; script-src 'self'; img-src 'self' data: https://*.r2.cloudflarestorage.com; connect-src 'self' https://*.r2.cloudflarestorage.com; frame-ancestors 'none';` | Blocks unauthorized script injection and restricts external network connections. |
| **Strict-Transport-Security (HSTS)** | `max-age=31536000; includeSubDomains; preload` | Forces all communications over HTTPS for 1 year. |
| **X-Content-Type-Options** | `nosniff` | Prevents browser MIME-type sniffing. |
| **X-Frame-Options** | `DENY` | Prevents clickjacking by blocking iframe embedding. |
| **Referrer-Policy** | `strict-origin-when-cross-origin` | Limits referrer information leakage. |
| **Permissions-Policy** | `camera=(), microphone=(), geolocation=()` | Disables unused browser hardware capabilities. |

#### 1.6 Rate Limiting Architecture
Rate limiting protects the application against brute-force attacks and denial-of-service:
* **Implementation:** Filter-based in-memory token bucket algorithm using Bucket4j / Caffeine.
* **Authentication Endpoint (`/api/v1/auth/login`):** Maximum **5 requests per minute** per client IP address.
* **General API Endpoints:** Maximum **120 requests per minute** per authenticated user ID.
* **Document Export Endpoints:** Maximum **5 requests per hour** per user ID.
* **Throttling Response:** Returns `429 Too Many Requests` with `Retry-After: {seconds}` header.

#### 1.7 Secrets Management and Git Hygiene
* **Zero Hardcoded Secrets:** No passwords, database credentials, JWT signing keys, SMTP credentials, or R2 tokens may ever be committed to the Git repository.
* **Environment Variables:** All secrets are injected dynamically via Render environment variables.
* **Repository Hygiene:** `.gitignore` explicitly excludes `.env`, `*.pem`, `*.key`, `credentials.json`. A dummy `.env.example` file provides placeholder variable names only.

---

### 2. Proposed Design

#### 2.1 Excel & PDF Parsing Risk Mitigation
Processing binary files uploaded by users introduces memory exhaustion and XXE risks:
1. **Apache POI Zip Bomb Defense:**
   * Maliciously compressed Excel `.xlsx` files expand billions of times in memory.
   * Spring Boot configuration enforces inflation ratio limits:
     ```java
     ZipSecureFile.setMinInflateRatio(0.01); // Rejects ratios below 1%
     ```
2. **XML External Entity (XXE) Prevention:**
   * XML document builders used for Excel OpenXML parsing must disable external DTDs:
     ```java
     factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
     ```
3. **Macro Prohibition:**
   * Macro-enabled spreadsheets (`.xlsm`) are strictly rejected by MIME-type and extension validation.
4. **Apache PDFBox Hardening:**
   * Disables embedded JavaScript execution and external font downloading when inspecting uploaded PDF engineering drawings.

#### 2.2 Integration Security Boundaries (RM & ECR)
* **ECR Integration Secret Handling:**
  - Outbound communication to ECR requires an API key or mutual TLS certificate stored in Render environment variables (`ECR_API_KEY`).
  - All outbound calls to ECR are encrypted using **TLS 1.3**.
* **Least Privilege for RM (V-SMART):**
  - The RM service account utilized by Velan Production operates with **read-only privileges**.
  - Production possesses zero write credentials to RM databases or endpoints (BR-067).

---

### 3. Technical Assumptions
1. **ASSUMPTION:** The maximum single file size for Cloudflare R2 uploads is **50 MB**.
2. **ASSUMPTION:** Render terminates SSL/TLS at the load balancer and forwards traffic over internal HTTPS/HTTP with appropriate `X-Forwarded-Proto: https` headers.
3. **ASSUMPTION:** Automated dependency scanning is integrated into GitHub Actions CI via GitHub Dependabot or OWASP Dependency-Check.

---

### 4. Open Questions
* **O-SEC-01 (Antivirus File Scanning):** Should uploaded engineering drawings in Cloudflare R2 be asynchronously scanned by an external antivirus engine (e.g. ClamAV Lambda / Cloudflare Worker) before being marked approved for shop-floor viewing?
