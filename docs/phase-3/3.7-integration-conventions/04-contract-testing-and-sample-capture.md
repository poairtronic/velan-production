# Part 3.7 — Integration Adapter Conventions
## Document 04: Contract Testing Standards, Sample Capture Protocol, and the VERIFY Register

---

### 1. Confirmed Rules

#### 1.1 Safe Sample Capture Protocol and Data Sanitization
To enable realistic contract testing without violating security policies or leaking commercial data:
1. **Live Payload Capture:** During Phase 13 (RM Connector) and Phase 14 (ECR Outbox), engineers capture raw JSON responses from live staging environments using `curl` or Postman with service account credentials.
2. **Mandatory Redaction Rules:** Before any recorded JSON payload is committed to source control:
   * **Secrets & Tokens:** All `Authorization: Bearer` tokens, API keys, passwords, and session cookies are replaced with `REDACTED_SECRET`.
   * **Commercial Pricing:** Any financial rates, raw material costs, or invoice figures must be masked or set to `0.00`.
   * **Personal Data (PII):** Employee phone numbers, personal email addresses, or home addresses must be sanitized to synthetic dummy data (`john.doe@example.com`).
   * **Traceability Numbers:** Real order numbers and serials are replaced with standardized synthetic values (`SC-TEST-1001`, `V-TEST-001`).
3. **Storage Location:** Sanitized payload files are stored in the test resource directory:
   * `backend/src/test/resources/integration-samples/rm/`
   * `backend/src/test/resources/integration-samples/ecr/`

#### 1.2 Automated Contract Testing with WireMock
* **Tool:** **WireMock** (`org.wiremock:wiremock-standalone` or `com.marcosbarbero.cloud:spring-cloud-contract-wiremock`).
* **Test Architecture:**
  - Contract integration tests spin up an in-process WireMock HTTP server on a dynamic port.
  - WireMock stubs serve recorded, sanitized JSON sample files matching the audited endpoints.
  - Tests verify that:
    1. Adapter classes (`RmClient`, `EcrClient`) serialize requests matching external expectations.
    2. Jackson object mappers deserialize external payloads into DTOs without throwing `UnrecognizedPropertyException`.
    3. Translators (`RmContractTranslator`) map DTOs into domain objects correctly.
    4. Timeouts, HTTP 5xx responses, and connection drops trigger expected domain exceptions.

---

### 2. The Master VERIFY Register

Every external endpoint, header, and data property is classified as **`CONFIRMED`** (audited from live systems in `09_Integration_Specification.md`) or **`VERIFY`** (requires verification against live HTTP response JSON during implementation):

