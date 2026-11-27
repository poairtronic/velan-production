-- Phase 7.7: Seed the 6 confirmed roles and role_permissions from docs/phase-1/06_Role_Matrix.md (no users)

INSERT INTO roles (id, name, description) VALUES
    ('11111111-1111-4111-8111-111111111101', 'ADMIN', 'System administrator and governance'),
    ('11111111-1111-4111-8111-111111111102', 'PM', 'Production Manager'),
    ('11111111-1111-4111-8111-111111111103', 'DESIGNER', 'Engineering and tolerance designer'),
    ('11111111-1111-4111-8111-111111111104', 'OPERATOR', 'Shop-floor operator'),
    ('11111111-1111-4111-8111-111111111105', 'QC', 'Quality Control inspector'),
    ('11111111-1111-4111-8111-111111111106', 'MANAGEMENT', 'Executive dashboard and reporting');

INSERT INTO role_permissions (id, role_id, permission)
SELECT
    ('22222222-2222-4222-8222-' || lpad(to_hex(row_number() OVER (ORDER BY r.name, p.perm)), 12, '0'))::uuid,
    r.id,
    p.perm
FROM roles r
JOIN (VALUES
    ('ADMIN', 'VIEW_APP_DATA'),
    ('ADMIN', 'USER_LIFECYCLE_MANAGE'),
    ('ADMIN', 'ROLE_PERMISSION_MANAGE'),
    ('ADMIN', 'NOTIFICATION_SETTINGS_MANAGE'),
    ('ADMIN', 'RECORD_CANCEL_DELETE'),
    ('ADMIN', 'SC_MANUAL_EXCEL_INTAKE'),
    ('ADMIN', 'SC_RM_IMPORT'),
    ('ADMIN', 'SC_STATUS_VIEW'),
    ('ADMIN', 'INTEGRATION_STATUS_VIEW'),
    ('ADMIN', 'MATERIAL_ISSUED_SET_MANUAL'),
    ('ADMIN', 'RM_MATERIAL_STATUS_VIEW'),
    ('ADMIN', 'REPORTS_VIEW'),
    ('ADMIN', 'AUDIT_LOGS_VIEW'),

    ('PM', 'VIEW_APP_DATA'),
    ('PM', 'SC_MANUAL_EXCEL_INTAKE'),
    ('PM', 'SC_RM_IMPORT'),
    ('PM', 'SC_STATUS_VIEW'),
    ('PM', 'INTEGRATION_STATUS_VIEW'),
    ('PM', 'MATERIAL_ISSUED_SET_MANUAL'),
    ('PM', 'REPLACEMENT_PIECE_RELEASE'),
    ('PM', 'PLANNING_MANAGE'),
    ('PM', 'WORK_ASSIGN'),
    ('PM', 'FLOW_SELECT'),
    ('PM', 'DRAWING_REVIEW'),
    ('PM', 'TOLERANCE_REVIEW'),
    ('PM', 'TOLERANCE_APPROVE'),
    ('PM', 'QC_DISPOSITION_DECIDE'),
    ('PM', 'VENDOR_TRACKING_VIEW'),
    ('PM', 'RM_MATERIAL_STATUS_VIEW'),
    ('PM', 'REPORTS_VIEW'),

    ('DESIGNER', 'VIEW_APP_DATA'),
    ('DESIGNER', 'SC_MANUAL_EXCEL_INTAKE'),
    ('DESIGNER', 'SC_STATUS_VIEW'),
    ('DESIGNER', 'FLOW_SELECT'),
    ('DESIGNER', 'TEMPLATE_VIEW'),
    ('DESIGNER', 'DRAWING_UPLOAD'),
    ('DESIGNER', 'TOLERANCE_VERIFY'),
    ('DESIGNER', 'VENDOR_TRACKING_VIEW'),
    ('DESIGNER', 'RM_MATERIAL_STATUS_VIEW'),
    ('DESIGNER', 'REPORTS_VIEW'),

    ('OPERATOR', 'VIEW_APP_DATA'),
    ('OPERATOR', 'SC_STATUS_VIEW'),
    ('OPERATOR', 'PLANNING_VIEW'),
    ('OPERATOR', 'DRAWING_VIEW'),
    ('OPERATOR', 'OPERATOR_MEASUREMENTS_WRITE'),
    ('OPERATOR', 'VENDOR_RETURN_TAG'),
    ('OPERATOR', 'RM_MATERIAL_STATUS_VIEW'),
    ('OPERATOR', 'REPORTS_VIEW'),

    ('QC', 'VIEW_APP_DATA'),
    ('QC', 'SC_STATUS_VIEW'),
    ('QC', 'PLANNING_VIEW'),
    ('QC', 'DRAWING_VIEW'),
    ('QC', 'QC_MEASUREMENTS_WRITE'),
    ('QC', 'QC_FLAG_RAISE'),
    ('QC', 'VENDOR_RETURN_INSPECT'),
    ('QC', 'RM_MATERIAL_STATUS_VIEW'),
    ('QC', 'REPORTS_VIEW'),

    ('MANAGEMENT', 'VIEW_APP_DATA'),
    ('MANAGEMENT', 'SC_STATUS_VIEW'),
    ('MANAGEMENT', 'PLANNING_VIEW'),
    ('MANAGEMENT', 'DRAWING_VIEW'),
    ('MANAGEMENT', 'VENDOR_TRACKING_VIEW'),
    ('MANAGEMENT', 'RM_MATERIAL_STATUS_VIEW'),
    ('MANAGEMENT', 'REPORTS_VIEW')
) AS p(role_name, perm) ON p.role_name = r.name;
