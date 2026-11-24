# Part 2.6 – SC and Completion State Machine
## Document 04: Completion Calculation Engine and State Transition Matrices

---

### 1. Executive Summary & Algorithmic Rigor

In the Velan Production platform, state transitions and completion metrics are evaluated by deterministic calculation engines implemented within the modular monolith's domain services (`OrderLifecycleService`, `SetCompletionService`, and `DispatchCoordinationService`).

To guarantee absolute database consistency, prevent phantom completions, and eliminate race conditions on the shop floor:
* All state transitions follow explicit, mathematically verifiable rules.
* Parent entity states are derived deterministically from child entity states.
* Database mutations are protected by **optimistic concurrency control (`@Version`)** and selective pessimistic row-level locking (`SELECT ... FOR UPDATE`) during atomic set aggregation and dock release.

---

### 2. State Derivation and Completion Algorithms

#### 2.1 Assembled Set Status Calculation Engine
Whenever any constituent physical piece finishes an operation or reaches terminal status, the `SetCompletionService` evaluates the parent set's state:

```
Algorithm 1: EvaluateSetStatus(Set S)
Input: S (Instance of assembled_sets)
Output: Updated status of S

1. Let P_all = Query all pieces WHERE assembled_set_id = S.id AND status != 'SCRAPPED'
2. Let TotalRequired = SUM(bom_lines.quantity_per_set for S.sc_product_id)
3. Let CompletedCount = COUNT(p IN P_all WHERE p.status = 'COMPLETED')

4. IF CompletedCount == TotalRequired THEN
5.     IF S.is_production_complete == false THEN
6.         SET S.is_production_complete = true
7.         SET S.production_completed_at = NOW()
8.         SET S.status = 'PRODUCTION_COMPLETE'
9.         EmitEvent(SET_PRODUCTION_COMPLETED, S.id)
10.    END IF
11. ELSE
12.    SET S.is_production_complete = false
13.    SET S.status = 'BUILDING'
14. END IF

15. Save(S)
16. Trigger: EvaluateSCProductStatus(S.sc_product_id)
```

#### 2.2 SC Product Line Status Calculation Engine
Whenever an individual set changes state (e.g., passes Final QC, completes packing, or dispatches):

```
Algorithm 2: EvaluateSCProductStatus(ProductLineId L_id)
Input: L_id (UUID of subcontract_products)
Output: Updated status and counters of subcontract_products

1. Let Sets = Query all assembled_sets WHERE sc_product_id = L_id
2. Let TargetQty = subcontract_products.target_set_qty

3. Let CompletedSets = COUNT(s IN Sets WHERE s.is_final_qc_passed == true)
4. Let PackedSets    = COUNT(s IN Sets WHERE s.is_packed == true)
5. Let DeliveredSets = COUNT(s IN Sets WHERE s.status == 'DELIVERED')

6. UPDATE subcontract_products SET
       completed_set_qty  = CompletedSets,
       packed_set_qty     = PackedSets,
       dispatched_set_qty = DeliveredSets

7. IF DeliveredSets == TargetQty THEN
8.     SET subcontract_products.status = 'DELIVERED'
9. ELSE IF DeliveredSets > 0 THEN
10.    SET subcontract_products.status = 'PARTIALLY_DELIVERED'
11. ELSE IF PackedSets == TargetQty THEN
12.    SET subcontract_products.status = 'DISPATCH_PENDING'
13. ELSE IF CompletedSets == TargetQty THEN
14.    SET subcontract_products.status = 'PRODUCTION_COMPLETE'
15. ELSE IF ANY(s IN Sets WHERE s.status != 'BUILDING') OR subcontract_products.material_ready == true THEN
16.    SET subcontract_products.status = 'IN_PRODUCTION'
17. ELSE
18.    SET subcontract_products.status = 'PLANNED'
19. END IF

20. Trigger: EvaluateSCHeaderStatus(subcontract_products.sc_id)
```

#### 2.3 SC Header Status Calculation Engine
Whenever any product line item changes status:

