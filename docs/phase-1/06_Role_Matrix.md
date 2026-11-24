# 06 — Role Matrix

## 1. Confirmed primary roles
Admin, PM, Operator, QC and Management. The application should support dynamic RBAC and Admin-created custom roles where needed. The matrix below defines business responsibility; final action grants are configured in the application.

| Module/action | Admin | PM | Operator | QC | Management |
|---|---|---|---|---|---|
| View application-wide data | Yes | Production scope | Assigned/permitted scope | QC scope | Dashboard/report scope |
| User lifecycle | Full | No by default | No | No | No |
| Create custom roles/permissions | Yes | No by default | No | No | No |
| Manage notification settings for users | Yes | No | No | No | No |
| Cancel/delete records | Yes only | No by default | No | No | No |
| View RM SC intake/status | Yes | Yes | Assigned scope | QC scope | Dashboard scope |
| Process planning/priority | Permission-based | Yes | View assigned | View relevant | View permitted |
| Assign/reassign work | Permission-based | Yes | No | No | No by default |
| Manage process templates | Permission-based | Yes if granted | No | No | No |
| Upload/review tolerance sheet | Permission-based | Approve | View active values | View active values | View/report |
| Approve tolerance revision | Permission-based | Yes | No | No | No by default |
| Enter operator measurements | No by default | No | Yes | No | No |
| Enter QC measurements/decision | No by default | No | No | Yes | No |
| QC failure disposition/rework routing | Permission-based | Yes | No | Request/record QC finding | No by default |
| Override approval | Admin if granted | Yes if granted | No | Request only | No unless explicitly granted |
| Vendor tracking | Permission-based | Yes | Work-route view | QC/return inspection | Dashboard view |
| Material issue transactions | No in Production | No in Production | No | No | No |
| View RM material status | Yes | Yes | Relevant operation | Relevant inspection | Dashboard |
| Packing confirmation | Permission-based | Monitor/permission | If assigned permission | If assigned permission | View |
| Dispatch confirmation | Permission-based | Monitor/permission | If assigned permission | No | View |
| Dashboards/reports | All permitted | Production/team | Own | QC/team | Management scope |
| View security/admin logs | Yes | No | No | No | No |

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
- PM is the business owner for production scheduling, QC failure disposition, tolerance revision approval and production exceptions, subject to configured permissions.
- Admin retains user/permission governance and Admin-only cancellation/deletion.
- QC records inspection results but does not approve its own out-of-tolerance override unless separately authorized; original measurement remains unchanged.

## 5. Role naming note
Earlier drafts included Design, Department Head, Stores, Vendor Coordinator, Packing and Dispatch as separate roles. The latest confirmed primary structure is the five roles above. Those functions can be represented as permission sets/custom roles or assigned responsibilities without silently adding primary role groups.
