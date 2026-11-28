package com.velan.production.shared.audit;

public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    STATUS_CHANGE,
    APPROVAL,
    PERMISSION_OVERRIDE,
    CANCELLATION,
    BYPASS,
    REASSIGNMENT,
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOCKOUT,
    PASSWORD_CHANGE,
    TOKEN_REFRESH,
    ACCESS_DENIED
}