```
Algorithm 3: EvaluateSCHeaderStatus(SC_Id sc_id)
Input: sc_id (UUID of subcontracts)
Output: Updated status of subcontracts header

1. Let Lines = Query all subcontract_products WHERE sc_id = sc_id
2. Let TotalLines = COUNT(Lines)

3. Let DeliveredLines = COUNT(l IN Lines WHERE l.status == 'DELIVERED')
4. Let PartiallyDeliveredLines = COUNT(l IN Lines WHERE l.status == 'PARTIALLY_DELIVERED' OR l.dispatched_set_qty > 0)
5. Let ProdCompleteLines = COUNT(l IN Lines WHERE l.status IN ('PRODUCTION_COMPLETE', 'DISPATCH_PENDING', 'DELIVERED'))

6. IF DeliveredLines == TotalLines THEN
7.     SET subcontracts.status = 'DELIVERED'
8.     EmitEvent(SC_FULLY_DELIVERED, sc_id)
9. ELSE IF PartiallyDeliveredLines > 0 OR DeliveredLines > 0 THEN
10.    SET subcontracts.status = 'PARTIALLY_DELIVERED'
11.    EmitEvent(SC_PARTIALLY_DELIVERED, sc_id)
12. ELSE IF ProdCompleteLines == TotalLines THEN
13.    SET subcontracts.status = 'PRODUCTION_COMPLETE'
14.    EmitEvent(SC_PRODUCTION_COMPLETED, sc_id)
15. ELSE IF ANY(l IN Lines WHERE l.status == 'IN_PRODUCTION') THEN
16.    SET subcontracts.status = 'IN_PRODUCTION'
17. ELSE
18.    SET subcontracts.status = 'ACTIVE'
19. END IF
```

---

### 3. Comprehensive State Transition Matrices

#### 3.1 Transition Matrix 1: Sales Contract (SC) Header Lifecycle (`subcontracts`)

| Transition ID | Source State | Target State | Triggering Event | Actor / Role | Required Validations & Guard Conditions | Resulting Database Changes & Emitted Events |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TR-SC-01** | *None* | `DRAFT_INTAKE` | Manual draft creation or Excel import | `ROLE_PM`, `ROLE_ADMIN` | PO number valid; target product in catalog; quantity $> 0$. | Inserts row in `subcontracts` with `status = 'DRAFT_INTAKE'`. |
| **TR-SC-02** | *None* | `ACTIVE` | Automated RM requisition synchronization | System (`ROLE_RM_CONNECTOR`) | Valid RM requisition payload; approved BOM and routing templates exist. | Inserts `subcontracts` with `status = 'ACTIVE'`. Generates sets, pieces, operations. Emits `SC_ACTIVATED`. |
| **TR-SC-03** | `DRAFT_INTAKE` | `ACTIVE` | PM clicks "Activate Contract" | `ROLE_PM`, `ROLE_ADMIN` | All drawing documents attached; approved BOM verified. | Sets `status = 'ACTIVE'`. Generates sets, pieces, and operations. Emits `SC_ACTIVATED`. |
| **TR-SC-04** | `ACTIVE` | `IN_PRODUCTION` | First piece operation opened on shop floor | System (Automated) | Preceding state `ACTIVE`; at least one product has `material_ready = true`. | Updates `status = 'IN_PRODUCTION'`. Emits `SC_IN_PRODUCTION`. |
| **TR-SC-05** | `IN_PRODUCTION` | `PRODUCTION_COMPLETE` | Final remaining set passes Final QC | System (Automated) | 100% of sets across all lines have `is_final_qc_passed = true`. Zero unresolved holds. | Updates `status = 'PRODUCTION_COMPLETE'`. Emits `SC_PRODUCTION_COMPLETED`. |
| **TR-SC-06** | `IN_PRODUCTION` | `PARTIALLY_DELIVERED`| First dispatch confirmation recorded | System (Automated) | At least 1 set delivered; at least 1 set remaining in production or staging. | Updates `status = 'PARTIALLY_DELIVERED'`. Emits `SC_PARTIALLY_DELIVERED`. |
| **TR-SC-07** | `PRODUCTION_COMPLETE` | `PARTIALLY_DELIVERED`| First dispatch confirmation recorded | System (Automated) | At least 1 set delivered; remaining sets packed or in staging. | Updates `status = 'PARTIALLY_DELIVERED'`. Emits `SC_PARTIALLY_DELIVERED`. |
| **TR-SC-08** | `PARTIALLY_DELIVERED`| `DELIVERED` | Final dispatch confirmation recorded | System (Automated) | 100% of ordered sets across all lines have `assembled_sets.status = 'DELIVERED'`. | Updates `status = 'DELIVERED'`. Emits `SC_FULLY_DELIVERED`. Closed order. |
| **TR-SC-09** | `PRODUCTION_COMPLETE` | `DELIVERED` | Consignment containing all sets dispatched | System (Automated) | 100% of ordered sets dispatched in a single shipment batch. | Updates `status = 'DELIVERED'`. Emits `SC_FULLY_DELIVERED`. |
| **TR-SC-10** | `DRAFT_INTAKE`, `ACTIVE`, `IN_PRODUCTION`, `PARTIALLY_DELIVERED` | `CANCELLED` | Admin executes contract cancellation | `ROLE_ADMIN` exclusively | Mandatory reason ($> 10$ chars); SC is not already `DELIVERED` (`BR-050`). | Sets `status = 'CANCELLED'`. Retires incomplete pieces/sets. Emits `SC_CANCELLED`. |

