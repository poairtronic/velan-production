# 06 — Role Matrix

Updated 2026-11-24 — decisions D-01 to D-19 (see 00_DECISION_LOG.md)

## 1. Confirmed primary roles
Confirmed primary roles: **Admin, PM, Designer, Operator, QC, Management**. (D-03)
The application supports dynamic RBAC and Admin-created custom roles where needed. Individual login per employee. The matrix below defines business responsibility; final action grants are configured in the application.

| Module/action | Admin | PM | Designer | Operator | QC | Management |
|---|---|---|---|---|---|---|
| View application-wide data | Yes | Production scope | Design/SC scope | Assigned/permitted scope | QC scope | Dashboard/report scope |
| User lifecycle (individual login) | Full | No by default | No | No | No | No |
| Create custom roles/permissions | Yes | No by default | No | No | No | No |
| Manage notification settings for users | Yes | No | No | No | No | No |
| Cancel/delete records | Yes only | No by default | No | No | No | No |
| Enter SC manually / Excel import | Yes | Yes | Yes | No | No | No |
| Import in-progress SC from RM | Yes | Yes | No | No | No | No |
| View RM SC intake/status | Yes | Yes | Yes | Assigned scope | QC scope | Dashboard scope |
| View integration/sync status | Yes | Yes | No | No | No | No |
| Set material-issued flag (manual) | Yes | Yes | No | No | No | No |
| Release replacement piece (manual) | Permission-based | Yes | No | No | No | No |
| Process planning / daily priority list | Permission-based | Yes | No | View assigned queue | View relevant | View permitted |
| Assign/reassign work | Permission-based | Yes | No | No | No | No by default |
| Select process flow per product | Permission-based | Yes | Yes | No | No | No |
| Manage process templates | Admin (O-12) | PM if granted (O-12) | View only | No | No | No |
| Upload drawing / tolerance sheet | Permission-based | Review | Yes | View active values | View active values | View/report |
| Verify tolerance extraction | Permission-based | Review | Yes | No | No | No |
| Approve tolerance revision | Permission-based | Yes | No | No | No | No by default |
| Enter operator measurements | No by default | No | No | Yes (bulk grid) | No | No |
| Enter QC measurements | No by default | No | No | No | Yes | No |
| QC flag / failure disposition | Permission-based | Decides (4 outcomes) | No | No | Flags finding | No by default |
| Vendor tracking (read RM DC details) | Permission-based | Yes | View | Tag returned pieces | Inspect return | Dashboard view |
| Material issue transactions | No in Production | No in Production | No | No | No | No |
| View RM material status | Yes | Yes | View | Blocked if not issued | Inspection scope | Dashboard |
| Dashboards/reports & 9 AM report | All permitted | Production/team | Design scope | Own | QC/team | Management scope |
| View security/admin logs | Yes | No | No | No | No | No |

*(Note: Rows for Override approval, Packing confirmation, and Dispatch confirmation are removed per D-07 and D-10.)*

## 2. RBAC evaluation rules
1. Admin creates roles and grants module/action permissions.
2. A user may have multiple roles.
3. Individual Admin permission override takes priority over role permissions.
4. If no individual override exists, evaluate role permissions; an explicit deny in any applicable role wins over a grant.
5. If there is no explicit grant, deny.
6. Backend API authorization is mandatory; hiding UI controls is not security.
7. Permission changes and privileged actions are audited.
8. Sensitive approvals require reason and actor/time capture.

## 3. Employee suspension
When Admin suspends an employee with active work: block access as configured; notify PM; PM chooses return of incomplete quantity to shared worklist or reassignment; completed measurements/history remain unchanged; audit the reassignment.

## 4. Approval authority
- PM or Admin may provide final business acceptance.
- PM is the business owner for production scheduling, QC failure disposition (CONTINUE with reason, EXTRA PROCESS, RETURN TO EARLIER OP, REJECT AND RESTART), tolerance revision approval and production exceptions, subject to configured permissions.
- PM decides flagged pieces; a continue decision requires a mandatory reason. (D-07)
- Admin retains user/permission governance and Admin-only cancellation/deletion.
- QC records inspection results independently on every operation; original measurements remain unchanged.

## 5. Role naming note
Design is a primary role (Designer). Stores, Packing, Dispatch, Vendor Coordinator and Department Head are not roles; Stores actions stay in RM. (D-03)