| System | Item / Field / Path | Audited Location / Reference | Status | Verification Task & Target Phase |
|:---|:---|:---|:---:|:---|
| **ECR** | `POST /api/internal/rm-intake` | `09_Integration_Specification.md:32` | **CONFIRMED** | Baseline confirmed. |
| **ECR** | Header `x-internal-secret` | `09_Integration_Specification.md:32` | **CONFIRMED** | Baseline confirmed. |
| **ECR** | Header `x-idempotency-key` | `09_Integration_Specification.md:32` | **CONFIRMED** | Baseline confirmed. |
| **ECR** | Body field `type` (`REWORK`, `REJECTION`)| `09_Integration_Specification.md:33` | **CONFIRMED** | Baseline confirmed. |
| **ECR** | Body fields `scNumber`, `poNumber`, `component`, `quantityAffected`, `defectDescription`, `raisedByName` | `09_Integration_Specification.md:33` | **CONFIRMED** | Baseline confirmed. |
| **ECR** | Response `{ reportId, reportNumber }` | `09_Integration_Specification.md:36` | **CONFIRMED** | Baseline confirmed. |
| **ECR** | Report number format `AGIPL-YYYY-ERR-XXXXX` | `09_Integration_Specification.md:29` | **CONFIRMED** | Baseline confirmed. |
| **RM** | Endpoints `/api/sc`, `/api/sc/:id`, `/api/sc/traceability/:poNumber` | `09_Integration_Specification.md:15` | **CONFIRMED** | Baseline confirmed. |
| **RM** | Endpoints `/api/rm`, `/api/rm/:id`, `/api/rm/stores/queue` | `09_Integration_Specification.md:15` | **CONFIRMED** | Baseline confirmed. |
| **RM** | Endpoints `/api/material-issues`, `/api/material-issues/:id` | `09_Integration_Specification.md:15` | **CONFIRMED** | Baseline confirmed. |
| **RM** | Endpoints `/api/delivery-challans`, `/api/delivery-challans/:id`, `/api/delivery-challans/movements` | `09_Integration_Specification.md:15` | **CONFIRMED** | Baseline confirmed. |
| **RM** | Endpoint `/api/ecr-issues` | `09_Integration_Specification.md:15` | **CONFIRMED** | Baseline confirmed. |
| **RM** | DC Filter `type = 'PRODUCTION_PROCESS_OUTWARD'` | `09_Integration_Specification.md:27` | **CONFIRMED** | Baseline confirmed. |
| **RM** | DC Line missing `rm_item_id`; `part_number` overwritten with SC | `09_Integration_Specification.md:28` | **CONFIRMED** | Baseline confirmed. |
| **RM** | `ecr_material_pending.status = 'ISSUED'` | `09_Integration_Specification.md:30` | **CONFIRMED** | Baseline confirmed. |
| **RM** | Exact JSON casing (camelCase vs snake_case) on `/api/sc` | `09_Integration_Specification.md:7` | **VERIFY** | Phase 13 Task 13.1 (Live Sample Capture) |
| **RM** | `sales_order_components.status` enumeration values | `09_Integration_Specification.md:20` | **VERIFY** | Phase 13 Task 13.1 (Live Sample Capture) |
| **RM** | `purchase_orders.reference_date` interpretation | `09_Integration_Specification.md:21` | **VERIFY** | Phase 13 Task 13.2 / Open O-01 |
| **RM** | `customers.code` and `name` JSON property names | `09_Integration_Specification.md:22` | **VERIFY** | Phase 13 Task 13.1 (Live Sample Capture) |
| **RM** | `rm_requests.status` enumeration values | `09_Integration_Specification.md:23` | **VERIFY** | Phase 13 Task 13.1 (Live Sample Capture) |
| **RM** | `material_issue_items` JSON response wrapper shape | `09_Integration_Specification.md:26` | **VERIFY** | Phase 22 Task 22.1 (Material Gate Testing) |
| **RM** | `delivery_challan_items` JSON property names | `09_Integration_Specification.md:28` | **VERIFY** | Phase 35 Task 35.1 (Vendor DC Testing) |
| **RM** | `ecr_issues` JSON response property names | `09_Integration_Specification.md:30` | **VERIFY** | Phase 32 Task 32.1 (Replacement Testing) |

---

### 3. Proposed Design

#### 3.1 Sample WireMock Contract Test Case (JUnit 5)

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@AutoConfigureWireMock(port = 0)
class RmConnectorContractTest {

    @Autowired
    private RmClient rmClient;

    @Test
    void shouldParseApprovedSalesContractSample() {
        stubFor(get(urlPathEqualTo("/api/sc/1024"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBodyFile("integration-samples/rm/sc-1024-response.json")));

        RmSalesContractDTO response = rmClient.getSalesContractById("1024");
        
        assertThat(response.scNumber()).isEqualTo("SC-1024");
        assertThat(response.targetQuantity()).isEqualTo(50);
        assertThat(response.status()).isEqualTo("IN_PRODUCTION");
    }
}
```

---

### 4. Technical Assumptions
1. **ASSUMPTION:** Staging environments for RM and ECR are accessible from the developer network or via recorded WireMock stubs during CI build execution.
2. **ASSUMPTION:** Jackson `FAIL_ON_UNKNOWN_PROPERTIES = false` is configured on integration object mappers, allowing the application to absorb non-breaking additive fields emitted by RM or ECR without failure.

---

### 5. Open Questions
* **O-INT-01 (WireMock Stub Packaging):** Should recorded WireMock JSON stubs be packaged in a shared test-jar library for cross-module integration tests?