---

#### 3.2 Transition Matrix 2: Assembled Set Lifecycle (`assembled_sets`)

| Transition ID | Source State | Target State | Triggering Event | Actor / Role | Required Validations & Guard Conditions | Resulting Database Changes & Emitted Events |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TR-SET-01** | *None* | `BUILDING` | SC Activation event | System (Automated) | Parent SC activated; target set quantity defined. | Inserts row in `assembled_sets` with `status = 'BUILDING'` and all milestone booleans `false`. |
| **TR-SET-02** | `BUILDING` | `PRODUCTION_COMPLETE` | Final constituent piece reaches `COMPLETED` | System (Automated) | 100% of pre-allocated physical pieces have reached `pieces.status = 'COMPLETED'`. | Sets `is_production_complete = true`, `production_completed_at = NOW()`, `status = 'PRODUCTION_COMPLETE'`. Emits `SET_PRODUCTION_COMPLETED`. |
| **TR-SET-03** | `PRODUCTION_COMPLETE` | `FINAL_QC_PASSED` | QC Inspector certifies assembled set | `ROLE_QC` | Preceding state `PRODUCTION_COMPLETE`; all traveler records verified; physical checks passed. | Sets `is_final_qc_passed = true`, `final_qc_passed_at = NOW()`, `final_qc_passed_by = User.id`, `status = 'FINAL_QC_PASSED'`. Emits `SET_FINAL_QC_PASSED`. |
| **TR-SET-04** | `FINAL_QC_PASSED` | `PACKING_CONFIRMED` | Packaging employee submits checklist | `ROLE_PM`, `ROLE_ADMIN`, or authorized `ROLE_OPERATOR` | Preceding state `FINAL_QC_PASSED`; all serialized parts verified against BOM manifest. | Inserts `packing_records`. Sets `is_packed = true`, `packing_confirmed_at = NOW()`, `status = 'PACKING_CONFIRMED'`. Emits `SET_PACKING_CONFIRMED`. |
| **TR-SET-05** | `PACKING_CONFIRMED` | `DISPATCH_PENDING` | Automated follow-through upon packing | System (Automated) | Crate sealed and staged in shipping warehouse. | Sets `dispatch_pending_at = NOW()`, `status = 'DISPATCH_PENDING'`. Increments `packed_set_qty`. Emits `SET_DISPATCH_PENDING`. |
| **TR-SET-06** | `DISPATCH_PENDING` | `DELIVERED` | Dispatch employee confirms vehicle release | `ROLE_PM`, `ROLE_ADMIN` | Set is sealed in crate; transport carrier, vehicle, and gate pass recorded in DB. | Inserts `dispatch_confirmations`. Sets `is_delivered = true`, `delivered_at = NOW()`, `status = 'DELIVERED'`. Increments `dispatched_set_qty`. Emits `SET_DELIVERED`. |
| **TR-SET-07** | `FINAL_QC_PASSED`, `PACKING_CONFIRMED` | `BUILDING` | Damage or defect discovered at packing | `ROLE_QC`, `ROLE_PM` | Physical defect detected; QC issues failure on damaged component; unboxing logged. | Reverts `is_packed = false`, `is_final_qc_passed = false`, `is_production_complete = false`, `status = 'BUILDING'`. Emits `SET_REWOUND_TO_BUILDING`. |
| **TR-SET-08** | Any non-delivered state | `CANCELLED` | Parent SC cancellation or order reduction | `ROLE_ADMIN` | Order line cancelled; set has not left factory dock. | Sets `status = 'CANCELLED'`. Retires constituent pieces. Emits `SET_CANCELLED`. |

