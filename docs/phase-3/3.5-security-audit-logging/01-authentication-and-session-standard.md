# Part 3.5 — Security, RBAC, Audit and Logging Standards
## Document 01: Authentication, Credential Policies, and Session Management

---

### 1. Confirmed Rules

#### 1.1 Individual Employee Identity Standard
1. **Mandatory Individual Login (D-03):** Every employee across office and shop-floor roles (Admin, PM, Designer, Operator, QC, Management) must authenticate using an individual account.
2. **Strict Prohibition of Shared Logins:** Group, shift-wide, machine-wide, or station-wide generic user accounts (e.g. `operator_lathe_1`, `qc_shift_morning`) are strictly prohibited. Every piece operation start, measurement submission, QC evaluation, and PM disposition must be immutably attributed to a single authenticated person.

#### 1.2 Password Hashing Standard & Parameters
* **Recommended Algorithm:** **Argon2id** (winner of the Password Hashing Competition, RFC 9106), supported natively in Spring Security via BouncyCastle.
* **Technical Justification:**
  - Argon2id provides hybrid defense against both side-channel timing attacks (Argon2i) and GPU/ASIC brute-force cracking attacks (Argon2d) through tunable memory hardness.
  - Unlike legacy algorithms (SHA-256, MD5) which can be cracked at billions of hashes per second on consumer GPUs, Argon2id forces the hardware to allocate significant RAM per hash attempt.
* **Prescriptive Parameters (Spring Security `Argon2PasswordEncoder`):**
  - Salt length: `16 bytes` (128 bits, cryptographically random via `SecureRandom`).
  - Hash length: `32 bytes` (256 bits).
  - Memory cost ($m$): `65536 KiB` (64 MB per hash).
  - Iterations / Time cost ($t$): `3 iterations`.
  - Parallelism ($p$): `1 thread` (optimized for Render container CPU limits).
* **Secondary / Fallback Algorithm:** **BCrypt** with cost factor `12` (acceptable alternative if native C-library memory allocation is constrained).

#### 1.3 Failed Login Tracking and Account Lockout
1. **Failure Threshold:** 5 consecutive failed login attempts within a sliding 15-minute window locks the account.
2. **Lockout Duration:** The account is automatically locked for **15 minutes**.
3. **Lockout Escalation:** If 3 consecutive 15-minute lockouts occur within 24 hours, the account transitions to `ADMIN_LOCKED`, requiring manual unlocking by an Administrator.
4. **Audit Requirement:** Every failed attempt and account lockout is written immediately to `audit_logs` with source IP, attempted username, and timestamp.

#### 1.4 Password Reset via SMTP
1. **Trigger:** User initiates "Forgot Password" or Admin triggers "Force Password Reset".
2. **Reset Token Lifecycle:**
   - Token is a cryptographically secure 256-bit random string (`SecureRandom`), base64url-encoded.
   - Stored in the database as an SHA-256 hash alongside an `expires_at` timestamp.
   - **Expiration Window:** Strictly **15 minutes**.
   - Single-use: once consumed, the record is immediately invalidated.
3. **Delivery Mechanism:** Outbound email sent via Spring Boot JavaMailSender (SMTP) using an HTML template with a one-time activation link. Password reset links must never include the user's password or sensitive identity tokens.

#### 1.5 Account Suspension & The PM Reassignment Rule
1. **Immediate Suspension:** Admin can suspend any user account immediately via `POST /api/v1/admin/users/{id}/actions/suspend`.
   - All active JWT refresh tokens for the user are immediately revoked in the database.
   - Any active SSE connection for the user is severed.
   - Cached user permissions in Caffeine are evicted immediately.
2. **Scheduled Suspension and Reactivation:**
   - Admin can schedule suspension (e.g. planned leave) by setting `suspended_from` and `suspended_to` timestamps. A scheduled job evaluates active statuses hourly.
3. **The PM Reassignment Rule (06_Role_Matrix §3):**
   - When a Project Manager (PM) is suspended, the system strictly blocks the suspension action until the Admin explicitly reassigns that PM's active project/SC oversight to an active PM.
   - Incomplete quantities or unassigned tasks at shop-floor stations are returned to the shared worklist.
   - Historical measurements, approvals, and previous dispositions made by the suspended PM remain immutable and unaltered.
   - The reassignment event is logged in `audit_logs` with `REASSIGNMENT` action code and PM IDs.

---

### 2. Proposed Design

#### 2.1 Token Strategy & Session Management

| Strategy Dimension | Selected Standard | Architectural Rationale |
|:---|:---|:---|
| **Access Token** | JWT (Stateless, signed with HMAC-SHA256 / RS256) | Contains `userId`, `username`, roles, and permissions. Allows micro-second authorization checks on API requests without hitting Neon DB on every call. |
| **Access Token Expiry** | **15 minutes** | Limits the exposure window if an access token is intercepted. |
| **Refresh Token** | Opaque random UUIDv4 string stored in DB | Allows immediate server-side revocation on logout, password change, or suspension. |
| **Refresh Token Expiry**| **7 days** (sliding window with rotation) | Balances user convenience with security. |
| **Browser Storage** | Access Token in **React memory**;<br/>Refresh Token in **`HttpOnly; Secure; SameSite=Strict` Cookie** | Protects refresh token from XSS script theft; keeps access token transient in memory. |
| **Token Rotation** | Refresh Token Rotation (RTR) on every `/auth/refresh` | Old refresh token is invalidated; a new refresh token is issued. If an invalidated refresh token is reused, all active tokens for that user are immediately revoked (theft detection). |
| **CSRF Defense** | Custom Header + `SameSite=Strict` | Browser cookies set to `SameSite=Strict` are not sent on cross-site requests. API requests require custom header `X-Requested-With: XMLHttpRequest` which browser CORS policies block cross-origin. |
| **Logout Protocol** | `POST /api/v1/auth/logout` | Revokes the refresh token in the database, clears the `HttpOnly` cookie with `Max-Age=0`, and terminates the client session. |

---

### 3. Technical Assumptions
1. **ASSUMPTION:** Spring Security 6.4 `Argon2PasswordEncoder` runs efficiently within Render web service container limits (minimum 512 MB RAM allocation).
2. **ASSUMPTION:** Inbound client IP addresses are extracted accurately from the `X-Forwarded-For` header set by Render reverse proxies.
3. **ASSUMPTION:** SMTP credentials for transactional emails are injected securely via environment variables (`SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`).

---

### 4. Open Questions
* **O-09 (Shop-Floor Credential Policy — OPEN):** What is the confirmed credential type and minimum length for shop-floor roles (Operator, QC)?
  - *Option A:* Standard username + strong password (minimum 10 characters, mixed case, numbers).
  - *Option B:* Employee ID + 6-digit numeric PIN for rugged touch terminals.
  - *Option C:* Barcode/NFC badge tap + 4-digit verification PIN.
  *(This policy remains formally OPEN per Decision Log O-09).*