---

#### 3.3 Transition Matrix 3: Packing Record Lifecycle (`packing_records`)

| Transition ID | Source State | Target State | Triggering Event | Actor / Role | Required Validations & Guard Conditions | Resulting Database Changes & Emitted Events |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TR-PACK-01**| *None* | `ACTIVE` | Packaging checklist submitted | Authorized User (`PERMISSION_EXECUTE_PACKING`) | Target set is in `FINAL_QC_PASSED`; all checklist fields boolean true; serials matched. | Inserts row in `packing_records` with generated `packing_slip_number`. Links to `set_id`. |
| **TR-PACK-02**| `ACTIVE` | `VOIDED` | PM voids packing slip for audit/repack | `ROLE_PM`, `ROLE_ADMIN` | Set has not been dispatched (`status != 'DELIVERED'`); mandatory void reason provided. | Updates `is_void = true`, `void_reason`, `voided_at = NOW()`. Triggers rewind of set to `FINAL_QC_PASSED`. |

---

#### 3.4 Transition Matrix 4: Dispatch Confirmation Lifecycle (`dispatch_confirmations`)

| Transition ID | Source State | Target State | Triggering Event | Actor / Role | Required Validations & Guard Conditions | Resulting Database Changes & Emitted Events |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **TR-DSP-01** | *None* | `CONFIRMED` | Dock gate pass and release committed | Authorized User (`PERMISSION_CONFIRM_DISPATCH`) | All selected sets are in `DISPATCH_PENDING` (`is_packed = true`); carrier and vehicle entered. | Inserts immutable row in `dispatch_confirmations` with unique `dispatch_challan_no`. Emits `DISPATCH_CONFIRMED`. |

---

### 4. Concurrency Control and Race Condition Prevention

Shop-floor execution involves high concurrency: multiple operators entering measurements on different pieces, QC inspectors certifying assemblies, and shipping coordinators dispatching crates.

#### 4.1 Optimistic Locking on Set and Contract Entities
To prevent lost updates, `subcontracts`, `subcontract_products`, and `assembled_sets` utilize JPA/Hibernate optimistic locking (`@Version Integer version`):
* If an operator's measurement completion triggers an automated set status evaluation at the exact millisecond that a QC Inspector submits a Final QC certification, one transaction will detect the version mismatch, roll back, and retry cleanly using Spring's `@Retryable(OptimisticLockException.class)`.

#### 4.2 Explicit Row-Level Locking during Dispatch Batches
When an authorized user confirms a dock dispatch consignment:
* The transaction acquires a pessimistic write lock on the target sets:
  ```sql
  SELECT * FROM assembled_sets 
  WHERE id IN (:dispatchedSetIds) 
  FOR UPDATE;
  ```
* Validates that every locked row currently has `status = 'DISPATCH_PENDING'` and `is_packed = true`.
* Prevents the possibility of double-dispatching the same set on two different vehicle gate passes.
